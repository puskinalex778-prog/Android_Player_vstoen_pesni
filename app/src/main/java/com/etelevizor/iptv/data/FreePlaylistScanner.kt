package com.etelevizor.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URI

data class DiscoveredPlaylist(
    val title: String,
    val url: String,
    val sourceHost: String,
    val epgUrl: String = ""
)

/**
 * Live M3U / M3U8 web-page and GitHub repository parser/scanner.
 * Extracts .m3u / .m3u8 playlist URLs from websites like https://m3u.su/,
 * GitHub repos like slava-67/TV, or any custom URL entered by the user.
 */
class FreePlaylistScanner(private val httpClient: OkHttpClient) {

    private val m3uUrlRegex = Regex(
        """https?://[^\s"'<>\\()\[\]]+\.(?:m3u8?|m3u\.gz)(?:\?[^\s"'<>\\()\[\]]*)?""",
        RegexOption.IGNORE_CASE
    )
    private val subPageRegex = Regex(
        """href\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    suspend fun scanSourceUrl(rawUrl: String): Result<List<DiscoveredPlaylist>> = withContext(Dispatchers.IO) {
        try {
            val url = rawUrl.trim().let {
                if (!it.startsWith("http://", true) && !it.startsWith("https://", true)) "https://$it" else it
            }

            // If user entered a direct .m3u / .m3u8 link, return it immediately
            if (url.lowercase().substringBefore("?").let {
                    it.endsWith(".m3u") || it.endsWith(".m3u8") || it.endsWith(".m3u.gz")
                }
            ) {
                val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "Плейлист M3U" }
                return@withContext Result.success(
                    listOf(DiscoveredPlaylist(name, url, hostOf(url)))
                )
            }

            // If user entered a GitHub repo URL, scan via GitHub API
            if (url.contains("github.com/", ignoreCase = true) && !url.contains("raw.githubusercontent.com")) {
                val ghResults = scanGitHubRepoUrl(url)
                if (ghResults.isNotEmpty()) return@withContext Result.success(ghResults)
            }

            val found = LinkedHashMap<String, DiscoveredPlaylist>()
            val host = hostOf(url)

            val html = fetchText(url)
            extractM3uLinks(html, host, found)

            // Also crawl up to 6 promising internal subpages on sites like m3u.su (e.g. /playlist, /iptv, /russia, etc.)
            val baseUri = runCatching { URI(url) }.getOrNull()
            if (baseUri != null) {
                val subLinks = subPageRegex.findAll(html)
                    .map { it.groupValues[1].trim() }
                    .filter { href ->
                        val l = href.lowercase()
                        !l.startsWith("#") && !l.startsWith("javascript:") && !l.startsWith("mailto:") &&
                            (l.contains("m3u") || l.contains("iptv") || l.contains("playlist") ||
                                l.contains("tv") || l.contains("rus") || l.contains("free") || l.contains("плейлист"))
                    }
                    .mapNotNull { href ->
                        runCatching { baseUri.resolve(href).toString() }.getOrNull()
                    }
                    .filter { hostOf(it) == host && it != url }
                    .distinct()
                    .take(6)

                for (sub in subLinks) {
                    try {
                        val subHtml = fetchText(sub)
                        extractM3uLinks(subHtml, host, found)
                    } catch (_: Exception) {
                        // Ignore individual subpage failure
                    }
                }
            }

            Result.success(found.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanGitHubRepo(owner: String, repo: String, branch: String): Result<List<DiscoveredPlaylist>> =
        withContext(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.github.com/repos/$owner/$repo/contents?ref=$branch"
                val json = fetchText(apiUrl)
                val arr = JSONArray(json)
                val found = mutableListOf<DiscoveredPlaylist>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    val downloadUrl = o.optString("download_url")
                    if (downloadUrl.isNotBlank() &&
                        (name.endsWith(".m3u", true) || name.endsWith(".m3u8", true))
                    ) {
                        found.add(
                            DiscoveredPlaylist(
                                title = "$owner/$repo • $name",
                                url = downloadUrl,
                                sourceHost = "github.com/$owner/$repo",
                                epgUrl = FreePlaylistsCatalog.DEFAULT_AUTO_EPG_URL
                            )
                        )
                    }
                }
                Result.success(found)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun scanAllDefaultSources(): List<DiscoveredPlaylist> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, DiscoveredPlaylist>()

        // 1. Scan slava-67/TV (IPTV branch)
        scanGitHubRepo("slava-67", "TV", "IPTV").getOrNull()?.forEach {
            merged.putIfAbsent(it.url, it)
        }

        // 2. Scan m3u.su
        scanSourceUrl("https://m3u.su/").getOrNull()?.forEach {
            merged.putIfAbsent(it.url, it)
        }

        // 3. Add curated self-updating Russian & World playlists
        FreePlaylistsCatalog.playlists.forEach { preset ->
            merged.putIfAbsent(
                preset.url,
                DiscoveredPlaylist(
                    title = preset.title,
                    url = preset.url,
                    sourceHost = hostOf(preset.url),
                    epgUrl = preset.recommendedEpgUrl
                )
            )
        }

        merged.values.toList()
    }

    private fun scanGitHubRepoUrl(url: String): List<DiscoveredPlaylist> {
        return try {
            val parts = URI(url).path.trim('/').split('/')
            if (parts.size < 2) return emptyList()
            val owner = parts[0]
            val repo = parts[1].removeSuffix(".git")
            val branch = if (parts.size >= 4 && parts[2] == "tree") parts[3] else ""
            val apiUrl = if (branch.isNotBlank()) {
                "https://api.github.com/repos/$owner/$repo/contents?ref=$branch"
            } else {
                "https://api.github.com/repos/$owner/$repo/contents"
            }
            val arr = JSONArray(fetchText(apiUrl))
            val result = mutableListOf<DiscoveredPlaylist>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                val dl = o.optString("download_url")
                if (dl.isNotBlank() && (name.endsWith(".m3u", true) || name.endsWith(".m3u8", true))) {
                    result.add(DiscoveredPlaylist("$owner/$repo • $name", dl, "github.com/$owner/$repo"))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractM3uLinks(
        html: String,
        sourceHost: String,
        dest: LinkedHashMap<String, DiscoveredPlaylist>
    ) {
        for (m in m3uUrlRegex.findAll(html)) {
            val link = m.value.trimEnd('.', ',', ';', ')', '"', '\'')
            val lower = link.lowercase()
            // Skip invalid dummy hostnames like https://status.m3u
            val linkHost = hostOf(link)
            if (linkHost.isBlank() || linkHost.endsWith(".m3u") || linkHost.endsWith(".m3u8")) continue
            if (lower.contains("example.com") || lower.contains("localhost")) continue

            val fileName = link.substringAfterLast('/').substringBefore('?').ifBlank { linkHost }
            dest.putIfAbsent(
                link,
                DiscoveredPlaylist(
                    title = "$fileName ($linkHost)",
                    url = link,
                    sourceHost = sourceHost
                )
            )
        }
    }

    private fun fetchText(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty() }.getOrDefault("")
}
