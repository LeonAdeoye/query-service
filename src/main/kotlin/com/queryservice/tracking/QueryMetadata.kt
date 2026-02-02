package com.queryservice.tracking

import java.time.Instant

data class QueryMetadata(
    val source: String? = null,
    val userId: String? = null,
    val clientId: String? = null,
    val requestId: String? = null,
    val timestamp: Instant = Instant.now()
)

