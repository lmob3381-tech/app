package com.lelee.githubmanager

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Simple wrapper around GitHub REST API v3 using a Personal Access Token.
 * Token disimpan di SharedPreferences oleh TokenStore.
 */
object GitHubApi {

    private const val BASE = "https://api.github.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    class ApiException(val code: Int, message: String) : Exception(message)

    private fun baseRequest(url: String): Request.Builder {
        val token = TokenStore.getToken()
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
    }

    private suspend fun executeRaw(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            Pair(resp.code, body)
        }
    }

    private suspend fun get(url: String): String {
        val req = baseRequest(url).get().build()
        val (code, body) = executeRaw(req)
        if (code !in 200..299) throw ApiException(code, body)
        return body
    }

    private suspend fun delete(url: String, jsonBody: String? = null): String {
        val builder = baseRequest(url)
        val req = if (jsonBody != null) {
            builder.delete(jsonBody.toRequestBody(JSON_MEDIA)).build()
        } else {
            builder.delete().build()
        }
        val (code, body) = executeRaw(req)
        if (code !in 200..299) throw ApiException(code, body)
        return body
    }

    private suspend fun put(url: String, jsonBody: String): String {
        val req = baseRequest(url).put(jsonBody.toRequestBody(JSON_MEDIA)).build()
        val (code, body) = executeRaw(req)
        if (code !in 200..299) throw ApiException(code, body)
        return body
    }

    private suspend fun post(url: String, jsonBody: String): String {
        val req = baseRequest(url).post(jsonBody.toRequestBody(JSON_MEDIA)).build()
        val (code, body) = executeRaw(req)
        if (code !in 200..299) throw ApiException(code, body)
        return body
    }

    private suspend fun patch(url: String, jsonBody: String): String {
        val req = baseRequest(url).patch(jsonBody.toRequestBody(JSON_MEDIA)).build()
        val (code, body) = executeRaw(req)
        if (code !in 200..299) throw ApiException(code, body)
        return body
    }

    // ---------- USER ----------

    suspend fun getAuthenticatedUser(): JSONObject {
        val body = get("$BASE/user")
        return JSONObject(body)
    }

    // ---------- REPOS ----------

    suspend fun listRepos(): JSONArray {
        val body = get("$BASE/user/repos?per_page=100&sort=updated")
        return JSONArray(body)
    }

    suspend fun createRepo(name: String, description: String, isPrivate: Boolean): JSONObject {
        val json = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("private", isPrivate)
            put("auto_init", true)
        }
        val body = post("$BASE/user/repos", json.toString())
        return JSONObject(body)
    }

    suspend fun deleteRepo(owner: String, repo: String) {
        delete("$BASE/repos/$owner/$repo")
    }

    // ---------- CONTENTS ----------

    /** List files/dirs in a path ("" for root) */
    suspend fun listContents(owner: String, repo: String, path: String, branch: String? = null): JSONArray {
        val branchQuery = if (branch != null) "?ref=$branch" else ""
        val body = get("$BASE/repos/$owner/$repo/contents/$path$branchQuery")
        return if (body.trim().startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
    }

    /** Get single file content (decoded text) + sha (needed for update/delete) */
    suspend fun getFile(owner: String, repo: String, path: String, branch: String? = null): JSONObject {
        val branchQuery = if (branch != null) "?ref=$branch" else ""
        val body = get("$BASE/repos/$owner/$repo/contents/$path$branchQuery")
        return JSONObject(body)
    }

    fun decodeBase64Content(json: JSONObject): String {
        val content = json.optString("content", "").replace("\n", "")
        return try {
            String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /** Create or update a file. If sha is null -> create new file, else update existing */
    suspend fun putFile(
        owner: String, repo: String, path: String,
        contentText: String, message: String, sha: String?, branch: String? = null
    ): JSONObject {
        val encoded = Base64.encodeToString(contentText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("message", message)
            put("content", encoded)
            if (sha != null) put("sha", sha)
            if (branch != null) put("branch", branch)
        }
        val body = put("$BASE/repos/$owner/$repo/contents/$path", json.toString())
        return JSONObject(body)
    }

    /** Upload raw bytes (e.g. images/binary) as base64 */
    suspend fun putFileBytes(
        owner: String, repo: String, path: String,
        bytes: ByteArray, message: String, sha: String?, branch: String? = null
    ): JSONObject {
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("message", message)
            put("content", encoded)
            if (sha != null) put("sha", sha)
            if (branch != null) put("branch", branch)
        }
        val body = put("$BASE/repos/$owner/$repo/contents/$path", json.toString())
        return JSONObject(body)
    }

    suspend fun deleteFile(owner: String, repo: String, path: String, sha: String, message: String, branch: String? = null) {
        val json = JSONObject().apply {
            put("message", message)
            put("sha", sha)
            if (branch != null) put("branch", branch)
        }
        delete("$BASE/repos/$owner/$repo/contents/$path", json.toString())
    }

    // ---------- ACTIONS ----------

    suspend fun listWorkflows(owner: String, repo: String): JSONObject {
        val body = get("$BASE/repos/$owner/$repo/actions/workflows")
        return JSONObject(body)
    }

    suspend fun listWorkflowRuns(owner: String, repo: String, workflowId: Long? = null): JSONObject {
        val url = if (workflowId != null)
            "$BASE/repos/$owner/$repo/actions/workflows/$workflowId/runs?per_page=30"
        else
            "$BASE/repos/$owner/$repo/actions/runs?per_page=30"
        val body = get(url)
        return JSONObject(body)
    }

    suspend fun dispatchWorkflow(owner: String, repo: String, workflowId: Long, ref: String) {
        val json = JSONObject().apply { put("ref", ref) }
        post("$BASE/repos/$owner/$repo/actions/workflows/$workflowId/dispatches", json.toString())
    }

    suspend fun enableWorkflow(owner: String, repo: String, workflowId: Long) {
        put("$BASE/repos/$owner/$repo/actions/workflows/$workflowId/enable", "{}")
    }

    suspend fun disableWorkflow(owner: String, repo: String, workflowId: Long) {
        put("$BASE/repos/$owner/$repo/actions/workflows/$workflowId/disable", "{}")
    }

    suspend fun cancelRun(owner: String, repo: String, runId: Long) {
        post("$BASE/repos/$owner/$repo/actions/runs/$runId/cancel", "{}")
    }

    suspend fun rerunRun(owner: String, repo: String, runId: Long) {
        post("$BASE/repos/$owner/$repo/actions/runs/$runId/rerun", "{}")
    }

    suspend fun deleteRun(owner: String, repo: String, runId: Long) {
        delete("$BASE/repos/$owner/$repo/actions/runs/$runId")
    }

    suspend fun listArtifacts(owner: String, repo: String, runId: Long): JSONObject {
        val body = get("$BASE/repos/$owner/$repo/actions/runs/$runId/artifacts")
        return JSONObject(body)
    }

    /** Returns raw zip bytes for an artifact download */
    suspend fun downloadArtifact(owner: String, repo: String, artifactId: Long): ByteArray = withContext(Dispatchers.IO) {
        val req = baseRequest("$BASE/repos/$owner/$repo/actions/artifacts/$artifactId/zip").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, "Download failed")
            resp.body?.bytes() ?: ByteArray(0)
        }
    }
}
