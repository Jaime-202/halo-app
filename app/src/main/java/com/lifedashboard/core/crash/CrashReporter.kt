package com.lifedashboard.core.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Guarda en el móvil el error que cerró la app, para mostrarlo al abrirla otra vez
 * y poder copiarlo. No se envía a ningún sitio.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val version = try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                } catch (_: Exception) { "?" }
                File(app.filesDir, FILE).writeText(
                    buildString {
                        appendLine("Halo $version · ${LocalDateTime.now()}")
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Hilo: ${thread.name}")
                        appendLine()
                        append(sw.toString().take(12_000))
                    }
                )
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
