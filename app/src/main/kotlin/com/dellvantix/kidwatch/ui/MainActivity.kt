package com.dellvantix.kidwatch.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dellvantix.kidwatch.receiver.KidWatchAdminReceiver
import com.dellvantix.kidwatch.service.MonitorService
import com.dellvantix.kidwatch.ui.theme.KidWatchTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionsResult(results)
    }

    // Device admin launcher
    private val adminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkAdminStatus(dpm, adminComponent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, KidWatchAdminReceiver::class.java)

        viewModel.checkAdminStatus(dpm, adminComponent)

        setContent {
            KidWatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    when (uiState.screen) {
                        Screen.CONSENT -> ConsentScreen(
                            onAccept = { viewModel.onConsentAccepted() }
                        )
                        Screen.SETUP -> SetupScreen(
                            uiState = uiState,
                            onRequestPermissions = {
                                permissionLauncher.launch(REQUIRED_PERMISSIONS)
                            },
                            onRequestAdmin = {
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                    putExtra(
                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        "KidWatch needs device admin to prevent unauthorized removal."
                                    )
                                }
                                adminLauncher.launch(intent)
                            },
                            onEnterParentPin = { pin ->
                                viewModel.saveParentPin(pin)
                            },
                            onStartMonitoring = {
                                startMonitorService()
                                viewModel.onMonitoringStarted()
                            }
                        )
                        Screen.ACTIVE -> ActiveScreen(
                            deviceId = uiState.deviceId
                        )
                    }
                }
            }
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorService::class.java)
        startForegroundService(intent)
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

// ─────────────────────────────────────────────
// Consent Screen
// ─────────────────────────────────────────────
@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Icon(
            imageVector = Icons.Outlined.ChildCare,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "KidWatch",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Parental Monitoring — by Dellvantix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Consent & Disclosure",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This application monitors the following activities on this device and shares them with the registered parent/guardian:\n\n" +
                            "• Screenshots captured every 30–60 seconds\n" +
                            "• Incoming and outgoing SMS messages\n" +
                            "• Call logs (incoming, outgoing, missed)\n\n" +
                            "This app must only be installed on a device owned by a minor with full knowledge and consent of their parent or guardian.\n\n" +
                            "By continuing, you confirm you are the parent/guardian of the device owner and have their knowledge and consent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I Understand & Consent — Continue")
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────
// Setup Screen
// ─────────────────────────────────────────────
@Composable
fun SetupScreen(
    uiState: MainUiState,
    onRequestPermissions: () -> Unit,
    onRequestAdmin: () -> Unit,
    onEnterParentPin: (String) -> Unit,
    onStartMonitoring: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Setup KidWatch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Complete all steps to activate monitoring.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        // Step 1: Permissions
        SetupStep(
            number = "1",
            title = "Grant Permissions",
            subtitle = "SMS, Call Logs, Notifications",
            isDone = uiState.permissionsGranted,
            action = if (!uiState.permissionsGranted) ({ onRequestPermissions() }) else null,
            actionLabel = "Grant"
        )

        // Step 2: Device Admin
        SetupStep(
            number = "2",
            title = "Enable Device Admin",
            subtitle = "Prevents app removal without parent PIN",
            isDone = uiState.isDeviceAdmin,
            action = if (!uiState.isDeviceAdmin) ({ onRequestAdmin() }) else null,
            actionLabel = "Enable"
        )

        // Step 3: Parent PIN
        SetupStep(
            number = "3",
            title = "Set Parent PIN",
            subtitle = "Required to disable or remove KidWatch",
            isDone = uiState.pinSaved
        )

        if (!uiState.pinSaved) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it },
                        label = { Text("Parent PIN (4–6 digits)") },
                        isError = pinError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { if (it.length <= 6) pinConfirm = it },
                        label = { Text("Confirm PIN") },
                        isError = pinError,
                        supportingText = if (pinError) ({ Text("PINs do not match") }) else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (pinInput.length >= 4 && pinInput == pinConfirm) {
                                onEnterParentPin(pinInput)
                                pinError = false
                            } else {
                                pinError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save PIN") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Start button
        val allReady = uiState.permissionsGranted && uiState.isDeviceAdmin && uiState.pinSaved
        Button(
            onClick = onStartMonitoring,
            enabled = allReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Monitoring")
        }
    }
}

@Composable
fun SetupStep(
    number: String,
    title: String,
    subtitle: String,
    isDone: Boolean,
    action: (() -> Unit)? = null,
    actionLabel: String = ""
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDone) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(number, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isDone && action != null) {
                TextButton(onClick = action) { Text(actionLabel) }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Active Screen (monitoring is running)
// ─────────────────────────────────────────────
@Composable
fun ActiveScreen(deviceId: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Shield, null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("KidWatch Active", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "This device is being monitored.\nAll activity is synced to the parent dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Card {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Device ID", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(deviceId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Enter this in the parent dashboard", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

enum class Screen { CONSENT, SETUP, ACTIVE }
