package com.queryservice.database

import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * Registry of configured datasource ids and their vendor (oracle/mssql).
 * Used to resolve dialect for parameter binding and to validate datasource id.
 */
@Component
class DatasourceRegistry(
    private val datasourceConfigProperties: DatasourceConfigProperties
) {
    private lateinit var idToVendor: Map<String, String>
    private lateinit var validIds: Set<String>

    @PostConstruct
    fun init() {
        val idToVendorMut = mutableMapOf<String, String>()
        val ids = mutableSetOf<String>()
        for (entry in datasourceConfigProperties.datasources) {
            if (entry.id.isBlank()) continue
            ids.add(entry.id)
            idToVendorMut[entry.id] = entry.vendor.lowercase()
        }
        idToVendor = idToVendorMut
        validIds = ids
    }

    fun getVendor(datasourceId: String): String {
        return idToVendor[datasourceId]
            ?: throw QueryServiceException(
                ErrorCodes.DATASOURCE_NOT_FOUND,
                "Unknown datasource id: $datasourceId. Valid ids: ${validIds.sorted().joinToString()}"
            )
    }

    fun getDatabaseType(datasourceId: String): DatabaseType {
        return when (getVendor(datasourceId)) {
            "oracle" -> DatabaseType.ORACLE
            "mssql" -> DatabaseType.MSSQL
            else -> throw QueryServiceException(
                ErrorCodes.DATASOURCE_NOT_FOUND,
                "Unsupported vendor for datasource $datasourceId"
            )
        }
    }

    fun isValid(datasourceId: String): Boolean = validIds.contains(datasourceId)

    fun getValidIds(): Set<String> = validIds.toSet()
}
