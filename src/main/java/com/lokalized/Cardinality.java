/*
 * Copyright 2017 Product Mog LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lokalized;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Language plural cardinality forms.
 * <p>
 * For example, English has two: {@code 1 dog} and {@code 2 dogs}, while Welsh has many: {@code 0 cŵn, 1 ci, 2 gi, 3 chi, 4 ci}.
 * <p>
 * See the <a href="http://cldr.unicode.org/index/cldr-spec/plural-rules">Unicode Common Locale Data Repository</a>
 * and its <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">Language Plural Rules</a> for details.
 * <p>
 * Per the CLDR:
 * <blockquote>
 * These categories are only mnemonics -- the names don't necessarily imply the exact contents of the category.
 * For example, for both English and French the number 1 has the category one (singular).
 * <p>
 * In English, every other number has a plural form, and is given the category other.
 * French is similar, except that the number 0 also has the category one and not other or zero, because the form of
 * units qualified by 0 is also singular.
 * <p>
 * This is worth emphasizing: A common mistake is to think that "one" is only for only the number 1.
 * Instead, "one" is a category for any number that behaves like 1. So in some languages, for example,
 * one → numbers that end in "1" (like 1, 21, 151) but that don't end in 11 (like "11, 111, 10311).
 * </blockquote>
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public enum Cardinality implements LanguageForm {
  /**
   * Normally the form used with 0, if it is limited to numbers whose integer values end with 0.
   * <p>
   * For example: the Welsh {@code 0 cŵn, 0 cathod} means {@code 0 dogs, 0 cats} in English.
   */
  ZERO,
  /**
   * The form used with 1.
   * <p>
   * For example: the Welsh {@code 1 ci, 1 gath} means {@code 1 dog, 1 cat} in English.
   */
  ONE,
  /**
   * Normally the form used with 2, if it is limited to numbers whose integer values end with 2.
   * <p>
   * For example: the Welsh {@code 2 gi, 2 gath} means {@code 2 dogs, 2 cats} in English.
   */
  TWO,
  /**
   * The form that falls between {@code TWO} and {@code MANY}.
   * <p>
   * For example: the Welsh {@code  3 chi, 3 cath} means {@code 3 dogs, 3 cats} in English.
   */
  FEW,
  /**
   * The form that falls between {@code FEW} and {@code OTHER}.
   * <p>
   * For example: the Welsh {@code 6 chi, 6 chath} means {@code 6 dogs, 6 cats} in English.
   */
  MANY,
  /**
   * General "catchall" form which comprises any cases not handled by the other forms.
   * <p>
   * For example: the Welsh {@code 4 ci, 4 cath} means {@code 4 dogs, 4 cats} in English.
   */
  OTHER;

  @Nonnull
  static final Map<String, Cardinality> CARDINALITIES_BY_NAME;

  static {
    CARDINALITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Cardinality.values()).collect(Collectors.toMap(cardinal -> cardinal.name(), cardinal -> cardinal)));
  }

  /**
   * Gets the mapping of cardinality names to values.
   *
   * @return the mapping of cardinality names to values, not null
   */
  @Nonnull
  public static Map<String, Cardinality> getCardinalitiesByName() {
    return CARDINALITIES_BY_NAME;
  }

  /**
   * Gets an appropriate plural cardinality for the given number and locale.
   * <p>
   * See <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html</a>
   * for a cheat sheet.
   *
   * @param number the number that drives pluralization, not null
   * @param locale the locale that drives pluralization, not null
   * @return an appropriate plural cardinality, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   */
  @Nonnull
  public static Cardinality forNumber(@Nonnull Number number, @Nonnull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    Optional<Function<Number, Cardinality>> cardinalityFunction = CardinalityFamily.cardinalityFunctionForLocale(locale);

    if (!cardinalityFunction.isPresent())
      throw new UnsupportedLocaleException(locale);

    return cardinalityFunction.get().apply(number);
  }

  /**
   * Plural cardinality forms grouped by language family.
   * <p>
   * Each family has a distinct cardinality calculation rule.
   * <p>
   * For example, Germanic languages support two {@link Cardinality} types: {@link Cardinality#ONE} for {@code 1}
   * and {@link Cardinality#OTHER} for all other values.
   * <p>
   * See <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html</a>
   * for a cheat sheet.
   */
  enum CardinalityFamily {
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Aymara</li>
     * <li>Burmese</li>
     * <li>Chiga</li>
     * <li>Chinese</li>
     * <li>Dzongkha</li>
     * <li>Georgian</li>
     * <li>Indonesian</li>
     * <li>Japanese</li>
     * <li>Kazakh</li>
     * <li>Khmer</li>
     * <li>Kirghiz</li>
     * <li>Korean</li>
     * <li>Lao</li>
     * <li>Lojban</li>
     * <li>Malay</li>
     * <li>Persian</li>
     * <li>Sundanese</li>
     * <li>Tatar</li>
     * <li>Thai</li>
     * <li>Uighur</li>
     * <li>Vietnamese</li>
     * <li>Wolof</li>
     * <li>Yakut</li>
     * </ul>
     */
    NO_PLURALS,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Afrikaans</li>
     * <li>Albanian</li>
     * <li>Angika</li>
     * <li>Aragonese</li>
     * <li>Armenian</li>
     * <li>Assamese</li>
     * <li>Asturian</li>
     * <li>Azerbaijani</li>
     * <li>Basque</li>
     * <li>Bengali</li>
     * <li>Bodo</li>
     * <li>Bulgarian</li>
     * <li>Catalan</li>
     * <li>Chhattisgarhi</li>
     * <li>Danish</li>
     * <li>Dogri</li>
     * <li>Dutch</li>
     * <li>English</li>
     * <li>Esperanto</li>
     * <li>Estonian</li>
     * <li>Faroese</li>
     * <li>Finnish</li>
     * <li>Frisian</li>
     * <li>Friulian</li>
     * <li>Fulah</li>
     * <li>Gallegan</li>
     * <li>German</li>
     * <li>Greek</li>
     * <li>Greenlandic</li>
     * <li>Gujarati</li>
     * <li>Hausa</li>
     * <li>Hebrew</li>
     * <li>Hindi</li>
     * <li>Hungarian</li>
     * <li>Interlingua</li>
     * <li>Italian</li>
     * <li>Kannada</li>
     * <li>Kinyarwanda</li>
     * <li>Kurdish</li>
     * <li>Luxembourgish</li>
     * <li>Maithili</li>
     * <li>Malayalam</li>
     * <li>Manipuri</li>
     * <li>Marathi</li>
     * <li>Mongolian</li>
     * <li>Nahuatl</li>
     * <li>Neapolitan</li>
     * <li>Nepali</li>
     * <li>Northern Sami</li>
     * <li>Norwegian Bokmål</li>
     * <li>Norwegian Nynorsk</li>
     * <li>Oriya</li>
     * <li>Panjabi</li>
     * <li>Papiamento</li>
     * <li>Pedi</li>
     * <li>Piemontese</li>
     * <li>Portuguese</li>
     * <li>Pushto</li>
     * <li>Raeto-Romance</li>
     * <li>Santali</li>
     * <li>Scots</li>
     * <li>Sindhi</li>
     * <li>Sinhalese</li>
     * <li>Somali</li>
     * <li>Songhai</li>
     * <li>Spanish</li>
     * <li>Spanish (Argentina)</li>
     * <li>Swahili</li>
     * <li>Swedish</li>
     * <li>Tamil</li>
     * <li>Telugu</li>
     * <li>Turkmen</li>
     * <li>Urdu</li>
     * <li>Yoruba</li>
     * </ul>
     */
    PLURAL_IF_NOT_EQUAL_1,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Acoli</li>
     * <li>Akan</li>
     * <li>Amharic</li>
     * <li>Filipino</li>
     * <li>French</li>
     * <li>Gun</li>
     * <li>Lingala</li>
     * <li>Malagasy</li>
     * <li>Maori</li>
     * <li>Mapudungun</li>
     * <li>Mauritian Creole</li>
     * <li>Occitan</li>
     * <li>Portuguese (Brazil)</li>
     * <li>Tajik</li>
     * <li>Tigrinya</li>
     * <li>Turkish</li>
     * <li>Uzbek</li>
     * <li>Walloon</li>
     * </ul>
     */
    PLURAL_IF_GREATER_THAN_1,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Belarusian</li>
     * <li>Bosnian</li>
     * <li>Croatian</li>
     * <li>Russian</li>
     * <li>Serbian</li>
     * <li>Ukrainian</li>
     * </ul>
     */
    SLAVIC_1,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Slovenian</li>
     * <li>Sorbian</li>
     * </ul>
     */
    SLAVIC_2,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Latvian</li>
     * </ul>
     */
    LATVIAN,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Scottish Gaelic</li>
     * </ul>
     */
    SCOTTISH_GAELIC,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Romanian</li>
     * </ul>
     */
    ROMANIAN,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Lithuanian</li>
     * </ul>
     */
    LITHUANIAN,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Polish</li>
     * </ul>
     */
    POLISH,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Irish Gaelic</li>
     * </ul>
     */
    IRISH_GAELIC,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Arabic</li>
     * </ul>
     */
    ARABIC,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Macedonian</li>
     * </ul>
     */
    MACEDONIAN,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Icelandic</li>
     * </ul>
     */
    ICELANDIC,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Breton</li>
     * </ul>
     */
    BRETON,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Welsh</li>
     * </ul>
     */
    WELSH,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Kashubian</li>
     * </ul>
     */
    KASHUBIAN,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Javanese</li>
     * </ul>
     */
    JAVANESE,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Cornish</li>
     * </ul>
     */
    CORNISH,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Montenegro</li>
     * </ul>
     */
    MONTENEGRO,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Mandinka</li>
     * </ul>
     */
    MANDINKA,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Maltese</li>
     * </ul>
     */
    MALTESE,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Czech</li>
     * </ul>
     */
    CZECH,
    /**
     * Languages include:
     * <p>
     * <ul>
     * <li>Slovak</li>
     * </ul>
     */
    SLOVAK;

    @Nonnull
    static final Map<CardinalityFamily, Function<Number, Cardinality>> CARDINALITY_FUNCTIONS_BY_CARDINAL_FAMILY;

    @Nonnull
    static final Map<String, CardinalityFamily> CARDINALITY_FAMILIES_BY_LANGUAGE_TAG;

    static {
      CARDINALITY_FUNCTIONS_BY_CARDINAL_FAMILY = Collections.unmodifiableMap(new HashMap<CardinalityFamily, Function<Number, Cardinality>>() {{
        put(CardinalityFamily.ARABIC, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.BRETON, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.CORNISH, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.CZECH, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value % 100 == 1)
              return ONE;
            if (value % 100 >= 2 && value % 100 <= 4)
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.ICELANDIC, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.IRISH_GAELIC, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.JAVANESE, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.KASHUBIAN, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.LATVIAN, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value % 10 == 1 && value % 100 != 11)
              return ONE;
            if (value != 0)
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.LITHUANIAN, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value % 10 == 1 && value % 100 != 11)
              return ONE;
            if (value % 100 != 12 && value % 10 == 2)
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.MACEDONIAN, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value % 10 == 1)
              return ONE;
            if (value % 10 == 2)
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.MALTESE, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.MANDINKA, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.MONTENEGRO, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.NO_PLURALS, (number) -> {
          return OTHER;
        });
        put(CardinalityFamily.PLURAL_IF_GREATER_THAN_1, (number) -> {
          return number != null && number.doubleValue() > 1 ? ONE : OTHER;
        });
        put(CardinalityFamily.PLURAL_IF_NOT_EQUAL_1, (number) -> {
          return number != null && number.doubleValue() == 1 ? ONE : OTHER;
        });
        put(CardinalityFamily.POLISH, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value == 1)
              return ONE;
            if (value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 > 20))
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.ROMANIAN, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value == 1)
              return ONE;
            if (value == 0 || (value % 100 >= 1 && value % 100 <= 20))
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.SCOTTISH_GAELIC, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.SLAVIC_1, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value % 10 == 1 && value % 100 != 11)
              return ONE;
            if (value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 > 20))
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.SLAVIC_2, (number) -> {
          throw new UnsupportedOperationException();
        });
        put(CardinalityFamily.SLOVAK, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value == 1)
              return ONE;
            if (value >= 2 && value <= 4)
              return FEW;
          }

          return OTHER;
        });
        put(CardinalityFamily.WELSH, (number) -> {
          throw new UnsupportedOperationException();
        });
      }});

      CARDINALITY_FAMILIES_BY_LANGUAGE_TAG = Collections.unmodifiableMap(new HashMap<String, CardinalityFamily>() {{
        put("ach", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Acholi
        put("af", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Afrikaans
        put("ak", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Akan
        put("am", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Amharic
        put("an", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Aragonese
        put("anp", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Angika
        put("ar", CardinalityFamily.ARABIC); // Arabic
        put("arn", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Mapudungun
        put("as", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Assamese
        put("ast", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Asturian
        put("ay", CardinalityFamily.NO_PLURALS); // Aymará
        put("az", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Azerbaijani
        put("be", CardinalityFamily.SLAVIC_1); // Belarusian
        put("bg", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Bulgarian
        put("bn", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Bengali
        put("br", CardinalityFamily.BRETON); // Breton
        put("brx", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Bodo
        put("bs", CardinalityFamily.SLAVIC_1); // Bosnian
        put("ca", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Catalan
        put("cgg", CardinalityFamily.NO_PLURALS); // Chiga
        put("cs", CardinalityFamily.CZECH); // Czech
        put("csb", CardinalityFamily.KASHUBIAN); // Kashubian
        put("cy", CardinalityFamily.WELSH); // Welsh
        put("da", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Danish
        put("de", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // German
        put("doi", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Dogri
        put("dz", CardinalityFamily.NO_PLURALS); // Dzongkha
        put("el", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Greek
        put("en", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // English
        put("eo", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Esperanto
        put("es", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Spanish
        put("es-AR", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Argentinean Spanish
        put("et", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Estonian
        put("eu", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Basque
        put("fa", CardinalityFamily.NO_PLURALS); // Persian
        put("ff", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Fulah
        put("fi", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Finnish
        put("fil", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Filipino
        put("fo", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Faroese
        put("fr", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // French
        put("fur", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Friulian
        put("fy", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Frisian
        put("ga", CardinalityFamily.IRISH_GAELIC); // Irish Gaelic
        put("gd", CardinalityFamily.SCOTTISH_GAELIC); // Scottish Gaelic
        put("gl", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Galician
        put("gu", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Gujarati
        put("gun", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Gun
        put("ha", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Hausa
        put("he", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Hebrew
        put("hi", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Hindi
        put("hne", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Chhattisgarhi
        put("hr", CardinalityFamily.SLAVIC_1); // Croatian
        put("hu", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Hungarian
        put("hy", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Armenian
        put("ia", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Interlingua
        put("id", CardinalityFamily.NO_PLURALS); // Indonesian
        put("is", CardinalityFamily.ICELANDIC); // Icelandic
        put("it", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Italian
        put("ja", CardinalityFamily.NO_PLURALS); // Japanese
        put("jbo", CardinalityFamily.NO_PLURALS); // Lojban
        put("jv", CardinalityFamily.JAVANESE); // Javanese
        put("ka", CardinalityFamily.NO_PLURALS); // Georgian
        put("kk", CardinalityFamily.NO_PLURALS); // Kazakh
        put("kl", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Greenlandic
        put("km", CardinalityFamily.NO_PLURALS); // Khmer
        put("kn", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Kannada
        put("ko", CardinalityFamily.NO_PLURALS); // Korean
        put("ku", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Kurdish
        put("kw", CardinalityFamily.CORNISH); // Cornish
        put("ky", CardinalityFamily.NO_PLURALS); // Kyrgyz
        put("lb", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Letzeburgesch
        put("ln", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Lingala
        put("lo", CardinalityFamily.NO_PLURALS); // Lao
        put("lt", CardinalityFamily.LITHUANIAN); // Lithuanian
        put("lv", CardinalityFamily.LATVIAN); // Latvian
        put("mai", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Maithili
        put("me", CardinalityFamily.MONTENEGRO); // Montenegro
        put("mfe", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Mauritian Creole
        put("mg", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Malagasy
        put("mi", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Maori
        put("mk", CardinalityFamily.MACEDONIAN); // Macedonian
        put("ml", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Malayalam
        put("mn", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Mongolian
        put("mni", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Manipuri
        put("mnk", CardinalityFamily.MANDINKA); // Mandinka
        put("mr", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Marathi
        put("ms", CardinalityFamily.NO_PLURALS); // Malay
        put("mt", CardinalityFamily.MALTESE); // Maltese
        put("my", CardinalityFamily.NO_PLURALS); // Burmese
        put("nah", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Nahuatl
        put("nap", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Neapolitan
        put("nb", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Norwegian Bokmål
        put("ne", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Nepali
        put("nl", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Dutch
        put("nn", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Norwegian Nyorsk
        put("nso", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Northern Sotho
        put("oc", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Occitan
        put("or", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Oriya
        put("pa", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Punjabi
        put("pap", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Papiamento
        put("pl", CardinalityFamily.POLISH); // Polish
        put("pms", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Piemontese
        put("ps", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Pashto
        put("pt", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Portuguese
        put("pt-BR", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Brazilian Portuguese
        put("rm", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Romansh
        put("ro", CardinalityFamily.ROMANIAN); // Romanian
        put("ru", CardinalityFamily.SLAVIC_1); // Russian
        put("rw", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Kinyarwanda
        put("sah", CardinalityFamily.NO_PLURALS); // Yakut
        put("sat", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Santali
        put("sco", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Scots
        put("sd", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Sindhi
        put("se", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Northern Sami
        put("si", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Sinhala
        put("sk", CardinalityFamily.SLOVAK); // Slovak
        put("sl", CardinalityFamily.SLAVIC_2); // Slovenian
        put("so", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Somali
        put("son", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Songhay
        put("sq", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Albanian
        put("sr", CardinalityFamily.SLAVIC_1); // Serbian
        put("su", CardinalityFamily.NO_PLURALS); // Sudanese
        put("sv", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Swedish
        put("sw", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Swahili
        put("ta", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Tamil
        put("te", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Telugu
        put("tg", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Tajik
        put("th", CardinalityFamily.NO_PLURALS); // Thai
        put("ti", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Tigrinya
        put("tk", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Turkmen
        put("tr", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Turkish
        put("tt", CardinalityFamily.NO_PLURALS); // Tatar
        put("ug", CardinalityFamily.NO_PLURALS); // Uyghur
        put("uk", CardinalityFamily.SLAVIC_1); // Ukrainian
        put("ur", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Urdu
        put("uz", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Uzbek
        put("vi", CardinalityFamily.NO_PLURALS); // Vietnamese
        put("wa", CardinalityFamily.PLURAL_IF_GREATER_THAN_1); // Walloon
        put("wen", CardinalityFamily.SLAVIC_2); // Sorbian
        put("wo", CardinalityFamily.NO_PLURALS); // Wolof
        put("yo", CardinalityFamily.PLURAL_IF_NOT_EQUAL_1); // Yoruba
        put("zh", CardinalityFamily.NO_PLURALS); // Chinese
      }});
    }

    /**
     * Gets an appropriate plural cardinality family for the given locale.
     *
     * @param locale the locale to check, not null
     * @return the appropriate plural cardinality family (if one exists) for the given locale, not null
     */
    @Nonnull
    public static Optional<CardinalityFamily> cardinalityFamilyForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      String language = locale.getLanguage();
      String country = locale.getCountry();

      CardinalityFamily cardinalityFamily = null;

      if (language != null && country != null)
        cardinalityFamily = CARDINALITY_FAMILIES_BY_LANGUAGE_TAG.get(format("%s-%s", language, country));

      if (cardinalityFamily != null)
        return Optional.of(cardinalityFamily);

      if (language != null)
        cardinalityFamily = CARDINALITY_FAMILIES_BY_LANGUAGE_TAG.get(language);

      return Optional.ofNullable(cardinalityFamily);
    }

    /**
     * Gets an appropriate plural cardinality determination function for the given locale.
     *
     * @param locale the locale to check, not null
     * @return the appropriate plural cardinality determination function (if one exists) for the given locale, not null
     */
    @Nonnull
    public static Optional<Function<Number, Cardinality>> cardinalityFunctionForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      Optional<CardinalityFamily> cardinalityFamily = cardinalityFamilyForLocale(locale);

      if (!cardinalityFamily.isPresent())
        return Optional.empty();

      return Optional.ofNullable(CARDINALITY_FUNCTIONS_BY_CARDINAL_FAMILY.get(cardinalityFamily.get()));
    }
  }
}