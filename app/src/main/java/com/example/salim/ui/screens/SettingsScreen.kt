package com.example.salim.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.salim.core.crypto.IdentityKeys
import com.example.salim.core.model.BatteryMode
import com.example.salim.core.model.Peer
import com.example.ui.theme.SalimCyanPrimary
import com.example.ui.theme.SalimEmergencyRed

@Composable
fun SettingsScreen(
    identity: IdentityKeys?,
    activePeers: List<Peer>,
    batteryMode: BatteryMode,
    readReceipts: Boolean,
    flagSecure: Boolean,
    onSetBatteryMode: (BatteryMode) -> Unit,
    onToggleReadReceipts: (Boolean) -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit,
    onExportBackup: (passphrase: String) -> String,
    onImportBackup: (backupBase64: String, passphrase: String) -> Boolean,
    onPanicWipe: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showPanicWipeConfirm by remember { mutableStateOf(false) }
    var showThreatModelDialog by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showExportBackupDialog by remember { mutableStateOf(false) }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    var backupPassphraseInput by remember { mutableStateOf("") }
    var backupPayloadInput by remember { mutableStateOf("") }
    var exportedBackupString by remember { mutableStateOf<String?>(null) }
    var versionTapCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Identity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(SalimCyanPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = SalimCyanPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = identity?.nickname ?: "Salim User",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ID: ${identity?.formattedShortId ?: "----"}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Battery & Mesh Profile
        Text(
            text = stringResource(R.string.mesh_section),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                BatteryOptionRow(
                    title = stringResource(R.string.battery_balanced),
                    selected = batteryMode == BatteryMode.BALANCED,
                    onClick = { onSetBatteryMode(BatteryMode.BALANCED) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                BatteryOptionRow(
                    title = stringResource(R.string.battery_performance),
                    selected = batteryMode == BatteryMode.PERFORMANCE,
                    onClick = { onSetBatteryMode(BatteryMode.PERFORMANCE) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                BatteryOptionRow(
                    title = stringResource(R.string.battery_saver),
                    selected = batteryMode == BatteryMode.SAVER,
                    onClick = { onSetBatteryMode(BatteryMode.SAVER) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Privacy & Security
        Text(
            text = stringResource(R.string.privacy_section),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.read_receipts_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Show double checkmarks when messages are read",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = readReceipts,
                        onCheckedChange = onToggleReadReceipts,
                        modifier = Modifier.testTag("read_receipts_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.screenshot_protection_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Prevents screenshots and app previews in recents screen",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = flagSecure,
                        onCheckedChange = onToggleFlagSecure,
                        modifier = Modifier.testTag("flag_secure_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Storage & Encrypted Backup
        Text(
            text = "Storage & Backup",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingsClickableRow(
                    icon = Icons.Default.FileDownload,
                    title = "Export Encrypted Backup",
                    onClick = { showExportBackupDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsClickableRow(
                    icon = Icons.Default.FileUpload,
                    title = "Restore from Encrypted Backup",
                    onClick = { showImportBackupDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Help, Threat Model & Diagnostics
        Text(
            text = stringResource(R.string.about_section),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingsClickableRow(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.threat_model_title),
                    onClick = { showThreatModelDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsClickableRow(
                    icon = Icons.Default.HelpOutline,
                    title = stringResource(R.string.faq_title),
                    onClick = { showFaqDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsClickableRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.diagnostics_title),
                    onClick = { showDiagnosticsDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Emergency Panic Wipe Actions
        Button(
            onClick = { showPanicWipeConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("panic_wipe_button"),
            colors = ButtonDefaults.buttonColors(containerColor = SalimEmergencyRed),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.panic_wipe_button),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm-less Panic Wipe Shortcut
        TextButton(
            onClick = onPanicWipe,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, tint = SalimEmergencyRed, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Instant Panic Wipe (No Confirmation)",
                color = SalimEmergencyRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Version footer with secret 7-tap diagnostics trigger
        Text(
            text = "Salim v1.0.0 (Offline Bluetooth Mesh)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    versionTapCount++
                    if (versionTapCount >= 7) {
                        versionTapCount = 0
                        showDiagnosticsDialog = true
                    }
                }
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.height(40.dp))
    }

    // Export Backup Dialog
    if (showExportBackupDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportBackupDialog = false
                exportedBackupString = null
                backupPassphraseInput = ""
            },
            title = { Text("Export Encrypted Backup") },
            text = {
                Column {
                    if (exportedBackupString == null) {
                        Text(
                            text = "Enter a strong passphrase to encrypt your identity keys and local settings into a portable backup.",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = backupPassphraseInput,
                            onValueChange = { backupPassphraseInput = it },
                            placeholder = { Text("Enter passphrase") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Backup generated! Copy this string and save it securely:",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = exportedBackupString!!,
                            onValueChange = {},
                            readOnly = true,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (exportedBackupString == null) {
                    Button(
                        onClick = {
                            val pass = backupPassphraseInput.trim()
                            if (pass.isNotEmpty()) {
                                exportedBackupString = onExportBackup(pass)
                            }
                        }
                    ) {
                        Text("Generate Backup")
                    }
                } else {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(exportedBackupString!!))
                            Toast.makeText(context, "Backup copied to clipboard", Toast.LENGTH_SHORT).show()
                            showExportBackupDialog = false
                            exportedBackupString = null
                            backupPassphraseInput = ""
                        }
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportBackupDialog = false
                    exportedBackupString = null
                    backupPassphraseInput = ""
                }) {
                    Text("Close")
                }
            }
        )
    }

    // Import Backup Dialog
    if (showImportBackupDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportBackupDialog = false
                backupPassphraseInput = ""
                backupPayloadInput = ""
            },
            title = { Text("Restore from Backup") },
            text = {
                Column {
                    Text(
                        text = "Paste your encrypted backup string and enter the passphrase used during export:",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backupPayloadInput,
                        onValueChange = { backupPayloadInput = it },
                        placeholder = { Text("Paste backup data") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupPassphraseInput,
                        onValueChange = { backupPassphraseInput = it },
                        placeholder = { Text("Enter passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val payload = backupPayloadInput.trim()
                        val pass = backupPassphraseInput.trim()
                        if (payload.isNotEmpty() && pass.isNotEmpty()) {
                            val success = onImportBackup(payload, pass)
                            if (success) {
                                Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                                showImportBackupDialog = false
                                backupPayloadInput = ""
                                backupPassphraseInput = ""
                            } else {
                                Toast.makeText(context, "Decryption failed: check passphrase", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportBackupDialog = false
                    backupPayloadInput = ""
                    backupPassphraseInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Panic Wipe Confirmation Dialog
    if (showPanicWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showPanicWipeConfirm = false },
            title = { Text(stringResource(R.string.panic_wipe_confirm)) },
            text = { Text(stringResource(R.string.panic_wipe_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showPanicWipeConfirm = false
                        onPanicWipe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SalimEmergencyRed)
                ) {
                    Text("Erase Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicWipeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Threat Model Dialog
    if (showThreatModelDialog) {
        AlertDialog(
            onDismissRequest = { showThreatModelDialog = false },
            title = { Text(stringResource(R.string.threat_model_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "What is Protected:\n• 1-on-1 chats are encrypted end-to-end with X25519 ECDH + ChaCha20-Poly1305 with forward secrecy.\n• No central servers, phone numbers, or analytics.\n• Keys are generated locally and hardware-wrapped in Android Keystore.\n\nWhat is NOT Protected:\n• Public Channel messages are unencrypted broadcasts readable by anyone in range.\n• Physical possession of an unlocked phone.\n• BLE signal proximity: nearby radio observers can detect that BLE communication is occurring.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showThreatModelDialog = false }) { Text("Close") }
            }
        )
    }

    // FAQ Dialog
    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text(stringResource(R.string.faq_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "How does Salim mesh work?\nDevices establish peer-to-peer Bluetooth connections. If person A cannot directly reach person C, person B forwards the encrypted packet.\n\nWhy does a message say 'Waiting for someone nearby'?\nIf the intended recipient is not currently in mesh range, your device holds the encrypted message and delivers it when they or an intermediate hop comes nearby.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showFaqDialog = false }) { Text("Close") }
            }
        )
    }

    // Diagnostics Dialog
    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                Column {
                    Text("Mesh Peers in Range: ${activePeers.size}")
                    Text("My Peer ID: ${identity?.peerIdHex?.take(8) ?: "N/A"}")
                    Text("Battery Profile: ${batteryMode.name}")
                    Text("Crypto Engine: Bouncy Castle v1.78.1 + Keystore")
                    Text("Transport: BLE Dual Peripheral/Central")
                }
            },
            confirmButton = {
                Button(onClick = { showDiagnosticsDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun BatteryOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SalimCyanPrimary)
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
