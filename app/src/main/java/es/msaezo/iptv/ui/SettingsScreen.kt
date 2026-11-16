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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import es.msaezo.iptv.data.Transfer
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import es.msaezo.iptv.data.PlaylistRepository
import es.msaezo.iptv.data.SportsRepository
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
        Text(tr(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(tr(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Section("App language") {
                    val language by store.language.collectAsState()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((code, label) in listOf("en" to "English", "es" to "Español")) {
                            // Language names are shown in their own language, never translated.
                            Text(
                                label,
                                color = if (language == code) Color.Black else Color.White,
                                fontWeight = if (language == code) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .tvClickable(Square, 1.06f) { store.setLanguage(code); I18n.lang = code }
                                    .background(if (language == code) Color.White else Color(0x26FFFFFF), Square)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

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
                        label = { Text(tr("M3U URL")) },
                        placeholder = { Text(tr("http://provider.com/get.php?username=…&type=m3u_plus")) },
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
                        Text(tr("Refresh failed, still showing the saved catalogue: $refreshError"),
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
                            Text(tr(Tags.languageLabels[code] ?: code.uppercase()), Modifier.weight(1f))
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
                        value = tmdb, onValueChange = { tmdb = it }, label = { Text(tr("TMDB API key")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Hint("OMDb (free, 1000/day): omdbapi.com/apikey.aspx — adds Rotten Tomatoes, IMDb and Metacritic scores.")
                    OutlinedTextField(
                        value = omdb, onValueChange = { omdb = it }, label = { Text(tr("OMDb API key")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton("Save keys", null, true) { store.setTmdbKey(tmdb); store.setOmdbKey(omdb) }
                    if (metaError != null) Text(tr("Last error: $metaError"), color = MaterialTheme.colorScheme.error)
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
                            Text(tr("Preview on a title's page"))
                            Hint("Plays the opening minutes without sound behind the title. Uses your stream connection while it runs.")
                        }
                    }
                    val audioOffset by store.audioOffset.collectAsState()
                    Text(tr("Audio sync"), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton("−", null, false) { store.setAudioOffset(audioOffset - UserStore.AUDIO_OFFSET_STEP) }
                        Text(
                            when {
                                audioOffset == 0 -> "0 ms"
                                audioOffset > 0 -> tr("+$audioOffset ms  ·  sound later")
                                else -> tr("$audioOffset ms  ·  sound earlier")
                            },
                            fontWeight = FontWeight.Medium,
                        )
                        ActionButton("+", null, false) { store.setAudioOffset(audioOffset + UserStore.AUDIO_OFFSET_STEP) }
                        if (audioOffset != 0) ActionButton("Reset", null, false) { store.setAudioOffset(0) }
                    }
                    Hint("If lips and sound don't match: sound arriving late needs a negative value. While a film is playing, up / down on the remote changes it in 25 ms steps (limit ±500 ms).")
                    val liveBuffer by store.liveBuffer.collectAsState()
                    Text(tr("Live TV buffer"), fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((sec, label) in listOf(0 to "Fast start", 10 to "10 seconds", 20 to "20 seconds", 30 to "30 seconds")) {
                            Pill(label, liveBuffer == sec) { store.setLiveBuffer(sec) }
                        }
                    }
                    Hint("Loads this much ahead before a channel starts, so a busy server (a big match) is less likely to make it stop. The channel takes that long to appear and you watch that far behind live.")
                    Hint("Some providers only accept certain players. Change the user agent if streams refuse to play.")
                    OutlinedTextField(
                        value = ua, onValueChange = { ua = it }, label = { Text(tr("User agent")) },
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
                Section("Subtitles") {
                    val sub by store.subtitles.collectAsState()
                    // Sample, drawn the way the player draws them.
                    Box(
                        Modifier.fillMaxWidth().height(110.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF3A5A6A), Color(0xFF9A8C6A), Color(0xFF2A2A3A)))
                        ),
                    ) {
                        Text(
                            tr("This is how subtitles will look"),
                            color = Color.White,
                            fontFamily = if (sub.serif) SubtitleSerif else null,
                            fontSize = (15 * sub.size / 100f).sp,
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = TitleShadow),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = (110 * sub.position / 100f).dp.coerceAtMost(70.dp))
                                .background(
                                    when (sub.background) { 1 -> Color(0x601E1E1E); 2 -> Color(0xC8000000); else -> Color.Transparent },
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Text(tr("Font"), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Serif", sub.serif) { store.setSubtitles(sub.copy(serif = true)) }
                        Pill("Sans", !sub.serif) { store.setSubtitles(sub.copy(serif = false)) }
                    }
                    Text(tr("Size"), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton("−", null, false) { store.setSubtitles(sub.copy(size = sub.size - 10)) }
                        Text("${sub.size}%", fontWeight = FontWeight.Medium)
                        ActionButton("+", null, false) { store.setSubtitles(sub.copy(size = sub.size + 10)) }
                    }
                    Text(tr("Background"), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("None", sub.background == 0) { store.setSubtitles(sub.copy(background = 0)) }
                        Pill("Soft", sub.background == 1) { store.setSubtitles(sub.copy(background = 1)) }
                        Pill("Dark", sub.background == 2) { store.setSubtitles(sub.copy(background = 2)) }
                    }
                    Text(tr("Height on screen"), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton("Lower", null, false) { store.setSubtitles(sub.copy(position = sub.position - 1)) }
                        Text(tr("${sub.position}% from the bottom"), fontWeight = FontWeight.Medium)
                        ActionButton("Higher", null, false) { store.setSubtitles(sub.copy(position = sub.position + 1)) }
                    }
                    Hint("Applies from the next thing you play.")
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Following") {
                    val follow by store.follow.collectAsState()
                    var name by remember { mutableStateOf("") }
                    Hint("Teams and players to look out for. Home shows a Following row with channels and programmes that mention them. Select a name to remove it.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in follow) Pill("$f  ×", false) { store.setFollow(follow - f) }
                    }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text(tr("Team or player")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton("Add", null, true) {
                        if (name.trim().length >= 3) {
                            store.setFollow(follow + name.trim()); name = ""
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Sports agenda") {
                    val chosen by store.competitions.collectAsState()
                    val agendaStatus by app.sports.status.collectAsState()
                    LaunchedEffect(chosen) { app.sports.refreshIfStale() }
                    Hint("Home shows the next two days of these competitions. Selecting a fixture opens that sport's channels. The fixtures come from ESPN's public feed, which is free but unofficial, so it can stop working without notice.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in SportsRepository.ALL) {
                            Pill(c.name, c.id in chosen, liveIcon(c.sport)) {
                                store.setCompetitions(if (c.id in chosen) chosen - c.id else chosen + c.id)
                            }
                        }
                    }
                    if (agendaStatus.isNotBlank()) Hint(agendaStatus)
                }
            }
        }

        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Section("Other devices") {
                    var sharing by remember { mutableStateOf<String?>(null) }
                    var address by remember { mutableStateOf("") }
                    var code by remember { mutableStateOf("") }
                    var status by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()
                    DisposableEffect(Unit) { onDispose { Transfer.stop() } }
                    Hint("Copies everything (playlist, keys, lists, what you were watching, these settings) to TUTV on another device on the same Wi-Fi. It is a one-time copy, not a live sync.")
                    Text(tr("1 · On the device that has everything"), fontWeight = FontWeight.Bold)
                    val shared = sharing
                    if (shared == null) {
                        ActionButton("Share from this device", null, false) {
                            val pin = (1000..9999).random().toString()
                            val ip = Transfer.localAddress()
                            sharing = if (ip != null && Transfer.start(store.export(), pin)) "$ip|$pin" else "error"
                        }
                    } else if (shared == "error") {
                        Text(tr("Couldn't start sharing. Is this device on Wi-Fi?"), color = MaterialTheme.colorScheme.error)
                        ActionButton("Try again", null, false) { sharing = null }
                    } else {
                        Text(tr("Address   ${shared.substringBefore('|')}"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(tr("Code   ${shared.substringAfter('|')}"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Hint("Keep this screen open while the other device copies.")
                        ActionButton("Stop sharing", null, false) { Transfer.stop(); sharing = null }
                    }
                    Text(tr("2 · On the new device"), fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = address, onValueChange = { address = it }, label = { Text(tr("Address shown on the other device")) },
                        placeholder = { Text("192.168.1.34") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it }, label = { Text(tr("Code")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton("Copy to this device", null, true) {
                        if (address.isNotBlank() && code.isNotBlank()) {
                            status = "Copying…"
                            scope.launch {
                                status = try {
                                    val json = withContext(Dispatchers.IO) { Transfer.fetch(address, code) }
                                    withContext(Dispatchers.IO) { store.import(json) }
                                    restartApp(context)
                                    "Done. Restarting…"
                                } catch (e: Exception) {
                                    "Couldn't copy: ${e.message ?: "no answer"}. Check the address, the code, and that both are on the same Wi-Fi."
                                }
                            }
                        }
                    }
                    status?.let { Hint(it) }
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

/** Starts the app again from scratch, so freshly imported settings are read everywhere. */
private fun restartApp(context: android.content.Context) {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
