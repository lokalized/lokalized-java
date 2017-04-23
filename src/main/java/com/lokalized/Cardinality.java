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
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Language plural cardinality forms.
 * <p>
 * For example, English has two: {@code 1 dog, 2 dogs}, while Welsh has many: {@code 0 cŵn, 1 ci, 2 gi, 3 chi, 4 ci}.
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
        Cardinality.values()).collect(Collectors.toMap(cardinality -> cardinality.name(), cardinality -> cardinality)));
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
   * It is assumed that the number will have no visible decimals.  If you need to specify this, use {@link #forNumber(Number, Integer, Locale)}.
   * <p>
   * See the <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
   * for further details.
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

    return forNumber(number, null, locale);
  }

  /**
   * Gets an appropriate plural cardinality for the given number, visible decimal places, and locale.
   * <p>
   * If {@code visibleDecimalPlaces} is null, then the decimal places of {@code number} will be computed and used.
   * Note that if trailing zeroes are important, e.g. {@code 1.00} instead of {@code 1}, you must either specify a {@link BigDecimal} with appropriate
   * scale or supply a non-null {@code visibleDecimalPlaces} value.
   * <p>
   * See the <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
   * for further details.
   *
   * @param number               the number that drives pluralization, not null
   * @param visibleDecimalPlaces the number of decimal places that will ultimately be displayed, may be null
   * @param locale               the locale that drives pluralization, not null
   * @return an appropriate plural cardinality, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   */
  @Nonnull
  public static Cardinality forNumber(@Nonnull Number number, @Nullable Integer visibleDecimalPlaces, @Nonnull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    // If number of visible decimal places is not specified, compute the number of decimal places.
    // If the number is a BigDecimal, then we have access to trailing zeroes.
    // We cannot know the number of trailing zeroes otherwise - onus is on caller to explicitly specify if she cares about this
    if (visibleDecimalPlaces == null)
      visibleDecimalPlaces = NumberUtils.numberOfDecimalPlaces(number);

    Optional<BiFunction<Number, Number, Cardinality>> cardinalityFunction = CardinalityFamily.cardinalityFunctionForLocale(locale);

    if (!cardinalityFunction.isPresent())
      throw new UnsupportedLocaleException(locale);

    return cardinalityFunction.get().apply(number, visibleDecimalPlaces);
  }

  /**
   * Plural cardinality forms grouped by language family.
   * <p>
   * Each family has a distinct cardinality calculation rule.
   * <p>
   * For example, Germanic languages {@link CardinalityFamily#FAMILY_2} support two {@link Cardinality} types: {@link Cardinality#ONE} for {@code 1}
   * and {@link Cardinality#OTHER} for all other values.
   * <p>
   * See <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
   * for more information.
   */
  enum CardinalityFamily {
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Afrikaans (af)</li>
     * <li>Asu (asa)</li>
     * <li>Azeri (az)</li>
     * <li>Bemba (bem)</li>
     * <li>Bena (bez)</li>
     * <li>Bulgarian (bg)</li>
     * <li>Bodo (brx)</li>
     * <li>Chechen (ce)</li>
     * <li>Chiga (cgg)</li>
     * <li>Cherokee (chr)</li>
     * <li>Central Kurdish (ckb)</li>
     * <li>Divehi (dv)</li>
     * <li>Ewe (ee)</li>
     * <li>Greek (el)</li>
     * <li>Esperanto (eo)</li>
     * <li>Spanish (es)</li>
     * <li>Basque (eu)</li>
     * <li>Faroese (fo)</li>
     * <li>Friulian (fur)</li>
     * <li>Swiss German (gsw)</li>
     * <li>Hausa (ha)</li>
     * <li>Hawaiian (haw)</li>
     * <li>Hungarian (hu)</li>
     * <li>Ngomba (jgo)</li>
     * <li>Machame (jmc)</li>
     * <li>Georgian (ka)</li>
     * <li>Jju (kaj)</li>
     * <li>Tyap (kcg)</li>
     * <li>Kazakh (kk)</li>
     * <li>Kako (kkj)</li>
     * <li>Kalaallisut (kl)</li>
     * <li>Kashmiri (ks)</li>
     * <li>Shambala (ksb)</li>
     * <li>Kurdish (ku)</li>
     * <li>Kirghiz (ky)</li>
     * <li>Luxembourgish (lb)</li>
     * <li>Ganda (lg)</li>
     * <li>Masai (mas)</li>
     * <li>Metaʼ (mgo)</li>
     * <li>Malayalam (ml)</li>
     * <li>Mongolian (mn)</li>
     * <li>Nahuatl (nah)</li>
     * <li>Norwegian Bokmål (nb)</li>
     * <li>North Ndebele (nd)</li>
     * <li>Nepali (ne)</li>
     * <li>Norwegian Nynorsk (nn)</li>
     * <li>Ngiemboon (nnh)</li>
     * <li>Norwegian (no)</li>
     * <li>South Ndebele (nr)</li>
     * <li>Nyanja (ny)</li>
     * <li>Nyankole (nyn)</li>
     * <li>Oromo (om)</li>
     * <li>Odia (or)</li>
     * <li>Ossetic (os)</li>
     * <li>Papiamento (pap)</li>
     * <li>Pushto (ps)</li>
     * <li>Romansh (rm)</li>
     * <li>Rombo (rof)</li>
     * <li>Rwa (rwk)</li>
     * <li>Samburu (saq)</li>
     * <li>Southern Kurdish (sdh)</li>
     * <li>Sena (seh)</li>
     * <li>Shona (sn)</li>
     * <li>Somali (so)</li>
     * <li>Albanian (sq)</li>
     * <li>Swati (ss)</li>
     * <li>Saho (ssy)</li>
     * <li>Southern Sotho (st)</li>
     * <li>Syriac (syr)</li>
     * <li>Tamil (ta)</li>
     * <li>Telugu (te)</li>
     * <li>Teso (teo)</li>
     * <li>Tigre (tig)</li>
     * <li>Turkmen (tk)</li>
     * <li>Tswana (tn)</li>
     * <li>Turkish (tr)</li>
     * <li>Tsonga (ts)</li>
     * <li>Uighur (ug)</li>
     * <li>Uzbek (uz)</li>
     * <li>Venda (ve)</li>
     * <li>Volapük (vo)</li>
     * <li>Vunjo (vun)</li>
     * <li>Walser (wae)</li>
     * <li>Xhosa (xh)</li>
     * <li>Soga (xog)</li>
     * </ul>
     */
    FAMILY_1,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Bambara (bm)</li>
     * <li>Tibetan (bo)</li>
     * <li>Dzongkha (dz)</li>
     * <li>Indonesian (id)</li>
     * <li>Igbo (ig)</li>
     * <li>Sichuan Yi (ii)</li>
     * <li>Japanese (ja)</li>
     * <li>Lojban (jbo)</li>
     * <li>Javanese (jv)</li>
     * <li>Javanese (jw)</li>
     * <li>Makonde (kde)</li>
     * <li>Kabuverdianu (kea)</li>
     * <li>Khmer (km)</li>
     * <li>Korean (ko)</li>
     * <li>Lakota (lkt)</li>
     * <li>Lao (lo)</li>
     * <li>Malay (ms)</li>
     * <li>Myanmar Language (my)</li>
     * <li>N’Ko (nqo)</li>
     * <li>Root (root)</li>
     * <li>Sakha (sah)</li>
     * <li>Koyraboro Senni (ses)</li>
     * <li>Sango (sg)</li>
     * <li>Thai (th)</li>
     * <li>Tongan (to)</li>
     * <li>Vietnamese (vi)</li>
     * <li>Wolof (wo)</li>
     * <li>Yoruba (yo)</li>
     * <li>Cantonese (yue)</li>
     * <li>Mandarin Chinese (zh)</li>
     * </ul>
     */
    FAMILY_2,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Asturian (ast)</li>
     * <li>Catalan (ca)</li>
     * <li>German (de)</li>
     * <li>English (en)</li>
     * <li>Estonian (et)</li>
     * <li>Finnish (fi)</li>
     * <li>Western Frisian (fy)</li>
     * <li>Galician (gl)</li>
     * <li>Italian (it)</li>
     * <li>Dutch (nl)</li>
     * <li>Swedish (sv)</li>
     * <li>Swahili (sw)</li>
     * <li>Urdu (ur)</li>
     * <li>Yiddish (yi)</li>
     * </ul>
     */
    FAMILY_3,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Akan (ak)</li>
     * <li>Bihari (bh)</li>
     * <li>Gurenɛ (guw)</li>
     * <li>Lingala (ln)</li>
     * <li>Malagasy (mg)</li>
     * <li>Northern Sotho (nso)</li>
     * <li>Punjabi (pa)</li>
     * <li>Tigrinya (ti)</li>
     * <li>Walloon (wa)</li>
     * </ul>
     */
    FAMILY_4,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Amharic (am)</li>
     * <li>Assamese (as)</li>
     * <li>Bangla (bn)</li>
     * <li>Persian (fa)</li>
     * <li>Gujarati (gu)</li>
     * <li>Hindi (hi)</li>
     * <li>Kannada (kn)</li>
     * <li>Marathi (mr)</li>
     * <li>Zulu (zu)</li>
     * </ul>
     */
    FAMILY_5,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Inuktitut (iu)</li>
     * <li>Cornish (kw)</li>
     * <li>Nama (naq)</li>
     * <li>Northern Sami (se)</li>
     * <li>Southern Sami (sma)</li>
     * <li>Sami (smi)</li>
     * <li>Lule Sami (smj)</li>
     * <li>Inari Sami (smn)</li>
     * <li>Skolt Sami (sms)</li>
     * </ul>
     */
    FAMILY_6,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Bosnian (bs)</li>
     * <li>Croatian (hr)</li>
     * <li>Serbo-Croatian (sh)</li>
     * <li>Serbian (sr)</li>
     * </ul>
     */
    FAMILY_7,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Fulah (ff)</li>
     * <li>French (fr)</li>
     * <li>Armenian (hy)</li>
     * <li>Kabyle (kab)</li>
     * </ul>
     */
    FAMILY_8,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Arabic (ar)</li>
     * <li>Najdi Arabic (ars)</li>
     * </ul>
     */
    FAMILY_9,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Czech (cs)</li>
     * <li>Slovak (sk)</li>
     * </ul>
     */
    FAMILY_10,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Lower Sorbian (dsb)</li>
     * <li>Upper Sorbian (hsb)</li>
     * </ul>
     */
    FAMILY_11,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Filipino (fil)</li>
     * <li>Tagalog (tl)</li>
     * </ul>
     */
    FAMILY_12,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Latvian (lv)</li>
     * <li>Prussian (prg)</li>
     * </ul>
     */
    FAMILY_13,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Moldovan (mo)</li>
     * <li>Romanian (ro)</li>
     * </ul>
     */
    FAMILY_14,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Russian (ru)</li>
     * <li>Ukrainian (uk)</li>
     * </ul>
     */
    FAMILY_15,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Belarusian (be)</li>
     * </ul>
     */
    FAMILY_16,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Breton (br)</li>
     * </ul>
     */
    FAMILY_17,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Welsh (cy)</li>
     * </ul>
     */
    FAMILY_18,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Danish (da)</li>
     * </ul>
     */
    FAMILY_19,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Irish (ga)</li>
     * </ul>
     */
    FAMILY_20,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Scottish Gaelic (gd)</li>
     * </ul>
     */
    FAMILY_21,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Manx (gv)</li>
     * </ul>
     */
    FAMILY_22,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Hebrew (he)</li>
     * </ul>
     */
    FAMILY_23,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Icelandic (is)</li>
     * </ul>
     */
    FAMILY_24,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Colognian (ksh)</li>
     * </ul>
     */
    FAMILY_25,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Langi (lag)</li>
     * </ul>
     */
    FAMILY_26,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Lithuanian (lt)</li>
     * </ul>
     */
    FAMILY_27,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Macedonian (mk)</li>
     * </ul>
     */
    FAMILY_28,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Maltese (mt)</li>
     * </ul>
     */
    FAMILY_29,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Polish (pl)</li>
     * </ul>
     */
    FAMILY_30,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Portuguese (pt)</li>
     * </ul>
     */
    FAMILY_31,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Tachelhit (shi)</li>
     * </ul>
     */
    FAMILY_32,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Sinhala (si)</li>
     * </ul>
     */
    FAMILY_33,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Slovenian (sl)</li>
     * </ul>
     */
    FAMILY_34,
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Central Atlas Tamazight (tzm)</li>
     * </ul>
     */
    FAMILY_35;

    @Nonnull
    static final Map<CardinalityFamily, BiFunction<Number, Number, Cardinality>> CARDINALITY_FUNCTIONS_BY_CARDINALITY_FAMILY;

    @Nonnull
    static final Map<String, CardinalityFamily> CARDINALITY_FAMILIES_BY_LANGUAGE_TAG;

    /**
     * Cardinality functions are driven by CLDR data.
     * <p>
     * The expression format as specified by http://www.unicode.org/reports/tr35/tr35-numbers.html#Language_Plural_Rules
     * uses the following notation:
     * <ul>
     * <li>{@code n} absolute value of the source number (integer and decimals).</li>
     * <li>{@code i} integer digits of n.</li>
     * <li>{@code v} number of visible fraction digits in n, with trailing zeros.</li>
     * <li>{@code w} number of visible fraction digits in n, without trailing zeros.</li>
     * <li>{@code f} visible fractional digits in n, with trailing zeros.</li>
     * <li>{@code t} visible fractional digits in n, without trailing zeros.</li>
     * </ul>
     * <p>
     * Some examples follow:
     * <ul>
     * <li>{@code n=1: i=1, v=0, w=0, f=0, t=0}</li>
     * <li>{@code n=1.0: i=1, v=1, w=0, f=0, t=0}</li>
     * <li>{@code n=1.00: i=1, v=2, w=0, f=0, t=0}</li>
     * <li>{@code n=1.3: i=1, v=1, w=1, f=3, t=3}</li>
     * <li>{@code n=1.30: i=1, v=2, w=1, f=30, t=3}</li>
     * <li>{@code n=1.03: i=1, v=2, w=2, f=3, t=3}</li>
     * <li>{@code n=1.230: i=1, v=3, w=2, f=230, t=23}</li>
     * </ul>
     */
    static {
      CARDINALITY_FUNCTIONS_BY_CARDINALITY_FAMILY = Collections.unmodifiableMap(new HashMap<CardinalityFamily, BiFunction<Number, Number, Cardinality>>() {{
        put(CardinalityFamily.FAMILY_1, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 1
            if (value == 1)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_2, (number, visibleDecimalPlaces) -> {
          // No cardinality rules for this family
          return OTHER;
        });

        put(CardinalityFamily.FAMILY_3, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();
            long valueAsLong = number.longValue();

            // i = 1 and v = 0
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_4, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 0..1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_5, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 0 or n = 1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_6, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 1
            if (false /* TODO */)
              return ONE;
            // n = 2
            if (false /* TODO */)
              return TWO;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_7, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // v = 0 and i % 10 = 1 and i % 100 != 11 or f % 10 = 1 and f % 100 != 11
            if (false /* TODO */)
              return ONE;
            // v = 0 and i % 10 = 2..4 and i % 100 != 12..14 or f % 10 = 2..4 and f % 100 != 12..14
            if (false /* TODO */)
              return FEW;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_8, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 0,1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_9, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 0
            if (false /* TODO */)
              return ZERO;
            // n = 1
            if (false /* TODO */)
              return ONE;
            // n = 2
            if (false /* TODO */)
              return TWO;
            // n % 100 = 3..10
            if (false /* TODO */)
              return FEW;
            // n % 100 = 11..99
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_10, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 1 and v = 0
            if (false /* TODO */)
              return ONE;
            // i = 2..4 and v = 0
            if (false /* TODO */)
              return FEW;
            // v != 0
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_11, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // v = 0 and i % 100 = 1 or f % 100 = 1
            if (false /* TODO */)
              return ONE;
            // v = 0 and i % 100 = 2 or f % 100 = 2
            if (false /* TODO */)
              return TWO;
            // v = 0 and i % 100 = 3..4 or f % 100 = 3..4
            if (false /* TODO */)
              return FEW;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_12, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // v = 0 and i = 1,2,3 or v = 0 and i % 10 != 4,6,9 or v != 0 and f % 10 != 4,6,9
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_13, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n % 10 = 0 or n % 100 = 11..19 or v = 2 and f % 100 = 11..19
            if (false /* TODO */)
              return ZERO;
            // n % 10 = 1 and n % 100 != 11 or v = 2 and f % 10 = 1 and f % 100 != 11 or v != 2 and f % 10 = 1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_14, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 1 and v = 0
            if (false /* TODO */)
              return ONE;
            // v != 0 or n = 0 or n != 1 and n % 100 = 1..19
            if (false /* TODO */)
              return FEW;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_15, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // v = 0 and i % 10 = 1 and i % 100 != 11
            if (false /* TODO */)
              return ONE;
            // v = 0 and i % 10 = 2..4 and i % 100 != 12..14
            if (false /* TODO */)
              return FEW;
            // v = 0 and i % 10 = 0 or v = 0 and i % 10 = 5..9 or v = 0 and i % 100 = 11..14
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_16, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n % 10 = 1 and n % 100 != 11
            if (false /* TODO */)
              return ONE;
            // n % 10 = 2..4 and n % 100 != 12..14
            if (false /* TODO */)
              return FEW;
            // n % 10 = 0 or n % 10 = 5..9 or n % 100 = 11..14
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_17, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n % 10 = 1 and n % 100 != 11,71,91
            if (false /* TODO */)
              return ONE;
            // n % 10 = 2 and n % 100 != 12,72,92
            if (false /* TODO */)
              return TWO;
            // n % 10 = 3..4,9 and n % 100 != 10..19,70..79,90..99
            if (false /* TODO */)
              return FEW;
            // n != 0 and n % 1000000 = 0
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_18, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 0
            if (false /* TODO */)
              return ZERO;
            // n = 1
            if (false /* TODO */)
              return ONE;
            // n = 2
            if (false /* TODO */)
              return TWO;
            // n = 3
            if (false /* TODO */)
              return FEW;
            // n = 6
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_19, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 1 or t != 0 and i = 0,1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_20, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 1
            if (false /* TODO */)
              return ONE;
            // n = 2
            if (false /* TODO */)
              return TWO;
            // n = 3..6
            if (false /* TODO */)
              return FEW;
            // n = 7..10
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_21, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 1,11
            if (false /* TODO */)
              return ONE;
            // n = 2,12
            if (false /* TODO */)
              return TWO;
            // n = 3..10,13..19
            if (false /* TODO */)
              return FEW;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_22, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // v = 0 and i % 10 = 1
            if (false /* TODO */)
              return ONE;
            // v = 0 and i % 10 = 2
            if (false /* TODO */)
              return TWO;
            // v = 0 and i % 100 = 0,20,40,60,80
            if (false /* TODO */)
              return FEW;
            // v != 0
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_23, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 1 and v = 0
            if (false /* TODO */)
              return ONE;
            // i = 2 and v = 0
            if (false /* TODO */)
              return TWO;
            // v = 0 and n != 0..10 and n % 10 = 0
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_24, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // t = 0 and i % 10 = 1 and i % 100 != 11 or t != 0
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_25, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 0
            if (false /* TODO */)
              return ZERO;
            // n = 1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_26, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 0
            if (false /* TODO */)
              return ZERO;
            // i = 0,1 and n != 0
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_27, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n % 10 = 1 and n % 100 != 11..19
            if (false /* TODO */)
              return ONE;
            // n % 10 = 2..9 and n % 100 != 11..19
            if (false /* TODO */)
              return FEW;
            // f != 0
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_28, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // v = 0 and i % 10 = 1 or f % 10 = 1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_29, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 1
            if (false /* TODO */)
              return ONE;
            // n = 0 or n % 100 = 2..10
            if (false /* TODO */)
              return FEW;
            // n % 100 = 11..19
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_30, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 1 and v = 0
            if (false /* TODO */)
              return ONE;
            // v = 0 and i % 10 = 2..4 and i % 100 != 12..14
            if (false /* TODO */)
              return FEW;
            // v = 0 and i != 1 and i % 10 = 0..1 or v = 0 and i % 10 = 5..9 or v = 0 and i % 100 = 12..14
            if (false /* TODO */)
              return MANY;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_31, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 0..1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_32, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // i = 0 or n = 1
            if (false /* TODO */)
              return ONE;
            // n = 2..10
            if (false /* TODO */)
              return FEW;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_33, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 0,1 or i = 0 and f = 1
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_34, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // v = 0 and i % 100 = 1
            if (false /* TODO */)
              return ONE;
            // v = 0 and i % 100 = 2
            if (false /* TODO */)
              return TWO;
            // v = 0 and i % 100 = 3..4 or v != 0
            if (false /* TODO */)
              return FEW;
          }

          return OTHER;
        });

        put(CardinalityFamily.FAMILY_35, (number, visibleDecimalPlaces) -> {
          if (number != null) {
            double value = number.doubleValue();

            // n = 0..1 or n = 11..99
            if (false /* TODO */)
              return ONE;
          }

          return OTHER;
        });
      }});

      CARDINALITY_FAMILIES_BY_LANGUAGE_TAG = Collections.unmodifiableMap(new HashMap<String, CardinalityFamily>() {{
        put("af", CardinalityFamily.FAMILY_1); // Afrikaans
        put("ak", CardinalityFamily.FAMILY_4); // Akan
        put("am", CardinalityFamily.FAMILY_5); // Amharic
        put("ar", CardinalityFamily.FAMILY_9); // Arabic
        put("ars", CardinalityFamily.FAMILY_9); // Najdi Arabic
        put("as", CardinalityFamily.FAMILY_5); // Assamese
        put("asa", CardinalityFamily.FAMILY_1); // Asu
        put("ast", CardinalityFamily.FAMILY_3); // Asturian
        put("az", CardinalityFamily.FAMILY_1); // Azeri
        put("be", CardinalityFamily.FAMILY_16); // Belarusian
        put("bem", CardinalityFamily.FAMILY_1); // Bemba
        put("bez", CardinalityFamily.FAMILY_1); // Bena
        put("bg", CardinalityFamily.FAMILY_1); // Bulgarian
        put("bh", CardinalityFamily.FAMILY_4); // Bihari
        put("bm", CardinalityFamily.FAMILY_2); // Bambara
        put("bn", CardinalityFamily.FAMILY_5); // Bangla
        put("bo", CardinalityFamily.FAMILY_2); // Tibetan
        put("br", CardinalityFamily.FAMILY_17); // Breton
        put("brx", CardinalityFamily.FAMILY_1); // Bodo
        put("bs", CardinalityFamily.FAMILY_7); // Bosnian
        put("ca", CardinalityFamily.FAMILY_3); // Catalan
        put("ce", CardinalityFamily.FAMILY_1); // Chechen
        put("cgg", CardinalityFamily.FAMILY_1); // Chiga
        put("chr", CardinalityFamily.FAMILY_1); // Cherokee
        put("ckb", CardinalityFamily.FAMILY_1); // Central Kurdish
        put("cs", CardinalityFamily.FAMILY_10); // Czech
        put("cy", CardinalityFamily.FAMILY_18); // Welsh
        put("da", CardinalityFamily.FAMILY_19); // Danish
        put("de", CardinalityFamily.FAMILY_3); // German
        put("dsb", CardinalityFamily.FAMILY_11); // Lower Sorbian
        put("dv", CardinalityFamily.FAMILY_1); // Divehi
        put("dz", CardinalityFamily.FAMILY_2); // Dzongkha
        put("ee", CardinalityFamily.FAMILY_1); // Ewe
        put("el", CardinalityFamily.FAMILY_1); // Greek
        put("en", CardinalityFamily.FAMILY_3); // English
        put("eo", CardinalityFamily.FAMILY_1); // Esperanto
        put("es", CardinalityFamily.FAMILY_1); // Spanish
        put("et", CardinalityFamily.FAMILY_3); // Estonian
        put("eu", CardinalityFamily.FAMILY_1); // Basque
        put("fa", CardinalityFamily.FAMILY_5); // Persian
        put("ff", CardinalityFamily.FAMILY_8); // Fulah
        put("fi", CardinalityFamily.FAMILY_3); // Finnish
        put("fil", CardinalityFamily.FAMILY_12); // Filipino
        put("fo", CardinalityFamily.FAMILY_1); // Faroese
        put("fr", CardinalityFamily.FAMILY_8); // French
        put("fur", CardinalityFamily.FAMILY_1); // Friulian
        put("fy", CardinalityFamily.FAMILY_3); // Western Frisian
        put("ga", CardinalityFamily.FAMILY_20); // Irish
        put("gd", CardinalityFamily.FAMILY_21); // Scottish Gaelic
        put("gl", CardinalityFamily.FAMILY_3); // Galician
        put("gsw", CardinalityFamily.FAMILY_1); // Swiss German
        put("gu", CardinalityFamily.FAMILY_5); // Gujarati
        put("guw", CardinalityFamily.FAMILY_4); // Gurenɛ
        put("gv", CardinalityFamily.FAMILY_22); // Manx
        put("ha", CardinalityFamily.FAMILY_1); // Hausa
        put("haw", CardinalityFamily.FAMILY_1); // Hawaiian
        put("he", CardinalityFamily.FAMILY_23); // Hebrew
        put("hi", CardinalityFamily.FAMILY_5); // Hindi
        put("hr", CardinalityFamily.FAMILY_7); // Croatian
        put("hsb", CardinalityFamily.FAMILY_11); // Upper Sorbian
        put("hu", CardinalityFamily.FAMILY_1); // Hungarian
        put("hy", CardinalityFamily.FAMILY_8); // Armenian
        put("id", CardinalityFamily.FAMILY_2); // Indonesian
        put("ig", CardinalityFamily.FAMILY_2); // Igbo
        put("ii", CardinalityFamily.FAMILY_2); // Sichuan Yi
        put("is", CardinalityFamily.FAMILY_24); // Icelandic
        put("it", CardinalityFamily.FAMILY_3); // Italian
        put("iu", CardinalityFamily.FAMILY_6); // Inuktitut
        put("ja", CardinalityFamily.FAMILY_2); // Japanese
        put("jbo", CardinalityFamily.FAMILY_2); // Lojban
        put("jgo", CardinalityFamily.FAMILY_1); // Ngomba
        put("jmc", CardinalityFamily.FAMILY_1); // Machame
        put("jv", CardinalityFamily.FAMILY_2); // Javanese
        put("jw", CardinalityFamily.FAMILY_2); // Javanese
        put("ka", CardinalityFamily.FAMILY_1); // Georgian
        put("kab", CardinalityFamily.FAMILY_8); // Kabyle
        put("kaj", CardinalityFamily.FAMILY_1); // Jju
        put("kcg", CardinalityFamily.FAMILY_1); // Tyap
        put("kde", CardinalityFamily.FAMILY_2); // Makonde
        put("kea", CardinalityFamily.FAMILY_2); // Kabuverdianu
        put("kk", CardinalityFamily.FAMILY_1); // Kazakh
        put("kkj", CardinalityFamily.FAMILY_1); // Kako
        put("kl", CardinalityFamily.FAMILY_1); // Kalaallisut
        put("km", CardinalityFamily.FAMILY_2); // Khmer
        put("kn", CardinalityFamily.FAMILY_5); // Kannada
        put("ko", CardinalityFamily.FAMILY_2); // Korean
        put("ks", CardinalityFamily.FAMILY_1); // Kashmiri
        put("ksb", CardinalityFamily.FAMILY_1); // Shambala
        put("ksh", CardinalityFamily.FAMILY_25); // Colognian
        put("ku", CardinalityFamily.FAMILY_1); // Kurdish
        put("kw", CardinalityFamily.FAMILY_6); // Cornish
        put("ky", CardinalityFamily.FAMILY_1); // Kirghiz
        put("lag", CardinalityFamily.FAMILY_26); // Langi
        put("lb", CardinalityFamily.FAMILY_1); // Luxembourgish
        put("lg", CardinalityFamily.FAMILY_1); // Ganda
        put("lkt", CardinalityFamily.FAMILY_2); // Lakota
        put("ln", CardinalityFamily.FAMILY_4); // Lingala
        put("lo", CardinalityFamily.FAMILY_2); // Lao
        put("lt", CardinalityFamily.FAMILY_27); // Lithuanian
        put("lv", CardinalityFamily.FAMILY_13); // Latvian
        put("mas", CardinalityFamily.FAMILY_1); // Masai
        put("mg", CardinalityFamily.FAMILY_4); // Malagasy
        put("mgo", CardinalityFamily.FAMILY_1); // Metaʼ
        put("mk", CardinalityFamily.FAMILY_28); // Macedonian
        put("ml", CardinalityFamily.FAMILY_1); // Malayalam
        put("mn", CardinalityFamily.FAMILY_1); // Mongolian
        put("mo", CardinalityFamily.FAMILY_14); // Moldovan
        put("mr", CardinalityFamily.FAMILY_5); // Marathi
        put("ms", CardinalityFamily.FAMILY_2); // Malay
        put("mt", CardinalityFamily.FAMILY_29); // Maltese
        put("my", CardinalityFamily.FAMILY_2); // Myanmar Language
        put("nah", CardinalityFamily.FAMILY_1); // Nahuatl
        put("naq", CardinalityFamily.FAMILY_6); // Nama
        put("nb", CardinalityFamily.FAMILY_1); // Norwegian Bokmål
        put("nd", CardinalityFamily.FAMILY_1); // North Ndebele
        put("ne", CardinalityFamily.FAMILY_1); // Nepali
        put("nl", CardinalityFamily.FAMILY_3); // Dutch
        put("nn", CardinalityFamily.FAMILY_1); // Norwegian Nynorsk
        put("nnh", CardinalityFamily.FAMILY_1); // Ngiemboon
        put("no", CardinalityFamily.FAMILY_1); // Norwegian
        put("nqo", CardinalityFamily.FAMILY_2); // N’Ko
        put("nr", CardinalityFamily.FAMILY_1); // South Ndebele
        put("nso", CardinalityFamily.FAMILY_4); // Northern Sotho
        put("ny", CardinalityFamily.FAMILY_1); // Nyanja
        put("nyn", CardinalityFamily.FAMILY_1); // Nyankole
        put("om", CardinalityFamily.FAMILY_1); // Oromo
        put("or", CardinalityFamily.FAMILY_1); // Odia
        put("os", CardinalityFamily.FAMILY_1); // Ossetic
        put("pa", CardinalityFamily.FAMILY_4); // Punjabi
        put("pap", CardinalityFamily.FAMILY_1); // Papiamento
        put("pl", CardinalityFamily.FAMILY_30); // Polish
        put("prg", CardinalityFamily.FAMILY_13); // Prussian
        put("ps", CardinalityFamily.FAMILY_1); // Pushto
        put("pt", CardinalityFamily.FAMILY_31); // Portuguese
        put("rm", CardinalityFamily.FAMILY_1); // Romansh
        put("ro", CardinalityFamily.FAMILY_14); // Romanian
        put("rof", CardinalityFamily.FAMILY_1); // Rombo
        put("root", CardinalityFamily.FAMILY_2); // Root
        put("ru", CardinalityFamily.FAMILY_15); // Russian
        put("rwk", CardinalityFamily.FAMILY_1); // Rwa
        put("sah", CardinalityFamily.FAMILY_2); // Sakha
        put("saq", CardinalityFamily.FAMILY_1); // Samburu
        put("sdh", CardinalityFamily.FAMILY_1); // Southern Kurdish
        put("se", CardinalityFamily.FAMILY_6); // Northern Sami
        put("seh", CardinalityFamily.FAMILY_1); // Sena
        put("ses", CardinalityFamily.FAMILY_2); // Koyraboro Senni
        put("sg", CardinalityFamily.FAMILY_2); // Sango
        put("sh", CardinalityFamily.FAMILY_7); // Serbo-Croatian
        put("shi", CardinalityFamily.FAMILY_32); // Tachelhit
        put("si", CardinalityFamily.FAMILY_33); // Sinhala
        put("sk", CardinalityFamily.FAMILY_10); // Slovak
        put("sl", CardinalityFamily.FAMILY_34); // Slovenian
        put("sma", CardinalityFamily.FAMILY_6); // Southern Sami
        put("smi", CardinalityFamily.FAMILY_6); // Sami
        put("smj", CardinalityFamily.FAMILY_6); // Lule Sami
        put("smn", CardinalityFamily.FAMILY_6); // Inari Sami
        put("sms", CardinalityFamily.FAMILY_6); // Skolt Sami
        put("sn", CardinalityFamily.FAMILY_1); // Shona
        put("so", CardinalityFamily.FAMILY_1); // Somali
        put("sq", CardinalityFamily.FAMILY_1); // Albanian
        put("sr", CardinalityFamily.FAMILY_7); // Serbian
        put("ss", CardinalityFamily.FAMILY_1); // Swati
        put("ssy", CardinalityFamily.FAMILY_1); // Saho
        put("st", CardinalityFamily.FAMILY_1); // Southern Sotho
        put("sv", CardinalityFamily.FAMILY_3); // Swedish
        put("sw", CardinalityFamily.FAMILY_3); // Swahili
        put("syr", CardinalityFamily.FAMILY_1); // Syriac
        put("ta", CardinalityFamily.FAMILY_1); // Tamil
        put("te", CardinalityFamily.FAMILY_1); // Telugu
        put("teo", CardinalityFamily.FAMILY_1); // Teso
        put("th", CardinalityFamily.FAMILY_2); // Thai
        put("ti", CardinalityFamily.FAMILY_4); // Tigrinya
        put("tig", CardinalityFamily.FAMILY_1); // Tigre
        put("tk", CardinalityFamily.FAMILY_1); // Turkmen
        put("tl", CardinalityFamily.FAMILY_12); // Tagalog
        put("tn", CardinalityFamily.FAMILY_1); // Tswana
        put("to", CardinalityFamily.FAMILY_2); // Tongan
        put("tr", CardinalityFamily.FAMILY_1); // Turkish
        put("ts", CardinalityFamily.FAMILY_1); // Tsonga
        put("tzm", CardinalityFamily.FAMILY_35); // Central Atlas Tamazight
        put("ug", CardinalityFamily.FAMILY_1); // Uighur
        put("uk", CardinalityFamily.FAMILY_15); // Ukrainian
        put("ur", CardinalityFamily.FAMILY_3); // Urdu
        put("uz", CardinalityFamily.FAMILY_1); // Uzbek
        put("ve", CardinalityFamily.FAMILY_1); // Venda
        put("vi", CardinalityFamily.FAMILY_2); // Vietnamese
        put("vo", CardinalityFamily.FAMILY_1); // Volapük
        put("vun", CardinalityFamily.FAMILY_1); // Vunjo
        put("wa", CardinalityFamily.FAMILY_4); // Walloon
        put("wae", CardinalityFamily.FAMILY_1); // Walser
        put("wo", CardinalityFamily.FAMILY_2); // Wolof
        put("xh", CardinalityFamily.FAMILY_1); // Xhosa
        put("xog", CardinalityFamily.FAMILY_1); // Soga
        put("yi", CardinalityFamily.FAMILY_3); // Yiddish
        put("yo", CardinalityFamily.FAMILY_2); // Yoruba
        put("yue", CardinalityFamily.FAMILY_2); // Cantonese
        put("zh", CardinalityFamily.FAMILY_2); // Mandarin Chinese
        put("zu", CardinalityFamily.FAMILY_5); // Zulu
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
    public static Optional<BiFunction<Number, Number, Cardinality>> cardinalityFunctionForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      Optional<CardinalityFamily> cardinalityFamily = cardinalityFamilyForLocale(locale);

      if (!cardinalityFamily.isPresent())
        return Optional.empty();

      return Optional.ofNullable(CARDINALITY_FUNCTIONS_BY_CARDINALITY_FAMILY.get(cardinalityFamily.get()));
    }
  }
}