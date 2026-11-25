package com.hypermusic.core.lyrics

import com.hypermusic.core.model.LyricLine
import com.hypermusic.core.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

fun interface LyricsTranslator {
    suspend fun translateLyrics(lyrics: Lyrics, targetLang: String): Lyrics?
}

class LyricsTranslationService : LyricsTranslator {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun translateLyrics(lyrics: Lyrics, targetLang: String): Lyrics? = withContext(Dispatchers.IO) {
        if (lyrics.text.isBlank()) return@withContext null

        try {
            if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
                val joinedSourceText = lyrics.lines.joinToString("\n") { it.text }
                val translatedText = executeTranslation(joinedSourceText, targetLang) ?: return@withContext null

                val translatedLines = translatedText.split("\n")
                val updatedLines = lyrics.lines.mapIndexed { index, line ->
                    val trans = translatedLines.getOrNull(index)?.trim()
                    line.copy(translatedText = if (!trans.isNullOrEmpty()) trans else null)
                }

                lyrics.copy(
                    lines = updatedLines,
                    translatedPlainText = translatedText,
                    translatedLang = targetLang
                )
            } else {
                val translatedText = executeTranslation(lyrics.text, targetLang) ?: return@withContext null
                lyrics.copy(
                    translatedPlainText = translatedText,
                    translatedLang = targetLang
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun executeTranslation(text: String, targetLang: String): String? {
        return try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t"
            val formBody = FormBody.Builder()
                .add("q", text)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HyperMusic/1.0 (Android; Linux)")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val rootArray = JSONArray(body)
                val sentencesArray = rootArray.optJSONArray(0) ?: return null

                val builder = StringBuilder()
                for (i in 0 until sentencesArray.length()) {
                    val segment = sentencesArray.optJSONArray(i) ?: continue
                    val textPart = segment.optString(0)
                    if (textPart.isNotEmpty()) {
                        builder.append(textPart)
                    }
                }
                builder.toString().ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }
}
