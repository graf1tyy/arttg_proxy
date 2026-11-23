package art.core

data class RouteStartResult(
    val started: Boolean,
    val detail: String? = null,
)

data class RouteAttempt(
    val candidateKey: String,
    val started: Boolean,
    val healthy: Boolean,
    val detail: String? = null,
)

data class RouteConnectionResult(
    val connected: Boolean,
    val candidate: ProxyCandidate? = null,
    val attempts: List<RouteAttempt> = emptyList(),
    val detail: String? = null,
)

/**
 * A platform adapter owns the real transport:
 * - Android: foreground/VPN or supported local-proxy integration.
 * - Windows: the existing ART engine while the migration is staged.
 *
 * stop() must return true once no ART route remains active, including when
 * there was no active route before the call.
 */
interface ProxyRouteBackend {
    suspend fun start(candidate: ProxyCandidate): RouteStartResult
    suspend fun isHealthy(): Boolean
    suspend fun stop(): Boolean
}

/** Shared failover policy; the OS-specific data plane is injected. */
class ProxyRouteManager(
    private val backend: ProxyRouteBackend,
) {
    suspend fun connectBest(
        candidates: Iterable<ProxyCandidate>,
        maxAttempts: Int = 5,
    ): RouteConnectionResult {
        val ranked = ProxyRanker.rank(candidates).take(maxAttempts.coerceIn(1, 20))
        if (ranked.isEmpty()) {
            return RouteConnectionResult(
                connected = false,
                detail = "Нет проверенных прокси без подтверждённой рекламы.",
            )
        }

        if (!backend.stop()) {
            return RouteConnectionResult(
                connected = false,
                detail = "Не удалось завершить предыдущий маршрут.",
            )
        }

        val attempts = mutableListOf<RouteAttempt>()
        for (rankedProxy in ranked) {
            val candidate = rankedProxy.candidate
            val start = backend.start(candidate)
            val healthy = start.started && backend.isHealthy()
            attempts += RouteAttempt(
                candidateKey = candidate.key,
                started = start.started,
                healthy = healthy,
                detail = start.detail,
            )

            if (healthy) {
                return RouteConnectionResult(
                    connected = true,
                    candidate = candidate,
                    attempts = attempts,
                    detail = "Подключение установлено.",
                )
            }

            // Always clean up a partial/failed start before moving to fallback.
            if (!backend.stop()) {
                return RouteConnectionResult(
                    connected = false,
                    attempts = attempts,
                    detail = "Не удалось очистить неудачную попытку подключения.",
                )
            }
        }

        return RouteConnectionResult(
            connected = false,
            attempts = attempts,
            detail = "Все проверенные маршруты отклонили подключение или не прошли проверку.",
        )
    }

    suspend fun disconnect(): Boolean = backend.stop()
}
