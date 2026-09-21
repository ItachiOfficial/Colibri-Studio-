package com.jarves.mh.network

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal client for Google Stitch's MCP server (https://stitch.googleapis.com/mcp).
 * Not a full MCP client — just enough JSON-RPC 2.0 over HTTP to call the three tools
 * this app needs: create_project, generate_screen_from_text, get_screen. See
 * /areas/mobile-harness-fork.md notes for context: Stitch generation is known to fail
 * intermittently (Google's own forum shows recurring "couldn't complete your
 * generation" reports across 2026), so every call here surfaces a clear
 * StitchException rather than crashing — callers are expected to offer a "skip
 * preview" path, not treat failure as exceptional.
 *
 * Auth: X-Goog-Api-Key header, per Stitch's documented priority order (API key checked
 * before OAuth/gcloud). The key itself is expected to come from ApiKeyVault, stored
 * under a dedicated id — this class never persists or logs it.
 */
data class StitchScreen(
    val id: String,
    val title: String,
    val htmlUrl: String?,
    val imageUrl: String?,
)

class StitchException(message: String, val retriable: Boolean = true) : Exception(message)

class StitchClient(private val apiKey: String) {
    private val requestId = AtomicInteger(1)

    fun createProject(title: String): String {
        val result = callTool("create_project", JSONObject().put("title", title))
        return result.optString("id").ifBlank {
            result.optString("projectId").ifBlank { throw StitchException("Stitch did not return a project id") }
        }
    }

    /**
     * Generates one screen from a text prompt inside an existing project. Returns
     * null fields for htmlUrl/imageUrl if Stitch's response didn't include them yet
     * (some generations return the screen record before assets finish rendering) —
     * callers should fall back to getScreen(projectId, screen.id) if either is null.
     */
    fun generateScreenFromText(projectId: String, prompt: String, title: String? = null): StitchScreen {
        val args = JSONObject()
            .put("projectId", projectId)
            .put("prompt", prompt)
        title?.let { args.put("title", it) }
        val result = callTool("generate_screen_from_text", args)
        return parseScreen(result)
    }

    fun getScreen(projectId: String, screenId: String): StitchScreen {
        val args = JSONObject().put("projectId", projectId).put("screenId", screenId)
        return parseScreen(callTool("get_screen", args))
    }

    private fun parseScreen(json: JSONObject): StitchScreen = StitchScreen(
        id = json.optString("id").ifBlank { json.optString("screenId") },
        title = json.optString("title"),
        htmlUrl = json.optString("htmlUrl").ifBlank { json.optJSONObject("html")?.optString("downloadUrl") }
            .takeIf { !it.isNullOrBlank() },
        imageUrl = json.optString("imageUrl").ifBlank { json.optJSONObject("image")?.optString("downloadUrl") }
            .takeIf { !it.isNullOrBlank() },
    )

    /**
     * One JSON-RPC 2.0 tools/call round-trip. Deliberately skips the full MCP
     * `initialize` handshake most clients do first — Stitch's HTTP endpoint accepts
     * direct tool calls per its own SDK examples (StitchToolClient "auto-connects on
     * the first callTool"), and skipping it keeps this client to one request per
     * operation instead of two.
     */
    private fun callTool(name: String, arguments: JSONObject): JSONObject {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", requestId.getAndIncrement())
            .put("method", "tools/call")
            .put("params", JSONObject().put("name", name).put("arguments", arguments))

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            // Screen generation can genuinely take a while; give it more room than a
            // typical REST call before treating it as a network failure rather than a
            // Stitch-side failure.
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            setRequestProperty("X-Goog-Api-Key", apiKey)
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        val status = connection.responseCode
        val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (status !in 200..299) {
            throw StitchException(
                extractErrorMessage(raw) ?: "Stitch returned HTTP $status",
                retriable = status >= 500 || status == 429,
            )
        }

        val response = parseJsonRpcBody(raw)
            ?: throw StitchException("Stitch returned an unreadable response")

        response.optJSONObject("error")?.let { err ->
            throw StitchException(
                err.optString("message").ifBlank { "Stitch tool call failed" },
                // Auth/validation errors (missing credential, bad arguments) won't
                // resolve on their own; only treat server-side/transient codes as
                // worth a retry prompt.
                retriable = err.optInt("code") <= -32000,
            )
        }

        val result = response.optJSONObject("result")
            ?: throw StitchException("Stitch call succeeded but returned no result")

        // Tool results normally arrive as { content: [{ type: "text", text: "<json>" }] }.
        // Unwrap that one layer if present; otherwise assume result IS the payload.
        val content = result.optJSONArray("content")
        if (content != null && content.length() > 0) {
            val text = content.optJSONObject(0)?.optString("text")
            if (!text.isNullOrBlank()) {
                return runCatching { JSONObject(text) }.getOrElse {
                    throw StitchException("Stitch returned content that wasn't valid JSON")
                }
            }
        }
        return result
    }

    /** Handles both a plain JSON body and an SSE-framed one (lines prefixed "data: "). */
    private fun parseJsonRpcBody(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) return runCatching { JSONObject(trimmed) }.getOrNull()
        val lastData = trimmed.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .lastOrNull { it.isNotBlank() && it != "[DONE]" }
        return lastData?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun extractErrorMessage(raw: String): String? = runCatching {
        val json = parseJsonRpcBody(raw) ?: JSONObject(raw)
        json.optJSONObject("error")?.optString("message")
            ?: json.optString("message").takeIf(String::isNotBlank)
    }.getOrNull()

    companion object {
        private const val ENDPOINT = "https://stitch.googleapis.com/mcp"

        /** Where the Stitch API key is stored in ApiKeyVault — not a ProviderKind, see notes. */
        const val VAULT_PROVIDER_ID = "STITCH"
    }
}
