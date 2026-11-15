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

@Composable
fun MoviesScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val progress by IptvApp.instance.store.progress.collectAsState()
    val scope = rememberCoroutineScope()
    CategoryLayout(view.movieCategories, nav.movieCategory, {
        nav.movieCategory = it; scope.launch { nav.movieGrid.scrollToItem(0) }
    }, wide) { cat ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (wide) 140.dp else 104.dp),
            state = nav.movieGrid,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cat.items, key = { it.id }) { m ->
                val p = m.versions.mapNotNull { progress[it.url] }.maxByOrNull { it.updatedAt }
                MovieCard(m, known[m.id], p?.takeIf { it.inProgress }?.fraction, null) {
                    nav.open(Route.MovieDetail(m.id))
                }
            }
        }
    }
}

@Composable
fun SeriesScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val scope = rememberCoroutineScope()
    CategoryLayout(view.showCategories, nav.showCategory, {
        nav.showCategory = it; scope.launch { nav.showGrid.scrollToItem(0) }
    }, wide) { cat ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (wide) 140.dp else 104.dp),
            state = nav.showGrid,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cat.items, key = { it.id }) { s ->
                ShowCard(s, known[s.id], null) { nav.open(Route.ShowDetail(s.id)) }
            }
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
                .background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
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
