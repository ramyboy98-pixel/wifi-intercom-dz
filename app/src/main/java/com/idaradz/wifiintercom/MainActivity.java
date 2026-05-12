package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private SettingsManager settings;

    private String username;

    private String currentChannel;

    private TextView logs;

    private TextView status;

    private TextView usernameView;

    private TextView channelView;

    private AudioRecord recorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.RECORD_AUDIO
                },
                1
        );

        settings =
                new SettingsManager(this);

        username =
                settings.getUsername();

        currentChannel =
                settings.getChannel();

        Intent serviceIntent =
                new Intent(
                        this,
                        VoiceService.class
                );

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){

            ContextCompat.startForegroundService(
                    this,
                    serviceIntent
            );

        }else{

            startService(serviceIntent);
        }

        recorder =
                AudioEngine.createRecorder();

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(25,45,25,25);

        root.setBackgroundColor(
                Color.parseColor("#101418")
        );

        TextView title =
                new TextView(this);

        title.setText(
                "📡 WiFi Intercom PRO"
        );

        title.setTextColor(Color.WHITE);

        title.setTextSize(30);

        usernameView =
                new TextView(this);

        usernameView.setText(
                "👤 " + username
        );

        usernameView.setTextColor(
                Color.LTGRAY
        );

        usernameView.setTextSize(16);

        channelView =
                new TextView(this);

        channelView.setText(
                "📻 " + currentChannel
        );

        channelView.setTextColor(
                Color.parseColor("#42A5F5")
        );

        channelView.setTextSize(18);

        channelView.setPadding(
                0,10,0,20
        );

        EditText usernameInput =
                new EditText(this);

        usernameInput.setHint(
                "Username"
        );

        usernameInput.setText(
                username
        );

        usernameInput.setTextColor(
                Color.WHITE
        );

        Button saveUser =
                buildButton(
                        "SAVE USERNAME",
                        "#1E88E5"
                );

        saveUser.setOnClickListener(v -> {

            username =
                    usernameInput
                            .getText()
                            .toString()
                            .trim();

            if(username.isEmpty()){

                return;
            }

            settings.setUsername(
                    username
            );

            usernameView.setText(
                    "👤 " + username
            );

            log(
                    "USERNAME UPDATED"
            );
        });

        LinearLayout channels =
                new LinearLayout(this);

        channels.setOrientation(
                LinearLayout.HORIZONTAL
        );

        channels.addView(
                buildChannelButton(
                        "GENERAL"
                )
        );

        channels.addView(
                buildChannelButton(
                        "KITCHEN"
                )
        );

        channels.addView(
                buildChannelButton(
                        "SECURITY"
                )
        );

        channels.addView(
                buildChannelButton(
                        "STORAGE"
                )
        );

        status =
                new TextView(this);

        status.setText(
                "🟢 READY"
        );

        status.setTextColor(
                Color.GREEN
        );

        status.setGravity(
                Gravity.CENTER
        );

        status.setTextSize(20);

        status.setPadding(
                0,20,0,20
        );

        Button ptt =
                buildButton(
                        "🎙 HOLD TO TALK",
                        "#D32F2F"
                );

        ptt.setTextSize(24);

        ptt.setOnTouchListener((v,event)->{

            if(event.getAction()
                    == MotionEvent.ACTION_DOWN){

                vibrate();

                status.setText(
                        "🔴 TALKING..."
                );

                log(
                        "VOICE TX START"
                );

                VoiceService.startTalking(
                        recorder
                );

                return true;
            }

            if(event.getAction()
                    == MotionEvent.ACTION_UP){

                recorder.stop();

                status.setText(
                        "🟢 READY"
                );

                log(
                        "VOICE TX STOP"
                );

                return true;
            }

            return true;
        });

        ScrollView scroll =
                new ScrollView(this);

        logs =
                new TextView(this);

        logs.setTextColor(
                Color.WHITE
        );

        logs.setTextSize(14);

        logs.setPadding(
                20,20,20,20
        );

        logs.setBackgroundColor(
                Color.parseColor("#1A222B")
        );

        scroll.addView(logs);

        root.addView(title);

        root.addView(usernameView);

        root.addView(channelView);

        root.addView(usernameInput);

        root.addView(saveUser);

        root.addView(channels);

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
                        600
                )
        );

        setContentView(root);

        log("SYSTEM READY");
    }

    private Button buildButton(
            String text,
            String color
    ){

        Button b =
                new Button(this);

        b.setText(text);

        b.setAllCaps(false);

        b.setTextColor(Color.WHITE);

        b.setBackgroundColor(
                Color.parseColor(color)
        );

        return b;
    }

    private Button buildChannelButton(
            String channel
    ){

        Button b =
                buildButton(
                        channel,
                        "#1565C0"
                );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        120,
                        1f
                );

        params.setMargins(
                8,0,8,0
        );

        b.setLayoutParams(params);

        b.setOnClickListener(v -> {

            currentChannel =
                    channel;

            settings.setChannel(
                    channel
            );

            channelView.setText(
                    "📻 " + currentChannel
            );

            log(
                    "CHANNEL "
                            + channel
            );
        });

        return b;
    }

    private void log(String text){

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
}
