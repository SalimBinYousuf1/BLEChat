package com.example.salim.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salim.core.model.Peer
import com.example.ui.theme.SalimCyanAccent
import com.example.ui.theme.SalimCyanPrimary
import com.example.ui.theme.SalimSuccessGreen
import com.example.ui.theme.SalimWarningOrange
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Network Mesh Topography Visualizer:
 * Renders live nodes-and-edges topology with animated wireless packets,
 * RSSI signal color coding, battery levels, and node tap selection.
 */
@Composable
fun MeshTopographyVisualizer(
    myNickname: String,
    peers: List<Peer>,
    onSelectPeer: (Peer) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNode by remember { mutableStateOf<Peer?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(peers) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val radius = minOf(centerX, centerY) * 0.72f

                        // Check if tap hit any orbiting peer node
                        var found: Peer? = null
                        val count = peers.size.coerceAtLeast(1)
                        peers.forEachIndexed { idx, p ->
                            val angle = (2 * Math.PI * idx / count).toFloat()
                            val px = centerX + radius * cos(angle)
                            val py = centerY + radius * sin(angle)
                            val dist = kotlin.math.hypot(tapOffset.x - px, tapOffset.y - py)
                            if (dist < 32.dp.toPx()) {
                                found = p
                            }
                        }
                        selectedNode = found
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val orbitRadius = minOf(cx, cy) * 0.72f

            // 1. Draw Range Rings
            drawCircle(
                color = SalimCyanPrimary.copy(alpha = 0.12f),
                radius = orbitRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            )
            drawCircle(
                color = SalimCyanPrimary.copy(alpha = 0.06f),
                radius = orbitRadius * 0.5f,
                center = Offset(cx, cy)
            )

            // 2. Draw Center Node (Self)
            drawCircle(
                color = SalimCyanPrimary.copy(alpha = 0.25f * (1f - phase)),
                radius = 24.dp.toPx() + phase * 18.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = SalimCyanPrimary,
                radius = 16.dp.toPx(),
                center = Offset(cx, cy)
            )

            // 3. Draw Orbiting Peer Nodes and Connecting Edges
            val count = peers.size
            if (count > 0) {
                peers.forEachIndexed { idx, p ->
                    val angle = (2 * Math.PI * idx / count).toFloat()
                    val px = cx + orbitRadius * cos(angle)
                    val py = cy + orbitRadius * sin(angle)

                    // Edge line
                    val edgeColor = if (p.rssi >= -65) SalimSuccessGreen else if (p.rssi >= -82) SalimCyanAccent else SalimWarningOrange
                    drawLine(
                        color = edgeColor.copy(alpha = 0.45f),
                        start = Offset(cx, cy),
                        end = Offset(px, py),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Moving Packet pulse dot along edge
                    val packetProgress = (phase + idx * 0.25f) % 1f
                    val dotX = cx + (px - cx) * packetProgress
                    val dotY = cy + (py - cy) * packetProgress
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )

                    // Peer node circle
                    val isSelected = selectedNode?.id == p.id
                    drawCircle(
                        color = if (isSelected) SalimCyanAccent else edgeColor,
                        radius = if (isSelected) 18.dp.toPx() else 14.dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Selected Node Info Pill Overlay
        selectedNode?.let { node ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = node.nickname, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = SalimSuccessGreen, modifier = Modifier.size(14.dp))
                            Text(text = " ${node.rssi} dBm · ${node.hops} hop", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.BatteryFull, contentDescription = null, tint = SalimCyanPrimary, modifier = Modifier.size(14.dp))
                            Text(text = " ${node.batteryPercent}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = { onSelectPeer(node) },
                        colors = ButtonDefaults.buttonColors(containerColor = SalimCyanPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat", fontSize = 12.sp)
                    }
                }
            }
        } ?: run {
            // Self Legend in corner
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).background(SalimCyanPrimary, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$myNickname (You)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(14.dp), tint = SalimCyanAccent)
                Text(
                    text = " ${peers.size} active mesh peers",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
