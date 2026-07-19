package com.lmob.repomanager.data

import com.lmob.repomanager.network.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class GitHubRepository(private val api: GitHubApi) {

    suspend fun verifyTokenAndGetUser(): Result<GhUser> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getAuthenticatedUser()
            if (resp.isSuccessful && resp.body() != null) {
                Result.Success(resp.body()!!)
            } else {
                Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Gagal terhubung ke GitHub")
        }
    }

    suspend fun listRepos(): Result<List<GhRepo>> = withContext(Dispatchers.IO) {
        try {
            val resp = api.listMyRepos()
            if (resp.isSuccessful && resp.body() != null) {
                Result.Success(resp.body()!!)
            } else {
                Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Gagal mengambil daftar repo")
        }
    }

    suspend fun createRepo(name: String, description: String?, private: Boolean): Result<GhRepo> =
        withContext(Dispatchers.IO) {
            try {
                val resp = api.createRepo(CreateRepoRequest(name = name, description = description, private = private))
                if (resp.isSuccessful && resp.body() != null) {
                    Result.Success(resp.body()!!)
                } else {
                    Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "Gagal membuat repo")
            }
        }

    suspend fun deleteRepo(owner: String, repo: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val resp = api.deleteRepo(owner, repo)
            if (resp.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Gagal menghapus repo")
        }
    }

    /**
     * Setara dengan bersihkan-repo.sh: hapus semua file di repo (kecuali .git,
     * yang memang tidak relevan lewat API) satu per satu lewat Contents API.
     * Mengembalikan jumlah file yang berhasil dihapus.
     */
    suspend fun cleanRepo(
        owner: String,
        repo: String,
        branch: String,
        onProgress: (String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val treeResp = api.getTree(owner, repo, branch)
            if (!treeResp.isSuccessful || treeResp.body() == null) {
                return@withContext Result.Error(errorMessage(treeResp.code(), treeResp.errorBody()?.string()))
            }
            val files = treeResp.body()!!.tree.filter { it.type == "blob" }
            if (files.isEmpty()) {
                onProgress("Repo sudah kosong.")
                return@withContext Result.Success(0)
            }

            var deleted = 0
            for (item in files) {
                onProgress("Menghapus ${item.path}...")
                // Need current sha per file via contents endpoint isn't necessary; tree sha works for delete
                val delResp = api.deleteFile(
                    owner, repo, item.path,
                    DeleteFileRequest(
                        message = "chore: kosongkan isi repo",
                        sha = item.sha,
                        branch = branch
                    )
                )
                if (delResp.isSuccessful) {
                    deleted++
                } else {
                    onProgress("⚠ Gagal hapus ${item.path}: ${delResp.code()}")
                }
            }
            Result.Success(deleted)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Gagal membersihkan repo")
        }
    }

    /** Setara bagian pertama cek-repo.sh: isi file & folder repo */
    suspend fun getContents(owner: String, repo: String, path: String = ""): Result<List<GhContentItem>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = api.getContents(owner, repo, path)
                if (resp.isSuccessful && resp.body() != null) {
                    Result.Success(resp.body()!!.sortedWith(compareBy({ it.type != "dir" }, { it.name })))
                } else {
                    Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "Gagal mengambil isi repo")
            }
        }

    /** Setara bagian kedua cek-repo.sh: 5 run Actions terakhir */
    suspend fun getWorkflowRuns(owner: String, repo: String, limit: Int = 10): Result<List<GhWorkflowRun>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = api.listWorkflowRuns(owner, repo, limit)
                if (resp.isSuccessful && resp.body() != null) {
                    Result.Success(resp.body()!!.workflowRuns)
                } else {
                    Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "Gagal mengambil riwayat Actions")
            }
        }

    private fun errorMessage(code: Int, body: String?): String {
        return when (code) {
            401 -> "Token tidak valid atau sudah kedaluwarsa (401)."
            403 -> "Akses ditolak / rate limit tercapai (403)."
            404 -> "Tidak ditemukan (404)."
            422 -> "Permintaan tidak valid (422). ${body ?: ""}"
            else -> "Error $code: ${body ?: "tidak diketahui"}"
        }
    }
}
