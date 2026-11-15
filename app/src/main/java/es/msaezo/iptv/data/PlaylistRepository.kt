package es.msaezo.iptv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

/** Downloads, caches and parses the playlist, and exposes the filtered [LibraryView]. */
class PlaylistRepository(
    private val context: Context,
    private val store: UserStore,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object NoPlaylist : State
        data class Loading(val message: String) : State
        data class Error(val message: String) : State
        data class Ready(val library: Library) : State
    }

    private val _state = MutableStateFlow<State>(State.NoPlaylist)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _view = MutableStateFlow<LibraryView?>(null)
    val view: StateFlow<LibraryView?> = _view.asStateFlow()

    /** Progress text of a refresh running behind an already usable catalogue. */
    private val _refreshing = MutableStateFlow<String?>(null)
    val refreshing: StateFlow<String?> = _refreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    /** When the catalogue on screen was last fetched from the provider (0 = unknown). */
    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    private val cacheFile = File(context.filesDir, "playlist.m3u")
    private val snapshotFile = File(context.filesDir, "catalogue.bin")
    private var job: Job? = null
    private val xtream = XtreamClient(File(context.filesDir, "xtream")) { store.userAgent.value }
    @Volatile private var account: XtreamAccount? = null
    @Volatile private var current: Snapshot? = null
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, Show>()

    /** One low-priority thread, so organising the catalogue never fights with the UI. */
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "catalogue").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
    }.asCoroutineDispatcher()

    init {
        scope.launch { store.languages.drop(1).collect { refilter() } }
        if (store.playlistUrl.value.isNotBlank()) load(forceDownload = false)
    }

    /**
     * Opens the saved catalogue, or fetches it when there is none or [forceDownload] is set.
     * When a catalogue is already on screen the refresh runs behind it and is applied on top.
     */
    fun load(forceDownload: Boolean) {
        val url = store.playlistUrl.value
        if (url.isBlank()) {
            _state.value = State.NoPlaylist
            _view.value = null
            return
        }
        job?.cancel()
        _refreshError.value = null
        job = scope.launch(worker) {
            var background = _state.value is State.Ready && current?.source == url
            try {
                if (!forceDownload) {
                    _state.value = State.Loading("Opening your catalogue…")
                    val saved = Snapshot.read(snapshotFile, url)
                    if (saved != null) {
                        publish(saved)
                        val stale = System.currentTimeMillis() - saved.savedAt > AUTO_REFRESH_MS
                        if (!stale || !url.startsWith("http")) return@launch
                        background = true
                    }
                }
                val fresh = refresh(url, background)
                publish(fresh)
                _refreshing.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = if (e is OutOfMemoryError) "Not enough memory on this device for the whole catalogue"
                else e.message ?: e.javaClass.simpleName
                _refreshing.value = null
                if (background) _refreshError.value = msg else _state.value = State.Error(msg)
            }
        }
    }

    private fun publish(snap: Snapshot) {
        val library = Library(snap.entries, snap.epgUrl, snap.series)
        val view = library.view(LibraryFilter(store.languages.value))
        current = snap
        account = XtreamAccount.fromUrl(snap.source)
        episodeCache.clear()
        _view.value = view
        _state.value = State.Ready(library)
        _lastUpdated.value = snap.savedAt
    }

    /** Fetches the catalogue from the provider, reusing what is unchanged, and saves it. */
    private fun refresh(url: String, background: Boolean): Snapshot {
        val report: (String) -> Unit = { msg ->
            if (background) _refreshing.value = msg else _state.value = State.Loading(msg)
        }
        val old = current?.takeIf { it.source == url } ?: Snapshot.read(snapshotFile, url)
        val previous: Map<String, Entry>? = old?.entries?.let { list ->
            HashMap<String, Entry>(list.size * 2).apply { for (e in list) put(e.url, e) }
        }
        val previousSeries = old?.series?.associateBy { it.seriesId }

        val xt = XtreamAccount.fromUrl(url)
        var xtError: Exception? = null
        var snap: Snapshot? = null
        if (xt != null) {
            try {
                val data = xtream.load(xt, previous, previousSeries, report)
                if (data.entries.isNotEmpty() || data.series.isNotEmpty()) {
                    snap = Snapshot(url, System.currentTimeMillis(), data.epgUrl, data.entries, data.series)
                } else {
                    xtError = IOException("The provider returned no channels, movies or series")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                xtError = e
            }
        }
        if (snap == null) {
            // Plain M3U playlist (or fallback when the Xtream API isn't available).
            try {
                report("Updating catalogue…")
                fetch(url, cacheFile, report)
                report("Reading playlist…")
                val result = cacheFile.bufferedReader(Charsets.UTF_8).use { M3uParser.parse(it, previous) }
                cacheFile.delete()
                if (result.entries.isEmpty()) throw IOException("The playlist is empty or not a valid M3U file")
                snap = Snapshot(url, System.currentTimeMillis(), result.epgUrl, result.entries, emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw xtError ?: e
            }
        }
        report("Organising ${snap.entries.size + snap.series.size} items…")
        Snapshot.write(snapshotFile, snap)
        return snap
    }

    // --- Xtream series episodes (loaded on demand) ---------------------------------------


    /** Returns [show] with its episodes, fetching them from the Xtream panel if needed. */
    suspend fun loadEpisodes(show: Show): Show = withContext(Dispatchers.IO) {
        if (!show.needsLoading) return@withContext show
        val key = show.id + "|" + show.sources.joinToString(",") { it.seriesId }
        episodeCache[key]?.let { return@withContext it }
        val acc = account ?: throw IOException("Series episodes need the Xtream login")
        val entries = show.sources.flatMap { src ->
            try {
                xtream.loadEpisodes(acc, src, show.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        if (entries.isEmpty()) throw IOException("No episodes found for this series")
        val merged = Library.mergeShows(entries).firstOrNull()
            ?: throw IOException("No episodes found for this series")
        val result = show.copy(episodes = merged.episodes)
        episodeCache[key] = result
        result
    }

    fun setPlaylist(url: String) {
        store.setPlaylistUrl(url)
        cacheFile.delete()
        snapshotFile.delete()
        current = null
        _lastUpdated.value = 0L
        load(forceDownload = true)
    }

    private suspend fun refilter() {
        val lib = (_state.value as? State.Ready)?.library ?: return
        _view.value = withContext(worker) { lib.view(LibraryFilter(store.languages.value)) }
    }

    private fun fetch(url: String, dest: File, report: (String) -> Unit) {
        if (url.startsWith("content://") || url.startsWith("file://")) {
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            context.contentResolver.openInputStream(Uri.parse(url)).use { input ->
                requireNotNull(input) { "Cannot open file" }
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true); tmp.delete()
            }
        } else {
            Http.download(url, dest, store.userAgent.value) { mb ->
                report("Updating catalogue… $mb MB")
            }
        }
    }

    companion object {
        /** A saved catalogue older than this is refreshed quietly when the app opens. */
        private const val AUTO_REFRESH_MS = 24 * 3_600_000L
    }
}
