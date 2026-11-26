package com.lifedashboard.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Application class.
 *
 * Implementa [Configuration.Provider] para que WorkManager use
 * HiltWorkerFactory — esto es OBLIGATORIO para inyectar dependencias
 * en Workers con @HiltWorker.
 */
@HiltAndroidApp
class LifeDashboardApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var foodCatalog: dagger.Lazy<com.lifedashboard.data.remote.food.FoodCatalogService>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        com.lifedashboard.core.crash.CrashReporter.install(this)
        // Precargar catálogos de comida cuando la app ya se ha mostrado
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(2_000)
            try { foodCatalog.get().warmUp() } catch (_: Exception) { }
        }
        try {
            WorkManager.initialize(this, workManagerConfiguration)
        } catch (e: Exception) {
            // Continuar si ya está inicializado por otra vía
        }
    }
}
