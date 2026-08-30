package com.shoren.oneui.clockmod.utils;

public final class PrefKeys {
    public static final String PACKAGE_NAME = "com.shoren.oneui.clockmod";
    public static final String PREFS_NAME = "com.shoren.oneui.clockmod_preferences";

    // Broadcast Actions
    public static final String ACTION_PREF_CHANGED = "com.shoren.oneui.clockmod.ACTION_PREF_CHANGED";
    public static final String ACTION_UPDATE_WEATHER = "com.shoren.oneui.clockmod.ACTION_UPDATE_WEATHER";

    // Preference Keys
    public static final String KEY_MODULE_ENABLED = "pref_module_enabled";
    public static final String KEY_CLOCK_POSITION = "pref_clock_position";
    public static final String KEY_CLOCK_OFFSET_X = "pref_clock_offset_x";
    public static final String KEY_CLOCK_OFFSET_Y = "pref_clock_offset_y";

    public static final String KEY_FORMAT_MODE = "pref_format_mode";
    public static final String KEY_CUSTOM_PATTERN = "pref_custom_pattern";
    public static final String KEY_SHOW_SECONDS = "pref_show_seconds";
    public static final String KEY_DATE_PRESET = "pref_date_preset";
    public static final String KEY_WEEK_PRESET = "pref_week_preset";

    public static final String KEY_ENABLE_LUNAR = "pref_enable_lunar";
    public static final String KEY_LUNAR_SHOW_YEAR = "pref_lunar_show_year";
    public static final String KEY_LUNAR_SHOW_SOLAR_TERM = "pref_lunar_show_solar_term";

    public static final String KEY_ENABLE_WEATHER = "pref_enable_weather";
    public static final String KEY_WEATHER_FORMAT = "pref_weather_format";
    public static final String KEY_CACHED_WEATHER_CONDITION = "pref_cached_weather_condition";
    public static final String KEY_CACHED_WEATHER_TEMP = "pref_cached_weather_temp";
    public static final String KEY_CACHED_WEATHER_ICON = "pref_cached_weather_icon";

    public static final String KEY_FONT_SCALE = "pref_font_scale";
    public static final String KEY_ENABLE_CUSTOM_COLOR = "pref_enable_custom_color";
    public static final String KEY_CUSTOM_COLOR_HEX = "pref_custom_color_hex";
    public static final String KEY_MULTI_LINE = "pref_multi_line";

    // Position Enum Constants
    public static final int POSITION_LEFT = 0;
    public static final int POSITION_CENTER = 1;
    public static final int POSITION_RIGHT = 2;
    public static final int POSITION_HIDE = 3;

    // Defaults
    public static final boolean DEFAULT_MODULE_ENABLED = true;
    public static final int DEFAULT_CLOCK_POSITION = POSITION_LEFT;
    public static final int DEFAULT_CLOCK_OFFSET_X = 0;
    public static final int DEFAULT_CLOCK_OFFSET_Y = 0;
    public static final String DEFAULT_FORMAT_MODE = "preset";
    public static final String DEFAULT_CUSTOM_PATTERN = "{TIME} {WEEK} {LUNAR}";
    public static final boolean DEFAULT_SHOW_SECONDS = false;
    public static final String DEFAULT_DATE_PRESET = "M月d日";
    public static final String DEFAULT_WEEK_PRESET = "zh_short";
    public static final boolean DEFAULT_ENABLE_LUNAR = true;
    public static final boolean DEFAULT_LUNAR_SHOW_YEAR = false;
    public static final boolean DEFAULT_LUNAR_SHOW_SOLAR_TERM = true;
    public static final boolean DEFAULT_ENABLE_WEATHER = false;
    public static final String DEFAULT_WEATHER_FORMAT = "text_temp";
    public static final int DEFAULT_FONT_SCALE = 100;
    public static final boolean DEFAULT_ENABLE_CUSTOM_COLOR = false;
    public static final String DEFAULT_CUSTOM_COLOR_HEX = "#FFFFFFFF";
    public static final boolean DEFAULT_MULTI_LINE = false;

    private PrefKeys() {}
}
