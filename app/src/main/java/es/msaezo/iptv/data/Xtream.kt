package es.msaezo.iptv.data

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** A series as listed by the Xtream API; episodes are fetched when the show is opened. */
data class SeriesSource(
    val seriesId: String,
    val rawName: String,
    val title: String,
    val group: String,
    val cover: String?,
    val rating: Double?,
    val language: String,
    val year: Int?,
)

/** Credentials + server of an Xtream Codes panel, derived from a get.php / player_api.php URL. */
data class XtreamAccount(
    val base: String,
    val username: String,
    val password: String,
    val liveExt: String = "ts",
) {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun api(action: String? = null, extra: String = ""): String =
        "$base/player_api.php?username=${enc(username)}&password=${enc(password)}" +
            (if (action != null) "&action=$action" else "") + extra

    fun liveUrl(id: String) = "$base/live/$username/$password/$id.$liveExt"
    fun movieUrl(id: String, ext: String?) = "$base/movie/$username/$password/$id.${ext.orEmpty().ifBlank { "mp4" }}"
    fun episodeUrl(id: String, ext: String?) = "$base/series/$username/$password/$id.${ext.orEmpty().ifBlank { "mp4" }}"
    val epgUrl get() = "$base/xmltv.php?username=${enc(username)}&password=${enc(password)}"

    companion object {
        fun fromUrl(url: String): XtreamAccount? {
            val uri = try { URI(url.trim()) } catch (e: Exception) { return null }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host ?: return null
            val query = uri.rawQuery ?: return null
            val params = query.split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).lowercase() to URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }.toMap()
            val user = params["username"]?.takeIf { it.isNotBlank() } ?: return null
            val pass = params["password"]?.takeIf { it.isNotBlank() } ?: return null
            val path = uri.rawPath.orEmpty()
            val prefix = when {
                path.endsWith("/get.php") -> path.removeSuffix("/get.php")
                path.endsWith("/player_api.php") -> path.removeSuffix("/player_api.php")
                else -> return null
            }
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val ext = when (params["output"]?.lowercase()) {
                "m3u8", "hls" -> "m3u8"
                else -> "ts"
            }
            return XtreamAccount("$scheme://$host$port$prefix", user, pass, ext)
        }
    }
}

class XtreamData(val entries: List<Entry>, val series: List<SeriesSource>, val epgUrl: String)

/** Loads live channels, movies and series from an Xtream Codes panel (player_api.php). */
class XtreamClient(private val cacheDir: File, private val userAgent: () -> String) {

    fun load(acc: XtreamAccount, force: Boolean, progress: (String) -> Unit): XtreamData {
        cacheDir.mkdirs()
        val stamp = File(cacheDir, "xt_account.txt")
        val accountKey = "${acc.base}|${acc.username}"
        val sameAccount = stamp.exists() && stamp.readText() == accountKey
        val needDownload = force || !sameAccount ||
            ACTIONS.any { !File(cacheDir, "xt_$it.json").exists() }

        if (needDownload) {
            progress("Signing in…")
            checkLogin(acc)
            for ((i, action) in ACTIONS.withIndex()) {
                progress("Updating catalogue: ${LABELS[i]}…")
                Http.download(acc.api(action), File(cacheDir, "xt_$action.json"), userAgent()) { mb ->
                    progress("Updating catalogue: ${LABELS[i]}… $mb MB")
                }
            }
            stamp.writeText(accountKey)
        }

        progress("Reading channels…")
        val liveCats = readCategories(File(cacheDir, "xt_get_live_categories.json"))
        val vodCats = readCategories(File(cacheDir, "xt_get_vod_categories.json"))
        val seriesCats = readCategories(File(cacheDir, "xt_get_series_categories.json"))

        val entries = ArrayList<Entry>(32_768)
        readArray(File(cacheDir, "xt_get_live_streams.json")) { o ->
            val id = o["stream_id"] ?: return@readArray
            val name = o["name"]?.trim().orEmpty().ifEmpty { "Channel $id" }
            val group = liveCats[o["category_id"]] ?: "Uncategorized"
            entries += Entry(
                title = name, rawName = name, url = acc.liveUrl(id),
                logo = o["stream_icon"]?.takeIf { it.startsWith("http") },
                group = group, tvgId = o["epg_channel_id"]?.takeIf { it.isNotBlank() },
                type = ContentType.LIVE,
                language = Tags.languageOfGroup(group) ?: Tags.UNKNOWN,
                quality = Tags.quality(name, group),
            )
        }
        progress("Reading movies…")
        readArray(File(cacheDir, "xt_get_vod_streams.json")) { o ->
            val id = o["stream_id"] ?: return@readArray
            val name = o["name"]?.trim().orEmpty().ifEmpty { return@readArray }
            val group = vodCats[o["category_id"]] ?: "Movies"
            val base = Classifier.classify(
                rawName = name, url = acc.movieUrl(id, o["container_extension"]),
                logo = o["stream_icon"]?.takeIf { it.startsWith("http") }, group = group,
                tvgId = null, duration = null, userAgent = null, referrer = null,
            )
            val year = base.year ?: o["year"]?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
            entries += base.copy(year = year, rating = parseRating(o["rating"]))
        }
        progress("Reading series…")
        val series = ArrayList<SeriesSource>(8192)
        readArray(File(cacheDir, "xt_get_series.json")) { o ->
            val id = o["series_id"] ?: return@readArray
            val name = o["name"]?.trim().orEmpty().ifEmpty { return@readArray }
            val group = seriesCats[o["category_id"]] ?: "Series"
            val (t, y) = Classifier.splitYear(name)
            series += SeriesSource(
                seriesId = id, rawName = name, title = Tags.displayTitle(t), group = group,
                cover = o["cover"]?.takeIf { it.startsWith("http") },
                rating = parseRating(o["rating"]),
                language = Tags.language(name, group),
                year = y ?: (o["releaseDate"] ?: o["release_date"])?.take(4)?.toIntOrNull(),
            )
        }
        return XtreamData(entries, series, acc.epgUrl)
    }

