package com.lifedashboard.presentation.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.theme.*
import java.util.Locale
import kotlin.math.roundToInt

/** Apartados de Ajustes; cada uno se abre en su propia pantalla. */
enum class SettingsPage(val title: String) {
    PERFIL("Perfil y peso"),
    CALORIAS("Calorías"),
    OBJETIVOS("Objetivos"),
    RECORDATORIOS("Mis recordatorios"),
    AVISOS("Avisos automáticos"),
    HOY("Pantalla Hoy"),
    TEMA("Apariencia"),
    CONEXIONES("Conexiones"),
    GEMINI("Gemini"),
    DATOS("Copia y exportación"),
    ACERCA("Acerca de Halo"),
}

/** Dentro de un apartado, el título ya está en la barra superior. */
internal val LocalInsidePage = compositionLocalOf { false }

private val mono11 = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp)

/**
 * Ajustes: portada con el perfil y los apartados agrupados. Al tocar uno se abre
 * su pantalla con las opciones de siempre.
 */
@Composable
internal fun SettingsHub(state: SettingsState, viewModel: SettingsViewModel, actions: SettingsActions) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
        },
        label = "settingsPage",
    ) { current ->
        if (current == null) {
            HubHome(state, onOpen = { page = it })
        } else {
            PageScreen(current, onBack = { page = null }) {
                CompositionLocalProvider(LocalInsidePage provides true) {
                    when (current) {
                        SettingsPage.PERFIL -> ProfileSection(state, viewModel, actions)
                        SettingsPage.CALORIAS -> CaloriesSection(state, viewModel, actions)
                        SettingsPage.OBJETIVOS -> GoalsSection(state, viewModel, actions)
                        SettingsPage.RECORDATORIOS -> MyRemindersSection(state, viewModel, actions)
                        SettingsPage.AVISOS -> AutoRemindersSection(state, viewModel, actions)
                        SettingsPage.HOY -> HomeScreenSection(state, viewModel, actions)
                        SettingsPage.TEMA -> ThemeSection(state, viewModel, actions)
                        SettingsPage.CONEXIONES -> ConnectionsSection(state, viewModel, actions)
                        SettingsPage.GEMINI -> GeminiSection(state, viewModel, actions)
                        SettingsPage.DATOS -> DataSection(state, viewModel, actions)
                        SettingsPage.ACERCA -> InfoSection(state, viewModel, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageScreen(page: SettingsPage, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Obsidian).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 44.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver a Ajustes", tint = TextHigh)
            }
            Text(page.title, color = TextHigh, style = MaterialTheme.typography.headlineSmall)
        }
        content()
    }
}

// ─── Portada ─────────────────────────────────────────────────────────────────

private data class HubRow(val page: SettingsPage, val icon: ImageVector, val tint: Color, val subtitle: String, val value: String? = null)

@Composable
private fun HubHome(state: SettingsState, onOpen: (SettingsPage) -> Unit) {
    val es = Locale("es", "ES")
    val activeReminders = state.reminders.count { it.isEnabled }
    val autos = listOf(state.bedtimeReminderEnabled, state.dailyReminders, state.eveningReview, state.hydrationReminder, state.weeklySummary).count { it }
    val theme = when (state.themeMode) {
        ThemeMode.CLARO -> "Claro"
        ThemeMode.SISTEMA -> "Sistema"
        ThemeMode.AMOLED -> "Negro OLED"
        else -> "Oscuro"
    }
    val groups = listOf(
        "TÚ" to listOf(
            HubRow(SettingsPage.PERFIL, Icons.Outlined.Person, Ion, "Datos, peso y su evolución", "%.1f kg".format(es, state.weightKg)),
            HubRow(SettingsPage.CALORIAS, Icons.Outlined.LocalFireDepartment, Ember, "Gasto diario y objetivo", "${state.tdeeKcal.roundToInt()} kcal"),
            HubRow(SettingsPage.OBJETIVOS, Icons.Outlined.Flag, Flux, "Pasos, sueño, macros, agua y entreno", "${"%,d".format(es, state.stepsTarget)} pasos"),
        ),
        "AVISOS" to listOf(
            HubRow(SettingsPage.RECORDATORIOS, Icons.Outlined.NotificationsActive, Color(0xFFE5A34B), "Suplementos, hábitos y tomas", if (activeReminders > 0) "$activeReminders activos" else "Ninguno"),
            HubRow(SettingsPage.AVISOS, Icons.Outlined.Schedule, Color(0xFFB58CF0), "Dormir, resúmenes y agua", when (autos) { 0 -> "Apagados"; 1 -> "1 activado"; else -> "$autos activados" }),
        ),
        "APP" to listOf(
            HubRow(SettingsPage.HOY, Icons.Outlined.Dashboard, Ion, "Qué se muestra en Hoy"),
            HubRow(SettingsPage.TEMA, Icons.Outlined.Palette, Color(0xFFE07BB0), "Oscuro, claro o según el móvil", theme),
            HubRow(SettingsPage.CONEXIONES, Icons.Outlined.MonitorHeart, Color(0xFF5DBB7A), "Health Connect y calendario", if (state.healthConnectGranted) "Conectado" else "Sin conectar"),
            HubRow(SettingsPage.GEMINI, Icons.Outlined.AutoAwesome, Flux, "Foto y texto de comida con IA", if (state.geminiApiKey.isNotBlank()) "Activado" else "Sin clave"),
        ),
        "DATOS" to listOf(
            HubRow(SettingsPage.DATOS, Icons.Outlined.CloudUpload, Color(0xFF5BA8E0), "Copia cifrada, restaurar y CSV"),
            HubRow(SettingsPage.ACERCA, Icons.Outlined.Info, TextMute, "Versión y licencia"),
        ),
    )

    Column(
        Modifier.fillMaxSize().background(Obsidian).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Ajustes", color = TextHigh, style = MaterialTheme.typography.headlineMedium)
        ProfileHeader(state, onClick = { onOpen(SettingsPage.PERFIL) })
        groups.forEach { (title, rows) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = TextMute, style = mono11, modifier = Modifier.padding(start = 4.dp))
                val shape = RoundedCornerShape(22.dp)
                Column(Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Line, shape)) {
                    rows.forEachIndexed { i, row ->
                        HubRowView(row) { onOpen(row.page) }
                        if (i < rows.lastIndex) HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: SettingsState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Ion.copy(alpha = 0.16f), Flux.copy(alpha = 0.10f))))
            .border(1.dp, Ion.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Ion), contentAlignment = Alignment.Center) {
            Text(state.userName.trim().take(1).uppercase().ifBlank { "H" }, color = OnAccent, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.userName.ifBlank { "Tu perfil" }, color = TextHigh, style = MaterialTheme.typography.titleLarge)
            Text("${"%.1f".format(Locale("es", "ES"), state.weightKg)} kg · ${state.heightCm} cm", color = TextMute, style = MaterialTheme.typography.bodySmall)
            Text("Gasto ${state.tdeeKcal.roundToInt()} kcal · basal ${state.bmrKcal.roundToInt()} kcal", color = TextMute, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = TextMute)
    }
}

@Composable
private fun HubRowView(row: HubRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(row.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(row.icon, contentDescription = null, tint = row.tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.page.title, color = TextHigh, style = MaterialTheme.typography.bodyLarge)
            Text(row.subtitle, color = TextMute, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        row.value?.let {
            Text(it, color = TextMute, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = TextDim)
    }
}
