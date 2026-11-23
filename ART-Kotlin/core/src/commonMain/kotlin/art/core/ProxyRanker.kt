package art.core

import kotlin.math.ln

data class RankedProxy(
    val candidate: ProxyCandidate,
    /** A comparable 0..100 score. Media success and throughput outweigh raw ping. */
    val score: Double,
)

object ProxyRanker {
    fun score(candidate: ProxyCandidate): Double {
        if (!candidate.isStructurallyValid) return 0.0

        val media = candidate.rateOrNull(candidate.mediaSuccessRate) ?: 0.0
        val requests = candidate.rateOrNull(candidate.requestSuccessRate) ?: 0.0
        val stability = candidate.rateOrNull(candidate.stabilityRate) ?: 0.0
        val throughput = candidate.throughputKibPerSecond
            ?.coerceAtLeast(0.0)
            ?.let { (ln(1.0 + it) / ln(1.0 + 50_000.0)).coerceIn(0.0, 1.0) }
            ?: 0.0
        val latency = candidate.latencyMs
            ?.takeIf { it >= 0.0 }
            ?.let { (1.0 - it / 2_000.0).coerceIn(0.0, 1.0) }
            ?: 0.0

        // Media behaviour is intentionally the largest signal. A low ping alone
        // cannot make a proxy rank above one that consistently loads video.
        val result = media * 0.40 +
            requests * 0.22 +
            throughput * 0.18 +
            stability * 0.12 +
            latency * 0.08
        return (result * 100.0).coerceIn(0.0, 100.0)
    }

    fun rank(candidates: Iterable<ProxyCandidate>): List<RankedProxy> =
        rankMatching(candidates) { it.eligibleForAutomaticSelection }

    /**
     * Excludes explicitly ad-tagged proxies but allows UNKNOWN results with a
     * visible warning in the UI. This is useful when feed maintainers do not
     * run the optional, account-backed Telegram ad-check for every endpoint.
     */
    fun rankExcludingDetectedSponsor(candidates: Iterable<ProxyCandidate>): List<RankedProxy> =
        rankMatching(candidates) { it.eligibleWithSponsorWarning }

    private fun rankMatching(
        candidates: Iterable<ProxyCandidate>,
        eligible: (ProxyCandidate) -> Boolean,
    ): List<RankedProxy> =
        candidates.asSequence()
            .filter(eligible)
            .map { RankedProxy(it.normalised(), score(it)) }
            .sortedWith(
                compareByDescending<RankedProxy> { it.score }
                    .thenByDescending { it.candidate.mediaSuccessRate ?: 0.0 }
                    .thenByDescending { it.candidate.requestSuccessRate ?: 0.0 }
                    .thenByDescending { it.candidate.throughputKibPerSecond ?: 0.0 }
                    .thenBy { it.candidate.latencyMs ?: Double.MAX_VALUE }
                    .thenBy { it.candidate.key },
            )
            .toList()

    fun best(candidates: Iterable<ProxyCandidate>): ProxyCandidate? =
        rank(candidates).firstOrNull()?.candidate
}
