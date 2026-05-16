import SwiftUI

struct ContentView: View {
    @Environment(AppSettings.self) private var appSettings
    @Environment(ThemeManager.self) private var themeManager
    @Environment(PhenologyRepository.self) private var repository
    @State private var selectedTab: AppTab = .explore
    @State private var explorePath = NavigationPath()
    @State private var targetsPath = NavigationPath()
    @State private var datasetsPath = NavigationPath()
    @State private var settingsPath = NavigationPath()
    @State private var hasCompletedOnboarding = UserDefaults.standard.bool(forKey: "hasCompletedOnboarding")
    private let apiClient = INatApiClient()
    @State private var lifeListService: LifeListService?

    enum AppTab: String, CaseIterable {
        case explore, targets, datasets, settings

        var label: String {
            switch self {
            case .explore: return "Explore"
            case .targets: return "Targets"
            case .datasets: return "Datasets"
            case .settings: return "Settings"
            }
        }

        var icon: String {
            switch self {
            case .explore: return "magnifyingglass"
            case .targets: return "star"
            case .datasets: return "list.bullet"
            case .settings: return "gearshape"
            }
        }
    }

    var body: some View {
        if !hasCompletedOnboarding {
            OnboardingView(onComplete: {
                hasCompletedOnboarding = true
                UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
            }, isReplay: false)
        } else {
            let sharedLifeListService = lifeListService ?? {
                let s = LifeListService(apiClient: apiClient)
                Task { @MainActor in lifeListService = s }
                return s
            }()
            TabView(selection: $selectedTab) {
                NavigationStack(path: $explorePath) {
                    SpeciesListView(lifeListService: sharedLifeListService)
                        .navigationDestination(for: AppRoute.self) { route in
                            routeDestination(route)
                        }
                }
                .tag(AppTab.explore)
                .tabItem {
                    Label(AppTab.explore.label, systemImage: AppTab.explore.icon)
                }

                NavigationStack(path: $targetsPath) {
                    TargetsView(repository: repository, lifeListService: sharedLifeListService, onSpeciesClick: { taxonId in
                        targetsPath.append(AppRoute.speciesDetail(taxonId: taxonId))
                    })
                        .navigationDestination(for: AppRoute.self) { route in
                            routeDestination(route)
                        }
                }
                .tag(AppTab.targets)
                .tabItem {
                    Label(AppTab.targets.label, systemImage: AppTab.targets.icon)
                }

                NavigationStack(path: $datasetsPath) {
                    ManageDatasetsView(repository: repository, onAddDataset: {
                        datasetsPath.append(AppRoute.addDataset)
                    })
                        .navigationDestination(for: AppRoute.self) { route in
                            routeDestination(route)
                        }
                }
                .tag(AppTab.datasets)
                .tabItem {
                    Label(AppTab.datasets.label, systemImage: AppTab.datasets.icon)
                }

                NavigationStack(path: $settingsPath) {
                    SettingsView(lifeListService: sharedLifeListService, repository: repository)
                        .navigationDestination(for: AppRoute.self) { route in
                            routeDestination(route)
                        }
                }
                .tag(AppTab.settings)
                .tabItem {
                    Label(AppTab.settings.label, systemImage: AppTab.settings.icon)
                }
            }
            .tint(.appPrimary)
        }
    }

    @ViewBuilder
    private func routeDestination(_ route: AppRoute) -> some View {
        switch route {
        case .speciesDetail(let taxonId):
            SpeciesDetailView(taxonId: taxonId, repository: repository, lifeListService: lifeListService ?? LifeListService(apiClient: apiClient))
        case .addDataset:
            AddDatasetView(apiClient: apiClient, onGenerate: {
                navigateToRoute(.generating)
            }, repository: repository)
        case .generating:
            if let params = GenerationParams.current {
                GeneratingView(
                    params: params,
                    generator: DatasetGenerator(apiClient: apiClient),
                    repository: repository,
                    onDone: { popCurrentPath() },
                    onCancel: { popCurrentPath() }
                )
            } else {
                Text("No generation in progress").navigationTitle("Generating")
            }
        case .compare:
            CompareView(repository: repository, lifeListService: lifeListService ?? LifeListService(apiClient: apiClient), onSpeciesClick: { taxonId in
                navigateToRoute(.speciesDetail(taxonId: taxonId))
            })
        case .help:
            HelpView()
        case .about:
            AboutView()
        case .timeline:
            TimelineView(repository: repository, onSpeciesClick: { taxonId in
                navigateToRoute(.speciesDetail(taxonId: taxonId))
            })
        case .tripReport:
            TripReportView(repository: repository)
        }
    }

    private func navigateToRoute(_ route: AppRoute) {
        switch selectedTab {
        case .explore: explorePath.append(route)
        case .targets: targetsPath.append(route)
        case .datasets: datasetsPath.append(route)
        case .settings: settingsPath.append(route)
        }
    }

    private func popCurrentPath() {
        switch selectedTab {
        case .explore: if !explorePath.isEmpty { explorePath.removeLast() }
        case .targets: if !targetsPath.isEmpty { targetsPath.removeLast() }
        case .datasets: if !datasetsPath.isEmpty { datasetsPath.removeLast() }
        case .settings: if !settingsPath.isEmpty { settingsPath.removeLast() }
        }
    }
}
