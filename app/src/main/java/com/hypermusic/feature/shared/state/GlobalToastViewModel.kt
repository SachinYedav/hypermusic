package com.hypermusic.feature.shared.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ToastType {
    INFO, SUCCESS, ERROR, LIKED
}

data class ToastAction(
    val label: String,
    val onClick: () -> Unit
)

enum class ToastLayoutMode {
    PILL,
    CARD
}

data class ToastState(
    val isVisible: Boolean = false,
    val layoutMode: ToastLayoutMode = ToastLayoutMode.PILL,
    val title: String? = null,
    val message: String = "",
    val type: ToastType = ToastType.INFO,
    val action: ToastAction? = null,
    val secondaryAction: ToastAction? = null
)

class GlobalToastViewModel : ViewModel() {
    private val _state = MutableStateFlow(ToastState())
    val state = _state.asStateFlow()

    private var hideJob: Job? = null

    /**
     * Standard transient notification pill (auto-dismisses after [durationMs]).
     */
    fun showToast(
        message: String,
        type: ToastType = ToastType.INFO,
        action: ToastAction? = null,
        durationMs: Long = 3000
    ) {
        hideJob?.cancel()
        _state.value = ToastState(
            isVisible = true,
            layoutMode = ToastLayoutMode.PILL,
            message = message,
            type = type,
            action = action
        )

        hideJob = viewModelScope.launch {
            delay(durationMs)
            hideToast()
        }
    }

    /**
     * Rich actionable notification card with title, description, and primary/secondary actions.
     * If [durationMs] is null, the card remains until explicitly dismissed or acted upon.
     */
    fun showAlertCard(
        title: String,
        message: String,
        type: ToastType = ToastType.INFO,
        primaryAction: ToastAction,
        secondaryAction: ToastAction? = null,
        durationMs: Long? = null
    ) {
        hideJob?.cancel()
        _state.value = ToastState(
            isVisible = true,
            layoutMode = ToastLayoutMode.CARD,
            title = title,
            message = message,
            type = type,
            action = primaryAction,
            secondaryAction = secondaryAction
        )

        if (durationMs != null && durationMs > 0L) {
            hideJob = viewModelScope.launch {
                delay(durationMs)
                hideToast()
            }
        }
    }

    fun hideToast() {
        hideJob?.cancel()
        hideJob = null
        _state.value = _state.value.copy(isVisible = false)
    }
}
