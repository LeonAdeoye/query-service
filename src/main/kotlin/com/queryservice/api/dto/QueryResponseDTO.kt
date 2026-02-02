package com.queryservice.api.dto

import java.time.Instant

data class QueryResponseDTO(
    val queryId: String,
    val success: Boolean,
    val data: List<Map<String, Any>>? = null,
    val rowCount: Int = 0,
    val executionDurationMs: Long? = null,
    val jsonTransformDurationMs: Long? = null,
    val totalDurationMs: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val timestamp: Instant = Instant.now(),
    val fileUrl: String? = null
)

