import Foundation
import Combine
import NetworkExtension
import XR18Core

struct PanelControlState: Identifiable {
    let id: String
    let title: String
    var fader: Float?
    var muted: Bool?
    var meter: Float
    let hasFader: Bool
    let hasMute: Bool
}

@MainActor final class SessionModel: ObservableObject {
    @Published var profiles: [MixerProfile] = []
    @Published var selectedProfileID: UUID?
    @Published var phaseText = "status.disconnected"
    @Published var connected = false
    @Published var isDemo = false
    @Published var controls: [PanelControlState] = []
    @Published var theme = "system"
    @Published var controlSize = "medium"
    @Published var textPercent = 100
    @Published var language = "system"
    @Published var showingReturnPrompt = false
    @Published var previousSSID = ""
    @Published var errorMessage: String?
    @Published private(set) var interactionGeneration = UUID()
    var selectedProfile: MixerProfile? { profiles.first { $0.id == selectedProfileID } ?? (isDemo ? demoProfile : nil) }
    private let defaults: UserDefaults
    private let wifi = WifiService()
    private let transport = MixerTransport()
    private var safety = SessionSafety()
    private var timer: Timer?
    private var connectTask: Task<Void, Never>?
    private var operation = UUID()
    private var started = false
    private var suspended = false
    private var joining = false
    private var requested = false
    private var pollTime: TimeInterval = 0
    private var remoteTime: TimeInterval = 0
    private var meterTime: TimeInterval = 0
    private var meterFrames: [String: (MeterFrame, TimeInterval)] = [:]
    private var demoValues: [String: PanelControlState] = [:]
    private var demoProfile: MixerProfile?
    private var journey: (from: String, to: String, profile: MixerProfile)?
    private var pendingJourney: (from: String, to: String, profile: MixerProfile)?
    private var now: TimeInterval { ProcessInfo.processInfo.systemUptime }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: "profiles"),
           let decoded = try? JSONDecoder().decode([MixerProfile].self, from: data) { profiles = decoded }
        selectedProfileID = defaults.string(forKey: "selectedProfile").flatMap(UUID.init(uuidString:)) ?? profiles.first?.id
        theme = defaults.string(forKey: "theme") ?? "system"
        controlSize = defaults.string(forKey: "controlSize") ?? "medium"
        let percent = defaults.integer(forKey: "textPercent")
        textPercent = [75,100,125,150,175,200,250,300].contains(percent) ? percent : 100
        language = defaults.string(forKey: "language") ?? "system"
        rebuildControls()
    }

    func startup() {
        guard !started else { return }; started = true
        if ProcessInfo.processInfo.arguments.contains("--ui-testing") {
            profiles = []; selectedProfileID = nil
            theme = "dark"; language = "en"; controlSize = "medium"; textPercent = 100
        }
        if ProcessInfo.processInfo.arguments.contains("--demo") || defaults.bool(forKey: "lastModeDemo") { enterDemo() }
        else if selectedProfile != nil { connect() }
    }
    func resume() {
        guard started, suspended else { return }; suspended = false
        Task { @MainActor in
            if let pending = pendingJourney, await wifi.currentSSID() == pending.to {
                journey = pending; previousSSID = pending.from; pendingJourney = nil
            }
            if requested && !isDemo { connect() }
        }
    }
    func suspend() {
        suspended = true; operation = UUID(); connectTask?.cancel()
        stopTransport(); connected = false
        if !isDemo { phaseText = "status.disconnected" }
    }
    func connect() {
        guard !joining, let profile = selectedProfile else { return }
        guard validHost(profile.host) else {
            errorMessage = localized("error.host")
            return
        }
        requested = true; isDemo = false; errorMessage = nil
        defaults.set(false, forKey: "lastModeDemo")
        stopTransport(); operation = UUID()
        let token = operation
        connectTask?.cancel()
        joining = !profile.wifiSSID.isEmpty
        connectTask = Task { @MainActor in
            if !profile.wifiSSID.isEmpty {
                let before = await wifi.currentSSID(requestPermission: true)
                guard token == operation, !Task.isCancelled else { joining = false; return }
                if before != profile.wifiSSID {
                    phaseText = "status.joiningWifi"
                    if let before { pendingJourney = (before, profile.wifiSSID, profile) }
                    do {
                        try await wifi.join(ssid: profile.wifiSSID, password: password(for: profile, returning: false))
                        guard token == operation, !Task.isCancelled else { joining = false; return }
                        if let before, before != profile.wifiSSID {
                            journey = (before, profile.wifiSSID, profile); previousSSID = before
                            pendingJourney = nil
                        }
                    } catch {
                        joining = false
                        guard token == operation, !Task.isCancelled else { return }
                        phaseText = "status.failed"; errorMessage = userFacing(error)
                        return
                    }
                }
                joining = false
            }
            guard token == operation, !suspended, !Task.isCancelled else { return }
            startTransport(profile)
        }
    }
    func disconnect() {
        requested = false; operation = UUID(); connectTask?.cancel()
        isDemo = false; stopTransport(); phaseText = "status.disconnected"
    }
    private func stopTransport() {
        interactionGeneration = UUID()
        timer?.invalidate(); timer = nil
        transport.stop(); safety.disconnect(); connected = false
        meterFrames.removeAll(); rebuildControls()
    }
    private func startTransport(_ profile: MixerProfile) {
        stopTransport()
        let generation = safety.begin(selectedIDs: profile.controls, now: now)
        phaseText = "status.connecting"
        transport.onReady = { [weak self] in
            guard let self, self.safety.generation == generation else { return }
            self.transport.send(.init(address: "/xinfo"))
        }
        transport.onPacket = { [weak self] data in self?.receive(data, generation: generation) }
        transport.onFailure = { [weak self] description in
            guard let self, self.safety.generation == generation else { return }
            self.recover()
        }
        pollTime = 0; remoteTime = 0; meterTime = 0
        transport.start(host: profile.host)
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }
    private func receive(_ data: Data, generation: UUID) {
        guard !isDemo, !suspended, generation == safety.generation else { return }
        if let meter = MeterCodec.decode(data) {
            guard safety.isReady(at: now) else { return }
            meterFrames[meter.bank] = (meter, now); rebuildControls(); return
        }
        guard let messages = try? OSCCodec.decode(data) else { return }
        safety.receive(messages, generation: generation, now: now)
        if generation != safety.generation { recover(); return }
        connected = safety.isReady(at: now)
        phaseText = connected ? "status.connected" : safety.identity == nil ? "status.connecting" : "status.synchronizing"
        if connected { errorMessage = nil }
        rebuildControls()
    }
    private func tick() {
        guard !isDemo, !suspended, requested else { return }
        if safety.expire(at: now) { recover(); return }
        connected = safety.isReady(at: now)
        if safety.identity == nil {
            if now - pollTime >= 1 { transport.send(.init(address: "/xinfo")); pollTime = now }
            return
        }
        if !connected { phaseText = "status.synchronizing" }
        if now - remoteTime >= 4 { transport.send(.init(address: "/xremote")); remoteTime = now }
        if now - pollTime >= 1 {
            safety.requiredPaths.sorted().forEach { transport.send(.init(address: $0)) }; pollTime = now
        }
        if now - meterTime >= MeterCodec.renewalInterval {
            for bank in [MeterCodec.channelBank, MeterCodec.dcaBank] {
                if let message = MeterCodec.subscription(bank) { transport.send(message) }
            }
            meterTime = now
        }
        rebuildControls()
    }
    private func recover() {
        guard let profile = selectedProfile, requested, !suspended, !isDemo else { return }
        stopTransport(); phaseText = "status.stale"
        let token = operation
        connectTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            guard token == operation, !Task.isCancelled, requested, !suspended else { return }
            startTransport(profile)
        }
    }
    func setFader(_ id: String, value: Float, expectedGeneration: UUID? = nil) {
        if let expectedGeneration, expectedGeneration != interactionGeneration { return }
        guard value.isFinite, (0...1).contains(value), let descriptor = ControlCatalog.find(id), let path = descriptor.faderPath else { return }
        if isDemo { demoValues[id]?.fader = value; rebuildControls(); return }
        send(path: path, value: .float(value))
    }
    func toggleMute(_ id: String, expectedGeneration: UUID? = nil) {
        if let expectedGeneration, expectedGeneration != interactionGeneration { return }
        guard let descriptor = ControlCatalog.find(id), let path = descriptor.mutePath,
              let muted = controls.first(where: { $0.id == id })?.muted else { return }
        if isDemo { demoValues[id]?.muted = !muted; rebuildControls(); return }
        send(path: path, value: .int(descriptor.muteValue(muted: !muted)))
    }
    private func send(path: String, value: OSCValue) {
        guard !suspended, let message = safety.command(path: path, value: value, generation: safety.generation, now: now) else {
            connected = false; return
        }
        transport.send(message); transport.send(.init(address: path))
    }
    func enterDemo() {
        disconnect(); isDemo = true
        defaults.set(true, forKey: "lastModeDemo")
        if selectedProfile == nil {
            demoProfile = MixerProfile(name: "Demo XR18", controls: ["master", "dca1", "mute1"])
        }
        demoValues = Dictionary(uniqueKeysWithValues: ControlCatalog.all.map { control in
            (control.id, PanelControlState(id: control.id, title: control.title,
                fader: control.faderPath == nil ? nil : 0.65, muted: false, meter: 0.55,
                hasFader: control.faderPath != nil, hasMute: control.mutePath != nil))
        })
        connected = false; phaseText = "status.demo"; rebuildControls()
    }
    private func rebuildControls() {
        controls = (selectedProfile?.controls ?? []).compactMap { id in
            guard let descriptor = ControlCatalog.find(id) else { return nil }
            if isDemo, let demo = demoValues[descriptor.id] { return demo }
            var fader: Float?; var muted: Bool?
            if let path = descriptor.faderPath, case .float(let value)? = safety.values[path] { fader = value }
            if let path = descriptor.mutePath, case .int(let value)? = safety.values[path] { muted = descriptor.isMuted(wireValue: value) }
            let db = meterFrames.values.filter { now - $0.1 <= MeterCodec.freshness }
                .flatMap { $0.0.levels(for: id) }.max() ?? -90
            return PanelControlState(id: descriptor.id, title: descriptor.title, fader: fader, muted: muted,
                meter: connected ? max(0, min(1, (db + 60) / 60)) : 0,
                hasFader: descriptor.faderPath != nil, hasMute: descriptor.mutePath != nil)
        }
    }
    func saveProfile(_ profile: MixerProfile, password: String?, returnPassword: String?) {
        guard !joining else { errorMessage = localized("error.busy"); return }
        errorMessage = nil
        var profile = profile
        profile.name = profile.name.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.host = profile.host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !profile.name.isEmpty, validHost(profile.host), !profile.controls.isEmpty else {
            errorMessage = localized("error.profile"); return
        }
        profile.controls = ControlCatalog.all.filter { profile.controls.contains($0.id) }.map(\.id)
        guard !profile.controls.isEmpty, profile.wifiSSID.utf8.count <= 32, profile.returnSSID.utf8.count <= 32 else {
            errorMessage = localized("error.ssid"); return
        }
        if [password, returnPassword].compactMap({ $0 }).contains(where: { !$0.isEmpty && !(8...63).contains($0.utf8.count) }) {
            errorMessage = localized("error.password"); return
        }
        do {
            if let password { try CredentialStore.write(password, key: credentialKey(profile, returning: false)) }
            if let returnPassword { try CredentialStore.write(returnPassword, key: credentialKey(profile, returning: true)) }
        } catch { errorMessage = userFacing(error); return }
        let wasDemo = isDemo
        disconnect()
        if let index = profiles.firstIndex(where: { $0.id == profile.id }) { profiles[index] = profile }
        else { profiles.append(profile) }
        selectedProfileID = profile.id; persist()
        if wasDemo { enterDemo() } else { rebuildControls() }
    }
    func deleteProfile(_ id: UUID) {
        guard !joining else { return }
        if let profile = profiles.first(where: { $0.id == id }) {
            do {
                try CredentialStore.write("", key: credentialKey(profile, returning: false))
                try CredentialStore.write("", key: credentialKey(profile, returning: true))
            } catch { errorMessage = userFacing(error); return }
        }
        if selectedProfileID == id { disconnect() }
        profiles.removeAll { $0.id == id }
        if selectedProfileID == id { selectedProfileID = profiles.first?.id }
        persist(); rebuildControls()
    }
    func selectProfile(_ id: UUID) {
        guard !joining, profiles.contains(where: { $0.id == id }) else { return }
        disconnect(); selectedProfileID = id; persist(); connect()
    }
    func savePreferences() { persist() }
    func password(for profile: MixerProfile, returning: Bool) -> String {
        CredentialStore.read(credentialKey(profile, returning: returning)) ?? ""
    }
    private func credentialKey(_ profile: MixerProfile, returning: Bool) -> String { profile.id.uuidString + (returning ? ".return" : ".mixer") }
    private func persist() {
        if !ProcessInfo.processInfo.arguments.contains("--ui-testing") {
            defaults.set(try? JSONEncoder().encode(profiles), forKey: "profiles")
            defaults.set(selectedProfileID?.uuidString, forKey: "selectedProfile")
        }
        defaults.set(theme, forKey: "theme"); defaults.set(controlSize, forKey: "controlSize")
        defaults.set(textPercent, forKey: "textPercent"); defaults.set(language, forKey: "language")
    }
    func finishSession() {
        disconnect()
        if journey != nil { showingReturnPrompt = true }
    }
    func stayOnMixerWifi() {
        showingReturnPrompt = false; journey = nil; pendingJourney = nil; previousSSID = ""
    }
    func returnToPreviousWifi() {
        guard !joining else { return }
        let profile = journey?.profile ?? selectedProfile
        guard let profile, !profile.returnSSID.isEmpty else {
            errorMessage = localized("error.returnMissing"); return
        }
        if let journey, journey.from != profile.returnSSID {
            errorMessage = localized("error.returnDifferent"); return
        }
        disconnect(); showingReturnPrompt = false; joining = true
        let token = operation
        phaseText = "status.returningWifi"
        connectTask = Task { @MainActor in
            defer { joining = false }
            do {
                try await wifi.join(ssid: profile.returnSSID, password: password(for: profile, returning: true))
                guard token == operation, !Task.isCancelled else { return }
                stayOnMixerWifi(); phaseText = "status.disconnected"
            } catch {
                guard token == operation, !Task.isCancelled else { return }
                phaseText = "status.failed"; errorMessage = userFacing(error)
            }
        }
    }
    private func validHost(_ host: String) -> Bool {
        guard !host.isEmpty, host.count <= 253,
              host.unicodeScalars.allSatisfy({ CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-").contains($0) }),
              host != "0.0.0.0", host != "255.255.255.255", !host.hasSuffix(".255") else { return false }
        if host.allSatisfy({ $0.isNumber || $0 == "." }) {
            let segments = host.split(separator: ".", omittingEmptySubsequences: false)
            guard segments.count == 4 else { return false }
            let bytes = segments.compactMap { Int($0) }
            guard bytes.count == 4, bytes.allSatisfy({ (0...255).contains($0) }), bytes[0] > 0, bytes[0] < 224 else { return false }
            #if !targetEnvironment(simulator)
            if bytes[0] == 127 { return false }
            #endif
        } else if host.split(separator: ".", omittingEmptySubsequences: false).contains(where: {
            $0.isEmpty || $0.count > 63 || $0.first == "-" || $0.last == "-"
        }) { return false }
        return true
    }
    private func localized(_ key: String) -> String {
        let bundle = language == "system" ? Bundle.main : Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:)) ?? Bundle.main
        return bundle.localizedString(forKey: key, value: nil, table: nil)
    }
    private func userFacing(_ error: Error) -> String {
        if error is CredentialStore.StoreError { return localized("error.keychain") }
        if let wifiError = error as? WifiService.WifiError {
            switch wifiError {
            case .simulator: return localized("error.simulatorWifi")
            case .unconfirmed: return localized("error.wifiUnconfirmed")
            }
        }
        let ns = error as NSError
        if ns.domain == NEHotspotConfigurationErrorDomain && ns.code == NEHotspotConfigurationError.userDenied.rawValue {
            return localized("error.wifiDenied")
        }
        return localized("error.wifiFailed")
    }
}
