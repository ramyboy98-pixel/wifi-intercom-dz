package com.idaradz.wifiintercom;

import android.media.AudioFormat;

public final class AudioConfig {
    private AudioConfig() {}

    public static final int AUDIO_PORT = 55555;
    public static final int DISCOVERY_PORT = 55556;

    public static final int SAMPLE_RATE = 16000;
    public static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    public static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // 20ms of 16-bit mono PCM at 16kHz = 640 bytes.
    public static final int FRAME_BYTES = 640;

    // Small buffer smooths WiFi jitter without making delay annoying.
    public static final int JITTER_PREBUFFER_FRAMES = 4;
    public static final int JITTER_MAX_FRAMES = 12;
}
