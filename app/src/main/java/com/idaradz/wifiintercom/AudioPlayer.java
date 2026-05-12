package com.idaradz.wifiintercom;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public class AudioPlayer {
    private final JitterBuffer jitterBuffer = new JitterBuffer(
            AudioConfig.JITTER_PREBUFFER_FRAMES,
            AudioConfig.JITTER_MAX_FRAMES
    );

    private volatile boolean running = false;
    private Thread thread;
    private AudioTrack track;

    public void start() {
        if (running) return;
        running = true;

        int min = AudioTrack.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_OUT,
                AudioConfig.FORMAT
        );
        int bufferSize = Math.max(min * 4, AudioConfig.FRAME_BYTES * 16);

        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioConfig.FORMAT)
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setChannelMask(AudioConfig.CHANNEL_OUT)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        track.play();

        thread = new Thread(() -> {
            while (running) {
                try {
                    byte[] pcm = jitterBuffer.takeForPlayback();
                    if (track != null) {
                        track.write(pcm, 0, pcm.length);
                    }
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "AudioPlayerThread");
        thread.start();
    }

    public void push(byte[] pcm) {
        jitterBuffer.push(pcm);
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        jitterBuffer.clear();
        try {
            if (track != null) {
                track.pause();
                track.flush();
                track.release();
            }
        } catch (Exception ignored) {
        }
        track = null;
    }
}
