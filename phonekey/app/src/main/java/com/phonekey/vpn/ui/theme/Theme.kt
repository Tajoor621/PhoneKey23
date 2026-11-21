package com.phonekey.vpn.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.phonekey.vpn.R

private val Ink = androidx.compose.ui.graphics.Color(0xFF10140F)
private val Paper = androidx.compose.ui.graphics.Color(0xFFF3F0E7)
private val Brass = androidx.compose.ui.graphics.Color(0xFFE0B15A)
private val BrassInk = androidx.compose.ui.graphics.Color(0xFF1A1408)
private val Sage = androidx.compose.ui.graphics.Color(0xFFB6D37A)
private val Clay = androidx.compose.ui.graphics.Color(0xFFE36A4C)
private val MutedDark = androidx.compose.ui.graphics.Color(0xFFA8AD9F)
private val LineDark = androidx.compose.ui.graphics.Color(0xFF3A4336)
private val SurfaceDark = androidx.compose.ui.graphics.Color(0xFF1C241A)
private val SurfaceDark2 = androidx.compose.ui.graphics.Color(0xFF273024)

private val BrassDeep = androidx.compose.ui.graphics.Color(0xFF8C6420)
private val InkText = androidx.compose.ui.graphics.Color(0xFF1A1E16)
private val MutedLight = androidx.compose.ui.graphics.Color(0xFF5C6456)
private val SurfaceLight = androidx.compose.ui.graphics.Color(0xFFFFFCF7)
private val SurfaceLight2 = androidx.compose.ui.graphics.Color(0xFFE7E2D4)
private val LineLight = androidx.compose.ui.graphics.Color(0xFFD5D0C4)
private val ClayDeep = androidx.compose.ui.graphics.Color(0xFFA33B24)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) = Font(
    resId = res,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Outfit = variableFamily(R.font.outfit_variable)
private val Fraunces = variableFamily(R.font.fraunces_variable)

/** Variable fonts and font variations exist on Android 8. Android 7 uses the system fonts. */
private fun variableFamily(res: Int): FontFamily {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return if (res == R.font.fraunces_variable) FontFamily.Serif else FontFamily.SansSerif
    }
    return FontFamily(
        variable(res, FontWeight.Normal),
        variable(res, FontWeight.Medium),
        variable(res, FontWeight.SemiBold),
    )
}

private fun phoneKeyType(): Typography {
    return Typography(
        displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp),
        headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
        labelMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    )
}

private val DarkColors = darkColorScheme(
    primary = Brass,
    onPrimary = BrassInk,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF3A2E16),
    onPrimaryContainer = Brass,
    secondary = Sage,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF14200C),
    background = Ink,
    onBackground = Paper,
    surface = SurfaceDark,
    onSurface = Paper,
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = MutedDark,
    outline = LineDark,
    error = Clay,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = BrassDeep,
    onPrimary = Paper,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFF3E2C0),
    onPrimaryContainer = BrassInk,
    secondary = androidx.compose.ui.graphics.Color(0xFF3E5C28),
    onSecondary = Paper,
    background = androidx.compose.ui.graphics.Color(0xFFF4F1EA),
    onBackground = InkText,
    surface = SurfaceLight,
    onSurface = InkText,
    surfaceVariant = SurfaceLight2,
    onSurfaceVariant = MutedLight,
    outline = LineLight,
    error = ClayDeep,
    onError = Paper,
)

@Composable
fun PhoneKeyTheme(content: @Composable () -> Unit) {
    val type = remember { phoneKeyType() }
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = type,
        content = content,
    )
}
