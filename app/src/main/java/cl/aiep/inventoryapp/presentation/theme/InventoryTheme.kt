package cl.aiep.inventoryapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF18333E)
val Teal = Color(0xFF166D77)
val Navy = Color(0xFF143E52)
val Pale = Color(0xFFF2F7F8)
val Mint = Color(0xFFE1F3EF)
val Border = Color(0xFFD6E5E7)
val Accent = Color(0xFF127D83)

private val Palette = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Mint, onPrimaryContainer = Ink,
    secondary = Navy, onSecondary = Color.White,
    background = Pale, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE9F2F3), outline = Border,
    error = Color(0xFFB42318)
)

@Composable
fun InventoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, typography = Typography(), content = content)
}
