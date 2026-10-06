package com.example.salim.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.salim.core.model.Conversation
import com.example.salim.core.model.Peer
import com.example.salim.ui.components.SosConfirmDialog
import com.example.salim.ui.screens.ChannelsScreen
import com.example.salim.ui.screens.ChatDetailScreen
import com.example.salim.ui.screens.ChatsListScreen
import com.example.salim.ui.screens.ContactVerifyScreen
import com.example.salim.ui.screens.NearbyRadarScreen
import com.example.salim.ui.screens.OnboardingScreen
import com.example.salim.ui.screens.SettingsScreen
import com.example.ui.theme.SalimCyanPrimary

@Composable
fun MainScreen(viewModel: SalimViewModel) {
    val context = LocalContext.current
    val identity by viewModel.identity.collectAsState()
    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activePeers by viewModel.activePeers.collectAsState()
    val batteryMode by viewModel.batteryMode.collectAsState()
    val readReceipts by viewModel.readReceipts.collectAsState()
    val flagSecure by viewModel.flagSecure.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var verifyingPeer by remember { mutableStateOf<Peer?>(null) }
    var showSosDialog by remember { mutableStateOf(false) }

    if (!isOnboardingComplete || identity == null) {
        OnboardingScreen(
            onComplete = { nickname ->
                viewModel.completeOnboarding(nickname)
            }
        )
        return
    }

    // Direct Chat Detail view
    if (currentConvId != null) {
        val conv = conversations.find { it.id == currentConvId } ?: Conversation(
            id = currentConvId!!,
            title = "Chat",
            peerId = currentConvId!!.removePrefix("direct_")
        )
        val peer = activePeers.find { it.id == conv.peerId }

        BackHandler {
            viewModel.closeConversation()
        }

        ChatDetailScreen(
            conversation = conv,
            peer = peer,
            messages = currentMessages,
            viewModel = viewModel,
            onBackClick = { viewModel.closeConversation() },
            onSendMessage = { text, replyToId -> viewModel.sendMessage(text, replyToId) },
            onSendImage = { uri, replyToId -> viewModel.sendImage(uri, replyToId) },
            onSendVoiceNote = { audioBytes, replyToId -> viewModel.sendVoiceNote(audioBytes, replyToId) },
            onDeleteMessage = { msgId -> viewModel.deleteMessage(msgId) },
            onVerifyClick = { verifyingPeer = peer },
            onClearChat = { viewModel.deleteConversation(conv.id) }
        )
        return
    }

    // Contact Verification View
    if (verifyingPeer != null) {
        BackHandler { verifyingPeer = null }

        ContactVerifyScreen(
            identity = identity!!,
            peer = verifyingPeer,
            onBackClick = { verifyingPeer = null },
            onToggleVerified = { currentVerified ->
                verifyingPeer?.let { p ->
                    viewModel.togglePeerVerified(p.id, currentVerified)
                    verifyingPeer = p.copy(isVerified = !currentVerified)
                }
            }
        )
        return
    }

    // Main 4-Tab Screen
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text(stringResource(R.string.tab_chats)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SalimCyanPrimary,
                        indicatorColor = SalimCyanPrimary.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Radar, contentDescription = "Nearby") },
                    label = { Text(stringResource(R.string.tab_nearby)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SalimCyanPrimary,
                        indicatorColor = SalimCyanPrimary.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Campaign, contentDescription = "Channels") },
                    label = { Text(stringResource(R.string.tab_channels)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SalimCyanPrimary,
                        indicatorColor = SalimCyanPrimary.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(stringResource(R.string.tab_settings)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SalimCyanPrimary,
                        indicatorColor = SalimCyanPrimary.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatsListScreen(
                    conversations = conversations,
                    activePeers = activePeers,
                    onConversationClick = { convId -> viewModel.openConversation(convId) },
                    onNewChatClick = { selectedTab = 1 },
                    onPanicWipe = {
                        viewModel.panicWipe {
                            Toast.makeText(context, "All keys and messages erased", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                1 -> NearbyRadarScreen(
                    peers = activePeers,
                    onMessagePeer = { peer -> viewModel.openDirectChatWithPeer(peer) }
                )
                2 -> ChannelsScreen(
                    conversations = conversations,
                    onOpenPublicChannel = { viewModel.openConversation(Conversation.PUBLIC_CHANNEL_ID) },
                    onOpenConversation = { convId -> viewModel.openConversation(convId) },
                    onCreateGroup = { name -> viewModel.createGroup(name) },
                    onTriggerSos = { showSosDialog = true }
                )
                3 -> SettingsScreen(
                    identity = identity,
                    activePeers = activePeers,
                    batteryMode = batteryMode,
                    readReceipts = readReceipts,
                    flagSecure = flagSecure,
                    onSetBatteryMode = { mode -> viewModel.setBatteryMode(mode) },
                    onToggleReadReceipts = { enabled -> viewModel.toggleReadReceipts(enabled) },
                    onToggleFlagSecure = { enabled -> viewModel.toggleFlagSecure(enabled) },
                    onExportBackup = { pass -> viewModel.exportEncryptedBackup(pass) },
                    onImportBackup = { data, pass -> viewModel.importEncryptedBackup(data, pass) },
                    onPanicWipe = {
                        viewModel.panicWipe {
                            Toast.makeText(context, "All keys and messages erased", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }

    if (showSosDialog) {
        SosConfirmDialog(
            onDismiss = { showSosDialog = false },
            onConfirm = {
                showSosDialog = false
                viewModel.triggerEmergencySos()
                Toast.makeText(context, "🚨 EMERGENCY SOS BROADCAST SENT", Toast.LENGTH_LONG).show()
            }
        )
    }
}
