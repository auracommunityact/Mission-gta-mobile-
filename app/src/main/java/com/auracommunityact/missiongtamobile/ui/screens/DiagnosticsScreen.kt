package com.auracommunityact.missiongtamobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.device.*
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider

@Composable
fun DiagnosticsScreen(onNavigateBack: () -> Unit, resourceManager: GameResourceManager, runtimeProvider: GameRuntimeProvider) {
    val context = LocalContext.current
    
    val deviceCapabilities = remember { DeviceCapabilityProvider.getCapabilities(context) }
    val graphicsCapabilities = remember { GraphicsCapabilityProvider.getCapabilities(context) }
    
    val thermalMonitor = remember { ThermalMonitor(context) }
    val performanceMonitor = remember { PerformanceMonitor(context) }
    
    val thermalState by thermalMonitor.monitor().collectAsState(initial = ThermalState(ThermalStatus.UNAVAILABLE, null))
    val performanceMetrics by performanceMonitor.monitor().collectAsState(initial = PerformanceMetrics(null, null, deviceCapabilities.availableRamMb, false))
    
    val resourceStatus by resourceManager.status.collectAsState()
    val runtimeStatus by runtimeProvider.status.collectAsState()
    
    val gameDirUri = resourceManager.getGameDirUri()
    val dxukState = remember(gameDirUri) { GraphicsCompatibilityManager.checkDxukCache(context, gameDirUri) }
    val driversState = remember(gameDirUri) { GraphicsCompatibilityManager.checkDrivers(context, gameDirUri) }
    
    val profileSelection = remember { DeviceProfileSelector.select(deviceCapabilities, graphicsCapabilities) }
    val activeBackend = remember { GraphicsCompatibilityManager.getActiveBackend(graphicsCapabilities) }

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
                text = "SYSTEM DIAGNOSTICS",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(Color(0xFF333333))
                    .clickable { 
                        val text = """
                            DEVICE
                            Manufacturer: ${deviceCapabilities.manufacturer}
                            Model: ${deviceCapabilities.model}
                            Android: ${deviceCapabilities.androidVersion}
                            RAM: ${deviceCapabilities.totalRamMb} MB
                            
                            GRAPHICS
                            GPU: ${graphicsCapabilities.glRenderer ?: "Unavailable"}
                            Vendor: ${graphicsCapabilities.glVendor ?: "Unavailable"}
                            Vulkan: ${if (graphicsCapabilities.vulkanAvailable) "Available" else "Unavailable"}
                            Vulkan API: ${graphicsCapabilities.vulkanVersion ?: "Unavailable"}
                        """.trimIndent()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Diagnostics", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("COPY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
                .padding(24.dp)
        ) {
            item {
                DiagCategory("DEVICE")
                DiagRow("Manufacturer", deviceCapabilities.manufacturer)
                DiagRow("Model", deviceCapabilities.model)
                DiagRow("Android", "${deviceCapabilities.androidVersion} (API ${deviceCapabilities.apiLevel})")
                DiagRow("Total RAM", "${deviceCapabilities.totalRamMb} MB")
                DiagRow("Available RAM", "${performanceMetrics.availableRamMb} MB")
                DiagRow("CPU Cores", "${deviceCapabilities.cpuCoreCount}")
                DiagRow("CPU ABI", deviceCapabilities.cpuAbi)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("GRAPHICS")
                DiagRow("GPU", graphicsCapabilities.glRenderer ?: "Unavailable")
                DiagRow("Vendor", graphicsCapabilities.glVendor ?: "Unavailable")
                DiagRow("Vulkan", if (graphicsCapabilities.vulkanAvailable) "Available" else "Unavailable")
                DiagRow("Vulkan API", graphicsCapabilities.vulkanVersion ?: "Unavailable")
                DiagRow("OpenGL ES", graphicsCapabilities.glVersion ?: "Unavailable")
                DiagRow("Backend", activeBackend.name)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("PERFORMANCE")
                DiagRow("FPS", performanceMetrics.fps?.let { "%.1f".format(it) } ?: "Unavailable (Not running)")
                DiagRow("Frame Time", performanceMetrics.frameTimeMs?.let { "%.1f ms".format(it) } ?: "Unavailable")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("THERMAL")
                DiagRow("Status", thermalState.status.name)
                DiagRow("Headroom", thermalState.headroom?.let { "%.2f".format(it) } ?: "Unavailable")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("RESOURCE")
                DiagRow("Game Data", resourceStatus.displayName)
                DiagRow("Drivers", if (driversState.present) (if (driversState.readable) "Usable" else "Unreadable") else "Not detected")
                DiagRow("dxuk-cache", if (dxukState.present) (if (dxukState.readable) "Readable" else "Unreadable") else "Not detected")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("PROFILE")
                DiagRow("Selected profile", profileSelection.profile.name)
                DiagRow("Reason", profileSelection.reason)
                
                Spacer(modifier = Modifier.height(24.dp))
                DiagRow("Runtime Status", runtimeStatus.name)
            }
        }
    }
}

@Composable
private fun DiagCategory(name: String) {
    Text(
        text = name,
        color = Color(0xFF64B5F6),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 16.dp))
    }
}
