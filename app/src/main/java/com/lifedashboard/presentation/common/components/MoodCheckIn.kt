package com.lifedashboard.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.theme.*

private val moodIcons = listOf(
    Icons.Outlined.SentimentVeryDissatisfied,
    Icons.Outlined.SentimentDissatisfied,
    Icons.Outlined.SentimentNeutral,
    Icons.Outlined.SentimentSatisfied,
    Icons.Outlined.SentimentVerySatisfied,
)
private val moodLabels = listOf("Muy mal", "Mal", "Normal", "Bien", "Muy bien")
private val energyLabels = listOf("Agotado", "Cansado", "Normal", "Con energía", "A tope")
private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

fun moodLabel(v: Int) = moodLabels.getOrElse(v - 1) { "—" }
fun energyLabel(v: Int) = energyLabels.getOrElse(v - 1) { "—" }

/** Selector de ánimo (caras) y energía (barras), del 1 al 5. */
@Composable
fun MoodEnergyPicker(mood: Int?, energy: Int?, onMood: (Int) -> Unit, onEnergy: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ÁNIMO" + (mood?.let { " · " + moodLabel(it) } ?: ""), color = TextMute, style = mono11)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            moodIcons.forEachIndexed { i, icon ->
                val v = i + 1
                val on = mood == v
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (on) PanelHigh else Panel)
                        .border(1.dp, if (on) Ion else Line, CircleShape)
                        .clickable(role = Role.RadioButton, onClickLabel = moodLabels[i]) { onMood(v) },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = moodLabels[i], tint = if (on) Ion else TextMute) }
            }
        }
        Text("ENERGÍA" + (energy?.let { " · " + energyLabel(it) } ?: ""), color = TextMute, style = mono11)
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            (1..5).forEach { v ->
                val filled = energy != null && v <= energy
                Box(
                    Modifier.weight(1f).fillMaxHeight(0.3f + 0.14f * v).clip(RoundedCornerShape(6.dp))
                        .background(if (filled) Flux else Line)
                        .clickable(role = Role.RadioButton, onClickLabel = energyLabels[v - 1]) { onEnergy(v) }
                )
            }
        }
    }
}

/** Tarjeta de Hoy: ¿qué tal el día? Aparece por la tarde o si ya lo has registrado. */
@Composable
fun MoodCheckInCard(mood: Int?, energy: Int?, onSave: (Int, Int) -> Unit) {
    var m by remember(mood) { mutableStateOf(mood) }
    var e by remember(energy) { mutableStateOf(energy) }
    var editing by remember(mood) { mutableStateOf(mood == null) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!editing && mood != null && energy != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(moodIcons[mood - 1], contentDescription = null, tint = Ion)
                Spacer(Modifier.width(10.dp))
                Text("${moodLabel(mood)} · ${energyLabel(energy).lowercase()}", color = TextHigh, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("Cambiar", color = Ion, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { editing = true }.padding(6.dp))
            }
        } else {
            Text("¿Qué tal el día?", color = TextHigh, style = MaterialTheme.typography.titleMedium)
            MoodEnergyPicker(m, e, { m = it; e?.let { en -> onSave(it, en); editing = false } }, { e = it; m?.let { mo -> onSave(mo, it); editing = false } })
        }
    }
}

@Composable
fun MoodDialog(initialMood: Int?, initialEnergy: Int?, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var m by remember { mutableStateOf(initialMood) }
    var e by remember { mutableStateOf(initialEnergy) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Ánimo y energía", color = TextHigh) },
        text = { MoodEnergyPicker(m, e, { m = it }, { e = it }) },
        confirmButton = {
            TextButton(onClick = { if (m != null && e != null) onSave(m!!, e!!) }, enabled = m != null && e != null) { Text("Guardar", color = Ion) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMute) } },
    )
}
