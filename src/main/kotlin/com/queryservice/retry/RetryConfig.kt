package com.queryservice.retry

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "query-service.retry")
data class RetryConfigProperties(
    val enabled: Boolean = true,
    val maxAttempts: Int = 3,
    val initialIntervalMs: Long = 1000,
    val multiplier: Double = 2.0,
    val maxIntervalMs: Long = 10000
)

