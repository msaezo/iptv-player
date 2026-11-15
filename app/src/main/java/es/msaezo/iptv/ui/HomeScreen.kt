package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.LibraryView
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.Show

private sealed interface Resume {
    data class M(val movie: Movie, val progress: Float) : Resume
    data class S(val show: Show, val progress: Float) : Resume
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
    val cardWidth = if (wide) 150.dp else 112.dp
    val pad = if (wide) 32.dp else 12.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 280.dp else 160.dp),
        state = nav.homeGrid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 10.dp),
    ) {
        full {
            Row(
                Modifier
                    .fillMaxWidth()
                    .tvClickable(RoundedCornerShape(50), 1.01f) { nav.select(Tab.SEARCH) }
                    .background(Color(0x22FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Search channels, movies, shows…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (resume.isNotEmpty()) full {
            PosterRow("Continue watching", resume) { r ->
                when (r) {
                    is Resume.M -> MovieCard(r.movie, known[r.movie.id], r.progress, cardWidth) {
                        Actions.playMovie(context, r.movie)
                    }
                    is Resume.S -> PosterCard(
                        r.show.name, "Continue", known[r.show.id]?.posterUrl ?: r.show.poster,
                        known[r.show.id]?.vote ?: r.show.rating ?: 0.0, r.show.languages, r.progress, cardWidth,
                    ) { nav.open(Route.ShowDetail(r.show.id)) }
                }
            }
        }
        if (favMovies.isNotEmpty()) full {
            PosterRow("My movies", favMovies) { m ->
                MovieCard(m, known[m.id], null, cardWidth) { nav.open(Route.MovieDetail(m.id)) }
            }
        }
        if (favShows.isNotEmpty()) full {
            PosterRow("My series", favShows) { s ->
                ShowCard(s, known[s.id], cardWidth) { nav.open(Route.ShowDetail(s.id)) }
            }
        }

        if (tmdbKey.isBlank()) {
            full {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x33B18CFF), RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Text("Unlock collections & ratings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add a free TMDB key (and optionally OMDb for Rotten Tomatoes / IMDb scores) in Settings " +
                            "to get Chillio-style collections like Trending, Best Picture winners or On Netflix.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            full {
                PosterRow("Movies", view.movies.take(40)) { m ->
                    MovieCard(m, known[m.id], null, cardWidth) { nav.open(Route.MovieDetail(m.id)) }
                }
            }
            full {
                PosterRow("Series", view.shows.take(40)) { s ->
                    ShowCard(s, known[s.id], cardWidth) { nav.open(Route.ShowDetail(s.id)) }
                }
            }
        } else {
            val movieCols = collections.filter { it.def.type == "movie" }
            val showCols = collections.filter { it.def.type == "tv" }
            if (movieCols.isNotEmpty()) {
                full { SectionTitle("Movies") }
                items(movieCols, key = { it.def.key }) { c ->
                    CollectionTile(c.def.title, c.size, c.backdrop) { nav.open(Route.CollectionRoute(c.def.key)) }
                }
            }
            if (showCols.isNotEmpty()) {
                full { SectionTitle("Series") }
                items(showCols, key = { it.def.key }) { c ->
                    CollectionTile(c.def.title, c.size, c.backdrop) { nav.open(Route.CollectionRoute(c.def.key)) }
                }
            }
            if (metaLoading) full {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Matching your playlist with TMDB collections…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (collections.isEmpty() && metaError != null) full {
                Text("TMDB error: $metaError", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
