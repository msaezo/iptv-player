package es.msaezo.iptv.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.data.PlaylistRepository
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IptvTheme { MainScreen() } }
    }
}

@Composable
fun MainScreen() {
    val app = IptvApp.instance
    val context = LocalContext.current
    val nav = remember { Nav() }
    val config = LocalConfiguration.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val wide = isTv || config.screenWidthDp >= 840
    val state by app.playlists.state.collectAsState()

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Tab.HOME) { nav.back() }

    if (wide) {
        Column(Modifier.fillMaxSize()) {
            TopNav(nav, focusOnStart = isTv)
            Box(Modifier.weight(1f).fillMaxWidth()) { Content(nav, wide, state) }
        }
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xF20B1116)) {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = nav.tab == t && nav.stack.isEmpty(),
                            onClick = { nav.select(t) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, maxLines = 1) },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) { Content(nav, wide, state) }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..5 -> "Hey night-owl"
    in 6..11 -> "Good morning"
    in 12..19 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun TopNav(nav: Nav, focusOnStart: Boolean) {
    val homeFocus = remember { FocusRequester() }
    if (focusOnStart) {
        LaunchedEffect(Unit) {
            delay(400)
            runCatching { homeFocus.requestFocus() }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(220.dp)) {
            Text(greeting(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("IPTV Player", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .background(Color(0x22FFFFFF), RoundedCornerShape(50))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (t in Tab.entries) {
                val selected = nav.tab == t
                Row(
                    Modifier
                        .then(if (t == Tab.HOME) Modifier.focusRequester(homeFocus) else Modifier)
                        .tvClickable(RoundedCornerShape(50), 1.06f) { nav.select(t) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, null, Modifier.size(18.dp))
                    if (t != Tab.SEARCH && t != Tab.SETTINGS) {
                        Spacer(Modifier.width(6.dp))
                        Text(t.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(220.dp))
    }
}

@Composable
private fun Content(nav: Nav, wide: Boolean, state: PlaylistRepository.State) {
    val app = IptvApp.instance
    if (nav.tab == Tab.SETTINGS && nav.stack.isEmpty()) {
        SettingsScreen(wide)
        return
    }
    when (state) {
        is PlaylistRepository.State.NoPlaylist -> CenterMessage("Add your M3U playlist to get started") {
            Button(onClick = { nav.select(Tab.SETTINGS) }) { Text("Open Settings") }
        }
        is PlaylistRepository.State.Loading -> CenterMessage(state.message, loading = true)
        is PlaylistRepository.State.Error -> CenterMessage("Couldn't load the playlist:\n${state.message}") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { app.playlists.load(forceDownload = true) }) { Text("Retry") }
                OutlinedButton(onClick = { nav.select(Tab.SETTINGS) }) { Text("Settings") }
            }
        }
        is PlaylistRepository.State.Ready -> {
            val view by app.playlists.view.collectAsState()
            val v = view ?: return CenterMessage("Preparing…", loading = true)
            val route = nav.stack.lastOrNull()
            when (route) {
                is Route.MovieDetail -> MovieDetailScreen(route.id, v, nav, wide)
                is Route.ShowDetail -> ShowDetailScreen(route.id, v, nav, wide)
                is Route.CollectionRoute -> CollectionScreen(route.key, nav, wide)
                null -> when (nav.tab) {
                    Tab.HOME -> HomeScreen(v, nav, wide)
                    Tab.MOVIES -> MoviesScreen(v, nav, wide)
                    Tab.SERIES -> SeriesScreen(v, nav, wide)
                    Tab.LIVE -> LiveScreen(v, nav, wide)
                    Tab.SEARCH -> SearchScreen(v, nav, wide)
                    Tab.SETTINGS -> SettingsScreen(wide)
                }
            }
        }
    }
}
