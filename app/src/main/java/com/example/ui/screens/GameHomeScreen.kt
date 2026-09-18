package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.GameRuntimeProvider
import com.example.runtime.GameRuntimeStatus

@Composable
fun GameHomeScreen(
    runtimeProvider: GameRuntimeProvider,
    onStartGame: () -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    val status by runtimeProvider.status.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Navigation Menu
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(Color(0xFF121212))
                .padding(vertical = 32.dp, horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "MISSION",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "GTA MOBILE",
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            MenuButton(
                text = if (status == GameRuntimeStatus.READY || status == GameRuntimeStatus.RUNNING) "START GAME" else "START (PENDING)",
                onClick = onStartGame
            )
            MenuButton(text = "DIAGNOSTICS", onClick = onDiagnostics)
            MenuButton(text = "SETTINGS", onClick = onSettings)
            MenuButton(text = "EXIT", onClick = onExit)
        }

        // Status Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(Color(0xFF222222))
                    .padding(24.dp)
            ) {
                Text(
                    text = if (status == GameRuntimeStatus.PENDING) "RUNTIME INTEGRATION PENDING" else "SYSTEM READY",
                    color = if (status == GameRuntimeStatus.PENDING) Color(0xFFFFB300) else Color(0xFF4CAF50),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Divider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                StatusRow("Android shell:", "READY", true)
                StatusRow("Landscape mode:", "READY", true)
                StatusRow("Game surface:", "READY", true)
                StatusRow("Input system:", "READY", true)
                StatusRow("Performance monitor:", "READY", true)
                StatusRow("Native runtime:", "NOT INSTALLED", false)
                StatusRow("Game assets:", "NOT INSTALLED", false)
            }
        }
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun StatusRow(label: String, status: String, isReady: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 14.sp)
        Text(
            text = status,
            color = if (isReady) Color(0xFF81C784) else Color(0xFFE57373),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
