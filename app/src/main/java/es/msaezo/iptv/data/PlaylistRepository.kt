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

    private val cacheFile = File(context.filesDir, "playlist.m3u")
    private var job: Job? = null
    private val xtream = XtreamClient(File(context.filesDir, "xtream")) { store.userAgent.value }
    @Volatile private var account: XtreamAccount? = null
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, Show>()

    init {
        scope.launch { store.languages.drop(1).collect { refilter() } }
        if (store.playlistUrl.value.isNotBlank()) load(forceDownload = false)
    }

    /** Loads the playlist (from the local cache unless [forceDownload]). */
    fun load(forceDownload: Boolean) {
        val url = store.playlistUrl.value
        if (url.isBlank()) {
            _state.value = State.NoPlaylist
            _view.value = null
            return
        }
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            try {
                val library = loadLibrary(url, forceDownload)
                _view.value = library.view(LibraryFilter(store.languages.value))
                _state.value = State.Ready(library)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun loadLibrary(url: String, force: Boolean): Library {
        val xt = XtreamAccount.fromUrl(url)
        var xtError: Exception? = null
        if (xt != null) {
            try {
                val data = xtream.load(xt, force) { _state.value = State.Loading(it) }
                if (data.entries.isNotEmpty() || data.series.isNotEmpty()) {
                    _state.value = State.Loading("Organising ${data.entries.size + data.series.size} items…")
                    account = xt
                    return Library(data.entries, data.epgUrl, data.series)
                }
                xtError = IOException("The provider returned no channels, movies or series")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                xtError = e
            }
        }
        // Plain M3U playlist (or fallback when the Xtream API isn't available).
        try {
            if (force || !cacheFile.exists() || cacheFile.length() == 0L) {
                _state.value = State.Loading("Updating catalogue…")
                fetch(url, cacheFile)
            }
            _state.value = State.Loading("Reading playlist…")
            val result = cacheFile.bufferedReader(Charsets.UTF_8).use { M3uParser.parse(it) }
            if (result.entries.isEmpty()) throw IOException("The playlist is empty or not a valid M3U file")
            _state.value = State.Loading("Organising ${result.entries.size} items…")
            account = null
            return Library(result.entries, result.epgUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw xtError ?: e
        }
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
        load(forceDownload = true)
    }

    private suspend fun refilter() {
        val lib = (_state.value as? State.Ready)?.library ?: return
        _view.value = withContext(Dispatchers.Default) { lib.view(LibraryFilter(store.languages.value)) }
    }

    private fun fetch(url: String, dest: File) {
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
                _state.value = State.Loading("Updating catalogue… $mb MB")
            }
        }
    }
}
