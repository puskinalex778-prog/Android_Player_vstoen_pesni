package com.etelevizor.iptv.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.etelevizor.iptv.ETelevizorApp
import com.etelevizor.iptv.R
import com.etelevizor.iptv.data.CatchupUrlBuilder
import com.etelevizor.iptv.databinding.ActivityEpgBinding
import com.etelevizor.iptv.model.Channel
import com.etelevizor.iptv.model.ContentType
import com.etelevizor.iptv.model.EpgProgram
import com.etelevizor.iptv.model.ViewStyle
import com.etelevizor.iptv.player.PlayerActivity
import com.etelevizor.iptv.util.DeviceModeHelper
import kotlinx.coroutines.launch
import java.util.Calendar

class EpgActivity : BaseActivity() {

    private lateinit var binding: ActivityEpgBinding
    private val app get() = ETelevizorApp.instance

    private var channels: List<Channel> = emptyList()
    private var selectedChannel: Channel? = null
    private var dayOffset: Int = 0 // -1 yesterday, 0 today, +1 tomorrow

    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceModeHelper.applyOrientation(this, app.prefs, isPlayer = false)
        binding = ActivityEpgBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.epgRoot.setBackgroundColor(app.prefs.appTheme.bgColor)

        binding.btnEpgBack.setOnClickListener { finish() }
        binding.btnDayYesterday.setOnClickListener { setDay(-1) }
        binding.btnDayToday.setOnClickListener { setDay(0) }
        binding.btnDayTomorrow.setOnClickListener { setDay(1) }

        channelAdapter = ChannelAdapter(
            prefs = app.prefs,
            repo = app.repository,
            viewStyle = ViewStyle.COMPACT,
            onClick = { ch, _ -> selectChannel(ch) },
            onLongClick = { ch -> launchLive(ch) },
            onFocus = { ch -> selectChannel(ch) }
        )

        binding.rvEpgChannels.layoutManager = LinearLayoutManager(this)
        binding.rvEpgChannels.adapter = channelAdapter
        binding.rvEpgPrograms.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            channels = app.repository.loadAllChannels().filter { it.contentType == ContentType.LIVE }
            channelAdapter.submitList(channels)
            val initialId = intent.getStringExtra("channel_id")
            val initial = channels.firstOrNull { it.id == initialId } ?: channels.firstOrNull()
            if (initial != null) selectChannel(initial)
        }
    }

    private fun setDay(offset: Int) {
        dayOffset = offset
        val cyan = getColor(R.color.accent_cyan)
        val primary = getColor(R.color.text_primary)
        binding.btnDayYesterday.setTextColor(if (offset == -1) cyan else primary)
        binding.btnDayToday.setTextColor(if (offset == 0) cyan else primary)
        binding.btnDayTomorrow.setTextColor(if (offset == 1) cyan else primary)
        selectedChannel?.let { loadPrograms(it) }
    }

    private fun selectChannel(channel: Channel) {
        if (selectedChannel?.id == channel.id) return
        selectedChannel = channel
        channelAdapter.submitList(channels, channel.id)
        val archiveDays = app.repository.effectiveArchiveDays(channel)
        val archiveInfo = if (archiveDays > 0) " • Архив $archiveDays дн." else ""
        binding.tvEpgSelectedChannel.text = "${channel.number}. ${channel.name}$archiveInfo"
        loadPrograms(channel)
    }

    private fun loadPrograms(channel: Channel) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fromMs = cal.timeInMillis
        val toMs = fromMs + 24L * 3600_000L

        lifecycleScope.launch {
            val programs = app.repository.getScheduleForChannel(channel, fromMs, toMs)
            binding.tvEpgEmptyPrograms.visibility = if (programs.isEmpty()) View.VISIBLE else View.GONE
            val progAdapter = EpgProgramAdapter(hasArchive = app.repository.channelHasArchive(channel)) { prog ->
                onProgramClick(channel, prog)
            }
            binding.rvEpgPrograms.adapter = progAdapter
            progAdapter.submitList(programs)

            // Auto-scroll to currently live program on Today
            if (dayOffset == 0) {
                val now = System.currentTimeMillis()
                val liveIdx = programs.indexOfFirst { it.isLive(now) }
                if (liveIdx >= 0) binding.rvEpgPrograms.scrollToPosition(liveIdx)
            }
        }
    }

    private fun onProgramClick(channel: Channel, prog: EpgProgram) {
        val now = System.currentTimeMillis()
        when {
            prog.isLive(now) -> launchLive(channel)
            prog.isPast(now) -> {
                if (app.repository.channelHasArchive(channel)) {
                    val playlist = app.repository.getPlaylistById(channel.playlistId)
                    val archiveUrl = CatchupUrlBuilder.buildCatchupUrl(channel, prog, playlist, app.prefs.globalCatchupType)
                    startActivity(
                        PlayerActivity.createArchiveIntent(
                            context = this,
                            channel = channel,
                            archiveUrl = archiveUrl,
                            programTitle = prog.title
                        )
                    )
                } else {
                    Toast.makeText(
                        this,
                        "Архив не настроен (можно включить в Настройки → Телепрограмма EPG → Тип архива)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            else -> {
                Toast.makeText(
                    this,
                    "Передача начнётся в ${DeviceModeHelper.formatTime(prog.startTimeMs)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun launchLive(channel: Channel) {
        val idx = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        startActivity(PlayerActivity.createChannelListIntent(this, channels, idx))
    }
}
