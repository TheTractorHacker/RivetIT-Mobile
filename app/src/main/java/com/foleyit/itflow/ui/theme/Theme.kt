package com.foleyit.itflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Stored theme preference values (see AppPreferences.THEME_MODE). */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

/**
 * Runtime-selectable brand color seed (see AppPreferences.COLOR_SEED). Five fixed, hand-tuned
 * palettes — still not Material You dynamic/wallpaper-derived color (that was deliberately
 * ruled out previously so the app looks the same, deliberately designed, on every device);
 * these are curated brand palettes the user explicitly picks, not device-dependent ones.
 * FOLEYIT (the operating company's own brand identity) is the default; TEAL is the app's
 * original/real fallback seed.
 */
enum class ColorSeed(val id: String) {
    FOLEYIT("foleyit"),
    TEAL("teal"),
    SUNSET("sunset"),
    FOREST("forest"),
    VIOLET("violet");

    companion object {
        val DEFAULT = FOLEYIT
        fun fromId(id: String?): ColorSeed = entries.find { it.id == id } ?: DEFAULT
    }
}

// Error roles are the standard M3 baseline red — seed-independent, same across all five seeds.
private const val ERR_LIGHT = 0xFFBA1A1A
private const val ON_ERR_LIGHT = 0xFFFFFFFF
private const val ERR_CONTAINER_LIGHT = 0xFFFFDAD6
private const val ON_ERR_CONTAINER_LIGHT = 0xFF410002
private const val ERR_DARK = 0xFFFFB4AB
private const val ON_ERR_DARK = 0xFF690005
private const val ERR_CONTAINER_DARK = 0xFF93000A
private const val ON_ERR_CONTAINER_DARK = 0xFFFFDAD6

private val TealLight = lightColorScheme(
    primary = Color(0xFF006875), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF97F0FF), onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6267), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE7EC), onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF525E7A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDAE2FF), onTertiaryContainer = Color(0xFF0D1B36),
    error = Color(ERR_LIGHT), onError = Color(ON_ERR_LIGHT),
    errorContainer = Color(ERR_CONTAINER_LIGHT), onErrorContainer = Color(ON_ERR_CONTAINER_LIGHT),
    background = Color(0xFFF5FAFA), onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF5FAFA), onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E6), onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797B), outlineVariant = Color(0xFFBFC8CA),
    inverseSurface = Color(0xFF2D3132), inverseOnSurface = Color(0xFFEFF1F1), inversePrimary = Color(0xFF4FD8EB),
    // Teal-neutral surface container ladder (hand-tuned, not part of the reference CSS — it
    // only specifies role colors, not these) so Compose doesn't fall back to stock M3's
    // purple-tinted neutral gray for Card/Sheet/Menu containers next to a teal surface.
    surfaceDim = Color(0xFFD3E9E9), surfaceBright = Color(0xFFF8FCFC),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainer = Color(0xFFE9F0F0), surfaceContainerHigh = Color(0xFFE3EAEA), surfaceContainerHighest = Color(0xFFDEE4E4),
)

private val TealDark = darkColorScheme(
    primary = Color(0xFF4FD8EB), onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004E58), onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFFB1CBD0), onSecondary = Color(0xFF1B343A),
    secondaryContainer = Color(0xFF324B51), onSecondaryContainer = Color(0xFFCCE7EC),
    tertiary = Color(0xFFBAC6E8), onTertiary = Color(0xFF232F4B),
    tertiaryContainer = Color(0xFF3A4663), onTertiaryContainer = Color(0xFFDAE2FF),
    error = Color(ERR_DARK), onError = Color(ON_ERR_DARK),
    errorContainer = Color(ERR_CONTAINER_DARK), onErrorContainer = Color(ON_ERR_CONTAINER_DARK),
    background = Color(0xFF191C1D), onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF191C1D), onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF3F484A), onSurfaceVariant = Color(0xFFBFC8CA),
    outline = Color(0xFF899294), outlineVariant = Color(0xFF3F484A),
    inverseSurface = Color(0xFFE1E3E3), inverseOnSurface = Color(0xFF2D3132), inversePrimary = Color(0xFF006875),
    surfaceDim = Color(0xFF0E1010), surfaceBright = Color(0xFF393F42),
    surfaceContainerLowest = Color(0xFF0F1213), surfaceContainerLow = Color(0xFF1D2021),
    surfaceContainer = Color(0xFF212425), surfaceContainerHigh = Color(0xFF2B2F30), surfaceContainerHighest = Color(0xFF363A3B),
)

