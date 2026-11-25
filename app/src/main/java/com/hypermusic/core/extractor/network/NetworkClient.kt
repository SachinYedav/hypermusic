package com.hypermusic.core.extractor.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import com.hypermusic.core.extractor.errors.NetworkException
import java.util.concurrent.TimeUnit
import java.io.IOException

/** Shared OkHttp client for direct InnerTube requests. */
object NetworkClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HeadersInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Checks status and wraps execution IO failures; the caller reads and closes successful responses. */
    @Throws(NetworkException::class)
    fun executeSync(request: Request): Response {
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                val message = response.message
                response.close()
                throw NetworkException(
                    message = "HTTP Error: $code - $message",
                    statusCode = code
                )
            }
            return response
        } catch (e: IOException) {
            throw NetworkException("Network error: Failed to execute request to ${request.url}", cause = e)
        }
    }

    /** Coroutine-cancellable request execution. Automatically cancels underlying Call when cancelled. */
    @Throws(NetworkException::class)
    suspend fun executeAsync(request: Request): Response = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation {
            try {
                call.cancel()
            } catch (_: Throwable) {}
        }
        call.enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    val code = response.code
                    val message = response.message
                    response.close()
                    continuation.resumeWith(
                        Result.failure(
                            NetworkException(
                                message = "HTTP Error: $code - $message",
                                statusCode = code
                            )
                        )
                    )
                } else {
                    continuation.resumeWith(Result.success(response))
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWith(
                    Result.failure(
                        NetworkException(
                            "Network error: Failed to execute request to ${request.url}",
                            cause = e
                        )
                    )
                )
            }
        })
    }
}
