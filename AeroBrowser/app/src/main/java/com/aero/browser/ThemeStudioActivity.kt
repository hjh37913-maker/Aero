package com.aero.browser

import android.graphics.Color
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aero.browser.databinding.ActivityThemeStudioBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID

class ThemeStudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeStudioBinding
    private var editingTheme: AeroTheme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshThemeList()
        setupSliders()
        setupColorButtons()
        setupPalette()
        setupGradient()
        binding.btnCompare.setOnClickListener { compareThemes() }

        binding.btnApply.setOnClickListener { applyAndSave() }
        binding.btnDuplicate.setOnClickListener { duplicateCurrent() }
        binding.btnRename.setOnClickListener { renameCurrent() }
        binding.btnExport.setOnClickListener { exportCurrent() }
        binding.btnImport.setOnClickListener { importTheme() }
        binding.btnDelete.setOnClickListener { deleteCurrent() }
        binding.btnReset.setOnClickListener { resetToDefault() }
    }

    private fun refreshThemeList() {
        val themes = ThemeEngine.getAllThemes()
        val names = themes.map { if (it.isPreset) "${it.name} (пресет)" else it.name }.toTypedArray()
        binding.btnSelectTheme.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Оберіть тему")
                .setItems(names) { _, which ->
                    loadThemeIntoEditor(themes[which])
                }
                .show()
        }
    }

    private fun loadThemeIntoEditor(theme: AeroTheme) {
        editingTheme = theme
        binding.tvCurrent.text = "Редагування: ${theme.name}"
        binding.seekRadius.progress = theme.cornerRadius
        binding.seekOpacity.progress = (theme.glassOpacity * 100).toInt()
        binding.seekShadow.progress = theme.elevation
        applyPreview(theme)
    }

    private fun applyPreview(theme: AeroTheme) {
        try {
            binding.root.setBackgroundColor(Color.parseColor(theme.background))
            binding.previewCard.setCardBackgroundColor(Color.parseColor(theme.surfaceGlass))
            binding.previewCard.strokeColor = Color.parseColor(theme.borderGlass)
            binding.previewCard.radius = theme.cornerRadius.toFloat()
            binding.previewCard.cardElevation = theme.elevation.toFloat()
            binding.previewTitle.setTextColor(Color.parseColor(theme.textPrimary))
            binding.previewSubtitle.setTextColor(Color.parseColor(theme.textSecondary))
            binding.previewButton.setBackgroundColor(Color.parseColor(theme.accent))
            binding.tvCurrent.setTextColor(Color.parseColor(theme.textPrimary))

            // Color chips
            binding.chipBg.setBackgroundColor(Color.parseColor(theme.background))
            binding.chipSurface.setBackgroundColor(Color.parseColor(theme.surface))
            binding.chipAccent.setBackgroundColor(Color.parseColor(theme.accent))
            binding.chipAccent2.setBackgroundColor(Color.parseColor(theme.accentSecondary))
            binding.chipText.setBackgroundColor(Color.parseColor(theme.textPrimary))
            binding.chipText2.setBackgroundColor(Color.parseColor(theme.textSecondary))
            binding.chipGlass.setBackgroundColor(Color.parseColor(theme.surfaceGlass))
            binding.chipBorder.setBackgroundColor(Color.parseColor(theme.borderGlass))

            binding.labelRadius.text = "Скруглення: ${theme.cornerRadius}"
            binding.labelOpacity.text = "Прозорість скла: ${(theme.glassOpacity * 100).toInt()}%"
            binding.labelShadow.text = "Глибина: ${theme.elevation}"
            binding.switchGradient.isChecked = theme.useGradient
            binding.chipGradStart.setBackgroundColor(Color.parseColor(theme.gradientStart))
            binding.chipGradEnd.setBackgroundColor(Color.parseColor(theme.gradientEnd))
            binding.labelAngle.text = "Кут градієнта: ${theme.gradientAngle}°"
            binding.seekAngle.progress = theme.gradientAngle

            // Live gradient bar
            val barGd = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor(theme.gradientStart), Color.parseColor(theme.gradientEnd))
            )
            barGd.cornerRadius = 12f
            binding.gradientPreviewBar.background = barGd

            if (theme.useGradient) {
                val gd = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor(theme.gradientStart), Color.parseColor(theme.gradientEnd))
                )
                gd.cornerRadius = theme.cornerRadius.toFloat()
                binding.previewCard.background = gd
            }
        } catch (_: Exception) {}
    }

    private fun setupGradient() {
        binding.switchGradient.setOnCheckedChangeListener { _, checked ->
            editingTheme = editingTheme?.copy(useGradient = checked)
            editingTheme?.let { applyPreview(it) }
        }
        binding.chipGradStart.setOnClickListener { pickColor("Початок градієнта") { c -> editingTheme = editingTheme?.copy(gradientStart = c) } }
        binding.chipGradEnd.setOnClickListener { pickColor("Кінець градієнта") { c -> editingTheme = editingTheme?.copy(gradientEnd = c) } }
        binding.seekAngle.setOnSeekBarChangeListener(simpleListener { v ->
            editingTheme = editingTheme?.copy(gradientAngle = v)
            editingTheme?.let { applyPreview(it) }
        })

        // Preset gradients
        val presets = listOf(
            "#0EA5E9" to "#818CF8",
            "#A78BFA" to "#F472B6",
            "#34D399" to "#0EA5E9",
            "#F59E0B" to "#EF4444"
        )
        val presetViews = listOf(binding.gradPreset1, binding.gradPreset2, binding.gradPreset3, binding.gradPreset4)
        presets.forEachIndexed { i, (start, end) ->
            val gd = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor(start), Color.parseColor(end))
            )
            gd.cornerRadius = 8f
            presetViews[i].background = gd
            presetViews[i].setOnClickListener {
                editingTheme = editingTheme?.copy(gradientStart = start, gradientEnd = end, useGradient = true)
                binding.switchGradient.isChecked = true
                editingTheme?.let { applyPreview(it) }
                Toast.makeText(this, "Градієнт застосовано", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupColorButtons() {
        binding.chipBg.setOnClickListener { pickColor("Фон") { c -> editingTheme = editingTheme?.copy(background = c) } }
        binding.chipSurface.setOnClickListener { pickColor("Поверхня") { c -> editingTheme = editingTheme?.copy(surface = c) } }
        binding.chipAccent.setOnClickListener { pickColor("Акцент") { c -> editingTheme = editingTheme?.copy(accent = c) } }
        binding.chipAccent2.setOnClickListener { pickColor("Другий акцент") { c -> editingTheme = editingTheme?.copy(accentSecondary = c) } }
        binding.chipText.setOnClickListener { pickColor("Основний текст") { c -> editingTheme = editingTheme?.copy(textPrimary = c) } }
        binding.chipText2.setOnClickListener { pickColor("Другорядний текст") { c -> editingTheme = editingTheme?.copy(textSecondary = c) } }
        binding.chipGlass.setOnClickListener { pickColor("Скло") { c -> editingTheme = editingTheme?.copy(surfaceGlass = c) } }
        binding.chipBorder.setOnClickListener { pickColor("Рамка") { c -> editingTheme = editingTheme?.copy(borderGlass = c) } }
    }

    private fun setupPalette() {
        val colors = listOf("#0EA5E9", "#818CF8", "#A78BFA", "#F472B6", "#34D399")
        val views = listOf(binding.pal1, binding.pal2, binding.pal3, binding.pal4, binding.pal5)
        views.forEachIndexed { i, v ->
            v.setOnClickListener {
                editingTheme = editingTheme?.copy(accent = colors[i])
                editingTheme?.let { applyPreview(it) }
                Toast.makeText(this, "Акцент змінено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun compareThemes() {
        val themes = ThemeEngine.getAllThemes()
        val names = themes.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Оберіть першу тему")
            .setItems(names) { _, first ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Оберіть другу тему")
                    .setItems(names) { _, second ->
                        val a = themes[first]
                        val b = themes[second]
                        try {
                            binding.compareLeft.setCardBackgroundColor(Color.parseColor(a.surfaceGlass))
                            binding.compareLeftText.text = a.name
                            binding.compareLeftText.setTextColor(Color.parseColor(a.textPrimary))
                            binding.compareRight.setCardBackgroundColor(Color.parseColor(b.surfaceGlass))
                            binding.compareRightText.text = b.name
                            binding.compareRightText.setTextColor(Color.parseColor(b.textPrimary))
                        } catch (_: Exception) {}
                    }
                    .show()
            }
            .show()
    }

    private fun setupSliders() {
        binding.seekRadius.setOnSeekBarChangeListener(simpleListener { v ->
            editingTheme = editingTheme?.copy(cornerRadius = v)
            editingTheme?.let { applyPreview(it) }
        })
        binding.seekOpacity.setOnSeekBarChangeListener(simpleListener { v ->
            editingTheme = editingTheme?.copy(glassOpacity = v / 100f)
            editingTheme?.let { applyPreview(it) }
        })
        binding.seekShadow.setOnSeekBarChangeListener(simpleListener { v ->
            editingTheme = editingTheme?.copy(elevation = v)
            editingTheme?.let { applyPreview(it) }
        })
    }

    private fun simpleListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun pickColor(title: String, onPicked: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            hint = "#RRGGBB або #AARRGGBB"
            setText(editingTheme?.accent ?: "#4F46E5")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Застосувати") { _, _ ->
                val hex = input.text.toString().trim()
                if (hex.matches(Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$"))) {
                    onPicked(hex)
                    editingTheme?.let { applyPreview(it) }
                } else {
                    Toast.makeText(this, "Невірний HEX", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun applyAndSave() {
        val theme = editingTheme ?: run {
            Toast.makeText(this, "Спочатку оберіть тему", Toast.LENGTH_SHORT).show()
            return
        }
        if (theme.isPreset) {
            Toast.makeText(this, "Пресети захищені. Спочатку дублюйте.", Toast.LENGTH_LONG).show()
            return
        }
        ThemeEngine.saveCustomTheme(theme)
        ThemeEngine.setCurrentTheme(theme.id)
        Toast.makeText(this, "Тему збережено і застосовано", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun duplicateCurrent() {
        val source = editingTheme ?: ThemeEngine.getCurrentTheme()
        val input = android.widget.EditText(this).apply { setText("${source.name} копія") }
        MaterialAlertDialogBuilder(this)
            .setTitle("Дублювати")
            .setView(input)
            .setPositiveButton("Створити") { _, _ ->
                val copy = ThemeEngine.duplicateTheme(source, input.text.toString().ifBlank { "Нова тема" })
                ThemeEngine.saveCustomTheme(copy)
                loadThemeIntoEditor(copy)
                Toast.makeText(this, "Створено копію", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun renameCurrent() {
        val theme = editingTheme ?: return
        if (theme.isPreset) {
            Toast.makeText(this, "Пресети не перейменовуються", Toast.LENGTH_SHORT).show()
            return
        }
        val input = android.widget.EditText(this).apply { setText(theme.name) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Нова назва")
            .setView(input)
            .setPositiveButton("Зберегти") { _, _ ->
                val updated = theme.copy(name = input.text.toString().ifBlank { theme.name })
                ThemeEngine.saveCustomTheme(updated)
                loadThemeIntoEditor(updated)
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun exportCurrent() {
        val theme = editingTheme ?: return
        val json = ThemeEngine.exportTheme(theme)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, json)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Aero Theme: ${theme.name}")
        }
        startActivity(android.content.Intent.createChooser(intent, "Експорт"))
    }

    private fun importTheme() {
        val input = android.widget.EditText(this).apply { hint = "Вставте JSON теми" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Імпорт")
            .setView(input)
            .setPositiveButton("Імпортувати") { _, _ ->
                val imported = ThemeEngine.importTheme(input.text.toString())
                if (imported != null) {
                    val fixed = imported.copy(id = "imported_${UUID.randomUUID().toString().take(6)}", isPreset = false)
                    ThemeEngine.saveCustomTheme(fixed)
                    loadThemeIntoEditor(fixed)
                    Toast.makeText(this, "Імпортовано", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Невірний JSON", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun deleteCurrent() {
        val theme = editingTheme ?: return
        if (theme.isPreset) {
            Toast.makeText(this, "Пресети захищені", Toast.LENGTH_SHORT).show()
            return
        }
        ThemeEngine.deleteCustomTheme(theme.id)
        editingTheme = null
        binding.tvCurrent.text = "Тему видалено"
        Toast.makeText(this, "Видалено", Toast.LENGTH_SHORT).show()
    }

    private fun resetToDefault() {
        ThemeEngine.setCurrentTheme("arctic")
        Toast.makeText(this, "Повернуто Arctic Glass", Toast.LENGTH_SHORT).show()
        finish()
    }
}
