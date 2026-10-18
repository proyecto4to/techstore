# Claves de Firebase para la app móvil, en un solo lugar.
#
# La app no usa google-services.json: arma FirebaseOptions con lo que llega por
# --dart-define (lib/core/push/firebase_configuration.dart). Si esos valores no
# viajan en la compilación, `FirebaseConfiguration.configured` da falso, la app
# arranca igual y las notificaciones quedan apagadas sin decir nada. Por eso
# este archivo existe: que la ausencia se avise fuerte al compilar.
#
# El archivo de claves vive FUERA del repositorio, junto al keystore:
#   %LOCALAPPDATA%\TechStore\mobile-signing\firebase-android.json
#
# Se baja de la consola de Firebase (Configuración del proyecto → Tus apps →
# Android) y tiene esta forma:
#   {
#     "apiKey": "AIza...",
#     "appId": "1:000000000000:android:abc123",
#     "messagingSenderId": "000000000000",
#     "projectId": "techstore-xxxxx",
#     "storageBucket": "techstore-xxxxx.firebasestorage.app"
#   }
#
# No es un secreto de servidor —viaja dentro del APK y Google la considera
# pública—, pero igual se guarda afuera para no versionar la identidad del
# proyecto y para que cada quien compile con la suya. Lo que sí es secreto es
# la credencial del backend (GOOGLE_APPLICATION_CREDENTIALS), que nunca entra
# en la app.

function Get-FirebaseMobileDefines {
    param(
        # Cuando es $true, la falta de claves corta la compilación en vez de
        # avisar. La usa el build de release: publicar una versión sin push,
        # sin enterarse, es peor que no publicar.
        [switch]$Obligatorio
    )

    $archivo = Join-Path $env:LOCALAPPDATA 'TechStore\mobile-signing\firebase-android.json'
    if (-not (Test-Path -LiteralPath $archivo)) {
        $aviso = "Falta $archivo. La app se compila sin Firebase y no va a recibir notificaciones con la app cerrada. Ver docs/mobile-expansion/PUSH.md."
        if ($Obligatorio) { throw $aviso }
        Write-Warning $aviso
        return @()
    }

    $config = Get-Content -LiteralPath $archivo -Raw | ConvertFrom-Json
    $requeridas = 'apiKey', 'appId', 'messagingSenderId', 'projectId'
    $faltantes = $requeridas | Where-Object { [string]::IsNullOrWhiteSpace($config.$_) }
    if ($faltantes) {
        throw "A $archivo le faltan campos: $($faltantes -join ', ')."
    }
    if ($config.appId -notmatch ':android:') {
        throw "El appId de $archivo no es de Android; en la consola hay que elegir la app Android, no la web ni la de iOS."
    }

    $defines = @(
        "--dart-define=FIREBASE_API_KEY=$($config.apiKey)",
        "--dart-define=FIREBASE_APP_ID=$($config.appId)",
        "--dart-define=FIREBASE_MESSAGING_SENDER_ID=$($config.messagingSenderId)",
        "--dart-define=FIREBASE_PROJECT_ID=$($config.projectId)"
    )
    if (-not [string]::IsNullOrWhiteSpace($config.storageBucket)) {
        $defines += "--dart-define=FIREBASE_STORAGE_BUCKET=$($config.storageBucket)"
    }
    Write-Host "  [push] Firebase: proyecto $($config.projectId)." -ForegroundColor Green
    return $defines
}
