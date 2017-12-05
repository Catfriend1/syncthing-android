package com.nutomic.syncthingandroid.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;

/**
 * Based on https://gitlab.com/fdroid/fdroidclient/blob/master/app/src/main/java/org/fdroid/fdroid/Languages.java
 */
public final class Languages {

    public static final String USE_SYSTEM_DEFAULT = "";

    private static final Locale DEFAULT_LOCALE;
    public static final String PREFERENCE_LANGUAGE = "pref_current_language";

    @Inject SharedPreferences mPreferences;
    private static Map<String, String> mAvailableLanguages;

    static {
        DEFAULT_LOCALE = Locale.getDefault();
    }

    public Languages(Context context) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        Map<String, String> tmpMap = new TreeMap<>();
        List<Locale> locales = Arrays.asList(LOCALES_TO_TEST);
        // Capitalize language names
        Collections.sort(locales, (l1, l2) -> l1.getDisplayLanguage().compareTo(l2.getDisplayLanguage()));
        for (Locale locale : locales) {
            String displayLanguage = locale.getDisplayLanguage(locale);
            displayLanguage = displayLanguage.substring(0, 1).toUpperCase(locale) + displayLanguage.substring(1);
            tmpMap.put(locale.getLanguage(), displayLanguage);
        }

        // remove the current system language from the menu
        tmpMap.remove(Locale.getDefault().getLanguage());

        /* SYSTEM_DEFAULT is a fake one for displaying in a chooser menu. */
        tmpMap.put(USE_SYSTEM_DEFAULT, context.getString(R.string.pref_language_default));
        mAvailableLanguages = Collections.unmodifiableMap(tmpMap);
    }

    /**
     * Handles setting the language if it is different than the current language,
     * or different than the current system-wide locale.  The preference is cleared
     * if the language matches the system-wide locale or "System Default" is chosen.
     */
    @TargetApi(17)
    public void setLanguage(Context context) {
        String language = mPreferences.getString(PREFERENCE_LANGUAGE, null);
        Locale locale;
        if (TextUtils.equals(language, DEFAULT_LOCALE.getLanguage())) {
            mPreferences.edit().remove(PREFERENCE_LANGUAGE).apply();
            locale = DEFAULT_LOCALE;
        } else if (language == null || language.equals(USE_SYSTEM_DEFAULT)) {
            mPreferences.edit().remove(PREFERENCE_LANGUAGE).apply();
            locale = DEFAULT_LOCALE;
        } else {
            /* handle locales with the country in it, i.e. zh_CN, zh_TW, etc */
            String[] localeSplit = language.split("_");
            if (localeSplit.length > 1) {
                locale = new Locale(localeSplit[0], localeSplit[1]);
            } else {
                locale = new Locale(language);
            }
        }
        Locale.setDefault(locale);

        final Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    /**
     * Force reload the {@link Activity to make language changes take effect.}
     *
     * @param activity the {@code Activity} to force reload
     */
    @SuppressLint("ApplySharedPref")
    public void forceChangeLanguage(Activity activity, String newLanguage) {
        mPreferences.edit().putString(PREFERENCE_LANGUAGE, newLanguage).commit();
        setLanguage(activity);
        Intent intent = activity.getIntent();
        if (intent == null) { // when launched as LAUNCHER
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.finish();
        activity.overridePendingTransition(0, 0);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    /**
     * @return an array of the names of all the supported languages, sorted to
     * match what is returned by {@link Languages#getSupportedLocales()}.
     */
    public String[] getAllNames() {
        return mAvailableLanguages.values().toArray(new String[mAvailableLanguages.size()]);
    }

    /**
     * @return sorted list of supported locales.
     */
    public String[] getSupportedLocales() {
        Set<String> keys = mAvailableLanguages.keySet();
        return keys.toArray(new String[keys.size()]);
    }

    private static final Locale[] LOCALES_TO_TEST = {
            Locale.ENGLISH,
            Locale.FRENCH,
            Locale.GERMAN,
            Locale.ITALIAN,
            Locale.JAPANESE,
            Locale.KOREAN,
            Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE,
            new Locale("af"),
            new Locale("ar"),
            new Locale("be"),
            new Locale("bg"),
            new Locale("ca"),
            new Locale("cs"),
            new Locale("da"),
            new Locale("el"),
            new Locale("es"),
            new Locale("eo"),
            new Locale("et"),
            new Locale("eu"),
            new Locale("fa"),
            new Locale("fi"),
            new Locale("he"),
            new Locale("hi"),
            new Locale("hu"),
            new Locale("hy"),
            new Locale("id"),
            new Locale("is"),
            new Locale("it"),
            new Locale("ml"),
            new Locale("my"),
            new Locale("nb"),
            new Locale("nl"),
            new Locale("pl"),
            new Locale("pt"),
            new Locale("ro"),
            new Locale("ru"),
            new Locale("sc"),
            new Locale("sk"),
            new Locale("sn"),
            new Locale("sr"),
            new Locale("sv"),
            new Locale("th"),
            new Locale("tr"),
            new Locale("uk"),
            new Locale("vi"),
    };

}
