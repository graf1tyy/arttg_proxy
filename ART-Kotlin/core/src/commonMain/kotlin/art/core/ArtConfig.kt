package art.core

/**
 * Cross-platform settings. UI and OS-specific integrations live in platform modules.
 */
data class ArtConfig(
    val autoSelectBestRoute: Boolean = true,
    val autoRefreshCatalog: Boolean = true,
    val refreshIntervalMinutes: Int = 30,
    val mediaProbeEnabled: Boolean = true,
    val maximumCandidates: Int = 300,
) {
    fun validated(): ArtConfig = copy(
        refreshIntervalMinutes = refreshIntervalMinutes.coerceIn(5, 720),
        maximumCandidates = maximumCandidates.coerceIn(10, 2_000),
    )
}
