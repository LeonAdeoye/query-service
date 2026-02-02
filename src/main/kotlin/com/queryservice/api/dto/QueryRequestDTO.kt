package com.queryservice.api.dto

import com.queryservice.database.DatabaseType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class QueryRequestDTO(
    @field:NotBlank(message = "SQL query is required")
    val sql: String,
    
    @field:NotNull(message = "Database type is required")
    val databaseType: DatabaseType,
    
    val parameters: Map<String, Any>? = null,
    
    val queryId: String? = null,
    
    val cacheEnabled: Boolean = true,
    
    val cacheTtlSeconds: Long? = null,
    
    val priority: QueryPriority = QueryPriority.NORMAL,
    
    val bigData: Boolean = false,
    
    val exportFormat: ExportFormat? = null
)

enum class QueryPriority {
    HIGH, NORMAL, LOW
}

enum class ExportFormat {
    CSV, JSON, EXCEL
}

