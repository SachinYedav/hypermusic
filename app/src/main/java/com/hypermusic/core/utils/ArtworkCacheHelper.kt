package com.hypermusic.core.utils

import coil.Coil
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.ErrorResult
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException

/** Cache probes go through Coil's size/configuration checks and never access the network. */
object ArtworkCacheHelper {
    suspend fun findCached(
        chain: Interceptor.Chain,
        urls: List<String>,
        includeDisk: Boolean = false
    ): SuccessResult? {
        val request = chain.request
        val memoryCache = Coil.imageLoader(request.context).memoryCache
        for (url in urls.distinct()) {
            val key = if (url == request.data) request.memoryCacheKey ?: MemoryCache.Key(url)
                else MemoryCache.Key(url)
            if (!includeDisk && (!request.memoryCachePolicy.readEnabled || memoryCache?.get(key) == null)) continue
            val probe = variantRequest(request, url).newBuilder()
                .memoryCachePolicy(if (request.memoryCachePolicy.readEnabled) CachePolicy.READ_ONLY else CachePolicy.DISABLED)
                .diskCachePolicy(if (includeDisk && request.diskCachePolicy.readEnabled) CachePolicy.READ_ONLY else CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
            val result = chain.proceed(probe)
            if (result is SuccessResult) return result
            val error = (result as? ErrorResult)?.throwable
            if (error is CancellationException) throw error
        }
        return null
    }

    fun variantRequest(request: ImageRequest, url: String): ImageRequest =
        if (url == request.data) request else request.newBuilder()
            .data(url)
            .memoryCacheKey(null as String?)
            .diskCacheKey(null)
            .build()
}
