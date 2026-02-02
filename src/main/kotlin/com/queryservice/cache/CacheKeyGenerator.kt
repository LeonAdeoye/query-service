package com.queryservice.cache

import com.queryservice.database.DatabaseType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class CacheKeyGenerator(
    private val objectMapper: ObjectMapper
) {
    
    fun generateKey(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?
    ): String {
        val keyData = mapOf(
            "sql" to sql,
            "databaseType" to databaseType.name,
            "parameters" to (parameters ?: emptyMap())
        )
        
        val json = objectMapper.writeValueAsString(keyData)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(json.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

