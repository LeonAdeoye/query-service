package com.queryservice.error

class QueryServiceException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

