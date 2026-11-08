package com.pedroleite.opencomanda.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pedroleite.opencomanda.R

// Both fonts are bundled (res/font, SIL Open Font License — see assets/licenses) so the app looks
// the same offline. They are variable fonts: one file per family, one entry per weight in use.

private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Inter: body copy, labels, buttons — legible at small sizes. */
val Inter = FontFamily(
    variable(R.font.inter, FontWeight.Normal),
    variable(R.font.inter, FontWeight.Medium),
    variable(R.font.inter, FontWeight.SemiBold),
    variable(R.font.inter, FontWeight.Bold),
)

/** Space Grotesk: screen titles and money figures. */
val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk, FontWeight.Medium),
    variable(R.font.space_grotesk, FontWeight.SemiBold),
    variable(R.font.space_grotesk, FontWeight.Bold),
)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.02).em,
)

private fun text(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val Typography = Typography(
    displayLarge = display(56, 60),
    displayMedium = display(44, 48),
    displaySmall = display(36, 40),
    headlineLarge = display(32, 38),
    headlineMedium = display(28, 34),
    headlineSmall = display(24, 30),
    titleLarge = display(22, 28),
    titleMedium = text(16, 22, FontWeight.SemiBold),
    titleSmall = text(14, 20, FontWeight.SemiBold),
    bodyLarge = text(16, 24, FontWeight.Normal),
    bodyMedium = text(14, 20, FontWeight.Normal),
    bodySmall = text(12, 16, FontWeight.Normal),
    labelLarge = text(15, 20, FontWeight.SemiBold),
    labelMedium = text(12, 16, FontWeight.Medium, tracking = 0.02),
    labelSmall = text(11, 16, FontWeight.Medium, tracking = 0.04),
)
