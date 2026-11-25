package com.lifedashboard.presentation.screen.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.lifedashboard.domain.energy.ActivityLevel
import com.lifedashboard.domain.energy.Sex
import com.lifedashboard.domain.energy.WeightGoal
import com.lifedashboard.presentation.theme.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Bienvenida de Halo: pide los datos necesarios para calcular el gasto diario
 * y el objetivo de calorías. Se muestra una vez (y se puede repetir desde Ajustes).
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    BackHandler(enabled = state.step > 0) { viewModel.back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Barra superior: atrás + progreso
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            if (state.step > 0) {
                IconButton(onClick = viewModel::back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = TextMute)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.width(8.dp))
            val progress by animateFloatAsState(state.step / OnboardingState.LAST_STEP.toFloat(), label = "progress")
            Box(
                Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Line)
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Ion))
            }
            Spacer(Modifier.width(56.dp))
        }

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 * dir } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { step ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (step) {
                    0 -> StepWelcome(state, viewModel)
                    1 -> StepBody(state, viewModel)
                    2 -> StepMeasures(state, viewModel)
                    3 -> StepActivity(state, viewModel)
                    4 -> StepGoal(state, viewModel)
                    else -> StepResult(state)
                }
            }
        }

        val last = state.step == OnboardingState.LAST_STEP
        Button(
            onClick = { if (last) viewModel.finish() else viewModel.next() },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Ion, contentColor = Obsidian),
        ) {
            Text(
                text = when {
                    state.step == 0 -> "Empezar"
                    last -> if (state.saving) "Guardando…" else "Entrar en Halo"
                    else -> "Continuar"
                },
                style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, letterSpacing = 0.5.sp),
            )
        }
    }
}

// ─── Pasos ────────────────────────────────────────────────────────────────────

