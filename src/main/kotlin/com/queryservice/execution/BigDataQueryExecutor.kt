package com.queryservice.execution

import com.queryservice.database.DatabaseType
import com.queryservice.export.FileExporter
import com.queryservice.export.ExportFormat
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class BigDataQueryExecutor(
    private val streamingQueryExecutor: StreamingQueryExecutor,
    private val fileExporter: FileExporter
) {

    fun executeBigDataQuery(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        exportFormat: ExportFormat
    ): Mono<String> {
        val flux = streamingQueryExecutor.streamQuery(sql, databaseType, parameters, 1000)
        return fileExporter.exportToFile(flux, exportFormat)
    }
}

