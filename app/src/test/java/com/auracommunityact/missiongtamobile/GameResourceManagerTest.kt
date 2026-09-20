package com.auracommunityact.missiongtamobile

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeStatus
import com.auracommunityact.missiongtamobile.storage.CacheDirectoryResult
import com.auracommunityact.missiongtamobile.storage.GameResourceManager
import com.auracommunityact.missiongtamobile.storage.GtaVResourceRequirement
import com.auracommunityact.missiongtamobile.storage.ResourceStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GameResourceManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var runtimeProvider: GameRuntimeProvider
    private lateinit var resourceManager: GameResourceManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("mission_gta_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        runtimeProvider = GameRuntimeProvider()
        resourceManager = GameResourceManager(context, runtimeProvider)
    }

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
        assertFalse("dxuk-cache should not be in requiredDirectories of pre-existing assets", req.requiredDirectories.contains("dxuk-cache"))
        assertTrue("dxuk-cache should be in runtimeCacheDirectories", req.runtimeCacheDirectories.contains("dxuk-cache"))
        assertTrue(req.requiredRootFiles.contains("common.rpf"))
        assertTrue(req.requiredRootFiles.contains("x64a.rpf"))
        
        // Log files are excluded by regex
        assertTrue(req.ignoredFilesRegex.any { it.matches("gtau_tty.log") })
        assertTrue(req.ignoredFilesRegex.any { it.matches("gtav_exit_trace_1.bin") })
    }

    @Test
    fun testDxukCacheAbsent_automaticallyCreated() = runBlocking {
        val gameDir = tempFolder.newFolder("game_data_absent_cache")
        val dxukCacheDir = File(gameDir, "dxuk-cache")
        assertFalse("dxuk-cache should not exist initially", dxukCacheDir.exists())

        val doc = DocumentFile.fromFile(gameDir)
        val result = resourceManager.ensureDxukCache(doc)

        assertTrue("Cache creation should succeed", result is CacheDirectoryResult.Success)
        val success = result as CacheDirectoryResult.Success
        assertTrue("Cache should be newly created", success.created)
        assertTrue("dxuk-cache directory should now exist on disk", dxukCacheDir.exists())
        assertTrue("dxuk-cache must be a directory", dxukCacheDir.isDirectory)
    }

    @Test
    fun testDxukCacheAlreadyExists_reusedWithoutDeletingContents() = runBlocking {
        val gameDir = tempFolder.newFolder("game_data_existing_cache")
        val dxukCacheDir = File(gameDir, "dxuk-cache").apply { mkdir() }
        val shaderFile = File(dxukCacheDir, "dxvk_shader.bin").apply { writeText("compiled_shader_binary") }
        assertTrue("Existing cache file should exist before check", shaderFile.exists())

        val doc = DocumentFile.fromFile(gameDir)
        val result = resourceManager.ensureDxukCache(doc)

        assertTrue("Cache check should succeed", result is CacheDirectoryResult.Success)
        val success = result as CacheDirectoryResult.Success
        assertFalse("Cache should be marked as reused, not newly created", success.created)
        assertTrue("dxuk-cache must still exist", dxukCacheDir.exists())
        assertTrue("Existing cache files must NOT be deleted", shaderFile.exists())
        assertEquals("compiled_shader_binary", shaderFile.readText())
    }

    @Test
    fun testDxukCacheCreationFails_properDiagnosticAndNoFalseResourceReport() = runBlocking {
        val gameDir = tempFolder.newFolder("game_data_cache_fail")
        // Create dxuk-cache as a non-directory file to trigger failure
        val conflictingFile = File(gameDir, "dxuk-cache").apply { createNewFile() }
        assertTrue("Conflicting file must exist", conflictingFile.isFile)

        val doc = DocumentFile.fromFile(gameDir)
        val uri = Uri.fromFile(gameDir)

        resourceManager.validateResourcesSuspend(uri)

        assertEquals(ResourceStatus.CACHE_CREATION_FAILED, resourceManager.status.value)
        assertEquals(GameRuntimeStatus.PENDING, runtimeProvider.status.value)
        assertNotNull("Diagnostic cache error should be reported", resourceManager.cacheError.value)
        assertTrue(
            "Cache error should contain diagnosis",
            resourceManager.cacheError.value!!.contains("dxuk-cache")
        )
        assertFalse(
            "dxuk-cache should NOT be falsely reported in missing game assets list",
            resourceManager.missingFiles.value.contains("dxuk-cache/")
        )
    }

    @Test
    fun testActualRequiredAssetMissing_actualAssetAppearsInDiagnostics() = runBlocking {
        val gameDir = tempFolder.newFolder("game_data_missing_assets")
        // Provide some directories but miss update/ and common.rpf
        File(gameDir, "Config").mkdir()
        File(gameDir, "Drivers").mkdir()
        File(gameDir, "save").mkdir()
        File(gameDir, "x64").mkdir()
        // Missing "update" directory and root .rpf files

        val uri = Uri.fromFile(gameDir)
        resourceManager.validateResourcesSuspend(uri)

        // dxuk-cache should be automatically created and NOT in missingFiles
        val dxukDir = File(gameDir, "dxuk-cache")
        assertTrue("dxuk-cache should be created", dxukDir.exists())

        assertEquals(ResourceStatus.MISSING_REQUIRED_RESOURCES, resourceManager.status.value)
        assertEquals(GameRuntimeStatus.PENDING, runtimeProvider.status.value)

        val missing = resourceManager.missingFiles.value
        assertTrue("Missing files must include update/", missing.contains("update/"))
        assertTrue("Missing files must include common.rpf", missing.contains("common.rpf"))
        assertFalse("Missing files must NOT include dxuk-cache/", missing.contains("dxuk-cache/"))
    }

    @Test
    fun testValidGameData_dxukCacheEnsuredAndRuntimeBecomesReady() = runBlocking {
        val gameDir = tempFolder.newFolder("game_data_valid")
        val req = GtaVResourceRequirement.requirement

        // Create all required directories
        for (dir in req.requiredDirectories) {
            File(gameDir, dir).mkdir()
        }
        // Create all required root files
        for (file in req.requiredRootFiles) {
            File(gameDir, file).createNewFile()
        }

        // dxuk-cache is initially absent
        val dxukDir = File(gameDir, "dxuk-cache")
        assertFalse("dxuk-cache should not exist prior to validation", dxukDir.exists())

        val uri = Uri.fromFile(gameDir)
        resourceManager.validateResourcesSuspend(uri)

        assertTrue("dxuk-cache should be automatically created", dxukDir.exists())
        assertEquals("No files should be missing", emptyList<String>(), resourceManager.missingFiles.value)
        assertEquals(ResourceStatus.READY, resourceManager.status.value)
        assertEquals(GameRuntimeStatus.READY, runtimeProvider.status.value)
    }
}

