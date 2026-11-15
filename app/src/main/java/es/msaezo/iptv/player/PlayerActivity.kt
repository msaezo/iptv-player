package es.msaezo.iptv.player

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.DefaultTimeBar
import es.msaezo.iptv.R
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.media3.datasource.HttpDataSource
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
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import es.msaezo.iptv.data.UserStore
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import es.msaezo.iptv.IptvApp
import es.msaezo.iptv.ui.tr

/** What to play. Kept in memory because channel lists can be too big for an Intent. */
object Playback {
    data class Item(
        val url: String,
        val title: String,
        val subtitle: String? = null,
        val userAgent: String? = null,
        val referrer: String? = null,
        val startPositionMs: Long = 0L,
        /** Live TV: the same channel in other qualities (best first; includes [url]). */
        val alternatives: List<Alt> = emptyList(),
        /** Films share one audio / subtitle preference; each series keeps its own. */
        val memoryKey: String? = null,
        /** Episodes: series + season, under which the intro's position is remembered. */
        val introKey: String? = null,
    )

    data class Alt(val label: String, val url: String)

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
    private lateinit var infoText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var hintText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val store get() = IptvApp.instance.store
    private var live = false
    private var liveRetries = 0
    private var userPickedTracks = false
    /** Which quality of the current live channel is playing (index into its alternatives). */
    private var liveAlt = 0
    private var errorDialog: AlertDialog? = null

    private val hideOverlay = Runnable { infoText.visibility = View.GONE }
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

        val renderers = OffsetRenderers(this) { store.audioOffset.value }
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // Live TV can be asked to load some seconds ahead before it starts, which rides out
        // a busy server at the price of a slower start and a picture that far behind live.
        val ahead = if (live) store.liveBuffer.value * 1000 else 0
        val loadControl = DefaultLoadControl.Builder().apply {
            if (ahead > 0) setBufferDurationsMs(maxOf(60_000, ahead * 2), maxOf(90_000, ahead * 3), ahead, ahead)
        }.build()

