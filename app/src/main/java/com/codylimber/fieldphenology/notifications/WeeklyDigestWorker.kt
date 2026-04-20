package com.codylimber.fieldphenology.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.codylimber.fieldphenology.R
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.theme.AppSettings
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit

class WeeklyDigestWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val CHANNEL_ID = "manakin_weekly_digest"
        const val WORK_NAME = "weekly_digest"

        fun schedule(context: Context, dayOfWeek: Int = 1, hour: Int = 8) {
            // Calculate delay to next occurrence of the target day/hour
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_WEEK, dayOfWeek)
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) add(java.util.Calendar.WEEK_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = android.net.Uri.parse(
                    "android.resource://${context.packageName}/${R.raw.manakin_notification}"
                )
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Weekly Species Digest",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Weekly summary of newly active species"
                    setSound(soundUri, audioAttributes)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun doWork(): Result {
        val repository = PhenologyRepository(applicationContext)
        repository.loadDatasets()

        val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val lastWeek = if (currentWeek > 1) currentWeek - 1 else 53

        // Find species that are newly active this week (weren't active last week)
        val newlyActive = mutableListOf<String>()
        val peakSpecies = mutableListOf<String>()

        for (key in repository.getKeys()) {
            for (sp in repository.getSpeciesForKey(key)) {
                val thisWeekEntry = sp.weekly.find { it.week == currentWeek }
                val lastWeekEntry = sp.weekly.find { it.week == lastWeek }
                val thisAbundance = thisWeekEntry?.relAbundance ?: 0f
                val lastAbundance = lastWeekEntry?.relAbundance ?: 0f

                val name = sp.commonName.ifEmpty { sp.scientificName }

                if (thisAbundance > 0f && lastAbundance == 0f) {
                    newlyActive.add(name)
                }
                if (thisAbundance >= 0.8f && lastAbundance < 0.8f) {
                    peakSpecies.add(name)
                }
            }
        }

        // Check target species (favorites) approaching or at peak
        val prefs = applicationContext.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)
        val targetNotifs = prefs.getBoolean("target_notifications_enabled", false)
        val favorites = prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

        val targetApproaching = mutableListOf<String>() // 2 weeks before peak
        val targetAtPeak = mutableListOf<String>() // at peak week

        if (targetNotifs && favorites.isNotEmpty()) {
            for (key in repository.getKeys()) {
                for (sp in repository.getSpeciesForKey(key)) {
                    if (sp.taxonId !in favorites) continue
                    val name = sp.commonName.ifEmpty { sp.scientificName }
                    if (sp.peakWeek == currentWeek) {
                        targetAtPeak.add(name)
                    } else if (sp.peakWeek == currentWeek + 2) {
                        targetApproaching.add(name)
                    }
                }
            }
        }

        createChannel(applicationContext)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)

        // Weekly digest notification
        if (newlyActive.isNotEmpty() || peakSpecies.isNotEmpty()) {
            val title = "Manakin Weekly Digest"
            val body = buildString {
                if (peakSpecies.isNotEmpty()) {
                    append("Entering peak: ${peakSpecies.take(3).joinToString(", ")}")
                    if (peakSpecies.size > 3) append(" +${peakSpecies.size - 3} more")
                }
                if (newlyActive.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("Newly active: ${newlyActive.take(3).joinToString(", ")}")
                    if (newlyActive.size > 3) append(" +${newlyActive.size - 3} more")
                }
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            manager.notify(1001, notification)
        }

        // Target species notification
        if (targetAtPeak.isNotEmpty() || targetApproaching.isNotEmpty()) {
            val body = buildString {
                if (targetAtPeak.isNotEmpty()) {
                    append("At peak now: ${targetAtPeak.joinToString(", ")}")
                }
                if (targetApproaching.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("Peak in 2 weeks: ${targetApproaching.joinToString(", ")}")
                }
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("\u2605 Target Species Alert")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            manager.notify(1002, notification)
        }

        return Result.success()
    }
}
