/*
 * Portions of vector path data derived from Lucide Icons (https://lucide.dev)
 * Copyright (c) Lucide Contributors (ISC License)
 * Some icons derived from Feather Icons, Copyright (c) Cole Bemis (MIT License)
 */
package com.hypermusic.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object AppIcons

private fun appIcon(name: String, vararg paths: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (p in paths) {
            addPath(
                pathData = addPathNodes(p),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

private fun appFilledIcon(name: String, vararg paths: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (p in paths) {
            addPath(
                pathData = addPathNodes(p),
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

private fun appCompositeIcon(
    name: String,
    strokePaths: Array<String> = emptyArray(),
    filledPaths: Array<String> = emptyArray(),
    strokeLineWidth: Float = 2.0f,
    pathFillType: PathFillType = PathFillType.EvenOdd
): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (p in filledPaths) {
            addPath(
                pathData = addPathNodes(p),
                fill = SolidColor(Color.White),
                pathFillType = pathFillType
            )
        }
        for (p in strokePaths) {
            addPath(
                pathData = addPathNodes(p),
                stroke = SolidColor(Color.White),
                strokeLineWidth = strokeLineWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

val AppIcons.Play: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.Play",
        "M5,5a2,2 0 0,1 3.008,-1.728l11.997,6.998a2,2 0 0,1 0.003,3.458l-12,7A2,2 0 0,1 5,19Z"
    )
}

val AppIcons.Pause: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.Pause",
        "M15,3H18A1,1 0 0,1 19,4V20A1,1 0 0,1 18,21H15A1,1 0 0,1 14,20V4A1,1 0 0,1 15,3Z",
        "M6,3H9A1,1 0 0,1 10,4V20A1,1 0 0,1 9,21H6A1,1 0 0,1 5,20V4A1,1 0 0,1 6,3Z"
    )
}

val AppIcons.SkipForward: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.SkipForward",
        "M21,4v16",
        "M6.029,4.285A2,2 0 0,0 3,6v12a2,2 0 0,0 3.029,1.715l9.997,-5.998a2,2 0 0,0 0.003,-3.432Z"
    )
}

val AppIcons.SkipBack: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.SkipBack",
        "M17.971,4.285A2,2 0 0,1 21,6v12a2,2 0 0,1 -3.029,1.715l-9.997,-5.998a2,2 0 0,1 -0.003,-3.432Z",
        "M3,20V4"
    )
}

val AppIcons.FastForward: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.FastForward",
        "M12,6a2,2 0 0,1 3.414,-1.414l6,6a2,2 0 0,1 0,2.828l-6,6A2,2 0 0,1 12,18z",
        "M2,6a2,2 0 0,1 3.414,-1.414l6,6a2,2 0 0,1 0,2.828l-6,6A2,2 0 0,1 2,18z"
    )
}

val AppIcons.Rewind: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.Rewind",
        "M12,6a2,2 0 0,0 -3.414,-1.414l-6,6a2,2 0 0,0 0,2.828l6,6A2,2 0 0,0 12,18z",
        "M22,6a2,2 0 0,0 -3.414,-1.414l-6,6a2,2 0 0,0 0,2.828l6,6A2,2 0 0,0 22,18z"
    )
}

val AppIcons.SeekToNext: ImageVector get() = AppIcons.SkipForward
val AppIcons.SeekToPrevious: ImageVector get() = AppIcons.SkipBack

val AppIcons.SeekToBackward: ImageVector by lazy {
    appIcon(
        "AppIcons.SeekToBackward",
        "M3,12a9,9 0 1,0 9,-9a9.75,9.75 0 0,0 -6.74,2.74L3,8",
        "M3,3v5h5",
        "M8.5,11l1,-1v4",
        "M12.5,11a1.5,1.5 0 0,1 3,0v2a1.5,1.5 0 0,1 -3,0Z"
    )
}

val AppIcons.SeekToForward: ImageVector by lazy {
    appIcon(
        "AppIcons.SeekToForward",
        "M21,12a9,9 0 1,1 -9,-9c2.52,0 4.93,1 6.74,2.74L21,8",
        "M21,3v5h-5",
        "M8.5,11l1,-1v4",
        "M12.5,11a1.5,1.5 0 0,1 3,0v2a1.5,1.5 0 0,1 -3,0Z"
    )
}

val AppIcons.RotateCw: ImageVector by lazy {
    appIcon(
        "AppIcons.RotateCw",
        "M21,12a9,9 0 1,1 -9,-9c2.52,0 4.93,1 6.74,2.74L21,8",
        "M21,3v5h-5"
    )
}

val AppIcons.Shuffle: ImageVector by lazy {
    appIcon(
        "AppIcons.Shuffle",
        "m18,14l4,4l-4,4",
        "m18,2l4,4l-4,4",
        "M2,18h1.973a4,4 0 0,0 3.3,-1.7l5.454,-8.6a4,4 0 0,1 3.3,-1.7H22",
        "M2,6h1.972a4,4 0 0,1 3.6,2.2",
        "M22,18h-6.041a4,4 0 0,1 -3.3,-1.8l-0.359,-0.45"
    )
}

val AppIcons.Repeat: ImageVector by lazy {
    appIcon(
        "AppIcons.Repeat",
        "m17,2l4,4l-4,4",
        "M3,11v-1a4,4 0 0,1 4,-4h14",
        "m7,22l-4,-4l4,-4",
        "M21,13v1a4,4 0 0,1 -4,4H3"
    )
}

