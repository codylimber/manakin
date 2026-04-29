package com.codylimber.fieldphenology.ui.screens.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.ui.theme.LocalBottomPadding
import com.codylimber.fieldphenology.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onReplayTutorial: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to Use Manakin", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                onClick = onReplayTutorial,
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Primary)
                    Column {
                        Text("Replay Tutorial", fontWeight = FontWeight.SemiBold, color = Primary)
                        Text("Walk through the app basics again", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HelpSection(
                "What is Manakin?",
                "Manakin helps you explore what species are active near you right now. " +
                "It uses community science data from iNaturalist to show phenology — " +
                "when species are most likely to be observed throughout the year. " +
                "Think of it like Merlin for all of nature, not just birds."
            )

            HelpSection(
                "Navigation",
                "Manakin has four main tabs at the bottom:\n\n" +
                "\u2022 Explore — browse species sorted by activity, search, and filter\n" +
                "\u2022 Targets — your starred species, lifers, and new-for-area targets\n" +
                "\u2022 Datasets — manage your downloaded data packs\n" +
                "\u2022 Settings — appearance, notifications, and iNaturalist account"
            )

            HelpSection(
                "Data Packs",
                "Manakin works with data packs — collections of species data for a specific " +
                "taxonomic group in a specific location.\n\n" +
                "To add a new data pack, go to the Datasets tab and tap the green \"+\" button:\n" +
                "1. Search for one or more locations (e.g., \"Connecticut\", \"Colorado\")\n" +
                "2. Search for one or more taxa (e.g., \"Butterflies\", \"Amphibians\", \"Snakes\")\n" +
                "3. Give it a label (e.g., \"Herps\", \"Trip Species\")\n" +
                "4. Set minimum observations — higher values give fewer but more reliable species\n" +
                "5. Tap Generate and wait — this fetches data from iNaturalist\n\n" +
                "You can have multiple packs and select them from the dataset selector " +
                "at the top of the Explore and Targets tabs. Tap a row to switch, " +
                "or tap the checkbox to view multiple packs at once."
            )

            HelpSection(
                "Explore Tab",
                "The main species list. Use the Active/All toggle in the top bar to show " +
                "only currently active species or everything.\n\n" +
                "Each species card shows:\n" +
                "\u2022 A blue checkmark after the name if you've observed it\n" +
                "\u2022 A colored rarity dot (green = common, orange = uncommon, red = rare)\n" +
                "\u2022 A status badge: Peak (amber), Active (green), Early/Late (blue), Inactive (gray)\n" +
                "\u2022 Activity percentage relative to peak\n" +
                "\u2022 A mini bar chart showing the full year's phenology\n\n" +
                "Swipe right on any card to add it to your targets.\n" +
                "Long-press a card to share the iNaturalist link.\n\n" +
                "Sort by Likelihood (combines activity + observation frequency), Peak Date, Name, or Taxonomy. " +
                "Taxonomy sort shows group headers that adapt to your dataset."
            )

            HelpSection(
                "Targets Tab",
                "Your species planning hub with three modes:\n\n" +
                "\u2022 Starred — species you've manually starred by swiping right\n" +
                "\u2022 New for Area — active species you haven't observed in the dataset's location\n" +
                "\u2022 Lifer Targets — active species you've never observed anywhere\n\n" +
                "Use the Active/All toggle and dataset selector to filter. " +
                "Swipe left on starred species to remove them.\n\n" +
                "The New for Area and Lifer Targets modes require connecting your " +
                "iNaturalist account in Settings."
            )

            HelpSection(
                "Date Picker & Trip Planning",
                "Tap \"Today\" in the Explore tab to pick a different date or date range. " +
                "The species list recalculates to show what will be active during that period.\n\n" +
                "For trip planning:\n" +
                "1. Pick a date range for your trip\n" +
                "2. Switch to the Targets tab > Lifer Targets or New for Area\n" +
                "3. Select the dataset for your destination\n" +
                "4. See exactly what you could find!"
            )

            HelpSection(
                "Observation Tracking",
                "Connect your iNaturalist account to see what you've already observed:\n\n" +
                "1. Go to Settings\n" +
                "2. Enter your iNaturalist username\n" +
                "3. Tap \"Sync Observations\"\n\n" +
                "Once synced, you'll see blue checkmarks next to observed species " +
                "and observation counts in the Explore tab. " +
                "The Targets tab unlocks New for Area and Lifer Targets modes.\n\n" +
                "Your data is cached locally for offline use. Re-sync anytime to pick up new observations."
            )

            HelpSection(
                "Compare Locations",
                "Access from the three-dot menu in the Explore tab. " +
                "Pick two datasets to see species unique to each location and species in common. " +
                "Great for deciding where to go on a trip."
            )

            HelpSection(
                "Organism of the Day",
                "Tap the Manakin logo at the top of the Explore tab to discover a random " +
                "active species from your packs. Changes daily."
            )

            HelpSection(
                "Notifications",
                "Enable in Settings:\n\n" +
                "\u2022 Weekly Digest — a summary of species entering peak and newly active species\n" +
                "\u2022 Target Species Alerts — notifications when your starred species approach peak " +
                "(2 weeks before) and reach peak\n\n" +
                "Choose which day of the week to receive your digest."
            )

            HelpSection(
                "About the Data",
                "All data comes from iNaturalist (inaturalist.org), a community science platform. " +
                "Phenology is based on research-grade observations. " +
                "Likelihood combines current activity with overall observation frequency. " +
                "Rarity is relative to each dataset (bottom 25% = Rare, middle 50% = Uncommon, top 25% = Common). " +
                "Only Creative Commons licensed photos are included."
            )

            Spacer(modifier = Modifier.height(LocalBottomPadding.current))
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(body, fontSize = 14.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
