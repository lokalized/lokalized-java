## Lokalized

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
String translation = strings.get("I read {{bookCount}} books.",
  new HashMap<String, Object>() {{
    put("bookCount", 0);
  }});

assertEquals("I didn't read any books.", translation);
```

#### Design Goals

* Complex translation rules can be expressed in a configuration file, not code
* First-class support for gender and plural (cardinal, ordinal, range) language forms per latest CLDR specifications
* Provide a simple expression language to handle traditionally difficult edge cases
* Support multiple platforms natively
* Immutability/thread-safety
* No dependencies

#### Design Non-Goals

* Support for date/time, number, percentage, and currency formatting/parsing
* Support for collation
* Support for Java 7 and below

#### Roadmap

* Static analysis tool to autogenerate/sync localized strings files
* Additional Ports (JavaScript, Python, Android, Go, ...)
* Webapp for translators

#### License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

#### Maven Installation

```xml
<dependency>
  <groupId>com.lokalized</groupId>
  <artifactId>lokalized-java</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### Direct Download

If you don't use Maven, you can drop [lokalized-java-1.0.0-SNAPSHOT.jar](http://central.maven.org/maven2/com/lokalized/lokalized/1.0.0-SNAPSHOT/lokalized-1.0.0-SNAPSHOT.jar) directly into your project.  No other dependencies are required.

## Why Lokalized?

* **As a developer**, it is unrealistic to embed per-locale translation rules in code for every text string
* **As a translator**, sufficient context and the power of an expression language are required to provide the best translations possible
* **As a manager**, it is preferable to have a single translation specification that works on the backend, web frontend, and native mobile apps

Perhaps most importantly, the Lokalized placeholder system and expression language allow you to support edge cases that are critical to natural-sounding translations - this can be difficult to achieve using traditional solutions. 

## Getting Started

We'll start with hands-on examples to illustrate key features.  More detailed documentation is available further down in this document.

#### 1. Create Localized Strings Files

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
        "bookCount == 0" : "I didn't read any books."        
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

#### 2. Create a Strings Instance
   
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

#### 3. Ask Strings Instance For Translations

```java
// Lokalized knows how to map numbers to plural cardinalities per locale.
// That is, it understands that 3 means CARDINALITY_OTHER ("books") in English
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
// Strings instance and Lokalized will use it to pick the appropriate locale, e.g. 
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

In Spanish, we have the same number of variants (in a language like Russian or Arabic there would be more!)
But notice how the statements must change to match gender - `uno` becomes `una`, `jugadores` becomes `jugadoras`, etc.

* `Fue uno de los X mejores jugadores de béisbol.`
* `Fue una de las X mejores jugadoras de béisbol.`
* `Él era el mejor jugador de béisbol.`
* `Ella era la mejor jugadora de béisbol.`

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
        "heOrShe == MASCULINE && groupSize <= 1" : "He was the best baseball player."        
      },
      {
        "heOrShe == FEMININE && groupSize <= 1" : "She was the best baseball player."        
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
        "heOrShe == MASCULINE && groupSize <= 1" : "Él era el mejor jugador de béisbol."        
      },
      {
        "heOrShe == FEMININE && groupSize <= 1" : "Ella era la mejor jugadora de béisbol."        
      }
    ]
  }
}
```

#### The Rules, Exercised

Again, we keep the gender and plural logic out of our code entirely and leave rule processing to the translation configuration.

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

#### Recursive Alternatives

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

## Working With Ordinals

Many languages have special forms called _ordinals_ to express a "ranking" in a sequence of numbers.  For example, in English we might say
 
* `Take the 1st left after the intersection`
* `She is my 2nd cousin`
* `I finished the race in 3rd place`

Let's look at an example related to birthdays.

#### English Translation File

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

#### Spanish Translation File

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

#### Ordinals, Exercised

```java
translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  new HashMap<String, Object>() {{
    put("hisOrHer", Gender.MASCULINE);
    put("year", 18);
  }});

// The ORDINALITY_OTHER rule is applied for 18 in English
assertEquals("His 18th birthday party is next week.", translation);

translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  new HashMap<String, Object>() {{
    put("hisOrHer", Gender.FEMININE);
    put("year", 21);
  }});

// The ORDINALITY_ONE rule is applied to any of the "one" numbers (1, 11, 21, ...) in English
assertEquals("Her 21st birthday party is next week.", translation);

translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  new HashMap<String, Object>() {{
    put("hisOrHer", Gender.MASCULINE);
    put("year", 18);
  }}, Locale.forLanguageTag("es"));

// Normal case
assertEquals("Su fiesta de cumpleaños número 18 es la próxima semana.", translation);

translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  new HashMap<String, Object>() {{
    put("year", 1);
  }}, Locale.forLanguageTag("es"));

// Special case for first birthday
assertEquals("Su primera fiesta de cumpleaños es la próxima semana.", translation);

translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
  new HashMap<String, Object>() {{
    put("hisOrHer", Gender.FEMININE);
    put("year", 15);
  }}, Locale.forLanguageTag("es"));

// Special case for a girl's 15th birthday
assertEquals("Su quinceañera es la próxima semana.", translation);
```

## Language Forms

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

* `CARDINALITY_ONE`: Matches 1 (e.g. `1 dollar`)
* `CARDINALITY_OTHER`: Everything else (e.g. `256 dollars`)

##### Russian

