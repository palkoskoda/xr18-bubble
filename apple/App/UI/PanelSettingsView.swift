import SwiftUI

struct PanelSettingsView: View {
    @EnvironmentObject private var model: SessionModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("settings.controlSize", selection: preference(\.controlSize)) {
                        ForEach(PanelAppearance.sizes, id: \.self) { size in
                            Text(LocalizedStringKey("size." + size)).tag(size)
                        }
                    }.accessibilityIdentifier("settings.size")
                    Picker("settings.textSize", selection: preference(\.textPercent)) {
                        ForEach(PanelAppearance.textPercentages, id: \.self) { percent in
                            (Text(percent, format: .number).monospacedDigit() + Text(" %"))
                                .tag(percent)
                        }
                    }.accessibilityIdentifier("settings.textSize")
                } header: { Text("settings.panel") } footer: { Text("settings.size.help") }
                Section {
                    Picker("settings.theme", selection: preference(\.theme)) {
                        Text("theme.system").tag("system")
                        Text("theme.light").tag("light")
                        Text("theme.dark").tag("dark")
                    }.accessibilityIdentifier("settings.theme")
                    Picker("settings.language", selection: preference(\.language)) {
                        Text("language.system").tag("system")
                        Text("English").tag("en")
                        Text("Slovenčina").tag("sk")
                    }.accessibilityIdentifier("settings.language")
                } header: { Text("settings.appearance") }
                Section {
                    Text("settings.accessibility.help")
                    Label("settings.voiceover", systemImage: "accessibility")
                } header: { Text("settings.accessibility") }
                Section {
                    Text("XR18 Bubble").font(.headline)
                    Text("about.author")
                    Link("pavolskoda@azet.sk", destination: URL(string: "mailto:pavolskoda@azet.sk")!)
                    Link("about.website", destination: URL(string: "https://palkoskoda.github.io/xr18-bubble/")!)
                    Link("about.source", destination: URL(string: "https://github.com/palkoskoda/xr18-bubble")!)
                } header: { Text("about.title") } footer: { Text("about.independent") }
            }
            .navigationTitle("settings.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) {
                Button("common.done") { dismiss() }.accessibilityIdentifier("settings.done")
            } }
        }
    }

    private func preference<Value>(_ keyPath: ReferenceWritableKeyPath<SessionModel, Value>) -> Binding<Value> {
        Binding(get: { model[keyPath: keyPath] }, set: { value in
            model[keyPath: keyPath] = value
            model.savePreferences()
        })
    }
}
