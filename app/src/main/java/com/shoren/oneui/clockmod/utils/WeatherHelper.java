package com.shoren.oneui.clockmod.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper to process, cache, and format weather data for the status bar clock.
 * Supports integration with Samsung Weather daemon (com.sec.android.daemonapp)
 * as well as custom broadcast feeds.
 */
public class WeatherHelper {

    private static final Map<String, String> ICON_MAP = new HashMap<>();

    static {
        ICON_MAP.put("晴", "☀️");
        ICON_MAP.put("多云", "⛅");
        ICON_MAP.put("阴", "☁️");
        ICON_MAP.put("阵雨", "🌦️");
        ICON_MAP.put("雷阵雨", "⛈️");
        ICON_MAP.put("小雨", "🌧️");
        ICON_MAP.put("中雨", "🌧️");
        ICON_MAP.put("大雨", "🌧️");
        ICON_MAP.put("暴雨", "🌧️");
        ICON_MAP.put("小雪", "🌨️");
        ICON_MAP.put("中雪", "❄️");
        ICON_MAP.put("大雪", "❄️");
        ICON_MAP.put("雾", "🌫️");
        ICON_MAP.put("霾", "🌫️");
        ICON_MAP.put("风", "💨");
        ICON_MAP.put("Sunny", "☀️");
        ICON_MAP.put("Clear", "☀️");
        ICON_MAP.put("Partly Cloudy", "⛅");
        ICON_MAP.put("Cloudy", "☁️");
        ICON_MAP.put("Overcast", "☁️");
        ICON_MAP.put("Rain", "🌧️");
        ICON_MAP.put("Snow", "❄️");
        ICON_MAP.put("Thunderstorm", "⛈️");
    }

    public static class WeatherInfo {
        public String condition;
        public String temp;
        public String icon;
        public long updateTime;

        public WeatherInfo(String condition, String temp, String icon, long updateTime) {
            this.condition = condition != null ? condition : "晴";
            this.temp = temp != null ? temp : "26°C";
            this.icon = (icon != null && !icon.isEmpty()) ? icon : getEmojiForCondition(this.condition);
            this.updateTime = updateTime;
        }
    }

    public static String getEmojiForCondition(String condition) {
        if (condition == null) return "☀️";
        for (Map.Entry<String, String> entry : ICON_MAP.entrySet()) {
            if (condition.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "⛅";
    }

    public static String formatWeather(WeatherInfo info, String formatType) {
        if (info == null) return "";
        String cleanTemp = info.temp.replace("℃", "°C").replace("C", "°C").replace("°°", "°");
        if (!cleanTemp.contains("°")) {
            cleanTemp += "°C";
        }
        String shortTemp = cleanTemp.replace("C", "");

        if ("icon_temp".equals(formatType)) {
            return info.icon + " " + shortTemp;
        } else if ("temp_only".equals(formatType)) {
            return cleanTemp;
        } else if ("text_only".equals(formatType)) {
            return info.condition;
        } else { // "text_temp" or default
            return info.condition + " " + cleanTemp;
        }
    }

    public static WeatherInfo parseFromIntent(Intent intent) {
        if (intent == null) return null;
        String condition = intent.getStringExtra("condition");
        String temp = intent.getStringExtra("temp");
        String icon = intent.getStringExtra("icon");

        // Handle Samsung Weather Daemon specific extras if present
        if (condition == null && intent.hasExtra("CURRENT_WEATHER_TEXT")) {
            condition = intent.getStringExtra("CURRENT_WEATHER_TEXT");
        }
        if (temp == null && intent.hasExtra("CURRENT_TEMP")) {
            temp = intent.getStringExtra("CURRENT_TEMP");
        }

        if (condition == null && temp == null) {
            return null;
        }
        return new WeatherInfo(condition, temp, icon, System.currentTimeMillis());
    }

    public static void saveToPreferences(SharedPreferences prefs, WeatherInfo info) {
        if (prefs == null || info == null) return;
        prefs.edit()
            .putString(PrefKeys.KEY_CACHED_WEATHER_CONDITION, info.condition)
            .putString(PrefKeys.KEY_CACHED_WEATHER_TEMP, info.temp)
            .putString(PrefKeys.KEY_CACHED_WEATHER_ICON, info.icon)
            .apply();
    }

    public static WeatherInfo loadFromPreferences(SharedPreferences prefs) {
        if (prefs == null) return new WeatherInfo("晴", "26°C", "☀️", System.currentTimeMillis());
        String condition = prefs.getString(PrefKeys.KEY_CACHED_WEATHER_CONDITION, "晴");
        String temp = prefs.getString(PrefKeys.KEY_CACHED_WEATHER_TEMP, "26°C");
        String icon = prefs.getString(PrefKeys.KEY_CACHED_WEATHER_ICON, "☀️");
        return new WeatherInfo(condition, temp, icon, System.currentTimeMillis());
    }
}
