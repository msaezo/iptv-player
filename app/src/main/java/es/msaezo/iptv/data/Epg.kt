package es.msaezo.iptv.data

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class Programme(val title: String, val start: Long, val end: Long)

/** What a channel is showing now and what comes next (either may be missing). */
data class NowNext(val now: Programme?, val next: Programme?, val fetchedAt: Long)

/**
 * Programme guide, asked from the provider one channel at a time and only for the
 * channels actually on screen, so a list of thousands costs nothing until scrolled.
 */
class EpgRepository(
    private val playlists: PlaylistRepository,
    private val store: UserStore,
    private val scope: CoroutineScope,
) {
    private val _guide = MutableStateFlow<Map<String, NowNext>>(emptyMap())
    /** Keyed by channel id. */
    val guide: StateFlow<Map<String, NowNext>> = _guide.asStateFlow()

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val limit = Semaphore(3)

    fun request(channel: LiveChannel) {
        val entry = channel.guideEntry ?: return
        val acc = playlists.xtreamAccount ?: return
        val known = _guide.value[channel.id]
        val nowMs = System.currentTimeMillis()
        if (known != null) {
            val current = known.now
            val stillValid = if (current != null) nowMs < current.end else nowMs - known.fetchedAt < EMPTY_TTL_MS
            if (stillValid) return
        }
        if (!inFlight.add(channel.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                val result = limit.withPermit { fetch(acc, entry) }
                _guide.value = _guide.value + (channel.id to result)
            } catch (e: Exception) {
                _guide.value = _guide.value + (channel.id to NowNext(null, null, System.currentTimeMillis()))
            } finally {
                inFlight.remove(channel.id)
            }
        }
    }

    private fun fetch(acc: XtreamAccount, entry: Entry): NowNext {
        val id = entry.url.substringAfterLast('/').substringBefore('.')
        val text = Http.getText(acc.api("get_short_epg", "&stream_id=$id&limit=4"), store.userAgent.value)
        val list = JSONObject(text).optJSONArray("epg_listings")
        val nowMs = System.currentTimeMillis()
        val items = ArrayList<Programme>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val start = (o.optString("start_timestamp").toLongOrNull() ?: continue) * 1000
                val end = (o.optString("stop_timestamp").toLongOrNull() ?: continue) * 1000
                val title = decode(o.optString("title"))
                if (title.isNotBlank()) items += Programme(title, start, end)
            }
        }
        items.sortBy { it.start }
        val now = items.firstOrNull { it.start <= nowMs && nowMs < it.end }
        val next = items.firstOrNull { it.start > nowMs }
        return NowNext(now, next, nowMs)
    }

    private fun decode(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: Exception) {
        s.trim()
    }

    private companion object {
        const val EMPTY_TTL_MS = 15 * 60_000L
    }
}
