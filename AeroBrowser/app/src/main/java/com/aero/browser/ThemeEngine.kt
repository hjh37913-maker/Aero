package com.aero.browser

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AeroTheme(
    val id: String,
    val name: String,
    val isPreset: Boolean = false,
    val version: Int = 2,
    // Colors
    val background: String = "#F8F9FC",
    val surface: String = "#FFFFFF",
    val surfaceVariant: String = "#F1F5F9",
    val surfaceGlass: String = "#E6FFFFFF",
    val borderGlass: String = "#33FFFFFF",
    val textPrimary: String = "#1A1A1A",
    val textSecondary: String = "#6B7280",
    val accent: String = "#4F46E5",
    val accentSecondary: String = "#818CF8",
    val link: String = "#2563EB",
    val error: String = "#DC2626",
    val toolbar: String = "#FFFFFF",
    val navigationBar: String = "#FFFFFF",
    // Geometry & effects
    val cornerRadius: Int = 24,
    val cornerRadiusSmall: Int = 12,
    val blurIntensity: Int = 20,
    val shadowIntensity: Int = 8,
    val glassOpacity: Float = 0.9f,
    val borderWidth: Int = 1,
    val elevation: Int = 6,
    // Typography
    val fontScale: Float = 1.0f,
    val letterSpacing: Float = 0f,
    // Background
    val backgroundDim: Float = 0.35f,
    val coverImage: String? = null,
    val useGradient: Boolean = false,
    val gradientStart: String = "#0EA5E9",
    val gradientEnd: String = "#818CF8",
    val gradientAngle: Int = 45
) {
    fun withSafeRanges(): AeroTheme = copy(
        cornerRadius = cornerRadius.coerceIn(4, 48),
        cornerRadiusSmall = cornerRadiusSmall.coerceIn(4, 32),
        blurIntensity = blurIntensity.coerceIn(0, 40),
        shadowIntensity = shadowIntensity.coerceIn(0, 24),
        glassOpacity = glassOpacity.coerceIn(0.3f, 1f),
        borderWidth = borderWidth.coerceIn(0, 4),
        elevation = elevation.coerceIn(0, 16),
        fontScale = fontScale.coerceIn(0.8f, 1.5f),
        backgroundDim = backgroundDim.coerceIn(0f, 0.8f),
        gradientAngle = gradientAngle.coerceIn(0, 360)
    )
}

object ThemeEngine {
    private const val PREFS = "aero_themes"
    private const val KEY_CURRENT = "current_theme_id"
    private const val KEY_CUSTOM = "custom_themes"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    val presets = listOf(
        AeroTheme("arctic", "Arctic Glass", true, 2, "#E8F4F8", "#FFFFFF", "#F0F9FF", "#CCFFFFFF", "#40FFFFFF", "#0F172A", "#64748B", "#0EA5E9", "#38BDF8", "#0284C7", "#EF4444", "#FFFFFF", "#FFFFFF", 28, 14, 25, 6, 0.85f, 1, 4, 1.0f, 0f, 0.3f),
        AeroTheme("midnight", "Midnight", true, 2, "#0F172A", "#1E293B", "#334155", "#CC1E293B", "#20FFFFFF", "#F1F5F9", "#94A3B8", "#818CF8", "#A5B4FC", "#6366F1", "#F87171", "#1E293B", "#0F172A", 20, 10, 15, 12, 0.75f, 1, 8, 1.0f, 0f, 0.5f),
        AeroTheme("pearl", "Pearl", true, 2, "#FDFCFB", "#FFFFFF", "#FAF5FF", "#E8FFFFFF", "#30FFFFFF", "#1C1917", "#78716C", "#A78BFA", "#C4B5FD", "#8B5CF6", "#EF4444", "#FFFFFF", "#FFFFFF", 32, 16, 18, 4, 0.92f, 1, 3, 1.0f, 0f, 0.25f),
        AeroTheme("graphite", "Graphite", true, 2, "#1C1C1E", "#2C2C2E", "#3A3A3C", "#CC2C2C2E", "#25FFFFFF", "#F5F5F7", "#A1A1AA", "#71717A", "#A1A1AA", "#52525B", "#F87171", "#2C2C2E", "#1C1C1E", 16, 8, 12, 10, 0.8f, 1, 6, 1.0f, 0f, 0.45f),
        AeroTheme("aurora", "Aurora", true, 2, "#0C1222", "#1A1F35", "#252B45", "#CC1A1F35", "#30A5B4FC", "#E0E7FF", "#A5B4FC", "#818CF8", "#C7D2FE", "#6366F1", "#FB7185", "#1A1F35", "#0C1222", 26, 12, 22, 14, 0.78f, 1, 10, 1.0f, 0f, 0.4f)
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getCurrentTheme(): AeroTheme {
        val id = prefs.getString(KEY_CURRENT, "arctic") ?: "arctic"
        return (getThemeById(id) ?: presets.first()).withSafeRanges()
    }

    fun getThemeById(id: String): AeroTheme? {
        return presets.find { it.id == id } ?: getCustomThemes().find { it.id == id }
    }

    fun getAllThemes(): List<AeroTheme> = presets + getCustomThemes()

    fun getCustomThemes(): List<AeroTheme> {
        val json = prefs.getString(KEY_CUSTOM, "[]") ?: "[]"
        return try {
            val list: List<AeroTheme> = gson.fromJson(json, object : TypeToken<List<AeroTheme>>() {}.type) ?: emptyList()
            list.map { it.withSafeRanges() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomTheme(theme: AeroTheme) {
        val safe = theme.withSafeRanges().copy(isPreset = false, version = 2)
        val list = getCustomThemes().toMutableList()
        val index = list.indexOfFirst { it.id == safe.id }
        if (index >= 0) list[index] = safe else list.add(safe)
        prefs.edit().putString(KEY_CUSTOM, gson.toJson(list)).apply()
    }

    fun deleteCustomTheme(id: String) {
        val list = getCustomThemes().filter { it.id != id }
        prefs.edit().putString(KEY_CUSTOM, gson.toJson(list)).apply()
    }

    fun setCurrentTheme(id: String) {
        prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    fun exportTheme(theme: AeroTheme): String = gson.toJson(theme.withSafeRanges())

    fun importTheme(json: String): AeroTheme? = try {
        gson.fromJson(json, AeroTheme::class.java)?.withSafeRanges()
    } catch (e: Exception) {
        null
    }

    fun duplicateTheme(source: AeroTheme, newName: String): AeroTheme {
        return source.copy(
            id = "custom_${System.currentTimeMillis()}",
            name = newName,
            isPreset = false,
            version = 2
        )
    }
}
