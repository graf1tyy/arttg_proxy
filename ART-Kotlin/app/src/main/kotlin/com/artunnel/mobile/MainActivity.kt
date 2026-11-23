package com.artunnel.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusPill: TextView
    private lateinit var headline: TextView
    private lateinit var description: TextView
    private lateinit var primaryButton: TextView
    private lateinit var telegramButton: TextView
    private lateinit var portValue: TextView
    private lateinit var errorText: TextView

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshState()
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        buildScreen()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(28))
        }
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { _, windowInsets ->
                val bars = windowInsets.getInsets(WindowInsets.Type.systemBars())
                root.setPadding(dp(22), dp(22) + bars.top, dp(22), dp(28) + bars.bottom)
                windowInsets
            }
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label("ARTproxy", 21f, WHITE, true))
            addView(label("PRIVATE ROUTE FOR TELEGRAM", 9f, MUTED, true).apply {
                letterSpacing = 0.11f
            })
        }
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("ANDROID", 9f, MUTED, true).apply {
            letterSpacing = 0.12f
            background = round(PANEL_ALT, 30)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        })
        root.addView(header)

        root.addView(space(34))
        root.addView(label("ПОДКЛЮЧЕНИЕ", 10f, MUTED, true).apply {
            letterSpacing = 0.18f
        })
        root.addView(space(9))
        root.addView(label("Твой маршрут. Без лишних серверов.", 25f, WHITE, true))
        root.addView(space(8))
        root.addView(label(
            "ART автоматически поднимает локальный мост и выбирает рабочий сетевой маршрут.",
            14f,
            SUBTLE,
        ))

        root.addView(space(22))
        val connectionCard = card()
        statusPill = label("●  ОТКЛЮЧЕНО", 10f, MUTED, true).apply {
            letterSpacing = 0.07f
            background = round(PANEL_ALT, 30)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }
        headline = label("ART готов к запуску", 22f, WHITE, true)
        description = label(
            "Нажми кнопку, чтобы запустить локальный ART-мост для Telegram.",
            13f,
            SUBTLE,
        )
        errorText = label("", 11f, WARNING)
        connectionCard.addView(statusPill, wrap())
        connectionCard.addView(space(19))
        connectionCard.addView(headline)
        connectionCard.addView(space(8))
        connectionCard.addView(description)
        connectionCard.addView(space(9))
        connectionCard.addView(errorText)
        connectionCard.addView(space(12))
        val portRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        portRow.addView(label("ЛОКАЛЬНЫЙ МОСТ", 9f, MUTED, true), LinearLayout.LayoutParams(0, dp(20), 1f))
        portValue = label("127.0.0.1:1443", 11f, WHITE, true).apply {
            typeface = Typeface.MONOSPACE
        }
        portRow.addView(portValue)
        connectionCard.addView(portRow)
        root.addView(connectionCard)

        root.addView(space(13))
        primaryButton = button("ЗАПУСТИТЬ ART", primary = true).apply {
            setOnClickListener { toggleProxy() }
        }
        root.addView(primaryButton, fullWidth())
        root.addView(space(10))
        telegramButton = button("ОТКРЫТЬ TELEGRAM", primary = false).apply {
            isEnabled = false
            alpha = 0.42f
            setOnClickListener { openTelegram() }
        }
        root.addView(telegramButton, fullWidth())

        root.addView(space(22))
        val routeCard = card()
        routeCard.addView(label("СЕТЕВОЙ МАРШРУТ", 10f, MUTED, true).apply {
            letterSpacing = 0.14f
        })
        routeCard.addView(space(15))
        addRouteRow(routeCard, "01", "WebSocket / WSS", "Основной транспорт")
        routeCard.addView(separator())
        addRouteRow(routeCard, "02", "Cloudflare", "Альтернативный маршрут")
        routeCard.addView(separator())
        addRouteRow(routeCard, "03", "TCP fallback", "Резерв при сбое WS")
        root.addView(routeCard)

        root.addView(space(17))
        val note = card()
        note.background = round(PANEL_DARK, 20)
        note.addView(label("БЕЗ ПУБЛИЧНЫХ MTProto-СПИСКОВ", 9f, ACCENT, true).apply {
            letterSpacing = 0.10f
        })
        note.addView(space(8))
        note.addView(label(
            "Telegram подключается к локальному адресу на этом устройстве. " +
                "ARTproxy самостоятельно строит внешний маршрут — искать и добавлять чужие прокси не нужно.",
            12f,
            SUBTLE,
        ))
        root.addView(note)

        root.addView(space(21))
        root.addView(label(
            "ARTproxy 0.3.0  ·  НЕ СИСТЕМНЫЙ VPN",
            9f,
            FOOTER,
            true,
        ).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        })

        setContentView(scroll)
        refreshState()
    }

    private fun addRouteRow(parent: LinearLayout, number: String, title: String, subtitle: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(number, 10f, ACCENT, true), LinearLayout.LayoutParams(dp(31), dp(34)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 13f, WHITE, true))
            addView(space(4))
            addView(label(subtitle, 11f, MUTED))
        }
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row)
    }

    private fun toggleProxy() {
        val prefs = getSharedPreferences(ArtProxyService.PREFS, MODE_PRIVATE)
        val running = runCatching { NativeProxy.isListening() }.getOrDefault(false)
        val starting = prefs.getBoolean(ArtProxyService.KEY_STARTING, false)
        val action = if (running || starting) ArtProxyService.ACTION_STOP else ArtProxyService.ACTION_START
        if (action == ArtProxyService.ACTION_START) {
            prefs.edit()
                .putBoolean(ArtProxyService.KEY_STARTING, true)
                .putString(ArtProxyService.KEY_ERROR, "")
                .apply()
        }
        val intent = Intent(this, ArtProxyService::class.java).setAction(action)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (error: Throwable) {
            prefs.edit()
                .putBoolean(ArtProxyService.KEY_STARTING, false)
                .putBoolean(ArtProxyService.KEY_RUNNING, false)
                .putString(ArtProxyService.KEY_ERROR, error.message ?: "Не удалось запустить службу")
                .apply()
        }
        refreshState()
    }

    private fun openTelegram() {
        if (!runCatching { NativeProxy.isListening() }.getOrDefault(false)) {
            refreshState()
            return
        }
        val prefs = getSharedPreferences(ArtProxyService.PREFS, MODE_PRIVATE)
        val secret = ArtSecretStore.getOrCreate(this)
        val port = prefs.getInt(ArtProxyService.KEY_PORT, ArtProxyService.DEFAULT_PORT)
        val uri = Uri.Builder()
            .scheme("tg")
            .authority("proxy")
            .appendQueryParameter("server", ArtProxyService.HOST)
            .appendQueryParameter("port", port.toString())
            .appendQueryParameter("secret", "dd$secret")
            .build()
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Открыть в Telegram"))
        } catch (_: Throwable) {
            errorText.text = "Не найден Telegram или совместимый клиент."
        }
    }

    private fun refreshState() {
        val prefs = getSharedPreferences(ArtProxyService.PREFS, MODE_PRIVATE)
        val running = runCatching { NativeProxy.isListening() }.getOrDefault(false)
        val starting = prefs.getBoolean(ArtProxyService.KEY_STARTING, false)
        val port = prefs.getInt(ArtProxyService.KEY_PORT, ArtProxyService.DEFAULT_PORT)
        portValue.text = "${ArtProxyService.HOST}:$port"
        val error = prefs.getString(ArtProxyService.KEY_ERROR, "").orEmpty()

        when {
            running -> {
                statusPill.text = "●  МОСТ АКТИВЕН"
                statusPill.setTextColor(SUCCESS)
                statusPill.background = round(SUCCESS_BG, 30)
                headline.text = "Локальный мост активен"
                description.text = "Прокси запущен. Подключи Telegram, чтобы открыть сетевой маршрут."
                primaryButton.text = "ОСТАНОВИТЬ ART"
                telegramButton.isEnabled = true
                telegramButton.alpha = 1f
                errorText.text = ""
            }
            starting -> {
                statusPill.text = "●  ЗАПУСК"
                statusPill.setTextColor(ACCENT)
                statusPill.background = round(ACCENT_BG, 30)
                headline.text = "Подготавливаю маршрут"
                description.text = "Запускаю локальный мост и проверяю сетевой транспорт."
                primaryButton.text = "ЗАПУСК…"
                primaryButton.isEnabled = false
                telegramButton.isEnabled = false
                telegramButton.alpha = 0.42f
                errorText.text = ""
            }
            else -> {
                statusPill.text = "●  ОТКЛЮЧЕНО"
                statusPill.setTextColor(MUTED)
                statusPill.background = round(PANEL_ALT, 30)
                headline.text = "ART готов к запуску"
                description.text = "Нажми кнопку, чтобы запустить локальный ART-мост для Telegram."
                primaryButton.text = "ЗАПУСТИТЬ ART"
                primaryButton.isEnabled = true
                telegramButton.isEnabled = false
                telegramButton.alpha = 0.42f
                errorText.text = error
            }
        }
        if (!starting) primaryButton.isEnabled = true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = GradientDrawable().apply {
            setColor(PANEL)
            cornerRadius = dp(23).toFloat()
            setStroke(dp(1), LINE)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun button(value: String, primary: Boolean) = TextView(this).apply {
        text = value
        textSize = 12f
        gravity = Gravity.CENTER
        letterSpacing = 0.10f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.rgb(8, 10, 14) else WHITE)
        background = round(if (primary) WHITE else PANEL_ALT, 16)
        minimumHeight = dp(57)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        isClickable = true
        isFocusable = true
    }

    private fun separator() = View(this).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1),
        ).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
            leftMargin = dp(31)
        }
    }

    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun wrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(57),
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xFF08090B.toInt()
        private const val PANEL = 0xFF151820.toInt()
        private const val PANEL_ALT = 0xFF222630.toInt()
        private const val PANEL_DARK = 0xFF101217.toInt()
        private const val LINE = 0xFF2A303B.toInt()
        private const val WHITE = 0xFFF6F7F9.toInt()
        private const val SUBTLE = 0xFFADB3BE.toInt()
        private const val MUTED = 0xFF777F8D.toInt()
        private const val FOOTER = 0xFF626A77.toInt()
        private const val ACCENT = 0xFF8AAEFF.toInt()
        private const val ACCENT_BG = 0xFF1C2A43.toInt()
        private const val SUCCESS = 0xFF8DD9AE.toInt()
        private const val SUCCESS_BG = 0xFF1D3028.toInt()
        private const val WARNING = 0xFFFFB27A.toInt()
    }
}
