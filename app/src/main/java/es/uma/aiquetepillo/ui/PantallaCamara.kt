package es.uma.aiquetepillo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.uma.aiquetepillo.Foto
import es.uma.aiquetepillo.datos.cargarReducida
import es.uma.aiquetepillo.datos.guardarFoto
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.Tinta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Devuelve una acción que abre la cámara: si falta el permiso CAMERA, lo pide
 * antes y solo sigue si se concede. Si se deniega no se insiste, y el resto de
 * la aplicación sigue funcionando; se avisa de que se puede activar en los
 * ajustes, porque tras dos negativas Android ya no vuelve a preguntar.
 */
@Composable
fun rememberAbrirCamara(onAbrir: () -> Unit): () -> Unit {
    val contexto = LocalContext.current
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) onAbrir()
        else Toast.makeText(
            contexto,
            "Sin permiso de cámara. Puedes activarlo en Ajustes > Aplicaciones > AI que te pillo.",
            Toast.LENGTH_LONG
        ).show()
    }
    return {
        val concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (concedido) onAbrir() else lanzador.launch(Manifest.permission.CAMERA)
    }
}

/**
 * Cámara de la aplicación, con CameraX: la trasera para las misiones y la
 * frontal para el selfie de registro.
 *
 * Se usa CameraX y no el Intent ACTION_IMAGE_CAPTURE porque con el Intent la
 * foto la hace la aplicación de cámara del sistema y no hace falta el permiso
 * CAMERA: así la foto es siempre en directo (no se puede elegir una de la
 * galería) y el permiso peligroso tiene un uso real. La foto se recibe en
 * memoria y se guarda cifrada en `fichero`, dentro del directorio privado.
 */
@Composable
fun PantallaCamara(
    fichero: File,
    onHecha: () -> Unit,
    onVolver: () -> Unit,
    frontal: Boolean = false,
    titulo: String = "¡PILLADO!"
) {
    val contexto = LocalContext.current
    val cicloDeVida = LocalLifecycleOwner.current
    val captura = remember { ImageCapture.Builder().build() }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    // La pantalla no da por hecho el permiso: puede haberse retirado desde los ajustes
    val concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    // Al salir de la pantalla se libera la cámara, aunque la Activity siga viva
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(contexto).get().unbindAll() }
        }
    }

    FondoJuego {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonRedondo("⬅️", "Volver", onVolver)
                Spacer(Modifier.weight(1f))
                TextoJuego(titulo, tamano = 22.sp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(3.dp, Tinta, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (concedido) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { vista ->
                                val futuro = ProcessCameraProvider.getInstance(ctx)
                                futuro.addListener({
                                    val proveedor = futuro.get()
                                    val vistaPrevia = Preview.Builder().build()
                                    vistaPrevia.setSurfaceProvider(vista.surfaceProvider)
                                    try {
                                        proveedor.unbindAll()
                                        proveedor.bindToLifecycle(
                                            cicloDeVida,
                                            if (frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                                            vistaPrevia,
                                            captura
                                        )
                                    } catch (e: Exception) {
                                        error = "No se ha podido abrir la cámara."
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Aviso("Hace falta el permiso de cámara para hacer la foto.", Modifier.padding(16.dp))
                }
            }

            Column(Modifier.padding(16.dp)) {
                error?.let {
                    Aviso(it)
                    Spacer(Modifier.height(12.dp))
                }
                BotonGrande(
                    texto = if (guardando) "GUARDANDO..." else "DISPARAR",
                    habilitado = concedido && !guardando,
                    onClick = {
                        guardando = true
                        // En memoria, no en un fichero: así la foto no toca el disco
                        // antes de cifrarse (P3.1)
                        captura.takePicture(
                            ContextCompat.getMainExecutor(contexto),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imagen: ImageProxy) {
                                    alcance.launch {
                                        val guardada = withContext(Dispatchers.Default) {
                                            try {
                                                guardarFoto(imagen.toBitmap(), imagen.imageInfo.rotationDegrees, fichero)
                                                true
                                            } catch (e: Exception) {
                                                Log.w("PantallaCamara", "No se ha guardado la foto: ${e.javaClass.simpleName}: ${e.message}")
                                                false
                                            } finally {
                                                imagen.close()
                                            }
                                        }
                                        if (guardada) onHecha() else {
                                            guardando = false
                                            error = "No se ha podido guardar la foto."
                                        }
                                    }
                                }

                                override fun onError(excepcion: ImageCaptureException) {
                                    guardando = false
                                    error = "No se ha podido hacer la foto."
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

/**
 * Miniatura de una foto guardada, descifrada y reducida.
 *
 * Descifrar es una operación del almacén del sistema, así que se hace fuera
 * del hilo principal; mientras tanto no se dibuja nada.
 */
@Composable
fun MiniaturaFoto(foto: Foto, modifier: Modifier = Modifier, descripcion: String = "Foto de la misión") {
    val imagen by produceState<ImageBitmap?>(null, foto.fichero.path, foto.version) {
        value = null  // que no se vea la anterior mientras carga esta
        value = withContext(Dispatchers.IO) { cargarReducida(foto.fichero, ladoMaximo = 800)?.asImageBitmap() }
    }
    val cargada = imagen ?: return
    Image(
        bitmap = cargada,
        contentDescription = descripcion,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(3.dp, Tinta, RoundedCornerShape(18.dp))
    )
}

/** Círculo con el selfie del jugador o, si no lo tiene, con su inicial. */
@Composable
fun Avatar(nombre: String, selfie: Foto?, tamano: Dp) {
    // Fuera del hilo principal, como en MiniaturaFoto; hasta entonces, la inicial
    val imagen by produceState<ImageBitmap?>(null, selfie?.fichero?.path, selfie?.version) {
        value = null  // que una fila reutilizada no enseñe el selfie de otro jugador
        value = selfie?.let {
            withContext(Dispatchers.IO) { cargarReducida(it.fichero, ladoMaximo = 256)?.asImageBitmap() }
        }
    }
    val cargada = imagen
    if (cargada == null) {
        Medallon(nombre.firstOrNull()?.uppercase() ?: "?", tamano = tamano)
        return
    }
    Image(
        bitmap = cargada,
        contentDescription = "Selfie de $nombre",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(tamano)
            .clip(CircleShape)
            .border(3.dp, Tinta, CircleShape)
    )
}
