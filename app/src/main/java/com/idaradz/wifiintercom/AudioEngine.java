package com.idaradz.wifiintercom;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

public class AudioEngine {

    public static final int SAMPLE_RATE = 16000;
    public static final int AUDIO_CHUNK = 960;

    public static AudioRecord createRecorder() {

        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        int bufferSize = Math.max(minBuffer, AUDIO_CHUNK * 4);

        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(recorder.getAudioSessionId());
        }

        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(recorder.getAudioSessionId());
        }

        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(recorder.getAudioSessionId());
        }

        return recorder;
    }
}
