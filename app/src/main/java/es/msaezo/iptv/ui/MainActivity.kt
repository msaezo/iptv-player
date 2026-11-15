package es.msaezo.iptv.ui

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateDpAsState
import android.os.Bundle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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

    override fun onStop() {
        super.onStop()
        Preview.stop()
    }
}

@Composable
fun MainScreen() {
    val app = IptvApp.instance
    val context = LocalContext.current
    val homeList = rememberLazyListState()
    val movieRows = rememberLazyListState()
    val showRows = rememberLazyListState()
    val liveList = rememberLazyListState()
    val nav = rememberSaveable(
        saver = listSaver<Nav, String>(save = { it.save() }, restore = { saved ->
            Nav(homeList, movieRows, showRows, liveList).apply { restore(saved) }
        }),
    ) { Nav(homeList, movieRows, showRows, liveList) }
    val config = LocalConfiguration.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val wide = isTv || config.screenWidthDp >= 840
    val state by app.playlists.state.collectAsState()

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Tab.HOME) { nav.back() }

    if (wide) {
        // On a TV everything is drawn a little smaller, so more titles fit on screen.
        val base = LocalDensity.current
        val density = if (isTv) Density(base.density * TV_SCALE, base.fontScale) else base
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(start = RAIL_WIDTH)) { Content(nav, wide, state) }
                SideRail(nav, focusOnStart = isTv)
            }
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

private const val TV_SCALE = 0.82f
private val RAIL_WIDTH = 60.dp

/** TV side menu: a slim column of icons, no labels. */
@Composable
private fun SideRail(nav: Nav, focusOnStart: Boolean) {
    val homeFocus = remember { FocusRequester() }
    val location by IptvApp.instance.net.location.collectAsState()
    if (focusOnStart) {
        // Give the menu focus first; the home banner then moves it to its Play button.
        LaunchedEffect(Unit) {
            delay(300)
            runCatching { homeFocus.requestFocus() }
        }
    }
    Column(
        Modifier.fillMaxHeight().width(RAIL_WIDTH).background(Color(0xFF050505)).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(painterResource(R.drawable.logo_mark), "TUTV", Modifier.size(28.dp))
        Spacer(Modifier.weight(1f))
        for (t in listOf(Tab.SEARCH, Tab.HOME, Tab.MOVIES, Tab.SERIES, Tab.LIVE)) {
            RailItem(t, nav, if (t == Tab.HOME) homeFocus else null)
        }
        Spacer(Modifier.weight(1f))
        // Country the connection appears from (a quick check that the VPN is on).
        location?.let {
            Text(
                it.countryCode, color = Color(0xFF9A9A9A), fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        RailItem(Tab.SETTINGS, nav, null)
    }
}

@Composable
private fun RailItem(t: Tab, nav: Nav, focus: FocusRequester?) {
    val selected = nav.tab == t
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .size(44.dp)
            .background(if (focused) Color.White else Color.Transparent, Square)
            .clickable { nav.select(t) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            t.icon, t.label, Modifier.size(22.dp),
            tint = when {
                focused -> Color.Black
                selected -> Color.White
                else -> Color(0xFF8A8A8A)
            },
        )
        if (selected && !focused) {
            Box(Modifier.align(Alignment.CenterStart).size(width = 2.dp, height = 18.dp).background(Brand))
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
            ActionButton("Open Settings", null, true) { nav.select(Tab.SETTINGS) }
        }
        is PlaylistRepository.State.Loading -> CenterMessage(state.message, loading = true)
        is PlaylistRepository.State.Error -> CenterMessage("Couldn't load the playlist:\n${state.message}") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Retry", null, true) { app.playlists.load(forceDownload = true) }
                ActionButton("Settings", null, false) { nav.select(Tab.SETTINGS) }
            }
        }
        is PlaylistRepository.State.Ready -> {
            val view by app.playlists.view.collectAsState()
            val v = view ?: return CenterMessage("Preparing…", loading = true)
            val route = nav.stack.lastOrNull()
            // Each screen remembers which card had the focus, to hand it back on return.
            CompositionLocalProvider(
                LocalFocusMemory provides nav.focus,
                LocalScreenId provides (route?.toString() ?: nav.tab.name),
            ) {
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
}
