package com.ahamai.app.screens

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.ahamai.app.ui.chat.HapticOnPress
import com.ahamai.app.ui.chat.rememberChatHaptics
import com.ahamai.app.ui.flow.FlowEnter
import com.ahamai.app.ui.flow.FlowMotion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.AuthManager
import com.ahamai.app.data.ImageUtils
import com.ahamai.app.data.PreferencesManager
import com.ahamai.app.ui.components.AhamaiLogo
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(onAuthed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val haptics = rememberChatHaptics()

    val bg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val cardBg = if (isDark) Color(0xFF1A1A1E) else Color(0xFFFFFFFF)
    val primaryText = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val secondaryText = if (isDark) Color(0xFF9A9AA2) else Color(0xFF6B6B6B)
    val fieldBg = if (isDark) Color(0xFF24242A) else Color(0xFFF5F5F7)
    val borderColor = if (isDark) Color(0xFF2E2E34) else Color(0xFFE8E8EC)
    val accentC = Color(0xFF0A84FF)
    val pillBg = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
    val pillFg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFFFFFFF)
    val cardShape = RoundedCornerShape(18.dp)

    var isSignup by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var avatarBmp by remember { mutableStateOf<Bitmap?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }

    val googleSignInClient = remember { AuthManager.getGoogleSignInClient(context) }

    LaunchedEffect(Unit) {
        try {
            val silentAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (silentAccount != null && silentAccount.idToken != null) {
                loading = true
                val res = AuthManager.signInWithGoogle(silentAccount.idToken!!)
                loading = false
                res.fold(
                    onSuccess = {
                        haptics.success()
                        Toast.makeText(context, "Welcome back", Toast.LENGTH_SHORT).show()
                        onAuthed()
                    },
                    onFailure = { /* ignore — manual sign-in still available */ }
                )
            }
        } catch (_: Exception) { }
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data == null) {
                error = "Google sign-in cancelled"
                return@rememberLauncherForActivityResult
            }
            loading = true
            error = null
            scope.launch {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account?.idToken
                    if (idToken != null) {
                        val res = AuthManager.signInWithGoogle(idToken)
                        loading = false
                        res.fold(
                            onSuccess = {
                                haptics.success()
                                Toast.makeText(context, "Welcome back", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    AuthManager.restoreHistory(context)
                                    AuthManager.restoreWorkspaces(context)
                                    AuthManager.restoreGithubToken(context)
                                    runCatching {
                                        val p = AuthManager.loadProfile()
                                        if (p.avatar.isNotBlank()) {
                                            PreferencesManager(context).saveAvatar(p.avatar)
                                        }
                                    }
                                    com.ahamai.app.data.HistoryRefresh.bump()
                                }
                                onAuthed()
                            },
                            onFailure = {
                                haptics.reject()
                                error = it.message ?: "Google sign-in failed"
                            }
                        )
                    } else {
                        loading = false
                        error = "Failed to get Google ID token"
                    }
                } catch (e: ApiException) {
                    loading = false
                    error = when (e.statusCode) {
                        12501 -> "Sign-in cancelled"
                        12500 -> "Google sign-in error. Check your Google account."
                        12502 -> "Sign-in already in progress"
                        else -> "Google sign-in failed (${e.statusCode})"
                    }
                } catch (e: Exception) {
                    loading = false
                    error = "Google sign-in failed: ${e.message}"
                }
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            val data = result.data
            val statusCode = data?.getIntExtra("googleSignInStatus", -1) ?: -1
            if (statusCode != -1) {
                error = "Google sign-in failed (status: $statusCode). Check that SHA-1 fingerprint is registered in Firebase Console."
            }
        } else {
            error = "Google sign-in failed (code: ${result.resultCode})"
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val url = withContext(kotlinx.coroutines.Dispatchers.IO) {
                ImageUtils.uriToDataUrl(context, uri.toString(), maxDim = 256, quality = 70)
            }
            val bmp = if (url != null) ImageUtils.decodeBase64(url) else null
            if (url != null && bmp != null) { avatarUrl = url; avatarBmp = bmp }
        }
    }

    fun submit() {
        error = null
        val em = email.trim()
        if (em.isBlank() || !em.contains("@")) { error = "Enter a valid email"; return }
        if (password.length < 6) { error = "Password must be at least 6 characters"; return }
        if (isSignup) {
            if (firstName.isBlank()) { error = "Enter your name"; return }
            if (!agreedToTerms) { error = "Please agree to the terms"; return }
        }
        loading = true
        scope.launch {
            val fullName = firstName.trim()
            val res = if (isSignup) AuthManager.signUp(fullName, em, password, avatarUrl)
                      else AuthManager.signIn(em, password)
            loading = false
            res.fold(
                onSuccess = {
                    haptics.success()
                    if (isSignup && avatarUrl != null) PreferencesManager(context).saveAvatar(avatarUrl!!)
                    Toast.makeText(context, if (isSignup) "Account created" else "Welcome back", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        AuthManager.restoreHistory(context)
                        AuthManager.restoreWorkspaces(context)
                        AuthManager.restoreGithubToken(context)
                        runCatching {
                            val p = AuthManager.loadProfile()
                            if (p.avatar.isNotBlank()) {
                                PreferencesManager(context).saveAvatar(p.avatar)
                            }
                        }
                        com.ahamai.app.data.HistoryRefresh.bump()
                    }
                    onAuthed()
                },
                onFailure = {
                    haptics.reject()
                    error = it.message ?: "Something went wrong"
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        FlowEnter(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))

                // Brand — Unica One wordmark (same as profile / pricing)
                AhamaiLogo(
                    color = primaryText,
                    fontSize = 32.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isSignup) "create your account" else "welcome back",
                    fontSize = 13.sp,
                    fontFamily = InterFamily,
                    color = secondaryText,
                    letterSpacing = 0.3.sp
                )

                Spacer(Modifier.height(28.dp))

                AnimatedContent(
                    targetState = isSignup,
                    transitionSpec = {
                        FlowMotion.authModeSwitch(toSignup = targetState)
                    },
                    label = "auth_content"
                ) { signup ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!signup) {
                            // ===== LOGIN =====
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(cardBg)
                                    .border(0.5.dp, borderColor, cardShape)
                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AuthField(
                                    label = "Email",
                                    value = email,
                                    onValueChange = { email = it },
                                    placeholder = "you@email.com",
                                    keyboardType = KeyboardType.Email,
                                    fieldBg = fieldBg,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )

                                Spacer(Modifier.height(12.dp))

                                AuthField(
                                    label = "Password",
                                    value = password,
                                    onValueChange = { password = it },
                                    placeholder = "••••••••",
                                    isPassword = true,
                                    showPassword = showPass,
                                    onTogglePassword = { showPass = !showPass },
                                    fieldBg = fieldBg,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "Forgot password?",
                                    fontSize = 12.sp,
                                    fontFamily = InterFamily,
                                    color = secondaryText,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .clickable {
                                            val em = email.trim()
                                            if (em.isBlank() || !em.contains("@")) error = "Enter your email first"
                                            else scope.launch {
                                                AuthManager.sendPasswordReset(em).fold(
                                                    onSuccess = {
                                                        error = null
                                                        Toast.makeText(context, "Reset link sent", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onFailure = { error = it.message }
                                                )
                                            }
                                        }
                                )

                                Spacer(Modifier.height(18.dp))

                                AuthPrimaryButton(
                                    text = "Continue",
                                    loading = loading,
                                    onClick = {
                                        haptics.confirm()
                                        submit()
                                    },
                                    pillBg = pillBg,
                                    pillFg = pillFg
                                )

                                Spacer(Modifier.height(14.dp))
                                AuthOrDivider(borderColor = borderColor, secondaryText = secondaryText)
                                Spacer(Modifier.height(14.dp))

                                AuthGoogleButton(
                                    text = "Continue with Google",
                                    onClick = {
                                        haptics.tick()
                                        error = null
                                        googleLauncher.launch(googleSignInClient.signInIntent)
                                    },
                                    fieldBg = fieldBg,
                                    borderColor = borderColor,
                                    primaryText = primaryText
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.select()
                                        isSignup = true
                                        error = null
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Don't have an account? ",
                                    fontSize = 13.sp,
                                    fontFamily = InterFamily,
                                    color = secondaryText
                                )
                                Text(
                                    "Sign up",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFamily,
                                    color = primaryText
                                )
                            }
                        } else {
                            // ===== SIGNUP =====
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(cardBg)
                                    .border(0.5.dp, borderColor, cardShape)
                                    .padding(horizontal = 18.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "← back",
                                        fontSize = 13.sp,
                                        fontFamily = InterFamily,
                                        color = secondaryText,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                haptics.select()
                                                isSignup = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "sign up",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = InterFamily,
                                        color = primaryText,
                                        letterSpacing = 0.4.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    // balance the back control
                                    Spacer(Modifier.width(52.dp))
                                }

                                Spacer(Modifier.height(16.dp))

                                // Photo — text-led, no material icon
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(fieldBg)
                                        .border(1.dp, borderColor, CircleShape)
                                        .clickable {
                                            picker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarBmp != null) {
                                        Image(
                                            bitmap = avatarBmp!!.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            "photo",
                                            fontSize = 11.sp,
                                            fontFamily = InterFamily,
                                            color = secondaryText
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "optional",
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = secondaryText.copy(alpha = 0.7f)
                                )

                                Spacer(Modifier.height(16.dp))

                                AuthField(
                                    label = "Full name",
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    placeholder = "James Anderson",
                                    fieldBg = fieldBg,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )

                                Spacer(Modifier.height(12.dp))

                                AuthField(
                                    label = "Email",
                                    value = email,
                                    onValueChange = { email = it },
                                    placeholder = "you@email.com",
                                    keyboardType = KeyboardType.Email,
                                    fieldBg = fieldBg,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )

                                Spacer(Modifier.height(12.dp))

                                AuthField(
                                    label = "Password",
                                    value = password,
                                    onValueChange = { password = it },
                                    placeholder = "min 6 characters",
                                    isPassword = true,
                                    showPassword = showPass,
                                    onTogglePassword = { showPass = !showPass },
                                    fieldBg = fieldBg,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )

                                Spacer(Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { agreedToTerms = !agreedToTerms }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (agreedToTerms) accentC else Color.Transparent)
                                            .border(
                                                1.5.dp,
                                                if (agreedToTerms) accentC else borderColor,
                                                RoundedCornerShape(5.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (agreedToTerms) {
                                            Text(
                                                "✓",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        buildAnnotatedString {
                                            append("I agree to the ")
                                            withStyle(
                                                SpanStyle(fontWeight = FontWeight.SemiBold, color = primaryText)
                                            ) { append("Terms") }
                                            append(" & ")
                                            withStyle(
                                                SpanStyle(fontWeight = FontWeight.SemiBold, color = primaryText)
                                            ) { append("Privacy Policy") }
                                        },
                                        fontSize = 12.sp,
                                        fontFamily = InterFamily,
                                        color = secondaryText,
                                        lineHeight = 16.sp
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                AuthPrimaryButton(
                                    text = "Create account",
                                    loading = loading,
                                    onClick = {
                                        haptics.confirm()
                                        submit()
                                    },
                                    pillBg = pillBg,
                                    pillFg = pillFg
                                )

                                Spacer(Modifier.height(14.dp))
                                AuthOrDivider(borderColor = borderColor, secondaryText = secondaryText)
                                Spacer(Modifier.height(14.dp))

                                AuthGoogleButton(
                                    text = "Continue with Google",
                                    onClick = {
                                        haptics.tick()
                                        error = null
                                        googleLauncher.launch(googleSignInClient.signInIntent)
                                    },
                                    fieldBg = fieldBg,
                                    borderColor = borderColor,
                                    primaryText = primaryText
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.select()
                                        isSignup = false
                                        error = null
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Already have an account? ",
                                    fontSize = 13.sp,
                                    fontFamily = InterFamily,
                                    color = secondaryText
                                )
                                Text(
                                    "Login",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFamily,
                                    color = primaryText
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
                ) {
                    error?.let {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFF3B30).copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    it,
                                    fontSize = 13.sp,
                                    fontFamily = InterFamily,
                                    color = Color(0xFFFF3B30),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    fieldBg: Color,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = InterFamily,
            color = secondaryText,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fieldBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = InterFamily,
                    color = primaryText,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(primaryText),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next
                ),
                visualTransformation = if (isPassword && !showPassword) {
                    PasswordVisualTransformation()
                } else VisualTransformation.None,
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                fontSize = 14.sp,
                                fontFamily = InterFamily,
                                color = secondaryText.copy(alpha = 0.45f),
                                lineHeight = 20.sp
                            )
                        }
                        inner()
                    }
                }
            )
            if (isPassword && onTogglePassword != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    if (showPassword) "hide" else "show",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFamily,
                    color = secondaryText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onTogglePassword() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthPrimaryButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    pillBg: Color,
    pillFg: Color
) {
    val haptics = rememberChatHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    HapticOnPress(interactionSource = interactionSource, haptics = haptics, enabled = !loading)
    val scale by animateFloatAsState(
        if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "s"
    )

    // Compact cute pill — not full tall bar
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .scale(scale)
                .clip(RoundedCornerShape(21.dp))
                .background(pillBg)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !loading
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = pillFg
                )
            } else {
                Text(
                    text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = pillFg
                )
            }
        }
    }
}

