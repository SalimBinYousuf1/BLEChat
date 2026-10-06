package com.example.salim.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DecoyNote(
    val title: String,
    val isDone: Boolean = false
)

/**
 * Emergency Panic Camouflage Screen:
 * Converts the entire UI into an innocent, functioning "Simple Notes & Reminders" app.
 * If forced to unlock or inspected by authorities, only innocent grocery notes and reminders are seen.
 * Secret gesture (5 taps on header title or entering exit PIN) exits camouflage.
 */
@Composable
fun CamouflageDecoyScreen(
    onExitCamouflage: () -> Unit
) {
    var titleTapCount by remember { mutableIntStateOf(0) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var newNoteText by remember { mutableStateOf("") }

    val notes = remember {
        mutableStateListOf(
            DecoyNote("Grocery: Almond milk, eggs, olive oil, bread", false),
            DecoyNote("Dentist checkup Tuesday at 3:30 PM", true),
            DecoyNote("Call plumbing repair company for water heater", false),
            DecoyNote("Pick up dry cleaning before weekend", false),
            DecoyNote("Buy running shoes on sale", true)
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddNoteDialog = true },
                containerColor = Color(0xFFFBBF24),
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF9FAFB))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header with secret 5-tap escape hatch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        titleTapCount++
                        if (titleTapCount >= 5) {
                            titleTapCount = 0
                            showUnlockDialog = true
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFFEF3C7), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = Color(0xFFD97706))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Simple Notes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    Text(
                        text = "${notes.size} notes stored locally",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(notes) { index, note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                notes[index] = note.copy(isDone = !note.isDone)
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = if (note.isDone) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (note.isDone) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = note.title,
                                fontSize = 15.sp,
                                color = if (note.isDone) Color(0xFF9CA3AF) else Color(0xFF1F2937),
                                textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { notes.removeAt(index) }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE5E7EB), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("New Note") },
            text = {
                OutlinedTextField(
                    value = newNoteText,
                    onValueChange = { newNoteText = it },
                    placeholder = { Text("Enter note text…") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newNoteText.isNotBlank()) {
                        notes.add(0, DecoyNote(newNoteText.trim()))
                        newNoteText = ""
                        showAddNoteDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00897B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exit Camouflage")
                }
            },
            text = {
                Column {
                    Text("Enter your Salim PIN or tap Resume to return to the encrypted mesh messenger.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        placeholder = { Text("PIN or leave empty") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showUnlockDialog = false
                    onExitCamouflage()
                }) {
                    Text("Resume Salim")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) { Text("Stay in Camouflage") }
            }
        )
    }
}
