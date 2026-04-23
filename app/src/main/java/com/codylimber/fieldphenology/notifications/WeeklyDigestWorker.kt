package com.codylimber.fieldphenology.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.codylimber.fieldphenology.R
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
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

        fun schedule(context: Context, hour: Int = 8) {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(1, TimeUnit.DAYS)
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
        val prefs = applicationContext.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)

        // Check if today is a selected digest day
        val digestDays = prefs.getStringSet("digest_days", null)?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: setOf(2) // default Monday
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val digestEnabled = prefs.getBoolean("weekly_digest_enabled", false)
        val targetNotifs = prefs.getBoolean("target_notifications_enabled", false)

        // If today isn't a digest day, only proceed for target notifications
        val sendDigest = digestEnabled && today in digestDays

        if (!sendDigest && !targetNotifs) return Result.success()

        val repository = PhenologyRepository(applicationContext)
        repository.loadDatasets()

        // Filter to selected datasets (empty = all)
        val notifKeys = prefs.getStringSet("notification_dataset_keys", emptySet()) ?: emptySet()
        val keys = if (notifKeys.isEmpty()) repository.getKeys() else repository.getKeys().filter { it in notifKeys }

        val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val lastWeek = if (currentWeek > 1) currentWeek - 1 else 53

        createChannel(applicationContext)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)

        // Weekly digest
        if (sendDigest) {
            val newlyActive = mutableListOf<String>()
            val peakSpecies = mutableListOf<String>()
            val seenIds = mutableSetOf<Int>()

            for (key in keys) {
                for (sp in repository.getSpeciesForKey(key)) {
                    if (!seenIds.add(sp.taxonId)) continue
                    val thisAbundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                    val lastAbundance = sp.weekly.find { it.week == lastWeek }?.relAbundance ?: 0f
                    val name = sp.commonName.ifEmpty { sp.scientificName }

                    if (thisAbundance > 0f && lastAbundance == 0f) {
                        newlyActive.add(name)
                    }
                    if (thisAbundance >= 0.8f && lastAbundance < 0.8f) {
                        peakSpecies.add(name)
                    }
                }
            }

            if (newlyActive.isNotEmpty() || peakSpecies.isNotEmpty()) {
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
                    .setContentTitle("Manakin Weekly Digest")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .build()
                manager.notify(1001, notification)
            }
        }

        // Target species notification (runs daily regardless of digest day)
        if (targetNotifs) {
            val favorites = prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
            if (favorites.isNotEmpty()) {
                val targetApproaching = mutableListOf<String>()
                val targetAtPeak = mutableListOf<String>()
                val seenIds = mutableSetOf<Int>()

                for (key in keys) {
                    for (sp in repository.getSpeciesForKey(key)) {
                        if (sp.taxonId !in favorites) continue
                        if (!seenIds.add(sp.taxonId)) continue
                        val name = sp.commonName.ifEmpty { sp.scientificName }
                        if (sp.peakWeek == currentWeek) {
                            targetAtPeak.add(name)
                        } else if (sp.peakWeek == currentWeek + 2) {
                            targetApproaching.add(name)
                        }
                    }
                }

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
            }
        }

        return Result.success()
    }
}