// FoleyIT — the operating company's own brand identity (blue/cyan/green). New default seed.
private val FoleyitLight = lightColorScheme(
    primary = Color(0xFF0066CC), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E1FF), onPrimaryContainer = Color(0xFF05162C),
    secondary = Color(0xFF009966), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBEE2C9), onSecondaryContainer = Color(0xFF021709),
    tertiary = Color(0xFF724AAB), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFD4F8), onTertiaryContainer = Color(0xFF170F23),
    error = Color(ERR_LIGHT), onError = Color(ON_ERR_LIGHT),
    errorContainer = Color(ERR_CONTAINER_LIGHT), onErrorContainer = Color(ON_ERR_CONTAINER_LIGHT),
    background = Color(0xFFF8FAFD), onBackground = Color(0xFF13161B),
    surface = Color(0xFFF8FAFD), onSurface = Color(0xFF13161B),
    surfaceVariant = Color(0xFFD8DFE8), onSurfaceVariant = Color(0xFF3D434A),
    outline = Color(0xFF5E646C), outlineVariant = Color(0xFFB7BEC7),
    inverseSurface = Color(0xFF26292E), inverseOnSurface = Color(0xFFEFF2F5), inversePrimary = Color(0xFF65D2D2),
    surfaceDim = Color(0xFFDBE6F5), surfaceBright = Color(0xFFFBFCFF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F5F9),
    surfaceContainer = Color(0xFFEBEFF4), surfaceContainerHigh = Color(0xFFE3E8EF), surfaceContainerHighest = Color(0xFFDCE2E9),
)

private val FoleyitDark = darkColorScheme(
    primary = Color(0xFF00D9FF), onPrimary = Color(0xFF020E0E),
    primaryContainer = Color(0xFF00393A), onPrimaryContainer = Color(0xFFB0EBEA),
    secondary = Color(0xFF00FF88), onSecondary = Color(0xFF020F06),
    secondaryContainer = Color(0xFF00391B), onSecondaryContainer = Color(0xFFC0EACD),
    tertiary = Color(0xFFCAACFF), onTertiary = Color(0xFF1B0E2D),
    tertiaryContainer = Color(0xFF3A2659), onTertiaryContainer = Color(0xFFE0D2FD),
    error = Color(ERR_DARK), onError = Color(ON_ERR_DARK),
    errorContainer = Color(ERR_CONTAINER_DARK), onErrorContainer = Color(ON_ERR_CONTAINER_DARK),
    background = Color(0xFF030305), onBackground = Color(0xFFE2E5E8),
    surface = Color(0xFF030305), onSurface = Color(0xFFE2E5E8),
    surfaceVariant = Color(0xFF242930), onSurfaceVariant = Color(0xFFB1B8C1),
    outline = Color(0xFF78818C), outlineVariant = Color(0xFF242930),
    inverseSurface = Color(0xFFE2E5E8), inverseOnSurface = Color(0xFF181B1F), inversePrimary = Color(0xFF0066CC),
    surfaceDim = Color(0xFF000001), surfaceBright = Color(0xFF1C1F24),
    surfaceContainerLowest = Color(0xFF010101), surfaceContainerLow = Color(0xFF06070A),
    surfaceContainer = Color(0xFF0B0D11), surfaceContainerHigh = Color(0xFF13161B), surfaceContainerHighest = Color(0xFF1B2025),
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFF9A3E00), onPrimary = Color(0xFFFCFCFC),
    primaryContainer = Color(0xFFFFD3B2), onPrimaryContainer = Color(0xFF270F00),
    secondary = Color(0xFF6A5243), onSecondary = Color(0xFFFCFCFC),
    secondaryContainer = Color(0xFFE9D7CC), onSecondaryContainer = Color(0xFF1D140D),
    tertiary = Color(0xFF575E1A), onTertiary = Color(0xFFFCFCFC),
    tertiaryContainer = Color(0xFFD9DFBA), onTertiaryContainer = Color(0xFF161802),
    error = Color(ERR_LIGHT), onError = Color(ON_ERR_LIGHT),
    errorContainer = Color(ERR_CONTAINER_LIGHT), onErrorContainer = Color(ON_ERR_CONTAINER_LIGHT),
    background = Color(0xFFFDF9F6), onBackground = Color(0xFF1A1512),
    surface = Color(0xFFFDF9F6), onSurface = Color(0xFF1A1512),
    surfaceVariant = Color(0xFFE9DBD2), onSurfaceVariant = Color(0xFF4B4038),
    outline = Color(0xFF6D6059), outlineVariant = Color(0xFFC8BBB2),
    inverseSurface = Color(0xFF2D2824), inverseOnSurface = Color(0xFFF5F1EE), inversePrimary = Color(0xFFF0AD7F),
    surfaceDim = Color(0xFFF2E1D6), surfaceBright = Color(0xFFFFFBF9),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAF4F0),
    surfaceContainer = Color(0xFFF4EDE8), surfaceContainerHigh = Color(0xFFEFE6E0), surfaceContainerHighest = Color(0xFFE9DFD9),
)

