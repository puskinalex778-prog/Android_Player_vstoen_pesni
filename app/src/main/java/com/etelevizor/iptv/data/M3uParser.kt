package com.etelevizor.iptv.data

import android.util.Xml
import com.etelevizor.iptv.model.Channel
import com.etelevizor.iptv.model.ContentType
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

data class M3uParseResult(
    val channels: List<Channel>,
    val epgUrls: List<String>,
    val defaultCatchupType: String,
    val defaultCatchupDays: Int,
    val defaultTvgShift: Float
)

/**
 * Universal IPTV playlist parser supporting:
 * - M3U / M3U8 (plain or .gz compressed)
 * - Automatic UTF-8 / Windows-1251 (CP1251) encoding detection for Russian playlists
 * - XSPF (.xspf XML playlists)
 * - PLS (.pls playlists)
 * - TXT (Name,http://... line playlists)
 * - Pipe headers in URLs (http://.../stream.m3u8|User-Agent=...&Referer=...)
 * - #EXTVLCOPT, #EXTHTTP, #EXTGRP, #EXTINF attributes & catch-up tags
 */
object M3uParser {

    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    fun parse(
        inputStream: InputStream,
        playlistId: String,
        playlistUserAgent: String = "",
        playlistReferer: String = "",
        playlistCatchupType: String = "",
        playlistCatchupDays: Int = 0
    ): M3uParseResult {
        val rawBytes = readMaybeGzipBytes(inputStream)
        val text = decodeSmartCharset(rawBytes)
        val trimmedStart = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')

        return when {
            trimmedStart.startsWith("<?xml", true) || trimmedStart.startsWith("<playlist", true) ->
                parseXspf(rawBytes, playlistId, playlistUserAgent, playlistReferer, playlistCatchupType, playlistCatchupDays)
            trimmedStart.startsWith("[playlist]", true) ->
                parsePls(text, playlistId, playlistUserAgent, playlistReferer, playlistCatchupType, playlistCatchupDays)
            trimmedStart.startsWith("#EXT", true) ->
                parseM3uText(text, playlistId, playlistUserAgent, playlistReferer, playlistCatchupType, playlistCatchupDays)
            else ->
                parseM3uOrTxt(text, playlistId, playlistUserAgent, playlistReferer, playlistCatchupType, playlistCatchupDays)
        }
    }

    private fun readMaybeGzipBytes(inputStream: InputStream): ByteArray {
        val bis = BufferedInputStream(inputStream, 64 * 1024)
        bis.mark(4)
        val b1 = bis.read()
        val b2 = bis.read()
        bis.reset()
        val effective: InputStream = if (b1 == 0x1F && b2 == 0x8B) {
            GZIPInputStream(bis, 64 * 1024)
        } else {
            bis
        }
        return effective.use { it.readBytes() }
    }

    private fun decodeSmartCharset(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        // If valid UTF-8 without replacement characters, use UTF-8
        if (!utf8.contains('\uFFFD')) return utf8
        // Fallback for older Russian playlists saved in Windows-1251
        return try {
            String(bytes, Charset.forName("windows-1251"))
        } catch (_: Exception) {
            utf8
        }
    }

