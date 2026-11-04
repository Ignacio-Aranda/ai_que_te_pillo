package es.uma.aiquetepillo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import es.uma.aiquetepillo.VistaModelo
import es.uma.aiquetepillo.modelo.EstadoUi

/**
 * Navegación entre pantallas. ESTÁ TERMINADA: no hace falta tocarla en P2.2.
 *
 * En la práctica P2.3 se ampliará con argumentos de ruta y enlaces profundos,
 * y se comprobará que la navegación no es un control de seguridad (sesión 2.5.3).
 */
@Composable
fun Navegacion(vistaModelo: VistaModelo) {
    val navController = rememberNavController()
    val estadoLogin by vistaModelo.estadoLogin.collectAsState()
    val estadoLista by vistaModelo.estadoLista.collectAsState()
    val estadoDetalle by vistaModelo.estadoDetalle.collectAsState()
    val fotos by vistaModelo.fotos.collectAsState()

    NavHost(navController = navController, startDestination = "login") {

        composable("login") {
            PantallaLogin(
                estado = estadoLogin,
                onEntrar = { usuario, clave -> vistaModelo.entrar(usuario, clave) }
            )
            // La navegación es un efecto: dentro de LaunchedEffect se lanza una
            // sola vez por cambio de estado, no en cada redibujado de la pantalla.
            LaunchedEffect(estadoLogin) {
                if (estadoLogin is EstadoUi.Exito) {
                    navController.navigate("lista") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        }

        composable("lista") {
            PantallaLista(
                nombre = (estadoLogin as? EstadoUi.Exito)?.datos?.nombre.orEmpty(),
                estado = estadoLista,
                fotos = fotos,
                onRecargar = { vistaModelo.cargarMisiones() },
                onSalir = {
                    vistaModelo.salir()
                    navController.navigate("login") {
                        popUpTo("lista") { inclusive = true }
                    }
                },
                onAbrir = { identificador -> navController.navigate("mision/$identificador") },
                onHacerFoto = { identificador -> navController.navigate("camara/$identificador") }
            )
        }

        // Sin enlace profundo: la cámara solo se abre desde la propia aplicación
        composable(
            route = "camara/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entrada ->
            val id = entrada.arguments?.getInt("id") ?: 0
            PantallaCamara(
                fichero = vistaModelo.ficheroFoto(id),
                onHecha = {
                    vistaModelo.fotoHecha(id)
                    navController.popBackStack()
                },
                onVolver = { navController.popBackStack() }
            )
        }

        composable(
            route = "mision/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = "aiquetepillo://mision/{id}" })
        ) { entrada ->
            val id = entrada.arguments?.getInt("id") ?: 0
            PantallaDetalle(
                id = id,
                estado = estadoDetalle,
                onCargar = { vistaModelo.cargarDetalle(it) },
                onVolver = { navController.popBackStack() }
            )
        }
    }
}
