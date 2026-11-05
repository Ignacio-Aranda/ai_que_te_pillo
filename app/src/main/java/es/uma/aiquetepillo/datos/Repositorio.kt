package es.uma.aiquetepillo.datos

import android.util.Base64
import android.util.Log
import es.uma.aiquetepillo.modelo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/* ==========================================================================
   EL REPOSITORIO
   Teoría: sesión 2.5.4 (arquitectura por capas) y 2.4.5 (corrutinas).

   Es la ÚNICA puerta de acceso a los datos. La interfaz no sabe si la
   información viene de la red o de la base de datos local, y no debe saberlo.
   Cuando en el Bloque III se añada cifrado o anclaje de certificados se hará
   aquí y en el cliente de red, nunca en las pantallas.

   Fíjate en que todo el trabajo va dentro de withContext(Dispatchers.IO): es
   la regla de la sesión 2.4.5, nada de red ni de disco en el hilo principal.
   ========================================================================== */

class Repositorio(
    private val api: ApiServicio,
    private val sesion: SesionLocal,
    private val dao: MisionDao,
    private val fotos: AlmacenFotos
) {

    /**
     * Inicia sesión contra el backend y guarda el token si todo va bien.
     *
     * Devuelve un EstadoUi en lugar de lanzar excepciones: el fallo es un
     * estado más del flujo normal, como se vio en la sesión 2.4.5.
     */
    suspend fun iniciarSesion(usuario: String, clave: String): EstadoUi<RespuestaLogin> =
        withContext(Dispatchers.IO) {
            try {
                val respuesta = api.login(PeticionLogin(usuario, clave))

                /* ----------------------------------------------------------
                   EL REGISTRO DELIBERADO

                   Esta línea es el ejemplo de la sesión 2.4.3 puesto en una
                   aplicación de verdad. RespuestaLogin es una clase de datos,
                   así que su toString() lo genera el compilador e incluye
                   TODOS los campos... incluido el token.

                   Nadie ha escrito «imprime el token»: han escrito «imprime
                   el objeto». El resultado es el mismo.

                   En la sección 5.1 lo verás en logcat. Después prueba a
                   comentar esta línea, vuelve a ejecutar y comprueba que el
                   token desaparece: así demuestras la causa en lugar de
                   suponerla.
                   ---------------------------------------------------------- */
                Log.d("Repositorio", "Login correcto: $respuesta")

                sesion.guardarSesion(respuesta.token, respuesta.usuario, respuesta.nombre)
                EstadoUi.Exito(respuesta)

            } catch (e: HttpException) {
                /*
                 * El servidor ha respondido, pero con un código de error.
                 * 401 es contraseña incorrecta y 404 usuario inexistente: los
                 * tratamos IGUAL a propósito, para no confirmar qué cuentas
                 * existen. Es la idea de la sesión 2.4.5 sobre los mensajes
                 * que cuentan de más.
                 */
                when (e.code()) {
                    401, 404 -> EstadoUi.Error(MotivoError.CredencialesNoValidas)
                    else -> EstadoUi.Error(MotivoError.Desconocido)
                }
            } catch (e: IOException) {
                // No ha habido respuesta: red caída, backend apagado, dirección mal puesta
                EstadoUi.Error(MotivoError.SinConexion)
            } catch (e: Exception) {
                /*
                 * Cualquier otra cosa. Fíjate en lo que NO se hace: no se
                 * devuelve e.message a la interfaz. Esos mensajes llevan rutas,
                 * consultas y direcciones de servidor.
                 */
                EstadoUi.Error(MotivoError.Desconocido)
            }
        }

    /**
     * La sesión guardada de la vez anterior, si la hay: así la aplicación no
     * pide entrar cada vez que se abre. El rol no se guarda porque la
     * aplicación no lo usa.
     */
    fun sesionGuardada(): RespuestaLogin? {
        val token = sesion.obtenerToken() ?: return null
        val usuario = sesion.obtenerUsuario() ?: return null
        return RespuestaLogin(token, usuario, sesion.obtenerNombre() ?: usuario, rol = "")
    }

    /**
     * Crea la cuenta con su selfie y deja la sesión iniciada, igual que el
     * acceso. El selfie se borra del dispositivo en cuanto el servidor lo tiene.
     */
    suspend fun registrarse(usuario: String, clave: String, nombre: String): EstadoUi<RespuestaLogin> =
        withContext(Dispatchers.IO) {
            val selfie = jpegParaSubir(fotos.ficheroSelfie())
                ?: return@withContext EstadoUi.Error(MotivoError.Desconocido)
            try {
                val respuesta = api.registro(
                    PeticionRegistro(usuario, clave, nombre, Base64.encodeToString(selfie, Base64.NO_WRAP))
                )
                sesion.guardarSesion(respuesta.token, respuesta.usuario, respuesta.nombre)
                fotos.ficheroSelfie().delete()
                EstadoUi.Exito(respuesta)
            } catch (e: HttpException) {
                // 409: el usuario ya existe. Como en el acceso, el servidor lo confirma
                if (e.code() == 409) EstadoUi.Error(MotivoError.UsuarioOcupado)
                else EstadoUi.Error(MotivoError.Desconocido)
            } catch (e: IOException) {
                EstadoUi.Error(MotivoError.SinConexion)
            } catch (e: Exception) {
                EstadoUi.Error(MotivoError.Desconocido)
            }
        }

    /**
     * Descarga las misiones del usuario, guarda una copia local y las devuelve.
     * Si la red falla, devuelve la copia local en lugar de un error.
     */
    suspend fun obtenerMisiones(): EstadoUi<List<Mision>> =
        withContext(Dispatchers.IO) {

            /*
             * El operador ?: de la sesión 2.4.2 en su uso más típico: si no hay
             * token se sale de la función con un estado de error. No hay !!, no
             * hay comprobación que se pueda olvidar, y el compilador no deja
             * continuar con un valor que podría faltar.
             */
            val token = sesion.obtenerToken()
                ?: return@withContext EstadoUi.Error(MotivoError.SesionCaducada)
            val propietario = sesion.obtenerUsuario() ?: ""

            try {
                val respuesta = api.misiones("Bearer $token")

                // Copia local, marcada con su propietario
                dao.guardar(
                    respuesta.misiones.map { mision ->
                        MisionEntidad(
                            id = mision.id,
                            titulo = mision.titulo,
                            descripcion = mision.descripcion,
                            puntos = mision.puntos,
                            fecha = mision.fecha,
                            propietario = propietario
                        )
                    }
                )
                EstadoUi.Exito(respuesta.misiones)

            } catch (e: Exception) {
                /*
                 * Si la red falla recurrimos a la copia local. Éste es el
                 * motivo por el que existe la base de datos, y también el
                 * motivo por el que los datos del usuario están en el disco del
                 * dispositivo, sin cifrar, hasta que alguien los borre.
                 */
                val copiaLocal = dao.obtenerDe(propietario)
                if (copiaLocal.isEmpty()) {
                    EstadoUi.Error(MotivoError.SinConexion)
                } else {
                    EstadoUi.Exito(
                        copiaLocal.map { fila ->
                            Mision(fila.id, fila.titulo, fila.descripcion, fila.puntos, fila.fecha)
                        }
                    )
                }
            }
        }

    suspend fun obtenerPerfil(): EstadoUi<Perfil> = withContext(Dispatchers.IO) {
        val token = sesion.obtenerToken()
            ?: return@withContext EstadoUi.Error(MotivoError.SesionCaducada)
        try {
            EstadoUi.Exito(api.perfil("Bearer $token"))
        } catch (e: Exception) {
            EstadoUi.Error(MotivoError.Desconocido)
        }
    }

    suspend fun obtenerMision(id: Int): EstadoUi<Mision> =
        withContext(Dispatchers.IO) {
            val token = sesion.obtenerToken()
                ?: return@withContext EstadoUi.Error(MotivoError.SesionCaducada)
            try {
                EstadoUi.Exito(api.mision("Bearer $token", id))
            } catch (e: IOException) {
                EstadoUi.Error(MotivoError.SinConexion)
            } catch (e: Exception) {
                EstadoUi.Error(MotivoError.Desconocido)
            }
        }

    /** Los grupos del usuario. Sin copia local: sin red no se muestran. */
    suspend fun obtenerGrupos(): EstadoUi<List<Grupo>> =
        conToken { token -> api.grupos(token).grupos }

    suspend fun crearGrupo(nombre: String, contexto: String): EstadoUi<Grupo> =
        conToken { token -> api.crearGrupo(token, PeticionGrupo(nombre, contexto)) }

    /** El grupo al que lleva un código, para confirmar antes de unirse. */
    suspend fun verInvitacion(codigo: String): EstadoUi<Grupo> =
        conToken(si404 = MotivoError.CodigoNoValido) { token -> api.invitacion(token, codigo.trim().uppercase()) }

    suspend fun unirse(codigo: String): EstadoUi<Grupo> =
        conToken(si404 = MotivoError.CodigoNoValido) { token ->
            api.unirse(token, PeticionUnirse(codigo.trim().uppercase()))
        }

    /** Empieza la partida: el servidor pide a la IA una misión para cada jugador. */
    suspend fun empezarPartida(idGrupo: Int): EstadoUi<Grupo> =
        conToken { token -> api.empezar(token, idGrupo) }

    /**
     * Sube la foto de una misión para que la IA la compruebe. Si la da por
     * buena, la copia local ya no hace falta y se borra.
     */
    suspend fun enviarFoto(idMision: Int): EstadoUi<ResultadoMision> {
        val foto = withContext(Dispatchers.IO) { jpegParaSubir(fotos.ficheroPara(idMision)) }
            ?: return EstadoUi.Error(MotivoError.Desconocido)
        val resultado = conToken(si404 = MotivoError.MisionNoActiva) { token ->
            api.enviarFoto(token, idMision, PeticionFoto(Base64.encodeToString(foto, Base64.NO_WRAP)))
        }
        if (resultado is EstadoUi.Exito && resultado.datos.valida) fotos.borrar(idMision)
        return resultado
    }

    suspend fun saltarMision(idMision: Int): EstadoUi<ResultadoMision> {
        val resultado = conToken(si404 = MotivoError.MisionNoActiva) { token -> api.saltar(token, idMision) }
        if (resultado is EstadoUi.Exito) fotos.borrar(idMision)
        return resultado
    }

    /**
     * Ejecuta una llamada autenticada y traduce el resultado a un EstadoUi.
     * `si404` dice qué significa que el servidor no encuentre lo pedido.
     */
    private suspend fun <T> conToken(
        si404: MotivoError = MotivoError.Desconocido,
        llamada: suspend (String) -> T
    ): EstadoUi<T> =
        withContext(Dispatchers.IO) {
            val token = sesion.obtenerToken()
                ?: return@withContext EstadoUi.Error(MotivoError.SesionCaducada)
            try {
                EstadoUi.Exito(llamada("Bearer $token"))
            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> EstadoUi.Error(MotivoError.SesionCaducada)
                    404 -> EstadoUi.Error(si404)
                    else -> EstadoUi.Error(MotivoError.Desconocido)
                }
            } catch (e: IOException) {
                EstadoUi.Error(MotivoError.SinConexion)
            } catch (e: Exception) {
                EstadoUi.Error(MotivoError.Desconocido)
            }
        }

    /**
     * Cierra la sesión.
     *
     * Borra todo lo que el usuario deja en el dispositivo: sus misiones de la
     * copia local, sus fotos y, por último, el token y el usuario de las
     * preferencias. Las preferencias van al final porque de ellas se lee de
     * quién son las filas que hay que borrar (P2.2, sección 5.6).
     */
    suspend fun cerrarSesion() {
        withContext(Dispatchers.IO) {
            val propietario = sesion.obtenerUsuario()
            if (propietario != null) {
                dao.borrarDe(propietario)
            }
            fotos.borrarTodas()
            sesion.cerrarSesion()
        }
    }

    /** Fichero donde se guarda el selfie hasta que se sube con el registro. */
    fun ficheroSelfie(): File = fotos.ficheroSelfie()

    /** Fichero donde se guarda la foto de una misión. */
    fun ficheroFoto(idMision: Int): File = fotos.ficheroPara(idMision)

    /** Las fotos ya hechas, por identificador de misión. */
    fun fotosGuardadas(): Map<Int, File> = fotos.existentes()
}
