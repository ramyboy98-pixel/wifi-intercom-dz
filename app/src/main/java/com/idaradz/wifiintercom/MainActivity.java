package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private TextView userView;
    private TextView channelView;
    private TextView selectedView;
    private TextView logs;

    private ListView deviceList;
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> deviceRows = new ArrayList<>();
    private final ArrayList<String> deviceIds = new ArrayList<>();

    private final ConcurrentHashMap<String, DeviceState> devices = new ConcurrentHashMap<>();

    private WifiManager.MulticastLock multicastLock;
    private PowerManager.WakeLock wakeLock;

    private AudioRecord recorder;
    private AudioTrack player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                1
        );

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

        int bg = dark ? Color.parseColor("#101418") : Color.parseColor("#EAF4FB");
        int card = dark ? Color.parseColor("#1A222B") : Color.parseColor("#DDEAF5");
        int text = dark ? Color.WHITE : Color.BLACK;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(25, 45, 25, 25);
        root.setBackgroundColor(bg);

        TextView title = new TextView(this);
        title.setText("📡 WiFi Intercom PRO");
        title.setTextColor(text);
        title.setTextSize(30);

        userView = new TextView(this);
        userView.setText("👤 " + username);
        userView.setTextColor(text);
        userView.setTextSize(16);

        channelView = new TextView(this);
        channelView.setText("📻 " + currentChannel);
        channelView.setTextColor(Color.parseColor("#42A5F5"));
        channelView.setTextSize(18);
        channelView.setPadding(0, 8, 0, 10);

        selectedView = new TextView(this);
        selectedView.setText("🎯 Target: ALL DEVICES");
        selectedView.setTextColor(text);
        selectedView.setTextSize(15);
        selectedView.setPadding(0, 5, 0, 10);

        EditText usernameInput = new EditText(this);
        usernameInput.setHint("Username");
        usernameInput.setText(username);
        usernameInput.setTextColor(text);
        usernameInput.setHintTextColor(Color.GRAY);

        Button saveUser = button("💾 SAVE", "#1E88E5");
        saveUser.setOnClickListener(v -> {
            String value = usernameInput.getText().toString().trim();
            if (!value.isEmpty()) {
                username = value;
                settings.setUsername(username);
                userView.setText("👤 " + username);
                log("USERNAME SAVED");
            }
        });

        LinearLayout channels = new LinearLayout(this);
        channels.setOrientation(LinearLayout.HORIZONTAL);
        channels.addView(channelButton("GENERAL"));
        channels.addView(channelButton("KITCHEN"));
        channels.addView(channelButton("SECURITY"));
        channels.addView(channelButton("STORAGE"));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button allBtn = button("📡 ALL", "#455A64");
        allBtn.setOnClickListener(v -> {
            selectedDeviceId = null;
            selectedView.setText("🎯 Target: ALL DEVICES");
            log("TARGET = ALL");
        });

        Button darkBtn = button(dark ? "☀ LIGHT" : "🌙 DARK", "#6A1B9A");
        darkBtn.setOnClickListener(v -> {
            settings.setDarkMode(!settings.isDarkMode());
            recreate();
        });

        actions.addView(allBtn, new LinearLayout.LayoutParams(0, 100, 1f));
        actions.addView(darkBtn, new LinearLayout.LayoutParams(0, 100, 1f));

        TextView onlineTitle = new TextView(this);
        onlineTitle.setText("🟢 ONLINE DEVICES");
        onlineTitle.setTextColor(text);
        onlineTitle.setTextSize(16);
        onlineTitle.setPadding(0, 12, 0, 6);

        deviceList = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceRows);
        deviceList.setAdapter(adapter);

        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < deviceIds.size()) {
                selectedDeviceId = deviceIds.get(position);
                DeviceState d = devices.get(selectedDeviceId);
                if (d != null) {
                    selectedView.setText("🎯 Target: " + d.name + " / " + d.ip);
                    log("TARGET = " + d.name);
                }
            }
        });

        statusView = new TextView(this);
        statusView.setText("🟢 READY");
        statusView.setTextColor(Color.GREEN);
        statusView.setTextSize(22);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, 15, 0, 15);

        Button ptt = button("🎙 HOLD TO TALK", "#D32F2F");
        ptt.setTextSize(24);

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

        ScrollView scroll = new ScrollView(this);
        logs = new TextView(this);
        logs.setTextColor(text);
        logs.setTextSize(14);
        logs.setPadding(15, 15, 15, 15);
        logs.setBackgroundColor(card);
        scroll.addView(logs);

        root.addView(title);
        root.addView(userView);
        root.addView(channelView);
        root.addView(selectedView);
        root.addView(usernameInput);
        root.addView(saveUser);
        root.addView(channels);
        root.addView(actions);
        root.addView(onlineTitle);
        root.addView(deviceList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                260
        ));
        root.addView(statusView);
        root.addView(ptt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                230
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                430
        ));

        setContentView(root);
    }

    private Button button(String text, String color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        return b;
    }

    private Button channelButton(String channel) {
        Button b = button(channel, "#1565C0");
        b.setTextSize(12);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 105, 1f);
        params.setMargins(4, 0, 4, 0);
        b.setLayoutParams(params);

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

        statusView.setText("🔴 TALKING...");
        statusView.setTextColor(Color.RED);

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
        statusView.setTextColor(Color.GREEN);

        log("TX STOP");
    }

    private void sendAudio(byte[] rawAudio) {

        new Thread(() -> {
            try {
                byte[] encrypted = CryptoManager.encrypt(rawAudio);

                String header =
                        "AUD|" +
                                currentChannel + "|" +
                                deviceId + "|" +
                                username + "|";

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

            byte[] audio = CryptoManager.decrypt(encrypted);

            player.write(audio, 0, audio.length);

            DeviceState d = devices.get(senderId);
            if (d != null) {
                d.talking = true;
                d.lastSeen = System.currentTimeMillis();
                updateDeviceList();
            }

            runOnUiThread(() -> {
                statusView.setText("🔊 RECEIVING: " + senderName);
                statusView.setTextColor(Color.YELLOW);
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
                            "DISC|" +
                                    deviceId + "|" +
                                    username + "|" +
                                    currentChannel + "|" +
                                    isTalking;

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
                        }

                        if (d != null && now - d.lastSeen > 2000) {
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
            deviceRows.clear();
            deviceIds.clear();

            for (DeviceState d : devices.values()) {
                deviceRows.add(d.display());
                deviceIds.add(d.id);
            }

            if (deviceRows.isEmpty()) {
                deviceRows.add("No devices online");
                deviceIds.add("");
            }

            adapter.notifyDataSetChanged();

            if (!isTalking) {
                statusView.setText("🟢 READY");
                statusView.setTextColor(Color.GREEN);
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
