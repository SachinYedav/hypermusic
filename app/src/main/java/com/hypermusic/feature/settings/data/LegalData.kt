package com.hypermusic.feature.settings.data

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LegalSection(
    val title: String,
    val paragraphs: List<String>,
    val webOnly: Boolean = false
)

@Serializable
data class LegalDocument(val title: String, val lastUpdated: String, val sections: List<LegalSection>)

@Serializable
data class LegalLink(val text: String, val url: String)

object LegalData {
    fun loadLinks(assets: AssetManager): Map<String, List<LegalLink>> = try {
        Json.decodeFromString(assets.open("references.json").bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        emptyMap()
    }
    @Serializable
    private data class Documents(
        val privacy: LegalDocument,
        val terms: LegalDocument,
        val copyright: LegalDocument
    )

    fun load(assets: AssetManager): List<LegalDocument> {
        val content = assets.open("content.json").bufferedReader().use { it.readText() }
        val documents = Json.decodeFromString<Documents>(content)
        return listOf(documents.privacy, documents.terms, documents.copyright)
    }
}
