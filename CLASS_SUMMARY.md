# Query Service – Main Classes Summary

| Class | Package | What it does | How it's used |
|-------|---------|--------------|----------------|
| **QueryServiceApplication** | `com.queryservice` | Spring Boot entry point; enables caching and async processing. | Started by `main()`; bootstraps the app and component scanning. |
| **QueryController** | `com.queryservice.api` | REST API for query execution, streaming, saved queries, and query CRUD. | Handles HTTP at `/api/v1/queries`; calls `QueryService` and `QuerySourceTracker`; maps errors via `ErrorCodeRegistry`. |
| **GlobalExceptionHandler** | `com.queryservice.api` | Central exception handling for REST layer. | `@RestControllerAdvice`; maps `QueryServiceException` and generic `Exception` to HTTP status and JSON body; logs via `ErrorCodeRegistry`. |
| **QueryService** | `com.queryservice.service` | Core orchestration: validation, cache, retry, execution (regular / big-data / streaming), metrics. | Injected into `QueryController`; uses cache, executors, retry, validators, repository, and metrics to run and stream queries. |
| **QueryExecutor** | `com.queryservice.execution` | Synchronous JDBC execution: gets connection, resolves parameters, runs SQL, maps `ResultSet` to `List<Map<String, Any>>`. | Used only by `AsyncQueryExecutor` for the actual DB call on the bounded-elastic scheduler. |
| **AsyncQueryExecutor** | `com.queryservice.execution` | Wraps `QueryExecutor` in a non-blocking `Mono`, running on `boundedElastic`. | Used by `QueryService` (regular/queued execution) and `QueueManager` (background workers) for async query runs. |
| **StreamingQueryExecutor** | `com.queryservice.execution` | Runs a query and streams rows as `Flux<Map<String, Any>>` with configurable fetch size. | Used by `QueryService` for streaming endpoints and by `BigDataQueryExecutor` as input to file export. |
| **BigDataQueryExecutor** | `com.queryservice.execution` | Runs a query via streaming, then exports the full result to a file (CSV/JSON/Excel). | Used by `QueryService` when `request.bigData` is true; returns `Mono<String>` (file path). |
| **ConnectionPoolManager** | `com.queryservice.database` | Provides JDBC connections for Oracle and MSSQL from HikariCP data sources; supports pool stats and health checks. | Injected into `QueryExecutor` and `StreamingQueryExecutor` to obtain connections by `DatabaseType`. |
| **DatabaseConfig** | `com.queryservice.database` | Builds HikariCP config and beans for Oracle and MSSQL from `query-service.databases` properties. | Loaded at startup; supplies `oracleDataSource` and `mssqlDataSource` used by `ConnectionPoolManager`. |
| **QueryCacheService** | `com.queryservice.cache` | Get/put query results in the configured cache (e.g. Caffeine) by SQL + database type + parameters. | Used by `QueryService` to check cache before execution and to store results when cache is enabled. |
| **CacheKeyGenerator** | `com.queryservice.cache` | Generates a deterministic cache key (SHA-256 of JSON) from SQL, database type, and parameters. | Used by `QueryCacheService` for every get/put so cache keys are consistent. |
| **CacheConfig** | `com.queryservice.cache` | Configures Caffeine cache manager and `queryResults` cache from `query-service.cache` properties. | Applied at startup; provides the `CacheManager` bean used by `QueryCacheService`. |
| **QueueManager** | `com.queryservice.queue` | Starts/stops priority-based workers (Reactor), polls `PriorityQueryQueue`, and runs enqueued queries via `AsyncQueryExecutor`; exposes `executeQueued` returning `Mono`. | `@PostConstruct` starts workers; `@PreDestroy` stops them; callers use `executeQueued(...)` for non-blocking queued execution. |
| **PriorityQueryQueue** | `com.queryservice.queue` | In-memory priority queue (e.g. `PriorityBlockingQueue`) for `QueuedQuery`; enqueue/dequeue with max size check. | Used by `QueueManager` to enqueue work and to dequeue items for worker processing. |
| **RetryService** | `com.queryservice.retry` | Applies Reactor `Retry.backoff` to a `Mono`: retries on retryable failures with backoff and logs exhaustion. | Used by `QueryService` to wrap `AsyncQueryExecutor` and `BigDataQueryExecutor` calls in retry logic. |
| **RetryPolicy** | `com.queryservice.retry` | Decides if a throwable is retryable (e.g. connection/timeout/transient SQL and certain `QueryServiceException` codes). | Used by `RetryService` in the Retry spec’s `filter` to decide whether to retry. |
| **FileExporter** | `com.queryservice.export` | Consumes a `Flux` of rows and writes CSV, JSON, or Excel to a temp file; returns `Mono<String>` (file path). | Used by `BigDataQueryExecutor`; runs heavy I/O on `boundedElastic`. |
| **ParameterResolver** | `com.queryservice.query` | Replaces named (`:name`) or positional parameters in SQL and builds the ordered list of values; handles type conversion per DB. | Used by `QueryExecutor` and `StreamingQueryExecutor` to resolve SQL and set `PreparedStatement` parameters. |
| **ParameterValidator** | `com.queryservice.query` | Validates parameters (e.g. injection patterns) and allowed types before execution. | Used by `QueryService` at the start of `executeQuery` and `streamQuery`. |
| **QueryMetricsCollector** | `com.queryservice.monitoring` | Logs query execution metrics (duration, success, error code, metadata) and can record errors in `ErrorCodeRegistry`. | Used by `QueryService` on success and failure of execute/stream flows. |
| **ExecutionTimer** | `com.queryservice.monitoring` | Tracks start/end of query execution and JSON transform; exposes durations in ms. | Created per request in `QueryService` and passed into executors; used for response timings and metrics. |
| **QuerySourceTracker** | `com.queryservice.tracking` | Extracts request metadata (source, userId, clientId, requestId) from HTTP headers. | Used by `QueryController` to build `QueryMetadata` for each request before calling `QueryService`. |
| **ErrorCodeRegistry** | `com.queryservice.error` | Maps error codes to messages and logs errors (code + message + optional throwable). | Used by `QueryController`, `GlobalExceptionHandler`, `QueryService` (via metrics), and several services for consistent error logging. |
| **QueryServiceException** | `com.queryservice.error` | Domain exception carrying an error code (from `ErrorCodes`) and message. | Thrown by services and executors; caught by controller and `GlobalExceptionHandler` to map to HTTP and body. |
| **QueryRepository** | `com.queryservice.repository` | JPA repository for `QueryEntity` (findById, findByName, save). | Used by `QueryService` to load saved queries and to save new ones. |

