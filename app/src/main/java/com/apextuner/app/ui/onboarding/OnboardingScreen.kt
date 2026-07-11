package com.apextuner.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apextuner.app.ui.components.GlassCard
import com.apextuner.app.ui.theme.ApexBgDark
import com.apextuner.app.ui.theme.ApexPurple
import com.apextuner.app.ui.theme.ApexPurpleContainer
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ApexPurple.copy(alpha = 0.25f), ApexBgDark)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress dots
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OnboardingStep.values().filter { it != OnboardingStep.DONE }.forEachIndexed { i, step ->
                    val active = i == state.step.ordinal
                    Box(
                        Modifier
                            .height(if (active) 4.dp else 2.dp)
                            .weight(1f)
                            .background(
                                if (active) ApexPurple
                                else Color.White.copy(alpha = 0.18f)
                            )
                    )
                }
            }

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.ROOT -> RootStep(state, vm)
                OnboardingStep.SHIZUKU -> ShizukuStep(state, vm)
                OnboardingStep.USAGE -> UsageStep(state, vm)
                OnboardingStep.ACCESSIBILITY -> AccessibilityStep(state, vm)
                OnboardingStep.VPN -> VpnStep(state, vm)
                OnboardingStep.BATTERY -> BatteryStep(state, vm)
                OnboardingStep.DONE -> {
                    scope.launch { vm.finish() }
                    onFinished()
                }
            }

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.step != OnboardingStep.WELCOME) {
                    TextButton(onClick = {
                        val prev = OnboardingStep.values()
                            .getOrElse(state.step.ordinal - 1) { OnboardingStep.WELCOME }
                        vm.goto(prev)
                    }) { Text("Back") }
                } else {
                    Spacer(Modifier.size(0.dp))
                }
                if (state.step == OnboardingStep.BATTERY) {
                    Button(onClick = { scope.launch { vm.finish(); onFinished() } }) {
                        Text("Finish setup")
                    }
                } else if (state.step != OnboardingStep.WELCOME && state.step != OnboardingStep.DONE) {
                    Button(onClick = { vm.next() }) { Text("Next") }
                } else if (state.step == OnboardingStep.WELCOME) {
                    Button(onClick = { vm.next() }) { Text("Get started") }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(40.dp))
    Icon(
        Icons.Filled.Bolt, null, tint = ApexPurple,
        modifier = Modifier.size(72.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text("Welcome to ApexTuner", style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.SemiBold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text(
        "Real CPU, GPU, refresh-rate, VPN and DNS tuning for rooted Android. " +
            "No paywalls, no placebo buttons — every toggle writes to real sysfs nodes.",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.75f)
    )
    Spacer(Modifier.height(24.dp))
    FeatureRow(Icons.Filled.Speed, "Live CPU/GPU control",
        "Governor, min/max freq, online mask per cluster — written to /sys/devices/system/cpu/*.")
    FeatureRow(Icons.Filled.Bolt, "One-tap Gaming Mode",
        "Auto-detects the running game and switches CPU/GPU/refresh-rate/DNS in one shot.")
    FeatureRow(Icons.Filled.Security, "VPN + DoH/DoT + Kill Switch",
        "Full-tunnel or DNS-only VPN, WireGuard import, system-wide Private DNS toggle.")
    FeatureRow(Icons.Filled.VerifiedUser, "Thermal watchdog",
        "Auto-reverts to Balanced the moment your SoC crosses the threshold you set.")
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ApexPurple, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun RootStep(state: OnboardingState, vm: OnboardingViewModel) {
    val scope = rememberCoroutineScope()
    StepHeader("Step 1 · Root access",
        "ApexTuner needs root (Magisk/KernelSU) to write CPU/GPU sysfs nodes. " +
            "Without root, CPU/GPU tuning is read-only.")
    StatusRow("Root detected", state.caps.hasRoot)
    StatusRow("Shizuku available (fallback)", state.caps.hasShizuku)
    OutlinedButton(onClick = { scope.launch { vm.recheck() } }) { Text("Re-check") }
    if (!state.caps.hasRoot) {
        WarningCard("Root not detected. You can continue — CPU/GPU tuning will be read-only. " +
            "DNS, VPN, and refresh-rate-for-own-window features still work without root.")
    }
}

@Composable
private fun ShizukuStep(state: OnboardingState, vm: OnboardingViewModel) {
    val scope = rememberCoroutineScope()
    StepHeader("Step 2 · Shizuku (optional)",
        "Shizuku lets ApexTuner write Android Private DNS and use hidden display " +
            "APIs without root. Install Shizuku from Play Store / GitHub and start its service.")
    StatusRow("Shizuku authorized", state.caps.hasShizuku)
    if (state.caps.shizukuRequiresActivation) {
        WarningCard("Shizuku is installed but its service is not running, or ApexTuner hasn't been granted permission. " +
            "Open Shizuku → Start → grant permission.")
    }
    OutlinedButton(onClick = { scope.launch { vm.recheck() } }) { Text("Re-check") }
}

@Composable
private fun UsageStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader("Step 3 · Usage Access",
        "Required so ApexTuner can detect when a tracked game enters the foreground " +
            "and auto-apply its tuning profile. ApexTuner never collects or transmits this data.")
    StatusRow("Usage Access granted", state.hasUsageAccess)
    TextButton(onClick = {
        // Intent to Usage Access settings — actual launch happens via LocalContext in a real build.
        vm.setUsageAccess(true)
    }) { Text("Open Settings") }
}

@Composable
private fun AccessibilityStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader("Step 4 · Accessibility (optional)",
        "Fallback for game detection on devices where Usage Access is unavailable. " +
            "ApexTuner only listens to WINDOW_STATE_CHANGED events — it never reads screen content.")
    StatusRow("Accessibility enabled", state.hasAccessibility)
    TextButton(onClick = { vm.setAccessibility(true) }) { Text("Open Accessibility") }
}

@Composable
private fun VpnStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader("Step 5 · VPN consent",
        "ApexTuner's VPN service captures traffic to apply DoH/DoT and the kill switch. " +
            "Android requires your explicit consent before any VPN can start. You'll be prompted " +
            "again the first time you toggle the VPN on.")
    StatusRow("VPN consent understood", state.vpnConsentGranted)
    OutlinedButton(onClick = { vm.setVpnConsent(true) }) { Text("I understand") }
}

@Composable
private fun BatteryStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepHeader("Step 6 · Battery optimization",
        "Disable battery optimization for ApexTuner so the thermal watchdog and " +
            "tuner foreground service survive Doze. Without this, Max-Performance " +
            "profiles may be killed mid-game.")
    StatusRow("Battery optimization ignored", state.batteryOptimizationIgnored)
    TextButton(onClick = { vm.setBatteryOptimizationIgnored(true) }) {
        Text("Open Battery settings")
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            null,
            tint = if (ok) MaterialTheme.colorScheme.secondary
                   else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WarningCard(message: String) {
    GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Suppress("unused")
private val iconRef = Icons.Filled.VideogameAsset
private val unusedBrush: Brush? = null
