package com.codylimber.fieldphenology.data.api

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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

    suspend fun refreshForDataset(
        datasetKey: String,
        taxonId: Int?,
        placeId: Int
    ) {
        val user = username
        if (user.isBlank()) return

        Log.d("LifeList", "Refreshing for '$datasetKey', user='$user'")

        // Fetch global (seen anywhere)
        val globalIds = apiClient.getUserSpeciesTaxonIds(user, taxonId, placeId = null)
        saveCachedIds(datasetKey, "global", globalIds)
        Log.d("LifeList", "Global: ${globalIds.size} species observed")

        // Fetch local (seen in this place)
        val localIds = apiClient.getUserSpeciesTaxonIds(user, taxonId, placeId)
        saveCachedIds(datasetKey, "local", localIds)
        Log.d("LifeList", "Local ($placeId): ${localIds.size} species observed")

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

    private fun cacheFile(datasetKey: String, scope: String): File {
        val slug = datasetKey.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return File(cacheDir, "${slug}_$scope.json")
    }

    private fun saveCachedIds(datasetKey: String, scope: String, ids: Set<Int>) {
        val file = cacheFile(datasetKey, scope)
        file.writeText(json.encodeToString(ids.toList()))
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
