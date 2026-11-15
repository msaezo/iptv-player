package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.Category
import es.msaezo.iptv.data.Entry
import es.msaezo.iptv.data.Collections
import es.msaezo.iptv.data.LibraryView
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.ResolvedCollection
import es.msaezo.iptv.data.Show
import es.msaezo.iptv.data.Tags

private const val ROW_ITEMS = 24

private fun <T> MutableList<Category<T>>.addRow(title: String, items: List<T>, min: Int = 3) {
    if (items.size >= min) add(Category(title, items))
}

/** Rows that organise the catalogue by what it is, not by which pack it came in. */
fun smartMovieRows(view: LibraryView, collections: List<ResolvedCollection>): List<Category<Movie>> {
    val cols = collections.associateBy { it.def.key }
    val rows = ArrayList<Category<Movie>>()
    rows.addRow("Recently added", view.recentMovies)
    val year = cols["m_year"]
    if (year != null) rows.addRow(year.def.title, year.movies) else rows.addRow("New in ${view.thisYear}", view.newMovies)
    for (k in listOf("m_trending", "m_cinema", "m_popular")) cols[k]?.let { rows.addRow(it.def.title, it.movies) }
    val byGenre = view.movieGenres.associate { it.name to it.items }
    for (g in Tags.genreOrder) {
        val fromTmdb = Collections.genreKeys[g].orEmpty().flatMap { cols[it]?.movies.orEmpty() }
        rows.addRow(g, (fromTmdb + byGenre[g].orEmpty()).distinctBy { it.id })
    }
    cols["m_top"]?.let { rows.addRow(it.def.title, it.movies) }
    rows.addRow("Top rated", view.topRatedMovies)
    for (k in listOf("m_oscars", "m_netflix", "m_prime", "m_disney", "m_max", "m_movistar", "m_apple")) {
        cols[k]?.let { rows.addRow(it.def.title, it.movies) }
    }
    return rows
}

fun smartShowRows(view: LibraryView, collections: List<ResolvedCollection>): List<Category<Show>> {
    val cols = collections.associateBy { it.def.key }
    val rows = ArrayList<Category<Show>>()
    rows.addRow("Recently updated", view.recentShows)
    for (k in listOf("t_year", "t_trending", "t_popular")) cols[k]?.let { rows.addRow(it.def.title, it.shows) }
    val byGenre = view.showGenres.associate { it.name to it.items }
    for (g in Tags.genreOrder) {
        val fromTmdb = Collections.genreKeys[g].orEmpty().flatMap { cols[it]?.shows.orEmpty() }
        rows.addRow(g, (fromTmdb + byGenre[g].orEmpty()).distinctBy { it.id })
    }
    cols["t_top"]?.let { rows.addRow(it.def.title, it.shows) }
    rows.addRow("Top rated", view.topRatedShows)
    for (k in listOf("t_netflix", "t_prime", "t_disney", "t_max", "t_movistar", "t_apple")) {
        cols[k]?.let { rows.addRow(it.def.title, it.shows) }
    }
    return rows
}

@Composable
private fun PackHeader(pad: androidx.compose.ui.unit.Dp) {
    Text(
        "FROM YOUR PROVIDER", color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = pad, top = 28.dp),
    )
}

@Composable
fun MoviesScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val collections by IptvApp.instance.meta.collections.collectAsState()
    val progress by IptvApp.instance.store.progress.collectAsState()
    val smart = remember(view, collections) { smartMovieRows(view, collections) }
    val packs = remember(view) { view.movieCategories.filter { it.name != LibraryView.ALL } }
    val pad = if (wide) 48.dp else 12.dp
    val w = if (wide) 150.dp else 108.dp
    if (view.movies.isEmpty()) {
        CenterMessage("No movies with your current language filter"); return
    }
    LazyColumn(Modifier.fillMaxSize(), state = nav.movieRows, contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
        item { ScreenTitle("Movies", "${view.movies.size} titles", pad) }
        items(smart, key = { "g:" + it.name }) { row ->
            PosterRow(
                row.name, row.items.take(ROW_ITEMS), pad,
                onSeeAll = if (row.items.size > ROW_ITEMS) ({ nav.openList(row.name, row.items) }) else null,
                seeAllWidth = w,
            ) { m ->
                val p = m.versions.mapNotNull { progress[it.url] }.maxByOrNull { it.updatedAt }
                MovieCard(m, known[m.id], p?.takeIf { it.inProgress }?.fraction, w) { nav.open(Route.MovieDetail(m.id)) }
            }
        }
        if (packs.isNotEmpty()) item { PackHeader(pad) }
        items(packs, key = { "m:" + it.name }) { cat ->
            PosterRow(
                cat.name, cat.items.take(ROW_ITEMS), pad,
                onSeeAll = if (cat.items.size > ROW_ITEMS) ({ nav.open(Route.CategoryRoute(false, cat.name)) }) else null,
                seeAllWidth = w,
            ) { m -> MovieCard(m, known[m.id], null, w) { nav.open(Route.MovieDetail(m.id)) } }
        }
    }
}

