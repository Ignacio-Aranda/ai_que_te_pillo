package es.uma.aiquetepillo.datos

import java.io.File

/**
 * Fotos de las misiones, una por misión, el selfie de registro y los selfies
 * descargados de los miembros de los grupos, en el directorio privado de la
 * aplicación (cache/fotos/).
 *
 * Desde P3.1 los ficheros están cifrados (CifradoFotos), de ahí la extensión
 * .cifrada: quien los copie no obtiene una imagen. Esta clase solo decide los
 * nombres; escribir y leer el contenido pasa siempre por CifradoFotos. El
 * selfie propio se borra en cuanto se sube; las fotos de misión, al conseguir
 * la misión o al cerrar sesión, y los selfies descargados, al cerrar sesión.
 */
class AlmacenFotos(private val carpeta: File) {

    init {
        /*
         * Las versiones anteriores a P3.1 guardaban las fotos en claro, como
         * .jpg. Se borran al arrancar: los selfies se vuelven a descargar y la
         * foto de una misión pendiente hay que repetirla.
         */
        carpeta.listFiles { fichero -> fichero.name.endsWith(".jpg") }?.forEach { it.delete() }
    }

    fun ficheroPara(idMision: Int): File {
        carpeta.mkdirs()
        return File(carpeta, "mision_$idMision.cifrada")
    }

    fun ficheroSelfie(): File {
        carpeta.mkdirs()
        return File(carpeta, "selfie.cifrada")
    }

    /** Selfie descargado de un miembro. `usuario` ya viene validado. */
    fun ficheroSelfieDe(usuario: String): File {
        carpeta.mkdirs()
        return File(carpeta, "selfie_$usuario.cifrada")
    }

    fun borrar(idMision: Int) {
        File(carpeta, "mision_$idMision.cifrada").delete()
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
        val PATRON = Regex("""mision_(\d+)\.cifrada""")
    }
}
