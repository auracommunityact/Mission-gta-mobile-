package com.auracommunityact.missiongtamobile.runtime

import android.net.Uri

data class GameRuntimeConfiguration(
    val resourceUri: Uri,
    val resourceVersion: String,
    val runtimeVersion: String
)
