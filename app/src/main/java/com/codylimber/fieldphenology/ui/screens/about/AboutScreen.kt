package com.codylimber.fieldphenology.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Manakin", color = Primary, fontWeight = FontWeight.Bold) },
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
            // App info
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Manakin", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Text("Version 1.2", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A field companion for exploring species phenology — " +
                        "what's active near you, right now.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
            }

            // Data attribution
            Text("Data Source", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Manakin uses data from iNaturalist (inaturalist.org), a joint initiative " +
                "of the California Academy of Sciences and the National Geographic Society. " +
                "iNaturalist is a community science platform where people share observations " +
                "of organisms from around the world.\n\n" +
                "All species data, observation counts, and phenology patterns are derived from " +
                "research-grade observations submitted by the iNaturalist community. " +
                "Manakin is not affiliated with or endorsed by iNaturalist.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.inaturalist.org")))
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Visit iNaturalist.org", color = Primary)
            }

            // Photo licensing
            Text("Photo Licensing", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Species photos are sourced from iNaturalist and are used under Creative Commons " +
                "licenses. Only photos with CC licenses (CC0, CC-BY, CC-BY-NC, CC-BY-SA, etc.) " +
                "are downloaded — photos marked \"All Rights Reserved\" are excluded.\n\n" +
                "Individual photo attributions are displayed on each photo. " +
                "The original photographers retain all rights to their images under the terms " +
                "of their chosen license.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // API usage
            Text("API Usage", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Manakin accesses the iNaturalist API v1 to fetch species data. " +
                "The app respects iNaturalist's rate limiting guidelines, spacing requests " +
                "at 2-second intervals to avoid overloading the service. " +
                "Data is cached locally after download so repeated access doesn't require " +
                "additional API calls.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // Open source note
            Text("Acknowledgments", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Species descriptions are sourced from Wikipedia and are available under " +
                "the Creative Commons Attribution-ShareAlike License.\n\n" +
                "Named after the manakin — a family of small, colorful birds known for " +
                "their elaborate courtship displays.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
