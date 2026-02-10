package com.queryservice.queue

import com.queryservice.database.DatabaseType
import com.queryservice.execution.AsyncQueryExecutor
import com.queryservice.monitoring.ExecutionTimer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
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
        val queuedQuery = priorityQueryQueue.dequeue() ?: return
        if (queuedQuery.priority != priority) {
            priorityQueryQueue.enqueue(queuedQuery)
            return
        }
        logger.debug("Processing query ${queuedQuery.id} with priority ${queuedQuery.priority}")
        val timer = queuedQuery.timer ?: ExecutionTimer()
        if (queuedQuery.resultSink != null) {
            // Caller is waiting for result: run query and complete the sink
            try {
                queuedQuery.timer?.startQueryExecution()
                val result = asyncQueryExecutor.executeQueryAsync(
                    queuedQuery.sql,
                    queuedQuery.databaseType,
                    queuedQuery.parameters,
                    timer
                ).subscribeOn(Schedulers.boundedElastic()).block()
                if (result != null) {
                    timer.endQueryExecution()
                    queuedQuery.resultSink?.tryEmitValue(QueuedQueryResult(result, timer))
                } else {
                    queuedQuery.resultSink?.tryEmitError(IllegalStateException("Query returned null"))
                }
            } catch (e: Throwable) {
                logger.error("Error executing queued query ${queuedQuery.id}", e)
                queuedQuery.resultSink?.tryEmitError(e)
            }
        } else {
            // Fire-and-forget: run in background
            asyncQueryExecutor.executeQueryAsync(
                queuedQuery.sql,
                queuedQuery.databaseType,
                queuedQuery.parameters,
                timer
            ).subscribeOn(Schedulers.boundedElastic()).subscribe(
                { },
                { e -> logger.error("Error executing queued query ${queuedQuery.id}", e) }
            )
        }
    }

    /**
     * Enqueues the query with the given priority. Returns a Mono that completes when a worker
     * processes the item and runs the query (result or error).
     */
    fun executeQueued(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        priority: QueryPriority
    ): Mono<QueuedQueryResult> {
        val timer = ExecutionTimer()
        val sink = Sinks.one<QueuedQueryResult>()
        val queuedQuery = QueuedQuery(
            id = UUID.randomUUID().toString(),
            sql = sql,
            databaseType = databaseType,
            parameters = parameters,
            priority = priority,
            resultSink = sink,
            timer = timer
        )
        priorityQueryQueue.enqueue(queuedQuery)
        return sink.asMono()
    }
}

