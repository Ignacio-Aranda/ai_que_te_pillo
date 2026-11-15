package es.uma.aiquetepillo.datos

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/* ==========================================================================
   EL CIFRADO DE LAS FOTOS
   Teoría: sesión 3.3, diapositivas 6 y 12. Práctica P3.1, parte de grupo.

   Las fotos (el selfie de registro, los selfies de los miembros y la foto de
   cada misión) se guardan cifradas con AES-256-GCM. Cada fichero contiene el
   nonce de 12 bytes seguido del texto cifrado con su etiqueta, y el nombre
   del fichero va como dato asociado.

   La clave NO es la de la sesión. La de la sesión exige la huella en cada
   operación, y las fotos se leen continuamente mientras se juega (avatares,
   miniaturas, el envío a la IA): pedir la huella en cada una no es viable.
   Esta clave no pide autenticación por uso, pero solo funciona con el
   terminal desbloqueado (setUnlockedDeviceRequired, Android 9 o superior):
   con el teléfono bloqueado, el almacén se niega a descifrar. En Android 8
   (minSdk 26) esa condición no existe y la clave no tiene ninguna.

   Lo que protege: los ficheros copiados fuera de la aplicación (con run-as
   sobre la variante depurable o con un volcado del almacenamiento) no se
   pueden abrir, porque la clave no sale del almacén. La caché, donde están,
   no entra en la copia de seguridad.
   Lo que no protege: quien ejecuta código como la aplicación con el terminal
   desbloqueado puede pedirle al almacén que descifre. En la variante
   depurable basta con adb y run-as.
   ========================================================================== */

object CifradoFotos {

    private const val ALIAS = "clave_fotos"
    private const val ALMACEN = "AndroidKeyStore"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_ETIQUETA = 128
    private const val BYTES_NONCE = 12

    /**
     * Cifra `claro` y lo escribe en `fichero`, con el nonce delante.
     *
     * El nombre del fichero entra como dato asociado de GCM: no se cifra,
     * pero queda cubierto por la etiqueta. Así no se puede cambiar una foto
     * por otra renombrando ficheros (el selfie de un usuario por el de otro,
     * por ejemplo): al descifrarla con otro nombre, la etiqueta no coincide.
     */
    fun escribir(fichero: File, claro: ByteArray) {
        val cifrador = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())
            updateAAD(fichero.name.toByteArray(Charsets.UTF_8))
        }
        val cifrado = cifrador.doFinal(claro)
        fichero.writeBytes(cifrador.iv + cifrado)
    }

    /**
     * Lee y descifra `fichero`. Devuelve null si no existe o si no se puede
     * descifrar.
     *
     * El fichero solo se borra cuando no se podrá leer nunca: si está
     * truncado o si la etiqueta no coincide (contenido manipulado, nombre
     * cambiado o clave que ya no existe y se ha creado de nuevo). Esta clave
     * no exige autenticación, así que el sistema no la invalida. Entonces el selfie de un
     * miembro se vuelve a descargar y la foto de una misión hay que repetirla.
     * Cualquier otro fallo, como que el almacén rechace la clave porque el
     * terminal se ha bloqueado a mitad de una lectura, deja el fichero intacto.
     */
    fun leer(fichero: File): ByteArray? {
        if (!fichero.exists()) return null
        val contenido = runCatching { fichero.readBytes() }.getOrNull() ?: return null
        if (contenido.size < BYTES_NONCE + BITS_ETIQUETA / 8) {
            fichero.delete()
            return null
        }
        return try {
            val cifrador = Cipher.getInstance(TRANSFORMACION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    obtenerOCrearClave(),
                    GCMParameterSpec(BITS_ETIQUETA, contenido, 0, BYTES_NONCE)
                )
                updateAAD(fichero.name.toByteArray(Charsets.UTF_8))
            }
            cifrador.doFinal(contenido, BYTES_NONCE, contenido.size - BYTES_NONCE)
        } catch (e: Exception) {
            // Se registra QUE ha fallado y por qué. Nunca el contenido.
            Log.w("CifradoFotos", "Foto ilegible: ${e.javaClass.simpleName}: ${e.message}")
            if (e is AEADBadTagException) fichero.delete()
            null
        }
    }

    /**
     * La clave se recupera por su nombre y solo se crea la primera vez. El
     * almacén genera un nonce nuevo en cada cifrado, porque
     * setRandomizedEncryptionRequired viene activado por defecto.
     */
    @Synchronized  // dos escrituras simultáneas no deben crear dos claves
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