@Composable
private fun StepWelcome(state: OnboardingState, vm: OnboardingViewModel) {
    HaloMark()
    Eyebrow("Bienvenida")
    Title("Vamos a calcular cuánto necesitas comer")
    Body("Con tu sexo, edad, altura, peso y actividad Halo calcula tu gasto diario y tu objetivo de calorías. Se ajusta solo cuando te pesas.")
    OutlinedTextField(
        value = state.name,
        onValueChange = vm::setName,
        label = { Text("Cómo te llamas") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = haloFieldColors(),
    )
}

@Composable
private fun StepBody(state: OnboardingState, vm: OnboardingViewModel) {
    Eyebrow("1 de 4 · Tú")
    Title("Sexo y edad")
    Body("La fórmula de metabolismo basal es distinta para hombres y mujeres, y baja con la edad.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard("Hombre", null, state.sex == Sex.HOMBRE, Modifier.weight(1f)) { vm.setSex(Sex.HOMBRE) }
        ChoiceCard("Mujer", null, state.sex == Sex.MUJER, Modifier.weight(1f)) { vm.setSex(Sex.MUJER) }
    }
    Stepper("Edad", "${state.ageYears}", "años", onMinus = { vm.setAge(state.ageYears - 1) }, onPlus = { vm.setAge(state.ageYears + 1) })
}

@Composable
private fun StepMeasures(state: OnboardingState, vm: OnboardingViewModel) {
    Eyebrow("2 de 4 · Medidas")
    Title("Altura y peso")
    Body("Usa tu peso de esta mañana, en ayunas si puedes.")
    Stepper("Altura", "${state.heightCm}", "cm", onMinus = { vm.setHeight(state.heightCm - 1) }, onPlus = { vm.setHeight(state.heightCm + 1) })
    Stepper(
        "Peso", String.format(Locale("es", "ES"), "%.1f", state.weightKg), "kg",
        onMinus = { vm.setWeight(state.weightKg - 0.1f) }, onPlus = { vm.setWeight(state.weightKg + 0.1f) },
    )
}

@Composable
private fun StepActivity(state: OnboardingState, vm: OnboardingViewModel) {
    Eyebrow("3 de 4 · Actividad")
    Title("¿Cuánto te mueves?")
    Body("Sin contar el entreno exacto: Halo suma lo que registre Health Connect.")
    ActivityLevel.entries.forEach { level ->
        ChoiceCard(level.label, level.description, state.activity == level, Modifier.fillMaxWidth()) { vm.setActivity(level) }
    }
}

@Composable
private fun StepGoal(state: OnboardingState, vm: OnboardingViewModel) {
    Eyebrow("4 de 4 · Objetivo")
    Title("¿Qué quieres conseguir?")
    Body("Perder 0,5 kg por semana es un ritmo sostenible para la mayoría de personas.")
    WeightGoal.entries.forEach { goal ->
        val delta = (goal.weeklyKg * 7700f / 7f).roundToInt()
        val sub = when {
            delta < 0 -> "$delta kcal al día"
            delta > 0 -> "+$delta kcal al día"
            else -> "Comer lo que gastas"
        }
        ChoiceCard(goal.label, sub, state.goal == goal, Modifier.fillMaxWidth()) { vm.setGoal(goal) }
    }
}

@Composable
private fun StepResult(state: OnboardingState) {
    val t = state.preview
    Eyebrow("Tu plan")
    Title("Tu objetivo diario")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "%,d".format(Locale("es", "ES"), t.caloriesKcal.roundToInt()),
            style = MaterialTheme.typography.displayMedium,
            color = Ion,
        )
        Spacer(Modifier.width(8.dp))
        Text("kcal", color = TextMute, style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp), modifier = Modifier.padding(bottom = 10.dp))
    }
    Column {
        ResultRow("Metabolismo basal", "${t.bmrKcal.roundToInt()} kcal")
        ResultRow("Gasto diario estimado", "${t.tdeeKcal.roundToInt()} kcal")
        ResultRow("Proteína", "${t.proteinG.roundToInt()} g")
        ResultRow("Carbohidratos", "${t.carbsG.roundToInt()} g")
        ResultRow("Grasa", "${t.fatG.roundToInt()} g")
    }
    Body("Fórmula Mifflin-St Jeor × factor de actividad. Tras 2–4 semanas registrando comida y peso, Halo sustituye la estimación por tu gasto real.")
}

// ─── Piezas ───────────────────────────────────────────────────────────────────

@Composable
private fun HaloMark() {
    Canvas(Modifier.size(64.dp)) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(Ion, -90f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(TextHigh, radius = stroke * 0.6f, center = Offset(size.width / 2, inset))
    }
}

@Composable
private fun Eyebrow(text: String) = Text(
    text.uppercase(),
    color = Ion,
    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.5.sp),
)

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, color = TextHigh)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMute)

@Composable
private fun ChoiceCard(title: String, subtitle: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) PanelHigh else Panel)
            .border(1.dp, if (selected) Ion else Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = if (selected) Ion else TextHigh, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, color = TextMute, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Stepper(label: String, value: String, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(), color = TextMute,
            style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.sp),
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        RepeatIconButton(onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Menos $label", tint = TextHigh) }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.widthIn(min = 96.dp), horizontalArrangement = Arrangement.Center) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = TextHigh)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = TextMute, style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp), modifier = Modifier.padding(bottom = 4.dp))
        }
        RepeatIconButton(onPlus) { Icon(Icons.Filled.Add, contentDescription = "Más $label", tint = Ion) }
    }
}

/** Botón que repite la acción mientras se mantiene pulsado. */
@Composable
private fun RepeatIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(400)
            while (true) {
                currentOnClick()
                kotlinx.coroutines.delay(60)
            }
        }
    }
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(PanelHigh),
    ) { content() }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextMute, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = TextHigh, style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp))
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun haloFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ion,
    unfocusedBorderColor = LineStrong,
    focusedLabelColor = Ion,
    unfocusedLabelColor = TextMute,
    cursorColor = Ion,
    focusedTextColor = TextHigh,
    unfocusedTextColor = TextHigh,
)
