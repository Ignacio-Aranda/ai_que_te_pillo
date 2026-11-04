package es.uma.aiquetepillo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Un único esquema, sin modo claro ni colores dinámicos: la estética del juego
 * es siempre la misma. Los componentes propios están en ui/juego.
 */
private val EsquemaJuego = darkColorScheme(
    primary = Amarillo,
    onPrimary = Tinta,
    secondary = Verde,
    onSecondary = Color.White,
    background = MoradoFondo,
    onBackground = Color.White,
    surface = MoradoPanel,
    onSurface = Color.White,
    error = Rojo,
    onError = Color.White
)

@Composable
fun AiQueTePilloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaJuego,
        typography = Typography,
        content = content
    )
}
