package com.lifedashboard.presentation.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifedashboard.core.worker.WidgetUpdateWorker
import com.lifedashboard.data.local.dao.WeightDao
import com.lifedashboard.data.local.entity.WeightEntryEntity
import com.lifedashboard.data.repository.EnergyTargetsManager
import com.lifedashboard.data.repository.UserPreferencesRepository
import com.lifedashboard.domain.energy.ActivityLevel
import com.lifedashboard.domain.energy.EnergyCalculator
import com.lifedashboard.domain.energy.EnergyProfile
import com.lifedashboard.domain.energy.EnergyTargets
import com.lifedashboard.domain.energy.Sex
import com.lifedashboard.domain.energy.WeightGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val sex: Sex = Sex.HOMBRE,
    val ageYears: Int = 30,
    val heightCm: Int = 175,
    val weightKg: Float = 75f,
    val activity: ActivityLevel = ActivityLevel.MODERADO,
    val goal: WeightGoal = WeightGoal.MANTENER,
    val saving: Boolean = false,
) {
    val profile get() = EnergyProfile(sex, ageYears, heightCm, weightKg, activity, goal)
    val preview: EnergyTargets get() = EnergyCalculator.targets(profile)

    companion object { const val LAST_STEP = 5 }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val energyTargets: EnergyTargetsManager,
    private val weightDao: WeightDao,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        // Rellenar con lo que ya hubiera guardado (usuarios que vienen de la versión anterior)
        viewModelScope.launch {
            val g = prefs.userGoals.first()
            _state.update {
                it.copy(
                    name = g.userName,
                    sex = Sex.from(g.sex),
                    ageYears = g.ageYears,
                    heightCm = g.heightCm,
                    weightKg = g.weightKg,
                    activity = ActivityLevel.from(g.activityLevel),
                    goal = WeightGoal.from(g.weightGoal),
                )
            }
        }
    }

    fun next() = _state.update { it.copy(step = (it.step + 1).coerceAtMost(OnboardingState.LAST_STEP)) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun setName(v: String) = _state.update { it.copy(name = v.take(30)) }
    fun setSex(v: Sex) = _state.update { it.copy(sex = v) }
    fun setAge(v: Int) = _state.update { it.copy(ageYears = v.coerceIn(14, 100)) }
    fun setHeight(v: Int) = _state.update { it.copy(heightCm = v.coerceIn(120, 230)) }
    fun setWeight(v: Float) = _state.update { it.copy(weightKg = (Math.round(v * 10f) / 10f).coerceIn(30f, 300f)) }
    fun setActivity(v: ActivityLevel) = _state.update { it.copy(activity = v) }
    fun setGoal(v: WeightGoal) = _state.update { it.copy(goal = v) }

    fun finish() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val previousWeight = prefs.userGoals.first().weightKg
            prefs.updateEnergyProfile(
                name = s.name,
                sex = s.sex.name,
                ageYears = s.ageYears,
                heightCm = s.heightCm,
                weightKg = s.weightKg,
                activityLevel = s.activity.name,
                weightGoal = s.goal.name,
            )
            // Guardar el peso inicial en el historial si es nuevo o ha cambiado
            val latest = weightDao.getLatestWeight()
            if (latest == null || latest.weightKg != s.weightKg || previousWeight != s.weightKg) {
                weightDao.upsert(WeightEntryEntity(dateMillis = System.currentTimeMillis(), weightKg = s.weightKg))
            }
            energyTargets.recalculate()
            prefs.setOnboardingCompleted(true)
            WidgetUpdateWorker.updateAllWidgets(context)
        }
    }
}