val AppIcons.Repeat1: ImageVector by lazy {
    appIcon(
        "AppIcons.Repeat1",
        "m17,2l4,4l-4,4",
        "M3,11v-1a4,4 0 0,1 4,-4h14",
        "m7,22l-4,-4l4,-4",
        "M21,13v1a4,4 0 0,1 -4,4H3",
        "M11,10h1v4"
    )
}

val AppIcons.ChevronDown: ImageVector by lazy {
    appIcon(
        "AppIcons.ChevronDown",
        "m6,9l6,6l6,-6"
    )
}

val AppIcons.ChevronRight: ImageVector by lazy {
    appIcon(
        "AppIcons.ChevronRight",
        "m9,18l6,-6l-6,-6"
    )
}

val AppIcons.Check: ImageVector by lazy {
    appIcon(
        "AppIcons.Check",
        "M20,6L9,17l-5,-5"
    )
}

val AppIcons.ExternalLink: ImageVector by lazy {
    appIcon(
        "AppIcons.ExternalLink",
        "M15,3h6v6",
        "M10,14L21,3",
        "M18,13v6a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2V8a2,2 0 0,1 2,-2h6"
    )
}

val AppIcons.Headphones: ImageVector by lazy {
    appIcon(
        "AppIcons.Headphones",
        "M3,14h3a2,2 0 0,1 2,2v3a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2v-7a9,9 0 0,1 18,0v7a2,2 0 0,1 -2,2h-1a2,2 0 0,1 -2,-2v-3a2,2 0 0,1 2,-2h3"
    )
}

val AppIcons.Video: ImageVector by lazy {
    appIcon(
        "AppIcons.Video",
        "m16,13l5.223,3.482a0.5,0.5 0 0,0 0.777,-0.416V7.87a0.5,0.5 0 0,0 -0.752,-0.432L16,10.5",
        "M4,6H14A2,2 0 0,1 16,8V16A2,2 0 0,1 14,18H4A2,2 0 0,1 2,16V8A2,2 0 0,1 4,6Z"
    )
}

val AppIcons.Sliders: ImageVector by lazy {
    appIcon(
        "AppIcons.Sliders",
        "M4,21v-7",
        "M4,10V3",
        "M2,14h4",
        "M12,21v-9",
        "M12,8V3",
        "M10,8h4",
        "M20,21v-5",
        "M20,12V3",
        "M18,16h4"
    )
}

val AppIcons.MoreVertical: ImageVector by lazy {
    appIcon(
        "AppIcons.MoreVertical",
        "M13,12A1,1 0 0,1 12,13A1,1 0 0,1 11,12A1,1 0 0,1 13,12Z",
        "M13,5A1,1 0 0,1 12,6A1,1 0 0,1 11,5A1,1 0 0,1 13,5Z",
        "M13,19A1,1 0 0,1 12,20A1,1 0 0,1 11,19A1,1 0 0,1 13,19Z"
    )
}

val AppIcons.ThumbsUp: ImageVector by lazy {
    appIcon(
        "AppIcons.ThumbsUp",
        "M15,5.88L14,10h5.83a2,2 0 0,1 1.92,2.56l-2.33,8A2,2 0 0,1 17.5,22H4a2,2 0 0,1 -2,-2v-8a2,2 0 0,1 2,-2h2.76a2,2 0 0,0 1.79,-1.11L12,2a3.13,3.13 0 0,1 3,3.88Z",
        "M7,10v12"
    )
}

val AppIcons.Download: ImageVector by lazy {
    appIcon(
        "AppIcons.Download",
        "M12,15V3",
        "M21,15v4a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2v-4",
        "m7,10l5,5l5,-5"
    )
}

val AppIcons.BookmarkPlus: ImageVector by lazy {
    appIcon(
        "AppIcons.BookmarkPlus",
        "m19,21l-7,-4l-7,4V5a2,2 0 0,1 2,-2h10a2,2 0 0,1 2,2v16Z",
        "M12,7L12,13",
        "M15,10L9,10"
    )
}

val AppIcons.BookmarkFilled: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.BookmarkFilled",
        "m19,21l-7,-4l-7,4V5a2,2 0 0,1 2,-2h10a2,2 0 0,1 2,2Z"
    )
}

val AppIcons.Bookmark: ImageVector by lazy {
    appIcon(
        "AppIcons.Bookmark",
        "m19,21l-7,-4l-7,4V5a2,2 0 0,1 2,-2h10a2,2 0 0,1 2,2Z"
    )
}

val AppIcons.Share2: ImageVector by lazy {
    appIcon(
        "AppIcons.Share2",
        "M21,5A3,3 0 0,1 18,8A3,3 0 0,1 15,5A3,3 0 0,1 21,5Z",
        "M9,12A3,3 0 0,1 6,15A3,3 0 0,1 3,12A3,3 0 0,1 9,12Z",
        "M21,19A3,3 0 0,1 18,22A3,3 0 0,1 15,19A3,3 0 0,1 21,19Z",
        "M8.59,13.51L15.42,17.49",
        "M15.41,6.51L8.59,10.49"
    )
}

val AppIcons.ListMusic: ImageVector by lazy {
    appIcon(
        "AppIcons.ListMusic",
        "M16,5H3",
        "M11,12H3",
        "M11,19H3",
        "M21,16V5",
        "M21,16A3,3 0 0,1 18,19A3,3 0 0,1 15,16A3,3 0 0,1 21,16Z"
    )
}

