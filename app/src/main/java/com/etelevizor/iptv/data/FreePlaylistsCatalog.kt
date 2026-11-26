package com.etelevizor.iptv.data

data class FreePlaylistPreset(
    val title: String,
    val subtitle: String,
    val region: String,
    val url: String,
    val recommendedEpgUrl: String = ""
)

data class FreeEpgPreset(
    val title: String,
    val subtitle: String,
    val url: String
)

/**
 * Curated catalog of open, publicly maintained free-to-air IPTV playlists (iptv-org)
 * and open EPG sources for Russian & International channels.
 */
object FreePlaylistsCatalog {

    val playlists: List<FreePlaylistPreset> = listOf(
        FreePlaylistPreset(
            title = "🇷🇺 Россия — открытые ТВ каналы (РФ)",
            subtitle = "Бесплатные открытые российские каналы (iptv-org)",
            region = "Россия / РФ / Русский",
            url = "https://iptv-org.github.io/iptv/countries/ru.m3u",
            recommendedEpgUrl = "https://iptvx.one/epg/epg.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇷🇺 Все русскоязычные каналы (РФ + СНГ + Мир)",
            subtitle = "Сборник открытых каналов на русском языке",
            region = "Россия / Русский / СНГ",
            url = "https://iptv-org.github.io/iptv/languages/rus.m3u",
            recommendedEpgUrl = "https://iptvx.one/epg/epg.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🌍 Весь мир — Зарубежные + РФ (Общий каталог)",
            subtitle = "Тысячи открытых бесплатных каналов со всего мира",
            region = "Мир / Зарубежные / Все",
            url = "https://iptv-org.github.io/iptv/index.m3u"
        ),
        FreePlaylistPreset(
            title = "🇬🇧 Англоязычные каналы (США / Великобритания / Мир)",
            subtitle = "Открытые каналы на английском языке",
            region = "Зарубежные / English / США / UK",
            url = "https://iptv-org.github.io/iptv/languages/eng.m3u"
        ),
        FreePlaylistPreset(
            title = "🎬 Кино и фильмы (Международные)",
            subtitle = "Бесплатные открытые киноканалы",
            region = "Жанры / Кино / Фильмы / Movies",
            url = "https://iptv-org.github.io/iptv/categories/movies.m3u"
        ),
        FreePlaylistPreset(
            title = "🎵 Музыкальные каналы (РФ и Мир)",
            subtitle = "Клипы, концерты и музыкальные эфиры",
            region = "Жанры / Музыка / Music",
            url = "https://iptv-org.github.io/iptv/categories/music.m3u"
        ),
        FreePlaylistPreset(
            title = "⚽ Спорт (Международные)",
            subtitle = "Спортивные трансляции и каналы",
            region = "Жанры / Спорт / Sports",
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u"
        ),
        FreePlaylistPreset(
            title = "📰 Новости (Мировые и РФ)",
            subtitle = "Круглосуточные новостные телеканалы",
            region = "Жанры / Новости / News",
            url = "https://iptv-org.github.io/iptv/categories/news.m3u"
        ),
        FreePlaylistPreset(
            title = "🧸 Детские и мультфильмы",
            subtitle = "Открытые детские каналы",
            region = "Жанры / Детские / Kids",
            url = "https://iptv-org.github.io/iptv/categories/kids.m3u"
        ),
        FreePlaylistPreset(
            title = "🔬 Документальные и наука",
            subtitle = "Познавательные и научные каналы",
            region = "Жанры / Документальные / Documentary",
            url = "https://iptv-org.github.io/iptv/categories/documentary.m3u"
        ),
        FreePlaylistPreset(
            title = "🎭 Развлечения и шоу",
            subtitle = "Развлекательные телеканалы",
            region = "Жанры / Развлечения / Entertainment",
            url = "https://iptv-org.github.io/iptv/categories/entertainment.m3u"
        ),
        FreePlaylistPreset(
            title = "🇧🇾 Беларусь — открытые каналы",
            subtitle = "Бесплатные телеканалы Беларуси",
            region = "СНГ / Беларусь",
            url = "https://iptv-org.github.io/iptv/countries/by.m3u",
            recommendedEpgUrl = "https://iptvx.one/epg/epg.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇰🇿 Казахстан — открытые каналы",
            subtitle = "Бесплатные телеканалы Казахстана",
            region = "СНГ / Казахстан",
            url = "https://iptv-org.github.io/iptv/countries/kz.m3u",
            recommendedEpgUrl = "https://iptvx.one/epg/epg.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇩🇪 Германия (Deutschland)",
            subtitle = "Открытые немецкие телеканалы",
            region = "Зарубежные / Германия / Europe",
            url = "https://iptv-org.github.io/iptv/countries/de.m3u"
        ),
        FreePlaylistPreset(
            title = "🇺🇸 США (United States)",
            subtitle = "Открытые американские каналы",
            region = "Зарубежные / США / USA",
            url = "https://iptv-org.github.io/iptv/countries/us.m3u"
        ),
        FreePlaylistPreset(
            title = "🇬🇧 Великобритания (UK)",
            subtitle = "Открытые британские телеканалы",
            region = "Зарубежные / Великобритания / UK",
            url = "https://iptv-org.github.io/iptv/countries/gb.m3u"
        ),
        FreePlaylistPreset(
            title = "🇫🇷 Франция (France)",
            subtitle = "Открытые французские каналы",
            region = "Зарубежные / Франция / Europe",
            url = "https://iptv-org.github.io/iptv/countries/fr.m3u"
        ),
        FreePlaylistPreset(
            title = "🇮🇹 Италия (Italia)",
            subtitle = "Открытые итальянские каналы",
            region = "Зарубежные / Италия / Europe",
            url = "https://iptv-org.github.io/iptv/countries/it.m3u"
        ),
        FreePlaylistPreset(
            title = "🇪🇸 Испания (España)",
            subtitle = "Открытые испанские каналы",
            region = "Зарубежные / Испания / Europe",
            url = "https://iptv-org.github.io/iptv/countries/es.m3u"
        ),
        FreePlaylistPreset(
            title = "🇹🇷 Турция (Türkiye)",
            subtitle = "Открытые турецкие каналы",
            region = "Зарубежные / Турция",
            url = "https://iptv-org.github.io/iptv/countries/tr.m3u"
        ),
        FreePlaylistPreset(
            title = "🇯🇵 Япония (Japan)",
            subtitle = "Открытые японские каналы",
            region = "Зарубежные / Япония / Asia",
            url = "https://iptv-org.github.io/iptv/countries/jp.m3u"
        )
    )

    val epgPresets: List<FreeEpgPreset> = listOf(
        FreeEpgPreset(
            title = "IPTVX.one — РФ и СНГ (Лёгкий EPG .xml.gz)",
            subtitle = "Рекомендуется для русских каналов и ТВ-приставок",
            url = "https://iptvx.one/epg/epg_lite.xml.gz"
        ),
        FreeEpgPreset(
            title = "IPTVX.one — РФ и СНГ (Полный EPG .xml.gz)",
            subtitle = "Подробная телепрограмма с описаниями передач",
            url = "https://iptvx.one/epg/epg.xml.gz"
        ),
        FreeEpgPreset(
            title = "EPG.one — Международный + РФ (.xml.gz)",
            subtitle = "Бесплатная телепрограмма для российских и зарубежных каналов",
            url = "https://epg.one/epg.xml.gz"
        )
    )

    fun search(query: String): List<FreePlaylistPreset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return playlists
        return playlists.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.region.lowercase().contains(q)
        }
    }
}
