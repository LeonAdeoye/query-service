package com.queryservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.queryservice.api.dto.*
import com.queryservice.cache.QueryCacheService
import com.queryservice.database.DatasourceRegistry
import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import com.queryservice.execution.AsyncQueryExecutor
import com.queryservice.execution.BigDataQueryExecutor
import com.queryservice.execution.StreamingQueryExecutor
import com.queryservice.export.ExportFormat
import com.queryservice.monitoring.ExecutionTimer
import com.queryservice.monitoring.QueryMetricsCollector
import com.queryservice.query.LikePatternValidator
import com.queryservice.query.ParameterResolver
import com.queryservice.query.ParameterValidator
import com.queryservice.queue.QueuedQueryResult
import com.queryservice.api.dto.QueryPriority
import com.queryservice.queue.QueueConfigProperties
import com.queryservice.queue.QueueManager
import com.queryservice.repository.QueryEntity
import com.queryservice.repository.QueryRepository
import com.queryservice.retry.RetryService
import com.queryservice.tracking.QueryMetadata
import com.queryservice.tracking.QuerySourceTracker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

@Service
class QueryService(
    private val queryRepository: QueryRepository,
    private val queryCacheService: QueryCacheService,
    private val asyncQueryExecutor: AsyncQueryExecutor,
    private val streamingQueryExecutor: StreamingQueryExecutor,
    private val bigDataQueryExecutor: BigDataQueryExecutor,
    private val parameterResolver: ParameterResolver,
    private val parameterValidator: ParameterValidator,
    private val likePatternValidator: LikePatternValidator,
    private val retryService: RetryService,
    private val queryMetricsCollector: QueryMetricsCollector,
    private val querySourceTracker: QuerySourceTracker,
    private val objectMapper: ObjectMapper,
    private val queueManager: QueueManager,
    private val queueConfig: QueueConfigProperties,
    private val datasourceRegistry: DatasourceRegistry
) {
    private val logger = LoggerFactory.getLogger(QueryService::class.java)
    
    fun executeQuery(
        request: QueryRequestDTO,
        metadata: QueryMetadata
    ): Mono<QueryResponseDTO> {
        val queryId = request.queryId ?: UUID.randomUUID().toString()
        val timer = ExecutionTimer()
        
        return Mono.fromCallable {
            parameterValidator.validateParameters(request.sql, request.parameters)
            parameterValidator.validateParameterTypes(request.parameters)
            likePatternValidator.validateNoDoubleLikeWildcard(request.sql)
            if (!datasourceRegistry.isValid(request.datasourceId)) {
                throw QueryServiceException(
                    com.queryservice.error.ErrorCodes.DATASOURCE_NOT_FOUND,
                    "Unknown datasource id: ${request.datasourceId}. Valid ids: ${datasourceRegistry.getValidIds().sorted().joinToString()}"
                )
            }
            request
        }.flatMap { req ->
            if (req.cacheEnabled) {
                val cached = queryCacheService.get(req.sql, req.datasourceId, req.parameters)
                if (cached != null) {
                    timer.endJsonTransform()
                    return@flatMap Mono.just(
                        QueryResponseDTO(
                            queryId = queryId,
                            success = true,
                            data = cached,
                            rowCount = cached.size,
                            executionDurationMs = 0,
                            jsonTransformDurationMs = 0,
                            totalDurationMs = timer.getTotalDurationMs()
                        )
                    )
                }
            }
            when {
                req.bigData -> executeBigDataQuery(req, queryId, timer)
                queueConfig.enabled -> executeViaQueue(req, queryId, timer)
                else -> executeRegularQuery(req, queryId, timer)
            }
        }.flatMap { result ->
            if (request.cacheEnabled && result.data != null) {
                queryCacheService.put(
                    request.sql,
                    request.datasourceId,
                    request.parameters,
                    result.data,
                    request.cacheTtlSeconds
                )
            }
            Mono.just(result)
        }.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .doOnSuccess { response ->
            queryMetricsCollector.logMetrics(queryId, metadata, timer, response.success)
        }
        .doOnError { error ->
            logger.error("Query execution failed", error)
            val errorCode = if (error is QueryServiceException) error.errorCode else ErrorCodes.UNKNOWN_ERROR
            queryMetricsCollector.logQueryExecution(
                com.queryservice.monitoring.QueryExecutionContext(
                    queryId = queryId,
                    metadata = metadata
                ),
                success = false,
                errorCode = errorCode
            )
        }
    }
    
    /**
     * Executes a regular (non–big-data) query via the priority queue. The request's priority
     * (e.g. from X-Query-Priority header) determines which worker pool processes it.
     */
    private fun executeViaQueue(
        request: QueryRequestDTO,
        queryId: String,
        timer: ExecutionTimer
    ): Mono<QueryResponseDTO> {
        return queueManager.executeQueued(
            request.sql,
            request.datasourceId,
            request.parameters,
            request.priority
        ).map { qr: QueuedQueryResult ->
            timer.startJsonTransform()
            timer.endJsonTransform()
            QueryResponseDTO(
                queryId = queryId,
                success = true,
                data = qr.data,
                rowCount = qr.data.size,
                executionDurationMs = qr.timer.getQueryExecutionDurationMs(),
                jsonTransformDurationMs = timer.getJsonTransformDurationMs(),
                totalDurationMs = timer.getTotalDurationMs()
            )
        }
    }

    private fun executeRegularQuery(
        request: QueryRequestDTO,
        queryId: String,
        timer: ExecutionTimer
    ): Mono<QueryResponseDTO> {
        timer.startQueryExecution()
        return retryService.executeWithRetry(
            asyncQueryExecutor.executeQueryAsync(
                request.sql,
                request.datasourceId,
                request.parameters,
                timer
            )
        ).map { jsonResult ->
            timer.startJsonTransform()
            timer.endJsonTransform()
            QueryResponseDTO(
                queryId = queryId,
                success = true,
                data = jsonResult,
                rowCount = jsonResult.size,
                executionDurationMs = timer.getQueryExecutionDurationMs(),
                jsonTransformDurationMs = timer.getJsonTransformDurationMs(),
                totalDurationMs = timer.getTotalDurationMs()
            )
        }
    }
    
    private fun executeBigDataQuery(
        request: QueryRequestDTO,
        queryId: String,
        timer: ExecutionTimer
    ): Mono<QueryResponseDTO> {
        timer.startQueryExecution()
        val exportFormat = request.exportFormat?.let {
            com.queryservice.export.ExportFormat.valueOf(it.name)
        } ?: com.queryservice.export.ExportFormat.CSV
        return retryService.executeWithRetry(
            bigDataQueryExecutor.executeBigDataQuery(
                request.sql,
                request.datasourceId,
                request.parameters,
                exportFormat
            )
        ).map { fileUrl ->
            timer.endQueryExecution()
            timer.endJsonTransform()
            QueryResponseDTO(
                queryId = queryId,
                success = true,
                data = null,
                rowCount = 0,
                executionDurationMs = timer.getQueryExecutionDurationMs(),
                jsonTransformDurationMs = timer.getJsonTransformDurationMs(),
                totalDurationMs = timer.getTotalDurationMs(),
                fileUrl = fileUrl
            )
        }
    }
    
    fun executeSavedQuery(
        queryId: String,
        parameters: Map<String, Any>?,
        metadata: QueryMetadata,
        priority: QueryPriority = QueryPriority.NORMAL
    ): Mono<QueryResponseDTO> {
        val queryEntity = queryRepository.findById(queryId)
            .orElseThrow {
                QueryServiceException(
                    ErrorCodes.QUERY_NOT_FOUND,
                    "Query not found: $queryId"
                )
            }

        val request = QueryRequestDTO(
            sql = queryEntity.sql,
            datasourceId = queryEntity.datasourceId,
            parameters = parameters,
            priority = priority
        )

        return executeQuery(request, metadata)
    }
    
    fun streamQuery(
        request: QueryRequestDTO,
        pageSize: Int,
        metadata: QueryMetadata
    ): Flux<Map<String, Any>> {
        val queryId = request.queryId ?: UUID.randomUUID().toString()

        parameterValidator.validateParameters(request.sql, request.parameters)
        parameterValidator.validateParameterTypes(request.parameters)
        likePatternValidator.validateNoDoubleLikeWildcard(request.sql)
        if (!datasourceRegistry.isValid(request.datasourceId)) {
            throw QueryServiceException(
                com.queryservice.error.ErrorCodes.DATASOURCE_NOT_FOUND,
                "Unknown datasource id: ${request.datasourceId}. Valid ids: ${datasourceRegistry.getValidIds().sorted().joinToString()}"
            )
        }

        return streamingQueryExecutor.streamQuery(
            request.sql,
            request.datasourceId,
            request.parameters,
            pageSize
        ).doOnError { error ->
            logger.error("Streaming query failed", error)
            val errorCode = if (error is QueryServiceException) error.errorCode else ErrorCodes.STREAMING_ERROR
            queryMetricsCollector.logQueryExecution(
                com.queryservice.monitoring.QueryExecutionContext(
                    queryId = queryId,
                    metadata = metadata
                ),
                success = false,
                errorCode = errorCode
            )
        }
    }
    
    fun saveQuery(request: SaveQueryRequestDTO, metadata: QueryMetadata): QueryEntity {
        val queryEntity = QueryEntity(
            name = request.name,
            sql = request.sql,
            datasourceId = request.datasourceId,
            parametersSchema = if (request.parametersSchema != null) {
                objectMapper.writeValueAsString(request.parametersSchema)
            } else null,
            createdBy = metadata.source ?: metadata.userId
        )
        
        return queryRepository.save(queryEntity)
    }
    
    fun getQuery(queryId: String): QueryEntity {
        return queryRepository.findById(queryId)
            .orElseThrow {
                QueryServiceException(
                    ErrorCodes.QUERY_NOT_FOUND,
                    "Query not found: $queryId"
                )
            }
    }
}

