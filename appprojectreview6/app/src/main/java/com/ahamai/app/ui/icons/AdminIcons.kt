package com.ahamai.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Extra Lucide-style stroke icons used by the Admin dashboard.
 * Monochrome (tinted by [androidx.compose.material3.Icon]), no fills, 24x24.
 * Also includes filled custom icons like [FolderMultiple].
 */
object AdminIcons {

    private fun ImageVector.Builder.s(b: PathBuilder.() -> Unit) {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = b
        )
    }

    private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    val Users: ImageVector by lazy {
        icon("Users") {
            s { moveTo(16f, 21f); verticalLineToRelative(-2f); arcToRelative(4f, 4f, 0f, false, false, -4f, -4f); horizontalLineTo(6f); arcToRelative(4f, 4f, 0f, false, false, -4f, 4f); verticalLineToRelative(2f) }
            s { moveTo(13f, 7f); arcToRelative(4f, 4f, 0f, true, false, -8f, 0f); arcToRelative(4f, 4f, 0f, true, false, 8f, 0f) }
            s { moveTo(22f, 21f); verticalLineToRelative(-2f); arcToRelative(4f, 4f, 0f, false, false, -3f, -3.87f) }
            s { moveTo(16f, 3.13f); arcToRelative(4f, 4f, 0f, false, true, 0f, 7.75f) }
        }
    }

    val Activity: ImageVector by lazy {
        icon("Activity") {
            s { moveTo(22f, 12f); horizontalLineTo(18f); lineTo(15f, 21f); lineTo(9f, 3f); lineTo(6f, 12f); horizontalLineTo(2f) }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            s { moveTo(19f, 11f); arcToRelative(8f, 8f, 0f, true, false, -16f, 0f); arcToRelative(8f, 8f, 0f, true, false, 16f, 0f) }
            s { moveTo(21f, 21f); lineTo(16.65f, 16.65f) }
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            s { moveTo(12f, 2f); arcToRelative(3f, 3f, 0f, false, false, -3f, 3f); verticalLineTo(7f); horizontalLineTo(7f); arcToRelative(2f, 2f, 0f, false, false, -2f, 2f); verticalLineTo(19f); arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); horizontalLineTo(17f); arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); verticalLineTo(9f); arcToRelative(2f, 2f, 0f, false, false, -2f, -2f); horizontalLineTo(15f); verticalLineTo(5f); arcToRelative(3f, 3f, 0f, false, false, -3f, -3f); close() }
            s { moveTo(12f, 14f); moveToRelative(-1f, 0f); arcToRelative(1f, 1f, 0f, true, true, 2f, 0f); arcToRelative(1f, 1f, 0f, true, true, -2f, 0f) }
            s { moveTo(12f, 14f); verticalLineTo(17f) }
        }
    }

    val X: ImageVector by lazy {
        icon("X") {
            s { moveTo(18f, 6f); lineTo(6f, 18f) }
            s { moveTo(6f, 6f); lineTo(18f, 18f) }
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            s {
                moveTo(12f, 2f)
                lineTo(20f, 5f)
                verticalLineTo(11f)
                curveTo(20f, 16f, 16f, 20f, 12f, 22f)
                curveTo(8f, 20f, 4f, 16f, 4f, 11f)
                verticalLineTo(5f)
                close()
            }
        }
    }

    val FileText: ImageVector by lazy {
        icon("FileText") {
            s {
                moveTo(14f, 2f); horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(8f)
                close()
            }
            s { moveTo(14f, 2f); verticalLineTo(8f); horizontalLineTo(20f) }
            s { moveTo(16f, 13f); horizontalLineTo(8f) }
            s { moveTo(16f, 17f); horizontalLineTo(8f) }
        }
    }

    val AlertTriangle: ImageVector by lazy {
        icon("AlertTriangle") {
            s {
                moveTo(12f, 3f)
                lineTo(2f, 20f)
                horizontalLineTo(22f)
                close()
            }
            s { moveTo(12f, 9f); verticalLineTo(13f) }
            s { moveTo(12f, 17f); lineTo(12.01f, 17f) }
        }
    }

    /**
     * Fluent-style FolderMultiple icon — two folders stacked (filled, no stroke).
     * Tinted by [androidx.compose.material3.Icon] via fill = SolidColor(Color.Black).
     */

    /**
     * Bootstrap-style save icon — downward arrow into a floppy disk outline (16x16 viewport).
     * Tinted by [androidx.compose.material3.Icon] via fill = SolidColor(Color.Black).
     */
    /**
     * Bootstrap-style search icon — magnifying glass (16x16 viewport).
     * Tinted by [androidx.compose.material3.Icon] via fill = SolidColor(Color.Black).
     */
    /** Bootstrap arrow-90deg-down icon — MIT License. */
    val BootstrapArrow90degDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "BootstrapArrow90degDown", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 16f, viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.854f, 14.854f)
                arcToRelative(0.5f, 0.5f, 0f, false, true, -0.708f, 0f)
                lineToRelative(-4f, -4f)
                arcToRelative(0.5f, 0.5f, 0f, false, true, 0.708f, -0.708f)
                lineTo(4f, 13.293f)
                verticalLineTo(3.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 6.5f, 1f)
                horizontalLineToRelative(8f)
                arcToRelative(0.5f, 0.5f, 0f, false, true, 0f, 1f)
                horizontalLineToRelative(-8f)
                arcTo(1.5f, 1.5f, 0f, false, false, 5f, 3.5f)
                verticalLineToRelative(9.793f)
                lineToRelative(3.146f, -3.147f)
                arcToRelative(0.5f, 0.5f, 0f, false, true, 0.708f, 0.708f)
                close()
            }
        }.build()
    }

    /** Bootstrap arrow-90deg-up icon — MIT License. */
    val BootstrapArrow90degUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "BootstrapArrow90degUp", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 16f, viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.854f, 1.146f)
                arcToRelative(0.5f, 0.5f, 0f, false, false, -0.708f, 0f)
                lineToRelative(-4f, 4f)
                arcToRelative(0.5f, 0.5f, 0f, true, false, 0.708f, 0.708f)
                lineTo(4f, 2.707f)
                verticalLineTo(12.5f)
                arcTo(2.5f, 2.5f, 0f, false, false, 6.5f, 15f)
                horizontalLineToRelative(8f)
                arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, -1f)
                horizontalLineToRelative(-8f)
                arcTo(1.5f, 1.5f, 0f, false, true, 5f, 12.5f)
                verticalLineTo(2.707f)
                lineToRelative(3.146f, 3.147f)
                arcToRelative(0.5f, 0.5f, 0f, true, false, 0.708f, -0.708f)
                close()
            }
        }.build()
    }

    val BootstrapCopy: ImageVector by lazy {
        ImageVector.Builder(
            name = "BootstrapCopy", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 16f, viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineToRelative(8f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineToRelative(8f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                close()
                moveToRelative(2f, -1f)
                arcToRelative(1f, 1f, 0f, false, false, -1f, 1f)
                verticalLineToRelative(8f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, 1f)
                horizontalLineToRelative(8f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, -1f)
                verticalLineTo(2f)
                arcToRelative(1f, 1f, 0f, false, false, -1f, -1f)
                close()
                moveTo(2f, 5f)
                arcToRelative(1f, 1f, 0f, false, false, -1f, 1f)
                verticalLineToRelative(8f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, 1f)
                horizontalLineToRelative(8f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, -1f)
                verticalLineToRelative(-1f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(1f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(1f)
                close()
            }
        }.build()
    }

    val BootstrapSearch: ImageVector by lazy {
        ImageVector.Builder(
            name = "BootstrapSearch", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 16f, viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.742f, 10.344f)
                arcToRelative(6.5f, 6.5f, 0f, true, false, -1.397f, 1.398f)
                horizontalLineToRelative(-0.001f)
                quadToRelative(0.044f, 0.06f, 0.098f, 0.115f)
                lineToRelative(3.85f, 3.85f)
                arcToRelative(1f, 1f, 0f, false, false, 1.415f, -1.414f)
                lineToRelative(-3.85f, -3.85f)
                arcToRelative(1f, 1f, 0f, false, false, -0.115f, -0.1f)
                close()
                moveTo(12f, 6.5f)
                arcToRelative(5.5f, 5.5f, 0f, true, true, -11f, 0f)
                arcToRelative(5.5f, 5.5f, 0f, false, true, 11f, 0f)
            }
        }.build()
    }

    val BootstrapSave: ImageVector by lazy {
        ImageVector.Builder(
            name = "BootstrapSave", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 16f, viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 1f)
                arcTo(1f, 1f, 0f, false, false, 1f, 2f)
                verticalLineTo(14f)
                arcTo(1f, 1f, 0f, false, false, 2f, 15f)
                horizontalLineTo(14f)
                arcTo(1f, 1f, 0f, false, false, 15f, 14f)
                verticalLineTo(2f)
                arcTo(1f, 1f, 0f, false, false, 14f, 1f)
                horizontalLineTo(9.5f)
                arcTo(1f, 1f, 0f, false, false, 8.5f, 2f)
                verticalLineTo(9.293f)
                lineTo(11.146f, 6.646f)
                arcTo(0.5f, 0.5f, 0f, false, true, 11.854f, 7.354f)
                lineTo(8.354f, 10.854f)
                arcTo(0.5f, 0.5f, 0f, false, true, 7.646f, 10.854f)
                lineTo(4.146f, 7.354f)
                arcTo(0.5f, 0.5f, 0f, true, true, 4.854f, 6.646f)
                lineTo(7.5f, 9.293f)
                verticalLineTo(2f)
                arcTo(2f, 2f, 0f, false, true, 9.5f, 0f)
                horizontalLineTo(14f)
                arcTo(2f, 2f, 0f, false, true, 16f, 2f)
                verticalLineTo(14f)
                arcTo(2f, 2f, 0f, false, true, 14f, 16f)
                horizontalLineTo(2f)
                arcTo(2f, 2f, 0f, false, true, 0f, 14f)
                verticalLineTo(2f)
                arcTo(2f, 2f, 0f, false, true, 2f, 0f)
                horizontalLineTo(4.5f)
                arcTo(0.5f, 0.5f, 0f, false, true, 4.5f, 1f)
                close()
            }
        }.build()
    }
    /**
     * Fluent UI System Icons — Chat bubble (filled, 32x32 viewport scaled to 24dp).
     * MIT License — Copyright (c) 2020 Microsoft Corporation.
     */
    val FluentChat: ImageVector by lazy {
        ImageVector.Builder(
            name = "FluentChat", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 32f, viewportHeight = 32f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1.99902f)
                curveTo(8.268f, 1.99902f, 2f, 8.26702f, 2f, 15.999f)
                curveTo(2f, 18.369f, 2.59f, 20.604f, 3.631f, 22.562f)
                lineTo(2.059f, 28.089f)
                curveTo(1.738f, 29.219f, 2.782f, 30.263f, 3.912f, 29.942f)
                lineTo(9.44f, 28.37f)
                curveTo(11.398f, 29.41f, 13.631f, 29.999f, 16f, 29.999f)
                curveTo(23.732f, 29.999f, 30f, 23.731f, 30f, 15.999f)
                curveTo(30f, 8.26702f, 23.732f, 1.99902f, 16f, 1.99902f)
                close()
                moveTo(16f, 28.999f)
                curveTo(13.877f, 28.999f, 11.771f, 28.477f, 9.91f, 27.487f)
                lineTo(9.555f, 27.298f)
                lineTo(3.639f, 28.981f)
                curveTo(3.59f, 28.995f, 3.542f, 29.002f, 3.495f, 29.002f)
                curveTo(3.344f, 29.002f, 3.197f, 28.926f, 3.1f, 28.799f)
                curveTo(3.033f, 28.71f, 2.964f, 28.562f, 3.021f, 28.363f)
                lineTo(4.703f, 22.448f)
                lineTo(4.514f, 22.093f)
                curveTo(3.524f, 20.231f, 3f, 18.123f, 3f, 16f)
                curveTo(3f, 8.83202f, 8.832f, 3.00002f, 16f, 3.00002f)
                curveTo(23.168f, 3.00002f, 29f, 8.83202f, 29f, 16f)
                curveTo(29f, 23.168f, 23.168f, 28.999f, 16f, 28.999f)
                close()
                moveTo(21.5f, 13f)
                horizontalLineTo(10.5f)
                curveTo(10.224f, 13f, 10f, 12.776f, 10f, 12.5f)
                curveTo(10f, 12.224f, 10.224f, 12f, 10.5f, 12f)
                horizontalLineTo(21.5f)
                curveTo(21.776f, 12f, 22f, 12.224f, 22f, 12.5f)
                curveTo(22f, 12.776f, 21.776f, 13f, 21.5f, 13f)
                close()
                moveTo(17.5f, 19f)
                horizontalLineTo(10.5f)
                curveTo(10.224f, 19f, 10f, 18.776f, 10f, 18.5f)
                curveTo(10f, 18.224f, 10.224f, 18f, 10.5f, 18f)
                horizontalLineTo(17.5f)
                curveTo(17.776f, 18f, 18f, 18.224f, 18f, 18.5f)
                curveTo(18f, 18.776f, 17.776f, 19f, 17.5f, 19f)
                close()
            }
        }.build()
    }

    /** Material Symbols — Arrow Back (iOS-style chevron, 960x960 viewport). Apache 2.0. */
    val ArrowBackIos: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBackIos", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 960f, viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(400f, 880f)
                lineTo(0f, 480f)
                lineToRelative(400f, -400f)
                lineToRelative(71f, 71f)
                lineToRelative(-329f, 329f)
                lineToRelative(329f, 329f)
                lineToRelative(-71f, 71f)
                close()
            }
        }.build()
    }

    /** Bootstrap FileCode icon (16f viewport, filled) — MIT License. */
    val BootstrapFileCode: ImageVector by lazy {
        ImageVector.Builder(
            name = "BootstrapFileCode", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 16f, viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.646f, 5.646f)
                arcToRelative(0.5f, 0.5f, 0f, true, true, 0.708f, 0.708f)
                lineTo(5.707f, 8f)
                lineToRelative(1.647f, 1.646f)
                arcToRelative(0.5f, 0.5f, 0f, false, true, -0.708f, 0.708f)
                lineToRelative(-2f, -2f)
                arcToRelative(0.5f, 0.5f, 0f, false, true, 0f, -0.708f)
                close()
                moveToRelative(2.708f, 0f)
                arcToRelative(0.5f, 0.5f, 0f, true, false, -0.708f, 0.708f)
                lineTo(10.293f, 8f)
                lineTo(8.646f, 9.646f)
                arcToRelative(0.5f, 0.5f, 0f, false, false, 0.708f, 0.708f)
                lineToRelative(2f, -2f)
                arcToRelative(0.5f, 0.5f, 0f, false, false, 0f, -0.708f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineToRelative(8f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineToRelative(12f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                close()
                moveToRelative(10f, -1f)
                horizontalLineTo(4f)
                arcToRelative(1f, 1f, 0f, false, false, -1f, 1f)
                verticalLineToRelative(12f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, 1f)
                horizontalLineToRelative(8f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, -1f)
                verticalLineTo(2f)
                arcToRelative(1f, 1f, 0f, false, false, -1f, -1f)
            }
        }.build()
    }

    /** Fluent-style Document icon — document page with fold corner (filled, no stroke). */
    val FluentDocument: ImageVector by lazy {
        ImageVector.Builder(
            name = "FluentDocument", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.1046f, 22f, 20f, 21.1046f, 20f, 20f)
                verticalLineTo(9f)
                lineTo(15f, 7f)
                lineTo(13f, 2f)
                horizontalLineTo(6f)
                curveTo(4.89543f, 2f, 4f, 2.89543f, 4f, 4f)
                verticalLineTo(20f)
                curveTo(4f, 21.1046f, 4.89543f, 22f, 6f, 22f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 7.5f)
                verticalLineTo(2f)
                lineTo(20f, 9f)
                horizontalLineTo(14.5f)
                curveTo(13.6716f, 9f, 13f, 8.32843f, 13f, 7.5f)
                close()
            }
        }.build()
    }

    /** Custom PDF icon matching the rich SVG design — document page with fold corner, red header banner, and
     *  "PDF"-style lettering. 32×32 viewport, filled paths with their own colors (no stroke). Use
     *  `Icon(pdfIcon, tint = Color.Unspecified)` to preserve the full-color appearance. */
    val PdfIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "PdfIcon", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 32f, viewportHeight = 32f
        ).apply {
            // Background document shape (gray)
            path(fill = SolidColor(Color(0xFF909090))) {
                moveTo(24.1f, 2.072f)
                lineTo(29.664f, 7.872f)
                verticalLineTo(29.928f)
                horizontalLineTo(8.879f)
                verticalLineTo(30.0f)
                horizontalLineTo(29.735f)
                verticalLineTo(7.945f)
                lineTo(24.1f, 2.072f)
            }
            // White inner document
            path(fill = SolidColor(Color(0xFFF4F4F4))) {
                moveTo(24.031f, 2.0f)
                horizontalLineTo(8.808f)
                verticalLineTo(29.928f)
                horizontalLineTo(29.664f)
                verticalLineTo(7.873f)
                lineTo(24.03f, 2.0f)
            }
            // Fold corner (gray)
            path(fill = SolidColor(Color(0xFF909090))) {
                moveTo(23.954f, 2.077f)
                verticalLineTo(7.95f)
                horizontalLineTo(29.587f)
                lineTo(23.954f, 2.077f)
                close()
            }
            // Fold corner highlight (white)
            path(fill = SolidColor(Color(0xFFF4F4F4))) {
                moveTo(24.031f, 2.0f)
                verticalLineTo(7.873f)
                horizontalLineTo(29.664f)
                lineTo(24.031f, 2.0f)
                close()
            }
            // Tab strip at top (medium gray)
            path(fill = SolidColor(Color(0xFF7A7B7C))) {
                moveTo(8.655f, 3.5f)
                horizontalLineTo(2.265f)
                verticalLineTo(10.327f)
                horizontalLineTo(22.365f)
                verticalLineTo(3.5f)
                horizontalLineTo(8.655f)
            }
            // Red banner/header strip
            path(fill = SolidColor(Color(0xFFDD2025))) {
                moveTo(22.472f, 10.211f)
                horizontalLineTo(2.395f)
                verticalLineTo(3.379f)
                horizontalLineTo(22.472f)
                verticalLineTo(10.211f)
            }
            // "PDF" lettering text (dark gray) — approximated from the SVG
            path(fill = SolidColor(Color(0xFF464648))) {
                moveTo(9.052f, 4.534f)
                horizontalLineTo(7.745f)
                verticalLineTo(9.334f)
                horizontalLineTo(8.773f)
                verticalLineTo(7.715f)
                lineTo(9.0f, 7.728f)
                // Letter P stem and bowl
                lineTo(9.647f, 7.611f)
                lineTo(10.14f, 7.32f)
                lineTo(10.473f, 6.866f)
                lineTo(10.578f, 5.958f)
                lineTo(10.464f, 5.314f)
                lineTo(9.777f, 4.664f)
                lineTo(9.052f, 4.534f)
                close()
            }
            // Letter P detail
            path(fill = SolidColor(Color(0xFF464648))) {
                moveTo(8.863f, 6.828f)
                horizontalLineTo(8.774f)
                verticalLineTo(5.348f)
                horizontalLineTo(8.967f)
                lineTo(9.426f, 5.529f)
                lineTo(9.609f, 6.087f)
                lineTo(9.387f, 6.713f)
                lineTo(8.863f, 6.828f)
                close()
            }
            // Letter D
            path(fill = SolidColor(Color(0xFF464648))) {
                moveTo(12.534f, 4.522f)
                lineTo(12.0f, 4.538f)
                horizontalLineTo(11.22f)
                verticalLineTo(9.338f)
                horizontalLineTo(12.138f)
                lineTo(13.166f, 9.163f)
                lineTo(13.846f, 8.672f)
                lineTo(14.219f, 7.923f)
                lineTo(14.333f, 6.974f)
                lineTo(14.246f, 5.847f)
                lineTo(13.846f, 5.114f)
                lineTo(13.311f, 4.714f)
                lineTo(12.762f, 4.536f)
                lineTo(12.534f, 4.522f)
                close()
            }
            // Letter D detail
            path(fill = SolidColor(Color(0xFF464648))) {
                moveTo(12.352f, 8.459f)
                horizontalLineTo(12.252f)
                verticalLineTo(5.392f)
                horizontalLineTo(12.265f)
                lineTo(12.865f, 5.499f)
                lineTo(13.189f, 5.899f)
                lineTo(13.331f, 6.425f)
                lineTo(13.331f, 6.974f)
                lineTo(13.298f, 7.487f)
                lineTo(13.129f, 7.987f)
                lineTo(12.766f, 8.347f)
                lineTo(12.352f, 8.459f)
                close()
            }
            // Letter F
            path(fill = SolidColor(Color(0xFF464648))) {
                moveTo(17.432f, 4.544f)
                horizontalLineTo(15.0f)
                verticalLineTo(9.344f)
                horizontalLineTo(16.028f)
                verticalLineTo(7.434f)
                horizontalLineTo(17.328f)
                verticalLineTo(6.542f)
                horizontalLineTo(16.028f)
                verticalLineTo(5.432f)
                horizontalLineTo(17.428f)
                verticalLineTo(4.544f)
            }
            // Red banner white "PDF" letters (simplified)
            path(fill = SolidColor(Color(0xFFFFFFFF))) {
                moveTo(8.975f, 4.457f)
                horizontalLineTo(7.668f)
                verticalLineTo(9.257f)
                horizontalLineTo(8.7f)
                verticalLineTo(7.639f)
                lineTo(8.928f, 7.652f)
                lineTo(9.575f, 7.535f)
                lineTo(10.068f, 7.244f)
                lineTo(10.4f, 6.79f)
                lineTo(10.505f, 5.882f)
                lineTo(10.391f, 5.238f)
                lineTo(9.704f, 4.588f)
                lineTo(8.975f, 4.457f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFFFFFF))) {
                moveTo(8.786f, 6.751f)
                horizontalLineTo(8.697f)
                verticalLineTo(5.271f)
                horizontalLineTo(8.891f)
                lineTo(9.35f, 5.452f)
                lineTo(9.533f, 6.01f)
                lineTo(9.311f, 6.636f)
                lineTo(8.786f, 6.751f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFFFFFF))) {
                moveTo(12.456f, 4.445f)
                lineTo(12.221f, 4.451f)
                horizontalLineTo(11.441f)
                verticalLineTo(9.251f)
                horizontalLineTo(12.359f)
                lineTo(13.387f, 9.076f)
                lineTo(14.067f, 8.585f)
                lineTo(14.44f, 7.836f)
                lineTo(14.554f, 6.887f)
                lineTo(14.467f, 5.76f)
                lineTo(14.067f, 5.027f)
                lineTo(13.532f, 4.627f)
                lineTo(12.983f, 4.449f)
                lineTo(12.456f, 4.445f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFFFFFF))) {
                moveTo(12.274f, 8.372f)
                horizontalLineTo(12.174f)
                verticalLineTo(5.315f)
                horizontalLineTo(12.187f)
                lineTo(12.787f, 5.422f)
                lineTo(13.111f, 5.822f)
                lineTo(13.253f, 6.348f)
                lineTo(13.253f, 6.897f)
                lineTo(13.22f, 7.41f)
                lineTo(13.051f, 7.91f)
                lineTo(12.688f, 8.27f)
                lineTo(12.274f, 8.372f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFFFFFF))) {
                moveTo(17.351f, 4.457f)
                horizontalLineTo(14.921f)
                verticalLineTo(9.257f)
                horizontalLineTo(15.949f)
                verticalLineTo(7.357f)
                horizontalLineTo(17.249f)
                verticalLineTo(6.465f)
                horizontalLineTo(15.949f)
                verticalLineTo(5.353f)
                horizontalLineTo(17.349f)
                verticalLineTo(4.457f)
            }
        }.build()
    }

    val FolderMultiple: ImageVector by lazy {
        icon("FolderMultiple") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.4932f, 8.50781f)
                curveTo(21.3991f, 9.0846f, 22.001f, 10.0964f, 22.001f, 11.25f)
                verticalLineTo(15.25f)
                curveTo(22.001f, 18.4256f, 19.4266f, 21f, 16.251f, 21f)
                horizontalLineTo(7.75098f)
                curveTo(6.59723f, 21f, 5.58455f, 20.3984f, 5.00781f, 19.4922f)
                curveTo(5.08831f, 19.4967f, 5.16935f, 19.5f, 5.25098f, 19.5f)
                horizontalLineTo(16.251f)
                curveTo(18.5982f, 19.5f, 20.501f, 17.5972f, 20.501f, 15.25f)
                verticalLineTo(8.75f)
                curveTo(20.501f, 8.6687f, 20.4977f, 8.58799f, 20.4932f, 8.50781f)
                close()
                moveTo(7.40625f, 3f)
                curveTo(7.97766f, 3.00012f, 8.5316f, 3.21748f, 8.94922f, 3.61816f)
                curveTo(9.4267f, 4.0763f, 10.2218f, 4.85447f, 10.8252f, 5.5f)
                horizontalLineTo(16.251f)
                curveTo(18.0463f, 5.5f, 19.5f, 6.9555f, 19.5f, 8.75f)
                verticalLineTo(15.25f)
                curveTo(19.5f, 17.0449f, 18.0449f, 18.5f, 16.25f, 18.5f)
                horizontalLineTo(5.25f)
                curveTo(3.45507f, 18.5f, 2f, 17.0449f, 2f, 15.25f)
                verticalLineTo(6.25f)
                curveTo(2f, 4.45507f, 3.45507f, 3f, 5.25f, 3f)
                horizontalLineTo(7.40625f)
                close()
                moveTo(10.8252f, 7f)
                curveTo(10.2218f, 7.64553f, 9.4267f, 8.4237f, 8.94922f, 8.88184f)
                curveTo(8.5316f, 9.28252f, 7.97766f, 9.49988f, 7.40625f, 9.5f)
                horizontalLineTo(3.5f)
                verticalLineTo(15.25f)
                curveTo(3.5f, 16.2165f, 4.2835f, 17f, 5.25f, 17f)
                horizontalLineTo(16.25f)
                curveTo(17.2165f, 17f, 18f, 16.2165f, 18f, 15.25f)
                verticalLineTo(8.75f)
                curveTo(18f, 7.78307f, 17.217f, 7f, 16.251f, 7f)
                horizontalLineTo(10.8252f)
                close()
                moveTo(5.25f, 4.5f)
                curveTo(4.2835f, 4.5f, 3.5f, 5.2835f, 3.5f, 6.25f)
                verticalLineTo(8f)
                horizontalLineTo(7.40625f)
                curveTo(7.5978f, 7.99988f, 7.77714f, 7.92729f, 7.91016f, 7.7998f)
                curveTo(8.32148f, 7.40515f, 8.94598f, 6.79809f, 9.47363f, 6.25f)
                curveTo(8.94598f, 5.70191f, 8.32148f, 5.09485f, 7.91016f, 4.7002f)
                curveTo(7.77714f, 4.57271f, 7.5978f, 4.50012f, 7.40625f, 4.5f)
                horizontalLineTo(5.25f)
                close()
            }
        }
    }

    /**
     * iOS-style horizontal ellipsis (three dots) — SF Symbol “ellipsis” feel.
     * Use on a white/circular chrome button for Agent overflow.
     */
    val Ellipsis: ImageVector by lazy {
        ImageVector.Builder(
            name = "Ellipsis", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Three filled circles in a row (iOS SF Symbols ellipsis)
            fun ImageVector.Builder.dot(cx: Float) {
                addPath(
                    pathData = PathParser().parsePathString(
                        "M${cx - 1.35f} 12a1.35 1.35 0 1 1 2.7 0a1.35 1.35 0 1 1 -2.7 0"
                    ).toNodes(),
                    fill = SolidColor(Color.Black)
                )
            }
            dot(6.5f)
            dot(12f)
            dot(17.5f)
        }.build()
    }

    /**
     * Archive box — stroke 2, matches context-menu reference SVG
     * (rounded rect + lid line + small center slot).
     */
    val ContextArchive: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContextArchive", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val stroke = 2f
            // Rounded rect body (x=3 y=4 w=18 h=16 rx=2 — matches reference SVG)
            addPath(
                pathData = PathParser().parsePathString(
                    "M5 4H19C20.1046 4 21 4.89543 21 6V18C21 19.1046 20.1046 20 19 20H5C3.89543 20 3 19.1046 3 18V6C3 4.89543 3.89543 4 5 4Z"
                ).toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            // Lid line y=10
            addPath(
                pathData = PathParser().parsePathString("M3 10H21").toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            // Center slot
            addPath(
                pathData = PathParser().parsePathString("M10 14H14").toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }

    /**
     * All tasks — pen/cursor + orbit arcs (stroke 1.5).
     * Used next to the "All tasks" filter label on Agent home.
     */
    val AllTasks: ImageVector by lazy {
        ImageVector.Builder(
            name = "AllTasks", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val stroke = 1.5f
            val paths = listOf(
                "M10 14L20 4",
                "M5.24299 15.1543C4.7142 15.68 4.15657 16.232 3.66853 16.7143C3.23749 17.1402 3.02197 17.3532 3.00283 17.5346C2.98465 17.7068 3.05493 17.8761 3.19001 17.9854C3.3323 18.1007 3.63618 18.1007 4.24396 18.1007C4.80191 18.1007 5.08089 18.1007 5.29552 18.2051C5.50131 18.3052 5.66771 18.4705 5.76847 18.675C5.87356 18.8883 5.87358 19.1705 5.87362 19.7349C5.87367 20.3641 5.87369 20.6786 5.9997 20.8231C6.10363 20.9423 6.25695 21.0073 6.41543 20.9994C6.60759 20.9897 6.83179 20.7762 7.2802 20.349L8.74318 18.9552C9.3558 18.3716 9.66211 18.0798 9.82722 17.7045C9.83213 17.6933 9.83693 17.6821 9.84164 17.6709C10 17.2927 10 16.8711 10 16.0277V15.9499C10 15.0807 10 14.6461 9.75179 14.3582C9.71731 14.3182 9.67978 14.2809 9.63954 14.2466C9.34982 14 8.91248 14 8.03779 14C7.27234 14 6.88961 14 6.54321 14.1274C6.49316 14.1458 6.4449 14.1656 6.39642 14.1878C6.0609 14.3412 5.78826 14.6123 5.24299 15.1543Z",
                "M17 3.22126C17 3.22126 20.2808 2.72341 20.7787 3.22129C21.2766 3.71917 20.7787 7 20.7787 7",
                "M18.2396 11C19.0904 13.2868 19.0543 15.8774 18.1635 18.1685C17.617 19.5741 17.3438 20.2769 16.2839 20.4999C15.224 20.7228 14.5402 20.0391 13.1728 18.6718M5.32864 10.8285C3.95727 9.45733 3.27159 8.77172 3.48591 7.71854C3.70024 6.66535 4.4056 6.38049 5.81632 5.81077C8.11856 4.88101 10.7129 4.8173 13 5.68707"
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

    /**
     * Import project — folder with inbound arrow (stroke 1.5).
     * Matches provided SVG: folder outline + right-side import arrow.
     */
    val ImportProject: ImageVector by lazy {
        ImageVector.Builder(
            name = "ImportProject", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val stroke = 1.5f
            val paths = listOf(
                "M17 21C16.3932 20.4102 14 18.8403 14 18C14 17.1597 16.3932 15.5898 17 15M15 18H22",
                "M12 21C7.28595 21 4.92893 21 3.46447 19.5355C2 18.0711 2 15.714 2 11V7.94427C2 6.1278 2 5.21956 2.38032 4.53806C2.65142 4.05227 3.05227 3.65142 3.53806 3.38032C4.21956 3 5.1278 3 6.94427 3C8.10802 3 8.6899 3 9.19926 3.19101C10.3622 3.62712 10.8418 4.68358 11.3666 5.73313L12 7M8 7H16.75C18.8567 7 19.91 7 20.6667 7.50559C20.9943 7.72447 21.2755 8.00572 21.4944 8.33329C21.9796 9.05942 21.9992 10.0588 22 12V14"
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

    /** File archive icon: document with a zipper/fold closure. */
    val ZipIcon: ImageVector by lazy {
        icon("ZipIcon") {
            s {
                // Document body with folded corner
                moveTo(20f, 13f)
                verticalLineTo(10.6569f)
                curveTo(20f, 9.83935f, 20f, 9.4306f, 19.8478f, 9.06306f)
                curveTo(19.6955f, 8.69552f, 19.4065f, 8.40649f, 18.8284f, 7.82843f)
                lineTo(14.0919f, 3.09188f)
                curveTo(13.593f, 2.593f, 13.3436f, 2.34355f, 13.0345f, 2.19575f)
                curveTo(12.9702f, 2.165f, 12.9044f, 2.13772f, 12.8372f, 2.11401f)
                curveTo(12.5141f, 2f, 12.1614f, 2f, 11.4558f, 2f)
                curveTo(8.21082f, 2f, 6.58831f, 2f, 5.48933f, 2.88607f)
                curveTo(5.26731f, 3.06508f, 5.06508f, 3.26731f, 4.88607f, 3.48933f)
                curveTo(4f, 4.58831f, 4f, 6.21082f, 4f, 9.45584f)
                verticalLineTo(13f)
                moveTo(13f, 2.5f)
                verticalLineTo(3f)
                curveTo(13f, 5.82843f, 13f, 7.24264f, 13.8787f, 8.12132f)
                curveTo(14.7574f, 9f, 16.1716f, 9f, 19f, 9f)
                horizontalLineTo(19.5f)
            }
            s {
                // Zipper teeth — left
                moveTo(11f, 16f)
                horizontalLineTo(12f)
                moveTo(12f, 22f)
                horizontalLineTo(11f)
            }
            s {
                // Zipper teeth — right
                moveTo(12f, 16f)
                horizontalLineTo(13f)
                moveTo(12f, 22f)
                horizontalLineTo(13f)
            }
            s {
                // Vertical zipper line
                moveTo(12f, 16f)
                verticalLineTo(22f)
            }
            s {
                // Right pull tab housing
                moveTo(15.5f, 22f)
                verticalLineTo(16f)
                horizontalLineTo(17.3618f)
                curveTo(18.0675f, 16f, 18.7977f, 16.3516f, 18.9492f, 17.0408f)
                curveTo(19.0128f, 17.33f, 19.0109f, 17.6038f, 18.9488f, 17.8923f)
                curveTo(18.7936f, 18.6138f, 18.0392f, 19f, 17.3012f, 19f)
                horizontalLineTo(16f)
            }
            s {
                // Left pull tab housing
                moveTo(5.00003f, 16f)
                horizontalLineTo(8.2f)
                curveTo(8.36569f, 16f, 8.5f, 16.1343f, 8.5f, 16.3f)
                verticalLineTo(16.4054f)
                curveTo(8.5f, 16.467f, 8.48107f, 16.527f, 8.44577f, 16.5775f)
                lineTo(5.06872f, 21.4018f)
                curveTo(5.02399f, 21.4657f, 5f, 21.5418f, 5f, 21.6198f)
                curveTo(5f, 21.8298f, 5.17021f, 22f, 5.38016f, 22f)
                horizontalLineTo(8.32349f)
            }
        }
    }
/** Excel file icon: document with zipper/fold closure (same shape as ZipIcon, distinct tint). */
    val ExcelIcon: ImageVector by lazy {
        icon("ExcelIcon") {
            s {
                // Document body with folded corner
                moveTo(20f, 13f)
                verticalLineTo(10.6569f)
                curveTo(20f, 9.83935f, 20f, 9.4306f, 19.8478f, 9.06306f)
                curveTo(19.6955f, 8.69552f, 19.4065f, 8.40649f, 18.8284f, 7.82843f)
                lineTo(14.0919f, 3.09188f)
                curveTo(13.593f, 2.593f, 13.3436f, 2.34355f, 13.0345f, 2.19575f)
                curveTo(12.9702f, 2.165f, 12.9044f, 2.13772f, 12.8372f, 2.11401f)
                curveTo(12.5141f, 2f, 12.1614f, 2f, 11.4558f, 2f)
                curveTo(8.21082f, 2f, 6.58831f, 2f, 5.48933f, 2.88607f)
                curveTo(5.26731f, 3.06508f, 5.06508f, 3.26731f, 4.88607f, 3.48933f)
                curveTo(4f, 4.58831f, 4f, 6.21082f, 4f, 9.45584f)
                verticalLineTo(13f)
                moveTo(13f, 2.5f)
                verticalLineTo(3f)
                curveTo(13f, 5.82843f, 13f, 7.24264f, 13.8787f, 8.12132f)
                curveTo(14.7574f, 9f, 16.1716f, 9f, 19f, 9f)
                horizontalLineTo(19.5f)
            }
            s {
                moveTo(11f, 16f)
                horizontalLineTo(12f)
                moveTo(12f, 22f)
                horizontalLineTo(11f)
            }
            s {
                moveTo(12f, 16f)
                horizontalLineTo(13f)
                moveTo(12f, 22f)
                horizontalLineTo(13f)
            }
            s {
                moveTo(12f, 16f)
                verticalLineTo(22f)
            }
            s {
                moveTo(15.5f, 22f)
                verticalLineTo(16f)
                horizontalLineTo(17.3618f)
                curveTo(18.0675f, 16f, 18.7977f, 16.3516f, 18.9492f, 17.0408f)
                curveTo(19.0128f, 17.33f, 19.0109f, 17.6038f, 18.9488f, 17.8923f)
                curveTo(18.7936f, 18.6138f, 18.0392f, 19f, 17.3012f, 19f)
                horizontalLineTo(16f)
            }
            s {
                moveTo(5.00003f, 16f)
                horizontalLineTo(8.2f)
                curveTo(8.36569f, 16f, 8.5f, 16.1343f, 8.5f, 16.3f)
                verticalLineTo(16.4054f)
                curveTo(8.5f, 16.467f, 8.48107f, 16.527f, 8.44577f, 16.5775f)
                lineTo(5.06872f, 21.4018f)
                curveTo(5.02399f, 21.4657f, 5f, 21.5418f, 5f, 21.6198f)
                curveTo(5f, 21.8298f, 5.17021f, 22f, 5.38016f, 22f)
                horizontalLineTo(8.32349f)
            }
        }
    }

    /** Doc file icon: document with curly brackets for text/code content. */
    val DocIcon: ImageVector by lazy {
        icon("DocIcon") {
            s {
                // Document body with folded corner
                moveTo(20f, 13f)
                verticalLineTo(10.6569f)
                curveTo(20f, 9.83935f, 20f, 9.4306f, 19.8478f, 9.06306f)
                curveTo(19.6955f, 8.69552f, 19.4065f, 8.40649f, 18.8284f, 7.82843f)
                lineTo(14.0919f, 3.09188f)
                curveTo(13.593f, 2.593f, 13.3436f, 2.34355f, 13.0345f, 2.19575f)
                curveTo(12.9702f, 2.165f, 12.9044f, 2.13772f, 12.8372f, 2.11401f)
                curveTo(12.5141f, 2f, 12.1614f, 2f, 11.4558f, 2f)
                curveTo(8.21082f, 2f, 6.58831f, 2f, 5.48933f, 2.88607f)
                curveTo(5.26731f, 3.06508f, 5.06508f, 3.26731f, 4.88607f, 3.48933f)
                curveTo(4f, 4.58831f, 4f, 6.21082f, 4f, 9.45584f)
                verticalLineTo(13f)
                moveTo(13f, 2.5f)
                verticalLineTo(3f)
                curveTo(13f, 5.82843f, 13f, 7.24264f, 13.8787f, 8.12132f)
                curveTo(14.7574f, 9f, 16.1716f, 9f, 19f, 9f)
                horizontalLineTo(19.5f)
            }
            s {
                // Curly bracket shape — left
                moveTo(20.5007f, 17.2196f)
                curveTo(20.4486f, 16.0292f, 19.674f, 16f, 18.6231f, 16f)
                curveTo(17.0044f, 16f, 16.736f, 16.406f, 16.736f, 18f)
                verticalLineTo(20f)
                curveTo(16.736f, 21.594f, 17.0044f, 22f, 18.6231f, 22f)
                curveTo(19.674f, 22f, 20.4486f, 21.9708f, 20.5007f, 20.7804f)
            }
            s {
                // Single bracket — right
                moveTo(7.26568f, 19f)
                curveTo(7.26568f, 20.6569f, 6.00155f, 22f, 4.44215f, 22f)
                curveTo(4.0903f, 22f, 3.91437f, 22f, 3.78333f, 21.9196f)
                curveTo(3.46959f, 21.7272f, 3.50098f, 21.3376f, 3.50098f, 21f)
                verticalLineTo(17f)
                curveTo(3.50098f, 16.6624f, 3.46959f, 16.2728f, 3.78333f, 16.0804f)
                curveTo(3.91437f, 16f, 4.0903f, 16f, 4.44215f, 16f)
                curveTo(6.00155f, 16f, 7.26568f, 17.3431f, 7.26568f, 19f)
            }
            s {
                // Pipe in middle
                moveTo(12.0007f, 22f)
                curveTo(11.1134f, 22f, 10.6697f, 22f, 10.394f, 21.7071f)
                curveTo(10.1184f, 21.4142f, 10.1184f, 20.9428f, 10.1184f, 20f)
                verticalLineTo(18f)
                curveTo(10.1184f, 17.0572f, 10.1184f, 16.5858f, 10.394f, 16.2929f)
                curveTo(10.6697f, 16f, 11.1134f, 16f, 12.0007f, 16f)
                curveTo(12.8881f, 16f, 13.3318f, 16f, 13.6074f, 16.2929f)
                curveTo(13.8831f, 16.5858f, 13.8831f, 17.0572f, 13.8831f, 18f)
                verticalLineTo(20f)
                curveTo(13.8831f, 20.9428f, 13.8831f, 21.4142f, 13.6074f, 21.7071f)
                curveTo(13.3318f, 22f, 12.8881f, 22f, 12.0007f, 22f)
            }
        }
    }


    /** Code file icon: document with angle brackets and a symbolic code mark. */
    val CodeFileIcon: ImageVector by lazy {
        icon("CodeFileIcon") {
            s {
                // Document body (wider top, code-style document)
                moveTo(9f, 7f)
                horizontalLineTo(16.75f)
                curveTo(18.8567f, 7f, 19.91f, 7f, 20.6667f, 7.50559f)
                curveTo(20.9943f, 7.72447f, 21.2755f, 8.00572f, 21.4944f, 8.33329f)
                curveTo(22f, 9.08996f, 22f, 10.1433f, 22f, 12.25f)
                curveTo(22f, 15.7612f, 22f, 17.5167f, 21.1573f, 18.7779f)
                curveTo(20.7926f, 19.3238f, 20.3238f, 19.7926f, 19.7779f, 20.1573f)
                curveTo(18.5167f, 21f, 16.7612f, 21f, 13.25f, 21f)
                horizontalLineTo(12f)
                curveTo(7.28595f, 21f, 4.92893f, 21f, 3.46447f, 19.5355f)
                curveTo(2f, 18.0711f, 2f, 15.714f, 2f, 11f)
                verticalLineTo(7.94427f)
                curveTo(2f, 6.1278f, 2f, 5.21956f, 2.38032f, 4.53806f)
                curveTo(2.65142f, 4.05227f, 3.05227f, 3.65142f, 3.53806f, 3.38032f)
                curveTo(4.21956f, 3f, 5.1278f, 3f, 6.94427f, 3f)
                curveTo(8.10802f, 3f, 8.6899f, 3f, 9.19926f, 3.19101f)
                curveTo(10.3622f, 3.62712f, 10.8418f, 4.68358f, 11.3666f, 5.73313f)
                lineTo(12f, 7f)
            }
            s {
                // Left angle bracket <
                moveTo(15.5f, 12f)
                lineTo(16.4199f, 12.7929f)
                curveTo(16.8066f, 13.1262f, 17f, 13.2929f, 17f, 13.5f)
                curveTo(17f, 13.7071f, 16.8066f, 13.8738f, 16.4199f, 14.2071f)
                lineTo(15.5f, 15f)
            }
            s {
                // Right angle bracket >
                moveTo(8.5f, 12f)
                lineTo(7.58009f, 12.7929f)
                curveTo(7.19337f, 13.1262f, 7f, 13.2929f, 7f, 13.5f)
                curveTo(7f, 13.7071f, 7.19336f, 13.8738f, 7.58009f, 14.2071f)
                lineTo(8.5f, 15f)
            }
            s {
                // Slash / in the middle
                moveTo(13f, 11f)
                lineTo(11f, 16f)
            }
        }
    }


    /** Text file icon: document with text lines and indicators. */
    val TextFileIcon: ImageVector by lazy {
        icon("TextFileIcon") {
            s {
                // Document body with folded corner
                moveTo(20f, 13f)
                verticalLineTo(10.6569f)
                curveTo(20f, 9.83935f, 20f, 9.4306f, 19.8478f, 9.06306f)
                curveTo(19.6955f, 8.69552f, 19.4065f, 8.40649f, 18.8284f, 7.82843f)
                lineTo(14.0919f, 3.09188f)
                curveTo(13.593f, 2.593f, 13.3436f, 2.34355f, 13.0345f, 2.19575f)
                curveTo(12.9702f, 2.165f, 12.9044f, 2.13772f, 12.8372f, 2.11401f)
                curveTo(12.5141f, 2f, 12.1614f, 2f, 11.4558f, 2f)
                curveTo(8.21082f, 2f, 6.58831f, 2f, 5.48933f, 2.88607f)
                curveTo(5.26731f, 3.06508f, 5.06508f, 3.26731f, 4.88607f, 3.48933f)
                curveTo(4f, 4.58831f, 4f, 6.21082f, 4f, 9.45584f)
                verticalLineTo(13f)
                moveTo(13f, 2.5f)
                verticalLineTo(3f)
                curveTo(13f, 5.82843f, 13f, 7.24264f, 13.8787f, 8.12132f)
                curveTo(14.7574f, 9f, 16.1716f, 9f, 19f, 9f)
                horizontalLineTo(19.5f)
            }
            s {
                // Text lines with arrows
                moveTo(10f, 16f)
                lineTo(12f, 19f)
                moveTo(12f, 19f)
                lineTo(14f, 22f)
                moveTo(12f, 19f)
                lineTo(14f, 16f)
                moveTo(12f, 19f)
                lineTo(10f, 22f)
            }
            s {
                // Right vertical bar with markers
                moveTo(16.5f, 16f)
                horizontalLineTo(18.2499f)
                moveTo(18.2499f, 16f)
                horizontalLineTo(19.9999f)
                moveTo(18.2499f, 16f)
                verticalLineTo(22f)
            }
            s {
                // Left vertical bar with markers
                moveTo(4f, 16f)
                horizontalLineTo(5.74997f)
                moveTo(5.74997f, 16f)
                horizontalLineTo(7.49993f)
                moveTo(5.74997f, 16f)
                verticalLineTo(22f)
            }
        }
    }

    /** Markdown file icon: document with a bold M-D mark. */
    val MdIcon: ImageVector by lazy {
        icon("MdIcon") {
            s {
                // Document body with folded corner
                moveTo(20f, 13f)
                verticalLineTo(10.6569f)
                curveTo(20f, 9.83935f, 20f, 9.4306f, 19.8478f, 9.06306f)
                curveTo(19.6955f, 8.69552f, 19.4065f, 8.40649f, 18.8284f, 7.82843f)
                lineTo(14.0919f, 3.09188f)
                curveTo(13.593f, 2.593f, 13.3436f, 2.34355f, 13.0345f, 2.19575f)
                curveTo(12.9702f, 2.165f, 12.9044f, 2.13772f, 12.8372f, 2.11401f)
                curveTo(12.5141f, 2f, 12.1614f, 2f, 11.4558f, 2f)
                curveTo(8.21082f, 2f, 6.58831f, 2f, 5.48933f, 2.88607f)
                curveTo(5.26731f, 3.06508f, 5.06508f, 3.26731f, 4.88607f, 3.48933f)
                curveTo(4f, 4.58831f, 4f, 6.21082f, 4f, 9.45584f)
                verticalLineTo(13f)
                moveTo(13f, 2.5f)
                verticalLineTo(3f)
                curveTo(13f, 5.82843f, 13f, 7.24264f, 13.8787f, 8.12132f)
                curveTo(14.7574f, 9f, 16.1716f, 9f, 19f, 9f)
                horizontalLineTo(19.5f)
            }
            s {
                // Bold M mark
                moveTo(8f, 16f)
                lineTo(8f, 22f)
                moveTo(8f, 16f)
                lineTo(11f, 19.5f)
                lineTo(14f, 16f)
                lineTo(14f, 22f)
            }
            s {
                // Horizontal rule separator
                moveTo(16f, 18f)
                horizontalLineTo(20f)
            }
            s {
                // Hash mark
                moveTo(4f, 18f)
                horizontalLineTo(6f)
                moveTo(4.5f, 16f)
                lineTo(5.5f, 22f)
            }
        }
    }


    /** Save/Download icon: angled arrow exiting a document frame. */
    val SaveIcon: ImageVector by lazy {
        icon("SaveIcon") {
            s {
                // Arrow pointing down with line
                moveTo(16.0001f, 12f)
                curveTo(16.0001f, 12f, 13.0542f, 16f, 12.0001f, 16f)
                curveTo(10.946f, 16f, 8.00012f, 12f, 8.00012f, 12f)
                moveTo(12.0001f, 15.5f)
                lineTo(12.0001f, 3f)
            }
            s {
                // Document tray shape below
                moveTo(17.0001f, 8f)
                curveTo(19.2093f, 8f, 21.0001f, 9.79086f, 21.0001f, 12f)
                verticalLineTo(14.5f)
                curveTo(21.0001f, 16.8346f, 21.0001f, 18.0019f, 20.5278f, 18.8856f)
                curveTo(20.1549f, 19.5833f, 19.5834f, 20.1547f, 18.8857f, 20.5277f)
                curveTo(18.0021f, 21f, 16.8348f, 21f, 14.5001f, 21f)
                horizontalLineTo(9.50052f)
                curveTo(7.16551f, 21f, 5.99801f, 21f, 5.11426f, 20.5275f)
                curveTo(4.41677f, 20.1546f, 3.84547f, 19.5834f, 3.47258f, 18.8859f)
                curveTo(3.00012f, 18.0021f, 3.00012f, 16.8346f, 3.00012f, 14.4996f)
                verticalLineTo(11.999f)
                curveTo(3.00067f, 9.79114f, 4.78999f, 8.00125f, 6.99785f, 8f)
                horizontalLineTo(7.00012f)
            }
        }
    }


    /** Cancel/Close square icon: X mark inside a square. */
    val CancelSquareIcon: ImageVector by lazy {
        icon("CancelSquareIcon") {
            s {
                moveTo(2f, 12f)
                curveTo(2f, 7.28595f, 2f, 4.92893f, 3.46447f, 3.46447f)
                curveTo(4.92893f, 2f, 7.28595f, 2f, 12f, 2f)
                curveTo(16.714f, 2f, 19.0711f, 2f, 20.5355f, 3.46447f)
                curveTo(22f, 4.92893f, 22f, 7.28595f, 22f, 12f)
                curveTo(22f, 16.714f, 22f, 19.0711f, 20.5355f, 20.5355f)
                curveTo(19.0711f, 22f, 16.714f, 22f, 12f, 22f)
                curveTo(7.28595f, 22f, 4.92893f, 22f, 3.46447f, 20.5355f)
                curveTo(2f, 19.0711f, 2f, 16.714f, 2f, 12f)
            }
            s {
                moveTo(8.5f, 8.5f)
                lineTo(15.5f, 15.5f)
            }
            s {
                moveTo(15.5f, 8.5f)
                lineTo(8.5f, 15.5f)
            }
        }
    }


    /** Full permission icon: shield with keyhole. */
    val FullPermissionIcon: ImageVector by lazy {
        icon("FullPermissionIcon") {
            s {
                // Shield body (circle/oval)
                moveTo(5f, 15f)
                curveTo(5f, 11.134f, 8.13401f, 8f, 12f, 8f)
                curveTo(15.866f, 8f, 19f, 11.134f, 19f, 15f)
                curveTo(19f, 18.866f, 15.866f, 22f, 12f, 22f)
                curveTo(8.13401f, 22f, 5f, 18.866f, 5f, 15f)
            }
            s {
                // Keyhole loop at top
                moveTo(7.5f, 9.5f)
                verticalLineTo(6.5f)
                curveTo(7.5f, 4.01472f, 9.51472f, 2f, 12f, 2f)
                curveTo(13.5602f, 2f, 14.935f, 2.79401f, 15.7422f, 4f)
            }
            s {
                // Dot/indicator at bottom
                moveTo(12f, 16f)
                verticalLineTo(14f)
            }
        }
    }


    /** New chat icon: chat bubble with a plus. */
    val NewChatIcon: ImageVector by lazy {
        icon("NewChatIcon") {
            s {
                // Chat bubble body
                moveTo(2f, 10.5f)
                curveTo(2f, 9.72921f, 2.01346f, 8.97679f, 2.03909f, 8.2503f)
                curveTo(2.12282f, 5.87683f, 2.16469f, 4.69009f, 3.13007f, 3.71745f)
                curveTo(4.09545f, 2.74481f, 5.3157f, 2.6926f, 7.7562f, 2.58819f)
                curveTo(9.09517f, 2.5309f, 10.5209f, 2.5f, 12f, 2.5f)
                curveTo(13.4791f, 2.5f, 14.9048f, 2.5309f, 16.2438f, 2.58819f)
                curveTo(18.6843f, 2.6926f, 19.9046f, 2.74481f, 20.8699f, 3.71745f)
                curveTo(21.8353f, 4.69009f, 21.8772f, 5.87683f, 21.9609f, 8.2503f)
                curveTo(21.9865f, 8.97679f, 22f, 9.72921f, 22f, 10.5f)
                curveTo(22f, 11.2708f, 21.9865f, 12.0232f, 21.9609f, 12.7497f)
                curveTo(21.8772f, 15.1232f, 21.8353f, 16.3099f, 20.8699f, 17.2826f)
                curveTo(19.9046f, 18.2552f, 18.6843f, 18.3074f, 16.2437f, 18.4118f)
                curveTo(15.5098f, 18.4432f, 14.7498f, 18.4667f, 13.9693f, 18.4815f)
                curveTo(13.2282f, 18.4955f, 12.8576f, 18.5026f, 12.532f, 18.6266f)
                curveTo(12.2064f, 18.7506f, 11.9325f, 18.9855f, 11.3845f, 19.4553f)
                lineTo(9.20503f, 21.3242f)
                curveTo(9.07273f, 21.4376f, 8.90419f, 21.5f, 8.72991f, 21.5f)
                curveTo(8.32679f, 21.5f, 8f, 21.1732f, 8f, 20.7701f)
                verticalLineTo(18.4219f)
                curveTo(7.91842f, 18.4186f, 7.83715f, 18.4153f, 7.75619f, 18.4118f)
                curveTo(5.31569f, 18.3074f, 4.09545f, 18.2552f, 3.13007f, 17.2825f)
                curveTo(2.16469f, 16.3099f, 2.12282f, 15.1232f, 2.03909f, 12.7497f)
                curveTo(2.01346f, 12.0232f, 2f, 11.2708f, 2f, 10.5f)
            }
            s {
                // Plus sign
                moveTo(15.5f, 10.5f)
                horizontalLineTo(8.5f)
                moveTo(12f, 7f)
                verticalLineTo(14f)
            }
        }
    }


    /** PDF export icon: document with text lines. */
    val PdfExportIcon: ImageVector by lazy {
        icon("PdfExportIcon") {
            s {
                // Document body with angled top-right
                moveTo(20f, 13f)
                verticalLineTo(10.6569f)
                curveTo(20f, 9.83935f, 20f, 9.4306f, 19.8478f, 9.06306f)
                curveTo(19.6955f, 8.69552f, 19.4065f, 8.40649f, 18.8284f, 7.82843f)
                lineTo(14.0919f, 3.09188f)
                curveTo(13.593f, 2.593f, 13.3436f, 2.34355f, 13.0345f, 2.19575f)
                curveTo(12.9702f, 2.165f, 12.9044f, 2.13772f, 12.8372f, 2.11401f)
                curveTo(12.5141f, 2f, 12.1614f, 2f, 11.4558f, 2f)
                curveTo(8.21082f, 2f, 6.58831f, 2f, 5.48933f, 2.88607f)
                curveTo(5.26731f, 3.06508f, 5.06508f, 3.26731f, 4.88607f, 3.48933f)
                curveTo(4f, 4.58831f, 4f, 6.21082f, 4f, 9.45584f)
                verticalLineTo(13f)
                moveTo(13f, 2.5f)
                verticalLineTo(3f)
                curveTo(13f, 5.82843f, 13f, 7.24264f, 13.8787f, 8.12132f)
                curveTo(14.7574f, 9f, 16.1716f, 9f, 19f, 9f)
                horizontalLineTo(19.5f)
            }
            s {
                // Text lines and save element
                moveTo(19.75f, 16f)
                horizontalLineTo(17.25f)
                curveTo(16.6977f, 16f, 16.25f, 16.4477f, 16.25f, 17f)
                verticalLineTo(19f)
                moveTo(16.25f, 19f)
                verticalLineTo(22f)
                moveTo(16.25f, 19f)
                horizontalLineTo(19.25f)
                moveTo(4.25f, 22f)
                verticalLineTo(19.5f)
                moveTo(4.25f, 19.5f)
                verticalLineTo(16f)
                horizontalLineTo(6f)
                curveTo(6.9665f, 16f, 7.75f, 16.7835f, 7.75f, 17.75f)
                curveTo(7.75f, 18.7165f, 6.9665f, 19.5f, 6f, 19.5f)
                horizontalLineTo(4.25f)
                moveTo(10.25f, 16f)
                horizontalLineTo(11.75f)
                curveTo(12.8546f, 16f, 13.75f, 16.8954f, 13.75f, 18f)
                verticalLineTo(20f)
                curveTo(13.75f, 21.1046f, 12.8546f, 22f, 11.75f, 22f)
                horizontalLineTo(10.25f)
                verticalLineTo(16f)
            }
        }
    }


    /** Empty chat welcome icon: robot face with smile. */
    val WelcomeChatIcon: ImageVector by lazy {
        icon("WelcomeChatIcon") {
            s {
                // Outer head shape
                moveTo(3.07818f, 7.5f)
                curveTo(2.38865f, 8.85588f, 2f, 10.39f, 2f, 12.0148f)
                curveTo(2f, 17.5295f, 6.47715f, 22f, 12f, 22f)
                curveTo(17.5228f, 22f, 22f, 17.5295f, 22f, 12.0148f)
                curveTo(22f, 10.39f, 21.6114f, 8.85588f, 20.9218f, 7.5f)
            }
            s {
                // Smile
                moveTo(8f, 15f)
                curveTo(8.91212f, 16.2144f, 10.3643f, 17f, 12f, 17f)
                curveTo(13.6357f, 17f, 15.0879f, 16.2144f, 16f, 15f)
            }
            s {
                // Top antenna / ellipse
                moveTo(2f, 4f)
                curveTo(2f, 2.89543f, 6.47715f, 2f, 12f, 2f)
                curveTo(17.5228f, 2f, 22f, 2.89543f, 22f, 4f)
                curveTo(22f, 5.10457f, 17.5228f, 6f, 12f, 6f)
                curveTo(6.47715f, 6f, 2f, 5.10457f, 2f, 4f)
            }
            s {
                // Left eye
                moveTo(7f, 10.5f)
                curveTo(7f, 9.67154f, 7.67157f, 8.99997f, 8.5f, 8.99997f)
                curveTo(9.32843f, 8.99997f, 10f, 9.67154f, 10f, 10.5f)
            }
            s {
                // Right eye
                moveTo(14f, 10.4999f)
                curveTo(14f, 9.67151f, 14.6716f, 8.99994f, 15.5f, 8.99994f)
                curveTo(16.3284f, 8.99994f, 17f, 9.67151f, 17f, 10.4999f)
            }
        }
    }

}