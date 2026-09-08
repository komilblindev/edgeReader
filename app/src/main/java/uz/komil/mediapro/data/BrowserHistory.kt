package uz.komil.mediapro.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Visited website entry in browser history. */
data class HistoryEntry(
    val id: String,
    val title: String,
    val url: String,
    val timestamp: Long
)

/**
 * Manages persistent browser history for visited sites.
 */
object BrowserHistory {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private var appFile: File? = null

    fun init(context: Context) {
        val f = File(context.filesDir, "browser_history.json")
        appFile = f
        load()
    }

    private fun load() {
        val f = appFile ?: return
        if (!f.exists()) return
        try {
            val json = f.readText()
            val arr = JSONArray(json)
            val list = mutableListOf<HistoryEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    HistoryEntry(
                        id = o.optString("id", i.toString()),
                        title = o.optString("title", ""),
                        url = o.optString("url", ""),
                        timestamp = o.optLong("timestamp", 0L)
                    )
                )
            }
            _entries.value = list
        } catch (t: Throwable) {
            ErrorLog.e("BrowserHistory", "load failed", t)
        }
    }

    suspend fun add(title: String, url: String) = withContext(Dispatchers.IO) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("data:")) return@withContext
        val cleanTitle = if (title.isBlank() || title == url) url else title
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(
            id = System.nanoTime().toString(),
            title = cleanTitle,
            url = url,
            timestamp = now
        )
        // Deduplicate recent same url
        val current = _entries.value.filterNot { it.url == url }
        val updated = (listOf(entry) + current).take(200)
        _entries.value = updated
        save(updated)
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val updated = _entries.value.filterNot { it.id == id }
        _entries.value = updated
        save(updated)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        _entries.value = emptyList()
        save(emptyList())
    }

    private fun save(list: List<HistoryEntry>) {
        val f = appFile ?: return
        try {
            val arr = JSONArray()
            list.forEach { item ->
                val o = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("timestamp", item.timestamp)
                }
                arr.put(o)
            }
            f.writeText(arr.toString())
        } catch (t: Throwable) {
            ErrorLog.e("BrowserHistory", "save failed", t)
        }
    }
}
