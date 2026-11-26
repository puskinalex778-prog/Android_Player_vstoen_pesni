package com.etelevizor.iptv.data

import android.content.Context
import android.content.SharedPreferences
import com.etelevizor.iptv.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores all E-Televizor settings (50+ options), playlists, EPG sources,
 * favorites, hidden/locked items, history, and JSON backup/restore.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("e_televizor_prefs", Context.MODE_PRIVATE)

    // ==================== FIRST LAUNCH & DEVICE MODE ====================
    var setupCompleted: Boolean
        get() = prefs.getBoolean("setup_completed", false)
        set(v) = prefs.edit().putBoolean("setup_completed", v).apply()

    var deviceMode: DeviceMode
        get() = DeviceMode.fromKey(prefs.getString("device_mode", DeviceMode.AUTO.key))
        set(v) = prefs.edit().putString("device_mode", v.key).apply()

    // ==================== INTERFACE & APPEARANCE ====================
    var appTheme: AppTheme
        get() = AppTheme.fromKey(prefs.getString("app_theme", AppTheme.DARK.key))
        set(v) = prefs.edit().putString("app_theme", v.key).apply()

    var accentColor: AccentColor
        get() = AccentColor.fromKey(prefs.getString("accent_color", AccentColor.CYAN.key))
        set(v) = prefs.edit().putString("accent_color", v.key).apply()

    var viewStyle: ViewStyle
        get() = ViewStyle.fromKey(prefs.getString("view_style", ViewStyle.LIST.key))
        set(v) = prefs.edit().putString("view_style", v.key).apply()

    var sortOrder: SortOrder
        get() = SortOrder.fromKey(prefs.getString("sort_order", SortOrder.PLAYLIST.key))
        set(v) = prefs.edit().putString("sort_order", v.key).apply()

    /** Font scale multiplier: 0.85, 1.0, 1.15, 1.30 */
    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1.0f)
        set(v) = prefs.edit().putFloat("font_scale", v).apply()

    var showChannelNumbers: Boolean
        get() = prefs.getBoolean("show_channel_numbers", true)
        set(v) = prefs.edit().putBoolean("show_channel_numbers", v).apply()

    var showChannelLogos: Boolean
        get() = prefs.getBoolean("show_channel_logos", true)
        set(v) = prefs.edit().putBoolean("show_channel_logos", v).apply()

    var showEpgProgress: Boolean
        get() = prefs.getBoolean("show_epg_progress", true)
        set(v) = prefs.edit().putBoolean("show_epg_progress", v).apply()

    var showNextProgram: Boolean
        get() = prefs.getBoolean("show_next_program", true)
        set(v) = prefs.edit().putBoolean("show_next_program", v).apply()

    var showPlayerClock: Boolean
        get() = prefs.getBoolean("show_player_clock", true)
        set(v) = prefs.edit().putBoolean("show_player_clock", v).apply()

    var showTvPreviewPanel: Boolean
        get() = prefs.getBoolean("show_tv_preview_panel", true)
        set(v) = prefs.edit().putBoolean("show_tv_preview_panel", v).apply()

    var osdTimeoutSeconds: Int
        get() = prefs.getInt("osd_timeout_sec", 5)
        set(v) = prefs.edit().putInt("osd_timeout_sec", v).apply()

    /** auto, landscape_player, landscape_always, portrait_menu */
    var phoneOrientation: String
        get() = prefs.getString("phone_orientation", "landscape_player") ?: "landscape_player"
        set(v) = prefs.edit().putString("phone_orientation", v).apply()

    /** menu, last_channel, favorites */
    var startupAction: String
        get() = prefs.getString("startup_action", "menu") ?: "menu"
        set(v) = prefs.edit().putString("startup_action", v).apply()

    var showHiddenItems: Boolean
        get() = prefs.getBoolean("show_hidden_items", false)
        set(v) = prefs.edit().putBoolean("show_hidden_items", v).apply()

    // ==================== PLAYER & PLAYBACK ====================
    var playerEngine: PlayerEngineType
        get() = PlayerEngineType.fromKey(prefs.getString("player_engine", PlayerEngineType.EXOPLAYER.key))
        set(v) = prefs.edit().putString("player_engine", v.key).apply()

    var autoFallbackToLibVlc: Boolean
        get() = prefs.getBoolean("auto_fallback_libvlc", true)
        set(v) = prefs.edit().putBoolean("auto_fallback_libvlc", v).apply()

    /** auto, full, decoding, software */
    var hwAcceleration: String
        get() = prefs.getString("hw_acceleration", "auto") ?: "auto"
        set(v) = prefs.edit().putString("hw_acceleration", v).apply()

    /** Buffer size in ms: 500, 1500, 3000, 5000, 10000 */
    var networkBufferMs: Int
        get() = prefs.getInt("network_buffer_ms", 3000)
        set(v) = prefs.edit().putInt("network_buffer_ms", v).apply()

    var autoReconnect: Boolean
        get() = prefs.getBoolean("auto_reconnect", true)
        set(v) = prefs.edit().putBoolean("auto_reconnect", v).apply()

    var reconnectAttempts: Int
        get() = prefs.getInt("reconnect_attempts", 5)
        set(v) = prefs.edit().putInt("reconnect_attempts", v).apply()

    var reconnectDelaySec: Int
        get() = prefs.getInt("reconnect_delay_sec", 3)
        set(v) = prefs.edit().putInt("reconnect_delay_sec", v).apply()

    var defaultAspectRatio: AspectRatioMode
        get() = AspectRatioMode.fromKey(prefs.getString("default_aspect_ratio", AspectRatioMode.FIT.key))
        set(v) = prefs.edit().putString("default_aspect_ratio", v.key).apply()

    var deinterlace: Boolean
        get() = prefs.getBoolean("deinterlace", false)
        set(v) = prefs.edit().putBoolean("deinterlace", v).apply()

    /** auto, ru, en, first */
    var preferredAudioLang: String
        get() = prefs.getString("preferred_audio_lang", "ru") ?: "ru"
        set(v) = prefs.edit().putString("preferred_audio_lang", v).apply()

    /** off, ru, en */
    var preferredSubtitleLang: String
        get() = prefs.getString("preferred_subtitle_lang", "off") ?: "off"
        set(v) = prefs.edit().putString("preferred_subtitle_lang", v).apply()

    var audioPassthrough: Boolean
        get() = prefs.getBoolean("audio_passthrough", false)
        set(v) = prefs.edit().putBoolean("audio_passthrough", v).apply()

    /** LibVLC volume boost 100..200 */
    var softwareVolumeBoost: Int
        get() = prefs.getInt("software_volume_boost", 100)
        set(v) = prefs.edit().putInt("software_volume_boost", v.coerceIn(100, 200)).apply()

    var enablePip: Boolean
        get() = prefs.getBoolean("enable_pip", true)
        set(v) = prefs.edit().putBoolean("enable_pip", v).apply()

    var connectionTimeoutSec: Int
        get() = prefs.getInt("connection_timeout_sec", 15)
        set(v) = prefs.edit().putInt("connection_timeout_sec", v).apply()

    var defaultUserAgent: String
        get() = prefs.getString("default_user_agent", "E-Televizor/1.0 (Android IPTV)") ?: "E-Televizor/1.0 (Android IPTV)"
        set(v) = prefs.edit().putString("default_user_agent", v).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(v) = prefs.edit().putBoolean("keep_screen_on", v).apply()

    // ==================== EPG SETTINGS ====================
    var epgTimeShiftHours: Float
        get() = prefs.getFloat("epg_time_shift_hours", 0f)
        set(v) = prefs.edit().putFloat("epg_time_shift_hours", v).apply()

    var epgAutoUpdateHours: Int
        get() = prefs.getInt("epg_auto_update_hours", 12)
        set(v) = prefs.edit().putInt("epg_auto_update_hours", v).apply()

    var epgRetentionDays: Int
        get() = prefs.getInt("epg_retention_days", 5)
        set(v) = prefs.edit().putInt("epg_retention_days", v).apply()

    var epgLastUpdated: Long
        get() = prefs.getLong("epg_last_updated", 0L)
        set(v) = prefs.edit().putLong("epg_last_updated", v).apply()

    // ==================== REMOTE CONTROL & GESTURES ====================
    /** channel, list, volume */
    var remoteUpDownAction: String
        get() = prefs.getString("remote_up_down_action", "channel") ?: "channel"
        set(v) = prefs.edit().putString("remote_up_down_action", v).apply()

    var invertChannelButtons: Boolean
        get() = prefs.getBoolean("invert_channel_buttons", false)
        set(v) = prefs.edit().putBoolean("invert_channel_buttons", v).apply()

    /** osd, list, pause */
    var remoteOkAction: String
        get() = prefs.getString("remote_ok_action", "osd") ?: "osd"
        set(v) = prefs.edit().putString("remote_ok_action", v).apply()

    /** list, seek_back, prev_channel */
    var remoteLeftAction: String
        get() = prefs.getString("remote_left_action", "list") ?: "list"
        set(v) = prefs.edit().putString("remote_left_action", v).apply()

    /** epg, seek_forward, info */
    var remoteRightAction: String
        get() = prefs.getString("remote_right_action", "epg") ?: "epg"
        set(v) = prefs.edit().putString("remote_right_action", v).apply()

    var enableNumericChannelInput: Boolean
        get() = prefs.getBoolean("enable_numeric_input", true)
        set(v) = prefs.edit().putBoolean("enable_numeric_input", v).apply()

    var numericInputTimeoutMs: Int
        get() = prefs.getInt("numeric_input_timeout_ms", 1600)
        set(v) = prefs.edit().putInt("numeric_input_timeout_ms", v).apply()

    var doubleBackToExit: Boolean
        get() = prefs.getBoolean("double_back_to_exit", true)
        set(v) = prefs.edit().putBoolean("double_back_to_exit", v).apply()

    var phoneGesturesEnabled: Boolean
        get() = prefs.getBoolean("phone_gestures_enabled", true)
        set(v) = prefs.edit().putBoolean("phone_gestures_enabled", v).apply()

    var doubleTapChannelSwitch: Boolean
        get() = prefs.getBoolean("double_tap_channel_switch", false)
        set(v) = prefs.edit().putBoolean("double_tap_channel_switch", v).apply()

    // ==================== PARENTAL CONTROL ====================
    var parentalControlEnabled: Boolean
        get() = prefs.getBoolean("parental_enabled", false)
        set(v) = prefs.edit().putBoolean("parental_enabled", v).apply()

    var parentalPin: String
        get() = prefs.getString("parental_pin", "0000") ?: "0000"
        set(v) = prefs.edit().putString("parental_pin", v).apply()

    var autoLockAdultGroups: Boolean
        get() = prefs.getBoolean("auto_lock_adult", true)
        set(v) = prefs.edit().putBoolean("auto_lock_adult", v).apply()

    var hideLockedChannels: Boolean
        get() = prefs.getBoolean("hide_locked_channels", false)
        set(v) = prefs.edit().putBoolean("hide_locked_channels", v).apply()

    var lockSettingsWithPin: Boolean
        get() = prefs.getBoolean("lock_settings_pin", false)
        set(v) = prefs.edit().putBoolean("lock_settings_pin", v).apply()

    // ==================== SLEEP TIMER & AUTOSTART ====================
    var defaultSleepTimerMinutes: Int
        get() = prefs.getInt("default_sleep_timer_min", 0)
        set(v) = prefs.edit().putInt("default_sleep_timer_min", v).apply()

    var launchOnBoot: Boolean
        get() = prefs.getBoolean("launch_on_boot", false)
        set(v) = prefs.edit().putBoolean("launch_on_boot", v).apply()

    // ==================== STATE: ACTIVE PLAYLIST, LAST & PREV CHANNELS ====================
    var activePlaylistId: String
        get() = prefs.getString("active_playlist_id", "ALL") ?: "ALL"
        set(v) = prefs.edit().putString("active_playlist_id", v).apply()

    var lastChannelId: String
        get() = prefs.getString("last_channel_id", "") ?: ""
        set(v) = prefs.edit().putString("last_channel_id", v).apply()

    var previousChannelId: String
        get() = prefs.getString("previous_channel_id", "") ?: ""
        set(v) = prefs.edit().putString("previous_channel_id", v).apply()

    // ==================== FAVORITES / HIDDEN / LOCKED ====================
    fun getFavoriteIds(): MutableSet<String> =
        HashSet(prefs.getStringSet("favorite_channel_ids", emptySet()) ?: emptySet())

    fun isFavorite(channelId: String): Boolean =
        (prefs.getStringSet("favorite_channel_ids", emptySet()) ?: emptySet()).contains(channelId)

    fun toggleFavorite(channelId: String): Boolean {
        val set = getFavoriteIds()
        val added = if (set.contains(channelId)) {
            set.remove(channelId); false
        } else {
            set.add(channelId); true
        }
        prefs.edit().putStringSet("favorite_channel_ids", set).apply()
        return added
    }

    fun getHiddenChannelIds(): MutableSet<String> =
        HashSet(prefs.getStringSet("hidden_channel_ids", emptySet()) ?: emptySet())

    fun toggleHiddenChannel(channelId: String): Boolean {
        val set = getHiddenChannelIds()
        val hidden = if (set.contains(channelId)) {
            set.remove(channelId); false
        } else {
            set.add(channelId); true
        }
        prefs.edit().putStringSet("hidden_channel_ids", set).apply()
        return hidden
    }

    fun getHiddenGroups(): MutableSet<String> =
        HashSet(prefs.getStringSet("hidden_groups", emptySet()) ?: emptySet())

    fun toggleHiddenGroup(group: String): Boolean {
        val set = getHiddenGroups()
        val hidden = if (set.contains(group)) {
            set.remove(group); false
        } else {
            set.add(group); true
        }
        prefs.edit().putStringSet("hidden_groups", set).apply()
        return hidden
    }

    fun getLockedChannelIds(): MutableSet<String> =
        HashSet(prefs.getStringSet("locked_channel_ids", emptySet()) ?: emptySet())

    fun toggleLockedChannel(channelId: String): Boolean {
        val set = getLockedChannelIds()
        val locked = if (set.contains(channelId)) {
            set.remove(channelId); false
        } else {
            set.add(channelId); true
        }
        prefs.edit().putStringSet("locked_channel_ids", set).apply()
        return locked
    }

    fun getLockedGroups(): MutableSet<String> =
        HashSet(prefs.getStringSet("locked_groups", emptySet()) ?: emptySet())

    fun toggleLockedGroup(group: String): Boolean {
        val set = getLockedGroups()
        val locked = if (set.contains(group)) {
            set.remove(group); false
        } else {
            set.add(group); true
        }
        prefs.edit().putStringSet("locked_groups", set).apply()
        return locked
    }

    // ==================== PLAYLISTS ====================
    fun getPlaylists(): List<PlaylistSource> {
        val raw = prefs.getString("playlists_json", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PlaylistSource>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PlaylistSource(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        type = PlaylistType.fromKey(o.optString("type")),
                        url = o.optString("url"),
                        localPath = o.optString("localPath"),
                        xtreamServer = o.optString("xtreamServer"),
                        xtreamUsername = o.optString("xtreamUsername"),
                        xtreamPassword = o.optString("xtreamPassword"),
                        epgUrl = o.optString("epgUrl"),
                        userAgent = o.optString("userAgent"),
                        referer = o.optString("referer"),
                        enabled = o.optBoolean("enabled", true),
                        epgShiftHours = o.optDouble("epgShiftHours", 0.0).toFloat(),
                        autoUpdateHours = o.optInt("autoUpdateHours", 24),
                        lastUpdated = o.optLong("lastUpdated", 0L),
                        catchupType = o.optString("catchupType"),
                        catchupDays = o.optInt("catchupDays", 0),
                        channelCount = o.optInt("channelCount", 0),
                        vodCount = o.optInt("vodCount", 0),
                        seriesCount = o.optInt("seriesCount", 0),
                        accountExpiry = o.optString("accountExpiry")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePlaylists(playlists: List<PlaylistSource>) {
        val arr = JSONArray()
        playlists.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("type", p.type.key)
                put("url", p.url)
                put("localPath", p.localPath)
                put("xtreamServer", p.xtreamServer)
                put("xtreamUsername", p.xtreamUsername)
                put("xtreamPassword", p.xtreamPassword)
                put("epgUrl", p.epgUrl)
                put("userAgent", p.userAgent)
                put("referer", p.referer)
                put("enabled", p.enabled)
                put("epgShiftHours", p.epgShiftHours.toDouble())
                put("autoUpdateHours", p.autoUpdateHours)
                put("lastUpdated", p.lastUpdated)
                put("catchupType", p.catchupType)
                put("catchupDays", p.catchupDays)
                put("channelCount", p.channelCount)
                put("vodCount", p.vodCount)
                put("seriesCount", p.seriesCount)
                put("accountExpiry", p.accountExpiry)
            })
        }
        prefs.edit().putString("playlists_json", arr.toString()).apply()
    }

    // ==================== EPG SOURCES ====================
    fun getEpgSources(): List<EpgSource> {
        val raw = prefs.getString("epg_sources_json", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<EpgSource>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    EpgSource(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        url = o.optString("url"),
                        enabled = o.optBoolean("enabled", true),
                        lastUpdated = o.optLong("lastUpdated", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveEpgSources(sources: List<EpgSource>) {
        val arr = JSONArray()
        sources.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("enabled", s.enabled)
                put("lastUpdated", s.lastUpdated)
            })
        }
        prefs.edit().putString("epg_sources_json", arr.toString()).apply()
    }

    // ==================== WATCH HISTORY ====================
    fun getWatchHistory(): List<WatchHistoryEntry> {
        val raw = prefs.getString("watch_history_json", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<WatchHistoryEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    WatchHistoryEntry(
                        channelId = o.optString("channelId"),
                        channelName = o.optString("channelName"),
                        logoUrl = o.optString("logoUrl"),
                        streamUrl = o.optString("streamUrl"),
                        group = o.optString("group"),
                        playlistId = o.optString("playlistId"),
                        contentType = ContentType.fromKey(o.optString("contentType")),
                        timestamp = o.optLong("timestamp"),
                        resumePositionMs = o.optLong("resumePositionMs", 0L),
                        durationMs = o.optLong("durationMs", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordWatchHistory(entry: WatchHistoryEntry) {
        val list = getWatchHistory().filterNot { it.channelId == entry.channelId }.toMutableList()
        list.add(0, entry)
        val trimmed = list.take(40)
        val arr = JSONArray()
        trimmed.forEach { h ->
            arr.put(JSONObject().apply {
                put("channelId", h.channelId)
                put("channelName", h.channelName)
                put("logoUrl", h.logoUrl)
                put("streamUrl", h.streamUrl)
                put("group", h.group)
                put("playlistId", h.playlistId)
                put("contentType", h.contentType.key)
                put("timestamp", h.timestamp)
                put("resumePositionMs", h.resumePositionMs)
                put("durationMs", h.durationMs)
            })
        }
        prefs.edit().putString("watch_history_json", arr.toString()).apply()

        if (entry.contentType == ContentType.LIVE) {
            val prevLast = lastChannelId
            if (prevLast.isNotBlank() && prevLast != entry.channelId) {
                previousChannelId = prevLast
            }
            lastChannelId = entry.channelId
        }
    }

    fun getResumePosition(channelId: String): Long {
        return getWatchHistory().firstOrNull { it.channelId == channelId }?.resumePositionMs ?: 0L
    }

    fun clearWatchHistory() {
        prefs.edit().putString("watch_history_json", "[]").apply()
    }

    // ==================== BACKUP & RESTORE (JSON) ====================
    fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("app", "E-Televizor")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val settingsObj = JSONObject()
        prefs.all.forEach { (k, v) ->
            when (v) {
                is Boolean -> settingsObj.put(k, v)
                is Int -> settingsObj.put(k, v)
                is Long -> settingsObj.put(k, v)
                is Float -> settingsObj.put(k, v.toDouble())
                is String -> settingsObj.put(k, v)
                is Set<*> -> {
                    val a = JSONArray()
                    v.forEach { if (it is String) a.put(it) }
                    settingsObj.put("__set__$k", a)
                }
            }
        }
        root.put("settings", settingsObj)
        return root.toString(2)
    }

    fun importBackupJson(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "E-Televizor") return false
            val settingsObj = root.getJSONObject("settings")
            val editor = prefs.edit()
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = settingsObj.get(key)
                if (key.startsWith("__set__") && value is JSONArray) {
                    val realKey = key.removePrefix("__set__")
                    val set = HashSet<String>()
                    for (i in 0 until value.length()) set.add(value.getString(i))
                    editor.putStringSet(realKey, set)
                } else {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                    }
                }
            }
            editor.putBoolean("setup_completed", true)
            editor.apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resetAllSettings() {
        val playlists = prefs.getString("playlists_json", "[]")
        val epg = prefs.getString("epg_sources_json", "[]")
        val favs = getFavoriteIds()
        prefs.edit().clear()
            .putString("playlists_json", playlists)
            .putString("epg_sources_json", epg)
            .putStringSet("favorite_channel_ids", favs)
            .putBoolean("setup_completed", true)
            .apply()
    }
}
