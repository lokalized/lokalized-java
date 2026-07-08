<a href="https://www.lokalized.com">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://cdn.lokalized.com/lokalized-gh-logo-dark-v5.png">
        <img alt="Lokalized" src="https://cdn.lokalized.com/lokalized-gh-logo-light-v5.png" width="300" height="93">
    </picture>
</a>

Lokalized facilitates natural-sounding software translations on the JVM.  Proudly powering production systems since 2017.

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
String translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 0));
assertEquals("I didn't read any books.", translation);
```

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

## Why Lokalized?

* **As a developer**, it is unrealistic to embed per-locale translation rules in code for every text string
* **As a translator**, sufficient context and the power of an expression language are required to provide the best translations possible
* **As a manager**, it is preferable to have a single translation specification that works on the backend, web frontend, and native mobile apps

Perhaps most importantly, the Lokalized placeholder system and expression language allow you to support edge cases that are critical to natural-sounding translations - this can be difficult to achieve using traditional solutions. 

## Getting Started

We'll start with hands-on examples to illustrate key features.

### 1. Create Localized Strings Files

Filenames must conform to the IETF BCP 47 language tag format, optionally suffixed with `.json`.

Here is a US English (`en-US`) localized strings file which includes a single localization:

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

### 2. Create a Strings Instance
   
```java
// Your "native" fallback strings file, used in case no specific locale match is found.
final Locale FALLBACK_LOCALE = Locale.forLanguageTag("en-US");

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
    // Lokalized gives you a matcher, which knows the most appropriate translation file to use.
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
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .translationFailureHandler(TranslationFailureHandler.throwException())
  .build();
```

You can also provide your own handler:

```java
// Custom telemetry for failures
Strings strings = Strings.withFallbackLocale(FALLBACK_LOCALE)
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my-directory")))
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .translationFailureHandler((failure) -> {
    exampleMetrics.increment("lokalized.translation.failure");
    return TranslationFailureResponse.returnString("Translation unavailable");
  })
  .build();
```

Lokalized [`Strings`](https://javadoc.lokalized.com/com/lokalized/Strings.html) instances are immutable and safe to share. If your application needs to reload strings files, rebuild a new instance and atomically swap the shared [`AtomicReference`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html):

```java
// Threadsafe reloading in your application via atomic swaps
final class LocalizedStrings {
  private static final Locale FALLBACK_LOCALE = Locale.forLanguageTag("en-US");
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
// That is, it understands that 3 means CARDINALITY_OTHER ("books") in English
String translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 3));
assertEquals("I read 3 books.", translation);

// 1 means CARDINALITY_ONE ("book") in English
translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 1));
assertEquals("I read 1 book.", translation);

// A special alternative rule is applied when bookCount == 0
translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 0));
assertEquals("I didn't read any books.", translation);
```

#### Formatting Placeholder Values

Lokalized selects translations and interpolates placeholder values, but it does not format dates, times, numbers, percentages, or currency values. Use JDK formatters such as [`NumberFormat`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/text/NumberFormat.html) and [`DateTimeFormatter`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/time/format/DateTimeFormatter.html) for display values before passing them to Lokalized:

```java
// Let the JDK do the formatting lift
Locale locale = Locale.forLanguageTag("fr-FR");
NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);

