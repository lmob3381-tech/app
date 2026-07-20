package com.lmob.gitrepomanager.data.remote

import com.lmob.gitrepomanager.data.model.FileOperationRequest
import com.lmob.gitrepomanager.data.model.FileOperationResponse
import com.lmob.gitrepomanager.data.model.GitHubContent
import com.lmob.gitrepomanager.data.model.GitHubRepo
import com.lmob.gitrepomanager.data.model.GitHubUser
import com.lmob.gitrepomanager.data.model.WorkflowRunsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * GitHub REST API v3 endpoints used by the app.
 * Base URL: https://api.github.com/
 *
 * Auth: every call requires an "Authorization: Bearer <token>" header,
 * added automatically by AuthInterceptor (see di/NetworkModule.kt) so it
 * is NOT declared on each method here.
 */
interface GitHubApiService {

    @GET("user")
    suspend fun getAuthenticatedUser(): Response<GitHubUser>

    @GET("user/repos")
    suspend fun listUserRepos(
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("affiliation") affiliation: String = "owner,collaborator,organization_member"
    ): Response<List<GitHubRepo>>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRepo>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<List<GitHubContent>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<GitHubContent>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: FileOperationRequest
    ): Response<FileOperationResponse>

    @DELETE("repos/{owner}/{repo}/contents/{path}")
    suspend fun deleteFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: FileOperationRequest
    ): Response<FileOperationResponse>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): Response<WorkflowRunsResponse>
}
