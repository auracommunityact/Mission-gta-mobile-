package com.auracommunityact.missiongtamobile.runtime.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogger {
    private const val TAG = "DiagnosticLogger"
    private var logsDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val runtimeLogBuffer = mutableListOf<String>()
    private val rendererLogBuffer = mutableListOf<String>()
    private val driverLogBuffer = mutableListOf<String>()

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        logsDir = dir
    }

    private fun writeEntry(fileName: String, buffer: MutableList<String>, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val formatted = "[$timestamp] $message"
        synchronized(buffer) {
            buffer.add(formatted)
            if (buffer.size > 200) buffer.removeAt(0)
            if (throwable != null) {
                val stack = Log.getStackTraceString(throwable)
                buffer.add(stack)
                if (buffer.size > 200) buffer.removeAt(0)
            }
        }

        val dir = logsDir ?: return
        try {
            val file = File(dir, fileName)
            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(formatted)
                    throwable?.printStackTrace(pw)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to $fileName", e)
        }
    }

    fun logRuntime(message: String, throwable: Throwable? = null) {
        Log.i("RUNTIME", message, throwable)
        writeEntry("runtime.log", runtimeLogBuffer, message, throwable)
    }

    fun logRenderer(message: String, throwable: Throwable? = null) {
        Log.i("RENDERER", message, throwable)
        writeEntry("renderer.log", rendererLogBuffer, message, throwable)
    }

    fun logDriver(message: String, throwable: Throwable? = null) {
        Log.i("DRIVER", message, throwable)
        writeEntry("driver.log", driverLogBuffer, message, throwable)
    }

    fun getRuntimeLogs(): List<String> = synchronized(runtimeLogBuffer) { runtimeLogBuffer.toList() }
    fun getRendererLogs(): List<String> = synchronized(rendererLogBuffer) { rendererLogBuffer.toList() }
    fun getDriverLogs(): List<String> = synchronized(driverLogBuffer) { driverLogBuffer.toList() }

    fun readLogFile(context: Context, fileName: String): String {
        val dir = logsDir ?: File(context.filesDir, "logs")
        val file = File(dir, fileName)
        return if (file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                "Error reading $fileName: ${e.message}"
            }
        } else {
            "Log file $fileName is empty or does not exist yet."
        }
    }
}