        player = ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http), extractors))
            .setSeekBackIncrementMs(JUMP_MS)
            .setSeekForwardIncrementMs(JUMP_MS)
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
        // Left / right on the progress bar also move one minute (the default was 1/20 of the film).
        playerView.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.setKeyTimeIncrement(JUMP_MS)

        // Keep the progress line and the buttons away from the edges: 2% free on each side.
        runCatching {
            val side = (maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.02f).toInt()
            // No dark band behind the buttons: they sit straight on the picture.
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.setBackgroundColor(Color.TRANSPARENT)
            for (id in intArrayOf(androidx.media3.ui.R.id.exo_bottom_bar, androidx.media3.ui.R.id.exo_progress)) {
                val v = playerView.findViewById<View>(id) ?: continue
                val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
                lp.leftMargin += side
                lp.rightMargin += side
                v.layoutParams = lp
            }
        }

        // Subtitles: our own text view, so they can sit on a soft rounded plate, centred.
        val screenShort = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val style = store.subtitles.value
        subtitleText = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = if (style.serif) {
                runCatching { ResourcesCompat.getFont(this@PlayerActivity, R.font.subtitle_serif) }.getOrNull() ?: Typeface.SERIF
            } else {
                runCatching { ResourcesCompat.getFont(this@PlayerActivity, R.font.inter_medium) }.getOrNull() ?: Typeface.SANS_SERIF
            }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, screenShort * 0.05f * style.size / 100f)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
            setLineSpacing(0f, 1.12f)
            val h = (screenShort * 0.022f).toInt()
            val v = (screenShort * 0.010f).toInt()
            setPadding(h, v, h, v)
            if (style.background > 0) {
                background = GradientDrawable().apply {
                    setColor(if (style.background == 1) Color.argb(96, 30, 30, 30) else Color.argb(200, 0, 0, 0))
                    cornerRadius = screenShort * 0.014f
                }
            }
            setShadowLayer(screenShort * 0.004f, 0f, screenShort * 0.0015f, Color.argb(220, 0, 0, 0))
            maxWidth = (maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.8f).toInt()
            visibility = View.GONE
        }
        hintText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { setColor(Color.argb(190, 0, 0, 0)); setStroke(dp(1), Color.argb(120, 255, 255, 255)) }
            visibility = View.GONE
        }
        // Lift the subtitles above the playback controls while those are showing.
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            subtitleText.translationY = if (visibility == View.VISIBLE) -screenShort * 0.17f else 0f
        })

        infoText = TextView(this).apply {
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
            addView(subtitleText, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (screenShort * style.position / 100f).toInt()
            })
            addView(hintText, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(40), (screenShort * 0.20f).toInt())
            })
            addView(infoText, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(32), dp(24), 0, 0)
            })
        }
        setContentView(root)

        // Start in the audio and subtitle language chosen last time for this series (or for films).
        first.memoryKey?.let { store.trackPref(it) }?.let { saved ->
            val audio = saved.substringBefore('|')
            val text = saved.substringAfter('|', "")
            val b = player.trackSelectionParameters.buildUpon()
            if (audio.isNotEmpty()) b.setPreferredAudioLanguage(audio)
            if (text.isNotEmpty() && text != "-") b.setPreferredTextLanguage(text)
            player.trackSelectionParameters = b.build()
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = handleError(error)

            // Fired when a track is picked in the on-screen menus (the one above ran earlier).
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                userPickedTracks = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (live || !userPickedTracks) return
                val key = Playback.queue.getOrNull(Playback.index)?.memoryKey ?: return
                fun selected(type: Int): String? {
                    for (g in tracks.groups) {
                        if (g.type != type || !g.isSelected) continue
                        for (i in 0 until g.length) {
                            if (g.isTrackSelected(i)) return g.getTrackFormat(i).language?.takeIf { it != "und" }
                        }
                    }
                    return null
                }
                val audio = selected(C.TRACK_TYPE_AUDIO).orEmpty()
                val text = selected(C.TRACK_TYPE_TEXT) ?: "-"
                store.setTrackPref(key, "$audio|$text")
            }

            override fun onCues(cueGroup: CueGroup) {
                val text = SpannableStringBuilder()
                val pictures = ArrayList<Cue>()
                for (cue in cueGroup.cues) {
                    val t = cue.text
                    if (t != null) {
                        val line = t.trim()
                        if (line.isNotEmpty()) {
                            if (text.isNotEmpty()) text.append("\n")
                            text.append(line)
                        }
                    } else if (cue.bitmap != null) {
                        pictures += cue
                    }
                }
                subtitleText.text = text
                subtitleText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                // Picture-based subtitles (DVD / Blu-ray style) still use the built-in view.
                playerView.subtitleView?.setCues(pictures)
            }

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
                    introStartMark = -1
                    Playback.moveTo(player.currentMediaItemIndex)
                    showOverlay(Playback.queue[player.currentMediaItemIndex].let { it.subtitle ?: it.title })
                }
            }
        })

        if (live) {
            playLive(Playback.index, immediate = true)
        } else {
            val items = queue.map { toMediaItem(it) }
            player.setMediaItems(items, Playback.index, first.startPositionMs.coerceAtLeast(0))
            player.prepare()
            player.playWhenReady = true
            if (first.startPositionMs > 0) {
                Toast.makeText(this, tr("Resuming from ${formatTime(first.startPositionMs)}"), Toast.LENGTH_SHORT).show()
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

    private val startLiveRunnable = Runnable { startLive() }

    /**
     * Changes channel. The old stream is closed first and the new one starts a moment later:
     * providers that allow a single connection refuse a channel opened while the previous
     * one is still streaming, and it also means only the last channel loads when zapping fast.
     */
    private fun playLive(index: Int, immediate: Boolean = false) {
        val q = Playback.queue
        val i = ((index % q.size) + q.size) % q.size
        Playback.moveTo(i)
        liveAlt = 0
        store.addRecentLive(q[i].url)
        handler.removeCallbacks(startLiveRunnable)
        if (immediate) {
            startLive()
        } else {
            player.stop()
            player.clearMediaItems()
            showOverlay("${i + 1}  ${q[i].title}")
            handler.postDelayed(startLiveRunnable, ZAP_DELAY_MS)
        }
    }

    /** (Re)starts the current live channel in the quality chosen by [liveAlt]. */
    private fun startLive() {
        val item = Playback.queue[Playback.index]
        val alts = item.alternatives
        val alt = alts.getOrNull(liveAlt)
        liveRetries = 0
        player.setMediaItem(toMediaItem(if (alt != null) item.copy(url = alt.url) else item))
        player.prepare()
        player.playWhenReady = true
        val quality = if (alts.size > 1 && alt != null) "   ·   ${alt.label}   ◀ ▶" else ""
        showOverlay("${Playback.index + 1}  ${item.title}$quality")
    }

    /** Left / right on live TV: same channel, another quality. */
    private fun switchQuality(step: Int): Boolean {
        val alts = Playback.queue[Playback.index].alternatives
        if (alts.size < 2) return false
        liveAlt = ((liveAlt + step) % alts.size + alts.size) % alts.size
        startLive()
        return true
    }

    private fun currentUrl(): String =
        if (live) Playback.queue[Playback.index].url
        else player.currentMediaItem?.mediaId ?: Playback.queue[Playback.index].url

    private fun showOverlay(text: String) {
        infoText.text = tr(text)
        infoText.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, 3500)
    }

    private fun handleError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        // Network hiccups and provider refusals (odd HTTP codes such as 458) are often gone a
        // moment later, typically once the provider has noticed the previous stream closed.
        // A live channel that won't start in this quality may still work in another one.
        if (live && liveRetries >= 3) {
            val alts = Playback.queue[Playback.index].alternatives
            if (liveAlt + 1 < alts.size) {
                liveAlt++
                startLive()
                return
            }
        }
        val network = error.errorCode in 2000..2999
        if ((live || network) && liveRetries < 3) {
            liveRetries++
            showOverlay(if (live) "Reconnecting… ($liveRetries/3)" else "Trying again… ($liveRetries/3)")
            handler.postDelayed({ player.prepare(); player.playWhenReady = true }, 2000L * liveRetries)
            return
        }
        val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val message = when {
            http != null && http in 450..499 && http != 451 ->
                "Your provider refused this stream (HTTP $http).\n\nThis is usually its connection limit: another " +
                    "device or a stream that only just closed still counts. Wait a few seconds and retry. " +
                    "A VPN that changed server can cause it too."
            http == 404 -> "The provider no longer has this file (HTTP 404)."
            http != null -> "The provider answered HTTP $http."
            else -> "${error.errorCodeName}\n${error.cause?.message ?: error.message ?: ""}".trim()
        }
        errorDialog?.dismiss()
        errorDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(tr("Can't play this stream"))
            .setMessage(tr(message))
            .setPositiveButton(tr("Retry")) { _, _ -> liveRetries = 0; player.prepare(); player.playWhenReady = true }
            .setNeutralButton(tr("Open in other player")) { _, _ -> openExternal() }
            .setNegativeButton(tr("Close")) { _, _ -> finish() }
            .show()
    }

    private fun openExternal() {
        val url = currentUrl()
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
        try {
            startActivity(Intent.createChooser(intent, tr("Open with")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, tr("No other video player installed (try VLC)"), Toast.LENGTH_LONG).show()
        }
    }

    /** Up / down during a film or episode: move the sound against the picture. */
    private fun nudgeAudio(stepMs: Int) {
        store.setAudioOffset(store.audioOffset.value + stepMs)
        val v = store.audioOffset.value
        showOverlay(
            when {
                v == 0 -> "Audio in sync (0 ms)"
                v > 0 -> "Audio +$v ms  ·  sound later"
                else -> "Audio $v ms  ·  sound earlier"
            }
        )
    }

    // --- skip intro / next episode (the ☰ menu button) -----------------------------------

    private enum class Hint { NONE, NEXT, SKIP, MARK_END, TEACH }
    private var hint = Hint.NONE
    private var introStartMark = -1L

    private val hintTicker = object : Runnable {
        override fun run() {
            updateHint()
            handler.postDelayed(this, 500)
        }
    }

    private fun currentItem(): Playback.Item? = Playback.queue.getOrNull(Playback.index)

    private fun updateHint() {
        if (live || !::player.isInitialized) return
        val item = currentItem()
        val pos = player.currentPosition
        val dur = player.duration
        val marks = item?.introKey?.let { store.introMark(it) }
        val menu = "\u2630"
        val (state, text) = when {
            dur > 0 && player.hasNextMediaItem() && dur - pos in 1L..60_000L ->
                Hint.NEXT to "Next episode   $menu"
            marks != null && pos >= marks.first - 2_000 && pos < marks.second - 2_000 ->
                Hint.SKIP to "Skip intro   $menu"
            item?.introKey != null && introStartMark >= 0 ->
                Hint.MARK_END to "Press $menu again when the intro ends"
            item?.introKey != null && marks == null && pos in 3_000L..25_000L ->
                Hint.TEACH to "Press $menu when the intro starts to skip it next time"
            else -> Hint.NONE to ""
        }
        hint = state
        if (state == Hint.NONE) {
            hintText.visibility = View.GONE
        } else {
            hintText.text = tr(text)
            hintText.visibility = View.VISIBLE
        }
    }

    /** ☰ on the remote: next episode, skip the intro, or teach where the intro is. */
    private fun onMenuKey(): Boolean {
        if (live) return false
        val item = currentItem() ?: return false
        val pos = player.currentPosition
        when (hint) {
            Hint.NEXT -> player.seekToNextMediaItem()
            Hint.SKIP -> item.introKey?.let { store.introMark(it) }?.let { player.seekTo(it.second) }
            Hint.MARK_END -> {
                val key = item.introKey ?: return false
                val length = pos - introStartMark
                if (length in 5_000L..300_000L) {
                    store.setIntroMark(key, introStartMark, pos)
                    showOverlay("Intro saved for this season")
                } else {
                    showOverlay("That doesn't look like an intro, try again")
                }
                introStartMark = -1
            }
            else -> {
                // Anywhere in the first 15 minutes: (re)teach where this season's intro starts.
                if (item.introKey == null || pos > 15 * 60_000) return false
                introStartMark = pos
            }
        }
        updateHint()
        return true
    }

    private var centerHandled = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // OK / centre: pause, and again to resume, without going through the on-screen controls.
        val isCenter = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (isCenter) {
            if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
                if (event.repeatCount == 0) {
                    centerHandled = true
                    if (player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                        player.pause()
                        playerView.showController()
                    } else {
                        player.play()
                        playerView.hideController()
                    }
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && centerHandled) {
                centerHandled = false
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> if (live) {
                    playLive(Playback.index - 1); return true
                } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    nudgeAudio(UserStore.AUDIO_OFFSET_STEP); return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (live) {
                    playLive(Playback.index + 1); return true
                } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    nudgeAudio(-UserStore.AUDIO_OFFSET_STEP); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!live) {
                    player.seekBack(); playerView.showController(); return true
                } else if (switchQuality(-1)) return true
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!live) {
                    player.seekForward(); playerView.showController(); return true
                } else if (switchQuality(1)) return true
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && onMenuKey()) return true
            if (event.action == KeyEvent.ACTION_UP && !live) return true
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
            handler.post(hintTicker)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(saveTicker)
        handler.removeCallbacks(hintTicker)
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
        /** Size of one jump back / forward. */
        const val JUMP_MS = 60_000L
        private const val ZAP_DELAY_MS = 600L

        fun formatTime(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
            else "%d:%02d".format(s / 60, s % 60)
        }
    }
}

/**
 * Renderers whose audio clock can be shifted, to correct sound that runs ahead of or
 * behind the picture. The video follows the audio clock, so reporting the audio
 * position a little later or earlier moves one against the other.
 */
@OptIn(markerClass = [UnstableApi::class])
private class OffsetRenderers(context: Context, private val offsetMs: () -> Int) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        val base = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams) ?: return null
        return object : ForwardingAudioSink(base) {
            override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
                val p = super.getCurrentPositionUs(sourceEnded)
                return if (p == AudioSink.CURRENT_POSITION_NOT_SET) p else p + offsetMs() * 1000L
            }
        }
    }
}
