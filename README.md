<a href="https://lokalized.com">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://cdn.lokalized.com/lokalized-gh-logo-dark-v5.png">
        <img alt="Lokalized" src="https://cdn.lokalized.com/lokalized-gh-logo-light-v5.png" width="300" height="93">
    </picture>
</a>

Lokalized facilitates natural-sounding software translations on the JVM.

It is both a file format...

```json
{
  "I read {{bookCount}} books." : {
    "translation" : "I read {{bookCount}} {{books}}.",
    "placeholders" : {
      "books" : {
        "value" : "bookCount",
        "translations" : {
          "CARDINALITY_ONE" : "book",
          "CARDINALITY_OTHER" : "books"
        }
      }
    },
    "alternatives" : [
      {
        "bookCount == 0" : "I didn't read any books."
      }
    ]
  }  
}
```

...and a library that operates on it. 

```java
String message = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 0));
assertEquals("I didn't read any books.", message);
```

Lokalized has proudly powered production systems since 2017.

## Design Goals

* Complex translation rules can be expressed in a configuration file, not code
* First-class support for common language forms such as gender, grammatical case, definiteness, classifiers, register, and plural (cardinal, ordinal, range)
* Provide a simple expression language to handle traditionally difficult edge cases
* Support multiple platforms natively
* Immutability/thread-safety
* No dependencies

## Design Non-Goals

* Support for date/time, number, percentage, and currency formatting/parsing (JDK provides these)
* Support for collation (JDK provides this)
* Support for Java 8 and below; Lokalized is for Java 9+ only

## Roadmap

* Static analysis tool to autogenerate/sync localized strings files
* Additional Ports (JavaScript, Python, Android, Go, ...)
* Webapp for translators

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Maven Installation

```xml
<dependency>
  <groupId>com.lokalized</groupId>
  <artifactId>lokalized</artifactId>
  <version>3.0.0-SNAPSHOT</version>
</dependency>
```

## Direct Download

If you don't use Maven, you can drop [lokalized-3.0.0-SNAPSHOT.jar](https://repo1.maven.org/maven2/com/lokalized/lokalized/3.0.0-SNAPSHOT/lokalized-3.0.0-SNAPSHOT.jar) directly into your project.  No other dependencies are required.

