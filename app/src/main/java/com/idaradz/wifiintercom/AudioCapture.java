package com.idaradz.wifiintercom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class AudioCapture {
    public interface Callback {
        void onPcmFrame(byte[] pcm);
        void onError(String message);
    }

    private final Context context;
    private final Callback callback;
    private volatile boolean recording = false;
    private Thread thread;
    private AudioRecord recorder;

    public AudioCapture(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public void start() {
        if (recording) return;

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onError("Microphone permission missing");
            return;
        }

        int min = AudioRecord.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_IN,
                AudioConfig.FORMAT
        );
        int bufferSize = Math.max(min * 4, AudioConfig.FRAME_BYTES * 12);

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_IN,
                AudioConfig.FORMAT,
                bufferSize
        );

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            callback.onError("AudioRecord failed to initialize");
            return;
        }

        recording = true;
        recorder.startRecording();

        thread = new Thread(() -> {
            byte[] buffer = new byte[AudioConfig.FRAME_BYTES];
            while (recording) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    byte[] frame = new byte[read];
                    System.arraycopy(buffer, 0, frame, 0, read);
                    callback.onPcmFrame(frame);
                }
            }
        }, "AudioCaptureThread");
        thread.start();
    }

    public void stop() {
        recording = false;
        if (thread != null) thread.interrupt();
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        } catch (Exception ignored) {
        }
        recorder = null;
    }
}