String translation = strings.get("Your balance is {{balance}} and is due on {{dueDate}}.", Map.of(
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

String translation = strings.get("You have {{formattedCount}} items.", Map.of(
  "count", count,
  "formattedCount", NumberFormat.getIntegerInstance(locale).format(count)
));
```

#### 4. Ensure Determinism via Tiebreakers

Suppose you have two translation files for Portuguese - Brazilian (`pt-BR`) and European (`pt-PT`).

A user who prefers only Angolan Portuguese (`pt-AO`) as defined by their `Accept-Language` HTTP request header then accesses your webapp.

Lokalized needs to know how to consistently "break the tie" to provide the Angolan user with a `pt` translation.

To that end, Lokalized will require that you specify `tiebreakerLocalesByLanguageCode` if it detects that you have more than one translation file per ISO 639 language code.

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

[`bestMatchFor(Locale)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchFor(java.util.Locale)) and [`bestMatchFor(List<LanguageRange>)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchFor(java.util.List)) match requested locale preferences against the locales loaded from your strings files. Matching is deterministic and follows these broad rules:

* Exact strings-file locale matches win first, including CLDR-canonical equivalents for deprecated or legacy language tags
* CLDR parent locales are considered before looser language-only matches. For example, `en-AU` can prefer a configured `en-001` file before `en`
* Matching is script-aware when CLDR likely-subtag data can infer a script. For example, `zh-TW` can match `zh-Hant`, and `sr-Latn` is distinct from `sr-Cyrl`
* The Norwegian macrolanguage tag `no` and Norwegian Bokmål tag `nb` bridge to each other as a compatibility fallback; exact files still win first
* If multiple supported files share the same language and no exact, parent, or script-aware match resolves the request, `tiebreakerLocalesByLanguageCode` controls which locale wins
* `Locale.ROOT`, `und`, wildcard-only ranges, empty preference lists, and unmatched requests resolve to the configured fallback locale

## Loading Localized Strings

Most applications load strings files from the filesystem during development and from the classpath in packaged deployments using [`LocalizedStringLoader`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html).

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

Classpath package names use slash-separated resource paths such as `strings` or `com/example/strings`. [`loadFromClasspath(String)`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html#loadFromClasspath(java.lang.String)) first uses the current thread context classloader when one is available, then falls back to Lokalized's own classloader. Use [`loadFromClasspath(ClassLoader, String)`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoader.html#loadFromClasspath(java.lang.ClassLoader,java.lang.String)) for containers, plugin systems, test harnesses, and other environments where the desired resources are visible through a specific classloader.

Filesystem and classpath loading both scan only the specified directory or package; child directories and child packages are not scanned recursively.

## Per-Invocation Options

The `localeSupplier` configured on [`Strings.Builder`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html) is convenient for web requests and other request-scoped contexts. For async jobs, batch work, tests, administrative tooling, or alternate output sinks, use [`TranslationOptions`](https://javadoc.lokalized.com/com/lokalized/TranslationOptions.html) to override lookup behavior for a single invocation:

```java
String translation = strings.get(
  "I read {{bookCount}} books.",
  Map.of("bookCount", 1),
  TranslationOptions.forLocale(Locale.forLanguageTag("fr-CA"))
);
```

Per-invocation options can supply a locale, language ranges, bidi isolation behavior, or a translation failure handler. Locale and language-range options bypass the configured `localeSupplier` for that call. Lokalized still applies the same matching, tiebreakers, and fallback behavior using the locale preference you supplied.

## A More Complex Example

Lokalized's strength is handling phrases that must be rewritten in different ways according to language rules. Suppose we introduce gender alongside plural forms.  In English, a noun's gender usually does not alter other components of a phrase.  But in Spanish it does.

This English statement has 4 variants:

* `He was one of the X best baseball players.`
* `She was one of the X best baseball players.`
* `He was the best baseball player.`
* `She was the best baseball player.`

In Spanish, we have the same number of variants (in a language like Russian or Arabic there would be more!)
But notice how the statements must change to match gender - `uno` becomes `una`, `jugadores` becomes `jugadoras`, etc.

* `Fue uno de los X mejores jugadores de béisbol.`
* `Fue una de las X mejores jugadoras de béisbol.`
* `Él era el mejor jugador de béisbol.`
* `Ella era la mejor jugadora de béisbol.`

### English Translation File

English is a little simpler than Spanish because gender only affects the `He` or `She` component of the sentence. 

```json
{
  "{{heOrShe}} was one of the {{groupSize}} best baseball players." : {
    "translation" : "{{heOrShe}} was one of the {{groupSize}} best baseball players.",
    "placeholders" : {
      "heOrShe" : {
        "value" : "heOrShe",
        "translations" : {
          "GENDER_MASCULINE" : "He",
          "GENDER_FEMININE" : "She"
        }
      }
    },
    "alternatives" : [
      {
        "heOrShe == GENDER_MASCULINE && groupSize <= 1" : "He was the best baseball player."        
      },
      {
        "heOrShe == GENDER_FEMININE && groupSize <= 1" : "She was the best baseball player."        
      }
    ]
  }
}
```

### Spanish Translation File

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
translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.MASCULINE,
    "groupSize", 10
  ));

assertEquals("He was one of the 10 best baseball players.", translation);

// Alternative expression triggered
translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.MASCULINE,
    "groupSize", 1
  ));

assertEquals("He was the best baseball player.", translation);

// ...now, here's what a Mexican Spanish (`es-MX`) user might see: 
translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  Map.of(
    "heOrShe", Gender.FEMININE,
    "groupSize", 3
  ));

// Note that the correct feminine forms were applied
assertEquals("Fue una de las 3 mejores jugadoras de béisbol.", translation);
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
          }
        ]
      }
    }
  ]
}
```

## Cardinality Ranges

When expressing a range of values (`1-3 meters`, `2.5-3.5 hours`), the cardinality of the range is determined by applying per-language rules to its start and end cardinalities.
  
In English we don't think about this - all ranges are of the form [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) - but many other languages have range-specific forms.

### French Translation File

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

### English Translation File

All English range forms evaluate to [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) so the file can be kept simple.


```json
{
  "The meeting will be {{minHours}}-{{maxHours}} hours long." : "The meeting will be {{minHours}}-{{maxHours}} hours long."
}
```

### Cardinality Ranges, Exercised

```java
// French CARDINALITY_OTHER case 
String translation = strings.get("The meeting will be {{minHours}}-{{maxHours}} hours long.",
  Map.of(
    "minHours", 1,
    "maxHours", 3
  ));

assertEquals("La réunion aura une durée de 1 à 3 heures.", translation);

// French CARDINALITY_ONE case
translation = strings.get("The meeting will be {{minHours}}-{{maxHours}} hours long.",
  Map.of(
    "minHours", 0,
    "maxHours", 1
  ));

assertEquals("La réunion aura une durée de 0 à 1 heure.", translation);
```

## Ordinal Forms

Many languages have special forms called _ordinals_ to express a "ranking" in a sequence of numbers.  For example, in English we might say
 
* `Take the 1st left after the intersection`
* `She is my 2nd cousin`
* `I finished the race in 3rd place`

Let's look at an example related to birthdays.

### English Translation File

English has 4 ordinals.

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

### Spanish Translation File

Spanish doesn't have ordinals, so we can disregard them.  But we do have a few special cases - a first birthday and a quinceañera for girls.

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
translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.MASCULINE,
    "year", 18
  ));

assertEquals("His 18th birthday party is next week.", translation);

// The ORDINALITY_ONE rule is applied to any of the "one" numbers (1, 11, 21, ...) in English
translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.FEMININE,
    "year", 21
  ));

assertEquals("Her 21st birthday party is next week.", translation);

// Spanish - normal case
translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.MASCULINE,
    "year", 18
  ));

assertEquals("Su fiesta de cumpleaños número 18 es la próxima semana.", translation);

// Spanish - special case for first birthday
translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "year", 1
  ));

assertEquals("Su primera fiesta de cumpleaños es la próxima semana.", translation);

// Spanish - special case for a girl's 15th birthday
translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  Map.of(
    "hisOrHer", Gender.FEMININE,
    "year", 15
  ));

assertEquals("Su quinceañera es la próxima semana.", translation);
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

Lokalized supports these values according to [CLDR rules](http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html):

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

Similar to plural cardinality, ordinal rules very widely across languages.

Lokalized supports these values according to [CLDR rules](http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html):

* [`ORDINALITY_ZERO`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ZERO)
* [`ORDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ONE)
* [`ORDINALITY_TWO`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#TWO)
* [`ORDINALITY_FEW`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#FEW)
* [`ORDINALITY_MANY`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#MANY)
* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER)

Again, like cardinal values, ordinals do not necessarily map to the named number. For example, [`ORDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ONE) might apply to any number that ends in `1`.

#### Spanish

* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER): Matches everything (this language has no ordinal form)

#### English

* [`ORDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#ONE): Matches 1, 21, 31, ... (e.g. `1st prize`)
* [`ORDINALITY_TWO`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#TWO): Matches 2, 22, 32, ... (e.g. `22nd prize`)
* [`ORDINALITY_FEW`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#FEW): Matches 3, 23, 33, ... (e.g. `33rd prize`)
* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER): Everything else (e.g. `12th prize`)

#### Italian

* [`ORDINALITY_MANY`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#MANY): Matches 8, 11, 80, 800 (e.g. `Prendi l'8° a destra`)
* [`ORDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#OTHER): Everything else (e.g. `	Prendi la 7° a destra`)

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

## CLDR Data

Lokalized's cardinality, ordinality, cardinality-range, locale matching, and locale-tag validation behavior is generated from pinned Unicode CLDR 48.2 XML data.

Pinned CLDR resources live under [src/test/resources/cldr/48.2](https://github.com/lokalized/lokalized-java/tree/master/src/test/resources/cldr/48.2). That directory records the upstream source URLs, SHA-256 checksums, license note, refresh commands, and generator command. Generated runtime data is checked in under `src/main/java`, and generated CLDR conformance fixtures are checked in under `src/test/java`.

After refreshing CLDR data, regenerate the checked-in sources and run the conformance tests before committing:

```shell
javac -d target/cldr-generator src/build/java/com/lokalized/cldr/CldrDataGenerator.java
java -cp target/cldr-generator com.lokalized.cldr.CldrDataGenerator
mvn -q test
```

The normal Maven build also compiles the generator sources as test sources, so `mvn verify` catches generator compile failures without packaging the generator in the runtime jar.

## Translation Failure Handling

When a lookup cannot be resolved, Lokalized asks the configured [`TranslationFailureHandler`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html) what to do. The default handler is [`TranslationFailureHandler.returnKey()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#returnKey()), which returns the lookup key with any caller-supplied placeholders interpolated into it.

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.US))
  .translationFailureHandler(TranslationFailureHandler.throwException())
  .build();
