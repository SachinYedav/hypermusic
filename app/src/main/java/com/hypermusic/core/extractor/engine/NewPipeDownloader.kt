package com.hypermusic.core.extractor.engine

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OkHttp adapter for NewPipe requests, with active extraction cancellation/deadline tracking. */
class NewPipeDownloader : Downloader() {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        val instance by lazy { NewPipeDownloader() }
    }

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder()
            .method(method, dataToSend?.toRequestBody())
            .url(url)

        headers?.forEach { (key, values) ->
            values?.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        val okHttpRequest = builder.build()
        val call = client.newCall(okHttpRequest)
        val extraction = ExtractionRequest.active()
        extraction?.register(call)
        try {
        call.execute().use { okHttpResponse ->

        val responseHeaders = mutableMapOf<String, List<String>>()
        okHttpResponse.headers.names().forEach { name ->
            responseHeaders[name] = okHttpResponse.headers.values(name)
        }

        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            responseHeaders,
            okHttpResponse.body?.string() ?: "",
            okHttpResponse.request.url.toString()
        )
        }
        } finally { extraction?.unregister(call) }
    }
}
