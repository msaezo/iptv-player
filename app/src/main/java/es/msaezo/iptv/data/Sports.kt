package es.msaezo.iptv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A competition the sports agenda can follow. [sport] is the Live TV sport it maps to. */
data class Competition(val id: String, val name: String, val sport: String, val path: String)

/** One fixture, session or match from the agenda. */
data class SportEvent(
    val competition: Competition,
    val title: String,
    val start: Long,
    val live: Boolean,
    /** Short status from the source while in play ("67'", "Set 2"…), if any. */
    val detail: String?,
)

/**
 * Fixtures for the next couple of days, read from ESPN's public scoreboard feed. That
 * feed is undocumented and has no key: if its format changes, the agenda simply shows
 * nothing until the reader here is updated.
 */
class SportsRepository(private val store: UserStore, private val scope: CoroutineScope) {

    private val _events = MutableStateFlow<List<SportEvent>>(emptyList())
    val events: StateFlow<List<SportEvent>> = _events.asStateFlow()

    @Volatile private var fetchedAt = 0L
    @Volatile private var fetchedFor: Set<String> = emptySet()
    @Volatile private var busy = false

    fun refreshIfStale() {
        val wanted = store.competitions.value
        val fresh = System.currentTimeMillis() - fetchedAt < REFRESH_MS && fetchedFor == wanted
        if (busy || fresh) return
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
                val range = day.format(Date(now)) + "-" + day.format(Date(now + 2 * 86_400_000L))
                val out = ArrayList<SportEvent>()
                for (c in ALL.filter { it.id in wanted }) {
                    try {
                        val text = Http.getText("https://site.api.espn.com/apis/site/v2/sports/${c.path}/scoreboard?dates=$range", "TUTV")
                        out += parse(c, JSONObject(text), now)
                    } catch (e: Exception) {
                        // One competition failing must not hide the others.
                    }
                }
                _events.value = out.sortedWith(compareBy({ !it.live }, { it.start })).take(60)
                fetchedAt = now
                fetchedFor = wanted
            } finally {
                busy = false
            }
        }
    }

    companion object {
        private const val REFRESH_MS = 20 * 60_000L

        val ALL = listOf(
            Competition("laliga", "LaLiga", "Football", "soccer/esp.1"),
            Competition("champions", "Champions League", "Football", "soccer/uefa.champions"),
            Competition("copa", "Copa del Rey", "Football", "soccer/esp.copa_del_rey"),
            Competition("premier", "Premier League", "Football", "soccer/eng.1"),
            Competition("worldcup", "World Cup", "Football", "soccer/fifa.world"),
            Competition("nations", "Nations League", "Football", "soccer/uefa.nations"),
            Competition("euro", "Euro", "Football", "soccer/uefa.euro"),
            Competition("atp", "ATP", "Tennis", "tennis/atp"),
            Competition("wta", "WTA", "Tennis", "tennis/wta"),
            Competition("nba", "NBA", "Basketball", "basketball/nba"),
            Competition("f1", "Formula 1", "Formula 1", "racing/f1"),
        )
        val DEFAULT = setOf("laliga", "champions", "copa", "worldcup", "nations", "atp", "nba", "f1")

        private fun time(s: String): Long? {
            for (p in listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
                try {
                    val f = SimpleDateFormat(p, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
                    return f.parse(s)?.time ?: continue
                } catch (e: Exception) {
                }
            }
            return null
        }

        /** Every nested object that looks like a contest: it has competitors and a date. */
        private fun contests(node: Any?, into: MutableList<JSONObject>) {
            when (node) {
                is JSONObject -> {
                    if (node.optJSONArray("competitors") != null && node.optString("date").isNotBlank()) into += node
                    else for (k in node.keys()) contests(node.opt(k), into)
                }
                is JSONArray -> for (i in 0 until node.length()) contests(node.opt(i), into)
            }
        }

        private fun names(contest: JSONObject): List<String> {
            val a = contest.optJSONArray("competitors") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val c = a.optJSONObject(i) ?: return@mapNotNull null
                (c.optJSONObject("team") ?: c.optJSONObject("athlete"))?.optString("displayName")?.takeIf { it.isNotBlank() }
            }
        }

        internal fun parse(c: Competition, root: JSONObject, now: Long): List<SportEvent> {
            val out = ArrayList<SportEvent>()
            val events = root.optJSONArray("events") ?: return out
            for (i in 0 until events.length()) {
                val ev = events.optJSONObject(i) ?: continue
                val evName = ev.optString("name").ifBlank { ev.optString("shortName") }
                val found = ArrayList<JSONObject>()
                contests(ev.opt("competitions"), found)
                contests(ev.opt("groupings"), found)
                if (found.isEmpty()) {
                    val start = time(ev.optString("date")) ?: continue
                    if (evName.isNotBlank() && start > now - 3 * 3_600_000L) out += SportEvent(c, evName, start, false, null)
                    continue
                }
                for (contest in found) {
                    val start = time(contest.optString("date")) ?: continue
                    val type = (contest.optJSONObject("status") ?: ev.optJSONObject("status"))?.optJSONObject("type")
                    val state = type?.optString("state").orEmpty()
                    if (state == "post") continue
                    val live = state == "in"
                    if (!live && start < now - 3 * 3_600_000L) continue
                    val who = names(contest)
                    val session = contest.optJSONObject("type")?.let { it.optString("text").ifBlank { it.optString("abbreviation") } }
                    val title = when {
                        who.size == 2 -> "${who[0]} vs ${who[1]}"
                        !session.isNullOrBlank() -> "$evName · $session"
                        else -> evName
                    }
                    if (title.isBlank()) continue
                    out += SportEvent(c, title, start, live, type?.optString("shortDetail")?.takeIf { live && it.isNotBlank() })
                }
            }
            // A tennis day can hold a hundred matches: keep what is on now and what comes first.
            return out.sortedWith(compareBy({ !it.live }, { it.start })).take(if (c.sport == "Tennis") 12 else 20)
        }
    }
}
