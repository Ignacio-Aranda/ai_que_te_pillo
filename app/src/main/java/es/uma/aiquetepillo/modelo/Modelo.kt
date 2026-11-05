package es.uma.aiquetepillo.modelo

import kotlinx.serialization.Serializable

/* ==========================================================================
   MODELADO DE DATOS Y DE ESTADOS
   Teoría: sesión 2.4, apartado 2.4.3 (clases de datos y clases selladas).

   Aquí no hay lógica: sólo la forma de los datos que entran y salen, y los
   estados por los que pasa una pantalla. Es el fichero más corto del proyecto
   y el que más decisiones de seguridad condiciona.
   ========================================================================== */

/** Petición que se envía a POST /api/login */
@Serializable
data class PeticionLogin(
    val usuario: String,
    val clave: String
)

/**
 * Petición de POST /api/registro. El selfie va en Base64 dentro del JSON: es
 * un dato biométrico y viaja igual que el resto, en claro por HTTP.
 */
@Serializable
data class PeticionRegistro(
    val usuario: String,
    val clave: String,
    val nombre: String,
    val selfie: String
)

/**
 * Respuesta de POST /api/login y de POST /api/registro.
 *
 * ATENCIÓN — sesión 2.4.3: esta clase guarda un token de sesión y, por ser una
 * clase de datos, el compilador le genera un toString() que incluye TODOS los
 * campos. Si en algún momento se registra este objeto, o aparece en el mensaje
 * de una excepción, el token sale completo en logcat.
 *
 * En Repositorio.kt hay una línea que lo registra a propósito, y en la sección
 * 5.1 de la práctica lo verás con tus propios ojos. En P3.1 lo corregirás.
 */
@Serializable
data class RespuestaLogin(
    val token: String,
    val usuario: String,
    val nombre: String,
    val rol: String
)

/**
 * Una misión devuelta por GET /api/misiones.
 *
 * `puntos` depende de la dificultad: 1 (fácil), 3 (media) o 5 (difícil).
 *
 * Los nombres de los campos tienen que coincidir con los del JSON del backend:
 * es kotlinx.serialization quien los empareja. Los valores por defecto hacen
 * que un campo que falte no tumbe la aplicación.
 */
@Serializable
data class Mision(
    val id: Int = 0,
    val titulo: String = "",
    val descripcion: String = "",
    val puntos: Int = 0,
    val fecha: String = "",
    val grupo: String = "",
    val objetivo: String = ""
)

/** Envoltorio de la respuesta de GET /api/misiones */
@Serializable
data class RespuestaMisiones(
    val misiones: List<Mision>
)

/** Un miembro de un grupo, con los puntos que lleva en él y si tiene selfie. */
@Serializable
data class Miembro(
    val usuario: String = "",
    val nombre: String = "",
    val puntos: Int = 0,
    val selfie: Boolean = false
)

/**
 * Un grupo de juego, devuelto por /api/grupos y /api/invitaciones/{codigo}.
 *
 * `codigo` es la invitación: quien lo conoce puede unirse al grupo.
 */
@Serializable
data class Grupo(
    val id: Int = 0,
    val nombre: String = "",
    val contexto: String = "",
    val codigo: String = "",
    val creador: String = "",
    val empezada: Boolean = false,
    val miembros: List<Miembro> = emptyList()
)

/** Envoltorio de la respuesta de GET /api/grupos */
@Serializable
data class RespuestaGrupos(
    val grupos: List<Grupo>
)

/** Petición de POST /api/grupos */
@Serializable
data class PeticionGrupo(
    val nombre: String,
    val contexto: String
)

/** Petición de POST /api/grupos/unirse */
@Serializable
data class PeticionUnirse(
    val codigo: String
)

/** Petición de POST /api/selfie: el selfie en Base64, para cuentas sin él. */
@Serializable
data class PeticionSelfie(
    val selfie: String
)

/** Petición de POST /api/misiones/{id}/foto: la foto en Base64. */
@Serializable
data class PeticionFoto(
    val foto: String
)

/**
 * Respuesta de POST /api/misiones/{id}/foto y /saltar. `puntos` es lo que se
 * suma (o se resta, al saltar); `motivo` es la explicación de la IA.
 */
@Serializable
data class ResultadoMision(
    val valida: Boolean = false,
    val motivo: String = "",
    val puntos: Int = 0
)

/**
 * Datos personales devueltos por GET /api/perfil.
 *
 * Fíjate en que aquí hay un DNI. Cuando en la sección 6.2 hagáis el inventario
 * de datos sensibles, éste es uno de los que hay que anotar.
 */
@Serializable
data class Perfil(
    val usuario: String = "",
    val nombre: String = "",
    val correo: String = "",
    val dni: String = "",
    val rol: String = ""
)

/* --------------------------------------------------------------------------
   ESTADOS DE LA INTERFAZ

   Una clase sellada: el compilador conoce TODAS las variantes posibles, así
   que un when que las cubra todas no necesita rama por defecto, y si mañana
   se añade una variante el compilador señala cada sitio que hay que revisar.

   Míralo en acción en ui/PantallaLogin.kt y ui/PantallaLista.kt.

   PARA EL CUADERNO: si añadieras aquí un estado SesionCaducada, ¿qué te diría
   el compilador? ¿Y si en las pantallas se hubiera usado un else genérico?
   -------------------------------------------------------------------------- */

sealed interface EstadoUi<out T> {
    data object Reposo : EstadoUi<Nothing>
    data object Cargando : EstadoUi<Nothing>
    data class Exito<T>(val datos: T) : EstadoUi<T>
    data class Error(val motivo: MotivoError) : EstadoUi<Nothing>
}

/**
 * Los motivos de error.
 *
 * Modelar el fallo como un estado, y no como una excepción suelta, obliga a
 * decidir qué se muestra al usuario en cada caso. Fíjate en que el motivo NO
 * lleva el mensaje crudo de la excepción: en la sesión 2.4.5 vimos que esos
 * mensajes suelen contener rutas, direcciones de servidor y detalles internos
 * que no deben llegar a la pantalla.
 *
 * Fíjate también en que no hay un motivo «UsuarioNoExiste» separado de
 * «ContraseñaIncorrecta». Es deliberado, y el Repositorio explica por qué.
 */
sealed interface MotivoError {
    data object SinConexion : MotivoError
    data object CredencialesNoValidas : MotivoError
    data object SesionCaducada : MotivoError
    data object CodigoNoValido : MotivoError
    data object UsuarioOcupado : MotivoError
    data object MisionNoActiva : MotivoError
    data object Desconocido : MotivoError
}
