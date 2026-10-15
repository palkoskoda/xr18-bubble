import SwiftUI
import XR18Core

/// The panel deliberately keeps transport state in SessionModel. A grey panel is read-only.
struct MixerRootView: View {
    @EnvironmentObject private var model: SessionModel
    @State private var showingMixers = false
    @State private var showingSettings = false

    private var scale: CGFloat { PanelAppearance.scale(model.controlSize) }
    private var interactive: Bool { model.connected || model.isDemo }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                statusBar
                if model.controls.isEmpty {
                    ContentUnavailableView {
                        Label("panel.empty.title", systemImage: "slider.vertical.3")
                    } description: {
                        Text(LocalizedStringKey(model.profiles.isEmpty ? "panel.empty.profile" : "panel.empty.controls"))
                    } actions: {
                        Button("mixers.title") { showingMixers = true }
                            .buttonStyle(.borderedProminent)
                        Button("demo.start") { model.enterDemo() }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("panel.demo")
                    }
                } else {
                    ScrollView([.horizontal, .vertical]) {
                        HStack(alignment: .top, spacing: 12 * scale) {
                            ForEach(model.controls) { control in
                                let generation = model.interactionGeneration
                                MixerControlCard(control: control, scale: scale,
                                    textScale: CGFloat(model.textPercent) / 100,
                                    interactive: interactive,
                                    onFader: { model.setFader(control.id, value: $0, expectedGeneration: generation) },
                                    onMute: { model.toggleMute(control.id, expectedGeneration: generation) })
                                    .id("\(control.id)-\(model.interactionGeneration)-\(interactive)")
                            }
                        }
                        .padding(16)
                    }
                    .accessibilityIdentifier("mixer.panel")
                    .background(Color(uiColor: .systemGroupedBackground))
                }
            }
            .navigationTitle(model.selectedProfile?.name ?? String(localized: "app.name"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showingMixers = true } label: {
                        Label("mixers.title", systemImage: "server.rack")
                    }
                    .accessibilityIdentifier("panel.mixers")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button { showingSettings = true } label: {
                            Label("settings.title", systemImage: "textformat.size")
                        }
                        .accessibilityIdentifier("panel.settings")
                        Button { showingMixers = true } label: {
                            Label("mixers.title", systemImage: "server.rack")
                        }
                        Divider()
                        if !model.isDemo && model.selectedProfile != nil {
                            Button { model.connect() } label: {
                                Label("connection.reconnect", systemImage: "arrow.clockwise")
                            }
                        }
                        Button { model.enterDemo() } label: {
                            Label("demo.start", systemImage: "play.rectangle")
                        }
                        .accessibilityIdentifier("panel.demo")
                        if let profile = model.selectedProfile, !profile.returnSSID.isEmpty, !model.isDemo {
                            Button { model.returnToPreviousWifi() } label: {
                                Label("return.confirm", systemImage: "wifi")
                            }
                        }
                        Button { model.finishSession() } label: {
                            Label("session.finish", systemImage: "power")
                        }
                        .accessibilityIdentifier("panel.finish")
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .accessibilityLabel(Text("menu.title"))
                    }
                    .accessibilityIdentifier("panel.menu")
                }
            }
            .sheet(isPresented: $showingMixers) { MixersView().environmentObject(model) }
            .sheet(isPresented: $showingSettings) { PanelSettingsView().environmentObject(model) }
            .confirmationDialog("return.title", isPresented: $model.showingReturnPrompt,
                                titleVisibility: .visible) {
                Button("return.confirm") { model.returnToPreviousWifi() }
                Button("return.stay", role: .cancel) { model.stayOnMixerWifi() }
            } message: {
                Text("return.message")
            }
            .alert("error.title", isPresented: Binding(
                get: { model.errorMessage != nil },
                set: { if !$0 { model.errorMessage = nil } })) {
                    Button("common.ok", role: .cancel) { model.errorMessage = nil }
                } message: { Text(model.errorMessage ?? "") }
        }
        .preferredColorScheme(model.theme == "dark" ? .dark : model.theme == "light" ? .light : nil)
    }

    private var statusBar: some View {
        HStack(spacing: 8) {
            Image(systemName: model.isDemo ? "play.circle.fill" : model.connected ? "checkmark.circle.fill" : "circle.dashed")
                .foregroundStyle(model.isDemo ? .orange : model.connected ? .green : .secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(LocalizedStringKey(model.phaseText)).font(.subheadline.weight(.semibold))
                if model.isDemo { Text("demo.notice").font(.caption).foregroundStyle(.secondary) }
            }
            Spacer(minLength: 8)
            if !model.connected && !model.isDemo && model.selectedProfile != nil {
                Button("connection.connect") { model.connect() }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("connection.connect")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("panel.status")
    }
}

enum PanelAppearance {
    static let sizes = ["small", "medium", "large", "xl", "2xl", "3xl", "4xl"]
    static let textPercentages = [75, 100, 125, 150, 175, 200, 250, 300]
    static func scale(_ size: String) -> CGFloat {
        ["small": 0.8, "medium": 1, "large": 1.25, "xl": 1.6,
         "2xl": 2.1, "3xl": 2.8, "4xl": 4][size] ?? 1
    }
    static func accent(_ id: String) -> Color {
        if id.hasPrefix("dca") { return Color(red: 0.83, green: 0.68, blue: 0.24) }
        if id.hasPrefix("bus") { return Color(red: 0.63, green: 0.45, blue: 0.78) }
        if id.hasPrefix("ch") { return Color(red: 0.23, green: 0.65, blue: 0.43) }
        return Color(red: 0.24, green: 0.57, blue: 0.83)
    }
    static func title(_ id: String, fallback: String) -> Text {
        if id == "master" || id == "main" { return Text("control.master") }
        if id.hasPrefix("dca"), let n = Int(id.dropFirst(3)) { return Text("DCA \(n)") }
        if id.hasPrefix("bus"), let n = Int(id.dropFirst(3)) { return Text("control.bus \(n)") }
        if id.hasPrefix("ch"), let n = Int(id.dropFirst(2)) { return Text("control.channel \(n)") }
        if id.hasPrefix("mute"), let n = Int(id.dropFirst(4)) { return Text("control.muteGroup \(n)") }
        return Text(fallback)
    }
    static func decibels(_ fader: Float) -> String {
        let value = min(1, max(0, fader))
        if value <= 0 { return "−∞ dB" }
        return String(format: "%+.1f dB", XAir.faderToDB(value))
    }
}

private struct MixerControlCard: View {
    let control: PanelControlState
    let scale: CGFloat
    let textScale: CGFloat
    let interactive: Bool
    let onFader: (Float) -> Void
    let onMute: () -> Void
    @ScaledMetric(relativeTo: .body) private var baseFont: CGFloat = 17
    private var accent: Color { PanelAppearance.accent(control.id) }
    private var fontSize: CGFloat { baseFont * scale * textScale }
    private var width: CGFloat { max(128 * scale, fontSize * 7.5) }

    var body: some View {
        VStack(spacing: 12 * scale) {
            PanelAppearance.title(control.id, fallback: control.title)
                .font(.system(size: fontSize, weight: .semibold))
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 6 * scale)
            if control.hasFader {
                Text(control.fader.map(PanelAppearance.decibels) ?? "— dB")
                    .font(.system(size: fontSize, weight: .medium, design: .monospaced))
                    .fixedSize()
                    .accessibilityIdentifier("\(control.id).value")
                VerticalMixerFader(value: control.fader ?? 0, meter: control.meter,
                    scale: scale, accent: accent, interactive: interactive && control.fader != nil,
                    onChange: onFader)
                    .frame(height: 245 * scale)
                    .accessibilityLabel(PanelAppearance.title(control.id, fallback: control.title))
                    .accessibilityValue(control.fader.map(PanelAppearance.decibels) ?? String(localized: "control.unavailable"))
                    .accessibilityAdjustableAction { direction in
                        guard interactive, let current = control.fader else { return }
                        switch direction {
                        case .increment: onFader(min(1, current + 0.01))
                        case .decrement: onFader(max(0, current - 0.01))
                        @unknown default: break
                        }
                    }
                    .accessibilityIdentifier("control.\(control.id).fader")
            }
            if control.hasMute {
                Button(action: onMute) {
                    VStack(spacing: 4 * scale) {
                        Image(systemName: control.muted == true ? "speaker.slash.fill" : "speaker.wave.2.fill")
                        Text(LocalizedStringKey(control.muted == true ? "control.muted" : "control.mute"))
                    }
                    .font(.system(size: fontSize, weight: .bold))
                    .frame(maxWidth: .infinity, minHeight: max(52, fontSize * 3))
                    .padding(.vertical, 6 * scale)
                    .foregroundStyle(control.muted == true ? Color.white : Color.primary)
                    .background(control.muted == true ? Color.red : Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10 * scale))
                }
                .buttonStyle(.plain)
                .disabled(!interactive || control.muted == nil)
                .accessibilityLabel(Text("control.mute") + Text(" ") + PanelAppearance.title(control.id, fallback: control.title))
                .accessibilityValue(Text(LocalizedStringKey(control.muted == nil ? "control.unavailable" : control.muted == true ? "control.muted" : "control.unmuted")))
                .accessibilityIdentifier("control.\(control.id).mute")
            }
        }
        .padding(12 * scale)
        .frame(width: width, alignment: .top)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16 * scale))
        .overlay(alignment: .top) { RoundedRectangle(cornerRadius: 3).fill(accent).frame(height: 5 * scale).padding(.horizontal, 16 * scale) }
        .overlay(RoundedRectangle(cornerRadius: 16 * scale).strokeBorder(accent.opacity(interactive ? 0.45 : 0.12), lineWidth: 1))
        .opacity(interactive ? 1 : 0.62)
        .accessibilityElement(children: .contain)
    }
}

