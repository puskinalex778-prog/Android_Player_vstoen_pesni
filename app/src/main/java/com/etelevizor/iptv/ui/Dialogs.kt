package com.etelevizor.iptv.ui

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.etelevizor.iptv.R
import com.etelevizor.iptv.data.AppPreferences
import com.etelevizor.iptv.data.FreeEpgPreset
import com.etelevizor.iptv.data.FreePlaylistPreset
import com.etelevizor.iptv.data.FreePlaylistsCatalog
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
        val btnPaste = Button(context).apply {
            text = "📋 Вставить ссылку из буфера обмена"
            isAllCaps = false
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_cyan))
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
        val etQuery = EditText(context).apply {
            hint = "Поиск: Россия, русский, кино, спорт, США, Германия..."
            setSingleLine(true)
        }
        root.addView(etQuery)

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 8)
        }
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (360 * context.resources.displayMetrics.density).toInt()
            )
            addView(listContainer)
        }
        root.addView(scroll)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("🌐 Бесплатные плейлисты (РФ и Мир)")
            .setView(root)
            .setNegativeButton("Закрыть", null)
            .create()

        fun renderPresets(query: String) {
            listContainer.removeAllViews()
            val matches = FreePlaylistsCatalog.search(query)
            if (matches.isEmpty()) {
                listContainer.addView(TextView(context).apply {
                    text = "Ничего не найдено. Попробуйте: Россия, Мир, Кино, Музыка, Спорт, США."
                    setPadding(8, 16, 8, 16)
                })
                return
            }
            for (preset in matches) {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_focus_card)
                    isFocusable = true
                    isClickable = true
                    setPadding(24, 18, 24, 18)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = 10
                    layoutParams = lp
                    setOnClickListener {
                        dialog.dismiss()
                        onSelectPreset(preset)
                    }
                }
                card.addView(TextView(context).apply {
                    text = preset.title
                    textSize = 15f
                    setTextColor(context.getColor(R.color.text_primary))
                })
                card.addView(TextView(context).apply {
                    text = preset.subtitle
                    textSize = 12f
                    setTextColor(context.getColor(R.color.text_secondary))
                })
                listContainer.addView(card)
            }
        }

        renderPresets("")
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderPresets(s?.toString().orEmpty())
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
        val btnPaste = Button(context).apply {
            text = "📋 Вставить ссылку из буфера обмена"
            isAllCaps = false
            setBackgroundResource(R.drawable.bg_focus_button)
            setTextColor(context.getColor(R.color.accent_cyan))
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
