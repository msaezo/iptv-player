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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.PlaylistRepository
import es.msaezo.iptv.data.Tags
import es.msaezo.iptv.data.UserStore

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF161616), Square)
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
    val refreshing by app.playlists.refreshing.collectAsState()
    val refreshError by app.playlists.refreshError.collectAsState()
    val lastUpdated by app.playlists.lastUpdated.collectAsState()

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

    val location by app.net.location.collectAsState()
    val checking by app.net.checking.collectAsState()
    val netError by app.net.error.collectAsState()
    val preview by store.preview.collectAsState()
    LaunchedEffect(Unit) { app.net.refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (wide) 48.dp else 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Your connection") {
                    val loc = location
                    when {
                        loc != null -> {
                            Text(
                                listOfNotNull(loc.country, loc.city).joinToString(" · "),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            )
                            Hint(listOfNotNull("IP ${loc.ip}", loc.isp).joinToString("  ·  "))
                        }
                        checking -> Hint("Checking where you appear to be…")
                        else -> Hint("Couldn't check your location${netError?.let { ": $it" } ?: ""}")
                    }
                    Hint("This is where websites and your provider see you. With the VPN on it should show the VPN's country.")
                    ActionButton(if (checking) "Checking…" else "Check again", null, false) { app.net.refresh() }
                }
            }
        }

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
                        ActionButton("Save & load", null, true) { if (url.isNotBlank()) app.playlists.setPlaylist(url) }
                        ActionButton("Refresh now", null, false) { if (savedUrl.isNotBlank()) app.playlists.load(forceDownload = true) }
                        ActionButton("Open .m3u file", null, false) { picker.launch(arrayOf("*/*")) }
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
                    if (refreshing != null) {
                        Hint("Refreshing in the background: $refreshing  (you can keep using the app)")
                    } else if (refreshError != null) {
                        Text("Refresh failed, still showing the saved catalogue: $refreshError",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else if (lastUpdated > 0) {
                        val h = (System.currentTimeMillis() - lastUpdated) / 3_600_000L
                        Hint(
                            "Catalogue saved on this device, updated " +
                                (if (h < 1) "less than an hour ago" else if (h < 48) "$h h ago" else "${h / 24} days ago") +
                                ". It refreshes by itself once a day."
                        )
                    }
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
                                .tvClickable(Square, 1.01f) {
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
                    ActionButton("Save keys", null, true) { store.setTmdbKey(tmdb); store.setOmdbKey(omdb) }
                    if (metaError != null) Text("Last error: $metaError", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Playback") {
                    Row(
                        Modifier.fillMaxWidth().tvClickable(Square, 1.01f) { store.setPreview(!preview) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = preview, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text("Preview on a title's page")
                            Hint("Plays the opening minutes without sound behind the title. Uses your stream connection while it runs.")
                        }
                    }
                    val audioOffset by store.audioOffset.collectAsState()
                    Text("Audio sync", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton("−", null, false) { store.setAudioOffset(audioOffset - UserStore.AUDIO_OFFSET_STEP) }
                        Text(
                            when {
                                audioOffset == 0 -> "0 ms"
                                audioOffset > 0 -> "+$audioOffset ms  ·  sound later"
                                else -> "$audioOffset ms  ·  sound earlier"
                            },
                            fontWeight = FontWeight.Medium,
                        )
                        ActionButton("+", null, false) { store.setAudioOffset(audioOffset + UserStore.AUDIO_OFFSET_STEP) }
                        if (audioOffset != 0) ActionButton("Reset", null, false) { store.setAudioOffset(0) }
                    }
                    Hint("If lips and sound don't match: sound arriving late needs a negative value. While a film is playing, up / down on the remote changes it in 25 ms steps (limit ±500 ms).")
                    Hint("Some providers only accept certain players. Change the user agent if streams refuse to play.")
                    OutlinedTextField(
                        value = ua, onValueChange = { ua = it }, label = { Text("User agent") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton("Save", null, true) { store.setUserAgent(ua) }
                        ActionButton("Clear watch history", null, false) { store.clearHistory() }
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
