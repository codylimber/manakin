package com.codylimber.fieldphenology.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.R
import com.codylimber.fieldphenology.ui.theme.Primary
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val emoji: String
)

private val pages = listOf(
    OnboardingPage(
        "Welcome to Manakin",
        "Your field companion for exploring species phenology — discover what's active near you, right now.",
        "\uD83D\uDC26"
    ),
    OnboardingPage(
        "Data Packs",
        "Go to the Datasets tab and tap + to download species data for any location and taxonomic group from iNaturalist.\n\nYou can combine multiple locations and taxa in one pack. Each pack contains phenology data showing when species are most likely to be observed.",
        "\uD83D\uDCE6"
    ),
    OnboardingPage(
        "Explore Species",
        "The Explore tab shows species sorted by likelihood. Use the Active/All toggle to filter.\n\nSwipe right on any species to add it to your targets. Long-press to share.",
        "\uD83D\uDCC5"
    ),
    OnboardingPage(
        "Targets & Tracking",
        "The Targets tab is your planning hub. Star species, find lifers, and discover species new to an area.\n\nConnect your iNaturalist username in Settings to unlock observation tracking.",
        "\u2B50"
    ),
    OnboardingPage(
        "Plan Ahead",
        "Use the date picker to see what will be active during a future trip. Pick a date range for multi-day planning.\n\nCompare locations from the menu to find species unique to different areas.",
        "\uD83D\uDDFA\uFE0F"
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit, isReplay: Boolean = false) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val item = pages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (page == 0) {
                    Image(
                        painter = painterResource(id = R.drawable.manakin_logo),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )
                } else {
                    Text(item.emoji, fontSize = 64.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    item.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    item.description,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Page indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            repeat(pages.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) Primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        if (pagerState.currentPage == pages.lastIndex) {
            Button(
                onClick = onComplete,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (isReplay) "Done" else "Get Started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onComplete) {
                    Text(if (isReplay) "Close" else "Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Next")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