    /** Episodes of one series (all seasons), as playable entries. */
    fun loadEpisodes(acc: XtreamAccount, src: SeriesSource, showName: String): List<Entry> {
        val file = File(cacheDir, "xt_series_${src.seriesId}.json")
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > 6 * 3_600_000L) {
            Http.download(acc.api("get_series_info", "&series_id=${src.seriesId}"), file, userAgent()) {}
        }
        val root = JSONObject(file.readText())
        val out = ArrayList<Entry>()
        fun addEpisode(e: JSONObject, seasonKey: String?) {
            val id = e.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return
            val season = e.opt("season")?.toString()?.toIntOrNull() ?: seasonKey?.toIntOrNull() ?: 1
            val num = e.opt("episode_num")?.toString()?.toIntOrNull()
            val rawTitle = e.optString("title")
            val info = e.optJSONObject("info")
            val epTitle = Classifier.episodeTitle(rawTitle).ifBlank { if (num != null) "Episode $num" else rawTitle }
            out += Entry(
                title = epTitle, rawName = rawTitle,
                url = acc.episodeUrl(id, e.optString("container_extension")),
                logo = info?.optString("movie_image")?.takeIf { it.startsWith("http") } ?: src.cover,
                group = src.group, tvgId = null, type = ContentType.SERIES,
                showName = showName, season = season, episode = num,
                language = src.language,
                quality = Tags.quality(rawTitle, src.rawName),
            )
        }
        when (val eps = root.opt("episodes")) {
            is JSONObject -> for (k in eps.keys()) {
                val arr = eps.optJSONArray(k) ?: continue
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(it, k) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val item = eps.opt(i)) {
                    is JSONObject -> addEpisode(item, null)
                    is JSONArray -> for (j in 0 until item.length()) item.optJSONObject(j)?.let { addEpisode(it, null) }
                }
            }
        }
        return out
    }

    private fun checkLogin(acc: XtreamAccount) {
        val text = Http.getText(acc.api(), userAgent())
        val o = try { JSONObject(text) } catch (e: Exception) {
            throw IOException("The server didn't answer like an Xtream panel")
        }
        val info = o.optJSONObject("user_info") ?: throw IOException("Login failed (no user info)")
        if (info.opt("auth")?.toString() == "0") throw IOException("Login rejected: check username/password")
        val status = info.optString("status")
        if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Account status: $status")
        }
    }

    companion object {
        private val ACTIONS = listOf(
            "get_live_categories", "get_vod_categories", "get_series_categories",
            "get_live_streams", "get_vod_streams", "get_series",
        )
        private val LABELS = listOf(
            "live categories", "movie categories", "series categories",
            "channel list", "movie list", "series list",
        )

        fun parseRating(s: String?): Double? =
            s?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 && it <= 10 }

        private fun readCategories(file: File): Map<String, String> {
            val map = HashMap<String, String>()
            readArray(file) { o ->
                val id = o["category_id"] ?: return@readArray
                map[id] = o["category_name"]?.trim().orEmpty().ifEmpty { id }
            }
            return map
        }

        /** Streams a top-level JSON array of flat objects; nested values are skipped. */
        fun readArray(file: File, onItem: (Map<String, String>) -> Unit) {
            if (!file.exists()) return
            file.bufferedReader(Charsets.UTF_8).use { readArray(it, onItem) }
        }

        fun readArray(reader: Reader, onItem: (Map<String, String>) -> Unit) {
            val r = JsonReader(reader)
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                // Some panels answer {} or an error object when a list is empty.
                r.skipValue(); return
            }
            r.beginArray()
            val map = HashMap<String, String>(32)
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) {
                    r.skipValue(); continue
                }
                map.clear()
                r.beginObject()
                while (r.hasNext()) {
                    val key = r.nextName()
                    when (r.peek()) {
                        JsonToken.STRING, JsonToken.NUMBER -> map[key] = r.nextString()
                        JsonToken.BOOLEAN -> map[key] = r.nextBoolean().toString()
                        JsonToken.NULL -> r.nextNull()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                onItem(map)
            }
            r.endArray()
        }
    }
}

/** Small HTTP helpers with manual redirect handling (http→https and back). */
object Http {
    private fun open(url: String, ua: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 90_000
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                conn.disconnect()
                current = URL(URL(current), loc).toString()
            } else if (code != 200) {
                conn.disconnect()
                throw IOException(
                    when (code) {
                        401, 403 -> "Access denied by the server (HTTP $code)"
                        404 -> "Not found (HTTP 404)"
                        in 880..890 -> "The provider blocked this request (HTTP $code)"
                        else -> "Server answered HTTP $code"
                    }
                )
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects")
    }

    fun getText(url: String, ua: String): String {
        val conn = open(url, ua)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, dest: File, ua: String, onProgressMb: (Long) -> Unit) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        val conn = open(url, ua)
        try {
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        if (total - last > 1_000_000) {
                            last = total; onProgressMb(total / 1_000_000)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true); tmp.delete()
        }
    }
}
