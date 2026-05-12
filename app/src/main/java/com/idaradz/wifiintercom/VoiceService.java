package com.idaradz.wifiintercom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioRecord;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VoiceService extends Service {

    public static final int AUDIO_PORT =
            55555;

    public static final String BROADCAST_IP =
            "255.255.255.255";

    private boolean running = true;

    @Override
    public void onCreate() {

        super.onCreate();

        startForegroundMode();

        startReceiver();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ){

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        running = false;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    private void startForegroundMode(){

        String channelId =
                "wifi_intercom_service";

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        if(Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O){

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            "WiFi Intercom",
                            NotificationManager.IMPORTANCE_LOW
                    );

            manager.createNotificationChannel(
                    channel
            );
        }

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        channelId
                )
                        .setContentTitle(
                                "WiFi Intercom PRO"
                        )
                        .setContentText(
                                "📡 الخدمة تعمل بالخلفية"
                        )
                        .setSmallIcon(
                                android.R.drawable.presence_audio_online
                        )
                        .build();

        startForeground(
                1001,
                notification
        );
    }

    private void startReceiver(){

        new Thread(() -> {

            try{

                int bufferSize = 8192;

                AudioTrack player =
                        new AudioTrack.Builder()
                                .setAudioAttributes(
                                        new AudioAttributes.Builder()
                                                .setUsage(
                                                        AudioAttributes.USAGE_MEDIA
                                                )
                                                .setContentType(
                                                        AudioAttributes.CONTENT_TYPE_SPEECH
                                                )
                                                .build()
                                )
                                .setAudioFormat(
                                        new AudioFormat.Builder()
                                                .setEncoding(
                                                        AudioFormat.ENCODING_PCM_16BIT
                                                )
                                                .setSampleRate(
                                                        AudioEngine.SAMPLE_RATE
                                                )
                                                .setChannelMask(
                                                        AudioFormat.CHANNEL_OUT_MONO
                                                )
                                                .build()
                                )
                                .setBufferSizeInBytes(
                                        bufferSize
                                )
                                .build();

                DatagramSocket socket =
                        new DatagramSocket(
                                AUDIO_PORT
                        );

                byte[] buffer =
                        new byte[bufferSize];

                player.play();

                while(running){

                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length
                            );

                    socket.receive(packet);

                    byte[] encrypted =
                            new byte[
                                    packet.getLength()
                            ];

                    System.arraycopy(
                            packet.getData(),
                            0,
                            encrypted,
                            0,
                            packet.getLength()
                    );

                    byte[] decrypted =
                            EncryptionManager.decrypt(
                                    encrypted
                            );

                    player.write(
                            decrypted,
                            0,
                            decrypted.length
                    );
                }

            }catch (Exception ignored){

            }

        }).start();
    }

    public static void sendVoice(
            byte[] audio
    ){

        new Thread(() -> {

            try{

                DatagramSocket socket =
                        new DatagramSocket();

                socket.setBroadcast(true);

                InetAddress address =
                        InetAddress.getByName(
                                BROADCAST_IP
                        );

                byte[] encrypted =
                        EncryptionManager.encrypt(
                                audio
                        );

                DatagramPacket packet =
                        new DatagramPacket(
                                encrypted,
                                encrypted.length,
                                address,
                                AUDIO_PORT
                        );

                socket.send(packet);

                socket.close();

            }catch (Exception ignored){

            }

        }).start();
    }

    public static void startTalking(
            AudioRecord recorder
    ){

        new Thread(() -> {

            try{

                int bufferSize =
                        AudioRecord.getMinBufferSize(
                                AudioEngine.SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT
                        );

                byte[] buffer =
                        new byte[bufferSize];

                recorder.startRecording();

                while(true){

                    int read =
                            recorder.read(
                                    buffer,
                                    0,
                                    buffer.length
                            );

                    if(read > 0){

                        byte[] finalAudio =
                                new byte[read];

                        System.arraycopy(
                                buffer,
                                0,
                                finalAudio,
                                0,
                                read
                        );

                        sendVoice(
                                finalAudio
                        );
                    }
                }

            }catch (Exception ignored){

            }

        }).start();
    }
}
