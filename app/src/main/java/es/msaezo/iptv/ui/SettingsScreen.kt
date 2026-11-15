package es.msaezo.iptv.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.PlaylistRepository
import es.msaezo.iptv.data.Tags

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(wide: Boolean) {
    val app = IptvApp.instance
    val store = app.store
    val context = LocalContext.current
    val savedUrl by store.playlistUrl.collectAsState()
    val state by app.playlists.state.collectAsState()
    val view by app.playlists.view.collectAsState()
    val languages by store.languages.collectAsState()
    val tmdbSaved by store.tmdbKey.collectAsState()
    val omdbSaved by store.omdbKey.collectAsState()
    val uaSaved by store.userAgent.collectAsState()
    val metaError by app.meta.lastError.collectAsState()

    var url by remember(savedUrl) { mutableStateOf(if (savedUrl.startsWith("content://")) "" else savedUrl) }
    var tmdb by remember(tmdbSaved) { mutableStateOf(tmdbSaved) }
    var omdb by remember(omdbSaved) { mutableStateOf(omdbSaved) }
    var ua by remember(uaSaved) { mutableStateOf(uaSaved) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.playlists.setPlaylist(uri.toString())
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (wide) 48.dp else 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Playlist (M3U)") {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("M3U URL") },
                        placeholder = { Text("http://provider.com/get.php?username=…&type=m3u_plus") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (url.isNotBlank()) app.playlists.setPlaylist(url) }) { Text("Save & load") }
                        OutlinedButton(onClick = { app.playlists.load(forceDownload = true) }, enabled = savedUrl.isNotBlank()) {
                            Text("Refresh now")
                        }
                        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Open .m3u file") }
                    }
                    val status = when (val s = state) {
                        is PlaylistRepository.State.NoPlaylist -> "No playlist yet."
                        is PlaylistRepository.State.Loading -> s.message
                        is PlaylistRepository.State.Error -> "Error: ${s.message}"
                        is PlaylistRepository.State.Ready -> {
                            val v = view
                            "Loaded ${s.library.totalCount} streams" + if (v != null)
                                " → ${v.live.size} channels, ${v.movies.size} movies, ${v.shows.size} series (after filter)" else ""
                        }
                    }
                    Hint(status)
                    if (savedUrl.startsWith("content://")) Hint("Using a local file.")
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Languages for Movies & Series") {
                    Hint("Live TV is never filtered. Spanish channels are listed first, then UK, then US.")
                    val counts = (state as? PlaylistRepository.State.Ready)?.library?.languageCounts ?: emptyMap()
                    val codes = (counts.keys + listOf("es", "en", "multi", "lat", "vose", Tags.UNKNOWN)).distinct()
                    for (code in codes) {
                        val checked = code in languages
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .tvClickable(RoundedCornerShape(10.dp), 1.01f) {
                                    store.setLanguages(if (checked) languages - code else languages + code)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(Tags.languageLabels[code] ?: code.uppercase(), Modifier.weight(1f))
                            counts[code]?.let { Hint("$it") }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Collections & ratings") {
                    Hint("TMDB (free): themoviedb.org → Settings → API → request a key. Paste the API key or the read access token.")
                    OutlinedTextField(
                        value = tmdb, onValueChange = { tmdb = it }, label = { Text("TMDB API key") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Hint("OMDb (free, 1000/day): omdbapi.com/apikey.aspx — adds Rotten Tomatoes, IMDb and Metacritic scores.")
                    OutlinedTextField(
                        value = omdb, onValueChange = { omdb = it }, label = { Text("OMDb API key") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { store.setTmdbKey(tmdb); store.setOmdbKey(omdb) }) { Text("Save keys") }
                    if (metaError != null) Text("Last error: $metaError", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Playback") {
                    Hint("Some providers only accept certain players. Change the user agent if streams refuse to play.")
                    OutlinedTextField(
                        value = ua, onValueChange = { ua = it }, label = { Text("User agent") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { store.setUserAgent(ua) }) { Text("Save") }
                        OutlinedButton(onClick = { store.clearHistory() }) { Text("Clear watch history") }
                    }
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                TmdbAttribution(0.dp)
            }
        }
    }
}