private struct VerticalMixerFader: View {
    let value: Float
    let meter: Float
    let scale: CGFloat
    let accent: Color
    let interactive: Bool
    let onChange: (Float) -> Void
    @State private var startingValue: Float?

    var body: some View {
        GeometryReader { geometry in
            let knobHeight = 30 * scale
            let travel = max(1, geometry.size.height - knobHeight)
            let normalized = CGFloat(min(1, max(0, value)))
            ZStack {
                RoundedRectangle(cornerRadius: 4 * scale)
                    .fill(Color.primary.opacity(0.12))
                    .frame(width: 8 * scale)
                ForEach(0..<11) { tick in
                    Rectangle().fill(Color.secondary.opacity(tick == 10 ? 0.9 : 0.35))
                        .frame(width: (tick % 5 == 0 ? 42 : 26) * scale, height: 1)
                        .position(x: geometry.size.width / 2, y: knobHeight / 2 + CGFloat(tick) / 10 * travel)
                }
                GeometryReader { meterGeometry in
                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        RoundedRectangle(cornerRadius: 3 * scale)
                            .fill(meter >= 0.94 ? Color.red : meter >= 0.75 ? Color.yellow : Color.green)
                            .frame(height: meterGeometry.size.height * CGFloat(min(1, max(0, meter))))
                    }
                }
                .frame(width: 7 * scale)
                .padding(.vertical, knobHeight / 2)
                .offset(x: 37 * scale)
                .accessibilityHidden(true)
                RoundedRectangle(cornerRadius: 5 * scale)
                    .fill(LinearGradient(colors: [accent.opacity(0.85), accent], startPoint: .top, endPoint: .bottom))
                    .overlay { Rectangle().fill(.white.opacity(0.85)).frame(height: 2 * scale).padding(.horizontal, 5 * scale) }
                    .overlay { RoundedRectangle(cornerRadius: 5 * scale).strokeBorder(Color.white.opacity(0.3), lineWidth: 1) }
                    .frame(width: 58 * scale, height: knobHeight)
                    .shadow(color: .black.opacity(0.2), radius: 2 * scale, y: 2 * scale)
                    .position(x: geometry.size.width / 2, y: knobHeight / 2 + (1 - normalized) * travel)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 8)
                .onChanged { drag in
                    guard interactive else { return }
                    if startingValue == nil { startingValue = value }
                    let change = Float(drag.translation.height / travel)
                    onChange(min(1, max(0, (startingValue ?? value) - change)))
                }
                .onEnded { _ in startingValue = nil })
        }
        .accessibilityElement(children: .ignore)
    }
}
