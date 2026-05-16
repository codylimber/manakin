import SwiftUI

@main
struct ManakinApp: App {
    @State private var themeManager = ThemeManager.shared
    @State private var appSettings = AppSettings.shared
    @State private var repository: PhenologyRepository

    init() {
        let repo = PhenologyRepository()
        repo.loadDatasets()
        _repository = State(initialValue: repo)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(themeManager)
                .environment(appSettings)
                .environment(repository)
                .environment(\.appColors, themeManager.colors)
                .preferredColorScheme(themeManager.colorScheme)
        }
    }
}
