package com.lifedashboard.data.importer

import android.content.Context
import android.net.Uri
import com.lifedashboard.data.local.dao.WorkoutDao
import com.lifedashboard.data.local.entity.ExerciseSetEntity
import com.lifedashboard.data.local.entity.WorkoutSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class CsvSet(val exercise: String, val setNumber: Int, val reps: Int, val weightKg: Float)
data class CsvWorkoutDay(val date: LocalDate, val sets: List<CsvSet>)

/**
 * Lee exportaciones CSV de entrenos (Symmetry u otras apps) sin depender de un formato fijo:
 * busca las columnas por nombre (fecha/date, ejercicio/exercise, serie/set, reps, peso/weight)
 * y convierte libras a kg si la cabecera lo indica.
 */
object SymmetryCsvParser {

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
    )

    fun parse(text: String): List<CsvWorkoutDay> {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()
        val sep = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val header = split(lines[0], sep).map { it.trim().lowercase() }

        fun col(vararg keys: String) = header.indexOfFirst { h -> keys.any { h.contains(it) } }
        val iDate = col("date", "fecha", "day", "día")
        val iExercise = col("exercise", "ejercicio", "movement")
        val iSet = col("set", "serie")
        val iReps = col("rep")
        val iWeight = col("weight", "peso", "load", "carga", "kg", "lb")
        if (iDate < 0 || iExercise < 0) return emptyList()
        val inPounds = iWeight >= 0 && header[iWeight].contains("lb")

        val sets = mutableListOf<Pair<LocalDate, CsvSet>>()
        val counters = mutableMapOf<Pair<LocalDate, String>, Int>()
        for (line in lines.drop(1)) {
            val c = split(line, sep)
            val date = c.getOrNull(iDate)?.let(::parseDate) ?: continue
            val exercise = c.getOrNull(iExercise)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val key = date to exercise.lowercase()
            val auto = (counters[key] ?: 0) + 1
            counters[key] = auto
            val setNumber = c.getOrNull(iSet)?.trim()?.toIntOrNull() ?: auto
            val reps = c.getOrNull(iReps)?.trim()?.toFloatOrNull()?.toInt() ?: 0
            var weight = c.getOrNull(iWeight)?.trim()?.replace(',', '.')?.toFloatOrNull() ?: 0f
            if (inPounds) weight *= 0.453592f
            sets += date to CsvSet(normalizeExercise(exercise), setNumber, reps, weight)
        }
        return sets.groupBy({ it.first }, { it.second })
            .map { (d, s) -> CsvWorkoutDay(d, s) }
            .sortedBy { it.date }
    }

    private fun parseDate(raw: String): LocalDate? {
        val t = raw.trim().trim('"')
        for (f in dateFormats) {
            try { return LocalDateTime.parse(t, f).toLocalDate() } catch (_: Exception) {}
            try { return LocalDate.parse(t, f) } catch (_: Exception) {}
        }
        return null
    }

    /** Separa una línea CSV respetando comillas. */
    private fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private val names = mapOf(
        "squat pendulum" to "Sentadilla en péndulo",
        "pendulum squat" to "Sentadilla en péndulo",
        "romanian deadlift" to "Peso muerto rumano",
        "rdl" to "Peso muerto rumano",
        "unilateral high row" to "Remo alto unilateral",
        "chest press" to "Press de pecho",
        "incline chest press" to "Press inclinado",
        "machine crunch" to "Abdominales en máquina",
        "ab crunch machine" to "Abdominales en máquina",
        "lat pulldown" to "Jalón al pecho",
        "cable row" to "Remo en polea",
        "leg press" to "Prensa de pierna",
        "shoulder press" to "Press de hombros",
        "lateral raise" to "Elevaciones laterales",
        "bicep curl" to "Curl de bíceps",
        "tricep pushdown" to "Extensión de tríceps",
    )

    fun normalizeExercise(raw: String): String = names[raw.trim().lowercase()] ?: raw.trim()
}

/** Guarda en la base de datos lo leído de un CSV. Reimportar el mismo archivo no duplica. */
@Singleton
class SymmetryCsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutDao: WorkoutDao,
) {
    /** @return número de días de entreno importados */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: error("No se pudo leer el archivo")
        val days = SymmetryCsvParser.parse(text)
        val zone = ZoneId.systemDefault()
        days.forEach { day ->
            val dayMillis = day.date.atStartOfDay(zone).toInstant().toEpochMilli()
            workoutDao.deleteCsvSessionsForDay(dayMillis)
            val sessionId = workoutDao.upsertSession(
                WorkoutSessionEntity(
                    dateMillis = dayMillis,
                    type = "STRENGTH",
                    durationMinutes = 0,
                    sourceFile = "symmetry_csv",
                )
            )
            workoutDao.upsertSets(
                day.sets.map {
                    ExerciseSetEntity(
                        sessionId = sessionId,
                        exerciseName = it.exercise,
                        setNumber = it.setNumber,
                        reps = it.reps,
                        weightKg = it.weightKg,
                        volumeKg = it.weightKg * it.reps,
                    )
                }
            )
        }
        days.size
    }
}
