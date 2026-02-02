package com.queryservice.monitoring

import java.time.Instant

class ExecutionTimer {
    private val startTime = Instant.now()
    private var queryExecutionStart: Instant? = null
    private var queryExecutionEnd: Instant? = null
    private var jsonTransformStart: Instant? = null
    private var jsonTransformEnd: Instant? = null
    
    fun startQueryExecution() {
        queryExecutionStart = Instant.now()
    }
    
    fun endQueryExecution() {
        queryExecutionEnd = Instant.now()
    }
    
    fun startJsonTransform() {
        jsonTransformStart = Instant.now()
    }
    
    fun endJsonTransform() {
        jsonTransformEnd = Instant.now()
    }
    
    fun getQueryExecutionDurationMs(): Long? {
        return if (queryExecutionStart != null && queryExecutionEnd != null) {
            java.time.Duration.between(queryExecutionStart, queryExecutionEnd).toMillis()
        } else null
    }
    
    fun getJsonTransformDurationMs(): Long? {
        return if (jsonTransformStart != null && jsonTransformEnd != null) {
            java.time.Duration.between(jsonTransformStart, jsonTransformEnd).toMillis()
        } else null
    }
    
    fun getTotalDurationMs(): Long {
        return java.time.Duration.between(startTime, Instant.now()).toMillis()
    }
}

