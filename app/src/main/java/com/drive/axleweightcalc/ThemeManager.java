package com.drive.axleweightcalc;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_DARK_MODE = "is_dark_mode";

    public static void applyTheme(Context context) {
        if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    public static void toggleTheme(Context context) {
        boolean newMode = !isDarkMode(context);
        saveTheme(context, newMode);
        applyTheme(context);
    }

    public static boolean isDarkMode(Context context) {
        // Force Light Theme as requested
        return false; 
    }

    private static void saveTheme(Context context, boolean isDarkMode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_DARK_MODE, isDarkMode);
        editor.apply();
    }
}
