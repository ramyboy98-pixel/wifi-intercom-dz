package com.idaradz.wifiintercom;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#101418"));

        TextView title = new TextView(this);
        title.setText("📡\nWiFi Intercom PRO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(android.view.Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("Local Push-To-Talk System");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(16);
        sub.setGravity(android.view.Gravity.CENTER);

        root.addView(title);
        root.addView(sub);

        setContentView(root);

        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 900);
    }
}
