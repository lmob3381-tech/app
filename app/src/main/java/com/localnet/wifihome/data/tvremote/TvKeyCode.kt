package com.localnet.wifihome.data.tvremote

/**
 * Kode tombol remote, mengikuti Android KeyEvent standar
 * (dipakai juga oleh Android TV Remote Protocol v2).
 * Referensi: https://developer.android.com/reference/android/view/KeyEvent
 */
enum class TvKeyCode(val code: Int) {
    DPAD_UP(19),
    DPAD_DOWN(20),
    DPAD_LEFT(21),
    DPAD_RIGHT(22),
    DPAD_CENTER(23), // OK/Select
    BACK(4),
    HOME(3),
    VOLUME_UP(24),
    VOLUME_DOWN(25),
    VOLUME_MUTE(164),
    POWER(26),
    MENU(82),
    PLAY_PAUSE(85)
}