val AppIcons.HeartFilled: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.HeartFilled",
        "M2,9.5a5.5,5.5 0 0,1 9.591,-3.676a0.56,0.56 0 0,0 0.818,0A5.49,5.49 0 0,1 22,9.5c0,2.29 -1.5,4 -3,5.5l-5.492,5.313a2,2 0 0,1 -3,0.019L5,15c-1.5,-1.5 -3,-3.2 -3,-5.5Z"
    )
}

val AppIcons.Heart: ImageVector by lazy {
    appIcon(
        "AppIcons.Heart",
        "M2,9.5a5.5,5.5 0 0,1 9.591,-3.676a0.56,0.56 0 0,0 0.818,0A5.49,5.49 0 0,1 22,9.5c0,2.29 -1.5,4 -3,5.5l-5.492,5.313a2,2 0 0,1 -3,0.019L5,15c-1.5,-1.5 -3,-3.2 -3,-5.5"
    )
}

val AppIcons.Trash2: ImageVector by lazy {
    appIcon(
        "AppIcons.Trash2",
        "M10,11v6",
        "M14,11v6",
        "M19,6v14a2,2 0 0,1 -2,2H7a2,2 0 0,1 -2,-2V6",
        "M3,6h18",
        "M8,6V4a2,2 0 0,1 2,-2h4a2,2 0 0,1 2,2v2"
    )
}

val AppIcons.FolderPlus: ImageVector by lazy {
    appIcon(
        "AppIcons.FolderPlus",
        "M12,10v6",
        "M9,13h6",
        "M20,20a2,2 0 0,0 2,-2V8a2,2 0 0,0 -2,-2h-7.9a2,2 0 0,1 -1.69,-0.9L9.6,3.9A2,2 0 0,0 7.93,3H4a2,2 0 0,0 -2,2v13a2,2 0 0,0 2,2Z"
    )
}

val AppIcons.Folder: ImageVector by lazy {
    appIcon(
        "AppIcons.Folder",
        "M20,20a2,2 0 0,0 2,-2V8a2,2 0 0,0 -2,-2h-7.9a2,2 0 0,1 -1.69,-0.9L9.6,3.9A2,2 0 0,0 7.93,3H4a2,2 0 0,0 -2,2v13a2,2 0 0,0 2,2Z"
    )
}

val AppIcons.Disc3: ImageVector by lazy {
    appIcon(
        "AppIcons.Disc3",
        "M22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 22,12Z",
        "M6,12c0,-1.7 0.7,-3.2 1.8,-4.2",
        "M14,12A2,2 0 0,1 12,14A2,2 0 0,1 10,12A2,2 0 0,1 14,12Z",
        "M18,12c0,1.7 -0.7,3.2 -1.8,4.2"
    )
}

val AppIcons.User: ImageVector by lazy {
    appIcon(
        "AppIcons.User",
        "M19,21v-2a4,4 0 0,0 -4,-4H9a4,4 0 0,0 -4,4v2",
        "M16,7A4,4 0 0,1 12,11A4,4 0 0,1 8,7A4,4 0 0,1 16,7Z"
    )
}

val AppIcons.Moon: ImageVector by lazy {
    appIcon(
        "AppIcons.Moon",
        "M12,3a6,6 0 0,0 9,9a9,9 0 1,1 -9,-9Z"
    )
}

val AppIcons.Sun: ImageVector by lazy {
    appIcon(
        "AppIcons.Sun",
        "M12,2v2",
        "M12,20v2",
        "m4.93,4.93l1.41,1.41",
        "m17.66,17.66l1.41,1.41",
        "M2,12h2",
        "M20,12h2",
        "m6.34,17.66l-1.41,1.41",
        "m19.07,4.93l-1.41,1.41",
        "M16,12A4,4 0 0,1 12,16A4,4 0 0,1 8,12A4,4 0 0,1 16,12Z"
    )
}

val AppIcons.Bell: ImageVector by lazy {
    appIcon(
        "AppIcons.Bell",
        "M10.3,21a1.94,1.94 0 0,0 3.4,0",
        "M6,8a6,6 0 0,1 12,0c0,7 3,9 3,9H3s3,-2 3,-9"
    )
}

val AppIcons.Mic: ImageVector by lazy {
    appIcon(
        "AppIcons.Mic",
        "M12,2a3,3 0 0,0 -3,3v7a3,3 0 0,0 6,0V5a3,3 0 0,0 -3,-3Z",
        "M19,10v2a7,7 0 0,1 -14,0v-2",
        "M12,19v4",
        "M8,23h8"
    )
}

val AppIcons.Quote: ImageVector by lazy {
    appIcon(
        "AppIcons.Quote",
        "M16,3a2,2 0 0,0 -2,2v6a2,2 0 0,0 2,2a1,1 0 0,1 1,1v1a2,2 0 0,1 -2,2a1,1 0 0,0 -1,1v2a1,1 0 0,0 1,1a6,6 0 0,0 6,-6V5a2,2 0 0,0 -2,-2z",
        "M5,3a2,2 0 0,0 -2,2v6a2,2 0 0,0 2,2a1,1 0 0,1 1,1v1a2,2 0 0,1 -2,2a1,1 0 0,0 -1,1v2a1,1 0 0,0 1,1a6,6 0 0,0 6,-6V5a2,2 0 0,0 -2,-2z"
    )
}

