<a href="https://www.lokalized.com">
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
String translation = strings.get("I read {{bookCount}} books.", Map.of("bookCount", 0));
assertEquals("I didn't read any books.", translation);
```

## Design Goals

* Complex translation rules can be expressed in a configuration file, not code
* First-class support for gender and plural (cardinal, ordinal, range) language forms per latest CLDR specifications
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
  <version>1.1.0</version>
</dependency>
```

## Direct Download

If you don't use Maven, you can drop [lokalized-1.1.0.jar](https://repo1.maven.org/maven2/com/lokalized/lokalized/1.1.0/lokalized-1.1.0.jar) directly into your project.  No other dependencies are required.

## Why Lokalized?

* **As a developer**, it is unrealistic to embed per-locale translation rules in code for every text string
* **As a translator**, sufficient context and the power of an expression language are required to provide the best translations possible
* **As a manager**, it is preferable to have a single translation specification that works on the backend, web frontend, and native mobile apps

Perhaps most importantly, the Lokalized placeholder system and expression language allow you to support edge cases that are critical to natural-sounding translations - this can be difficult to achieve using traditional solutions. 

## Getting Started

We'll start with hands-on examples to illustrate key features.

### 1. Create Localized Strings Files

Filenames must conform to the IETF BCP 47 language tag format.

