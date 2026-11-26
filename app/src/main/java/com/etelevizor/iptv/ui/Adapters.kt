package com.etelevizor.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.etelevizor.iptv.R
import com.etelevizor.iptv.data.AppPreferences
import com.etelevizor.iptv.data.IptvRepository
import com.etelevizor.iptv.model.*
import com.etelevizor.iptv.util.DeviceModeHelper

class ChannelAdapter(
    private val prefs: AppPreferences,
    private val repo: IptvRepository,
    private var viewStyle: ViewStyle,
    private val onClick: (Channel, Int) -> Unit,
    private val onLongClick: (Channel) -> Unit,
    private val onFocus: ((Channel) -> Unit)? = null
) : RecyclerView.Adapter<ChannelAdapter.ChannelVH>() {

    private var items: List<Channel> = emptyList()
    private var selectedChannelId: String = ""

    fun submitList(list: List<Channel>, selectedId: String = selectedChannelId) {
        items = list
        selectedChannelId = selectedId
        notifyDataSetChanged()
    }

    fun setViewStyle(style: ViewStyle) {
        if (viewStyle != style) {
            viewStyle = style
            notifyDataSetChanged()
        }
    }

    fun getItems(): List<Channel> = items

    override fun getItemViewType(position: Int): Int = when (viewStyle) {
        ViewStyle.LIST -> 0
        ViewStyle.GRID -> 1
        ViewStyle.COMPACT -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelVH {
        val layoutRes = when (viewType) {
            1 -> R.layout.item_channel_grid
            2 -> R.layout.item_channel_compact
            else -> R.layout.item_channel_list
        }
        val v = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ChannelVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ChannelVH, position: Int) {
        val channel = items[position]
        val scale = prefs.fontScale

        holder.tvNumber.visibility = if (prefs.showChannelNumbers && channel.contentType == ContentType.LIVE) {
            View.VISIBLE
        } else {
            View.GONE
        }
        holder.tvNumber.text = channel.number.toString()

        holder.tvName.text = channel.name
        holder.tvName.textSize = (if (viewStyle == ViewStyle.GRID) 13f else 15f) * scale

        if (prefs.showChannelLogos) {
            holder.ivLogo.visibility = View.VISIBLE
            if (channel.logoUrl.isNotBlank()) {
                Glide.with(holder.ivLogo)
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.ic_channel_placeholder)
                    .error(R.drawable.ic_channel_placeholder)
                    .into(holder.ivLogo)
            } else {
                holder.ivLogo.setImageResource(R.drawable.ic_channel_placeholder)
            }
        } else {
            holder.ivLogo.visibility = View.GONE
        }

        // Badges
        holder.tvFav.visibility = if (prefs.isFavorite(channel.id)) View.VISIBLE else View.GONE
        holder.tvArchive.visibility = if (channel.hasCatchup()) View.VISIBLE else View.GONE
        holder.tvLock.visibility = if (repo.isChannelLocked(channel)) View.VISIBLE else View.GONE

        // EPG or VOD metadata
        if (channel.contentType == ContentType.LIVE) {
            val nowNext = repo.getNowNextCached(channel)
            val cur = nowNext.current
            if (cur != null) {
                val timeRange = "${DeviceModeHelper.formatTime(cur.startTimeMs)}–${DeviceModeHelper.formatTime(cur.endTimeMs)}"
                holder.tvEpg.text = "$timeRange  ${cur.title}"
                holder.tvEpg.visibility = View.VISIBLE
                if (prefs.showEpgProgress && viewStyle != ViewStyle.COMPACT) {
                    holder.pbEpg.visibility = View.VISIBLE
                    holder.pbEpg.progress = cur.progressPercent()
                } else {
                    holder.pbEpg.visibility = View.GONE
                }
            } else {
                holder.tvEpg.text = channel.group
                holder.tvEpg.visibility = View.VISIBLE
                holder.pbEpg.visibility = View.GONE
            }
        } else {
            val meta = listOfNotNull(
                channel.year.takeIf { it.isNotBlank() },
                channel.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                channel.group.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            holder.tvEpg.text = meta
            holder.tvEpg.visibility = if (meta.isNotBlank()) View.VISIBLE else View.GONE
            holder.pbEpg.visibility = View.GONE
        }

        holder.itemView.isSelected = channel.id == selectedChannelId

        holder.itemView.setOnClickListener { onClick(channel, position) }
        holder.itemView.setOnLongClickListener {
            onLongClick(channel)
            true
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
                onFocus?.invoke(channel)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
        }
    }

    class ChannelVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNumber: TextView = v.findViewById(R.id.tvChannelNumber)
        val ivLogo: ImageView = v.findViewById(R.id.ivChannelLogo)
        val tvName: TextView = v.findViewById(R.id.tvChannelName)
        val tvEpg: TextView = v.findViewById(R.id.tvChannelEpg)
        val pbEpg: ProgressBar = v.findViewById(R.id.pbChannelEpg)
        val tvFav: TextView = v.findViewById(R.id.tvBadgeFav)
        val tvArchive: TextView = v.findViewById(R.id.tvBadgeArchive)
        val tvLock: TextView = v.findViewById(R.id.tvBadgeLock)
    }
}

