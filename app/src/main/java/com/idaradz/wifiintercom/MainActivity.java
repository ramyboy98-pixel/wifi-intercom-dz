package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {

    private static final int AUDIO_PORT = 55555;
    private static final int DISCOVERY_PORT = 55556;
    private static final String BROADCAST_IP = "255.255.255.255";

    private SettingsManager settings;
    private String username;
    private String currentChannel;
    private final String deviceId = Build.MODEL + "_" + System.currentTimeMillis();

    private volatile boolean isTalking = false;
    private volatile boolean running = true;

    private String selectedDeviceId = null;

    private TextView statusView, channelView, targetView, deviceBox, logs;
    private EditText usernameInput;

    private final ConcurrentHashMap<String, DeviceState> devices = new ConcurrentHashMap<>();

    private WifiManager.MulticastLock multicastLock;
    private PowerManager.WakeLock wakeLock;
    private AudioRecord recorder;
    private AudioTrack player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);

        settings = new SettingsManager(this);
        username = settings.getUsername();
        currentChannel = settings.getChannel();

        enableLocks();
        buildUi();

        startDiscoveryReceiver();
        startDiscoverySender();
        startVoiceReceiver();
        startCleanupLoop();

        log("SYSTEM READY");
        log("AES-GCM ACTIVE");
        log("CHANNEL = " + currentChannel);
    }

    private void buildUi() {
        boolean dark = settings.isDarkMode();

        int bg = dark ? Color.parseColor("#101418") : Color.parseColor("#F6F8FC");
        int card = dark ? Color.parseColor("#1B222C") : Color.WHITE;
        int text = dark ? Color.WHITE : Color.BLACK;
        int soft = dark ? Color.parseColor("#26313D") : Color.parseColor("#EEF3FA");

        ScrollView page = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 38, 24, 24);
        root.setBackgroundColor(bg);

        TextView title = label("📡 WiFi Intercom PRO", 30, text, true);
        title.setPadding(0, 0, 0, 18);

        LinearLayout userRow = new LinearLayout(this);
        userRow.setOrientation(LinearLayout.HORIZONTAL);
        userRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = label("👤", 42, text, false);
        usernameInput = new EditText(this);
        usernameInput.setText(username);
        usernameInput.setTextSize(20);
        usernameInput.setSingleLine(true);
        usernameInput.setTextColor(text);
        usernameInput.setHintTextColor(Color.GRAY);
        usernameInput.setBackgroundColor(card);
        usernameInput.setPadding(22, 6, 22, 6);

        userRow.addView(avatar, new LinearLayout.LayoutParams(80, 90));
        userRow.addView(usernameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                90
        ));

        channelView = label("📻 " + currentChannel, 24, Color.parseColor("#1565C0"), false);
        targetView = label("🎯 Target: ALL DEVICES", 20, text, false);

        LinearLayout saveCard = card(card);
        EditText dummy = new EditText(this);
        dummy.setHint("Username");
        dummy.setTextColor(text);
        dummy.setHintTextColor(Color.GRAY);
        dummy.setTextSize(22);

        Button save = button("💾  SAVE", "#1565C0", 22);
        save.setOnClickListener(v -> {
            String value = usernameInput.getText().toString().trim();
            if (!value.isEmpty()) {
                username = value;
                settings.setUsername(username);
                log("USERNAME SAVED");
            }
        });

        saveCard.addView(dummy, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                95
        ));
        saveCard.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                90
        ));

        LinearLayout channels = card(soft);
        channels.setOrientation(LinearLayout.HORIZONTAL);
        channels.addView(channelButton("GENERAL"));
        channels.addView(channelButton("KITCHEN"));
        channels.addView(channelButton("SECURITY"));
        channels.addView(channelButton("STORAGE"));

        LinearLayout mode = card(soft);
        mode.setOrientation(LinearLayout.HORIZONTAL);

        Button light = button("📡  Light", dark ? "#FFFFFF" : "#1565C0", 18);
        light.setTextColor(dark ? Color.BLACK : Color.WHITE);
        light.setOnClickListener(v -> {
            settings.setDarkMode(false);
            recreate();
        });

        Button darkBtn = button("💡  Dark", dark ? "#1565C0" : "#263238", 18);
        darkBtn.setOnClickListener(v -> {
            settings.setDarkMode(true);
            recreate();
        });

        mode.addView(light, new LinearLayout.LayoutParams(0, 85, 1f));
        mode.addView(darkBtn, new LinearLayout.LayoutParams(0, 85, 1f));

        TextView onlineTitle = label("🟢 ONLINE DEVICES", 22, text, false);

        deviceBox = label("No devices online", 20, text, false);
        deviceBox.setTypeface(Typeface.MONOSPACE);
        deviceBox.setPadding(22, 18, 22, 18);

        LinearLayout deviceCard = card(card);
        deviceCard.addView(deviceBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                210
        ));

        deviceCard.setOnClickListener(v -> {
            selectedDeviceId = null;
            targetView.setText("🎯 Target: ALL DEVICES");
            log("TARGET = ALL");
        });

        statusView = label("🟢 READY", 30, Color.BLACK, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setBackgroundColor(Color.parseColor("#E8F8EA"));
        statusView.setPadding(0, 26, 0, 26);

        Button ptt = button("🎙  HOLD TO TALK", "#C91414", 30);
        ptt.setPadding(0, 35, 0, 35);

        ptt.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startTalking();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopTalking();
                return true;
            }

            return true;
        });

        logs = label("", 16, text, false);
        logs.setTypeface(Typeface.MONOSPACE);
        logs.setPadding(20, 20, 20, 20);
        logs.setBackgroundColor(soft);

        root.addView(title);
        root.addView(userRow);
        root.addView(channelView);
        root.addView(targetView);
        root.addView(saveCard);
        root.addView(channels);
        root.addView(mode);
        root.addView(onlineTitle);
        root.addView(deviceCard);
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
        ));
        root.addView(ptt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                170
        ));
        root.addView(logs, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                330
        ));

        page.addView(root);
        setContentView(page);
    }

    private LinearLayout card(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(14, 14, 14, 14);
        box.setBackgroundColor(color);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(0, 12, 0, 12);
        box.setLayoutParams(p);

        return box;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String text, String color, int size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        return b;
    }

    private Button channelButton(String channel) {
        Button b = button(channel, "#DCE4F1", 15);
        b.setTextColor(Color.BLACK);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 80, 1f);
        p.setMargins(4, 0, 4, 0);
        b.setLayoutParams(p);

        b.setOnClickListener(v -> {
            currentChannel = channel;
            settings.setChannel(channel);
            channelView.setText("📻 " + currentChannel);
            log("CHANNEL = " + currentChannel);
        });

        return b;
    }

    private void startTalking() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            log("MIC PERMISSION REQUIRED");
            return;
        }

        if (isTalking) return;

        isTalking = true;
        beepStart();
        vibrate();

        statusView.setText("🔴 TALKING");
        statusView.setBackgroundColor(Color.parseColor("#FFE1E1"));

        recorder = AudioEngine.createRecorder();

        new Thread(() -> {
            try {
                recorder.startRecording();
                byte[] buffer = new byte[AudioEngine.AUDIO_CHUNK];

                while (isTalking) {
                    int read = recorder.read(buffer, 0, buffer.length);

                    if (read > 0) {
                        byte[] raw = new byte[read];
                        System.arraycopy(buffer, 0, raw, 0, read);
                        sendAudio(raw);
                    }
                }

            } catch (Exception e) {
                log("TX ERROR");
            }
        }).start();

        log("TX START");
    }

    private void stopTalking() {
        if (!isTalking) return;

        isTalking = false;
        beepStop();

        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
        } catch (Exception ignored) {}

        statusView.setText("🟢 READY");
        statusView.setBackgroundColor(Color.parseColor("#E8F8EA"));

        log("TX STOP");
    }

    private void sendAudio(byte[] rawAudio) {
        new Thread(() -> {
            try {
                byte[] encrypted = EncryptionManager.encrypt(rawAudio);

                String header =
                        "AUD|" + currentChannel + "|" + deviceId + "|" + username + "|";

                byte[] h = header.getBytes(StandardCharsets.UTF_8);
                byte[] packetData = new byte[h.length + encrypted.length];

                System.arraycopy(h, 0, packetData, 0, h.length);
                System.arraycopy(encrypted, 0, packetData, h.length, encrypted.length);

                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);

                InetAddress address;

                if (selectedDeviceId != null && devices.containsKey(selectedDeviceId)) {
                    address = InetAddress.getByName(devices.get(selectedDeviceId).ip);
                } else {
                    address = InetAddress.getByName(BROADCAST_IP);
                }

                DatagramPacket packet = new DatagramPacket(
                        packetData,
                        packetData.length,
                        address,
                        AUDIO_PORT
                );

                socket.send(packet);
                socket.close();

            } catch (Exception e) {
                log("SEND ERROR");
            }
        }).start();
    }

    private void startVoiceReceiver() {
        new Thread(() -> {
            try {
                int bufferSize = 8192;

                player = new AudioTrack.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build()
                        )
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(AudioEngine.SAMPLE_RATE)
                                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                        .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build();

                DatagramSocket socket = new DatagramSocket(AUDIO_PORT);
                byte[] buffer = new byte[32768];

                player.play();

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    parseAudioPacket(packet);
                }

            } catch (Exception e) {
                log("RX ERROR");
            }
        }).start();
    }

    private void parseAudioPacket(DatagramPacket packet) {
        try {
            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

            int[] bars = findBars(data, 4);
            if (bars == null) return;

            String type = new String(data, 0, bars[0], StandardCharsets.UTF_8);
            if (!type.equals("AUD")) return;

            String channel = new String(data, bars[0] + 1, bars[1] - bars[0] - 1, StandardCharsets.UTF_8);
            String senderId = new String(data, bars[1] + 1, bars[2] - bars[1] - 1, StandardCharsets.UTF_8);
            String senderName = new String(data, bars[2] + 1, bars[3] - bars[2] - 1, StandardCharsets.UTF_8);

            if (senderId.equals(deviceId)) return;
            if (!channel.equals(currentChannel)) return;
            if (isTalking) return;

            int audioStart = bars[3] + 1;
            byte[] encrypted = new byte[data.length - audioStart];
            System.arraycopy(data, audioStart, encrypted, 0, encrypted.length);

            byte[] audio = EncryptionManager.decrypt(encrypted);
            player.write(audio, 0, audio.length);

            DeviceState d = devices.get(senderId);
            if (d != null) {
                d.talking = true;
                d.lastSeen = System.currentTimeMillis();
                updateDeviceList();
            }

            runOnUiThread(() -> {
                statusView.setText("🔊 RECEIVING: " + senderName);
                statusView.setBackgroundColor(Color.parseColor("#FFF8D6"));
            });

        } catch (Exception ignored) {}
    }

    private int[] findBars(byte[] data, int count) {
        int[] result = new int[count];
        int found = 0;

        for (int i = 0; i < data.length && found < count; i++) {
            if (data[i] == '|') {
                result[found] = i;
                found++;
            }
        }

        return found == count ? result : null;
    }

    private void startDiscoverySender() {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                InetAddress address = InetAddress.getByName(BROADCAST_IP);

                while (running) {
                    String message =
                            "DISC|" + deviceId + "|" + username + "|" + currentChannel + "|" + isTalking;

                    byte[] data = message.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket packet = new DatagramPacket(
                            data,
                            data.length,
                            address,
                            DISCOVERY_PORT
                    );

                    socket.send(packet);
                    Thread.sleep(1500);
                }

                socket.close();

            } catch (Exception e) {
                log("DISCOVERY TX ERROR");
            }
        }).start();
    }

    private void startDiscoveryReceiver() {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT);
                byte[] buffer = new byte[2048];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(
                            packet.getData(),
                            0,
                            packet.getLength(),
                            StandardCharsets.UTF_8
                    );

                    if (!msg.startsWith("DISC|")) continue;

                    String[] p = msg.split("\\|");

                    if (p.length >= 5) {
                        String id = p[1];
                        String name = p[2];
                        String channel = p[3];
                        boolean talking = Boolean.parseBoolean(p[4]);
                        String ip = packet.getAddress().getHostAddress();

                        if (!id.equals(deviceId)) {
                            DeviceState existing = devices.get(id);

                            if (existing == null) {
                                devices.put(id, new DeviceState(id, name, channel, ip, talking));
                                log("DEVICE ONLINE: " + name);
                            } else {
                                existing.name = name;
                                existing.channel = channel;
                                existing.ip = ip;
                                existing.talking = talking;
                                existing.lastSeen = System.currentTimeMillis();
                            }

                            updateDeviceList();
                        }
                    }
                }

            } catch (Exception e) {
                log("DISCOVERY RX ERROR");
            }
        }).start();
    }

    private void startCleanupLoop() {
        new Thread(() -> {
            while (running) {
                try {
                    long now = System.currentTimeMillis();

                    for (String id : devices.keySet()) {
                        DeviceState d = devices.get(id);

                        if (d != null && now - d.lastSeen > 6000) {
                            devices.remove(id);
                            log("DEVICE OFFLINE: " + d.name);
                            continue;
                        }

                        if (d != null && now - d.lastSeen > 2500) {
                            d.talking = false;
                        }
                    }

                    updateDeviceList();
                    Thread.sleep(2000);

                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void updateDeviceList() {
        runOnUiThread(() -> {
            if (devices.isEmpty()) {
                deviceBox.setText("No devices online");
                return;
            }

            StringBuilder builder = new StringBuilder();

            for (DeviceState d : devices.values()) {
                builder.append(d.display()).append("\n");
            }

            deviceBox.setText(builder.toString());

            if (!isTalking) {
                statusView.setText("🟢 READY");
                statusView.setBackgroundColor(Color.parseColor("#E8F8EA"));
            }
        });
    }

    private void enableLocks() {
        try {
            WifiManager wifi =
                    (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

            multicastLock = wifi.createMulticastLock("wifi_intercom_multicast");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "wifiintercom:wakelock"
            );

            wakeLock.acquire();

        } catch (Exception ignored) {}
    }

    private void beepStart() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (Exception ignored) {}
    }

    private void beepStop() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 80);
        } catch (Exception ignored) {}
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

            if (vibrator == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createOneShot(
                                60,
                                VibrationEffect.DEFAULT_AMPLITUDE
                        )
                );
            } else {
                vibrator.vibrate(60);
            }

        } catch (Exception ignored) {}
    }

    private void log(String text) {
        runOnUiThread(() -> {
            if (logs == null) return;

            String time = new SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
            ).format(new Date());

            logs.append("[" + time + "] " + text + "\n");
        });
    }

    @Override
    protected void onDestroy() {
        running = false;
        stopTalking();

        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception ignored) {}

        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignored) {}

        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}

        super.onDestroy();
    }
}
