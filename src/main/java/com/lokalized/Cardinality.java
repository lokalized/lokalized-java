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

import com.lokalized.Maps.MapEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lokalized.NumberUtils.equal;
import static com.lokalized.NumberUtils.inRange;
import static com.lokalized.NumberUtils.inSet;
import static com.lokalized.NumberUtils.notEqual;
import static com.lokalized.NumberUtils.notInRange;
import static com.lokalized.NumberUtils.notInSet;
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
  private static final BigInteger BIG_INTEGER_0;
  @Nonnull
  private static final BigInteger BIG_INTEGER_1;
  @Nonnull
  private static final BigInteger BIG_INTEGER_2;
  @Nonnull
  private static final BigInteger BIG_INTEGER_3;
  @Nonnull
  private static final BigInteger BIG_INTEGER_4;
  @Nonnull
  private static final BigInteger BIG_INTEGER_5;
  @Nonnull
  private static final BigInteger BIG_INTEGER_6;
  @Nonnull
  private static final BigInteger BIG_INTEGER_9;
  @Nonnull
  private static final BigInteger BIG_INTEGER_10;
  @Nonnull
  private static final BigInteger BIG_INTEGER_11;
  @Nonnull
  private static final BigInteger BIG_INTEGER_12;
  @Nonnull
  private static final BigInteger BIG_INTEGER_14;
  @Nonnull
  private static final BigInteger BIG_INTEGER_19;
  @Nonnull
  private static final BigInteger BIG_INTEGER_20;
  @Nonnull
  private static final BigInteger BIG_INTEGER_40;
  @Nonnull
  private static final BigInteger BIG_INTEGER_60;
  @Nonnull
  private static final BigInteger BIG_INTEGER_80;
  @Nonnull
  private static final BigInteger BIG_INTEGER_100;

  @Nonnull
  private static final BigDecimal BIG_DECIMAL_0;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_1;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_2;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_3;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_4;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_5;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_6;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_7;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_9;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_10;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_11;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_12;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_13;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_14;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_19;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_70;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_71;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_72;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_79;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_90;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_91;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_92;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_99;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_100;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_1_000_000;

  @Nonnull
  static final Map<String, Cardinality> CARDINALITIES_BY_NAME;

  static {
    BIG_INTEGER_0 = BigInteger.ZERO;
    BIG_INTEGER_1 = BigInteger.ONE;
    BIG_INTEGER_2 = BigInteger.valueOf(2);
    BIG_INTEGER_3 = BigInteger.valueOf(3);
    BIG_INTEGER_4 = BigInteger.valueOf(4);
    BIG_INTEGER_5 = BigInteger.valueOf(5);
    BIG_INTEGER_6 = BigInteger.valueOf(6);
    BIG_INTEGER_9 = BigInteger.valueOf(9);
    BIG_INTEGER_10 = BigInteger.TEN;
    BIG_INTEGER_11 = BigInteger.valueOf(11);
    BIG_INTEGER_12 = BigInteger.valueOf(12);
    BIG_INTEGER_14 = BigInteger.valueOf(14);
    BIG_INTEGER_19 = BigInteger.valueOf(19);
    BIG_INTEGER_20 = BigInteger.valueOf(20);
    BIG_INTEGER_40 = BigInteger.valueOf(40);
    BIG_INTEGER_60 = BigInteger.valueOf(60);
    BIG_INTEGER_80 = BigInteger.valueOf(80);
    BIG_INTEGER_100 = BigInteger.valueOf(100);

    BIG_DECIMAL_0 = BigDecimal.ZERO;
    BIG_DECIMAL_1 = BigDecimal.ONE;
    BIG_DECIMAL_2 = BigDecimal.valueOf(2);
    BIG_DECIMAL_3 = BigDecimal.valueOf(3);
    BIG_DECIMAL_4 = BigDecimal.valueOf(4);
    BIG_DECIMAL_5 = BigDecimal.valueOf(5);
    BIG_DECIMAL_6 = BigDecimal.valueOf(6);
    BIG_DECIMAL_7 = BigDecimal.valueOf(7);
    BIG_DECIMAL_9 = BigDecimal.valueOf(9);
    BIG_DECIMAL_10 = BigDecimal.TEN;
    BIG_DECIMAL_11 = BigDecimal.valueOf(11);
    BIG_DECIMAL_12 = BigDecimal.valueOf(12);
    BIG_DECIMAL_13 = BigDecimal.valueOf(13);
    BIG_DECIMAL_14 = BigDecimal.valueOf(14);
    BIG_DECIMAL_19 = BigDecimal.valueOf(19);
    BIG_DECIMAL_70 = BigDecimal.valueOf(70);
    BIG_DECIMAL_71 = BigDecimal.valueOf(71);
    BIG_DECIMAL_72 = BigDecimal.valueOf(72);
    BIG_DECIMAL_79 = BigDecimal.valueOf(79);
    BIG_DECIMAL_90 = BigDecimal.valueOf(90);
    BIG_DECIMAL_91 = BigDecimal.valueOf(91);
    BIG_DECIMAL_92 = BigDecimal.valueOf(92);
    BIG_DECIMAL_99 = BigDecimal.valueOf(99);
    BIG_DECIMAL_100 = BigDecimal.valueOf(100);
    BIG_DECIMAL_1_000_000 = BigDecimal.valueOf(1_000_000);

    CARDINALITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Cardinality.values()).collect(Collectors.toMap(cardinality -> cardinality.name(), cardinality -> cardinality)));
  }

  /**
   * Gets an appropriate plural cardinality for the given number and locale.
   * <p>
   * When determining cardinality, the decimal places of {@code number} will be computed and used.
   * Note that if trailing zeroes are important, e.g. {@code 1.00} instead of {@code 1}, you must either specify a {@link BigDecimal} with appropriate
   * scale or supply a non-null {@code visibleDecimalPlaces} value.
   * <p>
   * If you do not provide a {@link BigDecimal} and wish to manually specify the number of visible decimals, use {@link #forNumber(Number, Integer, Locale)} instead.
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

    boolean numberIsBigDecimal = number instanceof BigDecimal;
    BigDecimal numberAsBigDecimal = null;

    // If number of visible decimal places is not specified, compute the number of decimal places.
    // If the number is a BigDecimal, then we have access to trailing zeroes.
    // We cannot know the number of trailing zeroes otherwise - onus is on caller to explicitly specify if she cares about this
    if (visibleDecimalPlaces == null && !numberIsBigDecimal) {
      numberAsBigDecimal = NumberUtils.toBigDecimal(number);
      numberAsBigDecimal.setScale(NumberUtils.numberOfDecimalPlaces(number), BigDecimal.ROUND_FLOOR);
    } else if (visibleDecimalPlaces != null && numberIsBigDecimal) {
      numberAsBigDecimal = (BigDecimal) number;
      numberAsBigDecimal.setScale(visibleDecimalPlaces, BigDecimal.ROUND_FLOOR);
    }

    if (numberAsBigDecimal == null)
      numberAsBigDecimal = NumberUtils.toBigDecimal(number);

    Optional<CardinalityFamily> cardinalityFamily = CardinalityFamily.cardinalityFamilyForLocale(locale);

    // TODO: throwing an exception might not be the best solution here...need to think about it
    if (!cardinalityFamily.isPresent())
      throw new UnsupportedLocaleException(locale);

    return cardinalityFamily.get().getCardinalityFunction().apply(numberAsBigDecimal);
  }

  /**
   * Gets an appropriate plural cardinality for the given range (start, end) and locale.
   * <p>
   * For example, a range might be {@code "1-3 hours"}.
   * <p>
   * Note that the cardinality of the end of the range does not necessarily
   * determine the range's cardinality.  In English, we say {@code "0–1 days"} - the value {@code 1} is {@code CARDINALITY_ONE}
   * but the range is {@code CARDINALITY_OTHER}.
   * <p>
   * See the <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
   * for further details.
   *
   * @param start  the cardinality for the start of the range, not null
   * @param end    the cardinality for the end of the range, not null
   * @param locale the locale that drives pluralization, not null
   * @return an appropriate plural cardinality for the range, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   */
  @Nonnull
  public static Cardinality forRange(@Nonnull Cardinality start, @Nonnull Cardinality end, @Nonnull Locale locale) {
    requireNonNull(start);
    requireNonNull(end);
    requireNonNull(locale);

    Optional<CardinalityRangeFamily> cardinalityRangeFamily = CardinalityRangeFamily.cardinalityRangeFamilyForLocale(locale);

    // TODO: throwing an exception might not be the best solution here...need to think about it
    if (!cardinalityRangeFamily.isPresent())
      throw new UnsupportedLocaleException(locale);

    CardinalityRange cardinalityRange = CardinalityRange.of(start, end);
    Cardinality cardinality = cardinalityRangeFamily.get().getCardinalitiesByCardinalityRange().get(cardinalityRange);

    return cardinality == null ? Cardinality.OTHER : cardinality;
  }

  /**
   * Gets the set of cardinalities supported for the given locale.
   * <p>
   * The empty set will be returned if the locale is not supported.
   * <p>
   * The set's values are sorted by the natural ordering of the {@link Cardinality} enumeration.
   *
   * @param locale the locale to use for lookup, not null
   * @return the cardinalities supported by the given locale, not null
   */
  @Nonnull
  public static SortedSet<Cardinality> supportedCardinalitiesForLocale(@Nonnull Locale locale) {
    requireNonNull(locale);

    Optional<CardinalityFamily> cardinalityFamily = CardinalityFamily.cardinalityFamilyForLocale(locale);
    return cardinalityFamily.isPresent() ? cardinalityFamily.get().getSupportedCardinalities() : Collections.emptySortedSet();
  }

  /**
   * Gets a mapping of cardinalities to example integer values for the given locale.
   * <p>
   * The empty map will be returned if the locale is not supported or if no example values are available.
   * <p>
   * The map's keys are sorted by the natural ordering of the {@link Cardinality} enumeration.
   *
   * @param locale the locale to use for lookup, not null
   * @return a mapping of cardinalities to example integer values, not null
   */
  @Nonnull
  public static SortedMap<Cardinality, Range<Integer>> exampleIntegerValuesForLocale(@Nonnull Locale locale) {
    requireNonNull(locale);

    Optional<CardinalityFamily> cardinalityFamily = CardinalityFamily.cardinalityFamilyForLocale(locale);
    return cardinalityFamily.isPresent() ? cardinalityFamily.get().getExampleIntegerValuesByCardinality() : Collections.emptySortedMap();
  }

  /**
   * Gets a mapping of cardinalities to example decimal values for the given locale.
   * <p>
   * The empty map will be returned if the locale is not supported or if no example values are available.
   * <p>
   * The map's keys are sorted by the natural ordering of the {@link Cardinality} enumeration.
   *
   * @param locale the locale to use for lookup, not null
   * @return a mapping of cardinalities to example decimal values, not null
   */
  @Nonnull
  public static SortedMap<Cardinality, Range<BigDecimal>> exampleDecimalValuesForLocale(@Nonnull Locale locale) {
    requireNonNull(locale);

    Optional<CardinalityFamily> cardinalityFamily = CardinalityFamily.cardinalityFamilyForLocale(locale);
    return cardinalityFamily.isPresent() ? cardinalityFamily.get().getExampleDecimalValuesByCardinality() : Collections.emptySortedMap();
  }

  /**
   * Gets the ISO 639 language codes for which cardinality operations are supported.
   * <p>
   * The set's values are ISO 639 codes and therefore sorted using English collation.
   *
   * @return the ISO 639 language codes for which cardinality operations are supported, not null
   */
  @Nonnull
  public static SortedSet<String> getSupportedLanguageCodes() {
    return CardinalityFamily.getSupportedLanguageCodes();
  }

  /**
   * Gets the mapping of cardinality names to values.
   *
   * @return the mapping of cardinality names to values, not null
   */
  @Nonnull
  static Map<String, Cardinality> getCardinalitiesByName() {
    return CARDINALITIES_BY_NAME;
  }

  /**
   * Plural cardinality forms grouped by language family.
   * <p>
   * Each family has a distinct cardinality calculation rule.
   * <p>
   * For example, Germanic languages {@link CardinalityFamily#FAMILY_3} support two {@link Cardinality} types: {@link Cardinality#ONE} for {@code 1}
   * and {@link Cardinality#OTHER} for all other values.
   * <p>
   * See <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
   * for more information.
   * <p>
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
     * <li>Greenlandic (kl)</li>
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
     * <li>Ossetian (os)</li>
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
    FAMILY_1(
        (n) -> {
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6")))
        )
    ),

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
     * <li>Burmese (my)</li>
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
    FAMILY_2(
        (n) -> {
          // No cardinality rules for this family
          return OTHER;
        },
        Sets.sortedSet(
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

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
    FAMILY_3(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);
          int v = NumberUtils.numberOfDecimalPlaces(n);

          // i = 1 and v = 0
          if (equal(i, BIG_INTEGER_1) && v == 0)
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Akan (ak)</li>
     * <li>Bihari (bh)</li>
     * <li>Gun (guw)</li>
     * <li>Lingala (ln)</li>
     * <li>Malagasy (mg)</li>
     * <li>Northern Sotho (nso)</li>
     * <li>Punjabi (pa)</li>
     * <li>Tigrinya (ti)</li>
     * <li>Walloon (wa)</li>
     * </ul>
     */
    FAMILY_4(
        (n) -> {
          // n = 0..1
          if (inRange(n, BIG_DECIMAL_0, BIG_DECIMAL_1))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(0, 1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7")))
        )
    ),

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
    FAMILY_5(
        (n) -> {
          // i = 0 or n = 1
          BigInteger i = NumberUtils.integerComponent(n);

          if (equal(i, BIG_INTEGER_0) || equal(n, BIG_DECIMAL_1))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(0, 1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(new BigDecimal("0.0"), new BigDecimal("0.01"), new BigDecimal("0.02"), new BigDecimal("0.03"), new BigDecimal("0.04"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("1.8"), new BigDecimal("1.9"), new BigDecimal("2.0"), new BigDecimal("2.1"), new BigDecimal("2.2"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("2.5"), new BigDecimal("2.6")))
        )
    ),

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
    FAMILY_6(
        (n) -> {
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2
          if (equal(n, BIG_DECIMAL_2))
            return TWO;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.TWO, Range.ofFiniteValues(2)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6")))
        )
    ),

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
    FAMILY_7(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);
          BigInteger f = NumberUtils.fractionalComponent(n);

          // v = 0 and i % 10 = 1 and i % 100 != 11 or f % 10 = 1 and f % 100 != 11
          if ((v == 0 && equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_1) && notEqual(i.mod(BIG_INTEGER_100), BIG_INTEGER_11))
              || (equal(f.mod(BIG_INTEGER_10), BIG_INTEGER_1) && notEqual(f.mod(BIG_INTEGER_100), BIG_INTEGER_11)))
            return ONE;
          // v = 0 and i % 10 = 2..4 and i % 100 != 12..14 or f % 10 = 2..4 and f % 100 != 12..14
          if ((v == 0
              && inRange(i.mod(BIG_INTEGER_10), BIG_INTEGER_2, BIG_INTEGER_4)
              && notInRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_12, BIG_INTEGER_14))
              ||
              (inRange(f.mod(BIG_INTEGER_10), BIG_INTEGER_2, BIG_INTEGER_4)
                  && notInRange(f.mod(BIG_INTEGER_100), BIG_INTEGER_12, BIG_INTEGER_14)))
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(2, 3, 4, 22, 23, 24, 32, 33, 34, 42, 43, 44, 52, 53, 54, 62, 102, 1002)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("1.1"), new BigDecimal("2.1"), new BigDecimal("3.1"), new BigDecimal("4.1"), new BigDecimal("5.1"), new BigDecimal("6.1"), new BigDecimal("7.1"), new BigDecimal("10.1"), new BigDecimal("100.1"), new BigDecimal("1000.1"))),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("2.2"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("3.2"), new BigDecimal("3.3"), new BigDecimal("3.4"), new BigDecimal("4.2"), new BigDecimal("4.3"), new BigDecimal("4.4"), new BigDecimal("5.2"), new BigDecimal("10.2"), new BigDecimal("100.2"), new BigDecimal("1000.2"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("1.8"), new BigDecimal("1.9"), new BigDecimal("2.0"), new BigDecimal("2.5"), new BigDecimal("2.6"), new BigDecimal("2.7")))
        )
    ),

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
    FAMILY_8(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 0,1
          if (inSet(i, BIG_INTEGER_0, BIG_INTEGER_1))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(0, 1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("2.0"), new BigDecimal("2.1"), new BigDecimal("2.2"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("2.5"), new BigDecimal("2.6"), new BigDecimal("2.7"), new BigDecimal("2.8"), new BigDecimal("2.9"), new BigDecimal("3.0"), new BigDecimal("3.1"), new BigDecimal("3.2"), new BigDecimal("3.3"), new BigDecimal("3.4"), new BigDecimal("3.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Arabic (ar)</li>
     * <li>Najdi Arabic (ars)</li>
     * </ul>
     */
    FAMILY_9(
        (n) -> {
          // n = 0
          if (equal(n, BIG_DECIMAL_0))
            return ZERO;
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2
          if (equal(n, BIG_DECIMAL_2))
            return TWO;
          // n % 100 = 3..10
          if (inRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_3, BIG_DECIMAL_10))
            return FEW;
          // n % 100 = 11..99
          if (inRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_99))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ZERO,
            ONE,
            TWO,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ZERO, Range.ofFiniteValues(0)),
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.TWO, Range.ofFiniteValues(2)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(3, 4, 5, 6, 7, 8, 9, 10, 103, 104, 105, 106, 107, 108, 109, 110, 1003)),
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 111, 1011)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(100, 101, 102, 200, 201, 202, 300, 301, 302, 400, 401, 402, 500, 501, 502, 600, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("10.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Czech (cs)</li>
     * <li>Slovak (sk)</li>
     * </ul>
     */
    FAMILY_10(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 1 and v = 0
          if (equal(i, BIG_INTEGER_1) && v == 0)
            return ONE;
          // i = 2..4 and v = 0
          if (inRange(i, BIG_INTEGER_2, BIG_INTEGER_4) && v == 0)
            return FEW;
          // v != 0
          if (v != 0)
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.FEW, Range.ofFiniteValues(2, 3, 4)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Lower Sorbian (dsb)</li>
     * <li>Upper Sorbian (hsb)</li>
     * </ul>
     */
    FAMILY_11(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);
          BigInteger f = NumberUtils.fractionalComponent(n);

          // v = 0 and i % 100 = 1 or f % 100 = 1
          if ((v == 0 && equal(i.mod(BIG_INTEGER_100), BIG_INTEGER_1))
              || (equal(f.mod(BIG_INTEGER_100), BIG_INTEGER_1)))
            return ONE;
          // v = 0 and i % 100 = 2 or f % 100 = 2
          if ((v == 0 && equal(i.mod(BIG_INTEGER_100), BIG_INTEGER_2))
              || equal(f.mod(BIG_INTEGER_100), BIG_INTEGER_2))
            return TWO;
          // v = 0 and i % 100 = 3..4 or f % 100 = 3..4
          if ((v == 0 && inRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_3, BIG_INTEGER_4))
              || inRange(f.mod(BIG_INTEGER_100), BIG_INTEGER_3, BIG_INTEGER_4))
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 101, 201, 301, 401, 501, 601, 701, 1001)),
            MapEntry.of(Cardinality.TWO, Range.ofInfiniteValues(2, 102, 202, 302, 402, 502, 602, 702, 1002)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(3, 4, 103, 104, 203, 204, 303, 304, 403, 404, 503, 504, 603, 604, 703, 704, 1003)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("1.1"), new BigDecimal("2.1"), new BigDecimal("3.1"), new BigDecimal("4.1"), new BigDecimal("5.1"), new BigDecimal("6.1"), new BigDecimal("7.1"), new BigDecimal("10.1"), new BigDecimal("100.1"), new BigDecimal("1000.1"))),
            MapEntry.of(Cardinality.TWO, Range.ofInfiniteValues(new BigDecimal("0.2"), new BigDecimal("1.2"), new BigDecimal("2.2"), new BigDecimal("3.2"), new BigDecimal("4.2"), new BigDecimal("5.2"), new BigDecimal("6.2"), new BigDecimal("7.2"), new BigDecimal("10.2"), new BigDecimal("100.2"), new BigDecimal("1000.2"))),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("3.3"), new BigDecimal("3.4"), new BigDecimal("4.3"), new BigDecimal("4.4"), new BigDecimal("5.3"), new BigDecimal("5.4"), new BigDecimal("6.3"), new BigDecimal("6.4"), new BigDecimal("7.3"), new BigDecimal("7.4"), new BigDecimal("10.3"), new BigDecimal("100.3"), new BigDecimal("1000.3"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("1.8"), new BigDecimal("1.9"), new BigDecimal("2.0"), new BigDecimal("2.5"), new BigDecimal("2.6"), new BigDecimal("2.7")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Filipino (fil)</li>
     * <li>Tagalog (tl)</li>
     * </ul>
     */
    FAMILY_12(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);
          BigInteger f = NumberUtils.fractionalComponent(n);

          // v = 0 and i = 1,2,3 or v = 0 and i % 10 != 4,6,9 or v != 0 and f % 10 != 4,6,9
          if ((v == 0 && inSet(i, BIG_INTEGER_1, BIG_INTEGER_2, BIG_INTEGER_3))
              || (v == 0 && notInSet(i.mod(BIG_INTEGER_10), BIG_INTEGER_4, BIG_INTEGER_6, BIG_INTEGER_9))
              || (v != 0 && notInSet(f.mod(BIG_INTEGER_10), BIG_INTEGER_4, BIG_INTEGER_6, BIG_INTEGER_9)))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(0, 1, 2, 3, 5, 7, 8, 10, 11, 12, 13, 15, 17, 18, 20, 21, 100, 1000, 10000, 100000, 1000000)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(4, 6, 9, 14, 16, 19, 24, 26, 104, 1004))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.5"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.5"), new BigDecimal("1.7"), new BigDecimal("1.8"), new BigDecimal("2.1"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.4"), new BigDecimal("0.6"), new BigDecimal("0.9"), new BigDecimal("1.4"), new BigDecimal("1.6"), new BigDecimal("1.9"), new BigDecimal("2.4"), new BigDecimal("2.6"), new BigDecimal("10.4"), new BigDecimal("100.4"), new BigDecimal("1000.4")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Latvian (lv)</li>
     * <li>Prussian (prg)</li>
     * </ul>
     */
    FAMILY_13(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger f = NumberUtils.fractionalComponent(n);

          // n % 10 = 0 or n % 100 = 11..19 or v = 2 and f % 100 = 11..19
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_0)
              || inRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_19)
              || (v == 2 && inRange(f.mod(BIG_INTEGER_100), BIG_INTEGER_11, BIG_INTEGER_19)))
            return ZERO;
          // n % 10 = 1 and n % 100 != 11 or v = 2 and f % 10 = 1 and f % 100 != 11 or v != 2 and f % 10 = 1
          if ((equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_1) && notEqual(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11))
              || (v == 2 && equal(f.mod(BIG_INTEGER_10), BIG_INTEGER_1) && notEqual(f.mod(BIG_INTEGER_100), BIG_INTEGER_11))
              || (v != 2 && equal(f.mod(BIG_INTEGER_10), BIG_INTEGER_1)))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ZERO,
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ZERO, Range.ofInfiniteValues(0, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 30, 40, 50, 60, 100, 1000, 10000, 100000, 1000000)),
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 22, 23, 24, 25, 26, 27, 28, 29, 102, 1002))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("1.1"), new BigDecimal("2.1"), new BigDecimal("3.1"), new BigDecimal("4.1"), new BigDecimal("5.1"), new BigDecimal("6.1"), new BigDecimal("7.1"), new BigDecimal("10.1"), new BigDecimal("100.1"), new BigDecimal("1000.1"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("1.8"), new BigDecimal("1.9"), new BigDecimal("10.2"), new BigDecimal("100.2"), new BigDecimal("1000.2")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Moldovan (mo)</li>
     * <li>Romanian (ro)</li>
     * </ul>
     */
    FAMILY_14(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 1 and v = 0
          if (equal(i, BIG_INTEGER_1) && v == 0)
            return ONE;
          // v != 0 or n = 0 or n != 1 and n % 100 = 1..19
          if (v != 0
              || equal(n, BIG_DECIMAL_0)
              || (notEqual(n, BIG_DECIMAL_1) && inRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_1, BIG_DECIMAL_19)))
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 101, 1001)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Russian (ru)</li>
     * <li>Ukrainian (uk)</li>
     * </ul>
     */
    FAMILY_15(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);

          // v = 0 and i % 10 = 1 and i % 100 != 11
          if (v == 0 && equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_1) && notEqual(i.mod(BIG_INTEGER_100), BIG_INTEGER_11))
            return ONE;
          // v = 0 and i % 10 = 2..4 and i % 100 != 12..14
          if (v == 0
              && inRange(i.mod(BIG_INTEGER_10), BIG_INTEGER_2, BIG_INTEGER_4)
              && notInRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_12, BIG_INTEGER_14))
            return FEW;
          // v = 0 and i % 10 = 0 or v = 0 and i % 10 = 5..9 or v = 0 and i % 100 = 11..14
          if ((v == 0 && equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_0))
              || (v == 0 && inRange(i.mod(BIG_INTEGER_10), BIG_INTEGER_5, BIG_INTEGER_9))
              || (v == 0 && inRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_11, BIG_INTEGER_14)))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(2, 3, 4, 22, 23, 24, 32, 33, 34, 42, 43, 44, 52, 53, 54, 62, 102, 1002)),
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Belarusian (be)</li>
     * </ul>
     */
    FAMILY_16(
        (n) -> {
          // n % 10 = 1 and n % 100 != 11
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_1) && notEqual(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11))
            return ONE;
          // n % 10 = 2..4 and n % 100 != 12..14
          if (inRange(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_2, BIG_DECIMAL_4)
              && notInRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_12, BIG_DECIMAL_14))
            return FEW;
          // n % 10 = 0 or n % 10 = 5..9 or n % 100 = 11..14
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_0)
              || inRange(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_5, BIG_DECIMAL_9)
              || inRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_14))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(2, 3, 4, 22, 23, 24, 32, 33, 34, 42, 43, 44, 52, 53, 54, 62, 102, 1002)),
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("10.1"), new BigDecimal("100.1"), new BigDecimal("1000.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Breton (br)</li>
     * </ul>
     */
    FAMILY_17(
        (n) -> {
          // n % 10 = 1 and n % 100 != 11,71,91
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_1)
              && notInSet(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_71, BIG_DECIMAL_91))
            return ONE;
          // n % 10 = 2 and n % 100 != 12,72,92
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_2)
              && notInSet(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_12, BIG_DECIMAL_72, BIG_DECIMAL_92))
            return TWO;
          // n % 10 = 3..4,9 and n % 100 != 10..19,70..79,90..99
          if ((inRange(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_3, BIG_DECIMAL_4) || equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_9))
              && notInRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_10, BIG_DECIMAL_19)
              && notInRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_70, BIG_DECIMAL_79)
              && notInRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_90, BIG_DECIMAL_99))
            return FEW;
          // n != 0 and n % 1000000 = 0
          if (notEqual(n, BIG_DECIMAL_0) && equal(n.remainder(BIG_DECIMAL_1_000_000), BIG_DECIMAL_0))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 81, 101, 1001)),
            MapEntry.of(Cardinality.TWO, Range.ofInfiniteValues(2, 22, 32, 42, 52, 62, 82, 102, 1002)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(3, 4, 9, 23, 24, 29, 33, 34, 39, 43, 44, 49, 103, 1003)),
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(1000000)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 100, 1000, 10000, 100000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Welsh (cy)</li>
     * </ul>
     */
    FAMILY_18(
        (n) -> {
          // n = 0
          if (equal(n, BIG_DECIMAL_0))
            return ZERO;
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2
          if (equal(n, BIG_DECIMAL_2))
            return TWO;
          // n = 3
          if (equal(n, BIG_DECIMAL_3))
            return FEW;
          // n = 6
          if (equal(n, BIG_DECIMAL_6))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ZERO,
            ONE,
            TWO,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ZERO, Range.ofFiniteValues(0)),
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.TWO, Range.ofFiniteValues(2)),
            MapEntry.of(Cardinality.FEW, Range.ofFiniteValues(3)),
            MapEntry.of(Cardinality.MANY, Range.ofFiniteValues(6)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Danish (da)</li>
     * </ul>
     */
    FAMILY_19(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);
          BigInteger t = NumberUtils.fractionalComponent(n.stripTrailingZeros());

          // n = 1 or t != 0 and i = 0,1
          if (equal(n, BIG_DECIMAL_1) || (notEqual(t, BIG_INTEGER_0) && inSet(i, BIG_INTEGER_0, BIG_INTEGER_1)))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("2.0"), new BigDecimal("2.1"), new BigDecimal("2.2"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("2.5"), new BigDecimal("2.6"), new BigDecimal("2.7"), new BigDecimal("2.8"), new BigDecimal("2.9"), new BigDecimal("3.0"), new BigDecimal("3.1"), new BigDecimal("3.2"), new BigDecimal("3.3"), new BigDecimal("3.4")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Irish (ga)</li>
     * </ul>
     */
    FAMILY_20(
        (n) -> {
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2
          if (equal(n, BIG_DECIMAL_2))
            return TWO;
          // n = 3..6
          if (inRange(n, BIG_DECIMAL_3, BIG_DECIMAL_6))
            return FEW;
          // n = 7..10
          if (inRange(n, BIG_DECIMAL_7, BIG_DECIMAL_10))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.TWO, Range.ofFiniteValues(2)),
            MapEntry.of(Cardinality.FEW, Range.ofFiniteValues(3, 4, 5, 6)),
            MapEntry.of(Cardinality.MANY, Range.ofFiniteValues(7, 8, 9, 10)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("10.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Scottish Gaelic (gd)</li>
     * </ul>
     */
    FAMILY_21(
        (n) -> {
          // n = 1,11
          if (inSet(n, BIG_DECIMAL_1, BIG_DECIMAL_11))
            return ONE;
          // n = 2,12
          if (inSet(n, BIG_DECIMAL_2, BIG_DECIMAL_12))
            return TWO;
          // n = 3..10,13..19
          if (inRange(n, BIG_DECIMAL_3, BIG_DECIMAL_10) || inRange(n, BIG_DECIMAL_13, BIG_DECIMAL_19))
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1, 11)),
            MapEntry.of(Cardinality.TWO, Range.ofFiniteValues(2, 12)),
            MapEntry.of(Cardinality.FEW, Range.ofFiniteValues(3, 4, 5, 6, 7, 8, 9, 10, 13, 14, 15, 16, 17, 18, 19)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("10.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Manx (gv)</li>
     * </ul>
     */
    FAMILY_22(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);

          // v = 0 and i % 10 = 1
          if (v == 0 && equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_1))
            return ONE;
          // v = 0 and i % 10 = 2
          if (v == 0 && equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_2))
            return TWO;
          // v = 0 and i % 100 = 0,20,40,60,80
          if (v == 0 && inSet(i.mod(BIG_INTEGER_100), BIG_INTEGER_0, BIG_INTEGER_20, BIG_INTEGER_40, BIG_INTEGER_60, BIG_INTEGER_80))
            return FEW;
          // v != 0
          if (v != 0)
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 11, 21, 31, 41, 51, 61, 71, 101, 1001)),
            MapEntry.of(Cardinality.TWO, Range.ofInfiniteValues(2, 12, 22, 32, 42, 52, 62, 72, 102, 1002)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(0, 20, 40, 60, 80, 100, 120, 140, 1000, 10000, 100000, 1000000)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(3, 4, 5, 6, 7, 8, 9, 10, 13, 14, 15, 16, 17, 18, 19, 23, 103, 1003))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Hebrew (he)</li>
     * </ul>
     */
    FAMILY_23(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 1 and v = 0
          if (equal(i, BIG_INTEGER_1) && v == 0)
            return ONE;
          // i = 2 and v = 0
          if (equal(i, BIG_INTEGER_2) && v == 0)
            return TWO;
          // v = 0 and n != 0..10 and n % 10 = 0
          if (v == 0
              && notInRange(n, BIG_DECIMAL_0, BIG_DECIMAL_10)
              && equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_0))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.TWO, Range.ofFiniteValues(2)),
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(20, 30, 40, 50, 60, 70, 80, 90, 100, 1000, 10000, 100000, 1000000)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 101, 1001))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Icelandic (is)</li>
     * </ul>
     */
    FAMILY_24(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);
          BigInteger t = NumberUtils.fractionalComponent(n.stripTrailingZeros());

          // t = 0 and i % 10 = 1 and i % 100 != 11 or t != 0
          if ((equal(t, BIG_INTEGER_0) && equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_1) && notEqual(i.mod(BIG_INTEGER_100), BIG_INTEGER_11))
              || notEqual(t, BIG_INTEGER_0))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("10.1"), new BigDecimal("100.1"), new BigDecimal("1000.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Colognian (ksh)</li>
     * </ul>
     */
    FAMILY_25(
        (n) -> {
          // n = 0
          if (equal(n, BIG_DECIMAL_0))
            return ZERO;
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ZERO,
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ZERO, Range.ofFiniteValues(0)),
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Langi (lag)</li>
     * </ul>
     */
    FAMILY_26(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);

          // n = 0
          if (equal(n, BIG_DECIMAL_0))
            return ZERO;
          // i = 0,1 and n != 0
          if (inSet(i, BIG_INTEGER_0, BIG_INTEGER_1) && notEqual(n, BIG_DECIMAL_0))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ZERO,
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ZERO, Range.ofFiniteValues(0)),
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("2.0"), new BigDecimal("2.1"), new BigDecimal("2.2"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("2.5"), new BigDecimal("2.6"), new BigDecimal("2.7"), new BigDecimal("2.8"), new BigDecimal("2.9"), new BigDecimal("3.0"), new BigDecimal("3.1"), new BigDecimal("3.2"), new BigDecimal("3.3"), new BigDecimal("3.4"), new BigDecimal("3.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Lithuanian (lt)</li>
     * </ul>
     */
    FAMILY_27(
        (n) -> {
          BigInteger f = NumberUtils.fractionalComponent(n);

          // n % 10 = 1 and n % 100 != 11..19
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_1)
              && notInRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_19))
            return ONE;
          // n % 10 = 2..9 and n % 100 != 11..19
          if (inRange(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_2, BIG_DECIMAL_9)
              && notInRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_19))
            return FEW;
          // f != 0
          if (notEqual(f, BIG_INTEGER_0))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 22, 23, 24, 25, 26, 27, 28, 29, 102, 1002)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 30, 40, 50, 60, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("10.1"), new BigDecimal("100.1"), new BigDecimal("1000.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Macedonian (mk)</li>
     * </ul>
     */
    FAMILY_28(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);
          BigInteger f = NumberUtils.fractionalComponent(n);

          // v = 0 and i % 10 = 1 or f % 10 = 1
          if ((v == 0 && equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_1))
              || equal(f.mod(BIG_INTEGER_10), BIG_INTEGER_1))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 11, 21, 31, 41, 51, 61, 71, 101, 1001)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("1.1"), new BigDecimal("2.1"), new BigDecimal("3.1"), new BigDecimal("4.1"), new BigDecimal("5.1"), new BigDecimal("6.1"), new BigDecimal("7.1"), new BigDecimal("10.1"), new BigDecimal("100.1"), new BigDecimal("1000.1"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Maltese (mt)</li>
     * </ul>
     */
    FAMILY_29(
        (n) -> {
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 0 or n % 100 = 2..10
          if (equal(n, BIG_DECIMAL_0) || inRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_2, BIG_DECIMAL_10))
            return FEW;
          // n % 100 = 11..19
          if (inRange(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_19))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 102, 103, 104, 105, 106, 107, 1002)),
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(11, 12, 13, 14, 15, 16, 17, 18, 19, 111, 112, 113, 114, 115, 116, 117, 1011)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("10.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Polish (pl)</li>
     * </ul>
     */
    FAMILY_30(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 1 and v = 0
          if (equal(i, BIG_INTEGER_1) && v == 0)
            return ONE;
          // v = 0 and i % 10 = 2..4 and i % 100 != 12..14
          if (v == 0
              && inRange(i.mod(BIG_INTEGER_10), BIG_INTEGER_2, BIG_INTEGER_4)
              && notInRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_12, BIG_INTEGER_14))
            return FEW;
          // v = 0 and i != 1 and i % 10 = 0..1 or v = 0 and i % 10 = 5..9 or v = 0 and i % 100 = 12..14
          if ((v == 0 && notEqual(i, BIG_INTEGER_1) && inRange(i.mod(BIG_INTEGER_10), BIG_INTEGER_0, BIG_INTEGER_1))
              || (v == 0 && inRange(i.mod(BIG_INTEGER_10), BIG_INTEGER_5, BIG_INTEGER_9))
              || (v == 0 && inRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_12, BIG_INTEGER_14)))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(2, 3, 4, 22, 23, 24, 32, 33, 34, 42, 43, 44, 52, 53, 54, 62, 102, 1002)),
            MapEntry.of(Cardinality.MANY, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Portuguese (pt)</li>
     * </ul>
     */
    FAMILY_31(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 0..1
          if (inRange(i, BIG_INTEGER_0, BIG_INTEGER_1))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(0, 1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("2.0"), new BigDecimal("2.1"), new BigDecimal("2.2"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("2.5"), new BigDecimal("2.6"), new BigDecimal("2.7"), new BigDecimal("2.8"), new BigDecimal("2.9"), new BigDecimal("3.0"), new BigDecimal("3.1"), new BigDecimal("3.2"), new BigDecimal("3.3"), new BigDecimal("3.4"), new BigDecimal("3.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Tachelhit (shi)</li>
     * </ul>
     */
    FAMILY_32(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 0 or n = 1
          if (equal(i, BIG_INTEGER_0) || equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2..10
          if (inRange(n, BIG_DECIMAL_2, BIG_DECIMAL_10))
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(0, 1)),
            MapEntry.of(Cardinality.FEW, Range.ofFiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(new BigDecimal("0.0"), new BigDecimal("0.01"), new BigDecimal("0.02"), new BigDecimal("0.03"), new BigDecimal("0.04"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("1.8"), new BigDecimal("1.9"), new BigDecimal("2.1"), new BigDecimal("2.2"), new BigDecimal("2.3"), new BigDecimal("2.4"), new BigDecimal("2.5"), new BigDecimal("2.6"), new BigDecimal("2.7"), new BigDecimal("10.1")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Sinhalese (si)</li>
     * </ul>
     */
    FAMILY_33(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);
          BigInteger f = NumberUtils.fractionalComponent(n);

          // n = 0,1 or i = 0 and f = 1
          if (inSet(n, BIG_DECIMAL_0, BIG_DECIMAL_1)
              || (equal(i, BIG_INTEGER_0) && equal(f, BIG_INTEGER_1)))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(0, 1)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(new BigDecimal("0.0001"), new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("0.1"))),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7"), new BigDecimal("1.8")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Slovenian (sl)</li>
     * </ul>
     */
    FAMILY_34(
        (n) -> {
          int v = NumberUtils.numberOfDecimalPlaces(n);
          BigInteger i = NumberUtils.integerComponent(n);

          // v = 0 and i % 100 = 1
          if (v == 0 && equal(i.mod(BIG_INTEGER_100), BIG_INTEGER_1))
            return ONE;
          // v = 0 and i % 100 = 2
          if (v == 0 && equal(i.mod(BIG_INTEGER_100), BIG_INTEGER_2))
            return TWO;
          // v = 0 and i % 100 = 3..4 or v != 0
          if ((v == 0 && inRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_3, BIG_INTEGER_4))
              || v != 0)
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            TWO,
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofInfiniteValues(1, 101, 201, 301, 401, 501, 601, 701, 1001)),
            MapEntry.of(Cardinality.TWO, Range.ofInfiniteValues(2, 102, 202, 302, 402, 502, 602, 702, 1002)),
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(3, 4, 103, 104, 203, 204, 303, 304, 403, 404, 503, 504, 603, 604, 703, 704, 1003)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.FEW, Range.ofInfiniteValues(new BigDecimal("0.0"), new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5")))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Central Atlas Tamazight (tzm)</li>
     * </ul>
     */
    FAMILY_35(
        (n) -> {
          // n = 0..1 or n = 11..99
          if (inRange(n, BIG_DECIMAL_0, BIG_DECIMAL_1) || inRange(n, BIG_DECIMAL_11, BIG_DECIMAL_99))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.ONE, Range.ofFiniteValues(0, 1, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)),
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(2, 3, 4, 5, 6, 7, 8, 9, 10, 100, 101, 102, 103, 104, 105, 106, 1000, 10000, 100000, 1000000))
        ),
        Maps.sortedMap(
            MapEntry.of(Cardinality.OTHER, Range.ofInfiniteValues(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.5"), new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("1.2"), new BigDecimal("1.3"), new BigDecimal("1.4"), new BigDecimal("1.5"), new BigDecimal("1.6"), new BigDecimal("1.7")))
        )
    );

    @Nonnull
    private static final Map<String, CardinalityFamily> CARDINALITY_FAMILIES_BY_LANGUAGE_CODE;
    @Nonnull
    private static final SortedSet<String> SUPPORTED_LANGUAGE_CODES;

    @Nonnull
    private final Function<BigDecimal, Cardinality> cardinalityFunction;
    @Nonnull
    private final SortedSet<Cardinality> supportedCardinalities;
    @Nonnull
    private final SortedMap<Cardinality, Range<Integer>> exampleIntegerValuesByCardinality;
    @Nonnull
    private final SortedMap<Cardinality, Range<BigDecimal>> exampleDecimalValuesByCardinality;

    /**
     * Constructs a cardinality family.
     *
     * @param cardinalityFunction               the cardinality-determining function for this cardinality family, not null
     * @param supportedCardinalities            the cardinalities supported by this family sorted by the natural ordering of {@link Cardinality}, not null
     * @param exampleIntegerValuesByCardinality a mapping of cardinalities to example integer values for this cardinality family sorted by the natural ordering of {@link Cardinality}, not null
     * @param exampleDecimalValuesByCardinality a mapping of cardinalities to example decimal values for this cardinality family sorted by the natural ordering of {@link Cardinality}, not null
     */
    CardinalityFamily(@Nonnull Function<BigDecimal, Cardinality> cardinalityFunction,
                      @Nonnull SortedSet<Cardinality> supportedCardinalities,
                      @Nonnull SortedMap<Cardinality, Range<Integer>> exampleIntegerValuesByCardinality,
                      @Nonnull SortedMap<Cardinality, Range<BigDecimal>> exampleDecimalValuesByCardinality) {
      requireNonNull(cardinalityFunction);
      requireNonNull(supportedCardinalities);
      requireNonNull(exampleIntegerValuesByCardinality);
      requireNonNull(exampleDecimalValuesByCardinality);

      this.cardinalityFunction = cardinalityFunction;
      this.supportedCardinalities = supportedCardinalities;
      this.exampleIntegerValuesByCardinality = exampleIntegerValuesByCardinality;
      this.exampleDecimalValuesByCardinality = exampleDecimalValuesByCardinality;
    }

    static {
      CARDINALITY_FAMILIES_BY_LANGUAGE_CODE = Collections.unmodifiableMap(new HashMap<String, CardinalityFamily>() {{
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
        put("guw", CardinalityFamily.FAMILY_4); // Gun
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
        put("kl", CardinalityFamily.FAMILY_1); // Greenlandic
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
        put("my", CardinalityFamily.FAMILY_2); // Burmese
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
        put("os", CardinalityFamily.FAMILY_1); // Ossetian
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
        put("si", CardinalityFamily.FAMILY_33); // Sinhalese
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

      // Language codes are in English - force collation for sorting
      SortedSet<String> supportedLanguageCodes = new TreeSet<>(Collator.getInstance(Locale.ENGLISH));
      supportedLanguageCodes.addAll(CARDINALITY_FAMILIES_BY_LANGUAGE_CODE.keySet());

      SUPPORTED_LANGUAGE_CODES = Collections.unmodifiableSortedSet(supportedLanguageCodes);
    }

    /**
     * Gets the cardinality-determining function for this cardinality family.
     * <p>
     * The function takes a numeric value as input and returns the appropriate cardinal form.
     * <p>
     * The function's input must not be null and its output is guaranteed non-null.
     *
     * @return the cardinality-determining function for this cardinality family, not null
     */
    @Nonnull
    Function<BigDecimal, Cardinality> getCardinalityFunction() {
      return cardinalityFunction;
    }

    /**
     * Gets the cardinalities supported by this cardinality family.
     * <p>
     * There will always be at least one value - {@link Cardinality#OTHER} - in the set.
     * <p>
     * The set's values are sorted by the natural ordering of the {@link Cardinality} enumeration.
     *
     * @return the cardinalities supported by this cardinality family, not null
     */
    @Nonnull
    SortedSet<Cardinality> getSupportedCardinalities() {
      return supportedCardinalities;
    }

    /**
     * Gets a mapping of cardinalities to example integer values for this cardinality family.
     * <p>
     * The map may be empty.
     * <p>
     * The map's keys are sorted by the natural ordering of the {@link Cardinality} enumeration.
     *
     * @return a mapping of cardinalities to example integer values, not null
     */
    @Nonnull
    SortedMap<Cardinality, Range<Integer>> getExampleIntegerValuesByCardinality() {
      return exampleIntegerValuesByCardinality;
    }

    /**
     * Gets a mapping of cardinalities to example decimal values for this cardinality family.
     * <p>
     * The map may be empty.
     * <p>
     * The map's keys are sorted by the natural ordering of the {@link Cardinality} enumeration.
     *
     * @return a mapping of cardinalities to example decimal values, not null
     */
    @Nonnull
    SortedMap<Cardinality, Range<BigDecimal>> getExampleDecimalValuesByCardinality() {
      return exampleDecimalValuesByCardinality;
    }

    /**
     * Gets the ISO 639 language codes for which cardinality operations are supported.
     * <p>
     * The set's values are ISO 639 codes and therefore sorted using English collation.
     *
     * @return the ISO 639 language codes for which cardinality operations are supported, not null
     */
    @Nonnull
    static SortedSet<String> getSupportedLanguageCodes() {
      return SUPPORTED_LANGUAGE_CODES;
    }

    /**
     * Gets an appropriate plural cardinality family for the given locale.
     *
     * @param locale the locale to check, not null
     * @return the appropriate plural cardinality family (if one exists) for the given locale, not null
     */
    @Nonnull
    static Optional<CardinalityFamily> cardinalityFamilyForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      String language = LocaleUtils.normalizedLanguage(locale).orElse(null);
      String country = locale.getCountry();

      CardinalityFamily cardinalityFamily = null;

      if (language != null && country != null)
        cardinalityFamily = CARDINALITY_FAMILIES_BY_LANGUAGE_CODE.get(format("%s-%s", language, country));

      if (cardinalityFamily != null)
        return Optional.of(cardinalityFamily);

      if (language != null)
        cardinalityFamily = CARDINALITY_FAMILIES_BY_LANGUAGE_CODE.get(language);

      return Optional.ofNullable(cardinalityFamily);
    }
  }


  enum CardinalityRangeFamily {
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Akan (ak)</li>
     * <li>Najdi Arabic (ars)</li>
     * <li>Assamese (as)</li>
     * <li>Asu (asa)</li>
     * <li>Asturian (ast)</li>
     * <li>Bemba (bem)</li>
     * <li>Bena (bez)</li>
     * <li>Bihari (bh)</li>
     * <li>Bambara (bm)</li>
     * <li>Tibetan (bo)</li>
     * <li>Breton (br)</li>
     * <li>Bodo (brx)</li>
     * <li>Chechen (ce)</li>
     * <li>Chiga (cgg)</li>
     * <li>Cherokee (chr)</li>
     * <li>Central Kurdish (ckb)</li>
     * <li>Lower Sorbian (dsb)</li>
     * <li>Divehi (dv)</li>
     * <li>Dzongkha (dz)</li>
     * <li>Ewe (ee)</li>
     * <li>Esperanto (eo)</li>
     * <li>Fulah (ff)</li>
     * <li>Faroese (fo)</li>
     * <li>Friulian (fur)</li>
     * <li>Western Frisian (fy)</li>
     * <li>Scottish Gaelic (gd)</li>
     * <li>Gun (guw)</li>
     * <li>Manx (gv)</li>
     * <li>Hausa (ha)</li>
     * <li>Hawaiian (haw)</li>
     * <li>Upper Sorbian (hsb)</li>
     * <li>Igbo (ig)</li>
     * <li>Sichuan Yi (ii)</li>
     * <li>Inuktitut (iu)</li>
     * <li>Lojban (jbo)</li>
     * <li>Ngomba (jgo)</li>
     * <li>Machame (jmc)</li>
     * <li>Javanese (jv)</li>
     * <li>Javanese (jw)</li>
     * <li>Kabyle (kab)</li>
     * <li>Jju (kaj)</li>
     * <li>Tyap (kcg)</li>
     * <li>Makonde (kde)</li>
     * <li>Kabuverdianu (kea)</li>
     * <li>Kako (kkj)</li>
     * <li>Greenlandic (kl)</li>
     * <li>Kashmiri (ks)</li>
     * <li>Shambala (ksb)</li>
     * <li>Colognian (ksh)</li>
     * <li>Kurdish (ku)</li>
     * <li>Cornish (kw)</li>
     * <li>Langi (lag)</li>
     * <li>Luxembourgish (lb)</li>
     * <li>Ganda (lg)</li>
     * <li>Lakota (lkt)</li>
     * <li>Lingala (ln)</li>
     * <li>Masai (mas)</li>
     * <li>Malagasy (mg)</li>
     * <li>Metaʼ (mgo)</li>
     * <li>Moldovan (mo)</li>
     * <li>Maltese (mt)</li>
     * <li>Nahuatl (nah)</li>
     * <li>Nama (naq)</li>
     * <li>North Ndebele (nd)</li>
     * <li>Norwegian Nynorsk (nn)</li>
     * <li>Ngiemboon (nnh)</li>
     * <li>Norwegian (no)</li>
     * <li>N’Ko (nqo)</li>
     * <li>South Ndebele (nr)</li>
     * <li>Northern Sotho (nso)</li>
     * <li>Nyanja (ny)</li>
     * <li>Nyankole (nyn)</li>
     * <li>Oromo (om)</li>
     * <li>Odia (or)</li>
     * <li>Ossetian (os)</li>
     * <li>Papiamento (pap)</li>
     * <li>Prussian (prg)</li>
     * <li>Pushto (ps)</li>
     * <li>Romansh (rm)</li>
     * <li>Rombo (rof)</li>
     * <li>Root (root)</li>
     * <li>Rwa (rwk)</li>
     * <li>Sakha (sah)</li>
     * <li>Samburu (saq)</li>
     * <li>Southern Kurdish (sdh)</li>
     * <li>Northern Sami (se)</li>
     * <li>Sena (seh)</li>
     * <li>Koyraboro Senni (ses)</li>
     * <li>Sango (sg)</li>
     * <li>Serbo-Croatian (sh)</li>
     * <li>Tachelhit (shi)</li>
     * <li>Southern Sami (sma)</li>
     * <li>Sami (smi)</li>
     * <li>Lule Sami (smj)</li>
     * <li>Inari Sami (smn)</li>
     * <li>Skolt Sami (sms)</li>
     * <li>Shona (sn)</li>
     * <li>Somali (so)</li>
     * <li>Swati (ss)</li>
     * <li>Saho (ssy)</li>
     * <li>Southern Sotho (st)</li>
     * <li>Syriac (syr)</li>
     * <li>Teso (teo)</li>
     * <li>Tigrinya (ti)</li>
     * <li>Tigre (tig)</li>
     * <li>Turkmen (tk)</li>
     * <li>Tagalog (tl)</li>
     * <li>Tswana (tn)</li>
     * <li>Tongan (to)</li>
     * <li>Tsonga (ts)</li>
     * <li>Central Atlas Tamazight (tzm)</li>
     * <li>Venda (ve)</li>
     * <li>Volapük (vo)</li>
     * <li>Vunjo (vun)</li>
     * <li>Walloon (wa)</li>
     * <li>Walser (wae)</li>
     * <li>Wolof (wo)</li>
     * <li>Xhosa (xh)</li>
     * <li>Soga (xog)</li>
     * <li>Yiddish (yi)</li>
     * <li>Yoruba (yo)</li>
     * </ul>
     */
    FAMILY_1(
        // There are no cardinality ranges for this family
        Collections.emptySortedMap()
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Azeri (az)</li>
     * <li>German (de)</li>
     * <li>Greek (el)</li>
     * <li>Galician (gl)</li>
     * <li>Swiss German (gsw)</li>
     * <li>Hungarian (hu)</li>
     * <li>Italian (it)</li>
     * <li>Kazakh (kk)</li>
     * <li>Kirghiz (ky)</li>
     * <li>Malayalam (ml)</li>
     * <li>Mongolian (mn)</li>
     * <li>Nepali (ne)</li>
     * <li>Dutch (nl)</li>
     * <li>Albanian (sq)</li>
     * <li>Swahili (sw)</li>
     * <li>Tamil (ta)</li>
     * <li>Telugu (te)</li>
     * <li>Turkish (tr)</li>
     * <li>Uighur (ug)</li>
     * <li>Uzbek (uz)</li>
     * </ul>
     */
    FAMILY_2(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Afrikaans (af)</li>
     * <li>Bulgarian (bg)</li>
     * <li>Catalan (ca)</li>
     * <li>English (en)</li>
     * <li>Spanish (es)</li>
     * <li>Estonian (et)</li>
     * <li>Basque (eu)</li>
     * <li>Finnish (fi)</li>
     * <li>Norwegian Bokmål (nb)</li>
     * <li>Swedish (sv)</li>
     * <li>Urdu (ur)</li>
     * </ul>
     */
    FAMILY_3(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Indonesian (id)</li>
     * <li>Japanese (ja)</li>
     * <li>Khmer (km)</li>
     * <li>Korean (ko)</li>
     * <li>Lao (lo)</li>
     * <li>Malay (ms)</li>
     * <li>Burmese (my)</li>
     * <li>Thai (th)</li>
     * <li>Vietnamese (vi)</li>
     * <li>Cantonese (yue)</li>
     * <li>Mandarin Chinese (zh)</li>
     * </ul>
     */
    FAMILY_4(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Amharic (am)</li>
     * <li>Bangla (bn)</li>
     * <li>French (fr)</li>
     * <li>Gujarati (gu)</li>
     * <li>Hindi (hi)</li>
     * <li>Armenian (hy)</li>
     * <li>Kannada (kn)</li>
     * <li>Marathi (mr)</li>
     * <li>Zulu (zu)</li>
     * </ul>
     */
    FAMILY_5(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Danish (da)</li>
     * <li>Filipino (fil)</li>
     * <li>Icelandic (is)</li>
     * <li>Punjabi (pa)</li>
     * <li>Portuguese (pt)</li>
     * </ul>
     */
    FAMILY_6(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Belarusian (be)</li>
     * <li>Lithuanian (lt)</li>
     * <li>Russian (ru)</li>
     * <li>Ukrainian (uk)</li>
     * </ul>
     */
    FAMILY_7(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, ONE), ONE),
            MapEntry.of(CardinalityRange.of(FEW, FEW), FEW),
            MapEntry.of(CardinalityRange.of(FEW, MANY), MANY),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(MANY, ONE), ONE),
            MapEntry.of(CardinalityRange.of(MANY, FEW), FEW),
            MapEntry.of(CardinalityRange.of(MANY, MANY), MANY),
            MapEntry.of(CardinalityRange.of(MANY, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, MANY), MANY),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Bosnian (bs)</li>
     * <li>Croatian (hr)</li>
     * <li>Serbian (sr)</li>
     * </ul>
     */
    FAMILY_8(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, ONE), ONE),
            MapEntry.of(CardinalityRange.of(FEW, FEW), FEW),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Czech (cs)</li>
     * <li>Polish (pl)</li>
     * <li>Slovak (sk)</li>
     * </ul>
     */
    FAMILY_9(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, FEW), FEW),
            MapEntry.of(CardinalityRange.of(FEW, MANY), MANY),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(MANY, ONE), ONE),
            MapEntry.of(CardinalityRange.of(MANY, FEW), FEW),
            MapEntry.of(CardinalityRange.of(MANY, MANY), MANY),
            MapEntry.of(CardinalityRange.of(MANY, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, MANY), MANY),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Arabic (ar)</li>
     * </ul>
     */
    FAMILY_10(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ZERO, ONE), ZERO),
            MapEntry.of(CardinalityRange.of(ZERO, TWO), ZERO),
            MapEntry.of(CardinalityRange.of(ZERO, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ZERO, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ZERO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, TWO), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(TWO, FEW), FEW),
            MapEntry.of(CardinalityRange.of(TWO, MANY), MANY),
            MapEntry.of(CardinalityRange.of(TWO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, FEW), FEW),
            MapEntry.of(CardinalityRange.of(FEW, MANY), MANY),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(MANY, FEW), FEW),
            MapEntry.of(CardinalityRange.of(MANY, MANY), MANY),
            MapEntry.of(CardinalityRange.of(MANY, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, TWO), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, MANY), MANY),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Welsh (cy)</li>
     * </ul>
     */
    FAMILY_11(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ZERO, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ZERO, TWO), TWO),
            MapEntry.of(CardinalityRange.of(ZERO, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ZERO, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ZERO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, TWO), TWO),
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(TWO, FEW), FEW),
            MapEntry.of(CardinalityRange.of(TWO, MANY), MANY),
            MapEntry.of(CardinalityRange.of(TWO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, MANY), MANY),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(MANY, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, TWO), TWO),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, MANY), MANY),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Persian (fa)</li>
     * </ul>
     */
    FAMILY_12(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Irish (ga)</li>
     * </ul>
     */
    FAMILY_13(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, TWO), TWO),
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(TWO, FEW), FEW),
            MapEntry.of(CardinalityRange.of(TWO, MANY), MANY),
            MapEntry.of(CardinalityRange.of(TWO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, FEW), FEW),
            MapEntry.of(CardinalityRange.of(FEW, MANY), MANY),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(MANY, MANY), MANY),
            MapEntry.of(CardinalityRange.of(MANY, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, TWO), TWO),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, MANY), MANY),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Hebrew (he)</li>
     * </ul>
     */
    FAMILY_14(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, TWO), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, MANY), MANY),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(TWO, MANY), OTHER),
            MapEntry.of(CardinalityRange.of(TWO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(MANY, MANY), MANY),
            MapEntry.of(CardinalityRange.of(MANY, OTHER), MANY),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, TWO), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, MANY), MANY),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Georgian (ka)</li>
     * </ul>
     */
    FAMILY_15(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, OTHER), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Latvian (lv)</li>
     * </ul>
     */
    FAMILY_16(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ZERO, ZERO), OTHER),
            MapEntry.of(CardinalityRange.of(ZERO, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ZERO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, ZERO), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ZERO), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), ONE),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Macedonian (mk)</li>
     * </ul>
     */
    FAMILY_17(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Romanian (ro)</li>
     * </ul>
     */
    FAMILY_18(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, ONE), FEW),
            MapEntry.of(CardinalityRange.of(FEW, FEW), FEW),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Sinhalese (si)</li>
     * </ul>
     */
    FAMILY_19(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), ONE),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Slovenian (sl)</li>
     * </ul>
     */
    FAMILY_20(
        Maps.sortedMap(
            MapEntry.of(CardinalityRange.of(ONE, ONE), FEW),
            MapEntry.of(CardinalityRange.of(ONE, TWO), TWO),
            MapEntry.of(CardinalityRange.of(ONE, FEW), FEW),
            MapEntry.of(CardinalityRange.of(ONE, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(TWO, ONE), FEW),
            MapEntry.of(CardinalityRange.of(TWO, TWO), TWO),
            MapEntry.of(CardinalityRange.of(TWO, FEW), FEW),
            MapEntry.of(CardinalityRange.of(TWO, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(FEW, ONE), FEW),
            MapEntry.of(CardinalityRange.of(FEW, TWO), TWO),
            MapEntry.of(CardinalityRange.of(FEW, FEW), FEW),
            MapEntry.of(CardinalityRange.of(FEW, OTHER), OTHER),
            MapEntry.of(CardinalityRange.of(OTHER, ONE), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, TWO), TWO),
            MapEntry.of(CardinalityRange.of(OTHER, FEW), FEW),
            MapEntry.of(CardinalityRange.of(OTHER, OTHER), OTHER)
        )
    );

    @Nonnull
    private static final Map<String, CardinalityRangeFamily> CARDINALITY_RANGE_FAMILIES_BY_LANGUAGE_CODE;

    @Nonnull
    private final SortedMap<CardinalityRange, Cardinality> cardinalitiesByCardinalityRange;

    static {
      CARDINALITY_RANGE_FAMILIES_BY_LANGUAGE_CODE = Collections.unmodifiableMap(new HashMap<String, CardinalityRangeFamily>() {
        {
          put("af", CardinalityRangeFamily.FAMILY_3); // Afrikaans
          put("ak", CardinalityRangeFamily.FAMILY_1); // Akan
          put("am", CardinalityRangeFamily.FAMILY_5); // Amharic
          put("ar", CardinalityRangeFamily.FAMILY_10); // Arabic
          put("ars", CardinalityRangeFamily.FAMILY_1); // Najdi Arabic
          put("as", CardinalityRangeFamily.FAMILY_1); // Assamese
          put("asa", CardinalityRangeFamily.FAMILY_1); // Asu
          put("ast", CardinalityRangeFamily.FAMILY_1); // Asturian
          put("az", CardinalityRangeFamily.FAMILY_2); // Azeri
          put("be", CardinalityRangeFamily.FAMILY_7); // Belarusian
          put("bem", CardinalityRangeFamily.FAMILY_1); // Bemba
          put("bez", CardinalityRangeFamily.FAMILY_1); // Bena
          put("bg", CardinalityRangeFamily.FAMILY_3); // Bulgarian
          put("bh", CardinalityRangeFamily.FAMILY_1); // Bihari
          put("bm", CardinalityRangeFamily.FAMILY_1); // Bambara
          put("bn", CardinalityRangeFamily.FAMILY_5); // Bangla
          put("bo", CardinalityRangeFamily.FAMILY_1); // Tibetan
          put("br", CardinalityRangeFamily.FAMILY_1); // Breton
          put("brx", CardinalityRangeFamily.FAMILY_1); // Bodo
          put("bs", CardinalityRangeFamily.FAMILY_8); // Bosnian
          put("ca", CardinalityRangeFamily.FAMILY_3); // Catalan
          put("ce", CardinalityRangeFamily.FAMILY_1); // Chechen
          put("cgg", CardinalityRangeFamily.FAMILY_1); // Chiga
          put("chr", CardinalityRangeFamily.FAMILY_1); // Cherokee
          put("ckb", CardinalityRangeFamily.FAMILY_1); // Central Kurdish
          put("cs", CardinalityRangeFamily.FAMILY_9); // Czech
          put("cy", CardinalityRangeFamily.FAMILY_11); // Welsh
          put("da", CardinalityRangeFamily.FAMILY_6); // Danish
          put("de", CardinalityRangeFamily.FAMILY_2); // German
          put("dsb", CardinalityRangeFamily.FAMILY_1); // Lower Sorbian
          put("dv", CardinalityRangeFamily.FAMILY_1); // Divehi
          put("dz", CardinalityRangeFamily.FAMILY_1); // Dzongkha
          put("ee", CardinalityRangeFamily.FAMILY_1); // Ewe
          put("el", CardinalityRangeFamily.FAMILY_2); // Greek
          put("en", CardinalityRangeFamily.FAMILY_3); // English
          put("eo", CardinalityRangeFamily.FAMILY_1); // Esperanto
          put("es", CardinalityRangeFamily.FAMILY_3); // Spanish
          put("et", CardinalityRangeFamily.FAMILY_3); // Estonian
          put("eu", CardinalityRangeFamily.FAMILY_3); // Basque
          put("fa", CardinalityRangeFamily.FAMILY_12); // Persian
          put("ff", CardinalityRangeFamily.FAMILY_1); // Fulah
          put("fi", CardinalityRangeFamily.FAMILY_3); // Finnish
          put("fil", CardinalityRangeFamily.FAMILY_6); // Filipino
          put("fo", CardinalityRangeFamily.FAMILY_1); // Faroese
          put("fr", CardinalityRangeFamily.FAMILY_5); // French
          put("fur", CardinalityRangeFamily.FAMILY_1); // Friulian
          put("fy", CardinalityRangeFamily.FAMILY_1); // Western Frisian
          put("ga", CardinalityRangeFamily.FAMILY_13); // Irish
          put("gd", CardinalityRangeFamily.FAMILY_1); // Scottish Gaelic
          put("gl", CardinalityRangeFamily.FAMILY_2); // Galician
          put("gsw", CardinalityRangeFamily.FAMILY_2); // Swiss German
          put("gu", CardinalityRangeFamily.FAMILY_5); // Gujarati
          put("guw", CardinalityRangeFamily.FAMILY_1); // Gun
          put("gv", CardinalityRangeFamily.FAMILY_1); // Manx
          put("ha", CardinalityRangeFamily.FAMILY_1); // Hausa
          put("haw", CardinalityRangeFamily.FAMILY_1); // Hawaiian
          put("he", CardinalityRangeFamily.FAMILY_14); // Hebrew
          put("hi", CardinalityRangeFamily.FAMILY_5); // Hindi
          put("hr", CardinalityRangeFamily.FAMILY_8); // Croatian
          put("hsb", CardinalityRangeFamily.FAMILY_1); // Upper Sorbian
          put("hu", CardinalityRangeFamily.FAMILY_2); // Hungarian
          put("hy", CardinalityRangeFamily.FAMILY_5); // Armenian
          put("id", CardinalityRangeFamily.FAMILY_4); // Indonesian
          put("ig", CardinalityRangeFamily.FAMILY_1); // Igbo
          put("ii", CardinalityRangeFamily.FAMILY_1); // Sichuan Yi
          put("is", CardinalityRangeFamily.FAMILY_6); // Icelandic
          put("it", CardinalityRangeFamily.FAMILY_2); // Italian
          put("iu", CardinalityRangeFamily.FAMILY_1); // Inuktitut
          put("ja", CardinalityRangeFamily.FAMILY_4); // Japanese
          put("jbo", CardinalityRangeFamily.FAMILY_1); // Lojban
          put("jgo", CardinalityRangeFamily.FAMILY_1); // Ngomba
          put("jmc", CardinalityRangeFamily.FAMILY_1); // Machame
          put("jv", CardinalityRangeFamily.FAMILY_1); // Javanese
          put("jw", CardinalityRangeFamily.FAMILY_1); // Javanese
          put("ka", CardinalityRangeFamily.FAMILY_15); // Georgian
          put("kab", CardinalityRangeFamily.FAMILY_1); // Kabyle
          put("kaj", CardinalityRangeFamily.FAMILY_1); // Jju
          put("kcg", CardinalityRangeFamily.FAMILY_1); // Tyap
          put("kde", CardinalityRangeFamily.FAMILY_1); // Makonde
          put("kea", CardinalityRangeFamily.FAMILY_1); // Kabuverdianu
          put("kk", CardinalityRangeFamily.FAMILY_2); // Kazakh
          put("kkj", CardinalityRangeFamily.FAMILY_1); // Kako
          put("kl", CardinalityRangeFamily.FAMILY_1); // Greenlandic
          put("km", CardinalityRangeFamily.FAMILY_4); // Khmer
          put("kn", CardinalityRangeFamily.FAMILY_5); // Kannada
          put("ko", CardinalityRangeFamily.FAMILY_4); // Korean
          put("ks", CardinalityRangeFamily.FAMILY_1); // Kashmiri
          put("ksb", CardinalityRangeFamily.FAMILY_1); // Shambala
          put("ksh", CardinalityRangeFamily.FAMILY_1); // Colognian
          put("ku", CardinalityRangeFamily.FAMILY_1); // Kurdish
          put("kw", CardinalityRangeFamily.FAMILY_1); // Cornish
          put("ky", CardinalityRangeFamily.FAMILY_2); // Kirghiz
          put("lag", CardinalityRangeFamily.FAMILY_1); // Langi
          put("lb", CardinalityRangeFamily.FAMILY_1); // Luxembourgish
          put("lg", CardinalityRangeFamily.FAMILY_1); // Ganda
          put("lkt", CardinalityRangeFamily.FAMILY_1); // Lakota
          put("ln", CardinalityRangeFamily.FAMILY_1); // Lingala
          put("lo", CardinalityRangeFamily.FAMILY_4); // Lao
          put("lt", CardinalityRangeFamily.FAMILY_7); // Lithuanian
          put("lv", CardinalityRangeFamily.FAMILY_16); // Latvian
          put("mas", CardinalityRangeFamily.FAMILY_1); // Masai
          put("mg", CardinalityRangeFamily.FAMILY_1); // Malagasy
          put("mgo", CardinalityRangeFamily.FAMILY_1); // Metaʼ
          put("mk", CardinalityRangeFamily.FAMILY_17); // Macedonian
          put("ml", CardinalityRangeFamily.FAMILY_2); // Malayalam
          put("mn", CardinalityRangeFamily.FAMILY_2); // Mongolian
          put("mo", CardinalityRangeFamily.FAMILY_1); // Moldovan
          put("mr", CardinalityRangeFamily.FAMILY_5); // Marathi
          put("ms", CardinalityRangeFamily.FAMILY_4); // Malay
          put("mt", CardinalityRangeFamily.FAMILY_1); // Maltese
          put("my", CardinalityRangeFamily.FAMILY_4); // Burmese
          put("nah", CardinalityRangeFamily.FAMILY_1); // Nahuatl
          put("naq", CardinalityRangeFamily.FAMILY_1); // Nama
          put("nb", CardinalityRangeFamily.FAMILY_3); // Norwegian Bokmål
          put("nd", CardinalityRangeFamily.FAMILY_1); // North Ndebele
          put("ne", CardinalityRangeFamily.FAMILY_2); // Nepali
          put("nl", CardinalityRangeFamily.FAMILY_2); // Dutch
          put("nn", CardinalityRangeFamily.FAMILY_1); // Norwegian Nynorsk
          put("nnh", CardinalityRangeFamily.FAMILY_1); // Ngiemboon
          put("no", CardinalityRangeFamily.FAMILY_1); // Norwegian
          put("nqo", CardinalityRangeFamily.FAMILY_1); // N’Ko
          put("nr", CardinalityRangeFamily.FAMILY_1); // South Ndebele
          put("nso", CardinalityRangeFamily.FAMILY_1); // Northern Sotho
          put("ny", CardinalityRangeFamily.FAMILY_1); // Nyanja
          put("nyn", CardinalityRangeFamily.FAMILY_1); // Nyankole
          put("om", CardinalityRangeFamily.FAMILY_1); // Oromo
          put("or", CardinalityRangeFamily.FAMILY_1); // Odia
          put("os", CardinalityRangeFamily.FAMILY_1); // Ossetian
          put("pa", CardinalityRangeFamily.FAMILY_6); // Punjabi
          put("pap", CardinalityRangeFamily.FAMILY_1); // Papiamento
          put("pl", CardinalityRangeFamily.FAMILY_9); // Polish
          put("prg", CardinalityRangeFamily.FAMILY_1); // Prussian
          put("ps", CardinalityRangeFamily.FAMILY_1); // Pushto
          put("pt", CardinalityRangeFamily.FAMILY_6); // Portuguese
          put("rm", CardinalityRangeFamily.FAMILY_1); // Romansh
          put("ro", CardinalityRangeFamily.FAMILY_18); // Romanian
          put("rof", CardinalityRangeFamily.FAMILY_1); // Rombo
          put("root", CardinalityRangeFamily.FAMILY_1); // Root
          put("ru", CardinalityRangeFamily.FAMILY_7); // Russian
          put("rwk", CardinalityRangeFamily.FAMILY_1); // Rwa
          put("sah", CardinalityRangeFamily.FAMILY_1); // Sakha
          put("saq", CardinalityRangeFamily.FAMILY_1); // Samburu
          put("sdh", CardinalityRangeFamily.FAMILY_1); // Southern Kurdish
          put("se", CardinalityRangeFamily.FAMILY_1); // Northern Sami
          put("seh", CardinalityRangeFamily.FAMILY_1); // Sena
          put("ses", CardinalityRangeFamily.FAMILY_1); // Koyraboro Senni
          put("sg", CardinalityRangeFamily.FAMILY_1); // Sango
          put("sh", CardinalityRangeFamily.FAMILY_1); // Serbo-Croatian
          put("shi", CardinalityRangeFamily.FAMILY_1); // Tachelhit
          put("si", CardinalityRangeFamily.FAMILY_19); // Sinhalese
          put("sk", CardinalityRangeFamily.FAMILY_9); // Slovak
          put("sl", CardinalityRangeFamily.FAMILY_20); // Slovenian
          put("sma", CardinalityRangeFamily.FAMILY_1); // Southern Sami
          put("smi", CardinalityRangeFamily.FAMILY_1); // Sami
          put("smj", CardinalityRangeFamily.FAMILY_1); // Lule Sami
          put("smn", CardinalityRangeFamily.FAMILY_1); // Inari Sami
          put("sms", CardinalityRangeFamily.FAMILY_1); // Skolt Sami
          put("sn", CardinalityRangeFamily.FAMILY_1); // Shona
          put("so", CardinalityRangeFamily.FAMILY_1); // Somali
          put("sq", CardinalityRangeFamily.FAMILY_2); // Albanian
          put("sr", CardinalityRangeFamily.FAMILY_8); // Serbian
          put("ss", CardinalityRangeFamily.FAMILY_1); // Swati
          put("ssy", CardinalityRangeFamily.FAMILY_1); // Saho
          put("st", CardinalityRangeFamily.FAMILY_1); // Southern Sotho
          put("sv", CardinalityRangeFamily.FAMILY_3); // Swedish
          put("sw", CardinalityRangeFamily.FAMILY_2); // Swahili
          put("syr", CardinalityRangeFamily.FAMILY_1); // Syriac
          put("ta", CardinalityRangeFamily.FAMILY_2); // Tamil
          put("te", CardinalityRangeFamily.FAMILY_2); // Telugu
          put("teo", CardinalityRangeFamily.FAMILY_1); // Teso
          put("th", CardinalityRangeFamily.FAMILY_4); // Thai
          put("ti", CardinalityRangeFamily.FAMILY_1); // Tigrinya
          put("tig", CardinalityRangeFamily.FAMILY_1); // Tigre
          put("tk", CardinalityRangeFamily.FAMILY_1); // Turkmen
          put("tl", CardinalityRangeFamily.FAMILY_1); // Tagalog
          put("tn", CardinalityRangeFamily.FAMILY_1); // Tswana
          put("to", CardinalityRangeFamily.FAMILY_1); // Tongan
          put("tr", CardinalityRangeFamily.FAMILY_2); // Turkish
          put("ts", CardinalityRangeFamily.FAMILY_1); // Tsonga
          put("tzm", CardinalityRangeFamily.FAMILY_1); // Central Atlas Tamazight
          put("ug", CardinalityRangeFamily.FAMILY_2); // Uighur
          put("uk", CardinalityRangeFamily.FAMILY_7); // Ukrainian
          put("ur", CardinalityRangeFamily.FAMILY_3); // Urdu
          put("uz", CardinalityRangeFamily.FAMILY_2); // Uzbek
          put("ve", CardinalityRangeFamily.FAMILY_1); // Venda
          put("vi", CardinalityRangeFamily.FAMILY_4); // Vietnamese
          put("vo", CardinalityRangeFamily.FAMILY_1); // Volapük
          put("vun", CardinalityRangeFamily.FAMILY_1); // Vunjo
          put("wa", CardinalityRangeFamily.FAMILY_1); // Walloon
          put("wae", CardinalityRangeFamily.FAMILY_1); // Walser
          put("wo", CardinalityRangeFamily.FAMILY_1); // Wolof
          put("xh", CardinalityRangeFamily.FAMILY_1); // Xhosa
          put("xog", CardinalityRangeFamily.FAMILY_1); // Soga
          put("yi", CardinalityRangeFamily.FAMILY_1); // Yiddish
          put("yo", CardinalityRangeFamily.FAMILY_1); // Yoruba
          put("yue", CardinalityRangeFamily.FAMILY_4); // Cantonese
          put("zh", CardinalityRangeFamily.FAMILY_4); // Mandarin Chinese
          put("zu", CardinalityRangeFamily.FAMILY_5); // Zulu
        }
      });
    }

    /**
     * Gets an appropriate plural cardinality range family for the given locale.
     *
     * @param locale the locale to check, not null
     * @return the appropriate plural cardinality range family (if one exists) for the given locale, not null
     */
    @Nonnull
    static Optional<CardinalityRangeFamily> cardinalityRangeFamilyForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      String language = LocaleUtils.normalizedLanguage(locale).orElse(null);
      String country = locale.getCountry();

      CardinalityRangeFamily cardinalityRangeFamily = null;

      if (language != null && country != null)
        cardinalityRangeFamily = CARDINALITY_RANGE_FAMILIES_BY_LANGUAGE_CODE.get(format("%s-%s", language, country));

      if (cardinalityRangeFamily != null)
        return Optional.of(cardinalityRangeFamily);

      if (language != null)
        cardinalityRangeFamily = CARDINALITY_RANGE_FAMILIES_BY_LANGUAGE_CODE.get(language);

      return Optional.ofNullable(cardinalityRangeFamily);
    }

    /**
     * Constructs a cardinality range family.
     *
     * @param cardinalitiesByCardinalityRange a mapping of cardinalities to example integer values for this cardinality range family sorted by the natural ordering of {@link Cardinality}, not null
     */
    CardinalityRangeFamily(@Nonnull SortedMap<CardinalityRange, Cardinality> cardinalitiesByCardinalityRange) {
      this.cardinalitiesByCardinalityRange = cardinalitiesByCardinalityRange;
    }

    @Nonnull
    SortedMap<CardinalityRange, Cardinality> getCardinalitiesByCardinalityRange() {
      return cardinalitiesByCardinalityRange;
    }
  }
}