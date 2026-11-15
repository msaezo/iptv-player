package es.msaezo.iptv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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

/**
 * Clickable that is comfortable on a TV remote: grows slightly and shows a bright
 * border while focused.
 */
@Composable
fun Modifier.tvClickable(
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Float = 1.05f,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, label = "focusScale")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
        .clip(shape)
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick)
}

@Composable
fun RatingBadge(vote: Double, modifier: Modifier = Modifier) {
    if (vote <= 0.0) return
    Row(
        modifier
            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
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
                    .background(Color(0xCC0E3B43), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
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
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x66000000),
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
            .tvClickable(RoundedCornerShape(16.dp), 1.06f, onClick)
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
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .tvClickable(RoundedCornerShape(50), 1.04f, onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
                            .tvClickable(RoundedCornerShape(10.dp), 1.02f) { onSelect(i) }
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                itemsIndexed(categories) { i, c -> Pill(c.name, i == sel) { onSelect(i) } }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content(categories[sel]) }
        }
    }
}

@Composable
fun <T> PosterRow(title: String, list: List<T>, card: @Composable (T) -> Unit) {
    if (list.isEmpty()) return
    Column {
        SectionTitle(title, Modifier.padding(horizontal = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(4.dp)) {
            items(list) { card(it) }
        }
    }
}
