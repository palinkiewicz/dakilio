package pl.dakil.dakilio.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.dakil.dakilio.core.model.HabitBump

/**
 * Habits that just gained a day, waiting for the app's progress popup. The entry editor posts them
 * on save and closes; the popup shows them over whatever screen comes next, then [consume]s them.
 * A state (not an event stream), so nothing is lost while the editor's exit transition runs.
 */
@Singleton
class HabitProgressEvents @Inject constructor() {
    private val _bumps = MutableStateFlow<List<HabitBump>>(emptyList())
    val bumps: StateFlow<List<HabitBump>> = _bumps.asStateFlow()

    fun post(bumps: List<HabitBump>) {
        if (bumps.isNotEmpty()) _bumps.value = bumps
    }

    fun consume() {
        _bumps.value = emptyList()
    }
}
