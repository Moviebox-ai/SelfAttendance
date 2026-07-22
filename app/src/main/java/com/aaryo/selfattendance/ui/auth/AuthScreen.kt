package com.aaryo.selfattendance.ui.auth

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.airbnb.lottie.compose.*
import com.aaryo.selfattendance.R
import kotlinx.coroutines.launch
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.remote.RemoteConfigManager

// FIX: Replaced deprecated GoogleSignIn API with new Credential Manager API
// Old: com.google.android.gms:play-services-auth (GoogleSignIn, GoogleSignInOptions, ApiException)
// New: androidx.credentials + com.google.android.libraries.identity.googleid
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@Composable
fun AuthScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: AuthViewModel = viewModel()
    val prefs = remember { com.aaryo.selfattendance.data.local.PreferencesManager(context) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    // Visible error below button — cleared when user starts typing
    var visibleError by remember { mutableStateOf<String?>(null) }

    val error by viewModel.errorMessage.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.login_animation)
    )
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    // FIX: Credential Manager replaces deprecated GoogleSignInClient
    val credentialManager = remember { CredentialManager.create(context) }
    val webClientId = remember { context.getString(R.string.default_web_client_id) }

    // Map raw Firebase error → friendly message
    fun friendlyError(raw: String?): String? = when {
        raw == null -> null
        raw.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
        raw.contains("auth/invalid-credential",   ignoreCase = true) ||
        raw.contains("no user record",             ignoreCase = true) ||
        raw.contains("INVALID_PASSWORD",           ignoreCase = true)
            -> "Email ya password galat hai. Dobara check karein."
        raw.contains("EMAIL_EXISTS",               ignoreCase = true) ||
        raw.contains("email address is already",   ignoreCase = true)
            -> "Yeh email pehle se registered hai. Login karein."
        raw.contains("WEAK_PASSWORD",              ignoreCase = true) ||
        raw.contains("weak-password",              ignoreCase = true)
            -> "Password weak hai. Kam se kam 6 characters use karein."
        raw.contains("INVALID_EMAIL",              ignoreCase = true) ||
        raw.contains("invalid email",              ignoreCase = true)
            -> "Email ka format sahi nahi hai."
        raw.contains("network",                    ignoreCase = true) ||
        raw.contains("NETWORK_ERROR",              ignoreCase = true) ||
        raw.contains("Unable to resolve host",     ignoreCase = true)
            -> "Internet connection check karein aur dobara try karein."
        raw.contains("TOO_MANY_REQUESTS",          ignoreCase = true) ||
        raw.contains("too many",                   ignoreCase = true)
            -> "Bahut zyada attempts. Kuch der baad try karein."
        raw.contains("CONFIGURATION_NOT_FOUND",    ignoreCase = true) ||
        raw.contains("operation-not-allowed",      ignoreCase = true)
            -> "Firebase mein Email/Password sign-in enable nahi hai. Developer se contact karein."
        raw.contains("EMAIL_NOT_FOUND",            ignoreCase = true)
            -> "Is email ka account nahi mila. Pehle Sign Up karein."
        else -> raw
    }

    // Sync ViewModel error → visible text + Snackbar
    LaunchedEffect(error) {
        val msg = friendlyError(error)
        visibleError = msg
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val remoteConfig = remember { RemoteConfigManager.getInstance() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (remoteConfig.showBannerAd()) {
                AdsController.BannerAd()
            }
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(24.dp)
                .padding(paddingValues)
        ) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isLogin) stringResource(R.string.auth_login) else stringResource(R.string.auth_register),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; visibleError = null },
                        label = { Text(stringResource(R.string.auth_email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; visibleError = null },
                        label = { Text(stringResource(R.string.auth_password)) },
                        singleLine = true,
                        visualTransformation =
                            if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector =
                                        if (passwordVisible) Icons.Filled.Visibility
                                        else Icons.Filled.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            visibleError = null
                            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                visibleError = "Email ka format sahi nahi hai."
                                return@Button
                            }
                            if (password.length < 6) {
                                visibleError = "Password kam se kam 6 characters ka hona chahiye."
                                return@Button
                            }
                            if (isLogin) viewModel.login(email, password, navController, prefs)
                            else viewModel.register(email, password, navController, prefs)
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isLogin) stringResource(R.string.auth_login) else stringResource(R.string.auth_register))
                    }

                    // Visible error message below button
                    if (visibleError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = visibleError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            "  OR  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // FIX: Google Sign-In now uses Credential Manager API
                    // Old: GoogleSignIn.getClient() + ActivityResultContracts.StartActivityForResult
                    //      → Deprecated, shows legacy "Choose account" dialog
                    // New: CredentialManager.getCredential() + GetGoogleIdOption
                    //      → Modern bottom sheet, handles passkeys too, Google recommended
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false) // show all accounts
                                        .setServerClientId(webClientId)
                                        .setAutoSelectEnabled(false) // always show picker
                                        .build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    val result = credentialManager.getCredential(
                                        request = request,
                                        context = context
                                    )

                                    val googleIdTokenCredential = GoogleIdTokenCredential
                                        .createFrom(result.credential.data)
                                    val idToken = googleIdTokenCredential.idToken

                                    viewModel.signInWithGoogle(idToken, navController, prefs)

                                } catch (e: GetCredentialCancellationException) {
                                    // User cancelled picker — no error message needed
                                    Log.d("GoogleSignIn", "User cancelled credential picker")
                                } catch (e: GetCredentialException) {
                                    Log.e("GoogleSignIn", "CredentialManager error: ${e.type}", e)
                                    snackbarHostState.showSnackbar(
                                        "Google sign-in failed: ${e.message}"
                                    )
                                } catch (e: Exception) {
                                    Log.e("GoogleSignIn", "Unexpected sign-in error", e)
                                    snackbarHostState.showSnackbar("Error: ${e.message}")
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color(0xFF4285F4),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.auth_google),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (loading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { isLogin = !isLogin }) {
                        Text(
                            if (isLogin) stringResource(R.string.auth_switch_register)
                            else stringResource(R.string.auth_switch_login)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://sites.google.com/view/self-attendance-privacy-policy/home"))
                                )
                            }
                        ) { Text(stringResource(R.string.settings_privacy)) }

                        Text("|", modifier = Modifier.align(Alignment.CenterVertically))

                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://sites.google.com/view/self-terms-and-conditions/home"))
                                )
                            }
                        ) { Text(stringResource(R.string.settings_terms)) }
                    }
                }
            }
        }
    }
}
