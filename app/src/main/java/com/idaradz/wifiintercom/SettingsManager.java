package com.idaradz.wifiintercom;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static final String PREFS =
            "wifi_intercom_settings";

    private static final String USERNAME =
            "username";

    private static final String CHANNEL =
            "channel";

    private SharedPreferences prefs;

    public SettingsManager(Context context){

        prefs = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    public void setUsername(String username){

        prefs.edit()
                .putString(
                        USERNAME,
                        username
                )
                .apply();
    }

    public String getUsername(){

        return prefs.getString(
                USERNAME,
                android.os.Build.MODEL
        );
    }

    public void setChannel(String channel){

        prefs.edit()
                .putString(
                        CHANNEL,
                        channel
                )
                .apply();
    }

    public String getChannel(){

        return prefs.getString(
                CHANNEL,
                "GENERAL"
        );
    }
}
