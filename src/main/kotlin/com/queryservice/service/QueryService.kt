package com.queryservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.queryservice.api.dto.*
import com.queryservice.cache.QueryCacheService
import com.queryservice.database.DatabaseType
import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import com.queryservice.execution.AsyncQueryExecutor
import com.queryservice.execution.BigDataQueryExecutor
import com.queryservice.execution.StreamingQueryExecutor
import com.queryservice.export.ExportFormat
import com.queryservice.monitoring.ExecutionTimer
import com.queryservice.monitoring.QueryMetricsCollector
import com.queryservice.query.ParameterResolver
import com.queryservice.query.ParameterValidator
import com.queryservice.queue.QueryPriority
import com.queryservice.repository.QueryEntity
import com.queryservice.repository.QueryRepository
import com.queryservice.retry.RetryService
import com.queryservice.tracking.QueryMetadata
import com.queryservice.tracking.QuerySourceTracker
import kotlinx.coroutines.runBlocking
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
    private val retryService: RetryService,
    private val queryMetricsCollector: QueryMetricsCollector,
    private val querySourceTracker: QuerySourceTracker,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(QueryService::class.java)
    
    fun executeQuery(
        request: QueryRequestDTO,
        metadata: QueryMetadata
    ): Mono<QueryResponseDTO> {
        val queryId = request.queryId ?: UUID.randomUUID().toString()
        val timer = ExecutionTimer()
        
        return Mono.fromCallable {
            // Validate parameters
            parameterValidator.validateParameters(request.sql, request.parameters)
            parameterValidator.validateParameterTypes(request.parameters)
            
            // Check cache if enabled
            if (request.cacheEnabled) {
                val cached = queryCacheService.get(request.sql, request.databaseType, request.parameters)
                if (cached != null) {
                    timer.endJsonTransform()
                    return@fromCallable QueryResponseDTO(
                        queryId = queryId,
                        success = true,
                        data = cached,
                        rowCount = cached.size,
                        executionDurationMs = 0,
                        jsonTransformDurationMs = 0,
                        totalDurationMs = timer.getTotalDurationMs()
                    )
                }
            }
            
            // Execute query
            val result = if (request.bigData) {
                executeBigDataQuery(request, queryId, timer)
            } else {
                executeRegularQuery(request, queryId, timer)
            }
            
            // Cache result if enabled
            if (request.cacheEnabled && result.data != null) {
                queryCacheService.put(
                    request.sql,
                    request.databaseType,
                    request.parameters,
                    result.data,
                    request.cacheTtlSeconds
                )
            }
            
            result
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
    
    private fun executeRegularQuery(
        request: QueryRequestDTO,
        queryId: String,
        timer: ExecutionTimer
    ): QueryResponseDTO {
        timer.startQueryExecution()
        
        val result = runBlocking {
            retryService.executeWithRetry {
                asyncQueryExecutor.executeQueryAsync(
                    request.sql,
                    request.databaseType,
                    request.parameters,
                    timer
                )
            }
        }
        
        timer.startJsonTransform()
        val jsonResult = result // Already in Map format
        timer.endJsonTransform()
        
        return QueryResponseDTO(
            queryId = queryId,
            success = true,
            data = jsonResult,
            rowCount = jsonResult.size,
            executionDurationMs = timer.getQueryExecutionDurationMs(),
            jsonTransformDurationMs = timer.getJsonTransformDurationMs(),
            totalDurationMs = timer.getTotalDurationMs()
        )
    }
    
    private fun executeBigDataQuery(
        request: QueryRequestDTO,
        queryId: String,
        timer: ExecutionTimer
    ): QueryResponseDTO {
        timer.startQueryExecution()
        
        val fileUrl = runBlocking {
            retryService.executeWithRetry {
                bigDataQueryExecutor.executeBigDataQuery(
                    request.sql,
                    request.databaseType,
                    request.parameters,
                    request.exportFormat ?: ExportFormat.CSV
                )
            }
        }
        
        timer.endQueryExecution()
        timer.endJsonTransform()
        
        return QueryResponseDTO(
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
    
    fun executeSavedQuery(
        queryId: String,
        parameters: Map<String, Any>?,
        metadata: QueryMetadata
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
            databaseType = queryEntity.databaseType,
            parameters = parameters
        )
        
        return executeQuery(request, metadata)
    }
    
    fun streamQuery(
        request: QueryRequestDTO,
        pageSize: Int,
        metadata: QueryMetadata
    ): Flux<Map<String, Any>> {
        val queryId = request.queryId ?: UUID.randomUUID().toString()
        
        // Validate parameters
        parameterValidator.validateParameters(request.sql, request.parameters)
        parameterValidator.validateParameterTypes(request.parameters)
        
        return streamingQueryExecutor.streamQuery(
            request.sql,
            request.databaseType,
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
            databaseType = request.databaseType,
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

