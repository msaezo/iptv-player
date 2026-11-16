package es.msaezo.iptv.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.R
import es.msaezo.iptv.data.Category
import es.msaezo.iptv.data.LibraryView
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import es.msaezo.iptv.data.LiveGuide
import es.msaezo.iptv.data.Library
import es.msaezo.iptv.data.LiveChannel
import es.msaezo.iptv.data.NowNext
import androidx.compose.ui.unit.Dp
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.ResolvedCollection
import es.msaezo.iptv.data.Show
import es.msaezo.iptv.data.TmdbItem
import kotlinx.coroutines.delay

private sealed interface Resume {
    data class M(val movie: Movie, val progress: Float) : Resume
    data class S(val show: Show, val progress: Float) : Resume
}

private sealed interface Featured {
    data class M(val movie: Movie) : Featured
    data class S(val show: Show) : Featured
}

/** Up to six titles for the rotating banner: ones with artwork, from the top collections. */
private fun pickFeatured(
    collections: List<ResolvedCollection>,
    known: Map<String, TmdbItem>,
    view: LibraryView,
    seed: Int,
): List<Featured> {
    val candidates = ArrayList<Featured>()
    for (c in collections.take(4)) {
        c.movies.filter { known[it.id]?.backdrop != null }.take(4).forEach { candidates += Featured.M(it) }
        c.shows.filter { known[it.id]?.backdrop != null }.take(3).forEach { candidates += Featured.S(it) }
        if (candidates.size >= 10) break
    }
    if (candidates.isEmpty()) {
        val recent = view.recentMovies.filter { it.poster != null }.take(12)
        val pool = recent.ifEmpty { view.movies.filter { it.poster != null }.take(20) }
        pool.forEach { candidates += Featured.M(it) }
    }
    if (candidates.isEmpty()) view.shows.firstOrNull()?.let { candidates += Featured.S(it) }
    if (candidates.isEmpty()) return emptyList()
    val start = Math.floorMod(seed, candidates.size)
    return (candidates.drop(start) + candidates.take(start)).distinct().take(6)
}

