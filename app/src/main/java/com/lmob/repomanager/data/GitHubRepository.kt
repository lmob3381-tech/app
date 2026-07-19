package com.lmob.repomanager.data

import android.util.Base64
import com.lmob.repomanager.network.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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

    /** Ambil daftar job untuk satu workflow run (dipakai untuk layar detail run). */
    suspend fun getJobsForRun(owner: String, repo: String, runId: Long): Result<List<GhJob>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = api.listJobsForRun(owner, repo, runId)
                if (resp.isSuccessful && resp.body() != null) {
                    Result.Success(resp.body()!!.jobs)
                } else {
                    Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "Gagal mengambil daftar job")
            }
        }

    /** Ambil isi log teks mentah untuk satu job (biasanya job pertama dari run yang gagal). */
    suspend fun getJobLogs(owner: String, repo: String, jobId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val resp = api.getJobLogs(owner, repo, jobId)
                if (resp.isSuccessful && resp.body() != null) {
                    Result.Success(resp.body()!!.string())
                } else {
                    Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "Gagal mengambil log job")
            }
        }

    /** Buat file teks baru (atau folder kosong lewat trik .gitkeep) di path tertentu. */
    suspend fun createFile(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        textContent: String,
        commitMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val encoded = Base64.encodeToString(textContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val resp = api.putFile(
                owner, repo, path,
                PutFileRequest(message = commitMessage, content = encoded, branch = branch)
            )
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error(errorMessage(resp.code(), resp.errorBody()?.string()))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Gagal membuat file")
        }
    }

    /** Buat "folder" kosong. Git tidak punya folder kosong asli, jadi kita taruh file .gitkeep di dalamnya. */
    suspend fun createFolder(
        owner: String,
        repo: String,
        branch: String,
        folderPath: String
    ): Result<Unit> {
        val keepPath = if (folderPath.endsWith("/")) "${folderPath}.gitkeep" else "$folderPath/.gitkeep"
        return createFile(owner, repo, branch, keepPath, "", "chore: buat folder $folderPath")
    }

    /**
     * Pindahkan file atau folder (rekursif) dari [fromPath] ke [toPath] dalam satu commit,
     * menggunakan Git Data API (tree + commit + update ref) supaya atomik dan tidak butuh
     * di-download lalu di-upload ulang isi filenya.
     */
    suspend fun moveItem(
        owner: String,
        repo: String,
        branch: String,
        fromPath: String,
        toPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val refResp = api.getBranchRef(owner, repo, branch)
            if (!refResp.isSuccessful || refResp.body() == null) {
                return@withContext Result.Error(errorMessage(refResp.code(), refResp.errorBody()?.string()))
            }
            val headSha = refResp.body()!!.`object`.sha

            val treeResp = api.getTree(owner, repo, branch)
            if (!treeResp.isSuccessful || treeResp.body() == null) {
                return@withContext Result.Error(errorMessage(treeResp.code(), treeResp.errorBody()?.string()))
            }
            val fullTree = treeResp.body()!!.tree

            val fromPrefix = "$fromPath/"
            val affected = fullTree.filter { it.type == "blob" && (it.path == fromPath || it.path.startsWith(fromPrefix)) }
            if (affected.isEmpty()) {
                return@withContext Result.Error("Tidak ditemukan file di '$fromPath' untuk dipindahkan.")
            }

            // Bangun tree baru: setiap file lama di-"pindah" dengan menambahkan blob-nya
            // (sha yang sama, tidak perlu upload ulang) di path baru, lalu path lama
            // ditandai dihapus dengan sha = null (didukung oleh Git Trees API).
            val newTreeItems = mutableListOf<GhNewTreeItem>()
            for (blob in affected) {
                val newPath = toPath + blob.path.removePrefix(fromPath)
                newTreeItems.add(GhNewTreeItem(path = newPath, mode = blob.mode, type = "blob", sha = blob.sha))
                newTreeItems.add(GhNewTreeItem(path = blob.path, mode = blob.mode, type = "blob", sha = null))
            }

            val treeCreateResp = api.createTree(owner, repo, GhNewTreeRequest(baseTree = headSha, tree = newTreeItems))
            if (!treeCreateResp.isSuccessful || treeCreateResp.body() == null) {
                return@withContext Result.Error(errorMessage(treeCreateResp.code(), treeCreateResp.errorBody()?.string()))
            }
            val newTreeSha = treeCreateResp.body()!!.sha

            val commitResp = api.createCommit(
                owner, repo,
                GhNewCommitRequest(message = "chore: pindahkan $fromPath ke $toPath", tree = newTreeSha, parents = listOf(headSha))
            )
            if (!commitResp.isSuccessful || commitResp.body() == null) {
                return@withContext Result.Error(errorMessage(commitResp.code(), commitResp.errorBody()?.string()))
            }
            val newCommitSha = commitResp.body()!!.sha

            val updateResp = api.updateRef(owner, repo, branch, GhUpdateRefRequest(sha = newCommitSha))
            if (updateResp.isSuccessful) Result.Success(Unit)
            else Result.Error(errorMessage(updateResp.code(), updateResp.errorBody()?.string()))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Gagal memindahkan item")
        }
    }

    /**
     * Import isi sebuah ZIP langsung ke [targetPath] dalam repo, dalam satu commit,
     * menggunakan Git Data API. zipBytes adalah isi mentah file .zip yang sudah dibaca dari uri lokal.
     */
    suspend fun importZip(
        owner: String,
        repo: String,
        branch: String,
        targetPath: String,
        zipBytes: ByteArray,
        onProgress: (String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val refResp = api.getBranchRef(owner, repo, branch)
            if (!refResp.isSuccessful || refResp.body() == null) {
                return@withContext Result.Error(errorMessage(refResp.code(), refResp.errorBody()?.string()))
            }
            val headSha = refResp.body()!!.`object`.sha

            val newTreeItems = mutableListOf<GhNewTreeItem>()
            var count = 0
            ZipInputStream(zipBytes.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val buffer = ByteArrayOutputStream()
                        val data = ByteArray(8192)
                        var n: Int
                        while (zis.read(data).also { n = it } != -1) {
                            buffer.write(data, 0, n)
                        }
                        val entryPath = entry.name.trimStart('/')
                        val fullPath = if (targetPath.isBlank()) entryPath else "${targetPath.trimEnd('/')}/$entryPath"
                        onProgress("Membaca $entryPath...")

                        // Blob langsung disisipkan sebagai base64 di tree item (tanpa panggilan createBlob terpisah)
                        // hanya berlaku untuk teks; untuk aman dengan file biner, kita tetap pakai createBlob.
                        val blobResp = api.createBlob(owner, repo, GhBlobRequest(content = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)))
                        if (!blobResp.isSuccessful || blobResp.body() == null) {
                            return@withContext Result.Error("Gagal upload blob untuk $entryPath: ${errorMessage(blobResp.code(), blobResp.errorBody()?.string())}")
                        }
                        newTreeItems.add(GhNewTreeItem(path = fullPath, sha = blobResp.body()!!.sha))
                        count++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (newTreeItems.isEmpty()) {
                return@withContext Result.Error("ZIP kosong atau tidak berisi file.")
            }

            onProgress("Membuat tree gabungan...")
            val treeResp = api.createTree(owner, repo, GhNewTreeRequest(baseTree = headSha, tree = newTreeItems))
            if (!treeResp.isSuccessful || treeResp.body() == null) {
                return@withContext Result.Error(errorMessage(treeResp.code(), treeResp.errorBody()?.string()))
            }

            onProgress("Membuat commit...")
            val commitResp = api.createCommit(
                owner, repo,
                GhNewCommitRequest(message = "chore: import ZIP ke ${targetPath.ifBlank { "/" }}", tree = treeResp.body()!!.sha, parents = listOf(headSha))
            )
            if (!commitResp.isSuccessful || commitResp.body() == null) {
                return@withContext Result.Error(errorMessage(commitResp.code(), commitResp.errorBody()?.string()))
            }

            onProgress("Update branch $branch...")
            val updateResp = api.updateRef(owner, repo, branch, GhUpdateRefRequest(sha = commitResp.body()!!.sha))
            if (updateResp.isSuccessful) Result.Success(count)
            else Result.Error(errorMessage(updateResp.code(), updateResp.errorBody()?.string()))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Gagal mengimpor ZIP")
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
