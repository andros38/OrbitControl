package id.orbitcontrol.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OrbitColors = lightColorScheme(
    primary = OrbitBlue,
    secondary = OrbitGreen,
    error = OrbitRed,
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    onPrimary = Color.White,
)

@Composable
fun OrbitControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OrbitColors, content = content)
}
