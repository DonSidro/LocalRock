package com.kodraliu.localrock.ui.components

import androidx.compose.ui.graphics.Color
import com.kodraliu.localrock.ui.AppColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingIconTintTest {
    // Light group colour measured on the emulator; dark is the Material 3 baseline surfaceContainer.
    private val lightGroup = Color(0xFFEEEDF4)
    private val darkGroup = Color(0xFF211F26)
    private val accents = listOf(
        AppColors.Good, AppColors.Warn, AppColors.Water, AppColors.DirtyWater, AppColors.AccentPurple,
        AppColors.AccentIndigo, AppColors.AccentRed, AppColors.AccentCyan, AppColors.AccentYellow, SettingAmber,
    )

    private fun tile(tint: Color, group: Color) = Color(
        red = group.red + (tint.red - group.red) * 0.12f,
        green = group.green + (tint.green - group.green) * 0.12f,
        blue = group.blue + (tint.blue - group.blue) * 0.12f,
    )

    @Test
    fun contrastRatioMatchesWcagEndpoints() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color.Red, Color.Red), 0.0001)
    }

    @Test
    fun everyAccentReachesThreeToOneInLightAndDark() {
        for (group in listOf(lightGroup, darkGroup)) {
            for (tint in accents) {
                val icon = legibleIconTint(tint, group)
                assertTrue(contrastRatio(icon, tile(tint, group)) >= 3.0, "$tint on $group")
            }
        }
    }

    @Test
    fun settingAmberPassesWithoutAdjustment() {
        assertEquals(SettingAmber, legibleIconTint(SettingAmber, lightGroup))
        assertEquals(SettingAmber, legibleIconTint(SettingAmber, darkGroup))
    }

    @Test
    fun yellowIsDarkenedOnLightAndKeptOnDark() {
        assertTrue(relativeLuminance(legibleIconTint(AppColors.AccentYellow, lightGroup)) < relativeLuminance(AppColors.AccentYellow))
        assertEquals(AppColors.AccentYellow, legibleIconTint(AppColors.AccentYellow, darkGroup))
    }
}
