package com.queryservice.queue

import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

@Component
@ConfigurationProperties(prefix = "query-service.queue")
data class QueueConfigProperties(
    val enabled: Boolean = true,
    val highPriorityWorkers: Int = 5,
    val normalPriorityWorkers: Int = 10,
    val lowPriorityWorkers: Int = 5,
    val maxQueueSize: Int = 1000
)

data class QueuedQuery(
    val id: String,
    val sql: String,
    val databaseType: com.queryservice.database.DatabaseType,
    val parameters: Map<String, Any>?,
    val priority: QueryPriority,
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = sequenceCounter.getAndIncrement()
) : Comparable<QueuedQuery> {
    override fun compareTo(other: QueuedQuery): Int {
        val priorityCompare = priority.compareTo(other.priority)
        if (priorityCompare != 0) return priorityCompare
        return sequenceNumber.compareTo(other.sequenceNumber)
    }
    
    companion object {
        private val sequenceCounter = AtomicLong(0)
    }
}

@Component
class PriorityQueryQueue(
    private val queueConfig: QueueConfigProperties
) {
    private val logger = LoggerFactory.getLogger(PriorityQueryQueue::class.java)
    private val queue = PriorityBlockingQueue<QueuedQuery>()
    
    fun enqueue(query: QueuedQuery) {
        if (queue.size >= queueConfig.maxQueueSize) {
            throw QueryServiceException(
                ErrorCodes.QUEUE_FULL,
                "Query queue is full (max size: ${queueConfig.maxQueueSize})"
            )
        }
        queue.offer(query)
        logger.debug("Enqueued query ${query.id} with priority ${query.priority}")
    }
    
    fun dequeue(): QueuedQuery? {
        return queue.poll()
    }
    
    fun size(): Int = queue.size
    
    fun isEmpty(): Boolean = queue.isEmpty()
}

