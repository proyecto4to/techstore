package py.com.techstore.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

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
    }

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
}
