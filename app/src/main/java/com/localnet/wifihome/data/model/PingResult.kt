package com.localnet.wifihome.data.model

data class PingResult(
    val host: String,
    val sequence: Int,
    val timeMs: Double?, // null = timeout/gagal
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class PingSummary(
    val host: String,
    val sent: Int,
    val received: Int,
    val minMs: Double,
    val avgMs: Double,
    val maxMs: Double,
    val packetLossPercent: Double
)
