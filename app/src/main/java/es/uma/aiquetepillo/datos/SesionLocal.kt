package es.uma.aiquetepillo.datos

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Cipher

/* ==========================================================================
   LA SESIÓN DEL USUARIO · versión cifrada y ligada a la huella
   Teoría: sesión 3.3, diapositivas 6, 12, 19 y 20. Práctica P3.1.

   Guarda el token, el usuario y el nombre visible para que la aplicación
   recuerde quién ha entrado entre un arranque y el siguiente. En disco solo
   queda un paquete cifrado con AES-GCM (Cripto), con una clave del Android
   Keystore que exige autenticación biométrica en cada uso.

   Como cada operación con esa clave pasa por el diálogo del sistema, que es
   asíncrono y necesita una pantalla, la sesión ya no se lee del disco cada
   vez que hace falta: se ABRE una vez con la huella y a partir de ahí vive en
   memoria mientras la aplicación está en marcha. El precio es que el token
   descifrado está en la memoria del proceso; el cifrado protege el disco, no
   el proceso.

   Los tres valores se guardan en UN SOLO campo: tres campos serían tres
   operaciones y tres huellas.

   Las funciones que usa el Repositorio (guardarSesion, obtenerToken,
   obtenerUsuario, obtenerNombre, cerrarSesion y haySesion) mantienen la
   misma firma que antes de cifrar.
   ========================================================================== */

class SesionLocal(contexto: Context) {

    private val preferencias: SharedPreferences =
        contexto.getSharedPreferences(NOMBRE_FICHERO, Context.MODE_PRIVATE)

    /** En qué situación está la sesión. La puerta biométrica la observa. */
    enum class Estado {
        /** No hay nada guardado: hay que iniciar sesión. */
        SIN_SESION,

        /** Se acaba de iniciar sesión y falta confirmar para guardarla cifrada. */
        PENDIENTE_DE_GUARDAR,

        /** Hay sesión guardada pero todavía no se ha desbloqueado. */
        BLOQUEADA,

        /** Desbloqueada: el token está en memoria y la aplicación puede trabajar. */
        ABIERTA
    }

    init {
        /*
         * Las versiones anteriores a P3.1 guardaban el token, el usuario y el
         * nombre en claro, cada uno en su campo. Cifrar a partir de ahora no
         * protege lo que ya estaba escrito, así que si quedan esos campos se
         * borran al arrancar: el usuario tendrá que volver a entrar.
         */
        if (preferencias.contains(CLAVE_TOKEN_EN_CLARO)) {
            preferencias.edit().clear().apply()
        }
    }

    private val _estado = MutableStateFlow(estadoInicial())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    /**
     * Lo que hay que borrar, además de la sesión, cuando se descarta por
     * irrecuperable. Lo fija MainActivity con la copia local de las misiones y
     * las fotos, que esta clase no conoce.
     */
    var alDescartar: () -> Unit = {}

    private var tokenEnMemoria: String? = null
    private var usuarioEnMemoria: String? = null
    private var nombreEnMemoria: String? = null

    private fun estadoInicial(): Estado =
        if (preferencias.contains(CLAVE_DATOS)) Estado.BLOQUEADA else Estado.SIN_SESION

    /* ----------------------------------------------------------------------
       LO QUE VE EL RESTO DE LA APLICACIÓN
       ---------------------------------------------------------------------- */

    /**
     * Deja la sesión en memoria y marca que falta persistirla.
     *
     * No puede escribir en disco todavía, porque cifrar exige autenticación y
     * esta función se llama desde una corrutina del repositorio, sin pantalla
     * a la vista. La puerta biométrica verá el estado y pedirá la huella.
     */
    fun guardarSesion(token: String, usuario: String, nombre: String) {
        tokenEnMemoria = token
        usuarioEnMemoria = usuario
        nombreEnMemoria = nombre
        _estado.value = Estado.PENDIENTE_DE_GUARDAR
    }

    fun obtenerToken(): String? = tokenEnMemoria

    /** El usuario de la sesión, o null. Sirve para saber de quién son los datos locales. */
    fun obtenerUsuario(): String? = usuarioEnMemoria

    /** El nombre que se muestra en la pantalla principal. */
    fun obtenerNombre(): String? = nombreEnMemoria

    fun haySesion(): Boolean = tokenEnMemoria != null

