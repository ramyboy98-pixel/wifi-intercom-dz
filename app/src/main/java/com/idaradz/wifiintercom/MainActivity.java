package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {

    private static final int AUDIO_PORT = 55555;

    private static final int DISCOVERY_PORT = 55556;

    private static final String BROADCAST_IP =
            "255.255.255.255";

    private static final int SAMPLE_RATE = 16000;

    private volatile boolean isTalking = false;

    private String currentChannel = "GENERAL";

    private final String deviceName =
            Build.MODEL;

    private final String deviceId =
            Build.MODEL + "_"
                    + System.currentTimeMillis();

    private TextView logs;

    private TextView status;

    private TextView onlineDevices;

    private TextView currentChannelView;

    private final ConcurrentHashMap<String,String>
            devices = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        requestPermissions(
                new String[]{
                        Manifest.permission.RECORD_AUDIO
                },
                10
        );

        enablePerformanceMode();

        createNotification();

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(25,45,25,25);

        root.setBackgroundColor(
                Color.parseColor("#EAF4FB")
        );

        TextView title = new TextView(this);

        title.setText(
                "📡 WiFi Intercom PRO"
        );

        title.setTextSize(30);

        title.setTextColor(Color.BLACK);

        currentChannelView =
                new TextView(this);

        currentChannelView.setText(
                "📻 " + currentChannel
        );

        currentChannelView.setTextSize(18);

        currentChannelView.setTextColor(
                Color.parseColor("#1565C0")
        );

        currentChannelView.setPadding(
                0,10,0,15
        );

        TextView device =
                new TextView(this);

        device.setText(
                "📱 " + deviceName
        );

        device.setTextSize(16);

        onlineDevices =
                new TextView(this);

        onlineDevices.setText(
                "🟢 WAITING FOR DEVICES..."
        );

        onlineDevices.setTextSize(15);

        onlineDevices.setPadding(
                0,15,0,15
        );

        LinearLayout channels =
                new LinearLayout(this);

        channels.setOrientation(
                LinearLayout.HORIZONTAL
        );

        Button g = buildChannelButton(
                "GENERAL"
        );

        Button k = buildChannelButton(
                "KITCHEN"
        );

        Button s = buildChannelButton(
                "SECURITY"
        );

        Button st = buildChannelButton(
                "STORAGE"
        );

        channels.addView(g);

        channels.addView(k);

        channels.addView(s);

        channels.addView(st);

        status = new TextView(this);

        status.setText(
                "🟢 READY"
        );

        status.setGravity(Gravity.CENTER);

        status.setTextSize(18);

        status.setPadding(
                0,20,0,20
        );

        Button ptt =
                buildActionButton(
                        "🎙 HOLD TO TALK",
                        "#1E88E5"
                );

        ptt.setTextSize(24);

        ScrollView scroll =
                new ScrollView(this);

        logs = new TextView(this);

        logs.setTextSize(14);

        logs.setPadding(
                20,20,20,20
        );

        logs.setBackgroundColor(
                Color.parseColor("#DDEAF5")
        );

        scroll.addView(logs);

        root.addView(title);

        root.addView(currentChannelView);

        root.addView(device);

        root.addView(channels);

        root.addView(onlineDevices);

        root.addView(status);

        root.addView(
                ptt,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        230
                )
        );

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        500
                )
        );

        setContentView(root);

        log("VOICE ENGINE READY");

        log("DISCOVERY SERVICE STARTED");

        log("CHANNEL = " + currentChannel);

        startDiscoveryReceiver();

        startDiscoverySender();

        startVoiceReceiver();

        ptt.setOnTouchListener((v,event)->{

            if(event.getAction()
                    == MotionEvent.ACTION_DOWN){

                vibrate();

                isTalking = true;

                status.setText(
                        "🔴 TRANSMITTING"
                );

                ptt.setBackgroundColor(
                        Color.parseColor("#D32F2F")
                );

                startVoiceSender();

                log(
                        "TX START "
                                + currentChannel
                );

                return true;
            }

            if(event.getAction()
                    == MotionEvent.ACTION_UP){

                isTalking = false;

                status.setText(
                        "🟢 READY"
                );

                ptt.setBackgroundColor(
                        Color.parseColor("#1E88E5")
                );

                log("TX STOP");

                return true;
            }

            return true;
        });
    }

    private Button buildChannelButton(
            String channel){

        Button b = new Button(this);

        b.setText(channel);

        b.setTextSize(13);

        b.setAllCaps(false);

        b.setTextColor(Color.WHITE);

        b.setBackgroundColor(
                Color.parseColor("#1565C0")
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        120,
                        1f
                );

        params.setMargins(8,0,8,0);

        b.setLayoutParams(params);

        b.setOnClickListener(v -> {

            currentChannel = channel;

            currentChannelView.setText(
                    "📻 " + currentChannel
            );

            vibrate();

            log(
                    "CHANNEL → "
                            + channel
            );
        });

        return b;
    }

    private Button buildActionButton(
            String text,
            String color){

        Button b = new Button(this);

        b.setText(text);

        b.setAllCaps(false);

        b.setTextColor(Color.WHITE);

        b.setTextSize(14);

        b.setBackgroundColor(
                Color.parseColor(color)
        );

        return b;
    }

    private void enablePerformanceMode(){

        WifiManager wifi =
                (WifiManager)
                        getApplicationContext()
                                .getSystemService(
                                        WIFI_SERVICE
                                );

        WifiManager.MulticastLock lock =
                wifi.createMulticastLock(
                        "wifiintercom"
                );

        lock.acquire();

        PowerManager pm =
                (PowerManager)
                        getSystemService(
                                POWER_SERVICE
                        );

        PowerManager.WakeLock wakeLock =
                pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "wifi:wakelock"
                );

        wakeLock.acquire();
    }

    private void createNotification(){

        String channelId =
                "wifi_intercom";

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

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,
                        channelId
                );

        builder.setContentTitle(
                "WiFi Intercom PRO"
        );

        builder.setContentText(
                "📡 ACTIVE"
        );

        builder.setSmallIcon(
                android.R.drawable.presence_audio_online
        );

        manager.notify(
                1,
                builder.build()
        );
    }

    private void startVoiceSender(){

        if(checkSelfPermission(
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED){

            return;
        }

        new Thread(() -> {

            int bufferSize =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            AudioRecord recorder =
                    new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                    );

            if(NoiseSuppressor.isAvailable()){

                NoiseSuppressor.create(
                        recorder.getAudioSessionId()
                );
            }

            if(AcousticEchoCanceler.isAvailable()){

                AcousticEchoCanceler.create(
                        recorder.getAudioSessionId()
                );
            }

            if(AutomaticGainControl.isAvailable()){

                AutomaticGainControl.create(
                        recorder.getAudioSessionId()
                );
            }

            try{

                DatagramSocket socket =
                        new DatagramSocket();

                socket.setBroadcast(true);

                InetAddress address =
                        InetAddress.getByName(
                                BROADCAST_IP
                        );

                byte[] buffer =
                        new byte[bufferSize];

                recorder.startRecording();

                while(isTalking){

                    int read =
                            recorder.read(
                                    buffer,
                                    0,
                                    buffer.length
                            );

                    if(read > 0){

                        String header =
                                currentChannel
                                        + "|"
                                        + deviceId
                                        + "|";

                        byte[] h =
                                header.getBytes();

                        byte[] finalData =
                                new byte[h.length + read];

                        System.arraycopy(
                                h,
                                0,
                                finalData,
                                0,
                                h.length
                        );

                        System.arraycopy(
                                buffer,
                                0,
                                finalData,
                                h.length,
                                read
                        );

                        DatagramPacket packet =
                                new DatagramPacket(
                                        finalData,
                                        finalData.length,
                                        address,
                                        AUDIO_PORT
                                );

                        socket.send(packet);
                    }
                }

                recorder.stop();

                recorder.release();

                socket.close();

            }catch (Exception e){

                log("TX ERROR");
            }

        }).start();
    }

    private void startVoiceReceiver(){

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
                                                        SAMPLE_RATE
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

                while(true){

                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length
                            );

                    socket.receive(packet);

                    String text =
                            new String(
                                    packet.getData(),
                                    0,
                                    packet.getLength()
                            );

                    int a = text.indexOf("|");

                    int b = text.indexOf(
                            "|",
                            a + 1
                    );

                    if(a > 0 && b > 0){

                        String channel =
                                text.substring(0,a);

                        String sender =
                                text.substring(
                                        a + 1,
                                        b
                                );

                        if(!sender.equals(deviceId)
                                && channel.equals(currentChannel)
                                && !isTalking){

                            int audioStart =
                                    b + 1;

                            int audioLength =
                                    packet.getLength()
                                            - audioStart;

                            player.write(
                                    packet.getData(),
                                    audioStart,
                                    audioLength
                            );
                        }
                    }
                }

            }catch (Exception e){

                log("RX ERROR");
            }

        }).start();
    }

    private void startDiscoverySender(){

        new Thread(() -> {

            try{

                DatagramSocket socket =
                        new DatagramSocket();

                socket.setBroadcast(true);

                InetAddress address =
                        InetAddress.getByName(
                                BROADCAST_IP
                        );

                while(true){

                    String message =
                            "DEVICE:"
                                    + deviceId
                                    + ":"
                                    + deviceName
                                    + ":"
                                    + currentChannel;

                    byte[] data =
                            message.getBytes();

                    DatagramPacket packet =
                            new DatagramPacket(
                                    data,
                                    data.length,
                                    address,
                                    DISCOVERY_PORT
                            );

                    socket.send(packet);

                    Thread.sleep(2000);
                }

            }catch (Exception e){

                log("DISCOVERY TX ERROR");
            }

        }).start();
    }

    private void startDiscoveryReceiver(){

        new Thread(() -> {

            try{

                DatagramSocket socket =
                        new DatagramSocket(
                                DISCOVERY_PORT
                        );

                byte[] buffer =
                        new byte[1024];

                while(true){

                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length
                            );

                    socket.receive(packet);

                    String msg =
                            new String(
                                    packet.getData(),
                                    0,
                                    packet.getLength()
                            );

                    if(msg.startsWith("DEVICE:")){

                        String[] p =
                                msg.split(":");

                        if(p.length >= 4){

                            String id = p[1];

                            String name = p[2];

                            String channel = p[3];

                            if(!id.equals(deviceId)){

                                devices.put(
                                        id,
                                        name
                                                + " ["
                                                + channel
                                                + "]"
                                );

                                updateOnlineDevices();
                            }
                        }
                    }
                }

            }catch (Exception e){

                log("DISCOVERY RX ERROR");
            }

        }).start();
    }

    private void updateOnlineDevices(){

        runOnUiThread(() -> {

            if(devices.isEmpty()){

                onlineDevices.setText(
                        "🟢 WAITING FOR DEVICES..."
                );

                return;
            }

            StringBuilder builder =
                    new StringBuilder();

            for(String d : devices.values()){

                builder.append("🟢 ")
                        .append(d)
                        .append("\n");
            }

            onlineDevices.setText(
                    builder.toString()
            );
        });
    }

    private void vibrate(){

        Vibrator vibrator =
                (Vibrator)
                        getSystemService(
                                VIBRATOR_SERVICE
                        );

        if(vibrator == null){

            return;
        }

        if(Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O){

            vibrator.vibrate(
                    VibrationEffect.createOneShot(
                            60,
                            VibrationEffect.DEFAULT_AMPLITUDE
                    )
            );

        }else{

            vibrator.vibrate(60);
        }
    }

    private void log(String text){

        runOnUiThread(() -> {

            String time =
                    new SimpleDateFormat(
                            "HH:mm:ss",
                            Locale.getDefault()
                    ).format(new Date());

            logs.append(
                    "[" + time + "] "
                            + text
                            + "\n"
            );
        });
    }
}
