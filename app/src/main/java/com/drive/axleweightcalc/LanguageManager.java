package com.drive.axleweightcalc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class LanguageManager {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LANGUAGE = "language_code";

    /**
     * Sets and saves the app locale using AndroidX AppCompat per-app locale API
     * as well as standard Context configuration.
     */
    public static void setLocale(Context context, String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) return;

        String current = getSavedLanguage(context);
        if (languageCode.equalsIgnoreCase(current)) {
            return;
        }

        // 1. Save to SharedPreferences
        saveLanguage(context, languageCode);

        // 2. Set default Java locale
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        // 3. AndroidX per-app language setting
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(appLocale);

        // 4. Update legacy resources configuration
        updateResourcesLegacy(context, locale);
    }

    /**
     * Applies locale and performs a zero-flicker instant activity restart.
     */
    public static void applyLocaleSmoothly(Activity activity, String languageCode) {
        if (activity == null || languageCode == null || languageCode.isEmpty()) return;

        String current = getSavedLanguage(activity);
        if (languageCode.equalsIgnoreCase(current)) {
            return;
        }

        // 1. Save to SharedPreferences
        saveLanguage(activity, languageCode);

        // 2. Set default Java locale
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        // 3. Update legacy resources configuration immediately
        updateResourcesLegacy(activity, locale);

        // 4. AndroidX per-app language setting
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(appLocale);

        // 5. Seamless instant restart without window tearing
        Intent intent = activity.getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.finish();
        activity.overridePendingTransition(0, 0);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    /**
     * Applies the saved locale on app startup.
     */
    public static void applySavedLocale(Context context) {
        String savedLang = getSavedLanguage(context);
        if (savedLang != null && !savedLang.isEmpty()) {
            Locale locale = new Locale(savedLang);
            Locale.setDefault(locale);
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(savedLang);
            if (!appLocale.equals(AppCompatDelegate.getApplicationLocales())) {
                AppCompatDelegate.setApplicationLocales(appLocale);
            }
            updateResourcesLegacy(context, locale);
        }
    }

    /**
     * Wraps context with the saved locale configuration for attachBaseContext().
     */
    public static Context wrapContext(Context context) {
        String savedLang = getSavedLanguage(context);
        if (savedLang == null || savedLang.isEmpty()) {
            return context;
        }

        Locale locale = new Locale(savedLang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.setLocale(locale);
        }

        return context.createConfigurationContext(config);
    }

    @SuppressWarnings("deprecation")
    private static void updateResourcesLegacy(Context context, Locale locale) {
        try {
            Resources resources = context.getResources();
            Configuration config = new Configuration(resources.getConfiguration());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(new LocaleList(locale));
            } else {
                config.setLocale(locale);
            }
            resources.updateConfiguration(config, resources.getDisplayMetrics());

            Context appContext = context.getApplicationContext();
            if (appContext != null && appContext != context) {
                Resources appResources = appContext.getResources();
                Configuration appConfig = new Configuration(appResources.getConfiguration());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appConfig.setLocales(new LocaleList(locale));
                } else {
                    appConfig.setLocale(locale);
                }
                appResources.updateConfiguration(appConfig, appResources.getDisplayMetrics());
            }
        } catch (Exception ignored) {
        }
    }

    private static void saveLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    public static String getSavedLanguage(Context context) {
        // Check SharedPreferences first
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_LANGUAGE, null);
        if (saved != null) {
            return saved;
        }

        // Check AppCompatDelegate locales
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            Locale first = locales.get(0);
            if (first != null) {
                return first.getLanguage();
            }
        }

        return null;
    }

    public static String getCurrentLanguage(Context context) {
        String saved = getSavedLanguage(context);
        if (saved != null) {
            return saved;
        }
        return Locale.getDefault().getLanguage();
    }

    public static boolean isLanguageSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(KEY_LANGUAGE)) return true;
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        return !locales.isEmpty();
    }
}