@Composable
fun SeriesScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val collections by IptvApp.instance.meta.collections.collectAsState()
    val smart = remember(view, collections) { smartShowRows(view, collections) }
    val packs = remember(view) { view.showCategories.filter { it.name != LibraryView.ALL } }
    val pad = if (wide) 48.dp else 12.dp
    val w = if (wide) 150.dp else 108.dp
    if (view.shows.isEmpty()) {
        CenterMessage("No series with your current language filter"); return
    }
    LazyColumn(Modifier.fillMaxSize(), state = nav.showRows, contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
        item { ScreenTitle("Series", "${view.shows.size} titles", pad) }
        items(smart, key = { "g:" + it.name }) { row ->
            PosterRow(
                row.name, row.items.take(ROW_ITEMS), pad,
                onSeeAll = if (row.items.size > ROW_ITEMS) ({ nav.openList(row.name, row.items) }) else null,
                seeAllWidth = w,
            ) { s -> ShowCard(s, known[s.id], w) { nav.open(Route.ShowDetail(s.id)) } }
        }
        if (packs.isNotEmpty()) item { PackHeader(pad) }
        items(packs, key = { "s:" + it.name }) { cat ->
            PosterRow(
                cat.name, cat.items.take(ROW_ITEMS), pad,
                onSeeAll = if (cat.items.size > ROW_ITEMS) ({ nav.open(Route.CategoryRoute(true, cat.name)) }) else null,
                seeAllWidth = w,
            ) { s -> ShowCard(s, known[s.id], w) { nav.open(Route.ShowDetail(s.id)) } }
        }
    }
}

/** Full grid for a smart row ("See all"). */
@Composable
fun ItemsScreen(route: Route.ItemsRoute, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val list = nav.lists[route.title].orEmpty()
    val pad = if (wide) 48.dp else 12.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 150.dp else 104.dp),
        contentPadding = PaddingValues(pad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(route.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        items(list) { x ->
            when (x) {
                is Movie -> MovieCard(x, known[x.id], null, null) { nav.open(Route.MovieDetail(x.id)) }
                is Show -> ShowCard(x, known[x.id], null) { nav.open(Route.ShowDetail(x.id)) }
            }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String?, pad: androidx.compose.ui.unit.Dp) {
    Row(Modifier.padding(start = pad, end = pad, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.width(12.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/** Full grid for one movie or series category ("See all"). */
@Composable
fun CategoryScreen(route: Route.CategoryRoute, view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val pad = if (wide) 48.dp else 12.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 150.dp else 104.dp),
        contentPadding = PaddingValues(pad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(route.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if (route.series) {
            val list = view.showCategories.firstOrNull { it.name == route.name }?.items.orEmpty()
            items(list, key = { it.id }) { s -> ShowCard(s, known[s.id], null) { nav.open(Route.ShowDetail(s.id)) } }
        } else {
            val list = view.movieCategories.firstOrNull { it.name == route.name }?.items.orEmpty()
            items(list, key = { it.id }) { m -> MovieCard(m, known[m.id], null, null) { nav.open(Route.MovieDetail(m.id)) } }
        }
    }
}

@Composable
fun LiveScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val store = IptvApp.instance.store
    val favorites by store.favorites.collectAsState()
    val recent by store.recentLive.collectAsState()
    val categories = remember(view, favorites, recent) {
        val extra = ArrayList<Category<Entry>>()
        val favs = view.live.filter { "l:${it.url}" in favorites }.distinctBy { it.url }
        if (favs.isNotEmpty()) extra += Category("★ Favorites", favs)
        val byUrl = view.live.associateBy { it.url }
        val rec = recent.mapNotNull { byUrl[it] }
        if (rec.isNotEmpty()) extra += Category("Recently watched", rec)
        extra + view.liveCategories
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    CategoryLayout(categories, nav.liveCategory, {
        nav.liveCategory = it; scope.launch { nav.liveList.scrollToItem(0) }
    }, wide) { cat ->
        LazyColumn(
            state = nav.liveList,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(cat.items) { i, ch ->
                ChannelRow(i + 1, ch, "l:${ch.url}" in favorites,
                    onFavorite = { store.toggleFavorite("l:${ch.url}") },
                    onClick = { Actions.playLive(context, cat.items, i) })
            }
        }
    }
}

@Composable
fun ChannelRow(number: Int, ch: Entry, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .tvClickable(RoundedCornerShape(10.dp), 1.02f, onClick)
                .background(Color(0xFF181818), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$number", modifier = Modifier.width(44.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                if (ch.logo != null) {
                    AsyncImage(ch.logo, ch.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(ch.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            if (ch.quality != null) {
                Text(ch.quality, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.tvClickable(RoundedCornerShape(50), 1.1f, onFavorite).padding(10.dp),
        ) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "Favorite",
                tint = if (favorite) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
