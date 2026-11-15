package es.msaezo.iptv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import es.msaezo.iptv.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import es.msaezo.iptv.data.Category
import es.msaezo.iptv.data.Movie
import es.msaezo.iptv.data.Show
import es.msaezo.iptv.data.Tags
import es.msaezo.iptv.data.TmdbItem

/** Per screen: the card that last had the focus, and where each row was scrolled to. */
class FocusMemory {
    val last = HashMap<String, String>()
    val rows = HashMap<String, LazyListState>()
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }
val LocalScreenId = compositionLocalOf { "" }
val LocalRowKey = compositionLocalOf { "" }

/**
 * Remembers that this item had the focus on its screen and takes it back when the screen
 * is shown again, so Back returns to the card you came from. Put it before the clickable.
 */
@Composable
fun Modifier.focusMemory(key: String): Modifier {
    val memory = LocalFocusMemory.current ?: return this
    val screen = LocalScreenId.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(key, screen) {
        if (memory.last[screen] == key) {
            delay(80)
            runCatching { requester.requestFocus() }
        }
    }
    return this.focusRequester(requester).onFocusChanged { if (it.isFocused) memory.last[screen] = key }
}

/**
 * Clickable that is comfortable on a TV remote: grows slightly and shows a white
 * frame while focused.
 */
@Composable
fun Modifier.tvClickable(
    shape: Shape = Square,
    scaleOnFocus: Float = 1.05f,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, label = "focusScale")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        // A white frame with a small gap, so focus reads on light and dark artwork alike.
        .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
        .padding(4.dp)
        .clip(shape)
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick)
}

