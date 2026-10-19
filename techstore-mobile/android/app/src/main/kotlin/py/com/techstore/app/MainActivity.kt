package py.com.techstore.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Puente con el instalador de paquetes de Android.
 *
 * No hay nada acá que evada al sistema operativo: se comprueba el permiso que
 * Android exige para instalar desde esta aplicación, se abre su propia pantalla
 * de ajustes cuando falta, y la instalación la resuelve el instalador oficial
 * mostrando su confirmación. La aplicación solo entrega el archivo ya verificado.
 */
class MainActivity : FlutterFragmentActivity() {

    private companion object {
        const val CANAL = "py.com.techstore.app/instalador"
        const val CANAL_MINIATURAS = "py.com.techstore.app/miniaturas"
    }

    /**
     * Un solo hilo: las miniaturas se arman de a una, sin competir con la
     * interfaz por los núcleos.
     */
    private val decodificador = Executors.newSingleThreadExecutor()

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CANAL)
            .setMethodCallHandler { call, resultado ->
                when (call.method) {
                    "puedeInstalar" -> resultado.success(puedeInstalar())
                    "abrirAjustesDeInstalacion" -> resultado.success(abrirAjustes())
                    "instalar" -> instalar(call.argument<String>("ruta"), resultado)
                    else -> resultado.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CANAL_MINIATURAS)
            .setMethodCallHandler { call, resultado ->
                when (call.method) {
                    "rgba" -> miniatura(
                        call.argument<String>("ruta"),
                        call.argument<Int>("ancho") ?: 48,
                        resultado,
                    )
                    else -> resultado.notImplemented()
                }
            }
    }

    override fun onDestroy() {
        decodificador.shutdown()
        super.onDestroy()
    }

    /**
     * Los píxeles de una foto achicada a [ancho], en RGBA, para que la vitrina
     * decida cómo presentarla.
     *
     * Existe porque hacerlo del lado de Flutter salía caro de las dos formas
     * posibles: leer los píxeles de una imagen ya decodificada obliga a traerla
     * de vuelta desde la GPU y frena el dibujo, y decodificarla en Dart puro
     * ocupa un núcleo entero por cada foto grande. El decodificador de Android
     * es nativo y, con el muestreo, nunca arma la foto entera en memoria: un
     * JPEG lo lee directamente achicado y un PNG lo descomprime salteando
     * filas y columnas.
     */
    private fun miniatura(ruta: String?, ancho: Int, resultado: MethodChannel.Result) {
        if (ruta.isNullOrBlank() || ancho <= 0) {
            resultado.error("argumentos", "Falta la ruta o el ancho", null)
            return
        }
        decodificador.execute {
            try {
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(ruta, medidas)
                if (medidas.outWidth <= 0 || medidas.outHeight <= 0) {
                    runOnUiThread { resultado.error("ilegible", "No es una imagen", null) }
                    return@execute
                }
                // La potencia de dos más grande que todavía deja la foto más
                // ancha que la miniatura: el resto lo hace el escalado final.
                var muestreo = 1
                while (medidas.outWidth / (muestreo * 2) >= ancho) muestreo *= 2
                val decodificada = BitmapFactory.decodeFile(
                    ruta,
                    BitmapFactory.Options().apply {
                        inSampleSize = muestreo
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ) ?: throw IllegalStateException("No se pudo decodificar")
                val alto = max(1, decodificada.height * ancho / decodificada.width)
                val chica = Bitmap.createScaledBitmap(decodificada, ancho, alto, true)
                if (chica !== decodificada) decodificada.recycle()
                // ARGB_8888 guarda los bytes en orden R, G, B, A.
                val pixeles = ByteBuffer.allocate(chica.byteCount)
                chica.copyPixelsToBuffer(pixeles)
                chica.recycle()
                val datos = mapOf("ancho" to ancho, "alto" to alto, "rgba" to pixeles.array())
                runOnUiThread { resultado.success(datos) }
            } catch (error: Exception) {
                runOnUiThread { resultado.error("miniatura", error.message, null) }
            }
        }
    }

    private fun puedeInstalar(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            // Antes de Android 8 el permiso era global del dispositivo y no se
            // consultaba por aplicación.
            true
        }

    private fun abrirAjustes(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            true
        } catch (error: Exception) {
            false
        }
    }

    private fun instalar(ruta: String?, resultado: MethodChannel.Result) {
        if (ruta.isNullOrBlank()) {
            resultado.error("ruta_invalida", "No se indicó el archivo a instalar", null)
            return
        }
        val archivo = File(ruta)
        if (!archivo.isFile) {
            resultado.error("archivo_ausente", "El archivo descargado ya no está", null)
            return
        }
        if (!puedeInstalar()) {
            resultado.error("sin_permiso", "Falta el permiso para instalar desde esta app", null)
            return
        }
        try {
            // FileProvider: pasar un file:// directo lo rechaza Android desde Nougat.
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            resultado.success(true)
        } catch (error: Exception) {
            resultado.error("instalacion_fallida", error.message, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedirLaFrecuenciaMasAlta()
    }

    /**
     * Las pantallas de 90 y 120 Hz —la mayoría de los Redmi, Poco y Samsung de
     * gama media— dejan a cada app en 60 Hz salvo que la app pida más. Se pide
     * el modo más rápido que tenga la misma resolución: Flutter dibuja al ritmo
     * de la pantalla, y al salir de TechStore el sistema vuelve al suyo.
     */
    private fun pedirLaFrecuenciaMasAlta() {
        val pantalla = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return
        val actual = pantalla.mode
        val mejor = pantalla.supportedModes
            .filter {
                it.physicalWidth == actual.physicalWidth &&
                    it.physicalHeight == actual.physicalHeight
            }
            .maxByOrNull { it.refreshRate } ?: return
        if (mejor.modeId == actual.modeId) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = mejor.modeId }
    }
}
