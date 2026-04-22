package com.example.e_inkbiketag

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class ImageItem(
    val displayName: String,
    val filename: String,
    val colorCode: Char? = null
)

class MainActivity : ComponentActivity() {
    private var selectedItem: ImageItem? = null
    private var selectedBitmap: Bitmap? = null
    private lateinit var statusText: TextView
    private lateinit var previewImage: ImageView
    private lateinit var nfcAdapter: NfcAdapter
    private var isWriting = false
    private lateinit var pendingIntent: PendingIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)
        previewImage = findViewById(R.id.previewImage)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val images = assets.list("images")
            ?.filter { it.endsWith(".bmp") }
            ?.sorted()
            ?.map { filename ->
                val nameWithoutExt = filename.removeSuffix(".bmp")
                val parts = nameWithoutExt.split("_")
                val (color, displayName) = if (parts.size >= 3 && parts[1].length == 1 && parts[1].uppercase() in "RBGYV") {
                    parts[1].uppercase()[0] to parts.drop(2).joinToString("_")
                } else {
                    null to nameWithoutExt.substringAfter("_")
                }
                ImageItem(
                    displayName = displayName,
                    filename = filename,
                    colorCode = color
                )
            }
            ?: emptyList()

        val recyclerView = findViewById<RecyclerView>(R.id.imageRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ImageListAdapter(images) { selected ->
            selectedItem = selected
            statusText.text = "Ready: ${selected.displayName} — tap display"

            val bitmap = assets.open("images/${selected.filename}").use {
                BitmapFactory.decodeStream(it)
            }?.copy(Bitmap.Config.ARGB_8888, false)

            if (bitmap != null) {
                selectedBitmap = bitmap
                previewImage.setImageBitmap(bitmap)
                previewImage.visibility = View.VISIBLE
                
                // Scroll to the selected item to keep it visible
                val selectedPos = images.indexOf(selected)
                if (selectedPos != -1) {
                    recyclerView.post {
                        recyclerView.smoothScrollToPosition(selectedPos)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val options = Bundle()
        // Add a small delay for presence check to reduce "Tag Lost" errors on some devices
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)

        nfcAdapter.enableReaderMode(
            this,
            { tag -> handleTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or   // IsoDep can run over NFC-B too
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableReaderMode(this)
    }

    /**
     * Provides haptic and audible feedback to the user.
     * @param success True for success tone/vibration, false for error.
     */
    private fun notifyUser(success: Boolean) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80) // Slightly lower volume
            if (success) {
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            } else {
                tg.startTone(ToneGenerator.TONE_PROP_NACK, 500)
            }
        } catch (e: Exception) {
            Log.e("NFC", "Failed to play tone", e)
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (success) {
            // Short pulse for success
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // Double pulse for error
            val timings = longArrayOf(0, 100, 50, 100)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun handleTag(tag: Tag) {
        if (isWriting) return
        val bitmap = selectedBitmap ?: run {
            runOnUiThread { 
                statusText.text = "Select an image first" 
                notifyUser(false)
            }
            return
        }

        isWriting = true
        // Run in a background thread to prevent UI stuttering during the slow NFC write
        Thread {
            val writer = EpdWriter(tag)
            try {
                runOnUiThread { statusText.text = "Tag detected — writing…" }

                writer.connect()
                writer.flashBitmap(bitmap) { progress ->
                    runOnUiThread { statusText.text = "Writing: $progress%" }
                }

                runOnUiThread { 
                    statusText.text = "Done!"
                    notifyUser(true)
                }
            } catch (e: Exception) {
                Log.e("NFC", "Error during write", e)
                runOnUiThread { 
                    statusText.text = "Error: ${e.message}"
                    notifyUser(false)
                }
            } finally {
                isWriting = false
                writer.close()
            }
        }.start()
    }
}
