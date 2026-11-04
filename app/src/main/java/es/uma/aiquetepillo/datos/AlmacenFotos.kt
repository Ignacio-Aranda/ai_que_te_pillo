package es.uma.aiquetepillo.datos

import java.io.File

/**
 * Fotos de las misiones, una por misión, en el directorio privado de la
 * aplicación (cache/fotos/mision_<id>.jpg).
 *
 * AVISO: igual que la base de datos, las fotos se guardan SIN CIFRAR y
 * cualquiera con run-as sobre la variante depurable puede sacarlas. Todavía
 * no se suben al servidor.
 */
class AlmacenFotos(private val carpeta: File) {

    fun ficheroPara(idMision: Int): File {
        carpeta.mkdirs()
        return File(carpeta, "mision_$idMision.jpg")
    }

    /** Las fotos que ya existen, por identificador de misión. */
    fun existentes(): Map<Int, File> =
        carpeta.listFiles().orEmpty().mapNotNull { fichero ->
            PATRON.matchEntire(fichero.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to fichero }
        }.toMap()

    fun borrarTodas() {
        carpeta.deleteRecursively()
    }

    private companion object {
        val PATRON = Regex("""mision_(\d+)\.jpg""")
    }
}
