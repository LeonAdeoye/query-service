# Multi-datasource refactor – changes and tracking

This document describes the refactor from a single Oracle + single MSSQL setup to **multiple named datasources** identified by a **datasource id** (string). Configuration is in **application.properties** (YAML removed). A **DatasourceRegistry** provides validation and dialect resolution.

---

## Configuration

- **Format:** `application.properties` (replaced `application.yml`).
- **Datasources:** List under `query-service.datasources[0]`, `query-service.datasources[1]`, etc. Each entry has:
  - `id` – datasource id (e.g. `oracle-primary`, `mssql-primary`) used in the API and pool lookup.
  - `vendor` – `oracle` or `mssql` (used for SQL dialect / parameter binding).
  - `jdbc-url`, `username`, `password`, `pool.*` – connection and pool settings.
- **Adding more datasources:** Add another index, e.g. `query-service.datasources[2].id=oracle-reporting`, etc.

---

## Bullet-point list of code and class changes

### New files
- **`src/main/kotlin/com/queryservice/database/DatasourceConfigProperties.kt`** – Binds `query-service.datasources` (list of `DatasourceEntry`: id, vendor, jdbcUrl, username, password, pool).
- **`src/main/kotlin/com/queryservice/database/DatasourceRegistry.kt`** – Registry of valid datasource ids and id → vendor. Methods: `getVendor(datasourceId)`, `getDatabaseType(datasourceId)`, `isValid(datasourceId)`, `getValidIds()`.
- **`src/main/resources/application.properties`** – All config moved from YAML to properties; datasources defined as list entries.

### Removed / replaced
- **`src/main/resources/application.yml`** – Removed; config lives in `application.properties`.
- **`DatabaseConfigProperties`** (old) – Replaced by `DatasourceConfigProperties` and `DatasourceEntry`.

### Modified – database / config
- **`DatabaseConfig.kt`** – Injects `DatasourceConfigProperties`. Builds a **Map&lt;String, HikariDataSource&gt;** from the datasources list; bean name `queryServiceDataSources`. Driver chosen by `vendor` (oracle / mssql).
- **`ConnectionPoolManager.kt`** – Injects `Map&lt;String, HikariDataSource&gt;` (qualified `queryServiceDataSources`). `getConnection(datasourceId: String)`, `getPoolStats(datasourceId)`, `healthCheck(datasourceId)`. Throws `DATASOURCE_NOT_FOUND` for unknown id.
- **`DatabaseType.kt`** – Unchanged (enum ORACLE, MSSQL). Used only for **dialect** (e.g. in `ParameterResolver`); resolution from datasource id is via `DatasourceRegistry.getDatabaseType(datasourceId)`.

### Modified – API / DTOs / persistence
- **`QueryRequestDTO.kt`** – `databaseType: DatabaseType` replaced with **`datasourceId: String`** (required).
- **`SaveQueryRequestDTO.kt`** – `databaseType: DatabaseType` replaced with **`datasourceId: String`** (required).
- **`QueryEntity.kt`** – `databaseType: DatabaseType` (enum) replaced with **`datasourceId: String`** (plain column). Existing DB: add column `datasource_id`, backfill from `database_type` if needed, then drop `database_type` (or use a migration script).

### Modified – cache
- **`CacheKeyGenerator.kt`** – `generateKey(sql, databaseType, parameters)` → **`generateKey(sql, datasourceId, parameters)`**; key includes `datasourceId` instead of `databaseType.name`.
- **`QueryCacheService.kt`** – All `get` / `put` / `evict` signatures use **`datasourceId: String`** instead of `databaseType: DatabaseType`.

### Modified – queue
- **`PriorityQueryQueue.kt`** – `QueuedQuery.databaseType: DatabaseType` → **`QueuedQuery.datasourceId: String`**. Uses **`com.queryservice.api.dto.QueryPriority`** for priority.
- **`QueueManager.kt`** – All usages of `databaseType` replaced with **`datasourceId`**; `executeQueued(sql, datasourceId, parameters, priority)`. Uses **`api.dto.QueryPriority`**.
- **`QueryPriority.kt`** (in queue package) – **Removed**; queue uses **`com.queryservice.api.dto.QueryPriority`** only.

