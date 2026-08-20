
package unicode.sinhala.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.Gravity.CENTER
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import unicode.sinhala.com.R
import android.util.TypedValue

class KeyboardButton : AppCompatTextView {
    private var isSpecial = false
    private var secondaryLabel: String? = null
    private val secondaryLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Long-press popup support
    var longPressListener: ((String) -> Unit)? = null
    private var popup: PopupWindow? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private val LONG_PRESS_DELAY_MS = 350L

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0)
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs, defStyleAttr)
    }

    var clickListener: (tag: String) -> Unit = { }

    private fun init(attrs: AttributeSet?, defStyleAttr: Int) {
        gravity = CENTER
        isClickable = true

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.KeyboardButton, defStyleAttr, 0)
            isSpecial = typedArray.getBoolean(R.styleable.KeyboardButton_isSpecial, false)
            typedArray.recycle()
        }

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.foreground, typedValue, true)
        setTextColor(typedValue.data)

        setOnTouchListener { view, event ->
            val secondary = secondaryLabel
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    longPressTriggered = false
                    // Schedule long press only if there's a secondary label and a listener
                    if (secondary != null && longPressListener != null) {
                        longPressHandler.postDelayed({
                            longPressTriggered = true
                            showPopup(secondary)
                        }, LONG_PRESS_DELAY_MS)
                    } else {
                        // Normal tap — commit immediately on down (existing behaviour)
                        val visible = text?.toString()?.takeIf { it.isNotEmpty() }
                        val rawTag = tag?.toString()?.takeIf { it.isNotEmpty() } ?: ""
                        val tagString = visible ?: convertTagToText(rawTag)
                        clickListener.invoke(tagString)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacksAndMessages(null)
                    view.isPressed = false
                    if (longPressTriggered) {
                        // Finger lifted while popup was showing → commit secondary char
                        dismissPopup()
                        if (secondary != null) {
                            longPressListener?.invoke(secondary)
                        }
                    } else if (secondary != null && longPressListener != null) {
                        // Short tap (released before long-press threshold) → commit primary
                        val visible = text?.toString()?.takeIf { it.isNotEmpty() }
                        val rawTag = tag?.toString()?.takeIf { it.isNotEmpty() } ?: ""
                        val tagString = visible ?: convertTagToText(rawTag)
                        clickListener.invoke(tagString)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacksAndMessages(null)
                    view.isPressed = false
                    if (longPressTriggered) {
                        dismissPopup()
                    }
                    true
                }
                else -> false
            }
        }

        if (background == null) {
            background = AppCompatResources.getDrawable(context, R.drawable.key_background)
        }

        if (isSpecial) {
            background = AppCompatResources.getDrawable(context, R.drawable.key_background_special)
        }

        secondaryLabelPaint.color = currentTextColor
        secondaryLabelPaint.alpha = 150
        secondaryLabelPaint.textAlign = Paint.Align.RIGHT
        secondaryLabelPaint.textSize = textSize * 0.5f
    }

    private fun showPopup(label: String) {
        dismissPopup() // dismiss any existing popup first

        val density = resources.displayMetrics.density

        // Build popup content view programmatically
        val tv = TextView(context).apply {
            text = label
            textSize = this@KeyboardButton.textSize * 1.4f  // bigger than key text
            gravity = CENTER
            setTextColor(this@KeyboardButton.currentTextColor)
            setPadding(
                (12 * density).toInt(), (6 * density).toInt(),
                (12 * density).toInt(), (6 * density).toInt()
            )
            background = AppCompatResources.getDrawable(
                context, R.drawable.key_background_pressed
            )
        }

        val pw = PopupWindow(
            tv,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        )
        pw.isOutsideTouchable = false
        pw.isTouchable = false  // let touches fall through to the key

        // Measure the popup so we can position it centred above the key
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupW = tv.measuredWidth
        val popupH = tv.measuredHeight

        val loc = IntArray(2)
        getLocationInWindow(loc)
        val xOff = loc[0] + (width - popupW) / 2
        val yOff = loc[1] - popupH - (4 * density).toInt()

        pw.showAtLocation(this, Gravity.NO_GRAVITY, xOff, yOff)
        popup = pw
    }

    private fun dismissPopup() {
        try { popup?.dismiss() } catch (_: Exception) {}
        popup = null
    }

    private fun convertTagToText(raw: String): String {
        if (raw.isEmpty()) return ""
        val digitsOnly = raw.all { it.isDigit() }
        if (digitsOnly) {
            return try {
                val code = raw.toInt()
                when (code) {
                    32 -> " "
                    else -> code.toChar().toString()
                }
            } catch (t: Throwable) {
                raw
            }
        }
        return raw
    }

    fun setSecondaryLabel(label: String?) {
        secondaryLabel = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        secondaryLabel?.let {
            secondaryLabelPaint.color = currentTextColor
            secondaryLabelPaint.alpha = 150
            secondaryLabelPaint.textSize = textSize * 0.5f

            val padding = 8f
            canvas.drawText(it, width.toFloat() - padding, secondaryLabelPaint.textSize + padding, secondaryLabelPaint)
        }
    }
}
