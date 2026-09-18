package com.auracommunityact.missiongtamobile.device

enum class DeviceProfile {
    SYSTEM_DEFAULT,
    QUALCOMM_HIGH_END, QUALCOMM_MID_RANGE, QUALCOMM_LOW_END,
    MEDIATEK_HIGH_END, MEDIATEK_MID_RANGE, MEDIATEK_LOW_END,
    OTHER_HIGH_END, OTHER_MID_RANGE, OTHER_LOW_END,
    SAFE_MODE
}

data class ProfileSelectionResult(
    val profile: DeviceProfile,
    val reason: String
)

object DeviceProfileSelector {
    fun select(device: DeviceCapabilities, graphics: GraphicsCapabilities): ProfileSelectionResult {
        val renderer = graphics.glRenderer?.lowercase() ?: ""
        val vendor = graphics.glVendor?.lowercase() ?: ""

        val isAdreno = renderer.contains("adreno") || vendor.contains("qualcomm")
        val isMali = renderer.contains("mali") || vendor.contains("arm")

        val ram = device.totalRamMb

        if (!graphics.vulkanAvailable) {
            return ProfileSelectionResult(DeviceProfile.SAFE_MODE, "Vulkan unavailable, falling back to SAFE_MODE")
        }

        if (isAdreno) {
            return if (ram >= 6000) {
                ProfileSelectionResult(DeviceProfile.QUALCOMM_HIGH_END, "Adreno GPU with >=6GB RAM")
            } else if (ram >= 4000) {
                ProfileSelectionResult(DeviceProfile.QUALCOMM_MID_RANGE, "Adreno GPU with >=4GB RAM")
            } else {
                ProfileSelectionResult(DeviceProfile.QUALCOMM_LOW_END, "Adreno GPU with <4GB RAM")
            }
        } else if (isMali) {
            return if (ram >= 6000) {
                ProfileSelectionResult(DeviceProfile.MEDIATEK_HIGH_END, "Mali GPU with >=6GB RAM")
            } else if (ram >= 4000) {
                ProfileSelectionResult(DeviceProfile.MEDIATEK_MID_RANGE, "Mali GPU with >=4GB RAM")
            } else {
                ProfileSelectionResult(DeviceProfile.MEDIATEK_LOW_END, "Mali GPU with <4GB RAM")
            }
        }
        
        return if (ram >= 6000) {
            ProfileSelectionResult(DeviceProfile.OTHER_HIGH_END, "Unknown GPU with >=6GB RAM")
        } else if (ram >= 4000) {
            ProfileSelectionResult(DeviceProfile.OTHER_MID_RANGE, "Unknown GPU with >=4GB RAM")
        } else {
            ProfileSelectionResult(DeviceProfile.OTHER_LOW_END, "Unknown GPU with <4GB RAM")
        }
    }
}
