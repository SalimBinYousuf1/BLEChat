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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.salim.ui.components.CupertinoSegmentedControl
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Apple-style Profile Inset Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SalimCyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = SalimCyanPrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = identity?.nickname ?: "Salim User",
                        fontSize = 19.sp,
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

        Spacer(modifier = Modifier.height(22.dp))

        // 2. Battery & Mesh Profile (Cupertino Segmented Control Pill)
        SectionHeader(text = stringResource(R.string.mesh_section))
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Duty Cycle Mode",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                val batteryOptions = listOf("Balanced", "Performance", "Saver")
                val selectedIdx = when (batteryMode) {
                    BatteryMode.BALANCED -> 0
                    BatteryMode.PERFORMANCE -> 1
                    BatteryMode.SAVER -> 2
                }
                CupertinoSegmentedControl(
                    items = batteryOptions,
                    selectedIndex = selectedIdx,
                    onItemSelected = { idx ->
                        val mode = when (idx) {
                            0 -> BatteryMode.BALANCED
                            1 -> BatteryMode.PERFORMANCE
                            else -> BatteryMode.SAVER
                        }
                        onSetBatteryMode(mode)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 3. Privacy & Security Inset Group
        SectionHeader(text = stringResource(R.string.privacy_section))
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AppleSwitchRow(
                    icon = Icons.Default.Security,
                    iconBgColor = Color(0xFF34C759),
                    title = stringResource(R.string.read_receipts_title),
                    subtitle = "Show double checkmarks when messages are read",
                    checked = readReceipts,
                    onCheckedChange = onToggleReadReceipts
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                AppleSwitchRow(
                    icon = Icons.Default.Shield,
                    iconBgColor = Color(0xFF007AFF),
                    title = stringResource(R.string.screenshot_protection_title),
                    subtitle = "Prevents screenshots and app previews in recents",
                    checked = flagSecure,
                    onCheckedChange = onToggleFlagSecure
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 4. Storage & Encrypted Backup Inset Group
        SectionHeader(text = "STORAGE & BACKUP")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AppleNavigationRow(
                    icon = Icons.Default.FileDownload,
                    iconBgColor = Color(0xFF5856D6),
                    title = "Export Encrypted Backup",
                    onClick = { showExportBackupDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                AppleNavigationRow(
                    icon = Icons.Default.FileUpload,
                    iconBgColor = Color(0xFFFF9500),
                    title = "Restore from Encrypted Backup",
                    onClick = { showImportBackupDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 5. Help, Threat Model & Diagnostics
        SectionHeader(text = stringResource(R.string.about_section))
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AppleNavigationRow(
                    icon = Icons.Default.Shield,
                    iconBgColor = Color(0xFF30B0C7),
                    title = stringResource(R.string.threat_model_title),
                    onClick = { showThreatModelDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                AppleNavigationRow(
                    icon = Icons.Default.HelpOutline,
                    iconBgColor = Color(0xFFAF52DE),
                    title = stringResource(R.string.faq_title),
                    onClick = { showFaqDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                AppleNavigationRow(
                    icon = Icons.Default.Security,
                    iconBgColor = Color(0xFF8E8E93),
                    title = stringResource(R.string.diagnostics_title),
                    onClick = { showDiagnosticsDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 6. Emergency Panic Wipe Pill Buttons
        Button(
            onClick = { showPanicWipeConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("panic_wipe_button"),
            colors = ButtonDefaults.buttonColors(containerColor = SalimEmergencyRed),
            shape = RoundedCornerShape(26.dp)
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

        // Confirm-less Instant Panic Wipe Shortcut Pill
        TextButton(
            onClick = onPanicWipe,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
        Spacer(modifier = Modifier.height(80.dp))
    }

    // Export Backup Dialog
    if (showExportBackupDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportBackupDialog = false
                exportedBackupString = null
                backupPassphraseInput = ""
            },
            title = { Text("Export Encrypted Backup", fontWeight = FontWeight.Bold) },
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
                            shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (exportedBackupString == null) {
                    Button(
                        shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(12.dp),
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
            title = { Text("Restore from Backup", fontWeight = FontWeight.Bold) },
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupPassphraseInput,
                        onValueChange = { backupPassphraseInput = it },
                        placeholder = { Text("Enter passphrase") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
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
            title = { Text(stringResource(R.string.panic_wipe_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.panic_wipe_desc)) },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
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
            title = { Text(stringResource(R.string.threat_model_title), fontWeight = FontWeight.Bold) },
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
                Button(shape = RoundedCornerShape(12.dp), onClick = { showThreatModelDialog = false }) { Text("Close") }
            }
        )
    }

    // FAQ Dialog
    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text(stringResource(R.string.faq_title), fontWeight = FontWeight.Bold) },
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
                Button(shape = RoundedCornerShape(12.dp), onClick = { showFaqDialog = false }) { Text("Close") }
            }
        )
    }

    // Diagnostics Dialog
    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text(stringResource(R.string.diagnostics_title), fontWeight = FontWeight.Bold) },
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
                Button(shape = RoundedCornerShape(12.dp), onClick = { showDiagnosticsDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun AppleSwitchRow(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SalimCyanPrimary)
        )
    }
}

@Composable
private fun AppleNavigationRow(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}
