package com.auracommunityact.missiongtamobile.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeProvider
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class CacheDirectoryResult {
    data class Success(val path: String, val created: Boolean) : CacheDirectoryResult()
    data class Failure(val error: String, val targetPath: String) : CacheDirectoryResult()
}

class GameResourceManager(
    private val context: Context,
    private val runtimeProvider: GameRuntimeProvider? = null
) {
    private val prefs = context.getSharedPreferences("mission_gta_prefs", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(ResourceStatus.NOT_CONFIGURED)
    val status: StateFlow<ResourceStatus> = _status.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _missingFiles = MutableStateFlow<List<String>>(emptyList())
    val missingFiles: StateFlow<List<String>> = _missingFiles.asStateFlow()

    private val _cacheError = MutableStateFlow<String?>(null)
    val cacheError: StateFlow<String?> = _cacheError.asStateFlow()

    private val _cachePath = MutableStateFlow<String?>(null)
    val cachePath: StateFlow<String?> = _cachePath.asStateFlow()

    init {
        val savedUriString = prefs.getString("resource_uri", null)
        if (savedUriString != null) {
            val uri = Uri.parse(savedUriString)
            _selectedUri.value = uri
            validateResources(uri)
        } else {
            _status.value = ResourceStatus.NOT_CONFIGURED
            runtimeProvider?.updateStatus(GameRuntimeStatus.PENDING)
        }
    }

    fun takePersistableUriPermission(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            prefs.edit().putString("resource_uri", uri.toString()).apply()
            _selectedUri.value = uri
            validateResources(uri)
        } catch (e: SecurityException) {
            Log.e("RESOURCE", "Failed to take persistable URI permission", e)
            _status.value = ResourceStatus.ACCESS_DENIED
            runtimeProvider?.updateStatus(GameRuntimeStatus.PENDING)
        }
    }

    fun clearPermission() {
        _selectedUri.value?.let { uri ->
            try {
                if (uri.scheme == "content") {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.releasePersistableUriPermission(uri, flags)
                }
            } catch (e: SecurityException) {
                Log.e("RESOURCE", "Failed to release persistable URI permission", e)
            }
        }
        prefs.edit().remove("resource_uri").apply()
        _selectedUri.value = null
        _missingFiles.value = emptyList()
        _cacheError.value = null
        _cachePath.value = null
        _status.value = ResourceStatus.NOT_CONFIGURED
        runtimeProvider?.updateStatus(GameRuntimeStatus.PENDING)
    }

    fun resolveDocumentRoot(uri: Uri): DocumentFile? {
        return if (uri.scheme == "file") {
            uri.path?.let { DocumentFile.fromFile(File(it)) }
        } else {
            try {
                DocumentFile.fromTreeUri(context, uri)
            } catch (e: Exception) {
                Log.e("RESOURCE", "Failed to resolve tree URI: $uri", e)
                null
            }
        }
    }

    fun ensureDxukCache(documentFile: DocumentFile): CacheDirectoryResult {
        val cacheDirName = GtaVResourceRequirement.DXUK_CACHE_DIR
        val expectedPath = "${documentFile.uri}/$cacheDirName"
        return try {
            val existing = documentFile.findFile(cacheDirName)
            if (existing != null) {
                if (existing.isDirectory) {
                    Log.d("CACHE", "Reusing existing dxuk-cache directory at: ${existing.uri}")
                    _cachePath.value = existing.uri.toString()
                    _cacheError.value = null
                    CacheDirectoryResult.Success(path = existing.uri.toString(), created = false)
                } else {
                    val errMsg = "Existing dxuk-cache is a file, not a directory"
                    Log.e("CACHE", "$errMsg at: ${existing.uri}")
                    _cacheError.value = "$errMsg: ${existing.uri}"
                    CacheDirectoryResult.Failure(error = errMsg, targetPath = existing.uri.toString())
                }
            } else {
                Log.d("CACHE", "dxuk-cache absent, creating directory automatically in: ${documentFile.uri}")
                val created = documentFile.createDirectory(cacheDirName)
                if (created != null && created.exists() && created.isDirectory) {
                    Log.i("CACHE", "Successfully created runtime dxuk-cache directory at: ${created.uri}")
                    _cachePath.value = created.uri.toString()
                    _cacheError.value = null
                    CacheDirectoryResult.Success(path = created.uri.toString(), created = true)
                } else {
                    val errMsg = "Unable to create runtime cache directory"
                    Log.e("CACHE", "$errMsg at: $expectedPath")
                    _cacheError.value = "$errMsg: $expectedPath"
                    CacheDirectoryResult.Failure(error = errMsg, targetPath = expectedPath)
                }
            }
        } catch (e: Exception) {
            val errMsg = "Unable to create runtime cache directory: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.e("CACHE", errMsg, e)
            _cacheError.value = "$errMsg ($expectedPath)"
            CacheDirectoryResult.Failure(error = errMsg, targetPath = expectedPath)
        }
    }

    suspend fun validateResourcesSuspend(uri: Uri) = validateResourcesInternal(uri)

    fun validateResources(uri: Uri) {
        _status.value = ResourceStatus.SCANNING
        _missingFiles.value = emptyList()
        _cacheError.value = null
        runtimeProvider?.updateStatus(GameRuntimeStatus.INITIALIZING)
        
        CoroutineScope(Dispatchers.IO).launch {
            validateResourcesInternal(uri)
        }
    }

    private suspend fun validateResourcesInternal(uri: Uri) {
        try {
            val documentFile = resolveDocumentRoot(uri)
            if (documentFile == null || !documentFile.exists() || !documentFile.canRead()) {
                withContext(Dispatchers.Main) {
                    _status.value = ResourceStatus.DIRECTORY_NOT_FOUND
                    runtimeProvider?.updateStatus(GameRuntimeStatus.PENDING)
                }
                return
            }

            // Step 1: Ensure dxuk-cache/ exists (create automatically if absent, reuse if present)
            val cacheResult = ensureDxukCache(documentFile)
            if (cacheResult is CacheDirectoryResult.Failure) {
                withContext(Dispatchers.Main) {
                    _status.value = ResourceStatus.CACHE_CREATION_FAILED
                    _missingFiles.value = emptyList()
                    runtimeProvider?.updateStatus(GameRuntimeStatus.PENDING)
                }
                return
            }

            // Step 2: Validate genuine required resources (pre-existing game assets)
            val requirement = GtaVResourceRequirement.requirement
            val missingList = mutableListOf<String>()

            // Check directories
            for (dirName in requirement.requiredDirectories) {
                val dirFile = documentFile.findFile(dirName)
                if (dirFile == null || !dirFile.isDirectory) {
                    missingList.add(dirName + "/")
                }
            }

            // Check root files
            for (fileName in requirement.requiredRootFiles) {
                val file = documentFile.findFile(fileName)
                if (file == null || !file.isFile) {
                    missingList.add(fileName)
                }
            }

            withContext(Dispatchers.Main) {
                if (missingList.isNotEmpty()) {
                    Log.w("VALIDATION", "Missing required resources: $missingList")
                    _missingFiles.value = missingList
                    _status.value = ResourceStatus.MISSING_REQUIRED_RESOURCES
                    runtimeProvider?.updateStatus(GameRuntimeStatus.PENDING)
                } else {
                    Log.i("VALIDATION", "All required resources found. Runtime is READY.")
                    _missingFiles.value = emptyList()
                    _status.value = ResourceStatus.READY
                    runtimeProvider?.updateStatus(GameRuntimeStatus.READY)
                }
            }
        } catch (e: Exception) {
            Log.e("RESOURCE", "Validation failed", e)
            withContext(Dispatchers.Main) {
                _status.value = ResourceStatus.VALIDATION_ERROR
                runtimeProvider?.updateStatus(GameRuntimeStatus.PENDING)
            }
        }
    }

    companion object {
        fun sanitizeRelativePath(relativePath: String): String {
            return relativePath.replace("\\", "/").replace("../", "")
        }
    }

    fun getGameDirUri(): String? {
        return prefs.getString("resource_uri", null)
    }

    fun getFile(relativePath: String): DocumentFile? {
        val rootUri = _selectedUri.value ?: return null
        var currentFile: DocumentFile? = resolveDocumentRoot(rootUri)
        
        // Prevent path traversal
        val cleanPath = sanitizeRelativePath(relativePath)
        
        val parts = cleanPath.split("/")
        for (part in parts) {
            if (part.isEmpty()) continue
            currentFile = currentFile?.findFile(part)
            if (currentFile == null) break
        }
        return currentFile
    }
}


