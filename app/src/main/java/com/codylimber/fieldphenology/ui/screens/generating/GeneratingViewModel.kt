package com.codylimber.fieldphenology.ui.screens.generating

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codylimber.fieldphenology.data.generator.GenerationPhase
import com.codylimber.fieldphenology.data.generator.GenerationProgress
import com.codylimber.fieldphenology.data.generator.GenerationService
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GeneratingState(
    val progress: GenerationProgress? = null,
    val completedPhases: Set<GenerationPhase> = emptySet(),
    val isComplete: Boolean = false,
    val error: String? = null
)

class GeneratingViewModel(
    private val repository: PhenologyRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratingState())
    val state: StateFlow<GeneratingState> = _state

    fun startGeneration(params: GenerationParams) {
        // Start the foreground service
        GenerationService.start(context)

        // Observe the service's state
        viewModelScope.launch {
            GenerationService.progress.collect { progress ->
                if (progress != null) {
                    val completed = _state.value.completedPhases.toMutableSet()
                    for (phase in GenerationPhase.entries) {
                        if (phase.ordinal < progress.phase.ordinal) completed.add(phase)
                    }
                    _state.value = _state.value.copy(progress = progress, completedPhases = completed)
                }
            }
        }

        viewModelScope.launch {
            GenerationService.isComplete.collect { complete ->
                if (complete) {
                    repository.reloadDatasets()
                    _state.value = _state.value.copy(
                        isComplete = true,
                        completedPhases = GenerationPhase.entries.toSet()
                    )
                }
            }
        }

        viewModelScope.launch {
            GenerationService.error.collect { error ->
                if (error != null) {
                    _state.value = _state.value.copy(error = error)
                }
            }
        }
    }

    fun cancel() {
        GenerationService.stop(context)
    }
}
