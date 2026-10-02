package com.lokalized.example;

import android.app.Application;
import java.io.IOException;

/** Owns the translations and locale state shared by all activities. */
public final class ExampleApplication extends Application {
    private AndroidStrings strings;

    @Override public void onCreate() {
        super.onCreate();
        try {
            // These demonstration assets are small. Load large catalogs off the UI thread.
            strings = new AndroidStrings(this);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load translation assets", exception);
        }
    }

    public AndroidStrings getStrings() {
        return strings;
    }
}
