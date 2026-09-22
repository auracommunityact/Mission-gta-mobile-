package com.auracommunityact.missiongtamobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.device.DeviceInfoManager
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.logging.DiagnosticLogger
import com.auracommunityact.missiongtamobile.storage.GameResourceManager

@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    resourceManager: GameResourceManager,
    runtimeProvider: GameRuntimeProvider
) {
    val context = LocalContext.current
    val runtimeManager = runtimeProvider.runtimeManager

    val hw = remember { DeviceInfoManager.getHardwareReport(context) }
    val gameDataManager = runtimeManager?.gameDataManager
    val driverManager = runtimeManager?.driverManager
    val dxvkManager = runtimeManager?.dxvkManager
    val performanceManager = runtimeManager?.performanceManager

    val gameDataStatus by (gameDataManager?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val missingFiles by (gameDataManager?.missingFiles ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val metadata by (gameDataManager?.metadata ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    val driverStatus by (driverManager?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val currentDriver by (driverManager?.currentDriver ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    val dxvkStatus by (dxvkManager?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val dxvkVersion by (dxvkManager?.activeVersion ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    val runtimeStatus by (runtimeManager?.runtimeStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val rendererStatus by (runtimeManager?.rendererStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val gameStatus by (runtimeManager?.gameStatus ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    val perfData by (performanceManager?.performanceData ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    var showLogsTab by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp)
    ) {
        // Header
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showLogsTab) Color(0xFF0284C7) else Color(0xFF333333))
                        .clickable { showLogsTab = !showLogsTab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(if (showLogsTab) "SPECS" else "VIEW LOGS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF333333))
                        .clickable {
                            val text = """
                                === DEVICE ===
                                Manufacturer: ${hw.deviceManufacturer}
                                Model: ${hw.deviceModel}
                                Android: ${hw.androidVersion} (API ${hw.apiLevel})
                                ABI: ${hw.primaryAbi} (arm64: ${hw.isArm64Supported})
                                SoC: ${hw.socManufacturer ?: "N/A"} ${hw.socModel ?: ""}
                                RAM: ${hw.totalRamMb} MB (Available: ${hw.availableRamMb} MB)

                                === GPU & DRIVER ===
                                GPU Renderer: ${hw.gpuRenderer ?: "N/A"}
                                GPU Vendor: ${hw.gpuVendor ?: "N/A"}
                                GPU Family: ${hw.gpuFamily.name}
                                Vulkan: ${if (hw.vulkanSupported) "Supported (${hw.vulkanVersion ?: "N/A"})" else "Unsupported"}
                                Driver Name: ${currentDriver?.name ?: "N/A"}
                                Driver Status: ${driverStatus?.displayName ?: "N/A"}

                                === RUNTIME & DXVK ===
                                Runtime Status: ${runtimeStatus?.displayName ?: "N/A"}
                                Game Process: ${gameStatus?.displayName ?: "N/A"}
                                DXVK Version: ${dxvkVersion ?: "N/A"}
                                DXVK Status: ${dxvkStatus?.displayName ?: "N/A"}

                                === GAME DATA ===
                                Data Status: ${gameDataStatus?.displayName ?: "N/A"}
                                Selected URI: ${gameDataManager?.getSelectedGameDirectory() ?: "None"}
                                Missing Files: ${if (missingFiles.isEmpty()) "None" else missingFiles.joinToString()}
                            """.trimIndent()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Diagnostics", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied diagnostics to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("COPY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (showLogsTab) {
            // Logs View
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(16.dp)
            ) {
                item {
                    Text("RUNTIME LOG (runtime.log)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = DiagnosticLogger.readLogFile(context, "runtime.log"),
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("RENDERER LOG (renderer.log)", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = DiagnosticLogger.readLogFile(context, "renderer.log"),
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("DRIVER LOG (driver.log)", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = DiagnosticLogger.readLogFile(context, "driver.log"),
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            // System Specifications & Diagnostics View
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(20.dp)
            ) {
                item {
                    DiagCategory("DEVICE HARDWARE")
                    DiagRow("Manufacturer", hw.deviceManufacturer)
                    DiagRow("Model", hw.deviceModel)
                    DiagRow("Android", "${hw.androidVersion} (API ${hw.apiLevel})")
                    DiagRow("Primary ABI", hw.primaryAbi)
                    DiagRow("ARM64 Supported", if (hw.isArm64Supported) "YES (arm64-v8a)" else "NO")
                    DiagRow("SoC Vendor", hw.socManufacturer ?: "N/A")
                    DiagRow("SoC Model", hw.socModel ?: "N/A")
                    DiagRow("CPU Cores", "${hw.cpuCoreCount}")
                    DiagRow("Total RAM", "${hw.totalRamMb} MB")
                    DiagRow("Available RAM", "${hw.availableRamMb} MB")

                    Spacer(modifier = Modifier.height(20.dp))

                    DiagCategory("GPU & GRAPHICS")
                    DiagRow("GPU Renderer", hw.gpuRenderer ?: "N/A")
                    DiagRow("GPU Vendor", hw.gpuVendor ?: "N/A")
                    DiagRow("GPU Family", hw.gpuFamily.name)
                    DiagRow("OpenGL ES", hw.openGlVersion ?: "N/A")
                    DiagRow("Max Texture Size", hw.maxTextureSize?.let { "${it}x${it}" } ?: "N/A")
                    DiagRow("Vulkan Support", if (hw.vulkanSupported) "Supported" else "UNSUPPORTED")
                    DiagRow("Vulkan Version", hw.vulkanVersion ?: "N/A")

                    Spacer(modifier = Modifier.height(20.dp))

                    DiagCategory("DRIVER SUBSYSTEM")
                    DiagRow("Active Driver", currentDriver?.name ?: "Detecting")
                    DiagRow("Driver Type", currentDriver?.type?.name ?: "N/A")
                    DiagRow("Driver Version", currentDriver?.version ?: "N/A")
                    DiagRow("Driver Status", driverStatus?.displayName ?: "DETECTING")
                    DiagRow("Adreno Turnip", if (hw.gpuFamily.name == "ADRENO") "Compatible" else "Unsupported on non-Qualcomm")

                    Spacer(modifier = Modifier.height(20.dp))

                    DiagCategory("DXVK & TRANSLATION LAYER")
                    DiagRow("DXVK Version", dxvkVersion ?: "2.4")
                    DiagRow("DXVK Status", dxvkStatus?.displayName ?: "NOT INSTALLED")
                    DiagRow("Shader State Cache", "Enabled (dxuk-cache/)")

                    Spacer(modifier = Modifier.height(20.dp))

                    DiagCategory("GAME DATA STORAGE")
                    DiagRow("Status", gameDataStatus?.displayName ?: "NOT SELECTED")
                    DiagRow("Selected URI", gameDataManager?.getSelectedGameDirectory()?.toString() ?: "None")
                    DiagRow("Cache Directory", metadata?.cacheDirectoryPath ?: "None")
                    DiagRow("Missing Files", if (missingFiles.isEmpty()) "None (All present)" else "${missingFiles.size} items missing")
                    if (missingFiles.isNotEmpty()) {
                        missingFiles.take(4).forEach { f ->
                            DiagRow("  - Missing", f)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    DiagCategory("RUNTIME & RENDERER")
                    DiagRow("Runtime Status", runtimeStatus?.displayName ?: "CHECKING")
                    DiagRow("Renderer Status", rendererStatus?.displayName ?: "INITIALIZING")
                    DiagRow("Game Process", gameStatus?.displayName ?: "STOPPED")
                    DiagRow("FPS", perfData?.fps?.let { "%.1f".format(it) } ?: "N/A (Not running)")
                    DiagRow("Frame Time", perfData?.frameTimeMs?.let { "%.1f ms".format(it) } ?: "N/A")
                    DiagRow("Thermal Status", perfData?.thermalStatus ?: "N/A")
                }
            }
        }
    }
}

@Composable
private fun DiagCategory(name: String) {
    Text(
        text = name,
        color = Color(0xFF64B5F6),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
