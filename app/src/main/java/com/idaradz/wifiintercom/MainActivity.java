package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends Activity {

    private volatile boolean isTalking = false;
    private final int port = 55555;
    private final String targetIp = "255.255.255.255";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);
        layout.setBackgroundColor(Color.rgb(235, 247, 255));

        TextView title = new TextView(this);
        title.setText("WiFi Intercom DZ");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);

        TextView status = new TextView(this);
        status.setText("متصل محلياً — اضغط للتحدث");
        status.setTextSize(18);
        status.setTextColor(Color.DKGRAY);

        Button button = new Button(this);
        button.setText("🎙️ اضغط للتحدث");
        button.setTextSize(24);

        layout.addView(title);
        layout.addView(status);
        layout.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                260
        ));

        setContentView(layout);

        startReceiver();

        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                isTalking = true;
                status.setText("يتم الإرسال الآن...");
                button.setText("🔴 أرسل صوتك");
                startSender();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                isTalking = false;
                status.setText("متصل محلياً — اضغط للتحدث");
                button.setText("🎙️ اضغط للتحدث");
                return true;
            }

            return true;
        });
    }

    private void startSender() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        new Thread(() -> {
            int sampleRate = 8000;
            int bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            AudioRecord recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                InetAddress address = InetAddress.getByName(targetIp);
                byte[] buffer = new byte[bufferSize];

                recorder.startRecording();

                while (isTalking) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        DatagramPacket packet = new DatagramPacket(buffer, read, address, port);
                        socket.send(packet);
                    }
                }

                recorder.stop();
                recorder.release();
                socket.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startReceiver() {
        new Thread(() -> {
            int sampleRate = 8000;
            int bufferSize = 4096;

            AudioTrack player = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
            );

            try {
                DatagramSocket socket = new DatagramSocket(port);
                byte[] buffer = new byte[bufferSize];

                player.play();

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    if (!isTalking) {
                        player.write(packet.getData(), 0, packet.getLength());
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
