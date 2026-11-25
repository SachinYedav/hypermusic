package com.hypermusic.navigation

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

fun NavController.navigateToArtist(artistId: String): Boolean {
    val currentEntry = currentBackStackEntry ?: run {
        navigate("artist/$artistId")
        return true
    }

    if (!currentEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        return false
    }

    val currentRoute = currentEntry.destination.route
    val currentId = currentEntry.arguments?.getString("id")

    if (currentRoute == "artist/{id}" && currentId == artistId) {
        return false
    }

    navigate("artist/$artistId")
    return true
}

fun NavController.navigateToCollection(
    type: String,
    id: String,
    title: String? = null,
    subtitle: String? = null,
    artworkUrl: String? = null
): Boolean {
    if (type == "artist") {
        return navigateToArtist(id)
    }

    val currentEntry = currentBackStackEntry ?: run {
        navigate(buildCollectionRoute(type, id, title, subtitle, artworkUrl))
        return true
    }

    if (!currentEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        return false
    }

    val currentRoute = currentEntry.destination.route
    val currentType = currentEntry.arguments?.getString("type")
    val currentId = currentEntry.arguments?.getString("id")

    if (currentRoute?.startsWith("collection/") == true && currentType == type && currentId == id) {
        return false
    }

    navigate(buildCollectionRoute(type, id, title, subtitle, artworkUrl))
    return true
}

fun NavController.navigateToCategory(categoryId: String, title: String): Boolean {
    val encodedId = Uri.encode(categoryId)
    val encodedTitle = Uri.encode(title)
    val currentEntry = currentBackStackEntry ?: run {
        navigate("explore/$encodedId/$encodedTitle")
        return true
    }

    if (!currentEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        return false
    }

    val currentRoute = currentEntry.destination.route
    val currentCategoryId = currentEntry.arguments?.getString("categoryId")

    if (currentRoute == "explore/{categoryId}/{title}" && (currentCategoryId == categoryId || currentCategoryId == encodedId)) {
        return false
    }

    navigate("explore/$encodedId/$encodedTitle")
    return true
}

fun NavController.navigateToCatalog(
    endpoint: String,
    title: String? = null,
    subtitle: String? = null
): Boolean {
    val currentEntry = currentBackStackEntry ?: run {
        navigate(buildCatalogRoute(endpoint, title, subtitle))
        return true
    }

    if (!currentEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        return false
    }

    val currentRoute = currentEntry.destination.route
    val currentEndpoint = currentEntry.arguments?.getString("endpoint")

    if (currentRoute?.startsWith("catalog/") == true && currentEndpoint == endpoint) {
        return false
    }

    navigate(buildCatalogRoute(endpoint, title, subtitle))
    return true
}

private fun buildCatalogRoute(
    endpoint: String,
    title: String?,
    subtitle: String?
): String {
    var route = "catalog/${Uri.encode(endpoint)}"
    val params = mutableListOf<String>()
    if (title != null) params.add("title=${Uri.encode(title)}")
    if (subtitle != null) params.add("subtitle=${Uri.encode(subtitle)}")
    if (params.isNotEmpty()) {
        route += "?" + params.joinToString("&")
    }
    return route
}

private fun buildCollectionRoute(
    type: String,
    id: String,
    title: String?,
    subtitle: String?,
    artworkUrl: String?
): String {
    var route = "collection/$type/${Uri.encode(id)}"
    val params = mutableListOf<String>()
    if (title != null) params.add("title=${Uri.encode(title)}")
    if (subtitle != null) params.add("subtitle=${Uri.encode(subtitle)}")
    if (artworkUrl != null) params.add("artworkUrl=${Uri.encode(artworkUrl)}")
    if (params.isNotEmpty()) {
        route += "?" + params.joinToString("&")
    }
    return route
}
