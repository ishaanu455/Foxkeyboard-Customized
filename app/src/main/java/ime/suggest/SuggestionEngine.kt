package ime.suggest

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight offline Suggestion Engine using Trie for fast prefix-based suggestions.
 * Supports English (case-insensitive) and Sinhala (Unicode NFC normalized).
 * Loads dictionaries from assets/english.json and assets/sinhala.json.
 */
class SuggestionEngine(private val context: Context) {

    private val initialized = AtomicBoolean(false)
    private val englishTrie = Trie()
    private val sinhalaTrie = Trie()

    companion object {
        private const val TAG = "SuggestionEngine"
        private const val ENGLISH_FILE = "english.json"
        private const val SINHALA_FILE = "sinhala.json"
    }

    suspend fun initializeIfNeeded() {
        if (initialized.get()) return

        withContext(Dispatchers.IO) {
            var englishLoaded = 0
            var sinhalaLoaded = 0

            try {
                // Load English dictionary
                context.assets.open(ENGLISH_FILE).use { stream ->
                    val jsonText = stream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonText)

                    for (i in 0 until jsonArray.length()) {
                        val rawWord = jsonArray.optString(i) // safer than getString()
                        val word = rawWord.trim().lowercase()
                        if (word.isNotEmpty()) {
                            englishTrie.insert(word)
                            englishLoaded++
                        }
                    }
                }
                Log.d(TAG, "Loaded $englishLoaded English words")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load English dictionary (normal in tests)", e)
            }

            try {
                // Load Sinhala dictionary
                context.assets.open(SINHALA_FILE).use { stream ->
                    val jsonText = stream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonText)

                    for (i in 0 until jsonArray.length()) {
                        val rawWord = jsonArray.optString(i)
                        val normalized = Normalizer.normalize(rawWord.trim(), Normalizer.Form.NFC)
                        if (normalized.isNotEmpty()) {
                            sinhalaTrie.insert(normalized)
                            sinhalaLoaded++
                        }
                    }
                }
                Log.d(TAG, "Loaded $sinhalaLoaded Sinhala words")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load Sinhala dictionary (normal in tests)", e)
            }

            initialized.set(true)
            Log.i(TAG, "SuggestionEngine initialized successfully")
        }
    }

    suspend fun suggest(prefix: String, limit: Int = 5): List<String> {
        if (!initialized.get()) initializeIfNeeded()

        val cleanedPrefix = prefix.trim()
        if (cleanedPrefix.isEmpty()) return emptyList()

        val lang = LanguageDetector.detectLanguage(cleanedPrefix)
        val queryPrefix = if (lang == LanguageDetector.Language.SINHALA) cleanedPrefix else cleanedPrefix.lowercase()
        val trie = if (lang == LanguageDetector.Language.SINHALA) sinhalaTrie else englishTrie

        // Gather more candidates than we'll show, so frequency-based ranking has
        // room to reorder — otherwise a frequent word buried deep in the dictionary
        // BFS order would never surface.
        val candidatePoolSize = limit * 6
        val dictionaryCandidates = trie.getByPrefix(queryPrefix, candidatePoolSize)
        val learnedCandidates = UserWordFrequency.getByPrefix(context, queryPrefix, candidatePoolSize)

        // Learned words go in first so they're never dropped before dictionary
        // candidates when we later cap the pool.
        val merged = LinkedHashSet<String>()
        merged.addAll(learnedCandidates)
        merged.addAll(dictionaryCandidates)

        // Rank: user's own typing frequency first, then shorter words, then alphabetical.
        val ranked = merged.sortedWith(
            compareByDescending<String> { UserWordFrequency.getFrequency(context, it) }
                .thenBy { it.length }
                .thenBy { it }
        )

        return ranked.take(limit)
    }

    /** Call when the user accepts a suggestion or finishes typing a word — learns it locally. */
    fun recordAccepted(word: String, lang: LanguageDetector.Language) {
        val normalized = if (lang == LanguageDetector.Language.SINHALA) {
            Normalizer.normalize(word, Normalizer.Form.NFC)
        } else {
            word.lowercase()
        }
        UserWordFrequency.learn(context, normalized)
    }

    /**
     * Efficient Trie with BFS for shortest + alphabetical suggestions
     */
    class Trie {
        private val root = Node()

        private class Node {
            val children: MutableMap<Char, Node> = LinkedHashMap() // preserves insertion order → alphabetical
            var isWord: Boolean = false
        }

        fun insert(word: String) {
            var current = root
            for (char in word) {
                current = current.children.getOrPut(char) { Node() }
            }
            current.isWord = true
        }

        fun getByPrefix(prefix: String, limit: Int): List<String> {
            val results = mutableListOf<String>()
            var current = root

            // Traverse to the prefix end
            for (char in prefix) {
                val node = current.children[char] ?: return results // no matches
                current = node
            }

            // BFS to get shortest words first, alphabetical due to LinkedHashMap
            val queue = ArrayDeque<Pair<Node, String>>()
            queue.add(current to prefix)

            while (queue.isNotEmpty() && results.size < limit) {
                val (node, currentWord) = queue.poll()

                if (node.isWord) {
                    results.add(currentWord)
                }

                // Add children in alphabetical order
                for ((char, childNode) in node.children) {
                    queue.add(childNode to (currentWord + char))
                }
            }

            return results
        }
    }
}