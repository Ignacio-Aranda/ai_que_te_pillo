package es.uma.aiquetepillo.ui

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import es.uma.aiquetepillo.datos.SesionLocal
import javax.crypto.Cipher

/* ==========================================================================
   LA PUERTA BIOMÉTRICA
   Teoría: sesión 3.3, diapositivas 19 y 20.

   Fichero NUEVO del paso 4. Envuelve a la navegación: mientras la sesión
   guardada esté bloqueada, la aplicación no se muestra.

   LA IDEA QUE HAY QUE ENTENDER, Y NO ES LA QUE PARECE

   Lo que protege los datos no es este composable. Si un adversario parchea
   esta pantalla y la salta entera, no consigue nada: al otro lado no hay
   datos descifrados esperando, porque descifrar requiere un cifrador que
   sólo el sistema entrega, y sólo tras una autenticación correcta.

   Compara con lo que NO se hace aquí:

       if (sistema.haAutenticado()) { mostrarDatos() }     // frágil

   Eso es una variable dentro de un proceso que el adversario controla, y es
   la falacia de la sesión 3.1. Aquí, en cambio, el objeto criptográfico se le
   entrega al diálogo y se recoge después:

       prompt.authenticate(info, BiometricPrompt.CryptoObject(cifrador))
       ...
       resultado.cryptoObject?.cipher     // <- el único que sirve

   Sin autenticación correcta no hay cifrador utilizable, y sin cifrador no
   hay operación. La garantía está en el hardware, no en este if.
   ========================================================================== */

@Composable
fun PuertaBiometrica(
    actividad: FragmentActivity,
    sesion: SesionLocal,
    contenido: @Composable () -> Unit
) {
    val estado by sesion.estado.collectAsState()
    // El aviso es de la pantalla en la que se produjo: al cambiar de estado se olvida
    var aviso by remember(estado) { mutableStateOf<String?>(null) }

    when (estado) {
        // No hay nada guardado todavía: que pase y se identifique.
        SesionLocal.Estado.SIN_SESION -> contenido()

        // Ya está abierta en memoria: la aplicación funciona con normalidad.
        SesionLocal.Estado.ABIERTA -> contenido()

        // Acaba de iniciar sesión y falta confirmar para guardarla cifrada.
        SesionLocal.Estado.PENDIENTE_DE_GUARDAR -> PantallaDePuerta(
            titulo = "Proteger tu sesión",
            explicacion = "Confirma con tu huella para guardar la sesión cifrada en este dispositivo.",
            textoBoton = "Confirmar",
            aviso = aviso,
            onPulsar = {
                /*
                 * Crear la clave también puede fallar: en un terminal sin
                 * bloqueo de pantalla ni huella, el sistema se niega a crear
                 * una clave que exige autenticación. Sin este try la excepción
                 * llegaba al hilo principal y cerraba la aplicación. Se decide
                 * no guardar la sesión y avisar.
                 */
                val cifrador = try {
                    sesion.cifradorParaGuardar()
                } catch (e: Exception) {
                    aviso = "Configura un bloqueo de pantalla y una huella para guardar la sesión."
                    null
                }
                if (cifrador != null) {
                    pedirAutenticacion(
                        actividad = actividad,
                        titulo = "Proteger tu sesión",
                        subtitulo = "Tu sesión se guardará cifrada",
                        cifrador = cifrador,
                        onCorrecta = { autorizado ->
                            aviso = if (sesion.completarGuardado(autorizado)) null
                            else "No se ha podido guardar la sesión cifrada. Inténtalo de nuevo."
                        },
                        onFallo = { mensaje -> aviso = mensaje }
                    )
                }
            }
        )

        // Hay sesión guardada: hay que desbloquearla para poder usarla.
        SesionLocal.Estado.BLOQUEADA -> PantallaDePuerta(
            titulo = "Desbloquear tus datos",
            explicacion = "Usa tu huella para abrir la sesión guardada en este dispositivo.",
            textoBoton = "Desbloquear",
            aviso = aviso,
            onPulsar = {
                /*
                 * Crear el cifrador puede fallar aquí mismo, antes de llegar
                 * al diálogo, si la clave ha sido invalidada. Es el caso de
                 * «se ha registrado una huella nueva»: se detecta, se
                 * descarta lo que ya no se puede recuperar y se avisa.
                 */
                val cifrador = try {
                    sesion.cifradorParaAbrir()
                } catch (e: Exception) {
                    sesion.descartarSesionIrrecuperable()
                    aviso = "La sesión guardada ya no se puede recuperar. Vuelve a iniciar sesión."
                    null
                }
                if (cifrador != null) {
                    pedirAutenticacion(
                        actividad = actividad,
                        titulo = "Desbloquear tus datos",
                        subtitulo = "Usa tu huella para acceder",
                        cifrador = cifrador,
                        onCorrecta = { autorizado ->
                            if (sesion.completarApertura(autorizado)) {
                                aviso = null
                            } else {
                                aviso = "La sesión guardada ya no se puede recuperar. Vuelve a iniciar sesión."
                            }
                        },
                        onFallo = { mensaje -> aviso = mensaje }
                    )
                }
            }
        )
    }
}

/**
 * Lanza el diálogo del sistema con el objeto criptográfico asociado.
 *
 * El cifrador que llega a onCorrecta NO es el que se pasó: es el que devuelve
 * el resultado del diálogo. Usar el otro no funcionaría, y ésa es toda la
 * diferencia entre comprobar la autenticación y depender de ella.
 */
private fun pedirAutenticacion(
    actividad: FragmentActivity,
    titulo: String,
    subtitulo: String,
    cifrador: Cipher,
    onCorrecta: (Cipher) -> Unit,
    onFallo: (String) -> Unit
) {
    val ejecutor = ContextCompat.getMainExecutor(actividad)

    val dialogo = BiometricPrompt(
        actividad,
        ejecutor,
        object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                val autorizado = resultado.cryptoObject?.cipher
                if (autorizado == null) {
                    onFallo("El sistema no ha devuelto el objeto criptográfico.")
                } else {
                    onCorrecta(autorizado)
                }
            }

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                // Cancelar es un resultado legítimo, no un error del programa.
                onFallo("No se ha completado la autenticación.")
            }

            override fun onAuthenticationFailed() {
                // Huella no reconocida: el diálogo sigue abierto, no hay que hacer nada.
            }
        }
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(titulo)
        .setSubtitle(subtitulo)
        .setNegativeButtonText("Cancelar")
        .build()

    dialogo.authenticate(info, BiometricPrompt.CryptoObject(cifrador))
}

@Composable
private fun PantallaDePuerta(
    titulo: String,
    explicacion: String,
    textoBoton: String,
    aviso: String?,
    onPulsar: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(explicacion, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPulsar) { Text(textoBoton) }

        if (aviso != null) {
            Spacer(Modifier.height(16.dp))
            Text(aviso, color = MaterialTheme.colorScheme.error)
        }
    }
}
