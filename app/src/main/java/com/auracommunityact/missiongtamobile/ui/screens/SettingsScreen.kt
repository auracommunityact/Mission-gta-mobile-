package com.auracommunityact.missiongtamobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import com.auracommunityact.missiongtamobile.storage.GameSettingsManager
import com.auracommunityact.missiongtamobile.storage.LandscapeOrientationMode

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    resourceManager: GameResourceManager,
    settingsManager: GameSettingsManager = GameSettingsManager.getInstance(LocalContext.current)
) {
    val selectedUri by resourceManager.selectedUri.collectAsState()
    val forceLandscape by settingsManager.forceLandscape.collectAsState()
    val landscapeMode by settingsManager.landscapeMode.collectAsState()
    val dpadHaptics by settingsManager.dpadHaptics.collectAsState()
    val dpadSizeDp by settingsManager.dpadSizeDp.collectAsState()
    val dpadOpacity by settingsManager.dpadOpacity.collectAsState()

    var showOrientationDialog by remember { mutableStateOf(false) }

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            resourceManager.takePersistableUriPermission(uri)
        }
    }

    if (showOrientationDialog) {
        LandscapeModeSelectionDialog(
            currentMode = landscapeMode,
            onModeSelected = { mode ->
                settingsManager.setLandscapeMode(mode)
                showOrientationDialog = false
            },
            onDismiss = { showOrientationDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Deep Navy Dark
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

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .padding(20.dp)
        ) {
            item {
                SettingsCategory("DISPLAY & ORIENTATION")

                SettingsToggleRow(
                    title = "Force Landscape in Gameplay",
                    subtitle = "Lock orientation to landscape mode during gameplay for a consistent control layout and viewport",
                    checked = forceLandscape,
                    onCheckedChange = { settingsManager.setForceLandscape(it) },
                    testTag = "force_landscape_switch"
                )

                if (forceLandscape) {
                    SettingsOptionRow(
                        title = "Landscape Orientation Mode",
                        currentValue = landscapeMode.displayName,
                        subtitle = landscapeMode.description,
                        onClick = { showOrientationDialog = true },
                        testTag = "landscape_mode_selector"
                    )
                }

                SettingsRow("Fullscreen Mode", "Enabled")
                SettingsRow("Resolution Scale", "Coming with runtime integration")
                SettingsRow("Graphics Quality", "Coming with runtime integration")
                SettingsRow("FPS Limit", "Coming with runtime integration")
                SettingsRow("VSync", "Coming with runtime integration")

                Spacer(modifier = Modifier.height(28.dp))

                SettingsCategory("GAME DATA")
                SettingsRow("Current Location", selectedUri?.toString() ?: "None configured")

                if (selectedUri != null) {
                    ActionRow("Re-scan Resources") { resourceManager.validateResources(selectedUri!!) }
                }

                ActionRow("Change Resource Folder") { documentTreeLauncher.launch(null) }

                if (selectedUri != null) {
                    ActionRow("Remove Resource Permission", Color(0xFFEF4444)) { resourceManager.clearPermission() }
                }

                Spacer(modifier = Modifier.height(28.dp))

                SettingsCategory("PERFORMANCE")
                SettingsRow("Performance Overlay", "Enabled (In-Game HUD)")
                SettingsRow("Texture Quality", "Coming with runtime integration")
                SettingsRow("Render Scale", "Coming with runtime integration")

                Spacer(modifier = Modifier.height(28.dp))

                SettingsCategory("CONTROLS")
                SettingsRow("On-Screen D-Pad", "Enabled (8-Way Directional)")
                SettingsToggleRow(
                    title = "D-Pad Tactile Haptics",
                    subtitle = "Trigger tactile haptic ticks when changing directional inputs",
                    checked = dpadHaptics,
                    onCheckedChange = { settingsManager.setDpadHaptics(it) },
                    testTag = "dpad_haptics_switch"
                )
                SettingsRow("D-Pad Dimensions", "${dpadSizeDp}dp")
                SettingsRow("D-Pad Opacity", "${(dpadOpacity * 100).toInt()}%")
                SettingsRow("Controller Support", "Ready (Standard Gamepad)")
                SettingsRow("Stick Sensitivity", "1.0x (Standard)")

                Spacer(modifier = Modifier.height(28.dp))

                SettingsCategory("SYSTEM")
                SettingsRow("Runtime Status", "Pending")
                SettingsRow("App Version", "0.1")
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2563EB),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF0F172A)
            )
        )
    }
}

@Composable
private fun SettingsOptionRow(
    title: String,
    currentValue: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier
        ) {
            Text(
                text = currentValue,
                color = Color(0xFF60A5FA),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LandscapeModeSelectionDialog(
    currentMode: LandscapeOrientationMode,
    onModeSelected: (LandscapeOrientationMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Select Landscape Mode",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose how the gameplay orientation behaves when rotating your device:",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                LandscapeOrientationMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF3B82F6),
                                unselectedColor = Color(0xFF64748B)
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = mode.displayName,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = mode.description,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ActionRow(label: String, color: Color = Color(0xFF60A5FA), onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsCategory(name: String) {
    Text(
        text = name,
        color = Color(0xFF38BDF8), // Light Sky/Navy accent
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 15.sp)
        Text(text = value, color = Color(0xFF94A3B8), fontSize = 13.sp)
    }
}
