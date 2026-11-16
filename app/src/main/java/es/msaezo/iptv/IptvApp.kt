package es.msaezo.iptv

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import es.msaezo.iptv.data.EpgRepository
import es.msaezo.iptv.data.MetaRepository
import es.msaezo.iptv.data.NetInfo
import es.msaezo.iptv.data.SportsRepository
import es.msaezo.iptv.data.PlaylistRepository
import es.msaezo.iptv.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class IptvApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var store: UserStore
        private set
    lateinit var playlists: PlaylistRepository
        private set
    lateinit var meta: MetaRepository
        private set
    lateinit var net: NetInfo
        private set
    lateinit var epg: EpgRepository
        private set
    lateinit var sports: SportsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Keep the last crash on disk so it can be read back in Settings and reported.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date())
                crashFile.writeText(stamp + "\n" + android.util.Log.getStackTraceString(error).take(6000))
            }
            previous?.uncaughtException(thread, error)
        }
        // Pictures are the biggest use of memory: keep fewer of them, in a lighter format.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .allowRgb565(true)
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
                .build()
        )
        store = UserStore(this)
        es.msaezo.iptv.ui.I18n.lang = store.language.value
        playlists = PlaylistRepository(this, store, scope)
        meta = MetaRepository(this, store, playlists, scope)
        net = NetInfo(scope).also { it.refresh() }
        epg = EpgRepository(playlists, store, scope)
        sports = SportsRepository(store, scope)
    }

    val crashFile: java.io.File get() = java.io.File(filesDir, "last_crash.txt")

    companion object {
        lateinit var instance: IptvApp
            private set
    }
}
