package com.codylimber.fieldphenology.ui.screens.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.generator.DatasetGenerator
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import com.codylimber.fieldphenology.ui.navigation.Routes
import com.codylimber.fieldphenology.ui.screens.about.AboutScreen
import com.codylimber.fieldphenology.ui.screens.adddataset.AddDatasetScreen
import com.codylimber.fieldphenology.ui.screens.compare.CompareScreen
import com.codylimber.fieldphenology.ui.screens.generating.GeneratingScreen
import com.codylimber.fieldphenology.ui.screens.help.HelpScreen
import com.codylimber.fieldphenology.ui.screens.managedatasets.ManageDatasetsScreen
import com.codylimber.fieldphenology.ui.screens.timeline.TimelineScreen
import com.codylimber.fieldphenology.ui.screens.tripreport.TripReportScreen
import com.codylimber.fieldphenology.ui.screens.settings.SettingsScreen
import com.codylimber.fieldphenology.ui.screens.speciesdetail.SpeciesDetailScreen
import com.codylimber.fieldphenology.ui.screens.specieslist.SpeciesListScreen
import com.codylimber.fieldphenology.ui.screens.targets.TargetsScreen
import com.codylimber.fieldphenology.ui.theme.Primary

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    EXPLORE(Routes.SPECIES_LIST, "Explore", Icons.Default.Search),
    TARGETS(Routes.TARGETS, "Targets", Icons.Default.Star),
    DATASETS(Routes.MANAGE_DATASETS, "Datasets", Icons.AutoMirrored.Filled.List),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    navController: NavHostController,
    repository: PhenologyRepository,
    apiClient: INatApiClient,
    generator: DatasetGenerator,
    lifeListService: LifeListService
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Only show bottom bar on tab-level routes
    val tabRoutes = Tab.entries.map { it.route }
    val showBottomBar = currentRoute in tabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.SPECIES_LIST) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary,
                                selectedTextColor = Primary,
                                indicatorColor = Primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPECIES_LIST,
            modifier = Modifier
        ) {
            // Tab screens
            composable(Routes.SPECIES_LIST) {
                val menuCallbacks = menuNavCallbacks(navController)
                SpeciesListScreen(
                    repository = repository,
                    onSpeciesClick = { navController.navigate(Routes.speciesDetail(it)) },
                    onTimeline = menuCallbacks.onTimeline,
                    onTripReport = menuCallbacks.onTripReport,
                    onCompare = menuCallbacks.onCompare,
                    onHelp = menuCallbacks.onHelp,
                    onAbout = menuCallbacks.onAbout,
                    lifeListService = lifeListService
                )
            }
            composable(Routes.TARGETS) {
                val mc = menuNavCallbacks(navController)
                TargetsScreen(
                    repository = repository,
                    lifeListService = lifeListService,
                    onSpeciesClick = { navController.navigate(Routes.speciesDetail(it)) },
                    onTimeline = mc.onTimeline, onTripReport = mc.onTripReport,
                    onCompare = mc.onCompare, onHelp = mc.onHelp, onAbout = mc.onAbout
                )
            }
            composable(Routes.MANAGE_DATASETS) {
                val mc = menuNavCallbacks(navController)
                ManageDatasetsScreen(
                    repository = repository,
                    onAddDataset = { navController.navigate(Routes.ADD_DATASET) },
                    onUpdateDataset = { placeId, placeName, groupName ->
                        com.codylimber.fieldphenology.ui.navigation.GenerationParams.current =
                            com.codylimber.fieldphenology.ui.navigation.GenerationParams(
                                placeIds = listOf(placeId),
                                placeName = placeName,
                                taxonIds = listOf(null),
                                taxonName = "All Species",
                                groupName = groupName,
                                minObs = 1
                            )
                        navController.navigate(Routes.GENERATING)
                    },
                    onTimeline = mc.onTimeline, onTripReport = mc.onTripReport,
                    onCompare = mc.onCompare, onHelp = mc.onHelp, onAbout = mc.onAbout
                )
            }
            composable(Routes.SETTINGS) {
                val mc = menuNavCallbacks(navController)
                SettingsScreen(
                    lifeListService = lifeListService,
                    repository = repository,
                    onTimeline = mc.onTimeline, onTripReport = mc.onTripReport,
                    onCompare = mc.onCompare, onHelp = mc.onHelp, onAbout = mc.onAbout
                )
            }

            // Push screens (hide bottom bar)
            composable(
                route = Routes.SPECIES_DETAIL,
                arguments = listOf(navArgument("taxonId") { type = NavType.IntType })
            ) { backStackEntry ->
                val taxonId = backStackEntry.arguments?.getInt("taxonId") ?: return@composable
                SpeciesDetailScreen(
                    taxonId = taxonId,
                    repository = repository,
                    lifeListService = lifeListService,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.ADD_DATASET) {
                AddDatasetScreen(
                    apiClient = apiClient,
                    onBack = { navController.popBackStack() },
                    onGenerate = {
                        navController.navigate(Routes.GENERATING) {
                            popUpTo(Routes.ADD_DATASET) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.GENERATING) {
                val params = GenerationParams.current
                if (params != null) {
                    GeneratingScreen(
                        params = params,
                        generator = generator,
                        repository = repository,
                        onDone = {
                            GenerationParams.current = null
                            navController.popBackStack(Routes.SPECIES_LIST, inclusive = false)
                        },
                        onCancel = {
                            GenerationParams.current = null
                            navController.popBackStack(Routes.SPECIES_LIST, inclusive = false)
                        }
                    )
                }
            }
            composable(Routes.COMPARE) {
                CompareScreen(
                    repository = repository,
                    lifeListService = lifeListService,
                    onBack = { navController.popBackStack() },
                    onSpeciesClick = { navController.navigate(Routes.speciesDetail(it)) }
                )
            }
            composable(Routes.HELP) {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TIMELINE) {
                TimelineScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onSpeciesClick = { navController.navigate(Routes.speciesDetail(it)) }
                )
            }
            composable(Routes.TRIP_REPORT) {
                TripReportScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private data class MenuCallbacks(
    val onTimeline: () -> Unit,
    val onTripReport: () -> Unit,
    val onCompare: () -> Unit,
    val onHelp: () -> Unit,
    val onAbout: () -> Unit
)

private fun menuNavCallbacks(navController: NavHostController) = MenuCallbacks(
    onTimeline = { navController.navigate(Routes.TIMELINE) },
    onTripReport = { navController.navigate(Routes.TRIP_REPORT) },
    onCompare = { navController.navigate(Routes.COMPARE) },
    onHelp = { navController.navigate(Routes.HELP) },
    onAbout = { navController.navigate(Routes.ABOUT) }
)
