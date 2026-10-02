package com.lokalized.example;

import android.content.Context;
import com.lokalized.LocalizedString;
import com.lokalized.LocalizedStringLoader;
import com.lokalized.Strings;
import com.lokalized.TranslationOptions;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Application adapter; the Lokalized JAR itself has no Android dependency. */
public final class AndroidStrings {
    private final AtomicReference<Locale> currentLocale = new AtomicReference<>(Locale.ENGLISH);
    private final Strings strings;

    public AndroidStrings(Context context) throws IOException {
        Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
        for (String tag : new String[] { "en", "fr", "ar" }) {
            String path = "strings/" + tag + ".json";
            try (InputStream stream = context.getAssets().open(path)) {
                localizedStringsByLocale.put(Locale.forLanguageTag(tag),
                        LocalizedStringLoader.parse(stream, Locale.forLanguageTag(tag), path));
            }
        }
        updateLocale(context);
        strings = Strings.withFallbackLocale(Locale.ENGLISH)
                .localizedStringSupplier(() -> localizedStringsByLocale)
                .localeSupplier(matcher -> matcher.bestMatchFor(currentLocale.get()))
                .build();
    }

    /** Refreshes the shared locale without retaining the UI context or reloading assets. */
    public void updateLocale(Context context) {
        currentLocale.set(context.getResources().getConfiguration().getLocales().get(0));
    }

    /** Uses the current app-wide locale. */
    public String get(String key, Map<String, Object> placeholders) {
        return strings.get(key, placeholders);
    }

    /** Overrides the locale for one lookup without changing the app-wide locale. */
    public String get(String key, Map<String, Object> placeholders, Locale locale) {
        return strings.get(key, placeholders, TranslationOptions.forLocale(locale));
    }

    /** Uses a particular UI context's locale for one lookup. */
    public String get(Context context, String key, Map<String, Object> placeholders) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return get(key, placeholders, locale);
    }

    Strings getStrings() {
        return strings;
    }
}
