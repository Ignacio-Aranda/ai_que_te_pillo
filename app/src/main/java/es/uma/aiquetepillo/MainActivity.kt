package es.uma.aiquetepillo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import es.uma.aiquetepillo.datos.*
import es.uma.aiquetepillo.ui.Navegacion
import es.uma.aiquetepillo.ui.PuertaBiometrica
import es.uma.aiquetepillo.ui.theme.AiQueTePilloTheme
import java.io.File

/**
 * Punto de entrada.
 *
 * Las dependencias se construyen aquí a mano. En un proyecto real se usaría
 * inyección de dependencias (sesión 2.5.4); lo importante es la idea: el
 * cliente de red y el almacén se configuran UNA VEZ y toda la aplicación los
 * hereda. Cuando en la práctica P3.2 se añada anclaje de certificados, se
 * tocará sólo ClienteRed y no habrá que revisar ninguna pantalla.
 *
 * Desde P3.1:
 * - Es una FragmentActivity porque lo exige el diálogo biométrico, que se
 *   apoya en el gestor de fragmentos para sobrevivir a que se recree la
 *   actividad con el dedo en el sensor.
 * - La sesión es UNA instancia compartida por el repositorio y la puerta
 *   biométrica: con dos, cada una tendría su propia copia del token.
 * - La navegación va dentro de la puerta: mientras la sesión guardada esté
 *   bloqueada no se compone. El VistaModelo también se crea dentro, porque al
 *   crearse lee la sesión guardada para decidir si se empieza en la lista, y
 *   antes de desbloquear todavía no hay ninguna en memoria.
 * - Si la sesión guardada se descarta por irrecuperable, se borran también la
 *   copia local de las misiones y las fotos, que eran del usuario anterior.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val sesion = SesionLocal(applicationContext)

        val repositorio = Repositorio(
            api = ClienteRed.crear(),
            sesion = sesion,
            dao = BaseDatos.obtener(applicationContext).misionDao(),
            fotos = AlmacenFotos(File(cacheDir, "fotos"))
        )
        sesion.alDescartar = { repositorio.borrarDatosLocales() }

        setContent {
            AiQueTePilloTheme {
                PuertaBiometrica(actividad = this@MainActivity, sesion = sesion) {
                    val vistaModelo: VistaModelo = viewModel(factory = FabricaVistaModelo(repositorio))
                    Navegacion(vistaModelo)
                }
            }
        }
    }
}

class FabricaVistaModelo(private val repositorio: Repositorio) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VistaModelo(repositorio) as T
}
