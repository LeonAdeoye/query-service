package com.queryservice.retry

import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RetryService(
    private val retryConfig: RetryConfigProperties,
    private val retryPolicy: RetryPolicy,
    private val errorCodeRegistry: ErrorCodeRegistry
) {
    private val logger = LoggerFactory.getLogger(RetryService::class.java)
    
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        operationName: String = "operation"
    ): T {
        if (!retryConfig.enabled) {
            return operation()
        }
        
        var lastException: Throwable? = null
        var currentInterval = retryConfig.initialIntervalMs
        
        for (attempt in 1..retryConfig.maxAttempts) {
            try {
                return operation()
            } catch (e: Throwable) {
                lastException = e
                
                if (!retryPolicy.isRetryable(e) || attempt >= retryConfig.maxAttempts) {
                    if (attempt >= retryConfig.maxAttempts) {
                        errorCodeRegistry.logError(
                            ErrorCodes.RETRY_EXHAUSTED,
                            "Retry exhausted after $attempt attempts for $operationName",
                            e
                        )
                    }
                    throw e
                }
                
                logger.warn("Attempt $attempt failed for $operationName, retrying in ${currentInterval}ms", e)
                delay(currentInterval)
                currentInterval = minOf(
                    (currentInterval * retryConfig.multiplier).toLong(),
                    retryConfig.maxIntervalMs
                )
            }
        }
        
        throw QueryServiceException(
            ErrorCodes.RETRY_EXHAUSTED,
            "Retry exhausted for $operationName",
            lastException
        )
    }
}

