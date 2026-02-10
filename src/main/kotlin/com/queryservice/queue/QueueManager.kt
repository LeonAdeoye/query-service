package com.queryservice.queue

import com.queryservice.database.DatabaseType
import com.queryservice.execution.AsyncQueryExecutor
import com.queryservice.monitoring.ExecutionTimer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

@Component
class QueueManager(
    private val priorityQueryQueue: PriorityQueryQueue,
    private val asyncQueryExecutor: AsyncQueryExecutor,
    private val queueConfig: QueueConfigProperties
) {
    private val logger = LoggerFactory.getLogger(QueueManager::class.java)
    private val running = AtomicBoolean(true)

    @PostConstruct
    fun startWorkers() {
        if (!queueConfig.enabled) {
            logger.info("Queue is disabled, workers not started")
            return
        }
        logger.info("Starting query queue workers...")
        val scheduler = Schedulers.boundedElastic()
        repeat(queueConfig.highPriorityWorkers) { scheduleProcessQueue(QueryPriority.HIGH, scheduler) }
        repeat(queueConfig.normalPriorityWorkers) { scheduleProcessQueue(QueryPriority.NORMAL, scheduler) }
        repeat(queueConfig.lowPriorityWorkers) { scheduleProcessQueue(QueryPriority.LOW, scheduler) }
        logger.info("Started queue workers (high=${queueConfig.highPriorityWorkers}, normal=${queueConfig.normalPriorityWorkers}, low=${queueConfig.lowPriorityWorkers})")
    }

    @PreDestroy
    fun stopWorkers() {
        logger.info("Stopping queue workers...")
        running.set(false)
    }

    private fun scheduleProcessQueue(priority: QueryPriority, scheduler: reactor.core.scheduler.Scheduler) {
        Mono.fromRunnable<Unit> { processQueue(priority) }
            .delayElement(Duration.ofMillis(100))
            .repeat { running.get() }
            .subscribeOn(scheduler)
            .subscribe(
                { _: Unit -> },
                { e: Throwable -> logger.error("Error in queue worker for priority $priority", e) }
            )
    }

    private fun processQueue(priority: QueryPriority) {
        val queuedQuery = priorityQueryQueue.dequeue()
        if (queuedQuery != null && queuedQuery.priority == priority) {
            logger.debug("Processing query ${queuedQuery.id} with priority ${queuedQuery.priority}")
            val timer = ExecutionTimer()
            asyncQueryExecutor.executeQueryAsync(
                queuedQuery.sql,
                queuedQuery.databaseType,
                queuedQuery.parameters,
                timer
            ).subscribeOn(Schedulers.boundedElastic()).subscribe(
                { },
                { e -> logger.error("Error executing queued query ${queuedQuery.id}", e) }
            )
        } else if (queuedQuery != null) {
            priorityQueryQueue.enqueue(queuedQuery)
        }
    }

    /**
     * Non-blocking: enqueues and runs the query on the bounded elastic scheduler, returns Mono with result.
     */
    fun executeQueued(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        priority: QueryPriority
    ): Mono<List<Map<String, Any>>> {
        val queuedQuery = QueuedQuery(
            id = UUID.randomUUID().toString(),
            sql = sql,
            databaseType = databaseType,
            parameters = parameters,
            priority = priority
        )
        priorityQueryQueue.enqueue(queuedQuery)
        val timer = ExecutionTimer()
        return asyncQueryExecutor.executeQueryAsync(sql, databaseType, parameters, timer)
    }
}

