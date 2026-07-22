package com.ahamai.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom Lucide-style icons for AhamAI Profile Screen.
 * Clean, minimal 24x24 stroke-based icons.
 */
object Lucide {

    val ArrowLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowLeft", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f)
                horizontalLineTo(5f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }.build()
    }


    val ChevronUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronUp", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 15f)
                lineTo(12f, 9f)
                lineTo(6f, 15f)
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronDown", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 18f)
                lineTo(15f, 12f)
                lineTo(9f, 6f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }.build()
    }

    /** Hollow circle — idle / empty state. */
    val Circle: ImageVector by lazy {
        ImageVector.Builder(
            name = "Circle", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, true, true, 2f, 12f)
                arcTo(10f, 10f, 0f, true, true, 22f, 12f)
                close()
            }
        }.build()
    }

    val PlugZap: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlugZap", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Lightning bolt shape
                moveTo(13f, 2f)
                lineTo(3f, 14f)
                horizontalLineTo(12f)
                lineTo(11f, 22f)
                lineTo(21f, 10f)
                horizontalLineTo(12f)
                lineTo(13f, 2f)
                close()
            }
        }.build()
    }


    val Globe: ImageVector by lazy {
        ImageVector.Builder(
            name = "Globe", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Outer circle using arcs
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, true, true, 2f, 12f)
                arcTo(10f, 10f, 0f, true, true, 22f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 12f)
                horizontalLineTo(22f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Vertical meridian left
                moveTo(12f, 2f)
                arcTo(15f, 15f, 0f, false, false, 12f, 22f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Vertical meridian right
                moveTo(12f, 2f)
                arcTo(15f, 15f, 0f, false, true, 12f, 22f)
            }
        }.build()
    }

    val Server: ImageVector by lazy {
        ImageVector.Builder(
            name = "Server", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Top server rect with rounded corners
                moveTo(4f, 2f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 22f, 4f)
                verticalLineTo(8f)
                arcTo(2f, 2f, 0f, false, true, 20f, 10f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 2f, 8f)
                verticalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 4f, 2f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Bottom server rect
                moveTo(4f, 14f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 22f, 16f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 20f, 22f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 2f, 20f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, false, true, 4f, 14f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 6f)
                horizontalLineTo(6.01f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 18f)
                horizontalLineTo(6.01f)
            }
        }.build()
    }


    val Layers: ImageVector by lazy {
        ImageVector.Builder(
            name = "Layers", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                lineTo(2f, 7f)
                lineTo(12f, 12f)
                lineTo(22f, 7f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 17f)
                lineTo(12f, 22f)
                lineTo(22f, 17f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 12f)
                lineTo(12f, 17f)
                lineTo(22f, 12f)
            }
        }.build()
    }

    val AudioLines: ImageVector by lazy {
        ImageVector.Builder(
            name = "AudioLines", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 10f)
                verticalLineTo(14f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 6f)
                verticalLineTo(18f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 3f)
                verticalLineTo(21f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14f, 6f)
                verticalLineTo(18f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 8f)
                verticalLineTo(16f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 10f)
                verticalLineTo(14f)
            }
        }.build()
    }


    val Image: ImageVector by lazy {
        ImageVector.Builder(
            name = "Image", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                horizontalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 21f, 5f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                verticalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Sun circle
                moveTo(10.5f, 8.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 7.5f, 8.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 8.5f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 15f)
                lineTo(16f, 10f)
                lineTo(5f, 21f)
            }
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, false, false, 2f, 12f)
                arcTo(10f, 10f, 0f, false, false, 12f, 22f)
                arcTo(1.5f, 1.5f, 0f, false, false, 13.5f, 20.5f)
                arcTo(1.5f, 1.5f, 0f, false, false, 12.8f, 18.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 14.3f, 17f)
                horizontalLineTo(16f)
                arcTo(6f, 6f, 0f, false, false, 22f, 11f)
                arcTo(10f, 10f, 0f, false, false, 12f, 2f)
                close()
            }
            // Dot
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 11f)
                horizontalLineTo(8.01f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 7f)
                horizontalLineTo(12.01f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 10f)
                horizontalLineTo(16.01f)
            }
        }.build()
    }


    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "Info", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Circle
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, true, true, 2f, 12f)
                arcTo(10f, 10f, 0f, true, true, 22f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 16f)
                verticalLineTo(12f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    val LogOut: ImageVector by lazy {
        ImageVector.Builder(
            name = "LogOut", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 21f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                verticalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                horizontalLineTo(9f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 17f)
                lineTo(21f, 12f)
                lineTo(16f, 7f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 12f)
                horizontalLineTo(9f)
            }
        }.build()
    }


    val Trash2: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trash2", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 6f)
                horizontalLineTo(21f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 6f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 17f, 22f)
                horizontalLineTo(7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 20f)
                verticalLineTo(6f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 6f)
                verticalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 10f, 2f)
                horizontalLineTo(14f)
                arcTo(2f, 2f, 0f, false, true, 16f, 4f)
                verticalLineTo(6f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 11f)
                verticalLineTo(17f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14f, 11f)
                verticalLineTo(17f)
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 3f)
                lineTo(20f, 12f)
                lineTo(6f, 21f)
                close()
            }
        }.build()
    }

    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "Plus", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5f)
                verticalLineTo(19f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12f)
                horizontalLineTo(19f)
            }
        }.build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder(
            name = "Send", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 2f)
                lineTo(11f, 13f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 2f)
                lineTo(15f, 22f)
                lineTo(11f, 13f)
                lineTo(2f, 9f)
                lineTo(22f, 2f)
                close()
            }
        }.build()
    }

    val Phone: ImageVector by lazy {
        ImageVector.Builder(
            name = "Phone", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 16.92f)
                verticalLineTo(19.92f)
                arcTo(2f, 2f, 0f, false, true, 19.79f, 21.92f)
                arcTo(19.79f, 19.79f, 0f, false, true, 11.18f, 19.27f)
                arcTo(19.5f, 19.5f, 0f, false, true, 5.73f, 16.92f)
                arcTo(19.79f, 19.79f, 0f, false, true, 3.08f, 8.31f)
                arcTo(2f, 2f, 0f, false, true, 5.06f, 6.08f)
                horizontalLineTo(8.06f)
                arcTo(2f, 2f, 0f, false, true, 10.06f, 7.72f)
                arcTo(12.44f, 12.44f, 0f, false, false, 10.58f, 9.49f)
                arcTo(2f, 2f, 0f, false, true, 10.13f, 11.55f)
                lineTo(8.83f, 12.85f)
                arcTo(16f, 16f, 0f, false, false, 11.15f, 16.17f)
                lineTo(12.45f, 14.87f)
                arcTo(2f, 2f, 0f, false, true, 14.51f, 14.42f)
                arcTo(12.44f, 12.44f, 0f, false, false, 16.28f, 14.94f)
                arcTo(2f, 2f, 0f, false, true, 17.92f, 16.94f)
                verticalLineTo(19.94f)
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 12f)
                arcTo(3f, 3f, 0f, true, true, 9f, 12f)
                arcTo(3f, 3f, 0f, true, true, 15f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                lineTo(15.09f, 8.26f)
                lineTo(22f, 9.27f)
                lineTo(17f, 14.14f)
                lineTo(18.18f, 21.02f)
                lineTo(12f, 17.77f)
                lineTo(5.82f, 21.02f)
                lineTo(7f, 14.14f)
                lineTo(2f, 9.27f)
                lineTo(8.91f, 8.26f)
                close()
            }
        }.build()
    }

    val Sliders: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sliders", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 21f)
                verticalLineTo(14f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 10f)
                verticalLineTo(3f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 21f)
                verticalLineTo(12f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8f)
                verticalLineTo(3f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 21f)
                verticalLineTo(16f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 12f)
                verticalLineTo(3f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 14f)
                horizontalLineTo(6f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 8f)
                horizontalLineTo(14f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 16f)
                horizontalLineTo(22f)
            }
        }.build()
    }

    val Activity: ImageVector by lazy {
        ImageVector.Builder(
            name = "Activity", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 12f)
                horizontalLineTo(18f)
                lineTo(15f, 21f)
                lineTo(9f, 3f)
                lineTo(6f, 12f)
                horizontalLineTo(2f)
            }
        }.build()
    }

    /** Monitor (desktop) icon — used for landscape mode */
    val Monitor: ImageVector by lazy {
        ImageVector.Builder(
            name = "Monitor", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Screen rect
                moveTo(4f, 4f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 22f, 6f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, false, true, 20f, 18f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 2f, 16f)
                verticalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 4f, 4f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 22f)
                horizontalLineTo(16f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 18f)
                verticalLineTo(22f)
            }
        }.build()
    }
