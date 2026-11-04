package es.uma.aiquetepillo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.uma.aiquetepillo.modelo.*
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.MoradoClaro
import es.uma.aiquetepillo.ui.theme.MoradoOscuro

/* ==========================================================================
   PANTALLA DE DETALLE
   Teoría: sesión 2.5 (Compose, elevación de estado y navegación).

   Esta pantalla SE ENTREGA HECHA, igual que las demás pantallas del proyecto
   base. El motivo es el mismo que en P2.2: lo que se evalúa en esta asignatura
   no es tu destreza con Compose, sino lo que seas capaz de demostrar sobre la
   aplicación. Construir una pantalla más no te enseñaría nada nuevo sobre
   seguridad; en cambio, lo que esta pantalla NO hace sí va a enseñarte bastante
   dentro de un rato.

   Léela buscando una ausencia concreta: aquí no hay ni una sola comprobación
   de sesión. No se pregunta si hay token, ni si la misión es tuya, ni si
   deberías estar viendo esto. Eso no es un descuido ni una simplificación
   didáctica: es como se escriben las pantallas en un proyecto real, y es
   correcto que sea así. En el paso 4 comprobarás quién te detiene de verdad.
   ========================================================================== */

/**
 * Muestra una misión concreta.
 *
 * Aplica la elevación de estado de la sesión 2.5: el composable NO guarda el
 * misión ni sabe de dónde sale. Recibe un estado y dos funciones, y se limita
 * a dibujar. Toda la lógica vive en el modelo de vista y en el repositorio.
 *
 * Fíjate en los parámetros: `id` es una referencia, no contenido. Por la ruta
 * de navegación viaja el identificador y nada más, que es la regla 2.5.3.
 */
@Composable
fun PantallaDetalle(
    id: Int,
    estado: EstadoUi<Mision>,
    onCargar: (Int) -> Unit,
    onVolver: () -> Unit
) {

    /*
     * LaunchedEffect lanza la carga UNA vez por cada valor distinto de `id`, y
     * la cancela sola si la pantalla desaparece antes de terminar. Es la
     * concurrencia estructurada de la sesión 2.4.5 aplicada a la interfaz: si
     * escribieras onCargar(id) suelto en el cuerpo del composable, se lanzaría
     * una petición en cada redibujado.
     *
     * Consecuencia que importa para el paso 3: basta con que esta pantalla se
     * abra para que la aplicación pida la misión al servidor. Nadie tiene que
     * pulsar nada.
     */
    LaunchedEffect(id) { onCargar(id) }

    FondoJuego {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonRedondo("⬅️", "Volver", onVolver)
                Spacer(Modifier.weight(1f))
                TextoJuego("MISIÓN $id", tamano = 20.sp)
            }

            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                /*
                 * El when cubre las cuatro variantes de la interfaz sellada y NO
                 * lleva else. Si mañana añades un estado nuevo a EstadoUi, el
                 * compilador se parará aquí y te obligará a decidir qué se muestra.
                 * Compara esto con el when de PantallaLogin.kt, que sí tiene un
                 * else: allí un estado nuevo pasaría desapercibido.
                 */
                when (estado) {
                    is EstadoUi.Reposo -> CircularProgressIndicator(color = Color.White)
                    is EstadoUi.Cargando -> CircularProgressIndicator(color = Color.White)

                    is EstadoUi.Error -> Aviso(mensajeDeError(estado.motivo))

                    is EstadoUi.Exito -> TarjetaJuego(Modifier.fillMaxWidth(), fondo = MoradoOscuro) {
                        Medallon("🕵️", Modifier.align(Alignment.CenterHorizontally), tamano = 120.dp, fondo = MoradoClaro)
                        Spacer(Modifier.height(16.dp))
                        TextoJuego(estado.datos.titulo, Modifier.fillMaxWidth(), tamano = 28.sp, grosor = 9f)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            estado.datos.descripcion,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        Pastilla("⭐", puntosTexto(estado.datos.puntos), Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Creada el ${estado.datos.fecha}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Traduce el motivo del error a un texto para la persona que usa la aplicación.
 *
 * Igual que en PantallaLogin.kt, el mensaje no revela detalles internos: ni
 * direcciones de servidor, ni rutas, ni trazas de excepciones (sesión 2.4.5).
 *
 * PARA EL CUADERNO: cuando en el paso 4 abras esta pantalla sin haber iniciado
 * sesión, aquí aparecerá uno de estos cuatro mensajes. Anota CUÁL, porque te
 * dice exactamente qué capa de la aplicación te ha detenido, y esa es la
 * pregunta central de la práctica de hoy.
 */
private fun mensajeDeError(motivo: MotivoError): String = when (motivo) {
    MotivoError.SinConexion -> "No se ha podido conectar. Inténtalo de nuevo."
    MotivoError.CredencialesNoValidas -> "Usuario o contraseña incorrectos."
    MotivoError.SesionCaducada -> "La sesión ha caducado. Vuelve a entrar."
    MotivoError.Desconocido -> "Ha ocurrido un error. Inténtalo de nuevo."
}
