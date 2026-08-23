import SwiftUI

@main
@MainActor
struct AltimeterApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            AltimeterScreen()
                .environmentObject(model)
        }
    }
}

