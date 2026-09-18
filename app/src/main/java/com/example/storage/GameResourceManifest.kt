package com.example.storage

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GameResourceManifest(
    val formatVersion: Int,
    val resourceVersion: String,
    val runtimeVersion: String,
    val requiredFiles: List<RequiredFile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RequiredFile(
    val path: String,
    val size: Long = 0L,
    val sha256: String = ""
)
