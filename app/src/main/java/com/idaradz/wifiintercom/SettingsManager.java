package com.idaradz.wifiintercom;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static final String PREFS = "wifi_intercom_settings";
    private static final String USERNAME = "username";
    private static final String CHANNEL = "channel";
    private static final String DARK = "dark";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setUsername(String username) {
        prefs.edit().putString(USERNAME, username).apply();
    }

    public String getUsername() {
        return prefs.getString(USERNAME, android.os.Build.MODEL);
    }

    public void setChannel(String channel) {
        prefs.edit().putString(CHANNEL, channel).apply();
    }

    public String getChannel() {
        return prefs.getString(CHANNEL, "GENERAL");
    }

    public void setDarkMode(boolean value) {
        prefs.edit().putBoolean(DARK, value).apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(DARK, true);
    }
}
