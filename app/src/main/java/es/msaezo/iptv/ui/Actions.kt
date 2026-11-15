package es.msaezo.iptv.ui

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.Entry
import es.msaezo.iptv.data.Episode
import es.msaezo.iptv.data.Library
import es.msaezo.iptv.data.LiveChannel
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.Show
import es.msaezo.iptv.data.Tags
import es.msaezo.iptv.player.Playback

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    MOVIES("Movies", Icons.Filled.Movie),
    SERIES("Series", Icons.Filled.Tv),
    LIVE("Live TV", Icons.Filled.LiveTv),
    SEARCH("Search", Icons.Filled.Search),
    SETTINGS("Settings", Icons.Filled.Settings),
}

sealed interface Route {
    data class MovieDetail(val id: String) : Route
    data class ShowDetail(val id: String) : Route
    data class CollectionRoute(val key: String) : Route
    data class CategoryRoute(val series: Boolean, val name: String) : Route
    /** A ready-made list of movies / shows kept in [Nav.lists] ("See all" of a smart row). */
    data class ItemsRoute(val title: String) : Route
}

/** Minimal navigation: a selected tab plus a stack of detail screens. */
class Nav {
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Route>()
    val lists = HashMap<String, List<Any>>()

    fun openList(title: String, items: List<Any>) {
        lists[title] = items
        open(Route.ItemsRoute(title))
    }

    var movieCategory by mutableIntStateOf(0)
    var showCategory by mutableIntStateOf(0)
    var liveCategory by mutableIntStateOf(0)
    var liveFilter by mutableIntStateOf(0)
    var searchQuery by mutableStateOf("")

    val movieRows = LazyListState()
    val showRows = LazyListState()
    val liveList = LazyListState()
    val homeList = LazyListState()

    fun select(t: Tab) {
        stack.clear(); tab = t
    }

    fun open(r: Route) {
        stack.add(r)
    }

    fun back(): Boolean = when {
        stack.isNotEmpty() -> { stack.removeAt(stack.size - 1); true }
        tab != Tab.HOME -> { tab = Tab.HOME; true }
        else -> false
    }
}

/** Starts playback for the different kinds of content. */
object Actions {
    private val store get() = IptvApp.instance.store

    private fun Entry.toItem(title: String, subtitle: String?, start: Long = 0) = Playback.Item(
        url = url, title = title, subtitle = subtitle, userAgent = userAgent, referrer = referrer,
        startPositionMs = start,
    )

    /** Most recently watched, unfinished version of a movie (if any). */
    fun resumeVersion(movie: Movie): Pair<Entry, Long>? {
        val progress = store.progress.value
        return movie.versions.mapNotNull { v -> progress[v.url]?.takeIf { it.inProgress }?.let { v to it } }
            .maxByOrNull { it.second.updatedAt }
            ?.let { it.first to it.second.position }
    }

    fun playMovie(context: Context, movie: Movie, version: Entry? = null, fromStart: Boolean = false) {
        val resume = resumeVersion(movie)
        val v = version ?: resume?.first ?: movie.versions.first()
        val start = if (!fromStart && resume != null && resume.first.url == v.url) resume.second else 0L
        Preview.stop()
        Playback.play(
            context,
            listOf(v.toItem(movie.title, "${movie.title} · ${v.versionLabel}", start)),
            0, live = false,
        )
    }

    fun preferredLanguage(show: Show): String {
        val chosen = store.showLanguage.value[show.id]
        if (chosen != null && chosen in show.languages) return chosen
        return show.languages.minByOrNull { Library.langRank(it) } ?: Tags.UNKNOWN
    }

    fun playEpisode(context: Context, show: Show, episode: Episode, language: String, fromStart: Boolean = false) {
        val season = show.episodesOf(episode.season)
        val startIndex = season.indexOf(episode).coerceAtLeast(0)
        val progress = store.progress.value
        val items = season.drop(startIndex).mapIndexed { i, ep ->
            val v = Library.bestVersion(ep.versions, language)!!
            val start = if (i == 0 && !fromStart) progress[v.url]?.takeIf { it.inProgress }?.position ?: 0L else 0L
            v.toItem(show.name, "${show.name} · ${ep.label}${if (ep.title.isNotBlank()) " · " + ep.title else ""}", start)
        }
        store.rememberEpisodes(show.id, show.episodes.flatMap { ep -> ep.versions.map { it.url } })
        Preview.stop()
        Playback.play(context, items, 0, live = false)
    }

    /** Episode to continue: the last one touched, or the next one if it was finished. */
    fun nextEpisode(show: Show, language: String): Pair<Episode, Boolean>? {
        val progress = store.progress.value
        var best: Pair<Int, Long>? = null
        show.episodes.forEachIndexed { i, ep ->
            for (v in ep.versions) {
                val p = progress[v.url] ?: continue
                if (best == null || p.updatedAt > best!!.second) best = i to p.updatedAt
            }
        }
        val idx = best?.first ?: return null
        val ep = show.episodes[idx]
        val finished = ep.versions.any { progress[it.url]?.finished == true }
        return if (finished) show.episodes.getOrNull(idx + 1)?.let { it to false } else ep to true
    }

    fun playLive(context: Context, channels: List<LiveChannel>, index: Int) {
        Preview.stop()
        Playback.play(
            context,
            channels.map { ch ->
                val best = ch.best
                Playback.Item(
                    url = best.url, title = ch.name, subtitle = ch.pack,
                    userAgent = best.userAgent, referrer = best.referrer,
                    alternatives = ch.versions.mapIndexed { i, v -> Playback.Alt(ch.labels[i], v.url) },
                )
            },
            index, live = true,
        )
    }
}
