package es.uma.aiquetepillo.ui.juego

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.uma.aiquetepillo.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/* ==========================================================================
   COMPONENTES DEL JUEGO
   Piezas visuales reutilizables con la estética de juego de móvil: fondo
   morado con estrellas, texto con contorno, botones gruesos con relieve,
   pastillas de marcador y barra de pestañas. Solo dibujan: no guardan estado
   de la aplicación ni comprueban nada.
   ========================================================================== */

/** Fondo morado con un patrón de estrellas tenues. */
@Composable
fun FondoJuego(modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MoradoClaro, MoradoFondo)))
            .drawBehind { dibujarEstrellas() },
        content = contenido
    )
}

private fun DrawScope.dibujarEstrellas() {
    val paso = 72.dp.toPx()
    val radio = 10.dp.toPx()
    var fila = 0
    var y = paso / 2
    while (y < size.height + paso) {
        var x = if (fila % 2 == 0) paso / 2 else paso
        while (x < size.width + paso) {
            drawPath(estrella(Offset(x, y), radio), Color.White.copy(alpha = 0.07f))
            x += paso
        }
        y += paso
        fila++
    }
}

private fun estrella(centro: Offset, radio: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radio else radio * 0.45f
        val angulo = Math.PI / 5 * i - Math.PI / 2
        val x = centro.x + (r * cos(angulo)).toFloat()
        val y = centro.y + (r * sin(angulo)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Texto grueso con contorno oscuro, como el de los títulos y botones del juego. */
@Composable
fun TextoJuego(
    texto: String,
    modifier: Modifier = Modifier,
    tamano: TextUnit = 20.sp,
    color: Color = Color.White,
    grosor: Float = 6f,
    alineacion: TextAlign = TextAlign.Center
) {
    val estilo = TextStyle(
        fontFamily = FuenteJuego,
        fontSize = tamano,
        textAlign = alineacion
    )
    val alineacionCaja = when (alineacion) {
        TextAlign.Start -> Alignment.CenterStart
        TextAlign.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    Box(modifier, contentAlignment = alineacionCaja) {
        Text(texto, style = estilo.copy(color = Tinta, drawStyle = Stroke(width = grosor, join = StrokeJoin.Round)))
        Text(texto, style = estilo.copy(color = color))
    }
}

/** Botón principal: grueso, con contorno y un "labio" inferior que se hunde al pulsarlo. */
@Composable
fun BotonGrande(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Verde,
    colorOscuro: Color = VerdeOscuro,
    habilitado: Boolean = true,
    altura: Dp = 68.dp
) {
    val interaccion = remember { MutableInteractionSource() }
    val pulsado by interaccion.collectIsPressedAsState()
    val hundido = if (pulsado && habilitado) 5.dp else 0.dp
    val forma = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(altura + 6.dp)
            .alpha(if (habilitado) 1f else 0.5f)
            .clickable(
                interactionSource = interaccion,
                indication = null,
                enabled = habilitado,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        // Labio inferior: da el efecto de relieve
        Box(
            Modifier
                .fillMaxWidth()
                .height(altura)
                .offset(y = 6.dp)
                .clip(forma)
                .background(Tinta)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(altura)
                .offset(y = hundido)
                .clip(forma)
                .background(Tinta)
                .padding(3.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Brush.verticalGradient(listOf(color, colorOscuro))),
            contentAlignment = Alignment.Center
        ) {
            // Brillo superior
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 5.dp)
                    .fillMaxWidth(0.86f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.28f))
            )
            TextoJuego(texto, tamano = 24.sp, grosor = 8f)
        }
    }
}

/**
 * Marcador de la barra superior: pastilla translúcida con un icono grande que
 * sobresale por la izquierda. Si recibe onClick, se comporta como un botón.
 */
