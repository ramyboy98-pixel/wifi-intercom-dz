package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
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
import android.os.PowerManager;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private volatile boolean isTalking = false;

    private final int VOICE_PORT = 55555;

    private final int DISCOVERY_PORT = 55556;

    private final String BROADCAST_IP = "255.255.255.255";

    private String currentChannel = "GENERAL";

    private final String deviceName = Build.MODEL;

    private final String deviceId =
            Build.MODEL + "_" + System.currentTimeMillis();

    private final ArrayList<String> devices =
            new ArrayList<>();

    private ArrayAdapter<String> adapter;

    private TextView status;

    private TextView logs;

    private TextView channelText;

    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        requestPermissions(
                new String[]{
                        Manifest.permission.RECORD_AUDIO
                },
                10
        );

        keepCpuAlive();

        createNotification();

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(25,45,25,25);

        root.setBackgroundColor(
                Color.parseColor("#EAF4FB")
        );

        TextView title = new TextView(this);

        title.setText("📡 WiFi Intercom DZ");

        title.setTextSize(30);

        title.setTextColor(Color.BLACK);

        channelText = new TextView(this);

        channelText.setText(
                "📻 القناة الحالية: " + currentChannel
        );

        channelText.setTextSize(17);

        channelText.setTextColor(
                Color.parseColor("#1565C0")
        );

        channelText.setPadding(0,10,0,15);

        TextView info = new TextView(this);

        info.setText("📱 " + deviceName);

        info.setTextSize(16);

        info.setTextColor(Color.DKGRAY);

        LinearLayout channels =
                new LinearLayout(this);

        channels.setOrientation(
                LinearLayout.HORIZONTAL
        );

        Button generalBtn = new Button(this);

        generalBtn.setText("GENERAL");

        Button kitchenBtn = new Button(this);

        kitchenBtn.setText("KITCHEN");

        Button securityBtn = new Button(this);

        securityBtn.setText("SECURITY");

        Button storageBtn = new Button(this);

        storageBtn.setText("STORAGE");

        channels.addView(generalBtn);

        channels.addView(kitchenBtn);

        channels.addView(securityBtn);

        channels.addView(storageBtn);

        TextView online = new TextView(this);

        online.setText("🟢 الأجهزة المتصلة");

        online.setTextSize(18);

        online.setPadding(0,20,0,10);

        ListView listView = new ListView(this);

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                devices
        );

        listView.setAdapter(adapter);

        status = new TextView(this);

        status.setText("🟢 جاهز للتحدث");

        status.setGravity(Gravity.CENTER);

        status.setTextSize(18);

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
                        220
                );

        btnParams.setMargins(0,15,0,20);

        TextView logsTitle = new TextView(this);

        logsTitle.setText("📜 سجل النشاط");

        logsTitle.setTextSize(18);

        ScrollView scrollView = new ScrollView(this);

        logs = new TextView(this);

        logs.setTextColor(Color.DKGRAY);

        logs.setTextSize(14);

        logs.setPadding(15,15,15,15);

        logs.setBackgroundColor(
                Color.parseColor("#DCEAF7")
        );

        scrollView.addView(logs);

        root.addView(title);

        root.addView(channelText);

        root.addView(info);

        root.addView(channels);

        root.addView(online);

        root.addView(
                listView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        250
                )
        );

        root.addView(status);

        root.addView(ptt, btnParams);

        root.addView(logsTitle);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        320
                )
        );

        setContentView(root);

        log("تم تشغيل النظام");

        generalBtn.setOnClickListener(
                v -> switchChannel("GENERAL")
        );

        kitchenBtn.setOnClickListener(
                v -> switchChannel("KITCHEN")
        );

        securityBtn.setOnClickListener(
                v -> switchChannel("SECURITY")
        );

        storageBtn.setOnClickListener(
                v -> switchChannel("STORAGE")
        );

        startVoiceReceiver();

        startDiscoveryReceiver();

        startDiscoverySender();

        ptt.setOnTouchListener((v, event) -> {

            if (event.getAction()
                    == MotionEvent.ACTION_DOWN) {

                vibrate(50);

                isTalking = true;

                status.setText("🔴 جاري الإرسال");

                ptt.setText("🛑 حرر الزر");

                ptt.setBackgroundColor(
                        Color.parseColor("#D32F2F")
                );

                log("بدأ الإرسال على قناة "
                        + currentChannel);

                startVoiceSender();

                return true;
            }

            if (event.getAction()
                    == MotionEvent.ACTION_UP
                    || event.getAction()
                    == MotionEvent.ACTION_CANCEL) {

                isTalking = false;

                status.setText("🟢 جاهز للتحدث");

                ptt.setText(
                        "🎙️ اضغط مطولاً للتحدث"
                );

                ptt.setBackgroundColor(
                        Color.parseColor("#2196F3")
                );

                log("تم إيقاف الإرسال");

                return true;
            }

            return true;
        });
    }

    private void keepCpuAlive() {

        PowerManager powerManager =
                (PowerManager) getSystemService(
                        Context.POWER_SERVICE
                );

        wakeLock =
                powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "wifiintercom:wakelock"
                );

        wakeLock.acquire();
    }

    private void createNotification() {

        String channelId = "wifi_intercom";

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            "WiFi Intercom",
                            NotificationManager.IMPORTANCE_LOW
                    );

            manager.createNotificationChannel(
                    channel
            );
        }

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        channelId
                )
                        .setContentTitle(
                                "WiFi Intercom DZ"
                        )
                        .setContentText(
                                "📡 التطبيق يعمل"
                        )
                        .setSmallIcon(
                                android.R.drawable.presence_audio_online
                        )
                        .build();

        manager.notify(1, notification);
    }

    private void switchChannel(String channel) {

        currentChannel = channel;

        channelText.setText(
                "📻 القناة الحالية: "
                        + currentChannel
        );

        log("تم التبديل إلى "
                + channel);
    }

    private void log(String text) {

        runOnUiThread(() -> {

            String time =
                    new SimpleDateFormat(
                            "HH:mm:ss",
                            Locale.getDefault()
                    ).format(new Date());

            logs.append(
                    "[" + time + "] "
                            + text + "\n"
            );
        });
    }

    private void vibrate(int ms) {

        Vibrator vibrator =
                (Vibrator) getSystemService(
                        VIBRATOR_SERVICE
                );

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

                        String header =
                                currentChannel
                                        + "|"
                                        + deviceId
                                        + "|";

                        byte[] headerBytes =
                                header.getBytes();

                        byte[] finalData =
                                new byte[
                                        headerBytes.length + read
                                ];

                        System.arraycopy(
                                headerBytes,
                                0,
                                finalData,
                                0,
                                headerBytes.length
                        );

                        System.arraycopy(
                                buffer,
                                0,
                                finalData,
                                headerBytes.length,
                                read
                        );

                        DatagramPacket packet =
                                new DatagramPacket(
                                        finalData,
                                        finalData.length,
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

                log("خطأ في الإرسال");
            }

        }).start();
    }

    private void startVoiceReceiver() {

        new Thread(() -> {

            int sampleRate = 16000;

            int bufferSize = 8192;

            AudioTrack player;

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.LOLLIPOP) {

                player =
                        new AudioTrack.Builder()
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

                log("نظام استقبال الصوت يعمل");

                while (true) {

                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length
                            );

                    socket.receive(packet);

                    String packetText =
                            new String(
                                    packet.getData(),
                                    0,
                                    packet.getLength()
                            );

                    int first =
                            packetText.indexOf("|");

                    int second =
                            packetText.indexOf(
                                    "|",
                                    first + 1
                            );

                    if (first > 0 && second > 0) {

                        String channel =
                                packetText.substring(
                                        0,
                                        first
                                );

                        String senderId =
                                packetText.substring(
                                        first + 1,
                                        second
                                );

                        if (!senderId.equals(deviceId)
                                && channel.equals(currentChannel)
                                && !isTalking) {

                            int audioStart =
                                    second + 1;

                            int audioLength =
                                    packet.getLength()
                                            - audioStart;

                            player.write(
                                    packet.getData(),
                                    audioStart,
                                    audioLength
                            );
                        }
                    }
                }

            } catch (Exception e) {

                log("خطأ مستقبل الصوت");
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
                                    + deviceName
                                    + ":"
                                    + currentChannel;

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

                log("خطأ اكتشاف الأجهزة");
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

                        if (parts.length >= 4) {

                            String incomingId =
                                    parts[1];

                            String incomingName =
                                    parts[2];

                            String incomingChannel =
                                    parts[3];

                            if (!incomingId.equals(deviceId)) {

                                String finalName =
                                        "🟢 "
                                                + incomingName
                                                + " ["
                                                + incomingChannel
                                                + "]";

                                if (!devices.contains(finalName)) {

                                    runOnUiThread(() -> {

                                        devices.add(
                                                finalName
                                        );

                                        adapter.notifyDataSetChanged();
                                    });

                                    log(
                                            "تم اكتشاف "
                                                    + incomingName
                                    );
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {

                log("خطأ مستقبل الأجهزة");
            }

        }).start();
    }
}
