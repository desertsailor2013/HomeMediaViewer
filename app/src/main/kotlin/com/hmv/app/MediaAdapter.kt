package com.hmv.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ImageRequest
import com.hmv.server.MediaItem

/**
 * 媒体列表适配器。支持平铺视图和分组视图两种模式。
 *
 * - 平铺模式：直接展示所有媒体项
 * - 分组模式：按文件夹分组，每个分组有 header
 */
class MediaAdapter(
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val allItems = mutableListOf<MediaItem>()
    private val displayItems = mutableListOf<ListItem>()
    private val collapsedFolders = mutableSetOf<String>()
    private var searchQuery = ""
    private var typeFilter = TypeFilter.ALL
    private var groupByFolder = false

    sealed class ListItem {
        data class Header(val folderName: String, val count: Int, val collapsed: Boolean) : ListItem()
        data class Media(val item: MediaItem) : ListItem()
    }

    enum class TypeFilter { ALL, VIDEO, AUDIO }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MEDIA = 1
    }

    fun submit(newItems: List<MediaItem>) {
        allItems.clear()
        allItems.addAll(newItems)
        applyFilter()
    }

    /** 获取当前过滤后的媒体列表（用于播放队列） */
    fun getCurrentItems(): List<MediaItem> = allItems.filter { item ->
        val matchesQuery = searchQuery.isEmpty() ||
            item.title.contains(searchQuery, ignoreCase = true)
        val matchesType = when (typeFilter) {
            TypeFilter.ALL -> true
            TypeFilter.VIDEO -> item.mimeType.startsWith("video")
            TypeFilter.AUDIO -> item.mimeType.startsWith("audio")
        }
        matchesQuery && matchesType
    }

    fun setSearchQuery(query: String) {
        searchQuery = query.trim()
        applyFilter()
    }

    fun setTypeFilter(filter: TypeFilter) {
        typeFilter = filter
        applyFilter()
    }

    fun setGroupByFolder(group: Boolean) {
        groupByFolder = group
        applyFilter()
    }

    fun isGroupByFolder(): Boolean = groupByFolder

    private fun applyFilter() {
        val filtered = allItems.filter { item ->
            val matchesQuery = searchQuery.isEmpty() ||
                item.title.contains(searchQuery, ignoreCase = true)
            val matchesType = when (typeFilter) {
                TypeFilter.ALL -> true
                TypeFilter.VIDEO -> item.mimeType.startsWith("video")
                TypeFilter.AUDIO -> item.mimeType.startsWith("audio")
            }
            matchesQuery && matchesType
        }

        val newItems = if (groupByFolder) {
            buildGroupedList(filtered)
        } else {
            filtered.map { ListItem.Media(it) }
        }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = displayItems.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = displayItems[oldPos]
                val new = newItems[newPos]
                return when {
                    old is ListItem.Header && new is ListItem.Header -> old.folderName == new.folderName
                    old is ListItem.Media && new is ListItem.Media -> old.item.id == new.item.id
                    else -> false
                }
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return displayItems[oldPos] == newItems[newPos]
            }
        })
        displayItems.clear()
        displayItems.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    private fun buildGroupedList(items: List<MediaItem>): List<ListItem> {
        val grouped = items.groupBy { it.folderName.ifEmpty { "未分类" } }
            .toSortedMap(compareByDescending { it })

        val result = mutableListOf<ListItem>()
        for ((folder, folderItems) in grouped) {
            val collapsed = collapsedFolders.contains(folder)
            result.add(ListItem.Header(folder, folderItems.size, collapsed))
            if (!collapsed) {
                for (item in folderItems) {
                    result.add(ListItem.Media(item))
                }
            }
        }
        return result
    }

    fun toggleFolder(folderName: String) {
        if (collapsedFolders.contains(folderName)) {
            collapsedFolders.remove(folderName)
        } else {
            collapsedFolders.add(folderName)
        }
        applyFilter()
    }

    override fun getItemViewType(position: Int): Int = when (displayItems[position]) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.Media -> TYPE_MEDIA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder_header, parent, false)
                HeaderHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_media, parent, false)
                MediaHolder(view)
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is ListItem.Header -> {
                val h = holder as HeaderHolder
                h.folderName.text = item.folderName
                h.count.text = holder.itemView.context.getString(R.string.folder_count, item.count)
                val arrowRes = if (item.collapsed) R.drawable.ic_arrow_right else R.drawable.ic_arrow_down
                h.arrow.setImageResource(arrowRes)
                h.itemView.setOnClickListener { toggleFolder(item.folderName) }
            }
            is ListItem.Media -> {
                val m = holder as MediaHolder
                m.title.text = item.item.title
                m.subtitle.text = "${typeLabel(m.itemView.context, item.item.mimeType)} · ${formatSize(item.item.size)}"
                m.itemView.setOnClickListener { onClick(item.item) }
                loadThumbnail(m.thumbnail, item.item)
            }
        }
    }

    private fun loadThumbnail(imageView: ImageView, item: MediaItem) {
        val context = imageView.context
        val thumbnailUri = item.thumbnailUri
        if (thumbnailUri.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_launcher)
            return
        }
        val request = ImageRequest.Builder(context)
            .data(Uri.parse(thumbnailUri))
            .crossfade(true)
            .size(168, 168)
            .build()
        imageView.load(request) {
            placeholder(R.drawable.ic_launcher)
            error(R.drawable.ic_launcher)
        }
    }

    private fun typeLabel(context: android.content.Context, mime: String) = when {
        mime.startsWith("video") -> context.getString(R.string.filter_video)
        mime.startsWith("audio") -> context.getString(R.string.filter_audio)
        else -> context.getString(R.string.type_media)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.folder_name)
        val count: TextView = view.findViewById(R.id.folder_count)
        val arrow: ImageView = view.findViewById(R.id.folder_arrow)
    }

    class MediaHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.media_thumbnail)
        val title: TextView = view.findViewById(R.id.media_title)
        val subtitle: TextView = view.findViewById(R.id.media_subtitle)
    }
}
