package com.hypermusic.core.domain

/** Provider-selected representation. The signed URL is private transport data, never telemetry. */
data class StreamDescriptor(val url: String, val formatId: String? = null,
    val mimeType: String? = null, val suffix: String? = null,
    val bitrate: Int? = null, val hasVideo: Boolean = false)
