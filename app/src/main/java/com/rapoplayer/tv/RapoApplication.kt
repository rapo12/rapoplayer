package com.rapoplayer.tv

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.rapoplayer.tv.data.*

class RapoApplication : Application(), ImageLoaderFactory {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Library exception traces can contain signed stream URLs. Support diagnostics use
        // only our explicit failure enums and never player/network exception messages.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
    }
    val network by lazy { Network() }
    val repository by lazy { Repository(Room.databaseBuilder(this, RapoDatabase::class.java, "rapoplayer.db")
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.query("PRAGMA secure_delete=ON").use { it.moveToFirst() }
            }
        }).build(), Crypto(), network) }
    val preferences by lazy { PreferenceStore(this) }
    override fun newImageLoader() = ImageLoader.Builder(this).okHttpClient(network.logos)
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(12 * 1024 * 1024).build() }
        .diskCache(null).crossfade(false).respectCacheHeaders(true).build()
}
