package com.hypermusic

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.hypermusic.core.data.repository.DownloadRepository
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.utils.ArtworkFallbackInterceptor
import com.hypermusic.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class HyperMusicApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        val syncPrefs = getSharedPreferences(PreferencesManager.SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val cachedTheme = syncPrefs.getString(PreferencesManager.KEY_CACHED_THEME, "system") ?: "system"
        PreferencesManager.applyUiMode(this, cachedTheme)

        startKoin {
            if (BuildConfig.DEBUG) {
                androidLogger(org.koin.core.logger.Level.ERROR)
            }
            androidContext(this@HyperMusicApp)
            modules(appModule)
        }

        // Observe playback for existing widgets; worker-only starts do not create a player otherwise.
        val widgets = android.appwidget.AppWidgetManager.getInstance(this)
        if (widgets.installedProviders.any { it.provider.packageName == packageName && widgets.getAppWidgetIds(it.provider).isNotEmpty() }) {
            getKoin().get<com.hypermusic.feature.widget.manager.WidgetUpdateManager>()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadRepo: DownloadRepository = getKoin().get()
                downloadRepo.cleanOrphanTempFiles()
            } catch (_: Exception) {}
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(ArtworkFallbackInterceptor())
            }
            .crossfade(true)
            .build()
    }
}
