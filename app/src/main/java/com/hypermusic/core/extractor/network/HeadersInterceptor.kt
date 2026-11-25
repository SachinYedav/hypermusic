package com.hypermusic.core.extractor.network

import okhttp3.Interceptor
import okhttp3.Response

/** Sets fixed web-client headers on requests made through NetworkClient. */
class HeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        
        val requestBuilder = original.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("Content-Type", "application/json")
            
        return chain.proceed(requestBuilder.build())
    }
}