val AppIcons.Lyrics: ImageVector get() = AppIcons.Quote

val AppIcons.Infinity: ImageVector by lazy {
    appIcon(
        "AppIcons.Infinity",
        "M12,12c-2,-2.67 -4,-4 -6,-4a4,4 0 1,0 0,8c2,0 4,-1.33 6,-4Zm0,0c2,2.67 4,4 6,4a4,4 0 1,0 0,-8c-2,0 -4,1.33 -6,4Z"
    )
}

val AppIcons.AudioWaveform: ImageVector by lazy {
    appIcon(
        "AppIcons.AudioWaveform",
        "M2,13a2,2 0 0,0 2,-2V7a2,2 0 0,1 4,0v13a2,2 0 0,0 4,0V4a2,2 0 0,1 4,0v13a2,2 0 0,0 4,0v-4a2,2 0 0,1 2,-2"
    )
}

val AppIcons.Leaf: ImageVector by lazy {
    appIcon(
        "AppIcons.Leaf",
        "M11,20A7,7 0 0,1 9.8,6.1C15.5,5 17,4.48 19,2c1,2 2,4.18 2,8a11,11 0 0,1 -10,10Z",
        "M2,21c0,-3 1.85,-5.36 5.08,-6C9.5,14.52 12,13 13,12"
    )
}

val AppIcons.Wifi: ImageVector by lazy {
    appIcon(
        "AppIcons.Wifi",
        "M12,20h0.01",
        "M2,8.82a15,15 0 0,1 20,0",
        "M5,12.859a10,10 0 0,1 14,0",
        "M8.5,16.429a5,5 0 0,1 7,0"
    )
}

val AppIcons.PieChart: ImageVector by lazy {
    appIcon(
        "AppIcons.PieChart",
        "M21.21,15.89A10,10 0 1,1 8,2.83",
        "M22,12A10,10 0 0,0 12,2v10z"
    )
}

val AppIcons.Timer: ImageVector by lazy {
    appIcon(
        "AppIcons.Timer",
        "M10,2h4",
        "M12,14l3,-3",
        "M21,13A9,9 0 0,1 12,22A9,9 0 0,1 3,13A9,9 0 0,1 21,13Z"
    )
}

val AppIcons.Info: ImageVector by lazy {
    appIcon(
        "AppIcons.Info",
        "M12,16v-4",
        "M12,8h0.01",
        "M22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 22,12Z"
    )
}

val AppIcons.ArrowUpCircle: ImageVector by lazy {
    appIcon(
        "AppIcons.ArrowUpCircle",
        "M12,16V8",
        "m8,12l4,-4l4,4",
        "M22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 22,12Z"
    )
}

val AppIcons.ShieldCheck: ImageVector by lazy {
    appIcon(
        "AppIcons.ShieldCheck",
        "M20,13c0,5 -3.5,7.5 -7.66,8.95a1,1 0 0,1 -0.67,-0.01C7.5,20.5 4,18 4,13V6a1,1 0 0,1 1,-1c2,0 4.5,-1.2 6.24,-2.72a1.17,1.17 0 0,1 1.52,0C14.51,3.81 17,5 19,5a1,1 0 0,1 1,1z",
        "m9,12l2,2l4,-4"
    )
}

val AppIcons.BookOpen: ImageVector by lazy {
    appIcon(
        "AppIcons.BookOpen",
        "M12,7v14",
        "M16,12h2",
        "M16,8h2",
        "M3,18a1,1 0 0,1 -1,-1V4a1,1 0 0,1 1,-1h5a4,4 0 0,1 4,4 4,4 0 0,1 4,-4h5a1,1 0 0,1 1,1v13a1,1 0 0,1 -1,1h-6a3,3 0 0,0 -3,3 3,3 0 0,0 -3,-3z"
    )
}

val AppIcons.Terminal: ImageVector by lazy {
    appIcon(
        "AppIcons.Terminal",
        "M12,19h8",
        "m4,17l6,-6l-6,-6"
    )
}

val AppIcons.Bug: ImageVector by lazy {
    appIcon(
        "AppIcons.Bug",
        "M12,20v-9",
        "M14,7a4,4 0 0,1 4,4v3a6,6 0 0,1 -12,0v-3a4,4 0 0,1 4,-4Z",
        "M14.12,3.88L16,2",
        "M21,21a4,4 0 0,0 -3.81,-4",
        "M21,5a4,4 0 0,1 -3.55,3.97",
        "M22,13h-4",
        "M3,21a4,4 0 0,1 3.81,-4",
        "M3,5a4,4 0 0,0 3.55,3.97",
        "M6,13H2",
        "m8,2l1.88,1.88",
        "M9,7.13V6a3,3 0 1,1 6,0v1.13"
    )
}

val AppIcons.Palette: ImageVector by lazy {
    appIcon(
        "AppIcons.Palette",
        "M12,22a1,1 0 0,1 0,-20a10,9 0 0,1 10,9a5,5 0 0,1 -5,5h-2.25a1.75,1.75 0 0,0 -1.4,2.8l0.3,0.4a1.75,1.75 0 0,1 -1.4,2.8Z"
    )
}

