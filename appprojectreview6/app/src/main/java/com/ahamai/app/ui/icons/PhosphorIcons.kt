package com.ahamai.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-crafted Phosphor-style stroke icons (SF Symbols-like: rounded caps, soft geometry).
 * Phosphor isn't published as a Compose artifact, so these mirror its look for the input bars.
 * Monochrome — tinted by [androidx.compose.material3.Icon]. 24x24, no fills.
 */
object Phosphor {

    private fun ImageVector.Builder.s(b: PathBuilder.() -> Unit) {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = b
        )
    }

    private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    /** + attach */
    val Plus: ImageVector by lazy {
        icon("Plus") {
            s { moveTo(12f, 5f); verticalLineTo(19f) }
            s { moveTo(5f, 12f); horizontalLineTo(19f) }
        }
    }

    /** Microphone (Phosphor-style capsule + cradle). */
    val Microphone: ImageVector by lazy {
        icon("Microphone") {
            s {
                moveTo(9f, 7f)
                arcToRelative(3f, 3f, 0f, false, true, 6f, 0f)
                lineTo(15f, 11f)
                arcToRelative(3f, 3f, 0f, false, true, -6f, 0f)
                close()
            }
            s { moveTo(6f, 11f); arcToRelative(6f, 6f, 0f, false, false, 12f, 0f) }
            s { moveTo(12f, 17f); verticalLineTo(20.5f) }
            s { moveTo(8.5f, 20.5f); horizontalLineTo(15.5f) }
        }
    }

    /** Microphone with a slash (listening / stop). */
    val MicrophoneSlash: ImageVector by lazy {
        icon("MicrophoneSlash") {
            s {
                moveTo(9f, 7f)
                arcToRelative(3f, 3f, 0f, false, true, 6f, 0f)
                lineTo(15f, 11f)
                arcToRelative(3f, 3f, 0f, false, true, -6f, 0f)
                close()
            }
            s { moveTo(6f, 11f); arcToRelative(6f, 6f, 0f, false, false, 12f, 0f) }
            s { moveTo(12f, 17f); verticalLineTo(20.5f) }
            s { moveTo(8.5f, 20.5f); horizontalLineTo(15.5f) }
            s { moveTo(4.5f, 4.5f); lineTo(19.5f, 19.5f) }
        }
    }

    /** Up arrow (send) — points UP for circular send buttons (ChatGPT/Claude style). */
    val ArrowUp: ImageVector by lazy {
        icon("ArrowUp") {
            s { moveTo(12f, 19f); verticalLineTo(5f) }
            s { moveTo(5f, 12f); lineTo(12f, 5f); lineTo(19f, 12f) }
        }
    }

    /** Paper-plane (alternate send glyph). */
    val PaperPlane: ImageVector by lazy {
        icon("PaperPlane") {
            s {
                moveTo(21f, 4f)
                lineTo(3f, 11.5f)
                lineTo(10.5f, 13.5f)
                lineTo(12.5f, 21f)
                close()
            }
            s { moveTo(21f, 4f); lineTo(10.5f, 13.5f) }
        }
    }

    /** Caret down (model selector chevron). */
    val CaretDown: ImageVector by lazy {
        icon("CaretDown") {
            s { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) }
        }
    }

    /** Lightning bolt (model / effort indicator). */
    val Lightning: ImageVector by lazy {
        icon("Lightning") {
            s {
                moveTo(13f, 3f)
                lineTo(4f, 13f)
                horizontalLineTo(11f)
                lineTo(10f, 21f)
                lineTo(20f, 11f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    /** Eye (Preview). */
    val Eye: ImageVector by lazy {
        icon("Eye") {
            s {
                moveTo(2f, 12f)
                curveTo(2f, 12f, 6f, 5f, 12f, 5f)
                curveTo(18f, 5f, 22f, 12f, 22f, 12f)
                curveTo(22f, 12f, 18f, 19f, 12f, 19f)
                curveTo(6f, 19f, 2f, 12f, 2f, 12f)
                close()
            }
            s { moveTo(15f, 12f); arcToRelative(3f, 3f, 0f, true, false, -6f, 0f); arcToRelative(3f, 3f, 0f, true, false, 6f, 0f) }
        }
    }

    /** Code brackets (Code). */
    val Code: ImageVector by lazy {
        icon("Code") {
            s { moveTo(9f, 8f); lineTo(5f, 12f); lineTo(9f, 16f) }
            s { moveTo(15f, 8f); lineTo(19f, 12f); lineTo(15f, 16f) }
        }
    }

    /** Floppy disk (Save). */
    val FloppyDisk: ImageVector by lazy {
        icon("FloppyDisk") {
            s {
                moveTo(5f, 4f); horizontalLineTo(16f); lineTo(20f, 8f); verticalLineTo(20f); horizontalLineTo(4f); verticalLineTo(5f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f); close()
            }
            s { moveTo(9f, 4f); verticalLineTo(7.5f); horizontalLineTo(14.5f); verticalLineTo(4f) }
            s { moveTo(7f, 20f); verticalLineTo(13f); horizontalLineTo(17f); verticalLineTo(20f) }
        }
    }

    /** Arrows out (Full screen). */
    val ArrowsOut: ImageVector by lazy {
        icon("ArrowsOut") {
            s { moveTo(8f, 3f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, false, -2f, 2f); verticalLineTo(8f) }
            s { moveTo(16f, 3f); horizontalLineTo(19f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineTo(8f) }
            s { moveTo(21f, 16f); verticalLineTo(19f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineTo(16f) }
            s { moveTo(8f, 21f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineTo(16f) }
        }
    }

    /** Close (X). */
    val X: ImageVector by lazy {
        icon("X") {
            s { moveTo(18f, 6f); lineTo(6f, 18f) }
            s { moveTo(6f, 6f); lineTo(18f, 18f) }
        }
    }

    /** Phone (voice call). */
    val Phone: ImageVector by lazy {
        icon("Phone") {
            s {
                moveTo(6.5f, 4f)
                horizontalLineTo(9f)
                lineTo(10.5f, 8f)
                lineTo(8.5f, 9.5f)
                arcToRelative(11f, 11f, 0f, false, false, 5f, 5f)
                lineTo(15f, 12.5f)
                lineTo(19f, 14f)
                verticalLineTo(16.5f)
                arcToRelative(2f, 2f, 0f, false, true, -2.2f, 2f)
                arcTo(15.5f, 15.5f, 0f, false, true, 4.5f, 6.2f)
                arcTo(2f, 2f, 0f, false, true, 6.5f, 4f)
                close()
            }
        }
    }

    /** SF-style chevron left (back). */
    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            s { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
        }
    }

    /** Person circle (profile fallback). */
    val UserCircle: ImageVector by lazy {
        icon("UserCircle") {
            s { moveTo(12f, 12f); arcToRelative(9f, 9f, 0f, true, false, 0.01f, 0f) }
            s { moveTo(12f, 10.5f); arcToRelative(2.5f, 2.5f, 0f, true, false, 0.01f, 0f) }
            s {
                moveTo(7.2f, 17.2f)
                curveTo(8.3f, 15.6f, 10f, 14.5f, 12f, 14.5f)
                curveTo(14f, 14.5f, 15.7f, 15.6f, 16.8f, 17.2f)
            }
        }
    }

    /** Magnifying glass (search). */
    val MagnifyingGlass: ImageVector by lazy {
        icon("MagnifyingGlass") {
            s { moveTo(11f, 11f); arcToRelative(5.5f, 5.5f, 0f, true, false, 0.01f, 0f) }
            s { moveTo(15.5f, 15.5f); lineTo(20f, 20f) }
        }
    }

    /** Checkmark. */
    val Check: ImageVector by lazy {
        icon("Check") {
            s { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
        }
    }

    /** Camera. */
    val Camera: ImageVector by lazy {
        icon("Camera") {
            s {
                moveTo(4f, 8f); horizontalLineTo(7f); lineTo(8.5f, 6f); horizontalLineTo(15.5f)
                lineTo(17f, 8f); horizontalLineTo(20f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(18f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(4f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                verticalLineTo(9.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                close()
            }
            s { moveTo(12f, 13.5f); arcToRelative(3f, 3f, 0f, true, false, 0.01f, 0f) }
        }
    }

    /** Image / photos. */
    val Image: ImageVector by lazy {
        icon("Image") {
            s {
                moveTo(4f, 5f); horizontalLineTo(20f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                verticalLineTo(18f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                horizontalLineTo(4f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                verticalLineTo(6f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                close()
            }
            s { moveTo(8.5f, 10f); arcToRelative(1.5f, 1.5f, 0f, true, false, 0.01f, 0f) }
            s { moveTo(3.5f, 17.5f); lineTo(9f, 12.5f); lineTo(12.5f, 15.5f); lineTo(15.5f, 12.5f); lineTo(20.5f, 17.5f) }
        }
    }

    /** File / document. */
    val File: ImageVector by lazy {
        icon("File") {
            s {
                moveTo(7f, 3.5f); horizontalLineTo(14f); lineTo(19f, 8.5f); verticalLineTo(20f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                horizontalLineTo(7f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                verticalLineTo(4.5f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                close()
            }
            s { moveTo(14f, 3.5f); verticalLineTo(9f); horizontalLineTo(19f) }
        }
    }

    /** Stop square (filled stroke). */
    val Stop: ImageVector by lazy {
        icon("Stop") {
            s {
                moveTo(7f, 7f); horizontalLineTo(17f); verticalLineTo(17f); horizontalLineTo(7f); close()
            }
        }
    }

    /** Copy. */
    val Copy: ImageVector by lazy {
        icon("Copy") {
            s {
                moveTo(9f, 9f); horizontalLineTo(18f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                verticalLineTo(19f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                horizontalLineTo(9f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                verticalLineTo(10f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                close()
            }
            s {
                moveTo(7f, 15f); horizontalLineTo(6f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                verticalLineTo(5f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                horizontalLineTo(14f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                verticalLineTo(6f)
            }
        }
    }

    /** Arrow clockwise (retry). */
    val ArrowClockwise: ImageVector by lazy {
        icon("ArrowClockwise") {
            s { moveTo(20f, 10f); arcToRelative(8f, 8f, 0f, true, false, -2.3f, 5.7f) }
            s { moveTo(20f, 4f); verticalLineTo(10f); horizontalLineTo(14f) }
        }
    }

    /** Waveform (listen). */
    val Waveform: ImageVector by lazy {
        icon("Waveform") {
            s { moveTo(6f, 10f); verticalLineTo(14f) }
            s { moveTo(10f, 7f); verticalLineTo(17f) }
            s { moveTo(14f, 9f); verticalLineTo(15f) }
            s { moveTo(18f, 11f); verticalLineTo(13f) }
        }
    }

    /** Download / export. */
    val DownloadSimple: ImageVector by lazy {
        icon("DownloadSimple") {
            s { moveTo(12f, 4f); verticalLineTo(15f) }
            s { moveTo(7f, 11f); lineTo(12f, 16f); lineTo(17f, 11f) }
            s { moveTo(5f, 19f); horizontalLineTo(19f) }
        }
    }

    /** Globe (web / search tool). */
    val Globe: ImageVector by lazy {
        icon("Globe") {
            s { moveTo(12f, 12f); arcToRelative(9f, 9f, 0f, true, false, 0.01f, 0f) }
            s { moveTo(3f, 12f); horizontalLineTo(21f) }
            s {
                moveTo(12f, 3f)
                curveTo(14.5f, 6f, 15.5f, 9f, 15.5f, 12f)
                curveTo(15.5f, 15f, 14.5f, 18f, 12f, 21f)
            }
            s {
                moveTo(12f, 3f)
                curveTo(9.5f, 6f, 8.5f, 9f, 8.5f, 12f)
                curveTo(8.5f, 15f, 9.5f, 18f, 12f, 21f)
            }
        }
    }
}
