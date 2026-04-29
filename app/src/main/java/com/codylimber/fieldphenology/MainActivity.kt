package com.codylimber.fieldphenology

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.generator.DatasetGenerator
import com.codylimber.fieldphenology.data.model.SortMode
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.screens.main.MainScreen
import com.codylimber.fieldphenology.ui.screens.onboarding.OnboardingScreen
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.FieldPhenologyTheme
import com.codylimber.fieldphenology.ui.theme.Primary
import com.codylimber.fieldphenology.ui.theme.ThemeState
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("deeplink_route")?.let { route ->
            navController?.navigate(route)
        }
    }

    companion object {
        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Manakin/${BuildConfig.VERSION_NAME} (Android; github.com/codylimber/manakin)")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val widgetDeepLink = intent?.getStringExtra("deeplink_route")

        val repository = PhenologyRepository(applicationContext)
        val apiClient = INatApiClient(sharedHttpClient)
        val generator = DatasetGenerator(apiClient, applicationContext)
        val lifeListService = LifeListService(apiClient, applicationContext)

        val prefs = getSharedPreferences("manakin_prefs", MODE_PRIVATE)
        ThemeState.isDarkMode = prefs.getBoolean("dark_mode", true)
        AppSettings.init(prefs)
        AppSettings.useScientificNames = prefs.getBoolean("use_scientific_names", false)
        AppSettings.minActivityPercent = prefs.getInt("min_activity_percent", 0)
        AppSettings.defaultSortMode = try {
            SortMode.valueOf(prefs.getString("default_sort", SortMode.LIKELIHOOD.name) ?: SortMode.LIKELIHOOD.name)
        } catch (_: Exception) { SortMode.LIKELIHOOD }

        val hasCompletedOnboarding = prefs.getBoolean("onboarding_complete", false)

        setContent {
            FieldPhenologyTheme {
                var isLoaded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    repository.loadDatasetsAsync()
                    isLoaded = true
                }

                if (!isLoaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else if (!hasCompletedOnboarding) {
                    var showOnboarding by remember { mutableStateOf(true) }
                    if (showOnboarding) {
                        OnboardingScreen(onComplete = {
                            prefs.edit().putBoolean("onboarding_complete", true).apply()
                            showOnboarding = false
                        })
                    } else {
                        val nav = rememberNavController().also { navController = it }
                        MainScreen(nav, repository, apiClient, generator, lifeListService, initialRoute = widgetDeepLink)
                    }
                } else {
                    val nav = rememberNavController().also { navController = it }
                    MainScreen(nav, repository, apiClient, generator, lifeListService, initialRoute = widgetDeepLink)
                }
            }
        }
    }
}
