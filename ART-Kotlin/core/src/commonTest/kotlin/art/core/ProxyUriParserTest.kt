package art.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProxyUriParserTest {
    private val secret = "0123456789abcdef0123456789abcdef"

    @Test
    fun parsesTelegramProxyLink() {
        val parsed = ProxyUriParser.parseLine(
            "tg://proxy?server=proxy.example.com&port=443&secret=$secret",
            "feed-a",
        )
        assertNotNull(parsed)
        assertEquals("proxy.example.com", parsed.server)
        assertEquals(443, parsed.port)
        assertEquals(secret, parsed.secret)
        assertEquals(setOf("feed-a"), parsed.sources)
    }

    @Test
    fun parsesHttpsTelegramLinkAndPercentEncoding() {
        val parsed = ProxyUriParser.parseLine(
            "https://t.me/proxy?server=proxy%2Eexample%2Ecom&port=8443&secret=$secret",
            "feed-b",
        )
        assertNotNull(parsed)
        assertEquals("proxy.example.com", parsed.server)
        assertEquals(8443, parsed.port)
    }

    @Test
    fun parsesSimpleWhitespaceSeparatedFeedLine() {
        val parsed = ProxyUriParser.parseLine(
            "proxy.example.com 443 $secret",
            "plain-feed",
        )
        assertNotNull(parsed)
        assertEquals(443, parsed.port)
    }

    @Test
    fun rejectsInvalidPortAndSecret() {
        assertNull(
            ProxyUriParser.parseLine(
                "tg://proxy?server=proxy.example.com&port=70000&secret=$secret",
                "bad-feed",
            ),
        )
        assertNull(
            ProxyUriParser.parseLine(
                "tg://proxy?server=proxy.example.com&port=443&secret=short",
                "bad-feed",
            ),
        )
    }
}
