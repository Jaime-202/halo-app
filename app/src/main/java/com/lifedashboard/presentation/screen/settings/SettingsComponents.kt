package com.lifedashboard.presentation.screen.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.notification.NotificationHelper
import com.lifedashboard.core.notification.ReminderScheduler
import com.lifedashboard.core.worker.WorkManagerScheduler
import com.lifedashboard.data.local.dao.ReminderDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.entity.ReminderEntity
import com.lifedashboard.data.local.entity.ReminderType
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.repository.CalendarRepository
import com.lifedashboard.presentation.common.components.WeightEvolutionChart
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Piezas reutilizables de Ajustes: tarjetas, filas, interruptores y chips.

@Composable
internal fun CompactMetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Gray400, fontSize = 10.sp)
        Spacer(Modifier.height(1.dp))
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ─── Componente: Seguimiento de Peso & Composición ───────────────────────────

@Composable
internal fun OptionChip(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Violet400 else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelSmall,
            color      = if (isSelected) Indigo900 else White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ThemeOptionChip(label: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Violet400 else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) Indigo900 else White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Indigo900 else White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SwitchSettingItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Gray500)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Indigo900,
                checkedTrackColor   = Violet400,
                uncheckedThumbColor = Gray400,
                uncheckedTrackColor = Indigo700
            )
        )
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = Gray500)
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        content  = content
    )
}

@Composable
internal fun SettingsItem(icon: ImageVector, iconColor: Color, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.15f))
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = White, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Gray500, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

internal fun openHealthConnectSettings(context: Context) {
    val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.apps.healthdata")
            )
            context.startActivity(playStoreIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "Health Connect no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }
}

// ─── Modal: Configuración de Hora para Levantarse ─────────────────────────────
