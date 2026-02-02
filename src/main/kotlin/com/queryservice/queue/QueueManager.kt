package com.queryservice.queue

import com.queryservice.database.DatabaseType
import com.queryservice.execution.AsyncQueryExecutor
import com.queryservice.monitoring.ExecutionTimer
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
class QueueManager(
    private val priorityQueryQueue: PriorityQueryQueue,
    private val asyncQueryExecutor: AsyncQueryExecutor,
    private val queueConfig: QueueConfigProperties
) {
    private val logger = LoggerFactory.getLogger(QueueManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val workers = mutableListOf<Job>()
    
    @PostConstruct
    fun startWorkers() {
        if (!queueConfig.enabled) {
            logger.info("Queue is disabled, workers not started")
            return
        }
        
        logger.info("Starting query queue workers...")
        
        // High priority workers
        repeat(queueConfig.highPriorityWorkers) {
            workers.add(scope.launch {
                processQueue(QueryPriority.HIGH)
            })
        }
        
        // Normal priority workers
        repeat(queueConfig.normalPriorityWorkers) {
            workers.add(scope.launch {
                processQueue(QueryPriority.NORMAL)
            })
        }
        
        // Low priority workers
        repeat(queueConfig.lowPriorityWorkers) {
            workers.add(scope.launch {
                processQueue(QueryPriority.LOW)
            })
        }
        
        logger.info("Started ${workers.size} queue workers")
    }
    
    @PreDestroy
    fun stopWorkers() {
        logger.info("Stopping queue workers...")
        workers.forEach { it.cancel() }
        scope.cancel()
    }
    
    private suspend fun processQueue(priority: QueryPriority) {
        while (isActive) {
            try {
                val queuedQuery = priorityQueryQueue.dequeue()
                if (queuedQuery != null && queuedQuery.priority == priority) {
                    logger.debug("Processing query ${queuedQuery.id} with priority ${queuedQuery.priority}")
                    val timer = ExecutionTimer()
                    asyncQueryExecutor.executeQueryAsync(
                        queuedQuery.sql,
                        queuedQuery.databaseType,
                        queuedQuery.parameters,
                        timer
                    )
                } else if (queuedQuery != null) {
                    // Wrong priority, put it back
                    priorityQueryQueue.enqueue(queuedQuery)
                }
                delay(100) // Small delay to prevent busy waiting
            } catch (e: Exception) {
                logger.error("Error processing queue", e)
                delay(1000) // Wait before retrying
            }
        }
    }
    
    suspend fun executeQueued(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        priority: QueryPriority
    ): List<Map<String, Any>> {
        val queryId = UUID.randomUUID().toString()
        val queuedQuery = QueuedQuery(
            id = queryId,
            sql = sql,
            databaseType = databaseType,
            parameters = parameters,
            priority = priority
        )
        
        priorityQueryQueue.enqueue(queuedQuery)
        
        // Wait for result (simplified - in production, use a result map or channel)
        val timer = ExecutionTimer()
        return asyncQueryExecutor.executeQueryAsync(sql, databaseType, parameters, timer)
    }
}

