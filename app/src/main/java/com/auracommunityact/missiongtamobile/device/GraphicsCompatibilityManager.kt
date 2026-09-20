package com.auracommunityact.missiongtamobile.device

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

enum class GraphicsBackend {
    SYSTEM_DRIVER, THIRD_PARTY_DRIVER, VULKAN, OPENGL_ES, UNAVAILABLE
}

data class ResourceDirectoryState(
    val present: Boolean,
    val readable: Boolean,
    val itemCount: Int?
)

object GraphicsCompatibilityManager {

    private fun resolveRoot(context: Context, gameDirUri: String?): DocumentFile? {
        if (gameDirUri == null) return null
        return try {
            val uri = Uri.parse(gameDirUri)
            if (uri.scheme == "file") {
                uri.path?.let { DocumentFile.fromFile(File(it)) }
            } else {
                DocumentFile.fromTreeUri(context, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun checkDxukCache(context: Context, gameDirUri: String?): ResourceDirectoryState {
        if (gameDirUri == null) return ResourceDirectoryState(false, false, null)
        try {
            val root = resolveRoot(context, gameDirUri)
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
            val root = resolveRoot(context, gameDirUri)
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
