package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.bladeStrings
import com.example.data.model.UserProfile
import com.example.data.remote.SupabaseConfig
import com.example.ui.components.BladeBrandMark
import com.example.ui.theme.BladeRed
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthMode
import com.example.ui.viewmodel.AuthViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel,
    onBack: () -> Unit,
    onAuthenticated: (UserProfile) -> Unit,
    onAdminAuthenticated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val adminUiState by adminViewModel.uiState.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var showGatePrompt by remember { mutableStateOf(false) }
    var gatePassword by remember { mutableStateOf("") }
    var showGatePassword by remember { mutableStateOf(false) }

    var showAdminLoginPopup by remember { mutableStateOf(false) }
    var adminId by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var showAdminPassword by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            BladeBrandMark()
            Spacer(Modifier.size(48.dp))
        }

        // Fast 1-Tap Sign In / Sign Up Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            AuthTabPill(
                label = strings.signIn,
                selected = uiState.mode == AuthMode.SIGN_IN,
                onClick = { authViewModel.setMode(AuthMode.SIGN_IN) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("auth_tab_signin")
            )
            AuthTabPill(
                label = strings.signUp,
                selected = uiState.mode == AuthMode.SIGN_UP,
                onClick = { authViewModel.setMode(AuthMode.SIGN_UP) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("auth_tab_signup")
            )
        }

        Text(
            text = when (uiState.mode) {
                AuthMode.SIGN_IN -> "Welcome back"
                AuthMode.SIGN_UP -> "Create account in seconds"
                AuthMode.FORGOT_PASSWORD -> strings.forgotPassword
            },
            style = MaterialTheme.typography.headlineMedium
        )

        when (uiState.mode) {
            AuthMode.SIGN_IN -> {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { authViewModel.setMode(AuthMode.FORGOT_PASSWORD) }) {
                        Text(strings.forgotPassword, color = BladeRed)
                    }
                }
                Button(
                    onClick = { authViewModel.signInWithEmail(email, password, onAuthenticated) },
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("auth_submit_button")
                ) {
                    if (uiState.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = TextWhite)
                    else Text(strings.signIn, fontWeight = FontWeight.Bold)
                }
            }

            AuthMode.SIGN_UP -> {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Enter your name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_name_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("you@example.com") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (6+ characters)") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_password_input")
                )
                Button(
                    onClick = {
                        authViewModel.signUpWithEmail(
                            name = name,
                            email = email,
                            password = password,
                            onAuthenticated = onAuthenticated
                        )
                    },
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("signup_submit_button")
                ) {
                    if (uiState.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = TextWhite)
                    else Text(strings.signUp, fontWeight = FontWeight.Bold)
                }
            }

            AuthMode.FORGOT_PASSWORD -> {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Registered Email Address") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { authViewModel.sendPasswordReset(email) },
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Send Reset Email")
                }
            }
        }

        uiState.errorMessage?.let { errText ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = errText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (uiState.suggestSignUp && uiState.mode == AuthMode.SIGN_IN) {
                        OutlinedButton(
                            onClick = { authViewModel.setMode(AuthMode.SIGN_UP) },
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("quick_create_account_button")
                        ) {
                            Text(
                                text = "Create Account with this Email",
                                color = BladeRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (uiState.emailAlreadyRegistered && uiState.mode == AuthMode.SIGN_UP) {
                        OutlinedButton(
                            onClick = { authViewModel.setMode(AuthMode.SIGN_IN) },
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("switch_to_signin_button")
                        ) {
                            Text(
                                text = "Sign In Instead",
                                color = BladeRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        uiState.infoMessage?.let { info ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = info,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (uiState.offerBrowserGoogleSignIn && SupabaseConfig.isConfigured) {
            OutlinedButton(
                onClick = {
                    val activity = context.findActivity()
                    authViewModel.launchBrowserGoogleOAuth(activity ?: context)
                },
                enabled = !uiState.isLoading && !uiState.isGoogleLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("google_browser_signin_button")
            ) {
                Text("Continue with Google in Browser", fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        OutlinedButton(
            onClick = {
                val clientId = SupabaseConfig.googleWebClientId
                val activity = context.findActivity()
                if (clientId.isBlank()) {
                    if (SupabaseConfig.isConfigured) {
                        authViewModel.launchBrowserGoogleOAuth(activity ?: context)
                    } else {
                        authViewModel.setError("Configure SUPABASE_URL and GOOGLE_WEB_CLIENT_ID in Secrets to enable Google Sign-In.")
                    }
                    return@OutlinedButton
                }
                if (activity == null) {
                    authViewModel.onGoogleCredentialError(
                        "Unable to open Google Sign-In from this context.",
                        canUseBrowserFallback = SupabaseConfig.isConfigured
                    )
                    return@OutlinedButton
                }
                scope.launch {
                    authViewModel.setGoogleLoading(true)
                    val startedAtMs = SystemClock.elapsedRealtime()
                    try {
                        val cm = CredentialManager.create(activity)
                        val mutableActivityContext = MutableContextWrapper(activity)
                        val buttonOption = GetSignInWithGoogleOption.Builder(clientId).build()
                        val primaryReq = GetCredentialRequest.Builder()
                            .addCredentialOption(buttonOption)
                            .build()
                        val result = try {
                            cm.getCredential(
                                request = primaryReq,
                                context = mutableActivityContext
                            )
                        } catch (primaryErr: GetCredentialException) {
                            when (primaryErr) {
                                is NoCredentialException,
                                is GetCredentialUnsupportedException,
                                is GetCredentialProviderConfigurationException -> {
                                    val oneTapOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(clientId)
                                        .setAutoSelectEnabled(false)
                                        .build()
                                    val fallbackReq = GetCredentialRequest.Builder()
                                        .addCredentialOption(oneTapOption)
                                        .build()
                                    cm.getCredential(
                                        request = fallbackReq,
                                        context = mutableActivityContext
                                    )
                                }
                                else -> throw primaryErr
                            }
                        }

                        val cred = result.credential
                        if (cred is CustomCredential &&
                            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        ) {
                            val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                            authViewModel.signInWithGoogleToken(token, onAuthenticated)
                        } else {
                            authViewModel.onGoogleCredentialError(
                                "Unexpected Google credential response. Please try again.",
                                canUseBrowserFallback = SupabaseConfig.isConfigured
                            )
                        }
                    } catch (_: GetCredentialCancellationException) {
                        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
                        authViewModel.onGoogleCredentialCancelled(
                            fastSystemCancel = elapsedMs < 1000L,
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    } catch (_: NoCredentialException) {
                        authViewModel.onGoogleCredentialError(
                            "No Google account is signed in on this device for one-tap sign-in.",
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    } catch (_: GetCredentialUnsupportedException) {
                        authViewModel.onGoogleCredentialError(
                            "Google one-tap sign-in is not supported on this device.",
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    } catch (_: GetCredentialProviderConfigurationException) {
                        authViewModel.onGoogleCredentialError(
                            "Google Credential provider is unavailable on this device.",
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    } catch (_: GetCredentialInterruptedException) {
                        authViewModel.onGoogleCredentialError(
                            "Google Sign-In was interrupted. Please try again.",
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    } catch (_: GoogleIdTokenParsingException) {
                        authViewModel.onGoogleCredentialError(
                            "Received an invalid Google ID token. Please try again.",
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    } catch (e: CancellationException) {
                        authViewModel.setGoogleLoading(false)
                        throw e
                    } catch (e: GetCredentialException) {
                        authViewModel.onGoogleCredentialError(
                            e.message,
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    } catch (e: Exception) {
                        authViewModel.onGoogleCredentialError(
                            e.message,
                            canUseBrowserFallback = SupabaseConfig.isConfigured
                        )
                    }
                }
            },
            enabled = !uiState.isLoading && !uiState.isGoogleLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("google_signin_button")
        ) {
            if (uiState.isGoogleLoading && !uiState.isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Connecting Google...", maxLines = 1)
                }
            } else {
                Text(strings.googleSignIn, maxLines = 1)
            }
        }
        }

        // Discreet bottom-corner User/Admin ID icon (hidden from normal users)
        IconButton(
            onClick = {
                gatePassword = ""
                showGatePassword = false
                adminViewModel.clearGateError()
                showGatePrompt = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(10.dp)
                .testTag("admin_corner_icon_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Badge,
                contentDescription = "User ID access",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showGatePrompt) {
        AlertDialog(
            onDismissRequest = {
                if (!adminUiState.isVerifyingGate) {
                    showGatePrompt = false
                    gatePassword = ""
                    adminViewModel.clearGateError()
                }
            },
            title = { Text("Enter Access Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = gatePassword,
                        onValueChange = {
                            gatePassword = it
                            if (adminUiState.gateError != null) adminViewModel.clearGateError()
                        },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showGatePassword = !showGatePassword }) {
                                Icon(
                                    imageVector = if (showGatePassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (showGatePassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_gate_password_input")
                    )

                    adminUiState.gateError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("admin_gate_error_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.verifyGatePassword(gatePassword) {
                            gatePassword = ""
                            showGatePrompt = false
                            adminId = ""
                            adminPassword = ""
                            showAdminPassword = false
                            adminViewModel.clearLoginError()
                            showAdminLoginPopup = true
                        }
                    },
                    enabled = !adminUiState.isVerifyingGate,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                    modifier = Modifier.testTag("admin_gate_submit_button")
                ) {
                    if (adminUiState.isVerifyingGate) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = TextWhite, strokeWidth = 2.dp)
                    } else {
                        Text("Submit")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGatePrompt = false
                        gatePassword = ""
                        adminViewModel.clearGateError()
                    },
                    enabled = !adminUiState.isVerifyingGate
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("admin_gate_password_dialog")
        )
    }

    if (showAdminLoginPopup) {
        AlertDialog(
            onDismissRequest = {
                if (!adminUiState.isLoggingIn) {
                    showAdminLoginPopup = false
                    adminPassword = ""
                    adminViewModel.clearLoginError()
                }
            },
            title = { Text("Admin Login") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = adminId,
                        onValueChange = {
                            adminId = it
                            if (adminUiState.loginError != null) adminViewModel.clearLoginError()
                        },
                        label = { Text("Admin Username / User ID") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_id_input")
                    )

                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = {
                            adminPassword = it
                            if (adminUiState.loginError != null) adminViewModel.clearLoginError()
                        },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showAdminPassword = !showAdminPassword }) {
                                Icon(
                                    imageVector = if (showAdminPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle admin password"
                                )
                            }
                        },
                        visualTransformation = if (showAdminPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_password_input")
                    )

                    adminUiState.loginError?.let { loginErr ->
                        Text(
                            text = loginErr,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("admin_login_error_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.loginAdmin(
                            adminId = adminId,
                            password = adminPassword,
                            onSuccess = {
                                adminPassword = ""
                                showAdminLoginPopup = false
                                onAdminAuthenticated()
                            }
                        )
                    },
                    enabled = !adminUiState.isLoggingIn,
                    colors = ButtonDefaults.buttonColors(containerColor = BladeRed, contentColor = TextWhite),
                    modifier = Modifier.testTag("admin_login_submit_button")
                ) {
                    if (adminUiState.isLoggingIn) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = TextWhite, strokeWidth = 2.dp)
                    } else {
                        Text("Log In")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAdminLoginPopup = false
                        adminPassword = ""
                        adminViewModel.clearLoginError()
                    },
                    enabled = !adminUiState.isLoggingIn
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("admin_credentials_dialog")
        )
    }
}

@Composable
private fun AuthTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BladeRed else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) TextWhite else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