* `CARDINALITY_ONE`: Matches 1, 21, 31, ... (e.g. `1 рубль` or `51 рубль`)
* `CARDINALITY_FEW`: Matches 2-4, 22-24, 32-34, ... (e.g. `2 рубля` or `53 рубля`)
* `CARDINALITY_MANY`: Matches 0, 5-20, 25-30, 45-50, ... (e.g. `5 рублей` or `17 рублей`)
* `CARDINALITY_OTHER`: Everything else (e.g. `0,3 руб`, `1,5 руб`)

#### Cardinality Ranges

#### French

* `CARDINALITY_ONE` - `CARDINALITY_ONE` ⇒ `CARDINALITY_ONE` (e.g. 0–1 jour)
* `CARDINALITY_ONE` - `CARDINALITY_OTHER` ⇒ `CARDINALITY_OTHER` (e.g. 0–2 jours)
* `CARDINALITY_OTHER` - `CARDINALITY_OTHER` ⇒ `CARDINALITY_OTHER` (e.g. 2–100 jours)

#### Ordinals

For example: `1st, 2nd, 3rd, 4th, ...`

Similar to plural cardinality, ordinal rules very widely across languages.

Lokalized supports these values according to [CLDR rules](http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html):

* `ORDINALITY_ZERO`
* `ORDINALITY_ONE`
* `ORDINALITY_TWO`
* `ORDINALITY_FEW`
* `ORDINALITY_MANY`
* `ORDINALITY_OTHER`

Again, like cardinal values, ordinals do not necessarily map to the named number. For example, `ORDINALITY_ONE` might apply to any number that ends in `1`.

##### Spanish

* `ORDINALITY_OTHER`: Matches everything (this language has no ordinal form)

##### English

* `ORDINALITY_ONE`: Matches 1, 21, 31, ... (e.g. `1st prize`)
* `ORDINALITY_TWO`: Matches 2, 22, 32, ... (e.g. `22nd prize`)
* `ORDINALITY_FEW`: Matches 3, 23, 33, ... (e.g. `33rd prize`)
* `ORDINALITY_OTHER`: Everything else (e.g. `12th prize`)

##### Italian

* `ORDINALITY_MANY`: Matches 8, 11, 80, 800 (e.g. `Prendi l'8° a destra`)
* `ORDINALITY_OTHER`: Everything else (e.g. `	Prendi la 7° a destra`)

## Alternative Expressions

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

#### What Expressions Currently Support

* Evaluation of "normal" infix expressions of arbitrary complexity (can be nested/parenthesized)
* Comparison of gender, plural, and literal numeric values against each other or user-supplied variables

#### What Expressions Do Not Currently Support

* The unary `!` operator
* Explicit `null` operands (can be implicit, i.e. a `VARIABLE` value)

## Localized Strings File Format

#### Structure

* Each strings file must be UTF-8 encoded and named according to the appropriate IETF BCP 47 language tag, such as `en` or `zh-TW`
* The file must contain a single toplevel JSON object
* The object's keys are the translation keys, e.g. `"I read {{bookCount}} books."`
* The value for a translation key can be a string (simple cases) or an object (complex cases)

With formalities out of the way, let's return to our example `en-GB` strings file, which contains a single translation.  We can use the string form shorthand to concisely express our intent:

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

#### Commentary

This field is used to supply context for the translator, such as how and where the phrase is used in the application.

```json
{
  "I am going on vacation." : {
    "commentary" : "This is one of the options in the user's status update dropdown.",
    "translation" : "I am going on holiday."
  }
}
```

#### Placeholders

A placeholder is any translation value enclosed in a pair of "mustaches" - `{{PLACEHOLDER_NAME_HERE}}`.

You are free to add as many as you like to support your translation.

##### TODO: finish up

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

#### Alternatives

##### TODO: finish up

## Keying Strategy

Ultimately, it is up to you and your team how best to name your localization keys.  Lokalized does not impose key naming constraints. 
  
There are two common approaches - natural language and contextual. Some benefits and drawbacks of each are listed below to help you make the best decision for your situation.
 
#### Natural Language Keys

For example: `"I read {{bookCount}} books."`

##### Pros

* Any developer can create a key by writing a phrase in her native language - no need to coordinate with others or choose arbitrary names
* Placeholders are encoded directly in the key and serve as "automatic" documentation for translators
* There is always a sensible default fallback in the event that a translation is missing

##### Cons

* Context is lost; the same text on one screen might have a completely different meaning on another
* Not suited for large amounts of text, like a software licensing agreement
* Small changes to text require updating every strings file since keys are not "constant"

#### Contextual Keys

For example: `"screen.profile.books-read"`

##### Pros

* It is possible to specifically target app components, which enforces translation context
* Perfect for big chunks of text like legal disclaimers
* "Constant" keys means translations can change without affecting code

##### Cons

* You must come up with names for every key and cross-reference in your localized strings files
* Placeholders are not encoded in the key and must be communicated to translators through some other mechanism
* Requires diligent recordkeeping and inter-team communication ("are our iOS and Android apps using the same keys or are we duplicating effort?")
* There is no default language fallback if no translation is present; users will see your contextual key onscreen 

#### Or - Mix Both!

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

Lokalized was created by [Mark Allen](https://www.revetkn.com) and sponsored by [Product Mog, LLC.](https://www.xmog.com)

Development was aided by

* [SomaFM](http://somafm.com)
* [Scared of Chaka](https://www.youtube.com/watch?v=lYSa2U2St54)
* [Dog Party](https://www.youtube.com/watch?v=GIn0SCdCu5I)