package com.lmob.repomanager.network

import com.lmob.repomanager.data.*
import retrofit2.Response
import retrofit2.http.*

interface GitHubApi {

    @GET("user")
    suspend fun getAuthenticatedUser(): Response<GhUser>

    @GET("user/repos")
    suspend fun listMyRepos(
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
        @Query("affiliation") affiliation: String = "owner"
    ): Response<List<GhRepo>>

    @POST("user/repos")
    suspend fun createRepo(@Body body: CreateRepoRequest): Response<GhRepo>

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String = ""
    ): Response<List<GhContentItem>>

    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    suspend fun getTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Query("recursive") recursive: Int = 1
    ): Response<GhTreeResponse>

    // Retrofit tidak mengizinkan @Body pada method @DELETE biasa, jadi pakai @HTTP
    // dengan method eksplisit "DELETE" dan hasBody = true.
    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: DeleteFileRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): Response<GhWorkflowRunsResponse>

    @GET("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun getBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<GhRefResponse>

    // ---- Actions job logs ----

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun listJobsForRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<GhJobsResponse>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun getJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): Response<okhttp3.ResponseBody>

    // ---- Create / update a single file ----

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: PutFileRequest
    ): Response<PutFileResponse>

    // ---- Git data API: used to move files and to import a zip as one commit ----

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GhBlobRequest
    ): Response<GhBlobResponse>

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GhNewTreeRequest
    ): Response<GhTreeResponse>

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GhNewCommitRequest
    ): Response<GhCommitResponse>

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body body: GhUpdateRefRequest
    ): Response<GhRefResponse>
}
