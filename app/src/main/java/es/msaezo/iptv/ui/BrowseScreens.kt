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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.graphics.vector.ImageVector
import es.msaezo.iptv.IptvApp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import es.msaezo.iptv.data.NowNext
import es.msaezo.iptv.data.LiveSection
import es.msaezo.iptv.data.LiveGuide
import es.msaezo.iptv.data.LiveChannel
import es.msaezo.iptv.data.Category
import es.msaezo.iptv.data.Entry
import es.msaezo.iptv.data.Collections
import es.msaezo.iptv.data.LibraryView
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.ResolvedCollection
import es.msaezo.iptv.data.Show
import es.msaezo.iptv.data.Tags

private const val ROW_ITEMS = 24

private fun <T> MutableList<Category<T>>.addRow(title: String, items: List<T>, min: Int = 3) {
    if (items.size >= min) add(Category(title, items))
}

/** Rows that organise the catalogue by what it is, not by which pack it came in. */
fun smartMovieRows(view: LibraryView, collections: List<ResolvedCollection>): List<Category<Movie>> {
    val cols = collections.associateBy { it.def.key }
    val rows = ArrayList<Category<Movie>>()
    rows.addRow("Recently added", view.recentMovies)
    val year = cols["m_year"]
    if (year != null) rows.addRow(year.def.title, year.movies) else rows.addRow("New in ${view.thisYear}", view.newMovies)
    for (k in listOf("m_trending", "m_cinema", "m_popular")) cols[k]?.let { rows.addRow(it.def.title, it.movies) }
    val byGenre = view.movieGenres.associate { it.name to it.items }
    for (g in Tags.genreOrder) {
        val fromTmdb = Collections.genreKeys[g].orEmpty().flatMap { cols[it]?.movies.orEmpty() }
        rows.addRow(g, (fromTmdb + byGenre[g].orEmpty()).distinctBy { it.id })
    }
    cols["m_top"]?.let { rows.addRow(it.def.title, it.movies) }
    rows.addRow("Top rated", view.topRatedMovies)
    for (k in listOf("m_oscars", "m_netflix", "m_prime", "m_disney", "m_max", "m_movistar", "m_apple")) {
        cols[k]?.let { rows.addRow(it.def.title, it.movies) }
    }
    return rows
}

fun smartShowRows(view: LibraryView, collections: List<ResolvedCollection>): List<Category<Show>> {
    val cols = collections.associateBy { it.def.key }
    val rows = ArrayList<Category<Show>>()
    rows.addRow("Recently updated", view.recentShows)
    for (k in listOf("t_year", "t_trending", "t_popular")) cols[k]?.let { rows.addRow(it.def.title, it.shows) }
    val byGenre = view.showGenres.associate { it.name to it.items }
    for (g in Tags.genreOrder) {
        val fromTmdb = Collections.genreKeys[g].orEmpty().flatMap { cols[it]?.shows.orEmpty() }
        rows.addRow(g, (fromTmdb + byGenre[g].orEmpty()).distinctBy { it.id })
    }
    cols["t_top"]?.let { rows.addRow(it.def.title, it.shows) }
    rows.addRow("Top rated", view.topRatedShows)
    for (k in listOf("t_netflix", "t_prime", "t_disney", "t_max", "t_movistar", "t_apple")) {
        cols[k]?.let { rows.addRow(it.def.title, it.shows) }
    }
    return rows
}

