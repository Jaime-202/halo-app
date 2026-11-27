package com.lifedashboard.domain.health

/**
 * Calcula el sueño real de una noche a partir de las sesiones de Health Connect.
 *
 * Problemas que corrige:
 *  - Varias apps (reloj, Samsung Health, Google Fit…) escriben la misma noche: antes se sumaban.
 *  - Si te despiertas, algunas apps crean dos sesiones que pueden solaparse: antes se sumaban.
 *  - El tiempo despierto dentro de la sesión (fases "despierto" o "fuera de la cama") contaba como sueño.
 *
 * Regla: se elige UNA fuente (la que tiene fases, porque es la más precisa; si no, la que más
 * cubre) y dentro de ella se unen los intervalos, de modo que ningún minuto cuenta dos veces.
 */
object SleepCalculator {

    const val STAGE_UNKNOWN = 0
    const val STAGE_AWAKE = 1
    const val STAGE_SLEEPING = 2
    const val STAGE_OUT_OF_BED = 3
    const val STAGE_LIGHT = 4
    const val STAGE_DEEP = 5
    const val STAGE_REM = 6
    const val STAGE_AWAKE_IN_BED = 7

    data class Stage(val start: Long, val end: Long, val type: Int)
    data class Session(val source: String, val start: Long, val end: Long, val stages: List<Stage> = emptyList())
    data class Night(val asleepMinutes: Long, val deepMinutes: Long, val remMinutes: Long, val source: String?)

    private val awakeTypes = setOf(STAGE_AWAKE, STAGE_OUT_OF_BED, STAGE_AWAKE_IN_BED)

    fun night(sessions: List<Session>, windowStart: Long, windowEnd: Long): Night {
        val candidates = sessions.groupBy { it.source }.map { (source, list) ->
            val stages = list.flatMap { it.stages }
            val hasStages = stages.isNotEmpty()
            val asleep = if (hasStages) {
                union(stages.filter { it.type !in awakeTypes }.map { it.start to it.end }, windowStart, windowEnd)
            } else {
                union(list.map { it.start to it.end }, windowStart, windowEnd)
            }
            val deep = union(stages.filter { it.type == STAGE_DEEP }.map { it.start to it.end }, windowStart, windowEnd)
            val rem = union(stages.filter { it.type == STAGE_REM }.map { it.start to it.end }, windowStart, windowEnd)
            Triple(hasStages, Night(asleep / 60_000, deep / 60_000, rem / 60_000, source), asleep)
        }.filter { it.third > 0 }
        val best = candidates.sortedWith(compareByDescending<Triple<Boolean, Night, Long>> { it.first }.thenByDescending { it.third }).firstOrNull()
        return best?.second ?: Night(0, 0, 0, null)
    }

    /** Milisegundos cubiertos por la unión de intervalos, recortados a la ventana. */
    fun union(intervals: List<Pair<Long, Long>>, windowStart: Long, windowEnd: Long): Long {
        val clipped = intervals.map { (s, e) -> maxOf(s, windowStart) to minOf(e, windowEnd) }
            .filter { it.second > it.first }.sortedBy { it.first }
        var total = 0L
        var curStart = -1L
        var curEnd = -1L
        for ((s, e) in clipped) {
            if (curEnd < 0 || s > curEnd) {
                if (curEnd > curStart) total += curEnd - curStart
                curStart = s; curEnd = e
            } else if (e > curEnd) curEnd = e
        }
        if (curEnd > curStart) total += curEnd - curStart
        return total
    }
}
