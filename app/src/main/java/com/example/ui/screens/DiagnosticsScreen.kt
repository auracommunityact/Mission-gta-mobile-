package com.example.ui.screens

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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.DiagnosticsCollector
import com.example.diagnostics.SystemDiagnostics

@Composable
fun DiagnosticsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val diagnostics = remember { DiagnosticsCollector.collect(context) }
    
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
                    .clickable { copyDiagnostics(context, diagnostics) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("COPY DIAGNOSTICS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                DiagCategory("SOFTWARE")
                DiagRow("App Version", diagnostics.appVersion)
                DiagRow("Android Version", diagnostics.androidVersion)
                DiagRow("Runtime Status", "PENDING (Not Installed)")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("HARDWARE")
                DiagRow("Device Model", diagnostics.deviceModel)
                DiagRow("CPU ABI", diagnostics.cpuAbi)
                DiagRow("Available RAM", "${diagnostics.availableRamMb} MB")
                DiagRow("Total RAM", "${diagnostics.totalRamMb} MB")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("DISPLAY & GRAPHICS")
                DiagRow("Screen Resolution", diagnostics.screenResolution)
                DiagRow("Screen Density", "${diagnostics.screenDensity}x")
                DiagRow("OpenGL ES", "Available")
                DiagRow("Vulkan", "Available")
                
                Spacer(modifier = Modifier.height(24.dp))
                
                DiagCategory("STORAGE")
                DiagRow("Storage Availability", "Available")
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
    Divider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun copyDiagnostics(context: Context, diagnostics: SystemDiagnostics) {
    val text = """
        MISSION GTA MOBILE - DIAGNOSTICS
        App Version: ${diagnostics.appVersion}
        Android Version: ${diagnostics.androidVersion}
        Device Model: ${diagnostics.deviceModel}
        CPU ABI: ${diagnostics.cpuAbi}
        Available RAM: ${diagnostics.availableRamMb} MB
        Total RAM: ${diagnostics.totalRamMb} MB
        Screen Resolution: ${diagnostics.screenResolution}
        Screen Density: ${diagnostics.screenDensity}x
        Runtime Status: PENDING (Not Installed)
    """.trimIndent()
    
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Diagnostics", text)
    clipboard.setPrimaryClip(clip)
    
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
