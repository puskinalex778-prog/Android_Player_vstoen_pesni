package com.etelevizor.iptv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.etelevizor.iptv.data.AppPreferences
import com.etelevizor.iptv.model.AspectRatioMode
import com.etelevizor.iptv.model.PlayerEngineType
import com.etelevizor.iptv.model.StreamStats
import com.etelevizor.iptv.model.TrackOption
import okhttp3.OkHttpClient
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.TimeUnit

interface DualPlayerListener {
    fun onBuffering(isBuffering: Boolean)
    fun onPlayingChanged(isPlaying: Boolean)
    fun onPlaybackEnded()
    fun onStreamStats(stats: StreamStats)
    fun onError(message: String, willRetry: Boolean, switchedToLibVlc: Boolean)
    fun onEngineChanged(engine: PlayerEngineType)
}

@OptIn(UnstableApi::class)
class DualPlayerEngine(
    private val context: Context,
    private val prefs: AppPreferences,
    private val exoPlayerView: PlayerView,
    private val vlcVideoLayout: VLCVideoLayout,
    private val listener: DualPlayerListener
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private var libVLC: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null

    var activeEngine: PlayerEngineType = prefs.playerEngine
        private set

    private var currentUrl: String = ""
    private var currentUserAgent: String = ""
    private var currentReferer: String = ""
    private var currentAspectRatio: AspectRatioMode = prefs.defaultAspectRatio
    private var currentSpeed: Float = 1.0f
    private var retryCount: Int = 0
    private var hasTriedFallback: Boolean = false
    private var isReleased: Boolean = false

    private var retryRunnable: Runnable? = null

    fun play(
        url: String,
        userAgent: String = "",
        referer: String = "",
        preferredEngine: PlayerEngineType? = null,
        resumeMs: Long = 0L
    ) {
        cancelPendingRetry()
        currentUrl = url
        currentUserAgent = userAgent.ifBlank { prefs.defaultUserAgent }
        currentReferer = referer
        retryCount = 0
        hasTriedFallback = false

        val engine = preferredEngine ?: prefs.playerEngine
        if (engine == PlayerEngineType.EXTERNAL) {
            openInExternalPlayer(context, url, "")
            return
        }

        // Automatically prefer LibVLC for protocols ExoPlayer cannot natively handle (udp://, rtmp://, mms://)
        val lower = url.lowercase()
        val chosen = if (engine == PlayerEngineType.EXOPLAYER &&
            (lower.startsWith("udp://") || lower.startsWith("rtp://") ||
                lower.startsWith("rtmp://") || lower.startsWith("mms://"))
        ) {
            hasTriedFallback = true
            PlayerEngineType.LIBVLC
        } else {
            engine
        }

        startWithEngine(chosen, resumeMs)
    }

    fun switchEngine(engine: PlayerEngineType) {
        if (engine == PlayerEngineType.EXTERNAL) {
            openInExternalPlayer(context, currentUrl, "")
            return
        }
        val pos = getCurrentPositionMs()
        cancelPendingRetry()
        retryCount = 0
        hasTriedFallback = (engine == PlayerEngineType.LIBVLC)
        startWithEngine(engine, if (isSeekable()) pos else 0L)
    }

    private fun startWithEngine(engine: PlayerEngineType, resumeMs: Long) {
        if (isReleased || currentUrl.isBlank()) return
        stopInternal()
        activeEngine = engine
        listener.onEngineChanged(engine)
        listener.onBuffering(true)

        when (engine) {
            PlayerEngineType.EXOPLAYER -> startExoPlayer(resumeMs)
            PlayerEngineType.LIBVLC -> startLibVlc(resumeMs)
            PlayerEngineType.EXTERNAL -> openInExternalPlayer(context, currentUrl, "")
        }
    }

    // ==================== EXOPLAYER (MEDIA3) ====================
    private fun startExoPlayer(resumeMs: Long) {
        vlcVideoLayout.visibility = View.GONE
        exoPlayerView.visibility = View.VISIBLE

        val timeoutSec = prefs.connectionTimeoutSec.coerceIn(5, 60).toLong()
        val headers = HashMap<String, String>()
        headers["User-Agent"] = currentUserAgent
        if (currentReferer.isNotBlank()) headers["Referer"] = currentReferer

        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec * 2, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val okFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(currentUserAgent)
            .setDefaultRequestProperties(headers)

        val bufMs = prefs.networkBufferMs.coerceIn(500, 15000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufMs,
                (bufMs * 4).coerceAtLeast(15000),
                (bufMs / 2).coerceAtLeast(500),
                bufMs.coerceAtLeast(1000)
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            val extMode = if (prefs.hwAcceleration == "software") {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            } else {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
            setExtensionRendererMode(extMode)
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(okFactory))
            .setLoadControl(loadControl)
            .build()

        // Preferred audio & subtitle language
        val trackParams = player.trackSelectionParameters.buildUpon()
        when (prefs.preferredAudioLang) {
            "ru" -> trackParams.setPreferredAudioLanguages("ru", "rus")
            "en" -> trackParams.setPreferredAudioLanguages("en", "eng")
        }
        when (prefs.preferredSubtitleLang) {
            "off" -> trackParams.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            "ru" -> {
                trackParams.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                trackParams.setPreferredTextLanguages("ru", "rus")
            }
            "en" -> {
                trackParams.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                trackParams.setPreferredTextLanguages("en", "eng")
            }
        }
        player.trackSelectionParameters = trackParams.build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> listener.onBuffering(true)
                    Player.STATE_READY -> {
                        retryCount = 0
                        listener.onBuffering(false)
                        emitExoStats(player)
                    }
                    Player.STATE_ENDED -> {
                        listener.onBuffering(false)
                        listener.onPlaybackEnded()
                    }
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener.onPlayingChanged(isPlaying)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                emitExoStats(player)
            }

            override fun onTracksChanged(tracks: Tracks) {
                emitExoStats(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackFailure(error.localizedMessage ?: "Ошибка потока ExoPlayer")
            }
        })

        exoPlayer = player
        exoPlayerView.player = player
        applyAspectRatio(currentAspectRatio)

        val mediaItem = MediaItem.fromUri(Uri.parse(currentUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        if (resumeMs > 0L) player.seekTo(resumeMs)
        if (currentSpeed != 1.0f) player.playbackParameters = PlaybackParameters(currentSpeed)
        player.playWhenReady = true
    }

    private fun emitExoStats(player: ExoPlayer) {
        val vf = player.videoFormat
        val af = player.audioFormat
        val stats = StreamStats(
            width = vf?.width ?: player.videoSize.width,
            height = vf?.height ?: player.videoSize.height,
            videoCodec = simplifyCodec(vf?.sampleMimeType ?: vf?.codecs.orEmpty()),
            audioCodec = simplifyCodec(af?.sampleMimeType ?: af?.codecs.orEmpty()),
            fps = vf?.frameRate?.takeIf { it > 0f } ?: 0f,
            engineName = "ExoPlayer"
        )
        listener.onStreamStats(stats)
    }

    // ==================== LIBVLC ====================
    private fun startLibVlc(resumeMs: Long) {
        exoPlayerView.visibility = View.GONE
        vlcVideoLayout.visibility = View.VISIBLE

        val args = arrayListOf(
            "--network-caching=${prefs.networkBufferMs.coerceIn(500, 15000)}",
            "--live-caching=${prefs.networkBufferMs.coerceIn(500, 15000)}",
            "--http-reconnect",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--http-user-agent=$currentUserAgent"
        )
        if (prefs.deinterlace) {
            args.add("--deinterlace=1")
            args.add("--deinterlace-mode=blend")
        }
        if (prefs.audioPassthrough) {
            args.add("--audio-resampler=soxr")
        }
        if (prefs.hwAcceleration == "software") {
            args.add("--avcodec-hw=none")
        }

        val vlc = LibVLC(context, args)
        val mp = MediaPlayer(vlc)
        mp.attachViews(vlcVideoLayout, null, true, false)

        mp.setEventListener { event ->
            mainHandler.post {
                if (isReleased || vlcPlayer !== mp) return@post
                when (event.type) {
                    MediaPlayer.Event.Buffering -> {
                        listener.onBuffering(event.buffering < 99.5f)
                    }
                    MediaPlayer.Event.Playing -> {
                        retryCount = 0
                        listener.onBuffering(false)
                        listener.onPlayingChanged(true)
                        if (prefs.softwareVolumeBoost > 100) {
                            mp.setVolume(prefs.softwareVolumeBoost)
                        }
                        emitVlcStats(mp)
                    }
                    MediaPlayer.Event.Paused -> {
                        listener.onPlayingChanged(false)
                    }
                    MediaPlayer.Event.EndReached -> {
                        listener.onBuffering(false)
                        listener.onPlaybackEnded()
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        handlePlaybackFailure("Ошибка воспроизведения LibVLC")
                    }
                    MediaPlayer.Event.Vout -> {
                        emitVlcStats(mp)
                    }
                }
            }
        }

        val media = Media(vlc, Uri.parse(currentUrl)).apply {
            when (prefs.hwAcceleration) {
                "software" -> setHWDecoderEnabled(false, false)
                "decoding" -> setHWDecoderEnabled(true, false)
                else -> setHWDecoderEnabled(true, true)
            }
            addOption(":network-caching=${prefs.networkBufferMs}")
            addOption(":http-user-agent=$currentUserAgent")
            if (currentReferer.isNotBlank()) {
                addOption(":http-referrer=$currentReferer")
            }
            if (resumeMs > 1000L) {
                addOption(":start-time=${resumeMs / 1000L}")
            }
        }

        libVLC = vlc
        vlcPlayer = mp
        mp.media = media
        media.release()
        applyAspectRatio(currentAspectRatio)
        mp.rate = currentSpeed
        mp.play()
    }

    private fun emitVlcStats(mp: MediaPlayer) {
        val vTrack = mp.currentVideoTrack
        val stats = StreamStats(
            width = vTrack?.width ?: 0,
            height = vTrack?.height ?: 0,
            videoCodec = vTrack?.codec.orEmpty().uppercase(),
            audioCodec = "",
            fps = if (vTrack != null && vTrack.frameRateDen > 0) {
                vTrack.frameRateNum.toFloat() / vTrack.frameRateDen.toFloat()
            } else 0f,
            engineName = "LibVLC"
        )
        listener.onStreamStats(stats)
    }

    // ==================== ERROR HANDLING, FALLBACK & AUTO-RECONNECT ====================
    private fun handlePlaybackFailure(message: String) {
        if (isReleased) return
        listener.onBuffering(false)

        // 1. Try automatic fallback from ExoPlayer -> LibVLC
        if (activeEngine == PlayerEngineType.EXOPLAYER && prefs.autoFallbackToLibVlc && !hasTriedFallback) {
            hasTriedFallback = true
            listener.onError(message, willRetry = true, switchedToLibVlc = true)
            startWithEngine(PlayerEngineType.LIBVLC, 0L)
            return
        }

        // 2. Auto-reconnect if configured
        if (prefs.autoReconnect && retryCount < prefs.reconnectAttempts) {
            retryCount++
            listener.onError(
                "$message (попытка $retryCount/${prefs.reconnectAttempts})",
                willRetry = true,
                switchedToLibVlc = false
            )
            cancelPendingRetry()
            val runnable = Runnable {
                if (!isReleased) {
                    startWithEngine(activeEngine, 0L)
                }
            }
            retryRunnable = runnable
            mainHandler.postDelayed(runnable, prefs.reconnectDelaySec * 1000L)
        } else {
            listener.onError(message, willRetry = false, switchedToLibVlc = false)
        }
    }

    private fun cancelPendingRetry() {
        retryRunnable?.let { mainHandler.removeCallbacks(it) }
        retryRunnable = null
    }

    // ==================== PLAYBACK CONTROLS ====================
    fun togglePlayPause(): Boolean {
        return when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> {
                val p = exoPlayer ?: return false
                if (p.isPlaying) {
                    p.pause(); false
                } else {
                    p.play(); true
                }
            }
            PlayerEngineType.LIBVLC -> {
                val p = vlcPlayer ?: return false
                if (p.isPlaying) {
                    p.pause(); false
                } else {
                    p.play(); true
                }
            }
            PlayerEngineType.EXTERNAL -> false
        }
    }

    fun isPlaying(): Boolean = when (activeEngine) {
        PlayerEngineType.EXOPLAYER -> exoPlayer?.isPlaying == true
        PlayerEngineType.LIBVLC -> vlcPlayer?.isPlaying == true
        PlayerEngineType.EXTERNAL -> false
    }

    fun getCurrentPositionMs(): Long = when (activeEngine) {
        PlayerEngineType.EXOPLAYER -> exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        PlayerEngineType.LIBVLC -> vlcPlayer?.time?.coerceAtLeast(0L) ?: 0L
        PlayerEngineType.EXTERNAL -> 0L
    }

    fun getDurationMs(): Long = when (activeEngine) {
        PlayerEngineType.EXOPLAYER -> exoPlayer?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        PlayerEngineType.LIBVLC -> vlcPlayer?.length?.takeIf { it > 0L } ?: 0L
        PlayerEngineType.EXTERNAL -> 0L
    }

    fun isSeekable(): Boolean {
        val dur = getDurationMs()
        if (dur <= 0L) return false
        return when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> exoPlayer?.isCurrentMediaItemSeekable == true
            PlayerEngineType.LIBVLC -> vlcPlayer?.isSeekable == true
            PlayerEngineType.EXTERNAL -> false
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> exoPlayer?.seekTo(target)
            PlayerEngineType.LIBVLC -> vlcPlayer?.setTime(target, false)
            PlayerEngineType.EXTERNAL -> Unit
        }
    }

    fun seekBy(deltaMs: Long) {
        val dur = getDurationMs()
        val target = (getCurrentPositionMs() + deltaMs).let {
            if (dur > 0L) it.coerceIn(0L, dur) else it.coerceAtLeast(0L)
        }
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> exoPlayer?.playbackParameters = PlaybackParameters(speed)
            PlayerEngineType.LIBVLC -> vlcPlayer?.rate = speed
            PlayerEngineType.EXTERNAL -> Unit
        }
    }

    fun setVlcSoftwareVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 200)
        vlcPlayer?.setVolume(clamped)
    }

    fun applyAspectRatio(mode: AspectRatioMode) {
        currentAspectRatio = mode
        // ExoPlayer
        when (mode) {
            AspectRatioMode.FIT -> exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL -> exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.ZOOM -> exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.RATIO_16_9 -> exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            AspectRatioMode.RATIO_4_3 -> exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            else -> exoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        // LibVLC
        vlcPlayer?.let { mp ->
            mp.videoScale = when (mode) {
                AspectRatioMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                AspectRatioMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
                AspectRatioMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                AspectRatioMode.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
                AspectRatioMode.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
                AspectRatioMode.RATIO_16_10 -> MediaPlayer.ScaleType.SURFACE_16_9
                AspectRatioMode.RATIO_21_9 -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                AspectRatioMode.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
            }
        }
    }

    fun getAspectRatio(): AspectRatioMode = currentAspectRatio

    // ==================== AUDIO & SUBTITLE TRACKS ====================
    fun getAudioTracks(): List<TrackOption> {
        return when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> getExoTracks(C.TRACK_TYPE_AUDIO)
            PlayerEngineType.LIBVLC -> {
                val mp = vlcPlayer ?: return emptyList()
                val current = mp.audioTrack
                mp.audioTracks?.map { t ->
                    TrackOption(
                        id = "vlc_a_${t.id}",
                        label = t.name ?: "Дорожка ${t.id}",
                        isSelected = t.id == current,
                        vlcTrackId = t.id
                    )
                } ?: emptyList()
            }
            PlayerEngineType.EXTERNAL -> emptyList()
        }
    }

    fun selectAudioTrack(option: TrackOption) {
        when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> selectExoTrack(C.TRACK_TYPE_AUDIO, option)
            PlayerEngineType.LIBVLC -> vlcPlayer?.setAudioTrack(option.vlcTrackId)
            PlayerEngineType.EXTERNAL -> Unit
        }
    }

    fun getSubtitleTracks(): List<TrackOption> {
        val list = mutableListOf<TrackOption>()
        when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> {
                val p = exoPlayer ?: return emptyList()
                val disabled = p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                list.add(TrackOption(id = "off", label = "Отключить субтитры", isSelected = disabled, groupIndex = -1, trackIndex = -1))
                list.addAll(getExoTracks(C.TRACK_TYPE_TEXT))
            }
            PlayerEngineType.LIBVLC -> {
                val mp = vlcPlayer ?: return emptyList()
                val current = mp.spuTrack
                mp.spuTracks?.forEach { t ->
                    list.add(
                        TrackOption(
                            id = "vlc_s_${t.id}",
                            label = if (t.id == -1) "Отключить субтитры" else (t.name ?: "Субтитры ${t.id}"),
                            isSelected = t.id == current,
                            vlcTrackId = t.id
                        )
                    )
                }
            }
            PlayerEngineType.EXTERNAL -> Unit
        }
        return list
    }

    fun selectSubtitleTrack(option: TrackOption) {
        when (activeEngine) {
            PlayerEngineType.EXOPLAYER -> {
                val p = exoPlayer ?: return
                if (option.id == "off") {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                    selectExoTrack(C.TRACK_TYPE_TEXT, option)
                }
            }
            PlayerEngineType.LIBVLC -> vlcPlayer?.setSpuTrack(option.vlcTrackId)
            PlayerEngineType.EXTERNAL -> Unit
        }
    }

    private fun getExoTracks(trackType: Int): List<TrackOption> {
        val p = exoPlayer ?: return emptyList()
        val result = mutableListOf<TrackOption>()
        val groups = p.currentTracks.groups
        for (gIdx in 0 until groups.size) {
            val group = groups[gIdx]
            if (group.type != trackType) continue
            for (tIdx in 0 until group.length) {
                val fmt = group.getTrackFormat(tIdx)
                val lang = fmt.language.orEmpty()
                val labelParts = mutableListOf<String>()
                if (!fmt.label.isNullOrBlank()) labelParts.add(fmt.label!!)
                if (lang.isNotBlank()) labelParts.add(lang.uppercase())
                if (trackType == C.TRACK_TYPE_AUDIO && fmt.channelCount > 0) {
                    labelParts.add("${fmt.channelCount}ch")
                }
                val codec = simplifyCodec(fmt.sampleMimeType ?: "")
                if (codec.isNotBlank()) labelParts.add(codec)
                val label = labelParts.distinct().joinToString(" • ")
                    .ifBlank { "Дорожка ${result.size + 1}" }

                result.add(
                    TrackOption(
                        id = "exo_${gIdx}_$tIdx",
                        label = label,
                        language = lang,
                        isSelected = group.isTrackSelected(tIdx),
                        groupIndex = gIdx,
                        trackIndex = tIdx
                    )
                )
            }
        }
        return result
    }

    private fun selectExoTrack(trackType: Int, option: TrackOption) {
        val p = exoPlayer ?: return
        val groups = p.currentTracks.groups
        if (option.groupIndex !in 0 until groups.size) return
        val group = groups[option.groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(override)
            .build()
    }

    private fun simplifyCodec(mime: String): String {
        val m = mime.lowercase()
        return when {
            m.contains("hevc") || m.contains("h265") -> "HEVC"
            m.contains("avc") || m.contains("h264") -> "H264"
            m.contains("vp9") -> "VP9"
            m.contains("av01") || m.contains("av1") -> "AV1"
            m.contains("mpeg2") -> "MPEG2"
            m.contains("eac3") -> "EAC3"
            m.contains("ac3") -> "AC3"
            m.contains("aac") || m.contains("mp4a") -> "AAC"
            m.contains("dts") -> "DTS"
            m.contains("opus") -> "OPUS"
            m.contains("mpeg") -> "MP2/MP3"
            else -> ""
        }
    }

    fun pause() {
        exoPlayer?.pause()
        vlcPlayer?.pause()
    }

    private fun stopInternal() {
        exoPlayer?.let {
            exoPlayerView.player = null
            it.stop()
            it.release()
        }
        exoPlayer = null

        vlcPlayer?.let {
            it.stop()
            it.detachViews()
            it.release()
        }
        vlcPlayer = null

        libVLC?.release()
        libVLC = null
    }

    fun release() {
        isReleased = true
        cancelPendingRetry()
        stopInternal()
    }

    companion object {
        fun openInExternalPlayer(context: Context, url: String, title: String) {
            if (url.isBlank()) return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                putExtra("title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(Intent.createChooser(intent, "Открыть в внешнем плеере"))
            } catch (_: Exception) {
                // No external player installed
            }
        }
    }
}
