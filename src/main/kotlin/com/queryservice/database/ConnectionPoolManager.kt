package com.queryservice.database

import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class ConnectionPoolManager(
    private val oracleDataSource: HikariDataSource,
    private val mssqlDataSource: HikariDataSource
) {
    private val logger = LoggerFactory.getLogger(ConnectionPoolManager::class.java)
    
    fun getConnection(databaseType: DatabaseType): Connection {
        return try {
            when (databaseType) {
                DatabaseType.ORACLE -> oracleDataSource.connection
                DatabaseType.MSSQL -> mssqlDataSource.connection
            }
        } catch (e: Exception) {
            logger.error("Failed to get connection for database type: $databaseType", e)
            throw QueryServiceException(
                ErrorCodes.DATABASE_CONNECTION_FAILURE,
                "Failed to get connection for ${databaseType.name}",
                e
            )
        }
    }
    
    fun getPoolStats(databaseType: DatabaseType): PoolStats {
        val pool = when (databaseType) {
            DatabaseType.ORACLE -> oracleDataSource.hikariPoolMXBean
            DatabaseType.MSSQL -> mssqlDataSource.hikariPoolMXBean
        }
        
        return PoolStats(
            activeConnections = pool?.activeConnections ?: 0,
            idleConnections = pool?.idleConnections ?: 0,
            totalConnections = pool?.totalConnections ?: 0,
            threadsAwaitingConnection = pool?.threadsAwaitingConnection ?: 0
        )
    }
    
    fun healthCheck(databaseType: DatabaseType): Boolean {
        return try {
            getConnection(databaseType).use { it.isValid(5) }
        } catch (e: Exception) {
            logger.error("Health check failed for $databaseType", e)
            false
        }
    }
}

data class PoolStats(
    val activeConnections: Int,
    val idleConnections: Int,
    val totalConnections: Int,
    val threadsAwaitingConnection: Int
)

