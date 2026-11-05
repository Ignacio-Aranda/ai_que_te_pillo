package es.uma.aiquetepillo.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.uma.aiquetepillo.modelo.*
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.*

/* ==========================================================================
   GRUPOS E INVITACIONES

   Cada grupo tiene un código de invitación de seis caracteres. Se puede
   escribir a mano o llegar con el enlace aiquetepillo://unirse/{codigo}, y en
   los dos casos la pantalla de unión enseña antes el grupo y espera a que el
   jugador acepte: abrir el enlace nunca une a nadie por sí solo.

   El código es lo único que protege el grupo. Quien lo tenga, porque se lo
   han reenviado o porque lo ha adivinado, puede entrar.
   ========================================================================== */

/** Enlace de invitación que se comparte con el selector de Android. */
fun enlaceInvitacion(codigo: String): String = "aiquetepillo://unirse/$codigo"

/** Contenido de la pestaña de grupo: los grupos del jugador y cómo entrar en otro. */
@Composable
fun ContenidoGrupos(
    estado: EstadoUi<List<Grupo>>,
    onRecargar: () -> Unit,
    onCrear: () -> Unit,
    onUnirse: () -> Unit,
    onAbrir: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BotonGrande("CREAR GRUPO", onCrear) }
        item { BotonGrande("UNIRSE CON CÓDIGO", onUnirse, color = Amarillo, colorOscuro = Naranja) }
        item { TextoJuego("MIS GRUPOS", tamano = 18.sp, alineacion = TextAlign.Start) }

        when (estado) {
            is EstadoUi.Reposo, is EstadoUi.Cargando -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is EstadoUi.Error -> item {
                Aviso(textoDeError(estado.motivo))
                Spacer(Modifier.height(12.dp))
                BotonGrande("REINTENTAR", onRecargar)
            }
            is EstadoUi.Exito -> {
                if (estado.datos.isEmpty()) {
                    item {
                        Text(
                            "Todavía no estás en ningún grupo. Crea uno o únete con el código de un amigo.",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                items(estado.datos) { grupo ->
                    TarjetaJuego(Modifier.fillMaxWidth().clickable { onAbrir(grupo.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Medallon("👥", tamano = 44.dp, fondo = MoradoClaro)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(grupo.nombre, color = Color.White, fontSize = 17.sp)
                                if (grupo.contexto.isNotBlank()) {
                                    Text(grupo.contexto, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Pastilla("👤", "${grupo.miembros.size}")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * Detalle de un grupo: el código para invitar, la partida y los miembros. Solo
 * quien creó el grupo puede empezar la partida; el servidor lo vuelve a
 * comprobar, porque esconder el botón no protege nada.
 */
@Composable
fun PantallaGrupo(
    grupo: Grupo?,
    cargando: Boolean,
    usuario: String?,
    partida: EstadoUi<Grupo>,
    onEmpezar: (Int) -> Unit,
    onVolver: () -> Unit
) {
    val contexto = LocalContext.current

    PantallaConCabecera(grupo?.nombre?.uppercase() ?: "GRUPO", onVolver) {
        if (grupo == null) {
            if (cargando) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
            else Aviso("No se ha encontrado el grupo.")
            return@PantallaConCabecera
        }

        if (grupo.contexto.isNotBlank()) {
            Text(grupo.contexto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        TarjetaJuego(Modifier.fillMaxWidth(), fondo = MoradoOscuro) {
            Text("CÓDIGO DE INVITACIÓN", color = Amarillo, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            TextoJuego(grupo.codigo, Modifier.fillMaxWidth(), tamano = 40.sp, grosor = 10f)
        }
        Spacer(Modifier.height(16.dp))
        BotonGrande(
            texto = "INVITAR",
            color = Amarillo,
            colorOscuro = Naranja,
            onClick = {
                val texto = "¡Únete a «${grupo.nombre}» en AI que te pillo! " +
                    "Código: ${grupo.codigo} · ${enlaceInvitacion(grupo.codigo)}"
                val envio = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, texto)
                contexto.startActivity(Intent.createChooser(envio, "Invitar al grupo"))
            }
        )
        Spacer(Modifier.height(16.dp))

        when {
            grupo.empezada -> TextoJuego("🎮 PARTIDA EN MARCHA", Modifier.fillMaxWidth(), tamano = 20.sp, color = Amarillo)
            grupo.creador == usuario -> {
                val generando = partida is EstadoUi.Cargando
                BotonGrande(
                    texto = if (generando) "CREANDO MISIONES..." else "EMPEZAR PARTIDA",
                    onClick = { onEmpezar(grupo.id) },
                    habilitado = grupo.miembros.size >= 2 && !generando
                )
                if (grupo.miembros.size < 2) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hacen falta al menos dos jugadores: invita a alguien.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            else -> {
                val creador = grupo.miembros.firstOrNull { it.usuario == grupo.creador }?.nombre ?: "quien creó el grupo"
                Text(
                    "Esperando a que $creador empiece la partida.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (partida is EstadoUi.Error) {
            Spacer(Modifier.height(12.dp))
            Aviso(textoDeError(partida.motivo))
        }
        Spacer(Modifier.height(20.dp))

        TextoJuego("MIEMBROS (${grupo.miembros.size})", tamano = 18.sp, alineacion = TextAlign.Start)
        Spacer(Modifier.height(8.dp))
        grupo.miembros.forEach { miembro ->
            TarjetaJuego(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Medallon(miembro.nombre.firstOrNull()?.uppercase() ?: "?", tamano = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        miembro.nombre,
                        color = if (miembro.usuario == usuario) Amarillo else Color.White,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (miembro.usuario == grupo.creador) Text("👑", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Pastilla("⭐", "${miembro.puntos}")
                }
            }
        }
    }
}

/** Clasificación de cada grupo del jugador, por puntos. */
@Composable
fun ContenidoRanking(estado: EstadoUi<List<Grupo>>, usuario: String, onRecargar: () -> Unit) {
    when (estado) {
        is EstadoUi.Reposo, is EstadoUi.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        is EstadoUi.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Aviso(textoDeError(estado.motivo))
            Spacer(Modifier.height(16.dp))
            BotonGrande("REINTENTAR", onRecargar)
        }
        is EstadoUi.Exito -> if (estado.datos.isEmpty()) {
            MensajeVacio("🏆", "SIN CLASIFICACIÓN", "Únete a un grupo para competir con tus amigos.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.datos) { grupo -> TablaGrupo(grupo, usuario) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun TablaGrupo(grupo: Grupo, usuario: String) {
    TarjetaJuego(Modifier.fillMaxWidth()) {
        TextoJuego(grupo.nombre.uppercase(), Modifier.fillMaxWidth(), tamano = 20.sp)
        Spacer(Modifier.height(8.dp))
        grupo.miembros.sortedByDescending { it.puntos }.forEach { miembro ->
            // Con los mismos puntos se comparte puesto (y medalla)
            val posicion = grupo.miembros.count { it.puntos > miembro.puntos }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (posicion) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${posicion + 1}" },
                    color = Color.White,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp)
                )
                Spacer(Modifier.width(6.dp))
                Medallon(miembro.nombre.firstOrNull()?.uppercase() ?: "?", tamano = 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    miembro.nombre,
                    color = if (miembro.usuario == usuario) Amarillo else Color.White,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                Pastilla("⭐", "${miembro.puntos}")
            }
        }
    }
}

/** Formulario para crear un grupo. El creador entra en él automáticamente. */
@Composable
fun PantallaCrearGrupo(
    estado: EstadoUi<Grupo>,
    onCrear: (String, String) -> Unit,
    onVolver: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var contexto by remember { mutableStateOf("") }
    val cargando = estado is EstadoUi.Cargando

    PantallaConCabecera("NUEVO GRUPO", onVolver) {
        TarjetaJuego(Modifier.fillMaxWidth()) {
            CampoJuego(nombre, { nombre = it }, "Nombre del grupo")
            Spacer(Modifier.height(12.dp))
            CampoJuego(contexto, { contexto = it }, "¿Dónde estáis? (casa rural, clase…)")
            Spacer(Modifier.height(8.dp))
            Text(
                "La IA usará el contexto para inventar las misiones.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(20.dp))
        BotonGrande(
            texto = if (cargando) "CREANDO..." else "CREAR",
            onClick = { onCrear(nombre.trim(), contexto.trim()) },
            habilitado = nombre.isNotBlank() && !cargando
        )
        if (estado is EstadoUi.Error) {
            Spacer(Modifier.height(16.dp))
            Aviso(textoDeError(estado.motivo))
        }
    }
}

/**
 * Unión a un grupo con su código. Primero se busca el grupo y se enseña; el
 * jugador solo entra si pulsa UNIRSE.
 *
 * `codigoInicial` llega relleno cuando se abre desde el enlace de invitación.
 */
@Composable
fun PantallaUnirse(
    codigoInicial: String,
    usuario: String?,
    invitacion: EstadoUi<Grupo>,
    union: EstadoUi<Grupo>,
    onBuscar: (String) -> Unit,
    onUnirse: (String) -> Unit,
    onVolver: () -> Unit
) {
    var codigo by remember { mutableStateOf(codigoInicial.uppercase()) }
    LaunchedEffect(codigoInicial) { if (codigoInicial.isNotBlank()) onBuscar(codigoInicial) }

    // Solo cuenta la vista previa del código que hay escrito ahora
    val grupo = (invitacion as? EstadoUi.Exito)?.datos?.takeIf { it.codigo == codigo.trim() }
    val yaEsMiembro = grupo != null && grupo.miembros.any { it.usuario == usuario }
    val ocupado = invitacion is EstadoUi.Cargando || union is EstadoUi.Cargando

    PantallaConCabecera("UNIRSE", onVolver) {
        TarjetaJuego(Modifier.fillMaxWidth()) {
            CampoJuego(codigo, { codigo = it.uppercase().take(6) }, "Código de invitación")
        }
        Spacer(Modifier.height(20.dp))

        if (grupo == null) {
            BotonGrande(
                texto = if (ocupado) "BUSCANDO..." else "BUSCAR",
                onClick = { onBuscar(codigo.trim()) },
                habilitado = codigo.trim().length == 6 && !ocupado
            )
        } else {
            TarjetaJuego(Modifier.fillMaxWidth(), fondo = MoradoOscuro) {
                Text(
                    if (yaEsMiembro) "YA ESTÁS EN" else "TE HAN INVITADO A",
                    color = Amarillo,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                TextoJuego(grupo.nombre, Modifier.fillMaxWidth(), tamano = 26.sp, grosor = 8f)
                if (grupo.contexto.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(grupo.contexto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    grupo.miembros.joinToString { it.nombre },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(20.dp))
            BotonGrande(
                texto = when {
                    union is EstadoUi.Cargando -> "UNIÉNDOTE..."
                    yaEsMiembro -> "VER GRUPO"
                    else -> "UNIRSE"
                },
                onClick = { onUnirse(grupo.codigo) },
                habilitado = !ocupado
            )
        }

        val error = (union as? EstadoUi.Error) ?: (invitacion as? EstadoUi.Error)
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Aviso(textoDeError(error.motivo))
        }
    }
}

/** Pantalla secundaria: botón de volver, título y contenido desplazable. */
@Composable
private fun PantallaConCabecera(titulo: String, onVolver: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    FondoJuego {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonRedondo("⬅️", "Volver", onVolver)
                Spacer(Modifier.width(12.dp))
                TextoJuego(titulo, Modifier.weight(1f), tamano = 20.sp, alineacion = TextAlign.End)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                content = contenido
            )
        }
    }
}
