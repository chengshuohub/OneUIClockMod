package com.shoren.oneui.clockmod.utils;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Universal Clock & Status Bar Text Formatting Engine.
 * Responsible for parsing formats, calculating Lunar dates, attaching weekday strings,
 * and embedding live weather information.
 */
public class ClockFormatter {

    public static class FormatConfig {
        public boolean moduleEnabled = true;
        public String formatMode = "preset"; // "preset" or "custom"
        public String customPattern = "{TIME} {WEEK} {LUNAR}";
        public boolean showSeconds = false;
        public String datePreset = "M月d日";
        public String weekPreset = "zh_short";
        public boolean enableLunar = true;
        public boolean lunarShowYear = false;
        public boolean lunarShowSolarTerm = true;
        public boolean enableWeather = false;
        public String weatherFormat = "text_temp";
        public boolean multiLine = false;
        public WeatherHelper.WeatherInfo weatherInfo = null;
    }

    /**
     * Formats the status bar clock text for the given date using the provided config and context.
     */
    public static CharSequence format(Context context, Calendar calendar, FormatConfig config) {
        if (config == null || !config.moduleEnabled) {
            return getDefaultTime(context, calendar, false);
        }

        Date date = calendar.getTime();
        boolean is24Hour = context != null ? DateFormat.is24HourFormat(context) : true;

        if ("custom".equals(config.formatMode)) {
            return formatCustomPattern(date, is24Hour, config);
        } else {
            return formatPreset(context, calendar, is24Hour, config);
        }
    }

    private static String formatPreset(Context context, Calendar calendar, boolean is24Hour, FormatConfig config) {
        Date date = calendar.getTime();
        String timePart = getTimeString(date, is24Hour, config.showSeconds);

        StringBuilder extraPart = new StringBuilder();

        // 1. Date Preset
        if (config.datePreset != null && !"none".equals(config.datePreset)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(config.datePreset, Locale.getDefault());
                extraPart.append(sdf.format(date)).append(" ");
            } catch (Exception ignored) {}
        }

        // 2. Weekday
        String weekStr = getWeekString(calendar, config.weekPreset);
        if (!weekStr.isEmpty()) {
            extraPart.append(weekStr).append(" ");
        }

        // 3. Lunar Calendar
        if (config.enableLunar) {
            LunarCalendar.LunarResult lunar = LunarCalendar.fromCalendar(calendar);
            String lunarStr = lunar.getFullDisplay(config.lunarShowYear, config.lunarShowSolarTerm);
            if (!lunarStr.isEmpty()) {
                extraPart.append(lunarStr).append(" ");
            }
        }

        // 4. Weather
        if (config.enableWeather && config.weatherInfo != null) {
            String weatherStr = WeatherHelper.formatWeather(config.weatherInfo, config.weatherFormat);
            if (!weatherStr.isEmpty()) {
                extraPart.append(weatherStr).append(" ");
            }
        }

        String extra = extraPart.toString().trim();
        if (extra.isEmpty()) {
            return timePart;
        }

        if (config.multiLine) {
            return timePart + "\n" + extra;
        } else {
            return timePart + " " + extra;
        }
    }

    private static String formatCustomPattern(Date date, boolean is24Hour, FormatConfig config) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        String pattern = config.customPattern != null ? config.customPattern : "{TIME}";

        // Replace Time tags
        String timeNormal = getTimeString(date, is24Hour, false);
        String timeSec = getTimeString(date, is24Hour, true);
        pattern = pattern.replace("{TIME}", timeNormal);
        pattern = pattern.replace("{TIME_SEC}", timeSec);

        // Replace Date tag
        String dateStr = "";
        if (config.datePreset != null && !"none".equals(config.datePreset)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(config.datePreset, Locale.getDefault());
                dateStr = sdf.format(date);
            } catch (Exception ignored) {}
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("M月d日", Locale.getDefault());
            dateStr = sdf.format(date);
        }
        pattern = pattern.replace("{DATE}", dateStr);

        // Replace Week tag
        String weekStr = getWeekString(cal, config.weekPreset != null ? config.weekPreset : "zh_short");
        pattern = pattern.replace("{WEEK}", weekStr);

        // Replace Lunar tags
        LunarCalendar.LunarResult lunar = LunarCalendar.fromCalendar(cal);
        pattern = pattern.replace("{LUNAR}", lunar.getSimpleDisplay());
        pattern = pattern.replace("{LUNAR_YEAR}", lunar.ganZhiYear + "年(" + lunar.animal + ")");
        pattern = pattern.replace("{SOLAR_TERM}", lunar.solarTerm != null ? lunar.solarTerm : "");

        // Replace Weather tag
        String weatherStr = "";
        if (config.weatherInfo != null) {
            weatherStr = WeatherHelper.formatWeather(config.weatherInfo, config.weatherFormat);
        }
        pattern = pattern.replace("{WEATHER}", weatherStr);

        // Support escaped newlines
        pattern = pattern.replace("\\n", "\n");

        return pattern.trim();
    }

    public static String getTimeString(Date date, boolean is24Hour, boolean showSeconds) {
        String pattern;
        if (is24Hour) {
            pattern = showSeconds ? "HH:mm:ss" : "HH:mm";
        } else {
            pattern = showSeconds ? "hh:mm:ss a" : "hh:mm a";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(date);
    }

    public static String getDefaultTime(Context context, Calendar calendar, boolean showSeconds) {
        boolean is24Hour = context != null ? DateFormat.is24HourFormat(context) : true;
        return getTimeString(calendar.getTime(), is24Hour, showSeconds);
    }

    public static String getWeekString(Calendar cal, String weekPreset) {
        if ("none".equals(weekPreset)) return "";

        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1 = Sunday, 7 = Saturday
        String[] zhShort = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        String[] zhFull = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        String[] enShort = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        String[] enFull = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

        int index = dayOfWeek - 1;
        if (index < 0 || index > 6) index = 0;

        if ("zh_full".equals(weekPreset)) {
            return zhFull[index];
        } else if ("en_short".equals(weekPreset)) {
            return enShort[index];
        } else if ("en_full".equals(weekPreset)) {
            return enFull[index];
        } else { // "zh_short" or default
            return zhShort[index];
        }
    }
}
