package com.codylimber.fieldphenology.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.SharedPreferences
import com.codylimber.fieldphenology.data.api.TaxonResult
import com.codylimber.fieldphenology.data.model.SortMode
import org.json.JSONArray
import org.json.JSONObject

object AppSettings {
    var useScientificNames by mutableStateOf(false)
    var minActivityPercent by mutableStateOf(0)
    var defaultSortMode by mutableStateOf(SortMode.LIKELIHOOD)
    var favorites by mutableStateOf(setOf<Int>())
    // Favorite taxa the user wants to quickly load when building a new dataset
    // (e.g. Butterflies, a few plant genera). Distinct from `favorites`, which
    // is starred individual species within datasets.
    var favoriteTaxa by mutableStateOf(listOf<TaxonResult>())
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
        favoriteTaxa = parseFavoriteTaxa(prefs.getString("favorite_taxa", null))
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

    fun addFavoriteTaxon(taxon: TaxonResult) {
        if (favoriteTaxa.none { it.id == taxon.id }) {
            favoriteTaxa = favoriteTaxa + taxon
            saveFavoriteTaxa()
        }
    }

    fun removeFavoriteTaxon(taxonId: Int) {
        favoriteTaxa = favoriteTaxa.filter { it.id != taxonId }
        saveFavoriteTaxa()
    }

    private fun saveFavoriteTaxa() {
        val arr = JSONArray()
        favoriteTaxa.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("displayName", t.displayName)
                put("scientificName", t.scientificName)
                put("commonName", t.commonName)
                put("rank", t.rank)
            })
        }
        prefs?.edit()?.putString("favorite_taxa", arr.toString())?.apply()
    }

    private fun parseFavoriteTaxa(json: String?): List<TaxonResult> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TaxonResult(
                    id = o.getInt("id"),
                    displayName = o.optString("displayName"),
                    scientificName = o.optString("scientificName"),
                    commonName = o.optString("commonName"),
                    rank = o.optString("rank")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
