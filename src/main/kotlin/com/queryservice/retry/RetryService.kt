package com.queryservice.retry

import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

@Service
class RetryService(
    private val retryConfig: RetryConfigProperties,
    private val retryPolicy: RetryPolicy,
    private val errorCodeRegistry: ErrorCodeRegistry
) {
    private val logger = LoggerFactory.getLogger(RetryService::class.java)

    /**
     * Non-blocking retry using Project Reactor's Retry.backoff.
     */
    fun <T> executeWithRetry(
        operation: Mono<T>,
        operationName: String = "operation"
    ): Mono<T> {
        if (!retryConfig.enabled) {
            return operation
        }
        val retrySpec = Retry.backoff(retryConfig.maxAttempts.toLong(), Duration.ofMillis(retryConfig.initialIntervalMs))
            .maxBackoff(Duration.ofMillis(retryConfig.maxIntervalMs))
            .filter { throwable: Throwable -> retryPolicy.isRetryable(throwable) }
            .doBeforeRetry { signal ->
                logger.warn(
                    "Attempt ${signal.totalRetries() + 1} failed for $operationName, retrying",
                    signal.failure()
                )
            }
            .onRetryExhaustedThrow { _: reactor.util.retry.RetryBackoffSpec, signal: Retry.RetrySignal ->
                errorCodeRegistry.logError(
                    ErrorCodes.RETRY_EXHAUSTED,
                    "Retry exhausted after ${signal.totalRetries()} attempts for $operationName",
                    signal.failure()
                )
                QueryServiceException(
                    ErrorCodes.RETRY_EXHAUSTED,
                    "Retry exhausted for $operationName",
                    signal.failure()
                )
            }
        return operation.retryWhen(retrySpec)
    }
}

