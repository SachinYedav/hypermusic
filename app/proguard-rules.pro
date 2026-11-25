# ProGuard / R8 configuration for HyperMusic

# Jetpack Compose, Kotlin & AndroidX Core
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# Kotlin Coroutines: Prevent ServiceLoader stripping of MainDispatcherFactory
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Room Database & Local Persistence
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.EntityDeletionOrUpdateAdapter { *; }
-keep class * extends androidx.room.EntityInsertionAdapter { *; }
-keep class com.hypermusic.core.data.local.** { *; }
-dontwarn androidx.room.paging.**

# Media3 Audio Engine & Background Service
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.hypermusic.core.player.** { *; }
-keep class com.hypermusic.core.audio.** { *; }

# YouTube Extractor, Mozilla Rhino & Jsoup
# Protect NewPipe Extractor and internal parsers
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Keep Mozilla Rhino JavaScript engine intact for YouTube sig cipher & n-param decryption
-keep class org.mozilla.javascript.** { *; }
-keep interface org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**

# Keep Jsoup for web scraping and HTML parsing
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Protect internal InnerTube Extractor engine and models
-keep class com.hypermusic.core.extractor.** { *; }

# OkHttp & Okio
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Core Data Models, Repositories & AppWidgets
-keep class com.hypermusic.core.model.** { *; }
-keep class com.hypermusic.core.data.repository.** { *; }
-keep class com.hypermusic.core.lyrics.** { *; }
-keep class com.hypermusic.feature.widget.data.** { *; }
-keep class com.hypermusic.feature.widget.manager.** { *; }
-keep class com.hypermusic.feature.widget.receiver.** { *; }
-keep class com.hypermusic.navigation.** { *; }

# Dependency Injection (Koin)
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# WorkManager & Background Sync
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class com.hypermusic.core.worker.** { *; }

# Coil Image Loading & Open Source Licenses
-keep class coil.** { *; }
-dontwarn coil.**
-keep class com.mikepenz.aboutlibraries.** { *; }
-dontwarn com.mikepenz.aboutlibraries.**

# Release Performance Optimizations (Strip Debug Logs)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
