package com.hypermusic.core.utils

import android.util.Log
import com.hypermusic.BuildConfig

/** Logging wrapper: d/i/w/e are debug-only; fatal also logs in release builds. */
object Logger {
    private val IS_DEBUG = BuildConfig.DEBUG
    private const val DEFAULT_TAG = "HyperMusic"

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (IS_DEBUG) Log.d(tag, message)
    }

    fun d(message: String) {
        if (IS_DEBUG) Log.d(DEFAULT_TAG, message)
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (IS_DEBUG) Log.i(tag, message)
    }

    fun i(message: String) {
        if (IS_DEBUG) Log.i(DEFAULT_TAG, message)
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (IS_DEBUG) Log.w(tag, message, throwable)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (IS_DEBUG) Log.w(DEFAULT_TAG, message, throwable)
    }

    /** Debug-only error logging (e.g. expected OEM DSP / audio effect failures) */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (IS_DEBUG) Log.e(tag, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (IS_DEBUG) Log.e(DEFAULT_TAG, message, throwable)
    }

    /** Critical production error logging (e.g. DB transaction or disk I/O commit failures) */
    fun fatal(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
