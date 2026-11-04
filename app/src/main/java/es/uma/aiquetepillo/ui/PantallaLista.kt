package es.uma.aiquetepillo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import es.uma.aiquetepillo.modelo.*
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.*

/**
 * Pantalla principal: la misión activa del jugador, el resto de sus misiones y
 * la barra de pestañas. Las pestañas de grupo y clasificación todavía no
 * tienen contenido.
 */
@Composable
fun PantallaLista(
    nombre: String,
    estado: EstadoUi<List<Mision>>,
    onRecargar: () -> Unit,
    onSalir: () -> Unit,
    onAbrir: (Int) -> Unit
) {
    var pestana by remember { mutableStateOf(Pestana.MISIONES) }

    // Al entrar se cargan las misiones sin esperar a que el jugador lo pida
    LaunchedEffect(Unit) { if (estado is EstadoUi.Reposo) onRecargar() }

    val misiones = (estado as? EstadoUi.Exito)?.datos.orEmpty()

    FondoJuego {
        Column(Modifier.fillMaxSize()) {
            BarraSuperior(
                misiones = misiones.size,
                puntosEnJuego = misiones.sumOf { it.puntos },
                onRecargar = onRecargar,
                onSalir = onSalir
            )
            TarjetaJugador(nombre, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (pestana) {
                    Pestana.MISIONES -> ContenidoMisiones(estado, onRecargar, onAbrir)
                    Pestana.GRUPO -> Proximamente("👥", "Aquí verás tu grupo, sus miembros y el código de invitación.")
                    Pestana.RANKING -> Proximamente("🏆", "Aquí verás la clasificación del grupo por puntos.")
                }
            }

            BarraInferior(actual = pestana, onCambiar = { pestana = it })
        }
    }
}

@Composable
private fun BarraSuperior(misiones: Int, puntosEnJuego: Int, onRecargar: () -> Unit, onSalir: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pastilla("🎯", "$misiones")
        Pastilla("⭐", "$puntosEnJuego")
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
private fun ContenidoMisiones(estado: EstadoUi<List<Mision>>, onRecargar: () -> Unit, onAbrir: (Int) -> Unit) {
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
                Proximamente("🎯", "Todavía no tienes misiones. Aparecerán cuando empiece la partida.")
            } else {
                ListaMisiones(estado.datos, onAbrir)
            }
        }
    }
}

@Composable
private fun ListaMisiones(misiones: List<Mision>, onAbrir: (Int) -> Unit) {
    val activa = misiones.first()
    val contexto = LocalContext.current
    var concedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var avisoCamara by remember { mutableStateOf(false) }

    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { resultado ->
        concedido = resultado
    }

    LazyColumn(
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
                Spacer(Modifier.height(10.dp))
                Pastilla("⭐", puntosTexto(activa.puntos), Modifier.align(Alignment.CenterHorizontally))
            }
        }
        item {
            Text("PUNTOS GANADOS", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            BarraProgreso(actual = 0, total = misiones.sumOf { it.puntos })
        }
        item {
            BotonGrande(
                texto = "HACER FOTO",
                onClick = {
                    if (concedido) avisoCamara = true
                    else lanzador.launch(Manifest.permission.CAMERA)
                }
            )
            if (concedido && avisoCamara) {
                Spacer(Modifier.height(10.dp))
                Aviso("La cámara llegará en la próxima versión.", fondo = MoradoOscuro)
            }
        }
        if (misiones.size > 1) {
            item {
                TextoJuego("MÁS MISIONES", tamano = 18.sp, alineacion = TextAlign.Start)
            }
            items(misiones.drop(1)) { mision ->
                TarjetaJuego(Modifier.fillMaxWidth().clickable { onAbrir(mision.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Medallon("🎯", tamano = 44.dp, fondo = MoradoClaro)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mision.titulo, color = Color.White, fontSize = 17.sp)
                            Text(mision.descripcion, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
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

/** Contenido de una pestaña que todavía no está hecha. */
@Composable
private fun Proximamente(icono: String, texto: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TarjetaJuego(Modifier.fillMaxWidth()) {
            Medallon(icono, Modifier.align(Alignment.CenterHorizontally), tamano = 80.dp, fondo = MoradoClaro)
            Spacer(Modifier.height(12.dp))
            TextoJuego("PRÓXIMAMENTE", Modifier.fillMaxWidth(), tamano = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text(texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** «1 punto», «3 puntos»… */
fun puntosTexto(puntos: Int): String = if (puntos == 1) "1 punto" else "$puntos puntos"
