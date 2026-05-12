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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);
        root.setBackgroundColor(Color.parseColor("#EAF4FB"));

        TextView logo = new TextView(this);
        logo.setText("📡 WiFi Intercom DZ");
        logo.setTextSize(28);
        logo.setTextColor(Color.BLACK);

        TextView subtitle = new TextView(this);
        subtitle.setText("اتصال محلي عبر Wi-Fi");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.DKGRAY);

        TextView status = new TextView(this);
        status.setText("🟢 جاهز للتحدث");
        status.setTextSize(18);
        status.setPadding(0, 40, 0, 40);

        Button ptt = new Button(this);
        ptt.setText("🎙️ اضغط مطولاً للتحدث");
        ptt.setTextSize(22);
        ptt.setAllCaps(false);
        ptt.setBackgroundColor(Color.parseColor("#2196F3"));
        ptt.setTextColor(Color.WHITE);

        LinearLayout.LayoutParams btnParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        260
                );

        root.addView(logo);
        root.addView(subtitle);
        root.addView(status);
        root.addView(ptt, btnParams);

        setContentView(root);

        startReceiver();

        ptt.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                isTalking = true;

                status.setText("🔴 جاري الإرسال...");
                ptt.setText("🛑 حرر الزر لإيقاف الإرسال");
                ptt.setBackgroundColor(Color.parseColor("#D32F2F"));

                startSender();

                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {

                isTalking = false;

                status.setText("🟢 جاهز للتحدث");
                ptt.setText("🎙️ اضغط مطولاً للتحدث");
                ptt.setBackgroundColor(Color.parseColor("#2196F3"));

                return true;
            }

            return true;
        });
    }

    private void startSender() {

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
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

                        DatagramPacket packet =
                                new DatagramPacket(
                                        buffer,
                                        read,
                                        address,
                                        port
                                );

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

                    DatagramPacket packet =
                            new DatagramPacket(buffer, buffer.length);

                    socket.receive(packet);

                    if (!isTalking) {

                        player.write(
                                packet.getData(),
                                0,
                                packet.getLength()
                        );
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }
}