    fun cerrarSesion() {
        tokenEnMemoria = null
        usuarioEnMemoria = null
        nombreEnMemoria = null
        preferencias.edit().clear().apply()
        _estado.value = Estado.SIN_SESION
    }

    /* ----------------------------------------------------------------------
       LO QUE USA LA PUERTA BIOMÉTRICA

       El cifrador no se usa en estas funciones: se crea, se le entrega al
       diálogo del sistema y vuelve utilizable solo si la autenticación salió
       bien. Quien impone la condición es el almacén del sistema.
       ---------------------------------------------------------------------- */

    /** Cifrador para persistir la sesión recién iniciada. Va al diálogo. */
    fun cifradorParaGuardar(): Cipher = Cripto.cifradorParaCifrar()

    /** Cifrador para abrir la sesión guardada. Va al diálogo. */
    fun cifradorParaAbrir(): Cipher =
        Cripto.cifradorParaDescifrar(preferencias.getString(CLAVE_DATOS, null)!!)

    /**
     * Termina de guardar con el cifrador que devuelve el diálogo, el único
     * habilitado.
     *
     * Devuelve false si el cifrado falla pese a la autenticación correcta. En
     * ese caso se borra la clave, para que el siguiente intento cree una
     * nueva, y la sesión sigue pendiente de guardar.
     */
    fun completarGuardado(cifrador: Cipher): Boolean {
        val token = tokenEnMemoria ?: return false
        val claro = listOf(token, usuarioEnMemoria.orEmpty(), nombreEnMemoria.orEmpty())
            .joinToString(SEPARADOR_CAMPOS)
        return try {
            preferencias.edit().putString(CLAVE_DATOS, Cripto.empaquetar(cifrador, claro)).apply()
            _estado.value = Estado.ABIERTA
            true
        } catch (e: Exception) {
            Log.w("SesionLocal", "No se ha podido cifrar la sesión: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { Cripto.borrarClave() }
            false
        }
    }

    /**
     * Termina de abrir con el cifrador que devuelve el diálogo.
     *
     * Devuelve false si los datos ya no se pueden recuperar, y en ese caso los
     * descarta: no hay nada que reintentar.
     */
    fun completarApertura(cifrador: Cipher): Boolean {
        val paquete = preferencias.getString(CLAVE_DATOS, null) ?: return false
        return try {
            val campos = Cripto.desempaquetar(cifrador, paquete).split(SEPARADOR_CAMPOS)
            tokenEnMemoria = campos[0]
            usuarioEnMemoria = campos.getOrNull(1)
            nombreEnMemoria = campos.getOrNull(2)
            _estado.value = Estado.ABIERTA
            true
        } catch (e: Exception) {
            // Se registra QUE ha fallado y de qué tipo. Nunca el contenido.
            Log.w("SesionLocal", "Sesión irrecuperable: ${e.javaClass.simpleName}: ${e.message}")
            descartarSesionIrrecuperable()
            false
        }
    }

    /**
     * Cuando la clave ya no sirve (huella nueva, bloqueo de pantalla retirado).
     *
     * Se borran los datos cifrados, ilegibles para siempre, y la clave, que
     * puede estar invalidada: si se dejara, el siguiente inicio de sesión
     * fallaría igual. También se borran la copia local de las misiones y las
     * fotos (alDescartar): eran del usuario anterior, y quien entre después
     * puede ser otra persona.
     */
    fun descartarSesionIrrecuperable() {
        tokenEnMemoria = null
        usuarioEnMemoria = null
        nombreEnMemoria = null
        preferencias.edit().clear().apply()
        try {
            Cripto.borrarClave()
        } catch (e: Exception) {
            Log.w("SesionLocal", "No se ha podido borrar la clave: ${e.javaClass.simpleName}: ${e.message}")
        }
        alDescartar()
        _estado.value = Estado.SIN_SESION
    }

    companion object {
        const val NOMBRE_FICHERO = "sesion"

        /** Un único campo: una operación criptográfica, una autenticación. */
        const val CLAVE_DATOS = "datos"

        /** Separa token, usuario y nombre dentro del paquete cifrado. */
        private const val SEPARADOR_CAMPOS = "\u0000"

        /** Campo de las versiones anteriores a P3.1, con el token en claro. */
        private const val CLAVE_TOKEN_EN_CLARO = "token"
    }
}
