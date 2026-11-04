package es.uma.aiquetepillo.datos

import android.content.Context
import androidx.room.*

/* ==========================================================================
   LA BASE DE DATOS LOCAL
   Teoría: sesión 2.2.5 y 2.5.5.

   La aplicación guarda una copia local de las misiones descargadas para poder
   mostrarlos cuando no hay conexión. Room es una capa sobre SQLite que escribe
   el SQL por ti y comprueba las consultas al compilar.

   AVISO: el fichero de base de datos se escribe SIN CIFRAR. No es un descuido,
   es el comportamiento por defecto de Room. En la sección 5.3 lo sacarás del
   dispositivo y lo abrirás; en la práctica P3.1 lo cifrarás.
   ========================================================================== */

/**
 * Una fila de la tabla.
 *
 * Fíjate en el campo `propietario`, que no viene del backend: lo añadimos
 * nosotros para saber de qué usuario son los datos. Sin él, al entrar con otra
 * cuenta en el mismo dispositivo se verían mezcladas las misiones de las dos.
 */
@Entity(tableName = "misiones")
data class MisionEntidad(
    @PrimaryKey val id: Int,
    val titulo: String,
    val descripcion: String,
    val puntos: Int,
    val fecha: String,
    val propietario: String
)

/**
 * El DAO declara QUÉ consultas existen; Room genera el código que las ejecuta
 * y comprueba el SQL al compilar, no en ejecución.
 *
 * Las tres funciones son `suspend`: no se pueden llamar desde el hilo
 * principal y el compilador lo impone. Es la regla de la sesión 2.4.5
 * convertida en una restricción del lenguaje.
 */
@Dao
interface MisionDao {

    /** Las misiones de un propietario, de la más reciente a la más antigua. */
    @Query("SELECT * FROM misiones WHERE propietario = :propietario ORDER BY fecha DESC")
    suspend fun obtenerDe(propietario: String): List<MisionEntidad>

    /** Inserta una lista; si ya existe una fila con ese id, la reemplaza. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(misiones: List<MisionEntidad>)

    /** Borra todo lo de un propietario. Existe; otra cosa es que alguien la llame. */
    @Query("DELETE FROM misiones WHERE propietario = :propietario")
    suspend fun borrarDe(propietario: String)
}

@Database(entities = [MisionEntidad::class], version = 1, exportSchema = false)
abstract class BaseDatos : RoomDatabase() {

    abstract fun misionDao(): MisionDao

    companion object {

        /**
         * Abrir dos veces la misma base de datos corrompe los datos, así que se
         * guarda una única instancia. @Volatile y synchronized evitan que dos
         * hilos la construyan a la vez.
         */
        @Volatile private var instancia: BaseDatos? = null

        fun obtener(contexto: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    BaseDatos::class.java,
                    NOMBRE_FICHERO
                ).build().also { instancia = it }
            }

        /** El fichero acaba en /data/data/<tu.paquete>/databases/aiquetepillo.db */
        const val NOMBRE_FICHERO = "aiquetepillo.db"
    }
}
