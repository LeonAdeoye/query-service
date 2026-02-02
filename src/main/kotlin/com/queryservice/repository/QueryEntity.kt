package com.queryservice.repository

import com.queryservice.database.DatabaseType
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "queries")
data class QueryEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),
    
    val name: String? = null,
    
    @Column(columnDefinition = "TEXT")
    val sql: String,
    
    @Enumerated(EnumType.STRING)
    val databaseType: DatabaseType,
    
    @Column(columnDefinition = "TEXT")
    val parametersSchema: String? = null,
    
    val createdBy: String? = null,
    
    val createdAt: Instant = Instant.now(),
    
    val updatedAt: Instant = Instant.now()
)

