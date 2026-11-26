package com.etelevizor.iptv.data

import android.content.Context
import android.net.Uri
import com.etelevizor.iptv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class IptvRepository(
    private val context: Context,
    private val prefs: AppPreferences,
    private val epgDb: EpgDatabase,
    private val httpClient: OkHttpClient
) {
    private val cacheDir = File(context.filesDir, "playlists_cache").apply { mkdirs() }
    private val localPlaylistsDir = File(context.filesDir, "local_m3u").apply { mkdirs() }
    private val xtreamClient = XtreamApiClient(httpClient)

    private val memoryChannels = ConcurrentHashMap<String, List<Channel>>()
    private val epgNowNextCache = ConcurrentHashMap<String, Pair<Long, EpgNowNext>>()

    private val adultKeywords = listOf(
        "18+", "xxx", "adult", "эротик", "взросл", "porno", "porn", "sex", "erotic", "playboy", "hustler"
    )

    // ==================== CHANNEL CACHE LOAD / SAVE ====================
    suspend fun loadAllChannels(forceDiskReload: Boolean = false): List<Channel> = withContext(Dispatchers.IO) {
        val playlists = prefs.getPlaylists().filter { it.enabled }
        val activeId = prefs.activePlaylistId
        val targetPlaylists = if (activeId == "ALL") playlists else playlists.filter { it.id == activeId }

        val result = mutableListOf<Channel>()
        for (p in targetPlaylists) {
            val cached = if (!forceDiskReload) memoryChannels[p.id] else null
            val list = cached ?: readChannelsFromDisk(p.id).also { memoryChannels[p.id] = it }
            result.addAll(list)
        }
        result
    }

    suspend fun getAllEnabledChannelsAcrossAllPlaylists(): List<Channel> = withContext(Dispatchers.IO) {
        val playlists = prefs.getPlaylists().filter { it.enabled }
        val result = mutableListOf<Channel>()
        for (p in playlists) {
            val list = memoryChannels[p.id] ?: readChannelsFromDisk(p.id).also { memoryChannels[p.id] = it }
            result.addAll(list)
        }
        result
    }

    fun getPlaylistById(playlistId: String): PlaylistSource? =
        prefs.getPlaylists().firstOrNull { it.id == playlistId }

    fun isChannelLocked(channel: Channel): Boolean {
        if (!prefs.parentalControlEnabled) return false
        if (prefs.getLockedChannelIds().contains(channel.id)) return true
        if (prefs.getLockedGroups().contains(channel.group)) return true
        if (prefs.autoLockAdultGroups) {
            val lowerGroup = channel.group.lowercase()
            val lowerName = channel.name.lowercase()
            if (adultKeywords.any { lowerGroup.contains(it) || lowerName.contains(it) }) {
                return true
            }
        }
        return false
    }

    fun isGroupLocked(groupName: String): Boolean {
        if (!prefs.parentalControlEnabled) return false
        if (prefs.getLockedGroups().contains(groupName)) return true
        if (prefs.autoLockAdultGroups) {
            val lower = groupName.lowercase()
            if (adultKeywords.any { lower.contains(it) }) return true
        }
        return false
    }

    // ==================== ADD / REFRESH PLAYLISTS ====================
    suspend fun addM3uUrlPlaylist(
        name: String,
        url: String,
        epgUrl: String = "",
        userAgent: String = "",
        referer: String = ""
    ): Result<PlaylistSource> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().take(12)
        val source = PlaylistSource(
            id = id,
            name = name.ifBlank { "M3U плейлист" },
            type = PlaylistType.M3U_URL,
            url = url.trim(),
            epgUrl = epgUrl.trim(),
            userAgent = userAgent.trim(),
            referer = referer.trim()
        )
        saveOrUpdatePlaylistMeta(source)
        refreshPlaylist(source)
    }

    suspend fun addLocalM3uPlaylist(
        name: String,
        uri: Uri
    ): Result<PlaylistSource> = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString().take(12)
            val destFile = File(localPlaylistsDir, "$id.m3u")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IllegalStateException("Не удалось прочитать файл"))

            val source = PlaylistSource(
                id = id,
                name = name.ifBlank { "Локальный M3U" },
                type = PlaylistType.M3U_FILE,
                localPath = destFile.absolutePath
            )
            saveOrUpdatePlaylistMeta(source)
            refreshPlaylist(source)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addXtreamPlaylist(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        userAgent: String = ""
    ): Result<PlaylistSource> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().take(12)
        val normalizedServer = serverUrl.trim().let {
            if (!it.startsWith("http://", true) && !it.startsWith("https://", true)) "http://$it" else it
        }.trimEnd('/')
        val source = PlaylistSource(
            id = id,
            name = name.ifBlank { "Xtream IPTV" },
            type = PlaylistType.XTREAM,
            xtreamServer = normalizedServer,
            xtreamUsername = username.trim(),
            xtreamPassword = password.trim(),
            userAgent = userAgent.trim()
        )
        saveOrUpdatePlaylistMeta(source)
        refreshPlaylist(source)
    }

    suspend fun addDemoPlaylist(): Result<PlaylistSource> = withContext(Dispatchers.IO) {
        val id = "demo_open_streams"
        val demoM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="demo.bbb" tvg-chno="1" group-title="Демо потоки (HD)",Big Buck Bunny (HLS 1080p)
            https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
            #EXTINF:-1 tvg-id="demo.tears" tvg-chno="2" group-title="Демо потоки (HD)",Tears of Steel (HLS HD)
            https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8
            #EXTINF:-1 tvg-id="demo.sintel" tvg-chno="3" group-title="Демо потоки (HD)",Sintel (HLS 4K/HD)
            https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8
            #EXTINF:-1 tvg-id="demo.bipbop" tvg-chno="4" group-title="Тест сигнала",Apple BipBop (Многодорожечный HLS)
            https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8
            #EXTINF:-1 tvg-id="demo.fmp4" tvg-chno="5" group-title="Тест сигнала",fMP4 Тестовый поток (60fps)
            https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8
            #EXTINF:-1 tvg-id="demo.elephants" tvg-chno="6" group-title="Фильмы (Демо)",Elephants Dream (MP4)
            https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4
        """.trimIndent()

        val destFile = File(localPlaylistsDir, "$id.m3u")
        destFile.writeText(demoM3u, Charsets.UTF_8)
        val source = PlaylistSource(
            id = id,
            name = "Демо-плейлист (Проверка)",
            type = PlaylistType.M3U_FILE,
            localPath = destFile.absolutePath
        )
        saveOrUpdatePlaylistMeta(source)

        // Seed sample EPG for demo channels so user immediately sees EPG bars and schedule!
        seedDemoEpg()

        refreshPlaylist(source)
    }

    private fun seedDemoEpg() {
        val now = System.currentTimeMillis()
        val hour = 3600_000L
        val base = (now / hour) * hour
        val demoChannels = listOf("demo.bbb", "demo.tears", "demo.sintel", "demo.bipbop", "demo.fmp4")
        val programs = mutableListOf<EpgProgram>()
        for (ch in demoChannels) {
            programs.add(
                EpgProgram(
                    ch,
                    "Предыдущая передача (Архив)",
                    "Демонстрация телепрограммы и архива в Е-Телевизёр.",
                    "Демо",
                    base - hour,
                    base
                )
            )
            programs.add(
                EpgProgram(
                    ch,
                    "Прямой эфир • Тест HD потока",
                    "Проверка плавности воспроизведения, переключения дорожек и интерфейса пульта ДУ.",
                    "Прямой эфир",
                    base,
                    base + hour
                )
            )
            programs.add(
                EpgProgram(
                    ch,
                    "Далее в эфире • Специальный выпуск",
                    "Следующая передача в расписании телепрограммы EPG.",
                    "Познавательное",
                    base + hour,
                    base + 2 * hour
                )
            )
        }
        epgDb.insertProgramsBatch(programs)
    }

    suspend fun refreshPlaylist(playlist: PlaylistSource): Result<PlaylistSource> = withContext(Dispatchers.IO) {
        try {
            val updated: PlaylistSource
            val channels: List<Channel>

            when (playlist.type) {
                PlaylistType.M3U_URL -> {
                    val ua = playlist.userAgent.ifBlank { prefs.defaultUserAgent }
                    val reqBuilder = Request.Builder().url(playlist.url).header("User-Agent", ua)
                    if (playlist.referer.isNotBlank()) reqBuilder.header("Referer", playlist.referer)
                    httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Ошибка загрузки M3U (HTTP ${resp.code})")
                        }
                        val bodyStream = resp.body?.byteStream()
                            ?: throw IllegalStateException("Пустой ответ от сервера")
                        val parsed = M3uParser.parse(
                            inputStream = bodyStream,
                            playlistId = playlist.id,
                            playlistUserAgent = playlist.userAgent,
                            playlistReferer = playlist.referer,
                            playlistCatchupType = playlist.catchupType,
                            playlistCatchupDays = playlist.catchupDays
                        )
                        channels = parsed.channels
                        val detectedEpg = playlist.epgUrl.ifBlank { parsed.epgUrls.firstOrNull().orEmpty() }
                        updated = playlist.copy(
                            epgUrl = detectedEpg,
                            catchupType = playlist.catchupType.ifBlank { parsed.defaultCatchupType },
                            catchupDays = if (playlist.catchupDays > 0) playlist.catchupDays else parsed.defaultCatchupDays,
                            lastUpdated = System.currentTimeMillis(),
                            channelCount = channels.count { it.contentType == ContentType.LIVE },
                            vodCount = channels.count { it.contentType == ContentType.MOVIE },
                            seriesCount = channels.count { it.contentType == ContentType.SERIES }
                        )
                    }
                }

                PlaylistType.M3U_FILE -> {
                    val file = File(playlist.localPath)
                    if (!file.exists()) {
                        throw IllegalStateException("Файл плейлиста не найден")
                    }
                    FileInputStream(file).use { fis ->
                        val parsed = M3uParser.parse(
                            inputStream = fis,
                            playlistId = playlist.id,
                            playlistUserAgent = playlist.userAgent,
                            playlistReferer = playlist.referer,
                            playlistCatchupType = playlist.catchupType,
                            playlistCatchupDays = playlist.catchupDays
                        )
                        channels = parsed.channels
                        val detectedEpg = playlist.epgUrl.ifBlank { parsed.epgUrls.firstOrNull().orEmpty() }
                        updated = playlist.copy(
                            epgUrl = detectedEpg,
                            lastUpdated = System.currentTimeMillis(),
                            channelCount = channels.count { it.contentType == ContentType.LIVE },
                            vodCount = channels.count { it.contentType == ContentType.MOVIE },
                            seriesCount = channels.count { it.contentType == ContentType.SERIES }
                        )
                    }
                }

                PlaylistType.XTREAM -> {
                    val xt = xtreamClient.loadAll(playlist, prefs.defaultUserAgent)
                    channels = xt.channels
                    updated = playlist.copy(
                        epgUrl = playlist.epgUrl.ifBlank { xt.epgUrl },
                        lastUpdated = System.currentTimeMillis(),
                        channelCount = xt.liveCount,
                        vodCount = xt.vodCount,
                        seriesCount = xt.seriesCount,
                        accountExpiry = xt.expiryText
                    )
                }
            }

            writeChannelsToDisk(updated.id, channels)
            memoryChannels[updated.id] = channels
            saveOrUpdatePlaylistMeta(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAllEnabledPlaylists(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var ok = 0
        var fail = 0
        for (p in prefs.getPlaylists().filter { it.enabled }) {
            if (refreshPlaylist(p).isSuccess) ok++ else fail++
        }
        ok to fail
    }

    suspend fun checkAutoUpdatesIfNeeded() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (p in prefs.getPlaylists().filter { it.enabled }) {
            val needUpdate = when {
                p.autoUpdateHours == -1 -> true
                p.autoUpdateHours > 0 -> (now - p.lastUpdated) > p.autoUpdateHours * 3600_000L
                else -> false
            }
            if (needUpdate) {
                refreshPlaylist(p)
            }
        }
        val epgInterval = prefs.epgAutoUpdateHours
        if (epgInterval > 0 && (now - prefs.epgLastUpdated) > epgInterval * 3600_000L) {
            val hasAnyEpgUrl = prefs.getEpgSources().any { it.enabled } ||
                prefs.getPlaylists().any { it.enabled && it.epgUrl.isNotBlank() }
            if (hasAnyEpgUrl) {
                refreshAllEpg()
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        val updated = prefs.getPlaylists().filterNot { it.id == playlistId }
        prefs.savePlaylists(updated)
        memoryChannels.remove(playlistId)
        File(cacheDir, "$playlistId.json").delete()
        File(localPlaylistsDir, "$playlistId.m3u").delete()
        if (prefs.activePlaylistId == playlistId) {
            prefs.activePlaylistId = "ALL"
        }
    }

    fun saveOrUpdatePlaylistMeta(source: PlaylistSource) {
        val list = prefs.getPlaylists().toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) list[idx] = source else list.add(source)
        prefs.savePlaylists(list)
    }

    suspend fun loadSeriesEpisodes(channel: Channel): Result<List<SeriesEpisode>> = withContext(Dispatchers.IO) {
        try {
            val playlist = getPlaylistById(channel.playlistId)
                ?: return@withContext Result.failure(IllegalStateException("Плейлист не найден"))
            val episodes = xtreamClient.fetchSeriesEpisodes(playlist, channel.seriesId, prefs.defaultUserAgent)
            Result.success(episodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== EPG MANAGEMENT ====================
    suspend fun refreshAllEpg(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val urls = LinkedHashSet<Pair<String, Float>>()
            prefs.getEpgSources().filter { it.enabled && it.url.isNotBlank() }.forEach {
                urls.add(it.url.trim() to prefs.epgTimeShiftHours)
            }
            prefs.getPlaylists().filter { it.enabled && it.epgUrl.isNotBlank() }.forEach {
                urls.add(it.epgUrl.trim() to (prefs.epgTimeShiftHours + it.epgShiftHours))
            }

            if (urls.isEmpty()) {
                return@withContext Result.success(0)
            }

            val retentionMs = prefs.epgRetentionDays.coerceAtLeast(1) * 24L * 3600_000L
            epgDb.pruneOldPrograms(System.currentTimeMillis() - retentionMs)

            var totalPrograms = 0
            for ((url, shift) in urls) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", prefs.defaultUserAgent)
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.let { stream ->
                                totalPrograms += XmltvParser.parseAndStore(
                                    rawStream = stream,
                                    epgDb = epgDb,
                                    timeShiftHours = shift,
                                    keepPastDays = prefs.epgRetentionDays
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Continue with other EPG sources
                }
            }
            epgNowNextCache.clear()
            prefs.epgLastUpdated = System.currentTimeMillis()
            Result.success(totalPrograms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNowNextCached(channel: Channel): EpgNowNext {
        val now = System.currentTimeMillis()
        val cached = epgNowNextCache[channel.id]
        if (cached != null && now - cached.first < 45_000L) {
            return cached.second
        }
        val result = epgDb.getNowNext(channel.epgLookupKeys(), now)
        epgNowNextCache[channel.id] = now to result
        return result
    }

    suspend fun getScheduleForChannel(channel: Channel, fromMs: Long, toMs: Long): List<EpgProgram> =
        withContext(Dispatchers.IO) {
            epgDb.getSchedule(channel.epgLookupKeys(), fromMs, toMs)
        }

    fun clearEpgCache() {
        epgDb.clearAll()
        epgNowNextCache.clear()
        prefs.epgLastUpdated = 0L
    }

    // ==================== CACHE SIZE & CLEANUP ====================
    fun calculateCacheSizeBytes(): Long {
        var total = 0L
        total += dirSize(context.cacheDir)
        context.externalCacheDir?.let { total += dirSize(it) }
        val dbFile = context.getDatabasePath("epg_cache.db")
        if (dbFile.exists()) total += dbFile.length()
        return total
    }

    fun clearImageAndTempCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += if (f.isDirectory) dirSize(f) else f.length()
        }
        return size
    }

    // ==================== DISK SERIALIZATION ====================
    private fun writeChannelsToDisk(playlistId: String, channels: List<Channel>) {
        val arr = JSONArray()
        for (c in channels) {
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("p", c.playlistId)
                put("no", c.number)
                put("n", c.name)
                put("u", c.streamUrl)
                if (c.logoUrl.isNotEmpty()) put("l", c.logoUrl)
                put("g", c.group)
                if (c.tvgId.isNotEmpty()) put("ti", c.tvgId)
                if (c.tvgName.isNotEmpty()) put("tn", c.tvgName)
                if (c.catchupType.isNotEmpty()) put("ct", c.catchupType)
                if (c.catchupDays > 0) put("cd", c.catchupDays)
                if (c.catchupSource.isNotEmpty()) put("cs", c.catchupSource)
                if (c.userAgent.isNotEmpty()) put("ua", c.userAgent)
                if (c.referer.isNotEmpty()) put("rf", c.referer)
                put("tp", c.contentType.key)
                if (c.seriesId.isNotEmpty()) put("si", c.seriesId)
                if (c.plot.isNotEmpty()) put("pl", c.plot)
                if (c.rating.isNotEmpty()) put("rt", c.rating)
                if (c.year.isNotEmpty()) put("yr", c.year)
                if (c.streamId.isNotEmpty()) put("sid", c.streamId)
            })
        }
        File(cacheDir, "$playlistId.json").writeText(arr.toString(), Charsets.UTF_8)
    }

    private fun readChannelsFromDisk(playlistId: String): List<Channel> {
        val file = File(cacheDir, "$playlistId.json")
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val list = ArrayList<Channel>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Channel(
                        id = o.optString("id"),
                        playlistId = o.optString("p", playlistId),
                        number = o.optInt("no", i + 1),
                        name = o.optString("n"),
                        streamUrl = o.optString("u"),
                        logoUrl = o.optString("l"),
                        group = o.optString("g", "Общие"),
                        tvgId = o.optString("ti"),
                        tvgName = o.optString("tn"),
                        catchupType = o.optString("ct"),
                        catchupDays = o.optInt("cd", 0),
                        catchupSource = o.optString("cs"),
                        userAgent = o.optString("ua"),
                        referer = o.optString("rf"),
                        contentType = ContentType.fromKey(o.optString("tp")),
                        seriesId = o.optString("si"),
                        plot = o.optString("pl"),
                        rating = o.optString("rt"),
                        year = o.optString("yr"),
                        streamId = o.optString("sid")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
