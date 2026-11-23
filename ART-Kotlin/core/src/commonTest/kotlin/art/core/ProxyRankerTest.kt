package art.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyRankerTest {
    private fun candidate(
        server: String,
        media: Double,
        latency: Double,
        throughput: Double,
    ) = ProxyCandidate(
        server = server,
        port = 443,
        secret = "0123456789abcdef0123456789abcdef",
        sourceVerified = true,
        sponsorStatus = SponsorStatus.NONE,
        tcpReachable = true,
        requestSuccessRate = 0.96,
        mediaSuccessRate = media,
        throughputKibPerSecond = throughput,
        stabilityRate = 0.95,
        latencyMs = latency,
    )

    @Test
    fun videoPerformanceOutweighsSmallPingDifference() {
        val lowPingButSlowVideo = candidate("slow.example.com", media = 0.25, latency = 18.0, throughput = 400.0)
        val fastVideo = candidate("video.example.com", media = 0.97, latency = 145.0, throughput = 6_000.0)
        val ranked = ProxyRanker.rank(listOf(lowPingButSlowVideo, fastVideo))
        assertEquals("video.example.com", ranked.first().candidate.server)
    }

    @Test
    fun unknownOrDetectedSponsorIsNeverAutoSelected() {
        val unknown = candidate("unknown.example.com", 0.9, 80.0, 2_000.0)
            .copy(sponsorStatus = SponsorStatus.UNKNOWN)
        val sponsor = candidate("sponsor.example.com", 0.9, 80.0, 2_000.0)
            .copy(sponsorStatus = SponsorStatus.PRESENT)
        assertFalse(unknown.eligibleForAutomaticSelection)
        assertFalse(sponsor.eligibleForAutomaticSelection)
        assertNull(ProxyRanker.best(listOf(unknown, sponsor)))
    }

    @Test
    fun explicitCleanSponsorStatusAndPositiveConnectivityAreRequired() {
        val clean = candidate("clean.example.com", 0.8, 90.0, 1_000.0)
        assertTrue(clean.eligibleForAutomaticSelection)
        assertNotNull(ProxyRanker.best(listOf(clean)))
    }
}
