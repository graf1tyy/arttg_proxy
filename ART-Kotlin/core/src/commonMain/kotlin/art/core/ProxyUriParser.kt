package art.core

/**
 * Parses Telegram MTProto links from plain-text feeds:
 * - tg://proxy?server=...&port=...&secret=...
 * - https://t.me/proxy?server=...&port=...&secret=...
 *
 * Feed fetching and JSON-specific adapters are deliberately kept outside this parser.
 */
object ProxyUriParser {
    private val linkPattern = Regex(
        """(?:tg://proxy|https?://t\.me/proxy)\?[^\s<>"']+""",
        RegexOption.IGNORE_CASE,
    )
    private val hexSecretPattern = Regex("^(?:[0-9a-f]{32,128})$", RegexOption.IGNORE_CASE)
    private val base64SecretPattern = Regex("^[a-z0-9_-]{22,128}={0,2}$", RegexOption.IGNORE_CASE)
    private val hostnamePattern = Regex("^[a-z0-9.-]{1,253}$", RegexOption.IGNORE_CASE)
    private val ipv6Pattern = Regex("^[0-9a-f:.]{2,64}$", RegexOption.IGNORE_CASE)

    fun parseLine(line: String, sourceId: String): ProxyCandidate? {
        val trimmed = line.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return null

        val link = linkPattern.find(trimmed)?.value ?: return parsePlainLine(trimmed, sourceId)
        val query = link.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null

        val parameters = linkedMapOf<String, String>()
        query.split('&').forEach { item ->
            val separator = item.indexOf('=')
            if (separator <= 0) return@forEach
            val key = decodeComponent(item.substring(0, separator)).lowercase()
            val value = decodeComponent(item.substring(separator + 1))
            if (key.isNotEmpty()) parameters[key] = value
        }

        val server = parameters["server"] ?: return null
        val port = parameters["port"]?.toIntOrNull() ?: return null
        val secret = parameters["secret"] ?: return null
        return candidate(server, port, secret, sourceId)
    }

    fun parseText(text: String, sourceId: String): List<ProxyCandidate> =
        text.lineSequence().mapNotNull { parseLine(it, sourceId) }.toList()

    fun isValidSecret(secret: String): Boolean {
        val value = secret.trim()
        return hexSecretPattern.matches(value) || base64SecretPattern.matches(value)
    }

    private fun parsePlainLine(line: String, sourceId: String): ProxyCandidate? {
        val fields = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (fields.size != 3) return null
        val port = fields[1].toIntOrNull() ?: return null
        return candidate(fields[0], port, fields[2], sourceId)
    }

    private fun candidate(
        serverValue: String,
        port: Int,
        secretValue: String,
        sourceId: String,
    ): ProxyCandidate? {
        val server = serverValue.trim().removeSurrounding("[", "]").trimEnd('.')
        val secret = secretValue.trim()
        val validHost = hostnamePattern.matches(server) || ipv6Pattern.matches(server)
        if (!validHost || port !in 1..65535 || !isValidSecret(secret)) return null
        return ProxyCandidate(
            server = server,
            port = port,
            secret = secret,
            sources = setOf(sourceId),
        )
    }

    /** Percent-decodes UTF-8 query components without requiring a JVM URL class. */
    private fun decodeComponent(value: String): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char == '+' -> {
                    bytes += 32.toByte()
                    index++
                }
                char == '%' && index + 2 < value.length -> {
                    val byteValue = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (byteValue != null) {
                        bytes += byteValue.toByte()
                        index += 3
                    } else {
                        char.toString().encodeToByteArray().forEach { bytes += it }
                        index++
                    }
                }
                else -> {
                    char.toString().encodeToByteArray().forEach { bytes += it }
                    index++
                }
            }
        }
        return bytes.toByteArray().decodeToString(throwOnInvalidSequence = false)
    }
}
