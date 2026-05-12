package com.idaradz.wifiintercom;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioRecord;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private SettingsManager settings;
    private AudioRecord recorder;
    private boolean talking = false;

    private TextView status;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                1
        );

        settings = new SettingsManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 60, 30, 30);
        root.setBackgroundColor(Color.parseColor("#101418"));

        TextView title = new TextView(this);
        title.setText("📡 WiFi Intercom PRO");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);

        TextView user = new TextView(this);
        user.setText("👤 " + settings.getUsername());
        user.setTextSize(18);
        user.setTextColor(Color.LTGRAY);

        TextView channel = new TextView(this);
        channel.setText("📻 " + settings.getChannel());
        channel.setTextSize(18);
        channel.setTextColor(Color.parseColor("#42A5F5"));

        status = new TextView(this);
        status.setText("🟢 READY");
        status.setTextSize(22);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(Color.GREEN);
        status.setPadding(0, 40, 0, 40);

        Button ptt = new Button(this);
        ptt.setText("🎙 HOLD TO TALK");
        ptt.setTextSize(24);
        ptt.setAllCaps(false);
        ptt.setTextColor(Color.WHITE);
        ptt.setBackgroundColor(Color.parseColor("#D32F2F"));

        logView = new TextView(this);
        logView.setTextColor(Color.WHITE);
        logView.setTextSize(15);
        logView.setPadding(20, 30, 20, 20);
        logView.setText("SYSTEM READY\n");

        root.addView(title);
        root.addView(user);
        root.addView(channel);
        root.addView(status);
        root.addView(
                ptt,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        240
                )
        );
        root.addView(logView);

        setContentView(root);

        ptt.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    log("MIC PERMISSION NOT GRANTED");
                    return true;
                }

                recorder = AudioEngine.createRecorder();
                talking = true;

                status.setText("🔴 TALKING...");
                status.setTextColor(Color.RED);
                log("TX START");

                VoiceService.startTalking(recorder);

                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {

                talking = false;

                try {
                    if (recorder != null) {
                        recorder.stop();
                        recorder.release();
                        recorder = null;
                    }
                } catch (Exception ignored) {}

                status.setText("🟢 READY");
                status.setTextColor(Color.GREEN);
                log("TX STOP");

                return true;
            }

            return true;
        });
    }

    private void log(String text) {
        logView.append(text + "\n");
    }
}
