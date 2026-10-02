package com.lokalized.example;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.TextView;
import java.util.Collections;

public final class MainActivity extends Activity {
    private AndroidStrings strings;
    private TextView text;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        strings = ((ExampleApplication) getApplication()).getStrings();
        text = new TextView(this);
        updateText();
        setContentView(text);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateText();
    }

    private void updateText() {
        // Called after UI configuration is applied, including activity recreation.
        strings.updateLocale(this);
        text.setText(strings.get("books", Collections.singletonMap("count", 3)));
    }
}
