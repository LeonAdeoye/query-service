package com.queryservice.tracking

import org.springframework.stereotype.Component

@Component
class QuerySourceTracker {
    
    fun extractMetadataFromHeaders(headers: Map<String, String>): QueryMetadata {
        return QueryMetadata(
            source = headers["X-Request-Source"],
            userId = headers["X-User-Id"],
            clientId = headers["X-Client-Id"],
            requestId = headers["X-Request-Id"] ?: java.util.UUID.randomUUID().toString()
        )
    }
}

