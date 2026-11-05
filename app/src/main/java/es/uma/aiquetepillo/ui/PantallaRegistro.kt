package es.uma.aiquetepillo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.uma.aiquetepillo.Foto
import es.uma.aiquetepillo.modelo.*
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.*

/**
 * Registro de una cuenta nueva.
 *
 * El selfie es obligatorio y se hace en el momento con la cámara frontal de la
 * aplicación, nunca desde la galería: la IA lo usa para reconocer a la persona
 * en las fotos de las misiones. Es un dato biométrico (art. 9 del RGPD).
 */
@Composable
fun PantallaRegistro(
    estado: EstadoUi<RespuestaLogin>,
    selfie: Foto?,
    onHacerSelfie: () -> Unit,
    onCrear: (usuario: String, clave: String, nombre: String) -> Unit,
    onVolver: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    val cargando = estado is EstadoUi.Cargando
    val usuarioValido = Regex("[a-z0-9_]{3,20}").matches(usuario)
    val abrirCamara = rememberAbrirCamara(onHacerSelfie)

    FondoJuego {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonRedondo("⬅️", "Volver", onVolver)
                Spacer(Modifier.width(12.dp))
                TextoJuego("NUEVA CUENTA", Modifier.weight(1f), tamano = 20.sp, alineacion = TextAlign.End)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selfie != null) {
                    MiniaturaFoto(selfie, Modifier.size(140.dp), descripcion = "Tu selfie")
                } else {
                    Medallon("🤳", tamano = 120.dp)
                }
                Spacer(Modifier.height(12.dp))
                BotonGrande(
                    texto = if (selfie == null) "HACER SELFIE" else "REPETIR SELFIE",
                    onClick = abrirCamara,
                    color = Amarillo,
                    colorOscuro = Naranja
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La IA usará tu selfie para reconocerte en las fotos de los demás.",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                TarjetaJuego(Modifier.fillMaxWidth()) {
                    CampoJuego(nombre, { nombre = it }, "Tu nombre")
                    Spacer(Modifier.height(12.dp))
                    CampoJuego(usuario, { usuario = it.lowercase().trim() }, "Usuario (minúsculas, números y _)")
                    Spacer(Modifier.height(12.dp))
                    CampoJuego(clave, { clave = it }, "Contraseña", secreto = true)
                }
                Spacer(Modifier.height(20.dp))

                BotonGrande(
                    texto = if (cargando) "CREANDO..." else "CREAR CUENTA",
                    onClick = { onCrear(usuario, clave, nombre.trim()) },
                    habilitado = selfie != null && nombre.isNotBlank() && usuarioValido && clave.isNotEmpty() && !cargando
                )
                if (estado is EstadoUi.Error) {
                    Spacer(Modifier.height(16.dp))
                    Aviso(textoDeError(estado.motivo))
                }
            }
        }
    }
}
