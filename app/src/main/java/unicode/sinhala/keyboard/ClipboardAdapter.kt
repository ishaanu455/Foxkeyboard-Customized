package unicode.sinhala.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import unicode.sinhala.com.R

/**
 * Renders the clipboard history as a 2-column "Clips" style grid, split into
 * a "Pinned" section (if any pinned clips exist) followed by an "Others"
 * section - mirroring the Pinned/Others clips screen design.
 */
class ClipboardAdapter(
    private val actions: Actions
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Actions {
        fun onClipTap(item: ClipItem)
        fun onClipPin(item: ClipItem)
        fun onClipShare(item: ClipItem)
        fun onClipDelete(item: ClipItem)
    }

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Clip(val item: ClipItem) : Row()
    }

    private val rows = ArrayList<Row>()

    /** Tracks which clip currently has its action row expanded (long-press), by clip id. */
    private var expandedId: Long? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CLIP = 1
    }

    /** Splits [newItems] into Pinned / Others sections, each with its own header. */
    fun submit(newItems: List<ClipItem>) {
        rows.clear()
        val pinned = newItems.filter { it.pinned }
        val others = newItems.filterNot { it.pinned }

        if (pinned.isNotEmpty()) {
            rows.add(Row.Header("pinned"))
            pinned.forEach { rows.add(Row.Clip(it)) }
        }
        if (others.isNotEmpty()) {
            rows.add(Row.Header("others"))
            others.forEach { rows.add(Row.Clip(it)) }
        }

        if (rows.none { it is Row.Clip && it.item.id == expandedId }) expandedId = null
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_CLIP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_clipboard_section_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_clipboard_clip, parent, false)
            ClipViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> bindHeader(holder as HeaderViewHolder, row)
            is Row.Clip -> bindClip(holder as ClipViewHolder, row.item)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, row: Row.Header) {
        val resId = if (row.title == "pinned") R.string.clipboard_section_pinned else R.string.clipboard_section_others
        holder.title.text = holder.itemView.context.getString(resId)

        // Section headers always span both grid columns.
        val lp = holder.itemView.layoutParams
        if (lp is StaggeredGridLayoutManager.LayoutParams) {
            lp.isFullSpan = true
        }
    }

    private fun bindClip(holder: ClipViewHolder, item: ClipItem) {
        holder.text.text = item.text
        holder.actions.isVisible = item.id == expandedId
        holder.pinBadge.isVisible = item.pinned
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

    override fun getItemCount(): Int = rows.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.section_header_title)
    }

    class ClipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.clip_text)
        val actions: View = itemView.findViewById(R.id.clip_actions)
        val pin: ImageView = itemView.findViewById(R.id.clip_pin)
        val share: ImageView = itemView.findViewById(R.id.clip_share)
        val delete: ImageView = itemView.findViewById(R.id.clip_delete)
        val pinBadge: ImageView = itemView.findViewById(R.id.clip_pin_badge)
    }
}
