package com.queryservice.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class QueryRequestDTO(
    @field:NotBlank(message = "SQL query is required")
    val sql: String,
    
    @field:NotBlank(message = "Datasource id is required")
    val datasourceId: String,
    
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

