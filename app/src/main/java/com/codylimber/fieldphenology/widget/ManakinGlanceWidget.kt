package com.codylimber.fieldphenology.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.LocalContext
import androidx.glance.unit.ColorProvider
import com.codylimber.fieldphenology.MainActivity
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import java.time.LocalDate
import java.time.temporal.IsoFields

class ManakinGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)
        val widgetMode = prefs.getString("widget_mode", "top_active") ?: "top_active"

        val repository = PhenologyRepository(context)
        repository.loadDatasets()

        val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val lastWeek = if (currentWeek > 1) currentWeek - 1 else 53

        val widgetData = buildWidgetData(context, repository, widgetMode, currentWeek, lastWeek)

        provideContent {
            WidgetContent(widgetData, widgetMode)
        }
    }

    private fun buildWidgetData(
        context: Context,
        repository: PhenologyRepository,
        mode: String,
        currentWeek: Int,
        lastWeek: Int
    ): WidgetData {
        val prefs = context.getSharedPreferences("manakin_prefs", Context.MODE_PRIVATE)

        return when (mode) {
            "organism_of_day" -> buildOrganismOfDay(context, repository, currentWeek)
            "timeline" -> buildTimeline(context, repository, currentWeek, lastWeek)
            "targets" -> buildTargets(context, repository, currentWeek, prefs)
            else -> buildTopActive(context, repository, currentWeek)
        }
    }

    private fun buildOrganismOfDay(
        context: Context,
        repository: PhenologyRepository,
        currentWeek: Int
    ): WidgetData {
        data class Candidate(val species: Species, val key: String)

        val candidates = mutableListOf<Candidate>()
        for (key in repository.getKeys()) {
            for (sp in repository.getSpeciesForKey(key)) {
                val entry = sp.weekly.find { it.week == currentWeek }
                if (entry != null && entry.relAbundance >= 0.25f && entry.n > 0) {
                    candidates.add(Candidate(sp, key))
                }
            }
        }

        if (candidates.isEmpty()) {
            return WidgetData(mode = "organism_of_day", isEmpty = true)
        }

        val pick = candidates[LocalDate.now().dayOfYear % candidates.size]
        val species = pick.species
        val photo = WidgetImageLoader.loadSpeciesPhoto(
            context, repository, species, pick.key, 400, 300
        )
        val chart = PhenologyChartRenderer.render(species.weekly, currentWeek, 280, 40)
        val name = species.commonName.ifEmpty { species.scientificName }
        val status = SpeciesStatus.classify(species, currentWeek)
        val abundance = species.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f

        return WidgetData(
            mode = "organism_of_day",
            heroSpecies = WidgetSpeciesItem(
                name = name,
                scientificName = species.scientificName,
                taxonId = species.taxonId,
                abundance = abundance,
                status = status,
                photo = photo,
                chart = chart
            )
        )
    }

    private fun buildTopActive(
        context: Context,
        repository: PhenologyRepository,
        currentWeek: Int
    ): WidgetData {
        val seen = mutableSetOf<Int>()
        val items = mutableListOf<WidgetSpeciesItem>()

        for (key in repository.getKeys()) {
            for (sp in repository.getSpeciesForKey(key)) {
                if (sp.taxonId in seen) continue
                seen.add(sp.taxonId)
                val abundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                if (abundance > 0f) {
                    val thumbnail = WidgetImageLoader.loadCircularThumbnail(
                        context, repository, sp, key, 48
                    )
                    val activityBar = PhenologyChartRenderer.renderActivityBar(abundance, 100, 8)
                    items.add(
                        WidgetSpeciesItem(
                            name = sp.commonName.ifEmpty { sp.scientificName },
                            scientificName = sp.scientificName,
                            taxonId = sp.taxonId,
                            abundance = abundance,
                            status = SpeciesStatus.classify(sp, currentWeek),
                            photo = thumbnail,
                            chart = activityBar
                        )
                    )
                }
            }
        }

        items.sortByDescending { it.abundance }
        return WidgetData(mode = "top_active", speciesList = items.take(4), isEmpty = items.isEmpty())
    }

    private fun buildTimeline(
        context: Context,
        repository: PhenologyRepository,
        currentWeek: Int,
        lastWeek: Int
    ): WidgetData {
        val items = mutableListOf<WidgetSpeciesItem>()
        val seen = mutableSetOf<Int>()

        for (key in repository.getKeys()) {
            for (sp in repository.getSpeciesForKey(key)) {
                if (sp.taxonId in seen) continue
                seen.add(sp.taxonId)
                val thisA = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                val lastA = sp.weekly.find { it.week == lastWeek }?.relAbundance ?: 0f

                val event = when {
                    thisA >= 0.8f && lastA < 0.8f -> "peak"
                    thisA > 0f && lastA == 0f -> "active"
                    else -> null
                }

                if (event != null) {
                    val thumbnail = WidgetImageLoader.loadCircularThumbnail(
                        context, repository, sp, key, 48
                    )
                    val chart = PhenologyChartRenderer.render(sp.weekly, currentWeek, 120, 24)
                    items.add(
                        WidgetSpeciesItem(
                            name = sp.commonName.ifEmpty { sp.scientificName },
                            scientificName = sp.scientificName,
                            taxonId = sp.taxonId,
                            abundance = thisA,
                            status = if (event == "peak") SpeciesStatus.PEAK else SpeciesStatus.ACTIVE,
                            photo = thumbnail,
                            chart = chart,
                            eventLabel = if (event == "peak") "Now at peak" else "Newly active"
                        )
                    )
                }
            }
        }

        items.sortByDescending { it.abundance }
        return WidgetData(mode = "timeline", speciesList = items.take(4), isEmpty = items.isEmpty())
    }

    private fun buildTargets(
        context: Context,
        repository: PhenologyRepository,
        currentWeek: Int,
        prefs: android.content.SharedPreferences
    ): WidgetData {
        val favorites = prefs.getStringSet("favorites", emptySet())!!
            .mapNotNull { it.toIntOrNull() }.toSet()

        if (favorites.isEmpty()) {
            return WidgetData(mode = "targets", isEmpty = true)
        }

        val items = mutableListOf<WidgetSpeciesItem>()
        for (key in repository.getKeys()) {
            for (sp in repository.getSpeciesForKey(key)) {
                if (sp.taxonId !in favorites) continue
                val abundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                if (abundance > 0f) {
                    val thumbnail = WidgetImageLoader.loadCircularThumbnail(
                        context, repository, sp, key, 48
                    )
                    val activityBar = PhenologyChartRenderer.renderActivityBar(abundance, 100, 8)
                    items.add(
                        WidgetSpeciesItem(
                            name = sp.commonName.ifEmpty { sp.scientificName },
                            scientificName = sp.scientificName,
                            taxonId = sp.taxonId,
                            abundance = abundance,
                            status = SpeciesStatus.classify(sp, currentWeek),
                            photo = thumbnail,
                            chart = activityBar
                        )
                    )
                }
            }
        }

        items.sortByDescending { it.abundance }
        return WidgetData(mode = "targets", speciesList = items.take(4), isEmpty = items.isEmpty())
    }
}

