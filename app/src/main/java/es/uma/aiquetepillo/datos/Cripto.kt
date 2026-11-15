package es.uma.aiquetepillo.datos

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/* ==========================================================================
   LA CLAVE Y LAS OPERACIONES
   Teoría: sesión 3.3, diapositivas 6, 9 y 12.

   Este fichero es TODO lo que la aplicación sabe de criptografía. Fíjate en
   lo que NO hay aquí: en ninguna línea aparece el material de una clave.
   Hay un nombre, unos propósitos, un modo y un tamaño. Nada más.

   La aplicación no crea la clave: le pide al sistema que la cree, y recibe a
   cambio una referencia. El material vive en el almacén del sistema y no sale
   de ahí nunca, ni siquiera para esta clase, que es la que lo encargó.
   ========================================================================== */

object Cripto {

    /** El nombre con el que la aplicación se refiere a la clave. No es la clave. */
    const val ALIAS = "clave_datos"

    private const val ALMACEN = "AndroidKeyStore"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"

    /**
     * Longitud en bits de la etiqueta de autenticación de GCM.
     *
     * Es la que permite detectar que alguien ha modificado el texto cifrado.
     * 128 es el valor completo: no lo bajes para "ahorrar espacio".
     */
    private const val BITS_ETIQUETA = 128

    /** Separa el nonce del texto cifrado dentro de la misma cadena guardada. */
    private const val SEPARADOR = ":"

    /* ----------------------------------------------------------------------
       LA CLAVE
       ---------------------------------------------------------------------- */

    /**
     * Devuelve la clave, creándola la primera vez.
     *
     * A partir de ahí se recupera por su nombre: NO se genera una clave nueva
     * en cada arranque. Si lo hiciera, todo lo cifrado en la ejecución
     * anterior quedaría irrecuperable.
     */
    fun obtenerOCrearClave(): SecretKey {
        val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }

        val existente = almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existente != null) return existente.secretKey

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // ------------------------------------------------------------
                // PASO 4 DE LA PRÁCTICA: las dos condiciones de autenticación.
                // La clave sólo se puede usar tras autenticar al usuario, y se
                // invalida si se registra una biometría nueva en el terminal.
                // ------------------------------------------------------------
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return generador.generateKey()
    }

    /** Si la clave existe todavía. Al retirar el bloqueo de pantalla, el sistema la destruye. */
    fun existeClave(): Boolean =
        KeyStore.getInstance(ALMACEN).apply { load(null) }.containsAlias(ALIAS)

    /**
     * Destruye la clave.
     *
     * Después de esto, todo lo que se cifró con ella es irrecuperable. Se usa
     * cuando la aplicación detecta que la clave ya no sirve y tiene que
     * empezar de cero.
     */
    fun borrarClave() {
        KeyStore.getInstance(ALMACEN).apply { load(null) }.deleteEntry(ALIAS)
    }

    /* ----------------------------------------------------------------------
       LOS CIFRADORES

       Están separados de las operaciones a propósito. En el paso 4, el
       cifrador no se usará directamente: se le entregará al diálogo
       biométrico, y sólo volverá utilizable si la autenticación salió bien.
       ---------------------------------------------------------------------- */

    /** Un cifrador listo para cifrar. El sistema le asigna un nonce nuevo. */
    fun cifradorParaCifrar(): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())
        }

    /**
     * Un cifrador listo para descifrar ese paquete concreto.
     *
     * Hay que devolverle el nonce con el que se cifró, y por eso el nonce se
     * guarda junto al texto cifrado. No es un secreto: es un dato necesario.
     */
    fun cifradorParaDescifrar(paquete: String): Cipher {
        val nonce = Base64.decode(paquete.substringBefore(SEPARADOR), Base64.NO_WRAP)
        return Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, obtenerOCrearClave(), GCMParameterSpec(BITS_ETIQUETA, nonce))
        }
    }

    /* ----------------------------------------------------------------------
       LAS OPERACIONES
       ---------------------------------------------------------------------- */

    /**
     * Cifra y empaqueta en una sola cadena: nonce y texto cifrado, en base64,
     * separados por dos puntos.
     *
     * Las preferencias guardan texto, no bytes, de ahí el base64. Y el nonce
     * va DENTRO del paquete porque sin él no se puede descifrar: guardarlos
     * separados es una forma estupenda de perder los datos.
     *
     * LA PROHIBICIÓN NÚMERO UNO (sesión 3.3, diapositiva 12): el nonce lo
     * genera el sistema en cada llamada, al hacer init(). No lo fijes, no lo
     * derives y no lo reutilices. Un nonce fijo cifra, descifra, pasa todas
     * las pruebas y está roto.
     */
    fun empaquetar(cifrador: Cipher, texto: String): String {
        val cifrado = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifrador.iv, Base64.NO_WRAP) +
            SEPARADOR +
            Base64.encodeToString(cifrado, Base64.NO_WRAP)
    }

    /**
     * Deshace lo anterior.
     *
     * Si el texto cifrado ha sido modificado, doFinal() lanza
     * AEADBadTagException y no devuelve nada. Eso no es un fallo: es el modo
     * autenticado haciendo su trabajo.
     */
    fun desempaquetar(cifrador: Cipher, paquete: String): String {
        val cifrado = Base64.decode(paquete.substringAfter(SEPARADOR), Base64.NO_WRAP)
        return String(cifrador.doFinal(cifrado), Charsets.UTF_8)
    }

    /* ----------------------------------------------------------------------
       ATAJOS PARA LOS PASOS 1 A 3

       Mientras la clave no exige autenticación, el cifrador se puede crear y
       usar en la misma línea. En el paso 4 dejan de valer, y la práctica te
       explica por qué.
       ---------------------------------------------------------------------- */

    fun cifrar(texto: String): String = empaquetar(cifradorParaCifrar(), texto)

    fun descifrar(paquete: String): String = desempaquetar(cifradorParaDescifrar(paquete), paquete)
}
