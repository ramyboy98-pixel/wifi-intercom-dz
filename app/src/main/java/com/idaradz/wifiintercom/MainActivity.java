package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private volatile boolean isTalking = false;

    private final int VOICE_PORT = 55555;

    private final int DISCOVERY_PORT = 55556;

    private final String BROADCAST_IP = "255.255.255.255";

    private final String deviceName = Build.MODEL;

    private final String deviceId =
            Build.MODEL + "_" + System.currentTimeMillis();

    private final ArrayList<String> devices =
            new ArrayList<>();

    private ArrayAdapter<String> adapter;

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        requestPermissions(
                new String[]{
                        Manifest.permission.RECORD_AUDIO
                },
                10
        );

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(35, 55, 35, 35);

        root.setBackgroundColor(
                Color.parseColor("#EAF4FB")
        );

        TextView title = new TextView(this);

        title.setText("📡 WiFi Intercom DZ");

        title.setTextSize(30);

        title.setTextColor(Color.BLACK);

        title.setPadding(0,0,0,15);

        TextView info = new TextView(this);

        info.setText(
                "📱 " + deviceName
        );

        info.setTextSize(16);

        info.setTextColor(Color.DKGRAY);

        info.setPadding(0,0,0,20);

        TextView online = new TextView(this);

        online.setText("🟢 الأجهزة المتصلة");

        online.setTextSize(18);

        online.setTextColor(Color.BLACK);

        online.setPadding(0,0,0,20);

        ListView listView = new ListView(this);

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                devices
        );

        listView.setAdapter(adapter);

        status = new TextView(this);

        status.setText("🟢 جاهز للتحدث");

        status.setTextSize(18);

        status.setGravity(Gravity.CENTER);

        status.setPadding(0,20,0,20);

        Button ptt = new Button(this);

        ptt.setText("🎙️ اضغط مطولاً للتحدث");

        ptt.setTextSize(24);

        ptt.setAllCaps(false);

        ptt.setTextColor(Color.WHITE);

        ptt.setBackgroundColor(
                Color.parseColor("#2196F3")
        );

        LinearLayout.LayoutParams btnParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        260
                );

        btnParams.setMargins(0,20,0,0);

        root.addView(title);

        root.addView(info);

        root.addView(online);

        root.addView(
                listView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        350
                )
        );

        root.addView(status);

        root.addView(ptt, btnParams);

        setContentView(root);

        startVoiceReceiver();

        startDiscoveryReceiver();

        startDiscoverySender();

        ptt.setOnTouchListener((v, event) -> {

            if (event.getAction()
                    == MotionEvent.ACTION_DOWN) {

                vibrate(60);

                isTalking = true;

                status.setText("🔴 جاري الإرسال");

                ptt.setText("🛑 حرر الزر");

                ptt.setBackgroundColor(
                        Color.parseColor("#D32F2F")
                );

                startVoiceSender();

                return true;
            }

            if (event.getAction()
                    == MotionEvent.ACTION_UP
                    || event.getAction()
                    == MotionEvent.ACTION_CANCEL) {

                isTalking = false;

                status.setText("🟢 جاهز للتحدث");

                ptt.setText("🎙️ اضغط مطولاً للتحدث");

                ptt.setBackgroundColor(
                        Color.parseColor("#2196F3")
                );

                return true;
            }

            return true;
        });
    }

    private void vibrate(int ms) {

        Vibrator vibrator =
                (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (vibrator != null) {
            vibrator.vibrate(ms);
        }
    }

    private void startVoiceSender() {

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        new Thread(() -> {

            int sampleRate = 16000;

            int bufferSize =
                    AudioRecord.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            AudioRecord recorder =
                    new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                    );

            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(
                        recorder.getAudioSessionId()
                );
            }

            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(
                        recorder.getAudioSessionId()
                );
            }

            try {

                DatagramSocket socket =
                        new DatagramSocket();

                socket.setBroadcast(true);

                InetAddress address =
                        InetAddress.getByName(
                                BROADCAST_IP
                        );

                byte[] buffer =
                        new byte[bufferSize];

                recorder.startRecording();

                while (isTalking) {

                    int read =
                            recorder.read(
                                    buffer,
                                    0,
                                    buffer.length
                            );

                    if (read > 0) {

                        DatagramPacket packet =
                                new DatagramPacket(
                                        buffer,
                                        read,
                                        address,
                                        VOICE_PORT
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

    private void startVoiceReceiver() {

        new Thread(() -> {

            int sampleRate = 16000;

            int bufferSize = 4096;

            AudioTrack player;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                player = new AudioTrack.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(
                                                AudioAttributes.USAGE_MEDIA
                                        )
                                        .setContentType(
                                                AudioAttributes.CONTENT_TYPE_SPEECH
                                        )
                                        .build()
                        )
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setEncoding(
                                                AudioFormat.ENCODING_PCM_16BIT
                                        )
                                        .setSampleRate(
                                                sampleRate
                                        )
                                        .setChannelMask(
                                                AudioFormat.CHANNEL_OUT_MONO
                                        )
                                        .build()
                        )
                        .setBufferSizeInBytes(
                                bufferSize
                        )
                        .build();

            } else {

                player = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                );
            }

            try {

                DatagramSocket socket =
                        new DatagramSocket(
                                VOICE_PORT
                        );

                byte[] buffer =
                        new byte[bufferSize];

                player.play();

                while (true) {

                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length
                            );

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

    private void startDiscoverySender() {

        new Thread(() -> {

            try {

                DatagramSocket socket =
                        new DatagramSocket();

                socket.setBroadcast(true);

                InetAddress address =
                        InetAddress.getByName(
                                BROADCAST_IP
                        );

                while (true) {

                    String message =
                            "DEVICE:"
                                    + deviceId
                                    + ":"
                                    + deviceName;

                    byte[] data =
                            message.getBytes();

                    DatagramPacket packet =
                            new DatagramPacket(
                                    data,
                                    data.length,
                                    address,
                                    DISCOVERY_PORT
                            );

                    socket.send(packet);

                    Thread.sleep(3000);
                }

            } catch (Exception e) {

                e.printStackTrace();
            }

        }).start();
    }

    private void startDiscoveryReceiver() {

        new Thread(() -> {

            try {

                DatagramSocket socket =
                        new DatagramSocket(
                                DISCOVERY_PORT
                        );

                byte[] buffer =
                        new byte[1024];

                while (true) {

                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length
                            );

                    socket.receive(packet);

                    String msg =
                            new String(
                                    packet.getData(),
                                    0,
                                    packet.getLength()
                            );

                    if (msg.startsWith("DEVICE:")) {

                        String[] parts =
                                msg.split(":");

                        if (parts.length >= 3) {

                            String incomingId =
                                    parts[1];

                            String incomingName =
                                    parts[2];

                            if (!incomingId.equals(deviceId)) {

                                String finalName =
                                        "🟢 "
                                                + incomingName;

                                if (!devices.contains(finalName)) {

                                    runOnUiThread(() -> {

                                        devices.add(
                                                finalName
                                        );

                                        adapter.notifyDataSetChanged();
                                    });
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {

                e.printStackTrace();
            }

        }).start();
    }
}
