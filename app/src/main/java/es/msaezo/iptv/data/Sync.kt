package es.msaezo.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL

/**
 * Keeps how far each film and episode was watched in step between devices, through a private
 * gist in the user's own GitHub account (their own token; nothing is built into the app).
 *
 * Stream addresses carry the provider username and password, so they are never uploaded:
 * only "movie/12345.mkv" or "series/678.mp4" is stored, and each device puts its own
 * account back in front of it.
 */
class ProgressSync(private val store: UserStore, private val playlists: PlaylistRepository) {
    private val lock = Mutex()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    suspend fun sync() {
        val token = store.syncToken.value.trim()
        if (token.isEmpty()) return
        val account = playlists.xtreamAccount ?: run { _status.value = "Needs a provider with the Xtream API"; return }
        lock.withLock {
            withContext(Dispatchers.IO) {
                _status.value = try {
                    run(token, account)
                } catch (e: Exception) {
                    "Last sync failed: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    private fun run(token: String, account: XtreamAccount): String {
        var id = store.syncGistId.value
        var remoteText: String? = null
        if (id.isNotBlank()) remoteText = try { read(token, id) } catch (e: NotFound) { id = ""; null }
        if (id.isBlank()) {
            id = find(token) ?: create(token)
            store.setSyncGistId(id)
            remoteText = read(token, id)
        }
        // Remote → this device.
        val remote = HashMap<String, Progress>()
        val shows = HashMap<String, String>()
        runCatching {
            val p = JSONObject(remoteText ?: "{}").optJSONObject("p") ?: JSONObject()
            for (path in p.keys()) {
                val a = p.getJSONArray(path)
                val url = urlOf(account, path) ?: continue
                remote[url] = Progress(a.getLong(0), a.getLong(1), a.getLong(2))
                a.optString(3).takeIf { it.isNotBlank() }?.let { shows[url] = it }
            }
        }
        store.mergeProgress(remote, shows)
        // This device → remote, only when something is new.
        val local = store.progress.value
        val episodeShow = store.episodeShow.value
        val p = JSONObject()
        for ((url, pr) in local) {
            val path = pathOf(url) ?: continue
            val a = JSONArray().put(pr.position).put(pr.duration).put(pr.updatedAt)
            episodeShow[url]?.let { a.put(it) }
            p.put(path, a)
        }
        val upToDate = remote.size == p.length() && local.all { (u, pr) -> pathOf(u) == null || remote[u]?.updatedAt == pr.updatedAt }
        if (!upToDate) write(token, id, JSONObject().put("tutv", 1).put("p", p).toString())
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date())
        return "Synced at $time · ${p.length()} titles"
    }

    // --- GitHub gists -----------------------------------------------------------------------

    private class NotFound : IOException("not found")

    private fun call(token: String, method: String, path: String, body: String? = null): String {
        val conn = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        try {
            try { conn.requestMethod = method } catch (e: ProtocolException) {
                // Older stacks refuse PATCH; GitHub takes POST for the same update.
                conn.requestMethod = "POST"
            }
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "TUTV")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code == 404) throw NotFound()
            if (code == 401) throw IOException("the token was refused (check it has the gist permission)")
            if (code !in 200..299) throw IOException("GitHub answered HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun read(token: String, id: String): String {
        val o = JSONObject(call(token, "GET", "/gists/$id"))
        val f = o.optJSONObject("files")?.optJSONObject(FILE) ?: return "{}"
        return if (f.optBoolean("truncated")) {
            val raw = URL(f.getString("raw_url")).openConnection() as HttpURLConnection
            raw.setRequestProperty("Authorization", "Bearer $token")
            raw.inputStream.bufferedReader().use { it.readText() }.also { raw.disconnect() }
        } else f.optString("content", "{}")
    }

    /** An existing TUTV gist in this account, so a second device joins the first one. */
    private fun find(token: String): String? {
        for (page in 1..5) {
            val a = JSONArray(call(token, "GET", "/gists?per_page=100&page=$page"))
            for (i in 0 until a.length()) {
                val g = a.getJSONObject(i)
                if (g.optJSONObject("files")?.has(FILE) == true) return g.getString("id")
            }
            if (a.length() < 100) break
        }
        return null
    }

    private fun create(token: String): String {
        val body = JSONObject().put("description", "TUTV watch progress").put("public", false)
            .put("files", JSONObject().put(FILE, JSONObject().put("content", "{\"tutv\":1,\"p\":{}}")))
        return JSONObject(call(token, "POST", "/gists", body.toString())).getString("id")
    }

    private fun write(token: String, id: String, content: String) {
        val body = JSONObject().put("files", JSONObject().put(FILE, JSONObject().put("content", content)))
        call(token, "PATCH", "/gists/$id", body.toString())
    }

    companion object {
        const val FILE = "tutv-progress.json"
        private val XTREAM = Regex("""^https?://[^/]+/(movie|series)/[^/]+/[^/]+/([^/?#]+)$""")

        /** "movie/12345.mkv" for a film or episode address, without the account; null otherwise. */
        fun pathOf(url: String): String? = XTREAM.find(url)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }

        fun urlOf(account: XtreamAccount, path: String): String? {
            val kind = path.substringBefore('/')
            val rest = path.substringAfter('/', "")
            if (kind != "movie" && kind != "series" || rest.isBlank()) return null
            return "${account.base}/$kind/${account.username}/${account.password}/$rest"
        }
    }
}
