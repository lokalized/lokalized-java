## Lokalized

Lokalized facilitates natural-sounding software translations.

#### Design Goals

* Complex translation rules can be expressed in a configuration file, not code
* First-class support for gender and plural (cardinal, ordinal) language forms
* Provide a simple expression language to handle traditionally difficult edge cases
* Support for multiple platforms
* Immutability/thread-safety
* No dependencies

#### Design Non-Goals

* Support for date/time, number, percentage, and currency formatting (these problems are already solved well)

#### License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

#### Maven Installation

```xml
<dependency>
  <groupId>com.lokalized</groupId>
  <artifactId>lokalized</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### Direct Download

If you don't use Maven, you can drop [lokalized-1.0.0-SNAPSHOT.jar](http://central.maven.org/maven2/com/lokalized/lokalized/1.0.0-SNAPSHOT/lokalized-1.0.0-SNAPSHOT.jar) directly into your project.  No other dependencies are required.

## Why Lokalized?

* **As a developer**, it is unrealistic to embed per-locale translation rules in code for every text string
* **As a translator**, sufficient context and the power of an expression language are required to provide the best translations possible
* **As a manager**, it is preferable to have a single translation specification that works on the backend, web frontend, and native mobile apps

Perhaps most importantly, the Lokalized placeholder system and expression language allow you to support edge cases that are critical to natural-sounding translations - this can be difficult to achieve using traditional solutions. 

## Getting Started

We'll start with hands-on examples to illustrate key features.  More detailed documentation is available further down in this document.

##### 1. Create Localized Strings Files

Filenames must conform to the IETF BCP 47 language tag format.

Here is a generic English (`en`) localized strings file which handles two localizations:

```json
{
  "I am going on vacation" : "I am going on vacation.",
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
        "bookCount == 0" : {
          "translation" : "I didn't read any books."
        }
      }
    ]
  }  
}
```

Here is a British English (`en-GB`) localized strings file:

```json
{
  "I am going on vacation." : "I am going on holiday."
}
```

Lokalized performs locale matching and falls back to less-specific locales as appropriate, so there is no need to duplicate all the `en` translations in `en-GB` - it is sufficient to specify only the dialect-specific differences. 

##### 2. Create a Strings Instance
   
```java
// Your "native" fallback strings file, used in case no specific locale match is found.
// ISO 639 alpha-2 or alpha-3 language code
final String FALLBACK_LANGUAGE_CODE = "en";

// Creates a Strings instance which loads localized strings files from the given directory.
// Normally you'll only need a single shared instance to support your entire application,
// even for multitenant/concurrent usage, e.g. a Servlet container
Strings strings = new DefaultStrings.Builder(FALLBACK_LANGUAGE_CODE,
    () -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my/strings/directory")))
  .build();
```

You may also provide the builder with a locale-supplying lambda, which is useful for
environments like webapps where each request can have a different locale.

```java
// "Smart" locale selection which queries the current web request for locale data.
// MyWebContext is a class you might write yourself, perhaps using a ThreadLocal internally
Strings webappStrings = new DefaultStrings.Builder(FALLBACK_LANGUAGE_CODE,
    () -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my/strings/directory")))
  .localeSupplier(() -> MyWebContext.getHttpServletRequest().getLocale())
  .build();
```

##### 3. Ask Strings Instance for Translations

```java
// Lokalized knows how to map numbers to plural cardinalities per locale.
// That is, it understands the 3 means CARDINALITY_OTHER ("books") in English
String translation = strings.get("I read {{bookCount}} books.",
  new HashMap<String, Object>() {{
    put("bookCount", 3);
  }});

assertEquals("I read 3 books.", translation);

// 1 means CARDINALITY_ONE ("book") in English
translation = strings.get("I read {{bookCount}} books.",
  new HashMap<String, Object>() {{
    put("bookCount", 1);
  }});

assertEquals("I read 1 book.", translation);

// A special alternative rule is applied when bookCount == 0
translation = strings.get("I read {{bookCount}} books.",
  new HashMap<String, Object>() {{
    put("bookCount", 0);
  }});

assertEquals("I didn't read any books.", translation);

// Here we force British English.
// Note that providing an explicit locale is an uncommon use case -
// standard practice is to specify a localeSupplier when constructing your 
// Strings instance and Lokalized will pick the appropriate locale, e.g. 
// the locale specified by the current web request's Accept-Language header
translation = strings.get("I am going on vacation.", Locale.forLanguageTag("en-GB"));

// We have an exact match for this key in the en-GB file, so that translation is applied.
// If none were found, we would fall back to "en" and try there instead
assertEquals("I am going on holiday.", translation);
```

## A More Complex Example

Lokalized's strength is handling phrases that must be rewritten in different ways according to language rules. Suppose we introduce gender alongside plural forms.  In English, a noun's gender usually does not alter other components of a phrase.  But in Spanish it does.

This English statement has 4 variants:

* `He was one of the X best baseball players.`
* `She was one of the X best baseball players.`
* `He was the best baseball player.`
* `She was the best baseball player.`

In Spanish, we have the same number of variants (in a language like Russian or Polish there would be more!)
But notice how the statements must change to match gender - `uno` becomes `una`, `jugadores` becomes `jugadoras`, etc.

* `Fue uno de los X mejores jugadores de béisbol.`
* `Fue una de las X mejores jugadoras de béisbol.`
* `Él era el mejor jugador de béisbol.`
* `Ella era la mejor jugadora de béisbol.`

Again, we keep this gender and plural logic out of our code entirely and leave it to the translation configuration.

```java
// "Normal" translation
translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  new HashMap<String, Object>() {{
    put("heOrShe", Gender.MASCULINE);
    put("groupSize", 10);
  }});

assertEquals("He was one of the 10 best baseball players.", translation);

// Alternative expression triggered
translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  new HashMap<String, Object>() {{
    put("heOrShe", Gender.MASCULINE);
    put("groupSize", 1);
  }});

