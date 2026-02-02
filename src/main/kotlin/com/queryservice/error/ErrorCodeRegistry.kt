package com.queryservice.error

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ErrorCodeRegistry {
    private val logger = LoggerFactory.getLogger(ErrorCodeRegistry::class.java)
    
    private val errorMessages = mapOf(
        ErrorCodes.INVALID_QUERY_REQUEST to "Invalid query request",
        ErrorCodes.DATABASE_CONNECTION_FAILURE to "Database connection failure",
        ErrorCodes.QUERY_EXECUTION_TIMEOUT to "Query execution timeout",
        ErrorCodes.INVALID_PARAMETERS to "Invalid parameters provided",
        ErrorCodes.QUERY_NOT_FOUND to "Query not found",
        ErrorCodes.DATABASE_TYPE_NOT_SUPPORTED to "Database type not supported",
        ErrorCodes.SQL_EXECUTION_ERROR to "SQL execution error",
        ErrorCodes.CACHE_ERROR to "Cache operation error",
        ErrorCodes.RETRY_EXHAUSTED to "Retry attempts exhausted",
        ErrorCodes.QUEUE_FULL to "Query queue is full",
        ErrorCodes.FILE_EXPORT_ERROR to "File export error",
        ErrorCodes.STREAMING_ERROR to "Streaming error",
        ErrorCodes.JSON_TRANSFORMATION_ERROR to "JSON transformation error",
        ErrorCodes.PARAMETER_VALIDATION_ERROR to "Parameter validation error",
        ErrorCodes.CONNECTION_POOL_EXHAUSTED to "Connection pool exhausted",
        ErrorCodes.UNKNOWN_ERROR to "Unknown error occurred"
    )
    
    fun getErrorMessage(errorCode: String): String {
        return errorMessages[errorCode] ?: "Unknown error code: $errorCode"
    }
    
    fun logError(errorCode: String, message: String, throwable: Throwable? = null) {
        val errorMessage = getErrorMessage(errorCode)
        val fullMessage = "[$errorCode] $errorMessage: $message"
        
        if (throwable != null) {
            logger.error(fullMessage, throwable)
        } else {
            logger.error(fullMessage)
        }
    }
}

