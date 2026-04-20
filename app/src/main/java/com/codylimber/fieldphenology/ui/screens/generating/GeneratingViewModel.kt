package com.codylimber.fieldphenology.ui.screens.generating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codylimber.fieldphenology.data.generator.DatasetGenerator
import com.codylimber.fieldphenology.data.generator.GenerationPhase
import com.codylimber.fieldphenology.data.generator.GenerationProgress
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import kotlinx.coroutines.Job
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
    private val generator: DatasetGenerator,
    private val repository: PhenologyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratingState())
    val state: StateFlow<GeneratingState> = _state

    private var generationJob: Job? = null

    fun startGeneration(params: GenerationParams) {
        if (generationJob?.isActive == true) return

        generationJob = viewModelScope.launch {
            try {
                generator.generate(
                    placeIds = params.placeIds,
                    placeName = params.placeName,
                    taxonIds = params.taxonIds,
                    taxonName = params.taxonName,
                    groupName = params.groupName,
                    minObs = params.minObs,
                    onProgress = { progress ->
                        val completed = _state.value.completedPhases.toMutableSet()
                        for (phase in GenerationPhase.entries) {
                            if (phase.ordinal < progress.phase.ordinal) {
                                completed.add(phase)
                            }
                        }
                        _state.value = _state.value.copy(
                            progress = progress,
                            completedPhases = completed
                        )
                    }
                )

                repository.reloadDatasets()

                _state.value = _state.value.copy(
                    isComplete = true,
                    completedPhases = GenerationPhase.entries.toSet()
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User cancelled
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Unknown error")
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
    }
}
