package com.queryservice.execution

import com.queryservice.export.ExportFormat
import com.queryservice.export.FileExporter
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class BigDataQueryExecutor(
    private val streamingQueryExecutor: StreamingQueryExecutor,
    private val fileExporter: FileExporter
) {

    fun executeBigDataQuery(
        sql: String,
        datasourceId: String,
        parameters: Map<String, Any>?,
        exportFormat: ExportFormat
    ): Mono<String> {
        val flux = streamingQueryExecutor.streamQuery(sql, datasourceId, parameters, 1000)
        return fileExporter.exportToFile(flux, exportFormat)
    }
}

