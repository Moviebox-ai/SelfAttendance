package com.aaryo.selfattendance.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.ads.BannerAd
import com.aaryo.selfattendance.data.remote.RemoteConfigManager

// ─────────────────────────────────────────────
//  About Screen
// ─────────────────────────────────────────────

@Composable
fun AboutScreen(navController: NavController) {

    val primary      = MaterialTheme.colorScheme.primary
    val onPrimary    = MaterialTheme.colorScheme.onPrimary
    val surface      = MaterialTheme.colorScheme.surface
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val background   = MaterialTheme.colorScheme.background
    val muted        = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val divider      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    val remoteConfig = remember { RemoteConfigManager.getInstance() }

    Scaffold(
        topBar = {
            AboutTopBar(
                onBack = { navController.popBackStack() },
                primary = primary,
                onPrimary = onPrimary
            )
        },
        containerColor = background,
        bottomBar = {
            if (remoteConfig.showBannerAd()) {
                BannerAd()
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // ── App Hero Banner ──
            AppHeroBanner(
                primary = primary,
                surface = surface,
                onSurface = onSurface,
                muted = muted
            )

            Spacer(Modifier.height(8.dp))

            // ── Stats Row ──
            StatsRow(
                primary = primary,
                surface = surface,
                onSurface = onSurface,
                muted = muted
            )

            Spacer(Modifier.height(20.dp))

            // ── Features Section ──
            SectionHeader(
                title = "Core Features",
                primary = primary,
                muted = muted
            )

            Spacer(Modifier.height(10.dp))

            FeaturesList(
                primary = primary,
                surface = surface,
                onSurface = onSurface,
                muted = muted,
                divider = divider
            )

            Spacer(Modifier.height(24.dp))

            // ── Security Section ──
            SectionHeader(
                title = "Security",
                primary = primary,
                muted = muted
            )

            Spacer(Modifier.height(10.dp))

            SecuritySection(
                primary = primary,
                surface = surface,
                onSurface = onSurface,
                muted = muted,
                divider = divider
            )

            Spacer(Modifier.height(24.dp))

            // ── Tech Stack ──
            SectionHeader(
                title = "Technology Stack",
                primary = primary,
                muted = muted
            )

            Spacer(Modifier.height(10.dp))

            TechStackGrid(
                primary = primary,
                surface = surface,
                onSurface = onSurface,
                muted = muted
            )

            Spacer(Modifier.height(24.dp))

            // ── Developer Card ──
            DeveloperCard(
                primary = primary,
                onPrimary = onPrimary,
                surface = surface,
                onSurface = onSurface,
                muted = muted
            )

            Spacer(Modifier.height(32.dp))

            // ── Footer ──
            Text(
                text = "Self Attendance Pro  · v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Top Bar
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutTopBar(
    onBack: () -> Unit,
    primary: Color,
    onPrimary: Color
) {
    TopAppBar(
        title = {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    )
}

// ─────────────────────────────────────────────
//  Hero Banner
// ─────────────────────────────────────────────

@Composable
private fun AppHeroBanner(
    primary: Color,
    surface: Color,
    onSurface: Color,
    muted: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        primary.copy(alpha = 0.18f),
                        primary.copy(alpha = 0.04f)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // App Icon — splash_logo from drawable
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Self Attendance",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Text(
                    text = "PRO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = primary,
                    letterSpacing = 4.sp
                )
            }

            Text(
                text = "Apni attendance khud track karo — professionally",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                textAlign = TextAlign.Center
            )

            // Version chip
            Surface(
                shape = RoundedCornerShape(50),
                color = primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "Version 1.0  ·  Android",
                    style = MaterialTheme.typography.labelMedium,
                    color = primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Stats Row
// ─────────────────────────────────────────────

@Composable
private fun StatsRow(
    primary: Color,
    surface: Color,
    onSurface: Color,
    muted: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            Triple("3", "Status\nTypes", Icons.Default.CheckCircle),
            Triple("100%", "Cloud\nSync", Icons.Default.Cloud),
            Triple("5+", "Security\nLayers", Icons.Default.Security),
            Triple("∞", "Records\nStored", Icons.Default.Storage)
        ).forEach { (num, label, icon) ->
            Card(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, onSurface.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = num,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Section Header
// ─────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, primary: Color, muted: Color) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(primary, RoundedCornerShape(2.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = primary
        )
    }
}

// ─────────────────────────────────────────────
//  Features List
// ─────────────────────────────────────────────

private data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val tint: Color = Color.Unspecified
)

@Composable
private fun FeaturesList(
    primary: Color,
    surface: Color,
    onSurface: Color,
    muted: Color,
    divider: Color
) {
    val green  = Color(0xFF22C55E)
    val yellow = Color(0xFFFACC15)
    val blue   = Color(0xFF38BDF8)
    val pink   = Color(0xFFFF4D6D)

    val features = listOf(
        FeatureItem(Icons.Default.DateRange,    "Smart Attendance Marking",       "PRESENT, HALF DAY, ya ABSENT — ek tap mein mark karo. Real-time Firestore sync.", primary),
        FeatureItem(Icons.Default.CurrencyRupee,"Automatic Salary Calculator",    "Per-day, half-day, overtime pay, bonus aur deductions — auto calculate.", green),
        FeatureItem(Icons.Default.AccessTime,   "Overtime Tracking",              "Extra hours count hote hain — automatically salary mein add ho jaate hain.", yellow),
        FeatureItem(Icons.Default.CloudSync,    "Firebase Cloud Backup & Restore","Phone badlo, data nahi jaata. 400-record batch backup + fast restore.", blue),
        FeatureItem(Icons.Default.Notifications,"Daily Smart Reminders",          "Custom time pe daily reminder. WorkManager — reboot ke baad bhi kaam karta hai.", yellow),
        FeatureItem(Icons.Default.Person,       "Personal Profile Setup",         "Naam, salary, hours, overtime rate — Firebase Auth ke saath secure login.", primary),
        FeatureItem(Icons.Default.BarChart,     "Attendance History & Stats",     "Month-wise summary, earned %, effective working days — real-time tracking.", green),
        FeatureItem(Icons.Default.DarkMode,     "Dark & Light Theme",             "Material You — preferences auto-save, app restart ke baad bhi yaad rehta hai.", primary),
        FeatureItem(Icons.Default.Fingerprint,  "Biometric Authentication",       "Fingerprint ya face unlock — app open hote hi verify. Firebase Auth integrated.", pink),
        FeatureItem(Icons.Default.Sync,         "Real-time Data Sync",            "Firestore callbackFlow — internet aate hi data instantly sync.", blue),
        FeatureItem(Icons.Default.Storage,      "Local Room Database",            "Offline support — check-in/check-out records locally. Internet ke bina bhi kaam.", green),
        FeatureItem(Icons.Default.Tune,         "Remote Config Control",          "Features remotely on/off — screenshot lock, toggles — bina update ke.", primary)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, divider)
    ) {
        features.forEachIndexed { idx, feat ->
            FeatureRow(feat = feat, onSurface = onSurface, muted = muted)
            if (idx < features.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 60.dp),
                    color = divider,
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(feat: FeatureItem, onSurface: Color, muted: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    feat.tint.copy(alpha = 0.14f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feat.icon,
                contentDescription = null,
                tint = feat.tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feat.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = feat.desc,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Security Section
// ─────────────────────────────────────────────

@Composable
private fun SecuritySection(
    primary: Color,
    surface: Color,
    onSurface: Color,
    muted: Color,
    divider: Color
) {
    val items = listOf(
        Pair("Root Detection",       "Rooted devices pe security warning dialog dikhata hai — user ko alert karta hai."),
        Pair("Biometric Gate",       "App open hote hi fingerprint/face unlock verify karta hai."),
        Pair("Play Store Validation","Sirf Google Play se install ki APK hi run hoti hai — tampered blocked."),
        Pair("Screenshot Control",   "Remote Config se screenshot allow/block — data leak protection."),
        Pair("Play Integrity API",   "Google Play Integrity check — device aur app authenticity verify."),
        Pair("Debug Detection",      "Debug builds mein ads aur security checks bypass hote hain — production safe.")
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, divider)
    ) {
        items.forEachIndexed { idx, (title, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        lineHeight = 18.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(18.dp)
                )
            }
            if (idx < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 62.dp),
                    color = divider,
                    thickness = 0.5.dp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Tech Stack Grid
// ─────────────────────────────────────────────

@Composable
private fun TechStackGrid(
    primary: Color,
    surface: Color,
    onSurface: Color,
    muted: Color
) {
    val techs = listOf(
        "Kotlin" to "Language",
        "Jetpack Compose" to "UI",
        "Material 3" to "Design",
        "Firebase Auth" to "Auth",
        "Firestore" to "Database",
        "Room DB" to "Local DB",
        "WorkManager" to "Background",
        "Coroutines + Flow" to "Async",
        "Biometric API" to "Security",
        "Play Integrity" to "Security",
        "AdMob + Unity" to "Ads",
        "Lottie" to "Animation",
        "Remote Config" to "Flags",
        "Navigation" to "Routing",
        "Crashlytics" to "Monitoring",
        "iText 7" to "PDF Export"
    )

    // 2-column grid using Column + Row
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        techs.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { (name, cat) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = surface),
                        elevation = CardDefaults.cardElevation(0.dp),
                        border = BorderStroke(1.dp, onSurface.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(primary, CircleShape)
                            )
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onSurface
                                )
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = muted
                                )
                            }
                        }
                    }
                }
                // Agar pair mein sirf 1 item hai toh empty spacer
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Developer Card
// ─────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperCard(
    primary: Color,
    onPrimary: Color,
    surface: Color,
    onSurface: Color,
    muted: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = primary.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Label
            Text(
                text = "DEVELOPER",
                style = MaterialTheme.typography.labelSmall,
                color = primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )

            // Avatar + Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(primary, primary.copy(alpha = 0.6f))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Y",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column {
                    Text(
                        text = "YOGESH",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                    Text(
                        text = "Android Developer  ·  Full Stack",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }

            HorizontalDivider(color = primary.copy(alpha = 0.15f))

            // Bio
            Text(
                text = "Self Attendance Pro ke creator aur sole developer. Yogesh ne yeh app aatmanirbharta ke vision se banaya hai — jaha koi bhi apni attendance, salary aur work hours professionally track kar sake bina kisi third-party tool ke.",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )

            // Skill chips
            val chips = listOf(
                "Kotlin", "Jetpack Compose", "Material 3",
                "Firebase", "MVVM", "App Security"
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { chip ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.labelSmall,
                            color = primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
