package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.Show
import kotlinx.coroutines.delay

/** Options of a film or series, opened with the ☰ key (or a long press). */
@Composable
fun TitleMenu(item: Any, onDismiss: () -> Unit) {
    val store = IptvApp.instance.store
    val favorites by store.favorites.collectAsState()
    val later by store.watchLater.collectAsState()
    val progress by store.progress.collectAsState()
    val episodeShow by store.episodeShow.collectAsState()
    val (key, name) = when (item) {
        is Movie -> "m:${item.id}" to item.title
        is Show -> "s:${item.id}" to item.name
        else -> return
    }
    // Every stream of this title that has a position saved.
    val watched = remember(item, progress, episodeShow) {
        when (item) {
            is Movie -> item.versions.map { it.url }.filter { it in progress }
            is Show -> {
                val own = item.episodes.flatMapTo(HashSet()) { ep -> ep.versions.map { it.url } }
                progress.keys.filter { it in own || episodeShow[it] == item.id }
            }
            else -> emptyList()
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(380.dp).background(Color(0xFF161616), Square).padding(14.dp)) {
            Text(
                name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            var mod: Modifier = Modifier.focusRequester(first)
            if (watched.isNotEmpty()) {
                MenuRow("Remove from Continue watching", Icons.Filled.RemoveCircleOutline, mod) {
                    store.clearProgress(watched); onDismiss()
                }
                mod = Modifier
            }
            val fav = key in favorites
            MenuRow(
                if (fav) "Remove from favourites" else "Add to favourites",
                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, mod,
            ) { store.toggleFavorite(key); onDismiss() }
            val saved = key in later
            MenuRow(if (saved) "Remove from Watch later" else "Watch later", Icons.Filled.WatchLater, Modifier) {
                store.toggleWatchLater(key); onDismiss()
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().tvClickable(Square, 1.02f, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(tr(text), color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}