Here is a US English (`en-US`) localized strings file which handles two localizations:

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
          "MASCULINE" : "He",
          "FEMININE" : "She"
        }
      }
    },
    "alternatives" : [
      {
        "heOrShe == MASCULINE && groupSize <= 1" : "He was the best baseball player."        
      },
      {
        "heOrShe == FEMININE && groupSize <= 1" : "She was the best baseball player."        
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
          "MASCULINE" : "uno",
          "FEMININE" : "una"
        }
      },
      "los" : {
        "value" : "heOrShe",
        "translations" : {
          "MASCULINE" : "los",
          "FEMININE" : "las"
        }
      },
      "jugadores" : {
        "value" : "heOrShe",
        "translations" : {
          "MASCULINE" : "jugadores",
          "FEMININE" : "jugadoras"
        }
      }
    },
    "alternatives" : [
      {
        "heOrShe == MASCULINE && groupSize <= 1" : "Él era el mejor jugador de béisbol."        
      },
      {
        "heOrShe == FEMININE && groupSize <= 1" : "Ella era la mejor jugadora de béisbol."        
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

You can exploit the recursive nature of alternative expressions to reduce logic duplication.  Here, we define a toplevel alternative for `groupSize <= 1` which itself has alternatives for `MASCULINE` and `FEMININE` cases.  This is equivalent to the alternative rules defined above but might be a more "comfortable" way to express behavior for some.

Note that this is just a snippet to illustrate functionality - the other portion of this localized string has been elided for brevity.

```json
{
  "alternatives" : [
    {
      "groupSize <= 1" : {
        "alternatives" : [
          {
            "heOrShe == MASCULINE" : "Él era el mejor jugador de béisbol."
          },
          {
            "heOrShe == FEMININE" : "Ella era la mejor jugadora de béisbol."
          }
        ]
      }
    }
  ]
}
```

## Cardinality Ranges

When expressing a range of values (`1-3 meters`, `2.5-3.5 hours`), the cardinality of the range is determined by applying per-language rules to its start and end cardinalities.
  
In English we don't think about this - all ranges are of the form [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) - but many other languages have range-specific forms.

### French Translation File

French ranges can be either [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) or  [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER).

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

All English range forms evaluate to [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) so the file can be kept simple.


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
          "MASCULINE" : "His",
          "FEMININE" : "Her"
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
        "hisOrHer == FEMININE && year == 15" : "Su quinceañera es la próxima semana."        
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

* [`GENDER_MASCULINE`](https://www.lokalized.com/javadoc/com/lokalized/Gender.html#MASCULINE)
* [`GENDER_FEMININE`](https://www.lokalized.com/javadoc/com/lokalized/Gender.html#FEMININE)
* [`GENDER_NEUTER`](https://www.lokalized.com/javadoc/com/lokalized/Gender.html#NEUTER)

Lokalized provides a [`Gender`](https://www.lokalized.com/javadoc/com/lokalized/Gender.html) type which enumerates supported genders.

### Plural Cardinality

For example: `1 book, 2 books, ...`

Plural rules vary widely across languages.

Lokalized supports these values according to [CLDR rules](http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html):

* [`CARDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ZERO)
* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE)
* [`CARDINALITY_TWO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#TWO)
* [`CARDINALITY_FEW`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#FEW)
* [`CARDINALITY_MANY`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#MANY)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) 

Values do not necessarily map exactly to the named number, e.g. in some languages [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) might mean any number ending in `1`, not just `1`.  Most languages only support a few plural forms, some have none at all (represented by [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) in those cases).

#### Japanese

* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER): Matches everything (this language has no plural form)

#### English

* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE): Matches 1 (e.g. `1 dollar`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER): Everything else (e.g. `256 dollars`)

#### Russian

* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE): Matches 1, 21, 31, ... (e.g. `1 рубль` or `51 рубль`)
* [`CARDINALITY_FEW`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#FEW): Matches 2-4, 22-24, 32-34, ... (e.g. `2 рубля` or `53 рубля`)
* [`CARDINALITY_MANY`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#MANY): Matches 0, 5-20, 25-30, 45-50, ... (e.g. `5 рублей` or `17 рублей`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER): Everything else (e.g. `0,3 руб`, `1,5 руб`)

Lokalized provides a [`Cardinality`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html) type which encapsulates cardinal functionality.

You may programmatically determine cardinality using [`Cardinality#forNumber(Number number, Locale locale)`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#forNumber-java.lang.Number-java.util.Locale-) and [`Cardinality#forNumber(Number number, Integer visibleDecimalPlaces, Locale locale)`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#forNumber-java.lang.Number-java.lang.Integer-java.util.Locale-) as shown below. 

It is important to note that the number of visible decimal places can be important for some languages when performing cardinality evaluation.  For example, in English, `1` matches [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) but `1.0` matches [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER).  Even though the numbers' true values are identical, you would say `1 inch` and `1.0 inches` and therefore must take visible decimals into account.

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

### Plural Cardinality Ranges

For example: `0-1 hours, 1-2 hours, ...`

The plural form of the range is determined by examining the cardinality of its start and end components. 

#### English

* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `1–2 days`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0–1 days`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0–2 days`)

#### French

* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) (e.g. `0–1 jour`)
* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0–2 jours`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `2–100 jours`)

#### Latvian

* [`CARDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ZERO) - [`CARDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ZERO) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0–10 diennaktis`)
* [`CARDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ZERO) - [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) (e.g. `0–1 diennakts`)
* [`CARDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ZERO) - [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0–2 diennaktis`)
* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ZERO) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0,1–10 diennaktis`)
* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) (e.g. `0,1–1 diennakts`)
* [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) - [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0,1–2 diennaktis`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ZERO) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0,2–10 diennaktis`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) ⇒ [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) (e.g. `0,2–1 diennakts`)
* [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) - [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) ⇒ [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) (e.g. `0,2–2 diennaktis`)

You may programmatically determine a range's cardinality using [`Cardinality#forRange(Cardinality start, Cardinality end, Locale locale)`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#forRange-com.lokalized.Cardinality-com.lokalized.Cardinality-java.util.Locale-) as shown below.

```java
// Latvian has a number of interesting range rules.
// ZERO-ZERO -> OTHER
Cardinality cardinality = Cardinality.forRange(Cardinality.ZERO, Cardinality.ZERO, Locale.forLanguageTag("lv"));
assertEquals(Cardinality.OTHER, cardinality);

// ZERO-ONE -> ONE
cardinality = Cardinality.forRange(Cardinality.ZERO, Cardinality.ONE, Locale.forLanguageTag("lv"));
assertEquals(Cardinality.ONE, cardinality);
```

### Ordinals

For example: `1st, 2nd, 3rd, 4th, ...`

Similar to plural cardinality, ordinal rules very widely across languages.

Lokalized supports these values according to [CLDR rules](http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html):

* [`ORDINALITY_ZERO`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#ZERO)
* [`ORDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#ONE)
* [`ORDINALITY_TWO`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#TWO)
* [`ORDINALITY_FEW`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#FEW)
* [`ORDINALITY_MANY`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#MANY)
* [`ORDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#OTHER)

Again, like cardinal values, ordinals do not necessarily map to the named number. For example, [`ORDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#ONE) might apply to any number that ends in `1`.

#### Spanish

* [`ORDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#OTHER): Matches everything (this language has no ordinal form)

#### English

* [`ORDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#ONE): Matches 1, 21, 31, ... (e.g. `1st prize`)
* [`ORDINALITY_TWO`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#TWO): Matches 2, 22, 32, ... (e.g. `22nd prize`)
* [`ORDINALITY_FEW`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#FEW): Matches 3, 23, 33, ... (e.g. `33rd prize`)
* [`ORDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#OTHER): Everything else (e.g. `12th prize`)

#### Italian

* [`ORDINALITY_MANY`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#MANY): Matches 8, 11, 80, 800 (e.g. `Prendi l'8° a destra`)
* [`ORDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#OTHER): Everything else (e.g. `	Prendi la 7° a destra`)

Lokalized provides an [`Ordinality`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html) type which encapsulates ordinal functionality.

You may programmatically determine ordinality using [`Ordinality#forNumber(Number number, Locale locale)`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html#forNumber-java.lang.Number-java.util.Locale-) as shown below. 

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

## Localized Strings File Format

### Structure

* Each strings file must be UTF-8 encoded and named according to the appropriate IETF BCP 47 language tag, such as `en` or `zh-TW`
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

In addition to `translation`, each object form supports 3 additional keys: `commentary`, `placeholders`, and `alternatives`.

All 4 are optional, with the stipulation that you must provide either a `translation` or at least one `alternatives` value.

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

You are free to add as many as you like to support your translation.

Placeholder values are initially specified by application code - they are the context that is passed in at string evaluation time.

Your translation file may override passed-in placeholders if desired, but that is an uncommon use case.

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

Each `placeholders` object key is the name of the placeholder - `books`, in this example - and the value is an object with `value` and `translations`.

* `value` is the placeholder value to examine. It may be a [`Number`](https://docs.oracle.com/javase/8/docs/api/java/lang/Number.html), [`Cardinality`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html), [`Ordinality`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html), or [`Gender`](https://www.lokalized.com/javadoc/com/lokalized/Gender.html) type.  Lokalized will convert [`Number`](https://docs.oracle.com/javase/8/docs/api/java/lang/Number.html) instances to the appropriate [`Cardinality`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html) or [`Ordinality`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html) according the language's rules  
* `translations` is a set of language rules against which to evaluate `value` and provide a translation

Here, the value of `bookCount` is evaluated against the specified cardinality rules and the result is placed into `books`.  For example, if application code passes in `1` for `bookCount`, this matches [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) and `book` is the value of the `books` placeholder.  If application code passes in a different value, [`CARDINALITY_OTHER`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#OTHER) is matched and `books` is used. 

Supported values for `translations` are [`Cardinality`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html), [`Ordinality`](https://www.lokalized.com/javadoc/com/lokalized/Ordinality.html), and [`Gender`](https://www.lokalized.com/javadoc/com/lokalized/Gender.html) types.

You may not mix language forms in the same `translations` object.  For example, it is illegal to specify both [`CARDINALITY_ONE`](https://www.lokalized.com/javadoc/com/lokalized/Cardinality.html#ONE) and [`GENDER_MASCULINE`](https://www.lokalized.com/javadoc/com/lokalized/Gender.html#MASCULINE).

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

You may specify parenthesized expressions of arbitrary complexity in `alternatives` to fine-tune your translations.  It's perfectly legal to have an alternative like this:
 
```text
gender == MASCULINE && (bookCount > 10 || magazineCount > 20)
```

Lokalized will automatically evaluate cardinality and ordinality for numbers if required by the expression.  For example, in English, if I were to supply `bookCount` of `50`, this expression would evalute to `true`:
 
```text
bookCount == CARDINALITY_OTHER
``` 

...and so would this:

```text
bookCount == 50
``` 

Note that the supported comparison operators for cardinality, ordinality, and gender forms are `==` and `!=`.  You cannot say `bookCount < CARDINALITY_FEW`, for example.

Alternative expression recursion is supported. That is, each value for `alternatives` can itself have `translation`, `placeholders`, `commentary`, and `alternatives`.  You can also use the simpler string-only form if no special translation functionality is needed.
  
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
LANGUAGE_FORM = CARDINALITY | ORDINALITY | GENDER ;
CARDINALITY = "CARDINALITY_ZERO" | "CARDINALITY_ONE" | "CARDINALITY_TWO" | "CARDINALITY_FEW" | "CARDINALITY_MANY" | "CARDINALITY_OTHER" ;
ORDINALITY = "ORDINALITY_ZERO" | "ORDINALITY_ONE" | "ORDINALITY_TWO" | "ORDINALITY_FEW" | "ORDINALITY_MANY" | "ORDINALITY_OTHER" ;
GENDER = "MASCULINE" | "FEMININE" | "NEUTER" ;
VARIABLE = { alphabetic character | digit } ;
BOOLEAN_OPERATOR = "&&" | "||" ;
COMPARISON_OPERATOR = "<" | ">" | "<=" | ">=" | "==" | "!=" ;
```

#### What Expressions Currently Support

* Evaluation of "normal" infix expressions of arbitrary complexity (can be nested/parenthesized)
* Comparison of gender, plural, and literal numeric values against each other or user-supplied variables

#### What Expressions Do Not Currently Support

* The unary `!` operator
* Explicit `null` operands (can be implicit, i.e. a `VARIABLE` value)
* A cardinality range construct ([to be added in a future release](https://github.com/lokalized/lokalized-java/issues/16))

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

For example: `"SCREEN-PROFILE-BOOKS_READ"`

#### Pros

* It is possible to specifically target app components, which enforces translation context
* Perfect for big chunks of text like legal disclaimers
* "Constant" keys means translations can change without affecting code

#### Cons

* You must come up with names for every key and cross-reference in your localized strings files
* Placeholders are not encoded in the key and must be communicated to translators through some other mechanism
* Requires diligent recordkeeping and inter-team communication ("are our iOS and Android apps using the same keys or are we duplicating effort?")
* There is no default language fallback if no translation is present; users will see your contextual key onscreen 

### Or - Mix Both!

It's possible to cherrypick and create a hybrid solution.  For example, you might use natural language keys in most cases but switch to contextual for legalese and other special cases.

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