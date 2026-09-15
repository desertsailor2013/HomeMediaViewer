package com.hmv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hmv.server.MediaItem

/**
 * 媒体列表适配器。条目展示标题 + 类型/大小，点击通过 [onClick] 回调抛出。
 */
class MediaAdapter(
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.ItemHolder>() {

    private val items = mutableListOf<MediaItem>()

    fun submit(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
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
        holder.subtitle.text = "${typeLabel(item.mimeType)} · ${formatSize(item.size)}"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun typeLabel(mime: String) = when {
        mime.startsWith("video") -> "视频"
        mime.startsWith("audio") -> "音频"
        else -> "媒体"
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
        val title: TextView = view.findViewById(R.id.media_title)
        val subtitle: TextView = view.findViewById(R.id.media_subtitle)
    }
}