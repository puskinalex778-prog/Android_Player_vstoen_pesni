package com.etelevizor.iptv.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.etelevizor.iptv.ETelevizorApp
import com.etelevizor.iptv.R
import com.etelevizor.iptv.data.CatchupUrlBuilder
import com.etelevizor.iptv.databinding.ActivityPlayerBinding
import com.etelevizor.iptv.model.*
import com.etelevizor.iptv.ui.BaseActivity
import com.etelevizor.iptv.ui.ChannelAdapter
import com.etelevizor.iptv.ui.Dialogs
import com.etelevizor.iptv.ui.EpgProgramAdapter
import com.etelevizor.iptv.ui.SettingsActivity
import com.etelevizor.iptv.util.DeviceModeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PlayerActivity : BaseActivity(), DualPlayerListener {

    override val isPlayerTheme: Boolean = true

    companion object {
        // Shared in-memory channel list to avoid Android Binder 1MB TransactionTooLargeException on 10,000+ IPTV channels!
        @Volatile
        var sharedChannelList: List<Channel> = emptyList()

        private const val EXTRA_CHANNEL_INDEX = "extra_channel_index"
        private const val EXTRA_FORCE_ENGINE = "extra_force_engine"
        private const val EXTRA_ARCHIVE_URL = "extra_archive_url"
        private const val EXTRA_ARCHIVE_TITLE = "extra_archive_title"
        private const val EXTRA_SINGLE_TITLE = "extra_single_title"
        private const val EXTRA_SINGLE_URL = "extra_single_url"

        fun createChannelListIntent(
            context: Context,
            channels: List<Channel>,
            index: Int,
            forceEngine: PlayerEngineType? = null
        ): Intent {
            sharedChannelList = channels
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_INDEX, index.coerceIn(0, (channels.size - 1).coerceAtLeast(0)))
                if (forceEngine != null) putExtra(EXTRA_FORCE_ENGINE, forceEngine.key)
            }
        }

        fun createArchiveIntent(
            context: Context,
            channel: Channel,
            archiveUrl: String,
            programTitle: String
        ): Intent {
            sharedChannelList = listOf(channel)
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_INDEX, 0)
                putExtra(EXTRA_ARCHIVE_URL, archiveUrl)
                putExtra(EXTRA_ARCHIVE_TITLE, programTitle)
            }
        }

        fun createEpisodeIntent(
            context: Context,
            title: String,
            streamUrl: String,
            logoUrl: String = "",
            userAgent: String = "",
            referer: String = ""
        ): Intent {
            val ch = Channel(
                id = "ep_${streamUrl.hashCode()}",
                playlistId = "",
                number = 1,
                name = title,
                streamUrl = streamUrl,
                logoUrl = logoUrl,
                userAgent = userAgent,
                referer = referer,
                contentType = ContentType.MOVIE
            )
            sharedChannelList = listOf(ch)
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_INDEX, 0)
                putExtra(EXTRA_SINGLE_TITLE, title)
                putExtra(EXTRA_SINGLE_URL, streamUrl)
            }
        }
    }

    private lateinit var binding: ActivityPlayerBinding
    private val app get() = ETelevizorApp.instance
    private val prefs get() = app.prefs

    private lateinit var engine: DualPlayerEngine
    private lateinit var audioManager: AudioManager
    private lateinit var gestureHelper: PlayerGestureHelper

    private val handler = Handler(Looper.getMainLooper())
    private var channels: List<Channel> = emptyList()
    private var currentIndex: Int = 0
    private var isArchiveMode: Boolean = false
    private var archiveTitle: String = ""
    private var timeshiftAnchorEpochSec: Long = 0L
    private var epgDrawerArchiveTab: Boolean = false
    private var cachedDrawerPrograms: List<EpgProgram> = emptyList()

    private var isOsdVisible = true
    private var isScreenLocked = false
    private var isSeeking = false
    private var lastBackPressMs = 0L
    private var currentBrightness = 0.5f

    // Numeric remote channel input buffer (0-9)
    private val numericBuffer = StringBuilder()
    private var numericRunnable: Runnable? = null

    // Sleep timer
    private var sleepEndMs: Long = 0L

    private var drawerChannelAdapter: ChannelAdapter? = null

    private val hideOsdRunnable = Runnable { setOsdVisible(false) }
    private val hideGesturePillRunnable = Runnable { binding.tvGesturePill.visibility = View.GONE }

    private val uiTickRunnable = object : Runnable {
        override fun run() {
            updateClockAndProgress()
            checkSleepTimer()
            handler.postDelayed(this, 1000L)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceModeHelper.applyOrientation(this, prefs, isPlayer = true)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        hideSystemBars()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        channels = sharedChannelList
        if (channels.isEmpty()) {
            Toast.makeText(this, "Нет канала для воспроизведения", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentIndex = intent.getIntExtra(EXTRA_CHANNEL_INDEX, 0).coerceIn(0, channels.lastIndex)

        engine = DualPlayerEngine(
            context = this,
            prefs = prefs,
            exoPlayerView = binding.exoPlayerView,
            vlcVideoLayout = binding.vlcVideoLayout,
            listener = this
        )

        setupGestures()
        setupButtons()
        setupSeekBar()
        setupDrawers()

        // Initialize default sleep timer if configured
        if (prefs.defaultSleepTimerMinutes > 0) {
            setSleepTimer(prefs.defaultSleepTimerMinutes)
        }

        val forceEngine = intent.getStringExtra(EXTRA_FORCE_ENGINE)?.let { PlayerEngineType.fromKey(it) }
        val archiveUrl = intent.getStringExtra(EXTRA_ARCHIVE_URL)
        val archTitle = intent.getStringExtra(EXTRA_ARCHIVE_TITLE)

        if (!archiveUrl.isNullOrBlank()) {
            playArchiveStream(channels[currentIndex], archiveUrl, archTitle.orEmpty())
        } else {
            playChannelAt(currentIndex, forceEngine)
        }
    }

    override fun onStart() {
        super.onStart()
        handler.post(uiTickRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(uiTickRunnable)
        saveVodProgressIfNeeded()
        if (!isInPictureInPictureMode) {
            engine.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        engine.release()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (prefs.enablePip && !DeviceModeHelper.isTvMode(this, prefs) && engine.isPlaying()) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            setOsdVisible(false)
            closeDrawers()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ==================== GESTURES (PHONE TOUCH) ====================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureHelper = PlayerGestureHelper(this, object : PlayerGestureCallbacks {
            override fun onSingleTap() {
                if (isScreenLocked) {
                    binding.btnUnlockScreen.visibility = View.VISIBLE
                    handler.postDelayed({ if (isScreenLocked) binding.btnUnlockScreen.visibility = View.GONE }, 2500L)
                    return
                }
                if (isAnyDrawerOpen()) {
                    closeDrawers()
                    return
                }
                setOsdVisible(!isOsdVisible)
            }

            override fun onDoubleTapLeft() {
                if (isScreenLocked) return
                if (!prefs.doubleTapChannelSwitch || isArchiveMode || currentChannel().contentType != ContentType.LIVE) {
                    performSmartSeek(-prefs.seekStepSeconds)
                } else {
                    switchChannelBy(-1)
                }
            }

            override fun onDoubleTapCenter() {
                if (isScreenLocked) return
                val playing = engine.togglePlayPause()
                showGesturePill(if (playing) "▶ Воспроизведение" else "⏸ Пауза")
            }

            override fun onDoubleTapRight() {
                if (isScreenLocked) return
                if (!prefs.doubleTapChannelSwitch || isArchiveMode || currentChannel().contentType != ContentType.LIVE) {
                    performSmartSeek(+prefs.seekStepSeconds)
                } else {
                    switchChannelBy(+1)
                }
            }

            override fun onBrightnessSlide(deltaPercent: Float) {
                if (isScreenLocked) return
                val lp = window.attributes
                val base = if (lp.screenBrightness < 0f) currentBrightness else lp.screenBrightness
                val next = (base + deltaPercent).coerceIn(0.02f, 1.0f)
                currentBrightness = next
                lp.screenBrightness = next
                window.attributes = lp
                showGesturePill("☀ Яркость ${(next * 100).roundToInt()}%")
            }

            override fun onVolumeSlide(deltaPercent: Float) {
                if (isScreenLocked) return
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val deltaSteps = (deltaPercent * maxVol * 1.4f).roundToInt()
                if (deltaSteps != 0) {
                    val next = (curVol + deltaSteps).coerceIn(0, maxVol)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    val pct = if (maxVol > 0) (next * 100) / maxVol else 0
                    showGesturePill("🔊 Громкость $pct%")
                }
            }

            override fun onSeekSlide(deltaMs: Long) {
                if (isScreenLocked || !engine.isSeekable()) return
                engine.seekBy(deltaMs)
                val pos = DeviceModeHelper.formatDuration(engine.getCurrentPositionMs())
                val dur = DeviceModeHelper.formatDuration(engine.getDurationMs())
                showGesturePill("⏩ $pos / $dur")
            }

            override fun onGestureEnd() = Unit
        })

        binding.touchOverlay.setOnTouchListener { v, event ->
            val enableGestures = !isScreenLocked &&
                prefs.phoneGesturesEnabled &&
                !DeviceModeHelper.isTvMode(this, prefs) &&
                !isAnyDrawerOpen()
            gestureHelper.onTouchEvent(event, v.width, v.height, enableGestures)
        }
    }

    private fun showGesturePill(text: String) {
        binding.tvGesturePill.text = text
        binding.tvGesturePill.visibility = View.VISIBLE
        handler.removeCallbacks(hideGesturePillRunnable)
        handler.postDelayed(hideGesturePillRunnable, 1300L)
    }

    // ==================== BUTTONS & OSD ACTIONS ====================
    private fun setupButtons() {
        binding.btnPlayerBack.setOnClickListener { finish() }
        binding.btnActionChannels.setOnClickListener { openChannelsDrawer() }
        binding.btnActionEpg.setOnClickListener { openEpgDrawer() }
        val step = prefs.seekStepSeconds
        binding.btnActionRewind.text = "⏪ -${step}с"
        binding.btnActionForward.text = "⏩ +${step}с"
        binding.btnActionRewind.setOnClickListener {
            performSmartSeek(-prefs.seekStepSeconds)
            scheduleHideOsd()
        }
        binding.btnActionForward.setOnClickListener {
            performSmartSeek(+prefs.seekStepSeconds)
            scheduleHideOsd()
        }
        binding.btnActionLiveNow.setOnClickListener {
            playChannelAt(currentIndex, null)
            showGesturePill("● Прямой эфир")
        }
        binding.btnActionPlayPause.setOnClickListener {
            val playing = engine.togglePlayPause()
            binding.btnActionPlayPause.text = if (playing) "Пауза" else "Играть"
            scheduleHideOsd()
        }
        binding.btnDrawerTabLive.setOnClickListener {
            epgDrawerArchiveTab = false
            renderEpgDrawerTab()
        }
        binding.btnDrawerTabArchive.setOnClickListener {
            epgDrawerArchiveTab = true
            renderEpgDrawerTab()
        }
        binding.btnActionFavorite.setOnClickListener {
            val ch = currentChannel()
            val added = prefs.toggleFavorite(ch.id)
            updateFavoriteButton(ch)
            Toast.makeText(
                this,
                if (added) "Добавлено в Избранное ★" else "Удалено из Избранного",
                Toast.LENGTH_SHORT
            ).show()
            scheduleHideOsd()
        }
        binding.btnActionAudio.setOnClickListener { showAudioTracksDialog() }
        binding.btnActionSubtitles.setOnClickListener { showSubtitlesDialog() }
        binding.btnActionAspect.setOnClickListener { showAspectRatioDialog() }
        binding.btnActionEngine.setOnClickListener { showEngineDialog() }
        binding.btnActionSleep.setOnClickListener {
            val remainingMin = if (sleepEndMs > System.currentTimeMillis()) {
                ((sleepEndMs - System.currentTimeMillis()) / 60_000L).toInt()
            } else 0
            Dialogs.showSleepTimerDialog(this, remainingMin) { min -> setSleepTimer(min) }
        }
        binding.btnActionPip.setOnClickListener { enterPipMode() }
        binding.btnActionLock.setOnClickListener {
            isScreenLocked = true
            setOsdVisible(false)
            binding.btnUnlockScreen.visibility = View.VISIBLE
            handler.postDelayed({ if (isScreenLocked) binding.btnUnlockScreen.visibility = View.GONE }, 2500L)
        }
        binding.btnUnlockScreen.setOnClickListener {
            isScreenLocked = false
            binding.btnUnlockScreen.visibility = View.GONE
            setOsdVisible(true)
        }
        binding.btnActionSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnCloseDrawerChannels.setOnClickListener { closeDrawers() }
        binding.btnCloseDrawerEpg.setOnClickListener { closeDrawers() }
        binding.btnReturnToLive.setOnClickListener {
            closeDrawers()
            playChannelAt(currentIndex, null)
        }

        if (DeviceModeHelper.isTvMode(this, prefs)) {
            binding.btnActionLock.visibility = View.GONE
            binding.btnActionPip.visibility = View.GONE
        }
    }

    private fun setupSeekBar() {
        binding.seekBarPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvSeekCurrent.text = DeviceModeHelper.formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                handler.removeCallbacks(hideOsdRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                seekBar?.let { engine.seekTo(it.progress.toLong()) }
                scheduleHideOsd()
            }
        })
    }

    private fun setupDrawers() {
        binding.rvDrawerChannels.layoutManager = LinearLayoutManager(this)
        drawerChannelAdapter = ChannelAdapter(
            prefs = prefs,
            repo = app.repository,
            viewStyle = ViewStyle.COMPACT,
            onClick = { _, pos ->
                closeDrawers()
                switchToChannelWithPinCheck(pos)
            },
            onLongClick = { ch ->
                val added = prefs.toggleFavorite(ch.id)
                Toast.makeText(
                    this,
                    if (added) "${ch.name} → Избранное ★" else "${ch.name} убран из Избранного",
                    Toast.LENGTH_SHORT
                ).show()
                drawerChannelAdapter?.notifyDataSetChanged()
            }
        )
        binding.rvDrawerChannels.adapter = drawerChannelAdapter
        binding.rvDrawerEpg.layoutManager = LinearLayoutManager(this)
    }

    // ==================== CHANNEL & ARCHIVE PLAYBACK ====================
    private fun currentChannel(): Channel = channels[currentIndex.coerceIn(0, channels.lastIndex)]

    private fun switchToChannelWithPinCheck(targetIndex: Int) {
        val safeIdx = targetIndex.coerceIn(0, channels.lastIndex)
        val target = channels[safeIdx]
        if (app.repository.isChannelLocked(target)) {
            Dialogs.showPinVerifyDialog(this, prefs.parentalPin, "Канал «${target.name}» защищён") {
                playChannelAt(safeIdx, null)
            }
        } else {
            playChannelAt(safeIdx, null)
        }
    }

    private fun switchChannelBy(delta: Int) {
        if (channels.size <= 1) return
        val effectiveDelta = if (prefs.invertChannelButtons) -delta else delta
        val nextIdx = (currentIndex + effectiveDelta + channels.size) % channels.size
        switchToChannelWithPinCheck(nextIdx)
    }

    private fun playChannelAt(index: Int, forceEngine: PlayerEngineType?) {
        saveVodProgressIfNeeded()
        currentIndex = index.coerceIn(0, channels.lastIndex)
        isArchiveMode = false
        archiveTitle = ""
        val ch = currentChannel()

        updateChannelOsdHeader(ch)
        setOsdVisible(true)

        val resumeMs = if (ch.contentType != ContentType.LIVE) prefs.getResumePosition(ch.id) else 0L
        engine.play(
            url = ch.streamUrl,
            userAgent = ch.userAgent,
            referer = ch.referer,
            preferredEngine = forceEngine,
            resumeMs = resumeMs
        )

        prefs.recordWatchHistory(
            WatchHistoryEntry(
                channelId = ch.id,
                channelName = ch.name,
                logoUrl = ch.logoUrl,
                streamUrl = ch.streamUrl,
                group = ch.group,
                playlistId = ch.playlistId,
                contentType = ch.contentType,
                timestamp = System.currentTimeMillis(),
                resumePositionMs = resumeMs
            )
        )
    }

    private fun playArchiveStream(ch: Channel, archiveUrl: String, programTitle: String) {
        isArchiveMode = true
        archiveTitle = programTitle
        updateChannelOsdHeader(ch)
        setOsdVisible(true)
        engine.play(
            url = archiveUrl,
            userAgent = ch.userAgent,
            referer = ch.referer,
            preferredEngine = null,
            resumeMs = 0L
        )
    }

    private fun updateChannelOsdHeader(ch: Channel) {
        binding.tvPlayerChannelNumber.visibility =
            if (prefs.showChannelNumbers && ch.contentType == ContentType.LIVE) View.VISIBLE else View.GONE
        binding.tvPlayerChannelNumber.text = ch.number.toString()
        binding.tvPlayerChannelName.text = ch.name

        if (ch.logoUrl.isNotBlank() && prefs.showChannelLogos) {
            Glide.with(this)
                .load(ch.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(binding.ivPlayerLogo)
        } else {
            binding.ivPlayerLogo.setImageResource(R.drawable.ic_channel_placeholder)
        }

        when {
            isArchiveMode -> {
                binding.tvPlayerBadge.text = "↺ АРХИВ"
                binding.tvPlayerBadge.setBackgroundResource(R.drawable.bg_badge_orange)
                binding.tvPlayerProgramNow.text = archiveTitle
                binding.tvPlayerProgramNext.text = "Архив / Таймшифт • Влево/Вправо: ±${prefs.seekStepSeconds}с • Кнопка «● В эфир» вернёт прямой эфир"
                binding.pbLiveEpg.visibility = View.GONE
                binding.vodSeekContainer.visibility = View.VISIBLE
                binding.btnActionLiveNow.visibility = View.VISIBLE
            }
            ch.contentType != ContentType.LIVE -> {
                binding.tvPlayerBadge.text = "🎬 VOD"
                binding.tvPlayerBadge.setBackgroundResource(R.drawable.bg_badge_archive)
                binding.tvPlayerProgramNow.text = ch.group
                binding.tvPlayerProgramNext.text = ch.plot.take(120)
                binding.pbLiveEpg.visibility = View.GONE
                binding.vodSeekContainer.visibility = View.VISIBLE
                binding.btnActionLiveNow.visibility = View.GONE
            }
            else -> {
                binding.tvPlayerBadge.text = "● В ЭФИРЕ"
                binding.tvPlayerBadge.setBackgroundResource(R.drawable.bg_badge_green)
                binding.vodSeekContainer.visibility = View.GONE
                binding.btnActionLiveNow.visibility = View.GONE
                updateLiveEpgInfo(ch)
            }
        }

        updateFavoriteButton(ch)
        binding.tvPlayerClock.visibility = if (prefs.showPlayerClock) View.VISIBLE else View.GONE
    }

    private fun updateLiveEpgInfo(ch: Channel) {
        val nowNext = app.repository.getNowNextCached(ch)
        val cur = nowNext.current
        if (cur != null) {
            val range = "${DeviceModeHelper.formatTime(cur.startTimeMs)}–${DeviceModeHelper.formatTime(cur.endTimeMs)}"
            binding.tvPlayerProgramNow.text = "$range  ${cur.title}"
            binding.pbLiveEpg.visibility = if (prefs.showEpgProgress) View.VISIBLE else View.GONE
            binding.pbLiveEpg.progress = cur.progressPercent()
        } else {
            binding.tvPlayerProgramNow.text = ch.group
            binding.pbLiveEpg.visibility = View.GONE
        }

        val nxt = nowNext.next
        if (nxt != null && prefs.showNextProgram) {
            binding.tvPlayerProgramNext.text = "Далее ${DeviceModeHelper.formatTime(nxt.startTimeMs)}: ${nxt.title}"
            binding.tvPlayerProgramNext.visibility = View.VISIBLE
        } else {
            binding.tvPlayerProgramNext.visibility = View.GONE
        }
    }

    private fun updateFavoriteButton(ch: Channel) {
        binding.btnActionFavorite.text = if (prefs.isFavorite(ch.id)) "★ В избранном" else "☆ Избранное"
    }

    private fun updateClockAndProgress() {
        binding.tvPlayerClock.text = DeviceModeHelper.formatTime(System.currentTimeMillis())

        val ch = currentChannel()
        if (!isArchiveMode && ch.contentType == ContentType.LIVE) {
            val nowNext = app.repository.getNowNextCached(ch)
            nowNext.current?.let { binding.pbLiveEpg.progress = it.progressPercent() }
        } else if (!isSeeking && engine.isSeekable()) {
            val dur = engine.getDurationMs()
            val pos = engine.getCurrentPositionMs()
            if (dur > 0L) {
                binding.vodSeekContainer.visibility = View.VISIBLE
                binding.seekBarPlayer.max = dur.toInt()
                binding.seekBarPlayer.progress = pos.toInt()
                binding.tvSeekCurrent.text = DeviceModeHelper.formatDuration(pos)
                binding.tvSeekDuration.text = DeviceModeHelper.formatDuration(dur)
            }
        }
    }

    private fun saveVodProgressIfNeeded() {
        if (channels.isEmpty()) return
        val ch = currentChannel()
        if (ch.contentType != ContentType.LIVE && !isArchiveMode) {
            val pos = engine.getCurrentPositionMs()
            val dur = engine.getDurationMs()
            if (pos > 3000L) {
                prefs.recordWatchHistory(
                    WatchHistoryEntry(
                        channelId = ch.id,
                        channelName = ch.name,
                        logoUrl = ch.logoUrl,
                        streamUrl = ch.streamUrl,
                        group = ch.group,
                        playlistId = ch.playlistId,
                        contentType = ch.contentType,
                        timestamp = System.currentTimeMillis(),
                        resumePositionMs = pos,
                        durationMs = dur
                    )
                )
            }
        }
    }

    // ==================== DRAWERS OVER VIDEO ====================
    private fun isAnyDrawerOpen(): Boolean =
        binding.drawerChannels.visibility == View.VISIBLE || binding.drawerEpg.visibility == View.VISIBLE

    private fun closeDrawers() {
        binding.drawerChannels.visibility = View.GONE
        binding.drawerEpg.visibility = View.GONE
    }

    private fun openChannelsDrawer() {
        closeDrawers()
        setOsdVisible(false)
        binding.drawerChannels.visibility = View.VISIBLE
        binding.tvDrawerChannelsTitle.text = "Каналы (${channels.size})"
        drawerChannelAdapter?.submitList(channels, currentChannel().id)
        binding.rvDrawerChannels.scrollToPosition(currentIndex)
        binding.rvDrawerChannels.post {
            binding.rvDrawerChannels.findViewHolderForAdapterPosition(currentIndex)?.itemView?.requestFocus()
                ?: binding.rvDrawerChannels.requestFocus()
        }
    }

    // ==================== SMART SEEK (ARCHIVE / VOD / LIVE TIMESHIFT) ====================
    private fun performSmartSeek(deltaSec: Int) {
        val ch = currentChannel()
        val deltaMs = deltaSec * 1000L
        val sign = if (deltaSec >= 0) "+" else ""

        // 1. Direct seek inside VOD, Archive, or seekable Live DVR buffer
        if (engine.isSeekable()) {
            engine.seekBy(deltaMs)
            val pos = DeviceModeHelper.formatDuration(engine.getCurrentPositionMs())
            val dur = DeviceModeHelper.formatDuration(engine.getDurationMs())
            val icon = if (deltaSec < 0) "⏪" else "⏩"
            showGesturePill("$icon ${sign}${deltaSec}с  ($pos / $dur)")
            return
        }

        // 2. Live channel with Archive / Catch-up -> seamless Timeshift jump!
        if (ch.contentType == ContentType.LIVE && app.repository.channelHasArchive(ch)) {
            val nowSec = System.currentTimeMillis() / 1000L
            val baseSec = if (isArchiveMode && timeshiftAnchorEpochSec > 0L) {
                timeshiftAnchorEpochSec + (engine.getCurrentPositionMs() / 1000L)
            } else {
                nowSec
            }
            val targetSec = (baseSec + deltaSec).coerceAtMost(nowSec)
            if (targetSec >= nowSec - 5L) {
                timeshiftAnchorEpochSec = 0L
                playChannelAt(currentIndex, null)
                showGesturePill("● Прямой эфир")
                return
            }
            val pl = app.repository.getPlaylistById(ch.playlistId)
            val tsUrl = CatchupUrlBuilder.buildTimeshiftUrl(ch, targetSec, pl, prefs.globalCatchupType)
            timeshiftAnchorEpochSec = targetSec
            val timeStr = DeviceModeHelper.formatTime(targetSec * 1000L)
            playArchiveStream(ch, tsUrl, "Таймшифт с $timeStr (${ch.name})")
            val icon = if (deltaSec < 0) "⏪" else "⏩"
            showGesturePill("$icon Таймшифт: $timeStr")
            return
        }

        // 3. Not seekable and no archive
        if (deltaSec < 0) {
            Toast.makeText(
                this,
                "Прямой эфир без архива. Для перемотки включите архив в настройках плейлиста или EPG.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            showGesturePill("● Уже в прямом эфире")
        }
    }

    private fun openEpgDrawer() {
        closeDrawers()
        setOsdVisible(false)
        val ch = currentChannel()
        binding.drawerEpg.visibility = View.VISIBLE
        binding.tvDrawerEpgTitle.text = "EPG: ${ch.name}"
        binding.btnReturnToLive.visibility = if (isArchiveMode) View.VISIBLE else View.GONE

        val now = System.currentTimeMillis()
        val pastDays = app.repository.effectiveArchiveDays(ch).coerceAtLeast(1)
        val fromMs = now - pastDays * 24L * 3600_000L
        val toMs = now + 24L * 3600_000L

        lifecycleScope.launch {
            cachedDrawerPrograms = app.repository.getScheduleForChannel(ch, fromMs, toMs)
            epgDrawerArchiveTab = isArchiveMode
            renderEpgDrawerTab()
        }
    }

    private fun renderEpgDrawerTab() {
        val ch = currentChannel()
        val now = System.currentTimeMillis()
        val hasArchive = app.repository.channelHasArchive(ch)

        binding.btnDrawerTabLive.isSelected = !epgDrawerArchiveTab
        binding.btnDrawerTabArchive.isSelected = epgDrawerArchiveTab

        val filtered = if (epgDrawerArchiveTab) {
            cachedDrawerPrograms.filter { it.isPast(now) || it.isLive(now) }.reversed()
        } else {
            cachedDrawerPrograms.filter { it.isLive(now) || it.isFuture(now) }
        }

        binding.tvDrawerEpgEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty()) {
            binding.tvDrawerEpgEmpty.text = if (epgDrawerArchiveTab) {
                "Нет архивных передач.\nПроверьте, что для канала загружена телепрограмма (EPG) и провайдер поддерживает архив."
            } else {
                "Телепрограмма для этого канала не загружена.\nДобавьте EPG (XMLTV) в Настройках → Телепрограмма (EPG)."
            }
        }

        val epgAdapter = EpgProgramAdapter(hasArchive = hasArchive) { prog ->
            val t = System.currentTimeMillis()
            when {
                prog.isLive(t) && !epgDrawerArchiveTab -> {
                    closeDrawers()
                    playChannelAt(currentIndex, null)
                }
                (prog.isPast(t) || (prog.isLive(t) && epgDrawerArchiveTab)) && hasArchive -> {
                    val pl = app.repository.getPlaylistById(ch.playlistId)
                    val url = CatchupUrlBuilder.buildCatchupUrl(ch, prog, pl, prefs.globalCatchupType)
                    timeshiftAnchorEpochSec = prog.startTimeMs / 1000L
                    closeDrawers()
                    playArchiveStream(ch, url, prog.title)
                }
                prog.isPast(t) -> {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Архив не указан для канала (можно включить в Настройки → EPG → Тип архива)",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Начало в ${DeviceModeHelper.formatTime(prog.startTimeMs)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        binding.rvDrawerEpg.adapter = epgAdapter
        epgAdapter.submitList(filtered)
        if (filtered.isNotEmpty()) {
            binding.rvDrawerEpg.scrollToPosition(0)
            binding.rvDrawerEpg.post {
                binding.rvDrawerEpg.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    ?: binding.rvDrawerEpg.requestFocus()
            }
        }
    }

    // ==================== OSD VISIBILITY ====================
    private fun setOsdVisible(visible: Boolean) {
        isOsdVisible = visible
        binding.osdContainer.visibility = if (visible) View.VISIBLE else View.GONE
        handler.removeCallbacks(hideOsdRunnable)
        if (visible) {
            scheduleHideOsd()
        }
    }

    private fun scheduleHideOsd() {
        handler.removeCallbacks(hideOsdRunnable)
        val timeoutMs = prefs.osdTimeoutSeconds.coerceIn(2, 30) * 1000L
        handler.postDelayed(hideOsdRunnable, timeoutMs)
    }

    // ==================== REMOTE CONTROL KEY HANDLING ====================
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // When a drawer is open, let Android's native D-Pad navigate the RecyclerView inside the drawer!
        if (isAnyDrawerOpen()) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                closeDrawers()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        // Numeric channel selection (0..9)
        if (prefs.enableNumericChannelInput && keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val digit = keyCode - KeyEvent.KEYCODE_0
            handleNumericDigit(digit)
            return true
        }

        // Dedicated TV remote channel buttons
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                switchChannelBy(+1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                switchChannelBy(-1)
                return true
            }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV_DATA_SERVICE -> {
                openEpgDrawer()
                return true
            }
            KeyEvent.KEYCODE_INFO -> {
                setOsdVisible(!isOsdVisible)
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                showQuickRemoteMenu()
                return true
            }
            KeyEvent.KEYCODE_LAST_CHANNEL -> {
                switchToPreviousChannel()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                val playing = engine.togglePlayPause()
                showGesturePill(if (playing) "▶ Воспроизведение" else "⏸ Пауза")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                performSmartSeek(-prefs.seekStepSeconds)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                performSmartSeek(+prefs.seekStepSeconds)
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (isOsdVisible) {
                    setOsdVisible(false)
                    return true
                }
                if (prefs.doubleBackToExit) {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressMs > 2000L) {
                        lastBackPressMs = now
                        Toast.makeText(this, "Нажмите «Назад» ещё раз для выхода из плеера", Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
                finish()
                return true
            }
        }

        // If OSD is visible and user navigates buttons on OSD, let D-Pad move between OSD action buttons
        if (isOsdVisible) {
            scheduleHideOsd()
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !binding.btnActionChannels.hasFocus()) {
                // Allow Up/Down channel switching even when OSD is shown if focus isn't inside seekbar
            }
            return super.onKeyDown(keyCode, event)
        }

        // OSD is hidden -> apply customizable TV remote shortcuts!
        val isSeekableContent = isArchiveMode || currentChannel().contentType != ContentType.LIVE
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                when (prefs.remoteUpDownAction) {
                    "list" -> openChannelsDrawer()
                    "volume" -> adjustSystemVolume(+1)
                    "televizo" -> setOsdVisible(true)
                    else -> switchChannelBy(+1)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (prefs.remoteUpDownAction) {
                    "list", "televizo" -> openChannelsDrawer()
                    "volume" -> adjustSystemVolume(-1)
                    else -> switchChannelBy(-1)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isSeekableContent) {
                    performSmartSeek(-prefs.seekStepSeconds)
                    return true
                }
                when (prefs.remoteLeftAction) {
                    "seek_back" -> performSmartSeek(-prefs.seekStepSeconds)
                    "prev_channel" -> switchToPreviousChannel()
                    "channel_prev" -> switchChannelBy(-1)
                    else -> openChannelsDrawer()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isSeekableContent) {
                    performSmartSeek(+prefs.seekStepSeconds)
                    return true
                }
                when (prefs.remoteRightAction) {
                    "seek_forward" -> performSmartSeek(+prefs.seekStepSeconds)
                    "info" -> setOsdVisible(true)
                    "channel_next" -> switchChannelBy(+1)
                    else -> openEpgDrawer()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                event.startTracking()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (!isAnyDrawerOpen() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            showQuickRemoteMenu()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isAnyDrawerOpen() && !isOsdVisible &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) &&
            !event.isCanceled
        ) {
            when (prefs.remoteOkAction) {
                "list" -> openChannelsDrawer()
                "pause" -> {
                    val playing = engine.togglePlayPause()
                    showGesturePill(if (playing) "▶ Воспроизведение" else "⏸ Пауза")
                }
                else -> {
                    setOsdVisible(true)
                    binding.btnActionChannels.requestFocus()
                }
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleNumericDigit(digit: Int) {
        if (numericBuffer.length >= 4) numericBuffer.clear()
        numericBuffer.append(digit)
        val num = numericBuffer.toString().toIntOrNull() ?: return

        val matchedIdx = channels.indexOfFirst { it.number == num }
            .takeIf { it >= 0 }
            ?: if (num in 1..channels.size) num - 1 else -1

        binding.tvNumericDigits.text = numericBuffer.toString()
        binding.tvNumericChannelHint.text = if (matchedIdx >= 0) channels[matchedIdx].name else "Канал не найден"
        binding.numericInputBox.visibility = View.VISIBLE

        numericRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            binding.numericInputBox.visibility = View.GONE
            val targetNum = numericBuffer.toString().toIntOrNull()
            numericBuffer.clear()
            if (targetNum == 0) {
                switchToPreviousChannel()
            } else if (matchedIdx >= 0) {
                switchToChannelWithPinCheck(matchedIdx)
            }
        }
        numericRunnable = r
        handler.postDelayed(r, prefs.numericInputTimeoutMs.toLong())
    }

    private fun switchToPreviousChannel() {
        val prevId = prefs.previousChannelId
        if (prevId.isBlank()) {
            Toast.makeText(this, "Нет предыдущего канала", Toast.LENGTH_SHORT).show()
            return
        }
        val idx = channels.indexOfFirst { it.id == prevId }
        if (idx >= 0) {
            switchToChannelWithPinCheck(idx)
        }
    }

    private fun adjustSystemVolume(dir: Int) {
        val flag = AudioManager.FLAG_SHOW_UI
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            flag
        )
    }

    // ==================== QUICK DIALOGS IN PLAYER ====================
    private fun showQuickRemoteMenu() {
        val ch = currentChannel()
        val favLabel = if (prefs.isFavorite(ch.id)) "Убрать из Избранного ★" else "Добавить в Избранное ☆"
        val items = arrayOf(
            "Список каналов",
            "Телепрограмма и Архив (EPG)",
            favLabel,
            "Предыдущий канал",
            "Аудиодорожка",
            "Субтитры",
            "Соотношение сторон (${engine.getAspectRatio().label})",
            "Движок плеера (${engine.activeEngine.label})",
            "Скорость воспроизведения (VOD / Архив)",
            "Таймер сна",
            "Открыть во внешнем плеере",
            "Настройки Е-Телевизёр"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(ch.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openChannelsDrawer()
                    1 -> openEpgDrawer()
                    2 -> {
                        val added = prefs.toggleFavorite(ch.id)
                        updateFavoriteButton(ch)
                        Toast.makeText(this, if (added) "В Избранном ★" else "Удалено из Избранного", Toast.LENGTH_SHORT).show()
                    }
                    3 -> switchToPreviousChannel()
                    4 -> showAudioTracksDialog()
                    5 -> showSubtitlesDialog()
                    6 -> showAspectRatioDialog()
                    7 -> showEngineDialog()
                    8 -> showSpeedDialog()
                    9 -> Dialogs.showSleepTimerDialog(this, 0) { setSleepTimer(it) }
                    10 -> DualPlayerEngine.openInExternalPlayer(this, ch.streamUrl, ch.name)
                    11 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            .show()
    }

    private fun showAudioTracksDialog() {
        val tracks = engine.getAudioTracks()
        if (tracks.isEmpty()) {
            Toast.makeText(this, "Дополнительных аудиодорожек нет", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = tracks.map { it.label }.toTypedArray()
        val selected = tracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Аудиодорожка")
            .setSingleChoiceItems(labels, selected) { d, which ->
                engine.selectAudioTrack(tracks[which])
                d.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSubtitlesDialog() {
        val tracks = engine.getSubtitleTracks()
        if (tracks.isEmpty()) {
            Toast.makeText(this, "Субтитры отсутствуют в потоке", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = tracks.map { it.label }.toTypedArray()
        val selected = tracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Субтитры")
            .setSingleChoiceItems(labels, selected) { d, which ->
                engine.selectSubtitleTrack(tracks[which])
                d.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAspectRatioDialog() {
        val modes = AspectRatioMode.entries
        val labels = modes.map { it.label }.toTypedArray()
        val selected = modes.indexOf(engine.getAspectRatio()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Соотношение сторон")
            .setSingleChoiceItems(labels, selected) { d, which ->
                val mode = modes[which]
                engine.applyAspectRatio(mode)
                showGesturePill("📐 ${mode.label}")
                d.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEngineDialog() {
        val engines = PlayerEngineType.entries
        val labels = engines.map { it.label }.toTypedArray()
        val selected = engines.indexOf(engine.activeEngine).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Выбор движка плеера")
            .setSingleChoiceItems(labels, selected) { d, which ->
                val chosen = engines[which]
                engine.switchEngine(chosen)
                showGesturePill("⚙ ${chosen.label}")
                d.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val labels = arrayOf("0.5x", "0.75x", "1.0x (Обычная)", "1.25x", "1.5x", "2.0x")
        MaterialAlertDialogBuilder(this)
            .setTitle("Скорость воспроизведения")
            .setItems(labels) { _, which ->
                engine.setPlaybackSpeed(speeds[which])
                showGesturePill("⏱ ${labels[which]}")
            }
            .show()
    }

    private fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            sleepEndMs = 0L
            binding.tvSleepBadge.visibility = View.GONE
            Toast.makeText(this, "Таймер сна отключён", Toast.LENGTH_SHORT).show()
        } else {
            sleepEndMs = System.currentTimeMillis() + minutes * 60_000L
            binding.tvSleepBadge.visibility = View.VISIBLE
            binding.tvSleepBadge.text = "💤 ${minutes}м"
            Toast.makeText(this, "Таймер сна: $minutes мин", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSleepTimer() {
        if (sleepEndMs <= 0L) return
        val leftMs = sleepEndMs - System.currentTimeMillis()
        if (leftMs <= 0L) {
            sleepEndMs = 0L
            Toast.makeText(this, "Таймер сна сработал — воспроизведение остановлено", Toast.LENGTH_LONG).show()
            finish()
        } else {
            val leftMin = ((leftMs + 59_999L) / 60_000L).toInt()
            binding.tvSleepBadge.visibility = View.VISIBLE
            binding.tvSleepBadge.text = "💤 ${leftMin}м"
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                Toast.makeText(this, "Режим PiP не поддерживается на устройстве", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== DUAL PLAYER CALLBACKS ====================
    override fun onBuffering(isBuffering: Boolean) {
        binding.bufferingBox.visibility = if (isBuffering) View.VISIBLE else View.GONE
        if (isBuffering) {
            binding.tvBufferingStatus.text = "Буферизация (${engine.activeEngine.key})..."
        }
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        binding.btnActionPlayPause.text = if (isPlaying) "Пауза" else "Играть"
    }

    override fun onPlaybackEnded() {
        if (isArchiveMode) {
            Toast.makeText(this, "Архивная передача завершена", Toast.LENGTH_SHORT).show()
            playChannelAt(currentIndex, null)
        } else if (currentChannel().contentType == ContentType.LIVE && prefs.autoReconnect) {
            engine.play(currentChannel().streamUrl, currentChannel().userAgent, currentChannel().referer)
        }
    }

    override fun onStreamStats(stats: StreamStats) {
        binding.tvPlayerStats.text = stats.summary()
    }

    override fun onError(message: String, willRetry: Boolean, switchedToLibVlc: Boolean) {
        if (switchedToLibVlc) {
            binding.bufferingBox.visibility = View.VISIBLE
            binding.tvBufferingStatus.text = "Переключение на LibVLC..."
            Toast.makeText(this, "Автопереключение потока на LibVLC", Toast.LENGTH_SHORT).show()
        } else if (willRetry) {
            binding.bufferingBox.visibility = View.VISIBLE
            binding.tvBufferingStatus.text = message
        } else {
            binding.bufferingBox.visibility = View.GONE
            Toast.makeText(this, "Не удалось открыть поток: $message", Toast.LENGTH_LONG).show()
        }
    }

    override fun onEngineChanged(engine: PlayerEngineType) {
        binding.tvPlayerStats.text = engine.label
    }
}
