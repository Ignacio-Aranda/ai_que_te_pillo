package es.uma.aiquetepillo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.uma.aiquetepillo.Foto
import es.uma.aiquetepillo.modelo.*
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.*

/**
 * Pantalla principal, con tres pestañas: los grupos del jugador, sus misiones
 * (la activa, con la foto, y el resto) y la clasificación de cada grupo.
 */
@Composable
fun PantallaLista(
    usuario: String,
    nombre: String,
    estado: EstadoUi<List<Mision>>,
    fotos: Map<Int, Foto>,
    grupos: EstadoUi<List<Grupo>>,
    resultado: EstadoUi<ResultadoMision>,
    onRecargar: () -> Unit,
    onSalir: () -> Unit,
    onAbrir: (Int) -> Unit,
    onHacerFoto: (Int) -> Unit,
    onEnviarFoto: (Int) -> Unit,
    onSaltar: (Int) -> Unit,
    onElegir: () -> Unit,
    onCrearGrupo: () -> Unit,
    onUnirse: () -> Unit,
    onAbrirGrupo: (Int) -> Unit
) {
    // rememberSaveable: al volver de otra pantalla se sigue en la misma pestaña
    var pestana by rememberSaveable { mutableStateOf(Pestana.MISIONES) }

    // Al entrar se cargan misiones y grupos sin esperar a que el jugador lo pida
    LaunchedEffect(Unit) { if (estado is EstadoUi.Reposo || grupos is EstadoUi.Reposo) onRecargar() }

    val misiones = (estado as? EstadoUi.Exito)?.datos.orEmpty()
    val misPuntos = (grupos as? EstadoUi.Exito)?.datos.orEmpty()
        .sumOf { grupo -> grupo.miembros.firstOrNull { it.usuario == usuario }?.puntos ?: 0 }

    FondoJuego {
        Column(Modifier.fillMaxSize()) {
            BarraSuperior(misiones.size, misPuntos, onRecargar, onSalir)
            TarjetaJugador(nombre, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (pestana) {
                    Pestana.MISIONES -> ContenidoMisiones(estado, fotos, resultado, onRecargar, onAbrir, onHacerFoto, onEnviarFoto, onSaltar, onElegir)
                    Pestana.GRUPO -> ContenidoGrupos(grupos, onRecargar, onCrearGrupo, onUnirse, onAbrirGrupo)
                    Pestana.RANKING -> ContenidoRanking(grupos, usuario, onRecargar)
                }
            }

            BarraInferior(actual = pestana, onCambiar = { pestana = it })
        }
    }
}

@Composable
private fun BarraSuperior(misiones: Int, puntos: Int, onRecargar: () -> Unit, onSalir: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pastilla("🎯", "$misiones")
        Pastilla("⭐", "$puntos")
        Spacer(Modifier.weight(1f))
        BotonRedondo("🔄", "Recargar", onRecargar)
        BotonRedondo("🚪", "Salir", onSalir)
    }
}

@Composable
private fun TarjetaJugador(nombre: String, modifier: Modifier = Modifier) {
    TarjetaJuego(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Medallon(nombre.firstOrNull()?.uppercase() ?: "?", tamano = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                TextoJuego(nombre.ifBlank { "Jugador" }, tamano = 22.sp, alineacion = TextAlign.Start)
                Text("Cazador", color = Amarillo)
            }
        }
    }
}

@Composable
private fun ContenidoMisiones(
    estado: EstadoUi<List<Mision>>,
    fotos: Map<Int, Foto>,
    resultado: EstadoUi<ResultadoMision>,
    onRecargar: () -> Unit,
    onAbrir: (Int) -> Unit,
    onHacerFoto: (Int) -> Unit,
    onEnviarFoto: (Int) -> Unit,
    onSaltar: (Int) -> Unit,
    onElegir: () -> Unit
) {
    when (estado) {
        is EstadoUi.Reposo, is EstadoUi.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        is EstadoUi.Error -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Aviso("No se han podido cargar las misiones")
            Spacer(Modifier.height(16.dp))
            BotonGrande("REINTENTAR", onRecargar)
        }
        is EstadoUi.Exito -> {
            if (estado.datos.isEmpty()) {
                MensajeVacio("🎯", "SIN MISIONES", "Aparecerán cuando empiece la partida de uno de tus grupos.")
            } else {
                ListaMisiones(estado.datos, fotos, resultado, onAbrir, onHacerFoto, onEnviarFoto, onSaltar, onElegir)
            }
        }
    }
}

