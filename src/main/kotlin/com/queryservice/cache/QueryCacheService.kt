package com.queryservice.cache

import com.queryservice.database.DatabaseType
import com.queryservice.error.ErrorCodes
import com.queryservice.error.ErrorCodeRegistry
import com.queryservice.error.QueryServiceException
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

@Service
class QueryCacheService(
    private val cacheManager: CacheManager,
    private val cacheKeyGenerator: CacheKeyGenerator,
    private val errorCodeRegistry: ErrorCodeRegistry
) {
    private val logger = LoggerFactory.getLogger(QueryCacheService::class.java)
    
    fun get(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?
    ): List<Map<String, Any>>? {
        return try {
            val cache: Cache? = cacheManager.getCache("queryResults")
            val key = cacheKeyGenerator.generateKey(sql, databaseType, parameters)
            val cached = cache?.get(key)
            if (cached != null) {
                logger.debug("Cache hit for key: $key")
                @Suppress("UNCHECKED_CAST")
                cached.get() as? List<Map<String, Any>>
            } else {
                logger.debug("Cache miss for key: $key")
                null
            }
        } catch (e: Exception) {
            errorCodeRegistry.logError(ErrorCodes.CACHE_ERROR, "Failed to get from cache", e)
            null
        }
    }
    
    fun put(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?,
        result: List<Map<String, Any>>,
        ttlSeconds: Long? = null
    ) {
        try {
            val cache: Cache? = cacheManager.getCache("queryResults")
            val key = cacheKeyGenerator.generateKey(sql, databaseType, parameters)
            cache?.put(key, result)
            logger.debug("Cached result for key: $key")
        } catch (e: Exception) {
            errorCodeRegistry.logError(ErrorCodes.CACHE_ERROR, "Failed to put in cache", e)
            // Don't throw, caching is optional
        }
    }
    
    fun evict(
        sql: String,
        databaseType: DatabaseType,
        parameters: Map<String, Any>?
    ) {
        try {
            val cache: Cache? = cacheManager.getCache("queryResults")
            val key = cacheKeyGenerator.generateKey(sql, databaseType, parameters)
            cache?.evict(key)
            logger.debug("Evicted cache for key: $key")
        } catch (e: Exception) {
            errorCodeRegistry.logError(ErrorCodes.CACHE_ERROR, "Failed to evict from cache", e)
        }
    }
}

