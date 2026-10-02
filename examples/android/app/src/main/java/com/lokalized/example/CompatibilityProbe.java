package com.lokalized.example;

import com.lokalized.BidiIsolation;
import com.lokalized.Cardinality;
import com.lokalized.TranslationOptions;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Runs inside the app APK so R8 can optimize every library call with its caller. */
public final class CompatibilityProbe {
    private CompatibilityProbe() {}

    public static Map<String, String> results(AndroidStrings adapter) {
        Map<String, String> results = new LinkedHashMap<>();
        results.put("integer", Cardinality.forNumber(1, Locale.ENGLISH).name());
        results.put("decimal", Cardinality.forNumber(new BigDecimal("1.0"), Locale.ENGLISH).name());
        results.put("negotiated", adapter.getStrings().bestMatchForAcceptLanguage("fr-CA, en;q=0.5").toLanguageTag());
        results.put("bidi", adapter.getStrings().get("hello", Collections.singletonMap("name", "Alice"),
                        TranslationOptions.builder().locale(Locale.forLanguageTag("ar"))
                                .bidiIsolation(BidiIsolation.RTL_LOCALES).build()));
        return Collections.unmodifiableMap(results);
    }
}
