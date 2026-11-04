package es.uma.aiquetepillo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import es.uma.aiquetepillo.R

/**
 * Lilita One (Google Fonts, licencia OFL: licencias/LilitaOne-OFL.txt).
 * Solo tiene un grosor, así que no se le pide negrita: Android la imitaría
 * engordando los trazos y se vería peor.
 */
val FuenteJuego = FontFamily(Font(R.font.lilita_one))

private fun TextStyle.enFuenteJuego() = copy(fontFamily = FuenteJuego)

/** Todos los estilos de Material con la fuente del juego. */
val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.enFuenteJuego(),
        displayMedium = displayMedium.enFuenteJuego(),
        displaySmall = displaySmall.enFuenteJuego(),
        headlineLarge = headlineLarge.enFuenteJuego(),
        headlineMedium = headlineMedium.enFuenteJuego(),
        headlineSmall = headlineSmall.enFuenteJuego(),
        titleLarge = titleLarge.enFuenteJuego(),
        titleMedium = titleMedium.enFuenteJuego(),
        titleSmall = titleSmall.enFuenteJuego(),
        bodyLarge = bodyLarge.enFuenteJuego(),
        bodyMedium = bodyMedium.enFuenteJuego(),
        bodySmall = bodySmall.enFuenteJuego(),
        labelLarge = labelLarge.enFuenteJuego(),
        labelMedium = labelMedium.enFuenteJuego(),
        labelSmall = labelSmall.enFuenteJuego()
    )
}