class GroupAdapter(
    private val isChipMode: Boolean,
    private val onSelect: (GroupEntry) -> Unit,
    private val onLongClick: ((GroupEntry) -> Unit)? = null
) : RecyclerView.Adapter<GroupAdapter.GroupVH>() {

    private var items: List<GroupEntry> = emptyList()
    private var selectedName: String = "Все"

    fun submitList(list: List<GroupEntry>, selected: String) {
        items = list
        selectedName = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupVH {
        val layout = if (isChipMode) R.layout.item_group_chip else R.layout.item_group
        return GroupVH(LayoutInflater.from(parent.context).inflate(layout, parent, false), isChipMode)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: GroupVH, position: Int) {
        val group = items[position]
        val prefix = if (group.isLocked) "🔒 " else ""
        if (isChipMode) {
            holder.tvChip?.text = "$prefix${group.name} (${group.count})"
        } else {
            holder.tvName?.text = "$prefix${group.name}"
            holder.tvCount?.text = group.count.toString()
        }
        holder.itemView.isSelected = group.name == selectedName
        holder.itemView.setOnClickListener { onSelect(group) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(group)
            onLongClick != null
        }
    }

    class GroupVH(v: View, isChip: Boolean) : RecyclerView.ViewHolder(v) {
        val tvName: TextView? = if (!isChip) v.findViewById(R.id.tvGroupName) else null
        val tvCount: TextView? = if (!isChip) v.findViewById(R.id.tvGroupCount) else null
        val tvChip: TextView? = if (isChip) v.findViewById(R.id.tvGroupChip) else null
    }
}

class EpgProgramAdapter(
    private val hasArchive: Boolean,
    private val onClick: (EpgProgram) -> Unit
) : RecyclerView.Adapter<EpgProgramAdapter.EpgVH>() {

    private var items: List<EpgProgram> = emptyList()

    fun submitList(list: List<EpgProgram>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpgVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_epg_program, parent, false)
        return EpgVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: EpgVH, position: Int) {
        val p = items[position]
        val now = System.currentTimeMillis()
        val timeRange = "${DeviceModeHelper.formatTime(p.startTimeMs)} – ${DeviceModeHelper.formatTime(p.endTimeMs)}"
        holder.tvTime.text = timeRange
        holder.tvTitle.text = p.title

        if (p.description.isNotBlank()) {
            holder.tvDesc.visibility = View.VISIBLE
            holder.tvDesc.text = p.description
        } else {
            holder.tvDesc.visibility = View.GONE
        }

        when {
            p.isLive(now) -> {
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "● В ЭФИРЕ"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_live)
                holder.itemView.isSelected = true
            }
            p.isPast(now) && hasArchive -> {
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "↺ АРХИВ"
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_archive)
                holder.itemView.isSelected = false
            }
            else -> {
                holder.tvBadge.visibility = View.GONE
                holder.itemView.isSelected = false
            }
        }

        holder.itemView.setOnClickListener { onClick(p) }
    }

    class EpgVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTime: TextView = v.findViewById(R.id.tvEpgTime)
        val tvBadge: TextView = v.findViewById(R.id.tvEpgStatusBadge)
        val tvTitle: TextView = v.findViewById(R.id.tvEpgTitle)
        val tvDesc: TextView = v.findViewById(R.id.tvEpgDesc)
    }
}

