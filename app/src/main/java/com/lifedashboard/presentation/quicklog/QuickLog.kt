package com.lifedashboard.presentation.quicklog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WidgetUpdateWorker
import com.lifedashboard.data.local.dao.WaterIntakeDao
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.entity.WaterIntakeEntity
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.data.repository.EnergyTargetsManager
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/** Acciones de comida que el "+" pide a la pantalla Comida. */
enum class NutritionAction { SEARCH, PHOTO, BARCODE, LABEL, TEXT }

/** Canal sencillo entre el botón "+" y la pantalla Comida (se consume una vez). */
object QuickActionBus {
    private val _pending = MutableStateFlow<NutritionAction?>(null)
    val pending: StateFlow<NutritionAction?> = _pending.asStateFlow()
    fun request(action: NutritionAction) { _pending.value = action }
    fun consume(): NutritionAction? = _pending.value.also { _pending.value = null }
}

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val weightDao: WeightDao,
    private val waterIntakeDao: WaterIntakeDao,
    private val prefs: UserPreferencesRepository,
    private val energyTargets: EnergyTargetsManager,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    suspend fun currentWeight(): Float = weightDao.getLatestWeight()?.weightKg ?: prefs.userGoals.first().weightKg

    fun logWeight(kg: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            weightDao.upsert(WeightEntryEntity(dateMillis = System.currentTimeMillis(), weightKg = kg))
            prefs.updateWeight(kg)
            energyTargets.recalculate()
            WidgetUpdateWorker.updateAllWidgets(context)
        }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            waterIntakeDao.insert(
                WaterIntakeEntity(
                    dateMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    timestampMillis = System.currentTimeMillis(),
                    amountMl = ml,
                )
            )
        }
    }
}

/** Hoja "Registrar" del botón "+": comida, peso y agua desde cualquier pantalla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(
    onDismiss: () -> Unit,
    onNutrition: (NutritionAction) -> Unit,
    onWeight: () -> Unit,
    onWater: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Panel,
        dragHandle = { BottomSheetDefaults.DragHandle(color = LineStrong) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("REGISTRAR", color = TextMute, style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.2.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.Outlined.Search, "Buscar alimento", Modifier.weight(1f)) { onNutrition(NutritionAction.SEARCH) }
                QuickTile(Icons.Outlined.PhotoCamera, "Foto del plato", Modifier.weight(1f)) { onNutrition(NutritionAction.PHOTO) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.Outlined.QrCodeScanner, "Código de barras", Modifier.weight(1f)) { onNutrition(NutritionAction.BARCODE) }
                QuickTile(Icons.Outlined.DocumentScanner, "Leer etiqueta", Modifier.weight(1f)) { onNutrition(NutritionAction.LABEL) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.Outlined.EditNote, "Describir comida", Modifier.weight(1f)) { onNutrition(NutritionAction.TEXT) }
                QuickTile(Icons.Outlined.MonitorWeight, "Peso", Modifier.weight(1f), onWeight)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.Outlined.WaterDrop, "Agua +250 ml", Modifier.weight(1f)) { onWater(250) }
                QuickTile(Icons.Outlined.LocalDrink, "Agua +500 ml", Modifier.weight(1f)) { onWater(500) }
            }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(PanelHigh)
            .border(1.dp, Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Ion, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextHigh, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun QuickWeightDialog(initialKg: Float, onSave: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(String.format(Locale("es", "ES"), "%.1f", initialKg)) }
    val value = text.replace(',', '.').toFloatOrNull()
    val valid = value != null && value in 30f..300f
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Peso de hoy", color = TextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(6) },
                    label = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Tus calorías objetivo se recalculan con cada pesaje.", color = TextMute, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = valid) { Text("Guardar", color = Ion) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMute) } },
    )
}
