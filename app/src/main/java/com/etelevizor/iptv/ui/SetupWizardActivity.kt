package com.etelevizor.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.etelevizor.iptv.ETelevizorApp
import com.etelevizor.iptv.databinding.ActivitySetupWizardBinding
import com.etelevizor.iptv.model.DeviceMode
import com.etelevizor.iptv.util.DeviceModeHelper
import kotlinx.coroutines.launch

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupWizardBinding
    private val app get() = ETelevizorApp.instance

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            setBusy(true)
            lifecycleScope.launch {
                val res = app.repository.addLocalM3uPlaylist("Мой M3U файл", uri)
                setBusy(false)
                res.onSuccess { p ->
                    Toast.makeText(
                        this@SetupWizardActivity,
                        "Загружено каналов: ${p.channelCount + p.vodCount}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finishWizard()
                }.onFailure { err ->
                    Toast.makeText(
                        this@SetupWizardActivity,
                        "Ошибка: ${err.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val detected = DeviceModeHelper.detectedHardwareLabel(this)
        binding.tvAutoSubtitle.text = "Определено устройство: $detected"

        binding.cardModeAuto.setOnClickListener { chooseMode(DeviceMode.AUTO) }
        binding.cardModeTv.setOnClickListener { chooseMode(DeviceMode.TV) }
        binding.cardModePhone.setOnClickListener { chooseMode(DeviceMode.PHONE) }

        binding.btnWizardAddM3uUrl.setOnClickListener {
            Dialogs.showAddM3uUrlDialog(this) { name, url, epgUrl, ua ->
                setBusy(true)
                lifecycleScope.launch {
                    val res = app.repository.addM3uUrlPlaylist(name, url, epgUrl, ua)
                    setBusy(false)
                    res.onSuccess { p ->
                        Toast.makeText(
                            this@SetupWizardActivity,
                            "Плейлист «${p.name}»: ${p.channelCount} каналов",
                            Toast.LENGTH_SHORT
                        ).show()
                        finishWizard()
                    }.onFailure { err ->
                        Toast.makeText(
                            this@SetupWizardActivity,
                            "Ошибка загрузки: ${err.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        binding.btnWizardAddXtream.setOnClickListener {
            Dialogs.showAddXtreamDialog(this) { name, server, user, pass ->
                setBusy(true)
                lifecycleScope.launch {
                    val res = app.repository.addXtreamPlaylist(name, server, user, pass)
                    setBusy(false)
                    res.onSuccess { p ->
                        Toast.makeText(
                            this@SetupWizardActivity,
                            "Xtream подключен: ТВ ${p.channelCount}, Фильмы ${p.vodCount}, Сериалы ${p.seriesCount}",
                            Toast.LENGTH_LONG
                        ).show()
                        finishWizard()
                    }.onFailure { err ->
                        Toast.makeText(
                            this@SetupWizardActivity,
                            "Ошибка Xtream: ${err.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        binding.btnWizardAddFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        binding.btnWizardDemo.setOnClickListener {
            setBusy(true)
            lifecycleScope.launch {
                val res = app.repository.addDemoPlaylist()
                setBusy(false)
                res.onSuccess {
                    Toast.makeText(this@SetupWizardActivity, "Демо-плейлист загружен!", Toast.LENGTH_SHORT).show()
                    finishWizard()
                }.onFailure { err ->
                    Toast.makeText(this@SetupWizardActivity, "Ошибка: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnWizardSkip.setOnClickListener { finishWizard() }

        // Put initial D-Pad focus on the recommended card
        binding.cardModeAuto.requestFocus()
    }

    private fun chooseMode(mode: DeviceMode) {
        app.prefs.deviceMode = mode
        if (app.prefs.getPlaylists().isNotEmpty()) {
            finishWizard()
            return
        }
        binding.stepModeContainer.visibility = View.GONE
        binding.stepPlaylistContainer.visibility = View.VISIBLE
        binding.tvDetectedHint.text = "Выбран режим: ${DeviceModeHelper.activeModeShortLabel(this, app.prefs)}"
        binding.btnWizardAddM3uUrl.requestFocus()
    }

    private fun setBusy(busy: Boolean) {
        binding.wizardProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun finishWizard() {
        app.prefs.setupCompleted = true
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }
}
