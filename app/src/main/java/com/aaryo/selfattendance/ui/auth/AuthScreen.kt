package com.aaryo.selfattendance.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.airbnb.lottie.compose.*
import com.aaryo.selfattendance.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: AuthViewModel = viewModel()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    val error by viewModel.errorMessage.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.login_animation)
    )
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    // Web Client ID (type 3) from google-services.json
    val webClientId = context.getString(R.string.default_web_client_id)

    // Build GoogleSignInClient - requestIdToken MUST use web client ID
    val googleSignInClient = remember(webClientId) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Activity result launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->

        Log.d("GoogleSignIn", "resultCode = ${result.resultCode}")

        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                Log.d("GoogleSignIn", "account=${account?.email}, idToken null?=${idToken == null}")

                if (idToken != null) {
                    viewModel.signInWithGoogle(idToken, navController)
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "Sign-in failed: No ID token received. Verify SHA-1 in Firebase Console."
                        )
                    }
                }
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "ApiException statusCode=${e.statusCode}", e)
                val msg = when (e.statusCode) {
                    12500 -> "Error 12500: SHA-1 fingerprint not registered in Firebase Console."
                    12501 -> "Sign-in cancelled."
                    12502 -> "Sign-in already in progress."
                    10    -> "Error 10: Developer error. Check SHA-1 and web client ID."
                    else  -> "Google sign-in error: ${e.statusCode}"
                }
                scope.launch { snackbarHostState.showSnackbar(msg) }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Unexpected", e)
                scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") }
            }
        } else {
            // RESULT_CANCELED ya kuch aur
            Log.d("GoogleSignIn", "Non-OK result: ${result.resultCode}, data=${result.data}")
            try {
                // Try to extract error even from non-OK result
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                task.getResult(ApiException::class.java)
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "Non-OK ApiException: ${e.statusCode}", e)
                if (e.statusCode != 12501) { // 12501 = user cancelled, don't show error
                    scope.launch {
                        snackbarHostState.showSnackbar("Sign-in failed (${e.statusCode})")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Show ViewModel errors
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF6C63FF),
                            Color(0xFF4F8CFF),
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
                        text = if (isLogin) "Login" else "Create Account",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
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
                            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                scope.launch { snackbarHostState.showSnackbar("Invalid email address") }
                                return@Button
                            }
                            if (password.length < 6) {
                                scope.launch { snackbarHostState.showSnackbar("Password must be at least 6 characters") }
                                return@Button
                            }
                            if (isLogin) viewModel.login(email, password, navController)
                            else viewModel.register(email, password, navController)
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isLogin) "Login" else "Create Account")
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

                    // Google Sign-In Button
                    OutlinedButton(
                        onClick = {
                            // signOut first (await in coroutine) so account picker always shows
                            scope.launch {
                                try {
                                    googleSignInClient.signOut().await()
                                } catch (_: Exception) {}
                                googleLauncher.launch(googleSignInClient.signInIntent)
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
                                text = "Continue with Google",
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
                            if (isLogin) "Don't have an account? Create one"
                            else "Already have an account? Login"
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
                        ) { Text("Privacy Policy") }

                        Text("|", modifier = Modifier.align(Alignment.CenterVertically))

                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://sites.google.com/view/self-terms-and-conditions/home"))
                                )
                            }
                        ) { Text("Terms & Conditions") }
                    }
                }
            }
        }
    }
}
