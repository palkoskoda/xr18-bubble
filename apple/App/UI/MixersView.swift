import SwiftUI
import XR18Core

struct MixersView: View {
    @EnvironmentObject private var model: SessionModel
    @Environment(\.dismiss) private var dismiss
    @State private var editingProfile: MixerProfile?
    @State private var showingEditor = false
    @State private var pendingDeletion: UUID?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if model.profiles.isEmpty { Text("mixers.empty").foregroundStyle(.secondary) }
                    ForEach(model.profiles, id: \.id) { profile in
                        HStack(spacing: 12) {
                            Button {
                                model.selectProfile(profile.id)
                                dismiss()
                            } label: {
                                HStack {
                                    Image(systemName: model.selectedProfileID == profile.id ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(model.selectedProfileID == profile.id ? Color.accentColor : Color.secondary)
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(profile.name).font(.headline)
                                        Text(profile.host).font(.subheadline).foregroundStyle(.secondary)
                                        if !profile.wifiSSID.isEmpty {
                                            Label(profile.wifiSSID, systemImage: "wifi").font(.caption).foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                }.contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Button {
                                editingProfile = profile
                                showingEditor = true
                            } label: { Image(systemName: "pencil.circle").font(.title2) }
                                .buttonStyle(.borderless)
                                .accessibilityLabel(Text("profile.edit") + Text(" ") + Text(profile.name))
                        }
                        .padding(.vertical, 4)
                        .swipeActions {
                            Button("common.delete", role: .destructive) { pendingDeletion = profile.id }
                        }
                        .contextMenu {
                            Button("profile.edit") { editingProfile = profile; showingEditor = true }
                            Button("common.delete", role: .destructive) { pendingDeletion = profile.id }
                        }
                    }
                    Button {
                        editingProfile = nil
                        showingEditor = true
                    } label: { Label("profile.add", systemImage: "plus.circle.fill") }
                        .accessibilityIdentifier("profile.add")
                } header: { Text("mixers.saved") } footer: { Text("mixers.instructions") }
                Section {
                    Button { model.enterDemo(); dismiss() } label: {
                        Label("demo.start", systemImage: "play.rectangle")
                    }
                } footer: { Text("demo.notice") }
            }
            .navigationTitle("mixers.title")
            .toolbar { ToolbarItem(placement: .confirmationAction) {
                Button("common.done") { dismiss() }.accessibilityIdentifier("mixers.done")
            } }
            .sheet(isPresented: $showingEditor) {
                ProfileEditorView(profile: editingProfile).environmentObject(model)
            }
            .alert("profile.delete.title", isPresented: Binding(
                get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } })) {
                    Button("common.delete", role: .destructive) {
                        if let id = pendingDeletion { model.deleteProfile(id) }
                        pendingDeletion = nil
                    }
                    Button("common.cancel", role: .cancel) { pendingDeletion = nil }
                } message: { Text("profile.delete.message") }
        }
    }
}

private struct ProfileEditorView: View {
    @EnvironmentObject private var model: SessionModel
    @Environment(\.dismiss) private var dismiss
    private let original: MixerProfile?
    @State private var name: String
    @State private var host: String
    @State private var wifiSSID: String
    @State private var returnSSID: String
    @State private var password = ""
    @State private var returnPassword = ""
    @State private var originalPassword = ""
    @State private var originalReturnPassword = ""
    @State private var loadedCredentials = false
    @State private var selectedControls: Set<String>

    init(profile: MixerProfile?) {
        original = profile
        _name = State(initialValue: profile?.name ?? "My XR18")
        _host = State(initialValue: profile?.host ?? "192.168.1.1")
        _wifiSSID = State(initialValue: profile?.wifiSSID ?? "")
        _returnSSID = State(initialValue: profile?.returnSSID ?? "")
        _selectedControls = State(initialValue: Set(profile?.controls ?? ["master"]))
    }

    private var valid: Bool {
        let address = host.trimmingCharacters(in: .whitespacesAndNewlines)
        return !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !address.isEmpty && !address.contains(where: \.isWhitespace) &&
            !address.contains("/") && !address.contains(":") && !selectedControls.isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                if let message = model.errorMessage {
                    Section { Text(message).foregroundStyle(.red) }
                }
                Section("profile.mixer") {
                    TextField("profile.name", text: $name).textContentType(.name)
                        .accessibilityIdentifier("profile.name")
                    TextField("profile.host", text: $host)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .keyboardType(.URL).accessibilityIdentifier("profile.host")
                }
                Section {
                    TextField("profile.ssid", text: $wifiSSID)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .accessibilityIdentifier("profile.wifiSSID")
                    SecureField("profile.password", text: $password).textContentType(.password)
                } header: { Text("profile.wifi") } footer: { Text("profile.wifi.help") }
                Section {
                    TextField("profile.returnSSID", text: $returnSSID)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .accessibilityIdentifier("profile.returnSSID")
                    SecureField("profile.password", text: $returnPassword).textContentType(.password)
                } header: { Text("profile.returnWifi") } footer: { Text("profile.returnWifi.help") }
                Section {
                    ForEach(ControlCatalog.all, id: \.id) { control in
                        Toggle(isOn: Binding(
                            get: { selectedControls.contains(control.id) },
                            set: { if $0 { selectedControls.insert(control.id) } else { selectedControls.remove(control.id) } })) {
                                PanelAppearance.title(control.id, fallback: control.title)
                            }
                            .toggleStyle(CheckboxToggleStyle())
                            .accessibilityIdentifier("control.\(control.id).selected")
                    }
                } header: { Text("profile.controls") } footer: { Text("profile.controls.help") }
            }
            .navigationTitle(Text(LocalizedStringKey(original == nil ? "profile.add" : "profile.edit")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("common.cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.save") { save() }.disabled(!valid)
                        .accessibilityIdentifier("profile.save")
                }
            }
            .onAppear {
                guard !loadedCredentials else { return }
                loadedCredentials = true
                if let original {
                    password = model.password(for: original, returning: false)
                    returnPassword = model.password(for: original, returning: true)
                    originalPassword = password
                    originalReturnPassword = returnPassword
                }
            }
        }
    }

    private func save() {
        let profile = MixerProfile(id: original?.id ?? UUID(),
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            host: host.trimmingCharacters(in: .whitespacesAndNewlines),
            wifiSSID: wifiSSID, returnSSID: returnSSID,
            controls: ControlCatalog.all.filter { selectedControls.contains($0.id) }.map(\.id))
        model.errorMessage = nil
        model.saveProfile(profile,
            password: password == originalPassword ? nil : password,
            returnPassword: returnPassword == originalReturnPassword ? nil : returnPassword)
        if model.errorMessage == nil { dismiss() }
    }
}

private struct CheckboxToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack {
                configuration.label.foregroundStyle(Color.primary)
                Spacer(minLength: 12)
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(configuration.isOn ? Color.accentColor : Color.secondary)
            }
            .frame(minHeight: 36).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(configuration.isOn ? [.isSelected] : [])
        .accessibilityValue(Text(LocalizedStringKey(configuration.isOn ? "control.selected" : "control.notSelected")))
    }
}
