package com.lifedashboard.presentation.screen.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.common.components.ReminderIcon
import com.lifedashboard.presentation.screen.dashboard.DashboardReminderItem
import com.lifedashboard.presentation.theme.*
import java.time.LocalDate

/** Recordatorios del día: un toque en el círculo los marca como hechos. */
@Composable
fun TodayRemindersCard(
    reminders: List<DashboardReminderItem>,
    selectedDate: LocalDate,
    onToggleReminder: (Long, Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val done = reminders.count { it.isCompleted }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape).padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selectedDate == LocalDate.now()) "RECORDATORIOS" else "RECORDATORIOS DEL DÍA",
                color = TextMute, style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp),
                modifier = Modifier.weight(1f),
            )
            if (reminders.isNotEmpty()) {
                Text("$done/${reminders.size}", color = if (done == reminders.size) Ion else TextMute, style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp))
            }
        }
        if (reminders.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onNavigateToSettings).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = TextMute, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sin recordatorios para hoy", color = TextHigh, style = MaterialTheme.typography.bodyMedium)
                    Text("Créalos en Ajustes → Mis recordatorios", color = TextMute, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        reminders.forEach { item ->
            val check by animateColorAsState(if (item.isCompleted) Ion else Color.Transparent, tween(HaloMotion.FAST), label = "check")
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggleReminder(item.id, !item.isCompleted) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Ion.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(ReminderIcon.from(item.iconEmoji).icon, contentDescription = null, tint = Ion, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title, color = if (item.isCompleted) TextMute else TextHigh, style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                    )
                    Text(item.subtitle, color = TextMute, style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(check).border(1.5.dp, if (item.isCompleted) Ion else LineStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.isCompleted) Icon(Icons.Outlined.Check, contentDescription = "Hecho", tint = OnAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun <T> tween(ms: Int) = androidx.compose.animation.core.tween<T>(ms)