@Composable
fun MoviesScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val collections by IptvApp.instance.meta.collections.collectAsState()
    val progress by IptvApp.instance.store.progress.collectAsState()
    val smart = remember(view, collections) {
        smartMovieRows(view, collections).ifEmpty { view.movieCategories.filter { it.name != LibraryView.ALL } }
    }
    val pad = if (wide) 48.dp else 12.dp
    val w = if (wide) 150.dp else 108.dp
    if (view.movies.isEmpty()) {
        CenterMessage("No movies with your current language filter"); return
    }
    LazyColumn(Modifier.fillMaxSize(), state = nav.movieRows, contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
        item { ScreenTitle("Movies", "${view.movies.size} titles", pad) }
        items(smart, key = { "g:" + it.name }) { row ->
            PosterRow(
                row.name, row.items.take(ROW_ITEMS), pad,
                onSeeAll = if (row.items.size > ROW_ITEMS) ({ nav.openList(row.name, row.items) }) else null,
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
    val collections by IptvApp.instance.meta.collections.collectAsState()
    val smart = remember(view, collections) {
        smartShowRows(view, collections).ifEmpty { view.showCategories.filter { it.name != LibraryView.ALL } }
    }
    val pad = if (wide) 48.dp else 12.dp
    val w = if (wide) 150.dp else 108.dp
    if (view.shows.isEmpty()) {
        CenterMessage("No series with your current language filter"); return
    }
    LazyColumn(Modifier.fillMaxSize(), state = nav.showRows, contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
        item { ScreenTitle("Series", "${view.shows.size} titles", pad) }
        items(smart, key = { "g:" + it.name }) { row ->
            PosterRow(
                row.name, row.items.take(ROW_ITEMS), pad,
                onSeeAll = if (row.items.size > ROW_ITEMS) ({ nav.openList(row.name, row.items) }) else null,
                seeAllWidth = w,
            ) { s -> ShowCard(s, known[s.id], w) { nav.open(Route.ShowDetail(s.id)) } }
        }
    }
}

/** Full grid for a smart row ("See all"). */
@Composable
fun ItemsScreen(route: Route.ItemsRoute, nav: Nav, wide: Boolean) {
    val known by IptvApp.instance.meta.known.collectAsState()
    val list = nav.lists[route.title].orEmpty()
    val pad = if (wide) 48.dp else 12.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 150.dp else 104.dp),
        contentPadding = PaddingValues(pad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(route.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        items(list) { x ->
            when (x) {
                is Movie -> MovieCard(x, known[x.id], null, null) { nav.open(Route.MovieDetail(x.id)) }
                is Show -> ShowCard(x, known[x.id], null) { nav.open(Route.ShowDetail(x.id)) }
            }
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

/** Favourites are stored per channel; older ones were stored per stream and still count. */
private fun isFavorite(ch: LiveChannel, favorites: Set<String>) =
    "l:${ch.id}" in favorites || ch.versions.any { "l:${it.url}" in favorites }

private fun toggleFavorite(ch: LiveChannel, favorites: Set<String>) {
    val store = IptvApp.instance.store
    val legacy = ch.versions.map { "l:${it.url}" }.filter { it in favorites }
    if (legacy.isNotEmpty()) legacy.forEach(store::toggleFavorite) else store.toggleFavorite("l:${ch.id}")
}

@Composable
fun rememberLiveGuide(view: LibraryView): LiveGuide? {
    val guide by produceState<LiveGuide?>(null, view) {
        value = withContext(Dispatchers.Default) { view.liveGuide }
    }
    return guide
}

@Composable
fun LiveScreen(view: LibraryView, nav: Nav, wide: Boolean) {
    val store = IptvApp.instance.store
    val favorites by store.favorites.collectAsState()
    val recent by store.recentLive.collectAsState()
    val nowNext by IptvApp.instance.epg.guide.collectAsState()
    val guide = rememberLiveGuide(view)
    // Event channels are renamed through the day: re-read the channel list when it is an hour old.
    LaunchedEffect(Unit) { IptvApp.instance.playlists.refreshLiveIfStale() }
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000); value = System.currentTimeMillis()
        }
    }
    if (guide == null) {
        CenterMessage("Organising channels…", loading = true); return
    }
    val sections = remember(guide, favorites, recent) {
        val extra = ArrayList<LiveSection>()
        val favs = guide.channels.filter { isFavorite(it, favorites) }
        if (favs.isNotEmpty()) extra += LiveSection("★ Favorites", listOf(Category(LiveGuide.ALL, favs)))
        val rec = recent.mapNotNull { guide.channelOf(it) }.distinct()
        if (rec.isNotEmpty()) extra += LiveSection("Recently watched", listOf(Category(LiveGuide.ALL, rec)))
        extra + guide.sections
    }
    val menu = remember(sections) {
        sections.map { s ->
            val all = s.filters.firstOrNull { it.name == LiveGuide.ALL }?.items
            Category(s.name, all ?: s.filters.flatMap { it.items })
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    CategoryLayout(menu, nav.liveCategory, {
        nav.liveCategory = it; nav.liveFilter = 0; scope.launch { nav.liveList.scrollToItem(0) }
    }, wide, icon = ::liveIcon, badge = ::liveBadge) { cat ->
        val section = sections.firstOrNull { it.name == cat.name } ?: return@CategoryLayout
        val chosen = section.filters[nav.liveFilter.coerceIn(0, section.filters.size - 1)]
        // Channels with something to show (a programme guide, or an announced event) come first.
        val filter = remember(chosen) {
            if (section.name == "Recently watched") chosen
            else Category(chosen.name, chosen.items.sortedBy { if (it.guideEntry != null || it.announced) 0 else 1 })
        }
        Column(Modifier.fillMaxSize()) {
            if (section.filters.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(section.filters) { i, f ->
                        Pill("${f.name}  ${f.items.size}", i == nav.liveFilter, liveIcon(f.name)) {
                            nav.liveFilter = i; scope.launch { nav.liveList.scrollToItem(0) }
                        }
                    }
                }
            }
            val windowStart = (nowMs - 15 * 60_000L) / HALF_HOUR * HALF_HOUR
            if (wide) GuideHeader(windowStart)
            LazyColumn(
                state = nav.liveList,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(filter.items, key = { _, ch -> ch.id }) { i, ch ->
                    ChannelRow(i + 1, ch, nowNext[ch.id], isFavorite(ch, favorites),
                        onFavorite = { toggleFavorite(ch, favorites) },
                        onClick = { Actions.playLive(context, filter.items, i) },
                        timeline = wide, nowMs = nowMs, windowStart = windowStart)
                }
            }
        }
    }
}

/** Icon for a Live TV section, sport or kind of channel. */
fun liveIcon(name: String): ImageVector? = when (name) {
    "★ Favorites" -> Icons.Filled.Star
    "Recently watched" -> Icons.Filled.History
    "Sports", "Other sports" -> Icons.Filled.EmojiEvents
    "Events", "Announced" -> Icons.Filled.Event
    "Football" -> Icons.Filled.SportsSoccer
    "Tennis" -> Icons.Filled.SportsTennis
    "Basketball" -> Icons.Filled.SportsBasketball
    "Formula 1" -> Icons.Filled.SportsMotorsports
    "Motorbikes" -> Icons.Filled.TwoWheeler
    "International" -> Icons.Filled.Public
    "Provider packs" -> Icons.Filled.Folder
    "General" -> Icons.Filled.Tv
    "Movies & Series" -> Icons.Filled.Movie
    "Kids" -> Icons.Filled.ChildCare
    "News" -> Icons.Filled.Article
    "Documentaries" -> Icons.Filled.Explore
    "Music" -> Icons.Filled.MusicNote
    "Adults" -> Icons.Filled.Lock
    else -> null
}

/** Countries get a two-letter mark instead of an icon. */
fun liveBadge(name: String): String? = when (name) {
    "Spain" -> "ES"
    "UK" -> "UK"
    "USA" -> "US"
    else -> null
}

private val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)
fun hhmm(ms: Long): String = synchronized(clock) { clock.format(java.util.Date(ms)) }

private const val HALF_HOUR = 30 * 60_000L
/** How much time the programme strip shows. */
private const val WINDOW_MS = 3 * 60 * 60_000L
private const val INFO_WEIGHT = 0.38f
private const val LANE_WEIGHT = 0.62f

/** Clock times above the programme strips, lined up with them. */
@Composable
private fun GuideHeader(windowStart: Long) {
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 78.dp, top = 2.dp)) {
        Spacer(Modifier.weight(INFO_WEIGHT))
        BoxWithConstraints(Modifier.weight(LANE_WEIGHT).height(18.dp)) {
            val w = maxWidth
            var t = windowStart
            while (t < windowStart + WINDOW_MS) {
                Text(
                    hhmm(t), maxLines = 1, softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.offset(x = w * ((t - windowStart).toFloat() / WINDOW_MS)),
                )
                t += HALF_HOUR
            }
        }
    }
}

