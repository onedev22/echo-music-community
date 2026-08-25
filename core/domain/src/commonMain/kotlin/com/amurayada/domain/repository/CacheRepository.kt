package com.amurayada.domain.repository

interface CacheRepository {
    suspend fun getCacheSize(cacheName: String): Long

    fun clearCache(cacheName: String)

    fun removeResource(cacheName: String, key: String)

    suspend fun getAllCacheKeys(cacheName: String): List<String>
}