@Composable
fun HomeScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val app = IptvApp.instance
    val context = LocalContext.current
    val collections by app.meta.collections.collectAsState()
    val known by app.meta.known.collectAsState()
    val metaLoading by app.meta.loading.collectAsState()
    val metaError by app.meta.lastError.collectAsState()
    val tmdbKey by app.store.tmdbKey.collectAsState()
    val progress by app.store.progress.collectAsState()
    val favorites by app.store.favorites.collectAsState()
    val episodeShow by app.store.episodeShow.collectAsState()
    val seed = remember { (System.currentTimeMillis() / 1000).toInt() }

    val resume = remember(progress, view, episodeShow) {
        val seen = HashSet<String>()
        progress.entries.filter { it.value.inProgress }
            .sortedByDescending { it.value.updatedAt }
            .mapNotNull { (url, p) ->
                view.movieFor(url)?.let { m -> if (seen.add(m.id)) Resume.M(m, p.fraction) else null }
                    ?: (view.showFor(url) ?: episodeShow[url]?.let { view.showById(it) })
                        ?.let { s -> if (seen.add(s.id)) Resume.S(s, p.fraction) else null }
            }.take(20)
    }
    val favMovies = remember(favorites, view) {
        favorites.filter { it.startsWith("m:") }.mapNotNull { view.movieById(it.removePrefix("m:")) }
    }
    val favShows = remember(favorites, view) {
        favorites.filter { it.startsWith("s:") }.mapNotNull { view.showById(it.removePrefix("s:")) }
    }
    val watchLater by app.store.watchLater.collectAsState()
    val later = remember(watchLater, view) {
        watchLater.mapNotNull { k ->
            if (k.startsWith("m:")) view.movieById(k.removePrefix("m:")) else view.showById(k.removePrefix("s:"))
        }
    }
    val featured = remember(collections.size, view, known.isNotEmpty()) { pickFeatured(collections, known, view, seed) }
    // With TMDB collections on screen only "Recently added" is extra; without them the
    // genre rows worked out from the catalogue itself fill the page.
    val movieRows = remember(view, collections.isEmpty()) {
        val smart = smartMovieRows(view, emptyList())
        val rows = if (collections.isEmpty()) smart.take(9) else smart.filter { it.name == "Recently added" }
        rows.ifEmpty { view.movieCategories.filter { it.name != LibraryView.ALL }.take(6) }
    }
    val showRows = remember(view, collections.isEmpty()) {
        val smart = smartShowRows(view, emptyList())
        val rows = if (collections.isEmpty()) smart.take(6) else smart.filter { it.name == "Recently updated" }
        rows.ifEmpty { view.showCategories.filter { it.name != LibraryView.ALL }.take(4) }
    }

    // "Because you watched…": recommendations for the two titles watched most recently.
    val watched = remember(progress, view, episodeShow) {
        val seen = HashSet<String>()
        progress.entries.sortedByDescending { it.value.updatedAt }.mapNotNull { (url, _) ->
            val m = view.movieFor(url)
            val sh = if (m == null) view.showFor(url) ?: episodeShow[url]?.let { view.showById(it) } else null
            when {
                m != null && seen.add(m.id) -> m
                sh != null && seen.add(sh.id) -> sh
                else -> null
            }
        }.take(2)
    }
    val watchedKey = watched.joinToString("|") { if (it is Movie) it.id else (it as Show).id }
    val because by produceState(emptyList<Pair<String, List<Any>>>(), watchedKey, view) {
        value = watched.map { x ->
            when (x) {
                is Movie -> x.title to app.meta.similarMovies(x)
                is Show -> x.name to app.meta.similarShows(x)
                else -> "" to emptyList<Any>()
            }
        }.filter { it.second.size >= 4 }
    }

    // "On now": what your favourite (or most recent) channels are showing.
    val recentLive by app.store.recentLive.collectAsState()
    val followAny by app.store.follow.collectAsState()
    val wantsLive = recentLive.isNotEmpty() || favorites.any { it.startsWith("l:") } || followAny.isNotEmpty()
    val liveGuide = if (wantsLive) rememberLiveGuide(view) else null
    val nowNext by app.epg.guide.collectAsState()
    val favChannels = remember(liveGuide, favorites) {
        liveGuide?.channels.orEmpty()
            .filter { ch -> "l:${ch.id}" in favorites || ch.versions.any { "l:${it.url}" in favorites } }.take(20)
    }
    val recentChannels = remember(liveGuide, recentLive) {
        val g = liveGuide ?: return@remember emptyList()
        recentLive.mapNotNull { g.channelOf(it) }.distinct().take(12)
    }
    val myChannels = favChannels.ifEmpty { recentChannels }

    // Teams and players you follow: channels that name them, and programmes that do.
    val follow by app.store.follow.collectAsState()
    val followedByName by produceState(emptyList<Pair<LiveChannel, String>>(), liveGuide, follow) {
        val g = liveGuide
        value = if (g == null || follow.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            followedByName(g, follow)
        }
    }
    val followed = remember(followedByName, nowNext, follow, liveGuide) {
        val g = liveGuide ?: return@remember emptyList()
        val merged = LinkedHashMap<String, Pair<LiveChannel, String>>()
        for (hit in followedByName) merged[hit.first.id] = hit
        for (hit in followedInGuide(g, follow, nowNext)) merged[hit.first.id] = hit
        // Spanish channels first; at most a row's worth.
        merged.values.sortedBy { if (it.first.region == "ES") 0 else 1 }.take(20)
    }
    LaunchedEffect(liveGuide, follow) {
        // Ask for the guide of the main Spanish sports channels, where those names show up.
        val g = liveGuide ?: return@LaunchedEffect
        if (follow.isEmpty()) return@LaunchedEffect
        g.channels.asSequence()
            .filter { it.region == "ES" && it.topic == LiveGuide.SPORTS && it.guideEntry != null }
            .take(40).forEach { app.epg.request(it) }
    }

    // Collections often overlap heavily (trending, popular, this year): show each title once.
    val collectionRows = remember(collections) {
        distinctRows(
            collections.map { c -> Category<Any>(c.def.title, c.movies + c.shows) },
            { if (it is Movie) "m" + it.id else "s" + (it as Show).id },
            shown = 30,
        )
    }

    val menu = LocalTitleMenu.current
    val pad = if (wide) 48.dp else 12.dp
    val landW = if (wide) 270.dp else 190.dp
    val portW = if (wide) 140.dp else 106.dp

    LazyColumn(Modifier.fillMaxSize(), state = nav.homeList, contentPadding = PaddingValues(bottom = 48.dp)) {
        item { Hero(featured, known, wide, nav) }

        if (resume.isNotEmpty()) item {
            PosterRow("Continue watching", resume, pad) { r ->
                when (r) {
                    is Resume.M -> LandscapeCard(
                        r.movie.title, known[r.movie.id]?.backdropUrl ?: r.movie.poster,
                        known[r.movie.id]?.vote ?: r.movie.rating ?: 0.0, r.progress, landW,
                        onMenu = menu?.let { { it(r.movie) } },
                    ) { Actions.playMovie(context, r.movie) }
                    is Resume.S -> LandscapeCard(
                        r.show.name, known[r.show.id]?.backdropUrl ?: r.show.poster,
                        known[r.show.id]?.vote ?: r.show.rating ?: 0.0, r.progress, landW,
                        onMenu = menu?.let { { it(r.show) } },
                    ) { nav.open(Route.ShowDetail(r.show.id)) }
                }
            }
        }
        if (myChannels.isNotEmpty()) item {
            PosterRow(if (favChannels.isNotEmpty()) "My channels" else "Recent channels", myChannels, pad) { ch ->
                OnNowCard(ch, nowNext[ch.id], null, landW) { Actions.playLive(context, myChannels, myChannels.indexOf(ch)) }
            }
        }
        if (followed.isNotEmpty()) item {
            val list = followed.map { it.first }
            PosterRow("Following", followed, pad) { (ch, note) ->
                OnNowCard(ch, nowNext[ch.id], note, landW) { Actions.playLive(context, list, list.indexOf(ch)) }
            }
        }
        if (later.isNotEmpty()) item {
            PosterRow("Watch later", later, pad) { x ->
                when (x) {
                    is Movie -> MovieCard(x, known[x.id], null, portW) { nav.open(Route.MovieDetail(x.id)) }
                    is Show -> ShowCard(x, known[x.id], portW) { nav.open(Route.ShowDetail(x.id)) }
                }
            }
        }
        if (favMovies.isNotEmpty()) item {
            PosterRow("My movies", favMovies, pad) { x ->
                MovieCard(x, known[x.id], null, portW) { nav.open(Route.MovieDetail(x.id)) }
            }
        }
        if (favShows.isNotEmpty()) item {
            PosterRow("My series", favShows, pad) { x ->
                ShowCard(x, known[x.id], portW) { nav.open(Route.ShowDetail(x.id)) }
            }
        }

        items(because) { (title, list) ->
            PosterRow("Because you watched $title", list, pad) { x ->
                when (x) {
                    is Movie -> MovieCard(x, known[x.id], null, portW) { nav.open(Route.MovieDetail(x.id)) }
                    is Show -> ShowCard(x, known[x.id], portW) { nav.open(Route.ShowDetail(x.id)) }
                }
            }
        }

        if (tmdbKey.isBlank()) item {
            Column(
                Modifier
                    .padding(horizontal = pad, vertical = 12.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A), Square)
                    .padding(18.dp)
            ) {
                Text(tr("Unlock collections & ratings"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    tr("Add a free TMDB key (and OMDb for Rotten Tomatoes / IMDb) in Settings to get rows like Trending, Best Picture winners or On Netflix, plus synopses and backdrops."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(collectionRows, key = { "col:" + it.name }) { row ->
            PosterRow(row.name, row.items.take(30), pad) { x ->
                when (x) {
                    is Movie -> LandscapeCard(
                        x.title, known[x.id]?.backdropUrl ?: x.poster, known[x.id]?.vote ?: x.rating ?: 0.0, null, landW,
                    ) { nav.open(Route.MovieDetail(x.id)) }
                    is Show -> LandscapeCard(
                        x.name, known[x.id]?.backdropUrl ?: x.poster, known[x.id]?.vote ?: x.rating ?: 0.0, null, landW,
                    ) { nav.open(Route.ShowDetail(x.id)) }
                }
            }
        }
        if (tmdbKey.isNotBlank() && metaLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = pad, vertical = 12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Finding your collections…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (tmdbKey.isNotBlank() && collections.isEmpty() && metaError != null) {
            item {
                Text(tr("TMDB: $metaError"), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = pad))
            }
        }

        items(movieRows, key = { "hm:" + it.name }) { cat ->
            PosterRow(
                cat.name, cat.items.take(20), pad,
                onSeeAll = if (cat.items.size > 20) ({ nav.openList(cat.name, cat.items) }) else null, seeAllWidth = portW,
            ) { m ->
                MovieCard(m, known[m.id], null, portW) { nav.open(Route.MovieDetail(m.id)) }
            }
        }
        items(showRows, key = { "hs:" + it.name }) { cat ->
            PosterRow(
                if (cat.name.startsWith("Recently")) "${cat.name} series" else "${cat.name} · Series", cat.items.take(20), pad,
                onSeeAll = if (cat.items.size > 20) ({ nav.openList(cat.name + " series", cat.items) }) else null, seeAllWidth = portW,
            ) { s ->
                ShowCard(s, known[s.id], portW) { nav.open(Route.ShowDetail(s.id)) }
            }
        }
    }
}

/** Sports and event channels whose own name mentions a followed team or player. */
private fun followedByName(guide: LiveGuide, follow: List<String>): List<Pair<LiveChannel, String>> {
    val words = follow.map { Library.normalize(it) }.filter { it.length >= 3 }
    val out = ArrayList<Pair<LiveChannel, String>>()
    for (ch in guide.channels) {
        if (!ch.isEvent && ch.topic != LiveGuide.SPORTS) continue
        if (ch.isEvent && !ch.announced) continue
        val name = Library.normalize(ch.name)
        if (words.any { name.contains(it) }) out += ch to ch.name
        if (out.size >= 40) break
    }
    return out
}

/** Channels with a programme, now or later, that mentions a followed team or player. */
private fun followedInGuide(guide: LiveGuide, follow: List<String>, epg: Map<String, NowNext>): List<Pair<LiveChannel, String>> {
    val words = follow.map { Library.normalize(it) }.filter { it.length >= 3 }
    val out = ArrayList<Pair<LiveChannel, String>>()
    val nowMs = System.currentTimeMillis()
    for ((id, nn) in epg) {
        val ch = guide.channelById(id) ?: continue
        val p = nn.items.firstOrNull { item -> Library.normalize(item.title).let { t -> words.any { t.contains(it) } } } ?: continue
        out += ch to (if (p.start <= nowMs) "Now  ${p.title}" else "${hhmm(p.start)}  ${p.title}")
    }
    return out
}

/** A channel with what it is showing right now and until when. */
@Composable
private fun OnNowCard(ch: LiveChannel, guide: NowNext?, note: String?, width: Dp, onClick: () -> Unit) {
    LaunchedEffect(ch.id) { IptvApp.instance.epg.request(ch) }
    Row(
        Modifier
            .width(width)
            .height(84.dp)
            .focusMemory("now|${ch.id}")
            .tvClickable(Square, 1.05f, onClick = onClick)
            .background(Color(0xFF181818))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp).background(Color(0xFF262626)), contentAlignment = Alignment.Center) {
            if (ch.logo != null) {
                AsyncImage(ch.logo, ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            val now = guide?.now
            Text(
                note?.let { tr(it) } ?: now?.title ?: guide?.next?.let { "${hhmm(it.start)}  ${it.title}" } ?: tr("Live"),
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFD0D0D0),
                style = MaterialTheme.typography.bodySmall,
            )
            if (now != null && note == null) {
                Text(tr("until ${hhmm(now.end)}"), color = Color(0xFF8A8A8A), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Hero(list: List<Featured>, known: Map<String, TmdbItem>, wide: Boolean, nav: Nav) {
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    var index by remember { mutableIntStateOf(0) }
    if (wide) {
        // Only on a fresh visit: when coming back, the card you left keeps the focus.
        LaunchedEffect(list.isNotEmpty()) {
            if (nav.focus.last[Tab.HOME.name] == null) {
                delay(500); runCatching { playFocus.requestFocus() }
            }
        }
    }
    LaunchedEffect(list.size) {
        index = 0
        while (list.size > 1) {
            delay(10_000)
            index = (index + 1) % list.size
        }
    }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Image(painterResource(R.drawable.logo_wordmark), "TUTV", Modifier.height(28.dp))
        }
        return
    }
    val current = list[index % list.size]
    val onPlay: () -> Unit = {
        when (current) {
            is Featured.M -> Actions.playMovie(context, current.movie)
            is Featured.S -> nav.open(Route.ShowDetail(current.show.id))
        }
    }
    val onInfo: () -> Unit = {
        when (current) {
            is Featured.M -> nav.open(Route.MovieDetail(current.movie.id))
            is Featured.S -> nav.open(Route.ShowDetail(current.show.id))
        }
    }
    val side = if (wide) 48.dp else 20.dp

    Box(if (wide) Modifier.fillMaxWidth().height(430.dp) else Modifier.fillMaxWidth().aspectRatio(0.72f)) {
        // Artwork and text fade from one title to the next; the buttons below stay put,
        // so the remote's focus is never lost.
        Crossfade(current, animationSpec = tween(900), label = "hero") { f ->
            val id = when (f) { is Featured.M -> f.movie.id; is Featured.S -> f.show.id }
            val meta = known[id]
            val title = when (f) { is Featured.M -> f.movie.title; is Featured.S -> f.show.name }
            val year = when (f) { is Featured.M -> f.movie.year; is Featured.S -> f.show.year } ?: meta?.year
            val langs = when (f) { is Featured.M -> f.movie.languages; is Featured.S -> f.show.languages }
            val vote = meta?.vote ?: when (f) { is Featured.M -> f.movie.rating; is Featured.S -> f.show.rating }
            val poster = when (f) { is Featured.M -> f.movie.poster; is Featured.S -> f.show.poster }
            Box(Modifier.fillMaxSize()) {
                if (wide) {
                    AsyncImage(
                        meta?.backdropLargeUrl ?: poster, title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight().align(Alignment.TopEnd),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(0f to Bg, 0.3f to Bg, 0.55f to Bg.copy(alpha = 0.6f), 0.85f to Color.Transparent)
                        )
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Bg)))
                } else {
                    AsyncImage(
                        meta?.posterUrl ?: poster ?: meta?.backdropLargeUrl, title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color(0x99000000), 0.25f to Color.Transparent, 0.45f to Color(0x33000000), 1f to Bg)
                        )
                    )
                }
                Column(
                    (if (wide) Modifier.align(Alignment.BottomStart).fillMaxWidth(0.45f) else Modifier.align(Alignment.BottomCenter))
                        .padding(start = side, end = side, bottom = if (wide) 116.dp else 84.dp),
                    horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        tr(if (f is Featured.S) "SERIES" else "FILM"),
                        color = Color(0xFFCCCCCC), fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                        style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (vote != null && vote > 0) RatingBadge(vote)
                        if (year != null) Text(year.toString(), color = Color(0xFFE0E0E0))
                        LanguagePills(langs)
                    }
                    val overview = meta?.overview
                    if (wide && !overview.isNullOrBlank()) {
                        Text(
                            overview, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFFE0E0E0),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        if (!wide) {
            Image(
                painterResource(R.drawable.logo_wordmark), "TUTV",
                Modifier.align(Alignment.TopStart).padding(16.dp).height(24.dp),
            )
        }
        Row(
            (if (wide) Modifier.align(Alignment.BottomStart) else Modifier.align(Alignment.BottomCenter))
                .padding(start = side, end = side, bottom = if (wide) 48.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton("Play", Icons.Filled.PlayArrow, true, Modifier.focusRequester(playFocus), onPlay)
            ActionButton("More info", Icons.Filled.Info, false, onClick = onInfo)
        }
        if (list.size > 1) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = side, bottom = if (wide) 60.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in list.indices) {
                    val on = i == index % list.size
                    Box(
                        Modifier.size(width = if (on) 18.dp else 6.dp, height = 3.dp)
                            .background(if (on) Color.White else Color(0x66FFFFFF), Square)
                    )
                }
            }
        }
    }
}
