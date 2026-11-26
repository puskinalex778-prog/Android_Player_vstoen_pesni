package com.etelevizor.iptv.data

import com.etelevizor.iptv.model.Channel
import com.etelevizor.iptv.model.EpgProgram
import com.etelevizor.iptv.model.PlaylistSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds archive / catch-up stream URLs supporting standard IPTV formats:
 * - default / template catchup-source ({utc}, ${start}, {lutc}, {duration}, {Y}, {m}, {d}, {H}, {M}, {S})
 * - append
 * - shift
 * - flussonic / flussonic-ts
 * - xc (Xtream Codes timeshift)
 */
object CatchupUrlBuilder {

    fun buildCatchupUrl(
        channel: Channel,
        program: EpgProgram,
        playlist: PlaylistSource?
    ): String {
        val startSec = program.startTimeMs / 1000L
        val endSec = program.endTimeMs / 1000L
        val nowSec = System.currentTimeMillis() / 1000L
        val durationSec = (endSec - startSec).coerceAtLeast(60L)
        val durationMin = (durationSec / 60L).coerceAtLeast(1L)
        val offsetSec = (nowSec - startSec).coerceAtLeast(0L)

        val type = channel.catchupType.lowercase().ifBlank {
            playlist?.catchupType?.lowercase().orEmpty()
        }

        // Xtream Codes archive
        if (type == "xc" && playlist != null && playlist.xtreamServer.isNotBlank() && channel.streamId.isNotBlank()) {
            val server = playlist.xtreamServer.trimEnd('/')
            val sdf = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val startStr = sdf.format(Date(program.startTimeMs))
            return "$server/timeshift/${playlist.xtreamUsername}/${playlist.xtreamPassword}/$durationMin/$startStr/${channel.streamId}.ts"
        }

        // Custom template in catchup-source
        if (channel.catchupSource.isNotBlank()) {
            val template = replacePlaceholders(
                channel.catchupSource,
                startSec,
                endSec,
                nowSec,
                durationSec,
                offsetSec
            )
            return if (type == "append" || !template.contains("://")) {
                appendToUrl(channel.streamUrl, template)
            } else {
                template
            }
        }

        val base = channel.streamUrl
        return when (type) {
            "flussonic", "flussonic-hls", "fs" -> {
                if (base.contains("index.m3u8")) {
                    base.replace("index.m3u8", "index-$startSec-$durationSec.m3u8")
                } else if (base.contains("video.m3u8")) {
                    base.replace("video.m3u8", "video-timeshift_abs-$startSec.m3u8")
                } else {
                    appendQuery(base, "utc=$startSec&lutc=$nowSec")
                }
            }
            "flussonic-ts" -> {
                val idx = base.lastIndexOf('/')
                if (idx > 8) "${base.substring(0, idx)}/timeshift_abs-$startSec.ts"
                else appendQuery(base, "utc=$startSec&lutc=$nowSec")
            }
            "shift", "default", "" -> {
                appendQuery(base, "utc=$startSec&lutc=$nowSec")
            }
            else -> appendQuery(base, "utc=$startSec&lutc=$nowSec")
        }
    }

    private fun replacePlaceholders(
        template: String,
        startSec: Long,
        endSec: Long,
        nowSec: Long,
        durationSec: Long,
        offsetSec: Long
    ): String {
        val utcCal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = startSec * 1000L
        }
        val y = String.format(Locale.US, "%04d", utcCal.get(java.util.Calendar.YEAR))
        val m = String.format(Locale.US, "%02d", utcCal.get(java.util.Calendar.MONTH) + 1)
        val d = String.format(Locale.US, "%02d", utcCal.get(java.util.Calendar.DAY_OF_MONTH))
        val h = String.format(Locale.US, "%02d", utcCal.get(java.util.Calendar.HOUR_OF_DAY))
        val min = String.format(Locale.US, "%02d", utcCal.get(java.util.Calendar.MINUTE))
        val s = String.format(Locale.US, "%02d", utcCal.get(java.util.Calendar.SECOND))

        return template
            .replace("\${start}", startSec.toString())
            .replace("{utc}", startSec.toString())
            .replace("\${timestamp}", startSec.toString())
            .replace("{start}", startSec.toString())
            .replace("\${end}", endSec.toString())
            .replace("{utcend}", endSec.toString())
            .replace("{end}", endSec.toString())
            .replace("{lutc}", nowSec.toString())
            .replace("\${lutc}", nowSec.toString())
            .replace("{duration}", durationSec.toString())
            .replace("\${duration}", durationSec.toString())
            .replace("{offset}", offsetSec.toString())
            .replace("\${offset}", offsetSec.toString())
            .replace("{Y}", y).replace("\${Y}", y)
            .replace("{m}", m).replace("\${m}", m)
            .replace("{d}", d).replace("\${d}", d)
            .replace("{H}", h).replace("\${H}", h)
            .replace("{M}", min).replace("\${M}", min)
            .replace("{S}", s).replace("\${S}", s)
    }

    private fun appendToUrl(base: String, suffix: String): String {
        if (suffix.startsWith("?") || suffix.startsWith("&")) {
            return if (base.contains("?")) "$base&${suffix.trimStart('?', '&')}" else "$base?${suffix.trimStart('?', '&')}"
        }
        return "$base$suffix"
    }

    private fun appendQuery(base: String, query: String): String {
        return if (base.contains("?")) "$base&$query" else "$base?$query"
    }
}
