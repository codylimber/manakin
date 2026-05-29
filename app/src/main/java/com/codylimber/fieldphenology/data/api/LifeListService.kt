package com.codylimber.fieldphenology.data.api

import android.content.Context
import android.util.Log
import com.codylimber.fieldphenology.data.model.LifeListEntry
import com.codylimber.fieldphenology.data.model.SavedLifeList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

class LifeListService(
    private val apiClient: INatApiClient,
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir get() = File(context.filesDir, "lifelist").also { it.mkdirs() }

    val prefs get() = context.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString("inat_username", "") ?: ""
        set(value) { prefs.edit().putString("inat_username", value).apply() }

    var observationScope: ObservationScope
        get() = ObservationScope.valueOf(
            prefs.getString("observation_scope", ObservationScope.ANYWHERE.name)
                ?: ObservationScope.ANYWHERE.name
        )
        set(value) { prefs.edit().putString("observation_scope", value.name).apply() }

    // --- Observation badge sync (for Explore/Targets tabs) ---

    suspend fun refreshForDataset(
        datasetKey: String,
        taxonId: Int?,
        placeId: Int
    ) {
        val user = username
        if (user.isBlank()) return
        Log.d("LifeList", "Refreshing for '$datasetKey', user='$user'")
        val globalIds = apiClient.getUserSpeciesTaxonIds(user, taxonId, placeId = null)
        saveCachedIds(datasetKey, "global", globalIds)
        val localIds = apiClient.getUserSpeciesTaxonIds(user, taxonId, placeId)
        saveCachedIds(datasetKey, "local", localIds)
        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
    }

    fun getObservedGlobal(datasetKey: String): Set<Int> = loadCachedIds(datasetKey, "global")
    fun getObservedLocal(datasetKey: String): Set<Int> = loadCachedIds(datasetKey, "local")

    fun getObservedForScope(datasetKey: String): Set<Int> =
        when (observationScope) {
            ObservationScope.ANYWHERE -> getObservedGlobal(datasetKey)
            ObservationScope.HERE -> getObservedLocal(datasetKey)
        }

    fun getLastSyncTime(): Long = prefs.getLong("last_sync_time", 0)
    fun hasUsername(): Boolean = username.isNotBlank()

    // --- Life List generation ---

    suspend fun generateLifeList(
        taxonId: Int,
        taxonName: String,
        onProgress: (fetched: Int, total: Int) -> Unit = { _, _ -> }
    ): SavedLifeList {
        val user = username
        if (user.isBlank()) throw IllegalStateException("No iNaturalist username set.")
        val entries = apiClient.getUserLifeListForTaxon(user, taxonId, onProgress)
        val lifeList = SavedLifeList(
            taxonId = taxonId,
            taxonName = taxonName,
            generatedAt = LocalDate.now().toString(),
            entries = entries
        )
        saveLifeList(lifeList)
        return lifeList
    }

    fun getSavedLifeLists(): List<SavedLifeList> {
        return cacheDir.listFiles { f -> f.name.endsWith("_lifelist.json") }
            ?.mapNotNull { f ->
                try { json.decodeFromString<SavedLifeList>(f.readText()) } catch (_: Exception) { null }
            } ?: emptyList()
    }

    fun getLifeList(taxonId: Int): SavedLifeList? {
        val file = lifeListFile(taxonId)
        if (!file.exists()) return null
        return try { json.decodeFromString<SavedLifeList>(file.readText()) } catch (_: Exception) { null }
    }

    fun deleteLifeList(taxonId: Int) {
        lifeListFile(taxonId).delete()
    }

    fun getLifeListFileSize(taxonId: Int): Long = lifeListFile(taxonId).length()

    // --- Internals ---

    private fun lifeListFile(taxonId: Int) = File(cacheDir, "${taxonId}_lifelist.json")

    private fun saveLifeList(lifeList: SavedLifeList) {
        lifeListFile(lifeList.taxonId).writeText(json.encodeToString(lifeList))
    }

    private fun cacheFile(datasetKey: String, scope: String): File {
        val slug = datasetKey.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return File(cacheDir, "${slug}_$scope.json")
    }

    private fun saveCachedIds(datasetKey: String, scope: String, ids: Set<Int>) {
        cacheFile(datasetKey, scope).writeText(json.encodeToString(ids.toList()))
    }

    private fun loadCachedIds(datasetKey: String, scope: String): Set<Int> {
        val file = cacheFile(datasetKey, scope)
        if (!file.exists()) return emptySet()
        return try {
            json.decodeFromString<List<Int>>(file.readText()).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}

enum class ObservationScope {
    ANYWHERE, HERE
}
