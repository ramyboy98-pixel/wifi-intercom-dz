package com.idaradz.wifiintercom;

public class DeviceState {

    public String id;
    public String name;
    public String channel;
    public String ip;
    public long lastSeen;
    public boolean talking;

    public DeviceState(String id, String name, String channel, String ip, boolean talking) {
        this.id = id;
        this.name = name;
        this.channel = channel;
        this.ip = ip;
        this.talking = talking;
        this.lastSeen = System.currentTimeMillis();
    }

    public String display() {
        String icon = talking ? "🔴 " : "🟢 ";
        return icon + name + " [" + channel + "] " + ip;
    }
}
