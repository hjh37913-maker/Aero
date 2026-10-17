package com.aero.browser.data

import android.content.Context
import android.content.SharedPreferences
import com.aero.browser.data.DataStore.Bookmark
import com.aero.browser.data.DataStore.HistoryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object DataStore {
    data class Bookmark(val id: String = UUID.randomUUID().toString(), val title: String, val url: String)
    data class HistoryEntry(val id: String = UUID.randomUUID().toString(), val title: String, val url: String)

    private const val PREFS = "aero_data"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // --- Search engine ---
    fun getSearchEngine(): String = prefs.getString("search_engine", "google") ?: "google"
    fun setSearchEngine(engine: String) { prefs.edit().putString("search_engine", engine).apply() }
    fun getCustomSearchUrl(): String = prefs.getString("custom_search_url", "https://www.google.com/search?q=%s") ?: "https://www.google.com/search?q=%s"
    fun setCustomSearchUrl(url: String) { prefs.edit().putString("custom_search_url", url).apply() }

    fun buildSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return when (getSearchEngine()) {
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "custom" -> getCustomSearchUrl().replace("%s", encoded)
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    // --- Bookmarks ---
    fun getBookmarks(): List<Bookmark> {
        val json = prefs.getString("bookmarks", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<Bookmark>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun addBookmark(title: String, url: String) {
        val list = getBookmarks().toMutableList()
        if (list.none { it.url == url }) {
            list.add(0, Bookmark(title = title.ifBlank { url }, url = url))
            prefs.edit().putString("bookmarks", gson.toJson(list)).apply()
        }
    }

    fun removeBookmark(id: String) {
        val list = getBookmarks().filter { it.id != id }
        prefs.edit().putString("bookmarks", gson.toJson(list)).apply()
    }

    fun exportBookmarks(): String = gson.toJson(getBookmarks())
    fun importBookmarks(json: String): Boolean = try {
        val list: List<Bookmark> = gson.fromJson(json, object : TypeToken<List<Bookmark>>() {}.type)
        prefs.edit().putString("bookmarks", gson.toJson(list)).apply()
        true
    } catch (_: Exception) { false }

    // --- History ---
    fun getHistory(): List<HistoryEntry> {
        val json = prefs.getString("history", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<HistoryEntry>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun addHistory(title: String, url: String, incognito: Boolean) {
        if (incognito || url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val list = getHistory().toMutableList()
        list.removeAll { it.url == url }
        list.add(0, HistoryEntry(title = title.ifBlank { url }, url = url))
        if (list.size > 300) list.subList(300, list.size).clear()
        prefs.edit().putString("history", gson.toJson(list)).apply()
    }

    fun removeHistory(id: String) {
        val list = getHistory().filter { it.id != id }
        prefs.edit().putString("history", gson.toJson(list)).apply()
    }

    fun clearHistory() { prefs.edit().remove("history").apply() }

    fun searchHistory(query: String): List<HistoryEntry> {
        if (query.isBlank()) return getHistory()
        val q = query.lowercase()
        return getHistory().filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
    }

    // --- UI settings ---
    fun getFontScale(): Float = prefs.getFloat("font_scale", 1.0f)
    fun setFontScale(scale: Float) { prefs.edit().putFloat("font_scale", scale.coerceIn(0.8f, 1.6f)).apply() }

    fun isShowFavorites(): Boolean = prefs.getBoolean("show_favorites", true)
    fun setShowFavorites(v: Boolean) { prefs.edit().putBoolean("show_favorites", v).apply() }

    fun isShowRecent(): Boolean = prefs.getBoolean("show_recent", true)
    fun setShowRecent(v: Boolean) { prefs.edit().putBoolean("show_recent", v).apply() }

    fun getPinnedUrls(): Set<String> = prefs.getStringSet("pinned_urls", emptySet()) ?: emptySet()
    fun pinUrl(url: String) {
        val set = getPinnedUrls().toMutableSet()
        set.add(url)
        prefs.edit().putStringSet("pinned_urls", set).apply()
    }
    fun unpinUrl(url: String) {
        val set = getPinnedUrls().toMutableSet()
        set.remove(url)
        prefs.edit().putStringSet("pinned_urls", set).apply()
    }
    fun updateBookmark(id: String, newTitle: String) {
        val list = getBookmarks().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(title = newTitle.ifBlank { list[idx].url })
            prefs.edit().putString("bookmarks", gson.toJson(list)).apply()
        }
    }

    fun searchBookmarks(query: String): List<Bookmark> {
        if (query.isBlank()) return getBookmarks()
        val q = query.lowercase()
        return getBookmarks().filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
    }

    // --- Tab groups (simple) ---
    data class TabGroup(val id: String = java.util.UUID.randomUUID().toString(), val name: String, val urls: MutableList<String> = mutableListOf())

    fun getTabGroups(): List<TabGroup> {
        val json = prefs.getString("tab_groups", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<TabGroup>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun saveTabGroups(groups: List<TabGroup>) {
        prefs.edit().putString("tab_groups", gson.toJson(groups)).apply()
    }

    fun isReduceMotion(): Boolean = prefs.getBoolean("reduce_motion", false)
    fun setReduceMotion(v: Boolean) { prefs.edit().putBoolean("reduce_motion", v).apply() }

    fun getDownloadsLog(): List<String> {
        val json = prefs.getString("downloads_log", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun addDownloadLog(entry: String) {
        val log = getDownloadsLog().toMutableList()
        log.add(0, entry)
        if (log.size > 100) log.subList(100, log.size).clear()
        prefs.edit().putString("downloads_log", gson.toJson(log)).apply()
    }

    fun isPinned(url: String): Boolean = url in getPinnedUrls()

    fun getBackgroundDim(): Float = prefs.getFloat("bg_dim", 0.35f)
    fun setBackgroundDim(dim: Float) {
        prefs.edit().putFloat("bg_dim", dim.coerceIn(0f, 0.8f)).apply()
    }

    fun exportAllSettings(): String {
        val data = mapOf(
            "search_engine" to getSearchEngine(),
            "custom_search_url" to getCustomSearchUrl(),
            "font_scale" to getFontScale(),
            "show_favorites" to isShowFavorites(),
            "reduce_motion" to isReduceMotion(),
            "bg_dim" to getBackgroundDim(),
            "bookmarks" to getBookmarks(),
            "tab_groups" to getTabGroups()
        )
        return gson.toJson(data)
    }

    fun importAllSettings(json: String): Boolean {
        return try {
            val map = gson.fromJson(json, Map::class.java) as? Map<*, *> ?: return false
            (map["search_engine"] as? String)?.let { setSearchEngine(it) }
            (map["custom_search_url"] as? String)?.let { setCustomSearchUrl(it) }
            (map["font_scale"] as? Double)?.let { setFontScale(it.toFloat()) }
            (map["show_favorites"] as? Boolean)?.let { setShowFavorites(it) }
            (map["reduce_motion"] as? Boolean)?.let { setReduceMotion(it) }
            (map["bg_dim"] as? Double)?.let { setBackgroundDim(it.toFloat()) }
            true
        } catch (_: Exception) { false }
    }
}