    private fun parseM3uText(
        text: String,
        playlistId: String,
        playlistUserAgent: String,
        playlistReferer: String,
        playlistCatchupType: String,
        playlistCatchupDays: Int
    ): M3uParseResult {
        val channels = mutableListOf<Channel>()
        val epgUrls = LinkedHashSet<String>()
        var defaultCatchupType = playlistCatchupType
        var defaultCatchupDays = playlistCatchupDays
        var defaultCatchupSource = ""
        var defaultTvgShift = 0f

        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingName = ""
        var pendingGroup = ""
        var pendingUserAgent = ""
        var pendingReferer = ""
        var hasPendingExtInf = false
        var channelCounter = 0

        text.lineSequence().forEachIndexed { idx, rawLine ->
            var line = rawLine
            if (idx == 0 && line.startsWith("\uFEFF")) line = line.substring(1)
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            when {
                trimmed.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val headerAttrs = parseAttributes(trimmed)
                    val rawEpg = headerAttrs["url-tvg"]
                        ?: headerAttrs["x-tvg-url"]
                        ?: headerAttrs["tvg-url"]
                        ?: ""
                    rawEpg.split(",", ";").map { it.trim() }.filter { it.startsWith("http", true) }
                        .forEach { epgUrls.add(it) }
                    headerAttrs["catchup"]?.takeIf { it.isNotBlank() }?.let {
                        if (defaultCatchupType.isBlank()) defaultCatchupType = it
                    }
                    headerAttrs["catchup-type"]?.takeIf { it.isNotBlank() }?.let {
                        if (defaultCatchupType.isBlank()) defaultCatchupType = it
                    }
                    headerAttrs["catchup-days"]?.toIntOrNull()?.let {
                        if (defaultCatchupDays <= 0) defaultCatchupDays = it
                    }
                    headerAttrs["catchup-source"]?.takeIf { it.isNotBlank() }?.let {
                        defaultCatchupSource = it
                    }
                    headerAttrs["tvg-shift"]?.toFloatOrNull()?.let {
                        defaultTvgShift = it
                    }
                }

                trimmed.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = trimmed.substringAfter(":")
                    val commaIdx = findTitleComma(body)
                    val attrPart = if (commaIdx >= 0) body.substring(0, commaIdx) else body
                    pendingName = if (commaIdx >= 0) body.substring(commaIdx + 1).trim() else ""
                    pendingAttrs = parseAttributes(attrPart)
                    pendingGroup = pendingAttrs["group-title"]?.trim().orEmpty()
                    pendingUserAgent = ""
                    pendingReferer = ""
                    hasPendingExtInf = true
                }

                trimmed.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val grp = trimmed.substringAfter(":").trim()
                    if (grp.isNotBlank()) pendingGroup = grp
                }

                trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = trimmed.substringAfter(":").trim()
                    if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                        pendingUserAgent = opt.substringAfter("=").trim()
                    } else if (opt.startsWith("http-referrer=", ignoreCase = true) ||
                        opt.startsWith("http-referer=", ignoreCase = true)
                    ) {
                        pendingReferer = opt.substringAfter("=").trim()
                    }
                }

                trimmed.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    try {
                        val obj = JSONObject(trimmed.substringAfter(":").trim())
                        obj.optString("User-Agent").takeIf { it.isNotBlank() }?.let { pendingUserAgent = it }
                        obj.optString("user-agent").takeIf { it.isNotBlank() }?.let { pendingUserAgent = it }
                        obj.optString("Referer").takeIf { it.isNotBlank() }?.let { pendingReferer = it }
                        obj.optString("referer").takeIf { it.isNotBlank() }?.let { pendingReferer = it }
                    } catch (_: Exception) {
                        // Ignore malformed JSON
                    }
                }

                trimmed.startsWith("#") -> Unit

                else -> {
                    if (trimmed.contains("://")) {
                        val (cleanUrl, pipeUa, pipeRef) = splitPipeHeaders(trimmed)
                        channelCounter++
                        val name = when {
                            pendingName.isNotBlank() -> pendingName
                            !pendingAttrs["tvg-name"].isNullOrBlank() -> pendingAttrs["tvg-name"]!!
                            else -> "Канал $channelCounter"
                        }
                        val chNo = pendingAttrs["tvg-chno"]?.toIntOrNull()
                            ?: pendingAttrs["chno"]?.toIntOrNull()
                            ?: channelCounter
                        val group = pendingGroup.ifBlank { "Общие" }
                        val tvgId = pendingAttrs["tvg-id"].orEmpty().trim()
                        val tvgName = pendingAttrs["tvg-name"].orEmpty().trim()
                        val logo = pendingAttrs["tvg-logo"].orEmpty().trim()

                        val catchupType = pendingAttrs["catchup"]
                            ?: pendingAttrs["catchup-type"]
                            ?: defaultCatchupType
                        val catchupDays = pendingAttrs["catchup-days"]?.toIntOrNull()
                            ?: pendingAttrs["tvg-rec"]?.toIntOrNull()
                            ?: pendingAttrs["timeshift"]?.toIntOrNull()
                            ?: defaultCatchupDays
                        val catchupSource = pendingAttrs["catchup-source"]?.takeIf { it.isNotBlank() }
                            ?: defaultCatchupSource

                        val ua = pipeUa.ifBlank { pendingUserAgent.ifBlank { playlistUserAgent } }
                        val ref = pipeRef.ifBlank { pendingReferer.ifBlank { playlistReferer } }

                        val contentType = detectContentType(cleanUrl, group)
                        val id = stableChannelId(playlistId, tvgId, name, cleanUrl)

                        channels.add(
                            Channel(
                                id = id,
                                playlistId = playlistId,
                                number = chNo,
                                name = name,
                                streamUrl = cleanUrl,
                                logoUrl = logo,
                                group = group,
                                tvgId = tvgId,
                                tvgName = tvgName,
                                catchupType = catchupType,
                                catchupDays = catchupDays,
                                catchupSource = catchupSource,
                                userAgent = ua,
                                referer = ref,
                                contentType = contentType
                            )
                        )
                    }
                    if (hasPendingExtInf) {
                        pendingAttrs = emptyMap()
                        pendingName = ""
                        pendingGroup = ""
                        pendingUserAgent = ""
                        pendingReferer = ""
                        hasPendingExtInf = false
                    }
                }
            }
        }

        return M3uParseResult(
            channels = channels,
            epgUrls = epgUrls.toList(),
            defaultCatchupType = defaultCatchupType,
            defaultCatchupDays = defaultCatchupDays,
            defaultTvgShift = defaultTvgShift
        )
    }

    private fun parseM3uOrTxt(
        text: String,
        playlistId: String,
        playlistUserAgent: String,
        playlistReferer: String,
        playlistCatchupType: String,
        playlistCatchupDays: Int
    ): M3uParseResult {
        if (text.contains("#EXTINF:", ignoreCase = true)) {
            return parseM3uText(text, playlistId, playlistUserAgent, playlistReferer, playlistCatchupType, playlistCatchupDays)
        }
        val channels = mutableListOf<Channel>()
        var currentGroup = "Общие"
        var num = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val idx = line.indexOf("://")
            if (idx > 0) {
                val comma = line.indexOf(',')
                val (name, rawUrl) = if (comma in 1 until idx) {
                    line.substring(0, comma).trim() to line.substring(comma + 1).trim()
                } else {
                    "Канал ${num + 1}" to line
                }
                val (cleanUrl, pipeUa, pipeRef) = splitPipeHeaders(rawUrl)
                num++
                channels.add(
                    Channel(
                        id = stableChannelId(playlistId, "", name, cleanUrl),
                        playlistId = playlistId,
                        number = num,
                        name = name,
                        streamUrl = cleanUrl,
                        group = currentGroup,
                        catchupType = playlistCatchupType,
                        catchupDays = playlistCatchupDays,
                        userAgent = pipeUa.ifBlank { playlistUserAgent },
                        referer = pipeRef.ifBlank { playlistReferer },
                        contentType = detectContentType(cleanUrl, currentGroup)
                    )
                )
            } else if (line.endsWith(",#genre#", ignoreCase = true)) {
                currentGroup = line.substringBefore(",").trim().ifBlank { "Общие" }
            }
        }
        return M3uParseResult(channels, emptyList(), playlistCatchupType, playlistCatchupDays, 0f)
    }

    private fun parsePls(
        text: String,
        playlistId: String,
        playlistUserAgent: String,
        playlistReferer: String,
        playlistCatchupType: String,
        playlistCatchupDays: Int
    ): M3uParseResult {
        val files = LinkedHashMap<Int, String>()
        val titles = HashMap<Int, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when {
                key.startsWith("File", true) -> key.drop(4).toIntOrNull()?.let { files[it] = value }
                key.startsWith("Title", true) -> key.drop(5).toIntOrNull()?.let { titles[it] = value }
            }
        }
        val channels = files.entries.sortedBy { it.key }.mapIndexed { idx, (k, rawUrl) ->
            val (cleanUrl, pipeUa, pipeRef) = splitPipeHeaders(rawUrl)
            val name = titles[k]?.takeIf { it.isNotBlank() } ?: "Канал ${idx + 1}"
            Channel(
                id = stableChannelId(playlistId, "", name, cleanUrl),
                playlistId = playlistId,
                number = idx + 1,
                name = name,
                streamUrl = cleanUrl,
                group = "PLS плейлист",
                catchupType = playlistCatchupType,
                catchupDays = playlistCatchupDays,
                userAgent = pipeUa.ifBlank { playlistUserAgent },
                referer = pipeRef.ifBlank { playlistReferer }
            )
        }
        return M3uParseResult(channels, emptyList(), playlistCatchupType, playlistCatchupDays, 0f)
    }

    private fun parseXspf(
        bytes: ByteArray,
        playlistId: String,
        playlistUserAgent: String,
        playlistReferer: String,
        playlistCatchupType: String,
        playlistCatchupDays: Int
    ): M3uParseResult {
        val channels = mutableListOf<Channel>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(bytes), null)
            var inTrack = false
            var tag = ""
            var title = ""
            var location = ""
            var image = ""
            var num = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name?.lowercase().orEmpty()
                        if (tag == "track") {
                            inTrack = true; title = ""; location = ""; image = ""
                        }
                    }
                    XmlPullParser.TEXT -> if (inTrack) {
                        val txt = parser.text?.trim().orEmpty()
                        if (txt.isNotEmpty()) {
                            when (tag) {
                                "title" -> if (title.isEmpty()) title = txt
                                "location" -> if (location.isEmpty()) location = txt
                                "image" -> if (image.isEmpty()) image = txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("track", true) && inTrack) {
                            inTrack = false
                            if (location.contains("://")) {
                                val (cleanUrl, pipeUa, pipeRef) = splitPipeHeaders(location)
                                num++
                                val chName = title.ifBlank { "Канал $num" }
                                channels.add(
                                    Channel(
                                        id = stableChannelId(playlistId, "", chName, cleanUrl),
                                        playlistId = playlistId,
                                        number = num,
                                        name = chName,
                                        streamUrl = cleanUrl,
                                        logoUrl = image,
                                        group = "XSPF плейлист",
                                        catchupType = playlistCatchupType,
                                        catchupDays = playlistCatchupDays,
                                        userAgent = pipeUa.ifBlank { playlistUserAgent },
                                        referer = pipeRef.ifBlank { playlistReferer }
                                    )
                                )
                            }
                        }
                        tag = ""
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Return whatever tracks were parsed
        }
        return M3uParseResult(channels, emptyList(), playlistCatchupType, playlistCatchupDays, 0f)
    }

    private fun splitPipeHeaders(rawUrl: String): Triple<String, String, String> {
        val pipeIdx = rawUrl.indexOf('|')
        if (pipeIdx < 0) return Triple(rawUrl, "", "")
        val cleanUrl = rawUrl.substring(0, pipeIdx).trim()
        val params = rawUrl.substring(pipeIdx + 1)
        var ua = ""
        var ref = ""
        params.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val k = pair.substring(0, eq).trim().lowercase()
                val v = pair.substring(eq + 1).trim()
                if (k == "user-agent") ua = v
                if (k == "referer" || k == "referrer") ref = v
            }
        }
        return Triple(cleanUrl, ua, ref)
    }

    private fun parseAttributes(input: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (match in attrRegex.findAll(input)) {
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty { match.groupValues[4] }
            }
            map[key] = value
        }
        return map
    }

    private fun findTitleComma(extInfBody: String): Int {
        var inQuotes = false
        var quoteChar = '"'
        for (i in extInfBody.indices) {
            val c = extInfBody[i]
            if (inQuotes) {
                if (c == quoteChar) inQuotes = false
            } else {
                if (c == '"' || c == '\'') {
                    inQuotes = true
                    quoteChar = c
                } else if (c == ',') {
                    return i
                }
            }
        }
        return -1
    }

    private fun detectContentType(url: String, group: String): ContentType {
        val lowerUrl = url.lowercase()
        val lowerGroup = group.lowercase()
        if (lowerUrl.contains("/movie/") || lowerGroup.contains("vod") ||
            lowerGroup.contains("фильмы") || lowerGroup.contains("кинозал") ||
            lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".avi")
        ) {
            if (!lowerUrl.contains("/live/")) return ContentType.MOVIE
        }
        return ContentType.LIVE
    }

    fun stableChannelId(playlistId: String, tvgId: String, name: String, url: String): String {
        val raw = "$playlistId|$tvgId|$name|$url"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
