package com.etelevizor.iptv.data

import com.etelevizor.iptv.model.Channel
import com.etelevizor.iptv.model.ContentType
import com.etelevizor.iptv.model.PlaylistSource
import com.etelevizor.iptv.model.SeriesEpisode
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class XtreamLoadResult(
    val channels: List<Channel>,
    val epgUrl: String,
    val liveCount: Int,
    val vodCount: Int,
    val seriesCount: Int,
    val expiryText: String
)

class XtreamApiClient(private val httpClient: OkHttpClient) {

    fun loadAll(playlist: PlaylistSource, defaultUserAgent: String): XtreamLoadResult {
        val server = playlist.xtreamServer.trim().trimEnd('/')
        val user = playlist.xtreamUsername.trim()
        val pass = playlist.xtreamPassword.trim()
        val ua = playlist.userAgent.ifBlank { defaultUserAgent }
        val baseApi = "$server/player_api.php?username=${enc(user)}&password=${enc(pass)}"

        // 1. Account info & auth check
        val authJson = JSONObject(fetchText(baseApi, ua))
        val userInfo = authJson.optJSONObject("user_info")
        val authOk = userInfo?.optInt("auth", 1) ?: 1
        if (authOk == 0) {
            throw IllegalStateException("Ошибка авторизации Xtream Codes (неверный логин или пароль)")
        }
        val expTs = userInfo?.optString("exp_date").orEmpty().toLongOrNull() ?: 0L
        val expiryText = if (expTs > 0L) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(expTs * 1000L))
        } else {
            "Без срока"
        }

        val allItems = mutableListOf<Channel>()
        var liveCount = 0
        var vodCount = 0
        var seriesCount = 0

        // 2. Live categories & streams
        val liveCatMap = fetchCategoryMap("$baseApi&action=get_live_categories", ua)
        val liveArr = fetchJsonArray("$baseApi&action=get_live_streams", ua)
        for (i in 0 until liveArr.length()) {
            val o = liveArr.optJSONObject(i) ?: continue
            val streamId = o.optString("stream_id").ifBlank { o.optInt("stream_id", 0).toString() }
            if (streamId == "0" || streamId.isBlank()) continue
            val name = o.optString("name", "Канал ${i + 1}")
            val num = o.optInt("num", i + 1)
            val catId = o.optString("category_id")
            val group = liveCatMap[catId] ?: "ТВ каналы"
            val logo = o.optString("stream_icon")
            val epgId = o.optString("epg_channel_id")
            val hasArchive = o.optInt("tv_archive", 0) == 1
            val archiveDays = o.optInt("tv_archive_duration", 0)
            val streamUrl = "$server/live/${enc(user)}/${enc(pass)}/$streamId.ts"

            allItems.add(
                Channel(
                    id = "${playlist.id}_live_$streamId",
                    playlistId = playlist.id,
                    number = num,
                    name = name,
                    streamUrl = streamUrl,
                    logoUrl = logo,
                    group = group,
                    tvgId = epgId,
                    tvgName = name,
                    catchupType = if (hasArchive) "xc" else playlist.catchupType,
                    catchupDays = if (hasArchive) archiveDays.coerceAtLeast(1) else playlist.catchupDays,
                    userAgent = playlist.userAgent,
                    referer = playlist.referer,
                    contentType = ContentType.LIVE,
                    streamId = streamId
                )
            )
            liveCount++
        }

        // 3. VOD (Movies)
        try {
            val vodCatMap = fetchCategoryMap("$baseApi&action=get_vod_categories", ua)
            val vodArr = fetchJsonArray("$baseApi&action=get_vod_streams", ua)
            for (i in 0 until vodArr.length()) {
                val o = vodArr.optJSONObject(i) ?: continue
                val streamId = o.optString("stream_id").ifBlank { o.optInt("stream_id", 0).toString() }
                if (streamId == "0" || streamId.isBlank()) continue
                val name = o.optString("name", "Фильм ${i + 1}")
                val catId = o.optString("category_id")
                val group = vodCatMap[catId] ?: "Фильмы"
                val logo = o.optString("stream_icon")
                val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                val rating = o.optString("rating")
                val streamUrl = "$server/movie/${enc(user)}/${enc(pass)}/$streamId.$ext"

                allItems.add(
                    Channel(
                        id = "${playlist.id}_vod_$streamId",
                        playlistId = playlist.id,
                        number = i + 1,
                        name = name,
                        streamUrl = streamUrl,
                        logoUrl = logo,
                        group = group,
                        userAgent = playlist.userAgent,
                        referer = playlist.referer,
                        contentType = ContentType.MOVIE,
                        rating = rating,
                        streamId = streamId
                    )
                )
                vodCount++
            }
        } catch (_: Exception) {
            // Some Xtream providers don't include VOD
        }

        // 4. Series
        try {
            val seriesCatMap = fetchCategoryMap("$baseApi&action=get_series_categories", ua)
            val seriesArr = fetchJsonArray("$baseApi&action=get_series", ua)
            for (i in 0 until seriesArr.length()) {
                val o = seriesArr.optJSONObject(i) ?: continue
                val seriesId = o.optString("series_id").ifBlank { o.optInt("series_id", 0).toString() }
                if (seriesId == "0" || seriesId.isBlank()) continue
                val name = o.optString("name", "Сериал ${i + 1}")
                val catId = o.optString("category_id")
                val group = seriesCatMap[catId] ?: "Сериалы"
                val logo = o.optString("cover")
                val plot = o.optString("plot")
                val rating = o.optString("rating")
                val year = o.optString("releaseDate")

                allItems.add(
                    Channel(
                        id = "${playlist.id}_ser_$seriesId",
                        playlistId = playlist.id,
                        number = i + 1,
                        name = name,
                        streamUrl = "",
                        logoUrl = logo,
                        group = group,
                        userAgent = playlist.userAgent,
                        referer = playlist.referer,
                        contentType = ContentType.SERIES,
                        seriesId = seriesId,
                        plot = plot,
                        rating = rating,
                        year = year
                    )
                )
                seriesCount++
            }
        } catch (_: Exception) {
            // Some Xtream providers don't include Series
        }

        val epgUrl = "$server/xmltv.php?username=${enc(user)}&password=${enc(pass)}"
        return XtreamLoadResult(
            channels = allItems,
            epgUrl = epgUrl,
            liveCount = liveCount,
            vodCount = vodCount,
            seriesCount = seriesCount,
            expiryText = expiryText
        )
    }

    fun fetchSeriesEpisodes(
        playlist: PlaylistSource,
        seriesId: String,
        defaultUserAgent: String
    ): List<SeriesEpisode> {
        val server = playlist.xtreamServer.trim().trimEnd('/')
        val user = playlist.xtreamUsername.trim()
        val pass = playlist.xtreamPassword.trim()
        val ua = playlist.userAgent.ifBlank { defaultUserAgent }
        val url = "$server/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_series_info&series_id=${enc(seriesId)}"

        val root = JSONObject(fetchText(url, ua))
        val episodesObj = root.optJSONObject("episodes") ?: return emptyList()
        val result = mutableListOf<SeriesEpisode>()

        val seasonKeys = episodesObj.keys()
        while (seasonKeys.hasNext()) {
            val seasonKey = seasonKeys.next()
            val seasonNum = seasonKey.toIntOrNull() ?: 1
            val epArr = episodesObj.optJSONArray(seasonKey) ?: continue
            for (i in 0 until epArr.length()) {
                val ep = epArr.optJSONObject(i) ?: continue
                val id = ep.optString("id")
                if (id.isBlank()) continue
                val epNum = ep.optInt("episode_num", i + 1)
                val title = ep.optString("title", "Серия $epNum")
                val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                val info = ep.optJSONObject("info")
                val duration = info?.optString("duration").orEmpty()
                val plot = info?.optString("plot").orEmpty()
                val thumb = info?.optString("movie_image").orEmpty()
                val streamUrl = "$server/series/${enc(user)}/${enc(pass)}/$id.$ext"

                result.add(
                    SeriesEpisode(
                        id = id,
                        seasonNum = seasonNum,
                        episodeNum = epNum,
                        title = title,
                        streamUrl = streamUrl,
                        duration = duration,
                        plot = plot,
                        thumbUrl = thumb
                    )
                )
            }
        }
        return result.sortedWith(compareBy<SeriesEpisode> { it.seasonNum }.thenBy { it.episodeNum })
    }

    private fun fetchCategoryMap(url: String, userAgent: String): Map<String, String> {
        val map = HashMap<String, String>()
        val arr = fetchJsonArray(url, userAgent)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("category_id")
            val name = o.optString("category_name")
            if (id.isNotBlank() && name.isNotBlank()) map[id] = name
        }
        return map
    }

    private fun fetchJsonArray(url: String, userAgent: String): JSONArray {
        val text = fetchText(url, userAgent).trim()
        return if (text.startsWith("[")) JSONArray(text) else JSONArray()
    }

    private fun fetchText(url: String, userAgent: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            return resp.body?.string().orEmpty()
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
}
