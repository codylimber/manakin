package com.codylimber.fieldphenology.data.generator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codylimber.fieldphenology.R
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GenerationService : Service() {

    companion object {
        const val CHANNEL_ID = "manakin_generation"
        const val NOTIFICATION_ID = 2001

        private val _progress = MutableStateFlow<GenerationProgress?>(null)
        val progress: StateFlow<GenerationProgress?> = _progress

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _isComplete = MutableStateFlow(false)
        val isComplete: StateFlow<Boolean> = _isComplete

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private val _queue = mutableListOf<GenerationParams>()
        private val _queueSize = MutableStateFlow(0)
        val queueSize: StateFlow<Int> = _queueSize

        fun reset() {
            _progress.value = null
            _isRunning.value = false
            _isComplete.value = false
            _error.value = null
        }

        fun start(context: Context) {
            reset()
            val intent = Intent(context, GenerationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun enqueue(context: Context, params: GenerationParams) {
            if (_isRunning.value) {
                synchronized(_queue) {
                    _queue.add(params)
                    _queueSize.value = _queue.size
                }
            } else {
                GenerationParams.current = params
                start(context)
            }
        }

        internal fun dequeueNext(): GenerationParams? {
            synchronized(_queue) {
                val next = _queue.removeFirstOrNull()
                _queueSize.value = _queue.size
                return next
            }
        }

        fun stop(context: Context) {
            synchronized(_queue) {
                _queue.clear()
                _queueSize.value = 0
            }
            context.stopService(Intent(context, GenerationService::class.java))
        }
    }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val params = GenerationParams.current
        if (params == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        _isRunning.value = true

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Manakin/1.2 (Android; github.com/codylimber/manakin)")
                    .build()
                chain.proceed(request)
            }
            .build()

        val apiClient = INatApiClient(okHttpClient)
        val generator = DatasetGenerator(apiClient, applicationContext)

        job = scope.launch {
            var currentParams: GenerationParams = params
            while (true) {
                try {
                    _isComplete.value = false
                    _error.value = null
                    _progress.value = null

                    generator.generate(
                        placeIds = currentParams.placeIds,
                        placeName = currentParams.placeName,
                        taxonIds = currentParams.taxonIds,
                        taxonName = currentParams.taxonName,
                        groupName = currentParams.groupName,
                        minObs = currentParams.minObs,
                        qualityGrade = currentParams.qualityGrade,
                        maxPhotos = currentParams.maxPhotos,
                        onProgress = { progress ->
                            _progress.value = progress
                            updateNotification(progress.message)
                        }
                    )
                    _isComplete.value = true
                    updateNotification("Complete!")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _error.value = e.message ?: "Unknown error"
                    updateNotification("Error: ${e.message}")
                }

                val next = dequeueNext()
                if (next != null) {
                    currentParams = next
                    GenerationParams.current = next
                } else {
                    _isRunning.value = false
                    delay(2000)
                    break
                }
            }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Dataset Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows progress while downloading species data" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Generating Dataset")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
