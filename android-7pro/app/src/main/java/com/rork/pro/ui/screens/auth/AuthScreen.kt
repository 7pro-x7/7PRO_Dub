package com.rork.pro.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.i18n.AppLanguage
import com.rork.pro.ui.i18n.Lang
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.i18n.StrAuth
import com.rork.pro.ui.i18n.tr

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }

@Composable
fun AuthScreen(session: SessionViewModel) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val busy by session.authBusy.collectAsStateWithLifecycle()
    val error by session.authError.collectAsStateWithLifecycle()
    val notice by session.authNotice.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))

        // Language switch stays reachable before anyone signs in.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.Surface)
                    .clickable {
                        AppLanguage.set(context, if (AppLanguage.current == Lang.AR) Lang.EN else Lang.AR)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = Ink.TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (AppLanguage.current == Lang.AR) "English" else "العربية",
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Box(
            Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Ink.Amber, Ink.AmberPressed))),
            contentAlignment = Alignment.Center,
        ) {
            Text("7", color = Ink.OnAmber, fontSize = 42.sp, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(18.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Ink.Amber, fontWeight = FontWeight.Black)) { append("7") }
                withStyle(SpanStyle(color = Ink.TextPrimary, fontWeight = FontWeight.Black)) { append("PRO") }
            },
            fontSize = 34.sp,
        )
        Text(
            tr(StrAuth.tagline),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp),
        )

        Spacer(Modifier.height(34.dp))

        Text(
            when (mode) {
                AuthMode.SIGN_IN -> tr(StrAuth.welcomeBack)
                AuthMode.SIGN_UP -> tr(StrAuth.createYourAccount)
                AuthMode.RESET -> tr(StrAuth.resetYourPassword)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = Ink.TextPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))

        if (mode == AuthMode.SIGN_UP) {
            InkField(name, { name = it }, tr(StrAuth.fullName))
            Spacer(Modifier.height(12.dp))
            InkField(phone, { phone = it }, tr(StrAuth.phoneOptional), keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(12.dp))
        }

        if (mode != AuthMode.RESET) {
            GoogleButton(enabled = !busy) { session.signInWithGoogle() }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(Ink.Hairline))
                Text(
                    tr(StrAuth.orUseEmail),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(Modifier.weight(1f).height(1.dp).background(Ink.Hairline))
            }
            Spacer(Modifier.height(16.dp))
        }

        InkField(email, { email = it }, tr(StrAuth.emailAddress), keyboardType = KeyboardType.Email)

        if (mode != AuthMode.RESET) {
            Spacer(Modifier.height(12.dp))
            InkField(
                password,
                { password = it },
                tr(StrAuth.password),
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) tr(StrAuth.hidePassword) else tr(StrAuth.showPassword),
                            tint = Ink.TextMuted,
                        )
                    }
                },
            )
        }

        AnimatedVisibility(error != null) {
            Text(
                error?.message.orEmpty(),
                color = Ink.Coral,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
        AnimatedVisibility(notice != null) {
            Text(
                notice.orEmpty(),
                color = Ink.Teal,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(22.dp))

        val canSubmit = when (mode) {
            AuthMode.SIGN_IN -> email.isNotBlank() && password.length >= 6
            AuthMode.SIGN_UP -> email.isNotBlank() && password.length >= 6 && name.isNotBlank()
            AuthMode.RESET -> email.isNotBlank()
        }

        PrimaryAction(
            label = when (mode) {
                AuthMode.SIGN_IN -> tr(StrAuth.signIn)
                AuthMode.SIGN_UP -> tr(StrAuth.createAccount)
                AuthMode.RESET -> tr(StrAuth.sendResetLink)
            },
            enabled = canSubmit,
            loading = busy,
        ) {
            when (mode) {
                AuthMode.SIGN_IN -> session.signIn(email, password)
                AuthMode.SIGN_UP -> session.signUp(email, password, name, phone)
                AuthMode.RESET -> session.resetPassword(email)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                AuthMode.SIGN_IN -> {
                    TextButton(onClick = {
                        session.clearAuthFeedback(); mode = AuthMode.SIGN_UP
                    }) { Text(tr(StrAuth.createAccount), color = Ink.Amber) }
                    Text("·", color = Ink.TextMuted)
                    TextButton(onClick = {
                        session.clearAuthFeedback(); mode = AuthMode.RESET
                    }) { Text(tr(StrAuth.forgotPassword), color = Ink.TextSecondary) }
                }
                else -> TextButton(onClick = {
                    session.clearAuthFeedback(); mode = AuthMode.SIGN_IN
                }) { Text(tr(StrAuth.backToSignIn), color = Ink.Amber) }
            }
        }

        if (mode == AuthMode.SIGN_UP) {
            Text(
                tr(StrAuth.noEmailConfirmation),
                color = Ink.Teal,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            tr(StrAuth.trustLine),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 34.dp),
        )
    }
}

/**
 * Google sign-in entry point.
 *
 * Drawn rather than shipped as an image so it stays crisp at any density and matches the dark
 * 7PRO surface, while keeping Google's four brand colours.
 */
@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GoogleGlyph()
        Spacer(Modifier.width(12.dp))
        Text(
            tr(StrAuth.continueWithGoogle),
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun GoogleGlyph() {
    Canvas(Modifier.size(19.dp)) {
        val stroke = size.minDimension * 0.24f
        val inset = stroke / 2f
        val arc = Size(size.width - stroke, size.height - stroke)
        val origin = Offset(inset, inset)
        // The mark is four arcs plus the crossbar, in Google's own palette.
        listOf(
            Color(0xFFEA4335) to (150f to 90f),
            Color(0xFFFBBC05) to (240f to 60f),
            Color(0xFF34A853) to (300f to 90f),
            Color(0xFF4285F4) to (30f to 60f),
        ).forEach { (color, sweep) ->
            drawArc(
                color = color,
                startAngle = sweep.first,
                sweepAngle = sweep.second,
                useCenter = false,
                topLeft = origin,
                size = arc,
                style = Stroke(width = stroke),
            )
        }
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, size.height * 0.5f),
            end = Offset(size.width * 0.95f, size.height * 0.5f),
            strokeWidth = stroke,
        )
    }
}

@Composable
fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Ink.Surface,
            unfocusedContainerColor = Ink.Surface,
            focusedBorderColor = Ink.Amber,
            unfocusedBorderColor = Ink.Hairline,
            focusedLabelColor = Ink.Amber,
            unfocusedLabelColor = Ink.TextMuted,
            cursorColor = Ink.Amber,
            focusedTextColor = Ink.TextPrimary,
            unfocusedTextColor = Ink.TextPrimary,
        ),
    )
}
