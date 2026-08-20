package unicode.sinhala.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import unicode.sinhala.com.R
import unicode.sinhala.com.databinding.KeyboardLayoutBinding
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class KeyboardView(
    context: Context,
    private val clickListener: ClickListener,
    private val swipeListener: SwipeListener,
    private val rowHeight: Int,
    private val darkTheme: Boolean,
    keyBorders: Boolean,
    private val swipeToErase: Boolean,
    private val swipeToMoveCursor: Boolean,
    private val textSize: Int,
    private var showRecentEmojiRow: Boolean = false,
    private var showNumberRow: Boolean = true,
    private val emojiStyle: EmojiStyle = EmojiStyle.SYSTEM,
    private var clipboardEnabled: Boolean = true
) : LinearLayout(context) {

    interface ClickListener {
        fun letterOrSymbolClick(tag: String)
        fun emojiClick(tag: String)
        fun numberClick(tag: String)
        fun functionClick(type: Function)
        fun specialClick(tag: String)
        fun longPressSecondaryClick(char: String)
        fun clipboardPasteClick(text: String)
        fun clipboardPinClick(item: ClipItem)
        fun clipboardShareClick(item: ClipItem)
        fun clipboardDeleteClick(item: ClipItem)
        fun clipboardClearClick()
    }

    interface SwipeListener {
        fun eraseDo()
        fun eraseUndo()
        fun eraseDone()
        fun moveRight()
        fun moveLeft()
    }

    private var lastBackspaceDownTime = 0L

    var keyboardVisible = false

    private lateinit var binding: KeyboardLayoutBinding

    val viewBlank1: View get() = binding.blank1
    val viewBlank2: View get() = binding.blank2
    val buttonColon: KeyboardButton get() = binding.colonWijesekara
    val buttonActionShift: ImageView get() = binding.shift

    val buttonSpecialComma: KeyboardButton get() = binding.comma
    val buttonSpecialCommaWijesekara: KeyboardButton get() = binding.commaWijesekara
    val buttonActionAction: ImageView get() = binding.action

    private val backspaceRepeater = flow<Unit> {
        while (true) {
            val currentTimeMillis = System.currentTimeMillis()
            val timeSinceLastDown = currentTimeMillis - lastBackspaceDownTime
            delay(
                when {
                    timeSinceLastDown > 5000 -> 4L
                    timeSinceLastDown > 4000 -> 8L
                    timeSinceLastDown > 3000 -> 16L
                    timeSinceLastDown > 2000 -> 32L
                    timeSinceLastDown > 1000 -> 64L
                    timeSinceLastDown > 500 -> 128L
                    else -> 500L
                }
            )
            clickListener.functionClick(Function.BACKSPACE)
        }
    }
    private lateinit var backspaceRepeaterJob: Job
    private lateinit var recentEmojiAdapter: EmojiAdapter
    private lateinit var clipboardAdapter: ClipboardAdapter

    private var swipeStepStartX: Float = 0F
    private val swipeStepDistance: Float = resources.displayMetrics.widthPixels / 15f
    private var startIgnoreSwipe = false
    private var currentSwipeActionType = SwipeActionType.NONE

    private enum class SwipeActionType { ERASE, MOVE_CURSOR, NONE }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (swipeToErase || swipeToMoveCursor) {
            if (ev != null && ev.pointerCount > 1) startIgnoreSwipe = true
            when (ev?.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentSwipeActionType = SwipeActionType.NONE
                    swipeStepStartX = ev.x
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!startIgnoreSwipe) {
                        val distanceFromDownX: Float = swipeStepStartX - ev.x

                        if (swipeToErase && ev.y < rowHeight * 4 && distanceFromDownX > swipeStepDistance)
                            currentSwipeActionType = SwipeActionType.ERASE
                        else if (swipeToMoveCursor && ev.y >= rowHeight * 4 && (distanceFromDownX > swipeStepDistance || distanceFromDownX < -swipeStepDistance))
                            currentSwipeActionType = SwipeActionType.MOVE_CURSOR

                        return currentSwipeActionType != SwipeActionType.NONE
                    }
                }

                MotionEvent.ACTION_UP -> if (ev.pointerCount == 1) startIgnoreSwipe = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_MOVE -> {
                val swipeDistance = swipeStepStartX - event.x
                if (swipeDistance > swipeStepDistance) {
                    when (currentSwipeActionType) {
                        SwipeActionType.ERASE -> swipeListener.eraseDo()
                        SwipeActionType.MOVE_CURSOR -> swipeListener.moveLeft()
                        SwipeActionType.NONE -> {}
                    }
                    swipeStepStartX = event.x
                    return true // Swipe handled
                } else if (swipeDistance < -swipeStepDistance) {
                    when (currentSwipeActionType) {
                        SwipeActionType.ERASE -> swipeListener.eraseUndo()
                        SwipeActionType.MOVE_CURSOR -> swipeListener.moveRight()
                        SwipeActionType.NONE -> {}
                    }
                    swipeStepStartX = event.x
                    return true // Swipe handled
                }

            }

            MotionEvent.ACTION_UP -> {
                if (currentSwipeActionType != SwipeActionType.NONE) {
                    swipeListener.eraseDone()
                    if (event.pointerCount == 1) startIgnoreSwipe = false
                    return true // Consume the ACTION_UP that ends a swipe
                }

            }
        }

        return false
    }

    init {
        val style = when {
            !darkTheme && keyBorders -> R.style.Light
            !darkTheme && !keyBorders -> R.style.LightNoBorder
            darkTheme && !keyBorders -> R.style.NightNoBorder
            else -> R.style.Night
        }

        val contextThemeWrapper = ContextThemeWrapper(context, style)

        try {
            binding =
                KeyboardLayoutBinding.inflate(LayoutInflater.from(contextThemeWrapper), this, true)
        } catch (t: Throwable) {
            Log.e("KeyboardView", "Themed inflation failed, falling back to default inflater", t)
            try {

                val root =
                    LayoutInflater.from(context).inflate(R.layout.keyboard_layout, this, true)
                binding = KeyboardLayoutBinding.bind(root)
            } catch (fallbackT: Throwable) {

                Log.e(
                    "KeyboardView",
                    "FATAL: Default inflation also failed. The layout XML is likely invalid.",
                    fallbackT
                )
                throw RuntimeException("Failed to inflate keyboard layout.", fallbackT)
            }
        }

        try {
            // Number row: hide entirely when the user has toggled it off in Settings
            binding.keyRow1.visibility = if (showNumberRow) View.VISIBLE else View.GONE

            // Recently-used emoji quick row (horizontal strip above the keys)
            recentEmojiAdapter = EmojiAdapter(
                contextThemeWrapper,
                clickListener,
                darkTheme,
                EmojiData.emojis["Recent"] ?: emptyList(),
                textSize,
                emojiStyle
            )
            binding.recentEmojiRow.layoutManager =
                LinearLayoutManager(contextThemeWrapper, LinearLayoutManager.HORIZONTAL, false)
            binding.recentEmojiRow.adapter = recentEmojiAdapter
            binding.recentEmojiRow.layoutParams.height = rowHeight
            updateRecentEmojiRowVisibility()

            binding.keyRow1.layoutParams.height = rowHeight
            binding.keyRow2.layoutParams.height = rowHeight
            binding.keyRow3.layoutParams.height = rowHeight
            binding.keyRow4.layoutParams.height = rowHeight
            binding.keyRow5.layoutParams.height = rowHeight

            for (row in binding.keyboardRows.children)
                if (row is LinearLayout)
                    for (button in row.children)
                        if (button is KeyboardButton)
                            button.textSize = textSize.toFloat()

            // Calculate padding to ensure icon size scales with text size
            val density = resources.displayMetrics.density
            // Use fitCenter to allow icons to scale UP if padding is small
            binding.emojiView.btnBackspace.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.emojiView.btnAbc.scaleType = ImageView.ScaleType.FIT_CENTER

            // Target icon size based on text size (roughly matching text height)
            val targetIconSize = textSize * density

            val padding = max(0, ((rowHeight - targetIconSize) / 2).toInt())

            binding.emojiView.btnBackspace.setPadding(padding, padding, padding, padding)
            binding.emojiView.btnAbc.setPadding(padding, padding, padding, padding)


            binding.n0.clickListener = { clickListener.numberClick(it) }
            binding.n1.clickListener = { clickListener.numberClick(it) }
            binding.n2.clickListener = { clickListener.numberClick(it) }
            binding.n3.clickListener = { clickListener.numberClick(it) }
            binding.n4.clickListener = { clickListener.numberClick(it) }
            binding.n5.clickListener = { clickListener.numberClick(it) }
            binding.n6.clickListener = { clickListener.numberClick(it) }
            binding.n7.clickListener = { clickListener.numberClick(it) }
            binding.n8.clickListener = { clickListener.numberClick(it) }
            binding.n9.clickListener = { clickListener.numberClick(it) }

            binding.lA.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lB.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lC.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lD.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lE.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lF.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lG.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lH.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lI.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lJ.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lK.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lL.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lM.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lN.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lO.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lP.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lQ.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lR.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lS.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lT.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lU.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lV.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lW.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lX.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lY.clickListener = { clickListener.letterOrSymbolClick(it) }
            binding.lZ.clickListener = { clickListener.letterOrSymbolClick(it) }

            // Wire long-press secondary char listeners for all letter keys
            val letterButtons = listOf(
                binding.lA, binding.lB, binding.lC, binding.lD, binding.lE,
                binding.lF, binding.lG, binding.lH, binding.lI, binding.lJ,
                binding.lK, binding.lL, binding.lM, binding.lN, binding.lO,
                binding.lP, binding.lQ, binding.lR, binding.lS, binding.lT,
                binding.lU, binding.lV, binding.lW, binding.lX, binding.lY,
                binding.lZ
            )
            for (btn in letterButtons) {
                btn.longPressListener = { clickListener.longPressSecondaryClick(it) }
            }

            binding.symbol1.clickListener = { clickListener.letterOrSymbolClick(it) }

            binding.colonWijesekara.clickListener = { clickListener.specialClick(it) }

            binding.comma.clickListener = { clickListener.specialClick(it) }
            binding.commaWijesekara.clickListener = { clickListener.specialClick(it) }
            binding.dot.clickListener = { clickListener.specialClick(it) }


            if (binding.space.tag == null || binding.space.tag.toString().isEmpty()) {
                binding.space.tag = " "
            }
            binding.space.setOnClickListener { v ->
                val tagStr = (v.tag as? String)?.takeIf { it.isNotEmpty() } ?: " "
                clickListener.specialClick(tagStr)
            }
            binding.space.setOnLongClickListener {
                clickListener.functionClick(Function.IME)
                true
            }

            binding.lang.setOnClickListener { clickListener.functionClick(Function.LANG) }
            binding.panel.clickListener = { clickListener.functionClick(Function.PANEL) }

            val fastTouchListener = View.OnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        when (v.id) {
                            R.id.action -> clickListener.functionClick(Function.ACTION)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        true
                    }

                    else -> false
                }
            }

            binding.shift.setOnClickListener { clickListener.functionClick(Function.SHIFT) }
            binding.action.setOnTouchListener(fastTouchListener)

            val backspaceTouchListener = View.OnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.background = AppCompatResources.getDrawable(
                            contextThemeWrapper,
                            R.drawable.key_background_pressed
                        )
                        clickListener.functionClick(Function.BACKSPACE)
                        lastBackspaceDownTime = System.currentTimeMillis()
                        v.performClick()
                        backspaceRepeaterJob =
                            backspaceRepeater.launchIn(CoroutineScope(Dispatchers.IO))
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.background = AppCompatResources.getDrawable(
                            contextThemeWrapper,
                            R.drawable.key_background
                        )
                        backspaceRepeaterJob.cancel()
                    }
                }
                true
            }

            binding.backspace.setOnTouchListener(backspaceTouchListener)
            binding.emojiView.btnBackspace.setOnTouchListener(backspaceTouchListener)

            // Emoji Logic
            binding.emojiView.root.layoutParams.height = rowHeight * 5

            binding.emojiView.emojiBottomBar.layoutParams.height = rowHeight

            val emojiCategories = binding.emojiView.emojiCategories
            val emojiGrid = binding.emojiView.emojiGrid
            val emojiCategoriesScroll = binding.emojiView.emojiCategoriesScroll

            val emojiAdapter = EmojiAdapter(
                contextThemeWrapper,
                clickListener,
                darkTheme,
                EmojiData.emojis["Recent"] ?: emptyList(),
                textSize,
                emojiStyle
            )
            emojiGrid.layoutManager = GridLayoutManager(context, 8)
            emojiGrid.adapter = emojiAdapter

            val categoryClickListener = View.OnClickListener { v ->
                val category = v.tag as String
                emojiAdapter.updateEmojis(EmojiData.emojis[category] ?: emptyList())
                for (child in emojiCategories.children) {
                    child.background = null
                }
                v.background = AppCompatResources.getDrawable(
                    contextThemeWrapper,
                    R.drawable.key_background_pressed
                )
            }

            for (category in EmojiData.categories) {
                val categoryView = TextView(contextThemeWrapper)
                val emojiIcon = if (category == "Recent") "🕒" else (EmojiData.emojis[category]?.first() ?: "😀")
                categoryView.text = emojiIcon
                categoryView.textSize = textSize.toFloat()
                categoryView.gravity = Gravity.CENTER
                categoryView.setTextColor(if (darkTheme) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                categoryView.layoutParams =
                    LinearLayout.LayoutParams(rowHeight, LayoutParams.MATCH_PARENT)
                categoryView.tag = category
                categoryView.setOnClickListener(categoryClickListener)
                emojiCategories.addView(categoryView)
            }

            // Click the first category (Recent) to load it by default
            (emojiCategories.getChildAt(0) as? TextView)?.performClick()

            fun toggleEmojiView(visible: Boolean) {
                binding.keyboardRows.visibility = if (visible) View.GONE else View.VISIBLE
                binding.emojiView.root.visibility = if (visible) View.VISIBLE else View.GONE
                if (visible) binding.clipboardView.root.visibility = View.GONE
                binding.btnEmoji.setImageResource(if (visible) R.drawable.ic_keyboard_arrow_left else R.drawable.ic_emoji)
                if (isClipboardPanelOpen) binding.btnClipboard.setImageResource(R.drawable.ic_clipboard)

                // Hide the quick "Recent" strip while the full picker (which has its own
                // Recent tab) is open; restore it per the user's Settings choice on close.
                isEmojiPanelOpen = visible
                if (visible) isClipboardPanelOpen = false
                updateRecentEmojiRowVisibility()

                // If showing emoji view, refresh Recent category as it might have changed
                if (visible) {
                     val firstChild = emojiCategories.getChildAt(0) as? TextView
                     // Only refresh if the "Recent" tab is currently selected
                     if (firstChild?.background != null) {
                         emojiAdapter.updateEmojis(EmojiData.emojis["Recent"] ?: emptyList())
                     }
                }
            }

            binding.btnEmoji.setOnClickListener { toggleEmojiView(!isEmojiPanelOpen) }

            binding.emojiView.btnAbc.setOnClickListener { toggleEmojiView(false) }

            // --- Clipboard panel logic ---
            binding.btnClipboard.isVisible = clipboardEnabled

            binding.clipboardView.root.layoutParams.height = rowHeight * 5
            binding.clipboardView.clipboardBottomBar.layoutParams.height = rowHeight

            clipboardAdapter = ClipboardAdapter(object : ClipboardAdapter.Actions {
                override fun onClipTap(item: ClipItem) = clickListener.clipboardPasteClick(item.text)
                override fun onClipPin(item: ClipItem) = clickListener.clipboardPinClick(item)
                override fun onClipShare(item: ClipItem) = clickListener.clipboardShareClick(item)
                override fun onClipDelete(item: ClipItem) = clickListener.clipboardDeleteClick(item)
            })
            binding.clipboardView.clipboardList.layoutManager = LinearLayoutManager(contextThemeWrapper)
            binding.clipboardView.clipboardList.adapter = clipboardAdapter

            binding.clipboardView.clipClearAll.setOnClickListener { clickListener.clipboardClearClick() }

            fun toggleClipboardView(visible: Boolean) {
                binding.keyboardRows.visibility = if (visible) View.GONE else View.VISIBLE
                binding.clipboardView.root.visibility = if (visible) View.VISIBLE else View.GONE
                if (visible) binding.emojiView.root.visibility = View.GONE
                binding.btnClipboard.setImageResource(if (visible) R.drawable.ic_keyboard_arrow_left else R.drawable.ic_clipboard)
                if (isEmojiPanelOpen) binding.btnEmoji.setImageResource(R.drawable.ic_emoji)

                isClipboardPanelOpen = visible
                if (visible) isEmojiPanelOpen = false
                updateRecentEmojiRowVisibility()
                if (visible) refreshClipboardList()
            }

            binding.btnClipboard.setOnClickListener { toggleClipboardView(!isClipboardPanelOpen) }
            binding.clipboardView.btnClipboardAbc.setOnClickListener { toggleClipboardView(false) }

            this.closeClipboardPanelFn = { toggleClipboardView(false) }
        } catch (t: Throwable) {
            Log.e("KeyboardView", "Error during KeyboardView init configuration", t)

        }

        // Programmatic creation of suggestion TextViews removed - relies on XML include.
    }

    fun setLetterKeys(keySet: Map<String, String>) {
        binding.lA.text = keySet["a"]
        binding.lB.text = keySet["b"]
        binding.lC.text = keySet["c"]
        binding.lD.text = keySet["d"]
        binding.lE.text = keySet["e"]
        binding.lF.text = keySet["f"]
        binding.lG.text = keySet["g"]
        binding.lH.text = keySet["h"]
        binding.lI.text = keySet["i"]
        binding.lJ.text = keySet["j"]
        binding.lK.text = keySet["k"]
        binding.lL.text = keySet["l"]
        binding.lM.text = keySet["m"]
        binding.lN.text = keySet["n"]
        binding.lO.text = keySet["o"]
        binding.lP.text = keySet["p"]
        binding.lQ.text = keySet["q"]
        binding.lR.text = keySet["r"]
        binding.lS.text = keySet["s"]
        binding.lT.text = keySet["t"]
        binding.lU.text = keySet["u"]
        binding.lV.text = keySet["v"]
        binding.lW.text = keySet["w"]
        binding.lX.text = keySet["x"]
        binding.lY.text = keySet["y"]
        binding.lZ.text = keySet["z"]
    }

    fun setSecondaryLabels(secondaryLabels: Map<String, String>?) {
        binding.lA.setSecondaryLabel(secondaryLabels?.get("a"))
        binding.lB.setSecondaryLabel(secondaryLabels?.get("b"))
        binding.lC.setSecondaryLabel(secondaryLabels?.get("c"))
        binding.lD.setSecondaryLabel(secondaryLabels?.get("d"))
        binding.lE.setSecondaryLabel(secondaryLabels?.get("e"))
        binding.lF.setSecondaryLabel(secondaryLabels?.get("f"))
        binding.lG.setSecondaryLabel(secondaryLabels?.get("g"))
        binding.lH.setSecondaryLabel(secondaryLabels?.get("h"))
        binding.lI.setSecondaryLabel(secondaryLabels?.get("i"))
        binding.lJ.setSecondaryLabel(secondaryLabels?.get("j"))
        binding.lK.setSecondaryLabel(secondaryLabels?.get("k"))
        binding.lL.setSecondaryLabel(secondaryLabels?.get("l"))
        binding.lM.setSecondaryLabel(secondaryLabels?.get("m"))
        binding.lN.setSecondaryLabel(secondaryLabels?.get("n"))
        binding.lO.setSecondaryLabel(secondaryLabels?.get("o"))
        binding.lP.setSecondaryLabel(secondaryLabels?.get("p"))
        binding.lQ.setSecondaryLabel(secondaryLabels?.get("q"))
        binding.lR.setSecondaryLabel(secondaryLabels?.get("r"))
        binding.lS.setSecondaryLabel(secondaryLabels?.get("s"))
        binding.lT.setSecondaryLabel(secondaryLabels?.get("t"))
        binding.lU.setSecondaryLabel(secondaryLabels?.get("u"))
        binding.lV.setSecondaryLabel(secondaryLabels?.get("v"))
        binding.lW.setSecondaryLabel(secondaryLabels?.get("w"))
        binding.lX.setSecondaryLabel(secondaryLabels?.get("x"))
        binding.lY.setSecondaryLabel(secondaryLabels?.get("y"))
        binding.lZ.setSecondaryLabel(secondaryLabels?.get("z"))
    }

    // What each key actually commits on long-press. Pass null to clear (falls back
    // to the visible secondary label per key). Keyed by physical key position
    // (a-z), same as setSecondaryLabels/setLetterKeys.
    fun setLongPressChars(longPressChars: Map<String, String>?) {
        binding.lA.setLongPressChar(longPressChars?.get("a"))
        binding.lB.setLongPressChar(longPressChars?.get("b"))
        binding.lC.setLongPressChar(longPressChars?.get("c"))
        binding.lD.setLongPressChar(longPressChars?.get("d"))
        binding.lE.setLongPressChar(longPressChars?.get("e"))
        binding.lF.setLongPressChar(longPressChars?.get("f"))
        binding.lG.setLongPressChar(longPressChars?.get("g"))
        binding.lH.setLongPressChar(longPressChars?.get("h"))
        binding.lI.setLongPressChar(longPressChars?.get("i"))
        binding.lJ.setLongPressChar(longPressChars?.get("j"))
        binding.lK.setLongPressChar(longPressChars?.get("k"))
        binding.lL.setLongPressChar(longPressChars?.get("l"))
        binding.lM.setLongPressChar(longPressChars?.get("m"))
        binding.lN.setLongPressChar(longPressChars?.get("n"))
        binding.lO.setLongPressChar(longPressChars?.get("o"))
        binding.lP.setLongPressChar(longPressChars?.get("p"))
        binding.lQ.setLongPressChar(longPressChars?.get("q"))
        binding.lR.setLongPressChar(longPressChars?.get("r"))
        binding.lS.setLongPressChar(longPressChars?.get("s"))
        binding.lT.setLongPressChar(longPressChars?.get("t"))
        binding.lU.setLongPressChar(longPressChars?.get("u"))
        binding.lV.setLongPressChar(longPressChars?.get("v"))
        binding.lW.setLongPressChar(longPressChars?.get("w"))
        binding.lX.setLongPressChar(longPressChars?.get("x"))
        binding.lY.setLongPressChar(longPressChars?.get("y"))
        binding.lZ.setLongPressChar(longPressChars?.get("z"))
    }

    fun setNumberKeys(keyLabels: Map<String, String>) {
        binding.n1.text = keyLabels["1"]
        binding.n2.text = keyLabels["2"]
        binding.n3.text = keyLabels["3"]
        binding.n4.text = keyLabels["4"]
        binding.n5.text = keyLabels["5"]
        binding.n6.text = keyLabels["6"]
        binding.n7.text = keyLabels["7"]
        binding.n8.text = keyLabels["8"]
        binding.n9.text = keyLabels["9"]
        binding.n0.text = keyLabels["0"]
    }

    fun setSpecialKeys(keyLabels: Map<String, String>) {
        keyLabels[";"]?.let { binding.colonWijesekara.text = it }
        keyLabels[","]?.let { binding.commaWijesekara.text = it }
        keyLabels["."]?.let { binding.dot.text = it }
    }

    fun setLangIndicator(text: String) {

    }

    fun setLangIndicatorIcon(iconResId: Int) {
        binding.lang.setImageResource(iconResId)
    }

    // Expose top bar and suggestion views for IME to control
    val topBarView: LinearLayout get() = binding.topBar
    val emojiButtonView: ImageView get() = binding.btnEmoji
    // suggestionContainer in the binding is a generated binding object; use its root view when a View is expected
    val suggestionContainerView: View get() = binding.suggestionContainer.root
    fun getSuggestionTextViews(): List<TextView> {
        val list = ArrayList<TextView>()
        // Use the root view of the suggestion container binding to access children
        val suggestionContainerRoot = binding.suggestionContainer.root

        if (suggestionContainerRoot is ViewGroup) {
            // Iterate children and check tag to ensure correct order (0, 1, 2)
            for (child in suggestionContainerRoot.children) {
                if (child is TextView && child.tag?.toString()?.startsWith("suggest_") == true) {
                    list.add(child)
                }
            }
            // Sort the list based on the numeric part of the tag
            list.sortBy { it.tag.toString().substringAfter("_").toIntOrNull() ?: Int.MAX_VALUE }
        }
        return list
    }

    // --- Recent emoji row + number row toggles ---

    // True while the full emoji picker panel (with its own Recent/Smileys/... tabs) is open.
    // The quick "Recent" strip above the keys is redundant then, so we hide it while this is true.
    private var isEmojiPanelOpen = false

    // True while the clipboard history panel is open.
    private var isClipboardPanelOpen = false
    private var closeClipboardPanelFn: (() -> Unit)? = null

    private fun updateRecentEmojiRowVisibility() {
        val recent = EmojiData.emojis["Recent"] ?: emptyList()
        binding.recentEmojiRow.visibility =
            if (showRecentEmojiRow && recent.isNotEmpty() && !isEmojiPanelOpen && !isClipboardPanelOpen) View.VISIBLE else View.GONE
    }

    /** Re-reads clip history from [ClipboardData] and refreshes the open panel's list/empty state. */
    fun refreshClipboardList() {
        if (!::clipboardAdapter.isInitialized) return
        val items = ClipboardData.all()
        clipboardAdapter.submit(items)
        binding.clipboardView.clipboardEmpty.isVisible = items.isEmpty()
        binding.clipboardView.clipboardList.isVisible = items.isNotEmpty()
    }

    /** Closes the clipboard panel (e.g. right after a paste, or when the input field changes). */
    fun closeClipboardPanel() {
        closeClipboardPanelFn?.invoke()
    }

    /** Hot-toggle from Settings without recreating the whole KeyboardView. */
    fun setClipboardEnabled(enabled: Boolean) {
        clipboardEnabled = enabled
        binding.btnClipboard.isVisible = enabled
        if (!enabled && isClipboardPanelOpen) closeClipboardPanel()
    }

    /** Call after a new emoji is committed so the quick row reflects the latest "Recent" list. */
    fun refreshRecentEmojiRow() {
        if (!::recentEmojiAdapter.isInitialized) return
        recentEmojiAdapter.updateEmojis(EmojiData.emojis["Recent"] ?: emptyList())
        updateRecentEmojiRowVisibility()
    }

    /** Applies the latest Settings value without recreating the whole keyboard view. */
    fun setShowRecentEmojiRow(enabled: Boolean) {
        showRecentEmojiRow = enabled
        updateRecentEmojiRowVisibility()
    }

    /** Applies the latest Settings value without recreating the whole keyboard view. */
    fun setShowNumberRow(enabled: Boolean) {
        showNumberRow = enabled
        binding.keyRow1.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    /** Hot-update all row heights (called when height slider changes without keyboard recreate). */
    fun updateRowHeight(newRowHeight: Int) {
        binding.recentEmojiRow.layoutParams.height = newRowHeight
        binding.keyRow1.layoutParams.height = newRowHeight
        binding.keyRow2.layoutParams.height = newRowHeight
        binding.keyRow3.layoutParams.height = newRowHeight
        binding.keyRow4.layoutParams.height = newRowHeight
        binding.keyRow5.layoutParams.height = newRowHeight
        binding.emojiView.root.layoutParams.height = newRowHeight * 5
        binding.emojiView.emojiBottomBar.layoutParams.height = newRowHeight
        binding.clipboardView.root.layoutParams.height = newRowHeight * 5
        binding.clipboardView.clipboardBottomBar.layoutParams.height = newRowHeight
        requestLayout()
    }
}