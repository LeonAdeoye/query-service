package com.queryservice.database

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Binds query-service.datasources from application.properties.
 * Each entry has an id (used in API and pool lookup) and vendor (oracle/mssql for SQL dialect).
 */
@Component
@ConfigurationProperties(prefix = "query-service")
data class DatasourceConfigProperties(
    val datasources: List<DatasourceEntry> = emptyList()
)

data class DatasourceEntry(
    val id: String = "",
    val vendor: String = "",
    val jdbcUrl: String = "",
    val username: String = "",
    val password: String = "",
    val pool: PoolConfig = PoolConfig()
)
