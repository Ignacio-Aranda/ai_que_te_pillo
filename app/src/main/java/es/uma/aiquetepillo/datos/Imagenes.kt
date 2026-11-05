package es.uma.aiquetepillo.datos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Carga una foto reducida para que su lado mayor no pase mucho de `ladoMaximo`,
 * y la gira según su EXIF. Devuelve null si el fichero no existe o no se lee.
 */
fun cargarReducida(fichero: File, ladoMaximo: Int): Bitmap? {
    if (!fichero.exists()) return null
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(fichero.path, medidas)
    var reduccion = 1
    while (maxOf(medidas.outWidth, medidas.outHeight) / reduccion > ladoMaximo * 2) reduccion *= 2
    val mapa = BitmapFactory.decodeFile(fichero.path, BitmapFactory.Options().apply { inSampleSize = reduccion })
        ?: return null

    // CameraX guarda la orientación en el EXIF en lugar de girar los píxeles
    val grados = when (ExifInterface(fichero.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (grados == 0f) return mapa
    val giro = Matrix().apply { postRotate(grados) }
    return Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, giro, true)
}

/**
 * La foto como JPEG reducido, ya girado, listo para subir. Pesa mucho menos que
 * el original y no lleva los metadatos EXIF de la cámara.
 */
fun jpegParaSubir(fichero: File, ladoMaximo: Int = 1280): ByteArray? {
    val cargada = cargarReducida(fichero, ladoMaximo) ?: return null
    val escala = minOf(1f, ladoMaximo.toFloat() / maxOf(cargada.width, cargada.height))
    val mapa = Bitmap.createScaledBitmap(
        cargada, (cargada.width * escala).toInt(), (cargada.height * escala).toInt(), true
    )
    return ByteArrayOutputStream().use { salida ->
        mapa.compress(Bitmap.CompressFormat.JPEG, 85, salida)
        salida.toByteArray()
    }
}