```

Built-in handler factories are:

* [`TranslationFailureHandler.returnKey()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#returnKey()) - returns the key with supplied placeholders interpolated
* [`TranslationFailureHandler.throwException()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#throwException()) - throws for missing translations and rethrows runtime resolution failures
* [`TranslationFailureHandler.logAndReturnKey(logger)`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#logAndReturnKey(java.util.logging.Logger)) - logs the failure without placeholder values, then returns the interpolated key

The fail-soft handlers, [`returnKey()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#returnKey()) and [`logAndReturnKey(logger)`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#logAndReturnKey(java.util.logging.Logger)), also handle runtime resolution failures by returning the interpolated key. Use [`throwException()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureHandler.html#throwException()) or a custom handler that throws on [`TranslationFailureReason.RESOLUTION_FAILURE`](https://javadoc.lokalized.com/com/lokalized/TranslationFailureReason.html#RESOLUTION_FAILURE) in development and test environments if you want broken placeholder rules, expressions, or custom resolvers to surface immediately.

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

[`TranslationFailure`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html) exposes the key, requested locale, candidate locales, caller-supplied placeholders, failure reason, and optional runtime cause. Placeholder values can contain user data, so avoid logging [`failure.getPlaceholders()`](https://javadoc.lokalized.com/com/lokalized/TranslationFailure.html#getPlaceholders()) unless your application has explicitly approved that.

## Localized Strings File Format

### Structure

* Each strings file must be UTF-8 encoded and named according to the appropriate IETF BCP 47 language tag, such as `en` or `zh-TW` (an optional `.json` suffix like `en.json` is also accepted; do not provide both for the same locale)
* The file must contain a single toplevel JSON object
* The object's keys are the translation keys, e.g. `"I read {{bookCount}} books."`
* The value for a translation key can be a string (simple cases) or an object (complex cases)

With formalities out of the way, let's examine an example UK English (`en-GB`) strings file, which contains a single translation.  We can use the string form shorthand to concisely express our intent:

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

In addition to `translation`, each object form supports 4 additional keys: `commentary`, `placeholderMetadata`, `placeholders`, and `alternatives`.

All 5 are optional, with the stipulation that you must provide either a `translation` or at least one `alternatives` value.

### JSON Schema

A JSON Schema for Lokalized strings files is packaged in the jar at `schema/lokalized-strings.schema.json` and is available at [src/main/resources/schema/lokalized-strings.schema.json](https://github.com/lokalized/lokalized-java/blob/master/src/main/resources/schema/lokalized-strings.schema.json).

The schema validates file structure, placeholder shapes, known language-form names, placeholder metadata, and alternatives. It does not parse alternative expression syntax or validate locale-specific plural completeness; those checks are performed by Lokalized when strings are loaded.

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

Placeholder names must start with a Unicode letter or underscore. Subsequent characters may be Unicode letters, Unicode digits, Unicode combining marks, underscores, or hyphens. Whitespace inside mustaches is not allowed, so write `{{bookCount}}`, not `{{ bookCount }}`.

To render a literal placeholder instead of resolving it, escape the opening delimiter with a backslash. In JSON this means writing `\\{{name}}`, which renders as `{{name}}` and is not resolved against the placeholder context. You can also write `\\}}` for a literal closing delimiter, or `\\\\{{name}}` when you need a literal backslash immediately before a live placeholder.

You are free to add as many as you like to support your translation.

Placeholder values are initially specified by application code - they are the context that is passed in at string evaluation time.

Your translation file may override passed-in placeholders if desired, but that is an uncommon use case.

For right-to-left resolved locales, Lokalized wraps application-supplied placeholder values with Unicode First Strong Isolate (U+2068) and Pop Directional Isolate (U+2069) by default. This prevents left-to-right values such as product codes, user names, and numbers from reordering nearby punctuation in Arabic, Hebrew, and other RTL translations. Translation-file-defined placeholder fragments, such as plural word choices, are not isolated.

Suppose the Arabic translation for `Shipment` is `تم تجهيز {{code}}`. By default, the caller-supplied `code` value is isolated:

```java
String translation = strings.get("Shipment", Map.of("code", "ACME-42"));
assertEquals("تم تجهيز \u2068ACME-42\u2069", translation);
```

Disable this behavior with [`BidiIsolation`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html) for plain-text sinks that cannot accept Unicode bidi controls:

```java
TranslationOptions options = TranslationOptions.builder()
  .bidiIsolation(BidiIsolation.NONE)
  .build();

String translation = strings.get("Shipment", Map.of("code", "ACME-42"), options);
assertEquals("تم تجهيز ACME-42", translation);
```

In the below example of an `en` strings file, the application code provides the `bookCount` value and the translation file introduces a `books` value to aid final translation.

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

Each `placeholders` object key is the name of the placeholder - `books`, in this example - and the value is an object.

Lokalized supports 2 placeholder formats:

* A simple single-axis format using `value` and `translations`
* A selector-driven multi-axis format using `selectors` and rule-array `translations`

#### Simple Placeholder Rules

In the simple format:

* `value` is the placeholder value to examine. It may be a [`Number`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Number.html), [`Cardinality`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html), [`Ordinality`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html), [`Gender`](https://javadoc.lokalized.com/com/lokalized/Gender.html), [`GrammaticalCase`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html), [`Definiteness`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html), [`Classifier`](https://javadoc.lokalized.com/com/lokalized/Classifier.html), [`Formality`](https://javadoc.lokalized.com/com/lokalized/Formality.html), [`Clusivity`](https://javadoc.lokalized.com/com/lokalized/Clusivity.html), [`Animacy`](https://javadoc.lokalized.com/com/lokalized/Animacy.html), [`Phonetic`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html), or [`String`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/String.html) type.  Lokalized will convert [`Number`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Number.html) instances to the appropriate [`Cardinality`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html) or [`Ordinality`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html) according the language's rules, and [`String`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/String.html) instances to [`Phonetic`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html) using your [`PhoneticResolver`](https://javadoc.lokalized.com/com/lokalized/PhoneticResolver.html) with the current locale.
* `translations` is a set of language rules against which to evaluate `value` and provide a translation

Here, the value of `bookCount` is evaluated against the specified cardinality rules and the result is placed into `books`.  For example, if application code passes in `1` for `bookCount`, this matches [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) and `book` is the value of the `books` placeholder.  If application code passes in a different value, [`CARDINALITY_OTHER`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#OTHER) is matched and `books` is used. 

Supported values for `translations` are [`Cardinality`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html), [`Ordinality`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html), [`Gender`](https://javadoc.lokalized.com/com/lokalized/Gender.html), [`GrammaticalCase`](https://javadoc.lokalized.com/com/lokalized/GrammaticalCase.html), [`Definiteness`](https://javadoc.lokalized.com/com/lokalized/Definiteness.html), [`Classifier`](https://javadoc.lokalized.com/com/lokalized/Classifier.html), [`Formality`](https://javadoc.lokalized.com/com/lokalized/Formality.html), [`Clusivity`](https://javadoc.lokalized.com/com/lokalized/Clusivity.html), [`Animacy`](https://javadoc.lokalized.com/com/lokalized/Animacy.html), and [`Phonetic`](https://javadoc.lokalized.com/com/lokalized/Phonetic.html) types.

In the simple format, you may not mix language forms in the same `translations` object.  For example, it is illegal to specify both [`CARDINALITY_ONE`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#ONE) and [`GENDER_MASCULINE`](https://javadoc.lokalized.com/com/lokalized/Gender.html#MASCULINE).  Use the selector-driven format when one placeholder depends on more than one agreement dimension.

Simple placeholder rules are strict: if your application supplies or resolves a language-form value that is not present in `translations`, the lookup is treated as a resolution failure and your configured `TranslationFailureHandler` decides what happens. Use selector-driven placeholders with a default rule if you need data-level fallback behavior.

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

#### Selector-Driven Placeholder Rules

Use selector-driven placeholders when a single placeholder depends on multiple language-form dimensions at once, for example `CASE` and `GENDER`.

```json
{
  "Send the invoice to {{honorific}} {{lastName}}." : {
    "translation" : "Senden Sie die Rechnung an {{honorific}} {{lastName}}.",
    "placeholders" : {
      "honorific" : {
        "selectors" : [
          {
            "value" : "grammaticalCase",
            "form" : "CASE"
          },
          {
            "value" : "gender",
            "form" : "GENDER"
          }
        ],
        "translations" : [
          {
            "when" : {
              "CASE" : "CASE_DATIVE",
              "GENDER" : "GENDER_MASCULINE"
            },
            "value" : "Herrn"
          },
          {
            "when" : {
              "GENDER" : "GENDER_MASCULINE"
            },
            "value" : "Herr"
          },
          {
            "when" : {
              "GENDER" : "GENDER_FEMININE"
            },
            "value" : "Frau"
          }
        ]
      }
    }
  }
}
```

In the selector-driven format:

* `selectors` declares which application-supplied values to inspect and which language-form family each one belongs to.  Supported selector `form` values are `CARDINALITY`, `ORDINALITY`, `GENDER`, `CASE`, `DEFINITENESS`, `CLASSIFIER`, `FORMALITY`, `CLUSIVITY`, `ANIMACY`, and `PHONETIC`.
* `translations` is an ordered list of rules.  Each rule has a `value` and may optionally have a `when` object.
* `when` is a structured match, not a general expression language.  It may only contain selector-form names such as `CASE` or `GENDER`.
* Lokalized selects the most specific matching rule.  In the above example, `CASE + GENDER` beats `GENDER` alone.
* A rule with no `when` is the default rule.  If no rule matches and no default rule is provided, the lookup is treated as a resolution failure and your configured `TranslationFailureHandler` decides what happens.
* Ambiguous overlapping rules with the same specificity are rejected while loading translations.

Here is the selector-driven placeholder exercised with a few simple assertions:

```java
Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("de"))
  .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
  .localeSupplier(matcher -> Locale.forLanguageTag("de"))
  .build();

// Most-specific CASE + GENDER rule
assertEquals("Senden Sie die Rechnung an Herrn Weber.", strings.get(
  "Send the invoice to {{honorific}} {{lastName}}.",
  Map.of(
    "grammaticalCase", GrammaticalCase.DATIVE,
    "gender", Gender.MASCULINE,
    "lastName", "Weber"
  )
));

// Falls back to the less-specific GENDER rule
assertEquals("Senden Sie die Rechnung an Herr Weber.", strings.get(
  "Send the invoice to {{honorific}} {{lastName}}.",
  Map.of(
    "grammaticalCase", GrammaticalCase.NOMINATIVE,
    "gender", Gender.MASCULINE,
    "lastName", "Weber"
  )
));

// Different less-specific GENDER rule
assertEquals("Senden Sie die Rechnung an Frau Weber.", strings.get(
  "Send the invoice to {{honorific}} {{lastName}}.",
  Map.of(
    "grammaticalCase", GrammaticalCase.NOMINATIVE,
    "gender", Gender.FEMININE,
    "lastName", "Weber"
  )
));
```

Selector-driven placeholders are for local agreement only.  Use `alternatives` when you need arbitrary boolean logic or whole-sentence rewrites.

### Alternatives

You may specify parenthesized expressions of arbitrary complexity in `alternatives` to fine-tune your translations.  `alternatives` complement selector-driven placeholders: use placeholder selectors for local agreement on one slot, and use `alternatives` for broader conditional rewrites.  It's perfectly legal to have an alternative like this:
 
```text
gender == GENDER_MASCULINE && (bookCount > 10 || magazineCount > 20)
```

Standard boolean operator precedence applies: `&&` binds tighter than `||`.

Lokalized will automatically evaluate cardinality and ordinality for numbers if required by the expression.  For example, in English, if I were to supply `bookCount` of `50`, this expression would evalute to `true`:
 
```text
bookCount == CARDINALITY_OTHER
``` 

...and so would this:

```text
bookCount == 50
``` 

Note that the supported comparison operators for cardinality, ordinality, gender, and phonetic forms are `==` and `!=`.  You cannot say `bookCount < CARDINALITY_FEW`, for example.

Alternative expression recursion is supported. That is, each value for `alternatives` can itself have `translation`, `commentary`, `placeholderMetadata`, `placeholders`, and `alternatives`.  You can also use the simpler string-only form if no special translation functionality is needed.
  
Alternative evaluation follows these rules:

* Deepest level of recursion is evaluated first
* Expressions are evaluated according to their order in the list, halting at first matched expression 

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
String translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 0));
assertEquals("I'm ashamed to admit I didn't read anything.", translation);

// 1 level deep recursion
translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 1));
assertEquals("I only read a few books. 1, in fact!", translation);

// Normal case
translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 3));
assertEquals("I read 3 books.", translation);
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
VARIABLE = ( Unicode letter | "_" ) { Unicode letter | Unicode digit | "_" | "-" } ;
BOOLEAN_OPERATOR = "&&" | "||" ;
COMPARISON_OPERATOR = "<" | ">" | "<=" | ">=" | "==" | "!=" ;
```

Built-in language-form constants are reserved in alternative expressions. A token like `CARDINALITY_ONE`, `GENDER_MASCULINE`, `CASE_DATIVE`, `DEFINITENESS_DEFINITE`, `CLASSIFIER_PERSON`, `FORMALITY_FORMAL`, `CLUSIVITY_INCLUSIVE`, `ANIMACY_ANIMATE`, or `PHONETIC_VOWEL` is parsed as a constant, not as a placeholder variable. Placeholder names may not use built-in constant names.

#### What Expressions Currently Support

* Evaluation of "normal" infix expressions of arbitrary complexity (can be nested/parenthesized)
* Comparison of supported language forms, phonetic forms, plural and ordinal forms, and literal numeric values against each other or user-supplied variables

#### What Expressions Do Not Currently Support

* The unary `!` operator
* Explicit `null` operands (can be implicit, i.e. a `VARIABLE` value)
* A cardinality range construct ([to be added in a future release](https://github.com/lokalized/lokalized-java/issues/16))

### Placeholder Metadata

The `placeholderMetadata` object lets you document individual placeholders for translators or tooling.  Unlike `placeholders`, it does not affect runtime evaluation.

Each `placeholderMetadata` object key is the name of a placeholder and the value is an object with optional fields:

* `type` is a translator-facing type label such as `STRING`, `NUMBER`, `DATE`, `GENDER`, or `CASE`
* `commentary` is free-form placeholder-specific context
* `example` is an example runtime value
* `allowedValues` is an array of unique string values that are valid for this placeholder

If `type` is one of Lokalized's built-in language-form families such as `GENDER` or `CASE`, any supplied `allowedValues` are validated against the corresponding built-in language-form values. Duplicate `allowedValues` entries are rejected.

If `allowedValues` is omitted, Lokalized does not restrict the placeholder to a predefined set of values.

```json
{
  "Send the invoice to {{honorific}} {{lastName}}." : {
    "commentary" : "Shown in the invoice send-confirmation flow.",
    "placeholderMetadata" : {
      "grammaticalCase" : {
        "type" : "CASE",
        "commentary" : "Case required by the surrounding German preposition.",
        "example" : "CASE_DATIVE",
        "allowedValues" : ["CASE_NOMINATIVE", "CASE_DATIVE"]
      },
      "gender" : {
        "type" : "GENDER",
        "commentary" : "Recipient grammatical gender.",
        "example" : "GENDER_MASCULINE",
        "allowedValues" : ["GENDER_MASCULINE", "GENDER_FEMININE"]
      },
      "lastName" : {
        "type" : "STRING",
        "commentary" : "Recipient family name without honorific.",
        "example" : "Weber"
      },
      "honorific" : {
        "type" : "STRING",
        "commentary" : "Derived placeholder selected by the translation rules below.",
        "example" : "Herrn"
      }
    },
    "translation" : "Senden Sie die Rechnung an {{honorific}} {{lastName}}."
  }
}
```

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
* Small changes to text require updating every strings file since keys are not "constant"

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

Lokalized overlaps with ICU MessageFormat, MF2, Fluent, and gettext, but it is optimized for a narrower job: selecting natural-sounding translated phrases from structured JSON while application code supplies typed placeholder values.

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

That extra structure pays off when a phrase needs several agreement dimensions. A Spanish baseball translation can select gender-specific fragments such as `uno`/`una`, `jugadores`/`jugadoras`, and plural forms independently, while German address text can select `Herr`, `Herrn`, or `Frau` from the same selector-driven placeholder rules. Cardinality ranges use CLDR plural-range data, and phonetic choices such as English `a`/`an` or Spanish `el agua` use an application-supplied [`PhoneticResolver`](https://javadoc.lokalized.com/com/lokalized/PhoneticResolver.html).

Use the standard JDK formatters for dates, times, numbers, percentages, and currency. Consider ICU MessageFormat/MF2 or Fluent when your team already has those translation workflows and needs broad ecosystem tooling. Consider gettext when PO-file tooling and translator workflows are the main constraint. Lokalized is strongest when the hard part is runtime agreement across plural, gender, case, range, definiteness, classifier, formality, clusivity, animacy, or phonetic forms.

## Language Reference

Each language reference page includes CLDR 48.2 cardinality, cardinality range, and ordinality data, plus generated cookbook examples that show translation-file structure and Java lookup calls for that language's plural categories.

These pages list CLDR plural-rule locales, not every valid IETF BCP 47 locale tag. Regional tags may inherit from a parent language when CLDR does not define separate rules; for example, `pt-BR` uses `pt`, while `pt-PT` has its own page because its rules differ.

| Language | Tag |
|---|---|
| [Afrikaans](https://www.lokalized.com/languages/af) | `af` |
| [Akan](https://www.lokalized.com/languages/ak) | `ak` |
| [Albanian (Shqip)](https://www.lokalized.com/languages/sq) | `sq` |
| [Amharic (አማርኛ)](https://www.lokalized.com/languages/am) | `am` |
| [Anii](https://www.lokalized.com/languages/blo) | `blo` |
| [Arabic (العربية)](https://www.lokalized.com/languages/ar) | `ar` |
| [Aragonese](https://www.lokalized.com/languages/an) | `an` |
| [Armenian (հայերէն)](https://www.lokalized.com/languages/hy) | `hy` |
| [Assamese (অসমীয়া)](https://www.lokalized.com/languages/as) | `as` |
| [Asturian (asturianu)](https://www.lokalized.com/languages/ast) | `ast` |
| [Asu (asa)](https://www.lokalized.com/languages/asa) | `asa` |
| [Azeri (Azerbaijani)](https://www.lokalized.com/languages/az) | `az` |
| [Baluchi](https://www.lokalized.com/languages/bal) | `bal` |
| [Bambara (Bamanankan)](https://www.lokalized.com/languages/bm) | `bm` |
| [Bangla (বাংলা)](https://www.lokalized.com/languages/bn) | `bn` |
| [Basque (euskara)](https://www.lokalized.com/languages/eu) | `eu` |
| [Belarusian (беларускі)](https://www.lokalized.com/languages/be) | `be` |
| [Bemba (ChiBemba)](https://www.lokalized.com/languages/bem) | `bem` |
| [Bena (Bəna)](https://www.lokalized.com/languages/bez) | `bez` |
| [Bhojpuri](https://www.lokalized.com/languages/bho) | `bho` |
| [Bodo (बर')](https://www.lokalized.com/languages/brx) | `brx` |
| [Bosnian (босански)](https://www.lokalized.com/languages/bs) | `bs` |
| [Breton (brezhoneg)](https://www.lokalized.com/languages/br) | `br` |
| [Bulgarian (български)](https://www.lokalized.com/languages/bg) | `bg` |
| [Burmese](https://www.lokalized.com/languages/my) | `my` |
| [Cantonese (廣東話)](https://www.lokalized.com/languages/yue) | `yue` |
| [Catalan (català)](https://www.lokalized.com/languages/ca) | `ca` |
| [Cebuano](https://www.lokalized.com/languages/ceb) | `ceb` |
| [Central Atlas Tamazight (Tamaziġt)](https://www.lokalized.com/languages/tzm) | `tzm` |
| [Central Kurdish (کوردیی ناوەندی)](https://www.lokalized.com/languages/ckb) | `ckb` |
| [Chechen (Нохчийн мотт)](https://www.lokalized.com/languages/ce) | `ce` |
| [Cherokee (ᏣᎳᎩ ᎦᏬᏂᎯᏍᏗ)](https://www.lokalized.com/languages/chr) | `chr` |
| [Chiga (Rukiga)](https://www.lokalized.com/languages/cgg) | `cgg` |
| [Chuvash](https://www.lokalized.com/languages/cv) | `cv` |
| [Colognian (Kölsch Platt)](https://www.lokalized.com/languages/ksh) | `ksh` |
| [Cornish (Kernowek)](https://www.lokalized.com/languages/kw) | `kw` |
| [Croatian (hrvatski)](https://www.lokalized.com/languages/hr) | `hr` |
| [Czech (čeština)](https://www.lokalized.com/languages/cs) | `cs` |
| [Danish (Dansk)](https://www.lokalized.com/languages/da) | `da` |
| [Divehi (dhivehi)](https://www.lokalized.com/languages/dv) | `dv` |
| [Dogri](https://www.lokalized.com/languages/doi) | `doi` |
| [Dutch (Nederlands)](https://www.lokalized.com/languages/nl) | `nl` |
| [Dzongkha (རྫོང་ཁ་)](https://www.lokalized.com/languages/dz) | `dz` |
| [English](https://www.lokalized.com/languages/en) | `en` |
| [Esperanto](https://www.lokalized.com/languages/eo) | `eo` |
| [Estonian (Eesti)](https://www.lokalized.com/languages/et) | `et` |
| [European Portuguese](https://www.lokalized.com/languages/pt-PT) | `pt-PT` |
| [Ewe (Èʋe)](https://www.lokalized.com/languages/ee) | `ee` |
| [Faroese (føroyskt)](https://www.lokalized.com/languages/fo) | `fo` |
| [Filipino (Wikang Filipino)](https://www.lokalized.com/languages/fil) | `fil` |
| [Finnish (suomi)](https://www.lokalized.com/languages/fi) | `fi` |
| [French (français)](https://www.lokalized.com/languages/fr) | `fr` |
| [Friulian (Furlan)](https://www.lokalized.com/languages/fur) | `fur` |
| [Fulah (Fulfulde)](https://www.lokalized.com/languages/ff) | `ff` |
| [Galician (galego)](https://www.lokalized.com/languages/gl) | `gl` |
| [Ganda (Oluganda)](https://www.lokalized.com/languages/lg) | `lg` |
| [Georgian (ქართული)](https://www.lokalized.com/languages/ka) | `ka` |
| [German (Deutsch)](https://www.lokalized.com/languages/de) | `de` |
| [Greek (Ελληνικά)](https://www.lokalized.com/languages/el) | `el` |
| [Greenlandic (Kalaallisut)](https://www.lokalized.com/languages/kl) | `kl` |
| [Gujarati (ગુજરાતી)](https://www.lokalized.com/languages/gu) | `gu` |
| [Gun (Fon gbè)](https://www.lokalized.com/languages/guw) | `guw` |
| [Hausa (هَرْشَن هَوْسَ‎)](https://www.lokalized.com/languages/ha) | `ha` |
| [Hawaiian (Ōlelo Hawaiʻi)](https://www.lokalized.com/languages/haw) | `haw` |
| [Hebrew (עברית)](https://www.lokalized.com/languages/he) | `he` |
| [Hindi (हिंदी)](https://www.lokalized.com/languages/hi) | `hi` |
| [Hmong Njua](https://www.lokalized.com/languages/hnj) | `hnj` |
| [Hungarian (magyar)](https://www.lokalized.com/languages/hu) | `hu` |
| [Icelandic (íslenska)](https://www.lokalized.com/languages/is) | `is` |
| [Ido](https://www.lokalized.com/languages/io) | `io` |
| [Igbo (Asụsụ Igbo)](https://www.lokalized.com/languages/ig) | `ig` |
| [Inari Sami (anarâškielâ)](https://www.lokalized.com/languages/smn) | `smn` |
| [Indonesian (Bahasa Indonesia)](https://www.lokalized.com/languages/id) | `id` |
| [Interlingua](https://www.lokalized.com/languages/ia) | `ia` |
| [Interlingue](https://www.lokalized.com/languages/ie) | `ie` |
| [Inuktitut (ᐃᓄᒃᑎᑐᑦ)](https://www.lokalized.com/languages/iu) | `iu` |
| [Irish (Gaeilge)](https://www.lokalized.com/languages/ga) | `ga` |
| [Italian (italiano)](https://www.lokalized.com/languages/it) | `it` |
| [Japanese (日本語)](https://www.lokalized.com/languages/ja) | `ja` |
| [Javanese (basa Jawa)](https://www.lokalized.com/languages/jv) | `jv` |
| [Javanese (basa Jawa)](https://www.lokalized.com/languages/jw) | `jw` |
| [Jju (Kaje)](https://www.lokalized.com/languages/kaj) | `kaj` |
| [Kabuverdianu (Kriolu)](https://www.lokalized.com/languages/kea) | `kea` |
| [Kabyle (Taqbaylit)](https://www.lokalized.com/languages/kab) | `kab` |
| [Kako](https://www.lokalized.com/languages/kkj) | `kkj` |
| [Kannada (ಕನ್ನಡ)](https://www.lokalized.com/languages/kn) | `kn` |
| [Kashmiri (कॉशुर)](https://www.lokalized.com/languages/ks) | `ks` |
| [Kazakh (қазақ тілі)](https://www.lokalized.com/languages/kk) | `kk` |
| [Khmer (ភាសាខ្មែរ)](https://www.lokalized.com/languages/km) | `km` |
| [Kirghiz (кыргызча)](https://www.lokalized.com/languages/ky) | `ky` |
| [Konkani](https://www.lokalized.com/languages/kok) | `kok` |
| [Konkani (Latin)](https://www.lokalized.com/languages/kok-Latn) | `kok-Latn` |
| [Korean (한국어)](https://www.lokalized.com/languages/ko) | `ko` |
| [Koyraboro Senni (koyra-boro senn-i)](https://www.lokalized.com/languages/ses) | `ses` |
| [Kurdish (کوردی)](https://www.lokalized.com/languages/ku) | `ku` |
| [Lakota (Lakȟótiyapi)](https://www.lokalized.com/languages/lkt) | `lkt` |
| [Langi (Kilaangi)](https://www.lokalized.com/languages/lag) | `lag` |
| [Lao (ພາສາລາວ)](https://www.lokalized.com/languages/lo) | `lo` |
| [Latvian (Latviešu)](https://www.lokalized.com/languages/lv) | `lv` |
| [Ligurian](https://www.lokalized.com/languages/lij) | `lij` |
| [Lingala (Lingála)](https://www.lokalized.com/languages/ln) | `ln` |
| [Lithuanian (Lietuvių)](https://www.lokalized.com/languages/lt) | `lt` |
| [lld](https://www.lokalized.com/languages/lld) | `lld` |
| [Lojban (la .lojban.)](https://www.lokalized.com/languages/jbo) | `jbo` |
| [Lower Sorbian (Dolnoserbski)](https://www.lokalized.com/languages/dsb) | `dsb` |
| [Lule Sami (julevsámegiella)](https://www.lokalized.com/languages/smj) | `smj` |
| [Luxembourgish (Lëtzebuergesch)](https://www.lokalized.com/languages/lb) | `lb` |
| [Macedonian (македонски)](https://www.lokalized.com/languages/mk) | `mk` |
| [Machame](https://www.lokalized.com/languages/jmc) | `jmc` |
| [Makonde (Chi(ni)makonde)](https://www.lokalized.com/languages/kde) | `kde` |
| [Malagasy (Fiteny Malagasy)](https://www.lokalized.com/languages/mg) | `mg` |
| [Malay (بهاس ملايو‎)](https://www.lokalized.com/languages/ms) | `ms` |
| [Malayalam (മലയാളം)](https://www.lokalized.com/languages/ml) | `ml` |
| [Maltese (Malti)](https://www.lokalized.com/languages/mt) | `mt` |
| [Mandarin Chinese (中文)](https://www.lokalized.com/languages/zh) | `zh` |
| [Manx (Gaelg)](https://www.lokalized.com/languages/gv) | `gv` |
| [Marathi (मराठी)](https://www.lokalized.com/languages/mr) | `mr` |
| [Masai (ɔl Maa)](https://www.lokalized.com/languages/mas) | `mas` |
| [Metaʼ](https://www.lokalized.com/languages/mgo) | `mgo` |
| [Moldovan (limba moldovenească)](https://www.lokalized.com/languages/mo) | `mo` |
| [Mongolian (монгол хэл)](https://www.lokalized.com/languages/mn) | `mn` |
| [N’Ko](https://www.lokalized.com/languages/nqo) | `nqo` |
| [Nahuatl (Nāhuatl)](https://www.lokalized.com/languages/nah) | `nah` |
| [Najdi Arabic (اللهجة النجدية)](https://www.lokalized.com/languages/ars) | `ars` |
| [Nama (Khoekhoegowab)](https://www.lokalized.com/languages/naq) | `naq` |
| [Nepali (नेपाली भाषा)](https://www.lokalized.com/languages/ne) | `ne` |
| [Ngiemboon (Ngyɛmbɔɔŋ)](https://www.lokalized.com/languages/nnh) | `nnh` |
| [Ngomba (Nda’a)](https://www.lokalized.com/languages/jgo) | `jgo` |
| [Nigerian Pidgin](https://www.lokalized.com/languages/pcm) | `pcm` |
| [North Ndebele (isiNdebele)](https://www.lokalized.com/languages/nd) | `nd` |
| [Northern Sami (davvisámegiella)](https://www.lokalized.com/languages/se) | `se` |
| [Northern Sotho (Sesotho sa Leboa)](https://www.lokalized.com/languages/nso) | `nso` |
| [Norwegian (norsk)](https://www.lokalized.com/languages/no) | `no` |
| [Norwegian Bokmål (bokmål)](https://www.lokalized.com/languages/nb) | `nb` |
| [Norwegian Nynorsk (nynorsk)](https://www.lokalized.com/languages/nn) | `nn` |
| [Nyanja (Chinyanja)](https://www.lokalized.com/languages/ny) | `ny` |
| [Nyankole (Runyankore)](https://www.lokalized.com/languages/nyn) | `nyn` |
| [Odia (ଓଡ଼ିଆ)](https://www.lokalized.com/languages/or) | `or` |
| [Oromo (Afaan Oromoo)](https://www.lokalized.com/languages/om) | `om` |
| [Osage](https://www.lokalized.com/languages/osa) | `osa` |
| [Ossetian (Ирон æвзаг)](https://www.lokalized.com/languages/os) | `os` |
| [Papiamento (Papiamentu)](https://www.lokalized.com/languages/pap) | `pap` |
| [Persian (فارسی)](https://www.lokalized.com/languages/fa) | `fa` |
| [Polish (polski)](https://www.lokalized.com/languages/pl) | `pl` |
| [Portuguese (português)](https://www.lokalized.com/languages/pt) | `pt` |
| [Prussian (Prūsiskan)](https://www.lokalized.com/languages/prg) | `prg` |
| [Punjabi (پنجابی)](https://www.lokalized.com/languages/pa) | `pa` |
| [Pushto (پښتو)](https://www.lokalized.com/languages/ps) | `ps` |
| [Romanian (română)](https://www.lokalized.com/languages/ro) | `ro` |
| [Romansh (rumàntsch)](https://www.lokalized.com/languages/rm) | `rm` |
| [Rombo (Kirombo)](https://www.lokalized.com/languages/rof) | `rof` |
| [Undetermined (CLDR root)](https://www.lokalized.com/languages/root) | `und` |
| [Russian (русский)](https://www.lokalized.com/languages/ru) | `ru` |
| [Rwa (West Chaga)](https://www.lokalized.com/languages/rwk) | `rwk` |
| [Saho (ሳሆኛ)](https://www.lokalized.com/languages/ssy) | `ssy` |
| [Sakha (Саха тыла)](https://www.lokalized.com/languages/sah) | `sah` |
| [Samburu (Sampur)](https://www.lokalized.com/languages/saq) | `saq` |
| [Sami (Saami)](https://www.lokalized.com/languages/smi) | `smi` |
| [Samogitian](https://www.lokalized.com/languages/sgs) | `sgs` |
| [Sango (yângâ tî sängö)](https://www.lokalized.com/languages/sg) | `sg` |
| [Santali](https://www.lokalized.com/languages/sat) | `sat` |
| [Sardinian](https://www.lokalized.com/languages/sc) | `sc` |
| [Scottish Gaelic (Gàidhlig)](https://www.lokalized.com/languages/gd) | `gd` |
| [Sena (xisena)](https://www.lokalized.com/languages/seh) | `seh` |
| [Serbian (Српски)](https://www.lokalized.com/languages/sr) | `sr` |
| [Serbo-Croatian (srpskohrvatski)](https://www.lokalized.com/languages/sh) | `sh` |
| [Shambala (kishambaa)](https://www.lokalized.com/languages/ksb) | `ksb` |
| [Shona (chiShona)](https://www.lokalized.com/languages/sn) | `sn` |
| [Sichuan Yi (ꆈꌠ꒿)](https://www.lokalized.com/languages/ii) | `ii` |
| [Sicilian](https://www.lokalized.com/languages/scn) | `scn` |
| [Sindhi](https://www.lokalized.com/languages/sd) | `sd` |
| [Sinhalese (සිංහල)](https://www.lokalized.com/languages/si) | `si` |
| [Skolt Sami (sääʹmǩiõll)](https://www.lokalized.com/languages/sms) | `sms` |
| [Slovak (Slovenčina)](https://www.lokalized.com/languages/sk) | `sk` |
| [Slovenian (Slovenščina)](https://www.lokalized.com/languages/sl) | `sl` |
| [Soga (Lusoga)](https://www.lokalized.com/languages/xog) | `xog` |
| [Somali (اف سومالى‎)](https://www.lokalized.com/languages/so) | `so` |
| [South Ndebele (isiNdebele)](https://www.lokalized.com/languages/nr) | `nr` |
| [Southern Kurdish (کوردی خوارگ)](https://www.lokalized.com/languages/sdh) | `sdh` |
| [Southern Sami (Åarjelsaemien gïele)](https://www.lokalized.com/languages/sma) | `sma` |
| [Southern Sotho (Sesotho)](https://www.lokalized.com/languages/st) | `st` |
| [Spanish (español)](https://www.lokalized.com/languages/es) | `es` |
| [Sundanese](https://www.lokalized.com/languages/su) | `su` |
| [Swahili (Kiswahili)](https://www.lokalized.com/languages/sw) | `sw` |
| [Swampy Cree](https://www.lokalized.com/languages/csw) | `csw` |
| [Swati (SiSwati)](https://www.lokalized.com/languages/ss) | `ss` |
| [Swedish (svenska)](https://www.lokalized.com/languages/sv) | `sv` |
| [Swiss German (Schwiizerdütsch)](https://www.lokalized.com/languages/gsw) | `gsw` |
| [Syriac (Leššānā Suryāyā)](https://www.lokalized.com/languages/syr) | `syr` |
| [Tachelhit (Tašlḥiyt)](https://www.lokalized.com/languages/shi) | `shi` |
| [Tagalog (Wikang Tagalog)](https://www.lokalized.com/languages/tl) | `tl` |
| [Tamil (Tamiḻ)](https://www.lokalized.com/languages/ta) | `ta` |
| [Telugu (తెలుగు)](https://www.lokalized.com/languages/te) | `te` |
| [Teso (Ateso)](https://www.lokalized.com/languages/teo) | `teo` |
| [Thai (ไทย)](https://www.lokalized.com/languages/th) | `th` |
| [Tibetan (བོད་སྐད་)](https://www.lokalized.com/languages/bo) | `bo` |
| [Tigre (ትግራይት)](https://www.lokalized.com/languages/tig) | `tig` |
| [Tigrinya (ትግርኛ)](https://www.lokalized.com/languages/ti) | `ti` |
| [Tok Pisin](https://www.lokalized.com/languages/tpi) | `tpi` |
| [Tongan (lea faka-Tonga)](https://www.lokalized.com/languages/to) | `to` |
| [Tsonga (Xitsonga)](https://www.lokalized.com/languages/ts) | `ts` |
| [Tswana (Setswana)](https://www.lokalized.com/languages/tn) | `tn` |
| [Turkish (Türkçe)](https://www.lokalized.com/languages/tr) | `tr` |
| [Turkmen (Türkmençe)](https://www.lokalized.com/languages/tk) | `tk` |
| [Tyap (Katab)](https://www.lokalized.com/languages/kcg) | `kcg` |
| [Uighur (ئۇيغۇر تىلى)](https://www.lokalized.com/languages/ug) | `ug` |
| [Ukrainian (українська)](https://www.lokalized.com/languages/uk) | `uk` |
| [Upper Sorbian (hornjoserbšćina)](https://www.lokalized.com/languages/hsb) | `hsb` |
| [Urdu (اُردُو)](https://www.lokalized.com/languages/ur) | `ur` |
| [Uzbek (ўзбек тили)](https://www.lokalized.com/languages/uz) | `uz` |
| [Venda (Tshivenḓa)](https://www.lokalized.com/languages/ve) | `ve` |
| [Venetian](https://www.lokalized.com/languages/vec) | `vec` |
| [Vietnamese (Tiếng Việt)](https://www.lokalized.com/languages/vi) | `vi` |
| [Volapük](https://www.lokalized.com/languages/vo) | `vo` |
| [Vunjo (Wuunjo)](https://www.lokalized.com/languages/vun) | `vun` |
| [Walloon (Walon)](https://www.lokalized.com/languages/wa) | `wa` |
| [Walser (Walscher)](https://www.lokalized.com/languages/wae) | `wae` |
| [Welsh (Cymraeg)](https://www.lokalized.com/languages/cy) | `cy` |
| [Western Frisian (Frysk)](https://www.lokalized.com/languages/fy) | `fy` |
| [Wolof](https://www.lokalized.com/languages/wo) | `wo` |
| [Xhosa (isiXhosa)](https://www.lokalized.com/languages/xh) | `xh` |
| [Yiddish (יידיש)](https://www.lokalized.com/languages/yi) | `yi` |
| [Yoruba (Èdè Yorùbá)](https://www.lokalized.com/languages/yo) | `yo` |
| [Zulu (isiZulu)](https://www.lokalized.com/languages/zu) | `zu` |

## java.util.logging

Lokalized uses ```java.util.logging``` internally.  The usual way to hook into this is with [SLF4J](http://slf4j.org), which can funnel all the different logging mechanisms in your app through a single one, normally [Logback](http://logback.qos.ch).  Your Maven configuration might look like this:

```xml
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.1.9</version>
</dependency>
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>jul-to-slf4j</artifactId>
  <version>1.7.22</version>
</dependency>
```

You might have code like this which runs at startup:

```java
// Bridge all java.util.logging to SLF4J
java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
for (Handler handler : rootLogger.getHandlers())
  rootLogger.removeHandler(handler);

SLF4JBridgeHandler.install();
```

Don't forget to uninstall the bridge at shutdown time:

```java
// Sometime later
SLF4JBridgeHandler.uninstall();
```

Note: ```SLF4JBridgeHandler``` can impact performance.  You can mitigate that with Logback's ```LevelChangePropagator``` configuration option [as described here](http://logback.qos.ch/manual/configuration.html#LevelChangePropagator).

## About

Lokalized was created by [Mark Allen](https://www.revetkn.com) and sponsored by [Transmogrify LLC](https://www.xmog.com) and [Revetware LLC](https://www.revetware.com).