/** Sun icon — used for light theme mode */
    val Sun: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sun", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Circle
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, false, false, 12f, 21f)
                arcTo(9f, 9f, 0f, false, false, 12f, 3f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                // Rays
                moveTo(12f, 1f)
                verticalLineTo(3f)
                moveTo(12f, 21f)
                verticalLineTo(23f)
                moveTo(4.22f, 4.22f)
                lineTo(5.64f, 5.64f)
                moveTo(18.36f, 18.36f)
                lineTo(19.78f, 19.78f)
                moveTo(1f, 12f)
                horizontalLineTo(3f)
                moveTo(21f, 12f)
                horizontalLineTo(23f)
                moveTo(4.22f, 19.78f)
                lineTo(5.64f, 18.36f)
                moveTo(18.36f, 5.64f)
                lineTo(19.78f, 4.22f)
            }
        }.build()
    }

    /** Moon icon — used for dark theme mode */
    val Moon: ImageVector by lazy {
        ImageVector.Builder(
            name = "Moon", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, false, false, 21f, 12f)
                arcTo(9f, 9f, 0f, false, true, 12f, 3f)
                close()
            }
        }.build()
    }

    /** Smartphone icon — used for portrait mode */
    val Smartphone: ImageVector by lazy {
        ImageVector.Builder(
            name = "Smartphone", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 2f)
                horizontalLineTo(17f)
                arcTo(2f, 2f, 0f, false, true, 19f, 4f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 17f, 22f)
                horizontalLineTo(7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 20f)
                verticalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 7f, 2f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 18f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    /** RotateCw (clockwise rotation) icon */
    val RotateCw: ImageVector by lazy {
        ImageVector.Builder(
            name = "RotateCw", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 2f)
                verticalLineTo(8f)
                horizontalLineTo(15f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 8f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, false, true, 19.73f, 5.4f)
            }
        }.build()
    }

    /** Counter-clockwise rotate — the standard "undo / revert" glyph. */
    val RotateCcw: ImageVector by lazy {
        ImageVector.Builder(
            name = "RotateCcw", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 2f)
                verticalLineTo(8f)
                horizontalLineTo(9f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 6f, 5.3f)
                lineTo(3f, 8f)
            }
        }.build()
    }

    /** User (person) icon */
    val User: ImageVector by lazy {
        ImageVector.Builder(
            name = "User", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 21f)
                verticalLineTo(19f)
                arcTo(4f, 4f, 0f, false, false, 16f, 15f)
                horizontalLineTo(8f)
                arcTo(4f, 4f, 0f, false, false, 4f, 19f)
                verticalLineTo(21f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 11f)
                arcTo(4f, 4f, 0f, true, true, 12f, 3f)
                arcTo(4f, 4f, 0f, true, true, 12f, 11f)
                close()
            }
        }.build()
    }

    /** Lucide "users" — group of people (used in Admin). */
    val Users: ImageVector by lazy {
        ImageVector.Builder(
            name = "Users", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(16f, 21f); verticalLineTo(19f); arcTo(4f, 4f, 0f, false, false, 12f, 15f)
                horizontalLineTo(6f); arcTo(4f, 4f, 0f, false, false, 2f, 19f); verticalLineTo(21f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 11f); arcTo(4f, 4f, 0f, true, true, 9f, 3f); arcTo(4f, 4f, 0f, true, true, 9f, 11f); close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(22f, 21f); verticalLineTo(19f); arcTo(4f, 4f, 0f, false, false, 19f, 15.13f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(16f, 3.13f); arcTo(4f, 4f, 0f, false, true, 16f, 10.88f)
            }
        }.build()
    }

    /** CreditCard icon for Plans & Pricing row */
    val CreditCard: ImageVector by lazy {
        ImageVector.Builder(
            name = "CreditCard", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 4f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 22f, 6f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 20f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 2f, 18f)
                verticalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 4f, 4f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 10f)
                horizontalLineTo(22f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 14f)
                horizontalLineTo(10f)
            }
        }.build()
    }

    /** Paperclip (attachment / add files). */
    val Paperclip: ImageVector by lazy {
        ImageVector.Builder(
            name = "Paperclip", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21.44f, 11.05f)
                lineTo(12.25f, 20.24f)
                arcTo(5.86f, 5.86f, 0f, false, true, 4f, 12f)
                arcTo(5.86f, 5.86f, 0f, false, true, 12.25f, 3.76f)
                lineTo(20.69f, 12.19f)
                arcTo(3.84f, 3.84f, 0f, false, true, 12f, 20.44f)
                lineTo(5.32f, 13.76f)
            }
        }.build()
    }

    /** Bot / Agent icon. */
    val Bot: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bot", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8f)
                verticalLineTo(4f)
                horizontalLineTo(8f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8f)
                verticalLineTo(4f)
                horizontalLineTo(16f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 8f)
                arcTo(4f, 4f, 0f, false, true, 8f, 12f)
                verticalLineTo(16f)
                horizontalLineTo(16f)
                verticalLineTo(12f)
                arcTo(4f, 4f, 0f, false, true, 12f, 8f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 20f)
                horizontalLineTo(18f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 12f)
                horizontalLineTo(10.01f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14f, 12f)
                horizontalLineTo(14.01f)
            }
        }.build()
    }

    /** Diamond (skills). */
    val Diamond: ImageVector by lazy {
        ImageVector.Builder(
            name = "Diamond", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.7f, 10.3f)
                lineTo(12f, 21.3f)
                lineTo(21.3f, 10.3f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 21.3f)
                lineTo(2.7f, 10.3f)
                lineTo(6.35f, 3.3f)
                lineTo(17.65f, 3.3f)
                lineTo(21.3f, 10.3f)
            }
        }.build()
    }
    /** Key icon — full access / permission to everything. */
    val Key: ImageVector by lazy {
        ImageVector.Builder(
            name = "Key", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Key bow (circular top)
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 7f)
                arcTo(5f, 5f, 0f, true, true, 8f, 10.27f)
                close()
            }
            // Key shaft
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 10f)
                lineTo(20f, 18f)
            }
            // Key teeth
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 16f)
                lineTo(20f, 18f)
                lineTo(16f, 22f)
                lineTo(14f, 20f)
            }
        }.build()
    }

    /** File text (workflows / documents). */
    val FileText: ImageVector by lazy {
        ImageVector.Builder(
            name = "FileText", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14.5f, 2f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, false, 4f, 4f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, false, 6f, 22f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, false, 20f, 20f)
                verticalLineTo(8f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14f, 2f)
                verticalLineTo(8f)
                horizontalLineTo(20f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 13f)
                horizontalLineTo(16f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 17f)
                horizontalLineTo(16f)
            }
        }.build()
    }

    /** Simple Icons GitHub — clean monochrome 24x24. Tinted by Icon. */
    val Github: ImageVector by lazy {
        ImageVector.Builder(
            name = "Github", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 0.297f)
                curveToRelative(-6.63f, 0f, -12f, 5.373f, -12f, 12f)
                curveToRelative(0f, 5.303f, 3.438f, 9.8f, 8.205f, 11.385f)
                curveToRelative(0.6f, 0.113f, 0.82f, -0.258f, 0.82f, -0.577f)
                curveToRelative(0f, -0.285f, -0.01f, -1.04f, -0.015f, -2.04f)
                curveToRelative(-3.338f, 0.724f, -4.042f, -1.61f, -4.042f, -1.61f)
                curveTo(4.422f, 18.07f, 3.633f, 17.7f, 3.633f, 17.7f)
                curveToRelative(-1.087f, -0.744f, 0.084f, -0.729f, 0.084f, -0.729f)
                curveToRelative(1.205f, 0.084f, 1.838f, 1.236f, 1.838f, 1.236f)
                curveToRelative(1.07f, 1.835f, 2.809f, 1.305f, 3.495f, 0.998f)
                curveToRelative(0.108f, -0.776f, 0.417f, -1.305f, 0.76f, -1.605f)
                curveToRelative(-2.665f, -0.3f, -5.466f, -1.332f, -5.466f, -5.93f)
                curveToRelative(0f, -1.31f, 0.465f, -2.38f, 1.235f, -3.22f)
                curveToRelative(-0.135f, -0.303f, -0.54f, -1.523f, 0.105f, -3.176f)
                curveToRelative(0f, 0f, 1.005f, -0.322f, 3.3f, 1.23f)
                curveToRelative(0.96f, -0.267f, 1.98f, -0.399f, 3f, -0.405f)
                curveToRelative(1.02f, 0.006f, 2.04f, 0.138f, 3f, 0.405f)
                curveToRelative(2.28f, -1.552f, 3.285f, -1.23f, 3.285f, -1.23f)
                curveToRelative(0.645f, 1.653f, 0.24f, 2.873f, 0.12f, 3.176f)
                curveToRelative(0.765f, 0.84f, 1.23f, 1.91f, 1.23f, 3.22f)
                curveToRelative(0f, 4.61f, -2.805f, 5.625f, -5.475f, 5.92f)
                curveToRelative(0.42f, 0.36f, 0.81f, 1.096f, 0.81f, 2.22f)
                curveToRelative(0f, 1.606f, -0.015f, 2.896f, -0.015f, 3.286f)
                curveToRelative(0f, 0.315f, 0.21f, 0.69f, 0.825f, 0.57f)
                curveTo(20.565f, 22.092f, 24f, 17.592f, 24f, 12.297f)
                curveToRelative(0f, -6.627f, -5.373f, -12f, -12f, -12f)
            }
        }.build()
    }

    /** Lucide Folder icon */
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 20f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, false, 22f, 18f)
                verticalLineTo(8f)
                arcTo(2f, 2f, 0f, false, false, 20f, 6f)
                horizontalLineTo(16f)
                lineTo(14f, 4f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, false, 4f, 6f)
                close()
            }
        }.build()
    }

    /** Lucide Shield icon (stroke-based) */
    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shield", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 22f)
                curveToRelative(0f, 0f, 8f, -4f, 8f, -10f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(12f)
                curveToRelative(0f, 6f, 8f, 10f, 8f, 10f)
                close()
            }
        }.build()
    }
