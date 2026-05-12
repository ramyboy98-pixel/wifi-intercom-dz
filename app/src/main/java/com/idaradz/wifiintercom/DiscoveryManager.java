package com.idaradz.wifiintercom;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoveryManager {
    public interface Listener {
        void onPeerListChanged(Map<String, Peer> peers);
        void onLog(String message);
    }

    private final Context context;
    private final String myId;
    private final Listener listener;
    private final ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile String username;
    private volatile String channel;
    private volatile boolean talking;

    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private Thread senderThread;
    private Thread receiverThread;
    private Thread cleanupThread;
    private WifiManager.MulticastLock multicastLock;

    public DiscoveryManager(Context context, String myId, String username, String channel, Listener listener) {
        this.context = context.getApplicationContext();
        this.myId = myId;
        this.username = username;
        this.channel = channel;
        this.listener = listener;
    }

    public void updateState(String username, String channel, boolean talking) {
        this.username = username;
        this.channel = channel;
        this.talking = talking;
    }

    public Map<String, Peer> getPeers() {
        return peers;
    }

    public void start() {
        if (running) return;
        running = true;
        acquireMulticastLock();

        senderThread = new Thread(() -> {
            try {
                sendSocket = new DatagramSocket();
                sendSocket.setBroadcast(true);
                while (running) {
                    sendBeacon();
                    Thread.sleep(1200);
                }
            } catch (Exception e) {
                if (running) listener.onLog("Discovery sender stopped: " + e.getMessage());
            }
        }, "DiscoverySender");
        senderThread.start();

        receiverThread = new Thread(() -> {
            try {
                receiveSocket = new DatagramSocket(AudioConfig.DISCOVERY_PORT);
                receiveSocket.setReuseAddress(true);
                byte[] buffer = new byte[1024];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    receiveSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleBeacon(msg, packet.getAddress().getHostAddress());
                }
            } catch (Exception e) {
                if (running) listener.onLog("Discovery receiver stopped: " + e.getMessage());
            }
        }, "DiscoveryReceiver");
        receiverThread.start();

        cleanupThread = new Thread(() -> {
            while (running) {
                try {
                    long now = System.currentTimeMillis();
                    boolean changed = false;
                    for (String id : peers.keySet()) {
                        Peer p = peers.get(id);
                        if (p != null && now - p.lastSeen > 5000) {
                            peers.remove(id);
                            changed = true;
                        }
                    }
                    if (changed) listener.onPeerListChanged(peers);
                    Thread.sleep(1200);
                } catch (Exception ignored) {
                }
            }
        }, "DiscoveryCleanup");
        cleanupThread.start();
    }

    private void sendBeacon() {
        try {
            String msg = "DISC1|" + myId + "|" + clean(username) + "|" + clean(channel) + "|" + (talking ? "1" : "0");
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            InetAddress broadcast = getBroadcastAddress();
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcast, AudioConfig.DISCOVERY_PORT);
            sendSocket.send(packet);
        } catch (Exception e) {
            listener.onLog("Discovery send failed: " + e.getMessage());
        }
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replace("|", " ").trim();
    }

    private void handleBeacon(String msg, String ip) {
        try {
            if (!msg.startsWith("DISC1|")) return;
            String[] parts = msg.split("\\|", 5);
            if (parts.length < 5) return;
            String id = parts[1];
            if (id.equals(myId)) return;
            String name = parts[2];
            String ch = parts[3];
            boolean isTalking = "1".equals(parts[4]);

            Peer old = peers.get(id);
            if (old == null) {
                peers.put(id, new Peer(id, name, ch, ip, isTalking));
            } else {
                old.update(name, ch, ip, isTalking);
            }
            listener.onPeerListChanged(peers);
        } catch (Exception ignored) {
        }
    }

    private InetAddress getBroadcastAddress() throws Exception {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) return InetAddress.getByName("255.255.255.255");
        DhcpInfo dhcp = wifi.getDhcpInfo();
        if (dhcp == null) return InetAddress.getByName("255.255.255.255");

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++) {
            quads[k] = (byte) ((broadcast >> (k * 8)) & 0xFF);
        }
        return InetAddress.getByAddress(quads);
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("wifi_intercom_discovery_lock");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        running = false;
        try { if (sendSocket != null) sendSocket.close(); } catch (Exception ignored) {}
        try { if (receiveSocket != null) receiveSocket.close(); } catch (Exception ignored) {}
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Exception ignored) {}
        if (senderThread != null) senderThread.interrupt();
        if (receiverThread != null) receiverThread.interrupt();
        if (cleanupThread != null) cleanupThread.interrupt();
    }
}
