package com.lmob.gitrepomanager.util

/**
 * Generic wrapper for representing the state of an async operation
 * (network calls, DB reads, etc.) throughout the app, from repository
 * up to the ViewModel and Compose UI layer.
 */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun dataOrNull(): T? = (this as? Success)?.data
}

/**
 * Convenience wrapper to run a suspend network/IO call and translate
 * exceptions (including HTTP error codes handled elsewhere) into a
 * Resource.Error with a user-friendly Indonesian message.
 */
inline fun <T> resourceCatching(block: () -> Resource<T>): Resource<T> {
    return try {
        block()
    } catch (e: java.net.UnknownHostException) {
        Resource.Error("Tidak ada koneksi internet.", e)
    } catch (e: java.net.SocketTimeoutException) {
        Resource.Error("Koneksi ke GitHub timeout. Coba lagi.", e)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Terjadi kesalahan tak terduga.", e)
    }
}
