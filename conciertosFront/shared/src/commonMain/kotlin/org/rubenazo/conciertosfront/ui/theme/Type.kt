package org.rubenazo.conciertosfront.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import conciertosfront.shared.generated.resources.Res
import conciertosfront.shared.generated.resources.sora_extrabold
import conciertosfront.shared.generated.resources.sora_bold
import conciertosfront.shared.generated.resources.hanken_grotesk_regular
import conciertosfront.shared.generated.resources.jetbrains_mono_medium

@Composable
fun SoraFamily() = FontFamily(
    Font(Res.font.sora_extrabold, FontWeight.ExtraBold),
    Font(Res.font.sora_bold, FontWeight.Bold),
)

@Composable
fun HankenGroteskFamily() = FontFamily(
    Font(Res.font.hanken_grotesk_regular, FontWeight.Normal),
)

@Composable
fun JetBrainsMonoFamily() = FontFamily(
    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
)

@Composable
fun VoltageTypography(): Typography {
    val sora = SoraFamily()
    val hanken = HankenGroteskFamily()
    val jetbrains = JetBrainsMonoFamily()

    return Typography(
        displayLarge = TextStyle(
            fontFamily = sora,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 48.sp,
            lineHeight = 48.sp,
            letterSpacing = TextUnit(-0.04f, TextUnitType.Em),
        ),
        headlineLarge = TextStyle(
            fontFamily = sora,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            letterSpacing = TextUnit(-0.02f, TextUnitType.Em),
        ),
        headlineMedium = TextStyle(
            fontFamily = sora,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = hanken,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            lineHeight = 28.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = hanken,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = jetbrains,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = sora,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        ),
    )
}
