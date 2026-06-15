package org.rubenazo.conciertosfront.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import conciertosfront.shared.generated.resources.Res
import conciertosfront.shared.generated.resources.splashscreen
import org.jetbrains.compose.resources.painterResource
import org.rubenazo.conciertosfront.ui.theme.PureBlack

/**
 * Branded fallback for artist/venue photos. It reuses the sync splash artwork
 * and crops around the centered logo instead of showing generic icons.
 */
@Composable
fun BrandedImageFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(PureBlack),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.splashscreen),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
    }
}

/**
 * Async image that degrades to [BrandedImageFallback] both when [url] is missing
 * and when the remote load fails. Coil does not fall back on load errors by
 * default, so a null-only check (as used previously) left artist/concert photos
 * blank whenever their scraped URL 404'd or failed to fetch.
 */
@Composable
fun BrandedAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) {
        BrandedImageFallback(modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        error = { BrandedImageFallback(modifier = Modifier.fillMaxSize()) },
    )
}
