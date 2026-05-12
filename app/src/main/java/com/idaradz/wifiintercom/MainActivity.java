package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

    private TextView statusView;
    private TextView channelView;
    private TextView targetView;
    private TextView deviceBox;
    private TextView logs;
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable strokeBg(int color, int stroke, int strokeColor, int radius) {
        GradientDrawable d = bg(color, radius);
        d.setStroke(dp(stroke), strokeColor);
        return d;
    }

    private void buildUi() {
        boolean dark = settings.isDarkMode();

        int pageBg = dark ? Color.parseColor("#F6F8FC") : Color.parseColor("#F6F8FC");
        int text = Color.BLACK;

        ScrollView scrollPage = new ScrollView(this);
        scrollPage.setFillViewport(true);
        scrollPage.setBackgroundColor(pageBg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(pageBg);

        TextView title = text("📡 WiFi Intercom PRO", 31, text, true);
        title.setPadding(0, 0, 0, dp(14));

        LinearLayout userRow = new LinearLayout(this);
        userRow.setOrientation(LinearLayout.HORIZONTAL);
        userRow.setGravity(Gravity.CENTER_VERTICAL);
        userRow.setPadding(0, 0, 0, dp(8));

        TextView avatar = text("👤", 42, Color.parseColor("#1976D2"), false);
        avatar.setGravity(Gravity.CENTER);

        usernameInput = new EditText(this);
        usernameInput.setText(username);
        usernameInput.setTextSize(20);
        usernameInput.setSingleLine(true);
        usernameInput.setTextColor(Color.BLACK);
        usernameInput.setHintTextColor(Color.GRAY);
        usernameInput.setPadding(dp(16), 0, dp(16), 0);
        usernameInput.setBackground(strokeBg(Color.WHITE, 1, Color.parseColor("#9E9E9E"), 28));

        userRow.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(60)));
        userRow.addView(usernameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));

        channelView = text("📻 " + currentChannel, 24, Color.parseColor("#1565C0"), false);
        targetView = text("🎯 Target: ALL DEVICES", 21, Color.BLACK, false);

        LinearLayout saveCard = card(Color.WHITE, 16);
        saveCard.setPadding(dp(12), dp(12), dp(12), dp(12));

        EditText saveInput = new EditText(this);
        saveInput.setHint("Username");
        saveInput.setTextSize(22);
        saveInput.setSingleLine(true);
        saveInput.setTextColor(Color.BLACK);
        saveInput.setHintTextColor(Color.GRAY);
        saveInput.setPadding(dp(16), 0, dp(16), 0);
        saveInput.setBackground(strokeBg(Color.WHITE, 1, Color.parseColor("#9E9E9E"), 2));

        Button saveBtn = button("💾  SAVE", "#1565C0", 21, 26);
        saveBtn.setOnClickListener(v -> {
            String value = usernameInput.getText().toString().trim();
            if (!value.isEmpty()) {
                username = value;
                settings.setUsername(username);
                saveInput.setText("");
                log("USERNAME SAVED");
            }
        });

        saveCard.addView(saveInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        LinearLayout.LayoutParams saveBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
        );
        saveBtnParams.setMargins(0, dp(12), 0, 0);
        saveCard.addView(saveBtn, saveBtnParams);

        LinearLayout channelsCard = card(Color.parseColor("#E8EEF7"), 28);
        channelsCard.setOrientation(LinearLayout.HORIZONTAL);
        channelsCard.setPadding(dp(4), dp(4), dp(4), dp(4));
        channelsCard.addView(channelButton("GENERAL"));
        channelsCard.addView(channelButton("KITCHEN"));
        channelsCard.addView(channelButton("SECURITY"));
        channelsCard.addView(channelButton("STORAGE"));

        LinearLayout modeCard = card(Color.parseColor("#263238"), 28);
        modeCard.setOrientation(LinearLayout.HORIZONTAL);
        modeCard.setPadding(dp(2), dp(2), dp(2), dp(2));

        Button light = button("📡  Light", "#FFFFFF", 18, 28);
        light.setTextColor(Color.BLACK);
        Button darkBtn = button("💡  Dark", "#263238", 18, 28);

        light.setOnClickListener(v -> {
            settings.setDarkMode(false);
            recreate();
        });

        darkBtn.setOnClickListener(v -> {
            settings.setDarkMode(true);
            recreate();
        });

        modeCard.addView(light, new LinearLayout.LayoutParams(0, dp(58), 1f));
        modeCard.addView(darkBtn, new LinearLayout.LayoutParams(0, dp(58), 1f));

        TextView onlineTitle = text("🟢 ONLINE DEVICES", 22, Color.BLACK, false);
        onlineTitle.setPadding(0, dp(8), 0, dp(8));

        LinearLayout deviceCard = card(Color.WHITE, 10);
        deviceBox = text("No devices online", 19, Color.BLACK, false);
        deviceBox.setTypeface(Typeface.MONOSPACE);
        deviceBox.setPadding(dp(16), dp(14), dp(16), dp(14));
        deviceCard.addView(deviceBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(150)
        ));

        statusView = text("🟢  READY", 30, Color.BLACK, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setBackground(strokeBg(Color.parseColor("#E8F8EA"), 1, Color.parseColor("#4CAF50"), 12));

        Button ptt = button("🎙  HOLD TO TALK", "#C91414", 30, 10);
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

        logs = text("", 16, Color.BLACK, false);
        logs.setTypeface(Typeface.MONOSPACE);
        logs.setPadding(dp(14), dp(14), dp(14), dp(14));
        logs.setBackground(bg(Color.parseColor("#EEF3FA"), 10));

        add(root, title);
        add(root, userRow);
        add(root, channelView);
        add(root, targetView);
        add(root, saveCard);
        add(root, channelsCard);
        add(root, modeCard);
        add(root, onlineTitle);
        add(root, deviceCard);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(88)
        );
        statusParams.setMargins(0, dp(12), 0, dp(12));
        root.addView(statusView, statusParams);

        LinearLayout.LayoutParams pttParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(116)
        );
        pttParams.setMargins(0, dp(8), 0, dp(16));
        root.addView(ptt, pttParams);

        root.addView(logs, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(210)
        ));

        scrollPage.addView(root);
        setContentView(scrollPage);
    }

    private void add(LinearLayout root, android.view.View view) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(0, 0, 0, dp(10));
        root.addView(view, p);
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(bg(color, radius));
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value, String color, int size, int radius) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(bg(Color.parseColor(color), radius));
        return b;
    }

    private Button channelButton(String channel) {
        boolean active = channel.equals(currentChannel);

        Button b = button(
                channel,
                active ? "#405A8A" : "#E8EEF7",
                14,
                24
        );

        b.setTextColor(active ? Color.WHITE : Color.BLACK);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(p);

        b.setOnClickListener(v -> {
            currentChannel = channel;
            settings.setChannel(channel);
            channelView.setText("📻 " + currentChannel);
            buildUi();
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

        statusView.setText("🔴  TALKING");
        statusView.setBackground(strokeBg(Color.parseColor("#FFE1E1"), 1, Color.RED, 12));

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

        statusView.setText("🟢  READY");
        statusView.setBackground(strokeBg(Color.parseColor("#E8F8EA"), 1, Color.parseColor("#4CAF50"), 12));

        log("TX STOP");
    }

    private void sendAudio(byte[] rawAudio) {
        new Thread(() -> {
            try {
                byte[] encrypted = EncryptionManager.encrypt(rawAudio);

                String header = "AUD|" + currentChannel + "|" + deviceId + "|" + username + "|";

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

                DatagramSocket socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress(AUDIO_PORT));

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
                statusView.setText("🔊  RECEIVING: " + senderName);
                statusView.setBackground(strokeBg(Color.parseColor("#FFF8D6"), 1, Color.parseColor("#FBC02D"), 12));
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
                DatagramSocket socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress(DISCOVERY_PORT));

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
                statusView.setText("🟢  READY");
                statusView.setBackground(strokeBg(Color.parseColor("#E8F8EA"), 1, Color.parseColor("#4CAF50"), 12));
            }
        });
    }

    private void enableLocks() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

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