assertEquals("He was the best baseball player.", translation);

// Let's try Spanish
translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  new HashMap<String, Object>() {{
    put("heOrShe", Gender.FEMININE);
    put("groupSize", 3);
  }}, Locale.forLanguageTag("es"));

// Note that the correct feminine forms were applied
assertEquals("Fue una de las 3 mejores jugadoras de béisbol.", translation);
```

#### English Translation File

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
        "heOrShe == MASCULINE && groupSize <= 1" : {
          "translation" : "He was the best baseball player."
        }
      },
      {
        "heOrShe == FEMININE && groupSize <= 1" : {
          "translation" : "She was the best baseball player."
        }
      }
    ]
  }
}
```

#### Spanish Translation File

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
        "heOrShe == MASCULINE && groupSize <= 1" : {
          "translation" : "Él era el mejor jugador de béisbol."
        }
      },
      {
        "heOrShe == FEMININE && groupSize <= 1" : {
          "translation" : "Ella era la mejor jugadora de béisbol."
        }
      }
    ]
  }
}
```

##### TODO: include example of recursive alternatives

## Ordinality Example

##### TODO: finish

## Localized Strings Concepts

#### Gender

Gender rules vary across languages, but the general meaning is the same.
 
Lokalized supports these values:

* `MASCULINE`
* `FEMININE`
* `NEUTER`

#### Plural Cardinality

For example: `1 book, 2 books, ...`

Plural rules vary widely across languages.

Lokalized supports these values according to [CLDR rules](http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html):

* `CARDINALITY_ZERO`
* `CARDINALITY_ONE`
* `CARDINALITY_TWO`
* `CARDINALITY_FEW`
* `CARDINALITY_MANY`
* `CARDINALITY_OTHER` 

Values do not necessarily map exactly to the named number, e.g. in some languages `CARDINALITY_ONE` might mean any number ending in `1`, not just `1`.  Most languages only support a few plural forms, some have none at all (represented by `CARDINALITY_OTHER` in those cases).

##### Japanese

* `CARDINALITY_OTHER`: Matches everything (this language has no plural form)

##### English

* `CARDINALITY_ONE`: Matches 1 (e.g. `1 book`)
* `CARDINALITY_OTHER`: Everything else (e.g. `256 books`)

##### Russian

* `CARDINALITY_ONE`: Matches 1, 21, 31, 41, 51, 61, ... (e.g. `1 книга` or `171 книга`)
* `CARDINALITY_FEW`: Matches 2-4, 22-24, 32-34, ... (e.g. `2 книг` or `53 книг`)
* `CARDINALITY_OTHER`: Everything else (e.g. `27 книги`, `1,5 книги`)

#### Plural Ordinality

##### TODO: finish

1st, 2nd, 3rd...

Lokalized supports these values according to [CLDR rules](http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html):

* `ORDINALITY_ZERO`
* `ORDINALITY_ONE`
* `ORDINALITY_TWO`
* `ORDINALITY_FEW`
* `ORDINALITY_MANY`
* `ORDINALITY_OTHER` 

#### Alternative Expressions

You may specify parenthesized expressions of arbitrary complexity in `alternatives` to fine-tune your translations.  It's perfectly legal to have an alternative like `gender == MASCULINE && (bookCount > 10 || magazineCount > 20)`.

Expression recursion is supported. That is, each value for `alternatives` can itself have a `translation`, `placeholders`, and `alternatives`.

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

##### What Expressions Currently Support

* Evaluation of "normal" infix expressions of arbitrary complexity (can be nested/parenthesized)
* Comparison of gender, plural, and literal numeric values against each other or user-supplied variables

##### What Expressions Do Not Currently Support

* The unary `!` operator
* Explicit `null` operands (can be implicit, i.e. a `VARIABLE` value)

## java.util.logging

Lokalized uses ```java.util.Logging``` internally.  The usual way to hook into this is with [SLF4J](http://slf4j.org), which can funnel all the different logging mechanisms in your app through a single one, normally [Logback](http://logback.qos.ch).  Your Maven configuration might look like this:

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

Lokalized was created by [Mark Allen](http://revetkn.com) and sponsored by [Product Mog, LLC.](https://www.xmog.com)

Development was aided by

* [SomaFM](http://somafm.com)
* [Scared of Chaka](https://www.youtube.com/watch?v=lYSa2U2St54)
* [Dog Party](https://www.youtube.com/watch?v=GIn0SCdCu5I)