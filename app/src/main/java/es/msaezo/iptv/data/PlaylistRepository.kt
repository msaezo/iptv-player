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
import java.net.HttpURLConnection
import java.net.URL

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
                if (forceDownload || !cacheFile.exists() || cacheFile.length() == 0L) {
                    _state.value = State.Loading("Downloading playlist…")
                    fetch(url, cacheFile)
                }
                _state.value = State.Loading("Reading playlist…")
                val result = cacheFile.bufferedReader(Charsets.UTF_8).use { M3uParser.parse(it) }
                if (result.entries.isEmpty()) throw IOException("The playlist is empty or not a valid M3U file")
                _state.value = State.Loading("Organising ${result.entries.size} items…")
                val library = Library(result.entries, result.epgUrl)
                _view.value = library.view(LibraryFilter(store.languages.value))
                _state.value = State.Ready(library)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: e.javaClass.simpleName)
            }
        }
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
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        if (url.startsWith("content://") || url.startsWith("file://")) {
            context.contentResolver.openInputStream(Uri.parse(url)).use { input ->
                requireNotNull(input) { "Cannot open file" }
                tmp.outputStream().use { input.copyTo(it) }
            }
        } else {
            var current = url
            var redirects = 0
            while (true) {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", store.userAgent.value)
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                    conn.disconnect()
                    current = URL(URL(current), loc).toString()
                    if (++redirects > 5) throw IOException("Too many redirects")
                    continue
                }
                if (code != 200) {
                    conn.disconnect()
                    throw IOException("Server answered HTTP $code")
                }
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                            if (total - lastReport > 1_000_000) {
                                lastReport = total
                                _state.value = State.Loading("Downloading playlist… ${total / 1_000_000} MB")
                            }
                        }
                    }
                }
                conn.disconnect()
                break
            }
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true); tmp.delete()
        }
    }
}
