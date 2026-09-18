package com.auracommunityact.missiongtamobile.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.R
import com.auracommunityact.missiongtamobile.device.*
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StartupScreen(
    runtimeProvider: GameRuntimeProvider,
    resourceManager: GameResourceManager,
    onStartupComplete: () -> Unit
) {
    val context = LocalContext.current
    var loadingState by remember { mutableStateOf("INITIALIZING") }
    var initializationFailed by remember { mutableStateOf(false) }
    
    val runtimeStatus by runtimeProvider.status.collectAsState()
    val resourceStatus by resourceManager.status.collectAsState()

    LaunchedEffect(Unit) {
        try {
            delay(500)
            loadingState = "DETECTING DEVICE"
            val device = withContext(Dispatchers.Default) { DeviceCapabilityProvider.getCapabilities(context) }
            delay(500)
            
            loadingState = "CHECKING GRAPHICS API"
            val graphics = withContext(Dispatchers.Default) { GraphicsCapabilityProvider.getCapabilities(context) }
            delay(500)
            
            loadingState = "SELECTING PROFILE"
            val profile = withContext(Dispatchers.Default) { DeviceProfileSelector.select(device, graphics) }
            delay(500)
            
            loadingState = "CHECKING GAME RUNTIME"
            delay(500)
            
            loadingState = "CHECKING GAME DATA"
            delay(500)
            
            loadingState = "READY"
            delay(500)
            
            onStartupComplete()
        } catch (e: Exception) {
            initializationFailed = true
            loadingState = "Initialization failed: ${e.localizedMessage}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)), // Dark game-style background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp).fillMaxWidth()
        ) {
            // GTA V Branding
            Image(
                painter = painterResource(id = R.drawable.gta_v_logo),
                contentDescription = "GTA V Icon",
                modifier = Modifier.size(160.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "GTA V Mobile",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Loading Area
            if (!initializationFailed) {
                CircularProgressIndicator(
                    color = Color(0xFF64B5F6),
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = loadingState,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    text = loadingState,
                    color = Color(0xFFE57373),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Aura Community Act Branding and Disclaimer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.9f)
        ) {
            Text(
                text = "Present by Aura Community Act",
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Image(
                painter = painterResource(id = R.drawable.aura_community_act_logo),
                contentDescription = "Aura Community Act Icon",
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = androidx.compose.ui.res.stringResource(id = R.string.disclaimer_text),
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

