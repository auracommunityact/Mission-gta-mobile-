package com.auracommunityact.missiongtamobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun StartupScreen(onStartupComplete: () -> Unit) {
    var loadingText by remember { mutableStateOf("Initializing application...") }
    
    LaunchedEffect(Unit) {
        delay(800)
        loadingText = "Checking resource configuration..."
        delay(800)
        loadingText = "Checking external data..."
        delay(800)
        loadingText = "Checking runtime..."
        delay(800)
        loadingText = "Preparing game surface..."
        delay(800)
        onStartupComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "GTA V MOBILE",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Experimental Mobile Porting Project",
                color = Color.LightGray,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = loadingText,
                color = Color.Gray,
                fontSize = 14.sp,
                letterSpacing = 2.sp
            )
        }
        
        Text(
            text = "Prototype Build 0.1",
            color = Color.DarkGray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
