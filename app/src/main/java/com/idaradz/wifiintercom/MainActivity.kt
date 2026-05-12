package com.idaradz.wifiintercom

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private var isTalking = false
    private val port = 55555
    private val targetIp = "255.255.255.255"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 60, 40, 40)
        layout.setBackgroundColor(Color.rgb(235, 247, 255))

        val title = TextView(this)
        title.text = "WiFi Intercom DZ"
        title.textSize = 30f
        title.setTextColor(Color.BLACK)

        val status = TextView(this)
        status.text = "متصل محلياً — اضغط للتحدث"
        status.textSize = 18f
        status.setTextColor(Color.DKGRAY)

        val button = Button(this)
        button.text = "🎙️ اضغط للتحدث"
        button.textSize = 24f

        layout.addView(title)
        layout.addView(status)
        layout.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            260
        ))

        setContentView(layout)

        startReceiver()

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isTalking = true
                    status.text = "يتم الإرسال الآن..."
                    button.text = "🔴 أرسل صوتك"
                    startSender()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTalking = false
                    status.text = "متصل محلياً — اضغط للتحدث"
                    button.text = "🎙️ اضغط للتحدث"
                }
            }
            true
        }
    }

    private fun startSender() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        thread {
            val sampleRate = 8000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val socket = DatagramSocket()
            socket.broadcast = true
            val address = InetAddress.getByName(targetIp)
            val buffer = ByteArray(bufferSize)

            recorder.startRecording()

            while (isTalking) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val packet = DatagramPacket(buffer, read, address, port)
                    socket.send(packet)
                }
            }

            recorder.stop()
            recorder.release()
            socket.close()
        }
    }

    private fun startReceiver() {
        thread {
            val sampleRate = 8000
            val bufferSize = 4096

            val player = android.media.AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                android.media.AudioTrack.MODE_STREAM
            )

            val socket = DatagramSocket(port)
            val buffer = ByteArray(bufferSize)

            player.play()

            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                if (!isTalking) {
                    player.write(packet.data, 0, packet.length)
                }
            }
        }
    }
}
