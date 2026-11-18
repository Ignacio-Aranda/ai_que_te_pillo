package es.uma.aiquetepillo.datos

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import es.uma.aiquetepillo.modelo.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Invocation
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/* ==========================================================================
   EL ACCESO AL BACKEND
   Teoría: sesión 2.5.5 (red) y 2.4.5 (corrutinas).

   Desde P3.2 el backend sirve por TLS. La política de red (tráfico en claro
   prohibido, anclas de confianza y anclaje de la clave del servidor) está
   declarada en res/xml/network_security_config.xml, no en este fichero.
   ========================================================================== */

/**
 * La interfaz describe la API: una función por endpoint. Retrofit genera en
 * tiempo de ejecución la clase que hace las peticiones de verdad, así que aquí
 * no hay ni una línea de código de red.
 *
 * Todas son `suspend`, de modo que sólo pueden llamarse desde una corrutina y
 * nunca bloquean el hilo de la interfaz.
 */
interface ApiServicio {

    /** El cuerpo se serializa a JSON a partir de la clase de datos. */
    @POST("api/login")
    suspend fun login(@Body peticion: PeticionLogin): RespuestaLogin

    @POST("api/registro")
    suspend fun registro(@Body peticion: PeticionRegistro): RespuestaLogin

    /** El selfie llega como JPEG, no como JSON: se lee el cuerpo tal cual. */
    @GET("api/usuarios/{usuario}/selfie")
    suspend fun selfieDe(
        @Header("Authorization") autorizacion: String,
        @Path("usuario") usuario: String
    ): okhttp3.ResponseBody

    @POST("api/selfie")
    suspend fun subirSelfie(
        @Header("Authorization") autorizacion: String,
        @Body peticion: PeticionSelfie
    )

    /**
     * La cabecera de autorización viaja como parámetro: quien llame tiene que
     * componer el valor "Bearer <token>". Lo hace el Repositorio.
     */
    @GET("api/misiones")
    suspend fun misiones(@Header("Authorization") autorizacion: String): RespuestaMisiones

    @GET("api/misiones/{id}")
    suspend fun mision(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Int
    ): Mision

    @GET("api/grupos")
    suspend fun grupos(@Header("Authorization") autorizacion: String): RespuestaGrupos

    @POST("api/grupos")
    suspend fun crearGrupo(
        @Header("Authorization") autorizacion: String,
        @Body peticion: PeticionGrupo
    ): Grupo

    @GET("api/invitaciones/{codigo}")
    suspend fun invitacion(
        @Header("Authorization") autorizacion: String,
        @Path("codigo") codigo: String
    ): Grupo

    @POST("api/grupos/unirse")
    suspend fun unirse(
        @Header("Authorization") autorizacion: String,
        @Body peticion: PeticionUnirse
    ): Grupo

    @POST("api/grupos/{id}/empezar")
    suspend fun empezar(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Int
    ): Grupo

    @POST("api/misiones/{id}/foto")
    suspend fun enviarFoto(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Int,
        @Body peticion: PeticionFoto
    ): ResultadoMision

    @POST("api/misiones/{id}/saltar")
    suspend fun saltar(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Int
    ): ResultadoMision
}

object ClienteRed {

    /**
     * Dirección del backend, servido por TLS en el puerto 8443.
     *
     * El emulador tiene su propia interfaz de red: dentro de él, localhost es
     * el propio emulador y no tu ordenador. De ahí el 10.0.2.2. Para un
     * dispositivo físico haría falta la IP del ordenador aquí, en el
     * certificado del backend (EXTRA=<IP> ./generar_certificado.sh) y en el
     * dominio de network_security_config.xml, en sus dos variantes.
     *
     * PARA EL CUADERNO: esta dirección queda escrita en el artefacto y se
     * recupera decompilándolo, tal como comprobaste en la práctica P2.1.
     * ¿Es eso un problema? ¿Y si en lugar de una dirección fuera una clave?
     */
    const val BASE_URL = "https://10.0.2.2:8443/"

    fun crear(): ApiServicio {

        /*
         * Registro de la red. Con el HttpLoggingInterceptor en nivel BODY
         * salían en logcat cabeceras y cuerpos: la contraseña del acceso, el
         * token, la cabecera Authorization, los selfies y fotos en base64 y
         * el contenido de las misiones. Ni siquiera el nivel BASIC basta,
         * porque imprime la URL y en la ruta van el código de invitación de
         * un grupo y los nombres de usuario de sus miembros.
         *
         * Desde P3.1 se registra solo el verbo HTTP, el nombre de la función
         * de ApiServicio que hizo la llamada (Retrofit lo deja en la etiqueta
         * Invocation de la petición) y el código de respuesta. Sigue sirviendo
         * para ver qué tráfico hay, sin sacar ningún dato.
         *
         * Si la llamada no llega a tener respuesta, se registra el tipo y el
         * mensaje de la excepción: desde P3.2 es la única forma de distinguir
         * un rechazo del anclaje («Pin verification failed») o de la cadena de
         * confianza de un fallo de red, porque la pantalla muestra en todos los
         * casos el mismo mensaje genérico.
         */
        val registro = Interceptor { cadena ->
            val peticion = cadena.request()
            val operacion = peticion.tag(Invocation::class.java)?.method()?.name ?: "?"
            val respuesta = try {
                cadena.proceed(peticion)
            } catch (e: IOException) {
                Log.w("Red", "${peticion.method} $operacion -> ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
            Log.d("Red", "${peticion.method} $operacion -> ${respuesta.code}")
            respuesta
        }

        /*
         * El cliente HTTP se construye UNA VEZ y lo hereda toda la aplicación.
         * Es la idea de la sesión 2.5.4: cualquier cambio en el cliente HTTP
         * se hace sólo aquí, y ninguna pantalla se entera. El anclaje de
         * certificados de P3.2 no pasa por aquí: lo aplica la plataforma a
         * partir de la configuración de red declarada en el manifiesto.
         */
        val cliente = OkHttpClient.Builder()
            .addInterceptor(registro)
            // Empezar la partida y comprobar una foto esperan a la IA del servidor
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        /*
         * ignoreUnknownKeys = true evita que la aplicación se caiga si el
         * servidor añade un campo que nosotros no conocemos. Es tolerancia
         * razonable, y no debe confundirse con validar la respuesta, que es
         * otra cosa y se trata en la sesión 2.5.5.
         */
        val json = Json { ignoreUnknownKeys = true }

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiServicio::class.java)
    }
}
