package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Global state holder for the dynamic theme toggle
object StudyBrainColors {
    var isDark: Boolean = false
}

// Professional Polish warm cream & lavender design palette
val PolishBackground get() = if (StudyBrainColors.isDark) Color(0xFF110E14) else Color(0xFFFDF8F6)
val PolishSurface get() = if (StudyBrainColors.isDark) Color(0xFF1C1A22) else Color(0xFFFFFFFF)
val PolishSoftSurface get() = if (StudyBrainColors.isDark) Color(0xFF25232A) else Color(0xFFF3EDF7)
val PolishDivider get() = if (StudyBrainColors.isDark) Color(0xFF49454F) else Color(0xFFCAC4D0)

val PolishPrimary get() = if (StudyBrainColors.isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
val PolishSecondary get() = if (StudyBrainColors.isDark) Color(0xFF381E72) else Color(0xFFD0BCFF)
val PolishTertiary get() = if (StudyBrainColors.isDark) Color(0xFF4F378B) else Color(0xFFEADDFF)
val PolishOnSecondary get() = if (StudyBrainColors.isDark) Color.White else Color(0xFF21005D)

val PolishTextPrimary get() = if (StudyBrainColors.isDark) Color(0xFFE6E1E5) else Color(0xFF1D1B1E)
val PolishTextMuted get() = if (StudyBrainColors.isDark) Color(0xFFCABEFF) else Color(0xFF49454F)
val PolishVibrantCoral = Color(0xFFFF4D6D)

// Standard colors for fallback/dynamic matching
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

