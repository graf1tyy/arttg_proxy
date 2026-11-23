package com.artunnel.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ArtProxyService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val startQueued = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            worker.execute {
                runCatching { NativeProxy.stop() }
                saveState(starting = false, running = false, error = "")
                stopForegroundCompat()
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat(buildNotification("Подготавливаю маршрут…"))
        } catch (error: Throwable) {
            saveState(starting = false, running = false, error = error.message ?: "Не удалось запустить фоновую службу")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        saveState(starting = true, running = false, error = "")
        if (startQueued.compareAndSet(false, true)) {
            worker.execute {
                try {
                    if (NativeProxy.isListening()) {
                        saveState(starting = false, running = true, error = "")
                        updateNotification("Локальный мост активен")
                        return@execute
                    }
                    val port = resolveLocalPort()
                    val secret = ArtSecretStore.getOrCreate(this)
                    NativeProxy.configure(cacheDir.absolutePath)
                    val result = NativeProxy.start(HOST, port, secret)
                    if (result != 0) {
                        val nativeError = NativeProxy.getLastError().orEmpty()
                        throw IllegalStateException(
                            nativeError.ifBlank { "ART Engine не запустился (код $result)" },
                        )
                    }
                    if (!NativeProxy.isListening()) {
                        throw IllegalStateException("ART Engine не подтвердил запуск локального моста")
                    }
                    saveState(starting = false, running = true, error = "")
                    updateNotification("Локальный мост активен · 127.0.0.1:$port")
                } catch (error: Throwable) {
                    val message = error.message?.take(240)?.ifBlank { null }
                        ?: error.javaClass.simpleName
                    saveState(starting = false, running = false, error = message)
                    stopForegroundCompat()
                    stopSelf(startId)
                } finally {
                    startQueued.set(false)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { NativeProxy.stop() }
        worker.shutdownNow()
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_STARTING, false)
            .putBoolean(KEY_RUNNING, false)
            .apply()
        super.onDestroy()
    }

    private fun resolveLocalPort(): Int {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        val selected = if (isPortFree(saved)) saved else {
            ServerSocket().use { socket ->
                socket.bind(InetSocketAddress(HOST, 0))
                socket.localPort
            }
        }
        prefs.edit().putInt(KEY_PORT, selected).apply()
        return selected
    }

    private fun isPortFree(port: Int): Boolean = runCatching {
        ServerSocket().use { it.bind(InetSocketAddress(HOST, port)) }
        true
    }.getOrDefault(false)

    private fun saveState(starting: Boolean, running: Boolean, error: String) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_STARTING, starting)
            .putBoolean(KEY_RUNNING, running)
            .putString(KEY_ERROR, error)
            .apply()
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ARTproxy",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Состояние локального ART-моста для Telegram"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_artproxy)
            .setContentTitle("ARTproxy")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val DEFAULT_PORT = 1443
        const val PREFS = "artproxy_runtime"
        const val KEY_PORT = "local_port"
        const val KEY_STARTING = "starting"
        const val KEY_RUNNING = "running"
        const val KEY_ERROR = "last_error"
        const val ACTION_START = "com.artunnel.mobile.START_PROXY"
        const val ACTION_STOP = "com.artunnel.mobile.STOP_PROXY"
        const val ACTION_STATUS_CHANGED = "com.artunnel.mobile.STATUS_CHANGED"
        private const val CHANNEL_ID = "artproxy_connection"
        private const val NOTIFICATION_ID = 7314
    }
}
