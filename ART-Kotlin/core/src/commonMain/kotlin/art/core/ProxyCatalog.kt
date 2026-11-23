package art.core

/**
 * In-memory catalogue merger. Persistence and HTTP transport are platform adapters,
 * so the same catalogue/ranking rules can be reused by Android and Windows.
 */
class ProxyCatalog {
    fun parseFeed(feedId: String, content: String): List<ProxyCandidate> =
        ProxyUriParser.parseText(content, feedId)

    fun merge(vararg batches: Iterable<ProxyCandidate>): List<ProxyCandidate> {
        val merged = linkedMapOf<String, ProxyCandidate>()
        batches.forEach { batch ->
            batch.forEach { candidate ->
                if (!candidate.isStructurallyValid) return@forEach
                val item = candidate.normalised()
                val previous = merged[item.key]
                merged[item.key] = if (previous == null) item else mergePair(previous, item)
            }
        }
        return merged.values.toList()
    }

    fun ranked(candidates: Iterable<ProxyCandidate>): List<RankedProxy> =
        ProxyRanker.rank(candidates)

    fun best(candidates: Iterable<ProxyCandidate>): ProxyCandidate? =
        ProxyRanker.best(candidates)

    private fun mergePair(left: ProxyCandidate, right: ProxyCandidate): ProxyCandidate {
        val sponsor = when {
            left.sponsorStatus == SponsorStatus.PRESENT || right.sponsorStatus == SponsorStatus.PRESENT ->
                SponsorStatus.PRESENT
            left.sponsorStatus == SponsorStatus.NONE || right.sponsorStatus == SponsorStatus.NONE ->
                SponsorStatus.NONE
            else -> SponsorStatus.UNKNOWN
        }
        return left.copy(
            sources = left.sources + right.sources,
            sourceVerified = left.sourceVerified || right.sourceVerified,
            sponsorStatus = sponsor,
            tcpReachable = left.tcpReachable || right.tcpReachable,
            requestSuccessRate = maxNullable(left.requestSuccessRate, right.requestSuccessRate),
            mediaSuccessRate = maxNullable(left.mediaSuccessRate, right.mediaSuccessRate),
            throughputKibPerSecond = maxNullable(
                left.throughputKibPerSecond,
                right.throughputKibPerSecond,
            ),
            stabilityRate = maxNullable(left.stabilityRate, right.stabilityRate),
            latencyMs = minNullable(left.latencyMs, right.latencyMs),
            samples = maxOf(left.samples, right.samples),
            failures = minOf(left.failures, right.failures),
            lastCheckedEpochMs = maxNullable(left.lastCheckedEpochMs, right.lastCheckedEpochMs),
        )
    }

    private fun maxNullable(a: Double?, b: Double?): Double? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun minNullable(a: Double?, b: Double?): Double? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }

    private fun maxNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }
}
