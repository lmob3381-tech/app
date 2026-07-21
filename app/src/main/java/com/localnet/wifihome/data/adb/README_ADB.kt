package com.localnet.wifihome.data.adb

/**
 * CATATAN PENTING — Fitur Screenshot TV via ADB
 * ================================================
 *
 * Android tidak menyediakan API publik untuk screen capture perangkat LAIN
 * (hanya bisa screenshot diri sendiri). Satu-satunya cara realistis untuk
 * screenshot Android TV / Google TV dari HP adalah lewat protokol ADB
 * (Android Debug Bridge) over network, yang mengharuskan:
 *
 * 1. Di TV: Settings > System > About > tekan "Android TV OS build" 7x
 *    untuk aktifkan Developer Options, lalu di Developer Options aktifkan
 *    "Wireless debugging" / "Network debugging".
 * 2. TV akan menampilkan IP:port (misal 192.168.1.20:5555) dan/atau kode
 *    pairing (untuk Android 11+, pakai adb pair dulu).
 * 3. App ini melakukan handshake protokol ADB (RSA key exchange) ke TV,
 *    lalu menjalankan shell command "screencap -p" dan menerima data PNG.
 *
 * IMPLEMENTASI ADB PROTOCOL DARI NOL itu cukup kompleks (perlu RSA keypair,
 * ADB auth protocol, ADB message framing/CRC). File AdbScreenshotClient.kt
 * di sini berisi KERANGKA (skeleton) dengan struktur yang benar, TAPI
 * bagian kriptografi (RSA sign, ADB AUTH) diberi TODO karena butuh testing
 * langsung dengan device fisik untuk dipastikan benar — sangat riskan kalau
 * saya tulis "asal jadi" tanpa bisa diuji di sini.
 *
 * REKOMENDASI PRAKTIS (lebih reliable, lebih cepat working):
 * Gunakan library open-source yang sudah teruji, misalnya:
 *   - "adblib" dari AOSP (kotlin, dipakai Android Studio sendiri)
 *   - Port Java "dadb" (https://github.com/mobile-dev-inc/dadb) — pure Kotlin/Java,
 *     tanpa perlu adb binary, mendukung pairing Android 11+, dan API-nya simpel:
 *
 *     implementation("dev.mobile:dadb:1.2.9")
 *     ...
 *     val dadb = Dadb.create("192.168.1.20", 5555)
 *     val result = dadb.shell("screencap -p")
 *     // result.stdout berisi bytes PNG mentah
 *
 * SARAN: AI/developer berikutnya yang melanjutkan sebaiknya GANTI skeleton
 * di AdbScreenshotClient.kt dengan pemanggilan library "dadb" di atas —
 * jauh lebih singkat & sudah battle-tested, tinggal tambah 1 baris dependency
 * di app/build.gradle.kts:
 *   implementation("dev.mobile:dadb:1.2.9")
 */
