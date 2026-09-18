package com.auracommunityact.missiongtamobile.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeStatus
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import com.auracommunityact.missiongtamobile.storage.ResourceStatus

@Composable
fun GameHomeScreen(
    runtimeProvider: GameRuntimeProvider,
    resourceManager: GameResourceManager,
    onStartGame: () -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    val runtimeStatus by runtimeProvider.status.collectAsState()
    val resourceStatus by resourceManager.status.collectAsState()
    val missingFiles by resourceManager.missingFiles.collectAsState()
    val selectedUri by resourceManager.selectedUri.collectAsState()
    
    val context = LocalContext.current

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            resourceManager.takePersistableUriPermission(uri)
        }
    }

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
                text = "SELECT GAME DATA",
                onClick = { documentTreeLauncher.launch(null) }
            )

            MenuButton(
                text = "START",
                onClick = {
                    if (resourceStatus != ResourceStatus.READY) {
                        Toast.makeText(context, "Game data is not ready.", Toast.LENGTH_SHORT).show()
                    } else if (runtimeStatus != GameRuntimeStatus.READY && runtimeStatus != GameRuntimeStatus.RUNNING) {
                        Toast.makeText(context, "Native game runtime is not installed.", Toast.LENGTH_SHORT).show()
                        onStartGame() // Let it navigate to show the "Runtime Unavailable" screen for the prototype
                    } else {
                        onStartGame()
                    }
                },
                enabled = true // Always clickable to show Toast or navigate to stub
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
                    text = "MISSION GTA MOBILE",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Divider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Runtime:",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "PENDING",
                    color = Color(0xFFFFB300),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Game Data:",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = resourceStatus.displayName,
                    color = if (resourceStatus == ResourceStatus.READY) Color(0xFF4CAF50) else if (resourceStatus == ResourceStatus.NOT_CONFIGURED) Color.Gray else Color(0xFFE57373),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (resourceStatus == ResourceStatus.READY) {
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    StatusRow("Validation:", "PASSED", true)
                    Text(
                        text = "All required root files and directories are present.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (resourceStatus == ResourceStatus.MISSING_REQUIRED_RESOURCES) {
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Missing Files/Directories:",
                        color = Color(0xFFE57373),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Column(modifier = Modifier.heightIn(max = 100.dp)) {
                        missingFiles.take(5).forEach { file ->
                            Text(text = "- $file", color = Color.LightGray, fontSize = 12.sp)
                        }
                        if (missingFiles.size > 5) {
                            Text(text = "...and ${missingFiles.size - 5} more", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else if (resourceStatus != ResourceStatus.NOT_CONFIGURED && resourceStatus != ResourceStatus.SCANNING && resourceStatus != ResourceStatus.SELECTED) {
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Please select a valid game-data folder.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.DarkGray,
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
