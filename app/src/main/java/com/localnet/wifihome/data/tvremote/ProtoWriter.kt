package com.localnet.wifihome.data.tvremote

import java.io.ByteArrayOutputStream

/**
 * Encoder protobuf minimal (wire format) tanpa perlu file .proto atau protoc.
 * Mendukung field yang dibutuhkan protokol Android TV Remote v2:
 * varint, length-delimited (string/bytes/submessage).
 *
 * Referensi wire format: https://protobuf.dev/programming-guides/encoding/
 */
class ProtoWriter {
    private val buffer = ByteArrayOutputStream()

    private fun writeVarint(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7F.inv().toLong() == 0L) {
                buffer.write(v.toInt())
                return
            } else {
                buffer.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
    }

    private fun writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    fun writeVarintField(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, 0) // wire type 0 = varint
        writeVarint(value)
    }

    fun writeBoolField(fieldNumber: Int, value: Boolean) {
        writeVarintField(fieldNumber, if (value) 1L else 0L)
    }

    fun writeStringField(fieldNumber: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeTag(fieldNumber, 2) // wire type 2 = length-delimited
        writeVarint(bytes.size.toLong())
        buffer.write(bytes)
    }

    fun writeBytesField(fieldNumber: Int, value: ByteArray) {
        writeTag(fieldNumber, 2)
        writeVarint(value.size.toLong())
        buffer.write(value)
    }

    fun writeMessageField(fieldNumber: Int, message: ByteArray) {
        writeTag(fieldNumber, 2)
        writeVarint(message.size.toLong())
        buffer.write(message)
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}
