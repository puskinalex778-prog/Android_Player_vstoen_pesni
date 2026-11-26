package com.etelevizor.iptv.data

import com.etelevizor.iptv.model.Channel
import com.etelevizor.iptv.model.ContentType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest

data class M3uParseResult(
    val channels: List<Channel>,
    val epgUrls: List<String>,
    val defaultCatchupType: String,
    val defaultCatchupDays: Int,
    val defaultTvgShift: Float
)

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

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 32 * 1024).use { reader ->
            var lineNum = 0
            while (true) {
                var line = reader.readLine() ?: break
                if (lineNum == 0 && line.startsWith("\uFEFF")) {
                    line = line.substring(1)
                }
                lineNum++
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

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

                    trimmed.startsWith("#") -> {
                        // Ignore other directives / comments
                    }

                    else -> {
                        val url = trimmed
                        if (url.contains("://")) {
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

                            val ua = pendingUserAgent.ifBlank { playlistUserAgent }
                            val ref = pendingReferer.ifBlank { playlistReferer }

                            val contentType = detectContentType(url, group)
                            val id = stableChannelId(playlistId, tvgId, name, url)

                            channels.add(
                                Channel(
                                    id = id,
                                    playlistId = playlistId,
                                    number = chNo,
                                    name = name,
                                    streamUrl = url,
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
        }

        return M3uParseResult(
            channels = channels,
            epgUrls = epgUrls.toList(),
            defaultCatchupType = defaultCatchupType,
            defaultCatchupDays = defaultCatchupDays,
            defaultTvgShift = defaultTvgShift
        )
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
