package com.queryservice.query

import com.queryservice.database.DatabaseType
import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date

@Component
class ParameterResolver {
    
    fun resolveParameters(
        sql: String,
        parameters: Map<String, Any>?,
        databaseType: DatabaseType
    ): Pair<String, List<Any>> {
        if (parameters == null || parameters.isEmpty()) {
            return sql to emptyList()
        }
        
        return when (databaseType) {
            DatabaseType.ORACLE -> resolveOracleParameters(sql, parameters)
            DatabaseType.MSSQL -> resolveMsSqlParameters(sql, parameters)
        }
    }
    
    private fun resolveOracleParameters(sql: String, parameters: Map<String, Any>): Pair<String, List<Any>> {
        var resolvedSql = sql
        val paramValues = mutableListOf<Any>()
        var paramIndex = 1
        
        // Handle named parameters (:paramName)
        val namedParamPattern = Regex(":([a-zA-Z_][a-zA-Z0-9_]*)")
        val matches = namedParamPattern.findAll(sql)
        
        matches.forEach { match ->
            val paramName = match.groupValues[1]
            val value = parameters[paramName] 
                ?: throw QueryServiceException(
                    ErrorCodes.INVALID_PARAMETERS,
                    "Parameter not found: $paramName"
                )
            resolvedSql = resolvedSql.replace(match.value, "?")
            paramValues.add(convertValue(value))
        }
        
        // Handle positional parameters (?)
        val positionalCount = sql.count { it == '?' }
        if (positionalCount > 0 && paramValues.isEmpty()) {
            // If we have positional parameters but no named ones, use positional
            parameters.values.forEach { value ->
                paramValues.add(convertValue(value))
            }
            if (paramValues.size != positionalCount) {
                throw QueryServiceException(
                    ErrorCodes.INVALID_PARAMETERS,
                    "Parameter count mismatch: expected $positionalCount, got ${paramValues.size}"
                )
            }
        }
        
        return resolvedSql to paramValues
    }
    
    private fun resolveMsSqlParameters(sql: String, parameters: Map<String, Any>): Pair<String, List<Any>> {
        // MS SQL also supports named parameters, but we'll convert to positional
        return resolveOracleParameters(sql, parameters)
    }
    
    fun setParameter(statement: PreparedStatement, index: Int, value: Any) {
        when (value) {
            is String -> statement.setString(index, value)
            is Int -> statement.setInt(index, value)
            is Long -> statement.setLong(index, value)
            is Double -> statement.setDouble(index, value)
            is Float -> statement.setFloat(index, value)
            is Boolean -> statement.setBoolean(index, value)
            is Date -> statement.setTimestamp(index, java.sql.Timestamp(value.time))
            is LocalDate -> statement.setDate(index, java.sql.Date.valueOf(value))
            is LocalDateTime -> statement.setTimestamp(index, java.sql.Timestamp.valueOf(value))
            is java.time.Instant -> statement.setTimestamp(index, java.sql.Timestamp.from(value))
            else -> statement.setObject(index, value)
        }
    }
    
    private fun convertValue(value: Any): Any {
        return when (value) {
            is Date -> java.sql.Timestamp(value.time)
            is LocalDate -> java.sql.Date.valueOf(value)
            is LocalDateTime -> java.sql.Timestamp.valueOf(value)
            is java.time.Instant -> java.sql.Timestamp.from(value)
            else -> value
        }
    }
}

