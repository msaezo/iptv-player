package es.msaezo.iptv

import kotlinx.coroutines.launch

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import es.msaezo.iptv.data.EpgRepository
import es.msaezo.iptv.data.MetaRepository
import es.msaezo.iptv.data.NetInfo
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
    lateinit var sync: es.msaezo.iptv.data.ProgressSync
        private set
    lateinit var net: NetInfo
        private set
    lateinit var epg: EpgRepository
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
        // A session that was in front when the process vanished (killed for memory, say) leaves no
        // error to catch, so note what it was doing and compare on the next start.
        runCatching {
            val last = sessionFile.takeIf { it.exists() }?.readText().orEmpty()
            if (last.startsWith("running")) {
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date())
                if (!crashFile.exists()) crashFile.writeText("$stamp\nClosed without an error (likely out of memory or killed).\nLast seen: ${last.removePrefix("running ")}")
            }
        }
        var started = 0
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: android.app.Activity) {
                // Coming to the front: pick up what was watched on another device.
                if (started++ == 0) { mark(lastNote); scope.launch { sync.sync() } }
            }
            override fun onActivityStopped(a: android.app.Activity) {
                if (--started == 0) { runCatching { sessionFile.writeText("background") }; scope.launch { sync.sync() } }
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
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
        sync = es.msaezo.iptv.data.ProgressSync(store, playlists)
        net = NetInfo(scope).also { it.refresh() }
        epg = EpgRepository(playlists, store, scope)
    }

    private val sessionFile: java.io.File get() = java.io.File(filesDir, "session.txt")
    private var lastNote = "start"

    /** Records where the app is, with memory use, so an unexplained close can be traced. */
    fun mark(note: String) {
        lastNote = note
        runCatching {
            val rt = Runtime.getRuntime()
            val used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
            sessionFile.writeText("running $note | memory ${used} of ${rt.maxMemory() / 1_048_576} MB")
        }
    }

    val crashFile: java.io.File get() = java.io.File(filesDir, "last_crash.txt")

    companion object {
        lateinit var instance: IptvApp
            private set
    }
}
