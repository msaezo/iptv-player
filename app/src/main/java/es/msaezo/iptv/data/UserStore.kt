package es.msaezo.iptv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Progress(val position: Long, val duration: Long, val updatedAt: Long) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = duration > 0 && position >= duration * 0.92
    val inProgress: Boolean get() = position > 30_000 && !finished
}

/** Settings and personal data (favorites, watch progress), persisted in SharedPreferences. */
class UserStore(context: Context) {

    private val prefs = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    private val _playlistUrl = MutableStateFlow(prefs.getString("playlistUrl", "") ?: "")
    val playlistUrl: StateFlow<String> = _playlistUrl.asStateFlow()

    private val _languages = MutableStateFlow(
        prefs.getStringSet("languages", null)?.toSet() ?: LibraryFilter.DEFAULT_LANGUAGES
    )
    val languages: StateFlow<Set<String>> = _languages.asStateFlow()

    private val _tmdbKey = MutableStateFlow(prefs.getString("tmdbKey", "") ?: "")
    val tmdbKey: StateFlow<String> = _tmdbKey.asStateFlow()

    private val _omdbKey = MutableStateFlow(prefs.getString("omdbKey", "") ?: "")
    val omdbKey: StateFlow<String> = _omdbKey.asStateFlow()

    private val _userAgent = MutableStateFlow(prefs.getString("userAgent", DEFAULT_UA) ?: DEFAULT_UA)
    val userAgent: StateFlow<String> = _userAgent.asStateFlow()

    private val _preview = MutableStateFlow(prefs.getBoolean("preview", true))
    /** Play a short muted preview on a title's page. */
    val preview: StateFlow<Boolean> = _preview.asStateFlow()

    fun setPreview(on: Boolean) {
        _preview.value = on; prefs.edit().putBoolean("preview", on).apply()
    }

    private val _audioOffset = MutableStateFlow(prefs.getInt("audioOffsetMs", 0))
    /** Lip-sync correction in ms: positive = sound later, negative = sound earlier. */
    val audioOffset: StateFlow<Int> = _audioOffset.asStateFlow()

    fun setAudioOffset(ms: Int) {
        val v = ms.coerceIn(-AUDIO_OFFSET_MAX, AUDIO_OFFSET_MAX)
        _audioOffset.value = v; prefs.edit().putInt("audioOffsetMs", v).apply()
    }

    // --- live buffer, subtitles, followed teams, intro marks, app language ---------------

    private val _liveBuffer = MutableStateFlow(prefs.getInt("liveBufferSec", 0))
    /** Seconds of live TV to load before starting (0 = start as fast as possible). */
    val liveBuffer: StateFlow<Int> = _liveBuffer.asStateFlow()
    fun setLiveBuffer(sec: Int) {
        _liveBuffer.value = sec; prefs.edit().putInt("liveBufferSec", sec).apply()
    }

    data class SubtitleStyle(
        val serif: Boolean = true,
        /** Percent of the standard size. */
        val size: Int = 100,
        /** 0 = no plate, 1 = soft grey, 2 = dark. */
        val background: Int = 1,
        /** Distance from the bottom edge, in percent of the screen height. */
        val position: Int = 7,
    )

    private val _subtitles = MutableStateFlow(
        SubtitleStyle(
            serif = prefs.getBoolean("subSerif", true),
            size = prefs.getInt("subSize", 100),
            background = prefs.getInt("subBg", 1),
            position = prefs.getInt("subPos", 7),
        )
    )
    val subtitles: StateFlow<SubtitleStyle> = _subtitles.asStateFlow()
    fun setSubtitles(style: SubtitleStyle) {
        val v = style.copy(
            size = style.size.coerceIn(60, 180),
            background = style.background.coerceIn(0, 2),
            position = style.position.coerceIn(2, 40),
        )
        _subtitles.value = v
        prefs.edit().putBoolean("subSerif", v.serif).putInt("subSize", v.size)
            .putInt("subBg", v.background).putInt("subPos", v.position).apply()
    }