@Composable
fun RatingBadge(vote: Double, modifier: Modifier = Modifier) {
    if (vote <= 0.0) return
    Row(
        modifier
            .background(Color(0xCC000000), Square)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text("%.1f".format(vote), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LanguagePills(langs: List<String>, modifier: Modifier = Modifier) {
    if (langs.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        langs.filter { it != Tags.UNKNOWN }.take(3).forEach {
            Text(
                Tags.shortLabel(it),
                color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xD9000000), Square)
                    .border(1.dp, Color(0x59FFFFFF), Square)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** 2:3 poster card used for movies and shows. */
@Composable
fun PosterCard(
    title: String,
    subtitle: String?,
    image: String?,
    vote: Double,
    langs: List<String>,
    progress: Float?,
    width: Dp?,
    onClick: () -> Unit,
) {
    Column(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusMemory("${LocalRowKey.current}|$title|$subtitle")
                .tvClickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (image != null) {
                AsyncImage(
                    model = image, contentDescription = title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    title, modifier = Modifier.align(Alignment.Center).padding(10.dp),
                    style = MaterialTheme.typography.titleSmall, maxLines = 4,
                )
            }
            RatingBadge(vote, Modifier.align(Alignment.TopEnd).padding(6.dp))
            LanguagePills(langs, Modifier.align(Alignment.BottomStart).padding(6.dp))
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = Brand,
                    trackColor = Color(0x88555555),
                )
            }
        }
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MovieCard(movie: Movie, meta: TmdbItem?, progress: Float?, width: Dp?, onClick: () -> Unit) {
    PosterCard(
        title = movie.title,
        subtitle = movie.year?.toString(),
        image = meta?.posterUrl ?: movie.poster,
        vote = meta?.vote ?: movie.rating ?: 0.0,
        langs = movie.languages,
        progress = progress,
        width = width,
        onClick = onClick,
    )
}

@Composable
fun ShowCard(show: Show, meta: TmdbItem?, width: Dp?, onClick: () -> Unit) {
    PosterCard(
        title = show.name,
        subtitle = if (show.episodes.isEmpty()) (show.year?.toString() ?: "Series")
        else "${show.seasons.size} season${if (show.seasons.size == 1) "" else "s"} · ${show.episodes.size} ep",
        image = meta?.posterUrl ?: show.poster,
        vote = meta?.vote ?: show.rating ?: 0.0,
        langs = show.languages,
        progress = null,
        width = width,
        onClick = onClick,
    )
}

/** Wide artwork tile, like Chillio's collection tiles. */
@Composable
fun CollectionTile(title: String, count: Int, image: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .tvClickable(Square, 1.06f, onClick)
            .background(Brush.linearGradient(listOf(Color(0xFF2B5876), Color(0xFF4E4376))))
    ) {
        if (image != null) {
            AsyncImage(image, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2)
            Text("$count titles", color = Color(0xCCFFFFFF), fontSize = 12.sp)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun CenterMessage(text: String, loading: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            if (loading) {
                Image(painterResource(R.drawable.logo_wordmark), "TUTV", Modifier.height(34.dp))
                Spacer(Modifier.height(28.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(180.dp).height(2.dp),
                    color = Color.White, trackColor = Color(0x33FFFFFF),
                )
                Spacer(Modifier.height(20.dp))
            }
            Text(
                text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                color = if (loading) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            )
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val color = if (selected) Color.Black else Color.White
    Row(
        Modifier
            .tvClickable(Square, 1.06f, onClick)
            .background(if (selected) Color.White else Color(0x26FFFFFF), Square)
            .border(1.dp, if (selected) Color.White else Color(0x55FFFFFF), Square)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** 16:9 artwork card (Netflix-style rows). */
@Composable
fun LandscapeCard(
    title: String,
    image: String?,
    vote: Double,
    progress: Float?,
    width: Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .focusMemory("${LocalRowKey.current}|$title")
            .tvClickable(Square, 1.08f, onClick)
            .background(Color(0xFF222222))
    ) {
        if (image != null) {
            AsyncImage(image, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6000000))
            )
        )
        Text(
            title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        )
        RatingBadge(vote, Modifier.align(Alignment.TopEnd).padding(6.dp))
        if (progress != null && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                color = Brand,
                trackColor = Color(0x88555555),
            )
        }
    }
}

/** Last card of a row: opens the full list. */
@Composable
fun SeeAllCard(width: Dp, ratio: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(ratio)
            .tvClickable(Square, 1.08f, onClick)
            .background(Color(0xFF1C1C1C))
            .border(1.dp, Color(0x33FFFFFF), Square),
        contentAlignment = Alignment.Center,
    ) {
        Text("See all ›", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/**
 * Categories on the left (wide screens) or as a pill row on top (phones), with the
 * selected category's content next to it.
 */
@Composable
fun <T> CategoryLayout(
    categories: List<Category<T>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    wide: Boolean,
    icon: (String) -> ImageVector? = { null },
    badge: (String) -> String? = { null },
    content: @Composable (Category<T>) -> Unit,
) {
    if (categories.isEmpty()) {
        CenterMessage("Nothing here with your current language filter")
        return
    }
    val sel = selected.coerceIn(0, categories.size - 1)
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.width(260.dp).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(categories) { i, c ->
                    val isSel = i == sel
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .tvClickable(Square, 1.02f) { onSelect(i) }
                            .background(
                                if (isSel) Color(0x2EFFFFFF) else Color.Transparent,
                                Square,
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val mark = badge(c.name)
                        val glyph = icon(c.name)
                        if (glyph != null) {
                            Icon(glyph, null, Modifier.size(18.dp), tint = if (isSel) Color.White else Color(0xFFB8B8B8))
                            Spacer(Modifier.width(12.dp))
                        } else if (mark != null) {
                            Text(
                                mark, Modifier.width(18.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1,
                                color = if (isSel) Color.White else Color(0xFFB8B8B8),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            c.items.size.toString(), fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content(categories[sel]) }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(categories) { i, c -> Pill(c.name, i == sel, icon(c.name)) { onSelect(i) } }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content(categories[sel]) }
        }
    }
}

@Composable
fun <T> PosterRow(
    title: String,
    list: List<T>,
    startPadding: Dp = 4.dp,
    onSeeAll: (() -> Unit)? = null,
    seeAllWidth: Dp = 140.dp,
    seeAllRatio: Float = 2f / 3f,
    card: @Composable (T) -> Unit,
) {
    if (list.isEmpty()) return
    Column {
        Text(
            title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = Color(0xFFE5E5E5),
            modifier = Modifier.padding(start = startPadding, top = 14.dp, bottom = 6.dp),
        )
        val memory = LocalFocusMemory.current
        val screen = LocalScreenId.current
        val state = memory?.rows?.getOrPut("$screen/$title") { LazyListState() } ?: rememberLazyListState()
        CompositionLocalProvider(LocalRowKey provides title) {
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = startPadding, end = startPadding, top = 8.dp, bottom = 8.dp),
            ) {
                items(list) { card(it) }
                if (onSeeAll != null) item { SeeAllCard(seeAllWidth, seeAllRatio, onSeeAll) }
            }
        }
    }
}
