package com.lmob.gitrepomanager.data.repository

import com.lmob.gitrepomanager.data.local.TokenStorage
import com.lmob.gitrepomanager.data.model.FileOperationRequest
import com.lmob.gitrepomanager.data.model.GitHubContent
import com.lmob.gitrepomanager.data.model.GitHubRepo
import com.lmob.gitrepomanager.data.model.GitHubUser
import com.lmob.gitrepomanager.data.model.GitHubWorkflowRun
import com.lmob.gitrepomanager.data.remote.GitHubApiService
import com.lmob.gitrepomanager.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for everything GitHub-related.
 * Wraps GitHubApiService calls, converts HTTP responses into Resource<T>,
 * and exposes the higher-level "empty repository" operation used by the
 * Danger Zone screen (equivalent to bersihkan-repo.sh).
 */
@Singleton
class GitHubRepository @Inject constructor(
    private val api: GitHubApiService,
    private val tokenStorage: TokenStorage
) {

    // ---------- Auth ----------

    suspend fun validateTokenAndFetchUser(token: String): Resource<GitHubUser> =
        withContext(Dispatchers.IO) {
            try {
                // Temporarily store token so AuthInterceptor picks it up for this call.
                tokenStorage.saveToken(token)
                val response = api.getAuthenticatedUser()
                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!)
                } else {
                    tokenStorage.clearToken()
                    Resource.Error(mapHttpError(response))
                }
            } catch (e: Exception) {
                tokenStorage.clearToken()
                Resource.Error(e.message ?: "Gagal memvalidasi token")
            }
        }

    fun logout() = tokenStorage.clearToken()

    fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()

    /**
     * Fetches the authenticated user using the currently stored token
     * (used to refresh profile info on the Repo List screen without
     * re-entering the token, unlike [validateTokenAndFetchUser]).
     */
    suspend fun validateCurrentSessionUser(): Resource<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAuthenticatedUser()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(mapHttpError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Gagal memuat profil")
        }
    }

    // ---------- Repos ----------

    suspend fun listRepos(): Resource<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        try {
            val response = api.listUserRepos()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(mapHttpError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Gagal memuat daftar repository")
        }
    }

    suspend fun getRepo(owner: String, repo: String): Resource<GitHubRepo> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getRepo(owner, repo)
                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!)
                } else {
                    Resource.Error(mapHttpError(response))
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Gagal memuat detail repository")
            }
        }

    // ---------- Contents / File browser ----------

    suspend fun listContents(
        owner: String,
        repo: String,
        path: String = ""
    ): Resource<List<GitHubContent>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getContents(owner, repo, path)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.sortedWith(
                    compareBy({ it.type != "dir" }, { it.name.lowercase() })
                ))
            } else if (response.code() == 404) {
                // Empty directory / repo returns 404 from GitHub — treat as empty list
                Resource.Success(emptyList())
            } else {
                Resource.Error(mapHttpError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Gagal memuat isi folder")
        }
    }

    suspend fun getFileContent(
        owner: String,
        repo: String,
        path: String
    ): Resource<GitHubContent> = withContext(Dispatchers.IO) {
        try {
            val response = api.getFileContent(owner, repo, path)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(mapHttpError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Gagal memuat berkas")
        }
    }

    // ---------- Actions ----------

    suspend fun listWorkflowRuns(
        owner: String,
        repo: String
    ): Resource<List<GitHubWorkflowRun>> = withContext(Dispatchers.IO) {
        try {
            val response = api.listWorkflowRuns(owner, repo)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.workflowRuns)
            } else if (response.code() == 404) {
                Resource.Success(emptyList())
            } else {
                Resource.Error(mapHttpError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Gagal memuat riwayat Actions")
        }
    }

    // ---------- Danger Zone: Empty repository (mirrors bersihkan-repo.sh) ----------

    /**
     * Recursively deletes every file in the repository's default branch,
     * one commit per file, mirroring what bersihkan-repo.sh does locally
     * via `rm -rf` + `git push`. Returns the count of deleted files.
     *
     * Progress is reported via [onProgress] (currentIndex, totalFiles, currentFileName).
     */
    suspend fun emptyRepository(
        owner: String,
        repo: String,
        branch: String,
        onProgress: (Int, Int, String) -> Unit
    ): Resource<Int> = withContext(Dispatchers.IO) {
        try {
            val allFiles = mutableListOf<GitHubContent>()
            collectFilesRecursively(owner, repo, "", allFiles)

            if (allFiles.isEmpty()) {
                return@withContext Resource.Success(0)
            }

            var deletedCount = 0
            for ((index, file) in allFiles.withIndex()) {
                onProgress(index + 1, allFiles.size, file.path)
                val request = FileOperationRequest(
                    message = "chore: kosongkan isi repo (${file.path})",
                    sha = file.sha,
                    branch = branch
                )
                val response = api.deleteFileContent(owner, repo, file.path, request)
                if (response.isSuccessful) {
                    deletedCount++
                }
                // Small delay isn't required by GitHub, but we keep operations
                // sequential to avoid SHA conflicts from concurrent tree edits.
            }
            Resource.Success(deletedCount)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Gagal mengosongkan repository")
        }
    }

    private suspend fun collectFilesRecursively(
        owner: String,
        repo: String,
        path: String,
        accumulator: MutableList<GitHubContent>
    ) {
        val response = api.getContents(owner, repo, path)
        if (!response.isSuccessful) return
        val entries = response.body().orEmpty()
        for (entry in entries) {
            if (entry.isDirectory) {
                collectFilesRecursively(owner, repo, entry.path, accumulator)
            } else {
                accumulator.add(entry)
            }
        }
    }

    // ---------- Helpers ----------

    private fun <T> mapHttpError(response: Response<T>): String = when (response.code()) {
        401 -> "Token tidak valid atau sudah kadaluarsa."
        403 -> "Akses ditolak. Periksa izin (scope) token kamu, atau rate limit terlampaui."
        404 -> "Data tidak ditemukan."
        409 -> "Konflik data. Coba muat ulang."
        422 -> "Permintaan tidak valid."
        in 500..599 -> "Server GitHub sedang bermasalah. Coba lagi nanti."
        else -> "Terjadi kesalahan (kode ${response.code()})."
    }
}
