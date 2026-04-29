package com.codylimber.fieldphenology.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.SharedPreferences
import com.codylimber.fieldphenology.data.model.SortMode

object AppSettings {
    var useScientificNames by mutableStateOf(false)
    var minActivityPercent by mutableStateOf(0)
    var defaultSortMode by mutableStateOf(SortMode.LIKELIHOOD)
    var favorites by mutableStateOf(setOf<Int>())
    var selectedDatasetKeys by mutableStateOf(setOf<String>())
    var showActiveOnly by mutableStateOf(true)
    var targetMode by mutableStateOf("STARRED")
    var weeklyDigestEnabled by mutableStateOf(false)
    var digestDays by mutableStateOf(setOf(2)) // Calendar day constants: Sunday=1..Saturday=7
    var digestHour by mutableStateOf(8)
    var targetNotificationsEnabled by mutableStateOf(false)
    var notificationDatasetKeys by mutableStateOf(setOf<String>()) // empty = all datasets

    private var prefs: SharedPreferences? = null

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        favorites = prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        weeklyDigestEnabled = prefs.getBoolean("weekly_digest_enabled", false)
        // Migrate old single digestDay to digestDays set
        val oldDay = prefs.getInt("digest_day", -1)
        digestDays = prefs.getStringSet("digest_days", null)?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: if (oldDay >= 1) setOf(oldDay) else setOf(2)
        digestHour = prefs.getInt("digest_hour", 8)
        targetNotificationsEnabled = prefs.getBoolean("target_notifications_enabled", false)
        notificationDatasetKeys = prefs.getStringSet("notification_dataset_keys", emptySet()) ?: emptySet()
        selectedDatasetKeys = prefs.getStringSet("selected_dataset_keys", emptySet()) ?: emptySet()
        showActiveOnly = prefs.getBoolean("show_active_only", true)
        targetMode = prefs.getString("target_mode", "STARRED") ?: "STARRED"
    }

    fun saveSelectedDatasetKeys() {
        prefs?.edit()?.putStringSet("selected_dataset_keys", selectedDatasetKeys)?.apply()
    }

    fun saveShowActiveOnly() {
        prefs?.edit()?.putBoolean("show_active_only", showActiveOnly)?.apply()
    }

    fun saveTargetMode() {
        prefs?.edit()?.putString("target_mode", targetMode)?.apply()
    }

    fun toggleFavorite(taxonId: Int) {
        favorites = if (taxonId in favorites) favorites - taxonId else favorites + taxonId
        prefs?.edit()?.putStringSet("favorites", favorites.map { it.toString() }.toSet())?.apply()
    }

    fun isFavorite(taxonId: Int): Boolean = taxonId in favorites
}
