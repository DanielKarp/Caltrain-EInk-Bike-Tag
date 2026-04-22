package com.example.e_inkbiketag

import android.graphics.Bitmap
import android.nfc.tech.NfcA
import android.util.Log
import java.io.IOException
import android.nfc.Tag
import android.nfc.tech.IsoDep

class EpdWriter(private val tag: Tag) {

/**
 * Direct implementation of the NFCTag 2.7" B/W driver command spec V1.1.
 * Replaces the Waveshare SDK entirely.
 *
 * Usage:
 *   val writer = EpdWriter(NfcA.get(tag))
 *   writer.connect()
 *   writer.flashBitmap(bitmap) { progress -> ... }
 *   writer.close()
 */

    companion object {
        private const val TAG = "EpdWriter"
        const val DISPLAY_WIDTH  = 264
        const val DISPLAY_HEIGHT = 176
        private const val CHUNK_SIZE = 250       // Safer chunk size (total packet ~225B); some phones limit at 253B
        private const val BUSY_TIMEOUT_MS = 15_000L
        private const val BUSY_POLL_MS    = 200L
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    private val isoDep: IsoDep = IsoDep.get(tag)
        ?: throw IOException("Tag does not support IsoDep")

    fun connect() {
        if (!isoDep.isConnected) {
            isoDep.connect()
            isoDep.timeout = 3000
            Thread.sleep(100)
        }
    }

    fun close() {
        try { isoDep.close() } catch (_: IOException) {}
    }


    /**
     * Returns the 10-byte product info block:
     *   bytes 0–7  = 8-byte serial number
     *   byte  8    = PID
     *   byte  9    = firmware version
     */
    fun readProductInfo(): ByteArray {
        // Command: ED05ACAF0A
        val resp = transceive(byteArrayOf(0xED.toByte(), 0x05, 0xAC.toByte(), 0xAF.toByte(), 0x0A))
        checkStatus(resp, "readProductInfo")
        return resp.copyOf(resp.size - 2)         // strip trailing status word
    }

    /**
     * Full init → image transfer → refresh sequence.
     * [onProgress] is called with 0–100 during image data transfer.
     * Throws IOException on any command failure or busy timeout.
     */
    fun flashBitmap(bitmap: Bitmap, onProgress: ((Int) -> Unit)? = null) {
        val imageData = bitmapToEpdBytes(bitmap)

        Log.d(TAG, "Starting flashBitmap...")
        // Hardware reset intentionally skipped for NFC:
        // RST LOW resets the NFC interface itself, killing the RF connection.
        try {
            val info = readProductInfo()
            Log.d(TAG, "Product info: ${info.joinToString("") { "%02X".format(it) }}")
        } catch (e: IOException) {
            Log.w(TAG, "readProductInfo failed (non-fatal): ${e.message}")
        }
        initSequence()
        Log.d(TAG, "Init sequence complete")
        sendImageData(imageData, onProgress)
        Log.d(TAG, "Image data transfer complete")
        triggerRefresh()
        Log.d(TAG, "Refresh triggered")
        waitUntilNotBusy()
        Log.d(TAG, "Flash complete!")
    }

    // -------------------------------------------------------------------------
    // Init / reset / refresh sequences  (straight from spec)
    // -------------------------------------------------------------------------

    private fun hardwareReset() {
        try {
            checkStatus(transceive(byteArrayOf(0x74, 0x97.toByte(), 0x00, 0x08, 0x00)), "resetLow")
            Thread.sleep(20)
            checkStatus(transceive(byteArrayOf(0x74, 0x97.toByte(), 0x01, 0x08, 0x00)), "resetHigh")
            Thread.sleep(50)  // longer settle after reset high
            Log.d(TAG, "Hardware reset complete")
        } catch (e: IOException) {
            // Reset can fail on initial power-up; the init sequence will
            // bring the controller to a known state regardless
            Log.w(TAG, "Hardware reset failed (non-fatal): ${e.message}")
        }
    }

    private fun initSequence() {
        // Driver output control: 263 (0x107) MUX
        sendCmd(0x01); sendData(0x07, 0x01, 0x00)
        
        // Data entry mode: X increment, Y increment
        sendCmd(0x11); sendData(0x03)
        
        // Set RAM X-address Start/End position: 0x00 to 0x15 (22 bytes = 176 pixels)
        sendCmd(0x44); sendData(0x00, 0x15)
        
        // Set RAM Y-address Start/End position: 0 to 263 (0x107)
        sendCmd(0x45); sendData(0x00, 0x00, 0x07, 0x01)
        
        // Border waveform control
        sendCmd(0x3C); sendData(0x05)
        
        // Temperature sensor control (Internal)
        sendCmd(0x18); sendData(0x80.toByte())
        
        // Display update control 2: B1 (Load temperature, Load LUT)
        sendCmd(0x22); sendData(0xB1.toByte())
        sendCmd(0x20); Thread.sleep(100)

        // Set RAM address counters back to 0 before data transfer
        sendCmd(0x4E); sendData(0x00)
        sendCmd(0x4F); sendData(0x00, 0x00)
    }

    private fun sendImageData(imageData: ByteArray, onProgress: ((Int) -> Unit)?) {
        // 7499000D0124  — start B/W image transfer
        sendCmd(0x24)

        var offset = 0
        while (offset < imageData.size) {
            val end   = minOf(offset + CHUNK_SIZE, imageData.size)
            val chunk = imageData.copyOfRange(offset, end)
            // 749A000E + Len(=chunk.size) + data
            sendDataRaw(chunk)
            offset = end
            onProgress?.invoke(offset * 100 / imageData.size)
            // Small delay between chunks to avoid overwhelming the tag's power harvester
            Thread.sleep(10)
        }
        Log.v(TAG, "Image data sent: ${imageData.size} bytes in ${(imageData.size + CHUNK_SIZE - 1) / CHUNK_SIZE} chunks")
    }

    private fun triggerRefresh() {
        sendCmd(0x22); sendData(0xC7.toByte())
        sendCmd(0x20)
    }

    // -------------------------------------------------------------------------
    // Low-level command builders
    // -------------------------------------------------------------------------

    /** 7499000D + 01 + cmd */
    private fun sendCmd(cmd: Int) {
        val packet = byteArrayOf(0x74, 0x99.toByte(), 0x00, 0x0D, 0x01, cmd.toByte())
        checkStatus(transceive(packet), "sendCmd(0x${cmd.toString(16).uppercase()})")
    }

    /** 749A000E + Len + data  (vararg convenience overload) */
    private fun sendData(vararg bytes: Byte) = sendDataRaw(bytes)

    /** 749A000E + Len + data  (raw ByteArray) */
    private fun sendDataRaw(data: ByteArray) {
        val packet = byteArrayOf(0x74, 0x9A.toByte(), 0x00, 0x0E, data.size.toByte()) + data
        checkStatus(transceive(packet), "sendData(${data.size}B)")
    }

    // -------------------------------------------------------------------------
    // Busy polling
    // -------------------------------------------------------------------------

    /**
     * 749b000F01 → returns [busyByte, 0x90, 0x00]
     * Polls until busyByte == 0x00 (not busy) or timeout.
     */
    private fun waitUntilNotBusy() {
        val deadline = System.currentTimeMillis() + BUSY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val resp = transceive(byteArrayOf(0x74, 0x9B.toByte(), 0x00, 0x0F, 0x01))
            if (resp.size >= 3 && statusWord(resp) == 0x9000) {
                val busyByte = resp[0].toInt() and 0xFF
                if (busyByte == 0x00) {
                    Log.v(TAG, "Display ready (not busy)")
                    return
                }
            }
            Thread.sleep(BUSY_POLL_MS)
        }
        throw IOException("Timed out waiting for display to become ready")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun transceive(cmd: ByteArray): ByteArray {
        val hexCmd = cmd.joinToString("") { "%02X".format(it) }
        var lastEx: IOException? = null
        for (i in 1..3) {
            try {
                if (!isoDep.isConnected) {
                    Log.w(TAG, "Tag disconnected, reconnecting (attempt $i)...")
                    isoDep.connect()
                }
                val resp = isoDep.transceive(cmd)
                val hexResp = resp.joinToString("") { "%02X".format(it) }
                Log.v(TAG, "TX: $hexCmd -> RX: $hexResp")
                return resp
            } catch (e: IOException) {
                lastEx = e
                Log.e(TAG, "Transceive error on attempt $i (Cmd: $hexCmd): ${e.message}")
                if (i < 3) Thread.sleep(100)
            }
        }
        throw lastEx ?: IOException("Transceive failed after retries")
    }

    private fun statusWord(resp: ByteArray): Int =
        ((resp[resp.size - 2].toInt() and 0xFF) shl 8) or (resp[resp.size - 1].toInt() and 0xFF)

    private fun checkStatus(resp: ByteArray, context: String) {
        if (resp.size < 2)
            throw IOException("$context: response too short (${resp.size}B)")
        val sw = statusWord(resp)
        if (sw != 0x9000)
            throw IOException("$context: bad status word 0x${sw.toString(16).uppercase()}")
    }

    // -------------------------------------------------------------------------
    // Bitmap → 1bpp packed bytes
    // -------------------------------------------------------------------------

    /**
     * Scales [src] to display dimensions and packs to 1bpp.
     * White pixel = 1, black pixel = 0 (MSB first within each byte).
     * Flip the luminance condition if colors appear inverted on your display.
     */
    private fun bitmapToEpdBytes(src: Bitmap): ByteArray {
        // Controller scans in portrait orientation — rotate source 90° CCW
        val matrix = android.graphics.Matrix().apply { postRotate(-90f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)

        // After rotation, dimensions are swapped
        val bmp = Bitmap.createScaledBitmap(rotated, DISPLAY_HEIGHT, DISPLAY_WIDTH, true)
        val bytes = ByteArray(DISPLAY_WIDTH * DISPLAY_HEIGHT / 8)
        var byteIdx = 0
        var bitIdx  = 7
        var packed  = 0

        for (y in 0 until DISPLAY_WIDTH) {        // 264 rows in controller space
            for (x in 0 until DISPLAY_HEIGHT) {   // 176 cols in controller space
                val px  = bmp.getPixel(x, y)
                val r   = (px shr 16) and 0xFF
                val g   = (px shr  8) and 0xFF
                val b   =  px         and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                // Invert logic: 1 = Black, 0 = White
                if (lum >= 128) packed = packed or (1 shl bitIdx)
                if (--bitIdx < 0) {
                    bytes[byteIdx++] = packed.toByte()
                    packed = 0; bitIdx = 7
                }
            }
        }
        if (bitIdx != 7) bytes[byteIdx] = packed.toByte()
        return bytes
    }
}