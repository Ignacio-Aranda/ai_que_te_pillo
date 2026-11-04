package es.uma.aiquetepillo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.uma.aiquetepillo.datos.Repositorio
import es.uma.aiquetepillo.modelo.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Modelo de vista de la aplicación.
 *
 * Sostiene el estado de las pantallas y lanza el trabajo en viewModelScope, de
 * modo que las corrutinas se cancelan solas cuando la pantalla desaparece
 * (sesión 2.4.5, concurrencia estructurada). Si el usuario sale mientras una
 * descarga está en marcha, esa descarga se cancela: no termina después y
 * escribe datos de la sesión anterior.
 *
 * Está completo. En P2.2 sólo hay que leerlo.
 */
class VistaModelo(private val repositorio: Repositorio) : ViewModel() {

    private val _estadoLogin = MutableStateFlow<EstadoUi<RespuestaLogin>>(EstadoUi.Reposo)
    val estadoLogin: StateFlow<EstadoUi<RespuestaLogin>> = _estadoLogin.asStateFlow()

    private val _estadoLista = MutableStateFlow<EstadoUi<List<Mision>>>(EstadoUi.Reposo)
    val estadoLista: StateFlow<EstadoUi<List<Mision>>> = _estadoLista.asStateFlow()

    private val _estadoDetalle = MutableStateFlow<EstadoUi<Mision>>(EstadoUi.Reposo)
    val estadoDetalle: StateFlow<EstadoUi<Mision>> = _estadoDetalle.asStateFlow()

    /** Fotos hechas, por misión. Se cargan del disco al arrancar. */
    private val _fotos = MutableStateFlow(
        repositorio.fotosGuardadas().mapValues { (_, fichero) -> FotoMision(fichero, fichero.lastModified()) }
    )
    val fotos: StateFlow<Map<Int, FotoMision>> = _fotos.asStateFlow()

    fun ficheroFoto(idMision: Int): File = repositorio.ficheroFoto(idMision)

    /** Avisa de que la cámara ha guardado la foto de una misión. */
    fun fotoHecha(idMision: Int) {
        val fichero = repositorio.ficheroFoto(idMision)
        _fotos.value = _fotos.value + (idMision to FotoMision(fichero, fichero.lastModified()))
    }

    fun entrar(usuario: String, clave: String) {
        viewModelScope.launch {
            _estadoLogin.value = EstadoUi.Cargando
            _estadoLogin.value = repositorio.iniciarSesion(usuario, clave)
        }
    }

    fun cargarMisiones() {
        viewModelScope.launch {
            _estadoLista.value = EstadoUi.Cargando
            _estadoLista.value = repositorio.obtenerMisiones()
        }
    }

    fun cargarDetalle(id: Int) {
        viewModelScope.launch {
            _estadoDetalle.value = EstadoUi.Cargando
            _estadoDetalle.value = repositorio.obtenerMision(id)
        }
    }

    /**
     * Cierre de sesión.
     *
     * Devuelve las pantallas a Reposo y después pide al repositorio que borre
     * la sesión. El orden no es decorativo: si el estado de acceso siguiera en
     * Exito mientras se borra la copia local, la pantalla de acceso volvería a
     * entrar sola en la lista.
     *
     * PARA EL CUADERNO: además del estado del modelo de vista y del token,
     * ¿qué otros rastros del usuario quedan en el dispositivo? Haz la lista
     * antes de seguir y compruébala en la sección 5.5.
     */
    fun salir() {
        _estadoLogin.value = EstadoUi.Reposo
        _estadoLista.value = EstadoUi.Reposo
        _estadoDetalle.value = EstadoUi.Reposo
        _fotos.value = emptyMap()
        viewModelScope.launch {
            repositorio.cerrarSesion()
        }
    }
}

/**
 * Una foto guardada. `version` cambia cada vez que se repite la foto, aunque el
 * fichero sea el mismo, para que la pantalla vuelva a cargarla.
 */
data class FotoMision(val fichero: File, val version: Long)
