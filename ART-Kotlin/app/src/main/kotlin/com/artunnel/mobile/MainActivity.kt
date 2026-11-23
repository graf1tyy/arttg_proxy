package com.artunnel.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import art.core.ProxyCandidate
import art.core.ProxyCatalog
import art.core.ProxyRanker
import art.core.SponsorStatus
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var selected: ProxyCandidate? = null

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var detailText: TextView
    private lateinit var warningText: TextView
    private lateinit var countText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var findButton: Button
    private lateinit var telegramButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 8, 9)
        window.navigationBarColor = Color.rgb(8, 8, 9)
        window.decorView.systemUiVisibility = 0
        buildScreen()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 8, 9))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "AR"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, 14)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label("ARTproxy", 21f, Color.WHITE, bold = true))
            addView(label("SMART TELEGRAM ROUTE", 9f, Color.rgb(130, 130, 136), bold = true).apply {
                letterSpacing = 0.13f
            })
        }
        header.addView(brand, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
        ))
        header.addView(label("PREVIEW", 9f, Color.rgb(155, 155, 160), bold = true).apply {
            letterSpacing = 0.10f
            background = rounded(Color.rgb(28, 28, 31), 30)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        })
        root.addView(header)

        root.addView(space(34))
        root.addView(label("ПОДКЛЮЧЕНИЕ", 10f, Color.rgb(155, 155, 160), bold = true).apply {
            letterSpacing = 0.18f
        })
        root.addView(space(10))
        root.addView(label("Найди свой лучший маршрут", 27f, Color.WHITE, bold = true))
        root.addView(space(8))
        root.addView(label(
            "ART проверит доступные MTProto-серверы и поможет выбрать подходящий для Telegram.",
            14f,
            Color.rgb(157, 157, 164),
        ))

        root.addView(space(24))
        val statusCard = card()
        statusText = label("ГОТОВ К ПРОВЕРКЕ", 10f, Color.rgb(170, 211, 183), bold = true).apply {
            letterSpacing = 0.08f
            background = rounded(Color.rgb(29, 43, 34), 30)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }
        resultText = label("Маршрут ещё не выбран", 21f, Color.WHITE, bold = true)
        detailText = label(
            "Нажми кнопку ниже — ART проверит обновляемые источники.",
            13f,
            Color.rgb(157, 157, 164),
        )
        warningText = label("", 12f, Color.rgb(221, 187, 128))
        countText = label("ИСТОЧНИКИ · 2", 10f, Color.rgb(116, 116, 124), bold = true).apply {
            letterSpacing = 0.08f
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }

        statusCard.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        statusCard.addView(space(20))
        statusCard.addView(resultText)
        statusCard.addView(space(7))
        statusCard.addView(detailText)
        statusCard.addView(space(12))
        statusCard.addView(warningText)
        statusCard.addView(space(12))
        statusCard.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3),
        ))
        statusCard.addView(space(15))
        statusCard.addView(countText)
        root.addView(statusCard)

        root.addView(space(14))
        findButton = actionButton("НАЙТИ ЛУЧШИЙ МАРШРУТ", primary = true).apply {
            setOnClickListener { findBestProxy() }
        }
        root.addView(findButton, fullWidth())
        root.addView(space(10))
        telegramButton = actionButton("ПЕРЕДАТЬ В TELEGRAM", primary = false).apply {
            isEnabled = false
            alpha = 0.38f
            setOnClickListener { addSelectedToTelegram() }
        }
        root.addView(telegramButton, fullWidth())

        root.addView(space(24))
        root.addView(label(
            "ВАЖНО  ·  В этой preview-версии выбранный MTProto-прокси передаётся в Telegram. " +
                "Это пока не собственный ART-транспорт и не системный VPN.",
            11f,
            Color.rgb(122, 122, 129),
        ))
        root.addView(space(16))
        root.addView(label(
            "ARTproxy 0.2.0",
            10f,
            Color.rgb(83, 83, 90),
            bold = true,
        ).apply {
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
        })

        setContentView(scroll)
    }

    private fun findBestProxy() {
        if (findButton.isEnabled.not()) return
        selected = null
        telegramButton.isEnabled = false
        telegramButton.alpha = 0.45f
        findButton.isEnabled = false
        findButton.text = "ПРОВЕРЯЮ СЕРВЕРЫ…"
        statusText.text = "ИДЁТ ПРОВЕРКА"
        resultText.text = "Ищу доступный маршрут"
        detailText.text = "Проверяю доступность серверов и время отклика."
        warningText.text = ""
        countText.text = "Подключаюсь к публичным источникам…"
        progress.visibility = View.VISIBLE

        worker.execute {
            val result = runCatching { loadAndRank() }
            mainHandler.post {
                progress.visibility = View.GONE
                findButton.isEnabled = true
                findButton.text = "ПОВТОРИТЬ ПОИСК"
                result.onSuccess { selection ->
                    selected = selection.candidate
                    statusText.text = "МАРШРУТ НАЙДЕН"
                    resultText.text = "${selection.candidate.server}:${selection.candidate.port}"
                    val latency = selection.candidate.latencyMs?.let { "${it.toInt()} мс" } ?: "нет RTT"
                    detailText.text = "Отклик: $latency · рейтинг: ${selection.score.toInt()}/100"
                    warningText.text = if (selection.candidate.sponsorStatus == SponsorStatus.UNKNOWN) {
                        "Реклама не проверена. Это не подтверждение отсутствия спонсорского канала."
                    } else {
                        "По данным источника рекламный канал не выявлен."
                    }
                    countText.text = "Проверено: ${selection.probed} · доступно: ${selection.total}"
                    telegramButton.isEnabled = true
                    telegramButton.alpha = 1f
                }.onFailure { error ->
                    statusText.text = "НЕ УДАЛОСЬ НАЙТИ СЕРВЕР"
                    resultText.text = "Нет подходящего прокси"
                    detailText.text = error.message ?: "Проверь интернет и попробуй ещё раз."
                    warningText.text = ""
                    countText.text = "Подтверждённо рекламные серверы исключаются."
                }
            }
        }
    }

    private data class Selection(
        val candidate: ProxyCandidate,
        val score: Double,
        val probed: Int,
        val total: Int,
    )

    private fun loadAndRank(): Selection {
        val feeds = listOf(
            "https://iwizard7.github.io/MTProxy_list/best.txt" to "iwizard-best",
            "https://iwizard7.github.io/MTProxy_list/stable.txt" to "iwizard-stable",
        )
        val feedPool = Executors.newFixedThreadPool(3)
        val feedFutures = feeds.map { (url, id) ->
            feedPool.submit(Callable {
                runCatching { readText(url) }.getOrNull()?.let { id to it }
            })
        }
        val metadataFuture = feedPool.submit(Callable {
            runCatching { readText("https://iwizard7.github.io/MTProxy_list/endpoints.json") }
                .getOrNull()
        })
        val batches = feedFutures.mapNotNull { future ->
            runCatching { future.get(9, TimeUnit.SECONDS) }.getOrNull()
        }
        val metadata = runCatching { metadataFuture.get(9, TimeUnit.SECONDS) }.getOrNull()
        feedPool.shutdownNow()

        if (batches.isEmpty()) throw IllegalStateException("Не удалось загрузить список прокси.")

        val catalog = ProxyCatalog()
        val imported = batches.flatMap { (source, text) -> catalog.parseFeed(source, text) }
        val merged = catalog.merge(imported)
        if (merged.isEmpty()) throw IllegalStateException("В источниках пока нет корректных MTProto-ссылок.")

        val metadataByKey = parseMetadata(metadata)
        val enriched = merged.map { candidate ->
            val meta = metadataByKey[candidate.key]
            val adStatus = when (meta?.optString("adsStatus", "unknown")) {
                "none" -> SponsorStatus.NONE
                "present" -> SponsorStatus.PRESENT
                else -> SponsorStatus.UNKNOWN
            }
            candidate.copy(
                sourceVerified = meta?.optBoolean("verified", true) ?: true,
                sponsorStatus = adStatus,
                requestSuccessRate = meta?.optNullableDouble("success_rate"),
                stabilityRate = meta?.optNullableDouble("success_rate"),
                latencyMs = meta?.optNullableDouble("median_rtt_ms")
                    ?: meta?.optNullableDouble("rtt_ms")
                    ?: meta?.optNullableDouble("check_ms"),
                samples = meta?.optInt("samples", 0) ?: 0,
            )
        }

        // Test a bounded set from an explicitly maintained, handshake-checked feed.
        val toProbe = enriched
            .filter { it.sponsorStatus != SponsorStatus.PRESENT }
            .sortedWith(
                compareByDescending<ProxyCandidate> { it.requestSuccessRate ?: 0.0 }
                    .thenBy { it.latencyMs ?: Double.MAX_VALUE },
            )
            .take(32)
        val probePool = Executors.newFixedThreadPool(8)
        val probes = toProbe.map { candidate ->
            probePool.submit(Callable { probeTcp(candidate) })
        }
        val probed = probes.mapNotNull { future ->
            runCatching { future.get(4, TimeUnit.SECONDS) }.getOrNull()
        }
        probePool.shutdownNow()

        val ranked = ProxyRanker.rankExcludingDetectedSponsor(probed.filter { it.tcpReachable })
        val best = ranked.firstOrNull()
            ?: throw IllegalStateException("Нет серверов, прошедших актуальную проверку доступности.")
        return Selection(
            candidate = best.candidate,
            score = best.score,
            probed = probed.count { it.tcpReachable },
            total = merged.size,
        )
    }

    private fun parseMetadata(json: String?): Map<String, JSONObject> {
        if (json.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, JSONObject>()
        val endpoints = runCatching { JSONObject(json).optJSONArray("endpoints") }.getOrNull()
            ?: return emptyMap()
        for (index in 0 until endpoints.length()) {
            val item = endpoints.optJSONObject(index) ?: continue
            val server = item.optString("server", "")
            val port = item.optInt("port", 0)
            val secret = item.optString("secret", "")
            if (server.isBlank() || port !in 1..65535 || secret.isBlank()) continue
            val status = item.optJSONObject("ads")?.optString("status", "unknown") ?: "unknown"
            val candidate = ProxyCandidate(server, port, secret)
            result[candidate.key] = JSONObject(item.toString()).put("adsStatus", status)
        }
        return result
    }

    private fun probeTcp(candidate: ProxyCandidate): ProxyCandidate {
        val started = System.nanoTime()
        val reachable = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(candidate.server, candidate.port), 1_400)
                socket.isConnected
            }
        }.getOrDefault(false)
        val measured = (System.nanoTime() - started) / 1_000_000.0
        return candidate.copy(
            tcpReachable = reachable,
            latencyMs = if (reachable) {
                candidate.latencyMs ?: measured
            } else {
                candidate.latencyMs
            },
        )
    }

    private fun readText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 6_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ARTproxy/0.2.0")
            setRequestProperty("Accept", "text/plain, application/json")
            useCaches = false
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val output = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                val buffer = CharArray(8_192)
                var total = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 2 * 1024 * 1024) throw IllegalStateException("Источник слишком большой.")
                    output.append(buffer, 0, count)
                }
            }
            return output.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun addSelectedToTelegram() {
        val candidate = selected ?: return
        val uri = Uri.Builder()
            .scheme("tg")
            .authority("proxy")
            .appendQueryParameter("server", candidate.server)
            .appendQueryParameter("port", candidate.port.toString())
            .appendQueryParameter("secret", candidate.secret)
            .build()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            detailText.text = "Не найдено приложение Telegram, поддерживающее добавление MTProto-прокси."
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { it.isFinite() }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(Color.rgb(18, 18, 20), 22)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = false
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun actionButton(value: String, primary: Boolean) = Button(this).apply {
        text = value
        textSize = 12f
        letterSpacing = 0.08f
        setPadding(dp(14), 0, dp(14), 0)
        setTextColor(if (primary) Color.rgb(8, 11, 17) else Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = rounded(
            if (primary) Color.WHITE else Color.rgb(35, 35, 39),
            16,
        )
        minimumHeight = dp(56)
        elevation = dp(0).toFloat()
        stateListAnimator = null
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(56),
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val MUTED = Color.rgb(151, 165, 185)
    }
}
