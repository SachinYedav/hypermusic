package com.hypermusic.core.utils

import coil.intercept.Interceptor
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException

/** Keeps fallback bytes under their real URL, with bounded, temporary hints for missing variants. */
class ArtworkFallbackInterceptor(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : Interceptor {
    private val missing = LinkedHashMap<String, Long>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String ?: return chain.proceed(chain.request)
        ArtworkCacheHelper.findCached(chain, listOf(url) + ArtworkUrlResolver.higherQualityUrls(url))
            ?.let { return it }

        val candidates = listOf(url) + ArtworkUrlResolver.fallbackUrls(url)
        var lastResult: ImageResult? = null
        for ((index, candidate) in candidates.withIndex()) {
            if (index < candidates.lastIndex && isMissing(candidate)) continue
            val result = chain.proceed(ArtworkCacheHelper.variantRequest(chain.request, candidate))
            if (result is SuccessResult) {
                synchronized(missing) { missing.remove(candidate) }
                return result
            }
            lastResult = result
            val error = (result as? ErrorResult)?.throwable
            if (error is CancellationException) throw error
            if ((error as? HttpException)?.response?.code == 404 && candidates.size > 1) {
                synchronized(missing) {
                    missing[candidate] = clockMs()
                    if (missing.size > 256) missing.remove(missing.keys.first())
                }
            } else {
                // An offline/stale-cache failure may still have a usable lower variant on disk.
                return ArtworkCacheHelper.findCached(chain, candidates.drop(index + 1), includeDisk = true)
                    ?: result
            }
        }
        return checkNotNull(lastResult)
    }

    private fun isMissing(url: String): Boolean = synchronized(missing) {
        val since = missing[url] ?: return@synchronized false
        if (clockMs() - since < 5 * 60_000L) true else {
            missing.remove(url)
            false
        }
    }
}