// Data classes for passing widget state to composables

data class WidgetData(
    val mode: String,
    val heroSpecies: WidgetSpeciesItem? = null,
    val speciesList: List<WidgetSpeciesItem> = emptyList(),
    val isEmpty: Boolean = false
)

data class WidgetSpeciesItem(
    val name: String,
    val scientificName: String,
    val taxonId: Int,
    val abundance: Float,
    val status: SpeciesStatus,
    val photo: Bitmap? = null,
    val chart: Bitmap? = null,
    val eventLabel: String? = null
)

// Glance composables

@Composable
private fun WidgetContent(data: WidgetData, mode: String) {
    val isDark = android.content.res.Configuration.UI_MODE_NIGHT_YES ==
        (LocalContext.current.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
    val bgColor = ColorProvider(if (isDark) androidx.compose.ui.graphics.Color(0xFF1A1A1A) else androidx.compose.ui.graphics.Color(0xFFF5F5F5))
    val textColor = ColorProvider(if (isDark) androidx.compose.ui.graphics.Color(0xFFEEEEEE) else androidx.compose.ui.graphics.Color(0xFF1A1A1A))
    val subtextColor = ColorProvider(if (isDark) androidx.compose.ui.graphics.Color(0xFFAAAAAA) else androidx.compose.ui.graphics.Color(0xFF666666))
    val accentColor = ColorProvider(if (isDark) androidx.compose.ui.graphics.Color(0xFF66BB6A) else androidx.compose.ui.graphics.Color(0xFF4CAF50))

    val deepLinkRoute = when (mode) {
        "organism_of_day" -> "species_detail/${data.heroSpecies?.taxonId ?: 0}"
        "timeline" -> "timeline"
        "targets" -> "targets"
        else -> "species_list"
    }

    val launchIntent = Intent(LocalContext.current, MainActivity::class.java).apply {
        putExtra("deeplink_route", deepLinkRoute)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(bgColor)
            .clickable(actionStartActivity(launchIntent))
            .padding(12.dp)
    ) {
        if (data.isEmpty) {
            EmptyState(mode, textColor, subtextColor)
        } else when (mode) {
            "organism_of_day" -> OrganismOfDayLayout(data, textColor, subtextColor, accentColor)
            "timeline" -> ListLayout(data, "This Week", textColor, subtextColor, accentColor)
            "targets" -> ListLayout(data, "Active Targets", textColor, subtextColor, accentColor)
            else -> ListLayout(data, "Top Active", textColor, subtextColor, accentColor)
        }
    }
}

@Composable
private fun EmptyState(mode: String, textColor: ColorProvider, subtextColor: ColorProvider) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Manakin",
            style = TextStyle(color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = when (mode) {
                "targets" -> "No target species starred"
                else -> "No active species"
            },
            style = TextStyle(color = subtextColor, fontSize = 12.sp)
        )
    }
}

