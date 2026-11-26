package com.etelevizor.iptv.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.etelevizor.iptv.ETelevizorApp
import com.etelevizor.iptv.R
import com.etelevizor.iptv.databinding.ActivityPlaylistsBinding
import com.etelevizor.iptv.model.EpgSource
import com.etelevizor.iptv.model.PlaylistSource
import com.etelevizor.iptv.util.DeviceModeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.UUID

class PlaylistsActivity : BaseActivity() {

    private lateinit var binding: ActivityPlaylistsBinding
    private val app get() = ETelevizorApp.instance
    private lateinit var adapter: PlaylistAdapter

    private val epgFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            setBusy(true)
            lifecycleScope.launch {
                val res = app.repository.importLocalEpgFile(uri)
                setBusy(false)
                res.onSuccess { count ->
                    Toast.makeText(this@PlaylistsActivity, "EPG из файла загружена: $count передач", Toast.LENGTH_LONG).show()
                    reloadLists()
                }.onFailure { err ->
                    Toast.makeText(this@PlaylistsActivity, "Ошибка EPG: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            setBusy(true)
            lifecycleScope.launch {
                val res = app.repository.addLocalM3uPlaylist("M3U файл", uri)
                setBusy(false)
                res.onSuccess { p ->
                    Toast.makeText(this@PlaylistsActivity, "Добавлено каналов: ${p.channelCount + p.vodCount}", Toast.LENGTH_SHORT).show()
                    reloadLists()
                }.onFailure { err ->
                    Toast.makeText(this@PlaylistsActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceModeHelper.applyOrientation(this, app.prefs, isPlayer = false)
        binding = ActivityPlaylistsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.playlistsRoot.setBackgroundColor(app.prefs.appTheme.bgColor)

        adapter = PlaylistAdapter(
            hideUrls = { app.prefs.hidePlaylistUrls },
            onRefresh = { refreshSingle(it) },
            onEdit = { editPlaylist(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvPlaylists.layoutManager = LinearLayoutManager(this)
        binding.rvPlaylists.adapter = adapter

        binding.btnPlaylistsBack.setOnClickListener { finish() }
        binding.btnAddPlaylist.setOnClickListener { showAddTypeMenu() }
        binding.btnQuickM3uUrl.setOnClickListener { openAddM3uUrl() }
        binding.btnQuickXtream.setOnClickListener { openAddXtream() }
        binding.btnQuickM3uFile.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        binding.btnQuickFreeSearch.setOnClickListener { openFreePlaylistFinder() }
        binding.btnQuickDemo.setOnClickListener { loadDemo() }

        binding.btnRefreshAllPlaylists.setOnClickListener {
            setBusy(true)
            lifecycleScope.launch {
                val (ok, fail) = app.repository.refreshAllEnabledPlaylists()
                setBusy(false)
                Toast.makeText(
                    this@PlaylistsActivity,
                    "Обновлено плейлистов: $ok (ошибок: $fail)",
                    Toast.LENGTH_SHORT
                ).show()
                reloadLists()
            }
        }

        binding.btnAddEpgSource.setOnClickListener {
            Dialogs.showEpgPresetsOrCustomDialog(
                context = this,
                onAddUrl = { name, url ->
                    val list = app.prefs.getEpgSources().toMutableList()
                    list.add(EpgSource(id = UUID.randomUUID().toString().take(10), name = name, url = url))
                    app.prefs.saveEpgSources(list)
                    reloadLists()
                    updateEpgNow()
                },
                onPickLocalFile = { epgFilePicker.launch(arrayOf("*/*")) }
            )
        }

        binding.btnUpdateEpgNow.setOnClickListener { updateEpgNow() }

        reloadLists()
    }

    private fun showAddTypeMenu() {
        val items = arrayOf(
            "По ссылке (M3U / M3U8 / XSPF / PLS / TXT)",
            "Из памяти устройства (выбрать файл)",
            "Xtream Codes API (Логин / Пароль)",
            "🌐 Поиск бесплатных плейлистов (РФ и Мир)",
            "Загрузить Демо-плейлист"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить плейлист")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openAddM3uUrl()
                    1 -> filePicker.launch(arrayOf("*/*"))
                    2 -> openAddXtream()
                    3 -> openFreePlaylistFinder()
                    4 -> loadDemo()
                }
            }
            .show()
    }

    private fun openFreePlaylistFinder() {
        Dialogs.showFreePlaylistsSearchDialog(this) { preset ->
            setBusy(true)
            lifecycleScope.launch {
                val res = app.repository.addM3uUrlPlaylist(
                    name = preset.title,
                    url = preset.url,
                    epgUrl = preset.recommendedEpgUrl
                )
                setBusy(false)
                res.onSuccess { p ->
                    Toast.makeText(
                        this@PlaylistsActivity,
                        "Добавлено «${p.name}»: ${p.channelCount} ТВ каналов",
                        Toast.LENGTH_LONG
                    ).show()
                    reloadLists()
                }.onFailure { err ->
                    Toast.makeText(this@PlaylistsActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openAddM3uUrl() {
        Dialogs.showAddM3uUrlDialog(this) { name, url, epg, ua ->
            setBusy(true)
            lifecycleScope.launch {
                val res = app.repository.addM3uUrlPlaylist(name, url, epg, ua)
                setBusy(false)
                res.onSuccess { p ->
                    Toast.makeText(this@PlaylistsActivity, "Загружено: ${p.channelCount} ТВ, ${p.vodCount} VOD", Toast.LENGTH_SHORT).show()
                    reloadLists()
                }.onFailure { err ->
                    Toast.makeText(this@PlaylistsActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openAddXtream() {
        Dialogs.showAddXtreamDialog(this) { name, server, user, pass ->
            setBusy(true)
            lifecycleScope.launch {
                val res = app.repository.addXtreamPlaylist(name, server, user, pass)
                setBusy(false)
                res.onSuccess { p ->
                    Toast.makeText(
                        this@PlaylistsActivity,
                        "Xtream готов: ТВ ${p.channelCount}, Фильмы ${p.vodCount}, Сериалы ${p.seriesCount}",
                        Toast.LENGTH_LONG
                    ).show()
                    reloadLists()
                }.onFailure { err ->
                    Toast.makeText(this@PlaylistsActivity, "Ошибка Xtream: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadDemo() {
        setBusy(true)
        lifecycleScope.launch {
            val res = app.repository.addDemoPlaylist()
            setBusy(false)
            res.onSuccess {
                Toast.makeText(this@PlaylistsActivity, "Демо-плейлист добавлен", Toast.LENGTH_SHORT).show()
                reloadLists()
            }.onFailure { err ->
                Toast.makeText(this@PlaylistsActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshSingle(p: PlaylistSource) {
        setBusy(true)
        lifecycleScope.launch {
            val res = app.repository.refreshPlaylist(p)
            setBusy(false)
            res.onSuccess { updated ->
                Toast.makeText(this@PlaylistsActivity, "Обновлено «${updated.name}»: ${updated.channelCount} ТВ", Toast.LENGTH_SHORT).show()
                reloadLists()
            }.onFailure { err ->
                Toast.makeText(this@PlaylistsActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun editPlaylist(p: PlaylistSource) {
        Dialogs.showEditPlaylistDialog(this, p) { updated ->
            app.repository.saveOrUpdatePlaylistMeta(updated)
            reloadLists()
            Toast.makeText(this, "Настройки плейлиста сохранены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(p: PlaylistSource) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить плейлист?")
            .setMessage("Удалить «${p.name}» и кэш его каналов?")
            .setPositiveButton("Удалить") { _, _ ->
                app.repository.deletePlaylist(p.id)
                reloadLists()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateEpgNow() {
        setBusy(true)
        Toast.makeText(this, "Загрузка телепрограммы EPG...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val res = app.repository.refreshAllEpg()
            setBusy(false)
            res.onSuccess { count ->
                Toast.makeText(this@PlaylistsActivity, "EPG обновлена: загружено $count передач", Toast.LENGTH_LONG).show()
                reloadLists()
            }.onFailure { err ->
                Toast.makeText(this@PlaylistsActivity, "Ошибка EPG: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun reloadLists() {
        val playlists = app.prefs.getPlaylists()
        adapter.submitList(playlists)
        binding.tvNoPlaylists.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE

        val progCount = app.epgDb.getProgramCount()
        val lastUpd = if (app.prefs.epgLastUpdated > 0L) {
            " • Обн: ${DeviceModeHelper.formatDateTime(app.prefs.epgLastUpdated)}"
        } else ""
        binding.tvEpgSummary.text = "В базе EPG: $progCount передач$lastUpd"

        binding.epgSourcesContainer.removeAllViews()
        val sources = app.prefs.getEpgSources()
        if (sources.isEmpty()) {
            val hint = TextView(this).apply {
                text = "Дополнительных XMLTV источников нет (автоматически используются ссылки из плейлистов M3U и Xtream)."
                setTextColor(getColor(R.color.text_muted))
                textSize = 13f
                setPadding(8, 8, 8, 8)
            }
            binding.epgSourcesContainer.addView(hint)
        } else {
            for (src in sources) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundResource(R.drawable.bg_focus_card)
                    setPadding(24, 18, 24, 18)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = 12
                    layoutParams = lp
                }
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(this).apply {
                    text = src.name
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 15f
                })
                info.addView(TextView(this).apply {
                    text = src.url
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 12f
                    maxLines = 1
                })
                val delBtn = Button(this).apply {
                    text = "✕"
                    setBackgroundResource(R.drawable.bg_focus_button)
                    setTextColor(getColor(R.color.accent_live))
                    setOnClickListener {
                        val next = app.prefs.getEpgSources().filterNot { it.id == src.id }
                        app.prefs.saveEpgSources(next)
                        reloadLists()
                    }
                }
                row.addView(info)
                row.addView(delBtn)
                binding.epgSourcesContainer.addView(row)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.playlistsProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }
}
