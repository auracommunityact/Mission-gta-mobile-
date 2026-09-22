package com.auracommunityact.missiongtamobile.device

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.auracommunityact.missiongtamobile.runtime.logging.DiagnosticLogger
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Manages the lifecycle, file system operations, and extraction of installable/packaged
 * Vulkan drivers (e.g. Turnip packages and custom imported packages), keeping them
 * isolated from Android OS system drivers.
 */
class DriverPackageManager(private val context: Context) {

    val driverStorageDir: File = File(context.filesDir, "drivers").apply {
        if (!exists()) mkdirs()
    }

    val turnipStorageDir: File = File(driverStorageDir, "turnip").apply {
        if (!exists()) mkdirs()
    }

    val customPackagesDir: File = File(driverStorageDir, "packages").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Prepares and returns the built-in Turnip driver package configuration.
     */
    fun getOrCreateDefaultTurnipPackage(
        isAdreno: Boolean,
        isArm64: Boolean,
        tuDebugFlags: String = "noconform"
    ): TurnipDriverPackage {
        val icdFileName = "freedreno_icd.arm64-v8a.json"
        val icdFile = File(turnipStorageDir, icdFileName)

        if (!icdFile.exists()) {
            val libPath = "${turnipStorageDir.absolutePath}/libvulkan_freedreno.so"
            icdFile.writeText(
                """
                {
                    "file_format_version": "1.0.0",
                    "ICD": {
                        "library_path": "$libPath",
                        "api_version": "1.3.0"
                    }
                }
                """.trimIndent()
            )
        }

        return TurnipDriverPackage(
            id = "driver_turnip_adreno",
            name = "Turnip Open-Source Vulkan Driver",
            version = "Mesa 24.x-turnip",
            vendor = "Mesa / Freedreno",
            description = "Optimized open-source Mesa Turnip Vulkan driver for Qualcomm Adreno GPUs.",
            packageDir = turnipStorageDir,
            libraryFileName = "libvulkan_freedreno.so",
            icdFileName = icdFileName,
            mesaVersion = "24.x",
            tuDebugFlags = tuDebugFlags,
            isAdrenoHardware = isAdreno,
            isArm64Supported = isArm64
        )
    }

    /**
     * Lists all installed packaged Vulkan drivers (Turnip + Custom).
     */
    fun getInstalledPackagedDrivers(isAdreno: Boolean, isArm64: Boolean): List<PackagedVulkanDriver> {
        val list = mutableListOf<PackagedVulkanDriver>()

        // 1. Turnip driver package
        list.add(getOrCreateDefaultTurnipPackage(isAdreno, isArm64))

        // 2. Custom packages scanned from disk
        list.addAll(scanCustomPackages())

        return list
    }

    /**
     * Scans and loads metadata from all custom driver packages on disk.
     */
    fun scanCustomPackages(): List<CustomDriverPackage> {
        val packages = mutableListOf<CustomDriverPackage>()
        if (!customPackagesDir.exists() || !customPackagesDir.isDirectory) {
            return packages
        }

        val dirs = customPackagesDir.listFiles()?.filter { it.isDirectory } ?: return packages
        for (dir in dirs) {
            try {
                val pkg = parseCustomPackageDir(dir)
                if (pkg != null) {
                    packages.add(pkg)
                }
            } catch (e: Exception) {
                DiagnosticLogger.logDriver("Error parsing custom driver package in ${dir.name}", e)
            }
        }
        return packages
    }

    private fun parseCustomPackageDir(dir: File): CustomDriverPackage? {
        val metaFile = File(dir, "meta.txt")
        val driverJson = File(dir, "driver.json")

        var name = dir.name
        var author: String? = null
        var version = "1.0"
        var description = "Custom imported driver package in ${dir.name}"

        if (metaFile.exists()) {
            val lines = metaFile.readLines()
            if (lines.isNotEmpty()) name = lines[0]
            if (lines.size > 1) author = lines[1]
            if (lines.size > 2) version = lines[2]
            if (lines.size > 3) description = lines.subList(3, lines.size).joinToString(" ")
        }

        // Find .so library in package dir
        val soFiles = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") } ?: emptyList()
        val libFileName = soFiles.firstOrNull()?.name ?: "vulkan.so"

        // Ensure an ICD json file exists
        val icdFile = File(dir, "icd.json")
        if (!icdFile.exists() && soFiles.isNotEmpty()) {
            val libPath = File(dir, libFileName).absolutePath
            icdFile.writeText(
                """
                {
                    "file_format_version": "1.0.0",
                    "ICD": {
                        "library_path": "$libPath",
                        "api_version": "1.3.0"
                    }
                }
                """.trimIndent()
            )
        }

        return CustomDriverPackage(
            id = "custom_${dir.name}",
            name = name,
            version = version,
            vendor = author ?: "Custom",
            description = description,
            packageDir = dir,
            libraryFileName = libFileName,
            icdFileName = "icd.json",
            author = author
        )
    }

    /**
     * Installs a driver package from a user-selected ZIP URI.
     */
    fun installPackageFromZipUri(uri: Uri): Result<CustomDriverPackage> {
        return try {
            val doc = DocumentFile.fromSingleUri(context, uri)
            val baseName = (doc?.name ?: "driver_${System.currentTimeMillis()}").replace(".zip", "")
            val safeName = sanitizePackageName(baseName)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Unable to open URI stream"))

            inputStream.use { stream ->
                installPackageFromStream(stream, safeName, doc?.name ?: safeName)
            }
        } catch (e: Exception) {
            DiagnosticLogger.logDriver("Failed installing driver ZIP: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts and installs a driver package from an input stream.
     */
    fun installPackageFromStream(
        inputStream: InputStream,
        packageFolderName: String,
        displayName: String
    ): Result<CustomDriverPackage> {
        val targetDir = File(customPackagesDir, packageFolderName).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        return try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(8192)

                while (entry != null) {
                    val entryFile = File(targetDir, entry.name)

                    // Zip slip security prevention
                    if (!entryFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        throw SecurityException("Zip entry attempted directory traversal: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Write metadata file if not already provided in archive
            val metaFile = File(targetDir, "meta.txt")
            if (!metaFile.exists()) {
                metaFile.writeText("$displayName\nUser\n1.0\nImported package: $displayName")
            }

            val parsed = parseCustomPackageDir(targetDir)
                ?: throw IllegalStateException("Failed to parse extracted package")

            DiagnosticLogger.logDriver("Successfully installed custom driver package: ${parsed.name}")
            Result.success(parsed)
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            DiagnosticLogger.logDriver("Failed extracting driver ZIP: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uninstalls and deletes a custom driver package from internal storage.
     */
    fun uninstallPackage(packageId: String): Boolean {
        if (!packageId.startsWith("custom_")) {
            // Cannot uninstall built-in Turnip or System drivers
            return false
        }
        val folderName = packageId.removePrefix("custom_")
        val targetDir = File(customPackagesDir, folderName)
        val success = if (targetDir.exists()) targetDir.deleteRecursively() else false
        if (success) {
            DiagnosticLogger.logDriver("Uninstalled custom driver package: $packageId")
        }
        return success
    }

    private fun sanitizePackageName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
    }
}
