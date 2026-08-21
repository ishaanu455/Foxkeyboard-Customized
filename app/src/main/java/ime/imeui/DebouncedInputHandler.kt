package ime.imeui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounces input so the callback fires only after the user pauses typing.
 * The callback itself is a plain lambda — the caller is responsible for
 * dispatching to the correct thread (e.g. launching a coroutine inside it).
 */
class DebouncedInputHandler(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 150L
) {
    private var debounceJob: Job? = null

    fun onTyping(
        token: String,
        onTypingImmediate: (String) -> Unit,
        onIdle: (() -> Unit)? = null
    ) {
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.Main) {
            delay(debounceMs)
            onTypingImmediate(token)
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
