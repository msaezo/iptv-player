package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.LibraryView
import es.msaezo.iptv.data.Entry
import es.msaezo.iptv.data.MetaDetails
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.Show
import es.msaezo.iptv.data.Tags
import es.msaezo.iptv.player.PlayerActivity
import kotlinx.coroutines.delay

/**
 * Artwork behind a title's page, an optional muted preview over it, and a dark veil
 * so white text stays readable however bright the picture is.
 */
@Composable
private fun Backdrop(url: String?, preview: Entry?) {
    val previewOn by IptvApp.instance.store.preview.collectAsState()
    if (url == null && (preview == null || !previewOn)) return
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        if (url != null) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (preview != null && previewOn) {
            PreviewVideo(preview.url, preview.userAgent, preview.referrer, Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xD9000000), 0.55f to Color(0x80000000), 1f to Color(0x26000000))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x59000000), 0.5f to Color(0x80000000), 1f to Bg)
            )
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector?,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .tvClickable(Square, 1.06f, onClick)
            .background(
                if (primary) Color.White else Color(0x66808080),
                Square,
            )
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (primary) Color.Black else Color.White
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingsRow(details: MetaDetails?, fallbackVote: Double?) {
    val chips = ArrayList<Triple<String, String, Color>>()
    details?.ratings?.rottenTomatoes?.let { chips += Triple("Rotten Tomatoes", it, Tomato) }
    details?.ratings?.imdb?.let { chips += Triple("IMDb", it, Gold) }
    details?.ratings?.metacritic?.let { chips += Triple("Metacritic", it, Color(0xFF66CC33)) }
    val tmdb = details?.item?.vote ?: fallbackVote
    if (tmdb != null && tmdb > 0) chips += Triple("TMDB", "%.1f".format(tmdb), Color(0xFF8CCBFF))
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value, color) in chips) {
            Row(
                Modifier.background(Color(0xB3000000), Square)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

private fun metaLine(year: Int?, details: MetaDetails?, extra: String? = null): String =
    listOfNotNull(
        year?.toString() ?: details?.item?.year?.toString(),
        details?.runtime?.let { "${it / 60}h ${it % 60}m".removePrefix("0h ") },
        details?.seasons?.let { "$it season${if (it == 1) "" else "s"}" },
        extra,
        details?.genres?.takeIf { it.isNotEmpty() }?.take(3)?.joinToString(", "),
    ).joinToString("  ·  ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(id: String, view: LibraryView, nav: Nav, wide: Boolean) {
    val app = IptvApp.instance
    val context = LocalContext.current
    val movie = remember(id, view) { view.movieById(id) }
    if (movie == null) {
        CenterMessage("This title is no longer in your filtered playlist"); return
    }
    val known by app.meta.known.collectAsState()
    val favorites by app.store.favorites.collectAsState()
    val progress by app.store.progress.collectAsState()
    var details by remember(id) { mutableStateOf<MetaDetails?>(null) }
    LaunchedEffect(id) { details = app.meta.movieDetails(movie) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(id) { delay(250); runCatching { playFocus.requestFocus() } }

    val previewOn by app.store.preview.collectAsState()
    val similar by produceState(emptyList<Movie>(), id) { value = app.meta.similarMovies(movie) }
    val resume = remember(progress, movie) { Actions.resumeVersion(movie) }
    val meta = details?.item ?: known[movie.id]
    val fav = "m:${movie.id}" in favorites
    val pad = if (wide) 48.dp else 16.dp

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box {
                Backdrop(if (wide) meta?.backdropLargeUrl else meta?.backdropUrl, movie.versions.firstOrNull())
                Row(
                    Modifier.padding(start = pad, end = pad, top = if (meta?.backdrop != null || previewOn) (if (wide) 120.dp else 80.dp) else 16.dp),
                ) {
                    val poster = meta?.posterUrl ?: movie.poster
                    if (poster != null) {
                        AsyncImage(
                            poster, movie.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (wide) 220.dp else 110.dp).aspectRatio(2f / 3f)
                                .clip(Square),
                        )
                        Spacer(Modifier.width(if (wide) 32.dp else 16.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            movie.title, fontWeight = FontWeight.Bold, color = Color.White,
                            style = (if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
                                .copy(shadow = TitleShadow),
                        )
                        Text(metaLine(movie.year, details), color = Color(0xFFE6E6E6),
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = TitleShadow))
                        RatingsRow(details, meta?.vote)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionButton(
                                if (resume != null) "Resume ${PlayerActivity.formatTime(resume.second)}" else "Play",
                                Icons.Filled.PlayArrow, true, Modifier.focusRequester(playFocus),
                            ) { Actions.playMovie(context, movie) }
                            if (resume != null) {
                                ActionButton("Start over", Icons.Filled.Replay, false) {
                                    Actions.playMovie(context, movie, resume.first, fromStart = true)
                                }
                            }
                            ActionButton(
                                if (fav) "In my list" else "My list",
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, false,
                            ) { app.store.toggleFavorite("m:${movie.id}") }
                            details?.trailer?.let { key ->
                                ActionButton("Trailer", Icons.Filled.Movie, false) { openTrailer(context, key) }
                            }
                        }
                        val overview = details?.item?.overview?.takeIf { it.isNotBlank() } ?: meta?.overview
                        if (!overview.isNullOrBlank()) {
                            Text(overview, color = Color.White,
                                style = MaterialTheme.typography.bodyLarge.copy(shadow = TitleShadow),
                                maxLines = if (wide) 6 else 10, overflow = TextOverflow.Ellipsis)
                        }
                        Credits(details, "Director")
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = pad, vertical = 16.dp)) {
                SectionTitle("Versions (${movie.versions.size})")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (v in movie.versions) {
                        Column(
                            Modifier
                                .tvClickable(Square, 1.04f) { Actions.playMovie(context, movie, v) }
                                .background(Color(0xFF1C1C1C), Square)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                listOfNotNull(Tags.languageLabels[v.language] ?: v.language, v.quality).joinToString(" · "),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(v.group, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            progress[v.url]?.takeIf { it.inProgress }?.let {
                                Text("Watched to ${PlayerActivity.formatTime(it.position)}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (similar.isNotEmpty()) item {
            PosterRow("More like this", similar, pad) { m ->
                MovieCard(m, known[m.id], null, if (wide) 140.dp else 106.dp) { nav.open(Route.MovieDetail(m.id)) }
            }
        }
        item { TmdbAttribution(pad) }
    }
}

@Composable
fun ShowDetailScreen(id: String, view: LibraryView, nav: Nav, wide: Boolean) {
    val app = IptvApp.instance
    val base = remember(id, view) { view.showById(id) }
    if (base == null) {
        CenterMessage("This series is no longer in your filtered playlist"); return
    }
    if (!base.needsLoading) {
        ShowDetailContent(base, nav, wide); return
    }
    var loaded by remember(id, view) { mutableStateOf<Show?>(null) }
    var error by remember(id, view) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(id, view, attempt) {
        error = null
        try {
            loaded = app.playlists.loadEpisodes(base)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load episodes"
        }
    }
    val show = loaded
    when {
        show != null -> ShowDetailContent(show, nav, wide)
        error != null -> CenterMessage("${base.name}\n$error") {
            ActionButton("Retry", Icons.Filled.Replay, true) { attempt++ }
        }
        else -> CenterMessage("Loading ${base.name}…", loading = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShowDetailContent(show: Show, nav: Nav, wide: Boolean) {
    val app = IptvApp.instance
    val context = LocalContext.current
    val id = show.id
    val known by app.meta.known.collectAsState()
    val favorites by app.store.favorites.collectAsState()
    val progress by app.store.progress.collectAsState()
    val showLangs by app.store.showLanguage.collectAsState()
    var details by remember(id) { mutableStateOf<MetaDetails?>(null) }
    LaunchedEffect(id) { details = app.meta.showDetails(show) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(id) { delay(250); runCatching { playFocus.requestFocus() } }

    val language = remember(showLangs, show) { Actions.preferredLanguage(show) }
    val next = remember(progress, show, language) { Actions.nextEpisode(show, language) }
    val previewOn by app.store.preview.collectAsState()
    val firstEpisode = remember(show, language) {
        show.episodes.firstOrNull()?.let { es.msaezo.iptv.data.Library.bestVersion(it.versions, language) }
    }
    val similar by produceState(emptyList<Show>(), id) { value = app.meta.similarShows(show) }
    var seasonsOpen by remember(id) { mutableStateOf(false) }
    var season by remember(id) { mutableStateOf(next?.first?.season ?: show.seasons.first()) }
    val episodes = remember(show, season) { show.episodesOf(season) }
    val meta = details?.item ?: known[show.id]
    val fav = "s:${show.id}" in favorites
    val pad = if (wide) 48.dp else 16.dp

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box {
                Backdrop(if (wide) meta?.backdropLargeUrl else meta?.backdropUrl, firstEpisode)
                Row(Modifier.padding(start = pad, end = pad, top = if (meta?.backdrop != null || (previewOn && firstEpisode != null)) (if (wide) 120.dp else 80.dp) else 16.dp)) {
                    val poster = meta?.posterUrl ?: show.poster
                    if (poster != null) {
                        AsyncImage(
                            poster, show.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (wide) 200.dp else 100.dp).aspectRatio(2f / 3f)
                                .clip(Square),
                        )
                        Spacer(Modifier.width(if (wide) 32.dp else 16.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            show.name, fontWeight = FontWeight.Bold, color = Color.White,
                            style = (if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
                                .copy(shadow = TitleShadow),
                        )
                        Text(metaLine(null, details, "${show.episodes.size} episodes"), color = Color(0xFFE6E6E6),
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = TitleShadow))
                        RatingsRow(details, meta?.vote)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val target = next?.first ?: show.episodes.first()
                            val label = when {
                                next == null -> "Play ${target.label}"
                                next.second -> "Continue ${target.label}"
                                else -> "Next: ${target.label}"
                            }
                            ActionButton(label, Icons.Filled.PlayArrow, true, Modifier.focusRequester(playFocus)) {
                                Actions.playEpisode(context, show, target, language)
                            }
                            ActionButton(
                                if (fav) "In my list" else "My list",
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, false,
                            ) { app.store.toggleFavorite("s:${show.id}") }
                            details?.trailer?.let { key ->
                                ActionButton("Trailer", Icons.Filled.Movie, false) { openTrailer(context, key) }
                            }
                        }
                        val overview = details?.item?.overview?.takeIf { it.isNotBlank() } ?: meta?.overview
                        if (!overview.isNullOrBlank()) {
                            Text(overview, color = Color.White,
                                style = MaterialTheme.typography.bodyLarge.copy(shadow = TitleShadow),
                                maxLines = if (wide) 5 else 8, overflow = TextOverflow.Ellipsis)
                        }
                        Credits(details, "Created by")
                    }
                }
            }
        }
        if (show.languages.size > 1) item {
            Column(Modifier.padding(start = pad, end = pad, top = 16.dp)) {
                Text("Audio / version", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(show.languages) { l ->
                        Pill(Tags.languageLabels[l] ?: l, l == language) { app.store.setShowLanguage(show.id, l) }
                    }
                }
            }
        }
        if (show.seasons.size > 1) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = pad, vertical = 8.dp)
                        .width(if (wide) 320.dp else 240.dp)
                        .tvClickable(Square, 1.02f) { seasonsOpen = !seasonsOpen }
                        .background(Color(0xFF2A2A2A), Square)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Season $season", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(
                        "${episodes.size} episodes", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        if (seasonsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        null, Modifier.size(22.dp),
                    )
                }
            }
            if (seasonsOpen) items(show.seasons) { s ->
                Row(
                    Modifier
                        .padding(horizontal = pad)
                        .width(if (wide) 320.dp else 240.dp)
                        .tvClickable(Square, 1.02f) { season = s; seasonsOpen = false }
                        .background(if (s == season) Color(0xFF3A3A3A) else Color(0xFF1C1C1C), Square)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Season $s", Modifier.weight(1f), fontWeight = if (s == season) FontWeight.Bold else FontWeight.Normal)
                    Text(
                        "${show.episodesOf(s).size}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        itemsIndexed(episodes) { _, ep ->
            val version = es.msaezo.iptv.data.Library.bestVersion(ep.versions, language)!!
            val p = ep.versions.mapNotNull { progress[it.url] }.maxByOrNull { it.updatedAt }
            Row(
                Modifier
                    .padding(horizontal = pad, vertical = 4.dp)
                    .fillMaxWidth()
                    .tvClickable(Square, 1.015f) { Actions.playEpisode(context, show, ep, language) }
                    .background(Color(0xFF181818), Square)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ep.label, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                Column(Modifier.weight(1f)) {
                    Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val note = if (version.language != language) "Only in ${version.versionLabel}"
                    else version.versionLabel
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p != null && p.fraction > 0.01f) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { p.fraction },
                            modifier = Modifier.fillMaxWidth(0.6f).height(3.dp),
                            color = Brand,
                            trackColor = Color(0x33FFFFFF),
                        )
                    }
                }
                if (p?.finished == true) Text("✓", color = Color.White, fontSize = 18.sp)
            }
        }
        if (similar.isNotEmpty()) item {
            PosterRow("More like this", similar, pad) { x ->
                ShowCard(x, known[x.id], if (wide) 140.dp else 106.dp) { nav.open(Route.ShowDetail(x.id)) }
            }
        }
        item { TmdbAttribution(pad) }
    }
}

/** Director / creators and the first names of the cast, when TMDB knows them. */
@Composable
private fun Credits(details: MetaDetails?, directorLabel: String) {
    if (details == null || (details.directors.isEmpty() && details.cast.isEmpty())) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (details.directors.isNotEmpty()) CreditLine(directorLabel, details.directors.joinToString(", "))
        if (details.cast.isNotEmpty()) CreditLine("Cast", details.cast.joinToString(", "))
    }
}

@Composable
private fun CreditLine(label: String, names: String) {
    Row {
        Text("$label  ", color = Color(0xFFBDBDBD), style = MaterialTheme.typography.bodyMedium.copy(shadow = TitleShadow))
        Text(
            names, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = TitleShadow),
        )
    }
}

/** Opens a trailer in the YouTube app, or in the browser when that isn't installed. */
private fun openTrailer(context: android.content.Context, key: String) {
    Preview.stop()
    val app = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$key"))
    val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$key"))
    try {
        context.startActivity(app)
    } catch (e: Exception) {
        try {
            context.startActivity(web)
        } catch (e2: Exception) {
            android.widget.Toast.makeText(context, "No app on this device can open the trailer", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun CollectionScreen(key: String, nav: Nav, wide: Boolean) {
    val app = IptvApp.instance
    val collections by app.meta.collections.collectAsState()
    val known by app.meta.known.collectAsState()
    val c = collections.firstOrNull { it.def.key == key }
    if (c == null) {
        CenterMessage("Collection not available"); return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 150.dp else 104.dp),
        contentPadding = PaddingValues(if (wide) 32.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(c.def.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        items(c.movies, key = { "m" + it.id }) { m ->
            MovieCard(m, known[m.id], null, null) { nav.open(Route.MovieDetail(m.id)) }
        }
        items(c.shows, key = { "s" + it.id }) { s ->
            ShowCard(s, known[s.id], null) { nav.open(Route.ShowDetail(s.id)) }
        }
    }
}

@Composable
fun TmdbAttribution(pad: androidx.compose.ui.unit.Dp) {
    Text(
        "Metadata and images from TMDB. Ratings via OMDb. This product uses the TMDB API but is not endorsed or certified by TMDB.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = pad, vertical = 24.dp),
    )
}
