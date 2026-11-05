package es.uma.aiquetepillo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.uma.aiquetepillo.modelo.*
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.Amarillo

/**
 * Pantalla de acceso.
 *
 * Sigue el patrón de elevación de estado de la sesión 2.5: el composable no
 * guarda el resultado de la operación, lo recibe, y comunica lo que ocurre
 * mediante la función onEntrar.
 */
@Composable
fun PantallaLogin(
    estado: EstadoUi<RespuestaLogin>,
    onEntrar: (String, String) -> Unit,
    onRegistro: () -> Unit
) {
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    val cargando = estado is EstadoUi.Cargando

    FondoJuego {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Medallon("📸", tamano = 96.dp)
            Spacer(Modifier.height(16.dp))
            TextoJuego("AI QUE", tamano = 38.sp, grosor = 10f)
            TextoJuego("TE PILLO", tamano = 46.sp, color = Amarillo, grosor = 12f)
            Spacer(Modifier.height(28.dp))

            TarjetaJuego(Modifier.fillMaxWidth()) {
                TextoJuego("ACCESO", Modifier.align(Alignment.CenterHorizontally), tamano = 22.sp)
                Spacer(Modifier.height(16.dp))
                CampoJuego(usuario, { usuario = it }, "Usuario")
                Spacer(Modifier.height(12.dp))
                CampoJuego(clave, { clave = it }, "Contraseña", secreto = true)
            }
            Spacer(Modifier.height(24.dp))

            BotonGrande(
                texto = if (cargando) "ENTRANDO..." else "ENTRAR",
                onClick = { onEntrar(usuario.trim(), clave) },
                habilitado = usuario.isNotBlank() && clave.isNotBlank() && !cargando
            )
            Spacer(Modifier.height(16.dp))

            // El when es exhaustivo gracias a la clase sellada de EstadoUi
            when (estado) {
                is EstadoUi.Error -> Aviso(textoDeError(estado.motivo))
                is EstadoUi.Cargando -> CircularProgressIndicator(color = Color.White)
                else -> Unit
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRegistro) {
                Text("¿No tienes cuenta? ", color = Color.White, fontSize = 16.sp)
                Text("REGÍSTRATE", color = Amarillo, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Traduce el motivo a un texto para el usuario.
 *
 * Fíjate en que el mensaje NO revela detalles internos: nada de direcciones de
 * servidor, rutas ni trazas. Es la idea de la sesión 2.4.5. Las pantallas de
 * grupos usan también esta función.
 */
fun textoDeError(motivo: MotivoError): String = when (motivo) {
    MotivoError.SinConexion -> "No se ha podido conectar. Inténtalo de nuevo."
    MotivoError.CredencialesNoValidas -> "Usuario o contraseña incorrectos."
    MotivoError.SesionCaducada -> "La sesión ha caducado. Vuelve a entrar."
    MotivoError.CodigoNoValido -> "Ese código de invitación no existe."
    MotivoError.UsuarioOcupado -> "Ese nombre de usuario ya está cogido."
    MotivoError.MisionNoActiva -> "Esa misión ya no está activa. Mira la nueva."
    MotivoError.Desconocido -> "Ha ocurrido un error. Inténtalo de nuevo."
}