@Composable
fun Pastilla(icono: String, texto: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier = modifier
            .height(46.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(start = 16.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(Tinta.copy(alpha = 0.45f))
                .padding(start = 26.dp, end = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            TextoJuego(texto, tamano = 17.sp, grosor = 5f)
        }
        Text(icono, fontSize = 30.sp)
    }
}

/** Botón circular translúcido, como el de ajustes del juego. */
@Composable
fun BotonRedondo(icono: String, descripcion: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Tinta.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClickLabel = descripcion, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(icono, fontSize = 26.sp)
    }
}

/** Panel con contorno oscuro y esquinas muy redondeadas. */
@Composable
fun TarjetaJuego(
    modifier: Modifier = Modifier,
    fondo: Color = MoradoPanel,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Tinta)
            .padding(3.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(fondo)
            .padding(16.dp),
        content = contenido
    )
}

/** Círculo con un emoji, para avatares e iconos grandes. */
@Composable
fun Medallon(icono: String, modifier: Modifier = Modifier, tamano: Dp = 64.dp, fondo: Color = Amarillo) {
    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(Tinta)
            .padding(3.dp)
            .clip(CircleShape)
            .background(fondo),
        contentAlignment = Alignment.Center
    ) {
        Text(icono, fontSize = (tamano.value * 0.5f).sp)
    }
}

/** Campo de texto blanco con contorno, para el formulario de acceso. */
@Composable
fun CampoJuego(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    secreto: Boolean = false
) {
    val forma = RoundedCornerShape(16.dp)
    TextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secreto) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        shape = forma,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Tinta,
            unfocusedTextColor = Tinta,
            focusedLabelColor = MoradoOscuro,
            unfocusedLabelColor = MoradoOscuro,
            cursorColor = MoradoOscuro
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(3.dp, Tinta, forma)
    )
}

/** Aviso destacado (errores y mensajes informativos). */
@Composable
fun Aviso(texto: String, modifier: Modifier = Modifier, fondo: Color = Rojo) {
    Text(
        texto,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fondo)
            .border(2.dp, Tinta, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

/** Pestañas de la barra inferior. */
enum class Pestana(val icono: String, val titulo: String) {
    GRUPO("👥", "GRUPO"),
    MISIONES("🎯", "MISIONES"),
    RANKING("🏆", "RANKING")
}

/**
 * Barra inferior: pestañas lavanda pegadas entre sí, separadas por una línea
 * oscura. La activa es más ancha, más alta y más clara, y muestra su nombre.
 * Detrás de las pestañas no hay fondo propio: asoma el de la pantalla, y las
 * pestañas bajan hasta el borde, por debajo de la barra de navegación.
 */
@Composable
fun BarraInferior(actual: Pestana, onCambiar: (Pestana) -> Unit, modifier: Modifier = Modifier) {
    val margenInferior = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp + margenInferior),
        verticalAlignment = Alignment.Bottom
    ) {
        Pestana.entries.forEach { pestana ->
            val seleccionada = pestana == actual
            val forma = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            Box(
                modifier = Modifier
                    .weight(if (seleccionada) 1.3f else 1f)
                    .fillMaxHeight(if (seleccionada) 1f else 0.82f)
                    .clip(forma)
                    .background(Tinta.copy(alpha = 0.6f))
                    .padding(start = 2.dp, end = 2.dp, top = 2.dp)
                    .clip(forma)
                    .background(
                        if (seleccionada) Brush.verticalGradient(listOf(LavandaClara, Lavanda))
                        else Brush.verticalGradient(listOf(Lavanda, LavandaMedia))
                    )
                    .clickable(role = Role.Tab) { onCambiar(pestana) },
                contentAlignment = Alignment.Center
            ) {
                // Brillo superior, como en las pestañas del juego
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 5.dp)
                        .fillMaxWidth(0.7f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.6f))
                )
                Column(
                    Modifier.padding(bottom = margenInferior),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(pestana.icono, fontSize = if (seleccionada) 40.sp else 34.sp)
                    if (seleccionada) TextoJuego(pestana.titulo, tamano = 15.sp, grosor = 6f)
                }
            }
        }
    }
}
