package com.hypermusic.di

import androidx.room.Room
import com.hypermusic.core.audio.EqualizerManager
import com.hypermusic.core.data.local.AppDatabase
import com.hypermusic.core.data.repository.DeviceMediaRepository
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.data.repository.PlaybackStateStore
import com.hypermusic.core.datastore.EqualizerPreferencesManager
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.extractor.HyperMusicExtractorService
import com.hypermusic.core.extractor.engine.YouTubeMusicEngine
import com.hypermusic.core.extractor.network.NetworkClient
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.core.player.SleepTimerManager
import com.hypermusic.feature.widget.manager.WidgetUpdateManager
import com.hypermusic.feature.equalizer.EqualizerViewModel
import com.hypermusic.feature.explore.ExploreViewModel
import com.hypermusic.feature.home.HomeViewModel
import com.hypermusic.feature.library.LibraryViewModel
import com.hypermusic.feature.player.PlayerViewModel
import com.hypermusic.feature.search.SearchViewModel
import com.hypermusic.feature.settings.SettingsViewModel
import com.hypermusic.feature.settings.StorageManagementViewModel
import com.hypermusic.feature.shared.screens.ArtistViewModel
import com.hypermusic.feature.shared.screens.CatalogViewModel
import com.hypermusic.feature.shared.screens.CollectionViewModel
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose

val appModule = module {
    single { PreferencesManager(androidContext()) }
    single { EqualizerPreferencesManager(androidContext()) }

    single { NetworkClient }
    single { YouTubeMusicEngine }
    single<MusicService> { HyperMusicExtractorService(get()) }

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "hypermusic.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    single { get<AppDatabase>().trackDao() }
    single { get<AppDatabase>().historyDao() }
    single { get<AppDatabase>().playlistDao() }
    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().downloadDao() }
    single { get<AppDatabase>().podcastDao() }
    single { get<AppDatabase>().recentCollectionDao() }

    single { com.hypermusic.core.data.repository.DownloadAvailability(get(), get()) }
    single { com.hypermusic.core.data.repository.PlaybackHistoryWriter(get()) }
    single { com.hypermusic.core.player.PlaybackRuntimeState() }
    single { com.hypermusic.core.lyrics.LyricsDiskCache(androidContext()) }
    single { com.hypermusic.core.lyrics.LyricsTranslationService() }
    single { LocalMediaRepository(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DownloadRepository(androidContext(), get(), get(), get(), get()) }
    single { DeviceMediaRepository(androidContext()) }
    single { com.hypermusic.core.data.repository.AppUpdateRepository(androidContext()) }
    single { com.hypermusic.core.data.repository.StorageMaintenanceRepository(androidContext() as android.app.Application, get(), get(), get(), get(), get(), get()) }
    single { com.hypermusic.navigation.NavigationEventBus() }
    single { PlaybackStateStore(androidContext()) }

    single { EqualizerManager(androidContext(), get()) }
    single { SleepTimerManager() }
    single { PlayerRepository(androidContext(), get(), get(), get(), get(), get(), get()) }.onClose { it?.close() }

    single { WidgetUpdateManager(androidContext(), get(), get(), get(), get()) }

    viewModel { EqualizerViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { com.hypermusic.feature.settings.AppUpdateViewModel(get()) }
    viewModel { StorageManagementViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { ExploreViewModel(get(), get()) }
    viewModel { CollectionViewModel(get(), get(), get()) }
    viewModel { CatalogViewModel(get(), get()) }
    viewModel { ArtistViewModel(get(), get(), get()) }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get(), get(), get()) }

    single { GlobalToastViewModel() }
    single { ActionSheetViewModel(get(), get(), get(), get(), get(), get()) }
}
