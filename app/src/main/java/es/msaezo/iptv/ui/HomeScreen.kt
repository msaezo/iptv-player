package es.msaezo.iptv.ui

import androidx.compose.foundation.Image
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

private fun pickFeatured(
    collections: List<ResolvedCollection>,
    known: Map<String, TmdbItem>,
    view: LibraryView,
    seed: Int,
): Featured? {
    val candidates = ArrayList<Featured>()
    for (c in collections.take(4)) {
        c.movies.filter { known[it.id]?.backdrop != null }.take(4).forEach { candidates += Featured.M(it) }
        c.shows.filter { known[it.id]?.backdrop != null }.take(3).forEach { candidates += Featured.S(it) }
        if (candidates.size >= 8) break
    }
    if (candidates.isNotEmpty()) return candidates[Math.floorMod(seed, candidates.size)]
    val withPoster = view.movies.filter { it.poster != null }.take(20)
    if (withPoster.isNotEmpty()) return Featured.M(withPoster[Math.floorMod(seed, withPoster.size)])
    return view.shows.firstOrNull()?.let { Featured.S(it) }
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
    val movieRows = remember(view) { view.movieCategories.filter { it.name != LibraryView.ALL }.take(10) }
    val showRows = remember(view) { view.showCategories.filter { it.name != LibraryView.ALL }.take(6) }

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
                onSeeAll = { nav.open(Route.CategoryRoute(false, cat.name)) }, seeAllWidth = portW,
            ) { m ->
                MovieCard(m, known[m.id], null, portW) { nav.open(Route.MovieDetail(m.id)) }
            }
        }
        items(showRows, key = { "hs:" + it.name }) { cat ->
            PosterRow(
                cat.name, cat.items.take(20), pad,
                onSeeAll = { nav.open(Route.CategoryRoute(true, cat.name)) }, seeAllWidth = portW,
            ) { s ->
                ShowCard(s, known[s.id], portW) { nav.open(Route.ShowDetail(s.id)) }
            }
        }
    }
}

@Composable
private fun Hero(featured: Featured?, known: Map<String, TmdbItem>, wide: Boolean, nav: Nav) {
    val context = LocalContext.current
    val playFocus = remember { FocusRequester() }
    if (wide) {
        LaunchedEffect(featured != null) {
            delay(500); runCatching { playFocus.requestFocus() }
        }
    }
    if (featured == null) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Image(painterResource(R.drawable.logo_wordmark), "TUTV", Modifier.height(28.dp))
        }
        return
    }
    val id = when (featured) { is Featured.M -> featured.movie.id; is Featured.S -> featured.show.id }
    val meta = known[id]
    val title = when (featured) { is Featured.M -> featured.movie.title; is Featured.S -> featured.show.name }
    val year = when (featured) { is Featured.M -> featured.movie.year; is Featured.S -> featured.show.year } ?: meta?.year
    val langs = when (featured) { is Featured.M -> featured.movie.languages; is Featured.S -> featured.show.languages }
    val vote = meta?.vote ?: when (featured) { is Featured.M -> featured.movie.rating; is Featured.S -> featured.show.rating }
    val poster = when (featured) { is Featured.M -> featured.movie.poster; is Featured.S -> featured.show.poster }
    val onPlay: () -> Unit = {
        when (featured) {
            is Featured.M -> Actions.playMovie(context, featured.movie)
            is Featured.S -> nav.open(Route.ShowDetail(featured.show.id))
        }
    }
    val onInfo: () -> Unit = {
        when (featured) {
            is Featured.M -> nav.open(Route.MovieDetail(featured.movie.id))
            is Featured.S -> nav.open(Route.ShowDetail(featured.show.id))
        }
    }

    @Composable
    fun Info(mod: Modifier, align: Alignment.Horizontal) {
        Column(mod, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (featured is Featured.S) "S E R I E S" else "F I L M",
                color = Brand, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
            )
            Text(
                title, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (vote != null && vote > 0) RatingBadge(vote)
                if (year != null) Text(year.toString(), color = Color(0xFFDDDDDD))
                LanguagePills(langs)
            }
            val overview = meta?.overview
            if (!overview.isNullOrBlank()) {
                Text(
                    overview, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFFDDDDDD),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                ActionButton("Play", Icons.Filled.PlayArrow, true, Modifier.focusRequester(playFocus), onPlay)
                ActionButton("More info", Icons.Filled.Info, false, onClick = onInfo)
            }
        }
    }

    if (wide) {
        Box(Modifier.fillMaxWidth().height(500.dp)) {
            AsyncImage(
                meta?.backdropLargeUrl ?: poster, title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight().align(Alignment.TopEnd),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Bg, 0.3f to Bg, 0.55f to Bg.copy(alpha = 0.55f), 0.8f to Color.Transparent)
                )
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Bg)))
            Info(Modifier.align(Alignment.CenterStart).padding(start = 48.dp, top = 24.dp).fillMaxWidth(0.42f), Alignment.Start)
        }
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
            AsyncImage(
                meta?.posterUrl ?: poster ?: meta?.backdropLargeUrl, title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color(0x99000000), 0.25f to Color.Transparent, 0.55f to Color.Transparent, 1f to Bg)
                )
            )
            Image(
                painterResource(R.drawable.logo_wordmark), "TUTV",
                Modifier.align(Alignment.TopStart).padding(16.dp).height(24.dp),
            )
            Info(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp), Alignment.CenterHorizontally)
        }
    }
}
