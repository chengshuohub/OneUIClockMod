package com.shoren.oneui.clockmod.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.shoren.oneui.clockmod.utils.PrefKeys;
import com.shoren.oneui.clockmod.utils.WeatherHelper;

/**
 * Receives weather updates from Samsung Weather Daemon or external sources,
 * stores them locally and relays them to SystemUI status bar clock.
 */
public class WeatherReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        WeatherHelper.WeatherInfo info = WeatherHelper.parseFromIntent(intent);
        if (info != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            WeatherHelper.saveToPreferences(prefs, info);

            // Forward to SystemUI
            Intent relay = new Intent(PrefKeys.ACTION_UPDATE_WEATHER);
            relay.setPackage("com.android.systemui");
            relay.putExtra("condition", info.condition);
            relay.putExtra("temp", info.temp);
            relay.putExtra("icon", info.icon);
            context.sendBroadcast(relay);
        }
    }
}
