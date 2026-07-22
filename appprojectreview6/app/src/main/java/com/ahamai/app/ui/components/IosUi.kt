package com.ahamai.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.ChatPalette
import com.ahamai.app.ui.theme.InterFamily

// ── Shared iOS-style palette tokens (used across all sheets/dialogs) ──────────
object IosSheetColors {
    // Elevated surfaces matching profile / Manus cards
    val darkSurface = Color(0xFF202024)
    val lightSurface = Color(0xFFFFFFFF)
    val darkBg = Color(0xFF0C0C0E)
    val lightBg = Color(0xFFF5F5F7)
    val darkOverlay = Color(0x66000000)  // 40% black
    val lightOverlay = Color(0x40000000) // 25% black
    val darkSeparator = Color(0xFF2A2A30)
    val lightSeparator = Color(0xFFF0F0F2)
}

/**
 * iOS-style Modal Bottom Sheet with consistent dark/light styling.
 * - Dark mode: black background (`#000`), white text
 * - Light mode: white background, dark text
 * - No drag handle (uses the standard swipe-to-dismiss)
 * - Overlay is a subtle translucent black
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosBottomSheet(
    isDark: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    noHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF),
        scrimColor = if (isDark) Color(0x66000000) else Color(0x33000000),
        dragHandle = if (noHandle) null else (@Composable {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(if (isDark) Color(0xFF636366) else Color(0xFFC7C7CC))
                )
            }
        }),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 28.dp),
            content = content
        )
    }
}

/**
 * iOS-style Dialog — a floating card with rounded corners.
 * - `RoundedCornerShape(16.dp)` Surface
 * - Dark: `#1C1C1E` background, Light: `White`
 * - Overlay is translucent black
 * - Inner horizontal padding: `20.dp`
 */
@Composable
fun IosDialog(
    isDark: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) IosSheetColors.darkSurface else IosSheetColors.lightSurface,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                content = content
            )
        }
    }
}

/**
 * One row in [IosFloatingContextMenu] actions card.
 */
data class IosContextMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Long-press floating menu — clean title pill + action card.
 *
 * **Title card**: 22dp radius, soft shadow, title only (no icon) — 18sp / 400.
 * **Actions card**: 22dp radius, ~230dp wide, icon+label rows (17sp), Delete #FF3B30.
 *
 * **iOS-style background blur**: Android 12+ [FLAG_BLUR_BEHIND] + `blurBehindRadius`;
 * older OS uses a light frosted scrim.
 */
