package com.hypermusic.feature.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.ArrowLeft
import com.hypermusic.core.designsystem.components.ScreenWrapper
import com.hypermusic.core.designsystem.theme.LocalBottomContentPadding
import com.hypermusic.feature.settings.data.LegalData
import com.hypermusic.feature.settings.data.LegalDocument
import com.hypermusic.feature.settings.data.LegalLink
import com.hypermusic.feature.settings.data.LegalSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onNavigateBack: () -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val assets = LocalContext.current.assets
    val documents = remember(assets) { LegalData.load(assets) }
    val links = remember(assets) { LegalData.loadLinks(assets) }
    val documentStates = rememberSaveableStateHolder()
    val tabs = listOf("Privacy", "Terms", "Copyright")

    ScreenWrapper(disableSafeAreaTop = true) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Legal & Privacy",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = AppIcons.ArrowLeft,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                documentStates.SaveableStateProvider(selectedTabIndex) {
                    val document = documents[selectedTabIndex]
                    LegalDocumentContent(document = document, links = links[document.title].orEmpty())
                }
            }
        }
    }
}

@Composable
private fun LegalDocumentContent(document: LegalDocument, links: List<LegalLink>) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalBottomContentPadding.current)
    ) {
        item {
            Text(
                text = "Last Updated: ${document.lastUpdated}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        items(document.sections.filterNot { it.webOnly }) { section ->
            LegalSectionView(section = section, links = links)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegalSectionView(section: LegalSection, links: List<LegalLink>) {
    Column {
        Text(
            text = section.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .semantics { heading() }
        )
        section.paragraphs.forEach { paragraph ->
            Text(
                text = linkedLegalText(paragraph, links),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun linkedLegalText(paragraph: String, links: List<LegalLink>): androidx.compose.ui.text.AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(paragraph, links, color) {
        buildAnnotatedString {
            val pattern = links.sortedByDescending { it.text.length }.joinToString("|") { Regex.escape(it.text) }
            if (pattern.isEmpty()) {
                append(paragraph)
                return@buildAnnotatedString
            }
            var position = 0
            Regex(pattern).findAll(paragraph).forEach { match ->
                append(paragraph.substring(position, match.range.first))
                val link = links.first { it.text == match.value }
                withLink(
                    LinkAnnotation.Url(
                        link.url,
                        TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
                    )
                ) {
                    append(match.value)
                }
                position = match.range.last + 1
            }
            append(paragraph.substring(position))
        }
    }
}
