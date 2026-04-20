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
    var digestDay by mutableStateOf(2) // Monday=2 in Calendar
    var digestHour by mutableStateOf(8)
    var targetNotificationsEnabled by mutableStateOf(false)

    private var prefs: SharedPreferences? = null

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        favorites = prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        weeklyDigestEnabled = prefs.getBoolean("weekly_digest_enabled", false)
        digestDay = prefs.getInt("digest_day", 2)
        digestHour = prefs.getInt("digest_hour", 8)
        targetNotificationsEnabled = prefs.getBoolean("target_notifications_enabled", false)
    }

    fun toggleFavorite(taxonId: Int) {
        favorites = if (taxonId in favorites) favorites - taxonId else favorites + taxonId
        prefs?.edit()?.putStringSet("favorites", favorites.map { it.toString() }.toSet())?.apply()
    }

    fun isFavorite(taxonId: Int): Boolean = taxonId in favorites
}