@Composable
private fun AuthOrDivider(borderColor: Color, secondaryText: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = borderColor, thickness = 0.5.dp)
        Text(
            "  or  ",
            fontSize = 11.sp,
            fontFamily = InterFamily,
            color = secondaryText
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = borderColor, thickness = 0.5.dp)
    }
}

@Composable
private fun AuthGoogleButton(
    text: String,
    onClick: () -> Unit,
    fieldBg: Color,
    borderColor: Color,
    primaryText: Color
) {
    val haptics = rememberChatHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    HapticOnPress(interactionSource = interactionSource, haptics = haptics)
    val scale by animateFloatAsState(
        if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "ss"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .scale(scale)
            .clip(RoundedCornerShape(21.dp))
            .background(fieldBg)
            .border(0.5.dp, borderColor, RoundedCornerShape(21.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Minimal multicolour G mark (brand, not decorative icon pack)
            Canvas(modifier = Modifier.size(14.dp)) {
                val s = size.width
                val sw = 2.dp.toPx()
                drawArc(Color(0xFF4285F4), -45f, 90f, false, topLeft = Offset(0f, 0f), size = Size(s, s), style = Stroke(sw))
                drawArc(Color(0xFFEA4335), 180f, 90f, false, topLeft = Offset(0f, 0f), size = Size(s, s), style = Stroke(sw))
                drawArc(Color(0xFFFBBC05), 90f, 90f, false, topLeft = Offset(0f, 0f), size = Size(s, s), style = Stroke(sw))
                drawArc(Color(0xFF34A853), 0f, 90f, false, topLeft = Offset(0f, 0f), size = Size(s, s), style = Stroke(sw))
                drawLine(Color(0xFF4285F4), Offset(s * 0.5f, s * 0.5f), Offset(s, s * 0.5f), sw)
                drawLine(Color(0xFF4285F4), Offset(s * 0.5f, s * 0.5f), Offset(s * 0.5f, s), sw)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                color = primaryText
            )
        }
    }
}
