package com.etelevizor.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.etelevizor.iptv.BuildConfig
import com.etelevizor.iptv.ETelevizorApp
import com.etelevizor.iptv.databinding.ActivitySettingsBinding
import com.etelevizor.iptv.model.*
import com.etelevizor.iptv.util.DeviceModeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val app get() = ETelevizorApp.instance
    private val prefs get() = app.prefs

    private val categories = listOf(
        "📺 Устройство и интерфейс",
        "📋 Плейлисты",
        "📅 Телепрограмма (EPG)",
        "▶ Плеер и декодер",
        "🎮 Пульт ДУ и жесты",
        "🔒 Родительский контроль",
        "💤 Сон и автозапуск",
        "💾 Копия, кэш и о программе"
    )

    private var activeCategory = 0
    private lateinit var rowAdapter: SettingRowAdapter
    private var catAdapter: SettingCategoryAdapter? = null

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = prefs.exportBackupJson()
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "Резервная копия успешно сохранена!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка экспорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                if (prefs.importBackupJson(json)) {
                    Toast.makeText(this, "Резервная копия восстановлена! Обновите плейлисты.", Toast.LENGTH_LONG).show()
                    renderCategory(activeCategory)
                } else {
                    Toast.makeText(this, "Файл не является резервной копией Е-Телевизёр", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка импорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceModeHelper.applyOrientation(this, prefs, isPlayer = false)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSettingsBack.setOnClickListener { finish() }

        rowAdapter = SettingRowAdapter()
        binding.rvSettingsItems.layoutManager = LinearLayoutManager(this)
        binding.rvSettingsItems.adapter = rowAdapter

        setupCategoryPane()
        applyTheme()
        renderCategory(0)
    }

    override fun onResume() {
        super.onResume()
        updateModeBadge()
        renderCategory(activeCategory)
    }

    private fun setupCategoryPane() {
        val isTv = DeviceModeHelper.isTvMode(this, prefs)
        if (isTv) {
            binding.rvSettingsCategoriesTv.visibility = View.VISIBLE
            binding.settingsDivider.visibility = View.VISIBLE
            binding.rvSettingsCategoriesPhone.visibility = View.GONE

            val adapter = SettingCategoryAdapter(isChipMode = false, categories = categories) { idx ->
                activeCategory = idx
                renderCategory(idx)
            }
            catAdapter = adapter
            binding.rvSettingsCategoriesTv.layoutManager = LinearLayoutManager(this)
            binding.rvSettingsCategoriesTv.adapter = adapter
            binding.rvSettingsCategoriesTv.post {
                binding.rvSettingsCategoriesTv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        } else {
            binding.rvSettingsCategoriesTv.visibility = View.GONE
            binding.settingsDivider.visibility = View.GONE
            binding.rvSettingsCategoriesPhone.visibility = View.VISIBLE

            val adapter = SettingCategoryAdapter(isChipMode = true, categories = categories) { idx ->
                activeCategory = idx
                renderCategory(idx)
            }
            catAdapter = adapter
            binding.rvSettingsCategoriesPhone.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.rvSettingsCategoriesPhone.adapter = adapter
        }
    }

    private fun applyTheme() {
        val t = prefs.appTheme
        binding.settingsRoot.setBackgroundColor(t.bgColor)
        binding.rvSettingsCategoriesTv.setBackgroundColor(t.surfaceColor)
        updateModeBadge()
    }

    private fun updateModeBadge() {
        binding.tvSettingsModeBadge.text = DeviceModeHelper.activeModeShortLabel(this, prefs)
    }

    private fun renderCategory(index: Int) {
        val rows: List<SettingRow> = when (index) {
            0 -> buildInterfaceSettings()
            1 -> buildPlaylistSettings()
            2 -> buildEpgSettings()
            3 -> buildPlayerSettings()
            4 -> buildRemoteAndGesturesSettings()
            5 -> buildParentalSettings()
            6 -> buildSleepAndBootSettings()
            7 -> buildBackupAndAboutSettings()
            else -> emptyList()
        }
        rowAdapter.submitList(rows)
    }

    // ==================== 0. DEVICE MODE & INTERFACE ====================
    private fun buildInterfaceSettings(): List<SettingRow> {
        val detected = DeviceModeHelper.detectedHardwareLabel(this)
        return listOf(
            SettingRow(
                id = "device_mode",
                title = "Режим устройства (ТВ / Телефон)",
                subtitle = "Определено автоматически: $detected",
                valueText = prefs.deviceMode.label
            ) {
                Dialogs.showDeviceModeDialog(this, prefs) {
                    updateModeBadge()
                    recreate()
                }
            },
            SettingRow(
                id = "wizard",
                title = "Запустить мастер первого запуска",
                subtitle = "Выбор ТВ/Телефон и быстрая настройка плейлиста"
            ) {
                startActivity(Intent(this, SetupWizardActivity::class.java))
            },
            SettingRow(
                id = "theme",
                title = "Тема оформления",
                subtitle = "Цветовая схема меню и списков",
                valueText = prefs.appTheme.label
            ) {
                val items = AppTheme.entries
                showSingleChoice("Тема оформления", items.map { it.label }, items.indexOf(prefs.appTheme)) {
                    prefs.appTheme = items[it]
                    applyTheme()
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "accent",
                title = "Акцентный цвет",
                valueText = prefs.accentColor.label
            ) {
                val items = AccentColor.entries
                showSingleChoice("Акцентный цвет", items.map { it.label }, items.indexOf(prefs.accentColor)) {
                    prefs.accentColor = items[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "view_style",
                title = "Вид списка каналов",
                valueText = prefs.viewStyle.label
            ) {
                val items = ViewStyle.entries
                showSingleChoice("Вид списка каналов", items.map { it.label }, items.indexOf(prefs.viewStyle)) {
                    prefs.viewStyle = items[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "sort_order",
                title = "Сортировка каналов",
                valueText = prefs.sortOrder.label
            ) {
                val items = SortOrder.entries
                showSingleChoice("Сортировка каналов", items.map { it.label }, items.indexOf(prefs.sortOrder)) {
                    prefs.sortOrder = items[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "font_scale",
                title = "Размер шрифта",
                valueText = when {
                    prefs.fontScale <= 0.9f -> "Мелкий (85%)"
                    prefs.fontScale <= 1.05f -> "Обычный (100%)"
                    prefs.fontScale <= 1.2f -> "Крупный (115%)"
                    else -> "Очень крупный для ТВ (130%)"
                }
            ) {
                val scales = floatArrayOf(0.85f, 1.0f, 1.15f, 1.30f)
                val labels = listOf("Мелкий (85%)", "Обычный (100%)", "Крупный (115%)", "Очень крупный для ТВ (130%)")
                val idx = scales.indices.minByOrNull { kotlin.math.abs(scales[it] - prefs.fontScale) } ?: 1
                showSingleChoice("Размер шрифта", labels, idx) {
                    prefs.fontScale = scales[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "show_numbers",
                title = "Показывать номера каналов",
                isSwitch = true,
                isChecked = prefs.showChannelNumbers
            ) {
                prefs.showChannelNumbers = !prefs.showChannelNumbers
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "show_logos",
                title = "Показывать логотипы каналов",
                isSwitch = true,
                isChecked = prefs.showChannelLogos
            ) {
                prefs.showChannelLogos = !prefs.showChannelLogos
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "show_clock",
                title = "Показывать часы в плеере (24ч)",
                isSwitch = true,
                isChecked = prefs.showPlayerClock
            ) {
                prefs.showPlayerClock = !prefs.showPlayerClock
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "tv_preview",
                title = "Панель EPG-предпросмотра справа (на ТВ)",
                subtitle = "Показывает описание передачи выбранного канала",
                isSwitch = true,
                isChecked = prefs.showTvPreviewPanel
            ) {
                prefs.showTvPreviewPanel = !prefs.showTvPreviewPanel
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "osd_timeout",
                title = "Автоскрытие панели в плеере",
                valueText = "${prefs.osdTimeoutSeconds} сек"
            ) {
                val vals = intArrayOf(3, 5, 8, 12, 20)
                val labels = vals.map { "$it сек" }
                showSingleChoice("Автоскрытие OSD", labels, vals.indexOf(prefs.osdTimeoutSeconds).coerceAtLeast(0)) {
                    prefs.osdTimeoutSeconds = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "phone_orientation",
                title = "Ориентация экрана (Телефон)",
                valueText = when (prefs.phoneOrientation) {
                    "landscape_always" -> "Всегда альбомная"
                    "landscape_player" -> "Альбомная в плеере"
                    "portrait_menu" -> "Портретная в меню"
                    else -> "Автоповорот"
                }
            ) {
                val keys = listOf("auto", "landscape_player", "landscape_always", "portrait_menu")
                val labels = listOf("Автоповорот", "Альбомная в плеере", "Всегда альбомная", "Портретная в меню")
                showSingleChoice("Ориентация на телефоне", labels, keys.indexOf(prefs.phoneOrientation).coerceAtLeast(0)) {
                    prefs.phoneOrientation = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "startup_action",
                title = "Действие при запуске",
                valueText = when (prefs.startupAction) {
                    "last_channel" -> "Включать последний канал"
                    "favorites" -> "Открывать Избранное"
                    else -> "Главный экран каналов"
                }
            ) {
                val keys = listOf("menu", "last_channel", "favorites")
                val labels = listOf("Главный экран каналов", "Включать последний канал", "Открывать Избранное")
                showSingleChoice("При запуске Е-Телевизёр", labels, keys.indexOf(prefs.startupAction).coerceAtLeast(0)) {
                    prefs.startupAction = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "show_hidden",
                title = "Показывать скрытые каналы и группы",
                isSwitch = true,
                isChecked = prefs.showHiddenItems
            ) {
                prefs.showHiddenItems = !prefs.showHiddenItems
                renderCategory(activeCategory)
            }
        )
    }

    // ==================== 1. PLAYLISTS ====================
    private fun buildPlaylistSettings(): List<SettingRow> {
        val count = prefs.getPlaylists().size
        return listOf(
            SettingRow(
                id = "manage_playlists",
                title = "Управление плейлистами (M3U / Xtream)",
                subtitle = "Добавление, редактирование, User-Agent, архив, автообновление",
                valueText = "Всего: $count"
            ) {
                startActivity(Intent(this, PlaylistsActivity::class.java))
            },
            SettingRow(
                id = "refresh_all_pl",
                title = "Обновить все плейлисты сейчас",
                subtitle = "Загрузить свежие списки каналов, фильмов и сериалов"
            ) {
                Toast.makeText(this, "Обновление плейлистов...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val (ok, fail) = app.repository.refreshAllEnabledPlaylists()
                    Toast.makeText(this@SettingsActivity, "Обновлено: $ok, ошибок: $fail", Toast.LENGTH_SHORT).show()
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "add_demo_pl",
                title = "Загрузить Демо-плейлист",
                subtitle = "Открытые тестовые потоки HD + демо EPG для проверки"
            ) {
                lifecycleScope.launch {
                    app.repository.addDemoPlaylist()
                    Toast.makeText(this@SettingsActivity, "Демо-плейлист загружен!", Toast.LENGTH_SHORT).show()
                    renderCategory(activeCategory)
                }
            }
        )
    }

    // ==================== 2. EPG ====================
    private fun buildEpgSettings(): List<SettingRow> {
        val count = app.epgDb.getProgramCount()
        val last = if (prefs.epgLastUpdated > 0L) DeviceModeHelper.formatDateTime(prefs.epgLastUpdated) else "Не обновлялась"
        return listOf(
            SettingRow(
                id = "epg_update_now",
                title = "Обновить телепрограмму (EPG) сейчас",
                subtitle = "Передач в базе: $count • Обновлено: $last"
            ) {
                Toast.makeText(this, "Загрузка XMLTV телепрограммы...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val res = app.repository.refreshAllEpg()
                    res.onSuccess { n ->
                        Toast.makeText(this@SettingsActivity, "Загружено передач: $n", Toast.LENGTH_LONG).show()
                        renderCategory(activeCategory)
                    }.onFailure { err ->
                        Toast.makeText(this@SettingsActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            SettingRow(
                id = "epg_sources",
                title = "Источники EPG (XMLTV / .xml.gz)",
                subtitle = "Добавить или удалить внешние ссылки телепрограммы",
                valueText = "${prefs.getEpgSources().size} шт."
            ) {
                startActivity(Intent(this, PlaylistsActivity::class.java))
            },
            SettingRow(
                id = "epg_shift",
                title = "Смещение времени EPG",
                subtitle = "Коррекция часового пояса программы передач",
                valueText = "${if (prefs.epgTimeShiftHours >= 0) "+" else ""}${prefs.epgTimeShiftHours} ч"
            ) {
                val shifts = (-12..12).map { it.toFloat() }
                val labels = shifts.map { "${if (it >= 0) "+" else ""}${it.toInt()} ч" }
                val idx = shifts.indexOfFirst { it == prefs.epgTimeShiftHours }.coerceAtLeast(12)
                showSingleChoice("Смещение времени EPG", labels, idx) {
                    prefs.epgTimeShiftHours = shifts[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "epg_interval",
                title = "Автообновление EPG",
                valueText = if (prefs.epgAutoUpdateHours == 0) "Вручную" else "Каждые ${prefs.epgAutoUpdateHours} ч"
            ) {
                val vals = intArrayOf(0, 6, 12, 24, 48)
                val labels = listOf("Вручную", "Каждые 6 часов", "Каждые 12 часов", "Раз в сутки (24 ч)", "Раз в 2 дня")
                showSingleChoice("Интервал обновления EPG", labels, vals.indexOf(prefs.epgAutoUpdateHours).coerceAtLeast(0)) {
                    prefs.epgAutoUpdateHours = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "epg_retention",
                title = "Хранить историю EPG (для архива)",
                valueText = "${prefs.epgRetentionDays} дн."
            ) {
                val vals = intArrayOf(1, 3, 5, 7, 14)
                val labels = vals.map { "$it дн." }
                showSingleChoice("Глубина хранения EPG", labels, vals.indexOf(prefs.epgRetentionDays).coerceAtLeast(0)) {
                    prefs.epgRetentionDays = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "epg_progress",
                title = "Полоса прогресса текущей передачи",
                isSwitch = true,
                isChecked = prefs.showEpgProgress
            ) {
                prefs.showEpgProgress = !prefs.showEpgProgress
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "epg_next",
                title = "Показывать следующую передачу в плеере",
                isSwitch = true,
                isChecked = prefs.showNextProgram
            ) {
                prefs.showNextProgram = !prefs.showNextProgram
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "epg_clear",
                title = "Очистить кэш телепрограммы (EPG)",
                subtitle = "Удалить все сохранённые передачи из базы"
            ) {
                app.repository.clearEpgCache()
                Toast.makeText(this, "Кэш EPG очищен", Toast.LENGTH_SHORT).show()
                renderCategory(activeCategory)
            }
        )
    }

    // ==================== 3. PLAYER & DECODER ====================
    private fun buildPlayerSettings(): List<SettingRow> {
        return listOf(
            SettingRow(
                id = "engine",
                title = "Движок плеера по умолчанию",
                subtitle = "ExoPlayer — быстрее переключает каналы; LibVLC — открывает любые кодеки (AC3/DTS/UDP)",
                valueText = prefs.playerEngine.label
            ) {
                val items = PlayerEngineType.entries
                showSingleChoice("Движок плеера", items.map { it.label }, items.indexOf(prefs.playerEngine)) {
                    prefs.playerEngine = items[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "fallback_vlc",
                title = "Автопереключение на LibVLC при ошибке",
                subtitle = "Если ExoPlayer не смог декодировать поток, автоматически запустить LibVLC",
                isSwitch = true,
                isChecked = prefs.autoFallbackToLibVlc
            ) {
                prefs.autoFallbackToLibVlc = !prefs.autoFallbackToLibVlc
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "hw_accel",
                title = "Аппаратное ускорение (HW)",
                valueText = when (prefs.hwAcceleration) {
                    "full" -> "Полное"
                    "decoding" -> "Только декодирование"
                    "software" -> "Программное (CPU)"
                    else -> "Авто"
                }
            ) {
                val keys = listOf("auto", "full", "decoding", "software")
                val labels = listOf("Авто", "Полное аппаратное", "Только декодирование", "Программное (CPU)")
                showSingleChoice("Аппаратное ускорение", labels, keys.indexOf(prefs.hwAcceleration).coerceAtLeast(0)) {
                    prefs.hwAcceleration = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "buffer",
                title = "Размер сетевого буфера",
                subtitle = "Больший буфер убирает подвисания на нестабильном интернете",
                valueText = "${prefs.networkBufferMs} мс"
            ) {
                val vals = intArrayOf(500, 1500, 3000, 5000, 8000, 12000)
                val labels = listOf("Минимальный (500 мс)", "Малый (1.5 сек)", "Средний (3 сек)", "Большой (5 сек)", "Очень большой (8 сек)", "Максимальный (12 сек)")
                showSingleChoice("Сетевой буфер", labels, vals.indexOf(prefs.networkBufferMs).coerceAtLeast(2)) {
                    prefs.networkBufferMs = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "auto_reconnect",
                title = "Автопереподключение при обрыве потока",
                isSwitch = true,
                isChecked = prefs.autoReconnect
            ) {
                prefs.autoReconnect = !prefs.autoReconnect
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "reconnect_attempts",
                title = "Количество попыток переподключения",
                valueText = "${prefs.reconnectAttempts}"
            ) {
                val vals = intArrayOf(2, 3, 5, 10, 20)
                showSingleChoice("Попытки переподключения", vals.map { "$it попыток" }, vals.indexOf(prefs.reconnectAttempts).coerceAtLeast(0)) {
                    prefs.reconnectAttempts = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "aspect",
                title = "Соотношение сторон по умолчанию",
                valueText = prefs.defaultAspectRatio.label
            ) {
                val items = AspectRatioMode.entries
                showSingleChoice("Соотношение сторон", items.map { it.label }, items.indexOf(prefs.defaultAspectRatio)) {
                    prefs.defaultAspectRatio = items[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "deinterlace",
                title = "Деинтерлейсинг (LibVLC)",
                subtitle = "Убирает «гребёнку» на чересстрочных ТВ-каналах (1080i / 576i)",
                isSwitch = true,
                isChecked = prefs.deinterlace
            ) {
                prefs.deinterlace = !prefs.deinterlace
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "audio_lang",
                title = "Предпочитаемый язык аудио",
                valueText = when (prefs.preferredAudioLang) {
                    "ru" -> "Русский"
                    "en" -> "Английский"
                    else -> "По умолчанию (первая)"
                }
            ) {
                val keys = listOf("ru", "en", "auto")
                val labels = listOf("Русский", "Английский", "По умолчанию (первая)")
                showSingleChoice("Язык аудио", labels, keys.indexOf(prefs.preferredAudioLang).coerceAtLeast(0)) {
                    prefs.preferredAudioLang = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "sub_lang",
                title = "Субтитры по умолчанию",
                valueText = when (prefs.preferredSubtitleLang) {
                    "ru" -> "Русский"
                    "en" -> "Английский"
                    else -> "Выключены"
                }
            ) {
                val keys = listOf("off", "ru", "en")
                val labels = listOf("Выключены", "Русский", "Английский")
                showSingleChoice("Субтитры по умолчанию", labels, keys.indexOf(prefs.preferredSubtitleLang).coerceAtLeast(0)) {
                    prefs.preferredSubtitleLang = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "passthrough",
                title = "Сквозной звук (Audio Passthrough)",
                subtitle = "Для ресиверов и саундбаров по HDMI",
                isSwitch = true,
                isChecked = prefs.audioPassthrough
            ) {
                prefs.audioPassthrough = !prefs.audioPassthrough
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "vol_boost",
                title = "Программное усиление звука (LibVLC)",
                valueText = "${prefs.softwareVolumeBoost}%"
            ) {
                val vals = intArrayOf(100, 125, 150, 175, 200)
                showSingleChoice("Усиление звука", vals.map { "$it%" }, vals.indexOf(prefs.softwareVolumeBoost).coerceAtLeast(0)) {
                    prefs.softwareVolumeBoost = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "pip",
                title = "Режим «Картинка в картинке» (PiP)",
                subtitle = "Сворачивать видео в маленькое окно на телефоне",
                isSwitch = true,
                isChecked = prefs.enablePip
            ) {
                prefs.enablePip = !prefs.enablePip
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "timeout",
                title = "Таймаут сетевого подключения",
                valueText = "${prefs.connectionTimeoutSec} сек"
            ) {
                val vals = intArrayOf(5, 10, 15, 25, 40)
                showSingleChoice("Таймаут подключения", vals.map { "$it сек" }, vals.indexOf(prefs.connectionTimeoutSec).coerceAtLeast(0)) {
                    prefs.connectionTimeoutSec = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "user_agent",
                title = "Глобальный User-Agent",
                subtitle = prefs.defaultUserAgent
            ) {
                showUserAgentDialog()
            }
        )
    }

    // ==================== 4. REMOTE CONTROL & GESTURES ====================
    private fun buildRemoteAndGesturesSettings(): List<SettingRow> {
        return listOf(
            SettingRow(
                id = "rem_up_down",
                title = "Пульт: кнопки Вверх / Вниз в плеере",
                valueText = when (prefs.remoteUpDownAction) {
                    "list" -> "Список каналов"
                    "volume" -> "Громкость"
                    else -> "Переключать каналы"
                }
            ) {
                val keys = listOf("channel", "list", "volume")
                val labels = listOf("Переключать каналы", "Открывать список каналов", "Регулировать громкость")
                showSingleChoice("Кнопки Вверх / Вниз", labels, keys.indexOf(prefs.remoteUpDownAction).coerceAtLeast(0)) {
                    prefs.remoteUpDownAction = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "invert_ch",
                title = "Инвертировать переключение каналов",
                subtitle = "Вверх = предыдущий канал, Вниз = следующий",
                isSwitch = true,
                isChecked = prefs.invertChannelButtons
            ) {
                prefs.invertChannelButtons = !prefs.invertChannelButtons
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "rem_ok",
                title = "Пульт: кнопка OK / Центр в плеере",
                subtitle = "Долгое удержание OK всегда открывает быстрое меню",
                valueText = when (prefs.remoteOkAction) {
                    "list" -> "Список каналов"
                    "pause" -> "Пауза / Плей"
                    else -> "Инфо-панель (OSD)"
                }
            ) {
                val keys = listOf("osd", "list", "pause")
                val labels = listOf("Показать инфо-панель (OSD)", "Открыть список каналов", "Пауза / Воспроизведение")
                showSingleChoice("Кнопка OK / Центр", labels, keys.indexOf(prefs.remoteOkAction).coerceAtLeast(0)) {
                    prefs.remoteOkAction = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "rem_left",
                title = "Пульт: кнопка Влево в плеере",
                valueText = when (prefs.remoteLeftAction) {
                    "seek_back" -> "Перемотка назад"
                    "prev_channel" -> "Предыдущий канал"
                    else -> "Список каналов"
                }
            ) {
                val keys = listOf("list", "seek_back", "prev_channel")
                val labels = listOf("Открыть список каналов", "Перемотка назад (VOD/Архив)", "Возврат на предыдущий канал")
                showSingleChoice("Кнопка Влево", labels, keys.indexOf(prefs.remoteLeftAction).coerceAtLeast(0)) {
                    prefs.remoteLeftAction = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "rem_right",
                title = "Пульт: кнопка Вправо в плеере",
                valueText = when (prefs.remoteRightAction) {
                    "seek_forward" -> "Перемотка вперёд"
                    "info" -> "Инфо-панель"
                    else -> "EPG и Архив"
                }
            ) {
                val keys = listOf("epg", "seek_forward", "info")
                val labels = listOf("Открыть EPG и Архив канала", "Перемотка вперёд (VOD/Архив)", "Показать инфо-панель")
                showSingleChoice("Кнопка Вправо", labels, keys.indexOf(prefs.remoteRightAction).coerceAtLeast(0)) {
                    prefs.remoteRightAction = keys[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "num_input",
                title = "Быстрый набор номера канала цифрами 0–9",
                subtitle = "Нажмите 0 для мгновенного возврата на предыдущий канал",
                isSwitch = true,
                isChecked = prefs.enableNumericChannelInput
            ) {
                prefs.enableNumericChannelInput = !prefs.enableNumericChannelInput
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "num_timeout",
                title = "Задержка подтверждения цифр (0–9)",
                valueText = "${prefs.numericInputTimeoutMs / 1000.0} сек"
            ) {
                val vals = intArrayOf(1000, 1600, 2200, 3000)
                val labels = listOf("1.0 сек", "1.6 сек", "2.2 сек", "3.0 сек")
                showSingleChoice("Задержка цифрового ввода", labels, vals.indexOf(prefs.numericInputTimeoutMs).coerceAtLeast(1)) {
                    prefs.numericInputTimeoutMs = vals[it]
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "double_back",
                title = "Подтверждать выход двойным нажатием «Назад»",
                isSwitch = true,
                isChecked = prefs.doubleBackToExit
            ) {
                prefs.doubleBackToExit = !prefs.doubleBackToExit
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "phone_gestures",
                title = "Телефон: жесты свайпов в плеере",
                subtitle = "Яркость слева, громкость справа, перемотка горизонтально",
                isSwitch = true,
                isChecked = prefs.phoneGesturesEnabled
            ) {
                prefs.phoneGesturesEnabled = !prefs.phoneGesturesEnabled
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "double_tap_ch",
                title = "Телефон: двойной тап переключает каналы",
                subtitle = "Вместо перемотки ±10 сек в прямом эфире",
                isSwitch = true,
                isChecked = prefs.doubleTapChannelSwitch
            ) {
                prefs.doubleTapChannelSwitch = !prefs.doubleTapChannelSwitch
                renderCategory(activeCategory)
            }
        )
    }

    // ==================== 5. PARENTAL CONTROL ====================
    private fun buildParentalSettings(): List<SettingRow> {
        return listOf(
            SettingRow(
                id = "parental_on",
                title = "Включить родительский контроль",
                subtitle = "Защита выбранных каналов и категорий 18+ PIN-кодом (по умолчанию 0000)",
                isSwitch = true,
                isChecked = prefs.parentalControlEnabled
            ) {
                if (prefs.parentalControlEnabled) {
                    Dialogs.showPinVerifyDialog(this, prefs.parentalPin, "Отключить родительский контроль") {
                        prefs.parentalControlEnabled = false
                        renderCategory(activeCategory)
                    }
                } else {
                    prefs.parentalControlEnabled = true
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "parental_pin",
                title = "Изменить PIN-код",
                subtitle = "Текущий PIN состоит из ${prefs.parentalPin.length} цифр"
            ) {
                Dialogs.showChangePinDialog(this, prefs) { renderCategory(activeCategory) }
            },
            SettingRow(
                id = "auto_adult",
                title = "Автоблокировка категорий 18+ / Adult / XXX",
                isSwitch = true,
                isChecked = prefs.autoLockAdultGroups
            ) {
                prefs.autoLockAdultGroups = !prefs.autoLockAdultGroups
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "hide_locked",
                title = "Полностью скрывать заблокированные каналы",
                subtitle = "Не показывать их в списке вместо значка замка 🔒",
                isSwitch = true,
                isChecked = prefs.hideLockedChannels
            ) {
                prefs.hideLockedChannels = !prefs.hideLockedChannels
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "lock_settings",
                title = "Требовать PIN для входа в Настройки и Плейлисты",
                isSwitch = true,
                isChecked = prefs.lockSettingsWithPin
            ) {
                prefs.lockSettingsWithPin = !prefs.lockSettingsWithPin
                renderCategory(activeCategory)
            }
        )
    }

    // ==================== 6. SLEEP TIMER & AUTOSTART ====================
    private fun buildSleepAndBootSettings(): List<SettingRow> {
        return listOf(
            SettingRow(
                id = "sleep_default",
                title = "Таймер сна по умолчанию в плеере",
                valueText = if (prefs.defaultSleepTimerMinutes == 0) "Выкл" else "${prefs.defaultSleepTimerMinutes} мин"
            ) {
                Dialogs.showSleepTimerDialog(this, prefs.defaultSleepTimerMinutes) { min ->
                    prefs.defaultSleepTimerMinutes = min
                    renderCategory(activeCategory)
                }
            },
            SettingRow(
                id = "launch_boot",
                title = "Автозапуск при включении ТВ / приставки",
                subtitle = "Автоматически открывать «Е-Телевизёр» после загрузки Android",
                isSwitch = true,
                isChecked = prefs.launchOnBoot
            ) {
                prefs.launchOnBoot = !prefs.launchOnBoot
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "keep_screen",
                title = "Не выключать экран во время просмотра",
                isSwitch = true,
                isChecked = prefs.keepScreenOn
            ) {
                prefs.keepScreenOn = !prefs.keepScreenOn
                renderCategory(activeCategory)
            }
        )
    }

    // ==================== 7. BACKUP, CACHE & ABOUT ====================
    private fun buildBackupAndAboutSettings(): List<SettingRow> {
        val cacheSize = DeviceModeHelper.formatBytes(app.repository.calculateCacheSizeBytes())
        return listOf(
            SettingRow(
                id = "export_backup",
                title = "Экспорт резервной копии (JSON)",
                subtitle = "Сохранить все настройки, плейлисты, EPG и Избранное в файл"
            ) {
                exportBackupLauncher.launch("E-Televizor-backup.json")
            },
            SettingRow(
                id = "import_backup",
                title = "Восстановить из резервной копии (JSON)",
                subtitle = "Загрузить ранее сохранённый файл настроек и плейлистов"
            ) {
                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
            },
            SettingRow(
                id = "clear_cache",
                title = "Очистить кэш логотипов и временных файлов",
                subtitle = "Занято кэшем и базой EPG: $cacheSize"
            ) {
                app.repository.clearImageAndTempCache()
                Toast.makeText(this, "Кэш очищен", Toast.LENGTH_SHORT).show()
                renderCategory(activeCategory)
            },
            SettingRow(
                id = "clear_history",
                title = "Очистить историю просмотров",
                subtitle = "Удалить список недавних каналов и позиции VOD"
            ) {
                prefs.clearWatchHistory()
                Toast.makeText(this, "История просмотров очищена", Toast.LENGTH_SHORT).show()
            },
            SettingRow(
                id = "reset_settings",
                title = "Сбросить настройки по умолчанию",
                subtitle = "Плейлисты и Избранное будут сохранены"
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Сбросить настройки?")
                    .setMessage("Все параметры интерфейса, плеера и пульта вернутся к стандартным. Ваши плейлисты сохранятся.")
                    .setPositiveButton("Сбросить") { _, _ ->
                        prefs.resetAllSettings()
                        Toast.makeText(this, "Настройки сброшены", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            },
            SettingRow(
                id = "about",
                title = "О программе «Е-Телевизёр»",
                subtitle = "Версия ${BuildConfig.VERSION_NAME} • Двойной движок: Media3 ExoPlayer + LibVLC 3.6.2\nУстройство: ${DeviceModeHelper.detectedHardwareLabel(this)}"
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Е-Телевизёр v${BuildConfig.VERSION_NAME}")
                    .setMessage(
                        "Универсальный IPTV плеер для Android TV, ТВ-приставок и смартфонов.\n\n" +
                            "• Поддержка M3U/M3U8 и Xtream Codes API (ТВ, Фильмы, Сериалы)\n" +
                            "• Телепрограмма XMLTV (EPG) и Архив (Catch-up)\n" +
                            "• Двойной движок: ExoPlayer + LibVLC + Внешний плеер\n" +
                            "• Удобное управление с пульта ДУ (D-Pad, 0–9) и сенсора\n" +
                            "• Определённое устройство: ${DeviceModeHelper.detectedHardwareLabel(this)}\n" +
                            "• Активный режим: ${DeviceModeHelper.activeModeShortLabel(this, prefs)}"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
    }

    private fun showUserAgentDialog() {
        val presets = arrayOf(
            "E-Televizor/1.0 (Android IPTV)",
            "VLC/3.0.20 LibVLC/3.0.20",
            "ExoPlayerLib/2.19.1",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0",
            "Ввести вручную..."
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Выберите User-Agent")
            .setItems(presets) { _, which ->
                if (which == presets.lastIndex) {
                    val et = EditText(this).apply { setText(prefs.defaultUserAgent) }
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Свой User-Agent")
                        .setView(et)
                        .setPositiveButton("Сохранить") { _, _ ->
                            val v = et.text.toString().trim()
                            if (v.isNotBlank()) {
                                prefs.defaultUserAgent = v
                                renderCategory(activeCategory)
                            }
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                } else {
                    prefs.defaultUserAgent = presets[which]
                    renderCategory(activeCategory)
                }
            }
            .show()
    }

    private fun showSingleChoice(
        title: String,
        labels: List<String>,
        checkedIndex: Int,
        onChoose: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex.coerceAtLeast(0)) { d, which ->
                d.dismiss()
                onChoose(which)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
