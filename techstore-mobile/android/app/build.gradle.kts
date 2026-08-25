import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val releaseKeystoreFile = rootProject.file("key.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystoreFile.isFile) {
        FileInputStream(releaseKeystoreFile).use(::load)
    }
}
val allowLocalReleaseSigning =
    providers.gradleProperty("allowLocalReleaseSigning").orNull.equals("true", ignoreCase = true) ||
        providers.environmentVariable("TECHSTORE_ALLOW_LOCAL_RELEASE_SIGNING")
            .orNull.equals("true", ignoreCase = true)

fun requiredSigningProperty(name: String): String =
    releaseKeystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Falta '$name' en android/key.properties")

android {
    namespace = "py.com.techstore.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    buildFeatures {
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "py.com.techstore.app"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    flavorDimensions += "environment"
    productFlavors {
        create("development") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "TechStore Dev")
        }
        create("private") {
            dimension = "environment"
            applicationIdSuffix = ".private"
            resValue("string", "app_name", "TechStore")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "TechStore Staging")
        }
        create("production") {
            dimension = "environment"
            resValue("string", "app_name", "TechStore")
        }
    }

    signingConfigs {
        if (releaseKeystoreFile.isFile) {
            create("release") {
                storeFile = file(requiredSigningProperty("storeFile"))
                storePassword = requiredSigningProperty("storePassword")
                keyAlias = requiredSigningProperty("keyAlias")
                keyPassword = requiredSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = when {
                releaseKeystoreFile.isFile -> signingConfigs.getByName("release")
                allowLocalReleaseSigning -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

}

gradle.taskGraph.whenReady {
    val buildsProductionRelease = allTasks.any {
        it.name.contains("ProductionRelease", ignoreCase = true)
    }
    if (buildsProductionRelease && !releaseKeystoreFile.isFile && !allowLocalReleaseSigning) {
        throw GradleException(
            "Release production requiere android/key.properties. " +
                "Solo para auditoría local puede usarse TECHSTORE_ALLOW_LOCAL_RELEASE_SIGNING=true."
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
