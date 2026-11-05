package es.uma.aiquetepillo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.NavController
import es.uma.aiquetepillo.VistaModelo
import es.uma.aiquetepillo.modelo.EstadoUi
import es.uma.aiquetepillo.modelo.Grupo

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
    val estadoGrupos by vistaModelo.estadoGrupos.collectAsState()
    val estadoInvitacion by vistaModelo.estadoInvitacion.collectAsState()
    val estadoAltaGrupo by vistaModelo.estadoAltaGrupo.collectAsState()
    val estadoRegistro by vistaModelo.estadoRegistro.collectAsState()
    val selfie by vistaModelo.selfie.collectAsState()
    val estadoPartida by vistaModelo.estadoPartida.collectAsState()
    val estadoMision by vistaModelo.estadoMision.collectAsState()
    val usuario = (estadoLogin as? EstadoUi.Exito)?.datos?.usuario

    // Con una sesión guardada se empieza en la lista. Es también la pantalla a
    // la que se vuelve desde un enlace profundo abierto con la app cerrada.
    val inicio = remember { if (estadoLogin is EstadoUi.Exito) "lista" else "login" }

    NavHost(navController = navController, startDestination = inicio) {

        composable("login") {
            PantallaLogin(
                estado = estadoLogin,
                onEntrar = { usuario, clave -> vistaModelo.entrar(usuario, clave) },
                onRegistro = {
                    vistaModelo.olvidarRegistro()
                    navController.navigate("registro")
                }
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

        composable("registro") {
            PantallaRegistro(
                estado = estadoRegistro,
                selfie = selfie,
                onHacerSelfie = { navController.navigate("selfie") },
                onCrear = { usuario, clave, nombre -> vistaModelo.registrarse(usuario, clave, nombre) },
                onVolver = { navController.popBackStack() }
            )
            // Al crear la cuenta la sesión queda iniciada, como al entrar
            LaunchedEffect(estadoLogin) {
                if (estadoLogin is EstadoUi.Exito) {
                    navController.navigate("lista") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        }

        // Sin enlace profundo, igual que la cámara de las misiones
        composable("selfie") {
            PantallaCamara(
                fichero = vistaModelo.ficheroSelfie(),
                frontal = true,
                titulo = "¡SONRÍE!",
                onHecha = {
                    vistaModelo.selfieHecho()
                    navController.popBackStack()
                },
                onVolver = { navController.popBackStack() }
            )
        }

        composable("lista") {
            PantallaLista(
                usuario = usuario.orEmpty(),
                nombre = (estadoLogin as? EstadoUi.Exito)?.datos?.nombre.orEmpty(),
                estado = estadoLista,
                fotos = fotos,
                grupos = estadoGrupos,
                resultado = estadoMision,
                onRecargar = {
                    vistaModelo.cargarMisiones()
                    vistaModelo.cargarGrupos()
                },
                onSalir = {
                    vistaModelo.salir()
                    navController.navigate("login") {
                        popUpTo("lista") { inclusive = true }
                    }
                },
                onAbrir = { identificador -> navController.navigate("mision/$identificador") },
                onHacerFoto = { identificador -> navController.navigate("camara/$identificador") },
                onEnviarFoto = { identificador -> vistaModelo.enviarFoto(identificador) },
                onElegir = { vistaModelo.olvidarResultadoMision() },
                onSaltar = { identificador -> vistaModelo.saltarMision(identificador) },
                onCrearGrupo = {
                    vistaModelo.olvidarAltaGrupo()
                    navController.navigate("nuevo-grupo")
                },
                onUnirse = {
                    vistaModelo.olvidarAltaGrupo()
                    navController.navigate("unirse")
                },
                onAbrirGrupo = { identificador ->
                    vistaModelo.olvidarAltaGrupo()
                    navController.navigate("grupo/$identificador")
                }
            )
        }

        composable(
            route = "grupo/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entrada ->
            val id = entrada.arguments?.getInt("id") ?: 0
            // Siempre al abrir: puede haber entrado alguien desde la última vez
            LaunchedEffect(Unit) { vistaModelo.cargarGrupos() }
            PantallaGrupo(
                grupo = (estadoGrupos as? EstadoUi.Exito)?.datos?.find { it.id == id },
                cargando = estadoGrupos is EstadoUi.Reposo || estadoGrupos is EstadoUi.Cargando,
                usuario = usuario,
                partida = estadoPartida,
                onEmpezar = { vistaModelo.empezarPartida(it) },
                onVolver = { navController.popBackStack() }
            )
        }

        composable("nuevo-grupo") {
            PantallaCrearGrupo(
                estado = estadoAltaGrupo,
                onCrear = { nombre, contexto -> vistaModelo.crearGrupo(nombre, contexto) },
                onVolver = { navController.popBackStack() }
            )
            AbrirGrupoAlTerminar(estadoAltaGrupo, "nuevo-grupo", navController, vistaModelo)
        }

        /*
         * Destino del enlace de invitación. El enlace solo rellena el código:
         * la pantalla enseña el grupo y no une a nadie hasta que se pulsa UNIRSE.
         */
        composable(
            route = "unirse?codigo={codigo}",
            arguments = listOf(navArgument("codigo") { type = NavType.StringType; defaultValue = "" }),
            deepLinks = listOf(navDeepLink { uriPattern = "aiquetepillo://unirse/{codigo}" })
        ) { entrada ->
            PantallaUnirse(
                codigoInicial = entrada.arguments?.getString("codigo").orEmpty(),
                usuario = (estadoLogin as? EstadoUi.Exito)?.datos?.usuario,
                invitacion = estadoInvitacion,
                union = estadoAltaGrupo,
                onBuscar = { vistaModelo.verInvitacion(it) },
                onUnirse = { vistaModelo.unirse(it) },
                onVolver = { navController.popBackStack() }
            )
            AbrirGrupoAlTerminar(estadoAltaGrupo, "unirse?codigo={codigo}", navController, vistaModelo)
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

/** Cuando se ha creado un grupo o se ha entrado en él, sustituye el formulario por el grupo. */
@Composable
private fun AbrirGrupoAlTerminar(
    estado: EstadoUi<Grupo>,
    rutaFormulario: String,
    navController: NavController,
    vistaModelo: VistaModelo
) {
    LaunchedEffect(estado) {
        if (estado is EstadoUi.Exito) {
            navController.navigate("grupo/${estado.datos.id}") {
                popUpTo(rutaFormulario) { inclusive = true }
            }
            vistaModelo.olvidarAltaGrupo()
        }
    }
}
