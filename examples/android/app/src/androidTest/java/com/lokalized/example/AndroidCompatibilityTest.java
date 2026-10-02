package com.lokalized.example;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(AndroidJUnit4.class)
public final class AndroidCompatibilityTest {
    private Context context(String tag) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(Locale.forLanguageTag(tag)));
        return context.createConfigurationContext(configuration);
    }

    @Test public void assetsExpressionsPluralsAndLocaleChanges() throws IOException {
        AndroidStrings strings = new AndroidStrings(context("en-US"));
        assertEquals("No books", strings.get("books", Collections.singletonMap("count", 0)));
        assertEquals("1 book", strings.get("books", Collections.singletonMap("count", 1)));
        assertEquals("3 books", strings.get("books", Collections.singletonMap("count", 3)));
        // The same Strings instance follows a new UI configuration without reloading files.
        strings.updateLocale(context("fr-CA"));
        assertEquals("1 livre", strings.get("books", Collections.singletonMap("count", 1)));
        assertEquals("3 livres", strings.get("books", Collections.singletonMap("count", 3)));
        assertEquals("English fallback", strings.get("englishOnly", Collections.emptyMap()));
        strings.updateLocale(context("de-DE"));
        assertEquals("1 book", strings.get("books", Collections.singletonMap("count", 1)));
    }

    @Test public void preciseDecimalsBidiAndLanguageRangeNegotiation() throws IOException {
        AndroidStrings strings = new AndroidStrings(context("en"));
        Map<String, String> results = CompatibilityProbe.results(strings);
        assertEquals("ONE", results.get("integer"));
        assertEquals("OTHER", results.get("decimal"));
        assertEquals("1.0 books", strings.get("books", Collections.singletonMap("count", new BigDecimal("1.0"))));
        assertEquals("fr", results.get("negotiated"));
        assertEquals("مرحبًا، \u2068Alice\u2069!", results.get("bidi"));
    }

    @Test public void explicitOverridesLeaveTheAppLocaleUnchanged() throws IOException {
        AndroidStrings strings = new AndroidStrings(context("fr-CA"));
        assertEquals("3 livres", strings.get("books", Collections.singletonMap("count", 3)));
        assertEquals("3 books", strings.get("books", Collections.singletonMap("count", 3), Locale.US));
        assertEquals("3 books", strings.get(context("de-DE"), "books", Collections.singletonMap("count", 3)));
        assertEquals("3 livres", strings.get("books", Collections.singletonMap("count", 3)));
    }

    @Test public void sharedLocaleUpdatesReachWorkerLookups() throws Exception {
        AndroidStrings strings = new AndroidStrings(context("en-US"));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            strings.updateLocale(context("fr-CA"));
            assertEquals("3 livres", worker.submit(() ->
                    strings.get("books", Collections.singletonMap("count", 3))).get());
            strings.updateLocale(context("en-US"));
            assertEquals("3 books", worker.submit(() ->
                    strings.get("books", Collections.singletonMap("count", 3))).get());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test public void applicationSharesTranslationsAcrossActivityRecreation() {
        ExampleApplication application = (ExampleApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        AndroidStrings shared = application.getStrings();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertSame(shared,
                    ((ExampleApplication) activity.getApplication()).getStrings()));
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertSame(shared, ((ExampleApplication) activity.getApplication()).getStrings());
                Locale uiLocale = activity.getResources().getConfiguration().getLocales().get(0);
                assertEquals(shared.get("books", Collections.singletonMap("count", 3), uiLocale),
                        shared.get("books", Collections.singletonMap("count", 3)));
            });
        }
    }
}
