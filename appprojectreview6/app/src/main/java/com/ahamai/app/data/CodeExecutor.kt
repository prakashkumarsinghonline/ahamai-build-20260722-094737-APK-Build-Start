package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Executes code using Wandbox API (free, no API key required).
 * Supports Python 3.12 execution with full standard library + internet access.
 */
object CodeExecutor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val WANDBOX_URL = "https://wandbox.org/api/compile.json"

    data class ExecutionResult(
        val output: String,
        val error: String,
        val exitCode: Int,
        val isSuccess: Boolean
    )

    /**
     * Execute Python code via Wandbox API.
     * Returns ExecutionResult with stdout, stderr, and exit status.
     */
    suspend fun executePython(code: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("code", code)
                put("compiler", "cpython-3.12.7")
                put("save", false)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = payload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(WANDBOX_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext ExecutionResult(
                    output = "",
                    error = "Server error: HTTP ${response.code}",
                    exitCode = 1,
                    isSuccess = false
                )
            }

            val json = JSONObject(responseBody)
            val programOutput = json.optString("program_output", "")
            val programError = json.optString("program_error", "")
            val compilerError = json.optString("compiler_error", "")
            val status = json.optString("status", "0")

            val combinedError = buildString {
                if (compilerError.isNotBlank()) append(compilerError)
                if (programError.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(programError)
                }
            }

            val exitCode = try { status.toInt() } catch (_: Exception) { if (combinedError.isNotBlank()) 1 else 0 }

            ExecutionResult(
                output = programOutput,
                error = combinedError,
                exitCode = exitCode,
                isSuccess = exitCode == 0 && combinedError.isBlank()
            )
        } catch (e: Exception) {
            ExecutionResult(
                output = "",
                error = "Execution failed: ${e.message}",
                exitCode = 1,
                isSuccess = false
            )
        }
    }

    /**
     * Ask AI to fix the code based on the error message.
     * Returns the prompt to send to the AI for auto-fix.
     */
    fun buildAutoFixPrompt(originalCode: String, error: String): String {
        return "The following Python code has an error. Fix the code and return ONLY the corrected Python code inside a ```python code block. Do not explain, just fix.\n\n" +
                "Original code:\n```python\n$originalCode\n```\n\n" +
                "Error:\n```\n$error\n```\n\n" +
                "Fixed code:"
    }
}