@Composable
private fun ListaMisiones(
    misiones: List<Mision>,
    fotos: Map<Int, Foto>,
    resultado: EstadoUi<ResultadoMision>,
    onAbrir: (Int) -> Unit,
    onHacerFoto: (Int) -> Unit,
    onEnviarFoto: (Int) -> Unit,
    onSaltar: (Int) -> Unit,
    onElegir: () -> Unit
) {
    // Hay una misión por grupo con partida: se juega la elegida (al principio, la primera)
    var elegida by rememberSaveable { mutableStateOf<Int?>(null) }
    val activa = misiones.firstOrNull { it.id == elegida } ?: misiones.first()
    val fotoActiva = fotos[activa.id]
    val ocupado = resultado is EstadoUi.Cargando
    val abrirCamara = rememberAbrirCamara { onHacerFoto(activa.id) }
    val lista = rememberLazyListState()

    // Cuando cambia la misión que se juega, se vuelve arriba para verla entera
    LaunchedEffect(activa.id) { lista.scrollToItem(0) }
    // Al volver de la cámara con una foto nueva, se baja hasta «ENVIAR FOTO»
    LaunchedEffect(fotoActiva?.version) { if (fotoActiva != null) lista.animateScrollToItem(1) }
    // El veredicto aparece junto a los botones, que es donde se está mirando
    LaunchedEffect(resultado) {
        if (resultado is EstadoUi.Exito || resultado is EstadoUi.Error) lista.animateScrollToItem(1)
    }

    LazyColumn(
        state = lista,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TarjetaJuego(
                Modifier.fillMaxWidth().clickable { onAbrir(activa.id) },
                fondo = MoradoOscuro
            ) {
                Text(
                    "MISIÓN ACTIVA",
                    color = Amarillo,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))
                Medallon("🕵️", Modifier.align(Alignment.CenterHorizontally), tamano = 96.dp, fondo = MoradoClaro)
                Spacer(Modifier.height(12.dp))
                TextoJuego(activa.titulo, Modifier.fillMaxWidth(), tamano = 24.sp, grosor = 8f)
                Spacer(Modifier.height(6.dp))
                Text(
                    activa.descripcion,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (activa.objetivo.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🎯 Objetivo: ${activa.objetivo}   👥 ${activa.grupo}",
                        color = Lavanda,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Pastilla("⭐", puntosTexto(activa.puntos), Modifier.align(Alignment.CenterHorizontally))
                if (fotoActiva != null) {
                    Spacer(Modifier.height(14.dp))
                    MiniaturaFoto(fotoActiva, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
            }
        }
        when (resultado) {
            is EstadoUi.Exito -> item { AvisoResultado(resultado.datos) }
            is EstadoUi.Error -> item { Aviso(textoDeError(resultado.motivo)) }
            else -> item { Spacer(Modifier.height(0.dp)) }
        }
        item {
            if (fotoActiva == null) {
                BotonGrande("HACER FOTO", abrirCamara, habilitado = !ocupado)
            } else {
                BotonGrande(
                    texto = if (ocupado) "COMPROBANDO..." else "ENVIAR FOTO",
                    onClick = { onEnviarFoto(activa.id) },
                    habilitado = !ocupado
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fotoActiva != null) {
                    BotonGrande(
                        "REPETIR", abrirCamara, Modifier.weight(1f),
                        color = Amarillo, colorOscuro = Naranja, habilitado = !ocupado, altura = 56.dp
                    )
                }
                BotonGrande(
                    "SALTAR (-1)", { onSaltar(activa.id) }, Modifier.weight(1f),
                    color = Rojo, colorOscuro = RojoOscuro, habilitado = !ocupado, altura = 56.dp
                )
            }
        }
        if (misiones.size > 1) {
            item {
                TextoJuego("OTRAS MISIONES", tamano = 18.sp, alineacion = TextAlign.Start)
            }
            // Tocar otra misión la pasa a la tarjeta grande para jugarla
            items(misiones.filter { it.id != activa.id }) { mision ->
                TarjetaJuego(Modifier.fillMaxWidth().clickable {
                    elegida = mision.id
                    onElegir()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Medallon("🎯", tamano = 44.dp, fondo = MoradoClaro)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mision.titulo, color = Color.White, fontSize = 17.sp)
                            Text("👥 ${mision.grupo}", color = Lavanda, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Pastilla("⭐", "${mision.puntos}")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Lo que ha dicho la IA de la foto, o el aviso de que se ha saltado la misión. */
@Composable
private fun AvisoResultado(resultado: ResultadoMision) {
    when {
        resultado.valida -> Aviso("¡Misión conseguida! +${puntosTexto(resultado.puntos)}. ${resultado.motivo}", fondo = VerdeOscuro)
        resultado.puntos < 0 -> Aviso(resultado.motivo, fondo = Naranja)
        else -> Aviso("Foto rechazada: ${resultado.motivo}")
    }
}

/** Mensaje centrado para una pestaña o lista sin contenido. */
@Composable
fun MensajeVacio(icono: String, titulo: String, texto: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TarjetaJuego(Modifier.fillMaxWidth()) {
            Medallon(icono, Modifier.align(Alignment.CenterHorizontally), tamano = 80.dp, fondo = MoradoClaro)
            Spacer(Modifier.height(12.dp))
            TextoJuego(titulo, Modifier.fillMaxWidth(), tamano = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text(texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** «1 punto», «3 puntos»… */
fun puntosTexto(puntos: Int): String = if (puntos == 1) "1 punto" else "$puntos puntos"
