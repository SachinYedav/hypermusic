package com.hypermusic.core.extractor.engine

import org.json.JSONObject

object PayloadBuilder {

    /** Builds the web-client context used by the direct InnerTube request builders. */
    fun buildContext(hl: String = "en", gl: String = "IN"): JSONObject {
        val context = JSONObject()
        val client = JSONObject()
        client.put("clientName", "WEB_REMIX")
        client.put("clientVersion", "1.20261117.06.00")
        client.put("hl", hl)
        client.put("gl", if (gl == "ZZ") "US" else gl)

        context.put("client", client)
        return context
    }

    fun buildSearchPayload(query: String, hl: String = "en", gl: String = "IN"): String {
        val payload = JSONObject()
        payload.put("context", buildContext(hl, gl))
        payload.put("query", query)
        return payload.toString()
    }

    fun buildSearchSuggestionsPayload(query: String, hl: String = "en", gl: String = "IN"): String {
        val payload = JSONObject()
        payload.put("context", buildContext(hl, gl))
        payload.put("input", query)
        return payload.toString()
    }

    /** Accepts browseId|||params to carry navigation parameters in a combined ID. */
    fun buildBrowsePayload(
        browseId: String,
        params: String? = null,
        hl: String = "en",
        gl: String = "IN",
        formDataValues: List<String>? = null
    ): String {
        val effectiveGl = if (gl == "ZZ") "US" else gl
        val payload = JSONObject()
        payload.put("context", buildContext(hl, effectiveGl))
        if (browseId.contains("|||")) {
            val parts = browseId.split("|||")
            payload.put("browseId", parts[0])
            if (parts.size > 1 && parts[1].isNotEmpty()) {
                payload.put("params", parts[1])
            }
        } else {
            payload.put("browseId", browseId)
            if (params != null) {
                payload.put("params", params)
            }
        }
        if (!formDataValues.isNullOrEmpty()) {
            val formData = JSONObject()
            val selectedArray = org.json.JSONArray()
            for (value in formDataValues) {
                selectedArray.put(value)
            }
            formData.put("selectedValues", selectedArray)
            payload.put("formData", formData)
        }
        return payload.toString()
    }

    /** Builds context/visitorData; fetchContinuation places the continuation token in the URL. */
    fun buildContinuationPayload(continuationToken: String, visitorData: String? = null, hl: String = "en", gl: String = "IN"): String {
        val payload = JSONObject()
        val context = buildContext(hl, gl)
        if (!visitorData.isNullOrEmpty()) {
            context.optJSONObject("client")?.put("visitorData", visitorData)
        }
        payload.put("context", context)
        return payload.toString()
    }

    fun buildRadioPayload(videoId: String, hl: String = "en", gl: String = "IN"): String {
        val payload = JSONObject()
        payload.put("context", buildContext(hl, gl))
        payload.put("videoId", videoId)
        payload.put("playlistId", "RDAMVM$videoId")
        return payload.toString()
    }
}

