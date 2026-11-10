package es.uma.aiquetepillo.datos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File

/** Lado mayor con el que se guarda y se sube cada foto. */
private const val LADO_GUARDADO = 1280

/**
 * Guarda una foto recién hecha: girada según `grados`, reducida a
 * LADO_GUARDADO, como JPEG sin metadatos y cifrada (CifradoFotos).
 *
 * La foto se recibe en memoria y no pasa nunca por el disco en claro. Al
 * girar los píxeles aquí, el fichero ya no necesita la orientación del EXIF.
 */
fun guardarFoto(mapa: Bitmap, grados: Int, fichero: File) {
    val giro = Matrix().apply {
        postRotate(grados.toFloat())
        val escala = minOf(1f, LADO_GUARDADO.toFloat() / maxOf(mapa.width, mapa.height))
        postScale(escala, escala)
    }
    val final = Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, giro, true)
    CifradoFotos.escribir(fichero, comprimir(final))
}

/**
 * Descifra una foto y la carga reducida para que su lado mayor no pase mucho
 * de `ladoMaximo`. Devuelve null si el fichero no existe o no se puede leer.
 */
fun cargarReducida(fichero: File, ladoMaximo: Int): Bitmap? {
    val bytes = CifradoFotos.leer(fichero) ?: return null
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    var reduccion = 1
    while (maxOf(medidas.outWidth, medidas.outHeight) / reduccion > ladoMaximo * 2) reduccion *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = reduccion })
}

/**
 * La foto como JPEG, lista para subir. Las fotos ya se guardan giradas,
 * reducidas y sin metadatos, así que basta con descifrarlas.
 */
fun jpegParaSubir(fichero: File): ByteArray? = CifradoFotos.leer(fichero)

private fun comprimir(mapa: Bitmap): ByteArray =
    ByteArrayOutputStream().use { salida ->
        mapa.compress(Bitmap.CompressFormat.JPEG, 85, salida)
        salida.toByteArray()
    }
