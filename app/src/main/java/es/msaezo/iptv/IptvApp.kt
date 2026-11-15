package es.msaezo.iptv

import android.app.Application
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
    lateinit var net: NetInfo
        private set
    lateinit var epg: EpgRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = UserStore(this)
        playlists = PlaylistRepository(this, store, scope)
        meta = MetaRepository(this, store, playlists, scope)
        net = NetInfo(scope).also { it.refresh() }
        epg = EpgRepository(playlists, store, scope)
    }

    companion object {
        lateinit var instance: IptvApp
            private set
    }
}
