package com.hypermusic.core.extractor.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** NewPipe's pinned getInfo path is synchronous; no coroutine thread-local inheritance is assumed. */
class ExtractionRequest {
    private val deadline = System.nanoTime() + 15_000_000_000L
    private val calls = mutableSetOf<Call>()
    private var cancelled = false
    @Synchronized fun register(call: Call) {
        val remaining = deadline - System.nanoTime()
        if (cancelled || remaining <= 0) throw InterruptedIOException("Extraction deadline reached")
        call.timeout().timeout(remaining, java.util.concurrent.TimeUnit.NANOSECONDS)
        calls += call
    }
    @Synchronized fun unregister(call: Call) { calls -= call }
    @Synchronized fun cancel() { cancelled = true; calls.forEach { it.cancel() }; calls.clear() }

    companion object {
        private val current = ThreadLocal<ExtractionRequest>()
        private val workers = Executors.newFixedThreadPool(4) { task ->
            Thread(task, "stream-extractor").apply { isDaemon = true }
        }
        fun active(): ExtractionRequest? = current.get()
        suspend fun <T> execute(block: () -> T): T = suspendCancellableCoroutine { continuation ->
            val request = ExtractionRequest()
            val future = workers.submit {
                current.set(request)
                try {
                    val result = block()
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } finally { current.remove(); request.cancel() }
            }
            continuation.invokeOnCancellation { request.cancel(); future.cancel(true) }
        }
    }
}
