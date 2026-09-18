package com.auracommunityact.missiongtamobile.device

import android.content.Context
import androidx.documentfile.provider.DocumentFile

enum class GraphicsBackend {
    SYSTEM_DRIVER, THIRD_PARTY_DRIVER, VULKAN, OPENGL_ES, UNAVAILABLE
}

data class ResourceDirectoryState(
    val present: Boolean,
    val readable: Boolean,
    val itemCount: Int?
)

object GraphicsCompatibilityManager {

    fun checkDxukCache(context: Context, gameDirUri: String?): ResourceDirectoryState {
        if (gameDirUri == null) return ResourceDirectoryState(false, false, null)
        try {
            val root = DocumentFile.fromTreeUri(context, android.net.Uri.parse(gameDirUri))
            val dxuk = root?.findFile("dxuk-cache")
            if (dxuk != null && dxuk.isDirectory) {
                return ResourceDirectoryState(true, dxuk.canRead(), dxuk.listFiles().size)
            }
        } catch(e: Exception) {
            return ResourceDirectoryState(false, false, null)
        }
        return ResourceDirectoryState(false, false, null)
    }

    fun checkDrivers(context: Context, gameDirUri: String?): ResourceDirectoryState {
        if (gameDirUri == null) return ResourceDirectoryState(false, false, null)
        try {
            val root = DocumentFile.fromTreeUri(context, android.net.Uri.parse(gameDirUri))
            val drivers = root?.findFile("Drivers")
            if (drivers != null && drivers.isDirectory) {
                return ResourceDirectoryState(true, drivers.canRead(), drivers.listFiles().size)
            }
        } catch(e: Exception) {
            return ResourceDirectoryState(false, false, null)
        }
        return ResourceDirectoryState(false, false, null)
    }

    fun getActiveBackend(graphics: GraphicsCapabilities): GraphicsBackend {
        return if (graphics.vulkanAvailable) GraphicsBackend.SYSTEM_DRIVER else GraphicsBackend.UNAVAILABLE
    }
}