/** One channel's programmes as boxes on a time strip, with a line marking the present. */
@Composable
private fun ProgrammeLane(guide: NowNext?, quiet: Boolean, nowMs: Long, windowStart: Long, modifier: Modifier) {
    BoxWithConstraints(modifier.height(38.dp).clipToBounds()) {
        val w = maxWidth
        fun x(t: Long): Dp = w * ((t - windowStart).coerceIn(0L, WINDOW_MS).toFloat() / WINDOW_MS)
        val shown = guide?.items.orEmpty().filter { it.end > windowStart && it.start < windowStart + WINDOW_MS }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().background(Color(0xFF111111)), contentAlignment = Alignment.CenterStart) {
                val note = when {
                    guide == null -> ""
                    quiet -> "No event announced"
                    else -> "No programme information"
                }
                Text(
                    note, Modifier.padding(start = 10.dp), maxLines = 1,
                    color = Color(0xFF6E6E6E), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        for (p in shown) {
            val x0 = x(p.start)
            val width = (x(p.end) - x0 - 2.dp).coerceAtLeast(2.dp)
            val current = p.start <= nowMs && nowMs < p.end
            Box(
                Modifier.offset(x = x0).width(width).fillMaxHeight()
                    .background(if (current) Color(0xFF3B3B3B) else Color(0xFF222222))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    p.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                    color = if (current) Color.White else Color(0xFFBDBDBD),
                )
            }
        }
        Box(Modifier.offset(x = x(nowMs)).width(2.dp).fillMaxHeight().background(Brand))
    }
}

