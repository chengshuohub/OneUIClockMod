# Keep Xposed entry point and hook classes
-keep class com.shoren.oneui.clockmod.xposed.** { *; }
-keepclassmembers class com.shoren.oneui.clockmod.xposed.** { *; }

# Keep Xposed API interface classes
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Keep Preference and UI classes
-keep class com.shoren.oneui.clockmod.ui.** { *; }
-keep class com.shoren.oneui.clockmod.utils.** { *; }
-keep class com.shoren.oneui.clockmod.receiver.** { *; }

# Keep AndroidX Preference custom views
-keep public class androidx.preference.Preference { *; }
-keep public class androidx.preference.PreferenceFragmentCompat { *; }
-keep public class * extends androidx.preference.Preference { *; }
