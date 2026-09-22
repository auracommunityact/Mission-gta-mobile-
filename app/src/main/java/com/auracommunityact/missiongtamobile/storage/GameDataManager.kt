package com.auracommunityact.missiongtamobile.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.auracommunityact.missiongtamobile.runtime.logging.DiagnosticLogger
import com.auracommunityact.missiongtamobile.runtime.state.GameDataStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GameDataMetadata(
    val directoryUri: String,
    val totalFilesFound: Int,
    val requiredDirectoriesFound: List<String>,
    val requiredRootFilesFound: List<String>,
    val missingItems: List<String>,
    val cacheDirectoryPath: String?,
    val isCacheReady: Boolean
)

data class GameDataValidationResult(
    val status: GameDataStatus,
    val missingFiles: List<String>,
    val errorMessage: String? = null,
    val metadata: GameDataMetadata? = null
)

class GameDataManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("mission_gta_prefs", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(GameDataStatus.NOT_SELECTED)
    val status: StateFlow<GameDataStatus> = _status.asStateFlow()

    private val _selectedDirectoryUri = MutableStateFlow<Uri?>(null)
    val selectedDirectoryUri: StateFlow<Uri?> = _selectedDirectoryUri.asStateFlow()

    private val _missingFiles = MutableStateFlow<List<String>>(emptyList())
    val missingFiles: StateFlow<List<String>> = _missingFiles.asStateFlow()

    private val _metadata = MutableStateFlow<GameDataMetadata?>(null)
    val metadata: StateFlow<GameDataMetadata?> = _metadata.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        val saved = prefs.getString("resource_uri", null)
        if (saved != null) {
            val uri = Uri.parse(saved)
            _selectedDirectoryUri.value = uri
            validateGameDataAsync(uri)
        } else {
            _status.value = GameDataStatus.NOT_SELECTED
        }
    }

    fun selectGameDirectory(uri: Uri) {
        persistDirectoryPermission(uri)
    }

    fun persistDirectoryPermission(uri: Uri): Boolean {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            prefs.edit().putString("resource_uri", uri.toString()).apply()
            _selectedDirectoryUri.value = uri
            DiagnosticLogger.logRuntime("Persisted storage permission for: $uri")
            validateGameDataAsync(uri)
            true
        } catch (e: SecurityException) {
            val msg = "Storage permission denied: ${e.message}"
            Log.e("GameDataManager", msg, e)
            DiagnosticLogger.logRuntime(msg, e)
            _lastError.value = msg
            _status.value = GameDataStatus.ERROR
            false
        }
    }

    fun clearDirectoryPermission() {
        _selectedDirectoryUri.value?.let { uri ->
            try {
                if (uri.scheme == "content") {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.releasePersistableUriPermission(uri, flags)
                }
            } catch (e: SecurityException) {
                Log.e("GameDataManager", "Failed releasing URI permission", e)
            }
        }
        prefs.edit().remove("resource_uri").apply()
        _selectedDirectoryUri.value = null
        _missingFiles.value = emptyList()
        _metadata.value = null
        _lastError.value = null
        _status.value = GameDataStatus.NOT_SELECTED
        DiagnosticLogger.logRuntime("Game data directory cleared.")
    }

    fun getSelectedGameDirectory(): Uri? = _selectedDirectoryUri.value

    fun validateGameDataAsync(uri: Uri? = _selectedDirectoryUri.value) {
        if (uri == null) {
            _status.value = GameDataStatus.NOT_SELECTED
            return
        }
        _status.value = GameDataStatus.CHECKING
        _missingFiles.value = emptyList()
        _lastError.value = null

        CoroutineScope(Dispatchers.IO).launch {
            val result = validateGameDataInternal(uri)
            withContext(Dispatchers.Main) {
                _status.value = result.status
                _missingFiles.value = result.missingFiles
                _lastError.value = result.errorMessage
                _metadata.value = result.metadata
            }
        }
    }

    suspend fun validateGameDataSync(uri: Uri? = _selectedDirectoryUri.value): GameDataValidationResult = withContext(Dispatchers.IO) {
        if (uri == null) {
            return@withContext GameDataValidationResult(
                status = GameDataStatus.NOT_SELECTED,
                missingFiles = emptyList(),
                errorMessage = "No directory selected"
            )
        }
        val result = validateGameDataInternal(uri)
        withContext(Dispatchers.Main) {
            _status.value = result.status
            _missingFiles.value = result.missingFiles
            _lastError.value = result.errorMessage
            _metadata.value = result.metadata
        }
        result
    }

    private fun validateGameDataInternal(uri: Uri): GameDataValidationResult {
        try {
            val doc = resolveDocumentFile(uri)
            if (doc == null || !doc.exists() || !doc.canRead()) {
                val err = "Directory not found or unreadable: $uri"
                DiagnosticLogger.logRuntime(err)
                return GameDataValidationResult(
                    status = GameDataStatus.ERROR,
                    missingFiles = emptyList(),
                    errorMessage = err
                )
            }

            // 1. Ensure dxuk-cache/ exists automatically (do not report as missing game asset)
            val cacheResult = ensureDxukCache(doc)
            if (cacheResult is CacheResult.Failure) {
                val err = "Unable to create runtime cache directory: ${cacheResult.error}"
                DiagnosticLogger.logRuntime(err)
                return GameDataValidationResult(
                    status = GameDataStatus.ERROR,
                    missingFiles = emptyList(),
                    errorMessage = err
                )
            }

            // 2. Validate real game assets
            val requirement = GtaVResourceRequirement.requirement
            val missing = mutableListOf<String>()
            val foundDirs = mutableListOf<String>()
            val foundFiles = mutableListOf<String>()

            // Check directories
            for (dir in requirement.requiredDirectories) {
                val item = doc.findFile(dir)
                if (item != null && item.isDirectory) {
                    foundDirs.add(dir)
                } else {
                    missing.add("$dir/")
                }
            }

            // Check root files
            for (file in requirement.requiredRootFiles) {
                val item = doc.findFile(file)
                if (item != null && item.isFile) {
                    foundFiles.add(file)
                } else {
                    missing.add(file)
                }
            }

            val meta = GameDataMetadata(
                directoryUri = uri.toString(),
                totalFilesFound = foundFiles.size + foundDirs.size,
                requiredDirectoriesFound = foundDirs,
                requiredRootFilesFound = foundFiles,
                missingItems = missing,
                cacheDirectoryPath = (cacheResult as? CacheResult.Success)?.path,
                isCacheReady = cacheResult is CacheResult.Success
            )

            return if (missing.isEmpty()) {
                DiagnosticLogger.logRuntime("Game data validation PASSED for $uri")
                GameDataValidationResult(
                    status = GameDataStatus.READY,
                    missingFiles = emptyList(),
                    metadata = meta
                )
            } else {
                DiagnosticLogger.logRuntime("Game data validation INVALID. Missing: $missing")
                GameDataValidationResult(
                    status = GameDataStatus.INVALID,
                    missingFiles = missing,
                    metadata = meta
                )
            }
        } catch (e: Exception) {
            val err = "Validation failed: ${e.message}"
            DiagnosticLogger.logRuntime(err, e)
            return GameDataValidationResult(
                status = GameDataStatus.ERROR,
                missingFiles = emptyList(),
                errorMessage = err
            )
        }
    }

    private sealed class CacheResult {
        data class Success(val path: String) : CacheResult()
        data class Failure(val error: String) : CacheResult()
    }

    private fun ensureDxukCache(doc: DocumentFile): CacheResult {
        return try {
            val existing = doc.findFile("dxuk-cache")
            if (existing != null) {
                if (existing.isDirectory) {
                    CacheResult.Success(existing.uri.toString())
                } else {
                    CacheResult.Failure("Existing 'dxuk-cache' is a file, not a directory.")
                }
            } else {
                val created = doc.createDirectory("dxuk-cache")
                if (created != null && created.exists() && created.isDirectory) {
                    DiagnosticLogger.logRuntime("Created dxuk-cache directory at ${created.uri}")
                    CacheResult.Success(created.uri.toString())
                } else {
                    CacheResult.Failure("Unable to create directory 'dxuk-cache' in ${doc.uri}")
                }
            }
        } catch (e: Exception) {
            CacheResult.Failure(e.localizedMessage ?: "Unknown cache creation error")
        }
    }

    fun resolveDocumentFile(uri: Uri): DocumentFile? {
        return if (uri.scheme == "file") {
            uri.path?.let { DocumentFile.fromFile(File(it)) }
        } else {
            try {
                DocumentFile.fromTreeUri(context, uri)
            } catch (e: Exception) {
                Log.e("GameDataManager", "Failed to resolve tree URI: $uri", e)
                null
            }
        }
    }

    fun reportMissingFiles(): List<String> = _missingFiles.value
    fun exposeValidationState(): StateFlow<GameDataStatus> = _status
}
