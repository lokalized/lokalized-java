## Lokalized

Lokalized facilitates natural-sounding software translations.

Design goals:

* Complex translation rules can be expressed in a configuration file, not code
* First-class support for gender and plural grammar rules
* Support for multiple platforms (currently JavaScript and Java - Swift and Python ports coming soon)
* No dependencies

Design non-goals:

* Support for date/time, number, percentage, and currency formatting (Java already does these well)

### Why?

As a developer, it is unrealistic to embed per-locale translation rules in code for every text string.

As a translator, sufficient context and the power of an expression language are required to provide the best translations possible.

As a project manager, it is preferable to have a single translation specification that works on the backend, web frontend, and native mobile apps.

Lokalized aims to provide the best solution for all parties.

### What Does That Mean?

Suppose my application needs to display the number of books I've read, and I have a requirement to support both English and Russian users.
In English, we can say this 3 ways:

* `I haven't read any books`
* `I read 1 book`
* `I read 2 books`

English is a complex language, but its plural rules are simple.  We say `book` if there is only 1 and `books` otherwise.

In Russian, we have 4 variants:

* `Я не читал ни одной книги`
* `Я прочитал 1 книгу`
* `Я прочитал 2 книги`
* `Я прочитал 5 книг`

Russian has more plural rules than English.  Roughly, we say `книгу` when the number of books ends in 1 (except for 11), `книги` when the number of books ends in 2, 3, or 4, and `книг` when the number of books ends in 5, 6, 7, 8, 9, or 0.

### How Can I Express This?

Our Java code might look like this:

```java
Map<String, Object> context = new HashMap<>();
context.put("bookCount", 0);

String translated = strings.get("I read {{bookCount}} books", context);

// Prints "I haven't read any books"
out.println(translated);

context.put("bookCount", 1);

// Translates to system locale - in my case en-US, by default
translated = strings.get("I read {{bookCount}} books", context);

// Prints "I read 1 book"
out.println(translated);
```

OK, let's try Russian strings:

```java
Locale russianLocale = Locale.forLanguageTag("ru");

context.put("bookCount", 0);

translated = strings.get("I read {{bookCount}} books", context, russianLocale);

// Prints "Я не читал ни одной книги"
// (in English: "I haven't read any books")
out.println(translated);
```

Notice that there is no logic in code for handling the different rules, regardless of language.  As a programmer, you are responsible for passing in whatever context is needed to display the string (in this case, the number of books).  The translator, via the translation file, is responsible for the rest.

##### English Translation File

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

##### Russian Translation File

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
          "MANY" : "книг",
          "OTHER" : "книги"
        }
      }
    },
    "alternatives" : [
      {
        "bookCount == 0" : {
          "translation" : "Я не читал ни одной книги"
        }
      }
    ]
  }
}
```

##### Notes

* Translation files are recursive.  Each value for `alternatives` can itself have a `translation`, `placeholders`, and `alternatives`
* You may specify parenthesized expressions of arbitrary complexity in `alternatives` to fine-tune your translations.  It's perfectly legal to have an alternative like `gender == MASCULINE && (bookCount > 10 || magazineCount > 20)`
* Each language has a well-defined set of plural and gender rules, with examples outlined below.  You may use these to determine placeholder values and include them in `alternatives` expressions

##### English Plural Rules

* `ONE`: Cardinality of 1 (e.g. `1 book`)
* `OTHER`: Everything else (e.g. `256 books`)

##### Russian Plural Rules

* `ONE`: Cardinality of 1, 21, 31, 41, 51, 61, ... (e.g. `1 книга` or `171 книга`)
* `FEW`: Cardinality of 2-4, 22-24, 32-34, ... (e.g. `2 книг` or `53 книг`)
* `MANY`: Cardinality of 0, 5-20, 25-30, 35-40, ... (e.g. `6 книг` or `29 книг`)
* `OTHER`: Everything else (e.g. `1,5 книги`)

A listing of rules for all languages is available at http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html.

### A More Complex Example

Suppose we introduce gender.  In English, gender usually does not alter verbs.  But in Russian it does.

This English statement has 6 variants:

* `He hasn't read any books`
* `He read 1 book`
* `He read 2 books`
* `She hasn't read any books`
* `She read 1 book`
* `She read 2 books`

In Russian, we have 10, and notice the `а` suffixes for the feminine gender:

* `Она не читала книг`
* `Она прочитала 1 книгу`
* `Она прочитала 2 книги`
* `Она прочитала 5 книг`
* `Она прочитала 1.5 книги`
* `Он не читал книг`
* `Он прочитал 1 книгу`
* `Он прочитал 2 книги`
* `Он прочитал 5 книг`
* `Он прочитал 1.5 книги`

It is necessary to do more than replace "he/she", we must rewrite other words in the sentence as well.

```json
{
  "{{gender}} read {{bookCount}} books" : {
    "translation" : "{{subject}} {{read}} {{bookCount}} {{books}}",
    "placeholders" : {
      "subject" : {
        "value" : "gender",
        "translations" : {
          "MASCULINE" : "Он",
          "FEMININE" : "Она"
        }
      },
      "read" : {
        "value" : "gender",
        "translations" : {
          "MASCULINE" : "прочитал",
          "FEMININE" : "прочитала"
        }
      },
      "books" : {
        "value" : "bookCount",
        "translations" : {
          "ONE" : "книгу",
          "FEW" : "книги",
          "MANY" : "книг",
          "OTHER" : "книги"
        }
      }
    },
    "alternatives" : [
      {
        "bookCount == 0 && gender == MASCULINE" : {
          "translation" : "Он не читал книг",
        }
      },
      {
        "bookCount == 0 && gender == FEMININE" : {
          "translation" : "Она не читала книг",
        }
      }
    ]
  }
}
```

#### TODO: finish documentation