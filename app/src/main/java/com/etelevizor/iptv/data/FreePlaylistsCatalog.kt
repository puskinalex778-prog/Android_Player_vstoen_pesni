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
 * Curated catalog of verified live free-to-air / self-updating IPTV playlists
 * and verified XMLTV EPG sources (including Gabbarit, IPTVX.one, Filmax, CineMan, ProgramTV, EPGshare01).
 */
object FreePlaylistsCatalog {

    const val DEFAULT_AUTO_PLAYLIST_ID = "auto_starter_iptv"
    const val DEFAULT_AUTO_PLAYLIST_NAME = "Автообновляемый IPTV (РФ + Мир)"
    const val DEFAULT_AUTO_PLAYLIST_URL = "https://raw.githubusercontent.com/slava-67/TV/IPTV/avto-full.m3u"
    const val FALLBACK_AUTO_PLAYLIST_URL = "https://iptv-org.github.io/iptv/languages/rus.m3u"
    const val DEFAULT_AUTO_EPG_URL = "http://gabbarit.drm-play.com/epg_lite.xml.gz"

    val playlists: List<FreePlaylistPreset> = listOf(
        // === Самообновляемые российские и СНГ сборники (проверено 200 OK) ===
        FreePlaylistPreset(
            title = "⭐ Автообновляемый Full IPTV (slava-67 • РФ + Кино + СНГ)",
            subtitle = "Большой самообновляемый плейлист (ТВ, кинозалы, спорт) + EPG",
            region = "Россия / РФ / Русский / Автообновляемый / Кино / СНГ",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/avto-full.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "⭐ Dimonovich FREE TV (РФ + Мир, самообновляемый)",
            subtitle = "Популярный самообновляемый сборник Dimonovich с EPG",
            region = "Россия / РФ / Русский / Dimonovich / Автообновляемый",
            url = "https://raw.githubusercontent.com/Dimonovich/TV/Dimonovich/FREE/TV",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇷🇺 NGRCH IPTV Россия (подборка РФ каналов)",
            subtitle = "Российские телеканалы с привязкой телепрограммы EPG",
            region = "Россия / РФ / Русский / NGRCH",
            url = "https://ngrch.github.io/iptv/ru.m3u",
            recommendedEpgUrl = "https://iptvx.one/epg/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇷🇺 Free-TV Россия (проверенные открытые потоки)",
            subtitle = "Официальный чистый плейлист Free-TV по России",
            region = "Россия / РФ / Free-TV",
            url = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_russia.m3u8",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "📺 Автообновляемый ТВ Стандарт (slava-67 • avto.m3u)",
            subtitle = "Оптимальный самообновляемый список ТВ-каналов без лишнего",
            region = "Россия / РФ / Русский / Автообновляемый",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/avto.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "📺 Автообновляемый ТВ Мини (slava-67 • avtomini.m3u)",
            subtitle = "Лёгкая сборка основных телеканалов",
            region = "Россия / РФ / Русский / Мини",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/avtomini.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🎬 Кинофильмы и кинозалы (slava-67 • kinofilm.m3u)",
            subtitle = "Огромная подборка кинозалов и фильмов",
            region = "Кино / Фильмы / РФ / Русский",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/kinofilm.m3u",
            recommendedEpgUrl = "https://cineman.su/epg/epg_cineman.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🎬 Кино+ (slava-67 • kino-plus.m3u)",
            subtitle = "Киноканалы и фильмовые подборки",
            region = "Кино / Фильмы / РФ / Русский",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/kino-plus.m3u",
            recommendedEpgUrl = "https://cineman.su/epg/epg_cineman.xml.gz"
        ),
        FreePlaylistPreset(
            title = "⚽ Спорт РФ и Мир (slava-67 • sport.m3u)",
            subtitle = "Спортивные телеканалы (футбол, хоккей, единоборства)",
            region = "Спорт / РФ / Русский",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/sport.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🧸 Детские каналы (slava-67 • kids.m3u)",
            subtitle = "Мультфильмы и детские телеканалы",
            region = "Детские / Мультфильмы / РФ",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/kids.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🔬 Познавательные и наука (slava-67 • poznavatelni.m3u)",
            subtitle = "Документальные, природа, история, технологии",
            region = "Познавательные / Документальные / РФ",
            url = "https://raw.githubusercontent.com/slava-67/TV/IPTV/poznavatelni.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),

        // === Международные и открытые каталоги (Free-TV & iptv-org) ===
        FreePlaylistPreset(
            title = "🌍 Free-TV Весь мир (проверенные каналы с EPG)",
            subtitle = "Чистая международная база открытого ТВ (Европа, США, РФ, Азия)",
            region = "Мир / Зарубежные / Free-TV / Все",
            url = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8",
            recommendedEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_ALL_SOURCES1.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇷🇺 Россия — открытые ТВ каналы (iptv-org)",
            subtitle = "Бесплатные открытые российские каналы (iptv-org)",
            region = "Россия / РФ / Русский",
            url = "https://iptv-org.github.io/iptv/countries/ru.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇷🇺 Все русскоязычные каналы (iptv-org • РФ + СНГ + Мир)",
            subtitle = "Сборник открытых каналов на русском языке",
            region = "Россия / Русский / СНГ",
            url = "https://iptv-org.github.io/iptv/languages/rus.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🌍 Весь мир — Зарубежные + РФ (iptv-org общий)",
            subtitle = "Тысячи открытых бесплатных каналов со всего мира",
            region = "Мир / Зарубежные / Все",
            url = "https://iptv-org.github.io/iptv/index.m3u"
        ),
        FreePlaylistPreset(
            title = "🇬🇧 Англоязычные каналы (США / Великобритания / Мир)",
            subtitle = "Открытые каналы на английском языке",
            region = "Зарубежные / English / США / UK",
            url = "https://iptv-org.github.io/iptv/languages/eng.m3u",
            recommendedEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🎬 Кино и фильмы (Международные • iptv-org)",
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
            title = "⚽ Спорт (Международные • iptv-org)",
            subtitle = "Спортивные трансляции и каналы мира",
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
            title = "🧸 Детские (Международные • iptv-org)",
            subtitle = "Открытые детские каналы мира",
            region = "Жанры / Детские / Kids",
            url = "https://iptv-org.github.io/iptv/categories/kids.m3u"
        ),
        FreePlaylistPreset(
            title = "🔬 Документальные и наука (Международные)",
            subtitle = "Познавательные и научные каналы",
            region = "Жанры / Документальные / Documentary",
            url = "https://iptv-org.github.io/iptv/categories/documentary.m3u"
        ),
        FreePlaylistPreset(
            title = "🇧🇾 Беларусь — открытые каналы",
            subtitle = "Бесплатные телеканалы Беларуси (Free-TV)",
            region = "СНГ / Беларусь",
            url = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_belarus.m3u8",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇰🇿 Казахстан — открытые каналы",
            subtitle = "Бесплатные телеканалы Казахстана",
            region = "СНГ / Казахстан",
            url = "https://iptv-org.github.io/iptv/countries/kz.m3u",
            recommendedEpgUrl = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇩🇪 Германия (Deutschland)",
            subtitle = "Открытые немецкие телеканалы",
            region = "Зарубежные / Германия / Europe",
            url = "https://iptv-org.github.io/iptv/countries/de.m3u",
            recommendedEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇺🇸 США (United States)",
            subtitle = "Открытые американские каналы",
            region = "Зарубежные / США / USA",
            url = "https://iptv-org.github.io/iptv/countries/us.m3u",
            recommendedEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇬🇧 Великобритания (UK)",
            subtitle = "Открытые британские телеканалы",
            region = "Зарубежные / Великобритания / UK",
            url = "https://iptv-org.github.io/iptv/countries/gb.m3u",
            recommendedEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz"
        ),
        FreePlaylistPreset(
            title = "🇫🇷 Франция (France)",
            subtitle = "Открытые французские каналы",
            region = "Зарубежные / Франция / Europe",
            url = "https://iptv-org.github.io/iptv/countries/fr.m3u",
            recommendedEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_FR1.xml.gz"
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

    /**
     * Verified live EPG XMLTV sources (tested HTTP 200 OK).
     * Includes all official Gabbarit EPG variants right at the top!
     */
    val epgPresets: List<FreeEpgPreset> = listOf(
        // === GABBARIT (Габарит) ===
        FreeEpgPreset(
            title = "⭐ Gabbarit (Габарит) — Light (14.8 МБ, быстро)",
            subtitle = "Облегчённый EPG от Gabbarit — идеально для ТВ-приставок и телефонов",
            url = "http://gabbarit.drm-play.com/epg_lite.xml.gz"
        ),
        FreeEpgPreset(
            title = "⭐ Gabbarit (Габарит) — Зеркало epg.one (26.6 МБ)",
            subtitle = "Быстрое зеркало Gabbarit на текущую неделю с описаниями",
            url = "http://gabbarit.epg.one/epg_1.xml.gz"
        ),
        FreeEpgPreset(
            title = "⭐ Gabbarit (Габарит) — Полная неделя + кинозалы (70 МБ)",
            subtitle = "Максимально подробные описания, актёры и кинозалы",
            url = "http://gabbarit.drm-play.com/epg_1.xml.gz"
        ),
        FreeEpgPreset(
            title = "⭐ Gabbarit (Габарит) — Архив 14 дней (154 МБ)",
            subtitle = "Полный EPG с глубоким архивом на 2 недели (для мощных устройств)",
            url = "http://gabbarit.drm-play.com/epg_14.xml.gz"
        ),

        // === IPTVX.ONE ===
        FreeEpgPreset(
            title = "IPTVX.one — Лёгкий EPG LITE (17.5 МБ, РФ и СНГ)",
            subtitle = "Быстрый и популярный EPG для русскоязычных каналов",
            url = "https://iptvx.one/epg/epg_lite.xml.gz"
        ),
        FreeEpgPreset(
            title = "IPTVX.one — Без архива (39 МБ, только эфир и будущее)",
            subtitle = "С описаниями передач, без прошлых дней",
            url = "https://iptvx.one/epg/epg_noarch.xml.gz"
        ),
        FreeEpgPreset(
            title = "IPTVX.one — Полный с архивом 7 дней (90 МБ)",
            subtitle = "Полная база РФ + СНГ с описаниями и архивом",
            url = "https://iptvx.one/epg/epg.xml.gz"
        ),

        // === КИНО И ЛЁГКИЕ РОССИЙСКИЕ EPG ===
        FreeEpgPreset(
            title = "Filmax TV — РФ и киноканалы (18.4 МБ)",
            subtitle = "Телепрограмма для российских и фильмовых каналов",
            url = "https://filmax-tv.ru/epg.xml.gz"
        ),
        FreeEpgPreset(
            title = "CineMan — Кинозалы (1.2 МБ, мгновенно)",
            subtitle = "Специализированная программа для фильмовых кинозалов",
            url = "https://cineman.su/epg/epg_cineman.xml.gz"
        ),
        FreeEpgPreset(
            title = "ProgramTV.ru — Сверхлёгкий РФ (0.9 МБ)",
            subtitle = "Очень компактный EPG для слабых ТВ-приставок",
            url = "http://programtv.ru/xmltv.xml.gz"
        ),
        FreeEpgPreset(
            title = "Open-EPG — Россия (лёгкий)",
            subtitle = "Открытый XMLTV для федеральных каналов РФ",
            url = "https://www.open-epg.com/files/russia1.xml.gz"
        ),

        // === ЗАРУБЕЖНЫЕ EPG (EPGshare01) ===
        FreeEpgPreset(
            title = "EPGshare01 — США (US)",
            subtitle = "Телепрограмма для американских каналов",
            url = "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz"
        ),
        FreeEpgPreset(
            title = "EPGshare01 — Великобритания (UK)",
            subtitle = "Телепрограмма для британских каналов",
            url = "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz"
        ),
        FreeEpgPreset(
            title = "EPGshare01 — Германия (DE)",
            subtitle = "Телепрограмма для немецких каналов",
            url = "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz"
        ),
        FreeEpgPreset(
            title = "EPGshare01 — Весь мир (ALL SOURCES, 200 МБ)",
            subtitle = "Огромная международная база EPG для всех стран",
            url = "https://epgshare01.online/epgshare01/epg_ripper_ALL_SOURCES1.xml.gz"
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
