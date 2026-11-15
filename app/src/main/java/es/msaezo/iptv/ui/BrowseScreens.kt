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
import es.msaezo.iptv.data.LibraryView

private const val ROW_ITEMS = 24

@Composable
fun MoviesScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val progress by IptvApp.instance.store.progress.collectAsState()
    val cats = remember(view) { view.movieCategories.filter { it.name != LibraryView.ALL } }
    val pad = if (wide) 48.dp else 12.dp
    val w = if (wide) 150.dp else 108.dp
    if (cats.isEmpty() && view.movies.isEmpty()) {
        CenterMessage("No movies with your current language filter"); return
    }
    LazyColumn(Modifier.fillMaxSize(), state = nav.movieRows, contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
        item { ScreenTitle("Movies", "${view.movies.size} titles", pad) }
        val rows = if (cats.isEmpty()) listOf(Category(LibraryView.ALL, view.movies)) else cats
        items(rows, key = { "m:" + it.name }) { cat ->
            PosterRow(
                cat.name, cat.items.take(ROW_ITEMS), pad,
                onSeeAll = if (cat.items.size > ROW_ITEMS) ({ nav.open(Route.CategoryRoute(false, cat.name)) }) else null,
                seeAllWidth = w,
            ) { m ->
                val p = m.versions.mapNotNull { progress[it.url] }.maxByOrNull { it.updatedAt }
                MovieCard(m, known[m.id], p?.takeIf { it.inProgress }?.fraction, w) { nav.open(Route.MovieDetail(m.id)) }
            }
        }
    }
}

@Composable
fun SeriesScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val cats = remember(view) { view.showCategories.filter { it.name != LibraryView.ALL } }
    val pad = if (wide) 48.dp else 12.dp
    val w = if (wide) 150.dp else 108.dp
    if (cats.isEmpty() && view.shows.isEmpty()) {
        CenterMessage("No series with your current language filter"); return
    }
    LazyColumn(Modifier.fillMaxSize(), state = nav.showRows, contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
        item { ScreenTitle("Series", "${view.shows.size} titles", pad) }
        val rows = if (cats.isEmpty()) listOf(Category(LibraryView.ALL, view.shows)) else cats
        items(rows, key = { "s:" + it.name }) { cat ->
            PosterRow(
                cat.name, cat.items.take(ROW_ITEMS), pad,
                onSeeAll = if (cat.items.size > ROW_ITEMS) ({ nav.open(Route.CategoryRoute(true, cat.name)) }) else null,
                seeAllWidth = w,
            ) { s -> ShowCard(s, known[s.id], w) { nav.open(Route.ShowDetail(s.id)) } }
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
                Text(ch.quality, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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