class PlaylistAdapter(
    private val onRefresh: (PlaylistSource) -> Unit,
    private val onEdit: (PlaylistSource) -> Unit,
    private val onDelete: (PlaylistSource) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistVH>() {

    private var items: List<PlaylistSource> = emptyList()

    fun submitList(list: List<PlaylistSource>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return PlaylistVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PlaylistVH, position: Int) {
        val p = items[position]
        val statusPrefix = if (p.enabled) "" else "[Выкл] "
        holder.tvName.text = "$statusPrefix${p.name}"

        val metaParts = mutableListOf<String>()
        metaParts.add(p.type.label)
        metaParts.add("ТВ: ${p.channelCount}")
        if (p.vodCount > 0) metaParts.add("Фильмы: ${p.vodCount}")
        if (p.seriesCount > 0) metaParts.add("Сериалы: ${p.seriesCount}")
        if (p.accountExpiry.isNotBlank()) metaParts.add("До: ${p.accountExpiry}")
        if (p.lastUpdated > 0L) metaParts.add("Обн: ${DeviceModeHelper.formatDateTime(p.lastUpdated)}")
        holder.tvMeta.text = metaParts.joinToString(" • ")

        holder.tvUrl.text = when (p.type) {
            PlaylistType.M3U_URL -> p.url
            PlaylistType.M3U_FILE -> p.localPath
            PlaylistType.XTREAM -> "${p.xtreamServer} (${p.xtreamUsername})"
        }

        holder.itemView.setOnClickListener { onEdit(p) }
        holder.btnRefresh.setOnClickListener { onRefresh(p) }
        holder.btnEdit.setOnClickListener { onEdit(p) }
        holder.btnDelete.setOnClickListener { onDelete(p) }
    }

    class PlaylistVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvPlaylistName)
        val tvMeta: TextView = v.findViewById(R.id.tvPlaylistMeta)
        val tvUrl: TextView = v.findViewById(R.id.tvPlaylistUrl)
        val btnRefresh: Button = v.findViewById(R.id.btnPlaylistRefresh)
        val btnEdit: Button = v.findViewById(R.id.btnPlaylistEdit)
        val btnDelete: Button = v.findViewById(R.id.btnPlaylistDelete)
    }
}

data class SettingRow(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val valueText: String = "",
    val isSwitch: Boolean = false,
    val isChecked: Boolean = false,
    val onClick: () -> Unit
)

class SettingCategoryAdapter(
    private val isChipMode: Boolean,
    private val categories: List<String>,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<SettingCategoryAdapter.CatVH>() {

    var selectedIndex = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatVH {
        val layout = if (isChipMode) R.layout.item_group_chip else R.layout.item_setting_category
        return CatVH(LayoutInflater.from(parent.context).inflate(layout, parent, false), isChipMode)
    }

    override fun getItemCount(): Int = categories.size

    override fun onBindViewHolder(holder: CatVH, position: Int) {
        holder.tv.text = categories[position]
        holder.itemView.isSelected = position == selectedIndex
        holder.itemView.setOnClickListener {
            selectedIndex = position
            onSelect(position)
        }
    }

    class CatVH(v: View, isChip: Boolean) : RecyclerView.ViewHolder(v) {
        val tv: TextView = if (isChip) v.findViewById(R.id.tvGroupChip) else v.findViewById(R.id.tvCategoryTitle)
    }
}

class SettingRowAdapter : RecyclerView.Adapter<SettingRowAdapter.RowVH>() {

    private var items: List<SettingRow> = emptyList()

    fun submitList(list: List<SettingRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_row, parent, false)
        return RowVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RowVH, position: Int) {
        val row = items[position]
        holder.tvTitle.text = row.title
        if (row.subtitle.isNotBlank()) {
            holder.tvSubtitle.visibility = View.VISIBLE
            holder.tvSubtitle.text = row.subtitle
        } else {
            holder.tvSubtitle.visibility = View.GONE
        }

        if (row.isSwitch) {
            holder.switchView.visibility = View.VISIBLE
            holder.tvValue.visibility = View.GONE
            holder.switchView.isChecked = row.isChecked
        } else {
            holder.switchView.visibility = View.GONE
            if (row.valueText.isNotBlank()) {
                holder.tvValue.visibility = View.VISIBLE
                holder.tvValue.text = row.valueText
            } else {
                holder.tvValue.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener { row.onClick() }
    }

    class RowVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvSettingTitle)
        val tvSubtitle: TextView = v.findViewById(R.id.tvSettingSubtitle)
        val tvValue: TextView = v.findViewById(R.id.tvSettingValue)
        val switchView: SwitchCompat = v.findViewById(R.id.switchSetting)
    }
}
