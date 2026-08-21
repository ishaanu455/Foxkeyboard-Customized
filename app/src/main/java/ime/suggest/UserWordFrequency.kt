package ime.suggest

import android.content.Context
import org.json.JSONObject

/**
 * Tracks how often the user types each word so frequently-used words can be
 * ranked above generic dictionary matches. Stored locally on-device only
 * (SharedPreferences) — never leaves the phone.
 *
 * Kept deliberately simple: a word -> count map, capped at MAX_WORDS so
 * storage/lookup never grows unbounded. When the cap is hit, the least-used
 * words are evicted first.
 */
object UserWordFrequency {
    private const val PREFS_NAME = "user_word_frequency"
    private const val KEY_DATA = "word_counts"
    private const val MAX_WORDS = 2000

    // In-memory cache — SharedPreferences/JSON is only parsed once per process.
    @Volatile
    private var cache: MutableMap<String, Int>? = null

    private fun loadCache(context: Context): MutableMap<String, Int> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_DATA, null)
            val map = LinkedHashMap<String, Int>()
            if (json != null) {
                try {
                    val obj = JSONObject(json)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = obj.optInt(k, 0)
                    }
                } catch (_: Exception) {
                    // Corrupt/old data — start fresh rather than crash.
                }
            }
            cache = map
            return map
        }
    }

    private fun persist(context: Context) {
        val map = cache ?: return
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, v)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATA, obj.toString())
            .apply() // async write, doesn't block caller
    }

    /**
     * Call this whenever the user finishes typing a word (space/punctuation pressed,
     * or a suggestion tapped). Safe to call from a background thread.
     */
    fun learn(context: Context, word: String) {
        val cleaned = word.trim()
        if (cleaned.length < 2) return // skip single letters / noise

        val map = loadCache(context)
        map[cleaned] = (map[cleaned] ?: 0) + 1

        if (map.size > MAX_WORDS) {
            // Evict the least-used entries first, down to the cap.
            val toEvict = map.entries
                .sortedBy { it.value }
                .take(map.size - MAX_WORDS)
            for (entry in toEvict) map.remove(entry.key)
        }

        persist(context)
    }

    fun getFrequency(context: Context, word: String): Int =
        loadCache(context)[word] ?: 0

    /** Learned words matching a prefix, most-used first. Used to merge into suggestions. */
    fun getByPrefix(context: Context, prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val map = loadCache(context)
        return map.entries
            .asSequence()
            .filter { it.key.startsWith(prefix, ignoreCase = true) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .toList()
    }
}
