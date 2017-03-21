## Lokalized

Lokalized facilitates natural-sounding software translations.

#### Design Goals

* Complex translation rules can be expressed in a configuration file, not code
* First-class support for gender and plural grammar rules
* Support for multiple platforms (currently JavaScript and Java - Swift and Python ports coming soon)
* Immutability/thread-safety
* No dependencies

#### Design Non-goals

* Support for date/time, number, percentage, and currency formatting (these problems are already solved well)

### Why?

As a developer, it is unrealistic to embed per-locale translation rules in code for every text string.

As a translator, sufficient context and the power of an expression language are required to provide the best translations possible.

As a manager, it is preferable to have a single translation specification that works on the backend, web frontend, and native mobile apps.

Lokalized aims to provide the best solution for all parties.

### What Does That Mean?

Suppose my application needs to display the number of books I've read, and I have a requirement to support both English and Russian users.
In English, we can say this 3 ways:

* `I didn't read any books`
* `I read 1 book`
* `I read 2 books`

English is a complex language, but its plural rules are simple.  We say `book` if there is only 1 and `books` otherwise.

In Russian, we have 4 variants:

* `Я не читал книг`
* `Я прочитал 1 книгу`
* `Я прочитал 2 книги`
* `Я прочитал 5 книг`

Russian has more plural rules than English.  Roughly, we say `книгу` when the number of books ends in 1 (except for 11), `книги` when the number of books ends in 2, 3, or 4, and `книг` when the number of books ends in 5, 6, 7, 8, 9, or 0.

### How Can I Express This?

Our Java code might look like this:

```java
// Your strings instance "knows" the correct locale by consulting a Supplier<Locale>
// that you provide. For example, in a webapp, to find an appropriate locale you
// might consult the HttpServletRequest bound to the current thread
Strings strings = new DefaultStrings.Builder("en", () -> LocalizedStringLoader.loadFromFilesystem(Paths.get("my/strings/directory")))
  .localeSupplier(() -> MyWebContext.getRequest().getLocale())
  .build();

String translated = strings.get("I read {{bookCount}} books", new HashMap<String, Object>() {{
  put("bookCount", 0);
}});

// Prints "I haven't read any books"
out.println(translated);

// Try again with a different value
translated = strings.get("I read {{bookCount}} books", new HashMap<String, Object>() {{
  put("bookCount", 1);
}});

// Prints "I read 1 book"
out.println(translated);
```

OK, let's try Russian strings:

```java
// You can force a target language via explicit Locale parameter - here we use Russian
String translated = strings.get("Hello, world!", Locale.forLanguageTag("ru"));

// Prints "Приветствую, мир"
// (in English: "Hello, world!")
out.println(translated);

// Try the special 0 case
translated = strings.get("I read {{bookCount}} books", new HashMap<String, Object>() {{
  put("bookCount", 0);
}}, Locale.forLanguageTag("ru"));

// Prints "Я не читал книг"
// (in English: "I haven't read any books")
out.println(translated);

// Try again with a different value
translated = strings.get("I read {{bookCount}} books", new HashMap<String, Object>() {{
  put("bookCount", 8);
}}, Locale.forLanguageTag("ru"));

// Prints "Я прочитал 8 книг"
// (in English: "I read 8 books")
out.println(translated);
```

Notice that there is no logic in code for handling the different rules, regardless of language.  As a programmer, you are responsible for passing in whatever context is needed to display the string (in this case, the number of books).  The translator, via the translation file, is responsible for the rest.

#### English Translation File

```json
{
  "I read {{bookCount}} books" : {
    "translation" : "I read {{bookCount}} {{books}}",
    "commentary" : "Message shown when user achieves her book-reading goal for the month",
    "placeholders" : {
      "books" : {
        "value" : "bookCount",
        "translations" : {
          "ONE" : "book",
          "OTHER" : "books"
        }
      }
    },
    "alternatives" : [
      {
        "bookCount == 0" : {
          "translation" : "I haven't read any books"
        }
      }
    ]
  }
}
```

#### Russian Translation File

