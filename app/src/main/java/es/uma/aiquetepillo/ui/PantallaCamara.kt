package es.uma.aiquetepillo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.uma.aiquetepillo.FotoMision
import es.uma.aiquetepillo.ui.juego.*
import es.uma.aiquetepillo.ui.theme.Tinta
import java.io.File

/**
 * Cámara de la misión, con CameraX dentro de la aplicación.
 *
 * Se usa CameraX y no el Intent ACTION_IMAGE_CAPTURE porque con el Intent la
 * foto la hace la aplicación de cámara del sistema y no hace falta el permiso
 * CAMERA: así la foto es siempre en directo y el permiso peligroso tiene un uso
 * real. La foto se guarda en `fichero`, dentro del directorio privado.
 */
@Composable
fun PantallaCamara(fichero: File, onHecha: () -> Unit, onVolver: () -> Unit) {
    val contexto = LocalContext.current
    val cicloDeVida = LocalLifecycleOwner.current
    val captura = remember { ImageCapture.Builder().build() }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

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
                TextoJuego("¡PILLADO!", tamano = 22.sp)
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
                                            cicloDeVida, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, captura
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
                        captura.takePicture(
                            ImageCapture.OutputFileOptions.Builder(fichero).build(),
                            ContextCompat.getMainExecutor(contexto),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                                    onHecha()
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

/** Miniatura de una foto guardada, reducida y girada según su EXIF. */
@Composable
fun MiniaturaFoto(foto: FotoMision, modifier: Modifier = Modifier) {
    val imagen = remember(foto.fichero.path, foto.version) {
        cargarReducida(foto.fichero, ladoMaximo = 800)?.asImageBitmap()
    }
    if (imagen != null) {
        Image(
            bitmap = imagen,
            contentDescription = "Foto de la misión",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .border(3.dp, Tinta, RoundedCornerShape(18.dp))
        )
    }
}

private fun cargarReducida(fichero: File, ladoMaximo: Int): Bitmap? {
    if (!fichero.exists()) return null
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(fichero.path, medidas)
    var reduccion = 1
    while (maxOf(medidas.outWidth, medidas.outHeight) / reduccion > ladoMaximo * 2) reduccion *= 2
    val mapa = BitmapFactory.decodeFile(fichero.path, BitmapFactory.Options().apply { inSampleSize = reduccion })
        ?: return null

    // CameraX guarda la orientación en el EXIF en lugar de girar los píxeles
    val grados = when (ExifInterface(fichero.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (grados == 0f) return mapa
    val giro = Matrix().apply { postRotate(grados) }
    return Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, giro, true)
}
