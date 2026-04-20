package com.codylimber.fieldphenology.ui.screens.speciesdetail

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SpeciesMapView(
    taxonId: Int,
    placeId: Int?,
    modifier: Modifier = Modifier
) {
    val url = buildString {
        append("https://www.inaturalist.org/observations?taxon_id=$taxonId")
        if (placeId != null) append("&place_id=$placeId")
        append("&subview=map")
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(url)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
    )
}