@Composable
fun ChannelRow(
    number: Int,
    ch: LiveChannel,
    guide: NowNext?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
    timeline: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    windowStart: Long = 0L,
) {
    // Ask for the programme guide only once the row has stayed on screen for a moment.
    LaunchedEffect(ch.id) {
        delay(350)
        IptvApp.instance.epg.request(ch)
    }
    val quiet = ch.isEvent && !ch.announced && guide?.now == null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .focusMemory("ch|${ch.id}")
                .tvClickable(Square, 1.015f, onClick)
                .background(Color(0xFF161616), Square)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(if (timeline) Modifier.weight(INFO_WEIGHT) else Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("$number", modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier.size(width = 56.dp, height = 36.dp).background(Color(0xFF262626)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ch.logo != null) {
                        AsyncImage(ch.logo, ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                        color = if (quiet) Color(0xFF8A8A8A) else Color.White,
                    )
                    val now = guide?.now
                    val next = guide?.next
                    val kinds = ch.labels.map { it.substringBefore(' ') }.distinct().filter { it != "Standard" && it != "Source" }
                    val qualities = when {
                        kinds.size > 1 -> kinds.joinToString(" · ")
                        ch.versions.size > 1 -> (kinds.firstOrNull()?.let { "$it · " } ?: "") + "${ch.versions.size} sources"
                        else -> kinds.firstOrNull()
                    }
                    val line = if (timeline) qualities else when {
                        now != null -> "Now  ${now.title}  ·  until ${hhmm(now.end)}" + (next?.let { "     Next  ${it.title}" } ?: "")
                        next != null -> "${hhmm(next.start)}  ${next.title}"
                        quiet -> "No event announced"
                        else -> qualities
                    }
                    if (line != null) {
                        Text(
                            line, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!timeline && now != null) Color(0xFFD0D0D0) else Color(0xFF8A8A8A),
                        )
                    }
                }
            }
            if (timeline) {
                Spacer(Modifier.width(10.dp))
                ProgrammeLane(guide, quiet, nowMs, windowStart, Modifier.weight(LANE_WEIGHT))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.tvClickable(Square, 1.1f, onFavorite).padding(8.dp)) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "Favorite", modifier = Modifier.size(20.dp),
                tint = if (favorite) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