private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFCB17E), onPrimary = Color(0xFF341600),
    primaryContainer = Color(0xFF5D2500), onPrimaryContainer = Color(0xFFFED4B9),
    secondary = Color(0xFFD4C0B2), onSecondary = Color(0xFF2C221B),
    secondaryContainer = Color(0xFF443429), onSecondaryContainer = Color(0xFFE6D8CF),
    tertiary = Color(0xFFC2CB8F), onTertiary = Color(0xFF232707),
    tertiaryContainer = Color(0xFF373B14), onTertiaryContainer = Color(0xFFD9DFBA),
    error = Color(ERR_DARK), onError = Color(ON_ERR_DARK),
    errorContainer = Color(ERR_CONTAINER_DARK), onErrorContainer = Color(ON_ERR_CONTAINER_DARK),
    background = Color(0xFF100C0A), onBackground = Color(0xFFE1DDDA),
    surface = Color(0xFF100C0A), onSurface = Color(0xFFE1DDDA),
    surfaceVariant = Color(0xFF3C332E), onSurfaceVariant = Color(0xFFC6BBB5),
    outline = Color(0xFF93867E), outlineVariant = Color(0xFF3C332E),
    inverseSurface = Color(0xFFE1DDDA), inverseOnSurface = Color(0xFF231E1B), inversePrimary = Color(0xFF9A3E00),
    surfaceDim = Color(0xFF050403), surfaceBright = Color(0xFF332C28),
    surfaceContainerLowest = Color(0xFF060403), surfaceContainerLow = Color(0xFF14110E),
    surfaceContainer = Color(0xFF1C1714), surfaceContainerHigh = Color(0xFF26201C), surfaceContainerHighest = Color(0xFF312A25),
)

