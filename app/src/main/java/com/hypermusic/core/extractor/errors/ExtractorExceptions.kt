package com.hypermusic.core.extractor.errors

/** Typed failures exposed by network, parser and engine operations. */
sealed class ExtractorException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** NetworkClient status-check or request-execution IO failure; not used by every transport. */
class NetworkException(
    message: String, 
    val statusCode: Int? = null,
    cause: Throwable? = null
) : ExtractorException(message, cause)

/** Response parsing failure that is propagated rather than skipped or returned as empty. */
class ParsingException(
    message: String,
    val jsonPath: String? = null,
    cause: Throwable? = null
) : ExtractorException(message, cause)

/** Engine orchestration or stream-resolution failure. */
class EngineException(
    message: String,
    cause: Throwable? = null
) : ExtractorException(message, cause)