    private val _follow = MutableStateFlow(loadList("follow").ifEmpty { if (prefs.contains("follow")) emptyList() else DEFAULT_FOLLOW })
    /** Teams and players to look out for in live TV. */
    val follow: StateFlow<List<String>> = _follow.asStateFlow()
    fun setFollow(list: List<String>) {
        val v = list.map { it.trim() }.filter { it.length >= 3 }.distinctBy { it.lowercase() }
        _follow.value = v
        prefs.edit().putString("follow", JSONArray(v).toString()).apply()
    }

    private val _competitions = MutableStateFlow(prefs.getStringSet("competitions", null)?.toSet() ?: SportsRepository.DEFAULT)
    /** Competitions shown in the sports agenda (ids from [SportsRepository.ALL]). */
    val competitions: StateFlow<Set<String>> = _competitions.asStateFlow()
    fun setCompetitions(ids: Set<String>) {
        _competitions.value = ids; prefs.edit().putStringSet("competitions", ids).apply()
    }

    private val introMarks = HashMap(loadMap("introMarks"))
    /** Where a season's intro starts and ends, in ms, as taught by the user. */
    fun introMark(key: String): Pair<Long, Long>? = introMarks[key]?.let { v ->
        val a = v.substringBefore('|').toLongOrNull()
        val b = v.substringAfter('|').toLongOrNull()
        if (a != null && b != null) a to b else null
    }
    fun setIntroMark(key: String, start: Long, end: Long) {
        introMarks[key] = "$start|$end"
        prefs.edit().putString("introMarks", JSONObject(introMarks as Map<*, *>).toString()).apply()
    }

    private val _language = MutableStateFlow(
        prefs.getString("appLanguage", null) ?: if (java.util.Locale.getDefault().language == "es") "es" else "en"
    )
    /** "en" or "es". */
    val language: StateFlow<String> = _language.asStateFlow()
    fun setLanguage(lang: String) {
        _language.value = lang; prefs.edit().putString("appLanguage", lang).apply()
    }

    // --- moving everything to another device ----------------------------------------------

    /** All settings and personal data as one JSON document. */
    fun export(): String {
        val s = JSONObject(); val i = JSONObject(); val b = JSONObject(); val l = JSONObject(); val ss = JSONObject()
        for ((k, v) in prefs.all) when (v) {
            is String -> s.put(k, v)
            is Int -> i.put(k, v)
            is Boolean -> b.put(k, v)
            is Long -> l.put(k, v)
            is Set<*> -> ss.put(k, JSONArray(v.toList()))
        }
        return JSONObject().put("tutv", 1).put("s", s).put("i", i).put("b", b).put("l", l).put("ss", ss).toString()
    }

    /** Replaces everything on this device with an [export] from another one. The app must restart afterwards. */
    fun import(json: String) {
        val o = JSONObject(json)
        if (o.optInt("tutv") != 1) throw IllegalArgumentException("Not a TUTV settings file")
        val e = prefs.edit().clear()
        o.optJSONObject("s")?.let { m -> for (k in m.keys()) e.putString(k, m.getString(k)) }
        o.optJSONObject("i")?.let { m -> for (k in m.keys()) e.putInt(k, m.getInt(k)) }
        o.optJSONObject("b")?.let { m -> for (k in m.keys()) e.putBoolean(k, m.getBoolean(k)) }
        o.optJSONObject("l")?.let { m -> for (k in m.keys()) e.putLong(k, m.getLong(k)) }
        o.optJSONObject("ss")?.let { m ->
            for (k in m.keys()) {
                val a = m.getJSONArray(k)
                e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
            }
        }
        e.commit()
    }

    private val trackPrefs = HashMap(loadMap("trackPrefs"))

    /** Audio and subtitle language last chosen for a series (or for films): "audio|subtitle". */
    fun trackPref(key: String): String? = trackPrefs[key]

    fun setTrackPref(key: String, value: String) {
        if (trackPrefs[key] == value) return
        trackPrefs[key] = value
        prefs.edit().putString("trackPrefs", JSONObject(trackPrefs as Map<*, *>).toString()).apply()
    }

    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _progress = MutableStateFlow(loadProgress())
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    private val _recentLive = MutableStateFlow(loadList("recentLive"))
    val recentLive: StateFlow<List<String>> = _recentLive.asStateFlow()

    private val _episodeShow = MutableStateFlow(loadMap("episodeShow"))
    /** Episode URL → show id, so "Continue watching" can find lazily-loaded series. */
    val episodeShow: StateFlow<Map<String, String>> = _episodeShow.asStateFlow()

