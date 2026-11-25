package com.lifedashboard.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lifedashboard.data.local.dao.HealthDao
import com.lifedashboard.data.local.dao.MealDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copia de seguridad cifrada y exportación CSV.
 *
 * La copia guarda la base de datos y las preferencias en un .halo cifrado con
 * AES-256-GCM (clave derivada de tu contraseña con PBKDF2). El archivo se guarda
 * donde elijas con el selector de Android, incluido Google Drive, sin dar a Halo
 * acceso a tu cuenta.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val weightDao: WeightDao,
    private val mealDao: MealDao,
    private val healthDao: HealthDao,
) {

    class WrongPasswordException : Exception("Contraseña incorrecta o archivo dañado")
    class InvalidBackupException : Exception("El archivo no es una copia de Halo")

    fun suggestedBackupName(): String = "halo-copia-${stamp()}.halo"
    fun suggestedCsvName(): String = "halo-datos-${stamp()}.zip"

    // ─── Copia cifrada ────────────────────────────────────────────────────────

    suspend fun createBackup(target: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        // Volcar el WAL a la base de datos principal para copiar un único archivo coherente
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zip ->
                backupFiles().forEach { (name, file) ->
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry(name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            bos.toByteArray()
        }

        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(zipBytes)

        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            out.write(MAGIC)
            out.write(salt)
            out.write(iv)
            out.write(encrypted)
        } ?: error("No se pudo escribir el archivo")
    }

    /**
     * Restaura una copia. Sustituye la base de datos y las preferencias actuales;
     * después hay que reiniciar la app (ver [restartApp]).
     */
    suspend fun restoreBackup(source: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val all = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: error("No se pudo leer el archivo")
        if (all.size < MAGIC.size + SALT_LEN + IV_LEN || !all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw InvalidBackupException()
        }
        var p = MAGIC.size
        val salt = all.copyOfRange(p, p + SALT_LEN); p += SALT_LEN
        val iv = all.copyOfRange(p, p + IV_LEN); p += IV_LEN
        val zipBytes = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(all, p, all.size - p)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        }

        // Extraer primero a una carpeta temporal y comprobar que contiene la base de datos
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (backupFiles().any { it.first == name }) {
                    File(staging, name).outputStream().use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
        if (!File(staging, DB_ENTRY).exists()) throw InvalidBackupException()

        db.close()
        backupFiles().forEach { (name, dest) ->
            val src = File(staging, name)
            dest.parentFile?.mkdirs()
            if (src.exists()) src.copyTo(dest, overwrite = true) else if (name != DB_ENTRY) dest.delete()
        }
        // Borrar restos del WAL antiguo para que no se mezclen con la base restaurada
        File(dbFile().path + "-wal").delete()
        File(dbFile().path + "-shm").delete()
        staging.deleteRecursively()
    }

    /** Reinicia la app para que cargue los datos restaurados. */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    // ─── Exportar CSV ─────────────────────────────────────────────────────────

    suspend fun exportCsv(target: Uri) = withContext(Dispatchers.IO) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val pesos = buildString {
            appendLine("fecha,peso_kg,nota")
            weightDao.getAllWeights().forEach {
                appendLine(listOf(time.format(Date(it.dateMillis)), it.weightKg.toString(), it.note.orEmpty()).toCsv())
            }
        }
        val comidas = buildString {
            appendLine("fecha,momento,alimento,marca,gramos,kcal,proteina_g,carbohidratos_g,grasa_g,notas")
            mealDao.getAllMeals().forEach {
                appendLine(
                    listOf(
                        day.format(Date(it.dateMillis)), it.mealType, it.name, it.brand,
                        it.grams.toString(), it.calories.toString(), it.protein.toString(),
                        it.carbs.toString(), it.fat.toString(), it.notes,
                    ).toCsv()
                )
            }
        }
        val salud = buildString {
            appendLine("fecha,pasos,distancia_m,kcal_activas,sueno_h,sueno_profundo_h,sueno_rem_h")
            healthDao.getAll().forEach {
                appendLine(
                    listOf(
                        day.format(Date(it.dateMillis)), it.steps.toString(), it.distanceMeters.toString(),
                        it.activeCalories.toString(), it.sleepHours.toString(),
                        it.sleepDeepHours.toString(), it.sleepRemHours.toString(),
                    ).toCsv()
                )
            }
        }

        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            ZipOutputStream(out).use { zip ->
                mapOf("pesos.csv" to pesos, "comidas.csv" to comidas, "salud.csv" to salud).forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    // BOM para que Excel abra bien las tildes
                    zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        } ?: error("No se pudo escribir el archivo")
    }

    // ─── Utilidades ───────────────────────────────────────────────────────────

    private fun dbFile(): File = context.getDatabasePath(DB_NAME)

    private fun backupFiles(): List<Pair<String, File>> = listOf(
        DB_ENTRY to dbFile(),
        PREFS_ENTRY to File(context.filesDir, "datastore/user_preferences.preferences_pb"),
    )

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun List<String>.toCsv(): String = joinToString(",") { field ->
        if (field.any { it == ',' || it == '"' || it == '\n' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private companion object {
        val MAGIC = "HALO1".toByteArray(Charsets.US_ASCII)
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val TAG_BITS = 128
        const val PBKDF2_ITERATIONS = 210_000
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DB_NAME = "life_dashboard.db"
        const val DB_ENTRY = "life_dashboard.db"
        const val PREFS_ENTRY = "user_preferences.preferences_pb"
    }
}