private val ForestLight = lightColorScheme(
    primary = Color(0xFF1C5C23), onPrimary = Color(0xFFFCFCFC),
    primaryContainer = Color(0xFFC3E6C3), onPrimaryContainer = Color(0xFF051606),
    secondary = Color(0xFF485748), onSecondary = Color(0xFFFCFCFC),
    secondaryContainer = Color(0xFFD0DBD0), onSecondaryContainer = Color(0xFF0C140C),
    tertiary = Color(0xFF005B7A), onTertiary = Color(0xFFFCFCFC),
    tertiaryContainer = Color(0xFFB7DEF3), onTertiaryContainer = Color(0xFF001420),
    error = Color(ERR_LIGHT), onError = Color(ON_ERR_LIGHT),
    errorContainer = Color(ERR_CONTAINER_LIGHT), onErrorContainer = Color(ON_ERR_CONTAINER_LIGHT),
    background = Color(0xFFF8FBF8), onBackground = Color(0xFF111511),
    surface = Color(0xFFF8FBF8), onSurface = Color(0xFF111511),
    surfaceVariant = Color(0xFFD7E1D7), onSurfaceVariant = Color(0xFF3A423A),
    outline = Color(0xFF5A635A), outlineVariant = Color(0xFFB7C1B7),
    inverseSurface = Color(0xFF232823), inverseOnSurface = Color(0xFFF0F3F0), inversePrimary = Color(0xFF85CC87),
    surfaceDim = Color(0xFFDCE9DC), surfaceBright = Color(0xFFFBFDFB),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F6F2),
    surfaceContainer = Color(0xFFEBF0EB), surfaceContainerHigh = Color(0xFFE3EAE3), surfaceContainerHighest = Color(0xFFDCE4DC),
)

private val ForestDark = darkColorScheme(
    primary = Color(0xFF85CC87), onPrimary = Color(0xFF042107),
    primaryContainer = Color(0xFF0B3E12), onPrimaryContainer = Color(0xFFC3E6C3),
    secondary = Color(0xFFB2C3B2), onSecondary = Color(0xFF1C241C),
    secondaryContainer = Color(0xFF2C3A2C), onSecondaryContainer = Color(0xFFD0DBD0),
    tertiary = Color(0xFF87C8E8), onTertiary = Color(0xFF002635),
    tertiaryContainer = Color(0xFF093B4E), onTertiaryContainer = Color(0xFFB7DEF3),
    error = Color(ERR_DARK), onError = Color(ON_ERR_DARK),
    errorContainer = Color(ERR_CONTAINER_DARK), onErrorContainer = Color(ON_ERR_CONTAINER_DARK),
    background = Color(0xFF090C09), onBackground = Color(0xFFD9DCD9),
    surface = Color(0xFF090C09), onSurface = Color(0xFFD9DCD9),
    surfaceVariant = Color(0xFF2F352F), onSurfaceVariant = Color(0xFFB6BDB5),
    outline = Color(0xFF808980), outlineVariant = Color(0xFF2F352F),
    inverseSurface = Color(0xFFD9DCD9), inverseOnSurface = Color(0xFF1A1E1A), inversePrimary = Color(0xFF1C5C23),
    surfaceDim = Color(0xFF020302), surfaceBright = Color(0xFF282D28),
    surfaceContainerLowest = Color(0xFF030403), surfaceContainerLow = Color(0xFF0E100E),
    surfaceContainer = Color(0xFF141714), surfaceContainerHigh = Color(0xFF1C211C), surfaceContainerHighest = Color(0xFF252B25),
)

private val VioletLight = lightColorScheme(
    primary = Color(0xFF703396), onPrimary = Color(0xFFFCFCFC),
    primaryContainer = Color(0xFFECD2FF), onPrimaryContainer = Color(0xFF1B0C24),
    secondary = Color(0xFF5F5269), onSecondary = Color(0xFFFCFCFC),
    secondaryContainer = Color(0xFFE0D6E7), onSecondaryContainer = Color(0xFF17111B),
    tertiary = Color(0xFF873E40), onTertiary = Color(0xFFFCFCFC),
    tertiaryContainer = Color(0xFFFACECD), onTertiaryContainer = Color(0xFF230C0C),
    error = Color(ERR_LIGHT), onError = Color(ON_ERR_LIGHT),
    errorContainer = Color(ERR_CONTAINER_LIGHT), onErrorContainer = Color(ON_ERR_CONTAINER_LIGHT),
    background = Color(0xFFFBF9FD), onBackground = Color(0xFF171519),
    surface = Color(0xFFFBF9FD), onSurface = Color(0xFF171519),
    surfaceVariant = Color(0xFFE2DBE8), onSurfaceVariant = Color(0xFF46404B),
    outline = Color(0xFF67606C), outlineVariant = Color(0xFFC2BAC8),
    inverseSurface = Color(0xFF2A282D), inverseOnSurface = Color(0xFFF3F1F5), inversePrimary = Color(0xFFD9ABFB),
    surfaceDim = Color(0xFFEAE1F1), surfaceBright = Color(0xFFFDFBFF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F4F9),
    surfaceContainer = Color(0xFFF0EDF4), surfaceContainerHigh = Color(0xFFEAE6EE), surfaceContainerHighest = Color(0xFFE4DFE8),
)

