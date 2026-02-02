package com.queryservice.execution

import com.queryservice.database.DatabaseType
import com.queryservice.monitoring.ExecutionTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class AsyncQueryExecutor(
    private val queryExecutor: QueryExecutor
) {
    
    suspend fun executeQueryAsync(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        timer: ExecutionTimer
    ): List<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            timer.startQueryExecution()
            try {
                queryExecutor.executeQuery(sql, databaseType, parameters)
            } finally {
                timer.endQueryExecution()
            }
        }
    }
    
    fun executeQueryReactive(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        timer: ExecutionTimer
    ): Mono<List<Map<String, Any>>> {
        return Mono.fromCallable {
            timer.startQueryExecution()
            try {
                queryExecutor.executeQuery(sql, databaseType, parameters)
            } finally {
                timer.endQueryExecution()
            }
        }.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
    }
}

