package com.codylimber.fieldphenology.data.generator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.codylimber.fieldphenology.MainActivity
import com.codylimber.fieldphenology.R
import com.codylimber.fieldphenology.data.api.DatasetSource
import com.codylimber.fieldphenology.data.api.GbifApiClient
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class JobState { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

data class GenerationJob(
    val id: Long,
    val params: GenerationParams,
    val state: JobState = JobState.QUEUED,
    val progress: GenerationProgress? = null,
    val error: String? = null
) {
    val isActive: Boolean get() = state == JobState.QUEUED || state == JobState.RUNNING
    val slug: String get() = GeneratorSupport.slug(params.groupName, params.placeName)
}

/**
 * Foreground service that works through a queue of pack downloads one at a time.
 *
 * All state lives in [jobs]; screens observe it rather than a single global "current params",
 * so several packs can be queued, cancelled individually, and watched from anywhere.
 */
class GenerationService : Service() {

    companion object {
        const val CHANNEL_ID = "manakin_generation"
        const val NOTIFICATION_ID = 2001
        private const val DONE_NOTIFICATION_BASE = 3000

        private val lock = Any()
        private val nextId = AtomicLong(1)

        private val _jobs = MutableStateFlow<List<GenerationJob>>(emptyList())
        /** Running, queued and finished (this session) jobs, in submission order. */
        val jobs: StateFlow<List<GenerationJob>> = _jobs

        /** True while any job is queued or running. */
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        /** Increments each time a pack finishes successfully; observe it to reload datasets. */
        private val _completedCount = MutableStateFlow(0)
        val completedCount: StateFlow<Int> = _completedCount

        /** Set when the worker loop owns the queue; guarded by [lock]. */
        private var workerActive = false

        /**
         * Adds a download to the queue and makes sure the service is running. A pack that is
         * already queued or running (same name + place) is not added twice.
         */
        fun enqueue(context: Context, params: GenerationParams): Long {
            val id: Long
            synchronized(lock) {
                val slug = GeneratorSupport.slug(params.groupName, params.placeName)
                _jobs.value.firstOrNull { it.isActive && it.slug == slug }?.let { return it.id }
                id = nextId.getAndIncrement()
                _jobs.update { it + GenerationJob(id = id, params = params) }
                _isRunning.value = true
            }
            context.startForegroundService(Intent(context, GenerationService::class.java))
            return id
        }

        /** Cancels a queued job, or stops the running one (the queue then moves on). */
        fun cancel(id: Long) {
            val wasRunning = synchronized(lock) {
                val job = _jobs.value.firstOrNull { it.id == id } ?: return
                when (job.state) {
                    JobState.QUEUED -> {
                        setState(id) { it.copy(state = JobState.CANCELLED) }
                        refreshRunning()
                        false
                    }
                    JobState.RUNNING -> true
                    else -> false
                }
            }
            if (wasRunning) runningJob?.cancel()
        }

        fun cancelAll() {
            val running = synchronized(lock) {
                _jobs.update { list ->
                    list.map { if (it.state == JobState.QUEUED) it.copy(state = JobState.CANCELLED) else it }
                }
                _jobs.value.firstOrNull { it.state == JobState.RUNNING }
            }
            if (running != null) runningJob?.cancel()
        }

        /** Re-queues a failed or cancelled job. */
        fun retry(context: Context, id: Long) {
            val job = _jobs.value.firstOrNull { it.id == id } ?: return
            if (job.isActive) return
            dismiss(id)
            enqueue(context, job.params)
        }

        /** Removes a finished job from the list. */
        fun dismiss(id: Long) = synchronized(lock) {
            _jobs.update { list -> list.filterNot { it.id == id && !it.isActive } }
        }

        fun clearFinished() = synchronized(lock) {
            _jobs.update { list -> list.filter { it.isActive } }
        }

        private fun setState(id: Long, transform: (GenerationJob) -> GenerationJob) {
            _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
        }

        private fun refreshRunning() {
            _isRunning.value = _jobs.value.any { it.isActive }
        }

        @Volatile private var runningJob: Job? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        goForeground(buildProgressNotification(null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Every startForegroundService() call must be answered with startForeground().
        goForeground(buildProgressNotification(_jobs.value.firstOrNull { it.state == JobState.RUNNING }))

        val startWorker = synchronized(lock) {
            if (workerActive) false else { workerActive = true; true }
        }
        if (startWorker) scope.launch { workLoop() }
        return START_NOT_STICKY
    }

    private suspend fun workLoop() {
        val apiClient = INatApiClient(MainActivity.sharedHttpClient)
        val generator = DatasetGenerator(apiClient, applicationContext)
        val gbifGenerator = GbifDatasetGenerator(
            GbifApiClient(MainActivity.sharedHttpClient), apiClient, applicationContext
        )

        while (true) {
            val job = synchronized(lock) {
                val next = _jobs.value.firstOrNull { it.state == JobState.QUEUED }
                if (next == null) {
                    workerActive = false
                    refreshRunning()
                } else {
                    setState(next.id) { it.copy(state = JobState.RUNNING, progress = null, error = null) }
                }
                next
            } ?: break

            val child = scope.launch { runJob(job, generator, gbifGenerator) }
            runningJob = child
            child.join()
            runningJob = null
            // A cancel that landed after runJob finished leaves nothing to clean up.
            synchronized(lock) {
                if (_jobs.value.firstOrNull { it.id == job.id }?.state == JobState.RUNNING) {
                    setState(job.id) { it.copy(state = JobState.CANCELLED) }
                }
            }
        }

        withContext(Dispatchers.Main) {
            // Only stop if no newer start request arrived; otherwise that request owns the service.
            ServiceCompat.stopForeground(this@GenerationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(lastStartId)
        }
    }

    private suspend fun runJob(
        job: GenerationJob,
        generator: DatasetGenerator,
        gbifGenerator: GbifDatasetGenerator
    ) {
        val params = job.params
        var lastNotified = 0L
        val onProgress: (GenerationProgress) -> Unit = { progress ->
            setState(job.id) { if (it.state == JobState.RUNNING) it.copy(progress = progress) else it }
            // Notification updates are rate limited by the system; ~2/s is plenty.
            val now = System.currentTimeMillis()
            if (now - lastNotified > 500) {
                lastNotified = now
                updateNotification(buildProgressNotification(_jobs.value.firstOrNull { it.id == job.id }))
            }
        }

        try {
            if (params.source == DatasetSource.GBIF) {
                gbifGenerator.generate(
                    areaGids = params.gbifAreaIds,
                    areaName = params.placeName,
                    taxonKeys = params.taxonIds,
                    taxonName = params.taxonName,
                    groupName = params.groupName,
                    minObs = params.minObs,
                    yearsBack = params.gbifYearsBack,
                    sampleSize = params.gbifSampleSize,
                    maxPhotos = params.maxPhotos,
                    months = params.months,
                    onProgress = onProgress
                )
            } else {
                generator.generate(
                    placeIds = params.placeIds,
                    placeName = params.placeName,
                    taxonIds = params.taxonIds,
                    taxonName = params.taxonName,
                    groupName = params.groupName,
                    minObs = params.minObs,
                    qualityGrade = params.qualityGrade,
                    maxPhotos = params.maxPhotos,
                    months = params.months,
                    onProgress = onProgress
                )
            }
            setState(job.id) { it.copy(state = JobState.DONE) }
            _completedCount.update { it + 1 }
            postResultNotification(job, success = true, message = "${params.groupName} is ready")
        } catch (e: CancellationException) {
            setState(job.id) { it.copy(state = JobState.CANCELLED) }
            // Don't rethrow: only this child was cancelled, and the loop should continue.
        } catch (e: Exception) {
            setState(job.id) { it.copy(state = JobState.FAILED, error = e.message ?: "Unknown error") }
            postResultNotification(job, success = false, message = e.message ?: "Download failed")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        synchronized(lock) {
            workerActive = false
            // The process may survive the service; don't leave jobs stuck as "running".
            _jobs.update { list ->
                list.map { if (it.isActive) it.copy(state = JobState.CANCELLED) else it }
            }
            refreshRunning()
        }
        super.onDestroy()
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Dataset Generation",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows progress while downloading species data" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildProgressNotification(job: GenerationJob?): Notification {
        val queued = _jobs.value.count { it.state == JobState.QUEUED }
        val progress = job?.progress
        val title = job?.let { "Downloading ${it.params.groupName}" } ?: "Preparing download…"
        val text = buildString {
            append(progress?.message ?: "Starting…")
            if (queued > 0) append(" · $queued queued")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (progress != null && progress.total > 0) {
                    setProgress(progress.total, progress.current.coerceAtMost(progress.total), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
    }

    private fun postResultNotification(job: GenerationJob, success: Boolean, message: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (success) "Pack downloaded" else "Pack download failed")
            .setContentText(message)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        updateNotification(n, DONE_NOTIFICATION_BASE + (job.id % 1000).toInt())
    }

    private fun updateNotification(notification: Notification, id: Int = NOTIFICATION_ID) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied: the download still runs, just without updates.
        }
    }
}
