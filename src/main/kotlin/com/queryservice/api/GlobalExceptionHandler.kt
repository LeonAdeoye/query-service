package com.queryservice.api

import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler(
    private val errorCodeRegistry: ErrorCodeRegistry
) {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    @ExceptionHandler(QueryServiceException::class)
    fun handleQueryServiceException(ex: QueryServiceException): ResponseEntity<Map<String, Any>> {
        errorCodeRegistry.logError(ex.errorCode, ex.message ?: "Query service error", ex)
        
        val status = when (ex.errorCode) {
            ErrorCodes.QUERY_NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorCodes.INVALID_QUERY_REQUEST,
            ErrorCodes.INVALID_PARAMETERS,
            ErrorCodes.PARAMETER_VALIDATION_ERROR,
            ErrorCodes.LIKE_DOUBLE_WILDCARD_NOT_ALLOWED,
            ErrorCodes.DATASOURCE_NOT_FOUND -> HttpStatus.BAD_REQUEST
            ErrorCodes.QUEUE_FULL -> HttpStatus.SERVICE_UNAVAILABLE
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        
        return ResponseEntity.status(status).body(mapOf(
            "errorCode" to ex.errorCode,
            "message" to (ex.message ?: "Unknown error"),
            "timestamp" to java.time.Instant.now().toString()
        ))
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<Map<String, Any>> {
        logger.error("Unexpected error", ex)
        errorCodeRegistry.logError(ErrorCodes.UNKNOWN_ERROR, "Unexpected error: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
            "errorCode" to ErrorCodes.UNKNOWN_ERROR,
            "message" to "An unexpected error occurred",
            "timestamp" to java.time.Instant.now().toString()
        ))
    }
}

