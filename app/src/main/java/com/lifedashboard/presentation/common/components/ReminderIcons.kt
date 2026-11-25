package com.lifedashboard.presentation.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Iconos de los recordatorios. Se guarda la clave (p. ej. "pastilla") en la columna
 * `iconEmoji`; los emojis de versiones anteriores se traducen a su icono equivalente.
 */
enum class ReminderIcon(val key: String, val icon: ImageVector, val label: String) {
    PASTILLA("pastilla", Icons.Outlined.Medication, "Suplemento"),
    AGUA("agua", Icons.Outlined.WaterDrop, "Agua"),
    PESAS("pesas", Icons.Outlined.FitnessCenter, "Entreno"),
    COMIDA("comida", Icons.Outlined.Restaurant, "Comida"),
    ALARMA("alarma", Icons.Outlined.Alarm, "Alarma"),
    NATURAL("natural", Icons.Outlined.Spa, "Natural"),
    ENERGIA("energia", Icons.Outlined.Bolt, "Energía"),
    CALMA("calma", Icons.Outlined.SelfImprovement, "Meditar"),
    CAFE("cafe", Icons.Outlined.Coffee, "Café"),
    PESO("peso", Icons.Outlined.MonitorWeight, "Pesarse"),
    SUENO("sueno", Icons.Outlined.Bedtime, "Dormir"),
    PASEO("paseo", Icons.AutoMirrored.Outlined.DirectionsWalk, "Caminar");

    companion object {
        private val legacy = mapOf(
            "💊" to PASTILLA, "💧" to AGUA, "🏋️" to PESAS, "🏋" to PESAS, "🥗" to COMIDA, "⏰" to ALARMA,
            "🌿" to NATURAL, "⚡" to ENERGIA, "🧘" to CALMA, "☕" to CAFE, "🍎" to COMIDA, "⚖️" to PESO,
        )

        fun from(value: String?): ReminderIcon =
            entries.firstOrNull { it.key == value } ?: legacy[value?.trim()] ?: PASTILLA
    }
}
