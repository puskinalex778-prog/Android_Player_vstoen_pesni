package com.etelevizor.iptv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.etelevizor.iptv.ETelevizorApp
import com.etelevizor.iptv.R
import com.etelevizor.iptv.databinding.ActivityMainPhoneBinding
import com.etelevizor.iptv.databinding.ActivityMainTvBinding
import com.etelevizor.iptv.model.*
import com.etelevizor.iptv.player.DualPlayerEngine
import com.etelevizor.iptv.player.PlayerActivity
import com.etelevizor.iptv.util.DeviceModeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private val app get() = ETelevizorApp.instance
    private val prefs get() = app.prefs

    private var tvBinding: ActivityMainTvBinding? = null
    private var phoneBinding: ActivityMainPhoneBinding? = null
    private var isTvUi: Boolean = false
    private var appliedFontScale: Float = 1.0f
    private var appliedFontStyle: FontStyle = FontStyle.CLEAR_BOLD

    private var allChannels: List<Channel> = emptyList()
    private var filteredChannels: List<Channel> = emptyList()
    private var currentSection: ContentSection = ContentSection.LIVE
    private var selectedGroup: String = "Все"
    private var searchQuery: String = ""
    private var hasHandledStartupAutoPlay: Boolean = false
    private var lastBackPressMs: Long = 0L

    private lateinit var groupAdapter: GroupAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            tvBinding?.tvClockRail?.text = DeviceModeHelper.formatTime(System.currentTimeMillis())
            clockHandler.postDelayed(this, 15_000L)
        }
    }

    private val m3uFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importLocalPlaylist(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First launch: show Setup Wizard (choose TV / Phone / Auto + optional playlist)
        if (!prefs.setupCompleted) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        isTvUi = DeviceModeHelper.isTvMode(this, prefs)
        appliedFontScale = prefs.fontScale
        appliedFontStyle = prefs.fontStyle
        DeviceModeHelper.applyOrientation(this, prefs, isPlayer = false)

        if (isTvUi) {
            setupTvUi()
        } else {
            setupPhoneUi()
        }

        handleIncomingM3uIntent(intent)

        // Auto-install built-in self-updating IPTV playlist on first run + check scheduled auto-updates
        lifecycleScope.launch {
            if (prefs.getPlaylists().isEmpty() && !prefs.defaultPlaylistAutoSeeded) {
                prefs.defaultPlaylistAutoSeeded = true
                setLoading(true)
                Toast.makeText(
                    this@MainActivity,
                    "Загружаю встроенный самообновляемый IPTV плейлист...",
                    Toast.LENGTH_SHORT
                ).show()
                val res = app.repository.installDefaultAutoUpdatingPlaylist()
                setLoading(false)
                res.onSuccess { p ->
                    Toast.makeText(
                        this@MainActivity,
                        "Готово! «${p.name}»: ${p.channelCount + p.vodCount} каналов",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                app.repository.checkAutoUpdatesIfNeeded()
            }
            reloadData()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingM3uIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // If user switched TV / Phone mode, Font Size, or Font Style in Settings, recreate immediately!
        val shouldBeTv = DeviceModeHelper.isTvMode(this, prefs)
        if (shouldBeTv != isTvUi ||
            prefs.fontScale != appliedFontScale ||
            prefs.fontStyle != appliedFontStyle
        ) {
            recreate()
            return
        }
        applyThemeColors()
        updateModeLabels()
        reloadData()
        if (isTvUi) clockHandler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    // ==================== TV INTERFACE (ANDROID TV / D-PAD REMOTE) ====================
    private fun setupTvUi() {
        val b = ActivityMainTvBinding.inflate(layoutInflater)
        tvBinding = b
        setContentView(b.root)

        applyThemeColors()

        groupAdapter = GroupAdapter(
            isChipMode = false,
            onSelect = { group -> onGroupSelected(group) },
            onLongClick = { group -> showGroupContextMenu(group) }
        )
        b.rvGroupsTv.layoutManager = LinearLayoutManager(this)
        b.rvGroupsTv.adapter = groupAdapter

        channelAdapter = ChannelAdapter(
            prefs = prefs,
            repo = app.repository,
            viewStyle = prefs.viewStyle,
            onClick = { ch, pos -> onChannelClicked(ch, pos) },
            onLongClick = { ch -> showChannelContextMenu(ch) },
            onFocus = { ch -> updateTvPreviewPanel(ch) }
        )
        applyChannelLayoutManager(b.rvChannelsTv)
        b.rvChannelsTv.adapter = channelAdapter

        b.tvPreviewPanel.visibility = if (prefs.showTvPreviewPanel) View.VISIBLE else View.GONE

        // Left Navigation Rail
        b.navLive.setOnClickListener { switchSection(ContentSection.LIVE) }
        b.navFavorites.setOnClickListener { switchSection(ContentSection.FAVORITES) }
        b.navRecent.setOnClickListener { switchSection(ContentSection.RECENT) }
        b.navMovies.setOnClickListener { switchSection(ContentSection.MOVIES) }
        b.navSeries.setOnClickListener { switchSection(ContentSection.SERIES) }
        b.navEpg.setOnClickListener { startActivity(Intent(this, EpgActivity::class.java)) }
        b.navSearch.setOnClickListener {
            switchSection(ContentSection.SEARCH)
            b.etSearchTv.requestFocus()
        }
        b.navPlaylists.setOnClickListener { openProtectedActivity(PlaylistsActivity::class.java) }
        b.navFreeSearch.setOnClickListener { openFreePlaylistParser() }
        b.navSettings.setOnClickListener { openProtectedActivity(SettingsActivity::class.java) }
        b.navDeviceMode.setOnClickListener { openDeviceModeSwitcher() }

        // Top controls
        b.tvPlaylistHeader.setOnClickListener { showPlaylistSwitcherDialog() }
        b.btnTvViewStyle.setOnClickListener { cycleViewStyle() }
        b.btnTvSort.setOnClickListener { showSortDialog() }
        b.btnTvRefresh.setOnClickListener { refreshPlaylistsNow() }

        // Empty state buttons
        b.btnEmptyAddPlaylist.setOnClickListener { showQuickAddPlaylistMenu() }
        b.btnEmptyDemo.setOnClickListener { openFreePlaylistParser() }

        // Search box
        b.etSearchTv.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                applyFiltersAndRender()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Highlight active nav item
        if (prefs.startupAction == "favorites") {
            currentSection = ContentSection.FAVORITES
        }
        updateTvNavSelection()
        updateModeLabels()
        b.navLive.requestFocus()
    }

    // ==================== PHONE INTERFACE (TOUCH / TABLET) ====================
    private fun setupPhoneUi() {
        val b = ActivityMainPhoneBinding.inflate(layoutInflater)
        phoneBinding = b
        setContentView(b.root)

        applyThemeColors()

        groupAdapter = GroupAdapter(
            isChipMode = true,
            onSelect = { group -> onGroupSelected(group) },
            onLongClick = { group -> showGroupContextMenu(group) }
        )
        b.rvGroupsPhone.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvGroupsPhone.adapter = groupAdapter

        channelAdapter = ChannelAdapter(
            prefs = prefs,
            repo = app.repository,
            viewStyle = prefs.viewStyle,
            onClick = { ch, pos -> onChannelClicked(ch, pos) },
            onLongClick = { ch -> showChannelContextMenu(ch) }
        )
        applyChannelLayoutManager(b.rvChannelsPhone)
        b.rvChannelsPhone.adapter = channelAdapter

        b.phonePlaylistSelector.setOnClickListener { showPlaylistSwitcherDialog() }
        b.btnPhoneModeSwitch.setOnClickListener { openDeviceModeSwitcher() }
        b.btnPhoneViewStyle.setOnClickListener { cycleViewStyle() }
        b.btnPhonePlaylists.setOnClickListener { openProtectedActivity(PlaylistsActivity::class.java) }
        b.btnPhoneSettings.setOnClickListener { openProtectedActivity(SettingsActivity::class.java) }

        b.btnPhoneSearch.setOnClickListener {
            val showing = b.phoneSearchBar.visibility == View.VISIBLE
            b.phoneSearchBar.visibility = if (showing) View.GONE else View.VISIBLE
            if (!showing) {
                b.etSearchPhone.requestFocus()
            } else {
                b.etSearchPhone.setText("")
                searchQuery = ""
                applyFiltersAndRender()
            }
        }
        b.btnPhoneClearSearch.setOnClickListener {
            b.etSearchPhone.setText("")
            searchQuery = ""
            b.phoneSearchBar.visibility = View.GONE
            applyFiltersAndRender()
        }
        b.etSearchPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                applyFiltersAndRender()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        b.swipeRefreshPhone.setOnRefreshListener { refreshPlaylistsNow() }

        b.btnPhoneEmptyAdd.setOnClickListener { showQuickAddPlaylistMenu() }
        b.btnPhoneEmptyDemo.setOnClickListener { openFreePlaylistParser() }

        b.phoneBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_live -> { switchSection(ContentSection.LIVE); true }
                R.id.tab_favorites -> { switchSection(ContentSection.FAVORITES); true }
                R.id.tab_vod -> { switchSection(ContentSection.MOVIES); true }
                R.id.tab_series -> { switchSection(ContentSection.SERIES); true }
                R.id.tab_epg -> {
                    startActivity(Intent(this, EpgActivity::class.java))
                    false
                }
                else -> false
            }
        }

        if (prefs.startupAction == "favorites") {
            currentSection = ContentSection.FAVORITES
            b.phoneBottomNav.selectedItemId = R.id.tab_favorites
        }
    }

    private fun applyThemeColors() {
        val theme = prefs.appTheme
        tvBinding?.tvMainRoot?.setBackgroundColor(theme.bgColor)
        tvBinding?.tvGroupsColumn?.setBackgroundColor(theme.surfaceColor)
        tvBinding?.tvPreviewPanel?.setBackgroundColor(theme.surfaceColor)
        tvBinding?.tvPreviewPanel?.visibility = if (prefs.showTvPreviewPanel) View.VISIBLE else View.GONE
        phoneBinding?.phoneMainRoot?.setBackgroundColor(theme.bgColor)
    }

    private fun updateModeLabels() {
        val label = DeviceModeHelper.activeModeShortLabel(this, prefs)
        tvBinding?.navDeviceMode?.text = label
    }

    private fun applyChannelLayoutManager(rv: RecyclerView) {
        val style = prefs.viewStyle
        channelAdapter.setViewStyle(style)
        rv.layoutManager = if (style == ViewStyle.GRID) {
            val cols = if (isTvUi) 3 else 2
            GridLayoutManager(this, cols)
        } else {
            LinearLayoutManager(this)
        }
    }

    // ==================== DATA LOADING & FILTERING ====================
    private fun reloadData() {
        lifecycleScope.launch {
            allChannels = app.repository.loadAllChannels()
            updatePlaylistHeaderLabel()
            applyFiltersAndRender()

            // Optional startup action: auto-launch last watched channel
            if (!hasHandledStartupAutoPlay && prefs.startupAction == "last_channel") {
                hasHandledStartupAutoPlay = true
                val lastId = prefs.lastChannelId
                if (lastId.isNotBlank()) {
                    val liveList = allChannels.filter { it.contentType == ContentType.LIVE }
                    val idx = liveList.indexOfFirst { it.id == lastId }
                    if (idx >= 0) {
                        startActivity(PlayerActivity.createChannelListIntent(this@MainActivity, liveList, idx))
                    }
                }
            }
        }
    }

    private fun updatePlaylistHeaderLabel() {
        val playlists = prefs.getPlaylists()
        val activeId = prefs.activePlaylistId
        val label = if (activeId == "ALL") {
            if (playlists.isEmpty()) "Нет плейлистов ▾" else "Все плейлисты (${playlists.count { it.enabled }}) ▾"
        } else {
            (playlists.firstOrNull { it.id == activeId }?.name ?: "Плейлист") + " ▾"
        }
        tvBinding?.tvPlaylistHeader?.text = label
        phoneBinding?.tvPhonePlaylistSub?.text = label
    }

    private fun switchSection(section: ContentSection) {
        currentSection = section
        selectedGroup = "Все"
        if (section != ContentSection.SEARCH) {
            searchQuery = ""
            tvBinding?.etSearchTv?.setText("")
        }
        tvBinding?.etSearchTv?.visibility = if (section == ContentSection.SEARCH) View.VISIBLE else View.GONE
        updateTvNavSelection()
        applyFiltersAndRender()
    }

    private fun updateTvNavSelection() {
        val b = tvBinding ?: return
        b.navLive.isSelected = currentSection == ContentSection.LIVE
        b.navFavorites.isSelected = currentSection == ContentSection.FAVORITES
        b.navRecent.isSelected = currentSection == ContentSection.RECENT
        b.navMovies.isSelected = currentSection == ContentSection.MOVIES
        b.navSeries.isSelected = currentSection == ContentSection.SERIES
        b.navSearch.isSelected = currentSection == ContentSection.SEARCH
    }

    private fun applyFiltersAndRender() {
        val hiddenChannels = prefs.getHiddenChannelIds()
        val hiddenGroups = prefs.getHiddenGroups()
        val favIds = prefs.getFavoriteIds()
        val showHidden = prefs.showHiddenItems

        // 1. Base pool by ContentSection
        val sectionPool: List<Channel> = when (currentSection) {
            ContentSection.LIVE -> allChannels.filter { it.contentType == ContentType.LIVE }
            ContentSection.FAVORITES -> allChannels.filter { favIds.contains(it.id) }
            ContentSection.RECENT -> {
                val byId = allChannels.associateBy { it.id }
                prefs.getWatchHistory().mapNotNull { byId[it.channelId] }
            }
            ContentSection.MOVIES -> allChannels.filter { it.contentType == ContentType.MOVIE }
            ContentSection.SERIES -> allChannels.filter { it.contentType == ContentType.SERIES }
            ContentSection.SEARCH -> allChannels
        }.filter { ch ->
            val hidden = hiddenChannels.contains(ch.id) || hiddenGroups.contains(ch.group)
            val lockedHidden = prefs.hideLockedChannels && app.repository.isChannelLocked(ch)
            (showHidden || !hidden) && !lockedHidden
        }

        // 2. Build groups list
        val groupCounts = LinkedHashMap<String, Int>()
        for (ch in sectionPool) {
            groupCounts[ch.group] = (groupCounts[ch.group] ?: 0) + 1
        }
        val groupEntries = mutableListOf<GroupEntry>()
        if (prefs.showAllChannelsGroup || groupCounts.isEmpty()) {
            groupEntries.add(GroupEntry("Все", sectionPool.size))
        }
        for ((grp, cnt) in groupCounts) {
            groupEntries.add(
                GroupEntry(
                    name = grp,
                    count = cnt,
                    isLocked = app.repository.isGroupLocked(grp),
                    isHidden = hiddenGroups.contains(grp)
                )
            )
        }
        if (groupEntries.none { it.name == selectedGroup }) {
            selectedGroup = groupEntries.firstOrNull()?.name ?: "Все"
        }
        groupAdapter.submitList(groupEntries, selectedGroup)

        // 3. Filter by selected group + search query
        val q = searchQuery.trim().lowercase()
        val afterGroup = if (selectedGroup == "Все") {
            sectionPool
        } else {
            sectionPool.filter { it.group == selectedGroup }
        }
        val afterSearch = if (q.isEmpty()) {
            afterGroup
        } else {
            afterGroup.filter {
                it.name.lowercase().contains(q) ||
                    it.group.lowercase().contains(q) ||
                    it.number.toString() == q
            }
        }

        // 4. Sort
        filteredChannels = when (prefs.sortOrder) {
            SortOrder.PLAYLIST -> afterSearch
            SortOrder.NAME_ASC -> afterSearch.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> afterSearch.sortedByDescending { it.name.lowercase() }
            SortOrder.NUMBER -> afterSearch.sortedBy { it.number }
        }

        // 5. Render
        channelAdapter.submitList(filteredChannels)
        tvBinding?.tvSectionTitle?.text = "${currentSection.label} — $selectedGroup (${filteredChannels.size})"

        val hasPlaylists = prefs.getPlaylists().isNotEmpty()
        val isEmpty = filteredChannels.isEmpty()

        tvBinding?.let { b ->
            b.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            b.tvEmptyTitle.text = if (!hasPlaylists) "Нет добавленных плейлистов" else "В этом разделе пока пусто"
            b.btnEmptyAddPlaylist.visibility = if (!hasPlaylists) View.VISIBLE else View.GONE
            b.btnEmptyDemo.visibility = if (!hasPlaylists) View.VISIBLE else View.GONE
        }
        phoneBinding?.let { b ->
            b.phoneEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            b.tvPhoneEmptyTitle.text = if (!hasPlaylists) "Нет добавленных плейлистов" else "В этом разделе пока пусто"
            b.btnPhoneEmptyAdd.visibility = if (!hasPlaylists) View.VISIBLE else View.GONE
            b.btnPhoneEmptyDemo.visibility = if (!hasPlaylists) View.VISIBLE else View.GONE
        }

        if (isTvUi && filteredChannels.isNotEmpty()) {
            updateTvPreviewPanel(filteredChannels.first())
        }
    }

    private fun onGroupSelected(group: GroupEntry) {
        if (group.isLocked && group.name != "Все") {
            Dialogs.showPinVerifyDialog(this, prefs.parentalPin, "Категория «${group.name}»") {
                selectedGroup = group.name
                applyFiltersAndRender()
            }
        } else {
            selectedGroup = group.name
            applyFiltersAndRender()
        }
    }

    // ==================== CHANNEL CLICK & SERIES EPISODES ====================
    private fun onChannelClicked(channel: Channel, position: Int) {
        if (app.repository.isChannelLocked(channel)) {
            Dialogs.showPinVerifyDialog(this, prefs.parentalPin, "Канал «${channel.name}» защищён") {
                openChannelUnlocked(channel, position)
            }
        } else {
            openChannelUnlocked(channel, position)
        }
    }

    private fun openChannelUnlocked(channel: Channel, position: Int) {
        if (channel.contentType == ContentType.SERIES) {
            openSeriesEpisodesDialog(channel)
            return
        }
        if (prefs.playerEngine == PlayerEngineType.EXTERNAL) {
            DualPlayerEngine.openInExternalPlayer(this, channel.streamUrl, channel.name)
            return
        }
        startActivity(PlayerActivity.createChannelListIntent(this, filteredChannels, position))
    }

    private fun openSeriesEpisodesDialog(seriesChannel: Channel) {
        Toast.makeText(this, "Загрузка сезонов и серий...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val res = app.repository.loadSeriesEpisodes(seriesChannel)
            res.onSuccess { episodes ->
                if (episodes.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Серии не найдены", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val labels = episodes.map { ep ->
                    val dur = if (ep.duration.isNotBlank()) " (${ep.duration})" else ""
                    "S%02dE%02d • %s%s".format(ep.seasonNum, ep.episodeNum, ep.title, dur)
                }.toTypedArray()

                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(seriesChannel.name)
                    .setItems(labels) { _, which ->
                        val ep = episodes[which]
                        startActivity(
                            PlayerActivity.createEpisodeIntent(
                                context = this@MainActivity,
                                title = "${seriesChannel.name} — S${ep.seasonNum}E${ep.episodeNum}: ${ep.title}",
                                streamUrl = ep.streamUrl,
                                logoUrl = ep.thumbUrl.ifBlank { seriesChannel.logoUrl },
                                userAgent = seriesChannel.userAgent,
                                referer = seriesChannel.referer
                            )
                        )
                    }
                    .setNegativeButton("Закрыть", null)
                    .show()
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== TV PREVIEW PANEL ====================
    private fun updateTvPreviewPanel(channel: Channel) {
        val b = tvBinding ?: return
        if (!prefs.showTvPreviewPanel) return

        b.tvPreviewChannelName.text = "${channel.number}. ${channel.name}"
        val archiveTag = if (channel.hasCatchup()) " • Архив ${channel.catchupDays} дн." else ""
        b.tvPreviewGroup.text = "${channel.group}$archiveTag"

        if (channel.logoUrl.isNotBlank() && prefs.showChannelLogos) {
            Glide.with(this)
                .load(channel.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(b.ivPreviewLogo)
        } else {
            b.ivPreviewLogo.setImageResource(R.drawable.ic_channel_placeholder)
        }

        if (channel.contentType == ContentType.LIVE) {
            val nowNext = app.repository.getNowNextCached(channel)
            val cur = nowNext.current
            if (cur != null) {
                b.tvPreviewNowTitle.text = cur.title
                b.tvPreviewNowTime.text = "${DeviceModeHelper.formatTime(cur.startTimeMs)} – ${DeviceModeHelper.formatTime(cur.endTimeMs)}"
                b.pbPreviewProgress.progress = cur.progressPercent()
                b.tvPreviewNowDesc.text = cur.description.ifBlank { cur.category }
            } else {
                b.tvPreviewNowTitle.text = "Нет данных телепрограммы"
                b.tvPreviewNowTime.text = ""
                b.pbPreviewProgress.progress = 0
                b.tvPreviewNowDesc.text = "Подключите источник XMLTV (EPG) в разделе Плейлисты или Настройки."
            }
            val nxt = nowNext.next
            b.tvPreviewNextLabel.text = if (nxt != null) {
                "Далее ${DeviceModeHelper.formatTime(nxt.startTimeMs)}: ${nxt.title}"
            } else ""
        } else {
            b.tvPreviewNowTitle.text = channel.name
            b.tvPreviewNowTime.text = listOfNotNull(
                channel.year.takeIf { it.isNotBlank() },
                channel.rating.takeIf { it.isNotBlank() }?.let { "Рейтинг ★ $it" }
            ).joinToString(" • ")
            b.pbPreviewProgress.progress = 0
            b.tvPreviewNowDesc.text = channel.plot.ifBlank { "Нажмите OK для воспроизведения" }
            b.tvPreviewNextLabel.text = ""
        }
    }

    // ==================== CONTEXT MENUS & DIALOGS ====================
    private fun showChannelContextMenu(channel: Channel) {
        val isFav = prefs.isFavorite(channel.id)
        val isLocked = prefs.getLockedChannelIds().contains(channel.id)
        val isHidden = prefs.getHiddenChannelIds().contains(channel.id)

        val options = arrayOf(
            if (isFav) "Убрать из Избранного ★" else "Добавить в Избранное ☆",
            "Телепрограмма и Архив (EPG)",
            "Смотреть через ExoPlayer (Media3)",
            "Смотреть через LibVLC (все кодеки)",
            "Открыть во внешнем плеере",
            if (isLocked) "Снять родительский замок 🔓" else "Заблокировать канал 🔒",
            if (isHidden) "Показать канал" else "Скрыть канал"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(channel.name)
            .setItems(options) { _, which ->
                val idx = filteredChannels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                when (which) {
                    0 -> {
                        val added = prefs.toggleFavorite(channel.id)
                        Toast.makeText(this, if (added) "Добавлено в Избранное ★" else "Удалено из Избранного", Toast.LENGTH_SHORT).show()
                        applyFiltersAndRender()
                    }
                    1 -> {
                        val intent = Intent(this, EpgActivity::class.java).putExtra("channel_id", channel.id)
                        startActivity(intent)
                    }
                    2 -> startActivity(PlayerActivity.createChannelListIntent(this, filteredChannels, idx, PlayerEngineType.EXOPLAYER))
                    3 -> startActivity(PlayerActivity.createChannelListIntent(this, filteredChannels, idx, PlayerEngineType.LIBVLC))
                    4 -> DualPlayerEngine.openInExternalPlayer(this, channel.streamUrl, channel.name)
                    5 -> {
                        val locked = prefs.toggleLockedChannel(channel.id)
                        if (locked && !prefs.parentalControlEnabled) {
                            prefs.parentalControlEnabled = true
                        }
                        Toast.makeText(this, if (locked) "Канал заблокирован (PIN)" else "Замок снят", Toast.LENGTH_SHORT).show()
                        applyFiltersAndRender()
                    }
                    6 -> {
                        val hidden = prefs.toggleHiddenChannel(channel.id)
                        Toast.makeText(this, if (hidden) "Канал скрыт" else "Канал восстановлен", Toast.LENGTH_SHORT).show()
                        applyFiltersAndRender()
                    }
                }
            }
            .show()
    }

    private fun showGroupContextMenu(group: GroupEntry) {
        if (group.name == "Все") return
        val isLocked = prefs.getLockedGroups().contains(group.name)
        val isHidden = prefs.getHiddenGroups().contains(group.name)
        val options = arrayOf(
            if (isLocked) "Снять замок с категории 🔓" else "Заблокировать категорию (PIN) 🔒",
            if (isHidden) "Показать категорию" else "Скрыть категорию"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Категория: ${group.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val locked = prefs.toggleLockedGroup(group.name)
                        if (locked && !prefs.parentalControlEnabled) prefs.parentalControlEnabled = true
                        applyFiltersAndRender()
                    }
                    1 -> {
                        prefs.toggleHiddenGroup(group.name)
                        if (selectedGroup == group.name) selectedGroup = "Все"
                        applyFiltersAndRender()
                    }
                }
            }
            .show()
    }

    private fun showPlaylistSwitcherDialog() {
        val playlists = prefs.getPlaylists().filter { it.enabled }
        val names = mutableListOf("Все плейлисты")
        val ids = mutableListOf("ALL")
        playlists.forEach {
            names.add("${it.name} (${it.channelCount + it.vodCount + it.seriesCount})")
            ids.add(it.id)
        }
        names.add("+ Управление плейлистами...")

        val checked = ids.indexOf(prefs.activePlaylistId).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Выбор плейлиста")
            .setSingleChoiceItems(names.toTypedArray(), checked) { d, which ->
                d.dismiss()
                if (which == names.lastIndex) {
                    openProtectedActivity(PlaylistsActivity::class.java)
                } else {
                    prefs.activePlaylistId = ids[which]
                    selectedGroup = "Все"
                    reloadData()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openDeviceModeSwitcher() {
        Dialogs.showDeviceModeDialog(this, prefs) { newMode ->
            Toast.makeText(
                this,
                "Режим: ${DeviceModeHelper.activeModeShortLabel(this, prefs)}",
                Toast.LENGTH_SHORT
            ).show()
            recreate()
        }
    }

    private fun cycleViewStyle() {
        val next = when (prefs.viewStyle) {
            ViewStyle.LIST -> ViewStyle.GRID
            ViewStyle.GRID -> ViewStyle.COMPACT
            ViewStyle.COMPACT -> ViewStyle.LIST
        }
        prefs.viewStyle = next
        tvBinding?.rvChannelsTv?.let { applyChannelLayoutManager(it) }
        phoneBinding?.rvChannelsPhone?.let { applyChannelLayoutManager(it) }
        Toast.makeText(this, "Вид: ${next.label}", Toast.LENGTH_SHORT).show()
    }

    private fun showSortDialog() {
        val orders = SortOrder.entries
        val labels = orders.map { it.label }.toTypedArray()
        val checked = orders.indexOf(prefs.sortOrder).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Сортировка каналов")
            .setSingleChoiceItems(labels, checked) { d, which ->
                prefs.sortOrder = orders[which]
                d.dismiss()
                applyFiltersAndRender()
            }
            .show()
    }

    private fun showQuickAddPlaylistMenu() {
        val items = arrayOf(
            "Добавить по ссылке (M3U / M3U8 / XSPF / PLS / TXT)",
            "Выбрать файл из памяти устройства",
            "Подключить Xtream Codes API",
            "🌐 Поиск бесплатных плейлистов (РФ и Мир)",
            "Загрузить Демо-плейлист"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить плейлист")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> Dialogs.showAddM3uUrlDialog(this) { name, url, epg, ua ->
                        setLoading(true)
                        lifecycleScope.launch {
                            val res = app.repository.addM3uUrlPlaylist(name, url, epg, ua)
                            setLoading(false)
                            res.onSuccess {
                                Toast.makeText(this@MainActivity, "Загружено каналов: ${it.channelCount + it.vodCount}", Toast.LENGTH_SHORT).show()
                                reloadData()
                            }.onFailure {
                                Toast.makeText(this@MainActivity, "Ошибка: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    1 -> m3uFilePicker.launch(arrayOf("*/*"))
                    2 -> Dialogs.showAddXtreamDialog(this) { name, server, user, pass ->
                        setLoading(true)
                        lifecycleScope.launch {
                            val res = app.repository.addXtreamPlaylist(name, server, user, pass)
                            setLoading(false)
                            res.onSuccess {
                                Toast.makeText(this@MainActivity, "Xtream подключен!", Toast.LENGTH_SHORT).show()
                                reloadData()
                            }.onFailure {
                                Toast.makeText(this@MainActivity, "Ошибка: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    3 -> Dialogs.showFreePlaylistsSearchDialog(this) { preset ->
                        setLoading(true)
                        lifecycleScope.launch {
                            val res = app.repository.addM3uUrlPlaylist(
                                name = preset.title,
                                url = preset.url,
                                epgUrl = preset.recommendedEpgUrl
                            )
                            setLoading(false)
                            res.onSuccess {
                                Toast.makeText(this@MainActivity, "Загружено «${it.name}»: ${it.channelCount} каналов", Toast.LENGTH_LONG).show()
                                reloadData()
                            }.onFailure {
                                Toast.makeText(this@MainActivity, "Ошибка: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    4 -> loadDemoPlaylist()
                }
            }
            .show()
    }

    private fun openFreePlaylistParser() {
        Dialogs.showFreePlaylistsSearchDialog(this) { preset ->
            setLoading(true)
            lifecycleScope.launch {
                val res = app.repository.addM3uUrlPlaylist(
                    name = preset.title,
                    url = preset.url,
                    epgUrl = preset.recommendedEpgUrl
                )
                setLoading(false)
                res.onSuccess {
                    Toast.makeText(this@MainActivity, "Подключено «${it.name}»: ${it.channelCount + it.vodCount} каналов", Toast.LENGTH_LONG).show()
                    reloadData()
                }.onFailure {
                    Toast.makeText(this@MainActivity, "Ошибка: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadDemoPlaylist() {
        setLoading(true)
        lifecycleScope.launch {
            val res = app.repository.addDemoPlaylist()
            setLoading(false)
            res.onSuccess {
                Toast.makeText(this@MainActivity, "Демо-плейлист загружен!", Toast.LENGTH_SHORT).show()
                reloadData()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Ошибка: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshPlaylistsNow() {
        setLoading(true)
        lifecycleScope.launch {
            val (ok, fail) = app.repository.refreshAllEnabledPlaylists()
            app.repository.refreshAllEpg()
            setLoading(false)
            Toast.makeText(this@MainActivity, "Обновлено плейлистов: $ok (ошибок: $fail)", Toast.LENGTH_SHORT).show()
            reloadData()
        }
    }

    private fun openProtectedActivity(cls: Class<*>) {
        if (prefs.parentalControlEnabled && prefs.lockSettingsWithPin) {
            Dialogs.showPinVerifyDialog(this, prefs.parentalPin, "Доступ защищён PIN-кодом") {
                startActivity(Intent(this, cls))
            }
        } else {
            startActivity(Intent(this, cls))
        }
    }

    private fun handleIncomingM3uIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) {
            importLocalPlaylist(uri)
        }
    }

    private fun importLocalPlaylist(uri: Uri) {
        setLoading(true)
        lifecycleScope.launch {
            val res = app.repository.addLocalM3uPlaylist("Импорт M3U", uri)
            setLoading(false)
            res.onSuccess { p ->
                Toast.makeText(this@MainActivity, "Плейлист импортирован (${p.channelCount} ТВ)", Toast.LENGTH_SHORT).show()
                reloadData()
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Ошибка импорта: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        tvBinding?.tvLoadingBar?.visibility = if (loading) View.VISIBLE else View.GONE
        phoneBinding?.swipeRefreshPhone?.isRefreshing = loading
    }

    // ==================== REMOTE CONTROL SHORTCUTS ON HOME SCREEN ====================
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openProtectedActivity(SettingsActivity::class.java)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_GUIDE) {
            startActivity(Intent(this, EpgActivity::class.java))
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && prefs.doubleBackToExit) {
            val now = System.currentTimeMillis()
            if (now - lastBackPressMs > 2000L) {
                lastBackPressMs = now
                Toast.makeText(this, "Нажмите «Назад» ещё раз для выхода из Е-Телевизёр", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
