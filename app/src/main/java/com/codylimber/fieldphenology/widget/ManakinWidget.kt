package com.codylimber.fieldphenology.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.codylimber.fieldphenology.R
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import java.time.LocalDate
import java.time.temporal.IsoFields

class ManakinWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)
            val widgetMode = prefs.getString("widget_mode", "top_active") ?: "top_active"

            val repository = PhenologyRepository(context)
            repository.loadDatasets()

            val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val lastWeek = if (currentWeek > 1) currentWeek - 1 else 53

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widget_title, "Manakin")

            val content = when (widgetMode) {
                "organism_of_day" -> {
                    val candidates = mutableListOf<String>()
                    for (key in repository.getKeys()) {
                        for (sp in repository.getSpeciesForKey(key)) {
                            val entry = sp.weekly.find { it.week == currentWeek }
                            if (entry != null && entry.relAbundance >= 0.25f && entry.n > 0) {
                                candidates.add(sp.commonName.ifEmpty { sp.scientificName })
                            }
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        val pick = candidates[LocalDate.now().dayOfYear % candidates.size]
                        "Species of the Day:\n$pick"
                    } else "No active species"
                }
                "timeline" -> {
                    val events = mutableListOf<String>()
                    for (key in repository.getKeys()) {
                        for (sp in repository.getSpeciesForKey(key)) {
                            val thisA = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                            val lastA = sp.weekly.find { it.week == lastWeek }?.relAbundance ?: 0f
                            val name = sp.commonName.ifEmpty { sp.scientificName }
                            if (thisA >= 0.8f && lastA < 0.8f) events.add("\uD83D\uDD25 $name at peak")
                            else if (thisA > 0f && lastA == 0f) events.add("\uD83C\uDF31 $name now active")
                        }
                    }
                    if (events.isNotEmpty()) events.take(4).joinToString("\n")
                    else "No changes this week"
                }
                "targets" -> {
                    val favorites = prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
                    if (favorites.isEmpty()) "No target species starred"
                    else {
                        val targets = mutableListOf<Pair<String, Float>>()
                        for (key in repository.getKeys()) {
                            for (sp in repository.getSpeciesForKey(key)) {
                                if (sp.taxonId !in favorites) continue
                                val abundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                                if (abundance > 0f) {
                                    val name = sp.commonName.ifEmpty { sp.scientificName }
                                    val pct = (abundance * 100).toInt()
                                    targets.add("\u2B50 $name ($pct%)" to abundance)
                                }
                            }
                        }
                        if (targets.isNotEmpty()) {
                            targets.sortByDescending { it.second }
                            "Active targets:\n${targets.take(4).joinToString("\n") { it.first }}"
                        } else "No targets active this week"
                    }
                }
                else -> { // top_active
                    val species = mutableListOf<Pair<String, Float>>()
                    val seen = mutableSetOf<Int>()
                    for (key in repository.getKeys()) {
                        for (sp in repository.getSpeciesForKey(key)) {
                            if (sp.taxonId in seen) continue
                            seen.add(sp.taxonId)
                            val abundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                            if (abundance > 0f) {
                                val name = sp.commonName.ifEmpty { sp.scientificName }
                                val status = SpeciesStatus.classify(sp, currentWeek)
                                val statusLabel = when (status) {
                                    SpeciesStatus.PEAK -> "\uD83D\uDD25"
                                    SpeciesStatus.ACTIVE -> "\u2705"
                                    else -> "\u25CB"
                                }
                                species.add("$statusLabel $name" to abundance)
                            }
                        }
                    }
                    species.sortByDescending { it.second }
                    if (species.isNotEmpty()) species.take(5).joinToString("\n") { it.first }
                    else "No active species"
                }
            }

            views.setTextViewText(R.id.widget_content, content)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
