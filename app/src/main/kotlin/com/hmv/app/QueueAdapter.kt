package com.hmv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QueueAdapter(
    private val onItemClicked: (Int) -> Unit,
    private val onItemRemoved: (Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val items = mutableListOf<QueueItem>()
    private var currentIndex = 0

    data class QueueItem(
        val title: String,
        val subtitle: String
    )

    fun submit(newItems: List<QueueItem>, current: Int) {
        items.clear()
        items.addAll(newItems)
        currentIndex = current
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val item = items[position]
        holder.index.text = "${position + 1}"
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle

        // 当前播放项高亮
        val isCurrent = position == currentIndex
        holder.itemView.alpha = if (isCurrent) 1.0f else 0.7f
        holder.title.setTextColor(
            holder.itemView.context.getColor(
                if (isCurrent) R.color.accent else R.color.text_primary
            )
        )

        holder.itemView.setOnClickListener { onItemClicked(position) }
        holder.remove.setOnClickListener { onItemRemoved(position) }
    }

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.queue_index)
        val title: TextView = view.findViewById(R.id.queue_title)
        val subtitle: TextView = view.findViewById(R.id.queue_subtitle)
        val remove: ImageView = view.findViewById(R.id.queue_remove)
    }
}
