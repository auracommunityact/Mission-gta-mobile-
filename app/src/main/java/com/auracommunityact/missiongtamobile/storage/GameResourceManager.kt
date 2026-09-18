package com.auracommunityact.missiongtamobile.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameResourceManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("mission_gta_prefs", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(ResourceStatus.NOT_CONFIGURED)
    val status: StateFlow<ResourceStatus> = _status.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _missingFiles = MutableStateFlow<List<String>>(emptyList())
    val missingFiles: StateFlow<List<String>> = _missingFiles.asStateFlow()

    init {
        val savedUriString = prefs.getString("resource_uri", null)
        if (savedUriString != null) {
            val uri = Uri.parse(savedUriString)
            _selectedUri.value = uri
            validateResources(uri)
        } else {
            _status.value = ResourceStatus.NOT_CONFIGURED
        }
    }

    fun takePersistableUriPermission(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            prefs.edit().putString("resource_uri", uri.toString()).apply()
            _selectedUri.value = uri
            validateResources(uri)
        } catch (e: SecurityException) {
            Log.e("RESOURCE", "Failed to take persistable URI permission", e)
            _status.value = ResourceStatus.ACCESS_DENIED
        }
    }

    fun clearPermission() {
        _selectedUri.value?.let { uri ->
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                Log.e("RESOURCE", "Failed to release persistable URI permission", e)
            }
        }
        prefs.edit().remove("resource_uri").apply()
        _selectedUri.value = null
        _missingFiles.value = emptyList()
        _status.value = ResourceStatus.NOT_CONFIGURED
    }

    fun validateResources(uri: Uri) {
        _status.value = ResourceStatus.SCANNING
        _missingFiles.value = emptyList()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                if (documentFile == null || !documentFile.exists() || !documentFile.canRead()) {
                    _status.value = ResourceStatus.DIRECTORY_NOT_FOUND
                    return@launch
                }

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
                    } else {
                        _status.value = ResourceStatus.READY
                    }
                }
            } catch (e: Exception) {
                Log.e("RESOURCE", "Validation failed", e)
                withContext(Dispatchers.Main) {
                    _status.value = ResourceStatus.VALIDATION_ERROR
                }
            }
        }
    }

    companion object {
        fun sanitizeRelativePath(relativePath: String): String {
            return relativePath.replace("\\", "/").replace("../", "")
        }
    }

    fun getFile(relativePath: String): DocumentFile? {
        val rootUri = _selectedUri.value ?: return null
        var currentFile: DocumentFile? = DocumentFile.fromTreeUri(context, rootUri)
        
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