    fun rememberEpisodes(showId: String, urls: List<String>) {
        val m = LinkedHashMap(_episodeShow.value)
        for (u in urls) { m.remove(u); m[u] = showId }
        while (m.size > 4000) m.remove(m.keys.first())
        _episodeShow.value = m
        prefs.edit().putString("episodeShow", JSONObject(m as Map<*, *>).toString()).apply()
    }

    private val _showLanguage = MutableStateFlow(loadMap("showLanguage"))
    val showLanguage: StateFlow<Map<String, String>> = _showLanguage.asStateFlow()

    fun setPlaylistUrl(url: String) {
        _playlistUrl.value = url.trim()
        prefs.edit().putString("playlistUrl", url.trim()).apply()
    }

    fun setLanguages(langs: Set<String>) {
        _languages.value = langs
        prefs.edit().putStringSet("languages", langs).apply()
    }

    fun setTmdbKey(k: String) {
        _tmdbKey.value = k.trim(); prefs.edit().putString("tmdbKey", k.trim()).apply()
    }

    fun setOmdbKey(k: String) {
        _omdbKey.value = k.trim(); prefs.edit().putString("omdbKey", k.trim()).apply()
    }

    fun setUserAgent(ua: String) {
        val v = ua.trim().ifEmpty { DEFAULT_UA }
        _userAgent.value = v; prefs.edit().putString("userAgent", v).apply()
    }

    // --- favorites: "m:<movieId>", "s:<showId>", "l:<url>" -------------------------------

    fun isFavorite(key: String) = key in _favorites.value

    fun toggleFavorite(key: String) {
        val s = _favorites.value.toMutableSet()
        if (!s.remove(key)) s.add(key)
        _favorites.value = s
        prefs.edit().putStringSet("favorites", s).apply()
    }

    // --- progress -----------------------------------------------------------------------

    fun saveProgress(url: String, position: Long, duration: Long) {
        if (duration <= 0) return
        val m = _progress.value.toMutableMap()
        m[url] = Progress(position, duration, System.currentTimeMillis())
        // keep the map bounded
        if (m.size > 2000) {
            m.entries.sortedBy { it.value.updatedAt }.take(m.size - 2000).forEach { m.remove(it.key) }
        }
        _progress.value = m
        val json = JSONObject()
        for ((k, p) in m) json.put(k, JSONArray().put(p.position).put(p.duration).put(p.updatedAt))
        prefs.edit().putString("progress", json.toString()).apply()
    }

    fun clearHistory() {
        _progress.value = emptyMap()
        _recentLive.value = emptyList()
        prefs.edit().remove("progress").remove("recentLive").apply()
    }

    private fun loadProgress(): Map<String, Progress> {
        val raw = prefs.getString("progress", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                for (k in o.keys()) {
                    val a = o.getJSONArray(k)
                    put(k, Progress(a.getLong(0), a.getLong(1), a.getLong(2)))
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // --- recents / per-show language ----------------------------------------------------

    fun addRecentLive(url: String) {
        val l = (listOf(url) + _recentLive.value.filter { it != url }).take(30)
        _recentLive.value = l
        prefs.edit().putString("recentLive", JSONArray(l).toString()).apply()
    }

    fun setShowLanguage(showId: String, lang: String) {
        val m = _showLanguage.value + (showId to lang)
        _showLanguage.value = m
        prefs.edit().putString("showLanguage", JSONObject(m as Map<*, *>).toString()).apply()
    }

    private fun loadList(key: String): List<String> = try {
        val a = JSONArray(prefs.getString(key, "[]"))
        List(a.length()) { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun loadMap(key: String): Map<String, String> = try {
        val o = JSONObject(prefs.getString(key, "{}") ?: "{}")
        buildMap { for (k in o.keys()) put(k, o.getString(k)) }
    } catch (e: Exception) {
        emptyMap()
    }

    companion object {
        /** Lip-sync errors beyond half a second are a broken file, not a delay to tune. */
        const val AUDIO_OFFSET_MAX = 500
        const val AUDIO_OFFSET_STEP = 25
        val DEFAULT_FOLLOW = listOf("Real Madrid", "Barcelona", "Alcaraz", "Jódar", "España")
        const val DEFAULT_UA = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
