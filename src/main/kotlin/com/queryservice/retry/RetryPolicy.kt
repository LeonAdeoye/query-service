package com.queryservice.retry

import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.SQLException

@Component
class RetryPolicy {
    private val logger = LoggerFactory.getLogger(RetryPolicy::class.java)
    
    fun isRetryable(throwable: Throwable): Boolean {
        return when (throwable) {
            is QueryServiceException -> {
                when (throwable.errorCode) {
                    ErrorCodes.DATABASE_CONNECTION_FAILURE,
                    ErrorCodes.QUERY_EXECUTION_TIMEOUT,
                    ErrorCodes.CONNECTION_POOL_EXHAUSTED -> true
                    else -> false
                }
            }
            is SQLException -> {
                // Retry on connection errors, timeouts, and transient errors
                val sqlState = throwable.sqlState
                sqlState?.startsWith("08") == true || // Connection exceptions
                sqlState?.startsWith("40") == true || // Transaction rollback
                sqlState?.startsWith("HY") == true    // General errors (some are retryable)
            }
            else -> false
        }
    }
}

