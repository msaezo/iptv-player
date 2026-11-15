package es.msaezo.iptv.ui

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateDpAsState
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.R
import es.msaezo.iptv.data.PlaylistRepository
import kotlinx.coroutines.delay

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
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(start = RAIL_WIDTH)) { Content(nav, wide, state) }
            SideRail(nav, focusOnStart = isTv)
        }
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFA0A0A0A), tonalElevation = 0.dp) {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = nav.tab == t && nav.stack.isEmpty(),
                            onClick = { nav.select(t) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, maxLines = 1) },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color(0xFF8C8C8C),
                                unselectedTextColor = Color(0xFF8C8C8C),
                                indicatorColor = Color.Transparent,
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

private val RAIL_WIDTH = 84.dp

/** TV side menu: icons only; expands with labels over the content while focused. */
@Composable
private fun SideRail(nav: Nav, focusOnStart: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 250.dp else RAIL_WIDTH, label = "rail")
    val homeFocus = remember { FocusRequester() }
    if (focusOnStart) {
        // Give the menu focus first; the home hero then moves it to its Play button.
        LaunchedEffect(Unit) {
            delay(300)
            runCatching { homeFocus.requestFocus() }
        }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                if (expanded) Brush.horizontalGradient(listOf(Color(0xF5000000), Color(0xE6000000), Color(0x00000000)))
                else Brush.horizontalGradient(listOf(Color(0xFF000000), Color(0xCC000000)))
            )
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.padding(start = 22.dp, bottom = 28.dp).height(40.dp), contentAlignment = Alignment.CenterStart) {
            if (expanded) {
                Image(painterResource(R.drawable.logo_wordmark), "TUTV", Modifier.height(26.dp))
            } else {
                Image(painterResource(R.mipmap.ic_launcher), "TUTV", Modifier.size(40.dp))
            }
        }
        val order = listOf(Tab.SEARCH, Tab.HOME, Tab.MOVIES, Tab.SERIES, Tab.LIVE)
        for (t in order) RailItem(t, nav, expanded, if (t == Tab.HOME) homeFocus else null)
        Spacer(Modifier.weight(1f))
        RailItem(Tab.SETTINGS, nav, expanded, null)
    }
}

@Composable
private fun RailItem(t: Tab, nav: Nav, expanded: Boolean, focus: FocusRequester?) {
    val selected = nav.tab == t
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .tvClickable(RoundedCornerShape(8.dp), 1.03f) { nav.select(t) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            t.icon, t.label, Modifier.size(26.dp),
            tint = if (selected) Color.White else Color(0xFF9A9A9A),
        )
        if (expanded) {
            Spacer(Modifier.width(18.dp))
            Text(
                t.label,
                color = if (selected) Color.White else Color(0xFFBDBDBD),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (selected) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 4.dp, height = 22.dp).background(Brand, RoundedCornerShape(2.dp)))
        }
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
            Button(modifier = Modifier.focusRing(), onClick = { nav.select(Tab.SETTINGS) }) { Text("Open Settings") }
        }
        is PlaylistRepository.State.Loading -> CenterMessage(state.message, loading = true)
        is PlaylistRepository.State.Error -> CenterMessage("Couldn't load the playlist:\n${state.message}") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(modifier = Modifier.focusRing(), onClick = { app.playlists.load(forceDownload = true) }) { Text("Retry") }
                OutlinedButton(modifier = Modifier.focusRing(), onClick = { nav.select(Tab.SETTINGS) }) { Text("Settings") }
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
                is Route.CategoryRoute -> CategoryScreen(route, v, nav, wide)
                is Route.ItemsRoute -> ItemsScreen(route, nav, wide)
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