val AppIcons.Globe: ImageVector by lazy {
    appIcon(
        "AppIcons.Globe",
        "M22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 22,12Z",
        "M12,2a14.5,14.5 0 0,0 0,20a14.5,14.5 0 0,0 0,-20",
        "M2,12h20"
    )
}

val AppIcons.ArrowLeft: ImageVector by lazy {
    appIcon(
        "AppIcons.ArrowLeft",
        "m12,19l-7,-7l7,-7",
        "M19,12H5"
    )
}

val AppIcons.Database: ImageVector by lazy {
    appIcon(
        "AppIcons.Database",
        "M21,5A9,3 0 0,1 12,8A9,3 0 0,1 3,5A9,3 0 0,1 21,5Z",
        "M3,5V19A9,3 0 0,0 21,19V5",
        "M3,12A9,3 0 0,0 21,12"
    )
}

val AppIcons.History: ImageVector by lazy {
    appIcon(
        "AppIcons.History",
        "M3,12a9,9 0 1,0 9,-9a9.75,9.75 0 0,0 -6.74,2.74L3,8",
        "M3,3v5h5",
        "M12,7v5l4,2"
    )
}

val AppIcons.ArrowDownCircle: ImageVector by lazy {
    appIcon(
        "AppIcons.ArrowDownCircle",
        "M22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 22,12Z",
        "M12,8v8",
        "m8,12 4,4 4,-4"
    )
}

val AppIcons.ArrowDown: ImageVector by lazy {
    appIcon(
        "AppIcons.ArrowDown",
        "M12,5v14",
        "m19,12 -7,7 -7,-7"
    )
}

val AppIcons.ArrowDropUp: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.ArrowDropUp",
        "M7,14l5,-5 5,5z"
    )
}

val AppIcons.ArrowDropDown: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.ArrowDropDown",
        "M7,10l5,5 5,-5z"
    )
}

val AppIcons.ArrowDownToLine: ImageVector by lazy {
    appIcon(
        "AppIcons.ArrowDownToLine",
        "M12,17V3",
        "m6,11 6,6 6,-6",
        "M19,21H5"
    )
}

val AppIcons.MonitorSmartphone: ImageVector by lazy {
    appIcon(
        "AppIcons.MonitorSmartphone",
        "M18,8V6a2,2 0 0,0 -2,-2H4a2,2 0 0,0 -2,2v7a2,2 0 0,0 2,2h8",
        "M10,19v-4",
        "M7,19h5",
        "M18,12h2a2,2 0 0,1 2,2v6a2,2 0 0,1 -2,2h-2a2,2 0 0,1 -2,-2v-6a2,2 0 0,1 2,-2z"
    )
}

val AppIcons.LayoutTemplate: ImageVector by lazy {
    appIcon(
        "AppIcons.LayoutTemplate",
        "M4,3h16a1,1 0 0,1 1,1v5a1,1 0 0,1 -1,1H4a1,1 0 0,1 -1,-1V4a1,1 0 0,1 1,-1z",
        "M4,14h7a1,1 0 0,1 1,1v5a1,1 0 0,1 -1,1H4a1,1 0 0,1 -1,-1v-5a1,1 0 0,1 1,-1z",
        "M17,14h3a1,1 0 0,1 1,1v5a1,1 0 0,1 -1,1h-3a1,1 0 0,1 -1,-1v-5a1,1 0 0,1 1,-1z"
    )
}

val AppIcons.Home: ImageVector by lazy {
    appIcon(
        "AppIcons.Home",
        "M9,21v-8a1,1 0 0,1 1,-1h4a1,1 0 0,1 1,1v8h6v-11a2,2 0 0,0 -0.709,-1.528l-7,-6a2,2 0 0,0 -2.582,0l-7,6A2,2 0 0,0 3,10v11z"
    )
}

val AppIcons.Search: ImageVector by lazy {
    appIcon(
        "AppIcons.Search",
        "m21,21 -4.34,-4.34",
        "M19,11 A8,8 0 1,1 3,11 A8,8 0 1,1 19,11 Z"
    )
}

val AppIcons.Library: ImageVector by lazy {
    appIcon(
        "AppIcons.Library",
        "m16,6 4,14",
        "M12,6v14",
        "M8,8v12",
        "M4,4v16"
    )
}

val AppIcons.Settings: ImageVector by lazy {
    appIcon(
        "AppIcons.Settings",
        "M12.22,2h-0.44a2,2 0 0,0 -2,2v0.18a2,2 0 0,1 -1,1.73l-0.43,0.25a2,2 0 0,1 -2,0l-0.15,-0.08a2,2 0 0,0 -2.73,0.73l-0.22,0.38a2,2 0 0,0 0.73,2.73l0.15,0.1a2,2 0 0,1 1,1.72v0.51a2,2 0 0,1 -1,1.74l-0.15,0.09a2,2 0 0,0 -0.73,2.73l0.22,0.38a2,2 0 0,0 2.73,0.73l0.15,-0.08a2,2 0 0,1 2,0l0.43,0.25a2,2 0 0,1 1,1.73V20a2,2 0 0,0 2,2h0.44a2,2 0 0,0 2,-2v-0.18a2,2 0 0,1 1,-1.73l0.43,-0.25a2,2 0 0,1 2,0l0.15,0.08a2,2 0 0,0 2.73,-0.73l0.22,-0.39a2,2 0 0,0 -0.73,-2.73l-0.15,-0.08a2,2 0 0,1 -1,-1.74v-0.5a2,2 0 0,1 1,-1.74l0.15,-0.09a2,2 0 0,0 0.73,-2.73l-0.22,-0.38a2,2 0 0,0 -2.73,-0.73l-0.15,0.08a2,2 0 0,1 -2,0l-0.43,-0.25a2,2 0 0,1 -1,-1.73V4a2,2 0 0,0 -2,-2z",
        "M12,8a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z"
    )
}

