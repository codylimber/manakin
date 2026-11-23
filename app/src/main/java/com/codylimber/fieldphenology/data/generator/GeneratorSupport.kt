package com.codylimber.fieldphenology.data.generator

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Shared plumbing for the iNaturalist and GBIF generators. */
internal object GeneratorSupport {

    /** Photos come from a static file host, not the rate-limited API, so a few run at once. */
    const val PHOTO_CONCURRENCY = 4

    fun slug(groupName: String, placeName: String): String =
        "$groupName-$placeName".lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /**
     * Runs [build] against a scratch directory and only swaps it in as `datasets/<slug>` once
     * it succeeds. Updating an existing pack used to write straight into the live folder, so a
     * failed or cancelled update left the old dataset.json pointing at half-replaced photos.
     */
    suspend fun buildAtomically(
        context: Context,
        slug: String,
        build: suspend (workDir: File) -> Unit
    ): String {
        val workDir = File(context.filesDir, "generating/$slug")
        workDir.deleteRecursively()
        File(workDir, "photos").mkdirs()
        try {
            build(workDir)
            val finalDir = File(context.filesDir, "datasets/$slug")
            finalDir.parentFile?.mkdirs()
            if (finalDir.exists() && !finalDir.deleteRecursively()) {
                throw RuntimeException("Could not replace the existing pack")
            }
            if (!workDir.renameTo(finalDir)) {
                // Rename can fail across mount points; fall back to a copy.
                workDir.copyRecursively(finalDir, overwrite = true)
                workDir.deleteRecursively()
            }
            return finalDir.absolutePath
        } catch (e: Throwable) {
            workDir.deleteRecursively()
            throw e
        }
    }

    /**
     * Resolves and downloads photos for every item with bounded concurrency. [resolve] may hit
     * the API (the client's own throttle keeps that within rate limits); downloads overlap.
     * Returns results in input order. [onEach] receives (completed, total) after each item.
     */
    suspend fun <T, R> forEachConcurrently(
        items: List<T>,
        concurrency: Int = PHOTO_CONCURRENCY,
        onEach: (done: Int, total: Int, item: T) -> Unit,
        work: suspend (T) -> R
    ): List<R> = coroutineScope {
        val sem = Semaphore(concurrency)
        val done = AtomicInteger(0)
        items.map { item ->
            async {
                val r = sem.withPermit { work(item) }
                onEach(done.incrementAndGet(), items.size, item)
                r
            }
        }.awaitAll()
    }
}
