package com.queryservice.execution

import com.queryservice.database.ConnectionPoolManager
import com.queryservice.database.DatabaseType
import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import com.queryservice.query.ParameterResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.sql.ResultSet
import java.sql.ResultSetMetaData

@Component
class StreamingQueryExecutor(
    private val connectionPoolManager: ConnectionPoolManager,
    private val parameterResolver: ParameterResolver
) {
    private val logger = LoggerFactory.getLogger(StreamingQueryExecutor::class.java)
    
    fun streamQuery(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        pageSize: Int = 100
    ): Flux<Map<String, Any>> {
        return Flux.create { sink ->
            try {
                val connection = connectionPoolManager.getConnection(databaseType)
                val (resolvedSql, paramValues) = parameterResolver.resolveParameters(sql, parameters, databaseType)
                
                connection.prepareStatement(
                    resolvedSql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
                ).apply {
                    fetchSize = pageSize
                    paramValues.forEachIndexed { index, value ->
                        parameterResolver.setParameter(this, index + 1, value)
                    }
                    
                    val resultSet = executeQuery()
                    val metaData: ResultSetMetaData = resultSet.metaData
                    val columnCount = metaData.columnCount
                    
                    var count = 0
                    while (resultSet.next() && !sink.isCancelled) {
                        val row = mutableMapOf<String, Any>()
                        for (i in 1..columnCount) {
                            val columnName = metaData.getColumnLabel(i)
                            val value = resultSet.getObject(i)
                            row[columnName] = value ?: ""
                        }
                        sink.next(row)
                        count++
                    }
                    
                    resultSet.close()
                    close()
                    connection.close()
                    sink.complete()
                }
            } catch (e: Exception) {
                logger.error("Streaming query execution failed", e)
                sink.error(
                    QueryServiceException(
                        ErrorCodes.STREAMING_ERROR,
                        "Failed to stream query: ${e.message}",
                        e
                    )
                )
            }
        }
    }
}

