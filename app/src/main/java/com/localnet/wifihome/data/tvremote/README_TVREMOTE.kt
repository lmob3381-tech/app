package com.localnet.wifihome.data.tvremote

/**
 * CATATAN IMPLEMENTASI — Android TV Remote Protocol v2
 * =====================================================
 *
 * Google TV / Android TV modern (Android 11+) menjalankan servis remote di
 * port TCP 6467 (pairing, TLS) dan 6466 (remote control, TLS setelah pairing).
 * Protokolnya berbasis Protobuf dengan skema resmi ada di:
 *   https://github.com/Aymkdn/assistant-freebox-cast/blob/master/tv_remote/
 *   (skema tidak resmi, hasil reverse-engineering komunitas — Google tidak
 *   mempublikasikan .proto resmi untuk protokol ini)
 *
 * KENAPA TIDAK PAKAI PROTOBUF PENUH DI SINI:
 * Supaya build APK di GitHub Actions tetap simpel (tanpa perlu protoc compiler
 * & skema .proto pihak ketiga yang bisa berubah), implementasi di file
 * TvRemoteClient.kt ini memakai pendekatan raw TLS socket dengan payload
 * protobuf yang di-encode manual (varint + tag) untuk pesan-pesan penting saja:
 * - Pairing request/response
 * - KeyEvent (arah, OK, back, home, volume, power)
 *
 * INI ARTINYA:
 * 1. Fitur remote (tombol arah, OK, volume, power) akan berfungsi.
 * 2. TIDAK mengimplementasikan seluruh protokol (misal app launch by name,
 *    now-playing info). Bisa ditambah nanti kalau dibutuhkan.
 * 3. Proses pairing WAJIB dilakukan manual sekali: TV akan menampilkan kode
 *    6 digit, dan user memasukkannya di app ini (mirip pairing remote resmi).
 *
 * ALTERNATIF LEBIH ROBUST (kalau nanti mau upgrade):
 * Gunakan library pihak ketiga seperti "androidtvremote2" (Python, referensi
 * logika) atau port Kotlin dari komunitas jika sudah tersedia & terpercaya.
 *
 * SOAL SCREENSHOT TV:
 * Android TV Remote Protocol TIDAK menyediakan screenshot. Screenshot hanya
 * bisa didapat lewat ADB (adb shell screencap), yang butuh Wireless Debugging
 * aktif di TV (Settings > System > Developer Options > Wireless debugging).
 * Implementasi ada di package data.adb — lihat AdbScreenshotClient.kt.
 */
