package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.storage.GameResourceManager

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit, resourceManager: GameResourceManager) {
    val selectedUri by resourceManager.selectedUri.collectAsState()
    
    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            resourceManager.takePersistableUriPermission(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "SETTINGS",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
                .padding(24.dp)
        ) {
            item {
                SettingsCategory("GAME DATA")
                SettingsRow("Current Location", selectedUri?.toString() ?: "None configured")
                
                if (selectedUri != null) {
                    ActionRow("Re-scan Resources") { resourceManager.validateResources(selectedUri!!) }
                }
                
                ActionRow("Change Resource Folder") { documentTreeLauncher.launch(null) }
                
                if (selectedUri != null) {
                    ActionRow("Remove Resource Permission", Color.Red) { resourceManager.clearPermission() }
                }

                Spacer(modifier = Modifier.height(32.dp))

                SettingsCategory("DISPLAY")
                SettingsRow("Resolution Scale", "Coming with runtime integration")
                SettingsRow("Graphics Quality", "Coming with runtime integration")
                SettingsRow("FPS Limit", "Coming with runtime integration")
                SettingsRow("VSync", "Coming with runtime integration")
                SettingsRow("Fullscreen Mode", "Enabled")
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SettingsCategory("PERFORMANCE")
                SettingsRow("Performance Overlay", "Disabled (Debug Only)")
                SettingsRow("Texture Quality", "Coming with runtime integration")
                SettingsRow("Render Scale", "Coming with runtime integration")
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SettingsCategory("CONTROLS")
                SettingsRow("Touch Controls", "Coming with runtime integration")
                SettingsRow("Controller Support", "Coming with runtime integration")
                SettingsRow("Sensitivity", "Coming with runtime integration")
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SettingsCategory("SYSTEM")
                SettingsRow("Runtime Status", "Pending")
                SettingsRow("App Version", "0.1")
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, color: Color = Color(0xFF64B5F6), onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsCategory(name: String) {
    Text(
        text = name,
        color = Color(0xFFFFB300),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Divider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
        Text(text = value, color = Color.Gray, fontSize = 14.sp)
    }
}