private val VioletDark = darkColorScheme(
    primary = Color(0xFFD9ABFB), onPrimary = Color(0xFF2C163A),
    primaryContainer = Color(0xFF45235B), onPrimaryContainer = Color(0xFFEAD4FC),
    secondary = Color(0xFFC7BCD0), onSecondary = Color(0xFF29242E),
    secondaryContainer = Color(0xFF3A3141), onSecondaryContainer = Color(0xFFDFD7E5),
    tertiary = Color(0xFFF6AAA9), onTertiary = Color(0xFF3F191A),
    tertiaryContainer = Color(0xFF502828), onTertiaryContainer = Color(0xFFFACECD),
    error = Color(ERR_DARK), onError = Color(ON_ERR_DARK),
    errorContainer = Color(ERR_CONTAINER_DARK), onErrorContainer = Color(ON_ERR_CONTAINER_DARK),
    background = Color(0xFF0E0C10), onBackground = Color(0xFFDFDDE1),
    surface = Color(0xFF0E0C10), onSurface = Color(0xFFDFDDE1),
    surfaceVariant = Color(0xFF38333B), onSurfaceVariant = Color(0xFFC1BBC5),
    outline = Color(0xFF8D8692), outlineVariant = Color(0xFF38333B),
    inverseSurface = Color(0xFFDFDDE1), inverseOnSurface = Color(0xFF211E23), inversePrimary = Color(0xFF703396),
    surfaceDim = Color(0xFF050405), surfaceBright = Color(0xFF302C33),
    surfaceContainerLowest = Color(0xFF050406), surfaceContainerLow = Color(0xFF131114),
    surfaceContainer = Color(0xFF1A171C), surfaceContainerHigh = Color(0xFF232026), surfaceContainerHighest = Color(0xFF2D2931),
)

// Radius scale (design tokens: 4 priority bars/dots, 8 badges/pills/chips, 12 dialogs/sheets,
// 16 cards/stat tiles, 28 search fields) maps 1:1 onto M3's five named shape levels. The
// "full/999" radius (buttons, avatars, nav pills, FAB) has no slot in Shapes — use CircleShape
// or RoundedCornerShape(percent = 50) directly at the call site, same as full-bleed radii today.
val ITFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

fun colorSchemeFor(seed: ColorSeed, darkTheme: Boolean): ColorScheme = when (seed) {
    ColorSeed.FOLEYIT -> if (darkTheme) FoleyitDark else FoleyitLight
    ColorSeed.TEAL -> if (darkTheme) TealDark else TealLight
    ColorSeed.SUNSET -> if (darkTheme) SunsetDark else SunsetLight
    ColorSeed.FOREST -> if (darkTheme) ForestDark else ForestLight
    ColorSeed.VIOLET -> if (darkTheme) VioletDark else VioletLight
}

@Composable
fun ITFlowTheme(
    themeMode: String = ThemeMode.SYSTEM,
    colorSeed: String = ColorSeed.DEFAULT.id,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = colorSchemeFor(ColorSeed.fromId(colorSeed), darkTheme)
    val statusColors = statusColorsFor(colorScheme, darkTheme)

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(colorScheme = colorScheme, shapes = ITFlowShapes, content = content)
    }
}