---

## Supporting types (data / config / enums)

| Type | Package | What it does | How it's used |
|------|---------|--------------|----------------|
| **QueryRequestDTO** | `api.dto` | Request body: sql, databaseType, parameters, cache options, bigData, exportFormat, etc. | Deserialized in controller; passed to `QueryService.executeQuery` / `streamQuery`. |
| **QueryResponseDTO** | `api.dto` | Response: queryId, success, data, rowCount, durations, fileUrl. | Returned by `QueryService.executeQuery` and sent in HTTP response. |
| **SaveQueryRequestDTO** | `api.dto` | Payload to create/update a saved query (name, sql, databaseType, parametersSchema). | Used by `QueryController` and `QueryService.saveQuery`. |
| **StreamResponseDTO** | `api.dto` | DTO for streaming responses. | Used where streaming API returns a structured DTO. |
| **QueryEntity** | `repository` | JPA entity for stored queries (id, name, sql, databaseType, parametersSchema, createdBy, timestamps). | Persisted via `QueryRepository`; loaded for “execute saved query” and get-by-id. |
| **DatabaseConfigProperties** | `database` | Binds `query-service.databases` (oracle, mssql connection config). | Injected into `DatabaseConfig` to create HikariCP beans. |
| **DatabaseConnectionConfig** | `database` | JDBC URL, username, password, pool settings for one DB. | Part of `DatabaseConfigProperties`. |
| **PoolConfig** | `database` | Pool size and timeouts. | Nested under `DatabaseConnectionConfig`. |
| **DatabaseType** | `database` | Enum: ORACLE, MSSQL. | Used in requests, entities, and to select data source in `ConnectionPoolManager`. |
| **QueuedQuery** | `queue` | Queued item: id, sql, databaseType, parameters, priority, timestamp, sequence. | Stored in `PriorityQueryQueue`; processed by `QueueManager` workers. |
| **QueueConfigProperties** | `queue` | Binds queue settings (enabled, worker counts, maxQueueSize). | Injected into `QueueManager` and `PriorityQueryQueue`. |
| **QueryPriority** | `queue` | Enum: HIGH, NORMAL, LOW. | Used in queue and request DTOs for prioritization. |
| **RetryConfigProperties** | `retry` | Binds retry settings (enabled, maxAttempts, intervals, multiplier). | Injected into `RetryService` to build the Retry spec. |
| **ExportFormat** | `export` | Enum: CSV, JSON, EXCEL. | Used in big-data requests and `FileExporter`. |
| **QueryMetadata** | `tracking` | source, userId, clientId, requestId, timestamp. | Built by `QuerySourceTracker`; passed through `QueryService` for metrics and logging. |
| **QueryExecutionContext** | `monitoring` | Bundles queryId, metadata, and timing fields for metrics. | Used by `QueryMetricsCollector` when logging execution. |
| **ErrorCodes** | `error` | Constants for all error codes (e.g. QRS-ERR-001, …). | Referenced when throwing `QueryServiceException` and when mapping errors in controller/handler. |
| **PoolStats** | `database` | Data class for active/idle/total connections and threads awaiting. | Returned by `ConnectionPoolManager.getPoolStats`. |
