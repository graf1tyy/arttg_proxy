package art.core

enum class ProxyFeedFormat {
    TELEGRAM_LINK_LINES,
}

/** A feed descriptor is data; actual HTTP access is supplied by the platform. */
data class ProxyFeedSource(
    val id: String,
    val url: String,
    val priority: Int = 100,
    val enabled: Boolean = true,
    val format: ProxyFeedFormat = ProxyFeedFormat.TELEGRAM_LINK_LINES,
) {
    fun isValid(): Boolean =
        id.isNotBlank() &&
            (url.startsWith("https://") || url.startsWith("http://"))
}

data class FeedFetchResult(
    val sourceId: String,
    val content: String? = null,
    val error: String? = null,
    val fetchedAtEpochMs: Long? = null,
) {
    val succeeded: Boolean
        get() = content != null && error == null
}

/**
 * Platform implementations fetch feeds concurrently with their own HTTP stack,
 * timeouts, cache and user-agent policy. One bad feed must not block the rest.
 */
fun interface ProxyFeedTransport {
    suspend fun fetchAll(sources: List<ProxyFeedSource>): List<FeedFetchResult>
}

data class ProxyProbeBatchResult(
    val updatedCandidates: List<ProxyCandidate>,
    val attempted: Int,
    val failed: Int,
)

/**
 * The platform probe should measure actual route use, not just a TCP handshake.
 * Implementations may test MTProto requests and a small media transfer.
 */
fun interface ProxyBatchProbe {
    suspend fun measure(
        candidates: List<ProxyCandidate>,
        maxParallelism: Int,
    ): ProxyProbeBatchResult
}
