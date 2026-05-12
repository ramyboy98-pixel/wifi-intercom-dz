package com.idaradz.wifiintercom;

public class Device {

    public String id;

    public String name;

    public String channel;

    public long lastSeen;

    public boolean talking;

    public Device(
            String id,
            String name,
            String channel
    ){

        this.id = id;

        this.name = name;

        this.channel = channel;

        this.lastSeen =
                System.currentTimeMillis();

        this.talking = false;
    }
}
