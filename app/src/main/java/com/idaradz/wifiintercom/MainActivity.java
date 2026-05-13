package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {

    private SettingsManager settings;
    private String myId;
    private String username;
    private String currentChannel;
    private boolean darkMode;
    private boolean isTalking = false;
    private AudioManager audioManager;

    private String selectedPeerId = null;

    private AudioPlayer audioPlayer;
    private AudioCapture audioCapture;
    private UdpVoiceTransport voiceTransport;
    private DiscoveryManager discoveryManager;

    private TextView statusView;
    private TextView channelView;
    private TextView targetView;
    private TextView peerBox;
    private TextView logs;
    private EditText usernameInput;
    private LinearLayout root;
    private LinearLayout peersList;

    private final List<Peer> latestPeers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        }

        forceSpeakerOutput();

        settings = new SettingsManager(this);
        myId = settings.getDeviceId();
        if (myId == null || myId.trim().isEmpty()) {
            myId = UUID.randomUUID().toString();
            settings.setDeviceId(myId);
        }
        username = settings.getUsername();
        currentChannel = settings.getChannel();
        darkMode = settings.isDarkMode();

        buildUi();
        startCore();
        log("SYSTEM READY");
        log("PCM + UDP + JITTER BUFFER ACTIVE");
        log("ENCRYPTION TEMPORARILY OFF FOR STABILITY");
    }


    private void forceSpeakerOutput() {
        try {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(true);

                int maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int currentMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int wantedMusic = Math.max(currentMusic, Math.max(1, maxMusic / 2));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, wantedMusic, 0);

                int maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                int currentVoice = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                int wantedVoice = Math.max(currentVoice, Math.max(1, maxVoice / 2));
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, wantedVoice, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void startCore() {
        forceSpeakerOutput();

        audioPlayer = new AudioPlayer();
        audioPlayer.start();

        voiceTransport = new UdpVoiceTransport(myId, new UdpVoiceTransport.Listener() {
            @Override
            public void onAudioFrame(String senderId, String channel, byte[] pcm) {
                if (!currentChannel.equals(channel)) return;
                runOnUiThread(() -> setStatus("🔊 Receiving voice", false));
                audioPlayer.push(pcm);
            }

            @Override
            public void onAlert(String senderId, String channel) {
                if (!currentChannel.equals(channel)) return;
                runOnUiThread(() -> {
                    vibrate();
                    beep();
                    log("ALERT RECEIVED");
                    setStatus("📳 Alert received", false);
                });
            }

            @Override
            public void onLog(String message) {
                runOnUiThread(() -> log(message));
            }
        });
        voiceTransport.start();

        audioCapture = new AudioCapture(this, new AudioCapture.Callback() {
            @Override
            public void onPcmFrame(byte[] pcm) {
                sendVoiceFrame(pcm);
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> log("AUDIO ERROR: " + message));
            }
        });

        discoveryManager = new DiscoveryManager(this, myId, username, currentChannel, new DiscoveryManager.Listener() {
            @Override
            public void onPeerListChanged(Map<String, Peer> peers) {
                runOnUiThread(() -> updatePeers(peers));
            }

            @Override
            public void onLog(String message) {
                runOnUiThread(() -> log(message));
            }
        });
        discoveryManager.start();
    }

    private void sendVoiceFrame(byte[] pcm) {
        if (voiceTransport == null) return;
        Peer selected = getSelectedPeer();
        if (selected == null) {
            voiceTransport.sendAudio("255.255.255.255", currentChannel, "", pcm);
        } else {
            voiceTransport.sendAudio(selected.ip, currentChannel, selected.id, pcm);
        }
    }

    private Peer getSelectedPeer() {
        if (selectedPeerId == null) return null;
        for (Peer p : latestPeers) {
            if (p.id.equals(selectedPeerId)) return p;
        }
        selectedPeerId = null;
        return null;
    }

    private void sendAlert() {
        if (voiceTransport == null) return;
        Peer selected = getSelectedPeer();
        if (selected == null) {
            voiceTransport.sendAlert("255.255.255.255", currentChannel, "");
            log("ALERT SENT TO ALL");
        } else {
            voiceTransport.sendAlert(selected.ip, currentChannel, selected.id);
            log("ALERT SENT TO " + selected.name);
        }
        vibrate();
        beep();
    }

    private void startTalking() {
        if (isTalking) return;
        isTalking = true;
        setStatus("🎙️ Talking...", true);
        if (discoveryManager != null) discoveryManager.updateState(username, currentChannel, true);
        if (audioCapture != null) audioCapture.start();
    }

    private void stopTalking() {
        if (!isTalking) return;
        isTalking = false;
        if (audioCapture != null) audioCapture.stop();
        if (discoveryManager != null) discoveryManager.updateState(username, currentChannel, false);
        setStatus("✅ Ready", false);
    }

    private void buildUi() {
        int pageBg = darkMode ? Color.parseColor("#101418") : Color.parseColor("#F6F8FC");
        int cardBg = darkMode ? Color.parseColor("#1A222C") : Color.WHITE;
        int textColor = darkMode ? Color.WHITE : Color.BLACK;
        int subText = darkMode ? Color.parseColor("#C9D1D9") : Color.parseColor("#455A64");
        int blue = Color.parseColor("#1565C0");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(pageBg);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(pageBg);
        scroll.addView(root);

        TextView title = text("📡 WiFi Intercom DZ", 30, textColor, true);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        LinearLayout userCard = card(cardBg, 22);
        userCard.setPadding(dp(14), dp(14), dp(14), dp(14));

        usernameInput = new EditText(this);
        usernameInput.setSingleLine(true);
        usernameInput.setText(username);
        usernameInput.setTextSize(19);
        usernameInput.setTextColor(textColor);
        usernameInput.setHintTextColor(subText);
        usernameInput.setHint("اسم الجهاز");
        usernameInput.setPadding(dp(16), 0, dp(16), 0);
        usernameInput.setBackground(strokeBg(darkMode ? Color.parseColor("#111820") : Color.WHITE, 1, darkMode ? Color.parseColor("#344151") : Color.parseColor("#B0BEC5"), 16));
        userCard.addView(usernameInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        Button saveName = button("💾 حفظ الاسم", "#1565C0", 18, 18);
        saveName.setOnClickListener(v -> {
            String value = usernameInput.getText().toString().trim();
            if (!value.isEmpty()) {
                username = value;
                settings.setUsername(username);
                if (discoveryManager != null) discoveryManager.updateState(username, currentChannel, isTalking);
                log("USERNAME SAVED: " + username);
            }
        });
        userCard.addView(saveName, matchWrap(0, dp(10), 0, 0));
        root.addView(userCard, matchWrap(0, 0, 0, dp(14)));

        channelView = text("📻 Channel: " + currentChannel, 22, blue, true);
        root.addView(channelView, matchWrap(0, 0, 0, dp(8)));

        LinearLayout channels = new LinearLayout(this);
        channels.setOrientation(LinearLayout.HORIZONTAL);
        channels.setGravity(Gravity.CENTER);
        String[] chs = {"GENERAL", "KITCHEN", "SECURITY"};
        for (String ch : chs) {
            Button b = button(ch, currentChannel.equals(ch) ? "#0D47A1" : "#607D8B", 14, 14);
            b.setOnClickListener(v -> {
                currentChannel = ch;
                settings.setChannel(ch);
                channelView.setText("📻 Channel: " + currentChannel);
                if (discoveryManager != null) discoveryManager.updateState(username, currentChannel, isTalking);
                log("CHANNEL = " + currentChannel);
                rebuildOnlyUi();
            });
            channels.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(channels, matchWrap(0, 0, 0, dp(12)));

        targetView = text("🎯 Target: ALL DEVICES", 20, textColor, true);
        targetView.setPadding(0, dp(4), 0, dp(6));
        root.addView(targetView);

        peerBox = text("الأجهزة المتصلة ستظهر هنا", 17, subText, false);
        peerBox.setPadding(dp(14), dp(14), dp(14), dp(14));
        peerBox.setBackground(strokeBg(cardBg, 1, darkMode ? Color.parseColor("#334155") : Color.parseColor("#D6DDE6"), 18));
        peerBox.setOnClickListener(v -> {
            selectedPeerId = null;
            targetView.setText("🎯 Target: ALL DEVICES");
            renderPeerList();
        });
        root.addView(peerBox, matchWrap(0, 0, 0, dp(10)));

        peersList = new LinearLayout(this);
        peersList.setOrientation(LinearLayout.VERTICAL);
        root.addView(peersList, matchWrap(0, 0, 0, dp(12)));

        statusView = text("✅ Ready", 22, Color.parseColor("#2E7D32"), true);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackground(bg(cardBg, 18));
        root.addView(statusView, matchWrap(0, 0, 0, dp(14)));

        Button talkButton = button("🎙️ اضغط للتحدث", "#D32F2F", 25, 34);
        talkButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startTalking();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopTalking();
                return true;
            }
            return true;
        });
        root.addView(talkButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96)));

        Button alertBtn = button("📳 إرسال تنبيه", "#F57C00", 20, 24);
        alertBtn.setOnClickListener(v -> sendAlert());
        root.addView(alertBtn, matchWrap(0, dp(12), 0, 0));

        Button darkBtn = button(darkMode ? "☀️ الوضع الفاتح" : "🌙 الوضع الليلي", darkMode ? "#455A64" : "#263238", 18, 20);
        darkBtn.setOnClickListener(v -> {
            darkMode = !darkMode;
            settings.setDarkMode(darkMode);
            rebuildOnlyUi();
        });
        root.addView(darkBtn, matchWrap(0, dp(10), 0, 0));

        logs = text("", 13, subText, false);
        logs.setPadding(dp(12), dp(12), dp(12), dp(12));
        logs.setBackground(bg(cardBg, 14));
        root.addView(logs, matchWrap(0, dp(12), 0, 0));

        setContentView(scroll);
        renderPeerList();
    }

    private void rebuildOnlyUi() {
        buildUi();
        updateTargetText();
        renderPeerList();
    }

    private void updatePeers(Map<String, Peer> peers) {
        latestPeers.clear();
        latestPeers.addAll(peers.values());
        peerBox.setText(latestPeers.isEmpty() ? "لا توجد أجهزة الآن" : "عدد الأجهزة: " + latestPeers.size() + "  —  اضغط هنا للكل");
        renderPeerList();
    }

    private void renderPeerList() {
        if (peersList == null) return;
        peersList.removeAllViews();
        int cardBg = darkMode ? Color.parseColor("#1A222C") : Color.WHITE;
        int textColor = darkMode ? Color.WHITE : Color.BLACK;
        int border = darkMode ? Color.parseColor("#334155") : Color.parseColor("#D6DDE6");

        for (Peer p : latestPeers) {
            TextView row = text(p.display(), 16, textColor, false);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(strokeBg(cardBg, selectedPeerId != null && selectedPeerId.equals(p.id) ? 2 : 1, selectedPeerId != null && selectedPeerId.equals(p.id) ? Color.parseColor("#1565C0") : border, 14));
            row.setOnClickListener(v -> {
                selectedPeerId = p.id;
                updateTargetText();
                renderPeerList();
            });
            peersList.addView(row, matchWrap(0, 0, 0, dp(7)));
        }
    }

    private void updateTargetText() {
        Peer p = getSelectedPeer();
        if (targetView == null) return;
        if (p == null) targetView.setText("🎯 Target: ALL DEVICES");
        else targetView.setText("🎯 Target: " + p.name);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String s, String color, int sp, int radius) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(sp);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(bg(Color.parseColor(color), radius));
        return b;
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(bg(color, radius));
        return l;
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

    private LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String s, boolean danger) {
        if (statusView == null) return;
        statusView.setText(s);
        statusView.setTextColor(danger ? Color.parseColor("#D32F2F") : Color.parseColor("#2E7D32"));
    }

    private void log(String s) {
        if (logs == null) return;
        String old = logs.getText().toString();
        String line = "• " + s + "\n";
        logs.setText(line + old);
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(180);
        } catch (Exception ignored) {
        }
    }

    private void beep() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        stopTalking();
        if (audioPlayer != null) audioPlayer.stop();
        if (voiceTransport != null) voiceTransport.stop();
        try {
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Exception ignored) {}
        if (discoveryManager != null) discoveryManager.stop();
        super.onDestroy();
    }
}
