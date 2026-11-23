package art.core

enum class SponsorStatus {
    /** Upstream or a local ad check explicitly confirmed no sponsor channel. */
    NONE,

    /** A sponsor/ad channel was positively detected. Never auto-select this route. */
    PRESENT,

    /** No reliable sponsor check has been performed. Never treat this as clean. */
    UNKNOWN,
}

/**
 * One MTProto route plus evidence collected from feeds and local measurements.
 *
 * Rates accept either fractions (0.0..1.0) or percentages (0..100).
 * Throughput is measured in KiB/s. Latency is measured in milliseconds.
 */
data class ProxyCandidate(
    val server: String,
    val port: Int,
    val secret: String,
    val sources: Set<String> = emptySet(),
    val sourceVerified: Boolean = false,
    val sponsorStatus: SponsorStatus = SponsorStatus.UNKNOWN,
    val tcpReachable: Boolean = false,
    val requestSuccessRate: Double? = null,
    val mediaSuccessRate: Double? = null,
    val throughputKibPerSecond: Double? = null,
    val stabilityRate: Double? = null,
    val latencyMs: Double? = null,
    val samples: Int = 0,
    val failures: Int = 0,
    val lastCheckedEpochMs: Long? = null,
) {
    fun normalised(): ProxyCandidate = copy(
        server = server.trim().removeSurrounding("[", "]").trimEnd('.').lowercase(),
        secret = secret.trim().lowercase(),
        sources = sources.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        samples = samples.coerceAtLeast(0),
        failures = failures.coerceAtLeast(0),
    )

    val key: String
        get() {
            val value = normalised()
            return "${value.server}:${value.port}:${value.secret}"
        }

    val isStructurallyValid: Boolean
        get() {
            val value = normalised()
            return value.server.isNotBlank() &&
                value.server.length <= 253 &&
                !value.server.any { it.isWhitespace() } &&
                value.port in 1..65535 &&
                ProxyUriParser.isValidSecret(value.secret)
        }

    fun rateOrNull(value: Double?): Double? = value?.let {
        val fraction = if (it > 1.0) it / 100.0 else it
        fraction.coerceIn(0.0, 1.0)
    }

    /** Strict on purpose: unknown sponsor status is not proof of a clean proxy. */
    val eligibleForAutomaticSelection: Boolean
        get() {
            if (!isStructurallyValid || sponsorStatus != SponsorStatus.NONE) return false
            val requestRate = rateOrNull(requestSuccessRate) ?: 0.0
            val mediaRate = rateOrNull(mediaSuccessRate) ?: 0.0
            val hasConnectivityEvidence = tcpReachable || requestRate > 0.0 || mediaRate > 0.0
            val hasVerificationEvidence = sourceVerified || tcpReachable || mediaRate > 0.0
            return hasConnectivityEvidence && hasVerificationEvidence
        }
}
