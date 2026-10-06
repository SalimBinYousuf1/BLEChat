package com.example.salim.ui.screens

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.salim.core.model.ChatMessage
import com.example.salim.core.model.Conversation
import com.example.salim.core.model.DeliveryStatus
import com.example.salim.core.model.MessageType
import com.example.salim.core.model.Peer
import com.example.salim.ui.SalimViewModel
import com.example.ui.theme.LightOutgoingBubble
import com.example.ui.theme.SalimCyanPrimary
import com.example.ui.theme.SalimEmergencyRed
import com.example.ui.theme.SalimSuccessGreen
import com.example.ui.theme.SalimWarningOrange
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation,
    peer: Peer?,
    messages: List<ChatMessage>,
    viewModel: SalimViewModel,
    onBackClick: () -> Unit,
    onSendMessage: (String, replyToId: String?) -> Unit,
    onSendImage: (Uri, replyToId: String?) -> Unit,
    onSendVoiceNote: (ByteArray, replyToId: String?) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onClearChat: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageForMenu by remember { mutableStateOf<ChatMessage?>(null) }
    var zoomedImageUrl by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    // Audio recording state
    var isRecording by remember { mutableStateOf(false) }
    var currentlyPlayingUri by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendImage(uri, replyingToMessage?.id)
            replyingToMessage = null
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val ok = viewModel.voiceRecorder.startRecording()
            if (ok) isRecording = true
        } else {
            Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = if (conversation.isPublicChannel) Color(0xFF0077B6) else SalimCyanPrimary.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (conversation.isPublicChannel) {
                                Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = Color.White)
                            } else {
                                Text(
                                    text = conversation.title.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = SalimCyanPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = conversation.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (peer?.isVerified == true) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = SalimSuccessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (conversation.isPublicChannel) "Public Broadcast" else if (peer != null) "${peer.hops} hop away · ${peer.signalBucket.name.lowercase()}" else "End-to-End Encrypted",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!conversation.isPublicChannel && peer != null) {
                        IconButton(onClick = onVerifyClick) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = "Verify Contact")
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (peer != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_verify)) },
                                onClick = { showMenu = false; onVerifyClick() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_clear_chat)) },
                            onClick = { showMenu = false; onClearChat() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Status Banners
            if (conversation.isPublicChannel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SalimWarningOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SalimWarningOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.channel_public_banner),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (peer == null && !conversation.isGroup) {
                // Out of direct range
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SalimWarningOrange.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = SalimWarningOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.mesh_waiting_nearby),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SalimSuccessGreen.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SalimSuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End-to-end encrypted with forward secrecy",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Messages LazyColumn
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    InteractiveMessageBubble(
                        message = msg,
                        currentlyPlaying = currentlyPlayingUri == msg.mediaUri,
                        onImageClick = { path -> zoomedImageUrl = path },
                        onPlayVoiceClick = { path ->
                            if (currentlyPlayingUri == path) {
                                viewModel.voicePlayer.stop()
                                currentlyPlayingUri = null
                            } else {
                                currentlyPlayingUri = path
                                viewModel.voicePlayer.play(path) {
                                    currentlyPlayingUri = null
                                }
                            }
                        },
                        onLongClick = { selectedMessageForMenu = msg }
                    )
                }
            }

            // Reply Preview Banner
            replyingToMessage?.let { replyTarget ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SalimCyanPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyTarget.senderNickname}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = SalimCyanPrimary
                        )
                        Text(
                            text = replyTarget.content,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { replyingToMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Voice Recording Bar or Standard Composer
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(SalimEmergencyRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Recording voice note…",
                        fontWeight = FontWeight.SemiBold,
                        color = SalimEmergencyRed,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        viewModel.voiceRecorder.cancelRecording()
                        isRecording = false
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val audioBytes = viewModel.voiceRecorder.stopRecording()
                            isRecording = false
                            if (audioBytes != null && audioBytes.isNotEmpty()) {
                                onSendVoiceNote(audioBytes, replyingToMessage?.id)
                                replyingToMessage = null
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(SalimCyanPrimary, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "Send Recording", tint = Color.White)
                    }
                }
            } else {
                // Regular Composer Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach photo",
                            tint = SalimCyanPrimary
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(stringResource(R.string.type_message_hint)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (inputText.isBlank()) {
                        // Mic button for voice note
                        IconButton(
                            onClick = {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(SalimCyanPrimary.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Record Audio", tint = SalimCyanPrimary)
                        }
                    } else {
                        // Send text button
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotEmpty()) {
                                    onSendMessage(text, replyingToMessage?.id)
                                    inputText = ""
                                    replyingToMessage = null
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(SalimCyanPrimary, CircleShape)
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Zoomed Image Dialog
    zoomedImageUrl?.let { imgPath ->
        AlertDialog(
            onDismissRequest = { zoomedImageUrl = null },
            confirmButton = {
                TextButton(onClick = { zoomedImageUrl = null }) { Text("Close") }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(imgPath),
                        contentDescription = "Full photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        )
    }

    // Long Press Action Sheet
    selectedMessageForMenu?.let { msg ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForMenu = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Message Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Actions: Copy, Reply, Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(msg.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            selectedMessageForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = SalimCyanPrimary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Copy text", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            replyingToMessage = msg
                            selectedMessageForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = SalimCyanPrimary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Reply", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDeleteMessage(msg.id)
                            selectedMessageForMenu = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = SalimEmergencyRed)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Delete for me", fontSize = 16.sp, color = SalimEmergencyRed)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InteractiveMessageBubble(
    message: ChatMessage,
    currentlyPlaying: Boolean,
    onImageClick: (String) -> Unit,
    onPlayVoiceClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val isOutgoing = message.isOutgoing
    val isSos = message.type == MessageType.SOS
    val isImage = message.type == MessageType.IMAGE
    val isAudio = message.type == MessageType.AUDIO

    val bubbleColor = when {
        isSos -> SalimEmergencyRed
        isOutgoing -> LightOutgoingBubble
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isSos || isOutgoing -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (!isOutgoing) {
            Text(
                text = message.senderNickname,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .combinedClickable(
                    onClick = {
                        if (isImage && message.mediaUri != null) onImageClick(message.mediaUri)
                        else if (isAudio && message.mediaUri != null) onPlayVoiceClick(message.mediaUri)
                    },
                    onLongClick = onLongClick
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isSos) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "EMERGENCY SOS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Render Photo if Image
                if (isImage && message.mediaUri != null) {
                    val file = File(message.mediaUri)
                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Photo message",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Render Voice Note Player if Audio
                if (isAudio && message.mediaUri != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { onPlayVoiceClick(message.mediaUri) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (currentlyPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Play/Stop voice",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        // Waveform simulated bars
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val barHeights = listOf(8, 16, 24, 12, 20, 28, 14, 22, 10, 18, 26, 12, 16, 8)
                            barHeights.forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(h.dp)
                                        .background(Color.White.copy(alpha = if (currentlyPlaying) 0.9f else 0.5f), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                } else if (!isImage) {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.75f)
                    )
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            DeliveryStatus.SENDING -> Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Sending",
                                tint = textColor.copy(alpha = 0.75f),
                                modifier = Modifier.size(12.dp)
                            )
                            DeliveryStatus.SENT_TO_MESH -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Sent",
                                tint = textColor.copy(alpha = 0.75f),
                                modifier = Modifier.size(12.dp)
                            )
                            DeliveryStatus.DELIVERED, DeliveryStatus.READ -> Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                tint = if (message.status == DeliveryStatus.READ) Color(0xFF64FFDA) else textColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp)
                            )
                            DeliveryStatus.FAILED -> Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Failed",
                                tint = SalimEmergencyRed,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
