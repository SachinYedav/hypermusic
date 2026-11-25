package com.hypermusic.feature.explore

import androidx.compose.runtime.Immutable

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.BrowseShelf
import com.hypermusic.core.utils.ErrorClassifier
import com.hypermusic.core.utils.FriendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ChartCountry(val code: String, val name: String, val flag: String)

@Immutable
data class ExploreScreenState(
    val uiState: ExploreUiState = ExploreUiState.Loading,
    val selectedCountry: String = "IN",
    val currentCountry: ChartCountry = ChartCountry("IN", "India", "🇮🇳"),
    val quickCountries: List<ChartCountry> = emptyList(),
    val isCharts: Boolean = false,
    val isMoodsAndGenres: Boolean = false,
    val title: String = ""
)

@Immutable
sealed interface ExploreUiState {
    @Immutable
    object Loading : ExploreUiState
    @Immutable
    data class Success(val shelves: List<BrowseShelf>) : ExploreUiState
    @Immutable
    data class Error(val message: String, val friendlyError: FriendlyError? = null) : ExploreUiState
}

class ExploreViewModel(
    savedStateHandle: SavedStateHandle,
    private val musicService: MusicService
) : ViewModel() {

    private val categoryId: String = checkNotNull(savedStateHandle["categoryId"])
    val title: String = checkNotNull(savedStateHandle["title"])

    val isCharts: Boolean = categoryId == "charts" || categoryId == "FEmusic_charts"
    val isMoodsAndGenres: Boolean = (categoryId == "moods" ||
        categoryId == "FEmusic_moods_and_genres" ||
        categoryId == "moods_and_genres") && !categoryId.contains("|||")

    val chartCountries: List<ChartCountry> get() = Companion.chartCountries
    val quickCountryCodes: List<String> get() = Companion.quickCountryCodes
    fun getCountry(code: String): ChartCountry = Companion.getCountry(code)

    private val _selectedCountry = MutableStateFlow("IN")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val screenState: StateFlow<ExploreScreenState> = combine(
        _uiState,
        _selectedCountry
    ) { ui, country ->
        ExploreScreenState(
            uiState = ui,
            selectedCountry = country,
            currentCountry = getCountry(country),
            quickCountries = quickCountryCodes
                .filter { it != country }
                .map { getCountry(it) },
            isCharts = isCharts,
            isMoodsAndGenres = isMoodsAndGenres,
            title = title
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExploreScreenState(
            selectedCountry = "IN",
            currentCountry = getCountry("IN"),
            quickCountries = quickCountryCodes
                .filter { it != "IN" }
                .map { getCountry(it) },
            isCharts = isCharts,
            isMoodsAndGenres = isMoodsAndGenres,
            title = title
        )
    )

    init {
        loadExplorePage()
    }

    fun selectCountry(countryCode: String) {
        if (_selectedCountry.value == countryCode) return
        _selectedCountry.value = countryCode
        loadExplorePage(countryCode)
    }

    fun loadExplorePage(countryCode: String? = if (isCharts) _selectedCountry.value else null) {
        _uiState.value = ExploreUiState.Loading
        viewModelScope.launch {
            val browseId = when (categoryId) {
                "charts" -> "FEmusic_charts"
                "new" -> "FEmusic_new_releases"
                "moods" -> "FEmusic_moods_and_genres"
                "podcasts" -> "FEmusic_podcasts"
                else -> if (categoryId.startsWith("FEmusic_")) categoryId else "FEmusic_$categoryId"
            }

            val result = musicService.getExplorePage(browseId, if (isCharts) countryCode else null)
            if (result.isSuccess) {
                val shelves = result.getOrNull() ?: emptyList()
                _uiState.value = ExploreUiState.Success(shelves)
            } else {
                val exception = result.exceptionOrNull()
                val friendly = ErrorClassifier.classify(exception)
                _uiState.value = ExploreUiState.Error(friendly.title, friendly)
            }
        }
    }

    companion object {
        val quickCountryCodes: List<String> = listOf("IN", "ZZ", "US", "GB", "KR", "JP")

        val chartCountries: List<ChartCountry> = listOf(
            ChartCountry("IN", "India", "🇮🇳"),
            ChartCountry("ZZ", "Global", "🌐"),
            ChartCountry("AR", "Argentina", "🇦🇷"),
            ChartCountry("AU", "Australia", "🇦🇺"),
            ChartCountry("AT", "Austria", "🇦🇹"),
            ChartCountry("BE", "Belgium", "🇧🇪"),
            ChartCountry("BO", "Bolivia", "🇧🇴"),
            ChartCountry("BR", "Brazil", "🇧🇷"),
            ChartCountry("CA", "Canada", "🇨🇦"),
            ChartCountry("CL", "Chile", "🇨🇱"),
            ChartCountry("CO", "Colombia", "🇨🇴"),
            ChartCountry("CR", "Costa Rica", "🇨🇷"),
            ChartCountry("CZ", "Czechia", "🇨🇿"),
            ChartCountry("DK", "Denmark", "🇩🇰"),
            ChartCountry("DO", "Dominican Republic", "🇩🇴"),
            ChartCountry("EC", "Ecuador", "🇪🇨"),
            ChartCountry("EG", "Egypt", "🇪🇬"),
            ChartCountry("SV", "El Salvador", "🇸🇻"),
            ChartCountry("EE", "Estonia", "🇪🇪"),
            ChartCountry("FI", "Finland", "🇫🇮"),
            ChartCountry("FR", "France", "🇫🇷"),
            ChartCountry("DE", "Germany", "🇩🇪"),
            ChartCountry("GT", "Guatemala", "🇬🇹"),
            ChartCountry("HN", "Honduras", "🇭🇳"),
            ChartCountry("HK", "Hong Kong", "🇭🇰"),
            ChartCountry("HU", "Hungary", "🇭🇺"),
            ChartCountry("IS", "Iceland", "🇮🇸"),
            ChartCountry("ID", "Indonesia", "🇮🇩"),
            ChartCountry("IE", "Ireland", "🇮🇪"),
            ChartCountry("IL", "Israel", "🇮🇱"),
            ChartCountry("IT", "Italy", "🇮🇹"),
            ChartCountry("JP", "Japan", "🇯🇵"),
            ChartCountry("KE", "Kenya", "🇰🇪"),
            ChartCountry("LU", "Luxembourg", "🇱🇺"),
            ChartCountry("MY", "Malaysia", "🇲🇾"),
            ChartCountry("MX", "Mexico", "🇲🇽"),
            ChartCountry("NL", "Netherlands", "🇳🇱"),
            ChartCountry("NZ", "New Zealand", "🇳🇿"),
            ChartCountry("NI", "Nicaragua", "🇳🇮"),
            ChartCountry("NG", "Nigeria", "🇳🇬"),
            ChartCountry("NO", "Norway", "🇳🇴"),
            ChartCountry("PA", "Panama", "🇵🇦"),
            ChartCountry("PY", "Paraguay", "🇵🇾"),
            ChartCountry("PE", "Peru", "🇵🇪"),
            ChartCountry("PH", "Philippines", "🇵🇭"),
            ChartCountry("PL", "Poland", "🇵🇱"),
            ChartCountry("PT", "Portugal", "🇵🇹"),
            ChartCountry("RO", "Romania", "🇷🇴"),
            ChartCountry("RU", "Russia", "🇷🇺"),
            ChartCountry("SA", "Saudi Arabia", "🇸🇦"),
            ChartCountry("RS", "Serbia", "🇷🇸"),
            ChartCountry("SG", "Singapore", "🇸🇬"),
            ChartCountry("ZA", "South Africa", "🇿🇦"),
            ChartCountry("KR", "South Korea", "🇰🇷"),
            ChartCountry("ES", "Spain", "🇪🇸"),
            ChartCountry("SE", "Sweden", "🇸🇪"),
            ChartCountry("CH", "Switzerland", "🇨🇭"),
            ChartCountry("TW", "Taiwan", "🇹🇼"),
            ChartCountry("TZ", "Tanzania", "🇹🇿"),
            ChartCountry("TH", "Thailand", "🇹🇭"),
            ChartCountry("TR", "Turkey", "🇹🇷"),
            ChartCountry("UG", "Uganda", "🇺🇬"),
            ChartCountry("UA", "Ukraine", "🇺🇦"),
            ChartCountry("AE", "United Arab Emirates", "🇦🇪"),
            ChartCountry("GB", "United Kingdom", "🇬🇧"),
            ChartCountry("US", "United States", "🇺🇸"),
            ChartCountry("UY", "Uruguay", "🇺🇾"),
            ChartCountry("VN", "Vietnam", "🇻🇳"),
            ChartCountry("ZW", "Zimbabwe", "🇿🇼")
        )

        fun getCountry(code: String): ChartCountry {
            return chartCountries.find { it.code.equals(code, ignoreCase = true) }
                ?: ChartCountry(code, code, "🌐")
        }
    }
}
