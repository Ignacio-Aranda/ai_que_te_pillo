package es.uma.aiquetepillo.datos

import java.io.File

/**
 * Fotos de las misiones, una por misión, el selfie de registro y los selfies
 * descargados de los miembros de los grupos, en el directorio privado de la
 * aplicación (cache/fotos/).
 *
 * AVISO: igual que la base de datos, las fotos se guardan SIN CIFRAR y
 * cualquiera con run-as sobre la variante depurable puede sacarlas. El selfie
 * propio se borra en cuanto se sube; las fotos de misión, al conseguir la
 * misión o al cerrar sesión, y los selfies descargados, al cerrar sesión.
 */
class AlmacenFotos(private val carpeta: File) {

    fun ficheroPara(idMision: Int): File {
        carpeta.mkdirs()
        return File(carpeta, "mision_$idMision.jpg")
    }

    fun ficheroSelfie(): File {
        carpeta.mkdirs()
        return File(carpeta, "selfie.jpg")
    }

    /** Selfie descargado de un miembro. `usuario` ya viene validado. */
    fun ficheroSelfieDe(usuario: String): File {
        carpeta.mkdirs()
        return File(carpeta, "selfie_$usuario.jpg")
    }

    fun borrar(idMision: Int) {
        File(carpeta, "mision_$idMision.jpg").delete()
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
