package com.queryservice.monitoring

import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.ErrorCodes
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class QueryMetricsCollector(
    private val errorCodeRegistry: ErrorCodeRegistry
) {
    private val logger = LoggerFactory.getLogger("com.queryservice.monitoring")
    
    fun logQueryExecution(context: QueryExecutionContext, success: Boolean, errorCode: String? = null) {
        val metrics = mapOf(
            "queryId" to context.queryId,
            "source" to context.metadata.source,
            "userId" to context.metadata.userId,
            "clientId" to context.metadata.clientId,
            "requestId" to context.metadata.requestId,
            "queryExecutionDurationMs" to context.getQueryExecutionDurationMs(),
            "jsonTransformDurationMs" to context.getJsonTransformDurationMs(),
            "totalDurationMs" to context.getTotalDurationMs(),
            "success" to success,
            "errorCode" to errorCode,
            "timestamp" to context.startTime.toString()
        )
        
        if (success) {
            logger.info("Query execution completed: {}", metrics)
        } else {
            logger.error("Query execution failed: {}", metrics)
            if (errorCode != null) {
                errorCodeRegistry.logError(errorCode, "Query execution failed for queryId: ${context.queryId}")
            }
        }
    }
    
    fun logMetrics(queryId: String, metadata: com.queryservice.tracking.QueryMetadata, timer: ExecutionTimer, success: Boolean) {
        val metrics = mapOf(
            "queryId" to queryId,
            "source" to metadata.source,
            "userId" to metadata.userId,
            "clientId" to metadata.clientId,
            "requestId" to metadata.requestId,
            "queryExecutionDurationMs" to timer.getQueryExecutionDurationMs(),
            "jsonTransformDurationMs" to timer.getJsonTransformDurationMs(),
            "totalDurationMs" to timer.getTotalDurationMs(),
            "success" to success,
            "timestamp" to metadata.timestamp.toString()
        )
        
        logger.info("Query metrics: {}", metrics)
    }
}

