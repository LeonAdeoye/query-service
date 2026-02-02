package com.queryservice.monitoring

import com.queryservice.tracking.QueryMetadata
import java.time.Instant

data class QueryExecutionContext(
    val queryId: String,
    val metadata: QueryMetadata,
    val startTime: Instant = Instant.now(),
    var queryExecutionStartTime: Instant? = null,
    var queryExecutionEndTime: Instant? = null,
    var jsonTransformStartTime: Instant? = null,
    var jsonTransformEndTime: Instant? = null,
    var endTime: Instant? = null
) {
    fun getQueryExecutionDurationMs(): Long? {
        return if (queryExecutionStartTime != null && queryExecutionEndTime != null) {
            java.time.Duration.between(queryExecutionStartTime, queryExecutionEndTime).toMillis()
        } else null
    }
    
    fun getJsonTransformDurationMs(): Long? {
        return if (jsonTransformStartTime != null && jsonTransformEndTime != null) {
            java.time.Duration.between(jsonTransformStartTime, jsonTransformEndTime).toMillis()
        } else null
    }
    
    fun getTotalDurationMs(): Long {
        return if (endTime != null) {
            java.time.Duration.between(startTime, endTime).toMillis()
        } else {
            java.time.Duration.between(startTime, Instant.now()).toMillis()
        }
    }
}

