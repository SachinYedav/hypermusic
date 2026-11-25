package com.hypermusic.core.extractor.engine

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

internal object StreamSelector {
    fun audio(streams: List<AudioStream?>, quality: String): AudioStream? {
        val available = streams.filterNotNull().filter { !it.content.isNullOrBlank() }
        val rated = available.filter { it.averageBitrate > 0 }
        val selected = when (quality) {
            "data_saver" -> rated.minByOrNull { it.averageBitrate }
            "high" -> rated.maxByOrNull { it.averageBitrate }
            else -> {
                val standard = rated.filter { it.format in standardFormats }.ifEmpty { rated }
                standard.minWithOrNull(compareBy<AudioStream> { abs(it.averageBitrate.toLong() - 128) }
                    .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                    .thenBy { it.averageBitrate })
            }
        }
        return selected ?: available.firstOrNull { it.format in standardFormats } ?: available.firstOrNull()
    }

    /** Never selects a video-only stream; callers supply NewPipe's progressive stream list. */
    fun progressiveVideo(streams: List<VideoStream?>): VideoStream? {
        val available = streams.filterNotNull().filter { !it.isVideoOnly() && !it.content.isNullOrBlank() }
        return available.firstOrNull { it.format == MediaFormat.MPEG_4 && it.getResolution() in setOf("360p", "480p") }
            ?: available.firstOrNull { it.format == MediaFormat.MPEG_4 }
            ?: available.firstOrNull()
    }

    private val standardFormats = setOf(MediaFormat.M4A, MediaFormat.OPUS, MediaFormat.WEBMA_OPUS)
}