val AppIcons.X: ImageVector by lazy {
    appIcon(
        "AppIcons.X",
        "M18,6 6,18",
        "m6,6 12,12"
    )
}

val AppIcons.Plus: ImageVector by lazy {
    appIcon(
        "AppIcons.Plus",
        "M5,12h14",
        "M12,5v14"
    )
}

val AppIcons.Flame: ImageVector by lazy {
    appIcon(
        "AppIcons.Flame",
        "M12,3q1,4 4,6.5t3,5.5a1,1 0 0,1 -14,0 5,5 0 0,1 1,-3 1,1 0 0,0 5,0c0,-2 -1.5,-3 -1.5,-5q0,-2 2.5,-4"
    )
}

val AppIcons.BarChart2: ImageVector by lazy {
    appIcon(
        "AppIcons.BarChart2",
        "M3,3v16a2,2 0 0,0 2,2h16",
        "M18,17V9",
        "M13,17V5",
        "M8,17v-3"
    )
}

val AppIcons.CheckCircle2: ImageVector by lazy {
    appIcon(
        "AppIcons.CheckCircle2",
        "M22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 22,12Z",
        "m16,9 -5.5,5.5L8,12"
    )
}

val AppIcons.AlertCircle: ImageVector by lazy {
    appIcon(
        "AppIcons.AlertCircle",
        "M22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 22,12Z",
        "M12,8v4",
        "M12,16h.01"
    )
}

val AppIcons.ArrowUpDown: ImageVector by lazy {
    appIcon(
        "AppIcons.ArrowUpDown",
        "m21,16 -4,4 -4,-4",
        "M17,20V4",
        "m3,8 4,-4 4,4",
        "M7,4v16"
    )
}

val AppIcons.LayoutGrid: ImageVector by lazy {
    appIcon(
        "AppIcons.LayoutGrid",
        "M4,3h5a1,1 0 0,1 1,1v5a1,1 0 0,1 -1,1H4a1,1 0 0,1 -1,-1V4a1,1 0 0,1 1,-1z",
        "M15,3h5a1,1 0 0,1 1,1v5a1,1 0 0,1 -1,1h-5a1,1 0 0,1 -1,-1V4a1,1 0 0,1 1,-1z",
        "M15,14h5a1,1 0 0,1 1,1v5a1,1 0 0,1 -1,1h-5a1,1 0 0,1 -1,-1v-5a1,1 0 0,1 1,-1z",
        "M4,14h5a1,1 0 0,1 1,1v5a1,1 0 0,1 -1,1H4a1,1 0 0,1 -1,-1v-5a1,1 0 0,1 1,-1z"
    )
}

val AppIcons.List: ImageVector by lazy {
    appIcon(
        "AppIcons.List",
        "M3,5h.01",
        "M3,12h.01",
        "M3,19h.01",
        "M8,5h13",
        "M8,12h13",
        "M8,19h13"
    )
}

val AppIcons.Music: ImageVector by lazy {
    appIcon(
        "AppIcons.Music",
        "M8,14a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z",
        "M12,18V2l7,4"
    )
}

val AppIcons.GripVertical: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.GripVertical",
        "M10,12 A1,1 0 1,1 8,12 A1,1 0 1,1 10,12 Z",
        "M10,5 A1,1 0 1,1 8,5 A1,1 0 1,1 10,5 Z",
        "M10,19 A1,1 0 1,1 8,19 A1,1 0 1,1 10,19 Z",
        "M16,12 A1,1 0 1,1 14,12 A1,1 0 1,1 16,12 Z",
        "M16,5 A1,1 0 1,1 14,5 A1,1 0 1,1 16,5 Z",
        "M16,19 A1,1 0 1,1 14,19 A1,1 0 1,1 16,19 Z"
    )
}

val AppIcons.Languages: ImageVector by lazy {
    appIcon(
        "AppIcons.Languages",
        "M5,8l6,6",
        "M4,14l6,-6 2,-3",
        "M2,5h12",
        "M7,2h1",
        "M22,22l-5,-10 -5,10",
        "M14,18h6"
    )
}

val AppIcons.MapPin: ImageVector by lazy {
    appIcon(
        "AppIcons.MapPin",
        "M20,10c0,4.993 -5.539,10.193 -7.399,11.799a1,1 0 0,1 -1.202,0C9.539,20.193 4,14.993 4,10a8,8 0 0,1 16,0Z",
        "M12,10m-3,0a3,3 0 1,0 6,0a3,3 0 1,0 -6,0"
    )
}

val AppIcons.Menu: ImageVector by lazy {
    appIcon(
        "AppIcons.Menu",
        "M4,12L20,12",
        "M4,6L20,6",
        "M4,18L20,18"
    )
}

