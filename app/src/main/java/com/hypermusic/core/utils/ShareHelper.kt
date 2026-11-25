package com.hypermusic.core.utils

import android.content.Context
import android.content.Intent

enum class ShareType {
    TRACK, PLAYLIST, ARTIST, ALBUM, PODCAST, PODCAST_EPISODE
}

object ShareHelper {
    fun shareContent(context: Context, type: ShareType, id: String, name: String) {
        val url = when (type) {
            ShareType.TRACK -> "https://music.youtube.com/watch?v=$id"
            ShareType.PLAYLIST -> "https://music.youtube.com/playlist?list=$id"
            ShareType.ARTIST -> "https://music.youtube.com/channel/$id"
            ShareType.ALBUM -> "https://music.youtube.com/playlist?list=$id"
            ShareType.PODCAST -> "https://music.youtube.com/podcast/$id"
            ShareType.PODCAST_EPISODE -> "https://music.youtube.com/watch?v=$id"
        }

        val message = "Listen to $name on YouTube Music: $url"

        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_TITLE, name)
            putExtra(Intent.EXTRA_SUBJECT, name)
        }

        val chooser = Intent.createChooser(intent, "Share $name")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
