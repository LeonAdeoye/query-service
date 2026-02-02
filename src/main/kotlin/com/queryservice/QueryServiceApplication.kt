package com.queryservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableCaching
@EnableAsync
class QueryServiceApplication

fun main(args: Array<String>) {
    runApplication<QueryServiceApplication>(*args)
}