@Composable
private fun OrganismOfDayLayout(
    data: WidgetData,
    textColor: ColorProvider,
    subtextColor: ColorProvider,
    accentColor: ColorProvider
) {
    val species = data.heroSpecies ?: return

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Species of the Day",
                style = TextStyle(color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            val statusText = when (species.status) {
                SpeciesStatus.PEAK -> "\uD83D\uDD25 Peak"
                SpeciesStatus.ACTIVE -> "Active"
                SpeciesStatus.EARLY -> "Early"
                SpeciesStatus.LATE -> "Late"
                else -> ""
            }
            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = TextStyle(color = subtextColor, fontSize = 10.sp)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Photo + name area
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo
            if (species.photo != null) {
                Image(
                    provider = ImageProvider(species.photo),
                    contentDescription = species.name,
                    modifier = GlanceModifier.size(72.dp).cornerRadius(8.dp)
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
            }

            // Name + scientific name
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = species.name,
                    style = TextStyle(
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    maxLines = 2
                )
                if (species.name != species.scientificName) {
                    Text(
                        text = species.scientificName,
                        style = TextStyle(color = subtextColor, fontSize = 11.sp),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
                val pct = (species.abundance * 100).toInt()
                Text(
                    text = "${pct}% activity",
                    style = TextStyle(color = accentColor, fontSize = 11.sp)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Phenology chart
        if (species.chart != null) {
            Image(
                provider = ImageProvider(species.chart),
                contentDescription = "Phenology chart",
                modifier = GlanceModifier.fillMaxWidth().height(28.dp)
            )
        }
    }
}

@Composable
private fun ListLayout(
    data: WidgetData,
    title: String,
    textColor: ColorProvider,
    subtextColor: ColorProvider,
    accentColor: ColorProvider
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header
        Text(
            text = title,
            style = TextStyle(color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        )
        Spacer(modifier = GlanceModifier.height(6.dp))

        // Species list
        for ((index, item) in data.speciesList.withIndex()) {
            if (index > 0) Spacer(modifier = GlanceModifier.height(4.dp))
            SpeciesRow(item, textColor, subtextColor, accentColor)
        }
    }
}

@Composable
private fun SpeciesRow(
    item: WidgetSpeciesItem,
    textColor: ColorProvider,
    subtextColor: ColorProvider,
    accentColor: ColorProvider
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular thumbnail
        if (item.photo != null) {
            Image(
                provider = ImageProvider(item.photo),
                contentDescription = item.name,
                modifier = GlanceModifier.size(32.dp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
        }

        // Name + event label or activity bar
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.name,
                style = TextStyle(color = textColor, fontWeight = FontWeight.Medium, fontSize = 12.sp),
                maxLines = 1
            )
            if (item.eventLabel != null) {
                Text(
                    text = item.eventLabel,
                    style = TextStyle(color = subtextColor, fontSize = 10.sp)
                )
            }
        }

        // Activity bar or chart
        if (item.chart != null) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Image(
                provider = ImageProvider(item.chart),
                contentDescription = "Activity",
                modifier = GlanceModifier.width(56.dp).height(if (item.eventLabel != null) 16.dp else 6.dp)
            )
        }

        // Percentage
        Spacer(modifier = GlanceModifier.width(6.dp))
        val pct = (item.abundance * 100).toInt()
        Text(
            text = "${pct}%",
            style = TextStyle(
                color = if (item.status == SpeciesStatus.PEAK) accentColor else subtextColor,
                fontSize = 11.sp,
                fontWeight = if (item.status == SpeciesStatus.PEAK) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

// Widget receiver

class ManakinWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ManakinGlanceWidget()
}
