# Android example and compatibility checks

This application uses the same `com.lokalized:lokalized:3.1.2` JAR as desktop Java.
Android 8.0/API 26 is the minimum; `Locale.LanguageRange` and named regular-expression
groups require it. No additional runtime dependencies or core library desugaring
configuration are required.

The checked-in sample uses AGP 8.9.2, Gradle 8.11.1, JDK 17, and compile/target SDK 35.
These are the sample's tested toolchain settings; consumers can use other compatible
Android build tools. Install SDK platform 35 and Build Tools 35.0.0 to build this sample.

From the `lokalized-java` directory:

```sh
mvn -Dmaven.javadoc.skip=true verify
cd examples/android
./gradlew assembleDebug assembleRelease lintDebug
./gradlew connectedDebugAndroidTest
./gradlew -Pminified connectedReleaseAndroidTest
```

The wrapper downloads checksum-pinned Gradle 8.11.1. Use JDK 17 for the Android commands. Set `ANDROID_HOME` to your SDK
or create an ignored `local.properties` with `sdk.dir=/absolute/path/to/sdk`.
The example intentionally consumes `target/lokalized-3.1.2.jar` rather than downloaded
release artifacts, so compatibility checks exercise the current library build.
Its release variant uses debug signing solely for local/CI compatibility tests.

[`ExampleApplication`](app/src/main/java/com/lokalized/example/ExampleApplication.java)
creates one [`AndroidStrings`](app/src/main/java/com/lokalized/example/AndroidStrings.java)
adapter shared by all screens. The adapter loads `src/main/assets/strings/{en,fr,ar}.json`
once, closes each asset stream, and stores the current locale in an
[`AtomicReference`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html).
Its [`localeSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localeSupplier(java.util.function.Function))
reads that shared locale on every lookup, including calls from background threads.

[`MainActivity`](app/src/main/java/com/lokalized/example/MainActivity.java) gets the shared
adapter from the application and calls `updateLocale(this)` after its superclass's
[`onCreate(...)`](https://developer.android.com/reference/android/app/Activity#onCreate(android.os.Bundle))
and [`onConfigurationChanged(...)`](https://developer.android.com/reference/android/app/Activity#onConfigurationChanged(android.content.res.Configuration))
callbacks. Normal activity recreation therefore refreshes the locale while preserving
the loaded translations. Ordinary calls use `get(key, placeholders)` without passing
a context or locale. The adapter retains the locale value, so it does not retain an activity.

For fine-grained control, `get(key, placeholders, locale)` overrides one lookup;
`get(context, key, placeholders)` uses a particular UI context's locale for one lookup.
Both leave the shared app locale unchanged. Apps with several preferred locales can build a
language-range list from
[`Configuration.getLocales()`](https://developer.android.com/reference/android/content/res/Configuration#getLocales())
in preference order.

Use `Resources.openRawResource(...)` with the same parser for raw resources. Desktop
classpath package scanning and multi-release JAR discovery are not Android asset
discovery mechanisms. Load and validate large catalogs off the UI thread.

For placeholders, use `Collections.singletonMap(...)` or a regular map. The collection
factories in the desktop Java examples (`Map.of`, `List.of`, and `Set.of`) require
API 30 or the application's own core library desugaring; the Android sample uses APIs
available at API 26 throughout.

The device tests exercise asset parsing, expression alternatives, plurals, fallback,
locale negotiation, precise decimal operands, RTL isolation, updated UI locales, worker-thread
lookups, per-call overrides, and sharing the same adapter across activity recreation.
CI runs both debug and R8-minified instrumentation on API 26 and API 35 without
core library desugaring or additional runtime dependencies, and without
library-wide keep rules. Only sample application/adapter/probe entry points used by the separate
test APK are kept; the probe executes library calls inside the optimized app APK.
The JAR includes narrow consumer rules for its optional
compile-time annotations. Device validation is a release gate.
