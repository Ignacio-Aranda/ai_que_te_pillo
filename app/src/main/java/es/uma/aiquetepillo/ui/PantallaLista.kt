package es.uma.aiquetepillo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*          // sustituye a runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import es.uma.aiquetepillo.modelo.*

/**
 * Listado de elementos. ESTÁ TERMINADA: no hace falta tocarla en P2.2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(
    estado: EstadoUi<List<Elemento>>,
    onRecargar: () -> Unit,
    onSalir: () -> Unit,
    onAbrir: (Int) -> Unit
) {
    val contexto = LocalContext.current
    var concedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { resultado ->
        concedido = resultado
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis elementos") },
                actions = {
                    TextButton(onClick = onRecargar) { Text("Recargar") }
                    TextButton(onClick = onSalir) { Text("Salir") }
                }
            )
        }
    ) { relleno ->
        Column(modifier = Modifier.fillMaxSize().padding(relleno)) {

            Button(
                onClick = { lanzador.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(if (concedido) "Permiso concedido" else "Conceder permiso")
            }

            if (!concedido) {
                Text(
                    "Sin este permiso no se pueden hacer fotos con la cámara de la aplicación. " +
                        "El resto de la aplicación sigue funcionando.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (estado) {
                    is EstadoUi.Reposo -> Text("Pulsa Recargar para cargar los datos")
                    is EstadoUi.Cargando -> CircularProgressIndicator()
                    is EstadoUi.Error -> Text("No se han podido cargar los elementos")
                    is EstadoUi.Exito -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    ) {
                        items(estado.datos) { elemento ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { onAbrir(elemento.id) }
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(elemento.titulo, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text(elemento.contenido, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text(elemento.fecha, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
