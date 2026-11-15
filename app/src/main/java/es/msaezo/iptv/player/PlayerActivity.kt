package es.msaezo.iptv.player

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import es.msaezo.iptv.IptvApp

/** What to play. Kept in memory because channel lists can be too big for an Intent. */
object Playback {
    data class Item(
        val url: String,
        val title: String,
        val subtitle: String? = null,
        val userAgent: String? = null,
        val referrer: String? = null,
        val startPositionMs: Long = 0L,
    )

    var queue: List<Item> = emptyList()
        private set
    var index: Int = 0
        private set
    var isLive: Boolean = false
        private set

    fun play(context: Context, items: List<Item>, startIndex: Int, live: Boolean) {
        if (items.isEmpty()) return
        queue = items
        index = startIndex.coerceIn(0, items.size - 1)
        isLive = live
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    internal fun moveTo(i: Int) {
        index = i
    }
}

@OptIn(markerClass = [UnstableApi::class])
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlay: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val store get() = IptvApp.instance.store
    private var live = false
    private var liveRetries = 0
    private var errorDialog: AlertDialog? = null

    private val hideOverlay = Runnable { overlay.visibility = View.GONE }
    private val saveTicker = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val queue = Playback.queue
        if (queue.isEmpty()) {
            finish(); return
        }
        live = Playback.isLive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val first = queue[Playback.index]
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(first.userAgent ?: store.userAgent.value)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        first.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            .setConstantBitrateSeekingEnabled(true)

        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http), extractors))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)

        playerView = PlayerView(this).apply {
            player = this@PlayerActivity.player
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setShowSubtitleButton(true)
            controllerShowTimeoutMs = 4000
            if (live) {
                setShowFastForwardButton(false)
                setShowRewindButton(false)
            }
            setShowNextButton(!live && queue.size > 1)
            setShowPreviousButton(!live && queue.size > 1)
        }
        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 0, 0, 0)); cornerRadius = dp(12).toFloat()
            }
            visibility = View.GONE
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(playerView, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(32), dp(24), 0, 0)
            })
        }
        setContentView(root)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = handleError(error)

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) liveRetries = 0
                if (state == Player.STATE_ENDED && !live) {
                    val d = player.duration
                    if (d > 0) store.saveProgress(currentUrl(), d, d)
                    finish()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!live) {
                    Playback.moveTo(player.currentMediaItemIndex)
                    showOverlay(Playback.queue[player.currentMediaItemIndex].let { it.subtitle ?: it.title })
                }
            }
        })

        if (live) {
            playLive(Playback.index)
        } else {
            val items = queue.map { toMediaItem(it) }
            player.setMediaItems(items, Playback.index, first.startPositionMs.coerceAtLeast(0))
            player.prepare()
            player.playWhenReady = true
            if (first.startPositionMs > 0) {
                Toast.makeText(this, "Resuming from ${formatTime(first.startPositionMs)}", Toast.LENGTH_SHORT).show()
            }
            showOverlay(first.subtitle ?: first.title)
        }
    }

    private fun toMediaItem(item: Playback.Item): MediaItem {
        val lower = item.url.lowercase()
        val mime = when {
            ".m3u8" in lower -> MimeTypes.APPLICATION_M3U8
            ".mpd" in lower -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        return MediaItem.Builder()
            .setUri(item.url)
            .setMediaId(item.url)
            .apply { if (mime != null) setMimeType(mime) }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
            .build()
    }

    private fun playLive(index: Int) {
        val q = Playback.queue
        val i = ((index % q.size) + q.size) % q.size
        Playback.moveTo(i)
        val item = q[i]
        liveRetries = 0
        player.setMediaItem(toMediaItem(item))
        player.prepare()
        player.playWhenReady = true
        store.addRecentLive(item.url)
        showOverlay("${i + 1}  ${item.title}")
    }

    private fun currentUrl(): String =
        if (live) Playback.queue[Playback.index].url
        else player.currentMediaItem?.mediaId ?: Playback.queue[Playback.index].url

    private fun showOverlay(text: String) {
        overlay.text = text
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, 3500)
    }

    private fun handleError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        if (live && liveRetries < 3) {
            liveRetries++
            showOverlay("Reconnecting… ($liveRetries/3)")
            handler.postDelayed({ player.prepare(); player.playWhenReady = true }, 2000L * liveRetries)
            return
        }
        errorDialog?.dismiss()
        errorDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Can't play this stream")
            .setMessage("${error.errorCodeName}\n${error.cause?.message ?: error.message ?: ""}".trim())
            .setPositiveButton("Retry") { _, _ -> player.prepare(); player.playWhenReady = true }
            .setNeutralButton("Open in other player") { _, _ -> openExternal() }
            .setNegativeButton("Close") { _, _ -> finish() }
            .show()
    }

    private fun openExternal() {
        val url = currentUrl()
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
        try {
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No other video player installed (try VLC)", Toast.LENGTH_LONG).show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> if (live) {
                    playLive(Playback.index - 1); return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (live) {
                    playLive(Playback.index + 1); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!live) {
                    player.seekBack(); playerView.showController(); return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!live) {
                    player.seekForward(); playerView.showController(); return true
                }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK &&
            playerView.isControllerFullyVisible
        ) {
            playerView.hideController()
            return true
        }
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    private fun saveProgress() {
        if (live || !::player.isInitialized) return
        val d = player.duration
        val p = player.currentPosition
        if (d != C.TIME_UNSET && d > 0 && p > 0) store.saveProgress(currentUrl(), p, d)
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) {
            player.playWhenReady = true
            handler.post(saveTicker)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(saveTicker)
        if (::player.isInitialized) {
            saveProgress()
            player.playWhenReady = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        errorDialog?.dismiss()
        if (::player.isInitialized) player.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun formatTime(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
            else "%d:%02d".format(s / 60, s % 60)
        }
    }
}