@Composable
fun IosFloatingContextMenu(
    isDark: Boolean,
    title: String? = null,
    onDismissRequest: () -> Unit,
    actions: List<IosContextMenuAction>,
    leading: (@Composable () -> Unit)? = null,
    titleIcon: ImageVector? = null
) {
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val ink = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1A1A1A)
    val danger = Color(0xFFFF3B30)
    val frosted = if (isDark) Color(0x661C1C1E) else Color(0x66E8E8E8)
    val showTitle = !title.isNullOrBlank()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        IosBlurBehindWindow(isDark = isDark)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(frosted)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* absorb */ }
                    ),
                horizontalAlignment = if (showTitle) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Title card — text only when provided (long-press); omitted for ⋮ overflow ──
                if (showTitle) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardBg,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = Color.Black.copy(alpha = 0.04f),
                                spotColor = Color.Black.copy(alpha = 0.04f)
                            )
                    ) {
                        Text(
                            text = title.orEmpty(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = InterFamily,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.2.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp, vertical = 22.dp)
                        )
                    }
                }

                // ── Actions card (same style for long-press + three-dot overflow) ──
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .width(if (showTitle) 230.dp else 260.dp)
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(22.dp),
                            ambientColor = Color.Black.copy(alpha = 0.04f),
                            spotColor = Color.Black.copy(alpha = 0.04f)
                        )
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        actions.forEach { action ->
                            IosContextMenuRow(
                                action = action,
                                ink = ink,
                                danger = danger
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared iOS circular chrome control (white / elevated surface + soft shadow).
 * Used for back, workspace, and ⋯ overflow on agent screens.
 */
@Composable
fun IosChromeIconButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val bg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(120),
        label = "chromePress"
    )
    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (isDark) 0.dp else 1.5.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * iOS chrome button for overflow (⋯) — circular white surface + SF ellipsis.
 */
@Composable
fun IosEllipsisButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "More"
) {
    val ink = if (isDark) Color(0xFFE5E5EA) else Color(0xFF3A3A3C)
    IosChromeIconButton(
        isDark = isDark,
        onClick = onClick,
        modifier = modifier,
        contentDescription = contentDescription
    ) {
        Icon(
            imageVector = com.ahamai.app.ui.icons.AdminIcons.Ellipsis,
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Rename dialog in the same floating-card + blur language as [IosFloatingContextMenu].
 */
@Composable
fun IosRenameDialog(
    isDark: Boolean,
    initialName: String,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit,
    title: String = "Rename"
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val ink = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1A1A1A)
    val muted = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val fieldBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)
    val frosted = if (isDark) Color(0x661C1C1E) else Color(0x66E8E8E8)
    val accent = ChatPalette.Accent

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        IosBlurBehindWindow(isDark = isDark)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(frosted)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.04f)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = ink,
                        letterSpacing = (-0.2f).sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = fieldBg,
                        tonalElevation = 0.dp
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 17.sp,
                                color = ink,
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                            singleLine = true,
                            decorationBox = { inner ->
                                Box {
                                    if (name.isEmpty()) {
                                        Text(
                                            "Name",
                                            fontSize = 17.sp,
                                            color = muted,
                                            fontFamily = InterFamily
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Cancel",
                            fontSize = 17.sp,
                            fontFamily = InterFamily,
                            color = muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onDismissRequest)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Save",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFamily,
                            color = accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSave(name.trim().ifBlank { initialName.ifBlank { "Untitled" } })
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Feedback sheet — same floating card + blur language as [IosFloatingContextMenu] / rename.
 * Multi-image attach, green Submit.
 */
@Composable
fun IosFeedbackDialog(
    isDark: Boolean,
    message: String,
    onMessageChange: (String) -> Unit,
    imageUris: List<Uri>,
    onAddImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onDismissRequest: () -> Unit,
    onSubmit: () -> Unit,
    maxImages: Int = 6
) {
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val ink = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1A1A1A)
    val muted = Color(0xFF8E8E93)
    val fieldBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)
    val frosted = if (isDark) Color(0x661C1C1E) else Color(0x66E8E8E8)
    val submitGreen = Color(0xFF34C759)
    val canSubmit = message.isNotBlank()
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        IosBlurBehindWindow(isDark = isDark)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(frosted)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.04f)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "Feedback",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = ink,
                        letterSpacing = (-0.2f).sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tell us what you love, what breaks, or what you want next.",
                        fontSize = 13.sp,
                        fontFamily = InterFamily,
                        color = muted,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = fieldBg,
                        tonalElevation = 0.dp
                    ) {
                        BasicTextField(
                            value = message,
                            onValueChange = onMessageChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp, max = 180.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = ink,
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 22.sp
                            ),
                            cursorBrush = SolidColor(submitGreen),
                            decorationBox = { inner ->
                                Box {
                                    if (message.isEmpty()) {
                                        Text(
                                            "Your message…",
                                            fontSize = 16.sp,
                                            color = muted.copy(alpha = 0.75f),
                                            fontFamily = InterFamily
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Image strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imageUris.forEach { uri ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(fieldBg)
                            ) {
                                val bmp = remember(uri) {
                                    runCatching {
                                        context.contentResolver.openInputStream(uri)?.use {
                                            BitmapFactory.decodeStream(it)
                                        }
                                    }.getOrNull()
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Lucide.Image,
                                        contentDescription = null,
                                        tint = muted,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(22.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable { onRemoveImage(uri) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Lucide.X,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                        if (imageUris.size < maxImages) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(fieldBg)
                                    .clickable(onClick = onAddImages),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Lucide.Plus,
                                        contentDescription = "Add photos",
                                        tint = muted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Photo",
                                        fontSize = 10.sp,
                                        fontFamily = InterFamily,
                                        color = muted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Cancel",
                            fontSize = 17.sp,
                            fontFamily = InterFamily,
                            color = muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onDismissRequest)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val submitInteraction = remember { MutableInteractionSource() }
                        val submitPressed by submitInteraction.collectIsPressedAsState()
                        val submitScale by animateFloatAsState(
                            targetValue = if (submitPressed) 0.96f else 1f,
                            animationSpec = tween(100),
                            label = "submitScale"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = submitScale
                                    scaleY = submitScale
                                    alpha = if (canSubmit) 1f else 0.45f
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(submitGreen)
                                .clickable(
                                    interactionSource = submitInteraction,
                                    indication = null,
                                    enabled = canSubmit,
                                    onClick = onSubmit
                                )
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Submit feedback",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFamily,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Apply iOS-like system blur behind a Dialog window (API 31+). */
@Composable
private fun IosBlurBehindWindow(isDark: Boolean) {
    val view = LocalView.current
    DisposableEffect(isDark) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setDimAmount(if (isDark) 0.28f else 0.10f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = 48
                }
            }
        }
        onDispose { }
    }
}

@Composable
private fun IosContextMenuRow(
    action: IosContextMenuAction,
    ink: Color,
    danger: Color
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(150),
        label = "ctxRowBg"
    )
    val color = if (action.destructive) danger else ink
    val pressFill = if (action.destructive) Color.Transparent
    else Color(0xFFF8F8F8).copy(alpha = bg * (if (ink == Color(0xFF1A1A1A)) 1f else 0.12f))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(pressFill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = action.onClick
            )
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = action.label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = InterFamily,
            color = color,
            letterSpacing = 0.sp
        )
    }
}

/**
 * iOS-style Dropdown Menu — a floating popup with rounded corners and consistent
 * dark/light background. Replaces Material3 default DropdownMenu.
 */
@Composable
fun IosDropdownMenu(
    expanded: Boolean,
    isDark: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = if (isDark) IosSheetColors.darkSurface else IosSheetColors.lightSurface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * iOS-style divider (hairline) used inside sheets and menus.
 */
@Composable
fun IosDivider(isDark: Boolean, modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = if (isDark) IosSheetColors.darkSeparator else IosSheetColors.lightSeparator,
        thickness = 0.5.dp,
        modifier = modifier
    )
}

/**
 * iOS-style row item for bottom sheets / pickers.
 * Height ~44dp, with a divider underneath.
 */
@Composable
fun IosSheetRow(
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    label: String,
    subtitle: String = "",
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = ChatType.sm,
                color = if (selected) MaterialTheme.colorScheme.primary else primaryText,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = InterFamily,
                maxLines = 1
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = ChatType.xs,
                    color = muted,
                    fontFamily = InterFamily,
                    maxLines = 1
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (selected) {
            Text(
                text = "✓",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = InterFamily
            )
        }
    }
    IosDivider(isDark, modifier = Modifier.padding(start = 20.dp))
}

/**
 * iOS-style section header for bottom sheets.
 */
@Composable
fun IosSheetHeader(
    title: String,
    subtitle: String = "",
    primaryText: Color,
    muted: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = ChatType.body,
            fontWeight = FontWeight.SemiBold,
            color = primaryText,
            fontFamily = InterFamily
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = ChatType.xs,
                color = muted,
                fontFamily = InterFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    IosDivider(isDark, modifier = Modifier.padding(start = 20.dp))
}

/**
 * iOS-style search field for bottom sheet model/list pickers.
 */
@Composable
fun IosSheetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    isDark: Boolean,
    muted: Color,
    primaryText: Color,
    placeholder: String = "Search",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFF4F4F4))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (query.isEmpty()) placeholder else "",
            fontSize = 14.sp,
            color = muted.copy(alpha = 0.7f),
            fontFamily = InterFamily,
            modifier = Modifier.weight(1f)
        )
        // BasicTextField overlay
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                color = primaryText,
                fontFamily = InterFamily
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * iOS-style "Cancel / Done" action row at the bottom of a sheet.
 */
@Composable
fun IosSheetActions(
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    cancelLabel: String = "Cancel",
    doneLabel: String = "Done",
    doneEnabled: Boolean = true,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = cancelLabel,
                fontSize = 15.sp,
                color = muted,
                fontFamily = InterFamily
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onDone,
            enabled = doneEnabled
        ) {
            Text(
                text = doneLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (doneEnabled) MaterialTheme.colorScheme.primary else muted,
                fontFamily = InterFamily
            )
        }
    }
}