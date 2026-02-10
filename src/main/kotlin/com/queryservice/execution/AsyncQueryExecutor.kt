package com.queryservice.execution

import com.queryservice.database.DatabaseType
import com.queryservice.monitoring.ExecutionTimer
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class AsyncQueryExecutor(
    private val queryExecutor: QueryExecutor
) {

    /**
     * Non-blocking query execution using Project Reactor.
     * Subscribe on boundedElastic to avoid blocking the reactive thread pool.
     */
    fun executeQueryAsync(
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

