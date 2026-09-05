package com.sellernest.poreceiving.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Requirements §3.1 Typography: "System sans (Roboto). Body no smaller than 16 sp;
 * quantities and SKUs at 20-28 sp. No thin or light weights - regular and bold only."
 *
 * [FontFamily.Default] resolves to Roboto on Android. Every style below is either
 * [FontWeight.Normal] or [FontWeight.Bold] — nothing thinner is defined, so a thin
 * or light weight cannot be reached through this type scale by accident.
 */
private val industrialFontFamily = FontFamily.Default

/** Dedicated scale for large on-screen numerals: quantities and SKUs (20-28 sp). */
object QuantityTextStyles {
    val skuLarge = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    )
    val quantityDominant = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
    )
    val quantityCompact = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
}

val PoReceivingTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = industrialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    ),
)
