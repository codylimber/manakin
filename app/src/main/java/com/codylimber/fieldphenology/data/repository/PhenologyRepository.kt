package com.codylimber.fieldphenology.data.repository

import android.content.Context
import android.util.Log
import com.codylimber.fieldphenology.data.model.Dataset
import com.codylimber.fieldphenology.data.model.Species
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class DatasetSource { ASSET, INTERNAL }

data class DatasetInfo(
    val key: String,
    val group: String,
    val placeName: String,
    val speciesCount: Int,
    val generatedAt: String,
    val source: DatasetSource,
    val sizeBytes: Long = 0,
    val months: List<Int> = emptyList()
) {
    val sizeDisplay: String get() {
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) "%.1f MB".format(mb)
        else "%.0f KB".format(sizeBytes / 1024.0)
    }
}

class PhenologyRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
    /**
     * Everything loaded from disk, published as one immutable snapshot. Readers on any thread
     * (UI, Default-dispatcher list building, widget, worker) always see a complete, consistent
     * set; a reload swaps in a new snapshot instead of clearing maps others are iterating.
     */
    private class Snapshot(
        // Keyed by "Group — Place" to avoid collisions
        val datasets: Map<String, Dataset>,
        val keyDirNames: Map<String, String>,
        val keySources: Map<String, DatasetSource>
    ) {
        val sortedKeys: List<String> = datasets.keys.sorted()
        /** taxonId -> (key, species): first dataset wins, matching the old lookup order. */
        val byTaxon: Map<Int, Pair<String, Species>> = buildMap {
            for ((key, ds) in datasets) for (sp in ds.species) {
                if (sp.taxonId !in this) put(sp.taxonId, key to sp)
            }
        }
    }

    @Volatile private var snapshot = Snapshot(emptyMap(), emptyMap(), emptyMap())
        set(value) { field = value; _version.value = _version.value + 1 }

    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Bumps whenever the loaded set of datasets changes; observe it instead of re-reading disk. */
    val version: kotlinx.coroutines.flow.StateFlow<Int> = _version
    private val datasets: Map<String, Dataset> get() = snapshot.datasets
    private val keyDirNames: Map<String, String> get() = snapshot.keyDirNames
    private val keySources: Map<String, DatasetSource> get() = snapshot.keySources
    private var hiddenKeys = mutableSetOf<String>()
    @Volatile private var loaded = false
    private val loadLock = Any()

    private val prefs get() = context.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)

    private fun datasetKey(group: String, placeName: String): String = "$group — $placeName"

    fun loadDatasets() = synchronized(loadLock) {
        if (loaded) return
        val datasets = LinkedHashMap<String, Dataset>()
        val keyDirNames = HashMap<String, String>()
        val keySources = HashMap<String, DatasetSource>()
        hiddenKeys = prefs.getStringSet("hidden_datasets", emptySet())!!.toMutableSet()

        // Load from assets
        val assetsDir = "datasets"
        val assetDirs = context.assets.list(assetsDir) ?: emptyArray()
        for (dir in assetDirs) {
            val jsonPath = "$assetsDir/$dir/dataset.json"
            try {
                val jsonStr = context.assets.open(jsonPath).bufferedReader().readText()
                val dataset = json.decodeFromString<Dataset>(jsonStr)
                val key = datasetKey(dataset.metadata.group, dataset.metadata.placeName)
                if (key !in hiddenKeys) {
                    datasets[key] = dataset
                    keyDirNames[key] = dir
                    keySources[key] = DatasetSource.ASSET
                }
                Log.d("PhenologyRepo", "Loaded asset '$key': ${dataset.species.size} species")
            } catch (e: Exception) {
                Log.e("PhenologyRepo", "Failed to load asset $jsonPath", e)
            }
        }

        // Load from internal storage
        val internalDir = File(context.filesDir, "datasets")
        if (internalDir.isDirectory) {
            for (dir in internalDir.listFiles() ?: emptyArray()) {
                if (!dir.isDirectory) continue
                val jsonFile = File(dir, "dataset.json")
                if (!jsonFile.exists()) continue
                try {
                    val jsonStr = jsonFile.readText()
                    val dataset = json.decodeFromString<Dataset>(jsonStr)
                    val key = datasetKey(dataset.metadata.group, dataset.metadata.placeName)
                    datasets[key] = dataset
                    keyDirNames[key] = dir.name
                    keySources[key] = DatasetSource.INTERNAL
                    Log.d("PhenologyRepo", "Loaded internal '$key': ${dataset.species.size} species")
                } catch (e: Exception) {
                    Log.e("PhenologyRepo", "Failed to load internal ${dir.name}", e)
                }
            }
        }

        snapshot = Snapshot(datasets, keyDirNames, keySources)
        loaded = true
    }

    fun reloadDatasets() = synchronized(loadLock) {
        loaded = false
        loadDatasets()
    }

    suspend fun loadDatasetsAsync() = withContext(Dispatchers.IO) {
        loadDatasets()
    }

    suspend fun reloadDatasetsAsync() = withContext(Dispatchers.IO) {
        reloadDatasets()
    }

    fun getKeys(): List<String> = snapshot.sortedKeys

    fun getDataset(key: String): Dataset? = datasets[key]

    fun getGroupName(key: String): String = datasets[key]?.metadata?.group ?: key

    fun getPlaceNameForKey(key: String): String = datasets[key]?.metadata?.placeName ?: ""

    fun getTaxonGroup(key: String): String {
        val meta = datasets[key]?.metadata ?: return ""
        return if (meta.taxonIds.isNotEmpty()) meta.taxonIds.sorted().joinToString(",")
        else meta.taxonName
    }

    fun getSpeciesForKey(key: String): List<Species> = datasets[key]?.species ?: emptyList()

    fun getSpeciesById(taxonId: Int): Species? = snapshot.byTaxon[taxonId]?.second

    fun getKeyForSpecies(taxonId: Int): String? = snapshot.byTaxon[taxonId]?.first

    fun getPhotoUri(key: String, filename: String): String {
        val snapshot = snapshot
        val keySources = snapshot.keySources
        val keyDirNames = snapshot.keyDirNames
        val source = keySources[key] ?: DatasetSource.ASSET
        val dir = keyDirNames[key] ?: ""
        return when (source) {
            DatasetSource.ASSET -> "file:///android_asset/datasets/$dir/photos/$filename"
            DatasetSource.INTERNAL -> File(context.filesDir, "datasets/$dir/photos/$filename").toURI().toString()
        }
    }

    fun getAllDatasets(): List<DatasetInfo> =
        datasets.entries.map { (key, ds) ->
            val source = keySources[key] ?: DatasetSource.ASSET
            val dir = keyDirNames[key] ?: ""
            val size = when (source) {
                DatasetSource.INTERNAL -> {
                    val dirFile = File(context.filesDir, "datasets/$dir")
                    dirFile.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
                DatasetSource.ASSET -> 0L  // can't easily measure asset size
            }
            DatasetInfo(key, ds.metadata.group, ds.metadata.placeName, ds.metadata.speciesCount,
                ds.metadata.generatedAt, source, size, ds.metadata.months)
        }

    fun deleteDataset(key: String) = synchronized(loadLock) {
        val current = snapshot
        val source = current.keySources[key]
        if (source == DatasetSource.INTERNAL) {
            val dir = current.keyDirNames[key] ?: return
            File(context.filesDir, "datasets/$dir").deleteRecursively()
        } else if (source == DatasetSource.ASSET) {
            // Hide bundled dataset
            hiddenKeys.add(key)
            prefs.edit().putStringSet("hidden_datasets", hiddenKeys).apply()
        }
        snapshot = Snapshot(current.datasets - key, current.keyDirNames - key, current.keySources - key)
    }

    fun exportDataset(key: String): java.io.File? {
        val source = keySources[key] ?: return null
        val dir = keyDirNames[key] ?: return null
        val sourceDir = when (source) {
            DatasetSource.INTERNAL -> File(context.filesDir, "datasets/$dir")
            DatasetSource.ASSET -> return null // Can't export bundled assets easily
        }
        if (!sourceDir.isDirectory) return null

        val exportsDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val zipFile = File(exportsDir, "$dir.manakin")
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zip ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(sourceDir).path
                zip.putNextEntry(java.util.zip.ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return zipFile
    }

    fun exportBundle(keys: List<String>, bundleName: String): java.io.File? {
        val exportsDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val zipFile = File(exportsDir, "$bundleName.manakin-bundle")
        try {
            java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zip ->
                for (key in keys) {
                    val source = keySources[key] ?: continue
                    val dir = keyDirNames[key] ?: continue
                    val sourceDir = when (source) {
                        DatasetSource.INTERNAL -> File(context.filesDir, "datasets/$dir")
                        DatasetSource.ASSET -> continue
                    }
                    if (!sourceDir.isDirectory) continue

                    sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val entryName = "$dir/${file.relativeTo(sourceDir).path}"
                        zip.putNextEntry(java.util.zip.ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            return zipFile
        } catch (e: Exception) {
            android.util.Log.e("PhenologyRepo", "Bundle export failed", e)
            return null
        }
    }

    fun importDataset(inputStream: java.io.InputStream): Boolean {
        val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
        return try {
            tempDir.mkdirs()

            java.util.zip.ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(tempDir, entry.name)
                    // Guard against zip slip (path traversal)
                    if (!outFile.canonicalPath.startsWith(tempDir.canonicalPath + File.separator) &&
                        outFile.canonicalPath != tempDir.canonicalPath) {
                        throw SecurityException("Zip entry outside target directory: ${entry.name}")
                    }
                    outFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Check if this is a single dataset or a bundle
            val topLevelJson = File(tempDir, "dataset.json")
            if (topLevelJson.exists()) {
                // Single dataset
                importSingleDataset(tempDir)
            } else {
                // Bundle — each subdirectory is a separate dataset
                var anySuccess = false
                tempDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                    if (File(subDir, "dataset.json").exists()) {
                        if (importSingleDataset(subDir)) anySuccess = true
                    }
                }
                tempDir.deleteRecursively()
                if (!anySuccess) return false
            }

            reloadDatasets()
            true
        } catch (e: Exception) {
            android.util.Log.e("PhenologyRepo", "Import failed", e)
            false
        } finally {
            // Decode failures used to leave the unzipped copy behind in cache.
            tempDir.deleteRecursively()
        }
    }

    private fun importSingleDataset(tempDir: File): Boolean {
        val jsonFile = File(tempDir, "dataset.json")
        if (!jsonFile.exists()) return false

        val dataset = json.decodeFromString<Dataset>(jsonFile.readText())
        val slug = com.codylimber.fieldphenology.data.generator.GeneratorSupport
            .slug(dataset.metadata.group, dataset.metadata.placeName)

        val destDir = File(context.filesDir, "datasets/$slug")
        if (destDir.exists()) destDir.deleteRecursively()
        tempDir.copyRecursively(destDir, overwrite = true)
        tempDir.deleteRecursively()
        return true
    }

    fun mergeAllDatasets(): String? {
        val allSpecies = mutableMapOf<Int, com.codylimber.fieldphenology.data.model.Species>()
        var placeName = ""
        var group = "All Species"

        for (key in getKeys()) {
            val ds = datasets[key] ?: continue
            if (placeName.isEmpty()) placeName = ds.metadata.placeName
            else if (!placeName.contains(ds.metadata.placeName)) placeName += ", ${ds.metadata.placeName}"
            for (sp in ds.species) {
                if (sp.taxonId !in allSpecies) allSpecies[sp.taxonId] = sp
            }
        }

        if (allSpecies.isEmpty()) return null

        val merged = Dataset(
            metadata = com.codylimber.fieldphenology.data.model.DatasetMetadata(
                placeName = placeName,
                placeId = datasets.values.firstOrNull()?.metadata?.placeId ?: 0,
                group = group,
                taxonName = "Merged",
                totalObs = allSpecies.values.sumOf { it.totalObs },
                speciesCount = allSpecies.size,
                generatedAt = java.time.Instant.now().toString()
            ),
            species = allSpecies.values.toList()
        )

        val slug = "all-species-merged"
        val outputDir = File(context.filesDir, "datasets/$slug")
        outputDir.mkdirs()

        val jsonStr = json.encodeToString(com.codylimber.fieldphenology.data.model.Dataset.serializer(), merged)
        File(outputDir, "dataset.json").writeText(jsonStr)

        // Copy photos from source datasets
        val photosDir = File(outputDir, "photos")
        photosDir.mkdirs()
        for (key in getKeys()) {
            val source = keySources[key] ?: continue
            val dir = keyDirNames[key] ?: continue
            when (source) {
                DatasetSource.INTERNAL -> {
                    val srcPhotos = File(context.filesDir, "datasets/$dir/photos")
                    if (srcPhotos.isDirectory) {
                        srcPhotos.listFiles()?.forEach { it.copyTo(File(photosDir, it.name), overwrite = true) }
                    }
                }
                DatasetSource.ASSET -> {
                    val assetPhotos = try { context.assets.list("datasets/$dir/photos") } catch (_: Exception) { null }
                    assetPhotos?.forEach { filename ->
                        try {
                            context.assets.open("datasets/$dir/photos/$filename").use { input ->
                                File(photosDir, filename).outputStream().use { output -> input.copyTo(output) }
                            }
                        } catch (_: Exception) { /* skip missing photo */ }
                    }
                }
            }
        }

        reloadDatasets()
        return "All Species — $placeName"
    }
}
