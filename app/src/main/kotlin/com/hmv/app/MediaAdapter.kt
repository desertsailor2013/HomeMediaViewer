package com.hmv.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ImageRequest
import com.hmv.server.MediaItem

/**
 * 媒体列表适配器。条目展示缩略图 + 标题 + 类型/大小，点击通过 [onClick] 回调抛出。
 *
 * 支持按名称搜索和按类型（全部/视频/音频）筛选。
 */
class MediaAdapter(
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.ItemHolder>() {

    private val allItems = mutableListOf<MediaItem>()
    private val items = mutableListOf<MediaItem>()
    private var searchQuery = ""
    private var typeFilter = TypeFilter.ALL

    enum class TypeFilter { ALL, VIDEO, AUDIO }

    fun submit(newItems: List<MediaItem>) {
        allItems.clear()
        allItems.addAll(newItems)
        applyFilter()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query.trim()
        applyFilter()
    }

    fun setTypeFilter(filter: TypeFilter) {
        typeFilter = filter
        applyFilter()
    }

    private fun applyFilter() {
        items.clear()
        items.addAll(allItems.filter { item ->
            val matchesQuery = searchQuery.isEmpty() ||
                item.title.contains(searchQuery, ignoreCase = true)
            val matchesType = when (typeFilter) {
                TypeFilter.ALL -> true
                TypeFilter.VIDEO -> item.mimeType.startsWith("video")
                TypeFilter.AUDIO -> item.mimeType.startsWith("audio")
            }
            matchesQuery && matchesType
        })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return ItemHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = "${typeLabel(holder.itemView.context, item.mimeType)} · ${formatSize(item.size)}"
        holder.itemView.setOnClickListener { onClick(item) }
        loadThumbnail(holder.thumbnail, item)
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

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.media_thumbnail)
        val title: TextView = view.findViewById(R.id.media_title)
        val subtitle: TextView = view.findViewById(R.id.media_subtitle)
    }
}
