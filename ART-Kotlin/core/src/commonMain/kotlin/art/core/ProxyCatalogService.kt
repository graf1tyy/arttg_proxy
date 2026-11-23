package art.core

data class CatalogRefreshResult(
    val candidates: List<ProxyCandidate>,
    val ranked: List<RankedProxy>,
    val successfulFeeds: Int,
    val failedFeeds: Int,
    val probed: Int,
    val probeFailures: Int,
) {
    val bestCandidate: ProxyCandidate?
        get() = ranked.firstOrNull()?.candidate
}

/**
 * Coordinates the common catalogue lifecycle without choosing an HTTP, storage
 * or probing library. Android/Windows adapters provide those platform services.
 */
class ProxyCatalogService(
    private val feedTransport: ProxyFeedTransport,
    private val probe: ProxyBatchProbe,
    private val catalog: ProxyCatalog = ProxyCatalog(),
) {
    suspend fun refresh(
        current: Iterable<ProxyCandidate>,
        sources: List<ProxyFeedSource>,
        probeBudget: Int = 120,
        maxParallelism: Int = 8,
    ): CatalogRefreshResult {
        val activeSources = sources
            .filter { it.enabled && it.isValid() }
            .sortedBy { it.priority }

        val fetchResults = feedTransport.fetchAll(activeSources)
        val imported = fetchResults
            .filter { it.succeeded }
            .flatMap { result ->
                catalog.parseFeed(
                    feedId = result.sourceId,
                    content = result.content.orEmpty(),
                )
            }

        val merged = catalog.merge(current, imported)
        val candidatesToProbe = merged.take(probeBudget.coerceIn(0, 2_000))
        val probeResult = if (candidatesToProbe.isEmpty()) {
            ProxyProbeBatchResult(emptyList(), attempted = 0, failed = 0)
        } else {
            probe.measure(
                candidates = candidatesToProbe,
                maxParallelism = maxParallelism.coerceIn(1, 32),
            )
        }

        val latestByKey = probeResult.updatedCandidates
            .filter { it.isStructurallyValid }
            .associateBy { it.key }

        val refreshed = merged.map { old ->
            val latest = latestByKey[old.key] ?: return@map old
            val sponsorStatus = when {
                old.sponsorStatus == SponsorStatus.PRESENT ||
                    latest.sponsorStatus == SponsorStatus.PRESENT -> SponsorStatus.PRESENT
                latest.sponsorStatus == SponsorStatus.UNKNOWN -> old.sponsorStatus
                else -> latest.sponsorStatus
            }
            latest.copy(
                sources = old.sources + latest.sources,
                sourceVerified = old.sourceVerified || latest.sourceVerified,
                sponsorStatus = sponsorStatus,
            )
        }

        return CatalogRefreshResult(
            candidates = refreshed,
            ranked = ProxyRanker.rank(refreshed),
            successfulFeeds = fetchResults.count { it.succeeded },
            failedFeeds = activeSources.size - fetchResults.count { it.succeeded },
            probed = probeResult.attempted.coerceAtLeast(0),
            probeFailures = probeResult.failed.coerceAtLeast(0),
        )
    }
}
