package com.artunnel.mobile

import art.core.ProxyUriParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SmokeTest {
    @Test
    fun androidModuleCanUseTheSharedProxyParser() {
        val proxy = ProxyUriParser.parseLine(
            "tg://proxy?server=proxy.example.com&port=443&secret=0123456789abcdef0123456789abcdef",
            "android-test",
        )
        assertNotNull(proxy)
        assertEquals("proxy.example.com", proxy!!.server)
        assertEquals(443, proxy.port)
    }
}
