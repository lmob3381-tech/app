package com.localnet.wifihome.data.model

enum class SpeedTestPhase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedTestResult(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val progressPercent: Int = 0,
    val errorMessage: String? = null
)
