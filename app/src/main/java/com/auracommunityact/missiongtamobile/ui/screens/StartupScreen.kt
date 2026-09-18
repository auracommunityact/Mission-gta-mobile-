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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracommunityact.missiongtamobile.R
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import kotlinx.coroutines.delay

@Composable
fun StartupScreen(
    runtimeProvider: GameRuntimeProvider,
    resourceManager: GameResourceManager,
    onStartupComplete: () -> Unit
) {
    var loadingState by remember { mutableStateOf("INITIALIZING") }
    var initializationFailed by remember { mutableStateOf(false) }
    
    val runtimeStatus by runtimeProvider.status.collectAsState()
    val resourceStatus by resourceManager.status.collectAsState()

    LaunchedEffect(Unit) {
        try {
            delay(800)
            loadingState = "LOADING APPLICATION"
            delay(1000)
            
            loadingState = "CHECKING GAME RUNTIME"
            delay(800)
            // We just observe the status. For this shell, runtime is pending/not installed.
            
            loadingState = "CHECKING GAME DATA"
            delay(800)
            // Resource manager handles data status in real-time
            
            loadingState = "READY"
            delay(800)
            
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
                text = "Unofficial project. Not affiliated with or endorsed by Rockstar Games.\nGTA V Mobile is an experimental project developed by Aura Community Act.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

