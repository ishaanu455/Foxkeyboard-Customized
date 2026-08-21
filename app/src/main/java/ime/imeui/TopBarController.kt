package ime.imeui

import android.graphics.Color
import android.view.View
import android.widget.TextView

class TopBarController(
    private val suggestionContainer: View?,
    private val emojiButton: View?,
    private val darkTheme: Boolean = false,
    private val clipboardButton: View? = null,
    // Whether the clipboard icon should be shown at all when suggestions AREN'T
    // active - i.e. the user's Settings > clipboard toggle. Read lazily (not once at
    // construction time) since the pref can change while the keyboard stays open.
    private val isClipboardEnabled: () -> Boolean = { true }
) {

    private fun applyColors(tv: TextView?) {
        if (tv == null) return
        if (darkTheme) {
            tv.setTextColor(Color.WHITE)
        } else {
            tv.setTextColor(Color.BLACK)
        }
    }

    fun showNormal() {
        suggestionContainer?.visibility = View.GONE
        emojiButton?.visibility = View.VISIBLE
        // Restore the clipboard icon only if the feature is actually enabled in
        // Settings - showSuggestions() hides it unconditionally while a suggestion
        // chip is up, so coming back to "normal" must respect the user's toggle
        // rather than always forcing it visible again.
        clipboardButton?.visibility = if (isClipboardEnabled()) View.VISIBLE else View.GONE
    }

    fun showSuggestions(suggestions: List<String>, suggestionTextViews: List<TextView>, onClick: (String) -> Unit) {
        emojiButton?.visibility = View.GONE
        clipboardButton?.visibility = View.GONE
        suggestionContainer?.visibility = View.VISIBLE
        for (i in 0 until 3) {
            val tv = suggestionTextViews.getOrNull(i)
            val text = suggestions.getOrNull(i) ?: ""
            if (tv != null) {
                applyColors(tv)
                tv.text = text
                tv.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                tv.setOnClickListener { onClick(text) }
            }
        }
    }
}