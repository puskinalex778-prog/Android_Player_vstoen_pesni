package com.etelevizor.iptv.model

enum class DeviceMode(val key: String, val label: String) {
    AUTO("auto", "Автоопределение"),
    TV("tv", "Телевизор / Приставка (Пульт ДУ)"),
    PHONE("phone", "Телефон / Планшет (Сенсор)");

    companion object {
        fun fromKey(key: String?): DeviceMode = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

enum class PlayerEngineType(val key: String, val label: String) {
    EXOPLAYER("exoplayer", "ExoPlayer (Media3 — быстрый)"),
    LIBVLC("libvlc", "LibVLC (все кодеки, AC3/DTS/UDP)"),
    EXTERNAL("external", "Внешний плеер (VLC / MX / ALEXVID)");

    companion object {
        fun fromKey(key: String?): PlayerEngineType = entries.firstOrNull { it.key == key } ?: EXOPLAYER
    }
}

enum class PlaylistType(val key: String, val label: String) {
    M3U_URL("m3u_url", "M3U / M3U8 ссылка"),
    M3U_FILE("m3u_file", "M3U / M3U8 файл"),
    XTREAM("xtream", "Xtream Codes API");

    companion object {
        fun fromKey(key: String?): PlaylistType = entries.firstOrNull { it.key == key } ?: M3U_URL
    }
}

enum class ContentType(val key: String) {
    LIVE("live"),
    MOVIE("movie"),
    SERIES("series");

    companion object {
        fun fromKey(key: String?): ContentType = entries.firstOrNull { it.key == key } ?: LIVE
    }
}

enum class ViewStyle(val key: String, val label: String) {
    LIST("list", "Список с телепрограммой"),
    GRID("grid", "Крупная плитка"),
    COMPACT("compact", "Компактный список");

    companion object {
        fun fromKey(key: String?): ViewStyle = entries.firstOrNull { it.key == key } ?: LIST
    }
}

enum class SortOrder(val key: String, val label: String) {
    PLAYLIST("playlist", "Порядок плейлиста"),
    NAME_ASC("name_asc", "По алфавиту (А–Я)"),
    NAME_DESC("name_desc", "По алфавиту (Я–А)"),
    NUMBER("number", "По номеру канала");

    companion object {
        fun fromKey(key: String?): SortOrder = entries.firstOrNull { it.key == key } ?: PLAYLIST
    }
}

enum class AppTheme(val key: String, val label: String, val bgColor: Int, val surfaceColor: Int, val cardColor: Int) {
    DARK("dark", "Тёмная классическая", 0xFF0D1117.toInt(), 0xFF161B22.toInt(), 0xFF1E2633.toInt()),
    AMOLED("amoled", "AMOLED чёрная", 0xFF000000.toInt(), 0xFF0D0D0F.toInt(), 0xFF16171A.toInt()),
    MIDNIGHT("midnight", "Полночный синий", 0xFF091021.toInt(), 0xFF101C36.toInt(), 0xFF17274A.toInt()),
    GRAPHITE("graphite", "Графит", 0xFF181A1F.toInt(), 0xFF21242B.toInt(), 0xFF2A2E37.toInt());

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class AccentColor(val key: String, val label: String, val colorInt: Int) {
    CYAN("cyan", "Неоновый циан", 0xFF00D4FF.toInt()),
    EMERALD("emerald", "Изумруд", 0xFF10B981.toInt()),
    AMBER("amber", "Янтарь", 0xFFF59E0B.toInt()),
    CORAL("coral", "Коралл", 0xFFFF5A5F.toInt()),
    PURPLE("purple", "Фиолетовый", 0xFFA855F7.toInt());

    companion object {
        fun fromKey(key: String?): AccentColor = entries.firstOrNull { it.key == key } ?: CYAN
    }
}

enum class FontStyle(val key: String, val label: String) {
    CLEAR_BOLD("clear_bold", "Чёткий ТВ-шрифт (Жирный, хорошо читаемый)"),
    STANDARD("standard", "Стандартный (Sans-serif)"),
    CONDENSED("condensed", "Компактный чёткий (Condensed)"),
    MONOSPACE("monospace", "Моноширинный (Крупные символы)");

    companion object {
        fun fromKey(key: String?): FontStyle = entries.firstOrNull { it.key == key } ?: CLEAR_BOLD
    }
}

enum class AspectRatioMode(val key: String, val label: String) {
    FIT("fit", "Вписать (авто)"),
    FILL("fill", "Заполнить экран"),
    ZOOM("zoom", "Обрезать (Zoom)"),
    RATIO_16_9("16:9", "16:9"),
    RATIO_4_3("4:3", "4:3"),
    RATIO_16_10("16:10", "16:10"),
    RATIO_21_9("21:9", "21:9"),
    ORIGINAL("original", "Оригинал");

    companion object {
        fun fromKey(key: String?): AspectRatioMode = entries.firstOrNull { it.key == key } ?: FIT
    }
}

enum class ContentSection(val key: String, val label: String) {
    LIVE("live", "ТВ каналы"),
    FAVORITES("favorites", "Избранное"),
    RECENT("recent", "Недавние"),
    MOVIES("movies", "Фильмы (VOD)"),
    SERIES("series", "Сериалы"),
    SEARCH("search", "Поиск");

    companion object {
        fun fromKey(key: String?): ContentSection = entries.firstOrNull { it.key == key } ?: LIVE
    }
}

data class PlaylistSource(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val url: String = "",
    val localPath: String = "",
    val xtreamServer: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = "",
    val epgUrl: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val enabled: Boolean = true,
    val epgShiftHours: Float = 0f,
    val autoUpdateHours: Int = 24, // 0 = manual, -1 = every launch
    val lastUpdated: Long = 0L,
    val catchupType: String = "",
    val catchupDays: Int = 0,
    val channelCount: Int = 0,
    val vodCount: Int = 0,
    val seriesCount: Int = 0,
    val accountExpiry: String = ""
)

data class Channel(
    val id: String,
    val playlistId: String,
    val number: Int,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val group: String = "Общие",
    val tvgId: String = "",
    val tvgName: String = "",
    val catchupType: String = "",
    val catchupDays: Int = 0,
    val catchupSource: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val contentType: ContentType = ContentType.LIVE,
    val seriesId: String = "",
    val plot: String = "",
    val rating: String = "",
    val year: String = "",
    val duration: String = "",
    val streamId: String = ""
) {
    fun hasCatchup(): Boolean = catchupDays > 0 || catchupSource.isNotBlank() || catchupType.isNotBlank()

    fun epgLookupKeys(): List<String> {
        val keys = LinkedHashSet<String>()
        if (tvgId.isNotBlank()) keys.add(tvgId.trim().lowercase())
        if (tvgName.isNotBlank()) keys.add(tvgName.trim().lowercase())
        if (name.isNotBlank()) keys.add(name.trim().lowercase())
        return keys.toList()
    }
}

data class SeriesEpisode(
    val id: String,
    val seasonNum: Int,
    val episodeNum: Int,
    val title: String,
    val streamUrl: String,
    val duration: String = "",
    val plot: String = "",
    val thumbUrl: String = ""
)

data class EpgProgram(
    val channelKey: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val startTimeMs: Long,
    val endTimeMs: Long
) {
    fun isLive(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs in startTimeMs until endTimeMs
    fun isPast(nowMs: Long = System.currentTimeMillis()): Boolean = endTimeMs <= nowMs
    fun isFuture(nowMs: Long = System.currentTimeMillis()): Boolean = startTimeMs > nowMs

    fun progressPercent(nowMs: Long = System.currentTimeMillis()): Int {
        val total = endTimeMs - startTimeMs
        if (total <= 0) return 0
        val elapsed = (nowMs - startTimeMs).coerceIn(0, total)
        return ((elapsed * 100L) / total).toInt()
    }
}

data class EpgNowNext(
    val current: EpgProgram? = null,
    val next: EpgProgram? = null
)

data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdated: Long = 0L
)

data class WatchHistoryEntry(
    val channelId: String,
    val channelName: String,
    val logoUrl: String,
    val streamUrl: String,
    val group: String,
    val playlistId: String,
    val contentType: ContentType = ContentType.LIVE,
    val timestamp: Long = System.currentTimeMillis(),
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class TrackOption(
    val id: String,
    val label: String,
    val language: String = "",
    val isSelected: Boolean = false,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val vlcTrackId: Int = -1
)

data class StreamStats(
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val fps: Float = 0f,
    val engineName: String = "ExoPlayer"
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (height > 0) {
            val res = when {
                height >= 2100 -> "4K"
                height >= 1050 -> "1080p"
                height >= 700 -> "720p"
                height >= 540 -> "576p"
                else -> "${width}x${height}"
            }
            parts.add(res)
        }
        if (fps > 1f) parts.add("${fps.toInt()}fps")
        if (videoCodec.isNotBlank()) parts.add(videoCodec)
        if (audioCodec.isNotBlank()) parts.add(audioCodec)
        parts.add(engineName)
        return parts.joinToString(" • ")
    }
}

data class GroupEntry(
    val name: String,
    val count: Int,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false
)
