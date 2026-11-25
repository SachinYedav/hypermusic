package com.hypermusic.core.utils

import com.hypermusic.core.extractor.errors.NetworkException
import com.hypermusic.core.extractor.errors.ParsingException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class FriendlyError(
    val title: String,
    val description: String,
    val canRetry: Boolean = true
)

object ErrorClassifier {

    /**
     * Inspects a [Throwable] and its causal chain to classify it into a clean, human-readable [FriendlyError].
     */
    fun classify(throwable: Throwable?): FriendlyError {
        if (throwable == null) {
            return FriendlyError(
                title = "Something went wrong",
                description = "We couldn't load this content. Please try again."
            )
        }

        // 1. Inspect causal exception chain for concrete types
        val chain = generateSequence(throwable) { it.cause }
        for (t in chain) {
            when (t) {
                is UnknownHostException,
                is ConnectException,
                is NoRouteToHostException -> {
                    return FriendlyError(
                        title = "You're offline",
                        description = "Check your internet connection and try again."
                    )
                }
                is SocketTimeoutException -> {
                    return FriendlyError(
                        title = "Connection timed out",
                        description = "The server took too long to respond. Tap to retry."
                    )
                }
                is SSLException -> {
                    return FriendlyError(
                        title = "Secure connection failed",
                        description = "A secure connection could not be established."
                    )
                }
                is NetworkException -> {
                    when (t.statusCode) {
                        404 -> return FriendlyError(
                            title = "Content unavailable",
                            description = "This content is no longer available or was removed."
                        )
                        403 -> return FriendlyError(
                            title = "Access restricted",
                            description = "This stream is currently restricted in your region."
                        )
                    }
                    val cause = t.cause
                    if (cause is UnknownHostException || cause is ConnectException || cause is NoRouteToHostException) {
                        return FriendlyError(
                            title = "You're offline",
                            description = "Check your internet connection and try again."
                        )
                    }
                    if (cause is SocketTimeoutException) {
                        return FriendlyError(
                            title = "Connection timed out",
                            description = "The server took too long to respond. Tap to retry."
                        )
                    }
                    if (cause is SSLException) {
                        return FriendlyError(
                            title = "Secure connection failed",
                            description = "A secure connection could not be established."
                        )
                    }
                    return FriendlyError(
                        title = "You're offline",
                        description = "Check your internet connection and try again."
                    )
                }
                is ParsingException -> {
                    return FriendlyError(
                        title = "Response error",
                        description = "Received an unexpected response. Please try again."
                    )
                }
            }
        }

        // 2. Fall back to inspecting message strings across the causal chain
        for (t in chain) {
            val msg = t.message
            if (!msg.isNullOrBlank()) {
                val candidate = classify(msg)
                if (candidate.title != "Something went wrong") {
                    return candidate
                }
            }
        }

        return classify(throwable.message)
    }

    /**
     * Fallback classifier parsing raw error message strings.
     */
    fun classify(rawMessage: String?): FriendlyError {
        if (rawMessage.isNullOrBlank()) {
            return FriendlyError(
                title = "Something went wrong",
                description = "We couldn't load this content. Please try again."
            )
        }

        val msg = rawMessage.lowercase()
        return when {
            msg.contains("unknownhost") ||
            msg.contains("unable to resolve host") ||
            msg.contains("no address associated") ||
            msg.contains("network") ||
            msg.contains("offline") ||
            msg.contains("connectexception") ||
            msg.contains("failed to connect") ||
            msg.contains("unreachable") -> {
                FriendlyError(
                    title = "You're offline",
                    description = "Check your internet connection and try again."
                )
            }
            msg.contains("timeout") || msg.contains("timed out") -> {
                FriendlyError(
                    title = "Connection timed out",
                    description = "The server took too long to respond. Tap to retry."
                )
            }
            msg.contains("ssl") || msg.contains("handshake") || msg.contains("cert") -> {
                FriendlyError(
                    title = "Secure connection failed",
                    description = "A secure connection could not be established."
                )
            }
            msg.contains("404") || msg.contains("not found") -> {
                FriendlyError(
                    title = "Content unavailable",
                    description = "This content is no longer available or was removed."
                )
            }
            msg.contains("403") || msg.contains("forbidden") -> {
                FriendlyError(
                    title = "Access restricted",
                    description = "This stream is currently restricted in your region."
                )
            }
            msg.contains("format") || msg.contains("json") || msg.contains("parse") -> {
                FriendlyError(
                    title = "Response error",
                    description = "Received an unexpected response. Please try again."
                )
            }
            else -> {
                FriendlyError(
                    title = "Something went wrong",
                    description = "An unexpected error occurred. Tap below to retry."
                )
            }
        }
    }
}
