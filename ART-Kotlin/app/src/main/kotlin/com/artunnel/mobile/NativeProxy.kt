package com.artunnel.mobile

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal interface ArtProxyLibrary : Library {
    companion object {
        val INSTANCE: ArtProxyLibrary by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Native.load("tgwsproxy", ArtProxyLibrary::class.java) as ArtProxyLibrary
        }
    }

    fun StartProxy(host: String, port: Int, dcIps: String, secret: String, verbose: Int): Int
    fun StopProxy(): Int
    fun IsListening(): Int
    fun SetPoolSize(size: Int)
    fun SetCfProxyCacheDir(cacheDir: String)
    fun SetCfProxyConfig(enabled: Int, priority: Int, userDomain: String)
    fun SetCfProxyDisableTls(disable: Int)
    fun SetSecret(secret: String)
    fun GetSecretWithPrefix(): Pointer?
    fun GetStats(): Pointer?
    fun GetLastTransportError(): Pointer?
    fun FreeString(pointer: Pointer)
}

internal object NativeProxy {
    private fun consume(pointer: Pointer?): String? {
        val value = pointer ?: return null
        return try {
            value.getString(0, "UTF-8")
        } finally {
            ArtProxyLibrary.INSTANCE.FreeString(value)
        }
    }

    fun start(host: String, port: Int, secret: String): Int =
        ArtProxyLibrary.INSTANCE.StartProxy(host, port, "", secret, 0)

    fun stop(): Int = ArtProxyLibrary.INSTANCE.StopProxy()

    fun isListening(): Boolean = ArtProxyLibrary.INSTANCE.IsListening() == 1

    fun configure(cacheDir: String) {
        ArtProxyLibrary.INSTANCE.SetPoolSize(4)
        ArtProxyLibrary.INSTANCE.SetCfProxyCacheDir(cacheDir)
        ArtProxyLibrary.INSTANCE.SetCfProxyConfig(1, 1, "")
        ArtProxyLibrary.INSTANCE.SetCfProxyDisableTls(0)
    }

    fun getStats(): String? = consume(ArtProxyLibrary.INSTANCE.GetStats())

    fun getLastError(): String? = consume(ArtProxyLibrary.INSTANCE.GetLastTransportError())
}
