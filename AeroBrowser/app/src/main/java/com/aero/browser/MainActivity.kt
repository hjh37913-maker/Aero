package com.aero.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aero.browser.data.DataStore
import com.aero.browser.databinding.*
import java.net.URLEncoder
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tabs = mutableListOf<BrowserTab>()
    private var currentTabIndex = 0
    private var isIncognito = false
    private var backgroundUri: Uri? = null

    data class BrowserTab(
        val id: String = UUID.randomUUID().toString(),
        var url: String = "",
        var title: String = "Нова вкладка",
        val webView: WebView
    )

    data class FavoriteSite(val title: String, val url: String)

    private val favorites = mutableListOf(
        FavoriteSite("Google", "https://google.com"),
        FavoriteSite("YouTube", "https://youtube.com"),
        FavoriteSite("Wikipedia", "https://uk.wikipedia.org")
    )

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            backgroundUri = it
            binding.startPageBackground.setImageURI(it)
            Toast.makeText(this, "Фон оновлено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeEngine.init(this)
        DataStore.init(this)
        requestNotificationPermission()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge for Android 12+
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            window.setDecorFitsSystemWindows(false)
        }

        applyTheme()
        applyFontScale()
        binding.startPageBackground.alpha = DataStore.getBackgroundDim()
        setupStartPage()
        setupListeners()
        createNewTab(showHome = true)
    }

    private fun applyFontScale() {
        val scale = DataStore.getFontScale()
        binding.logoText.textSize = 52f * scale
        binding.searchInput.textSize = 16f * scale
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun applyTheme() {
        val t = ThemeEngine.getCurrentTheme()
        try {
            binding.root.setBackgroundColor(android.graphics.Color.parseColor(t.background))
            binding.logoText.setTextColor(android.graphics.Color.parseColor(t.textPrimary))
            binding.logoText.textSize = 52f * t.fontScale
            binding.bottomBar.setCardBackgroundColor(android.graphics.Color.parseColor(t.surfaceGlass))
            binding.bottomBar.strokeColor = android.graphics.Color.parseColor(t.borderGlass)
            binding.bottomBar.radius = t.cornerRadius.toFloat()
            binding.bottomBar.cardElevation = t.elevation.toFloat()
            binding.searchCard.setCardBackgroundColor(android.graphics.Color.parseColor(t.surfaceGlass))
            binding.searchCard.strokeColor = android.graphics.Color.parseColor(t.borderGlass)
            binding.searchCard.radius = t.cornerRadiusSmall.toFloat()
            binding.searchInput.setTextColor(android.graphics.Color.parseColor(t.textPrimary))
            binding.startPageBackground.alpha = t.backgroundDim
            if (t.useGradient) {
                val gd = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        android.graphics.Color.parseColor(t.gradientStart),
                        android.graphics.Color.parseColor(t.gradientEnd)
                    )
                )
                binding.root.background = gd
            }
        } catch (_: Exception) {}
    }

    private fun setupStartPage() {
        binding.favoritesRecycler.layoutManager = GridLayoutManager(this, 3)
        binding.favoritesRecycler.adapter = FavoritesAdapter(favorites) { site ->
            loadUrlOrSearch(site.url)
        }
        binding.favoritesRecycler.visibility = if (DataStore.isShowFavorites()) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            val q = binding.searchInput.text.toString().trim()
            if (q.isNotEmpty()) loadUrlOrSearch(q)
            true
        }
        binding.btnHome.setOnClickListener { showStartPage() }
        binding.btnBack.setOnClickListener { goBack() }
        binding.btnTabs.setOnClickListener { showTabs() }
        binding.btnMenu.setOnClickListener { showMenu() }
        binding.btnChangeBackground.setOnClickListener { pickBackground() }
    }

    private fun pickBackground() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 100)
                return
            }
        }
        imagePicker.launch("image/*")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewTab(showHome: Boolean = false) {
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowFileAccess = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val tab = tabs.getOrNull(currentTabIndex) ?: return
                    tab.url = url ?: ""
                    tab.title = view?.title?.ifBlank { null } ?: url ?: "Вкладка"
                    if (!isIncognito && !url.isNullOrBlank() && !url.startsWith("data:")) {
                        DataStore.addHistory(tab.title, url, false)
                    }
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        view?.loadData("<html><body style='padding:40px;text-align:center;font-family:sans-serif'><h2>Помилка завантаження</h2><p>Перевірте інтернет</p></body></html>", "text/html", "UTF-8")
                    }
                }
            }
            webChromeClient = WebChromeClient()
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                try {
                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                        setMimeType(mimeType)
                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
                    }
                    val dm = getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    dm.enqueue(request)
                    DataStore.addDownloadLog("${android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)} — розпочато")
                    Toast.makeText(this@MainActivity, "Завантаження розпочато", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Не вдалося почати завантаження", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val tab = BrowserTab(webView = wv)
        tabs.add(tab)
        currentTabIndex = tabs.lastIndex
        switchTab()
        if (showHome) showStartPage()
    }

    private fun switchTab() {
        binding.webContainer.removeAllViews()
        tabs.getOrNull(currentTabIndex)?.let { binding.webContainer.addView(it.webView) }
    }

    private fun loadUrlOrSearch(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> DataStore.buildSearchUrl(input)
        }
        showBrowser()
        tabs.getOrNull(currentTabIndex)?.webView?.loadUrl(url)
    }

    private fun showStartPage() {
        binding.startPage.visibility = View.VISIBLE
        binding.webContainer.visibility = View.GONE
    }

    private fun showBrowser() {
        binding.startPage.visibility = View.GONE
        binding.webContainer.visibility = View.VISIBLE
    }

    private fun goBack() {
        val wv = tabs.getOrNull(currentTabIndex)?.webView
        if (wv?.canGoBack() == true) wv.goBack()
        else if (binding.webContainer.visibility == View.VISIBLE) showStartPage()
        else finish()
    }

    private fun showTabs() {
        val titles = tabs.mapIndexed { i, t -> "${if (i == currentTabIndex) "● " else ""}${t.title}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Вкладки (${tabs.size})")
            .setItems(titles) { _, which ->
                currentTabIndex = which
                switchTab()
                if (tabs[which].url.isBlank()) showStartPage() else showBrowser()
            }
            .setPositiveButton("Нова") { _, _ -> createNewTab(true) }
            .setNeutralButton("Закрити поточну") { _, _ -> closeCurrentTab() }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun closeCurrentTab() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        if (DataStore.isPinned(tab.url)) {
            Toast.makeText(this, "Закріплену вкладку не можна закрити. Спочатку відкріпіть.", Toast.LENGTH_LONG).show()
            return
        }
        if (tabs.size <= 1) {
            Toast.makeText(this, "Має бути хоча б одна вкладка", Toast.LENGTH_SHORT).show()
            return
        }
        tabs.removeAt(currentTabIndex).webView.destroy()
        if (currentTabIndex >= tabs.size) currentTabIndex = tabs.lastIndex
        switchTab()
        showTabs()
    }

    private fun showMenu() {
        val items = arrayOf(
            "Theme Studio",
            "Пошукова система",
            "Закладки",
            "Історія",
            "Додати в закладки",
            if (isIncognito) "Вимкнути інкогніто" else "Увімкнути інкогніто",
            "Приватність і дані",
            "Резервне копіювання",
            "Діагностика",
            "Змінити фон",
            "Налаштування інтерфейсу",
            "Знайти на сторінці",
            "Закріпити вкладку",
            "Режим читання",
            "Перекласти сторінку",
            "Групи вкладок",
            "Налаштування фону",
            "Завантаження",
            "Зменшити анімації",
            "Недавні сайти",
            "Дозволи сайтів",
            "Експорт усіх налаштувань",
            "Імпорт налаштувань",
            "Про Aero (Android 12+)"
        )
        AlertDialog.Builder(this)
            .setTitle("Aero")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> startActivity(Intent(this, ThemeStudioActivity::class.java))
                    1 -> showSearchEngineDialog()
                    2 -> openBookmarks()
                    3 -> openHistory()
                    4 -> addCurrentBookmark()
                    5 -> {
                        isIncognito = !isIncognito
                        Toast.makeText(this, if (isIncognito) "Інкогніто увімкнено" else "Інкогніто вимкнено", Toast.LENGTH_SHORT).show()
                    }
                    6 -> showPrivacyDialog()
                    7 -> showBackupDialog()
                    8 -> showDiagnostics()
                    9 -> pickBackground()
                    10 -> showInterfaceSettings()
                    11 -> findOnPage()
                    12 -> pinCurrentTab()
                    13 -> openReadingMode()
                    14 -> translatePage()
                    15 -> showTabGroups()
                    16 -> adjustBackground()
                    17 -> showDownloads()
                    18 -> toggleReduceMotion()
                    19 -> showRecentSites()
                    20 -> showPermissionsInfo()
                    21 -> exportAllSettings()
                    22 -> importAllSettings()
                    23 -> showAbout()
                }
            }.show()
    }

    private fun showAbout() {
        val info = """
            Aero Browser
            Оптимізовано для Android 12+ та One UI 4.1+
            
            • Edge-to-edge інтерфейс
            • Підтримка сучасних дозволів
            • Системний DownloadManager
            • Локальне зберігання всіх даних
            • Без серверів і реєстрації
            
            Версія: 1.0 (Stage 5+)
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Про Aero")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPermissionsInfo() {
        val info = """
            Aero не надає дозволи сайтам автоматично.
            
            Камера, мікрофон і геолокація запитуються системою Android лише коли сайт їх вимагає.
            
            Ви можете керувати дозволами в системних налаштуваннях Android:
            Налаштування → Додатки → Aero → Дозволи.
            
            Cookies і дані сайтів можна очистити через меню «Приватність і дані».
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Дозволи сайтів")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun exportAllSettings() {
        val json = DataStore.exportAllSettings()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "Aero Settings Backup")
        }
        startActivity(Intent.createChooser(intent, "Експорт налаштувань"))
    }

    private fun importAllSettings() {
        val input = android.widget.EditText(this).apply {
            hint = "Вставте JSON налаштувань"
            minLines = 4
        }
        AlertDialog.Builder(this)
            .setTitle("Імпорт налаштувань")
            .setView(input)
            .setPositiveButton("Імпортувати") { _, _ ->
                if (DataStore.importAllSettings(input.text.toString())) {
                    Toast.makeText(this, "Налаштування імпортовано. Перезапустіть додаток.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Невірний або пошкоджений файл", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showDownloads() {
        val log = DataStore.getDownloadsLog()
        if (log.isEmpty()) {
            Toast.makeText(this, "Немає записів про завантаження", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Завантаження")
            .setItems(log.toTypedArray()) { _, _ -> }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun toggleReduceMotion() {
        val current = DataStore.isReduceMotion()
        DataStore.setReduceMotion(!current)
        Toast.makeText(this, if (!current) "Анімації зменшено" else "Анімації увімкнено", Toast.LENGTH_SHORT).show()
    }

    private fun showRecentSites() {
        val recent = DataStore.getHistory().take(15)
        if (recent.isEmpty()) {
            Toast.makeText(this, "Немає недавніх сайтів", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Недавні сайти")
            .setItems(recent.map { it.title }.toTypedArray()) { _, which ->
                loadUrlOrSearch(recent[which].url)
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun openReadingMode() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        if (tab.url.isBlank()) {
            Toast.makeText(this, "Немає сторінки", Toast.LENGTH_SHORT).show()
            return
        }
        tab.webView.evaluateJavascript(
            "(function(){var t=document.body?document.body.innerText:'';return t.substring(0,8000);})()"
        ) { result ->
            val text = result?.trim('"')?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
            if (text.length < 50) {
                Toast.makeText(this, "Не вдалося витягти текст (сайт несумісний або захищений)", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }
            val scroll = android.widget.ScrollView(this)
            val tv = android.widget.TextView(this).apply {
                this.text = text
                textSize = 16f * DataStore.getFontScale()
                setPadding(48, 32, 48, 32)
                setTextIsSelectable(true)
            }
            scroll.addView(tv)
            AlertDialog.Builder(this)
                .setTitle(tab.title)
                .setView(scroll)
                .setPositiveButton("Закрити", null)
                .show()
        }
    }

    private fun translatePage() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        if (tab.url.isBlank()) return
        val translateUrl = "https://translate.google.com/translate?sl=auto&tl=uk&u=${java.net.URLEncoder.encode(tab.url, "UTF-8")}"
        AlertDialog.Builder(this)
            .setTitle("Переклад сторінки")
            .setMessage("Відкрити переклад через Google Translate? Вміст сторінки буде передано зовнішньому сервісу.")
            .setPositiveButton("Відкрити") { _, _ -> loadUrlOrSearch(translateUrl) }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showTabGroups() {
        val groups = DataStore.getTabGroups().toMutableList()
        val names = if (groups.isEmpty()) arrayOf("Немає груп") else groups.map { "${it.name} (${it.urls.size})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Групи вкладок")
            .setItems(names) { _, which ->
                if (groups.isEmpty()) return@setItems
                val g = groups[which]
                AlertDialog.Builder(this)
                    .setTitle(g.name)
                    .setItems(g.urls.toTypedArray()) { _, u -> loadUrlOrSearch(g.urls[u]) }
                    .setPositiveButton("Додати поточну") { _, _ ->
                        val url = tabs.getOrNull(currentTabIndex)?.url
                        if (!url.isNullOrBlank() && !g.urls.contains(url)) {
                            g.urls.add(url)
                            DataStore.saveTabGroups(groups)
                            Toast.makeText(this, "Додано до групи", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Видалити групу") { _, _ ->
                        groups.removeAt(which)
                        DataStore.saveTabGroups(groups)
                    }
                    .setNeutralButton("Закрити", null)
                    .show()
            }
            .setPositiveButton("Нова група") { _, _ ->
                val input = android.widget.EditText(this).apply { hint = "Назва групи" }
                AlertDialog.Builder(this)
                    .setTitle("Нова група")
                    .setView(input)
                    .setPositiveButton("Створити") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotEmpty()) {
                            groups.add(DataStore.TabGroup(name = name))
                            DataStore.saveTabGroups(groups)
                            Toast.makeText(this, "Групу створено", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Скасувати", null)
                    .show()
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun adjustBackground() {
        val seek = android.widget.SeekBar(this).apply {
            max = 80
            progress = (DataStore.getBackgroundDim() * 100).toInt()
        }
        AlertDialog.Builder(this)
            .setTitle("Затемнення фону")
            .setView(seek)
            .setPositiveButton("Зберегти") { _, _ ->
                val dim = seek.progress / 100f
                DataStore.setBackgroundDim(dim)
                binding.startPageBackground.alpha = dim
                Toast.makeText(this, "Затемнення збережено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showInterfaceSettings() {
        val options = arrayOf(
            "Розмір шрифту: ${(DataStore.getFontScale() * 100).toInt()}%",
            if (DataStore.isShowFavorites()) "Сховати обрані" else "Показати обрані",
            "Скинути розмір шрифту"
        )
        AlertDialog.Builder(this)
            .setTitle("Налаштування інтерфейсу")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val seek = android.widget.SeekBar(this).apply {
                            max = 80
                            progress = ((DataStore.getFontScale() - 0.8f) * 100).toInt()
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Розмір шрифту")
                            .setView(seek)
                            .setPositiveButton("Зберегти") { _, _ ->
                                DataStore.setFontScale(0.8f + seek.progress / 100f)
                                applyFontScale()
                                Toast.makeText(this, "Розмір збережено", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Скасувати", null)
                            .show()
                    }
                    1 -> {
                        DataStore.setShowFavorites(!DataStore.isShowFavorites())
                        setupStartPage()
                        Toast.makeText(this, "Оновлено", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        DataStore.setFontScale(1.0f)
                        applyFontScale()
                    }
                }
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun findOnPage() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        val input = android.widget.EditText(this).apply { hint = "Текст для пошуку" }
        AlertDialog.Builder(this)
            .setTitle("Знайти на сторінці")
            .setView(input)
            .setPositiveButton("Знайти") { _, _ ->
                val query = input.text.toString()
                if (query.isNotBlank()) {
                    tab.webView.findAllAsync(query)
                    Toast.makeText(this, "Пошук запущено", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Наступний") { _, _ -> tab.webView.findNext(true) }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun pinCurrentTab() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        if (tab.url.isBlank()) {
            Toast.makeText(this, "Немає сторінки для закріплення", Toast.LENGTH_SHORT).show()
            return
        }
        if (DataStore.isPinned(tab.url)) {
            DataStore.unpinUrl(tab.url)
            Toast.makeText(this, "Вкладку відкріплено", Toast.LENGTH_SHORT).show()
        } else {
            DataStore.pinUrl(tab.url)
            Toast.makeText(this, "Вкладку закріплено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSearchEngineDialog() {
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Власний URL")
        val current = when (DataStore.getSearchEngine()) {
            "bing" -> 1
            "duckduckgo" -> 2
            "custom" -> 3
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("Пошукова система")
            .setSingleChoiceItems(engines, current) { dialog, which ->
                when (which) {
                    0 -> DataStore.setSearchEngine("google")
                    1 -> DataStore.setSearchEngine("bing")
                    2 -> DataStore.setSearchEngine("duckduckgo")
                    3 -> {
                        val input = android.widget.EditText(this).apply {
                            setText(DataStore.getCustomSearchUrl())
                            hint = "https://example.com/search?q=%s"
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Власний пошук")
                            .setView(input)
                            .setPositiveButton("Зберегти") { _, _ ->
                                val url = input.text.toString().trim()
                                if (url.contains("%s")) {
                                    DataStore.setSearchEngine("custom")
                                    DataStore.setCustomSearchUrl(url)
                                    Toast.makeText(this, "Власний пошук збережено", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "URL має містити %s", Toast.LENGTH_LONG).show()
                                }
                            }
                            .setNegativeButton("Скасувати", null)
                            .show()
                    }
                }
                if (which != 3) Toast.makeText(this, "Пошукову систему змінено", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Приватність і дані")
            .setItems(arrayOf("Очистити історію", "Очистити кеш WebView", "Очистити cookies", "Очистити все")) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("Підтвердження")
                    .setMessage("Цю дію не можна скасувати. Продовжити?")
                    .setPositiveButton("Так") { _, _ ->
                        when (which) {
                            0 -> { DataStore.clearHistory(); Toast.makeText(this, "Історію очищено", Toast.LENGTH_SHORT).show() }
                            1 -> {
                                tabs.forEach { it.webView.clearCache(true) }
                                Toast.makeText(this, "Кеш очищено", Toast.LENGTH_SHORT).show()
                            }
                            2 -> {
                                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                Toast.makeText(this, "Cookies очищено", Toast.LENGTH_SHORT).show()
                            }
                            3 -> {
                                DataStore.clearHistory()
                                tabs.forEach { it.webView.clearCache(true) }
                                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                Toast.makeText(this, "Дані очищено", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Скасувати", null)
                    .show()
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun showBackupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Резервне копіювання")
            .setItems(arrayOf("Експорт закладок", "Імпорт закладок", "Експорт поточної теми")) { _, which ->
                when (which) {
                    0 -> {
                        val json = DataStore.exportBookmarks()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "Aero Bookmarks")
                        }
                        startActivity(Intent.createChooser(intent, "Експорт закладок"))
                    }
                    1 -> {
                        val input = android.widget.EditText(this).apply { hint = "Вставте JSON закладок" }
                        AlertDialog.Builder(this)
                            .setTitle("Імпорт закладок")
                            .setView(input)
                            .setPositiveButton("Імпортувати") { _, _ ->
                                if (DataStore.importBookmarks(input.text.toString())) {
                                    Toast.makeText(this, "Закладки імпортовано", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Невірний формат", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Скасувати", null)
                            .show()
                    }
                    2 -> {
                        val theme = ThemeEngine.getCurrentTheme()
                        val json = ThemeEngine.exportTheme(theme)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "Aero Theme: ${theme.name}")
                        }
                        startActivity(Intent.createChooser(intent, "Експорт теми"))
                    }
                }
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun showDiagnostics() {
        val webViewVersion = try {
            android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: "невідомо"
        } catch (_: Exception) { "невідомо" }
        val info = """
            Aero Browser
            Версія додатку: 1.0
            Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
            WebView: $webViewVersion
            Відкритих вкладок: ${tabs.size}
            Пошукова система: ${DataStore.getSearchEngine()}
            Інкогніто: ${if (isIncognito) "увімкнено" else "вимкнено"}
            Закладок: ${DataStore.getBookmarks().size}
            Записів історії: ${DataStore.getHistory().size}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Діагностика")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openBookmarks() {
        val list = DataStore.getBookmarks()
        if (list.isEmpty()) {
            Toast.makeText(this, "Немає закладок", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = list.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Закладки")
            .setItems(titles) { _, which ->
                val bm = list[which]
                AlertDialog.Builder(this)
                    .setTitle(bm.title)
                    .setMessage(bm.url)
                    .setPositiveButton("Відкрити") { _, _ -> loadUrlOrSearch(bm.url) }
                    .setNegativeButton("Видалити") { _, _ ->
                        DataStore.removeBookmark(bm.id)
                        Toast.makeText(this, "Закладку видалено", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Перейменувати") { _, _ ->
                        val input = android.widget.EditText(this).apply { setText(bm.title) }
                        AlertDialog.Builder(this)
                            .setTitle("Нова назва")
                            .setView(input)
                            .setPositiveButton("Зберегти") { _, _ ->
                                DataStore.updateBookmark(bm.id, input.text.toString())
                                Toast.makeText(this, "Назву змінено", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Скасувати", null)
                            .show()
                    }
                    .show()
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun openHistory() {
        val list = DataStore.getHistory()
        if (list.isEmpty()) {
            Toast.makeText(this, "Історія порожня", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = list.map { "${it.title}\n${it.url.take(40)}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Історія")
            .setItems(titles) { _, which ->
                val entry = list[which]
                AlertDialog.Builder(this)
                    .setTitle(entry.title)
                    .setMessage(entry.url)
                    .setPositiveButton("Відкрити") { _, _ -> loadUrlOrSearch(entry.url) }
                    .setNegativeButton("Видалити") { _, _ ->
                        DataStore.removeHistory(entry.id)
                        Toast.makeText(this, "Запис видалено", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Закрити", null)
                    .show()
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun addCurrentBookmark() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        if (tab.url.isBlank()) return
        DataStore.addBookmark(tab.title, tab.url)
        Toast.makeText(this, "Додано в закладки", Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() = goBack()

    override fun onDestroy() {
        tabs.forEach { it.webView.destroy() }
        super.onDestroy()
    }

    class FavoritesAdapter(
        private val items: List<FavoriteSite>,
        private val onClick: (FavoriteSite) -> Unit
    ) : RecyclerView.Adapter<FavoritesAdapter.VH>() {
        class VH(val b: ItemFavoriteBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(p: ViewGroup, v: Int) = VH(ItemFavoriteBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            h.b.title.text = items[pos].title
            h.b.root.setOnClickListener { onClick(items[pos]) }
        }
        override fun getItemCount() = items.size
    }
}
