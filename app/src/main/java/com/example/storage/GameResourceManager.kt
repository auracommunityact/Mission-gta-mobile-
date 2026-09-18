package com.example.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class GameResourceManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("mission_gta_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().build()
    private val manifestAdapter = moshi.adapter(GameResourceManifest::class.java)

    private val _status = MutableStateFlow(ResourceStatus.NOT_CONFIGURED)
    val status: StateFlow<ResourceStatus> = _status.asStateFlow()

    private val _manifest = MutableStateFlow<GameResourceManifest?>(null)
    val manifest: StateFlow<GameResourceManifest?> = _manifest.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

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
        _manifest.value = null
        _status.value = ResourceStatus.NOT_CONFIGURED
    }

    fun validateResources(uri: Uri) {
        _status.value = ResourceStatus.VALIDATING
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                if (documentFile == null || !documentFile.exists() || !documentFile.canRead()) {
                    _status.value = ResourceStatus.DIRECTORY_NOT_FOUND
                    return@launch
                }

                val manifestFile = documentFile.findFile("resource_manifest.json")
                if (manifestFile == null || !manifestFile.exists()) {
                    _status.value = ResourceStatus.MANIFEST_NOT_FOUND
                    return@launch
                }

                context.contentResolver.openInputStream(manifestFile.uri)?.use { inputStream ->
                    val reader = InputStreamReader(inputStream)
                    val json = reader.readText()
                    val parsedManifest = manifestAdapter.fromJson(json)
                    
                    if (parsedManifest == null) {
                        _status.value = ResourceStatus.INVALID_MANIFEST
                        return@launch
                    }
                    _manifest.value = parsedManifest

                    // Validate required files existence
                    var missing = false
                    for (required in parsedManifest.requiredFiles) {
                        val pathParts = required.path.split("/")
                        var currentDir: DocumentFile? = documentFile
                        var found = true
                        for (part in pathParts) {
                            currentDir = currentDir?.findFile(part)
                            if (currentDir == null) {
                                found = false
                                break
                            }
                        }
                        if (!found) {
                            Log.w("VALIDATION", "Missing required file: ${required.path}")
                            missing = true
                            break
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (missing) {
                            _status.value = ResourceStatus.MISSING_FILES
                        } else {
                            _status.value = ResourceStatus.READY
                        }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        _status.value = ResourceStatus.INVALID_MANIFEST
                    }
                }
            } catch (e: Exception) {
                Log.e("RESOURCE", "Validation failed", e)
                withContext(Dispatchers.Main) {
                    _status.value = ResourceStatus.INVALID_MANIFEST
                }
            }
        }
    }
}
