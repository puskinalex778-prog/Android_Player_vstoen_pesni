package com.etelevizor.iptv.ui

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import com.etelevizor.iptv.ETelevizorApp
import com.etelevizor.iptv.R
import com.etelevizor.iptv.data.AppPreferences
import com.etelevizor.iptv.data.DiscoveredPlaylist
import com.etelevizor.iptv.data.FreeEpgPreset
import com.etelevizor.iptv.data.FreePlaylistPreset
import com.etelevizor.iptv.data.FreePlaylistsCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.etelevizor.iptv.model.DeviceMode
import com.etelevizor.iptv.model.PlaylistSource
import com.etelevizor.iptv.model.PlaylistType
import com.etelevizor.iptv.util.DeviceModeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Dialogs {

    fun showDeviceModeDialog(
        context: Context,
        prefs: AppPreferences,
        onModeChanged: (DeviceMode) -> Unit
    ) {
        val detected = DeviceModeHelper.detectedHardwareLabel(context)
        val options = arrayOf(
            "Автоопределение (сейчас: $detected)",
            "Телевизор / Приставка (Пульт ДУ)",
            "Телефон / Планшет (Сенсорный экран)"
        )
        val checked = when (prefs.deviceMode) {
            DeviceMode.AUTO -> 0
            DeviceMode.TV -> 1
            DeviceMode.PHONE -> 2
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Режим интерфейса")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val newMode = when (which) {
                    1 -> DeviceMode.TV
                    2 -> DeviceMode.PHONE
                    else -> DeviceMode.AUTO
                }
                prefs.deviceMode = newMode
                dialog.dismiss()
                onModeChanged(newMode)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showAddM3uUrlDialog(
        context: Context,
        onSubmit: (name: String, url: String, epgUrl: String, userAgent: String) -> Unit
    ) {
        val container = createFormContainer(context)
        val etName = addField(context, container, "Название плейлиста", "Мой IPTV плейлист")
        val etUrl = addField(context, container, "Ссылка M3U / M3U8 / XSPF / PLS / TXT *", "")
        etUrl.hint = "http://.../playlist.m3u8"
        val btnPaste = AppCompatButton(context).apply {
            text = "📋 Вставить ссылку из буфера обмена"
            isAllCaps = false
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_cyan))
            setPadding(24, 14, 24, 14)
            setOnClickListener {
                val clip = readClipboardText(context)
                if (clip.isNotBlank()) etUrl.setText(clip)
                else Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(btnPaste)
        val etEpg = addField(context, container, "Ссылка EPG / XMLTV (необязательно)", "")
        etEpg.hint = "http://.../epg.xml.gz"
        val etUa = addField(context, container, "User-Agent (необязательно)", "")

        MaterialAlertDialogBuilder(context)
            .setTitle("Добавить плейлист по ссылке")
            .setView(wrapScroll(context, container))
            .setPositiveButton("Загрузить") { _, _ ->
                val url = etUrl.text.toString().trim()
                if (url.isBlank()) {
                    Toast.makeText(context, "Введите ссылку на плейлист", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSubmit(
                    etName.text.toString().trim().ifBlank { "M3U плейлист" },
                    url,
                    etEpg.text.toString().trim(),
                    etUa.text.toString().trim()
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showFreePlaylistsSearchDialog(
        context: Context,
        onSelectPreset: (FreePlaylistPreset) -> Unit
    ) {
        val root = createFormContainer(context)
        val dp = context.resources.displayMetrics.density

        // 1. Live Web / GitHub Source Parser Bar
        root.addView(TextView(context).apply {
            text = "Парсер сайтов и GitHub (ищет .m3u / .m3u8 ссылки):"
            textSize = 12f
            setTextColor(context.getColor(R.color.accent_cyan))
            setPadding(0, 0, 0, 6)
        })

        val parseRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val etParseUrl = EditText(context).apply {
            hint = "https://m3u.su/ или любой сайт / GitHub"
            setText("https://m3u.su/")
            setSingleLine(true)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnScanUrl = AppCompatButton(context).apply {
            text = "Парсить"
            isAllCaps = false
            textSize = 13f
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_emerald))
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }
        parseRow.addView(etParseUrl)
        parseRow.addView(btnScanUrl)
        root.addView(parseRow)

        // Quick scan buttons row
        val quickScanRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        val btnScanM3uSu = AppCompatButton(context).apply {
            text = "🔍 m3u.su"
            isAllCaps = false
            textSize = 12f
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_cyan))
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = (6 * dp).toInt()
            layoutParams = lp
        }
        val btnScanSlava = AppCompatButton(context).apply {
            text = "🔍 slava-67/TV"
            isAllCaps = false
            textSize = 12f
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_cyan))
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = (6 * dp).toInt()
            layoutParams = lp
        }
        val btnScanAll = AppCompatButton(context).apply {
            text = "🌐 Все источники"
            isAllCaps = false
            textSize = 12f
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_amber))
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        quickScanRow.addView(btnScanM3uSu)
        quickScanRow.addView(btnScanSlava)
        quickScanRow.addView(btnScanAll)
        root.addView(quickScanRow)

        val scanProgress = ProgressBar(context).apply {
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        }
        root.addView(scanProgress)

        // 2. Filter input for catalog + parsed results
        val etQuery = EditText(context).apply {
            hint = "Фильтр: Россия, русский, кино, спорт, m3u.su, США..."
            setSingleLine(true)
            textSize = 13f
        }
        root.addView(etQuery)

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * dp).toInt()
            )
            addView(listContainer)
        }
        root.addView(scroll)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("🔍 Парсер и поиск бесплатных M3U")
            .setView(root)
            .setNegativeButton("Закрыть", null)
            .create()

        var discoveredItems: List<DiscoveredPlaylist> = emptyList()

        fun addCard(title: String, subtitle: String, url: String, epgUrl: String) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_focus_card)
                isFocusable = true
                isClickable = true
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (8 * dp).toInt()
                layoutParams = lp
                setOnClickListener {
                    dialog.dismiss()
                    onSelectPreset(
                        FreePlaylistPreset(
                            title = title,
                            subtitle = subtitle,
                            region = "",
                            url = url,
                            recommendedEpgUrl = epgUrl
                        )
                    )
                }
            }
            card.addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(context.getColor(R.color.text_primary))
            })
            card.addView(TextView(context).apply {
                text = subtitle
                textSize = 11f
                setTextColor(context.getColor(R.color.accent_cyan))
                maxLines = 2
            })
            listContainer.addView(card)
        }

        fun renderList(query: String) {
            listContainer.removeAllViews()
            val q = query.trim().lowercase()

            if (discoveredItems.isNotEmpty()) {
                val filteredDisc = if (q.isEmpty()) discoveredItems else discoveredItems.filter {
                    it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
                }
                if (filteredDisc.isNotEmpty()) {
                    listContainer.addView(TextView(context).apply {
                        text = "НАЙДЕНО ПАРСЕРОМ (${filteredDisc.size}):"
                        textSize = 11f
                        setTextColor(context.getColor(R.color.accent_emerald))
                        setPadding(0, 4, 0, 6)
                    })
                    for (d in filteredDisc) {
                        addCard("🌐 ${d.title}", d.url, d.url, d.epgUrl.ifBlank { FreePlaylistsCatalog.DEFAULT_AUTO_EPG_URL })
                    }
                }
            }

            val matches = FreePlaylistsCatalog.search(query)
            if (matches.isNotEmpty()) {
                listContainer.addView(TextView(context).apply {
                    text = "ПРОВЕРЕННЫЕ САМООБНОВЛЯЕМЫЕ КАТАЛОГИ (${matches.size}):"
                    textSize = 11f
                    setTextColor(context.getColor(R.color.text_muted))
                    setPadding(0, 8, 0, 6)
                })
                for (preset in matches) {
                    addCard(preset.title, "${preset.subtitle}\n${preset.url}", preset.url, preset.recommendedEpgUrl)
                }
            }

            if (listContainer.childCount == 0) {
                listContainer.addView(TextView(context).apply {
                    text = "Ничего не найдено. Введите URL сайта сверху и нажмите «Парсить» или измените фильтр."
                    setPadding(8, 16, 8, 16)
                })
            }
        }

        val scope = CoroutineScope(Dispatchers.Main)
        fun runScan(action: suspend () -> List<DiscoveredPlaylist>, label: String) {
            scanProgress.visibility = android.view.View.VISIBLE
            Toast.makeText(context, "Сканирую $label...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val res = runCatching { action() }
                scanProgress.visibility = android.view.View.GONE
                res.onSuccess { list ->
                    discoveredItems = list
                    Toast.makeText(context, "Найдено плейлистов: ${list.size}", Toast.LENGTH_SHORT).show()
                    renderList(etQuery.text.toString())
                }.onFailure { err ->
                    Toast.makeText(context, "Ошибка парсинга: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val scanner = ETelevizorApp.instance.repository.playlistScanner
        btnScanUrl.setOnClickListener {
            val target = etParseUrl.text.toString().trim()
            if (target.isBlank()) return@setOnClickListener
            runScan({ scanner.scanSourceUrl(target).getOrThrow() }, target)
        }
        btnScanM3uSu.setOnClickListener {
            etParseUrl.setText("https://m3u.su/")
            runScan({ scanner.scanSourceUrl("https://m3u.su/").getOrThrow() }, "m3u.su")
        }
        btnScanSlava.setOnClickListener {
            etParseUrl.setText("https://github.com/slava-67/TV/tree/IPTV")
            runScan({ scanner.scanGitHubRepo("slava-67", "TV", "IPTV").getOrThrow() }, "slava-67/TV")
        }
        btnScanAll.setOnClickListener {
            runScan({ scanner.scanAllDefaultSources() }, "все источники")
        }

        renderList("")
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderList(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.show()
    }

    fun showEpgPresetsOrCustomDialog(
        context: Context,
        onAddUrl: (name: String, url: String) -> Unit,
        onPickLocalFile: () -> Unit
    ) {
        val options = mutableListOf<String>()
        options.add("➕ Ввести свою ссылку EPG (XML / .xml.gz)")
        options.add("📂 Загрузить файл телепрограммы из памяти (.xml / .xml.gz)")
        FreePlaylistsCatalog.epgPresets.forEach { preset ->
            options.add("🌐 ${preset.title}")
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Добавить телепрограмму (EPG)")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showAddEpgSourceDialog(context, onAddUrl)
                    1 -> onPickLocalFile()
                    else -> {
                        val preset: FreeEpgPreset = FreePlaylistsCatalog.epgPresets[which - 2]
                        onAddUrl(preset.title, preset.url)
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun readClipboardText(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = cm?.primaryClip?.getItemAt(0)
        return item?.coerceToText(context)?.toString()?.trim().orEmpty()
    }

    fun showAddXtreamDialog(
        context: Context,
        onSubmit: (name: String, server: String, user: String, pass: String) -> Unit
    ) {
        val container = createFormContainer(context)
        val etName = addField(context, container, "Название плейлиста", "Xtream IPTV")
        val etServer = addField(context, container, "Адрес сервера (URL) *", "http://server:port")
        val etUser = addField(context, container, "Имя пользователя (Логин) *", "")
        val etPass = addField(context, container, "Пароль *", "", isPassword = true)

        MaterialAlertDialogBuilder(context)
            .setTitle("Подключить Xtream Codes API")
            .setView(wrapScroll(context, container))
            .setPositiveButton("Подключить") { _, _ ->
                val server = etServer.text.toString().trim()
                val user = etUser.text.toString().trim()
                val pass = etPass.text.toString().trim()
                if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                    Toast.makeText(context, "Заполните сервер, логин и пароль", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSubmit(
                    etName.text.toString().trim().ifBlank { "Xtream IPTV" },
                    server,
                    user,
                    pass
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showEditPlaylistDialog(
        context: Context,
        playlist: PlaylistSource,
        onSave: (PlaylistSource) -> Unit
    ) {
        val container = createFormContainer(context)
        val etName = addField(context, container, "Название", playlist.name)
        val etUrl = if (playlist.type == PlaylistType.M3U_URL) {
            addField(context, container, "URL плейлиста", playlist.url)
        } else null
        val etServer = if (playlist.type == PlaylistType.XTREAM) {
            addField(context, container, "Сервер Xtream", playlist.xtreamServer)
        } else null
        val etUser = if (playlist.type == PlaylistType.XTREAM) {
            addField(context, container, "Логин", playlist.xtreamUsername)
        } else null
        val etPass = if (playlist.type == PlaylistType.XTREAM) {
            addField(context, container, "Пароль", playlist.xtreamPassword)
        } else null

        val etEpg = addField(context, container, "Ссылка EPG (XMLTV / .xml.gz)", playlist.epgUrl)
        val etShift = addField(context, container, "Смещение EPG (часы, напр. +3 или -2)", playlist.epgShiftHours.toString())
        val etUa = addField(context, container, "Пользовательский User-Agent", playlist.userAgent)
        val etRef = addField(context, container, "Referer", playlist.referer)
        val etCatchupType = addField(context, container, "Тип архива (default, shift, flussonic, append, xc)", playlist.catchupType)
        val etCatchupDays = addField(context, container, "Глубина архива (дни)", playlist.catchupDays.toString())
        val etAutoUpdate = addField(context, container, "Автообновление (часы: 0=вручную, -1=при запуске, 24=раз в день)", playlist.autoUpdateHours.toString())

        val cbEnabled = CheckBox(context).apply {
            text = "Плейлист активен (показывать каналы)"
            isChecked = playlist.enabled
        }
        container.addView(cbEnabled)

        MaterialAlertDialogBuilder(context)
            .setTitle("Настройки плейлиста")
            .setView(wrapScroll(context, container))
            .setPositiveButton("Сохранить") { _, _ ->
                val updated = playlist.copy(
                    name = etName.text.toString().trim().ifBlank { playlist.name },
                    url = etUrl?.text?.toString()?.trim() ?: playlist.url,
                    xtreamServer = etServer?.text?.toString()?.trim() ?: playlist.xtreamServer,
                    xtreamUsername = etUser?.text?.toString()?.trim() ?: playlist.xtreamUsername,
                    xtreamPassword = etPass?.text?.toString()?.trim() ?: playlist.xtreamPassword,
                    epgUrl = etEpg.text.toString().trim(),
                    epgShiftHours = etShift.text.toString().trim().toFloatOrNull() ?: 0f,
                    userAgent = etUa.text.toString().trim(),
                    referer = etRef.text.toString().trim(),
                    catchupType = etCatchupType.text.toString().trim(),
                    catchupDays = etCatchupDays.text.toString().trim().toIntOrNull() ?: 0,
                    autoUpdateHours = etAutoUpdate.text.toString().trim().toIntOrNull() ?: 24,
                    enabled = cbEnabled.isChecked
                )
                onSave(updated)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showAddEpgSourceDialog(
        context: Context,
        onSubmit: (name: String, url: String) -> Unit
    ) {
        val container = createFormContainer(context)
        val etName = addField(context, container, "Название источника EPG", "Телепрограмма XMLTV")
        val etUrl = addField(context, container, "Ссылка на .xml или .xml.gz *", "")
        etUrl.hint = "https://iptvx.one/epg/epg_lite.xml.gz"
        val btnPaste = AppCompatButton(context).apply {
            text = "📋 Вставить ссылку из буфера обмена"
            isAllCaps = false
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_cyan))
            setPadding(24, 14, 24, 14)
            setOnClickListener {
                val clip = readClipboardText(context)
                if (clip.isNotBlank()) etUrl.setText(clip)
                else Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(btnPaste)

        MaterialAlertDialogBuilder(context)
            .setTitle("Добавить источник EPG")
            .setView(wrapScroll(context, container))
            .setPositiveButton("Добавить") { _, _ ->
                val url = etUrl.text.toString().trim()
                if (url.isBlank()) {
                    Toast.makeText(context, "Введите URL файла телепрограммы", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSubmit(etName.text.toString().trim().ifBlank { "XMLTV EPG" }, url)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showPinVerifyDialog(
        context: Context,
        expectedPin: String,
        title: String = "Родительский контроль",
        onSuccess: () -> Unit
    ) {
        val container = createFormContainer(context)
        val etPin = addField(context, container, "Введите PIN-код (по умолчанию 0000)", "", isNumericPin = true)

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Подтвердить") { _, _ ->
                if (etPin.text.toString().trim() == expectedPin) {
                    onSuccess()
                } else {
                    Toast.makeText(context, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showChangePinDialog(
        context: Context,
        prefs: AppPreferences,
        onChanged: () -> Unit
    ) {
        val container = createFormContainer(context)
        val etOld = addField(context, container, "Текущий PIN (по умолчанию 0000)", "", isNumericPin = true)
        val etNew = addField(context, container, "Новый PIN (4 цифры)", "", isNumericPin = true)

        MaterialAlertDialogBuilder(context)
            .setTitle("Изменить PIN-код")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                val oldPin = etOld.text.toString().trim()
                val newPin = etNew.text.toString().trim()
                if (oldPin != prefs.parentalPin) {
                    Toast.makeText(context, "Неверный текущий PIN", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPin.length < 4) {
                    Toast.makeText(context, "PIN должен содержать минимум 4 цифры", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.parentalPin = newPin
                Toast.makeText(context, "PIN-код обновлён", Toast.LENGTH_SHORT).show()
                onChanged()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showSleepTimerDialog(
        context: Context,
        currentMinutes: Int,
        onSelect: (Int) -> Unit
    ) {
        val values = intArrayOf(0, 15, 30, 45, 60, 90, 120)
        val labels = arrayOf("Выключен", "15 минут", "30 минут", "45 минут", "60 минут", "90 минут", "120 минут")
        val checked = values.indexOfFirst { it == currentMinutes }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle("Таймер сна")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                onSelect(values[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createFormContainer(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * context.resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, p / 2)
        }
    }

    private fun addField(
        context: Context,
        parent: LinearLayout,
        label: String,
        initial: String,
        isPassword: Boolean = false,
        isNumericPin: Boolean = false
    ): EditText {
        val tv = TextView(context).apply {
            text = label
            textSize = 12f
            setPadding(0, 12, 0, 4)
        }
        val et = EditText(context).apply {
            setText(initial)
            setSingleLine(true)
            inputType = when {
                isNumericPin -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                isPassword -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT
            }
        }
        parent.addView(tv)
        parent.addView(et)
        return et
    }

    private fun wrapScroll(context: Context, child: LinearLayout): ScrollView {
        return ScrollView(context).apply { addView(child) }
    }
}
