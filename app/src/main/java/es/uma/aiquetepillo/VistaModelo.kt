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

    /** Arranca con la sesión guardada, si la hay: no hace falta volver a entrar. */
    private val _estadoLogin = MutableStateFlow<EstadoUi<RespuestaLogin>>(
        repositorio.sesionGuardada()?.let { EstadoUi.Exito(it) } ?: EstadoUi.Reposo
    )
    val estadoLogin: StateFlow<EstadoUi<RespuestaLogin>> = _estadoLogin.asStateFlow()

    private val _estadoLista = MutableStateFlow<EstadoUi<List<Mision>>>(EstadoUi.Reposo)
    val estadoLista: StateFlow<EstadoUi<List<Mision>>> = _estadoLista.asStateFlow()

    private val _estadoDetalle = MutableStateFlow<EstadoUi<Mision>>(EstadoUi.Reposo)
    val estadoDetalle: StateFlow<EstadoUi<Mision>> = _estadoDetalle.asStateFlow()

    private val _estadoGrupos = MutableStateFlow<EstadoUi<List<Grupo>>>(EstadoUi.Reposo)
    val estadoGrupos: StateFlow<EstadoUi<List<Grupo>>> = _estadoGrupos.asStateFlow()

    /** Vista previa del grupo de un código de invitación. */
    private val _estadoInvitacion = MutableStateFlow<EstadoUi<Grupo>>(EstadoUi.Reposo)
    val estadoInvitacion: StateFlow<EstadoUi<Grupo>> = _estadoInvitacion.asStateFlow()

    /** Resultado de crear un grupo o de unirse a uno. */
    private val _estadoAltaGrupo = MutableStateFlow<EstadoUi<Grupo>>(EstadoUi.Reposo)
    val estadoAltaGrupo: StateFlow<EstadoUi<Grupo>> = _estadoAltaGrupo.asStateFlow()

    private val _estadoRegistro = MutableStateFlow<EstadoUi<RespuestaLogin>>(EstadoUi.Reposo)
    val estadoRegistro: StateFlow<EstadoUi<RespuestaLogin>> = _estadoRegistro.asStateFlow()

    /** Subida del selfie de una cuenta que no lo tiene. */
    private val _estadoSelfie = MutableStateFlow<EstadoUi<Unit>>(EstadoUi.Reposo)
    val estadoSelfie: StateFlow<EstadoUi<Unit>> = _estadoSelfie.asStateFlow()

    /** Selfie hecho para el registro, hasta que se sube. */
    private val _selfie = MutableStateFlow<Foto?>(null)
    val selfie: StateFlow<Foto?> = _selfie.asStateFlow()

    private val _estadoPartida = MutableStateFlow<EstadoUi<Grupo>>(EstadoUi.Reposo)
    val estadoPartida: StateFlow<EstadoUi<Grupo>> = _estadoPartida.asStateFlow()

    /** Resultado de la última foto enviada o de la última misión saltada. */
    private val _estadoMision = MutableStateFlow<EstadoUi<ResultadoMision>>(EstadoUi.Reposo)
    val estadoMision: StateFlow<EstadoUi<ResultadoMision>> = _estadoMision.asStateFlow()

    /** Fotos hechas, por misión. Se cargan del disco al arrancar. */
    private val _fotos = MutableStateFlow(leerFotos())
    val fotos: StateFlow<Map<Int, Foto>> = _fotos.asStateFlow()

    private fun leerFotos(): Map<Int, Foto> =
        repositorio.fotosGuardadas().mapValues { (_, fichero) -> Foto(fichero, fichero.lastModified()) }

    fun ficheroFoto(idMision: Int): File = repositorio.ficheroFoto(idMision)

    /** Avisa de que la cámara ha guardado la foto de una misión. */
    fun fotoHecha(idMision: Int) {
        val fichero = repositorio.ficheroFoto(idMision)
        _fotos.value = _fotos.value + (idMision to Foto(fichero, fichero.lastModified()))
        olvidarResultadoMision()
    }

    /** El veredicto anterior deja de verse al hacer otra foto o cambiar de misión. */
    fun olvidarResultadoMision() {
        _estadoMision.value = EstadoUi.Reposo
    }

    fun ficheroSelfie(): File = repositorio.ficheroSelfie()

    /** Avisa de que la cámara ha guardado el selfie. */
    fun selfieHecho() {
        val fichero = repositorio.ficheroSelfie()
        _selfie.value = Foto(fichero, fichero.lastModified())
    }

    /** Sube el selfie recién hecho y, si sale bien, recarga los grupos para que deje de pedirse. */
    fun subirSelfie() {
        viewModelScope.launch {
            _estadoSelfie.value = EstadoUi.Cargando
            val resultado = repositorio.subirSelfie()
            _estadoSelfie.value = resultado
            if (resultado is EstadoUi.Exito) cargarGrupos()
        }
    }

    /** Crea la cuenta y, si sale bien, deja la sesión iniciada como el acceso. */
    fun registrarse(usuario: String, clave: String, nombre: String) {
        viewModelScope.launch {
            _estadoRegistro.value = EstadoUi.Cargando
            val resultado = repositorio.registrarse(usuario, clave, nombre)
            _estadoRegistro.value = resultado
            if (resultado is EstadoUi.Exito) {
                _selfie.value = null
                _estadoLogin.value = resultado
            }
        }
    }

    fun olvidarRegistro() {
        _estadoRegistro.value = EstadoUi.Reposo
    }

    fun entrar(usuario: String, clave: String) {
        viewModelScope.launch {
            _estadoLogin.value = EstadoUi.Cargando
            _estadoLogin.value = repositorio.iniciarSesion(usuario, clave)
        }
    }

    /** Al recargar, la lista que ya se ve se mantiene hasta que llega la nueva. */
    fun cargarMisiones() {
        viewModelScope.launch {
            if (_estadoLista.value !is EstadoUi.Exito) _estadoLista.value = EstadoUi.Cargando
            _estadoLista.value = repositorio.obtenerMisiones()
        }
    }

    fun cargarDetalle(id: Int) {
        viewModelScope.launch {
            _estadoDetalle.value = EstadoUi.Cargando
            _estadoDetalle.value = repositorio.obtenerMision(id)
        }
    }

    fun cargarGrupos() {
        viewModelScope.launch {
            if (_estadoGrupos.value !is EstadoUi.Exito) _estadoGrupos.value = EstadoUi.Cargando
            _estadoGrupos.value = repositorio.obtenerGrupos()
        }
    }

    fun crearGrupo(nombre: String, contexto: String) {
        darDeAlta { repositorio.crearGrupo(nombre, contexto) }
    }

    fun verInvitacion(codigo: String) {
        viewModelScope.launch {
            _estadoInvitacion.value = EstadoUi.Cargando
            _estadoInvitacion.value = repositorio.verInvitacion(codigo)
        }
    }

    fun unirse(codigo: String) {
        darDeAlta { repositorio.unirse(codigo) }
    }

    /** Limpia los resultados anteriores antes de abrir las pantallas de grupo. */
    fun olvidarAltaGrupo() {
        _estadoInvitacion.value = EstadoUi.Reposo
        _estadoAltaGrupo.value = EstadoUi.Reposo
        _estadoPartida.value = EstadoUi.Reposo
        _estadoSelfie.value = EstadoUi.Reposo
    }

    /** Empieza la partida y, si sale bien, recarga los grupos y las misiones nuevas. */
    fun empezarPartida(idGrupo: Int) {
        viewModelScope.launch {
            _estadoPartida.value = EstadoUi.Cargando
            val resultado = repositorio.empezarPartida(idGrupo)
            _estadoPartida.value = resultado
            if (resultado is EstadoUi.Exito) {
                cargarGrupos()
                cargarMisiones()
            }
        }
    }

    fun enviarFoto(idMision: Int) {
        actuarSobreMision { repositorio.enviarFoto(idMision) }
    }

    fun saltarMision(idMision: Int) {
        actuarSobreMision { repositorio.saltarMision(idMision) }
    }

    /**
     * Envía la foto o salta la misión. Si cambian los puntos, el servidor ya ha
     * puesto otra misión en su lugar: se recargan misiones, grupos y fotos. Si
     * la misión ya no estaba activa (la ha cambiado otro dispositivo), también.
     */
    private fun actuarSobreMision(operacion: suspend () -> EstadoUi<ResultadoMision>) {
        viewModelScope.launch {
            _estadoMision.value = EstadoUi.Cargando
            val resultado = operacion()
            _estadoMision.value = resultado
            val cambiada = resultado is EstadoUi.Exito && resultado.datos.puntos != 0
            if (cambiada || (resultado as? EstadoUi.Error)?.motivo == MotivoError.MisionNoActiva) {
                _fotos.value = leerFotos()
                cargarMisiones()
                cargarGrupos()
            }
        }
    }

    /** Crea o une y, si sale bien, vuelve a pedir la lista de grupos. */
    private fun darDeAlta(operacion: suspend () -> EstadoUi<Grupo>) {
        viewModelScope.launch {
            _estadoAltaGrupo.value = EstadoUi.Cargando
            val resultado = operacion()
            _estadoAltaGrupo.value = resultado
            if (resultado is EstadoUi.Exito) cargarGrupos()
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
        _estadoGrupos.value = EstadoUi.Reposo
        _estadoRegistro.value = EstadoUi.Reposo
        _estadoMision.value = EstadoUi.Reposo
        _selfie.value = null
        olvidarAltaGrupo()
        _fotos.value = emptyMap()
        viewModelScope.launch {
            repositorio.cerrarSesion()
        }
    }
}

/**
 * Una foto guardada (de una misión o el selfie). `version` cambia cada vez que
 * se repite la foto, aunque el fichero sea el mismo, para que la pantalla
 * vuelva a cargarla.
 */
data class Foto(val fichero: File, val version: Long)
