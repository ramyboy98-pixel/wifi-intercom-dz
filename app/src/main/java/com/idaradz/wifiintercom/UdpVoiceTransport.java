package com.idaradz.wifiintercom;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class UdpVoiceTransport {
    public interface Listener {
        void onAudioFrame(String senderId, String channel, byte[] pcm);
        void onAlert(String senderId, String channel);
        void onLog(String message);
    }

    private static final String AUDIO_PREFIX = "AUD1";
    private static final String ALERT_PREFIX = "ALT1";

    private final String myId;
    private final Listener listener;
    private final AtomicInteger sequence = new AtomicInteger(0);

    private volatile boolean running = false;
    private DatagramSocket receiveSocket;
    private DatagramSocket sendSocket;
    private Thread receiveThread;

    public UdpVoiceTransport(String myId, Listener listener) {
        this.myId = myId;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;

        receiveThread = new Thread(() -> {
            try {
                receiveSocket = new DatagramSocket(AudioConfig.AUDIO_PORT);
                receiveSocket.setReuseAddress(true);
                byte[] buffer = new byte[4096];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    receiveSocket.receive(packet);
                    byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                    handlePacket(data);
                }
            } catch (Exception e) {
                if (running) listener.onLog("Voice receiver stopped: " + e.getMessage());
            }
        }, "UdpVoiceReceiver");
        receiveThread.start();

        try {
            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
        } catch (Exception e) {
            listener.onLog("Voice sender failed: " + e.getMessage());
        }
    }

    private void handlePacket(byte[] data) {
        try {
            int p1 = findPipe(data, 0);
            int p2 = findPipe(data, p1 + 1);
            int p3 = findPipe(data, p2 + 1);
            int p4 = findPipe(data, p3 + 1);
            int p5 = findPipe(data, p4 + 1);

            if (p1 < 0 || p2 < 0 || p3 < 0 || p4 < 0) return;

            String type = str(data, 0, p1);
            String channel = str(data, p1 + 1, p2);
            String senderId = str(data, p2 + 1, p3);
            String targetId = str(data, p3 + 1, p4);

            if (senderId.equals(myId)) return;
            if (!targetId.isEmpty() && !targetId.equals(myId)) return;

            if (AUDIO_PREFIX.equals(type)) {
                if (p5 < 0) return;
                byte[] pcm = Arrays.copyOfRange(data, p5 + 1, data.length);
                listener.onAudioFrame(senderId, channel, pcm);
            } else if (ALERT_PREFIX.equals(type)) {
                listener.onAlert(senderId, channel);
            }
        } catch (Exception ignored) {
        }
    }

    private int findPipe(byte[] data, int start) {
        for (int i = start; i < data.length; i++) {
            if (data[i] == '|') return i;
        }
        return -1;
    }

    private String str(byte[] data, int start, int end) {
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    public void sendAudio(String ip, String channel, String targetId, byte[] pcm) {
        sendPacket(AUDIO_PREFIX, ip, channel, targetId, pcm, true);
    }

    public void sendAlert(String ip, String channel, String targetId) {
        sendPacket(ALERT_PREFIX, ip, channel, targetId, new byte[0], false);
    }

    private void sendPacket(String type, String ip, String channel, String targetId, byte[] payload, boolean includeSequence) {
        if (sendSocket == null) return;
        try {
            String safeTarget = targetId == null ? "" : targetId;
            String header = type + "|" + channel + "|" + myId + "|" + safeTarget + "|";
            if (includeSequence) header += sequence.incrementAndGet() + "|";
            byte[] h = header.getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[h.length + payload.length];
            System.arraycopy(h, 0, out, 0, h.length);
            System.arraycopy(payload, 0, out, h.length, payload.length);

            DatagramPacket packet = new DatagramPacket(
                    out,
                    out.length,
                    InetAddress.getByName(ip),
                    AudioConfig.AUDIO_PORT
            );
            sendSocket.send(packet);
        } catch (Exception e) {
            listener.onLog("Send failed: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try { if (receiveSocket != null) receiveSocket.close(); } catch (Exception ignored) {}
        try { if (sendSocket != null) sendSocket.close(); } catch (Exception ignored) {}
        if (receiveThread != null) receiveThread.interrupt();
    }
}
