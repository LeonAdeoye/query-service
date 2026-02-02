package com.queryservice.api.dto

import com.queryservice.database.DatabaseType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class SaveQueryRequestDTO(
    @field:NotBlank(message = "Query name is required")
    val name: String,
    
    @field:NotBlank(message = "SQL query is required")
    val sql: String,
    
    @field:NotNull(message = "Database type is required")
    val databaseType: DatabaseType,
    
    val parametersSchema: Map<String, String>? = null
)

