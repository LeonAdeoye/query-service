package com.queryservice.database

import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.sql.Connection

@Component
class ConnectionPoolManager(
    @Qualifier("queryServiceDataSources") private val dataSources: Map<String, HikariDataSource>
) {
    private val logger = LoggerFactory.getLogger(ConnectionPoolManager::class.java)

    fun getConnection(datasourceId: String): Connection {
        val ds = dataSources[datasourceId]
            ?: throw QueryServiceException(
                ErrorCodes.DATASOURCE_NOT_FOUND,
                "Unknown datasource id: $datasourceId. Valid ids: ${dataSources.keys.sorted().joinToString()}"
            )
        return try {
            ds.connection
        } catch (e: Exception) {
            logger.error("Failed to get connection for datasource: $datasourceId", e)
            throw QueryServiceException(
                ErrorCodes.DATABASE_CONNECTION_FAILURE,
                "Failed to get connection for $datasourceId",
                e
            )
        }
    }

    fun getPoolStats(datasourceId: String): PoolStats {
        val ds = dataSources[datasourceId]
            ?: throw QueryServiceException(
                ErrorCodes.DATASOURCE_NOT_FOUND,
                "Unknown datasource id: $datasourceId"
            )
        val pool = ds.hikariPoolMXBean
        return PoolStats(
            activeConnections = pool?.activeConnections ?: 0,
            idleConnections = pool?.idleConnections ?: 0,
            totalConnections = pool?.totalConnections ?: 0,
            threadsAwaitingConnection = pool?.threadsAwaitingConnection ?: 0
        )
    }

    fun healthCheck(datasourceId: String): Boolean {
        return try {
            getConnection(datasourceId).use { it.isValid(5) }
        } catch (e: Exception) {
            logger.error("Health check failed for datasource $datasourceId", e)
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
