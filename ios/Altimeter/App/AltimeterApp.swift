import SwiftUI

@main
@MainActor
struct AltimeterApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            AltimeterRootView()
                .environmentObject(model)
                .environment(\.locale, model.appLanguage.locale)
                .preferredColorScheme(model.darkTheme ? .dark : .light)
        }
    }
}

