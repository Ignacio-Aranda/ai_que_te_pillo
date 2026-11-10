package es.uma.aiquetepillo.datos

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/* ==========================================================================
   EL CIFRADO DE LAS FOTOS
   Teoría: sesión 3.3, diapositivas 6 y 12. Práctica P3.1, parte de grupo.

   Las fotos (el selfie de registro, los selfies de los miembros y la foto de
   cada misión) se guardan cifradas con AES-256-GCM. Cada fichero contiene el
   nonce de 12 bytes seguido del texto cifrado con su etiqueta.

   La clave NO es la de la sesión. La de la sesión exige la huella en cada
   operación, y las fotos se leen continuamente mientras se juega (avatares,
   miniaturas, el envío a la IA): pedir la huella en cada una no es viable.
   Esta clave no pide autenticación por uso, pero solo funciona con el
   terminal desbloqueado (setUnlockedDeviceRequired, Android 9 o superior):
   con el teléfono bloqueado, el almacén se niega a descifrar.

   Lo que protege: los ficheros copiados fuera de la aplicación (run-as sobre
   la variante depurable, una copia de seguridad, un volcado del
   almacenamiento) no se pueden abrir, porque la clave no sale del almacén.
   Lo que no protege: quien ejecuta código como la aplicación con el terminal
   desbloqueado puede pedirle al almacén que descifre.
   ========================================================================== */

object CifradoFotos {

    private const val ALIAS = "clave_fotos"
    private const val ALMACEN = "AndroidKeyStore"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_ETIQUETA = 128
    private const val BYTES_NONCE = 12

    /** Cifra `claro` y lo escribe en `fichero`, con el nonce delante. */
    fun escribir(fichero: File, claro: ByteArray) {
        val cifrador = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())
        }
        val cifrado = cifrador.doFinal(claro)
        fichero.writeBytes(cifrador.iv + cifrado)
    }

    /**
     * Lee y descifra `fichero`. Devuelve null si no existe o si no se puede
     * descifrar (fichero manipulado, clave perdida o terminal bloqueado).
     *
     * Si falla, el fichero se borra: el selfie de un miembro se vuelve a
     * descargar y la foto de una misión hay que repetirla. Las fotos solo se
     * leen con la aplicación en primer plano, y por tanto con el terminal
     * desbloqueado, así que el bloqueo no llega a borrar ninguna.
     */
    fun leer(fichero: File): ByteArray? {
        if (!fichero.exists()) return null
        return try {
            val contenido = fichero.readBytes()
            val cifrador = Cipher.getInstance(TRANSFORMACION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    obtenerOCrearClave(),
                    GCMParameterSpec(BITS_ETIQUETA, contenido, 0, BYTES_NONCE)
                )
            }
            cifrador.doFinal(contenido, BYTES_NONCE, contenido.size - BYTES_NONCE)
        } catch (e: Exception) {
            // Se registra QUE ha fallado y de qué tipo. Nunca el contenido.
            Log.w("CifradoFotos", "Foto ilegible: ${e.javaClass.simpleName}")
            fichero.delete()
            null
        }
    }

    /**
     * La clave se recupera por su nombre y solo se crea la primera vez. El
     * almacén genera un nonce nuevo en cada cifrado, porque
     * setRandomizedEncryptionRequired viene activado por defecto.
     */
    private fun obtenerOCrearClave(): SecretKey {
        val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }
        (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val especificacion = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            especificacion.setUnlockedDeviceRequired(true)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN).run {
            init(especificacion.build())
            generateKey()
        }
    }
}
