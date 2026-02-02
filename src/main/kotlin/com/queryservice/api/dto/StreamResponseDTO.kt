package com.queryservice.api.dto

import java.time.Instant

data class StreamResponseDTO(
    val queryId: String,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean,
    val data: List<Map<String, Any>>,
    val timestamp: Instant = Instant.now()
)

