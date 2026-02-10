package com.queryservice.api

import com.queryservice.api.dto.*
import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import com.queryservice.service.QueryService
import com.queryservice.tracking.QuerySourceTracker
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/queries")
class QueryController(
    private val queryService: QueryService,
    private val querySourceTracker: QuerySourceTracker,
    private val errorCodeRegistry: ErrorCodeRegistry
) {
    private val logger = LoggerFactory.getLogger(QueryController::class.java)
    
    @PostMapping("/execute")
    fun executeQuery(
        @Valid @RequestBody request: QueryRequestDTO,
        @RequestHeader headers: Map<String, String>
    ): Mono<ResponseEntity<QueryResponseDTO>> {
        val metadata = querySourceTracker.extractMetadataFromHeaders(headers)
        val priority = headers["X-Query-Priority"]?.uppercase()?.let { parseQueryPriority(it) } ?: request.priority
        val requestWithPriority = request.copy(priority = priority)

        return queryService.executeQuery(requestWithPriority, metadata)
            .map { ResponseEntity.ok(it) }
            .onErrorResume { error ->
                handleError(error, "executeQuery")
            }
    }
    
    @PostMapping("/{queryId}/execute")
    fun executeSavedQuery(
        @PathVariable queryId: String,
        @RequestBody(required = false) parameters: Map<String, Any>?,
        @RequestHeader headers: Map<String, String>
    ): Mono<ResponseEntity<QueryResponseDTO>> {
        val metadata = querySourceTracker.extractMetadataFromHeaders(headers)
        val priority = headers["X-Query-Priority"]?.uppercase()?.let { parseQueryPriority(it) } ?: QueryPriority.NORMAL

        return queryService.executeSavedQuery(queryId, parameters, metadata, priority)
            .map { ResponseEntity.ok(it) }
            .onErrorResume { error ->
                handleError(error, "executeSavedQuery")
            }
    }
    
    @GetMapping("/{queryId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamQuery(
        @PathVariable queryId: String,
        @RequestParam(required = false, defaultValue = "100") pageSize: Int,
        @RequestParam(required = false) sql: String?,
        @RequestParam(required = false) databaseType: com.queryservice.database.DatabaseType?,
        @RequestParam(required = false) parameters: Map<String, Any>?,
        @RequestHeader headers: Map<String, String>
    ): Flux<ServerSentEvent<Map<String, Any>>> {
        val metadata = querySourceTracker.extractMetadataFromHeaders(headers)
        
        val request = if (sql != null && databaseType != null) {
            QueryRequestDTO(
                sql = sql,
                databaseType = databaseType,
                parameters = parameters
            )
        } else {
            // Get saved query
            val savedQuery = queryService.getQuery(queryId)
            QueryRequestDTO(
                sql = savedQuery.sql,
                databaseType = savedQuery.databaseType,
                parameters = parameters
            )
        }
        
        return queryService.streamQuery(request, pageSize, metadata)
            .map { data -> ServerSentEvent.builder<Map<String, Any>>().data(data).build() }
            .onErrorResume { error ->
                logger.error("Streaming error", error)
                Flux.error(error)
            }
    }
    
    @PostMapping
    fun saveQuery(
        @Valid @RequestBody request: SaveQueryRequestDTO,
        @RequestHeader headers: Map<String, String>
    ): Mono<ResponseEntity<com.queryservice.repository.QueryEntity>> {
        val metadata = querySourceTracker.extractMetadataFromHeaders(headers)
        
        return Mono.fromCallable {
            queryService.saveQuery(request, metadata)
        }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
            .onErrorResume { error ->
                handleError(error, "saveQuery")
            }
    }
    
    @GetMapping("/{queryId}")
    fun getQuery(@PathVariable queryId: String): Mono<ResponseEntity<com.queryservice.repository.QueryEntity>> {
        return Mono.fromCallable {
            queryService.getQuery(queryId)
        }
            .map { ResponseEntity.ok(it) }
            .onErrorResume { error ->
                handleError(error, "getQuery")
            }
    }
    
    private fun parseQueryPriority(value: String): QueryPriority = when (value) {
        "HIGH" -> QueryPriority.HIGH
        "LOW" -> QueryPriority.LOW
        else -> QueryPriority.NORMAL
    }

    private fun <T> handleError(error: Throwable, operation: String): Mono<ResponseEntity<T>> {
        val errorCode = when (error) {
            is QueryServiceException -> error.errorCode
            else -> ErrorCodes.UNKNOWN_ERROR
        }
        
        errorCodeRegistry.logError(errorCode, "Error in $operation", error)
        
        val status = when (errorCode) {
            ErrorCodes.QUERY_NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorCodes.INVALID_QUERY_REQUEST,
            ErrorCodes.INVALID_PARAMETERS,
            ErrorCodes.PARAMETER_VALIDATION_ERROR -> HttpStatus.BAD_REQUEST
            ErrorCodes.QUEUE_FULL -> HttpStatus.SERVICE_UNAVAILABLE
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        
        return Mono.just(ResponseEntity.status(status).build())
    }
}

