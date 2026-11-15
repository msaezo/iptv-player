package es.msaezo.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.LibraryView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val app = IptvApp.instance
    val context = LocalContext.current
    val known by app.meta.known.collectAsState()
    val store = app.store
    val favorites by store.favorites.collectAsState()
    var results by remember { mutableStateOf<LibraryView.SearchResults?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { focus.requestFocus() } }
    LaunchedEffect(nav.searchQuery, view) {
        delay(250)
        val q = nav.searchQuery
        results = if (q.trim().length < 2) null else withContext(Dispatchers.Default) { view.search(q) }
    }
    val pad = if (wide) 32.dp else 12.dp
    val cardWidth = if (wide) 140.dp else 104.dp

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = nav.searchQuery,
                onValueChange = { nav.searchQuery = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search channels, movies, shows…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        val r = results
        if (r == null) {
            item {
                Text("Type at least 2 letters. Accents don't matter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (r.isEmpty) {
            item { Text("No results for “${nav.searchQuery}”") }
        } else {
            item {
                PosterRow("Movies", r.movies) { m ->
                    MovieCard(m, known[m.id], null, cardWidth) { nav.open(Route.MovieDetail(m.id)) }
                }
            }
            item {
                PosterRow("Series", r.shows) { s ->
                    ShowCard(s, known[s.id], cardWidth) { nav.open(Route.ShowDetail(s.id)) }
                }
            }
            if (r.live.isNotEmpty()) item { SectionTitle("Live TV") }
            itemsIndexed(r.live) { i, ch ->
                ChannelRow(i + 1, ch, "l:${ch.url}" in favorites,
                    onFavorite = { store.toggleFavorite("l:${ch.url}") },
                    onClick = { Actions.playLive(context, r.live, i) })
            }
        }
    }
}
