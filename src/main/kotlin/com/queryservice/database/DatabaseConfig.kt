package com.queryservice.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "query-service.databases")
data class DatabaseConfigProperties(
    val oracle: DatabaseConnectionConfig = DatabaseConnectionConfig(),
    val mssql: DatabaseConnectionConfig = DatabaseConnectionConfig()
)

data class DatabaseConnectionConfig(
    val jdbcUrl: String = "",
    val username: String = "",
    val password: String = "",
    val pool: PoolConfig = PoolConfig()
)

@Configuration
class DatabaseConfig(
    private val databaseConfigProperties: DatabaseConfigProperties
) {
    
    @Bean("oracleDataSource")
    fun oracleDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = databaseConfigProperties.oracle.jdbcUrl
            username = databaseConfigProperties.oracle.username
            password = databaseConfigProperties.oracle.password
            driverClassName = "oracle.jdbc.OracleDriver"
            maximumPoolSize = databaseConfigProperties.oracle.pool.maximumPoolSize
            minimumIdle = databaseConfigProperties.oracle.pool.minimumIdle
            connectionTimeout = databaseConfigProperties.oracle.pool.connectionTimeout
            idleTimeout = databaseConfigProperties.oracle.pool.idleTimeout
            maxLifetime = databaseConfigProperties.oracle.pool.maxLifetime
            poolName = "OraclePool"
        }
        return HikariDataSource(config)
    }
    
    @Bean("mssqlDataSource")
    fun mssqlDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = databaseConfigProperties.mssql.jdbcUrl
            username = databaseConfigProperties.mssql.username
            password = databaseConfigProperties.mssql.password
            driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
            maximumPoolSize = databaseConfigProperties.mssql.pool.maximumPoolSize
            minimumIdle = databaseConfigProperties.mssql.pool.minimumIdle
            connectionTimeout = databaseConfigProperties.mssql.pool.connectionTimeout
            idleTimeout = databaseConfigProperties.mssql.pool.idleTimeout
            maxLifetime = databaseConfigProperties.mssql.pool.maxLifetime
            poolName = "MsSqlPool"
        }
        return HikariDataSource(config)
    }
}

