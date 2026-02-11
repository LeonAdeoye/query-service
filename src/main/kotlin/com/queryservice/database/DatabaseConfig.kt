package com.queryservice.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DatabaseConfig(
    private val datasourceConfigProperties: DatasourceConfigProperties
) {

    @Bean("queryServiceDataSources")
    fun dataSourceMap(): Map<String, HikariDataSource> {
        val map = mutableMapOf<String, HikariDataSource>()
        for (entry in datasourceConfigProperties.datasources) {
            if (entry.id.isBlank()) continue
            val ds = createDataSource(entry)
            map[entry.id] = ds
        }
        return map
    }

    private fun createDataSource(entry: DatasourceEntry): HikariDataSource {
        val driverClassName = when (entry.vendor.lowercase()) {
            "oracle" -> "oracle.jdbc.OracleDriver"
            "mssql" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver"
            else -> throw IllegalArgumentException("Unsupported datasource vendor: ${entry.vendor}")
        }
        val config = HikariConfig().apply {
            setJdbcUrl(entry.jdbcUrl)
            username = entry.username
            password = entry.password
            setDriverClassName(driverClassName)
            maximumPoolSize = entry.pool.maximumPoolSize
            minimumIdle = entry.pool.minimumIdle
            connectionTimeout = entry.pool.connectionTimeout
            idleTimeout = entry.pool.idleTimeout
            maxLifetime = entry.pool.maxLifetime
            poolName = "Pool-${entry.id}"
        }
        return HikariDataSource(config)
    }
}
