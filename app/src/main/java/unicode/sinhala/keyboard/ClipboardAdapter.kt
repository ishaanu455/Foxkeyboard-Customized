package unicode.sinhala.keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
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

    /** True while the multi-select delete flow (entered via the top-bar trash icon) is
     *  active - taps toggle a clip's selected state instead of pasting it. */
    private var selectionMode = false
    private val selectedIds = mutableSetOf<Long>()

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
        selectedIds.retainAll(newItems.map { it.id }.toSet())
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
        // The pin/share/delete row and the selection checkmark are mutually exclusive -
        // only one indicator makes sense on a card at a time.
        holder.actions.isVisible = !selectionMode && item.id == expandedId
        holder.pinBadge.isVisible = item.pinned && !selectionMode
        holder.pin.alpha = if (item.pinned) 1f else 0.5f

        // Selection checkbox: visible on EVERY clip while selection mode is active (not
        // just the ones already picked) so it's obvious up front what can be selected,
        // rather than the badge only appearing once a clip has already been tapped.
        holder.selectCheck.isVisible = selectionMode
        if (selectionMode) {
            val isSelected = item.id in selectedIds
            holder.selectCheck.background = AppCompatResources.getDrawable(
                holder.itemView.context,
                if (isSelected) R.drawable.bg_clip_purple_circle else R.drawable.bg_clip_select_empty
            )
            holder.selectCheck.setImageResource(if (isSelected) R.drawable.ic_check else 0)
            // The checkmark sits on a solid purple circle, so force it white for
            // contrast regardless of light/dark theme (ic_check's own fill color
            // follows the theme, which would blend into the purple in dark mode).
            holder.selectCheck.imageTintList = if (isSelected) ColorStateList.valueOf(Color.WHITE) else null
        }

        if (selectionMode) {
            // While selecting, a tap toggles the clip instead of pasting it, and
            // long-press (which would normally expand pin/share/delete) is disabled
            // so it can't be confused with selection.
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    if (!selectedIds.remove(item.id)) selectedIds.add(item.id)
                    notifyItemChanged(pos)
                }
            }
            holder.itemView.setOnLongClickListener { true }
        } else {
            holder.itemView.setOnClickListener { actions.onClipTap(item) }
            holder.itemView.setOnLongClickListener {
                val previousExpandedId = expandedId
                expandedId = if (expandedId == item.id) null else item.id

                // Re-bind only the rows whose expanded state actually changed, instead of a
                // full notifyDataSetChanged(). With StaggeredGridLayoutManager, a full rebind
                // doesn't reliably reflow item heights, which is why toggling used to look like
                // it "stuck" (long-pressing again didn't visibly collapse the actions row).
                if (previousExpandedId != null) {
                    val oldPos = rows.indexOfFirst { it is Row.Clip && it.item.id == previousExpandedId }
                    if (oldPos >= 0) notifyItemChanged(oldPos)
                }
                val newPos = rows.indexOfFirst { it is Row.Clip && it.item.id == expandedId }
                if (newPos >= 0) notifyItemChanged(newPos)
                true
            }
        }
        holder.pin.setOnClickListener { actions.onClipPin(item) }
        holder.share.setOnClickListener { actions.onClipShare(item) }
        holder.delete.setOnClickListener { actions.onClipDelete(item) }
    }

    /** Collapses whichever clip's pin/share/delete row is currently expanded, if any.
     *  Called when the clipboard panel closes and when the user taps empty space in
     *  the list, so an expanded row never lingers across opens or "leaks" onto a tap
     *  that lands away from it. */
    fun collapse() {
        val id = expandedId ?: return
        expandedId = null
        val pos = rows.indexOfFirst { it is Row.Clip && it.item.id == id }
        if (pos >= 0) notifyItemChanged(pos)
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun hasSelection(): Boolean = selectedIds.isNotEmpty()

    /** The clip ids currently checked for deletion. */
    fun selectedIds(): Set<Long> = selectedIds.toSet()

    /** Enters the multi-select delete flow - lets the user pick which clips (pinned
     *  or not) to remove, instead of the old single tap wiping every unpinned clip
     *  with no way to double-check what's being deleted. */
    fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        collapse()
        notifyDataSetChanged()
    }

    /** Leaves selection mode, whether triggered by a completed delete or a cancel. */
    fun exitSelectionMode() {
        if (!selectionMode && selectedIds.isEmpty()) return
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
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
        val selectCheck: ImageView = itemView.findViewById(R.id.clip_select_check)
    }
}
