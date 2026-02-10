package com.queryservice.export

import com.fasterxml.jackson.databind.ObjectMapper
import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class FileExporter(
    private val objectMapper: ObjectMapper,
    private val errorCodeRegistry: ErrorCodeRegistry,
    @Value("\${query-service.export.temp-directory:}") private val tempDirectory: String
) {
    private val logger = LoggerFactory.getLogger(FileExporter::class.java)
    
    init {
        val exportDir = if (tempDirectory.isNotEmpty()) {
            Paths.get(tempDirectory)
        } else {
            Paths.get(System.getProperty("java.io.tmpdir"), "query-service-exports")
        }
        Files.createDirectories(exportDir)
        logger.info("Export directory: $exportDir")
    }
    
    /**
     * Non-blocking export: returns Mono that completes with the file path when done.
     * Runs on boundedElastic to avoid blocking the reactive thread pool.
     */
    fun exportToFile(
        dataFlux: Flux<Map<String, Any>>,
        format: ExportFormat
    ): reactor.core.publisher.Mono<String> {
        return dataFlux.collectList()
            .flatMap { data ->
                reactor.core.publisher.Mono.fromCallable {
                    val fileId = UUID.randomUUID().toString()
                    val fileName = when (format) {
                        ExportFormat.CSV -> "query_${fileId}.csv"
                        ExportFormat.JSON -> "query_${fileId}.json"
                        ExportFormat.EXCEL -> "query_${fileId}.xlsx"
                    }
                    val exportDir = if (tempDirectory.isNotEmpty()) {
                        Paths.get(tempDirectory)
                    } else {
                        Paths.get(System.getProperty("java.io.tmpdir"), "query-service-exports")
                    }
                    val filePath = exportDir.resolve(fileName)
                    when (format) {
                        ExportFormat.CSV -> exportToCsv(data, filePath.toFile())
                        ExportFormat.JSON -> exportToJson(data, filePath.toFile())
                        ExportFormat.EXCEL -> exportToExcel(data, filePath.toFile())
                    }
                    logger.info("Exported ${data.size} rows to $filePath")
                    filePath.toString()
                }.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .onErrorMap { e ->
                        errorCodeRegistry.logError(ErrorCodes.FILE_EXPORT_ERROR, "Failed to export file", e)
                        QueryServiceException(
                            ErrorCodes.FILE_EXPORT_ERROR,
                            "Failed to export file: ${e.message}",
                            e
                        )
                    }
            }
    }
    
    private fun exportToCsv(data: List<Map<String, Any>>, file: File) {
        if (data.isEmpty()) {
            FileWriter(file).use { it.write("") }
            return
        }
        
        FileWriter(file).use { writer ->
            val headers = data[0].keys.toList()
            CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(*headers.toTypedArray())).use { printer ->
                data.forEach { row ->
                    printer.printRecord(headers.map { row[it] ?: "" })
                }
            }
        }
    }
    
    private fun exportToJson(data: List<Map<String, Any>>, file: File) {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data)
    }
    
    private fun exportToExcel(data: List<Map<String, Any>>, file: File) {
        if (data.isEmpty()) {
            XSSFWorkbook().use { workbook ->
                workbook.createSheet("Query Results")
                workbook.write(file.outputStream())
            }
            return
        }
        
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Query Results")
            val headers = data[0].keys.toList()
            
            // Create header row
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).setCellValue(header)
            }
            
            // Create data rows
            data.forEachIndexed { rowIndex, row ->
                val dataRow = sheet.createRow(rowIndex + 1)
                headers.forEachIndexed { colIndex, header ->
                    val value = row[header]
                    when (value) {
                        is Number -> dataRow.createCell(colIndex).setCellValue(value.toDouble())
                        is Boolean -> dataRow.createCell(colIndex).setCellValue(value)
                        else -> dataRow.createCell(colIndex).setCellValue(value?.toString() ?: "")
                    }
                }
            }
            
            workbook.write(file.outputStream())
        }
    }
}