val AppIcons.ReorderHandle: ImageVector by lazy {
    ImageVector.Builder(
        name = "AppIcons.ReorderHandle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes("M4,9L20,9 M4,15L20,15"),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

val AppIcons.ReorderHandleIcon: ImageVector
    get() = AppIcons.ReorderHandle

val AppIcons.TabHomeOutline: ImageVector by lazy {
    appIcon(
        "AppIcons.TabHomeOutline",
        "M9,21v-8a1,1 0 0,1 1,-1h4a1,1 0 0,1 1,1v8h6v-11a2,2 0 0,0 -0.709,-1.528l-7,-6a2,2 0 0,0 -2.582,0l-7,6A2,2 0 0,0 3,10v11z"
    )
}

val AppIcons.TabHomeFilled: ImageVector by lazy {
    appFilledIcon(
        "AppIcons.TabHomeFilled",
        "M9,21v-8a1,1 0 0,1 1,-1h4a1,1 0 0,1 1,1v8h6v-11a2,2 0 0,0 -0.709,-1.528l-7,-6a2,2 0 0,0 -2.582,0l-7,6A2,2 0 0,0 3,10v11z"
    )
}

val AppIcons.TabSearchOutline: ImageVector by lazy {
    appIcon(
        "AppIcons.TabSearchOutline",
        "M11,3a8,8 0 1,0 0,16a8,8 0 1,0 0,-16z",
        "M21,21l-4.35,-4.35"
    )
}

val AppIcons.TabSearchFilled: ImageVector by lazy {
    appCompositeIcon(
        name = "AppIcons.TabSearchFilled",
        strokePaths = arrayOf(
            "M11,3a8,8 0 1,0 0,16a8,8 0 1,0 0,-16z",
            "M21,21l-4.35,-4.35"
        ),
        filledPaths = arrayOf(
            "M11,6.5a4.5,4.5 0 1,0 0,9a4.5,4.5 0 1,0 0,-9z"
        )
    )
}

val AppIcons.TabLibraryOutline: ImageVector by lazy {
    appCompositeIcon(
        name = "AppIcons.TabLibraryOutline",
        filledPaths = arrayOf(
            "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zm16,-4H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zm0,14H8V4h12v12z"
        ),
        pathFillType = PathFillType.EvenOdd
    )
}

val AppIcons.TabLibraryFilled: ImageVector by lazy {
    appCompositeIcon(
        name = "AppIcons.TabLibraryFilled",
        filledPaths = arrayOf(
            "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zm16,-4H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z"
        ),
        pathFillType = PathFillType.EvenOdd
    )
}

val AppIcons.TabSettingsOutline: ImageVector by lazy {
    appIcon(
        "AppIcons.TabSettingsOutline",
        "M12.22,2h-0.44a2,2 0 0,0 -2,2v0.18a2,2 0 0,1 -1,1.73l-0.43,0.25a2,2 0 0,1 -2,0l-0.15,-0.08a2,2 0 0,0 -2.73,0.73l-0.22,0.38a2,2 0 0,0 0.73,2.73l0.15,0.1a2,2 0 0,1 1,1.72v0.51a2,2 0 0,1 -1,1.74l-0.15,0.09a2,2 0 0,0 -0.73,2.73l0.22,0.38a2,2 0 0,0 2.73,0.73l0.15,-0.08a2,2 0 0,1 2,0l0.43,0.25a2,2 0 0,1 1,1.73V20a2,2 0 0,0 2,2h0.44a2,2 0 0,0 2,-2v-0.18a2,2 0 0,1 1,-1.73l0.43,-0.25a2,2 0 0,1 2,0l0.15,0.08a2,2 0 0,0 2.73,-0.73l0.22,-0.39a2,2 0 0,0 -0.73,-2.73l-0.15,-0.08a2,2 0 0,1 -1,-1.74v-0.5a2,2 0 0,1 1,-1.74l0.15,-0.09a2,2 0 0,0 0.73,-2.73l-0.22,-0.38a2,2 0 0,0 -2.73,-0.73l-0.15,0.08a2,2 0 0,1 -2,0l-0.43,-0.25a2,2 0 0,1 -1,-1.73V4a2,2 0 0,0 -2,-2z",
        "M12,8a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z"
    )
}

val AppIcons.TabSettingsFilled: ImageVector by lazy {
    appCompositeIcon(
        name = "AppIcons.TabSettingsFilled",
        filledPaths = arrayOf(
            "M12.22,2h-0.44a2,2 0 0,0 -2,2v0.18a2,2 0 0,1 -1,1.73l-0.43,0.25a2,2 0 0,1 -2,0l-0.15,-0.08a2,2 0 0,0 -2.73,0.73l-0.22,0.38a2,2 0 0,0 0.73,2.73l0.15,0.1a2,2 0 0,1 1,1.72v0.51a2,2 0 0,1 -1,1.74l-0.15,0.09a2,2 0 0,0 -0.73,2.73l0.22,0.38a2,2 0 0,0 2.73,0.73l0.15,-0.08a2,2 0 0,1 2,0l0.43,0.25a2,2 0 0,1 1,1.73V20a2,2 0 0,0 2,2h0.44a2,2 0 0,0 2,-2v-0.18a2,2 0 0,1 1,-1.73l0.43,-0.25a2,2 0 0,1 2,0l0.15,0.08a2,2 0 0,0 2.73,-0.73l0.22,-0.39a2,2 0 0,0 -0.73,-2.73l-0.15,-0.08a2,2 0 0,1 -1,-1.74v-0.5a2,2 0 0,1 1,-1.74l0.15,-0.09a2,2 0 0,0 0.73,-2.73l-0.22,-0.38a2,2 0 0,0 -2.73,-0.73l-0.15,0.08a2,2 0 0,1 -2,0l-0.43,-0.25a2,2 0 0,1 -1,-1.73V4a2,2 0 0,0 -2,-2z M12,8a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z"
        ),
        pathFillType = PathFillType.EvenOdd
    )
}

val AppIcons.RotateCcw: ImageVector by lazy {
    appIcon(
        "AppIcons.RotateCcw",
        "M3,12a9,9 0 1,0 9,-9a9.75,9.75 0 0,0 -6.74,2.74L3,8",
        "M3,3v5h5"
    )
}

val AppIcons.ListPlus: ImageVector by lazy {
    appIcon(
        "AppIcons.ListPlus",
        "M16,5H3",
        "M11,12H3",
        "M16,19H3",
        "M18,9v6",
        "M21,12h-6"
    )
}

val AppIcons.AddToPlaylist: ImageVector get() = AppIcons.ListPlus

val AppIcons.ListEnd: ImageVector by lazy {
    appIcon(
        "AppIcons.ListEnd",
        "M16,5H3",
        "M16,12H3",
        "M9,19H3",
        "m16,16l-3,3l3,3",
        "M21,5v12a2,2 0 0,1 -2,2h-6"
    )
}

val AppIcons.AddToQueue: ImageVector get() = AppIcons.ListEnd

val AppIcons.ListStart: ImageVector by lazy {
    appIcon(
        "AppIcons.ListStart",
        "M3,5h6",
        "M3,12h13",
        "M3,19h13",
        "m16,8l-3,-3l3,-3",
        "M21,19V7a2,2 0 0,0 -2,-2h-6"
    )
}

val AppIcons.PlayNext: ImageVector get() = AppIcons.ListStart

val AppIcons.UserPlus: ImageVector by lazy {
    appIcon(
        "AppIcons.UserPlus",
        "M16,21v-2a4,4 0 0,0 -4,-4H6a4,4 0 0,0 -4,4v2",
        "M9,11A4,4 0 1,0 9,3a4,4 0 0,0 0,8z",
        "M19,8v6",
        "M22,11h-6"
    )
}

val AppIcons.UserCheck: ImageVector by lazy {
    appIcon(
        "AppIcons.UserCheck",
        "M16,21v-2a4,4 0 0,0 -4,-4H6a4,4 0 0,0 -4,4v2",
        "M9,11A4,4 0 1,0 9,3a4,4 0 0,0 0,8z",
        "M16,11l2,2l4,-4"
    )
}

val AppIcons.AudioLines: ImageVector by lazy {
    appIcon(
        "AppIcons.AudioLines",
        "M2,10v3",
        "M6,6v11",
        "M10,3v18",
        "M14,8v7",
        "M18,5v14",
        "M22,10v3"
    )
}

val AppIcons.HardDrive: ImageVector by lazy {
    appIcon(
        "AppIcons.HardDrive",
        "M22,12H2",
        "M5.45,5.11L2,12v6a2,2 0 0,0 2,2h16a2,2 0 0,0 2,-2v-6l-3.45,-6.89A2,2 0 0,0 16.76,4H7.24a2,2 0 0,0 -1.79,1.11z",
        "M6,16h.01",
        "M10,16h.01"
    )
}

val AppIcons.ExplicitBadge: ImageVector by lazy {
    appIcon(
        "AppIcons.ExplicitBadge",
        "M6,3h12a3,3 0 0,1 3,3v12a3,3 0 0,1 -3,3H6a3,3 0 0,1 -3,-3V6a3,3 0 0,1 3,-3z",
        "M15,8.5H9.5",
        "M9.5,8.5v7H15",
        "M9.5,12H13.5"
    )
}

val AppIcons.GitBranch: ImageVector by lazy {
    appIcon(
        "AppIcons.GitBranch",
        "M6,3v12",
        "M18,9a9,9 0 0,1 -9,9",
        "M6,21a3,3 0 1,0 0,-6 3,3 0 0,0 0,6z",
        "M18,9a3,3 0 1,0 0,-6 3,3 0 0,0 0,6z"
    )
}

val AppIcons.Podcast: ImageVector by lazy {
    appIcon(
        "AppIcons.Podcast",
        "M18.1,14.3a7.5,7.5 0 1,0 -12.2,0",
        "M15.7,12.6a4.5,4.5 0 1,0 -7.4,0",
        "M12,12a2,2 0 1,0 0,-4a2,2 0 0,0 0,4z",
        "M12,12v9",
        "M9,21h6"
    )
}

val AppIcons.SkipSilence: ImageVector by lazy {
    appIcon(
        "AppIcons.SkipSilence",
        "M3,10v4",
        "M7,6v12",
        "M11,9v6",
        "M14,12h5",
        "m16,9l3,3l-3,3",
        "M22,6v12"
    )
}

val AppIcons.ListRestart: ImageVector by lazy {
    appIcon(
        "AppIcons.ListRestart",
        "M21,5H3",
        "M7,12H3",
        "M7,19H3",
        "M12,18a5,5 0 0,0 9,-3a4.5,4.5 0 0,0 -4.5,-4.5c-1.33,0 -2.54,0.54 -3.41,1.41L11,14",
        "M11,10v4h4"
    )
}

val AppIcons.PersistentQueue: ImageVector get() = AppIcons.ListRestart
