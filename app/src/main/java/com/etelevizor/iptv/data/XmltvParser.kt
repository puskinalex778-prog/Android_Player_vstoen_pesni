package com.etelevizor.iptv.data

import android.util.Xml
import com.etelevizor.iptv.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * Memory-efficient streaming XMLTV parser (supports both plain .xml and compressed .xml.gz).
 */
object XmltvParser {

    fun parseAndStore(
        rawStream: InputStream,
        epgDb: EpgDatabase,
        timeShiftHours: Float,
        keepPastDays: Int,
        keepFutureDays: Int = 3
    ): Int {
        val buffered = BufferedInputStream(rawStream, 64 * 1024)
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()

        val stream: InputStream = if (b1 == 0x1F && b2 == 0x8B) {
            GZIPInputStream(buffered, 64 * 1024)
        } else {
            buffered
        }

        val shiftMs = (timeShiftHours * 3600_000f).toLong()
        val nowMs = System.currentTimeMillis()
        val minKeepMs = nowMs - keepPastDays.coerceAtLeast(1) * 24L * 3600_000L
        val maxKeepMs = nowMs + keepFutureDays.coerceAtLeast(1) * 24L * 3600_000L

        val aliases = HashMap<String, String>()
        val batch = ArrayList<EpgProgram>(1000)
        var totalInserted = 0

        stream.use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)

            var currentChannelId = ""
            var inProgramme = false
            var progChannel = ""
            var progStart = 0L
            var progStop = 0L
            var progTitle = ""
            var progDesc = ""
            var progCategory = ""
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase().orEmpty()
                        currentTag = tag
                        when (tag) {
                            "channel" -> {
                                currentChannelId = parser.getAttributeValue(null, "id")
                                    ?.trim()?.lowercase().orEmpty()
                            }
                            "programme" -> {
                                inProgramme = true
                                progChannel = parser.getAttributeValue(null, "channel")
                                    ?.trim()?.lowercase().orEmpty()
                                progStart = parseXmltvTime(parser.getAttributeValue(null, "start")) + shiftMs
                                progStop = parseXmltvTime(parser.getAttributeValue(null, "stop")) + shiftMs
                                progTitle = ""
                                progDesc = ""
                                progCategory = ""
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            if (inProgramme) {
                                when (currentTag) {
                                    "title" -> if (progTitle.isEmpty()) progTitle = text
                                    "desc" -> if (progDesc.isEmpty()) progDesc = text
                                    "category" -> if (progCategory.isEmpty()) progCategory = text
                                }
                            } else if (currentChannelId.isNotEmpty() && currentTag == "display-name") {
                                aliases[text.lowercase()] = currentChannelId
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase().orEmpty()
                        if (tag == "channel") {
                            currentChannelId = ""
                        } else if (tag == "programme" && inProgramme) {
                            inProgramme = false
                            if (progChannel.isNotEmpty() &&
                                progTitle.isNotEmpty() &&
                                progStart > 0L &&
                                progStop > progStart &&
                                progStop >= minKeepMs &&
                                progStart <= maxKeepMs
                            ) {
                                batch.add(
                                    EpgProgram(
                                        channelKey = progChannel,
                                        title = progTitle,
                                        description = progDesc,
                                        category = progCategory,
                                        startTimeMs = progStart,
                                        endTimeMs = progStop
                                    )
                                )
                                if (batch.size >= 1000) {
                                    epgDb.insertProgramsBatch(batch)
                                    totalInserted += batch.size
                                    batch.clear()
                                }
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        }

        if (aliases.isNotEmpty()) epgDb.insertAliases(aliases)
        if (batch.isNotEmpty()) {
            epgDb.insertProgramsBatch(batch)
            totalInserted += batch.size
        }
        return totalInserted
    }

    private fun parseXmltvTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw.trim()
        return try {
            val parts = cleaned.split(" ", limit = 2)
            val datePart = parts[0].take(14).padEnd(14, '0')
            val tzPart = if (parts.size > 1) parts[1].trim() else "+0000"
            val sdf = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse("$datePart $tzPart")?.time ?: 0L
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(cleaned.take(14))?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