Upgrading an existing application? See the [2.1-to-3.0 Java migration guide](https://lokalized.com/upgrading/2.1-to-3.0/).

## Why Lokalized?

* **As a developer**, it is unrealistic to embed per-locale translation rules in code for every text string
* **As a translator**, sufficient context and the power of an expression language are required to provide the best translations possible
* **As a manager**, it is preferable to have a single translation specification that works on the backend, web frontend, and native mobile apps

Perhaps most importantly, the Lokalized placeholder system and expression language allow you to support edge cases that are critical to natural-sounding translations - this can be difficult to achieve using traditional solutions. 

## Getting Started

We'll start with hands-on examples to illustrate key features.

### 1. Create Localized Strings Files

Filenames must conform to the IETF BCP 47 language tag format, optionally suffixed with `.json`.

Here is a Brazilian Portuguese (`pt-BR`) localized strings file which includes a single localization. The English source text remains the lookup key; the file supplies the Brazilian Portuguese rendering:

```json
{
  "I read {{bookCount}} books." : {
    "translation" : "Li {{bookCount}} {{books}}.",
    "placeholders" : {
      "books" : {
        "value" : "bookCount",
        "translations" : {
          "CARDINALITY_ONE" : "livro",
          "CARDINALITY_OTHER" : "livros"
        }
      }
    },
    "alternatives" : [
      {
        "bookCount == 0" : "Não li nenhum livro."
      }
    ]
  }  
}
```

### 2. Create a Strings Instance
   
```java
// Your fallback localized strings file, used in case no specific locale match is found.
final Locale FALLBACK_LOCALE = Locale.forLanguageTag("pt-BR");

// Creates a Strings instance which loads localized strings files from the given directory.
// Normally you'll only need a single shared instance to support your entire application,
// even for multitenant/concurrent usage, e.g. a Servlet container
Strings strings = Strings.withFallbackLocale(FALLBACK_LOCALE)
  // Looks in 'my-directory' for localized strings files
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  // Provides Lokalized with the appropriate locale to use for fetching translations
  .localeSupplier((matcher) -> {
    // "Smart" locale selection which queries the current web request for locale data.
    // MyWebContext is a class you might write yourself, perhaps using a ThreadLocal internally		
    Locale locale = MyWebContext.getHttpServletRequest().getLocale();
    // Lokalized gives you a matcher, which knows the most appropriate localized strings file to use.
    // The matcher also supports language range sets, e.g. `Accept-Language` HTTP request header
    return matcher.bestMatchFor(locale);
  })
  .build();
```

By default, failed lookups return the key with supplied placeholders interpolated into it. To throw instead:

```java
// Fail-fast
Strings strings = Strings.withFallbackLocale(FALLBACK_LOCALE)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  .localeSupplier((matcher) -> matcher.bestMatchFor(FALLBACK_LOCALE))
  .translationFailureHandler(TranslationFailureHandler.throwException())
  .build();
```

To keep the default fail-soft result while observing structured failures, attach a consumer to `returnKey(...)`:

```java
// Custom telemetry for failures
Strings strings = Strings.withFallbackLocale(FALLBACK_LOCALE)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  .localeSupplier((matcher) -> matcher.bestMatchFor(FALLBACK_LOCALE))
  .translationFailureHandler(TranslationFailureHandler.returnKey(failure ->
    exampleMetrics.increment("lokalized.translation." + failure.getReason())))
  .build();
```

Lokalized [`Strings`](https://javadoc.lokalized.com/com/lokalized/Strings.html) instances are immutable and safe to share. If your application needs to reload localized strings files, rebuild a new instance and atomically swap the shared [`AtomicReference`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html):

```java
// Threadsafe reloading in your application via atomic swaps
final class LocalizedStrings {
  private static final Locale FALLBACK_LOCALE = Locale.forLanguageTag("pt-BR");
  private final AtomicReference<Strings> strings = new AtomicReference<>(load());

  public String get(String key, Map<String, Object> placeholders) {
    return strings.get().get(key, placeholders);
  }

  public void reload() {
    strings.set(load());
  }

  private static Strings load() {
    return Strings.withFallbackLocale(FALLBACK_LOCALE)
      .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
      .localeSupplier((matcher) -> matcher.bestMatchFor(MyWebContext.getHttpServletRequest().getLocale()))
      .build();
  }
}
```

### 3. Ask Strings Instance For Translations

```java
// Lokalized knows how to map numbers to plural cardinalities per locale.
// That is, it understands that 3 means CARDINALITY_OTHER ("livros") in Brazilian Portuguese
String message = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 3));
assertEquals("Li 3 livros.", message);

// 1 means CARDINALITY_ONE ("livro") in Brazilian Portuguese
message = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 1));
assertEquals("Li 1 livro.", message);

// A special alternative rule is applied when bookCount == 0
message = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 0));
assertEquals("Não li nenhum livro.", message);
```

#### Formatting Placeholder Values

Lokalized selects translations and interpolates placeholder values, but it does not format dates, times, numbers, percentages, or currency values. Use JDK formatters such as [`NumberFormat`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/text/NumberFormat.html) and [`DateTimeFormatter`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/time/format/DateTimeFormatter.html) for display values before passing them to Lokalized:

```java
// Let the JDK do the formatting lift
Locale locale = Locale.forLanguageTag("fr-FR");
NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);

String message = strings.get("Your balance is {{balance}} and is due on {{dueDate}}.", Map.of(
  "balance", currencyFormat.format(new BigDecimal("1234.56")),
  "dueDate", dateFormat.format(LocalDate.of(2026, 7, 7))
));
```

When a value affects translation selection and also needs locale-aware display formatting, pass separate placeholders: a raw value for Lokalized's language-form rules and a formatted value for interpolation.

```json
{
  "You have {{formattedCount}} items." : {
    "translation" : "You have {{formattedCount}} {{items}}.",
    "placeholders" : {
      "items" : {
        "value" : "count",
        "translations" : {
          "CARDINALITY_ONE" : "item",
          "CARDINALITY_OTHER" : "items"
        }
      }
    }
  }
}
```

```java
// Provide both "raw" and "formatted" values to support placeholder logic
int count = 12_345;
Locale locale = Locale.forLanguageTag("en-US");

String message = strings.get("You have {{formattedCount}} items.", Map.of(
  "count", count,
  "formattedCount", NumberFormat.getIntegerInstance(locale).format(count)
));
```

#### 4. Ensure Determinism via Tiebreakers

Suppose you have two localized strings files for Portuguese - Brazilian (`pt-BR`) and European (`pt-PT`).

A user who prefers only Angolan Portuguese (`pt-AO`) as defined by their `Accept-Language` HTTP request header then accesses your webapp.

Lokalized needs to know how to consistently "break the tie" to provide the Angolan user with a `pt` translation.

To that end, Lokalized will require that you specify `tiebreakerLocalesByLanguageCode` if it detects that you have more than one localized strings file per ISO 639 language code.

```java
Strings strings = Strings.withFallbackLocale(FALLBACK_LOCALE)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  .localeSupplier((matcher) -> {
    Locale locale = MyWebContext.getHttpServletRequest().getLocale();
    return matcher.bestMatchFor(locale);
  })
  // Declare your tiebreakers where ambiguity exists.
  // Lokalized will automatically detect ambiguities and require you to resolve them here -
  // an exception will be thrown with detailed instructions to that effect.
  // Here, we express that if there's a language preference for Portuguese but no exact locale match,
  // we should provide the user with a Brazilian Portuguese translation  
  .tiebreakerLocalesByLanguageCode(Map.of(
    "pt", List.of(Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("pt-PT"))
  ))
  .build();
```

#### 5. Respect User Language Preferences

Here's a common scenario: a user visits your webapp, and their browser automatically populates the `Accept-Language` HTTP request header with
an [RFC 3282](https://datatracker.ietf.org/doc/html/rfc3282) ordered set of language range values like `en-GB;q=1.0,en;q=0.75,fr-FR;q=0.25`.

That one says: "I prefer British English, then other forms of English, then French (from France) - in that order."

Lokalized offers "best match" functionality which evaluates the combination of your available localized strings files and
a set of language range values to pick the most appropriate localization that your application supports for that user. 

```java
Strings strings = Strings.withFallbackLocale(FALLBACK_LOCALE)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  // Drive locale selection via List<LanguageRange> parsed from Accept-Language header
  .localeSupplier((matcher) -> {
    HttpServletRequest request = MyWebContext.getHttpServletRequest();
    String acceptLanguage = request.getHeader("Accept-Language");
    List<LanguageRange> languageRanges = LanguageRange.parse(acceptLanguage);
    return matcher.bestMatchFor(languageRanges);
  })
  .build();
```

### Locale Matching Behavior

[`bestMatchFor(Locale)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchFor(java.util.Locale)) and [`bestMatchFor(List<LanguageRange>)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchFor(java.util.List)) match requested locale preferences against the locales loaded from your localized strings files. Matching is deterministic and follows these broad rules:

* An exact locale tag from a localized strings file wins before a CLDR-canonical-equivalent tag; deprecated and legacy aliases are then considered
* CLDR parent locales are considered before looser language-only matches. For example, `en-AU` can prefer a configured `en-001` file before `en`
* Matching is script-aware when CLDR likely-subtag data can infer a script. For example, `zh-TW` can match `zh-Hant`, and `sr-Latn` is distinct from `sr-Cyrl`
* The Norwegian macrolanguage tag `no` and Norwegian Bokmål tag `nb` bridge to each other as a compatibility fallback; exact files still win first
* If multiple supported files share the same language and no exact, parent, or script-aware match resolves the request, `tiebreakerLocalesByLanguageCode` controls which locale wins
* Language-range quality weights are honored, and a `q=0` range excludes that locale and its matching descendants when another acceptable loaded locale remains
* `Locale.ROOT`, `und`, wildcard-only ranges, empty preference lists, and unmatched requests resolve to the configured fallback locale

[`LocaleMatcher`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html) accepts at most
[`32` parsed language ranges](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#MAXIMUM_LANGUAGE_RANGES)
per call to bound matching work. This count applies to the list returned by
[`LanguageRange.parse(...)`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Locale.LanguageRange.html#parse(java.lang.String)),
which may add IANA-equivalent ranges beyond those written in the header.
[`TranslationOptions`](https://javadoc.lokalized.com/com/lokalized/TranslationOptions.html) enforces the same limit when
the options are constructed, before a lookup begins. Applications accepting untrusted `Accept-Language` values should
also limit the raw HTTP header size before parsing; Lokalized receives the parsed list and cannot bound the parser's
work. `bestMatchFor(...)` always returns a locale and uses the configured fallback when nothing is acceptable.

## Loading Localized Strings

Most applications load localized strings files from the filesystem during development and from the classpath in packaged deployments using [`LocalizedStringLoader`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html).

```java
Strings filesystemStrings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("strings")))
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .build();

Strings classpathStrings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .build();
```

Classpath package names use slash-separated resource paths such as `com/example/myapp/strings`. Prefer an
application-specific, namespaced path over a generic top-level package like `strings` so unrelated dependencies cannot
publish resources into the same package. [`loadFromClasspath(String)`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html#loadFromClasspath(java.lang.String)) first uses the current thread context classloader when one is available, then falls back to Lokalized's own classloader. Use [`loadFromClasspath(ClassLoader, String)`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html#loadFromClasspath(java.lang.ClassLoader,java.lang.String)) for containers, plugin systems, test harnesses, and other environments where the desired resources are visible through a specific classloader.

Filesystem and classpath loading both scan only the specified directory or package; child directories and child packages are not scanned recursively.
Classpath package names must be nonempty slash-relative paths and may not contain empty interior, `.` or `..` segments.
One or more trailing slashes are ignored; leading slashes and traversal remain invalid.
The valid BCP 47 tag `und` represents Java's `Locale.ROOT`, so a root localized strings file is named `und.json`; tags such as
`und-Latn.json` are also supported.

Loading is bounded per resource and per load by [`LocalizedStringLoadingOptions`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoadingOptions.html).
The default limit is 8 MiB for a `Path` or `InputStream`, 8,388,608 UTF-16 code units for a `Reader`, and 64 levels of
JSON object/array nesting. A filesystem directory, discovered classpath package, or explicit classpath-resource mapping
is additionally limited to 32 MiB total input and 256 localized strings files. All loading operations, including single-resource
`parse(...)` methods, accept at most 100,000 translation nodes and 1,000 warnings by default. The node count includes
top-level messages, whole-message alternatives, every placeholder definition, and every expression-selected fragment
alternative, so conditional structures cannot bypass the work budget. Overloads
accepting loading options may lower or raise these defaults; the loader's hard maximum nesting depth is 128. The total
input-byte limit also applies to single-resource `Path` and `InputStream` parsing, while it cannot apply to a `Reader`
because the original byte representation is unavailable. A single-resource parse consumes one localized strings file
from its file-count budget. Discovery-based filesystem and classpath loads examine at most 100,000 entries by default;
this limit is configurable up to 1,000,000. Explicit locale-to-resource maps and single-resource parsing enumerate no
candidates and do not consume the discovery budget. Input streams are decoded as strict UTF-8, and blank or BOM-only
localized strings files are rejected - use `{}` for an intentionally empty localized strings file.

Classpath loading normally uses the classloader's package-resource discovery and does not sweep every classpath root.
Some JAR creation tools omit directory entries, which makes their packages invisible to ordinary discovery. Enable
[`exhaustiveClasspathSearch(true)`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoadingOptions.Builder.html#exhaustiveClasspathSearch(java.lang.Boolean))
only when you need to support such a JAR; this inspects every filesystem and JAR root
visible to the classloader, including filesystem JARs referenced through manifest `Class-Path` entries. Localized strings
files in multi-release JARs use the entry selected for the running Java version. A `.json` resource in a classpath package whose filename is not a valid locale tag is ignored
with a warning so an unrelated dependency cannot abort application startup. Filesystem loading remains strict and rejects
the same filename, which catches mistakes in a localized strings directory owned by the application.

```java
LocalizedStringLoadingOptions limits = LocalizedStringLoadingOptions.builder()
  .maximumInputBytes(4 * 1024 * 1024)
  .maximumReaderCharacters(4 * 1024 * 1024)
  .maximumTotalInputBytes(16L * 1024L * 1024L)
  .maximumLocalizedStringsFiles(100)
  .maximumTranslationNodes(25_000)
  .maximumWarnings(500)
  .maximumJsonNestingDepth(32)
  .exhaustiveClasspathSearch(true) // Only for JARs that omit package directory entries
  .build();

Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
  LocalizedStringLoader.loadFromClasspath("strings", limits);
```

Some container and plugin classloaders can open known resources but cannot enumerate a package or expose a standard
`file:`/`jar:` package URL. Use
[`loadFromClasspathResources(...)`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html#loadFromClasspathResources(java.lang.ClassLoader,java.util.Map,com.lokalized.LocalizedStringWarningHandler,com.lokalized.LocalizedStringLoadingOptions))
to map locales to exact resource paths in those environments; this path uses
`ClassLoader.getResourceAsStream(...)` and performs no package discovery:

```java
Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspathResources(
  pluginClassLoader,
  Map.of(
    Locale.ROOT, "myapp/strings/und.json",
    Locale.ENGLISH, "myapp/strings/en.json",
    Locale.FRENCH, "myapp/strings/fr.json"
  ),
  limits
);
```

## Per-Invocation Options

The [`localeSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localeSupplier(java.util.function.Function))
configured on [`Strings.Builder`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html) is convenient for web
requests and other request-scoped contexts. For async jobs, batch work, tests, administrative tooling, or alternate
output sinks, use [`TranslationOptions`](https://javadoc.lokalized.com/com/lokalized/TranslationOptions.html) to override
lookup behavior for a single invocation:

```java
String message = strings.get(
  "I read {{bookCount}} books.",
  Map.of("bookCount", 1),
  TranslationOptions.forLocale(Locale.forLanguageTag("fr-CA"))
);
```

Per-invocation options can supply a locale, language ranges, bidi isolation behavior,
[`TranslationFallbackPolicy`](https://javadoc.lokalized.com/com/lokalized/TranslationFallbackPolicy.html), or
[`TranslationFailureHandler`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html). Locale and
language-range options bypass the configured
[`localeSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localeSupplier(java.util.function.Function))
or [`localeMatchSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localeMatchSupplier(java.util.function.Function))
for that call. Lokalized still applies the same matching, tiebreakers, and fallback behavior
using the preference you supplied.

## Runtime Safety Limits

Localized strings compilation and translation evaluation use immutable [`TranslationRuntimeLimits`](https://javadoc.lokalized.com/com/lokalized/TranslationRuntimeLimits.html).
The defaults cap numbers and numeric literals at 1,024 digits of precision and absolute scale, explicitly visible
decimal places at 1,024, compact exponents at 64, expressions at 2,048 characters / 256 tokens / 32 nested groups,
generated placeholders at 32 levels, one interpolated result or phonetic input at 262,144 UTF-16 code units, and
cumulative generated-fragment expansion at 1,048,576 UTF-16 code units per locale fallback attempt. Locale fallback starts a
fresh generated-expansion budget for each candidate. Applications may lower or raise the defaults with
[`TranslationRuntimeLimits.builder()`](https://javadoc.lokalized.com/com/lokalized/TranslationRuntimeLimits.html#builder())
and [`TranslationRuntimeLimits.Builder`](https://javadoc.lokalized.com/com/lokalized/TranslationRuntimeLimits.Builder.html).
Hard ceilings remain 4,096 for numeric precision, absolute scale, visible decimal places, and compact exponents;
4,096 characters / 512 tokens / 64 nested groups for expressions; 64 levels for generated placeholders; 1,048,576
UTF-16 code units for one interpolated result or phonetic input; and 8,388,608 UTF-16 code units for cumulative
generated expansion.

```java
TranslationRuntimeLimits runtimeLimits = TranslationRuntimeLimits.builder()
  .maximumExpressionCharacters(1_024)
  .maximumExpressionTokens(128)
  .maximumInterpolatedOutputCharacters(128 * 1_024)
  .maximumGeneratedExpansionCharacters(512 * 1_024)
  .build();

Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("com/example/myapp/strings"))
  .localeSupplier(matcher -> Locale.ENGLISH)
  .runtimeLimits(runtimeLimits)
  .build();
```

Apply the limits to a provider with
[`Strings.Builder.runtimeLimits(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#runtimeLimits(com.lokalized.TranslationRuntimeLimits)).
It can also be supplied directly through
[`PluralOperands.Builder.runtimeLimits(...)`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.Builder.html#runtimeLimits(com.lokalized.TranslationRuntimeLimits)).
The loader validates expressions and numeric literals against hard ceilings because it does not yet know which runtime
policy an application will select. `Strings` construction then compiles the loaded localized strings and enforces its
configured limits, so a localized strings file may load successfully but be rejected by `Strings` under the defaults.
This preserves an explicit opt-up
path without allowing work above the hard ceilings. Violations caused by lookup values or generated expansion are
resolution failures.

## A More Complex Example

Lokalized's strength is handling phrases that must be rewritten in different ways according to language rules. Suppose we introduce gender alongside plural forms.  In English, a noun's gender usually does not alter other components of a phrase.  But in Spanish it does.

This English statement has singular and plural variants for masculine, feminine, and common gender:

* `He was one of the X best baseball players.`
* `She was one of the X best baseball players.`
* `This person was one of the X best baseball players.`
* `He was the best baseball player.`
* `She was the best baseball player.`
* `This person was the best baseball player.`

For the masculine and feminine Spanish cases, notice how several words must change to match gender - `uno` becomes
`una`, `jugadores` becomes `jugadoras`, and so on. For common gender, the translation instead uses `persona` and a
relative clause so agreement does not depend on the referent's gender. `Persona` is grammatically feminine regardless
of whom it denotes, so `las personas` does not imply an all-female group.

* `Fue uno de los X mejores jugadores de béisbol.`
* `Fue una de las X mejores jugadoras de béisbol.`
* `Esta persona estaba entre las X personas que mejor jugaban al béisbol.`
* `Él era el mejor jugador de béisbol.`
* `Ella era la mejor jugadora de béisbol.`
* `Esta persona era quien mejor jugaba al béisbol.`

### English Localized Strings File

English is a little simpler than Spanish here because gender affects only the generated subject fragment.

```json
{
  "{{heOrShe}} was one of the {{groupSize}} best baseball players." : {
    "translation" : "{{heOrShe}} was one of the {{groupSize}} best baseball players.",
    "placeholders" : {
      "heOrShe" : {
        "value" : "heOrShe",
        "translations" : {
          "GENDER_MASCULINE" : "He",
          "GENDER_FEMININE" : "She",
          "GENDER_COMMON" : "This person"
        }
      }
    },
    "alternatives" : [
      {
        "groupSize <= 1" : "{{heOrShe}} was the best baseball player."
      }
    ]
  }
}
```

The singular alternative inherits the root `heOrShe` definition. The selected branch can therefore reuse the same
gender fragment instead of duplicating one whole-message alternative per gender.

### Spanish Localized Strings File

Note that we define our own placeholders in `translation` and drive them off of the `heOrShe` value to support gender-based word changes.

```json
{
  "{{heOrShe}} was one of the {{groupSize}} best baseball players." : {
    "translation" : "Fue {{uno}} de {{los}} {{groupSize}} mejores {{jugadores}} de béisbol.",
    "placeholders" : {
      "uno" : {
        "value" : "heOrShe",
        "translations" : {
          "GENDER_MASCULINE" : "uno",
          "GENDER_FEMININE" : "una"
        }
      },
      "los" : {
        "value" : "heOrShe",
        "translations" : {
          "GENDER_MASCULINE" : "los",
          "GENDER_FEMININE" : "las"
        }
      },
      "jugadores" : {
        "value" : "heOrShe",
        "translations" : {
          "GENDER_MASCULINE" : "jugadores",
          "GENDER_FEMININE" : "jugadoras"
        }
      }
    },
    "alternatives" : [
      {
        "heOrShe == GENDER_COMMON && groupSize <= 1" : "Esta persona era quien mejor jugaba al béisbol."
      },
      {
        "heOrShe == GENDER_COMMON" : "Esta persona estaba entre las {{groupSize}} personas que mejor jugaban al béisbol."
      },
      {
        "heOrShe == GENDER_MASCULINE && groupSize <= 1" : "Él era el mejor jugador de béisbol."        
      },
      {
        "heOrShe == GENDER_FEMININE && groupSize <= 1" : "Ella era la mejor jugadora de béisbol."        
      }
    ]
  }
}
```

### The Rules, Exercised

Notice that we keep the gender and plural logic out of our code entirely and leave rule processing to the translation configuration.

```java
// "Normal" translation
String message = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.MASCULINE,
    "groupSize", 10
  ));

assertEquals("He was one of the 10 best baseball players.", message);

// Alternative expression triggered
message = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.MASCULINE,
    "groupSize", 1
  ));

assertEquals("He was the best baseball player.", message);

// ...now, here's what a Mexican Spanish (`es-MX`) user might see: 
message = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.FEMININE,
    "groupSize", 3
  ));

// Note that the correct feminine forms were applied
assertEquals("Fue una de las 3 mejores jugadoras de béisbol.", message);

// Spanish - common-gender wording uses persona instead of gendered jugador/jugadora forms
message = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.COMMON,
    "groupSize", 3
  ));

assertEquals("Esta persona estaba entre las 3 personas que mejor jugaban al béisbol.", message);

message = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.COMMON,
    "groupSize", 1
  ));

assertEquals("Esta persona era quien mejor jugaba al béisbol.", message);
```

### Recursive Alternatives

You can exploit the recursive nature of alternative expressions to reduce logic duplication.  Here, we define a toplevel alternative for `groupSize <= 1` which itself has alternatives for `GENDER_MASCULINE` and `GENDER_FEMININE` cases.  This is equivalent to the alternative rules defined above but might be a more "comfortable" way to express behavior for some.

Note that this is just a snippet to illustrate functionality - the other portion of this localized string has been elided for brevity.

```json
{
  "alternatives" : [
    {
      "groupSize <= 1" : {
        "alternatives" : [
          {
            "heOrShe == GENDER_MASCULINE" : "Él era el mejor jugador de béisbol."
          },
          {
            "heOrShe == GENDER_FEMININE" : "Ella era la mejor jugadora de béisbol."
          },
          {
            "heOrShe == GENDER_COMMON" : "Esta persona era quien mejor jugaba al béisbol."
          }
        ]
      }
    }
  ]
}
```

## Cardinality Ranges

When expressing a range of values (`1-3 meters`, `2.5-3.5 hours`), the cardinality of the range is determined by applying per-language rules to its start and end cardinalities.
  
Every explicitly listed English CLDR range pair selects [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER), but unlisted pairs use Lokalized's end-category fallback. For example, [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) -> [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) is unlisted and therefore evaluates to [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE). Many other languages have additional range-specific forms.

### French Localized Strings File

French ranges can be either [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) or  [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER).

```json
{
  "The meeting will be {{minHours}}-{{maxHours}} hours long." : {
    "translation" : "La réunion aura une durée de {{minHours}} à {{maxHours}} {{heures}}.",
    "placeholders" : {
      "heures" : {
        "range" : {
          "start" : "minHours",
          "end" : "maxHours"
        },
        "translations" : {
          "CARDINALITY_ONE" : "heure",
          "CARDINALITY_OTHER" : "heures"
        }
      }
    }
  }
}
```

### English Localized Strings File

English ranges with different endpoints use the explicit CLDR mappings and evaluate to [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER). Equal singular endpoints ([`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) -> [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE)) use the end-category fallback and evaluate to [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE), so a translation that must handle that case should provide both forms.


```json
{
  "The meeting will be {{minHours}}-{{maxHours}} hours long." : {
    "translation" : "The meeting will be {{minHours}}-{{maxHours}} {{hours}} long.",
    "placeholders" : {
      "hours" : {
        "range" : {
          "start" : "minHours",
          "end" : "maxHours"
        },
        "translations" : {
          "CARDINALITY_ONE" : "hour",
          "CARDINALITY_OTHER" : "hours"
        }
      }
    }
  }
}
```

### Cardinality Ranges, Exercised

```java
// French CARDINALITY_OTHER case 
String message = strings.get("The meeting will be {{minHours}}-{{maxHours}} hours long.",
  Map.of(
    "minHours", 1,
    "maxHours", 3
  ));

assertEquals("La réunion aura une durée de 1 à 3 heures.", message);

// French CARDINALITY_ONE case
message = strings.get("The meeting will be {{minHours}}-{{maxHours}} hours long.",
  Map.of(
    "minHours", 0,
    "maxHours", 1
  ));

assertEquals("La réunion aura une durée de 0 à 1 heure.", message);
```

## Ordinal Forms

Many languages have special forms called _ordinals_ to express a "ranking" in a sequence of numbers.  For example, in English we might say
 
* `Take the 1st left after the intersection`
* `She is my 2nd cousin`
* `I finished the race in 3rd place`

Let's look at an example related to birthdays.

### English Localized Strings File

CLDR defines four ordinal categories for English.

```json
{
  "{{hisOrHer}} {{year}}th birthday party is next week." : {  
    "translation" : "{{hisOrHer}} {{year}}{{ordinal}} birthday party is next week.",
    "placeholders" : {  
      "hisOrHer" : {  
        "value" : "hisOrHer",
        "translations" : {  
          "GENDER_MASCULINE" : "His",
          "GENDER_FEMININE" : "Her"
        }
      },
      "ordinal" : {  
        "value" : "year",
        "translations" : {  
          "ORDINALITY_ONE" : "st",
          "ORDINALITY_TWO" : "nd",
          "ORDINALITY_FEW" : "rd",
          "ORDINALITY_OTHER" : "th"
        }
      }
    }
  }
}
```

### Spanish Localized Strings File

CLDR assigns Spanish only `ORDINALITY_OTHER`, so ordinal category selection does not vary by number. Spanish still has ordinal expressions; this example uses application-specific birthday wording instead of an ordinal-suffix map.

```json
{
  "{{hisOrHer}} {{year}}th birthday party is next week." : {
    "translation" : "Su fiesta de cumpleaños número {{year}} es la próxima semana.",
    "alternatives" : [
      {
        "year == 1" : "Su primera fiesta de cumpleaños es la próxima semana."        
      },
      {
        "hisOrHer == GENDER_FEMININE && year == 15" : "Su quinceañera es la próxima semana."        
      }
    ]
  }
}
```

### Ordinals, Exercised

```java
// The ORDINALITY_OTHER rule is applied for 18 in English
String message = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.MASCULINE,
    "year", 18
  ));

assertEquals("His 18th birthday party is next week.", message);

// The ORDINALITY_ONE rule is applied to any of the "one" numbers (1, 21, 31, ...) in English
message = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.FEMININE,
    "year", 21
  ));

assertEquals("Her 21st birthday party is next week.", message);

// Spanish - normal case
message = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.MASCULINE,
    "year", 18
  ));

assertEquals("Su fiesta de cumpleaños número 18 es la próxima semana.", message);

// Spanish - special case for first birthday
message = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "year", 1
  ));

assertEquals("Su primera fiesta de cumpleaños es la próxima semana.", message);

// Spanish - special case for a girl's 15th birthday
message = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.FEMININE,
    "year", 15
  ));

assertEquals("Su quinceañera es la próxima semana.", message);
```

## Language Forms

### Gender

Gender rules vary across languages, but the general meaning is the same.
 
Lokalized supports these values:

* [`GENDER_MASCULINE`](https://javadoc.lokalized.com/com/lokalized/Gender.html#MASCULINE)
* [`GENDER_FEMININE`](https://javadoc.lokalized.com/com/lokalized/Gender.html#FEMININE)
* [`GENDER_COMMON`](https://javadoc.lokalized.com/com/lokalized/Gender.html#COMMON)
* [`GENDER_NEUTER`](https://javadoc.lokalized.com/com/lokalized/Gender.html#NEUTER)

Some languages (e.g. Swedish, Danish, Dutch) collapse masculine and feminine into a common gender. Use
`GENDER_COMMON` for that class (for example, Swedish `en` words) and `GENDER_NEUTER` for neuter (`ett` words).

Lokalized provides a [`Gender`](https://javadoc.lokalized.com/com/lokalized/Gender.html) type which enumerates supported genders.

### Grammatical Case

Grammatical case rules determine how a noun or pronoun changes according to its syntactic role.

Lokalized supports these values:

* [`CASE_NOMINATIVE`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#NOMINATIVE)
* [`CASE_ACCUSATIVE`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#ACCUSATIVE)
* [`CASE_GENITIVE`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#GENITIVE)
* [`CASE_DATIVE`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#DATIVE)
* [`CASE_INSTRUMENTAL`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#INSTRUMENTAL)
* [`CASE_LOCATIVE`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#LOCATIVE)
* [`CASE_PREPOSITIONAL`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#PREPOSITIONAL)
* [`CASE_VOCATIVE`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#VOCATIVE)
* [`CASE_ABLATIVE`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html#ABLATIVE)

Lokalized provides a [`GrammaticalCase`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html) type which enumerates supported case values. The enum is intentionally high-coverage rather than exhaustive; if a language distinguishes more cases, map them to the closest supported value in your application code.

#### Example

In Russian, a recipient often takes dative case:

```json
{
  "Send a message to the recipient." : {
    "translation" : "Отправить сообщение {{recipientForm}}.",
    "placeholders" : {
      "recipientForm" : {
        "value" : "grammaticalCase",
        "translations" : {
          "CASE_NOMINATIVE" : "Иван",
          "CASE_DATIVE" : "Ивану",
          "CASE_ACCUSATIVE" : "Ивана"
        }
      }
    }
  }
}
```

Now select the grammatical role at runtime:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ru"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier(matcher -> Locale.forLanguageTag("ru"))
  .build();

assertEquals("Отправить сообщение Ивану.", strings.get("Send a message to the recipient.", Map.of(
  "grammaticalCase", GrammaticalCase.DATIVE
)));
```

This example is intentionally partial: if application code supplies a grammatical case that is not listed here, the lookup is treated as a resolution failure and your configured `TranslationFailureHandler` decides what happens.  The default returns the key; use `TranslationFailureHandler.throwException()` to throw.

### Definiteness

Definiteness rules distinguish whether a noun phrase is definite, indefinite, or in construct/bound state.

Lokalized supports these values:

* [`DEFINITENESS_DEFINITE`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html#DEFINITE)
* [`DEFINITENESS_INDEFINITE`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html#INDEFINITE)
* [`DEFINITENESS_CONSTRUCT`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html#CONSTRUCT)

Lokalized provides a [`Definiteness`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html) type which enumerates supported definiteness values.

#### Example

Arabic and Hebrew frequently change noun phrases based on definiteness:

```json
{
  "Open the document." : {
    "translation" : "افتح {{documentForm}}.",
    "placeholders" : {
      "documentForm" : {
        "value" : "definiteness",
        "translations" : {
          "DEFINITENESS_DEFINITE" : "الكتاب",
          "DEFINITENESS_INDEFINITE" : "كتابًا",
          "DEFINITENESS_CONSTRUCT" : "كتاب"
        }
      }
    }
  }
}
```

Then choose the desired form at runtime:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ar"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier(matcher -> Locale.forLanguageTag("ar"))
  .build();

assertEquals("افتح الكتاب.", strings.get("Open the document.", Map.of(
  "definiteness", Definiteness.DEFINITE
)));
```

### Classifiers

Classifier rules select the measure word or counter associated with a noun.

Lokalized supports these values:

* [`CLASSIFIER_GENERAL`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#GENERAL)
* [`CLASSIFIER_PERSON`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#PERSON)
* [`CLASSIFIER_ANIMAL`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#ANIMAL)
* [`CLASSIFIER_LONG_THIN`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#LONG_THIN)
* [`CLASSIFIER_FLAT`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#FLAT)
* [`CLASSIFIER_BOUND`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#BOUND)
* [`CLASSIFIER_MACHINE`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#MACHINE)
* [`CLASSIFIER_VEHICLE`](https://javadoc.lokalized.com/com/lokalized/Classifier.html#VEHICLE)

Lokalized provides a [`Classifier`](https://javadoc.lokalized.com/com/lokalized/Classifier.html) type which enumerates supported classifier categories. This enum is intentionally generic and non-exhaustive: it captures common semantic buckets across classifier languages, but applications with language-specific inventories may still want separate keys or alternative expressions in some cases.

#### Example

In Japanese, the counter for books differs from the general-purpose counter:

```json
{
  "I bought {{count}} items." : {
    "translation" : "{{count}}{{counter}}買いました。",
    "placeholders" : {
      "counter" : {
        "value" : "classifier",
        "translations" : {
          "CLASSIFIER_GENERAL" : "つ",
          "CLASSIFIER_BOUND" : "冊",
          "CLASSIFIER_MACHINE" : "台"
        }
      }
    }
  }
}
```

Then choose the classifier category in calling code:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ja"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier(matcher -> Locale.forLanguageTag("ja"))
  .build();

assertEquals("3冊買いました。", strings.get("I bought {{count}} items.", Map.of(
  "count", 3,
  "classifier", Classifier.BOUND
)));
```

This example is intentionally partial: if application code supplies a classifier that is not listed here, the lookup is treated as a resolution failure and your configured `TranslationFailureHandler` decides what happens.  The default returns the key; use `TranslationFailureHandler.throwException()` to throw.

### Formality

Formality rules determine whether a phrase is rendered in a casual, informal, formal, humble, or honorific register.

Lokalized supports these values:

* [`FORMALITY_CASUAL`](https://javadoc.lokalized.com/com/lokalized/Formality.html#CASUAL)
* [`FORMALITY_INFORMAL`](https://javadoc.lokalized.com/com/lokalized/Formality.html#INFORMAL)
* [`FORMALITY_FORMAL`](https://javadoc.lokalized.com/com/lokalized/Formality.html#FORMAL)
* [`FORMALITY_HUMBLE`](https://javadoc.lokalized.com/com/lokalized/Formality.html#HUMBLE)
* [`FORMALITY_HONORIFIC`](https://javadoc.lokalized.com/com/lokalized/Formality.html#HONORIFIC)

Lokalized provides a [`Formality`](https://javadoc.lokalized.com/com/lokalized/Formality.html) type which enumerates supported formality values.

#### Example

Let's model a greeting with different levels of formality:

```json
{
  "Hello, {{name}}." : {
    "translation" : "{{greeting}}, {{name}}.",
    "placeholders" : {
      "greeting" : {
        "value" : "formality",
        "translations" : {
          "FORMALITY_CASUAL" : "Hey",
          "FORMALITY_INFORMAL" : "Hi",
          "FORMALITY_FORMAL" : "Hello",
          "FORMALITY_HUMBLE" : "I humbly greet you",
          "FORMALITY_HONORIFIC" : "Greetings"
        }
      }
    }
  }
}
```

Now select the register at runtime:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier(matcher -> Locale.forLanguageTag("en"))
  .build();

assertEquals("Greetings, Dr. Smith.", strings.get("Hello, {{name}}.", Map.of(
  "formality", Formality.HONORIFIC,
  "name", "Dr. Smith"
)));

assertEquals("Hey, Sam.", strings.get("Hello, {{name}}.", Map.of(
  "formality", Formality.CASUAL,
  "name", "Sam"
)));

assertEquals("I humbly greet you, Professor Tanaka.", strings.get("Hello, {{name}}.", Map.of(
  "formality", Formality.HUMBLE,
  "name", "Professor Tanaka"
)));

assertEquals("Hi, Sam.", strings.get("Hello, {{name}}.", Map.of(
  "formality", Formality.INFORMAL,
  "name", "Sam"
)));
```

### Clusivity

Clusivity rules distinguish between inclusive and exclusive first-person plurals.

Lokalized supports these values:

* [`CLUSIVITY_INCLUSIVE`](https://javadoc.lokalized.com/com/lokalized/Clusivity.html#INCLUSIVE)
* [`CLUSIVITY_EXCLUSIVE`](https://javadoc.lokalized.com/com/lokalized/Clusivity.html#EXCLUSIVE)

Lokalized provides a [`Clusivity`](https://javadoc.lokalized.com/com/lokalized/Clusivity.html) type which enumerates supported clusivity values.

#### Example

In Malay, `kita` includes the addressee while `kami` excludes them. Let's model `We will meet at noon.`:

```json
{
  "We will meet at noon." : {
    "translation" : "{{we}} akan bertemu pada tengah hari.",
    "placeholders" : {
      "we" : {
        "value" : "clusivity",
        "translations" : {
          "CLUSIVITY_INCLUSIVE" : "Kita",
          "CLUSIVITY_EXCLUSIVE" : "Kami"
        }
      }
    }
  }
}
```

Now choose inclusive vs exclusive at runtime:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ms"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier(matcher -> Locale.forLanguageTag("ms"))
  .build();

assertEquals("Kita akan bertemu pada tengah hari.", strings.get("We will meet at noon.", Map.of(
  "clusivity", Clusivity.INCLUSIVE
)));

assertEquals("Kami akan bertemu pada tengah hari.", strings.get("We will meet at noon.", Map.of(
  "clusivity", Clusivity.EXCLUSIVE
)));
```

### Animacy

Animacy rules distinguish between animate and inanimate referents.

Lokalized supports these values:

* [`ANIMACY_ANIMATE`](https://javadoc.lokalized.com/com/lokalized/Animacy.html#ANIMATE)
* [`ANIMACY_INANIMATE`](https://javadoc.lokalized.com/com/lokalized/Animacy.html#INANIMATE)

Lokalized provides an [`Animacy`](https://javadoc.lokalized.com/com/lokalized/Animacy.html) type which enumerates supported animacy values.

#### Example

In Russian, masculine accusative forms often change based on animacy. Here's a simple example:

```json
{
  "I see {{object}}." : {
    "translation" : "Я вижу {{object}}.",
    "placeholders" : {
      "object" : {
        "value" : "animacy",
        "translations" : {
          "ANIMACY_ANIMATE" : "брата",
          "ANIMACY_INANIMATE" : "стол"
        }
      }
    }
  }
}
```

Then select the animacy value at runtime:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ru"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier(matcher -> Locale.forLanguageTag("ru"))
  .build();

assertEquals("Я вижу брата.", strings.get("I see {{object}}.", Map.of(
  "animacy", Animacy.ANIMATE
)));

assertEquals("Я вижу стол.", strings.get("I see {{object}}.", Map.of(
  "animacy", Animacy.INANIMATE
)));
```

### Plural Cardinality

For example: `1 book, 2 books, ...`

Plural rules vary widely across languages.

Lokalized supports these values according to [CLDR 48 rules](https://www.unicode.org/cldr/charts/48/supplemental/language_plural_rules.html):

* [`CARDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ZERO)
* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE)
* [`CARDINALITY_TWO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#TWO)
* [`CARDINALITY_FEW`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#FEW)
* [`CARDINALITY_MANY`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#MANY)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) 

Values do not necessarily map exactly to the named number, e.g. in some languages [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) might mean any number ending in `1`, not just `1`.  Most languages only support a few plural forms, some have none at all (represented by [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) in those cases).

#### Japanese

* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER): Matches everything (this language has no plural form)

#### English

* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE): Matches 1 (e.g. `1 dollar`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER): Everything else (e.g. `256 dollars`)

#### Russian

* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE): Matches 1, 21, 31, ... (e.g. `1 рубль` or `51 рубль`)
* [`CARDINALITY_FEW`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#FEW): Matches 2-4, 22-24, 32-34, ... (e.g. `2 рубля` or `53 рубля`)
* [`CARDINALITY_MANY`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#MANY): Matches 0, 5-20, 25-30, 45-50, ... (e.g. `5 рублей` or `17 рублей`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER): Everything else (e.g. `0,3 руб`, `1,5 руб`)

Lokalized provides a [`Cardinality`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html) type which encapsulates cardinal functionality.

[`Cardinality#getSupportedLocaleTags()`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#getSupportedLocaleTags()) returns the locale tags represented directly in the pinned CLDR cardinality-rule data. Concrete region- or script-qualified locales can also work through rule fallback; use [`Cardinality#supportedCardinalitiesForLocale(Locale)`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#supportedCardinalitiesForLocale(java.util.Locale)) to probe one.

You may programmatically determine cardinality using [`Cardinality#forNumber(Number number, Locale locale)`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#forNumber(java.lang.Number,java.util.Locale)) and [`Cardinality#forNumber(Number number, Integer visibleDecimalPlaces, Locale locale)`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#forNumber(java.lang.Number,java.lang.Integer,java.util.Locale)) as shown below.

It is important to note that the number of visible decimal places can be important for some languages when performing cardinality evaluation.  For example, in English, `1` matches [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) but `1.0` matches [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER).  Even though the numbers' true values are identical, you would say `1 inch` and `1.0 inches` and therefore must take visible decimals into account.

```java
// Basic case - a primitive number, no decimals
Cardinality cardinality = Cardinality.forNumber(1, Locale.forLanguageTag("en"));
assertEquals(Cardinality.ONE, cardinality);

// In the absence of an explicit number of visible decimals,
// 1.0 evaluates to Cardinality.ONE since primitive 1 == primitive 1.0
cardinality = Cardinality.forNumber(1.0, Locale.forLanguageTag("en"));
assertEquals(Cardinality.ONE, cardinality);

// With 1 visible decimal specified ("1.0"), we evaluate to Cardinality.OTHER
cardinality = Cardinality.forNumber(1, 1, Locale.forLanguageTag("en"));
assertEquals(Cardinality.OTHER, cardinality);

// Let's try BigDecimal instead of a primitive...
cardinality = Cardinality.forNumber(new BigDecimal("1"), Locale.forLanguageTag("en"));
assertEquals(Cardinality.ONE, cardinality);

// Using BigDecimal obviates the need to specify visible decimals
// since they can be encoded directly in the number.
// We evaluate to Cardinality.OTHER, as expected
cardinality = Cardinality.forNumber(new BigDecimal("1.0"), Locale.forLanguageTag("en"));
assertEquals(Cardinality.OTHER, cardinality);
```  

For compact-decimal displays, use [`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html) when CLDR needs operand details that are not fully represented by the Java number itself:

```java
PluralOperands operands = PluralOperands.forNumber(2)
    .compactExponent(6)
    .build();

cardinality = Cardinality.forOperands(operands, Locale.forLanguageTag("fr"));
assertEquals(Cardinality.MANY, cardinality);
```

Here, `2` is the displayed mantissa and the compact exponent `6` carries the magnitude (for example, a display such
as `2M`). Lokalized accepts `BigDecimal`, `BigInteger`, the boxed integral and floating-point JDK types, and the JDK
atomic/adder/accumulator numeric types documented by `PluralOperands`. Unknown `Number` implementations are rejected;
convert an application-specific number to `BigDecimal` explicitly so precision is never guessed through
`doubleValue()` or an arbitrary `toString()` representation.

`visibleDecimalPlaces(...)` may add trailing zeroes, but it never rounds a number implicitly. If reducing the
scale would discard a nonzero digit, `build()` throws `ArithmeticException`; round the displayed value explicitly
before constructing its operands so plural selection and presentation cannot silently disagree.

To keep plural-operand construction predictably bounded, the defaults accept at most 1,024 significant digits, an
absolute decimal scale of 1,024, 1,024 explicitly visible decimal places, and a compact exponent of 64. Applications
can configure these through [`TranslationRuntimeLimits`](https://javadoc.lokalized.com/com/lokalized/TranslationRuntimeLimits.html),
up to hard ceilings of 4,096 for each value. The hard ceilings are also exposed as boxed constants on
[`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html).
Apply custom limits to a `Strings` instance with
[`Strings.Builder.runtimeLimits(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#runtimeLimits(com.lokalized.TranslationRuntimeLimits))
or to direct plural-operand construction with
[`PluralOperands.Builder.runtimeLimits(...)`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.Builder.html#runtimeLimits(com.lokalized.TranslationRuntimeLimits)).
The [`Cardinality.forNumber(...)`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#forNumber(java.lang.Number,java.util.Locale))
and [`Ordinality.forNumber(...)`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#forNumber(java.lang.Number,java.util.Locale))
convenience methods use the library defaults; for an opted-up value, build `PluralOperands` explicitly and pass them to
[`Cardinality.forOperands(...)`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#forOperands(com.lokalized.PluralOperands,java.util.Locale))
or [`Ordinality.forOperands(...)`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#forOperands(com.lokalized.PluralOperands,java.util.Locale)).

### Plural Cardinality Ranges

For example: `0-1 hours, 1-2 hours, ...`

The plural form of the range is determined by examining the cardinality of its start and end components. 

#### English

* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `1–2 days`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0–1 days`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0–2 days`)

#### French

* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) (e.g. `0–1 jour`)
* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0–2 jours`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `2–100 jours`)

#### Latvian

* [`CARDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ZERO) - [`CARDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ZERO) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0–10 diennaktis`)
* [`CARDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ZERO) - [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) (e.g. `0–1 diennakts`)
* [`CARDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ZERO) - [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0–2 diennaktis`)
* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ZERO) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0,1–10 diennaktis`)
* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) (e.g. `0,1–1 diennakts`)
* [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0,1–2 diennaktis`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ZERO) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0,2–10 diennaktis`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) (e.g. `0,2–1 diennakts`)
* [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) (e.g. `0,2–2 diennaktis`)

You may programmatically determine a range's cardinality using [`Cardinality#forRange(Cardinality start, Cardinality end, Locale locale)`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#forRange(com.lokalized.Cardinality,com.lokalized.Cardinality,java.util.Locale)) as shown below.

```java
// Latvian has a number of interesting range rules.
// ZERO-ZERO -> OTHER
Cardinality cardinality = Cardinality.forRange(Cardinality.ZERO, Cardinality.ZERO, Locale.forLanguageTag("lv"));
assertEquals(Cardinality.OTHER, cardinality);

// ZERO-ONE -> ONE
cardinality = Cardinality.forRange(Cardinality.ZERO, Cardinality.ONE, Locale.forLanguageTag("lv"));
assertEquals(Cardinality.ONE, cardinality);
```

### Phonetics

Some languages choose word forms based on the <em>sound</em> that follows (e.g. English `a/an`, Spanish `el agua`, Italian `lo studente`). Lokalized supports these via phonetic categories and a user-provided resolver.

Lokalized supports these values:

* [`PHONETIC_VOWEL`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#VOWEL)
* [`PHONETIC_CONSONANT`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#CONSONANT)
* [`PHONETIC_H_SILENT`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#H_SILENT)
* [`PHONETIC_H_ASPIRATED`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#H_ASPIRATED)
* [`PHONETIC_S_IMPURE`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#S_IMPURE)
* [`PHONETIC_Z`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#Z)
* [`PHONETIC_GN`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#GN)
* [`PHONETIC_PS`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#PS)
* [`PHONETIC_PN`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#PN)
* [`PHONETIC_X`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#X)
* [`PHONETIC_GLIDE_Y`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#GLIDE_Y)
* [`PHONETIC_GLIDE_W`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#GLIDE_W)
* [`PHONETIC_STRESSED_A`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#STRESSED_A)
* [`PHONETIC_SOLAR`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#SOLAR)
* [`PHONETIC_LUNAR`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#LUNAR)
* [`PHONETIC_OTHER`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html#OTHER)

Lokalized provides a [`Phonetic`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html) type which enumerates supported phonetic categories. To use phonetics, supply a [`PhoneticResolver`](https://javadoc.lokalized.com/com/lokalized/PhoneticResolver.html) when building [`Strings`](https://javadoc.lokalized.com/com/lokalized/Strings.html) and use `PHONETIC_*` values in your translations file. The resolver receives both the term and its locale.

#### English Example

Let's model `I received a {{noun}}.`:

```json
{
  "I received a {{noun}}." : {
    "translation" : "I received {{article}} {{noun}}.",
    "placeholders" : {
      "article" : {
        "value" : "noun",
        "translations" : {
          "PHONETIC_VOWEL" : "an",
          "PHONETIC_CONSONANT" : "a"
        }
      }
    }
  }
}
```

Now, ensure we have translations like `an honor` and `a gift`:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  // Plug in a custom resolver here. You would bring your own "startsWithVowelSound" implementation
  .phoneticResolver((term, locale) -> startsWithVowelSound(term, locale) ? Phonetic.VOWEL : Phonetic.CONSONANT)
  .localeSupplier(matcher -> Locale.forLanguageTag("en"))
  .build();

assertEquals("I received an honor.", strings.get("I received a {{noun}}.", Map.of("noun", "honor")));
assertEquals("I received a gift.", strings.get("I received a {{noun}}.", Map.of("noun", "gift")));
```

#### Spanish Example (Stressed A)

Now, for Spanish:

```json
{
  "I received a {{noun}}." : {
    "translation" : "Recibi {{article}} {{noun}}.",
    "placeholders" : {
      "article" : {
        "value" : "noun",
        "translations" : {
          "PHONETIC_STRESSED_A" : "el",
          "PHONETIC_OTHER" : "la"
        }
      }
    }
  }
}
```

...and its [`PhoneticResolver`](https://javadoc.lokalized.com/com/lokalized/PhoneticResolver.html):

```java
// Special "Stressed-A" support for Spanish languages
PhoneticResolver spanishResolver = (term, locale) -> {
  if (!"es".equals(locale.getLanguage()))
    return Phonetic.OTHER;

  String normalized = term.toLowerCase(Locale.ROOT);

  // It is your responsibility to define this set
  return Set.of("acta", "arma", "hacha").contains(normalized)
    ? Phonetic.STRESSED_A
    : Phonetic.OTHER;
};

Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("es"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .phoneticResolver(spanishResolver)
  .localeSupplier(matcher -> Locale.forLanguageTag("es"))
  .build();

assertEquals("Recibi el acta.", strings.get("I received a {{noun}}.", Map.of("noun", "acta")));
assertEquals("Recibi la carta.", strings.get("I received a {{noun}}.", Map.of("noun", "carta")));
```

### Ordinals

For example: `1st, 2nd, 3rd, 4th, ...`

Similar to plural cardinality, ordinal rules vary widely across languages.

Lokalized supports these values according to [CLDR 48 rules](https://www.unicode.org/cldr/charts/48/supplemental/language_plural_rules.html):

* [`ORDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ZERO)
* [`ORDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ONE)
* [`ORDINALITY_TWO`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#TWO)
* [`ORDINALITY_FEW`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#FEW)
* [`ORDINALITY_MANY`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#MANY)
* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER)

Again, like cardinal values, ordinals do not necessarily map to the named number. For example, [`ORDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ONE) might apply to any number that ends in `1`.

#### Spanish

* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER): Matches every number (CLDR defines no category-dependent ordinal variation for Spanish)

#### English

* [`ORDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ONE): Matches 1, 21, 31, ... (e.g. `1st prize`)
* [`ORDINALITY_TWO`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#TWO): Matches 2, 22, 32, ... (e.g. `22nd prize`)
* [`ORDINALITY_FEW`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#FEW): Matches 3, 23, 33, ... (e.g. `33rd prize`)
* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER): Everything else (e.g. `12th prize`)

#### Italian

* [`ORDINALITY_MANY`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#MANY): Matches 8, 11, 80, 800 (e.g. `Prendi l’8° a destra`)
* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER): Everything else (e.g. `Prendi la 7° a destra`)

Lokalized provides an [`Ordinality`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html) type which encapsulates ordinal functionality.

You may programmatically determine ordinality using [`Ordinality#forNumber(Number number, Locale locale)`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#forNumber(java.lang.Number,java.util.Locale)) as shown below.

```java
// e.g. "1st"
Ordinality ordinality = Ordinality.forNumber(1, Locale.forLanguageTag("en"));
assertEquals(Ordinality.ONE, ordinality);

// e.g. "2nd"
ordinality = Ordinality.forNumber(2, Locale.forLanguageTag("en"));
assertEquals(Ordinality.TWO, ordinality);

// e.g. "3rd"
ordinality = Ordinality.forNumber(3, Locale.forLanguageTag("en"));
assertEquals(Ordinality.FEW, ordinality);

// e.g. "21st"
ordinality = Ordinality.forNumber(21, Locale.forLanguageTag("en"));
assertEquals(Ordinality.ONE, ordinality);

// e.g. "27th"
ordinality = Ordinality.forNumber(27, Locale.forLanguageTag("en"));
assertEquals(Ordinality.OTHER, ordinality);
```

[`Ordinality#forOperands(PluralOperands operands, Locale locale)`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#forOperands(com.lokalized.PluralOperands,java.util.Locale)) is also available for advanced CLDR operand cases.

[`Ordinality#getSupportedLocaleTags()`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#getSupportedLocaleTags()) returns the locale tags represented directly in the pinned CLDR plural-rule data. Concrete region- or script-qualified locales can also work through rule fallback; use [`Ordinality#supportedOrdinalitiesForLocale(Locale)`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#supportedOrdinalitiesForLocale(java.util.Locale)) to probe one.

## CLDR Data

Lokalized's cardinality, ordinality, cardinality-range, locale matching, locale-tag validation, and right-to-left script behavior is generated from pinned Unicode CLDR 48.2 source data.

Pinned CLDR resources live under [src/test/resources/cldr/48.2](https://github.com/lokalized/lokalized-java/tree/master/src/test/resources/cldr/48.2). That directory records the upstream source URLs, SHA-256 checksums, license note, refresh commands, and generator command. Generated runtime data is checked in under `src/main/java`, exhaustive conformance fixtures are checked in under `src/test/java`, and the website-facing grouped plural data (`formatVersion: 1`) is checked in at `src/build/resources/cldr/cldr-plural-data.json`.

After refreshing CLDR data, regenerate the checked-in sources and run the conformance tests before committing:

```shell
javac -d target/cldr-generator src/build/java/com/lokalized/cldr/CldrDataGenerator.java
java -cp target/cldr-generator com.lokalized.cldr.CldrDataGenerator
java -cp target/cldr-generator com.lokalized.cldr.CldrDataGenerator --check
mvn -q test
```

The normal Maven build compiles the generator as test code and performs a byte-for-byte drift check across every generated Java and JSON artifact. The generator is not packaged in the runtime JAR.

## Translation Failure Handling

Locale fallback and final failure handling are separate decisions. After each unsuccessful locale attempt,
[`TranslationFallbackPolicy`](https://javadoc.lokalized.com/com/lokalized/TranslationFallbackPolicy.html) decides whether
to try the next candidate. Only after fallback stops or candidates are exhausted does Lokalized ask the configured
[`TranslationFailureHandler`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html) what to return
or throw. The default policy falls back for missing translations and unmatched alternatives, but stops on runtime
resolution failures so a corrupt translation cannot be silently hidden by a different locale. The default handler is
[`TranslationFailureHandler.returnKey()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#returnKey()),
which silently returns the lookup key with caller-supplied placeholders interpolated into it.

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .translationFailureHandler(TranslationFailureHandler.throwException())
  .build();
```

Built-in handler factories are:

* [`TranslationFailureHandler.returnKey()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#returnKey()) - silently returns the key with supplied placeholders interpolated
* [`TranslationFailureHandler.returnKey(Consumer<? super TranslationFailure>)`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#returnKey(java.util.function.Consumer)) - sends the structured failure to the supplied observer, then returns the interpolated key
* [`TranslationFailureHandler.throwException()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#throwException()) - throws [`MissingTranslationException`](https://javadoc.lokalized.com/com/lokalized/MissingTranslationException.html) for missing translations and rethrows runtime resolution failures

Built-in fallback policies are:

* [`fallbackOnMissingTranslationOrNoMatchingAlternative()`](https://javadoc.lokalized.com/com/lokalized/TranslationFallbackPolicy.html#fallbackOnMissingTranslationOrNoMatchingAlternative()) - the safe default
* [`fallbackOnAnyFailure()`](https://javadoc.lokalized.com/com/lokalized/TranslationFallbackPolicy.html#fallbackOnAnyFailure()) - preserves Lokalized 2.x behavior, including fallback after resolution failures
* [`neverFallback()`](https://javadoc.lokalized.com/com/lokalized/TranslationFallbackPolicy.html#neverFallback()) - stops after the first failed locale attempt

Global policies are configured with
[`Strings.Builder.translationFallbackPolicy(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#translationFallbackPolicy(com.lokalized.TranslationFallbackPolicy));
[`TranslationOptions.Builder.translationFallbackPolicy(...)`](https://javadoc.lokalized.com/com/lokalized/TranslationOptions.Builder.html#translationFallbackPolicy(com.lokalized.TranslationFallbackPolicy))
overrides it for one lookup. Custom policies and handlers may be called concurrently and must be thread-safe.

Failure reasons distinguish a key that is absent from every attempted candidate locale
([`MISSING_TRANSLATION`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#MISSING_TRANSLATION)),
a present alternatives-only key for which no condition matched
([`NO_MATCHING_ALTERNATIVE`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#NO_MATCHING_ALTERNATIVE)),
and a translation that could not be evaluated or interpolated
([`RESOLUTION_FAILURE`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#RESOLUTION_FAILURE)).
Only resolution failures carry a runtime cause.
An evaluated expression-fragment predicate or selected-fragment interpolation failure is a resolution failure, so the
default fallback policy stops; [`fallbackOnAnyFailure()`](https://javadoc.lokalized.com/com/lokalized/TranslationFallbackPolicy.html#fallbackOnAnyFailure())
may continue to another locale. An expression-selected fragment always has a default `translation` and therefore
cannot itself cause `NO_MATCHING_ALTERNATIVE`.

Both [`returnKey()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#returnKey()) variants also handle runtime resolution failures by returning the interpolated key. Use [`throwException()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#throwException()) or a custom handler that throws on [`TranslationFailureReason.RESOLUTION_FAILURE`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#RESOLUTION_FAILURE) in development and test environments if you want broken placeholder rules, expressions, or custom resolvers to surface immediately.

Custom handlers inspect a [`TranslationFailure`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html) and return a [`TranslationFailureResponse`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureResponse.html). For example, you might fail softly for missing translations but throw for resolution failures, which usually indicate a broken placeholder, expression, or language-form rule:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .translationFailureHandler((failure) -> {
    if (failure.getReason() == TranslationFailureReason.RESOLUTION_FAILURE)
      return TranslationFailureResponse.throwException();

    return TranslationFailureResponse.returnKey();
  })
  .build();
```

[`TranslationFailure`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html) exposes
[`getKey()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getKey()),
[`getLookupLocale()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getLookupLocale()), optional
[`getLocaleMatchResult()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getLocaleMatchResult()),
[`getAttemptedLocales()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getAttemptedLocales()),
caller-supplied placeholders,
[`getReason()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getReason()), and optional
[`getCause()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getCause()). Its
[`getMessage()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getMessage()) is a redacted,
human-readable summary containing only the key, lookup locale, reason, and attempted locales. It omits caller-supplied
placeholder values and runtime-cause messages; inspect `getCause()` separately when your application intends to expose
that detail. Placeholder values can contain user data, so avoid recording
[`failure.getPlaceholders()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getPlaceholders())
unless your application has explicitly approved that.

## Translation Diagnostics

Most callers can use [`get(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.html#get(java.lang.String,java.util.Map,com.lokalized.TranslationOptions))
and configure locale selection with
[`localeSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localeSupplier(java.util.function.Function))
and [`bestMatchFor(...)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchFor(java.util.List)). Call
[`getResult(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.html#getResult(java.lang.String,java.util.Map,com.lokalized.TranslationOptions))
only when the caller also needs diagnostics:

```java
TranslationResult result = strings.getResult(
  "I read {{bookCount}} books.",
  Map.of("bookCount", 3),
  TranslationOptions.forLanguageRanges(LanguageRange.parse("pt-PT,pt;q=0.8"))
);

String message = result.getTranslation();
Locale lookupLocale = result.getLookupLocale();
Optional<Locale> resolvedLocale = result.getResolvedLocale();
List<Locale> attemptedLocales = result.getAttemptedLocales();
Boolean usedFallback = result.isFallback();
Optional<LocaleMatchResult> localeMatch = result.getLocaleMatchResult();
```

[`TranslationResult`](https://javadoc.lokalized.com/com/lokalized/TranslationResult.html) uses
[`TranslationResultStatus`](https://javadoc.lokalized.com/com/lokalized/TranslationResultStatus.html) to distinguish
translated, returned-key, and handler-returned-string outcomes, and exposes the final failure reason and resolution cause
when applicable. Per-call language ranges, such as those supplied through
[`TranslationOptions.forLanguageRanges(...)`](https://javadoc.lokalized.com/com/lokalized/TranslationOptions.html#forLanguageRanges(java.util.List)),
are preserved in its optional [`LocaleMatchResult`](https://javadoc.lokalized.com/com/lokalized/LocaleMatchResult.html).

If request-scoped `Accept-Language` negotiation is configured globally and those original diagnostics must be retained,
use the diagnostic supplier explicitly:

```java
Strings stringsWithNegotiationDiagnostics = Strings.withFallbackLocale(FALLBACK_LOCALE)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  .localeMatchSupplier((matcher) -> {
    HttpServletRequest request = MyWebContext.getHttpServletRequest();
    String acceptLanguage = request.getHeader("Accept-Language");
    List<LanguageRange> languageRanges = LanguageRange.parse(acceptLanguage);
    return matcher.matchFor(languageRanges);
  })
  .build();
```

[`localeMatchSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localeMatchSupplier(java.util.function.Function))
passes through the result of strict
[`matchFor(...)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#matchFor(java.util.List)). Its immutable
[`LocaleMatchResult`](https://javadoc.lokalized.com/com/lokalized/LocaleMatchResult.html) preserves the requested ranges,
locales considered, winning range and quality, match kind
([`LocaleMatchType`](https://javadoc.lokalized.com/com/lokalized/LocaleMatchType.html)), and an explicit unmatched state. If
every loaded locale is excluded with `q=0`, translation lookup can still use the configured fallback while the result
accurately reports that negotiation found no acceptable match. The simpler `localeSupplier(...bestMatchFor(...))` path
remains appropriate when an application only needs the selected locale.

## Localized Strings File Format

### Structure

* Each localized strings file must be UTF-8 encoded and named according to the appropriate IETF BCP 47 language tag, such as `en` or `zh-TW` (an optional `.json` suffix like `en.json` is also accepted; do not provide both for the same locale)
* A blank or BOM-only file is invalid; use `{}` for an intentionally empty localized strings file
* The file must contain a single toplevel JSON object
* The object's keys are the translation keys, e.g. `"I read {{bookCount}} books."`
* The value for a translation key can be a string (simple cases) or an object (complex cases)

With formalities out of the way, let's examine an example UK English (`en-GB`) localized strings file, which contains a single translation.  We can use the string form shorthand to concisely express our intent:

```json
{
  "I am going on vacation." : "I am going on holiday."
}
```

This is equivalent to the more verbose object form, which we don't need in this situation.

```json
{
  "I am going on vacation." : {
    "translation" : "I am going on holiday."
  }
}
```

In addition to `translation`, each object form supports 3 additional keys: `commentary`, `placeholders`, and `alternatives`.

All 4 are optional, with the stipulation that you must provide either a `translation` or at least one `alternatives` value.

### JSON Schema

A JSON Schema for localized strings files is packaged in the jar at `schema/lokalized-strings.schema.json` and is available at [src/main/resources/schema/lokalized-strings.schema.json](https://github.com/lokalized/lokalized-java/blob/master/src/main/resources/schema/lokalized-strings.schema.json).

The schema validates file structure, placeholder shapes, known language-form names, and alternatives. It does not parse alternative expression syntax; Lokalized validates expression syntax when strings are loaded. Completeness of locale-specific cardinality and ordinality maps is not enforced at load time; an incomplete file still loads, but Lokalized emits a warning when a cardinality- or ordinality-driven placeholder omits a language form its locale requires per CLDR (for example, a Russian file that omits `CARDINALITY_MANY`). Values that resolve to a missing form surface during resolution according to the configured failure handler.

Validation warnings are delivered to a [`LocalizedStringWarningHandler`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringWarningHandler.html), which each `LocalizedStringLoader.load*` method accepts as an optional argument:

```java
// Default: load successfully and emit no warning callbacks.
Map<Locale, Set<LocalizedString>> strings = LocalizedStringLoader.loadFromClasspath("strings");

// Receive structured warnings directly.
List<LocalizedStringWarning> warnings = new ArrayList<>();
LocalizedStringLoader.loadFromClasspath("strings", warning -> {
  warnings.add(warning);
});

// Fail fast: treat any incomplete file as a load error (useful in tests/CI).
LocalizedStringLoader.loadFromClasspath("strings", LocalizedStringWarningHandler.throwException());
```

Warnings are silently ignored when no handler is supplied. Each [`LocalizedStringWarning`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringWarning.html) exposes structured detail (`getType()`, `getSource()`, optional `getLocale()`, optional `getKey()`, optional `getPlaceholder()`, and `getMissingLanguageForms()`) alongside a human-readable `getMessage()`. Resource-level warnings such as an invalid classpath locale filename omit locale, key, and placeholder context.

### Commentary

This free-form field is used to supply context for the translator, such as how and where the phrase is used in the application.  It might also include documentation about the application-supplied placeholder values (names and types) so it's clear what data is available to perform the translation.

```json
{
  "I am going on vacation." : {
    "commentary" : "This is one of the options in the user's status update dropdown.",
    "translation" : "I am going on holiday."
  }
}
```

### Placeholders

A placeholder is any translation value enclosed in a pair of "mustaches" - `{{PLACEHOLDER_NAME_HERE}}`.

Placeholder names must start with a Unicode letter or underscore. Subsequent characters may be Unicode letters,
Unicode numbers, Unicode combining marks, underscores, or hyphens. Whitespace inside mustaches is not allowed, so
write `{{bookCount}}`, not `{{ bookCount }}`.

To render a literal placeholder instead of resolving it, escape the opening delimiter with a backslash. In JSON this means writing `\\{{name}}`, which renders as `{{name}}` and is not resolved against the placeholder context. You can also write `\\}}` for a literal closing delimiter, or `\\\\{{name}}` when you need a literal backslash immediately before a live placeholder.

Add as many as the translation needs, subject to the configured translation-node limit for localized strings files.

Placeholder values are initially specified by application code - they are the context that is passed in at string evaluation time.

When a reachable generated definition has the same name as a caller value, the generated value wins during output
interpolation. Prefer distinct input and generated-output names unless that override is intentional.

For right-to-left resolved locales, Lokalized wraps application-supplied placeholder values with Unicode First Strong Isolate (U+2068) and Pop Directional Isolate (U+2069) by default. This prevents left-to-right values such as product codes, user names, and numbers from reordering nearby punctuation in Arabic, Hebrew, and other RTL translations. Placeholder fragments defined by the localized strings file, such as plural word choices, are not isolated.

Suppose the Arabic translation for `Shipment` is `تم تجهيز {{code}}`. By default, the caller-supplied `code` value is isolated:

```java
String message = strings.get("Shipment", Map.of("code", "ACME-42"));
assertEquals("تم تجهيز \u2068ACME-42\u2069", message);
```

Disable this behavior with [`BidiIsolation`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html) for plain-text sinks that cannot accept Unicode bidi controls:

```java
TranslationOptions options = TranslationOptions.builder()
  .bidiIsolation(BidiIsolation.NONE)
  .build();

String message = strings.get("Shipment", Map.of("code", "ACME-42"), options);
assertEquals("تم تجهيز ACME-42", message);
```

The default [`BidiIsolation.RTL_LOCALES`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html#RTL_LOCALES) policy isolates caller values only when the resolved translation locale is
right-to-left. Use [`BidiIsolation.ALWAYS`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html#ALWAYS) when caller-supplied right-to-left text can also appear inside left-to-right
translations; already balanced isolate controls are preserved rather than nested again.

In the below example of an `en` localized strings file, the application code provides the `bookCount` value and the localized strings file introduces a `books` value to aid final translation.

```json
{
  "I read {{bookCount}} books." : {
    "translation" : "I read {{bookCount}} {{books}}.",    
    "placeholders" : {
      "books" : {
        "value" : "bookCount",
        "translations" : {
          "CARDINALITY_ONE" : "book",
          "CARDINALITY_OTHER" : "books"
        }
      }
    }
  }  
}
```

Each `placeholders` object key is the name of a generated placeholder - `books`, in this example. A definition has one
of three mutually exclusive shapes: `value` plus `translations`, cardinality `range` plus `translations`, or
`translation` with optional expression-selected `alternatives`. The corresponding programmatic types are the closed
[`LocalizedString.PlaceholderDefinition`](https://javadoc.lokalized.com/com/lokalized/LocalizedString.PlaceholderDefinition.html)
hierarchy, [`LocalizedString.LanguageFormTranslation`](https://javadoc.lokalized.com/com/lokalized/LocalizedString.LanguageFormTranslation.html),
[`LocalizedString.ExpressionTranslation`](https://javadoc.lokalized.com/com/lokalized/LocalizedString.ExpressionTranslation.html),
and its ordered [`LocalizedString.ExpressionAlternative`](https://javadoc.lokalized.com/com/lokalized/LocalizedString.ExpressionAlternative.html)
entries.

* `value` is the placeholder value to examine. It may be a [`Number`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Number.html), [`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html), [`Cardinality`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html), [`Ordinality`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html), [`Gender`](https://javadoc.lokalized.com/com/lokalized/Gender.html), [`GrammaticalCase`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html), [`Definiteness`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html), [`Classifier`](https://javadoc.lokalized.com/com/lokalized/Classifier.html), [`Formality`](https://javadoc.lokalized.com/com/lokalized/Formality.html), [`Clusivity`](https://javadoc.lokalized.com/com/lokalized/Clusivity.html), [`Animacy`](https://javadoc.lokalized.com/com/lokalized/Animacy.html), [`Phonetic`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html), or [`CharSequence`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/CharSequence.html) type. Lokalized converts [`Number`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Number.html) and [`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html) instances to the appropriate [`Cardinality`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html) or [`Ordinality`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html) according to the language's rules, accepts pre-resolved `Cardinality` and `Ordinality` values directly, and converts [`CharSequence`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/CharSequence.html) instances to [`Phonetic`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html) using your [`PhoneticResolver`](https://javadoc.lokalized.com/com/lokalized/PhoneticResolver.html) with the current locale. The same cardinality input forms are accepted for range endpoints.
* `translations` is a set of language rules against which to evaluate `value` and provide a translation

Here, the value of `bookCount` is evaluated against the specified cardinality rules and the result is placed into `books`.  For example, if application code passes in `1` for `bookCount`, this matches [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) and `book` is the value of the `books` placeholder.  If application code passes in a different value, [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) is matched and `books` is used. 

Supported values for `translations` are [`Cardinality`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html), [`Ordinality`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html), [`Gender`](https://javadoc.lokalized.com/com/lokalized/Gender.html), [`GrammaticalCase`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html), [`Definiteness`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html), [`Classifier`](https://javadoc.lokalized.com/com/lokalized/Classifier.html), [`Formality`](https://javadoc.lokalized.com/com/lokalized/Formality.html), [`Clusivity`](https://javadoc.lokalized.com/com/lokalized/Clusivity.html), [`Animacy`](https://javadoc.lokalized.com/com/lokalized/Animacy.html), and [`Phonetic`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html) types.

You may not mix language forms in the same `translations` object.  For example, it is illegal to specify both [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) and [`GENDER_MASCULINE`](https://javadoc.lokalized.com/com/lokalized/Gender.html#MASCULINE).

Placeholder rules are strict: if your application supplies or resolves a language-form value that is not present in `translations`, the lookup is treated as a resolution failure and your configured `TranslationFailureHandler` decides what happens.

Lokalized evaluates only placeholders defined by the localized strings file that are reachable from the selected translation.
A selected language-form value may itself reference application-supplied placeholders or other
placeholders defined by the localized strings file; those fragments are expanded recursively. Cycles, excessive nesting, and
interpolated output above the configured limit fail resolution clearly. The default is 262,144 UTF-16 code units,
and applications can opt up to the 1,048,576-code-unit hard ceiling with
[`TranslationRuntimeLimits`](https://javadoc.lokalized.com/com/lokalized/TranslationRuntimeLimits.html). Application-supplied
values remain opaque and are never reinterpreted as template syntax, even when a value contains text such as
`{{name}}`.

#### Expression-Selected Fragments

Use a generated fragment with `translation` and ordered `alternatives` when a small, reusable portion of a message
depends on an exact value, threshold, or compound business rule. `translation` is the required default. The first
matching alternative wins; later expressions are not evaluated. Omitting `alternatives` creates a message-scoped
constant or composed fragment, such as `"productName": { "translation": "Firefox" }`. Each alternative array element
contains exactly one expression-to-string member; nested localized-string objects are not permitted there. Template
members (`translation`, `alternatives`) cannot be mixed with language-form members (`value`, `range`, `translations`)
in one placeholder definition.

This example has three result-summary cases and an independent two-case timing axis. Factoring those decisions into
two fragments avoids six coordinated whole-message alternatives:

```json
{
  "Search completed." : {
    "translation" : "Found {{resultSummary}} {{timing}}.",
    "placeholders" : {
      "resultSummary" : {
        "translation" : "{{formattedResultCount}} {{resultNoun}}",
        "alternatives" : [
          {
            "resultCount == 0" : "no results"
          },
          {
            "resultCount >= resultLimit" : "at least {{formattedResultLimit}} results"
          }
        ]
      },
      "timing" : {
        "translation" : "in {{formattedDuration}}",
        "alternatives" : [
          {
            "elapsedMilliseconds < 1000" : "instantly"
          }
        ]
      },
      "resultNoun" : {
        "value" : "resultCount",
        "translations" : {
          "CARDINALITY_ONE" : "result",
          "CARDINALITY_OTHER" : "results"
        }
      }
    }
  }
}
```

The same localized strings file produces all six combinations. Raw numeric values drive expressions and cardinality; separately
formatted strings are used only for display:

```java
assertEquals("Found no results instantly.", strings.get("Search completed.", Map.of(
  "resultCount", Long.valueOf(0), "resultLimit", Long.valueOf(100),
  "elapsedMilliseconds", Long.valueOf(250), "formattedResultCount", "0",
  "formattedResultLimit", "100", "formattedDuration", "0.25 seconds")));

assertEquals("Found no results in 1.5 seconds.", strings.get("Search completed.", Map.of(
  "resultCount", Long.valueOf(0), "resultLimit", Long.valueOf(100),
  "elapsedMilliseconds", Long.valueOf(1_500), "formattedResultCount", "0",
  "formattedResultLimit", "100", "formattedDuration", "1.5 seconds")));

assertEquals("Found at least 100 results instantly.", strings.get("Search completed.", Map.of(
  "resultCount", Long.valueOf(100), "resultLimit", Long.valueOf(100),
  "elapsedMilliseconds", Long.valueOf(250), "formattedResultCount", "100",
  "formattedResultLimit", "100", "formattedDuration", "0.25 seconds")));

assertEquals("Found at least 100 results in 1.5 seconds.", strings.get("Search completed.", Map.of(
  "resultCount", Long.valueOf(100), "resultLimit", Long.valueOf(100),
  "elapsedMilliseconds", Long.valueOf(1_500), "formattedResultCount", "100",
  "formattedResultLimit", "100", "formattedDuration", "1.5 seconds")));

assertEquals("Found 2 results instantly.", strings.get("Search completed.", Map.of(
  "resultCount", Long.valueOf(2), "resultLimit", Long.valueOf(100),
  "elapsedMilliseconds", Long.valueOf(250), "formattedResultCount", "2",
  "formattedResultLimit", "100", "formattedDuration", "0.25 seconds")));

assertEquals("Found 2 results in 1.5 seconds.", strings.get("Search completed.", Map.of(
  "resultCount", Long.valueOf(2), "resultLimit", Long.valueOf(100),
  "elapsedMilliseconds", Long.valueOf(1_500), "formattedResultCount", "2",
  "formattedResultLimit", "100", "formattedDuration", "1.5 seconds")));
```

Expression-fragment predicates and whole-message predicates both read the same immutable snapshot of caller input and
use the locale of the localized strings file for the candidate locale. Generated values never become predicate operands. Numeric
ordering requires a [`Number`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Number.html) or
numeric [`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html); a formatted string such as
`"1,000"` is display text, not a numeric operand. More generally, expression [`String`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/String.html)
and other [`CharSequence`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/CharSequence.html)
operands are phonetic inputs: Lokalized resolves them through your
[`PhoneticResolver`](https://javadoc.lokalized.com/com/lokalized/PhoneticResolver.html) for comparison with
`PHONETIC_*` constants or explicit `Phonetic` values. They are not numeric values or general-purpose string literals,
and two raw `CharSequence` placeholders cannot be compared for textual equality. Missing, null, and incompatible
operands are resolution failures, not implicit non-matches.

Use language-form definitions for grammatical categories and expression fragments for exact, threshold, or compound
rules. The distinction matters: `count == 1` is an exact numeric test, while `count == CARDINALITY_ONE` is a
locale-sensitive grammatical classification. Russian classifies values such as 21 as `ONE`, while French and
Brazilian Portuguese can classify 0 as `ONE`. Keep a whole-message alternative when a decision changes word order or
agreement so broadly that a fragment boundary would be unsafe.

#### Placeholder Scope and Inheritance

Generated-placeholder definitions inherit down only the selected whole-message alternative path. Definitions from the
root and every selected intermediate branch remain visible at the terminal translation. A nearer child definition
replaces the complete same-named ancestor definition - language forms and expression alternatives are never merged - and
unselected siblings are invisible. Effective precedence is `nearest selected child > selected ancestor > caller
value` for output interpolation.

Selection is completed before generated placeholders resolve, so an inherited parent fragment is late-bound to any
dependency overridden by the selected child. Only definitions reachable from the selected template and selected
fragment results are resolved; shadowed definitions, unselected fragment dependencies, and unused definitions consume
no lookup-time expansion work.

There is one deliberate exception to generated-over-caller output precedence: every language-form `value`,
`range.start`, and `range.end` name always reads the raw caller input. A generated definition named `count` may supply
the rendered `{{count}}`, while another definition with `"value": "count"` still classifies the caller's raw `count`.
Use distinct input and output names, such as `count` and `countText`, to avoid surprising localized strings behavior. A generated value
cannot satisfy a missing selector input.

Expression-selected and language-form fragments share recursive dependency expansion, cycle detection, depth and
output limits, and bidi behavior. Translation-owned fragment text is not isolated; caller values interpolated inside
it follow the configured [`BidiIsolation`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html) policy. A
matched predicate whose fragment later fails does not fall through to a later predicate or the default. Predicate and
fragment interpolation errors are [`RESOLUTION_FAILURE`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#RESOLUTION_FAILURE)
events; because `translation` is mandatory, an expression-selected fragment cannot itself produce
[`NO_MATCHING_ALTERNATIVE`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#NO_MATCHING_ALTERNATIVE).

The placeholder structure is slightly different for cardinality ranges.  A `range` property is introduced and requires both a `start` and `end` value.  

```json
{
  "The meeting will be {{minHours}}-{{maxHours}} hours long." : {
    "translation" : "La réunion aura une durée de {{minHours}} à {{maxHours}} {{heures}}.",
    "placeholders" : {
      "heures" : {
        "range" : {
          "start" : "minHours",
          "end" : "maxHours"
        },
        "translations" : {
          "CARDINALITY_ONE" : "heure",
          "CARDINALITY_OTHER" : "heures"
        }
      }
    }
  }
}
```

Here, the cardinalities of `minHours` and `maxHours` are evaluated to determine the overall cardinality of the range, which is used to select the appropriate value in `translations`.

You are prohibited from supplying both `range` and `value` fields - use `range` only for cardinality ranges and `value` otherwise.

### Alternatives

You may specify bounded, parenthesized expressions in `alternatives` to fine-tune your translations. Each object in an `alternatives` array contains exactly one expression.
It's perfectly legal to have an alternative like this:
 
```text
gender == GENDER_MASCULINE && (bookCount > 10 || magazineCount > 20)
```

Standard boolean operator precedence applies: `&&` binds tighter than `||`.

Numeric literals and expressions are parsed and checked against hard ceilings when translations are loaded. Numeric
literals use the same precision and absolute-scale limits as
[`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html), so an exponent cannot defer
unbounded decimal materialization until lookup time. The configured runtime policy is applied when `Strings` compiles
the localized strings. Expressions are limited to 2,048 source characters, 256 tokens, and 32 nested groups by default. `Strings`
applications may configure these with
[`TranslationRuntimeLimits`](https://javadoc.lokalized.com/com/lokalized/TranslationRuntimeLimits.html), up to hard
ceilings of 4,096 characters, 512 tokens, and 64 nested groups.

Lokalized will automatically evaluate cardinality and ordinality for numbers or [`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html) if required by the expression. `PluralOperands` also expose their numeric value for ordinary numeric comparisons. For example, in English, if I were to supply `bookCount` of `50`, this expression would evaluate to `true`:
 
```text
bookCount == CARDINALITY_OTHER
``` 

...and so would this:

```text
bookCount == 50
``` 

Note that the supported comparison operators for cardinality, ordinality, gender, and phonetic forms are `==` and `!=`.  You cannot say `bookCount < CARDINALITY_FEW`, for example.

Alternative expression recursion is supported. That is, each value for `alternatives` can itself have `translation`, `commentary`, `placeholders`, and `alternatives`.  You can also use the simpler string-only form if no special translation functionality is needed.
  
Alternative evaluation follows these rules:

* At each level, expressions are evaluated according to their order in the list, halting at the first match
* Within a matched branch, nested alternatives are evaluated before that branch's default `translation`
* Once an expression matches, evaluation stays within that branch; an unmatched nested subtree does not fall through
  to a later sibling
* Placeholder definitions declared by the root and each selected branch are inherited by descendants; the nearest
  selected definition replaces a same-named ancestor definition as a complete unit
* Predicates read only the immutable caller-input snapshot. Generated placeholder values do not become operands
* If no expression matches and no default `translation` is present in any attempted candidate locale, failure handlers receive
  [`TranslationFailureReason.NO_MATCHING_ALTERNATIVE`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#NO_MATCHING_ALTERNATIVE)

A somewhat contrived example of multiple levels of recursion follows.  The first level of recursion uses a full object, the second uses the string shorthand.

```json
{
  "I read {{bookCount}} books." : {
    "translation" : "I read {{bookCount}} books.",    
    "alternatives" : [
      {
        "bookCount < 3" : {
          "translation" : "I only read a few books. {{bookCount}}, in fact!",
          "alternatives": [
            {
              "bookCount == 0" : "I'm ashamed to admit I didn't read anything."
            }
          ]
        }        
      }
    ]
  }  
}
```

Evaluation works as you might expect.

```java
// Deepest recursion
String message = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 0));
assertEquals("I'm ashamed to admit I didn't read anything.", message);

// 1 level deep recursion
message = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 1));
assertEquals("I only read a few books. 1, in fact!", message);

// Normal case
message = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 3));
assertEquals("I read 3 books.", message);
```

A grammar for alternative expressions follows.

```EBNF
EXPRESSION = OPERAND COMPARISON_OPERATOR OPERAND | "(" EXPRESSION ")" | EXPRESSION BOOLEAN_OPERATOR EXPRESSION ;
OPERAND = VARIABLE | LANGUAGE_FORM | NUMBER ;
LANGUAGE_FORM = CARDINALITY | ORDINALITY | GENDER | GRAMMATICAL_CASE | DEFINITENESS | CLASSIFIER | FORMALITY | CLUSIVITY | ANIMACY | PHONETIC ;
CARDINALITY = "CARDINALITY_ZERO" | "CARDINALITY_ONE" | "CARDINALITY_TWO" | "CARDINALITY_FEW" | "CARDINALITY_MANY" | "CARDINALITY_OTHER" ;
ORDINALITY = "ORDINALITY_ZERO" | "ORDINALITY_ONE" | "ORDINALITY_TWO" | "ORDINALITY_FEW" | "ORDINALITY_MANY" | "ORDINALITY_OTHER" ;
GENDER = "GENDER_MASCULINE" | "GENDER_FEMININE" | "GENDER_COMMON" | "GENDER_NEUTER" ;
GRAMMATICAL_CASE = "CASE_NOMINATIVE" | "CASE_ACCUSATIVE" | "CASE_GENITIVE" | "CASE_DATIVE"
                 | "CASE_INSTRUMENTAL" | "CASE_LOCATIVE" | "CASE_PREPOSITIONAL" | "CASE_VOCATIVE" | "CASE_ABLATIVE" ;
DEFINITENESS = "DEFINITENESS_DEFINITE" | "DEFINITENESS_INDEFINITE" | "DEFINITENESS_CONSTRUCT" ;
CLASSIFIER = "CLASSIFIER_GENERAL" | "CLASSIFIER_PERSON" | "CLASSIFIER_ANIMAL" | "CLASSIFIER_LONG_THIN"
           | "CLASSIFIER_FLAT" | "CLASSIFIER_BOUND" | "CLASSIFIER_MACHINE" | "CLASSIFIER_VEHICLE" ;
FORMALITY = "FORMALITY_CASUAL" | "FORMALITY_INFORMAL" | "FORMALITY_FORMAL" | "FORMALITY_HUMBLE" | "FORMALITY_HONORIFIC" ;
CLUSIVITY = "CLUSIVITY_INCLUSIVE" | "CLUSIVITY_EXCLUSIVE" ;
ANIMACY = "ANIMACY_ANIMATE" | "ANIMACY_INANIMATE" ;
PHONETIC = "PHONETIC_VOWEL" | "PHONETIC_CONSONANT"
         | "PHONETIC_H_SILENT" | "PHONETIC_H_ASPIRATED"
         | "PHONETIC_S_IMPURE" | "PHONETIC_Z" | "PHONETIC_GN" | "PHONETIC_PS" | "PHONETIC_PN" | "PHONETIC_X"
         | "PHONETIC_GLIDE_Y" | "PHONETIC_GLIDE_W"
         | "PHONETIC_STRESSED_A"
         | "PHONETIC_SOLAR" | "PHONETIC_LUNAR" 
         | "PHONETIC_OTHER" ;
NUMBER = [ SIGN ], ( DIGITS, [ ".", { DIGIT } ] | ".", DIGITS ), [ EXPONENT ] ;
EXPONENT = ( "e" | "E" ), [ SIGN ], DIGITS ;
SIGN = "+" | "-" ;
DIGITS = DIGIT, { DIGIT } ;
DIGIT = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;
VARIABLE = ( Unicode letter | "_" )
           { Unicode letter | Unicode number | Unicode combining mark | "_" | "-" } ;
BOOLEAN_OPERATOR = "&&" | "||" ;
COMPARISON_OPERATOR = "<" | ">" | "<=" | ">=" | "==" | "!=" ;
```

Expressions ignore ASCII space, horizontal tab, carriage return, line feed, and form feed between tokens. Other
Unicode whitespace and separator characters are rejected rather than silently skipped.

Built-in language-form constants are reserved in alternative expressions. A token like `CARDINALITY_ONE`, `GENDER_MASCULINE`, `CASE_DATIVE`, `DEFINITENESS_DEFINITE`, `CLASSIFIER_PERSON`, `FORMALITY_FORMAL`, `CLUSIVITY_INCLUSIVE`, `ANIMACY_ANIMATE`, or `PHONETIC_VOWEL` is parsed as a constant, not as a placeholder variable. Placeholder names may not use built-in constant names.

#### What Expressions Currently Support

* Evaluation of bounded "normal" infix expressions (can be nested/parenthesized)
* Comparison of supported language forms, phonetic forms, plural and ordinal forms, and literal numeric values against each other or user-supplied variables

#### What Expressions Do Not Currently Support

* The unary `!` operator
* String literals, Boolean literals, or explicit `null` operands
* Textual equality between two raw `CharSequence` placeholder values; compare phonetic input with `PHONETIC_*`
* Functions or expressions that return arbitrary values
* A cardinality range construct ([to be added in a future release](https://github.com/lokalized/lokalized-java/issues/16))

## Inspection

[`Strings`](https://javadoc.lokalized.com/com/lokalized/Strings.html) provides read-only inspection helpers for audit and tooling use:

```java
Strings strings = Strings.withFallbackLocale(FALLBACK_LOCALE)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .build();

Set<Locale> supportedLocales = strings.getSupportedLocales();
Set<String> englishKeys = strings.getKeysForLocale(Locale.forLanguageTag("en"));
Set<String> missingFrenchKeys =
  strings.getMissingKeys(Locale.forLanguageTag("en"), Locale.forLanguageTag("fr"));
```

`getKeysForLocale(Locale)` and `getMissingKeys(Locale sourceLocale, Locale targetLocale)` are intentionally strict: inspected locales must be supported, and unsupported locales throw `IllegalArgumentException`. Use `getSupportedLocales()` first when you need to probe availability.

## Keying Strategy

Ultimately, it is up to you and your team how best to name your localization keys.  Lokalized does not impose key naming constraints. 
  
There are two common approaches - natural language and contextual. Some benefits and drawbacks of each are listed below to help you make the best decision for your situation.
 
### Natural Language Keys

For example: `"I read {{bookCount}} books."`

#### Pros

* Any developer can create a key by writing a phrase in her native language - no need to coordinate with others or choose arbitrary names
* Placeholders are encoded directly in the key and serve as "automatic" documentation for translators
* There is always a sensible default fallback in the event that a translation is missing

#### Cons

* Context is lost; the same text on one screen might have a completely different meaning on another
* Not suited for large amounts of text, like a software licensing agreement
* Small changes to text require updating every localized strings file since keys are not "constant"

### Contextual Keys

For example: `"Checkout.Title"`, `"Checkout.Submit"`, and `"Checkout.Cancel"`.

```json
{
  "Checkout.Title" : {
    "commentary" : "Heading shown at the top of the checkout page.",
    "translation" : "Checkout"
  },
  "Checkout.Submit" : {
    "commentary" : "Primary button that submits the order.",
    "translation" : "Place order"
  },
  "Checkout.Cancel" : {
    "commentary" : "Secondary button that returns the user to the cart.",
    "translation" : "Return to cart"
  }
}
```

#### Pros

* It is possible to target a specific product surface or component, which enforces translation context
* Perfect for big chunks of text like legal disclaimers
* "Constant" keys means translations can change without affecting code

#### Cons

* You must come up with names for every key and cross-reference in your localized strings files
* Placeholders are not encoded in the key and must be communicated to translators through some other mechanism
* Requires diligent recordkeeping and inter-team communication ("are our iOS and Android apps using the same keys or are we duplicating effort?")
* There is no default language fallback if no translation is present; users will see your contextual key onscreen 

### Or - Mix Both!

It's possible to cherrypick and create a hybrid solution.  For example, you might use natural language keys in most cases but switch to contextual for legalese and other special cases.

## Comparing Localization Formats

Lokalized overlaps with [ICU MessageFormat](https://unicode-org.github.io/icu/userguide/format_parse/messages/), [MF2](https://messageformat.unicode.org/), [Fluent](https://projectfluent.org/), and [gettext](https://www.gnu.org/software/gettext/), but it is optimized for a narrower job: selecting natural-sounding translated phrases from structured JSON while application code supplies typed placeholder values.

For simple plural messages, ICU MessageFormat and MF2 are compact and widely supported:

```text
{bookCount, plural, one {I read # book.} other {I read # books.}}
```

The equivalent Lokalized file separates the sentence shape from the plural word choice:

```json
{
  "I read {{bookCount}} books." : {
    "translation" : "I read {{bookCount}} {{books}}.",
    "placeholders" : {
      "books" : {
        "value" : "bookCount",
        "translations" : {
          "CARDINALITY_ONE" : "book",
          "CARDINALITY_OTHER" : "books"
        }
      }
    }
  }
}
```

That extra structure lets translators keep individual grammatical choices close to the words they affect. Cardinality ranges use CLDR plural-range data, and phonetic choices such as English `a`/`an` or Spanish `el agua` use an application-supplied [`PhoneticResolver`](https://javadoc.lokalized.com/com/lokalized/PhoneticResolver.html). When an entire message depends on several values at once, use ordered `alternatives` or have application code choose a purpose-specific translation key.

Use the standard JDK formatters for dates, times, numbers, percentages, and currency. Consider ICU MessageFormat/MF2 or Fluent when your team already has those translation workflows and needs broad ecosystem tooling. Consider gettext when PO-file tooling and translator workflows are the main constraint. Lokalized is strongest when the hard part is runtime agreement across plural, gender, case, range, definiteness, classifier, formality, clusivity, animacy, or phonetic forms.

## Language Reference

Each language reference page includes CLDR 48.2 cardinality, cardinality range, and ordinality data, plus generated cookbooks that show localized strings file structure and Java lookup calls for that language's plural and ordinal categories. When verified day-phrase wording is unavailable, the page labels the gap and renders a neutral, translator-owned structural skeleton instead of guessing the language's word order. Locales with multiple ordinal categories also receive a runnable ordinal cookbook when CLDR supplies a complete set of localized minimal-pair patterns; incomplete source coverage is labeled rather than filled with guessed copy. The page identifies the exact Java [`Locale`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Locale.html) construction (either `Locale.ROOT` or [`Locale.forLanguageTag(...)`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Locale.html#forLanguageTag(java.lang.String))), the localized strings filename accepted by [`LocalizedStringLoader`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html), inherited rule sources, and the formatting-data provenance.

The reference includes every canonical CLDR plural-rule locale and a curated set of widely used region- and script-qualified application profiles. These profiles make inherited behavior explicit without suggesting that every valid IETF BCP 47 tag needs a separate page.

Exact tags for which CLDR supplies distinct rules, such as `pt-PT`, remain in the canonical CLDR table below. The curated table covers exact tags that inherit their plural rules from another entry.

Common inherited tags without dedicated pages, including `en-AU`, `en-CA`, `ja-JP`, and `ko-KR`, remain searchable on the website and point to the applicable plural-rule page. Their number formatting, unit wording, and application copy can still differ from the parent locale.

### Curated exact-tag profiles

| Locale profile | Tag | Plural rules from |
|---|---|---|
| [American English](https://lokalized.com/languages/en-US) | `en-US` | `en` |
| [Arabic (Egypt)](https://lokalized.com/languages/ar-EG) | `ar-EG` | `ar` |
| [Brazilian Portuguese](https://lokalized.com/languages/pt-BR) | `pt-BR` | `pt` |
| [British English](https://lokalized.com/languages/en-GB) | `en-GB` | `en` |
| [Canadian French](https://lokalized.com/languages/fr-CA) | `fr-CA` | `fr` |
| [Chinese (China)](https://lokalized.com/languages/zh-CN) | `zh-CN` | `zh` |
| [Chinese (Hong Kong SAR China)](https://lokalized.com/languages/zh-HK) | `zh-HK` | `zh` |
| [Chinese (Taiwan)](https://lokalized.com/languages/zh-TW) | `zh-TW` | `zh` |
| [English (India)](https://lokalized.com/languages/en-IN) | `en-IN` | `en` |
| [European Spanish](https://lokalized.com/languages/es-ES) | `es-ES` | `es` |
| [Latin American Spanish](https://lokalized.com/languages/es-419) | `es-419` | `es` |
| [Mexican Spanish](https://lokalized.com/languages/es-MX) | `es-MX` | `es` |
| [Serbian (Cyrillic)](https://lokalized.com/languages/sr-Cyrl) | `sr-Cyrl` | `sr` |
| [Serbian (Latin)](https://lokalized.com/languages/sr-Latn) | `sr-Latn` | `sr` |
| [Simplified Chinese](https://lokalized.com/languages/zh-Hans) | `zh-Hans` | `zh` |
| [Swiss High German](https://lokalized.com/languages/de-CH) | `de-CH` | `de` |
| [Traditional Chinese](https://lokalized.com/languages/zh-Hant) | `zh-Hant` | `zh` |

### Canonical CLDR plural-rule locales

| Language | Tag |
|---|---|
| [Afrikaans](https://lokalized.com/languages/af) | `af` |
| [Akan](https://lokalized.com/languages/ak) | `ak` |
| [Albanian (Shqip)](https://lokalized.com/languages/sq) | `sq` |
| [Amharic (አማርኛ)](https://lokalized.com/languages/am) | `am` |
| [Anii](https://lokalized.com/languages/blo) | `blo` |
| [Arabic (العربية)](https://lokalized.com/languages/ar) | `ar` |
| [Aragonese](https://lokalized.com/languages/an) | `an` |
| [Armenian (հայերէն)](https://lokalized.com/languages/hy) | `hy` |
| [Assamese (অসমীয়া)](https://lokalized.com/languages/as) | `as` |
| [Asturian (asturianu)](https://lokalized.com/languages/ast) | `ast` |
| [Asu (asa)](https://lokalized.com/languages/asa) | `asa` |
| [Azeri (Azerbaijani)](https://lokalized.com/languages/az) | `az` |
| [Baluchi](https://lokalized.com/languages/bal) | `bal` |
| [Bambara (Bamanankan)](https://lokalized.com/languages/bm) | `bm` |
| [Bangla (বাংলা)](https://lokalized.com/languages/bn) | `bn` |
| [Basque (euskara)](https://lokalized.com/languages/eu) | `eu` |
| [Belarusian (беларускі)](https://lokalized.com/languages/be) | `be` |
| [Bemba (ChiBemba)](https://lokalized.com/languages/bem) | `bem` |
| [Bena (Bəna)](https://lokalized.com/languages/bez) | `bez` |
| [Bhojpuri](https://lokalized.com/languages/bho) | `bho` |
| [Bodo (बर')](https://lokalized.com/languages/brx) | `brx` |
| [Bosnian (босански)](https://lokalized.com/languages/bs) | `bs` |
| [Breton (brezhoneg)](https://lokalized.com/languages/br) | `br` |
| [Bulgarian (български)](https://lokalized.com/languages/bg) | `bg` |
| [Burmese](https://lokalized.com/languages/my) | `my` |
| [Cantonese (廣東話)](https://lokalized.com/languages/yue) | `yue` |
| [Catalan (català)](https://lokalized.com/languages/ca) | `ca` |
| [Cebuano](https://lokalized.com/languages/ceb) | `ceb` |
| [Central Atlas Tamazight (Tamaziġt)](https://lokalized.com/languages/tzm) | `tzm` |
| [Central Kurdish (کوردیی ناوەندی)](https://lokalized.com/languages/ckb) | `ckb` |
| [Chechen (Нохчийн мотт)](https://lokalized.com/languages/ce) | `ce` |
| [Cherokee (ᏣᎳᎩ ᎦᏬᏂᎯᏍᏗ)](https://lokalized.com/languages/chr) | `chr` |
| [Chiga (Rukiga)](https://lokalized.com/languages/cgg) | `cgg` |
| [Chuvash](https://lokalized.com/languages/cv) | `cv` |
| [Colognian (Kölsch Platt)](https://lokalized.com/languages/ksh) | `ksh` |
| [Cornish (Kernowek)](https://lokalized.com/languages/kw) | `kw` |
| [Croatian (hrvatski)](https://lokalized.com/languages/hr) | `hr` |
| [Czech (čeština)](https://lokalized.com/languages/cs) | `cs` |
| [Danish (Dansk)](https://lokalized.com/languages/da) | `da` |
| [Divehi (dhivehi)](https://lokalized.com/languages/dv) | `dv` |
| [Dogri](https://lokalized.com/languages/doi) | `doi` |
| [Dutch (Nederlands)](https://lokalized.com/languages/nl) | `nl` |
| [Dzongkha (རྫོང་ཁ་)](https://lokalized.com/languages/dz) | `dz` |
| [English](https://lokalized.com/languages/en) | `en` |
| [Esperanto](https://lokalized.com/languages/eo) | `eo` |
| [Estonian (Eesti)](https://lokalized.com/languages/et) | `et` |
| [European Portuguese](https://lokalized.com/languages/pt-PT) | `pt-PT` |
| [Ewe (Èʋe)](https://lokalized.com/languages/ee) | `ee` |
| [Faroese (føroyskt)](https://lokalized.com/languages/fo) | `fo` |
| [Filipino (Wikang Filipino)](https://lokalized.com/languages/fil) | `fil` |
| [Finnish (suomi)](https://lokalized.com/languages/fi) | `fi` |
| [French (français)](https://lokalized.com/languages/fr) | `fr` |
| [Friulian (Furlan)](https://lokalized.com/languages/fur) | `fur` |
| [Fulah (Fulfulde)](https://lokalized.com/languages/ff) | `ff` |
| [Galician (galego)](https://lokalized.com/languages/gl) | `gl` |
| [Ganda (Oluganda)](https://lokalized.com/languages/lg) | `lg` |
| [Georgian (ქართული)](https://lokalized.com/languages/ka) | `ka` |
| [German (Deutsch)](https://lokalized.com/languages/de) | `de` |
| [Greek (Ελληνικά)](https://lokalized.com/languages/el) | `el` |
| [Greenlandic (Kalaallisut)](https://lokalized.com/languages/kl) | `kl` |
| [Gujarati (ગુજરાતી)](https://lokalized.com/languages/gu) | `gu` |
| [Gun (Fon gbè)](https://lokalized.com/languages/guw) | `guw` |
| [Hausa (هَرْشَن هَوْسَ‎)](https://lokalized.com/languages/ha) | `ha` |
| [Hawaiian (Ōlelo Hawaiʻi)](https://lokalized.com/languages/haw) | `haw` |
| [Hebrew (עברית)](https://lokalized.com/languages/he) | `he` |
| [Hindi (हिंदी)](https://lokalized.com/languages/hi) | `hi` |
| [Hmong Njua](https://lokalized.com/languages/hnj) | `hnj` |
| [Hungarian (magyar)](https://lokalized.com/languages/hu) | `hu` |
| [Icelandic (íslenska)](https://lokalized.com/languages/is) | `is` |
| [Ido](https://lokalized.com/languages/io) | `io` |
| [Igbo (Asụsụ Igbo)](https://lokalized.com/languages/ig) | `ig` |
| [Inari Sami (anarâškielâ)](https://lokalized.com/languages/smn) | `smn` |
| [Indonesian (Bahasa Indonesia)](https://lokalized.com/languages/id) | `id` |
| [Interlingua](https://lokalized.com/languages/ia) | `ia` |
| [Interlingue](https://lokalized.com/languages/ie) | `ie` |
| [Inuktitut (ᐃᓄᒃᑎᑐᑦ)](https://lokalized.com/languages/iu) | `iu` |
| [Irish (Gaeilge)](https://lokalized.com/languages/ga) | `ga` |
| [Italian (italiano)](https://lokalized.com/languages/it) | `it` |
| [Japanese (日本語)](https://lokalized.com/languages/ja) | `ja` |
| [Javanese (basa Jawa)](https://lokalized.com/languages/jv) | `jv` |
| [Jju (Kaje)](https://lokalized.com/languages/kaj) | `kaj` |
| [Kabuverdianu (Kriolu)](https://lokalized.com/languages/kea) | `kea` |
| [Kabyle (Taqbaylit)](https://lokalized.com/languages/kab) | `kab` |
| [Kako](https://lokalized.com/languages/kkj) | `kkj` |
| [Kannada (ಕನ್ನಡ)](https://lokalized.com/languages/kn) | `kn` |
| [Kashmiri (कॉशुर)](https://lokalized.com/languages/ks) | `ks` |
| [Kazakh (қазақ тілі)](https://lokalized.com/languages/kk) | `kk` |
| [Khmer (ភាសាខ្មែរ)](https://lokalized.com/languages/km) | `km` |
| [Kirghiz (кыргызча)](https://lokalized.com/languages/ky) | `ky` |
| [Konkani](https://lokalized.com/languages/kok) | `kok` |
| [Konkani (Latin)](https://lokalized.com/languages/kok-Latn) | `kok-Latn` |
| [Korean (한국어)](https://lokalized.com/languages/ko) | `ko` |
| [Koyraboro Senni (koyra-boro senn-i)](https://lokalized.com/languages/ses) | `ses` |
| [Kurdish (کوردی)](https://lokalized.com/languages/ku) | `ku` |
| [Ladin](https://lokalized.com/languages/lld) | `lld` |
| [Lakota (Lakȟótiyapi)](https://lokalized.com/languages/lkt) | `lkt` |
| [Langi (Kilaangi)](https://lokalized.com/languages/lag) | `lag` |
| [Lao (ພາສາລາວ)](https://lokalized.com/languages/lo) | `lo` |
| [Latvian (Latviešu)](https://lokalized.com/languages/lv) | `lv` |
| [Ligurian](https://lokalized.com/languages/lij) | `lij` |
| [Lingala (Lingála)](https://lokalized.com/languages/ln) | `ln` |
| [Lithuanian (Lietuvių)](https://lokalized.com/languages/lt) | `lt` |
| [Lojban (la .lojban.)](https://lokalized.com/languages/jbo) | `jbo` |
| [Lower Sorbian (Dolnoserbski)](https://lokalized.com/languages/dsb) | `dsb` |
| [Lule Sami (julevsámegiella)](https://lokalized.com/languages/smj) | `smj` |
| [Luxembourgish (Lëtzebuergesch)](https://lokalized.com/languages/lb) | `lb` |
| [Macedonian (македонски)](https://lokalized.com/languages/mk) | `mk` |
| [Machame](https://lokalized.com/languages/jmc) | `jmc` |
| [Makonde (Chi(ni)makonde)](https://lokalized.com/languages/kde) | `kde` |
| [Malagasy (Fiteny Malagasy)](https://lokalized.com/languages/mg) | `mg` |
| [Malay (بهاس ملايو‎)](https://lokalized.com/languages/ms) | `ms` |
| [Malayalam (മലയാളം)](https://lokalized.com/languages/ml) | `ml` |
| [Maltese (Malti)](https://lokalized.com/languages/mt) | `mt` |
| [Mandarin Chinese (中文)](https://lokalized.com/languages/zh) | `zh` |
| [Manx (Gaelg)](https://lokalized.com/languages/gv) | `gv` |
| [Marathi (मराठी)](https://lokalized.com/languages/mr) | `mr` |
| [Masai (ɔl Maa)](https://lokalized.com/languages/mas) | `mas` |
| [Metaʼ](https://lokalized.com/languages/mgo) | `mgo` |
| [Mongolian (монгол хэл)](https://lokalized.com/languages/mn) | `mn` |
| [N’Ko](https://lokalized.com/languages/nqo) | `nqo` |
| [Nahuatl (Nāhuatl)](https://lokalized.com/languages/nah) | `nah` |
| [Najdi Arabic (اللهجة النجدية)](https://lokalized.com/languages/ars) | `ars` |
| [Nama (Khoekhoegowab)](https://lokalized.com/languages/naq) | `naq` |
| [Nepali (नेपाली भाषा)](https://lokalized.com/languages/ne) | `ne` |
| [Ngiemboon (Ngyɛmbɔɔŋ)](https://lokalized.com/languages/nnh) | `nnh` |
| [Ngomba (Nda’a)](https://lokalized.com/languages/jgo) | `jgo` |
| [Nigerian Pidgin](https://lokalized.com/languages/pcm) | `pcm` |
| [North Ndebele (isiNdebele)](https://lokalized.com/languages/nd) | `nd` |
| [Northern Sami (davvisámegiella)](https://lokalized.com/languages/se) | `se` |
| [Northern Sotho (Sesotho sa Leboa)](https://lokalized.com/languages/nso) | `nso` |
| [Norwegian (norsk)](https://lokalized.com/languages/no) | `no` |
| [Norwegian Bokmål (bokmål)](https://lokalized.com/languages/nb) | `nb` |
| [Norwegian Nynorsk (nynorsk)](https://lokalized.com/languages/nn) | `nn` |
| [Nyanja (Chinyanja)](https://lokalized.com/languages/ny) | `ny` |
| [Nyankole (Runyankore)](https://lokalized.com/languages/nyn) | `nyn` |
| [Odia (ଓଡ଼ିଆ)](https://lokalized.com/languages/or) | `or` |
| [Oromo (Afaan Oromoo)](https://lokalized.com/languages/om) | `om` |
| [Osage](https://lokalized.com/languages/osa) | `osa` |
| [Ossetian (Ирон æвзаг)](https://lokalized.com/languages/os) | `os` |
| [Papiamento (Papiamentu)](https://lokalized.com/languages/pap) | `pap` |
| [Persian (فارسی)](https://lokalized.com/languages/fa) | `fa` |
| [Polish (polski)](https://lokalized.com/languages/pl) | `pl` |
| [Portuguese (português)](https://lokalized.com/languages/pt) | `pt` |
| [Prussian (Prūsiskan)](https://lokalized.com/languages/prg) | `prg` |
| [Punjabi (پنجابی)](https://lokalized.com/languages/pa) | `pa` |
| [Pushto (پښتو)](https://lokalized.com/languages/ps) | `ps` |
| [Romanian (română)](https://lokalized.com/languages/ro) | `ro` |
| [Romansh (rumàntsch)](https://lokalized.com/languages/rm) | `rm` |
| [Rombo (Kirombo)](https://lokalized.com/languages/rof) | `rof` |
| [Russian (русский)](https://lokalized.com/languages/ru) | `ru` |
| [Rwa (West Chaga)](https://lokalized.com/languages/rwk) | `rwk` |
| [Saho (ሳሆኛ)](https://lokalized.com/languages/ssy) | `ssy` |
| [Sakha (Саха тыла)](https://lokalized.com/languages/sah) | `sah` |
| [Samburu (Sampur)](https://lokalized.com/languages/saq) | `saq` |
| [Sami (Saami)](https://lokalized.com/languages/smi) | `smi` |
| [Samogitian](https://lokalized.com/languages/sgs) | `sgs` |
| [Sango (yângâ tî sängö)](https://lokalized.com/languages/sg) | `sg` |
| [Santali](https://lokalized.com/languages/sat) | `sat` |
| [Sardinian](https://lokalized.com/languages/sc) | `sc` |
| [Scottish Gaelic (Gàidhlig)](https://lokalized.com/languages/gd) | `gd` |
| [Sena (xisena)](https://lokalized.com/languages/seh) | `seh` |
| [Serbian (Српски)](https://lokalized.com/languages/sr) | `sr` |
| [Shambala (kishambaa)](https://lokalized.com/languages/ksb) | `ksb` |
| [Shona (chiShona)](https://lokalized.com/languages/sn) | `sn` |
| [Sichuan Yi (ꆈꌠ꒿)](https://lokalized.com/languages/ii) | `ii` |
| [Sicilian](https://lokalized.com/languages/scn) | `scn` |
| [Sindhi](https://lokalized.com/languages/sd) | `sd` |
| [Sinhalese (සිංහල)](https://lokalized.com/languages/si) | `si` |
| [Skolt Sami (sääʹmǩiõll)](https://lokalized.com/languages/sms) | `sms` |
| [Slovak (Slovenčina)](https://lokalized.com/languages/sk) | `sk` |
| [Slovenian (Slovenščina)](https://lokalized.com/languages/sl) | `sl` |
| [Soga (Lusoga)](https://lokalized.com/languages/xog) | `xog` |
| [Somali (اف سومالى‎)](https://lokalized.com/languages/so) | `so` |
| [South Ndebele (isiNdebele)](https://lokalized.com/languages/nr) | `nr` |
| [Southern Kurdish (کوردی خوارگ)](https://lokalized.com/languages/sdh) | `sdh` |
| [Southern Sami (Åarjelsaemien gïele)](https://lokalized.com/languages/sma) | `sma` |
| [Southern Sotho (Sesotho)](https://lokalized.com/languages/st) | `st` |
| [Spanish (español)](https://lokalized.com/languages/es) | `es` |
| [Sundanese](https://lokalized.com/languages/su) | `su` |
| [Swahili (Kiswahili)](https://lokalized.com/languages/sw) | `sw` |
| [Swampy Cree](https://lokalized.com/languages/csw) | `csw` |
| [Swati (SiSwati)](https://lokalized.com/languages/ss) | `ss` |
| [Swedish (svenska)](https://lokalized.com/languages/sv) | `sv` |
| [Swiss German (Schwiizerdütsch)](https://lokalized.com/languages/gsw) | `gsw` |
| [Syriac (Leššānā Suryāyā)](https://lokalized.com/languages/syr) | `syr` |
| [Tachelhit (Tašlḥiyt)](https://lokalized.com/languages/shi) | `shi` |
| [Tamil (Tamiḻ)](https://lokalized.com/languages/ta) | `ta` |
| [Telugu (తెలుగు)](https://lokalized.com/languages/te) | `te` |
| [Teso (Ateso)](https://lokalized.com/languages/teo) | `teo` |
| [Thai (ไทย)](https://lokalized.com/languages/th) | `th` |
| [Tibetan (བོད་སྐད་)](https://lokalized.com/languages/bo) | `bo` |
| [Tigre (ትግራይት)](https://lokalized.com/languages/tig) | `tig` |
| [Tigrinya (ትግርኛ)](https://lokalized.com/languages/ti) | `ti` |
| [Tok Pisin](https://lokalized.com/languages/tpi) | `tpi` |
| [Tongan (lea faka-Tonga)](https://lokalized.com/languages/to) | `to` |
| [Tsonga (Xitsonga)](https://lokalized.com/languages/ts) | `ts` |
| [Tswana (Setswana)](https://lokalized.com/languages/tn) | `tn` |
| [Turkish (Türkçe)](https://lokalized.com/languages/tr) | `tr` |
| [Turkmen (Türkmençe)](https://lokalized.com/languages/tk) | `tk` |
| [Tyap (Katab)](https://lokalized.com/languages/kcg) | `kcg` |
| [Uighur (ئۇيغۇر تىلى)](https://lokalized.com/languages/ug) | `ug` |
| [Ukrainian (українська)](https://lokalized.com/languages/uk) | `uk` |
| [Undetermined (CLDR root)](https://lokalized.com/languages/root) | `und` |
| [Upper Sorbian (hornjoserbšćina)](https://lokalized.com/languages/hsb) | `hsb` |
| [Urdu (اُردُو)](https://lokalized.com/languages/ur) | `ur` |
| [Uzbek (ўзбек тили)](https://lokalized.com/languages/uz) | `uz` |
| [Venda (Tshivenḓa)](https://lokalized.com/languages/ve) | `ve` |
| [Venetian](https://lokalized.com/languages/vec) | `vec` |
| [Vietnamese (Tiếng Việt)](https://lokalized.com/languages/vi) | `vi` |
| [Volapük](https://lokalized.com/languages/vo) | `vo` |
| [Vunjo (Wuunjo)](https://lokalized.com/languages/vun) | `vun` |
| [Walloon (Walon)](https://lokalized.com/languages/wa) | `wa` |
| [Walser (Walscher)](https://lokalized.com/languages/wae) | `wae` |
| [Welsh (Cymraeg)](https://lokalized.com/languages/cy) | `cy` |
| [Western Frisian (Frysk)](https://lokalized.com/languages/fy) | `fy` |
| [Wolof](https://lokalized.com/languages/wo) | `wo` |
| [Xhosa (isiXhosa)](https://lokalized.com/languages/xh) | `xh` |
| [Yiddish (יידיש)](https://lokalized.com/languages/yi) | `yi` |
| [Yoruba (Èdè Yorùbá)](https://lokalized.com/languages/yo) | `yo` |
| [Zulu (isiZulu)](https://lokalized.com/languages/zu) | `zu` |

## About

Lokalized was created by [Mark Allen](https://www.revetkn.com) and sponsored by [Transmogrify LLC](https://www.xmog.com) and [Revetware LLC](https://www.revetware.com).
