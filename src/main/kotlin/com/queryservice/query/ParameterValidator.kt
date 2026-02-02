package com.queryservice.query

import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import org.springframework.stereotype.Component

@Component
class ParameterValidator {
    
    fun validateParameters(sql: String, parameters: Map<String, Any>?): Boolean {
        if (parameters == null || parameters.isEmpty()) {
            return true
        }
        
        // Check for SQL injection patterns
        val suspiciousPatterns = listOf(
            "';",
            "--",
            "/*",
            "*/",
            "xp_",
            "sp_",
            "exec(",
            "execute(",
            "union select",
            "drop table",
            "delete from",
            "truncate"
        )
        
        parameters.values.forEach { value ->
            val valueStr = value.toString().lowercase()
            suspiciousPatterns.forEach { pattern ->
                if (valueStr.contains(pattern)) {
                    throw QueryServiceException(
                        ErrorCodes.PARAMETER_VALIDATION_ERROR,
                        "Suspicious pattern detected in parameter: $pattern"
                    )
                }
            }
        }
        
        return true
    }
    
    fun validateParameterTypes(parameters: Map<String, Any>?): Boolean {
        if (parameters == null) {
            return true
        }
        
        parameters.values.forEach { value ->
            when (value) {
                is String, is Number, is Boolean, is java.util.Date, is java.time.temporal.Temporal -> {
                    // Valid types
                }
                else -> {
                    throw QueryServiceException(
                        ErrorCodes.PARAMETER_VALIDATION_ERROR,
                        "Unsupported parameter type: ${value::class.simpleName}"
                    )
                }
            }
        }
        
        return true
    }
}

