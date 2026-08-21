package unicode.sinhala.keyboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import unicode.sinhala.com.R
import unicode.sinhala.keyboard.Maps.keyLabelsLettersEnglish
import unicode.sinhala.keyboard.Maps.keyLabelsLettersEnglishShifted
import unicode.sinhala.keyboard.Maps.keyLabelsNumbers
import unicode.sinhala.keyboard.Maps.keyLabelsSpecialEnglish
import unicode.sinhala.keyboard.Maps.keyLabelsLettersWijesekara
import unicode.sinhala.keyboard.Maps.keyLabelsLettersWijesekaraShifted
import unicode.sinhala.keyboard.Maps.keyLabelsNumbersWijesekara
import unicode.sinhala.keyboard.Maps.keyLabelsSpecialWijesekaraSinhala
import unicode.sinhala.keyboard.Maps.keyLabelsSpecialWijesekaraSinhalaShifted
import unicode.sinhala.keyboard.Maps.singlishMap
import unicode.sinhala.keyboard.swaraSignMap
import unicode.sinhala.keyboard.Maps.symbolsMap
import unicode.sinhala.keyboard.Maps.symbolsMapShifted
import ime.suggest.SuggestionEngine
import ime.suggest.LanguageDetector
import ime.imeui.DebouncedInputHandler
import ime.imeui.TopBarController
import android.widget.TextView
import androidx.compose.ui.semantics.text
import java.text.Normalizer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class InputMethodService : android.inputmethodservice.InputMethodService(),
    KeyboardView.ClickListener, KeyboardView.SwipeListener, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardLayout: KeyboardLayout

    private var caps = false
    private var shift = false


    private var keyboardSymbolsActive = false

    private var mComposing = ""
    private var tComposing = ""


    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)


    private var userInvokedInputMethodPicker = false

    private var suggestionEngine: SuggestionEngine? = null
    private var debouncer: DebouncedInputHandler? = null
    private var topBarController: TopBarController? = null
    private var suggestionTextViews: List<TextView> = emptyList()
    private var suggestionsEnabled = true

    // Lifecycle and SavedStateRegistry support
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        Log.d("IME", "onCreate called")
        suggestionEngine = SuggestionEngine(this)
        // Initialize engine asynchronously
        serviceScope.launch {
            suggestionEngine?.initializeIfNeeded()
        }
        debouncer = DebouncedInputHandler(serviceScope, 700L)

        EmojiData.loadRecentEmojis(this)
        ClipboardData.load(this)
        registerClipboardListener()
    }

    // --- System clipboard auto-capture ---
    // Registers with the platform ClipboardManager so any text the user copies anywhere
    // on the device (not just inside this IME) is captured into clip history automatically.
    private val systemClipboardManager: android.content.ClipboardManager by lazy {
        getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    // Set right before we ourselves commit a clip via paste, so the resulting primary-clip
    // change (some apps re-broadcast the committed text as the new clip) isn't re-captured.
    private var suppressNextClipCapture = false

    // Set right before we ourselves commit text while the emoji panel is open (i.e. an
    // emoji tap), so the resulting cursor-position change doesn't trip the "user touched
    // the text field" auto-close in onUpdateSelection - otherwise typing several emojis
    // in a row kept getting kicked back to the normal keyboard after every single one.
    private var suppressNextSelectionAutoClose = false

    private val clipChangedListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        if (suppressNextClipCapture) {
            suppressNextClipCapture = false
            return@OnPrimaryClipChangedListener
        }
        if (!Prefs.getClipboardEnabled(this)) return@OnPrimaryClipChangedListener
        try {
            val clip = systemClipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener
            ClipboardData.add(this, text)
            if (::keyboardView.isInitialized) keyboardView.refreshClipboardList()
        } catch (t: Throwable) {
            Log.e("IME", "clipboard capture failed", t)
        }
    }

    private fun registerClipboardListener() {
        try {
            systemClipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        } catch (t: Throwable) {
            Log.e("IME", "failed to register clipboard listener", t)
        }
    }


    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        try {
            systemClipboardManager.removePrimaryClipChangedListener(clipChangedListener)
        } catch (t: Throwable) {
            Log.e("IME", "failed to unregister clipboard listener", t)
        }
        super.onDestroy()
        serviceJob.cancel()
        Log.d("IME", "onDestroy called")
    }

    private fun commitWijesekaraChar(char: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""

        val composed = when (before + char) {
            "අැ" -> "ඇ"
            "අා" -> "ආ"
            "එ්" -> "ඒ"
            "එෙ" -> "ඓ"
            "ෙඑ" -> "ඓ"
            "ඔ්" -> "ඕ"
            "උ්" -> "ඌ"


            // Consonant + 'e' sign combinations


            else -> null
        }

        if (composed != null) {
            // If we have a composition, delete the previous character and commit the new one.
            ic.deleteSurroundingText(1, 0)
            ic.commitText(composed, 1)
        } else {
            // Otherwise, just commit the character the user typed.
            ic.commitText(char, 1)
        }
    }



    // Settings that require a full KeyboardView rebuild to take effect, since they
    // drive the inflate-time theme/style or per-button text size and have no
    // cheap hot-update path (unlike row height / number row / recent-emoji row,
    // which update in place). We snapshot what's currently applied and rebuild
    // whenever Settings has changed one of these since the keyboard was last shown.
    private var appliedDarkTheme = false
    private var appliedKeyBorders = true
    private var appliedTextSize = -1
    private var appliedEmojiStyle = EmojiStyle.SYSTEM

    private fun rememberAppliedAppearancePrefs() {
        appliedDarkTheme = Prefs.getDarkTheme(this)
        appliedKeyBorders = Prefs.getKeyBorders(this)
        appliedTextSize = Prefs.getTextSize(this)
        appliedEmojiStyle = Prefs.getEmojiStyle(this)
    }

    private fun appearancePrefsRequireRebuild(): Boolean {
        return appliedDarkTheme != Prefs.getDarkTheme(this) ||
            appliedKeyBorders != Prefs.getKeyBorders(this) ||
            appliedTextSize != Prefs.getTextSize(this) ||
            appliedEmojiStyle != Prefs.getEmojiStyle(this)
    }

    private fun buildKeyboardView(): KeyboardView {
        return KeyboardView(
            this,
            this,
            this,
            Prefs.getRowHeight(this),
            Prefs.getDarkTheme(this),
            Prefs.getKeyBorders(this),
            Prefs.getSwipeToErase(this),
            Prefs.getSwipeToMoveCursor(this),
            Prefs.getTextSize(this),
            Prefs.getShowRecentEmojiRow(this),
            Prefs.getShowNumberRow(this),
            Prefs.getEmojiStyle(this),
            Prefs.getClipboardEnabled(this)
        )
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (::keyboardView.isInitialized) return keyboardView

        try {
            keyboardView = buildKeyboardView()
            rememberAppliedAppearancePrefs()

            keyboardLayout = Prefs.getSelectedLayout(this)
            setKeyboardLayout(keyboardLayout)

            // Setup top bar controller with views from keyboardView and pass dark theme preference
            topBarController = TopBarController(
                keyboardView.suggestionContainerView,
                keyboardView.emojiButtonView,
                Prefs.getDarkTheme(this)
            )
            suggestionTextViews = keyboardView.getSuggestionTextViews()

            return keyboardView
        } catch (t: Throwable) {
            Log.e("IME", "Keyboard view creation failed, providing safe fallback view", t)

            // Provide a safe, minimal fallback view so IME does not crash.
            val fallback = View(this)
            try {
                // Attempt to set a sensible background color from theme attr if available, else default to white/black depending on night mode
                val typedValue = android.util.TypedValue()
                val theme = theme
                // First try app-specific fox_background (safe, non-colliding), then fall back to platform background attr
                var got = theme.resolveAttribute(R.attr.fox_background, typedValue, true)
                if (!got) {
                    try {
                        got = theme.resolveAttribute(android.R.attr.background, typedValue, true)
                    } catch (_: Exception) {
                        // some devices/themes might not expose android attr; ignore and fallback below
                        got = false
                    }
                }
                if (got) {
                    if (typedValue.resourceId != 0) {
                        fallback.setBackgroundResource(typedValue.resourceId)
                    } else {
                        try {
                            fallback.setBackgroundColor(typedValue.data)
                        } catch (e: Exception) {
                            fallback.setBackgroundColor(if (Prefs.getDarkTheme(this)) 0xFF263238.toInt() else 0xFFECEFF1.toInt())
                        }
                    }
                } else {
                    fallback.setBackgroundColor(if (Prefs.getDarkTheme(this)) 0xFF263238.toInt() else 0xFFECEFF1.toInt())
                }
            } catch (inner: Throwable) {
                Log.e("IME", "Failed to set fallback background", inner)
            }

            // Return fallback view to avoid crashing the IME.
            return fallback
        }
    }

     override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
         super.onStartInputView(info, restarting)
         lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
         Log.d("IME", "onStartInputView called restarting=$restarting info=")

         // Reset to the default key screen (lowercase, no clipboard/emoji panel) every
         // time the keyboard is (re)shown - whether it was fully closed and reopened, or
         // the user just switched focus to a different field while it stayed up.
         resetKeyboardState()

         val desired = Prefs.getSelectedLayout(this)
         if (!::keyboardView.isInitialized) {

             onCreateInputView()
         } else if (appearancePrefsRequireRebuild()) {
             // Dark theme / key borders / text size drive the inflate-time style and
             // per-button text size — no cheap hot-update path, so rebuild the view
             // when Settings has changed one of these since the keyboard was last shown.
             try {
                 keyboardView = buildKeyboardView()
                 rememberAppliedAppearancePrefs()
                 setInputView(keyboardView)
                 topBarController = TopBarController(
                     keyboardView.suggestionContainerView,
                     keyboardView.emojiButtonView,
                     Prefs.getDarkTheme(this)
                 )
                 suggestionTextViews = keyboardView.getSuggestionTextViews()
             } catch (t: Throwable) {
                 Log.e("IME", "Failed to rebuild keyboard view for changed appearance settings", t)
             }
         }

        // Re-apply latest toggle/value settings each time the keyboard is shown,
        // so Settings changes take effect without restarting the app.
        keyboardView.setShowRecentEmojiRow(Prefs.getShowRecentEmojiRow(this))
        keyboardView.setShowNumberRow(Prefs.getShowNumberRow(this))
        keyboardView.updateRowHeight(Prefs.getRowHeight(this))
        keyboardView.setClipboardEnabled(Prefs.getClipboardEnabled(this))
        // Pick up any emoji usage from the previous session — kept out of the live
        // typing session (see emojiClick) so the row doesn't reorder under the user's
        // finger while they're using it.
        keyboardView.refreshRecentEmojiRow()

        if (userInvokedInputMethodPicker) {

            userInvokedInputMethodPicker = false
            Log.d("IME", "Skipping automatic keyboard layout change after input method picker")
        } else {
            setKeyboardLayout(desired)
        }


        try {
            updateKeyboard()
        } catch (t: Throwable) {
            Log.e("IME", "updateKeyboard failed in onStartInputView", t)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d("IME", "onStartInput called restarting=$restarting")

        if (currentInputConnection == null && restarting) {
            resetKeyboardState()
        }


        try {
            updateKeyboard()
        } catch (t: Throwable) {
            Log.e("IME", "updateKeyboard failed in onStartInput", t)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        Log.d("IME", "onFinishInputView called finishingInput=$finishingInput")

        if (finishingInput) {
            resetKeyboardState()
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        // The field's text/cursor can change for reasons that never go through our own
        // key handlers - e.g. the host app clears the box after its own Send button is
        // tapped, or the cursor is moved by tapping elsewhere in the text. Without this,
        // whatever suggestion was showing beforehand stays stuck on screen indefinitely.
        // Re-derive the token at the new cursor position and refresh/hide the suggestion
        // bar to match what's actually there now.
        try {
            val textBefore = currentInputConnection
                ?.getTextBeforeCursor(50, 0)
                ?.toString() ?: ""
            val token = textBefore.takeLastWhile { !it.isWhitespace() }
            if (token.isEmpty()) {
                topBarController?.showNormal()
            } else {
                requestSuggestionsForToken(token)
            }
        } catch (_: Throwable) {}

        // The cursor/selection can only change like this while a panel is open if the
        // user tapped directly in the app's text field (our own key clicks don't move
        // the cursor via touch) - EXCEPT for an emoji tap, which also commits text while
        // the emoji panel is open but should keep the panel open so several emojis can
        // be typed in a row. That case sets suppressNextSelectionAutoClose beforehand.
        if (suppressNextSelectionAutoClose) {
            suppressNextSelectionAutoClose = false
        } else if (::keyboardView.isInitialized) {
            keyboardView.closeClipboardPanel()
            keyboardView.closeEmojiPanel()
        }
    }


    private fun resetKeyboardState() {
        mComposing = ""
        tComposing = ""
        // Always come back to the plain key screen in lowercase - whether the keyboard
        // is being (re)shown after being fully closed, or the user has just switched to
        // a different text field - regardless of which panel (clipboard/emoji) or shift
        // state it was left in, and regardless of language layout.
        caps = false
        shift = false
        if (::keyboardView.isInitialized) {
            keyboardView.closeClipboardPanel()
            keyboardView.closeEmojiPanel()
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {

        return false
    }

    // Helper to request suggestions for a token
    private fun requestSuggestionsForToken(token: String) {
        // Do not auto-hide suggestions once opened. Only disable suggestions for password fields or when disabled in prefs.
        if (!suggestionsEnabled || isInPasswordField()) {
            topBarController?.showNormal()
            return
        }
        // Do not auto-hide suggestions on idle - only hide on explicit actions (space/action)
        debouncer?.onTyping(token, onTypingImmediate = { t ->
            serviceScope.launch {
                try {
                    val sList = suggestionEngine?.suggest(Normalizer.normalize(t, Normalizer.Form.NFC), 3) ?: emptyList<String>()
                    if (sList.isNotEmpty()) {
                        topBarController?.showSuggestions(sList, suggestionTextViews) { suggestion ->
                            onSuggestionClicked(suggestion)
                        }
                    } else {
                        // No suggestions found for this prefix. Do not automatically hide the suggestion bar per requirements.
                        // Keep current suggestions visible.
                    }
                } catch (e: Exception) {
                    topBarController?.showNormal()
                }
            }
        }, onIdle = null)
    }

    override fun letterOrSymbolClick(tag: String) {
        when {
            keyboardLayout == KeyboardLayout.SINGLISH && !keyboardSymbolsActive -> {
                singlishInput(tag)
            }

            keyboardLayout == KeyboardLayout.WIJESEKARA && !keyboardSymbolsActive -> {
                commitWijesekaraChar(tag)
            }

            else -> {
                currentInputConnection?.commitText(tag, 1)
            }
        }

        vibrate()

        try {
            val textBefore = currentInputConnection
                ?.getTextBeforeCursor(50, 0)
                ?.toString() ?: ""
            val token = textBefore.takeLastWhile { !it.isWhitespace() }
            requestSuggestionsForToken(token)
        } catch (_: Throwable) {}

        checkAutoUnshift()
    }

    private fun checkAutoUnshift() {
        if (caps && !shift) {
            caps = false
            updateKeyboard()
        }
    }

    private fun isInPasswordField(): Boolean {
        val t = currentInputEditorInfo
        return t != null && (t.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                t != null && (t.inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun onSuggestionClicked(suggestion: String) {
        val ic = currentInputConnection ?: return
        // Replace current token with suggestion
        val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(100, 0)?.toString() ?: ""
        val tokenStart = before.lastIndexOfAny(charArrayOf(' ', '\n', '\t')).let { if (it < 0) 0 else it + 1 }
        val token = before.substring(tokenStart)
        // delete token
        for (i in 0 until token.codePointCount(0, token.length)) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        }
        // commit suggestion, followed by a single space so the user can keep typing the next word
        ic.commitText("$suggestion ", 1)

        // Mirror the normal space-bar bookkeeping, since we just committed a space too.
        lastChar = null
        lastLetter = null
        positionFlag = ""
        mComposing = ""
        tComposing = ""

        // record acceptance
        serviceScope.launch {
            val lang = LanguageDetector.detectLanguage(suggestion)
            suggestionEngine?.recordAccepted(suggestion, lang)
        }

        // Hide suggestions now that the word is complete (word + space), same as pressing space.
        topBarController?.showNormal()
        debouncer?.cancel()
    }

    private var lastChar: CHAR? = null
    private var lastLetter: CHAR? = null
    private var positionFlag = ""

    // Holds the base consonant when "r" forms a rakaransaya right after a consonant+al-lakuna.
    // If the very next key is "u", we retro-convert that rakar into a gaetta pilla (vocalic-r
    // matra) instead, so "kru"/"shru"/etc. produce කෘ/ශෘ style output instead of ක්‍ර/ශ්‍ර.
    private var pendingGaettaPillaBase: CHAR? = null

    private fun hasPositionChanged(): Boolean =
        currentInputConnection.getTextBeforeCursor(5, 0)?.toString() != positionFlag

    private fun singlishInput(input: String) {
        var output = ""
        var erasePreviousChars = 0
        var mLastChar: CHAR? = null
        var mLastLetter: CHAR? = null
        var tLastChar: CHAR? = null
        var tLastLetter: CHAR? = null

        if (!hasPositionChanged()) {
            mLastChar = lastChar
            mLastLetter = lastLetter
        }

        lastChar = null
        lastLetter = null

        // Snapshot and clear; only reused this call if the "ru" pattern below actually matches.
        val pendingGaettaBase = pendingGaettaPillaBase
        pendingGaettaPillaBase = null

        var singlishChar: CHAR = getSinglishChars(input) ?: CHAR.EMPTY

        fun newLetter() {
            output = singlishChar.text
            if (singlishChar.type == CharType.WYANJANA) {
                output += CHAR.SIGN_AL_LAKUNA.text
                tLastChar = CHAR.SIGN_AL_LAKUNA
            }
        }

        if (mLastChar == null || mLastChar == CHAR.EMPTY) {
            if (input == "z" || input == "Z") tLastChar = CHAR.MARK_SANYAKA
            else newLetter()
        } else {
            when {
                input == "z" || input == "Z" -> tLastChar = CHAR.MARK_SANYAKA

                pendingGaettaBase != null && mLastChar.code == CHAR.SIGN_AL_LAKUNA.code && singlishChar.code == CHAR.UYANNA.code -> {
                    // "r" just added a rakar (ZWJ + RAYANNA + al-lakuna) on top of the base
                    // consonant's al-lakuna. Erase all 4 of those units and drop in the
                    // gaetta pilla instead, turning e.g. "k" + "r" + "u" into කෘ.
                    output = CHAR.GAETTA_PILLA.text
                    erasePreviousChars = 4
                    tLastLetter = pendingGaettaBase
                    tLastChar = CHAR.GAETTA_PILLA
                }

                mLastChar.type == CharType.WYANJANA ->
                    when (singlishChar.code) {
                        CHAR.AYANNA.code -> output = CHAR.AELA_PILLA.text
                        CHAR.IYANNA.code -> output = CHAR.KOMBU_DEKA.text
                        CHAR.UYANNA.code -> output = CHAR.KOMBUVA_HAA_GAYANUKITTA.text
                        else -> newLetter()
                    }

                mLastChar.code == CHAR.SIGN_AL_LAKUNA.code -> {
                    when (singlishChar.code) {
                        CHAR.AYANNA.code -> {
                            erasePreviousChars = 1
                            tLastChar = mLastLetter
                        }

                        CHAR.RAYANNA.code -> {
                            output =
                                CHAR.ZERO_WIDTH_JOINER.text + CHAR.RAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                            tLastChar = CHAR.SIGN_AL_LAKUNA
                            // Remember the base consonant in case the next key is "u",
                            // which should convert this rakar into a gaetta pilla (see above).
                            pendingGaettaPillaBase = mLastLetter
                        }

                        CHAR.YAYANNA.code -> {
                            output =
                                CHAR.ZERO_WIDTH_JOINER.text + CHAR.YAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                            tLastChar = CHAR.SIGN_AL_LAKUNA
                        }

                        CHAR.HAYANNA.code -> {
                            if (mLastLetter != null) {
                                when (mLastLetter.code) {
                                    CHAR.ALPAPRAANA_TTAYANNA.code -> {
                                        output =
                                            CHAR.ALPAPRAANA_TAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.ALPAPRAANA_TAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.MAHAAPRAANA_TTAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_TAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_TAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_DDAYANNA.code -> {
                                        output =
                                            CHAR.ALPAPRAANA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.ALPAPRAANA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.MAHAAPRAANA_DDAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_KAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_KAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_KAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_GAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_GAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_GAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_CAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_CAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_CAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_JAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_JAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_JAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_TAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_TAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_TAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_DAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_PAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_PAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_PAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_BAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_BAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_BAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.DANTAJA_SAYANNA.code -> {
                                        output =
                                            CHAR.TAALUJA_SAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.TAALUJA_SAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.SANYAKA_DDAYANNA.code -> {
                                        output =
                                            CHAR.SANYAKA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.SANYAKA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.MUURDHAJA_SAYANNA.code -> {
                                        output =
                                            CHAR.MUURDHAJA_SAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MUURDHAJA_SAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    else -> newLetter()
                                }
                            } else newLetter()
                        }

                        else -> {
                            when (singlishChar.type) {
                                CharType.SWARA -> {
                                    if (mLastLetter != null) {
                                        if (singlishChar.code == CHAR.AYANNA.code)
                                            erasePreviousChars = 1
                                        else {
                                            swaraSignMap[singlishChar.code].let { sign ->
                                                if (sign != null) {
                                                    output += sign.text
                                                    tLastChar = sign
                                                    erasePreviousChars = 1
                                                } else output = singlishChar.text
                                            }
                                        }
                                    } else output = singlishChar.text
                                }

                                else -> newLetter()
                            }
                        }
                    }
                }

                mLastChar.type == CharType.PILI -> {
                    when {
                        mLastChar.code == CHAR.KETTI_AEDA_PILLA.code && singlishChar.code == CHAR.AYANNA.code -> {
                            output = CHAR.DIGA_AEDA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_AEDA_PILLA
                        }

                        mLastChar.code == CHAR.KETTI_IS_PILLA.code && singlishChar.code == CHAR.IYANNA.code -> {
                            output = CHAR.DIGA_IS_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_IS_PILLA
                        }

                        mLastChar.code == CHAR.KETTI_PAA_PILLA.code && singlishChar.code == CHAR.UYANNA.code -> {
                            output = CHAR.DIGA_PAA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_PAA_PILLA
                        }

                        mLastChar.code == CHAR.GAETTA_PILLA.code && singlishChar.code == CHAR.IYANNA.code -> {
                            output = CHAR.DIGA_GAETTA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_GAETTA_PILLA
                        }

                        mLastChar.code == CHAR.KOMBUVA.code && singlishChar.code == CHAR.EYANNA.code -> {
                            output = CHAR.DIGA_KOMBUVA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_KOMBUVA
                        }

                        mLastChar.code == CHAR.KOMBUVA_HAA_AELA_PILLA.code && singlishChar.code == CHAR.OYANNA.code -> {
                            output = CHAR.KOMBUVA_HAA_DIGA_AELA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.KOMBUVA_HAA_DIGA_AELA_PILLA
                        }

                        mLastChar.code == CHAR.GAETTA_PILLA.code && singlishChar.code == CHAR.UYANNA.code -> {
                            // Second "u" lengthens the gaetta pilla, e.g. "kru" + "u" -> කෲ.
                            output = CHAR.DIGA_GAETTA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_GAETTA_PILLA
                        }

                        else -> newLetter()
                    }
                }

                mLastChar.code == CHAR.MARK_SANYAKA.code -> {
                    when (singlishChar.code) {
                        CHAR.ALPAPRAANA_KAYANNA.code -> singlishChar = CHAR.TAALUJA_NAASIKYAYA
                        CHAR.ALPAPRAANA_GAYANNA.code -> singlishChar = CHAR.SANYAKA_GAYANNA
                        CHAR.ALPAPRAANA_JAYANNA.code -> singlishChar = CHAR.SANYAKA_JAYANNA
                        CHAR.ALPAPRAANA_DDAYANNA.code -> singlishChar = CHAR.SANYAKA_DDAYANNA
                        CHAR.ALPAPRAANA_DAYANNA.code -> singlishChar = CHAR.SANYAKA_DAYANNA
                        CHAR.ALPAPRAANA_BAYANNA.code -> singlishChar = CHAR.AMBA_BAYANNA
                        CHAR.HAYANNA.code -> singlishChar = CHAR.TAALUJA_SANYOOGA_NAAKSIKYAYA
                    }
                    newLetter()
                }

                else -> {
                    if (mLastLetter != null) {
                        when (mLastLetter) {
                            CHAR.AYANNA -> {
                                when (singlishChar.code) {
                                    CHAR.AYANNA.code -> {
                                        output = CHAR.AAYANNA.text
                                        erasePreviousChars = 1
                                        tLastLetter = CHAR.AAYANNA
                                    }

                                    CHAR.IYANNA.code -> {
                                        output = CHAR.AIYANNA.text
                                        erasePreviousChars = 1
                                        tLastLetter = CHAR.AIYANNA
                                    }

                                    CHAR.UYANNA.code -> {
                                        output = CHAR.AUYANNA.text
                                        erasePreviousChars = 1
                                        tLastLetter = CHAR.AUYANNA
                                    }

                                    else -> newLetter()
                                }
                            }

                            CHAR.AEYANNA -> {
                                if (singlishChar.code == CHAR.AYANNA.code) {
                                    output = CHAR.AEEYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.AEEYANNA
                                } else newLetter()
                            }

                            CHAR.IYANNA -> {
                                if (singlishChar.code == CHAR.IYANNA.code) {
                                    output = CHAR.IIYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.IIYANNA
                                } else newLetter()
                            }

                            CHAR.UYANNA -> {
                                if (singlishChar.code == CHAR.UYANNA.code) {
                                    output = CHAR.UUYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.UUYANNA
                                } else newLetter()
                            }

                            CHAR.IRUYANNA -> {
                                if (singlishChar.code == CHAR.IYANNA.code) {
                                    output = CHAR.IRUUYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.IRUUYANNA
                                } else newLetter()
                            }

                            CHAR.EYANNA -> {
                                if (singlishChar.code == CHAR.EYANNA.code) {
                                    output = CHAR.EEYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.EEYANNA
                                } else newLetter()
                            }

                            CHAR.OYANNA -> {
                                if (singlishChar.code == CHAR.OYANNA.code) {
                                    output = CHAR.OOYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.OOYANNA
                                } else newLetter()
                            }

                            else -> newLetter()
                        }
                    } else newLetter()
                }
            }
        }

        if (erasePreviousChars > 0) erasePrevious(erasePreviousChars)

        val ic = currentInputConnection
        if (ic != null) {
            try {
                ic.commitText(output, 1)
            } catch (t: Throwable) {
                Log.e("IME", "singlishInput commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in singlishInput")
        }

        lastChar = tLastChar ?: tLastLetter ?: singlishChar
        lastLetter = tLastLetter ?: singlishChar
        positionFlag = currentInputConnection.getTextBeforeCursor(5, 0)?.toString() ?: ""

        // After committing singlish output, request suggestions for updated token
        try {
            val textBefore2 = currentInputConnection.getTextBeforeCursor(50, 0)?.toString() ?: ""
            val token2 = textBefore2.takeLastWhile { !it.isWhitespace() }
            requestSuggestionsForToken(token2)
        } catch (t: Throwable) {
            Log.w("IME", "failed to update suggestions after singlishInput", t)
        }
    }

    private fun getSinglishChars(input: String): CHAR? = singlishMap[input]

    override fun emojiClick(tag: String) {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                suppressNextSelectionAutoClose = true
                ic.commitText(tag, 1)
                // Update the recency data + persist it now, but do NOT refresh the
                // on-screen recent-emoji row here — reordering it under the user's
                // finger mid-session is jarring. The row picks up the new order the
                // next time the keyboard is shown (see onStartInputView).
                EmojiData.addRecentEmoji(this, tag)
            } catch (t: Throwable) {
                Log.e("IME", "emoji commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in emojiClick")
        }
        vibrate()
        checkAutoUnshift()
    }

    // --- Clipboard manager ---

    override fun clipboardPasteClick(text: String) {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                // Mark the next primary-clip change as self-triggered so pasting a clip
                // doesn't get re-captured as a "new" copy by the system clipboard listener.
                suppressNextClipCapture = true
                ic.commitText(text, 1)
            } catch (t: Throwable) {
                Log.e("IME", "clipboard paste failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in clipboardPasteClick")
        }
        vibrate()
        if (::keyboardView.isInitialized) keyboardView.closeClipboardPanel()
    }

    override fun clipboardPinClick(item: ClipItem) {
        ClipboardData.setPinned(this, item.id, !item.pinned)
        if (::keyboardView.isInitialized) keyboardView.refreshClipboardList()
    }

    override fun clipboardShareClick(item: ClipItem) {
        try {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, item.text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(shareIntent, null).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (t: Throwable) {
            Log.e("IME", "clipboard share failed", t)
        }
    }

    override fun clipboardDeleteClick(item: ClipItem) {
        ClipboardData.delete(this, item.id)
        if (::keyboardView.isInitialized) keyboardView.refreshClipboardList()
    }

    override fun clipboardDeleteSelectedClick(ids: Set<Long>) {
        ClipboardData.deleteAll(this, ids)
        if (::keyboardView.isInitialized) keyboardView.refreshClipboardList()
    }

    override fun numberClick(tag: String) {
        val ic = currentInputConnection
        if (ic != null) {
            try {

                val toCommit = when (keyboardLayout) {
                    KeyboardLayout.WIJESEKARA -> Maps.keyLabelsNumbersWijesekara[tag] ?: tag
                    else -> tag
                }
                ic.commitText(toCommit, 1)
            } catch (t: Throwable) {
                Log.e("IME", "number commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in numberClick")
        }
        vibrate()
        checkAutoUnshift()
    }


    override fun functionClick(type: Function) {
        val ic = currentInputConnection
        when (type) {
            Function.ACTION -> {
                if (ic != null) {
                    try {
                        val editorInfo = currentInputEditorInfo
                        val actionId = editorInfo?.actionId ?: 0
                        if (actionId != 0) ic.performEditorAction(actionId)
                        else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    } catch (t: Throwable) {
                        Log.e("IME", "performEditorAction/sendKeyEvent failed", t)
                    }
                } else {
                    Log.w("IME", "currentInputConnection is null in ACTION")
                }
                // Hide suggestions explicitly when user presses action
                topBarController?.showNormal()
                debouncer?.cancel()
            }

            Function.SHIFT -> {
                if (!caps) {
                    caps = true
                    shift = false
                } else if (!shift) {
                    shift = true
                } else {
                    caps = false
                    shift = false
                }
                updateKeyboard()
            }

            Function.LANG -> {
                try {
                    val enabled = Prefs.getEnabledLayouts(this)
                    val currentIndex = enabled.indexOf(keyboardLayout).let { if (it < 0) 0 else it }
                    val next = enabled[(currentIndex + 1) % enabled.size]
                    setKeyboardLayout(next)


                    mComposing = ""
                } catch (t: Throwable) {
                    Log.e("IME", "language switch failed", t)

                    setKeyboardLayout(if (keyboardLayout == KeyboardLayout.ENGLISH) Prefs.getKeyboardLayout(this) else KeyboardLayout.ENGLISH)
                }
            }

            Function.IME -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                if (imm != null) {
                    try {

                        userInvokedInputMethodPicker = true
                        imm.showInputMethodPicker()
                    } catch (t: Throwable) {
                        Log.e("IME", "showInputMethodPicker failed", t)
                        userInvokedInputMethodPicker = false
                    }
                } else {
                    Log.w("IME", "InputMethodManager is null in Function.IME")
                }
            }

            Function.BACKSPACE -> {
                if (ic != null) {
                    try {
                        if (ic.getSelectedText(0).isNullOrEmpty()) {
                            ic.deleteSurroundingTextInCodePoints(1, 0)
                        } else {
                            ic.commitText("", 1)


                        }
                    } catch (t: Throwable) {
                        Log.e("IME", "BACKSPACE operation failed", t)
                    }
                } else {
                    Log.w("IME", "currentInputConnection is null in BACKSPACE")
                }


                lastChar = null
                lastLetter = null
                positionFlag = ""
                mComposing = ""
            }
            Function.PANEL -> {

                keyboardSymbolsActive = !keyboardSymbolsActive
                updateKeyboard()
            }
        }
        vibrate()
    }

    override fun specialClick(tag: String) {
        val ic = currentInputConnection
        if (ic != null) {
            try {

                val toCommit = if (tag.isNotEmpty() && tag.all { it.isDigit() }) {
                    try {
                        val code = tag.toInt()
                        when (code) {
                            32 -> " "
                            else -> code.toChar().toString()
                        }
                    } catch (t: Throwable) {
                        tag
                    }
                } else tag

                ic.commitText(toCommit, 1)


            } catch (t: Throwable) {
                Log.e("IME", "specialClick commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in specialClick")
        }
        vibrate()

        if (tag == " " || tag == "32") {

            lastChar = null
            lastLetter = null
            positionFlag = ""
            // hide suggestions on space/punctuation
            topBarController?.showNormal()
            debouncer?.cancel()
        }
        checkAutoUnshift()
    }

    override fun longPressSecondaryClick(char: String) {
        val ic = currentInputConnection ?: return
        try {
            ic.commitText(char, 1)
            vibrate()
        } catch (t: Throwable) {
            Log.e("IME", "longPressSecondaryClick commit failed", t)
        }
    }

    override fun eraseDo() {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                ic.deleteSurroundingTextInCodePoints(1, 0)
            } catch (t: Throwable) {
                Log.e("IME", "eraseDo failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in eraseDo")
        }
    }

    override fun eraseUndo() {

    }

    override fun eraseDone() {

    }

    override fun moveRight() {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                val newCursorPosition = (ic.getTextBeforeCursor(100, 0)?.length ?: 0) + 1
                ic.setSelection(newCursorPosition, newCursorPosition)
            } catch (t: Throwable) {
                Log.e("IME", "moveRight failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in moveRight")
        }
    }

    override fun moveLeft() {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                val currentCursorPosition = ic.getTextBeforeCursor(100, 0)?.length ?: 0
                if (currentCursorPosition > 0) {
                    ic.setSelection(currentCursorPosition - 1, currentCursorPosition - 1)
                }
            } catch (t: Throwable) {
                Log.e("IME", "moveLeft failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in moveLeft")
        }
    }

    private fun setKeyboardLayout(layout: KeyboardLayout) {
        keyboardLayout = layout


      try {
          Prefs.setSelectedLayout(this, layout)
        } catch (t: Throwable) {
            Log.e("IME", "Failed to persist selected keyboard layout", t)
       }

         when (layout) {
             KeyboardLayout.ENGLISH -> {
                 keyboardView.setLangIndicator("ENG")
                 updateKeyboard()
             }
             KeyboardLayout.WIJESEKARA -> {
                 keyboardView.setLangIndicator("SIN")
                 updateKeyboard()
             }
             KeyboardLayout.SINGLISH -> {
                 keyboardView.setLangIndicator("SIN")
                 updateKeyboard()
             }
         }
     }

    private fun updateKeyboard() {

        if (keyboardSymbolsActive) {
            try {
                val symMap = if (caps) symbolsMapShifted else symbolsMap
                val letters = mutableMapOf<String, String>()
                for (c in 'a'..'z') {
                    val key = c.toString()
                    letters[key] = symMap[key] ?: ""
                }
                keyboardView.setLetterKeys(letters)

                keyboardView.setNumberKeys(keyLabelsNumbers)
                keyboardView.setSpecialKeys(keyLabelsSpecialEnglish)
                keyboardView.setSecondaryLabels(null)
                keyboardView.setLongPressChars(null)
                return
            } catch (t: Throwable) {
                Log.e("IME", "failed to render symbols keyboard", t)
            }
        }

        val keySet = if (caps) keyLabelsLettersEnglishShifted else keyLabelsLettersEnglish
        var secondaryLabels: Map<String, String>? = null

        when (keyboardLayout) {
            KeyboardLayout.ENGLISH -> {
                keyboardView.setLetterKeys(keySet)
                keyboardView.setNumberKeys(keyLabelsNumbers)
                keyboardView.setSpecialKeys(keyLabelsSpecialEnglish)
                
                // Secondary labels: matches symbol keyboard position exactly
                // Row1: q w e r t y u i o p  -> _ ! | = [ ] < > { }
                // Row2: a s d f g h j k l    -> @ # ^ % & - + ( )
                // Row3: z x c v b n m        -> * " ' : ; \ ?
                val englishSecondary = mapOf(
                    "q" to "_",  "w" to "!", "e" to "|",  "r" to "=",
                    "t" to "[",  "y" to "]", "u" to "<",  "i" to ">",
                    "o" to "{",  "p" to "}",
                    "a" to "@",  "s" to "#", "d" to "^",  "f" to "%",
                    "g" to "&",  "h" to "-", "j" to "+",  "k" to "(",
                    "l" to ")",
                    "z" to "*",  "x" to "\"", "c" to "\'", "v" to ":",
                    "b" to ";",  "n" to "\\", "m" to "?"
                )
                keyboardView.setSecondaryLabels(englishSecondary)
                // English: corner label IS the committed char, no separate override needed
                keyboardView.setLongPressChars(null)
            }

            KeyboardLayout.WIJESEKARA -> {
                val sinhalaKeySet = if (caps) keyLabelsLettersWijesekaraShifted else keyLabelsLettersWijesekara
                keyboardView.setLetterKeys(sinhalaKeySet)
                keyboardView.setNumberKeys(keyLabelsNumbersWijesekara)
                val specialKeys = if (caps) keyLabelsSpecialWijesekaraSinhalaShifted else keyLabelsSpecialWijesekaraSinhala
                keyboardView.setSpecialKeys(specialKeys)
                keyboardView.setSecondaryLabels(null)
                // No corner label shown, but long-press still commits the symbol
                // that sits in this key's position on the symbol keyboard.
                keyboardView.setLongPressChars(symbolsMap)


            }

            KeyboardLayout.SINGLISH -> {
                keyboardView.setLetterKeys(keySet)
                keyboardView.setNumberKeys(keyLabelsNumbers)
                keyboardView.setSpecialKeys(keyLabelsSpecialEnglish)


                val labels = mutableMapOf<String, String>()
                for ((k, v) in keySet) {
                    val key = k.lowercase()

                    val charMap = singlishMap[if (caps) key.uppercase() else key]
                    if (charMap != null && charMap != CHAR.EMPTY) {
                        labels[k] = charMap.text
                    }
                }
                // Corner label stays the Sinhala phonetic char (visual only).
                keyboardView.setSecondaryLabels(labels)
                // But long-press commits the symbol from this key's symbol-keyboard
                // position, not the Sinhala char — fixes "z" long-press committing
                // ඳ instead of typing the symbol at that position.
                keyboardView.setLongPressChars(symbolsMap)
            }
        }

        keyboardView.buttonActionShift.setImageResource(
            if (caps) R.drawable.ic_shift_pressed
            else R.drawable.ic_shift
        )


        val editorInfo = currentInputEditorInfo
        if (editorInfo != null) {
            val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            val (iconRes, desc) = when (imeAction) {
                EditorInfo.IME_ACTION_GO -> Pair(R.drawable.ic_keyboard_return, "Go")
                EditorInfo.IME_ACTION_SEARCH -> Pair(R.drawable.ic_search, "Search")
                EditorInfo.IME_ACTION_SEND -> Pair(R.drawable.ic_send, "Send")
                EditorInfo.IME_ACTION_NEXT -> Pair(R.drawable.ic_keyboard_arrow_right, "Next")
                EditorInfo.IME_ACTION_DONE -> Pair(R.drawable.ic_check, "Done")
                EditorInfo.IME_ACTION_NONE -> Pair(R.drawable.ic_keyboard_return, "Enter")
                else -> Pair(R.drawable.ic_keyboard_return, "Enter")
            }

            try {
                keyboardView.buttonActionAction.setImageResource(iconRes)
                keyboardView.buttonActionAction.contentDescription = desc
            } catch (t: Throwable) {

                Log.e("IME", "Failed to set action icon resource", t)
                keyboardView.buttonActionAction.setImageResource(R.drawable.ic_keyboard_return)
                keyboardView.buttonActionAction.contentDescription = "Enter"
            }
        } else {

            keyboardView.buttonActionAction.setImageResource(R.drawable.ic_keyboard_return)
            keyboardView.buttonActionAction.contentDescription = "Enter"
        }
    }

    private fun vibrate() {
        if (!Prefs.getVibration(this)) return
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null) {
            Log.w("IME", "Vibrator service not available")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (t: Throwable) {
            Log.e("IME", "vibrate failed", t)
        }
    }




    private fun erasePrevious(count: Int = 1) {
        val ic = currentInputConnection ?: return


        fun deleteUnits(units: Int) {
            try {
                ic.deleteSurroundingText(units, 0)
            } catch (t: Throwable) {
                Log.e("IME", "deleteSurroundingText failed in erasePrevious", t)
            }
        }

        if (count == 1) {

            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length >= 2) {
                val ch = before[before.length - 2]
                if (Character.isHighSurrogate(ch) || Character.isLowSurrogate(ch)) {
                    deleteUnits(2)
                } else {
                    deleteUnits(1)
                }
            } else {
                deleteUnits(1)
            }
        } else {
            deleteUnits(count)
        }


        try {
            val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
            if (before == CHAR.ZERO_WIDTH_JOINER.text) {

                deleteUnits(1)

                erasePrevious(1)
            }
        } catch (t: Throwable) {
            Log.e("IME", "post-delete ZWJ check failed", t)
        }
    }
}
