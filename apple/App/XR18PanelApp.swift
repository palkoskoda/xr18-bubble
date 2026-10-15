import SwiftUI

@main struct XR18PanelApp: App {
    @StateObject private var model = SessionModel()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            MixerRootView()
                .environmentObject(model)
                .environment(\.locale, model.language == "system" ? .current : Locale(identifier: model.language))
                .preferredColorScheme(model.theme == "dark" ? .dark : model.theme == "light" ? .light : nil)
                .task { model.startup() }
                .onChange(of: phase) { _, value in
                    if value == .background { model.suspend() }
                    if value == .active { model.resume() }
                }
        }
    }
}
