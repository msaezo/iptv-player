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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
    /** Last time the provider touched it (epoch seconds, 0 = unknown). */
    val added: Long = 0,
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

    /**
     * Fetches the catalogue and turns it into entries. The three big lists (channels,
     * movies, series) are read straight off the network, in parallel, and items unchanged
     * since the [previous] catalogue reuse the work already done for them.
     */
    fun load(
        acc: XtreamAccount,
        previous: Map<String, Entry>?,
        previousSeries: Map<String, SeriesSource>?,
        progress: (String) -> Unit,
    ): XtreamData {
        progress("Signing in…")
        checkLogin(acc)
        progress("Updating catalogue…")
        val live = AtomicInteger(); val movies = AtomicInteger(); val shows = AtomicInteger()
        val tick: () -> Unit = {
            progress("Updating catalogue…  %,d channels · %,d movies · %,d series".format(live.get(), movies.get(), shows.get()))
        }
        fun categories(action: String): Map<String, String> {
            val map = HashMap<String, String>()
            Http.read(acc.api(action), userAgent()) { reader ->
                readArray(reader, CATEGORY_KEYS) { o ->
                    val id = o["category_id"] ?: return@readArray
                    map[id] = o["category_name"]?.trim().orEmpty().ifEmpty { id }
                }
            }
            return map
        }

        val liveJob = {
            val cats = categories("get_live_categories")
            val out = ArrayList<Entry>(8192)
            Http.read(acc.api("get_live_streams"), userAgent()) { reader ->
                readArray(reader, LIVE_KEYS) { o ->
                    val id = o["stream_id"] ?: return@readArray
                    val name = o["name"]?.trim().orEmpty().ifEmpty { "Channel $id" }
                    val group = cats[o["category_id"]] ?: "Uncategorized"
                    val liveUrl = acc.liveUrl(id)
                    val logo = o["stream_icon"]?.takeIf { it.startsWith("http") }
                    val old = previous?.get(liveUrl)
                    out += if (old != null && old.rawName == name && old.group == group && old.logo == logo) old
                    else Entry(
                        title = name, rawName = name, url = liveUrl, logo = logo,
                        group = group, tvgId = o["epg_channel_id"]?.takeIf { it.isNotBlank() },
                        type = ContentType.LIVE,
                        language = Tags.languageOfGroup(group) ?: Tags.UNKNOWN,
                        quality = Tags.quality(name, group),
                    )
                    if (live.incrementAndGet() % 2000 == 0) tick()
                }
            }
            out
        }
        val movieJob = {
            val cats = categories("get_vod_categories")
            val out = ArrayList<Entry>(32_768)
            Http.read(acc.api("get_vod_streams"), userAgent()) { reader ->
                readArray(reader, VOD_KEYS) { o ->
                    val id = o["stream_id"] ?: return@readArray
                    val name = o["name"]?.trim().orEmpty().ifEmpty { return@readArray }
                    val group = cats[o["category_id"]] ?: "Movies"
                    val base = Classifier.movie(
                        rawName = name, url = acc.movieUrl(id, o["container_extension"]),
                        logo = o["stream_icon"]?.takeIf { it.startsWith("http") }, group = group,
                        previous = previous,
                    )
                    val year = base.year ?: o["year"]?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
                    val rating = parseRating(o["rating"])
                    val added = o["added"]?.toLongOrNull() ?: 0L
                    out += if (base.year == year && base.rating == rating && base.added == added) base
                    else base.copy(year = year, rating = rating, added = added)
                    if (movies.incrementAndGet() % 2000 == 0) tick()
                }
            }
            out
        }
        val seriesJob = {
            val cats = categories("get_series_categories")
            val out = ArrayList<SeriesSource>(8192)
            Http.read(acc.api("get_series"), userAgent()) { reader ->
                readArray(reader, SERIES_KEYS) { o ->
                    val id = o["series_id"] ?: return@readArray
                    val name = o["name"]?.trim().orEmpty().ifEmpty { return@readArray }
                    val group = cats[o["category_id"]] ?: "Series"
                    val modified = o["last_modified"]?.toLongOrNull() ?: 0L
                    val old = previousSeries?.get(id)
                    if (old != null && old.rawName == name && old.group == group && old.added == modified) {
                        out += old
                    } else {
                        val (t, y) = Classifier.splitYear(name)
                        out += SeriesSource(
                            seriesId = id, rawName = name, title = Tags.displayTitle(t), group = group,
                            cover = o["cover"]?.takeIf { it.startsWith("http") },
                            rating = parseRating(o["rating"]),
                            language = Tags.language(name, group),
                            year = y ?: (o["releaseDate"] ?: o["release_date"])?.take(4)?.toIntOrNull(),
                            added = modified,
                        )
                    }
                    if (shows.incrementAndGet() % 2000 == 0) tick()
                }
            }
            out
        }

        val liveList: List<Entry>
        val movieList: List<Entry>
        val seriesList: List<SeriesSource>
        val pool = Executors.newFixedThreadPool(3) { r ->
            Thread(r, "catalogue-net").apply { priority = Thread.MIN_PRIORITY + 1; isDaemon = true }
        }
        try {
            val f1 = pool.submit(Callable { runCatching(liveJob) })
            val f2 = pool.submit(Callable { runCatching(movieJob) })
            val f3 = pool.submit(Callable { runCatching(seriesJob) })
            // Some panels refuse simultaneous requests: anything that failed is tried again alone.
            liveList = f1.get().getOrElse { live.set(0); liveJob() }
            movieList = f2.get().getOrElse { movies.set(0); movieJob() }
            seriesList = f3.get().getOrElse { shows.set(0); seriesJob() }
        } finally {
            pool.shutdownNow()
        }
        tick()
        val entries = ArrayList<Entry>(liveList.size + movieList.size)
        entries.addAll(liveList); entries.addAll(movieList)
        return XtreamData(entries, seriesList, acc.epgUrl)
    }

    /** Episodes of one series (all seasons), as playable entries. */
    fun loadEpisodes(acc: XtreamAccount, src: SeriesSource, showName: String): List<Entry> {
        cacheDir.mkdirs()
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
        private val CATEGORY_KEYS = setOf("category_id", "category_name")
        private val LIVE_KEYS = setOf("stream_id", "name", "category_id", "stream_icon", "epg_channel_id")
        private val VOD_KEYS = setOf("stream_id", "name", "category_id", "stream_icon", "container_extension",
            "year", "rating", "added")
        private val SERIES_KEYS = setOf("series_id", "name", "category_id", "cover", "rating", "last_modified",
            "releaseDate", "release_date")

        fun parseRating(s: String?): Double? =
            s?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 && it <= 10 }

        /**
         * Streams a top-level JSON array of flat objects. Only [keys] are kept (null = all);
         * everything else, long synopses included, is skipped without being built.
         */
        fun readArray(reader: Reader, keys: Set<String>? = null, onItem: (Map<String, String>) -> Unit) {
            val r = JsonReader(reader)
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                // Some panels answer {} or an error object when a list is empty.
                r.skipValue(); return
            }
            r.beginArray()
            val map = HashMap<String, String>(16)
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) {
                    r.skipValue(); continue
                }
                map.clear()
                r.beginObject()
                while (r.hasNext()) {
                    val key = r.nextName()
                    if (keys != null && key !in keys) {
                        r.skipValue(); continue
                    }
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

    /** Opens [url] and hands its body to [block] as a buffered UTF-8 reader. */
    fun <T> read(url: String, ua: String, block: (Reader) -> T): T {
        val conn = open(url, ua)
        try {
            return java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream, Charsets.UTF_8), 1 shl 16).use(block)
        } finally {
            conn.disconnect()
        }
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
