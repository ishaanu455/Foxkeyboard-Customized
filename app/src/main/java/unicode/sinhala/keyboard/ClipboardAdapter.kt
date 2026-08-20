package unicode.sinhala.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import unicode.sinhala.com.R

class ClipboardAdapter(
    private val actions: Actions
) : RecyclerView.Adapter<ClipboardAdapter.ClipViewHolder>() {

    interface Actions {
        fun onClipTap(item: ClipItem)
        fun onClipPin(item: ClipItem)
        fun onClipShare(item: ClipItem)
        fun onClipDelete(item: ClipItem)
    }

    private val items = ArrayList<ClipItem>()

    /** Tracks which row currently has its action row expanded (long-press), by clip id. */
    private var expandedId: Long? = null

    fun submit(newItems: List<ClipItem>) {
        items.clear()
        items.addAll(newItems)
        if (items.none { it.id == expandedId }) expandedId = null
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clipboard_clip, parent, false)
        return ClipViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.text
        holder.actions.isVisible = item.id == expandedId
        holder.pin.setImageResource(R.drawable.ic_clip_pin)
        holder.pin.alpha = if (item.pinned) 1f else 0.5f

        holder.itemView.setOnClickListener { actions.onClipTap(item) }
        holder.itemView.setOnLongClickListener {
            expandedId = if (expandedId == item.id) null else item.id
            notifyDataSetChanged()
            true
        }
        holder.pin.setOnClickListener { actions.onClipPin(item) }
        holder.share.setOnClickListener { actions.onClipShare(item) }
        holder.delete.setOnClickListener { actions.onClipDelete(item) }
    }

    override fun getItemCount(): Int = items.size

    class ClipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.clip_text)
        val actions: View = itemView.findViewById(R.id.clip_actions)
        val pin: ImageView = itemView.findViewById(R.id.clip_pin)
        val share: ImageView = itemView.findViewById(R.id.clip_share)
        val delete: ImageView = itemView.findViewById(R.id.clip_delete)
    }
}
