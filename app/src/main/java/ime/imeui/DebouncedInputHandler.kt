package ime.imeui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debounces suggestion search so it only fires after the user pauses typing.
 * Search runs on Dispatchers.Default — never blocks the main/UI thread.
 *
 * onTypingImmediate is no longer called on every keystroke.
 * Instead, after [debounceMs] ms of silence the callback fires on Default,
 * and the caller switches to Main for any UI update.
 */
class DebouncedInputHandler(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 150L   // 150 ms feels instant but skips mid-word searches
) {
    private var debounceJob: Job? = null

    fun onTyping(
        token: String,
        onTypingImmediate: (String) -> Unit,   // now called debounced, on Default thread
        onIdle: (() -> Unit)? = null
    ) {
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.Default) {
            delay(debounceMs)
            onTypingImmediate(token)   // search happens here, off main thread
            if (onIdle != null) {
                delay(debounceMs)
                onIdle()
            }
        }
    }

    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
    }
}
