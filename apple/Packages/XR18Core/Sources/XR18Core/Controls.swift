import Foundation

public struct MixerProfile: Identifiable, Codable, Equatable {
    public var id: UUID
    public var name: String
    public var host: String
    public var wifiSSID: String
    public var returnSSID: String
    public var controls: [String]

    public init(id: UUID = UUID(), name: String = "My XR18", host: String = "192.168.1.1",
                wifiSSID: String = "", returnSSID: String = "", controls: [String] = ["master"]) {
        self.id = id; self.name = name; self.host = host
        self.wifiSSID = wifiSSID; self.returnSSID = returnSSID; self.controls = controls
    }
}

public struct ControlDescriptor: Identifiable, Equatable {
    public let id: String
    public let title: String
    public let faderPath: String?
    public let mutePath: String?
    /// True when the wire switch is signal-on: 0 is muted, 1 is audible.
    public let muteInverted: Bool

    public init(id: String, title: String, faderPath: String?, mutePath: String?, muteInverted: Bool) {
        self.id = id; self.title = title; self.faderPath = faderPath
        self.mutePath = mutePath; self.muteInverted = muteInverted
    }

    public func isMuted(wireValue: Int32) -> Bool { muteInverted ? wireValue == 0 : wireValue != 0 }
    public func muteValue(muted: Bool) -> Int32 { muted != muteInverted ? 1 : 0 }
}

public enum ControlCatalog {
    public static let all: [ControlDescriptor] = {
        var result = [ControlDescriptor(id: "master", title: "MAIN LR", faderPath: "/lr/mix/fader", mutePath: "/lr/mix/on", muteInverted: true)]
        for i in 1...4 { result.append(.init(id: "dca\(i)", title: "DCA \(i)", faderPath: "/dca/\(i)/fader", mutePath: "/dca/\(i)/on", muteInverted: true)) }
        for i in 1...4 { result.append(.init(id: "mute\(i)", title: "MUTE GROUP \(i)", faderPath: nil, mutePath: "/config/mute/\(i)", muteInverted: false)) }
        for i in 1...6 { result.append(.init(id: "bus\(i)", title: "BUS \(i)", faderPath: "/bus/\(i)/mix/fader", mutePath: "/bus/\(i)/mix/on", muteInverted: true)) }
        for i in 1...16 {
            let number = String(format: "%02d", i)
            result.append(.init(id: "ch\(i)", title: "CH \(number)", faderPath: "/ch/\(number)/mix/fader", mutePath: "/ch/\(number)/mix/on", muteInverted: true))
        }
        return result
    }()
    public static func find(_ id: String) -> ControlDescriptor? { all.first { $0.id == (id == "main" ? "master" : id) } }
    public static func paths(for ids: [String]) -> Set<String> {
        Set(ids.compactMap(find).flatMap { [$0.faderPath, $0.mutePath].compactMap { $0 } })
    }
    public static func validated(path: String, value: OSCValue) -> OSCValue? {
        for control in all {
            if control.faderPath == path, case .float(let number) = value, number.isFinite, (0...1).contains(number) { return value }
            if control.mutePath == path, case .int(let number) = value, number == 0 || number == 1 { return value }
        }
        return nil
    }
}

public enum XAir {
    public static let port: UInt16 = 10024
    /// Exact four-segment X AIR taper. Zero represents the -90 dB floor.
    public static func faderToDB(_ normalized: Float) -> Float {
        guard !normalized.isNaN else { return .nan }
        let v = max(0, min(1, normalized))
        if v >= 0.5 { return 40 * v - 30 }
        if v >= 0.25 { return 80 * v - 50 }
        if v >= 0.0625 { return 160 * v - 70 }
        return 480 * v - 90
    }
    public static func dbToFader(_ db: Float) -> Float {
        guard !db.isNaN else { return .nan }
        if db >= 10 { return 1 }
        if db >= -10 { return (db + 30) / 40 }
        if db >= -30 { return (db + 50) / 80 }
        if db >= -60 { return (db + 70) / 160 }
        if db >= -90 { return (db + 90) / 480 }
        return 0
    }
}
