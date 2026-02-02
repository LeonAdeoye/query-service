package com.queryservice.execution

import com.queryservice.database.DatabaseType
import com.queryservice.export.FileExporter
import com.queryservice.export.ExportFormat
import org.springframework.stereotype.Component

@Component
class BigDataQueryExecutor(
    private val streamingQueryExecutor: StreamingQueryExecutor,
    private val fileExporter: FileExporter
) {
    
    suspend fun executeBigDataQuery(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        exportFormat: ExportFormat
    ): String {
        val flux = streamingQueryExecutor.streamQuery(sql, databaseType, parameters, 1000)
        return fileExporter.exportToFile(flux, exportFormat)
    }
}

