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
import es.msaezo.iptv.data.MetaDetails
import es.msaezo.iptv.data.Show
import es.msaezo.iptv.data.Tags
import es.msaezo.iptv.player.PlayerActivity
import kotlinx.coroutines.delay

@Composable
private fun Backdrop(url: String?) {
    if (url == null) return
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x66000000), Color(0xFF0B1116)))
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
            .tvClickable(RoundedCornerShape(50), 1.06f, onClick)
            .background(
                if (primary) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (primary) MaterialTheme.colorScheme.onPrimary else Color.White
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
    details?.ratings?.rottenTomatoes?.let { chips += Triple("🍅", it, Tomato) }
    details?.ratings?.imdb?.let { chips += Triple("IMDb", it, Gold) }
    details?.ratings?.metacritic?.let { chips += Triple("Metacritic", it, Color(0xFF66CC33)) }
    val tmdb = details?.item?.vote ?: fallbackVote
    if (tmdb != null && tmdb > 0) chips += Triple("TMDB", "%.1f".format(tmdb), Accent)
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value, color) in chips) {
            Row(
                Modifier.background(Color(0x33000000), RoundedCornerShape(8.dp))
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

    val resume = remember(progress, movie) { Actions.resumeVersion(movie) }
    val meta = details?.item ?: known[movie.id]
    val fav = "m:${movie.id}" in favorites
    val pad = if (wide) 48.dp else 16.dp

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box {
                Backdrop(if (wide) meta?.backdropLargeUrl else meta?.backdropUrl)
                Row(
                    Modifier.padding(start = pad, end = pad, top = if (meta?.backdrop != null) (if (wide) 120.dp else 80.dp) else 16.dp),
                ) {
                    val poster = meta?.posterUrl ?: movie.poster
                    if (poster != null) {
                        AsyncImage(
                            poster, movie.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (wide) 220.dp else 110.dp).aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(if (wide) 32.dp else 16.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            movie.title, fontWeight = FontWeight.Bold,
                            style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                        )
                        Text(metaLine(movie.year, details), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        }
                        val overview = details?.item?.overview?.takeIf { it.isNotBlank() } ?: meta?.overview
                        if (!overview.isNullOrBlank()) {
                            Text(overview, style = MaterialTheme.typography.bodyLarge, maxLines = if (wide) 6 else 10,
                                overflow = TextOverflow.Ellipsis)
                        }
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
                                .tvClickable(RoundedCornerShape(12.dp), 1.04f) { Actions.playMovie(context, movie, v) }
                                .background(Color(0x22FFFFFF), RoundedCornerShape(12.dp))
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
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
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
    var season by remember(id) { mutableStateOf(next?.first?.season ?: show.seasons.first()) }
    val episodes = remember(show, season) { show.episodesOf(season) }
    val meta = details?.item ?: known[show.id]
    val fav = "s:${show.id}" in favorites
    val pad = if (wide) 48.dp else 16.dp

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box {
                Backdrop(if (wide) meta?.backdropLargeUrl else meta?.backdropUrl)
                Row(Modifier.padding(start = pad, end = pad, top = if (meta?.backdrop != null) (if (wide) 120.dp else 80.dp) else 16.dp)) {
                    val poster = meta?.posterUrl ?: show.poster
                    if (poster != null) {
                        AsyncImage(
                            poster, show.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (wide) 200.dp else 100.dp).aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(if (wide) 32.dp else 16.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            show.name, fontWeight = FontWeight.Bold,
                            style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                        )
                        Text(metaLine(null, details, "${show.episodes.size} episodes"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        }
                        val overview = details?.item?.overview?.takeIf { it.isNotBlank() } ?: meta?.overview
                        if (!overview.isNullOrBlank()) {
                            Text(overview, style = MaterialTheme.typography.bodyLarge, maxLines = if (wide) 5 else 8,
                                overflow = TextOverflow.Ellipsis)
                        }
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
        if (show.seasons.size > 1) item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
            ) {
                items(show.seasons) { s -> Pill("Season $s", s == season) { season = s } }
            }
        }
        itemsIndexed(episodes) { _, ep ->
            val version = es.msaezo.iptv.data.Library.bestVersion(ep.versions, language)!!
            val p = ep.versions.mapNotNull { progress[it.url] }.maxByOrNull { it.updatedAt }
            Row(
                Modifier
                    .padding(horizontal = pad, vertical = 4.dp)
                    .fillMaxWidth()
                    .tvClickable(RoundedCornerShape(12.dp), 1.015f) { Actions.playEpisode(context, show, ep, language) }
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
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
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0x33FFFFFF),
                        )
                    }
                }
                if (p?.finished == true) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
            }
        }
        item { TmdbAttribution(pad) }
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
