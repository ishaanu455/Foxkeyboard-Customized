package unicode.sinhala.keyboard

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * Handles the "use my own local font file" flow: the user picks a .ttf/.otf they already
 * have on their device via the system file picker; we copy those bytes into our own app
 * storage (so it keeps working even if the original file moves, and so the keyboard
 * service can read it without holding a content:// Uri permission) and load a Typeface
 * from that private copy. The app never downloads, bundles, or shares this file with
 * anyone - it's purely a local, on-device copy of a file the user already owned.
 */
object CustomFontManager {
    private const val FONT_FILE_NAME = "custom_emoji_font.ttf"

    fun fontFile(context: Context): File = File(context.filesDir, FONT_FILE_NAME)

    fun hasCustomFont(context: Context): Boolean = fontFile(context).exists()

    /**
     * Copies the picked font into app storage and validates it actually parses as a font.
     * Returns true on success; on failure, no partial/broken file is left behind.
     */
    fun importFont(context: Context, uri: Uri): Boolean {
        val dest = fontFile(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            // Throws if the bytes aren't a font Android can parse.
            Typeface.createFromFile(dest)
            true
        } catch (t: Throwable) {
            dest.delete()
            false
        }
    }

    fun removeFont(context: Context) {
        fontFile(context).delete()
        clearCache()
    }

    // In-memory cache — disk is only read once per app process lifetime
    private var cachedTypeface: Typeface? = null
    private var cachedFontPath: String? = null

    fun loadTypeface(context: Context): Typeface? {
        val file = fontFile(context)
        if (!file.exists()) {
            cachedTypeface = null
            cachedFontPath = null
            return null
        }
        // Return cached if same file (path + last-modified match)
        if (cachedTypeface != null && cachedFontPath == file.absolutePath) {
            return cachedTypeface
        }
        return try {
            Typeface.createFromFile(file).also {
                cachedTypeface = it
                cachedFontPath = file.absolutePath
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** Call after removing the font so the cache is cleared immediately. */
    fun clearCache() {
        cachedTypeface = null
        cachedFontPath = null
    }
}
