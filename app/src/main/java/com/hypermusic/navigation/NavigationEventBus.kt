package com.hypermusic.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationEventBus {
    private val _navTargetFlow = MutableStateFlow<String?>(null)
    val navTargetFlow: StateFlow<String?> = _navTargetFlow.asStateFlow()

    private val _autoFocusSearchTrigger = MutableStateFlow(0L)
    val autoFocusSearchTrigger: StateFlow<Long> = _autoFocusSearchTrigger.asStateFlow()

    private val _libraryFilterTrigger = MutableStateFlow<String?>(null)
    val libraryFilterTrigger: StateFlow<String?> = _libraryFilterTrigger.asStateFlow()

    fun navigateTo(target: String) {
        _navTargetFlow.value = target
    }

    fun consumeNavTarget() {
        _navTargetFlow.value = null
    }

    fun triggerAutoFocusSearch() {
        _autoFocusSearchTrigger.value = System.currentTimeMillis()
    }

    fun consumeAutoFocusSearch() {
        _autoFocusSearchTrigger.value = 0L
    }

    fun setLibraryFilter(filter: String) {
        _libraryFilterTrigger.value = filter
    }

    fun consumeLibraryFilter() {
        _libraryFilterTrigger.value = null
    }
}
