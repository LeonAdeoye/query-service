package com.queryservice.execution

import com.queryservice.database.ConnectionPoolManager
import com.queryservice.database.DatabaseType
import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import com.queryservice.query.ParameterResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.sql.ResultSetMetaData

@Component
class QueryExecutor(
    private val connectionPoolManager: ConnectionPoolManager,
    private val parameterResolver: ParameterResolver
) {
    private val logger = LoggerFactory.getLogger(QueryExecutor::class.java)
    
    fun executeQuery(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?
    ): List<Map<String, Any>> {
        val connection = connectionPoolManager.getConnection(databaseType)
        
        try {
            val (resolvedSql, paramValues) = parameterResolver.resolveParameters(sql, parameters, databaseType)
            
            connection.prepareStatement(resolvedSql).use { statement ->
                paramValues.forEachIndexed { index, value ->
                    parameterResolver.setParameter(statement, index + 1, value)
                }
                
                val resultSet = statement.executeQuery()
                return convertResultSetToList(resultSet)
            }
        } catch (e: Exception) {
            logger.error("Query execution failed", e)
            throw QueryServiceException(
                ErrorCodes.SQL_EXECUTION_ERROR,
                "Failed to execute query: ${e.message}",
                e
            )
        } finally {
            connection.close()
        }
    }
    
    private fun convertResultSetToList(resultSet: ResultSet): List<Map<String, Any>> {
        val rows = mutableListOf<Map<String, Any>>()
        val metaData: ResultSetMetaData = resultSet.metaData
        val columnCount = metaData.columnCount
        
        while (resultSet.next()) {
            val row = mutableMapOf<String, Any>()
            for (i in 1..columnCount) {
                val columnName = metaData.getColumnLabel(i)
                val value = resultSet.getObject(i)
                row[columnName] = value ?: ""
            }
            rows.add(row)
        }
        
        return rows
    }
}

