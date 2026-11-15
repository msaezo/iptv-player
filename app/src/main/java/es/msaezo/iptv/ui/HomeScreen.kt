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
import es.msaezo.iptv.data.LibraryView
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
                    ) { Actions.playMovie(context, r.movie) }
                    is Resume.S -> LandscapeCard(
                        r.show.name, known[r.show.id]?.backdropUrl ?: r.show.poster,
                        known[r.show.id]?.vote ?: r.show.rating ?: 0.0, r.progress, landW,
                    ) { nav.open(Route.ShowDetail(r.show.id)) }
                }
            }
        }
        if (favMovies.isNotEmpty() || favShows.isNotEmpty()) item {
            val all: List<Any> = favMovies + favShows
            PosterRow("My list", all, pad) { x ->
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
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
                    .padding(18.dp)
            ) {
                Text("Unlock collections & ratings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Add a free TMDB key (and OMDb for Rotten Tomatoes / IMDb) in Settings to get rows like " +
                        "Trending, Best Picture winners or On Netflix, plus synopses and backdrops.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(collections, key = { it.def.key }) { c ->
            val list: List<Any> = c.movies + c.shows
            PosterRow(c.def.title, list.take(30), pad) { x ->
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
                    Text("Finding your collections…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (tmdbKey.isNotBlank() && collections.isEmpty() && metaError != null) {
            item {
                Text("TMDB: $metaError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = pad))
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

@Composable
private fun Hero(list: List<Featured>, known: Map<String, TmdbItem>, wide: Boolean, nav: Nav) {
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    var index by remember { mutableIntStateOf(0) }
    if (wide) {
        LaunchedEffect(list.isNotEmpty()) {
            delay(500); runCatching { playFocus.requestFocus() }
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

    Box(if (wide) Modifier.fillMaxWidth().height(500.dp) else Modifier.fillMaxWidth().aspectRatio(0.72f)) {
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
                        if (f is Featured.S) "SERIES" else "FILM",
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
                            .background(if (on) Color.White else Color(0x66FFFFFF), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
