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
    val sizeBytes: Long = 0
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
    // Keyed by "Group — Place" to avoid collisions
    private val datasets = mutableMapOf<String, Dataset>()
    private val keyDirNames = mutableMapOf<String, String>()
    private val keySources = mutableMapOf<String, DatasetSource>()
    private var hiddenKeys = mutableSetOf<String>()
    private var loaded = false

    private val prefs get() = context.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)

    private fun datasetKey(group: String, placeName: String): String = "$group — $placeName"

    fun loadDatasets() {
        if (loaded) return
        datasets.clear()
        keyDirNames.clear()
        keySources.clear()
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

        loaded = true
    }

    fun reloadDatasets() {
        loaded = false
        loadDatasets()
    }

    suspend fun loadDatasetsAsync() = withContext(Dispatchers.IO) {
        loadDatasets()
    }

    suspend fun reloadDatasetsAsync() = withContext(Dispatchers.IO) {
        reloadDatasets()
    }

    fun getKeys(): List<String> = datasets.keys.sorted()

    fun getDataset(key: String): Dataset? = datasets[key]

    fun getGroupName(key: String): String = datasets[key]?.metadata?.group ?: key

    fun getPlaceNameForKey(key: String): String = datasets[key]?.metadata?.placeName ?: ""

    fun getTaxonGroup(key: String): String {
        val meta = datasets[key]?.metadata ?: return ""
        return if (meta.taxonIds.isNotEmpty()) meta.taxonIds.sorted().joinToString(",")
        else meta.taxonName
    }

    fun getSpeciesForKey(key: String): List<Species> = datasets[key]?.species ?: emptyList()

    fun getSpeciesById(taxonId: Int): Species? =
        datasets.values.flatMap { it.species }.find { it.taxonId == taxonId }

    fun getKeyForSpecies(taxonId: Int): String? =
        datasets.entries.find { (_, ds) -> ds.species.any { it.taxonId == taxonId } }?.key

    fun getPhotoUri(key: String, filename: String): String {
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
                ds.metadata.generatedAt, source, size)
        }

    fun deleteDataset(key: String) {
        val source = keySources[key]
        if (source == DatasetSource.INTERNAL) {
            val dir = keyDirNames[key] ?: return
            File(context.filesDir, "datasets/$dir").deleteRecursively()
        } else if (source == DatasetSource.ASSET) {
            // Hide bundled dataset
            hiddenKeys.add(key)
            prefs.edit().putStringSet("hidden_datasets", hiddenKeys).apply()
        }
        datasets.remove(key)
        keyDirNames.remove(key)
        keySources.remove(key)
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
        return try {
            val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
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
        }
    }

    private fun importSingleDataset(tempDir: File): Boolean {
        val jsonFile = File(tempDir, "dataset.json")
        if (!jsonFile.exists()) return false

        val dataset = json.decodeFromString<Dataset>(jsonFile.readText())
        val slug = "${dataset.metadata.group}-${dataset.metadata.placeName}".lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')

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
