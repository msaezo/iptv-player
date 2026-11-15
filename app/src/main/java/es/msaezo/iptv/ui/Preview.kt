package es.msaezo.iptv.ui

import android.view.LayoutInflater
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.R
import kotlinx.coroutines.delay

/**
 * The single muted preview that may be playing behind a title's page. Kept global so it
 * can be shut down before real playback starts: many providers allow one stream at a time.
 */
object Preview {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    fun start(p: ExoPlayer) {
        stop()
        player = p
    }

    fun stop() {
        val p = player ?: return
        player = null
        runCatching { p.stop(); p.release() }
    }

    fun stop(p: ExoPlayer) {
        if (player === p) stop()
    }
}

/** Where a preview starts: past the studio logos, early enough not to spoil anything. */
private const val PREVIEW_START_MS = 90_000L
private const val PREVIEW_LENGTH_MS = 75_000L
private const val PREVIEW_DELAY_MS = 2_500L

/**
 * Plays the opening of [url] without sound after a short pause on the page, fading in
 * over whatever is behind it. Draws nothing until the first frame is ready.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun PreviewVideo(url: String, userAgent: String?, referrer: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ready by remember(url) { mutableStateOf(false) }
    var mine by remember(url) { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(url) {
        delay(PREVIEW_DELAY_MS)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent ?: IptvApp.instance.store.userAgent.value)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
        if (referrer != null) http.setDefaultRequestProperties(mapOf("Referer" to referrer))
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build()
        p.volume = 0f
        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                ready = true
            }

            override fun onPlayerError(error: PlaybackException) {
                ready = false
                Preview.stop(p)
            }
        })
        p.setMediaItem(MediaItem.fromUri(url), PREVIEW_START_MS)
        p.prepare()
        p.playWhenReady = true
        Preview.start(p)
        mine = p
        delay(PREVIEW_LENGTH_MS)
        ready = false
        delay(900)
        Preview.stop(p)
    }
    DisposableEffect(url) {
        onDispose { mine?.let { Preview.stop(it) } }
    }

    val p = mine
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(800), label = "preview")
    if (p != null && Preview.player === p) {
        AndroidView(
            factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.preview_player, null) as PlayerView },
            update = { it.player = p },
            onRelease = { it.player = null },
            modifier = modifier.alpha(alpha),
        )
    }
}
