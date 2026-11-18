package es.uma.aiquetepillo

import android.content.Context
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
 * hereda. El anclaje de certificados de P3.2 ni siquiera pasa por ClienteRed:
 * lo aplica la plataforma a partir de la configuración de red declarada en el
 * manifiesto, sin que ninguna pantalla se entere.
 *
 * Desde P3.1:
 * - Es una FragmentActivity porque lo exige el diálogo biométrico, que se
 *   apoya en el gestor de fragmentos para sobrevivir a que se recree la
 *   actividad con el dedo en el sensor.
 * - La sesión es UNA instancia por proceso, compartida por el repositorio y
 *   la puerta biométrica (Dependencias): con dos, cada una tendría su propia
 *   copia del token.
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

        val (sesion, repositorio) = Dependencias.obtener(applicationContext)

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

/**
 * La sesión y el repositorio, UNO por proceso.
 *
 * La actividad se recrea al cambiar el tema, el idioma o el tamaño de letra,
 * y el VistaModelo sobrevive a esa recreación. Si cada onCreate construyera
 * su propia sesión, el VistaModelo seguiría con la anterior mientras la puerta
 * biométrica vigila la nueva: una sesión sin proteger podría pasar la puerta.
 * onCreate se ejecuta siempre en el hilo principal, así que no hace falta
 * sincronizar.
 */
private object Dependencias {

    private var creadas: Pair<SesionLocal, Repositorio>? = null

    fun obtener(contexto: Context): Pair<SesionLocal, Repositorio> =
        creadas ?: run {
            val sesion = SesionLocal(contexto)
            val repositorio = Repositorio(
                api = ClienteRed.crear(),
                sesion = sesion,
                dao = BaseDatos.obtener(contexto).misionDao(),
                fotos = AlmacenFotos(File(contexto.cacheDir, "fotos"))
            )
            sesion.alDescartar = { repositorio.borrarDatosLocales() }
            (sesion to repositorio).also { creadas = it }
        }
}

class FabricaVistaModelo(private val repositorio: Repositorio) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VistaModelo(repositorio) as T
}
