package com.queryservice.database

data class PoolConfig(
    val maximumPoolSize: Int = 20,
    val minimumIdle: Int = 5,
    val connectionTimeout: Long = 30000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000
)