```json
{
  "Hello, world!" : "Приветствую, мир",
  "I read {{bookCount}} books" : {
    "translation" : "I прочитал {{bookCount}} {{books}}",
    "commentary" : "Message shown when user achieves her book-reading goal for the month",
    "placeholders" : {
      "books" : {
        "value" : "bookCount",
        "translations" : {
          "ONE" : "книга",
          "FEW" : "книг",
          "OTHER" : "книги"
        }
      }
    },
    "alternatives" : [
      {
        "bookCount == 0" : {
          "translation" : "Я не читал книг"
        }
      }
    ]
  }
}
```

#### Notes

* Translation files are recursive.  Each value for `alternatives` can itself have a `translation`, `placeholders`, and `alternatives`
* You may specify parenthesized expressions of arbitrary complexity in `alternatives` to fine-tune your translations.  It's perfectly legal to have an alternative like `gender == MASCULINE && (bookCount > 10 || magazineCount > 20)`
* Each language has a well-defined set of gender and plural rules, with examples outlined below.  You may use these to determine placeholder values and include them in `alternatives` expressions
* Gender rules vary across languages, but the meaning is the same. Valid values are `MASCULINE`, `FEMININE`, and `NEUTER`
* Plural rules vary across languages, and the meanings may differ. Valid values are `ZERO`, `ONE`, `TWO`, `FEW`, `MANY`, `OTHER`. Values do not necessarily map exactly to the named number, e.g. in some languages `ONE` might mean any number ending in `1`, not just `1`.  Most languages only support a few plural forms, some have none at all (represented by `OTHER` in those cases)

#### Example: English Plural Rules

* `ONE`: Matches 1 (e.g. `1 book`)
* `OTHER`: Everything else (e.g. `256 books`)

#### Example: Russian Plural Rules

* `ONE`: Matches 1, 21, 31, 41, 51, 61, ... (e.g. `1 книга` or `171 книга`)
* `FEW`: Matches 2-4, 22-24, 32-34, ... (e.g. `2 книг` or `53 книг`)
* `OTHER`: Everything else (e.g. `27 книги`, `1,5 книги`)

A listing of plural rules for all languages is available at http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html.

### A More Complex Example

Suppose we introduce gender to go along with plurals.  In English, a noun's gender usually does not alter other components of a phrase.  But in Spanish it does.

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
translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  new HashMap<String, Object>() {{
    put("heOrShe", Gender.MASCULINE);
    put("groupSize", 10);
  }});

// Prints "He was one of the 10 best baseball players."
out.println(translated);

translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  new HashMap<String, Object>() {{
    put("heOrShe", Gender.MASCULINE);
    put("groupSize", 1);
  }});

// Prints "He was the best baseball player."
out.println(translated);

translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
  new HashMap<String, Object>() {{
    put("heOrShe", Gender.FEMININE);
    put("groupSize", 3);
  }}, Locale.forLanguageTag("es"));

// Prints "Fue una de las 3 mejores jugadoras de béisbol."
out.println(translated);
```

#### English Translation File

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

### Alternative Expressions

A grammar for alternative expressions follows.

```EBNF
EXPRESSION = OPERAND COMPARISON_OPERATOR OPERAND | "(" EXPRESSION ")" | EXPRESSION BOOLEAN_OPERATOR EXPRESSION ;
OPERAND = VARIABLE | PLURAL | GENDER | NUMBER ;
PLURAL = "ZERO" | "ONE" | "TWO" | "FEW" | "MANY" | "OTHER" ;
GENDER = "MASCULINE" | "FEMININE" | "NEUTER" ;
VARIABLE = { alphabetic character | digit } ;
BOOLEAN_OPERATOR = "&&" | "||" ;
COMPARISON_OPERATOR = "<" | ">" | "<=" | ">=" | "==" | "!=" ;
```

#### What Expressions Currently Support

* Evaluate "normal" infix expressions of arbitrary complexity (can be nested/parenthesized)
* Compare gender, plural, and literal numeric values against each other or user-supplied variables

#### What Expressions Do Not Currently Support

* The unary `!` operator
* Explicit `null` operands (can be implicit via `VARIABLE` value)

#### TODO: finish documentation