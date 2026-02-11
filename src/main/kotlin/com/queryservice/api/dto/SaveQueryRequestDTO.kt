package com.queryservice.api.dto

import jakarta.validation.constraints.NotBlank

data class SaveQueryRequestDTO(
    @field:NotBlank(message = "Query name is required")
    val name: String,
    
    @field:NotBlank(message = "SQL query is required")
    val sql: String,
    
    @field:NotBlank(message = "Datasource id is required")
    val datasourceId: String,
    
    val parametersSchema: Map<String, String>? = null
)

