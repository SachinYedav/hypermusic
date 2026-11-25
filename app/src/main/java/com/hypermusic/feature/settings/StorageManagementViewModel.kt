package com.hypermusic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.data.repository.StorageMaintenanceRepository
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorageState(
    val cacheSizeRaw: Long = 0L,
    val downloadsSizeRaw: Long = 0L,
    val databaseSizeRaw: Long = 0L,
    val appTotalStorageRaw: Long = 1L,
    val totalDeviceStorageRaw: Long = 1L, // Prevent division by zero
    val freeDeviceStorageRaw: Long = 0L,
    
    val cacheSize: String = "Calculating...",
    val downloadsSize: String = "Calculating...",
    val databaseSize: String = "Calculating...",
    val appTotalStorage: String = "Calculating...",
    val totalDeviceStorage: String = "Calculating...",
    val freeDeviceStorage: String = "Calculating...",
    val historyCount: String = "Calculating..."
)

class StorageManagementViewModel(
    private val storageMaintenanceRepository: StorageMaintenanceRepository,
    private val toastViewModel: GlobalToastViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageState())
    val uiState: StateFlow<StorageState> = _uiState.asStateFlow()

    init {
        recalculateStorage()
    }

    fun recalculateStorage() {
        viewModelScope.launch {
            val sizes = storageMaintenanceRepository.getStorageSizes()
            val appTotal = sizes.cacheSize + sizes.downloadsSize + sizes.databaseSize

            _uiState.value = StorageState(
                cacheSizeRaw = sizes.cacheSize,
                downloadsSizeRaw = sizes.downloadsSize,
                databaseSizeRaw = sizes.databaseSize,
                appTotalStorageRaw = if (appTotal > 0) appTotal else 1L,
                totalDeviceStorageRaw = sizes.totalDeviceStorage,
                freeDeviceStorageRaw = sizes.freeDeviceStorage,
                cacheSize = formatSize(sizes.cacheSize),
                downloadsSize = formatSize(sizes.downloadsSize),
                databaseSize = formatSize(sizes.databaseSize),
                appTotalStorage = formatSize(appTotal),
                totalDeviceStorage = formatSize(sizes.totalDeviceStorage),
                freeDeviceStorage = formatSize(sizes.freeDeviceStorage),
                historyCount = "${sizes.historyCount} items"
            )
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.ROOT, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                storageMaintenanceRepository.clearCache()
                toastViewModel.showToast("Cache cleared successfully", ToastType.SUCCESS)
                recalculateStorage()
            } catch (e: Exception) {
                toastViewModel.showToast("Failed to clear cache", ToastType.ERROR)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                storageMaintenanceRepository.clearHistory()
                toastViewModel.showToast("Playback history cleared", ToastType.SUCCESS)
                recalculateStorage()
            } catch (e: Exception) {
                toastViewModel.showToast("Failed to clear history", ToastType.ERROR)
            }
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            try {
                storageMaintenanceRepository.clearDownloads()
                toastViewModel.showToast("All downloads removed", ToastType.SUCCESS)
                recalculateStorage()
            } catch (e: Exception) {
                toastViewModel.showToast("Failed to remove downloads", ToastType.ERROR)
            }
        }
    }
}
