package com.ryuuflores2006.inventorysystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ryuuflores2006.inventorysystem.data.SupabaseHelper
import com.ryuuflores2006.inventorysystem.ui.components.AppTextField
import com.ryuuflores2006.inventorysystem.ui.components.ErrorBanner
import com.ryuuflores2006.inventorysystem.ui.components.PrimaryButton
import com.ryuuflores2006.inventorysystem.ui.theme.Ash
import com.ryuuflores2006.inventorysystem.ui.theme.Cyan
import com.ryuuflores2006.inventorysystem.ui.theme.GlassBorder
import com.ryuuflores2006.inventorysystem.ui.theme.GlassSurfaceRaised
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToRegister: () -> Unit) {
    AuthShell(
        title = "Welcome back",
        subtitle = "Sign in to your shop account",
        primaryLabel = "Sign in",
        footerPrompt = "No account yet?",
        footerAction = "Register",
        onFooterClick = onNavigateToRegister,
        minPasswordLength = 1,
        submit = { email, password ->
            SupabaseHelper.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        },
        onSuccess = onLoginSuccess
    )
}

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onNavigateToLogin: () -> Unit) {
    AuthShell(
        title = "Staff registration",
        subtitle = "Create an account for this shop. You are signed in straight away — no confirmation email.",
        primaryLabel = "Create account",
        footerPrompt = "Already registered?",
        footerAction = "Sign in",
        onFooterClick = onNavigateToLogin,
        minPasswordLength = 6,
        submit = { email, password ->
            SupabaseHelper.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        },
        onSuccess = onRegisterSuccess
    )
}

/**
 * Login and registration differ only in wording and which GoTrue call they
 * make, so they share one layout — the branding and error handling stay
 * identical between them.
 */
@Composable
private fun AuthShell(
    title: String,
    subtitle: String,
    primaryLabel: String,
    footerPrompt: String,
    footerAction: String,
    onFooterClick: () -> Unit,
    minPasswordLength: Int,
    submit: suspend (email: String, password: String) -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(listOf(Cyan.copy(alpha = 0.28f), Cyan.copy(alpha = 0.06f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Storefront,
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        ErrorBanner(error)

        Spacer(Modifier.height(8.dp))

        AppTextField(
            value = email,
            onValueChange = { email = it.trim(); error = null },
            label = "Email",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Default.Mail
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Ash) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = Ash
                    )
                }
            },
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GlassSurfaceRaised,
                unfocusedContainerColor = GlassSurfaceRaised,
                focusedBorderColor = Cyan,
                unfocusedBorderColor = GlassBorder,
                focusedLabelColor = Cyan,
                unfocusedLabelColor = Ash,
                cursorColor = Cyan
            )
        )

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = primaryLabel,
            busy = isLoading,
            onClick = {
                when {
                    email.isBlank() || !email.contains("@") ->
                        error = "Enter a valid email address."
                    password.length < minPasswordLength ->
                        error = "Password must be at least $minPasswordLength characters."
                    else -> {
                        error = null
                        isLoading = true
                        scope.launch {
                            try {
                                submit(email, password)
                                onSuccess()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                error = e.localizedMessage ?: "Something went wrong. Try again."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(footerPrompt, style = MaterialTheme.typography.bodyMedium, color = Ash)
            TextButton(onClick = onFooterClick) { Text(footerAction, color = Cyan) }
        }
    }
}
