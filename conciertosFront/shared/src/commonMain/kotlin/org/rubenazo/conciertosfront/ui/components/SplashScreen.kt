package org.rubenazo.conciertosfront.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import conciertosfront.shared.generated.resources.Res
import conciertosfront.shared.generated.resources.splashscreen
import org.jetbrains.compose.resources.painterResource
import org.rubenazo.conciertosfront.ui.theme.PureBlack

/**
 * Full-bleed splash that mirrors the branded splashscreen mockup. The brand
 * artwork (logo + "ConciertosCerca") is baked into the image; [bottomContent]
 * renders the live state (sync progress, errors) over the lower crowd area.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    bottomContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Image(
            painter = painterResource(Res.drawable.splashscreen),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            bottomContent()
        }
    }
}