/** Lucide ShieldCheck icon — shield with a checkmark, ideal for permissions. */
    val ShieldCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheck", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Shield outline
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 22f)
                curveToRelative(0f, 0f, 8f, -4f, 8f, -10f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineTo(12f)
                curveToRelative(0f, 6f, 8f, 10f, 8f, 10f)
                close()
            }
            // Checkmark inside the shield
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(9f, 12f)
                lineToRelative(2f, 2f)
                lineToRelative(4f, -4f)
            }
        }.build()
    }

    /** Lock with key icon — full permission / access control */
    val LockKey: ImageVector by lazy {
        ImageVector.Builder(
            name = "LockKey", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Shackle (arched top of padlock)
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(8f, 11f)
                verticalLineTo(7f)
                arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
                verticalLineToRelative(4f)
            }
            // Lock body
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 11f)
                lineToRelative(0f, 8f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                lineToRelative(12f, 0f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                lineToRelative(0f, -8f)
                close()
            }
            // Keyhole circle
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(10.5f, 15.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -3f, 0f)
                close()
            }
            // Keyhole vertical slot
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 17f)
                verticalLineToRelative(2f)
            }
            // Small key beside/below the lock (angled)
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(17f, 19f)
                lineToRelative(3f, -3f)
                lineToRelative(-3f, 3f)
                moveTo(18f, 16f)
                lineToRelative(2f, -2f)
            }
        }.build()
    }

    /** Lucide GitBranch icon (stroke-based) */
    val GitBranch: ImageVector by lazy {
        ImageVector.Builder(
            name = "GitBranch", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(6f, 3f)
                verticalLineTo(15f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(18f, 9f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(6f, 21f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(18f, 9f)
                lineTo(9f, 15f)
            }
        }.build()
    }

    /** Lucide Search icon (magnifying glass) — used for search/grep/find actions. */
    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(19f, 11f)
                arcTo(8f, 8f, 0f, true, true, 3f, 11f)
                arcTo(8f, 8f, 0f, true, true, 19f, 11f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
            }
        }.build()
    }

    /** Lucide Terminal icon — used for shell/run/cloud-command actions. */
    val Terminal: ImageVector by lazy {
        ImageVector.Builder(
            name = "Terminal", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 17f)
                lineTo(10f, 11f)
                lineTo(4f, 5f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 19f)
                horizontalLineTo(20f)
            }
        }.build()
    }

    /** Lucide Eye icon — used for read/view/scan actions. */
    val Eye: ImageVector by lazy {
        ImageVector.Builder(
            name = "Eye", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(1f, 12f)
                curveTo(1f, 12f, 5f, 4f, 12f, 4f)
                curveTo(19f, 4f, 23f, 12f, 23f, 12f)
                curveTo(23f, 12f, 19f, 20f, 12f, 20f)
                curveTo(5f, 20f, 1f, 12f, 1f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(15f, 12f)
                arcTo(3f, 3f, 0f, true, true, 9f, 12f)
                arcTo(3f, 3f, 0f, true, true, 15f, 12f)
                close()
            }
        }.build()
    }

    /** Lucide Edit (pencil) icon — used for edit/write/create actions. */
    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Edit", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 20f)
                horizontalLineTo(21f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(16.5f, 3.5f)
                lineTo(19.5f, 6.5f)
                lineTo(8f, 18f)
                lineTo(4f, 19f)
                lineTo(5f, 15f)
                lineTo(16.5f, 3.5f)
                close()
            }
        }.build()
    }

    /** Lucide AlertTriangle icon — used for warnings/failed actions. */
    val AlertTriangle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AlertTriangle", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 3f)
                lineTo(22f, 20f)
                horizontalLineTo(2f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 9f)
                verticalLineTo(13f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 16.5f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    /** Lucide X icon — used for delete actions. */
    val X: ImageVector by lazy {
        ImageVector.Builder(
            name = "X", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
        }.build()
    }

    /** Lucide Download icon — used for download/save/pull actions. */
    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 14f)
                verticalLineTo(20f)
                horizontalLineTo(20f)
                verticalLineTo(14f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 3f)
                verticalLineTo(14f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(7f, 9f)
                lineTo(12f, 14f)
                lineTo(17f, 9f)
            }
        }.build()
    }

    /** Lucide MessageSquare icon — used for chat-mode activity. */
    val MessageSquare: ImageVector by lazy {
        ImageVector.Builder(
            name = "MessageSquare", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(21f, 15f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(7f)
                lineTo(3f, 21f)
                verticalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                close()
            }
        }.build()
    }

    /** Lucide Zap (lightning) icon — used for token usage. */
    val Zap: ImageVector by lazy {
        ImageVector.Builder(
            name = "Zap", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(13f, 2f)
                lineTo(3f, 14f)
                horizontalLineTo(12f)
                lineTo(11f, 22f)
                lineTo(21f, 10f)
                horizontalLineTo(12f)
                close()
            }
        }.build()
    }

    /** Lucide Square icon — filled square used for the Stop button (replaces X/cross). */
    val Square: ImageVector by lazy {
        ImageVector.Builder(
            name = "Square", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3f, 3f)
                horizontalLineTo(21f)
                verticalLineTo(21f)
                horizontalLineTo(3f)
                close()
            }
        }.build()
    }

    /** Lucide Calendar icon — used for the reset/period line. */
    val Calendar: ImageVector by lazy {
        ImageVector.Builder(
            name = "Calendar", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(5f, 4f)
                horizontalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(16f, 2f)
                verticalLineTo(6f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(8f, 2f)
                verticalLineTo(6f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 10f)
                horizontalLineTo(21f)
            }
        }.build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Camera", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Body: rounded rectangle with the raised lens housing on top
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(23f, 19f)
                curveTo(23f, 20.1f, 22.1f, 21f, 21f, 21f)
                lineTo(3f, 21f)
                curveTo(1.9f, 21f, 1f, 20.1f, 1f, 19f)
                lineTo(1f, 8f)
                curveTo(1f, 6.9f, 1.9f, 6f, 3f, 6f)
                lineTo(7f, 6f)
                lineTo(9f, 3f)
                lineTo(15f, 3f)
                lineTo(17f, 6f)
                lineTo(21f, 6f)
                curveTo(22.1f, 6f, 23f, 6.9f, 23f, 8f)
                close()
            }
            // Lens circle
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(16f, 13f)
                curveTo(16f, 15.21f, 14.21f, 17f, 12f, 17f)
                curveTo(9.79f, 17f, 8f, 15.21f, 8f, 13f)
                curveTo(8f, 10.79f, 9.79f, 9f, 12f, 9f)
                curveTo(14.21f, 9f, 16f, 10.79f, 16f, 13f)
                close()
            }
        }.build()
    }

    val File: ImageVector by lazy {
        ImageVector.Builder(
            name = "File", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Document body with a folded top-right corner.
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(14f, 2f)
                lineTo(6f, 2f)
                curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                lineTo(4f, 20f)
                curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                lineTo(18f, 22f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                lineTo(20f, 8f)
                close()
            }
            // Folded corner.
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(14f, 2f)
                lineTo(14f, 8f)
                lineTo(20f, 8f)
            }
        }.build()
    }

    val Sparkles: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sparkles", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Main four-point sparkle (concave star) — the Anthropic/Codex "skill" mark.
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(11f, 3f)
                lineTo(12.6f, 8.7f)
                lineTo(18.3f, 10.3f)
                lineTo(12.6f, 11.9f)
                lineTo(11f, 17.6f)
                lineTo(9.4f, 11.9f)
                lineTo(3.7f, 10.3f)
                lineTo(9.4f, 8.7f)
                close()
            }
            // Small secondary sparkle, top-right.
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(18.5f, 15f)
                lineTo(19.2f, 17.3f)
                lineTo(21.5f, 18f)
                lineTo(19.2f, 18.7f)
                lineTo(18.5f, 21f)
                lineTo(17.8f, 18.7f)
                lineTo(15.5f, 18f)
                lineTo(17.8f, 17.3f)
                close()
            }
        }.build()
    }

    val Plug: ImageVector by lazy {
        ImageVector.Builder(
            name = "Plug", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Cable
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 22f)
                verticalLineTo(17f)
            }
            // Prongs
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(9f, 8f)
                verticalLineTo(2f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(15f, 8f)
                verticalLineTo(2f)
            }
            // Socket body
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(18f, 8f)
                lineTo(18f, 13f)
                curveTo(18f, 15.21f, 16.21f, 17f, 14f, 17f)
                lineTo(10f, 17f)
                curveTo(7.79f, 17f, 6f, 15.21f, 6f, 13f)
                lineTo(6f, 8f)
                close()
            }
        }.build()
    }

    /**
     * Unified Skills mark (bookmark / open-book glyph) — used for EVERY skill row.
     * Paths from product SVG (stroke 1.5, round caps/joins).
     */
    val Skills: ImageVector by lazy {
        ImageVector.Builder(
            name = "Skills", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val stroke = 1.5f
            val paths = listOf(
                "M17.5055 2.01874C12.8289 2.83455 12 7.5 12 7.5V22C12 22 12.8867 17.1272 18.0004 16.5588C18.5493 16.4978 19 16.0576 19 15.5058V3.39309C19 2.5654 18.3216 1.87638 17.5055 2.01874Z",
                "M5.33333 5.00001C7.79379 4.99657 10.1685 5.88709 12 7.5V22C10.1685 20.3871 7.79379 19.4966 5.33333 19.5C3.77132 19.5 2.99032 19.5 2.64526 19.2792C2.4381 19.1466 2.35346 19.0619 2.22086 18.8547C2 18.5097 2 17.8941 2 16.6629V8.40322C2 6.97543 2 6.26154 2.54874 5.68286C3.09748 5.10418 3.65923 5.07432 4.78272 5.0146C4.965 5.00491 5.14858 5.00001 5.33333 5.00001Z",
                "M12 22.001C13.8315 20.3881 16.2062 19.4976 18.6667 19.501C20.2287 19.501 21.0097 19.501 21.3547 19.2802C21.5619 19.1476 21.6465 19.0629 21.7791 18.8558C22 18.5107 22 17.8951 22 16.6639V8.40424C22 6.97645 22 6.26256 21.4513 5.68388C20.9025 5.1052 20.1235 5.05972 19 5"
            )
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()
    }

    /** Alias — same unified skill glyph. */
    val SkillsNodes: ImageVector by lazy { Skills }

    /**
     * Access tab — keycard + fingerprint (admin access control).
     */
    val AccessCard: ImageVector by lazy {
        ImageVector.Builder(
            name = "AccessCard", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val s = 1.5f
            // Key teeth (right)
            listOf(
                "M15 5H19.5C20.4346 5 20.9019 5 21.25 5.20096C21.478 5.33261 21.6674 5.52197 21.799 5.75C22 6.09808 22 6.56538 22 7.5C22 8.43462 22 8.90192 21.799 9.25C21.6674 9.47803 21.478 9.66739 21.25 9.79904C20.9019 10 20.4346 10 19.5 10H15",
                "M13 14H19.5C20.4346 14 20.9019 14 21.25 14.201C21.478 14.3326 21.6674 14.522 21.799 14.75C22 15.0981 22 15.5654 22 16.5C22 17.4346 22 17.9019 21.799 18.25C21.6674 18.478 21.478 18.6674 21.25 18.799C20.9019 19 20.4346 19 19.5 19H13"
            ).forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = s,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
            // Key bow (circle)
            addPath(
                pathData = PathParser().parsePathString(
                    "M7 2C4.23858 2 2 4.23858 2 7C2 9.35903 3.6705 11.3846 5.69972 12.2712V19.5858C5.69972 19.5858 5.58579 21 7 21C8.41421 21 8.41421 21 8.41421 21C8.41421 21 9.05201 20.3622 9.05201 20.3622C9.60244 19.7056 9.64765 19.0695 9.19561 18.3417C8.79241 17.7024 8.79241 17.1261 9.19561 16.4867C9.27186 16.3851 9.75494 15.5819 9.76462 15.2332C9.32684 14.4045 9.29969 14.3638 9.02647 13.84C9 13.3739 9 11.584 9 11.584C11.0295 10.6974 12 8.6718 12 7C12 4.23858 9.76142 2 7 2Z"
                ).toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = s,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            // Center pin
            addPath(
                pathData = PathParser().parsePathString(
                    "M7.25 7H7M7.5 7C7.5 7.27614 7.27614 7.5 7 7.5C6.72386 7.5 6.5 7.27614 6.5 7C6.5 6.72386 6.72386 6.5 7 6.5C7.27614 6.5 7.5 6.72386 7.5 7Z"
                ).toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = s,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }

    /** Email / @ mark. */
    val AtSign: ImageVector by lazy {
        ImageVector.Builder(
            name = "AtSign", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M15.6 8.40033V12.9003C15.6 14.3915 16.8088 15.6003 18.3 15.6003C19.7912 15.6003 21 14.3915 21 12.9003V12C21 7.02944 16.9706 3 12 3C7.02944 3 3 7.02944 3 12C3 16.9706 7.02944 21 12 21C14.0265 21 15.8965 20.3302 17.4009 19.2M15.6 12.0003C15.6 13.9886 13.9882 15.6003 12 15.6003C10.0118 15.6003 8.4 13.9886 8.4 12.0003C8.4 10.0121 10.0118 8.40033 12 8.40033C13.9882 8.40033 15.6 10.0121 15.6 12.0003Z"
                ).toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }

    /** PDF file with folded corner + mark. */
    val FilePdf: ImageVector by lazy {
        ImageVector.Builder(
            name = "FilePdf", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, false, 4f, 4f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, false, 6f, 22f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, false, 20f, 20f)
                verticalLineTo(8f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(14f, 2f)
                verticalLineTo(8f)
                horizontalLineTo(20f)
            }
            // small "A" / text mark
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(9f, 17f)
                lineTo(12f, 11f)
                lineTo(15f, 17f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(10.2f, 15f)
                horizontalLineTo(13.8f)
            }
        }.build()
    }

    /** Pie chart — presentations. */
    val PieChart: ImageVector by lazy {
        ImageVector.Builder(
            name = "PieChart", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(21.21f, 15.89f)
                arcTo(10f, 10f, 0f, true, true, 8f, 2.83f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, false, false, 12f, 2f)
                verticalLineTo(12f)
                close()
            }
        }.build()
    }

    /** Table / spreadsheet grid. */
    val Table: ImageVector by lazy {
        ImageVector.Builder(
            name = "Table", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 5f)
                horizontalLineTo(21f)
                verticalLineTo(19f)
                horizontalLineTo(3f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 10f)
                horizontalLineTo(21f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 15f)
                horizontalLineTo(21f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(9f, 5f)
                verticalLineTo(19f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(15f, 5f)
                verticalLineTo(19f)
            }
        }.build()
    }

    /** Database cylinder — SQL. */
    val Database: ImageVector by lazy {
        ImageVector.Builder(
            name = "Database", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 5f)
                // ellipse approx for top
                moveTo(4f, 6f)
                arcTo(8f, 3f, 0f, false, true, 20f, 6f)
                arcTo(8f, 3f, 0f, false, true, 4f, 6f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 6f)
                verticalLineTo(18f)
                arcTo(8f, 3f, 0f, false, false, 20f, 18f)
                verticalLineTo(6f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 12f)
                arcTo(8f, 3f, 0f, false, false, 20f, 12f)
            }
        }.build()
    }

    /** Bar chart. */
    val BarChart: ImageVector by lazy {
        ImageVector.Builder(
            name = "BarChart", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 20f)
                verticalLineTo(10f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12f, 20f)
                verticalLineTo(4f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(20f, 20f)
                verticalLineTo(14f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(2f, 20f)
                horizontalLineTo(22f)
            }
        }.build()
    }

    /** Code brackets — fullstack / code-doc. */
    val Code: ImageVector by lazy {
        ImageVector.Builder(
            name = "Code", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(16f, 18f)
                lineTo(22f, 12f)
                lineTo(16f, 6f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(8f, 6f)
                lineTo(2f, 12f)
                lineTo(8f, 18f)
            }
        }.build()
    }

    /** Layout panel — frontend UI. */
    val Layout: ImageVector by lazy {
        ImageVector.Builder(
            name = "Layout", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 4f)
                horizontalLineTo(21f)
                verticalLineTo(20f)
                horizontalLineTo(3f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 10f)
                horizontalLineTo(21f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(9f, 10f)
                verticalLineTo(20f)
            }
        }.build()
    }

    /** Book open — documentation / resume. */
    val BookOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "BookOpen", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(2f, 6f)
                curveTo(2f, 6f, 5f, 4f, 8f, 4f)
                curveTo(11f, 4f, 12f, 6f, 12f, 6f)
                verticalLineTo(20f)
                curveTo(12f, 20f, 11f, 18f, 8f, 18f)
                curveTo(5f, 18f, 2f, 20f, 2f, 20f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(22f, 6f)
                curveTo(22f, 6f, 19f, 4f, 16f, 4f)
                curveTo(13f, 4f, 12f, 6f, 12f, 6f)
                verticalLineTo(20f)
                curveTo(12f, 20f, 13f, 18f, 16f, 18f)
                curveTo(19f, 18f, 22f, 20f, 22f, 20f)
                close()
            }
        }.build()
    }

    /** Film strip — video / shorts. */
    val Film: ImageVector by lazy {
        ImageVector.Builder(
            name = "Film", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(8f, 4f)
                verticalLineTo(20f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(16f, 4f)
                verticalLineTo(20f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 9f)
                horizontalLineTo(8f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 15f)
                horizontalLineTo(8f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(16f, 9f)
                horizontalLineTo(20f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(16f, 15f)
                horizontalLineTo(20f)
            }
        }.build()
    }

    /** Wand — skill creator. */
    val Wand: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wand", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(15f, 4f)
                verticalLineTo(2f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(15f, 16f)
                verticalLineTo(14f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(8f, 9f)
                horizontalLineTo(10f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(20f, 9f)
                horizontalLineTo(22f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(17.8f, 11.8f)
                lineTo(19f, 13f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(17.8f, 6.2f)
                lineTo(19f, 5f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12.2f, 11.8f)
                lineTo(11f, 13f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(12.2f, 6.2f)
                lineTo(11f, 5f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(4f, 21f)
                lineTo(13f, 12f)
            }
        }.build()
    }

    /** Image / icon-logo. */
    val ImageIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "ImageIcon", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 5f)
                horizontalLineTo(21f)
                verticalLineTo(19f)
                horizontalLineTo(3f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(8.5f, 11f)
                arcTo(1.5f, 1.5f, 0f, true, true, 8.5f, 8f)
                arcTo(1.5f, 1.5f, 0f, true, true, 8.5f, 11f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(21f, 15f)
                lineTo(16f, 10f)
                lineTo(5f, 19f)
            }
        }.build()
    }

    /** Trending up — SEO. */
    val TrendingUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "TrendingUp", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(3f, 17f)
                lineTo(9f, 11f)
                lineTo(13f, 15f)
                lineTo(21f, 7f)
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(14f, 7f)
                horizontalLineTo(21f)
                verticalLineTo(14f)
            }
        }.build()
    }

    /** Bookmarked page / reader. */
    val BookMarked: ImageVector by lazy {
        ImageVector.Builder(
            name = "BookMarked", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent)
            ) {
                moveTo(6f, 3f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 5f)
                verticalLineTo(21f)
                lineTo(12f, 17f)
                lineTo(4f, 21f)
                verticalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 6f, 3f)
                close()
            }
        }.build()
    }
}