### Modified – execution
- **`QueryExecutor.kt`** – Injects **`DatasourceRegistry`**. `executeQuery(sql, datasourceId, parameters)`; gets connection via `connectionPoolManager.getConnection(datasourceId)`; gets dialect via `datasourceRegistry.getDatabaseType(datasourceId)` and passes to `ParameterResolver`.
- **`StreamingQueryExecutor.kt`** – Same: injects **`DatasourceRegistry`**; `streamQuery(sql, datasourceId, parameters, pageSize)`; connection by `datasourceId`, dialect from registry.
- **`AsyncQueryExecutor.kt`** – `executeQueryAsync(sql, datasourceId, parameters, timer)`.
- **`BigDataQueryExecutor.kt`** – `executeBigDataQuery(sql, datasourceId, parameters, exportFormat)`.

### Modified – service
- **`QueryService.kt`** – Injects **`DatasourceRegistry`**. All logic uses **`request.datasourceId`** / **`queryEntity.datasourceId`**. Validates `datasourceRegistry.isValid(request.datasourceId)` before execute and stream; on invalid id throws `DATASOURCE_NOT_FOUND`. Cache and queue calls use `datasourceId`. `saveQuery` builds `QueryEntity` with `datasourceId`; `executeSavedQuery` builds `QueryRequestDTO` with `queryEntity.datasourceId`.

### Modified – controller
- **`QueryController.kt`** – Stream endpoint: query param **`databaseType`** replaced with **`datasourceId`** (String). Builds `QueryRequestDTO` with `datasourceId` from request or from saved query. Error handling: **`DATASOURCE_NOT_FOUND`** and **`LIKE_DOUBLE_WILDCARD_NOT_ALLOWED`** mapped to `BAD_REQUEST`.

### Modified – errors
- **`ErrorCodes.kt`** – Added **`DATASOURCE_NOT_FOUND = "QRS-ERR-017"`**.
- **`ErrorCodeRegistry.kt`** – Message for `DATASOURCE_NOT_FOUND`.
- **`GlobalExceptionHandler.kt`** – `DATASOURCE_NOT_FOUND` → `HttpStatus.BAD_REQUEST`.

### Unchanged
- **`ParameterResolver.kt`** – Still takes **`DatabaseType`** (for dialect). Callers obtain it via **`DatasourceRegistry.getDatabaseType(datasourceId)`**.
- **`DatabaseType`** enum – Still used internally for Oracle vs MSSQL dialect only.

---

## API usage

- **Execute query:** Request body must include **`datasourceId`** (e.g. `"oracle-primary"`, `"mssql-primary"`) instead of `databaseType`.
- **Stream query:** Use query param **`datasourceId`** when passing sql/datasource; for saved-query stream, the saved query’s **`datasourceId`** is used.
- **Save query:** Request body must include **`datasourceId`** instead of `databaseType`.

Valid `datasourceId` values are those defined under `query-service.datasources` in `application.properties`.

---

## Database migration (existing data)

If the `queries` table already has a **`database_type`** column (e.g. `ORACLE` / `MSSQL`):

1. Add column: `ALTER TABLE queries ADD COLUMN datasource_id VARCHAR(255);`
2. Backfill, e.g.:  
   `UPDATE queries SET datasource_id = 'oracle-primary' WHERE database_type = 'ORACLE';`  
   `UPDATE queries SET datasource_id = 'mssql-primary' WHERE database_type = 'MSSQL';`
3. Drop old column: `ALTER TABLE queries DROP COLUMN database_type;`  
   (Adjust for your DB and naming conventions.)

With **JPA `ddl-auto=update`**, Hibernate will add the new `datasource_id` column; it may not remove `database_type`. Run the above (or a Flyway script) if you want a clean schema.

---

## Summary table

| Area            | Change summary |
|-----------------|----------------|
| Config          | YAML → properties; datasources as list with `id` + `vendor` per entry. |
| Pool            | ConnectionPoolManager uses `Map<String, HikariDataSource>` and `datasourceId`. |
| Registry        | New DatasourceRegistry: valid ids, id → vendor, id → DatabaseType (dialect). |
| API / DTOs      | `databaseType` → `datasourceId: String` everywhere. |
| Cache / queue   | Keys and payloads use `datasourceId`. |
| Executors       | All take `datasourceId`; get connection by id and dialect from registry. |
| Errors          | New `DATASOURCE_NOT_FOUND` (400) for unknown datasource id. |
