package com.queryservice.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConfigurationProperties(prefix = "query-service.cache")
data class CacheConfigProperties(
    val enabled: Boolean = true,
    val defaultTtlSeconds: Long = 3600,
    val maxSize: Long = 1000
)

@Configuration
@EnableCaching
class CacheConfig(
    private val cacheConfigProperties: CacheConfigProperties
) {
    
    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager("queryResults")
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(cacheConfigProperties.maxSize)
                .expireAfterWrite(cacheConfigProperties.defaultTtlSeconds, TimeUnit.SECONDS)
                .recordStats()
        )
        return cacheManager
    }
}

