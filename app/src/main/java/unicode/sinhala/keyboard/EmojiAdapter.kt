package unicode.sinhala.keyboard

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import coil.target.Target

class EmojiAdapter(
    private val context: Context,
    private val clickListener: KeyboardView.ClickListener,
    private val darkTheme: Boolean,
    emojis: List<String>,
    private val textSize: Int,
    private val emojiStyle: EmojiStyle = EmojiStyle.SYSTEM
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    private val items = ArrayList<String>(emojis)

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private fun sp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val pad = dp(8f)

        if (emojiStyle == EmojiStyle.SYSTEM || emojiStyle == EmojiStyle.CUSTOM) {
            val tv = TextView(context)
            tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            tv.gravity = Gravity.CENTER
            tv.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
            tv.setPadding(pad, pad, pad, pad)
            tv.includeFontPadding = false
            tv.setTextColor(if (darkTheme) Color.WHITE else Color.BLACK)
            if (emojiStyle == EmojiStyle.CUSTOM) {
                // Falls back to the default system typeface (still fine for emoji) if the
                // stored file is missing or Android can't parse its color-glyph table.
                CustomFontManager.loadTypeface(context)?.let { tv.typeface = it }
            }
            return EmojiViewHolder(tv, null, null)
        }

        // Twemoji mode: stack a system-emoji TextView (always present, used as an instant
        // fallback) underneath an ImageView that shows the downloaded Twemoji artwork once
        // it's loaded. If the image fails (e.g. no network and not yet cached), the system
        // glyph underneath just stays visible instead of leaving a blank cell.
        val glyphSize = sp(textSize.toFloat()) + dp(4f)
        val cellSize = glyphSize + pad * 2

        val frame = FrameLayout(context)
        frame.layoutParams = RecyclerView.LayoutParams(cellSize, cellSize)
        frame.setPadding(pad, pad, pad, pad)

        val fallbackText = TextView(context)
        fallbackText.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        fallbackText.gravity = Gravity.CENTER
        fallbackText.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        fallbackText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
        fallbackText.includeFontPadding = false
        fallbackText.setTextColor(if (darkTheme) Color.WHITE else Color.BLACK)
        frame.addView(fallbackText)

        val image = ImageView(context)
        image.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.visibility = View.INVISIBLE
        frame.addView(image)

        return EmojiViewHolder(null, fallbackText, image)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = items[position]

        if (emojiStyle == EmojiStyle.SYSTEM || emojiStyle == EmojiStyle.CUSTOM) {
            holder.textView?.text = emoji
            holder.textView?.setOnClickListener { clickListener.emojiClick(emoji) }
            return
        }

        holder.fallbackText?.text = emoji
        holder.imageView?.visibility = View.INVISIBLE
        holder.imageView?.setImageDrawable(null)
        // Cancel any still-in-flight load from a previous (recycled) binding of this view.
        holder.currentDisposable?.dispose()

        val request = ImageRequest.Builder(context)
            .data(TwemojiUtil.urlFor(emoji))
            .target(object : Target {
                override fun onStart(placeholder: Drawable?) {}

                override fun onSuccess(result: Drawable) {
                    holder.imageView?.setImageDrawable(result)
                    holder.imageView?.visibility = View.VISIBLE
                }

                override fun onError(error: Drawable?) {
                    // Leave the system-glyph fallback (already showing) visible.
                }
            })
            .build()
        holder.currentDisposable = context.imageLoader.enqueue(request)

        holder.itemView.setOnClickListener { clickListener.emojiClick(emoji) }
    }

    override fun onViewRecycled(holder: EmojiViewHolder) {
        super.onViewRecycled(holder)
        holder.currentDisposable?.dispose()
        holder.currentDisposable = null
    }

    override fun getItemCount(): Int = items.size

    fun updateEmojis(newEmojis: List<String>) {
        items.clear()
        items.addAll(newEmojis)
        notifyDataSetChanged()
    }

    class EmojiViewHolder(
        val textView: TextView?,
        val fallbackText: TextView?,
        val imageView: ImageView?
    ) : RecyclerView.ViewHolder(textView ?: (fallbackText!!.parent as View)) {
        var currentDisposable: Disposable? = null
    }
}
