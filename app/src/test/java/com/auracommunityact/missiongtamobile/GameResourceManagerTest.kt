package com.auracommunityact.missiongtamobile

import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import com.auracommunityact.missiongtamobile.storage.GtaVResourceRequirement
import org.junit.Assert.*
import org.junit.Test

class GameResourceManagerTest {
    @Test
    fun testPathTraversalProtection() {
        assertEquals("common.rpf", GameResourceManager.sanitizeRelativePath("../../common.rpf"))
        assertEquals("x64/data", GameResourceManager.sanitizeRelativePath("../x64/data"))
        assertEquals("x64/data", GameResourceManager.sanitizeRelativePath("..\\x64\\data"))
        assertEquals("x64/data", GameResourceManager.sanitizeRelativePath("x64/data"))
    }

    @Test
    fun testResourceRequirementsDefined() {
        val req = GtaVResourceRequirement.requirement
        assertTrue(req.requiredDirectories.contains("x64"))
        assertTrue(req.requiredRootFiles.contains("common.rpf"))
        assertTrue(req.requiredRootFiles.contains("x64a.rpf"))
        
        // Log files are excluded by regex
        assertTrue(req.ignoredFilesRegex.any { it.matches("gtau_tty.log") })
        assertTrue(req.ignoredFilesRegex.any { it.matches("gtav_exit_trace_1.bin") })
    }
}
