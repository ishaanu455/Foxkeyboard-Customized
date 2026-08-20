package unicode.sinhala.keyboard

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the full Twemoji image set used by [EmojiStyle.TWEMOJI] and lets Coil's disk
 * cache keep the files, so the keyboard can show them offline afterwards without re-fetching
 * on every keypress.
 */
object EmojiDownloader {

    private fun allEmojis(): List<String> =
        EmojiData.emojis.filterKeys { it != "Recent" }.values.flatten().distinct()

    /**
     * Fetches every emoji image once (caching it to disk). Calls [onProgress] on the main
     * thread after each item. Returns true if the download is considered successful overall
     * (a handful of misses for obscure/newer emoji sequences is tolerated).
     */
    suspend fun downloadTwemojiPack(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit
    ): Boolean {
        val emojis = allEmojis()
        val total = emojis.size
        var failures = 0

        withContext(Dispatchers.IO) {
            emojis.forEachIndexed { index, emoji ->
                val request = ImageRequest.Builder(context)
                    .data(TwemojiUtil.urlFor(emoji))
                    .build()
                val result = context.imageLoader.execute(request)
                if (result !is SuccessResult) failures++

                withContext(Dispatchers.Main) { onProgress(index + 1, total) }
            }
        }

        return total > 0 && failures < total / 10
    }
}
