/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2025 Revetware LLC.
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
import static com.lokalized.NumberUtils.notInSet;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Language plural ordinality forms.
 * <p>
 * For example, English has four: {@code 1st, 2nd, 3rd, 4th}, while Swedish has two: {@code 1:a, 3:e}.
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
public enum Ordinality implements LanguageForm {
  /**
   * Normally the form used with 0, if it is limited to numbers whose integer values end with 0.
   * <p>
   * For example: the Welsh {@code 0fed ci} means "{@code 0th dog}" in English.
   */
  ZERO,
  /**
   * The form used with 1.
   * <p>
   * For example: the Welsh {@code ci 1af} means {@code 1st dog} in English.
   */
  ONE,
  /**
   * Normally the form used with 2, if it is limited to numbers whose integer values end with 2.
   * <p>
   * For example: the Welsh {@code 2il gi} means {@code 2nd dog} in English.
   */
  TWO,
  /**
   * The form that falls between {@code TWO} and {@code MANY}.
   * <p>
   * For example: the Welsh {@code 3ydd ci} means {@code 3rd dog} in English.
   */
  FEW,
  /**
   * The form that falls between {@code FEW} and {@code OTHER}.
   * <p>
   * For example: the Welsh {@code 5ed ci} means {@code 5th dog} in English.
   */
  MANY,
  /**
   * General "catchall" form which comprises any cases not handled by the other forms.
   * <p>
   * For example: the Welsh {@code ci rhif 10} means {@code 10th dog} in English.
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
  private static final BigInteger BIG_INTEGER_7;
  @Nonnull
  private static final BigInteger BIG_INTEGER_8;
  @Nonnull
  private static final BigInteger BIG_INTEGER_10;
  @Nonnull
  private static final BigInteger BIG_INTEGER_11;
  @Nonnull
  private static final BigInteger BIG_INTEGER_12;
  @Nonnull
  private static final BigInteger BIG_INTEGER_17;
  @Nonnull
  private static final BigInteger BIG_INTEGER_18;
  @Nonnull
  private static final BigInteger BIG_INTEGER_20;
  @Nonnull
  private static final BigInteger BIG_INTEGER_40;
  @Nonnull
  private static final BigInteger BIG_INTEGER_50;
  @Nonnull
  private static final BigInteger BIG_INTEGER_60;
  @Nonnull
  private static final BigInteger BIG_INTEGER_70;
  @Nonnull
  private static final BigInteger BIG_INTEGER_80;
  @Nonnull
  private static final BigInteger BIG_INTEGER_90;
  @Nonnull
  private static final BigInteger BIG_INTEGER_100;
  @Nonnull
  private static final BigInteger BIG_INTEGER_200;
  @Nonnull
  private static final BigInteger BIG_INTEGER_300;
  @Nonnull
  private static final BigInteger BIG_INTEGER_400;
  @Nonnull
  private static final BigInteger BIG_INTEGER_500;
  @Nonnull
  private static final BigInteger BIG_INTEGER_600;
  @Nonnull
  private static final BigInteger BIG_INTEGER_700;
  @Nonnull
  private static final BigInteger BIG_INTEGER_800;
  @Nonnull
  private static final BigInteger BIG_INTEGER_900;
  @Nonnull
  private static final BigInteger BIG_INTEGER_1_000;

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
  private static final BigDecimal BIG_DECIMAL_8;
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
  private static final BigDecimal BIG_DECIMAL_80;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_100;
  @Nonnull
  private static final BigDecimal BIG_DECIMAL_800;

  @Nonnull
  static final Map<String, Ordinality> ORDINALITIES_BY_NAME;

  static {
    BIG_INTEGER_0 = BigInteger.ZERO;
    BIG_INTEGER_1 = BigInteger.ONE;
    BIG_INTEGER_2 = BigInteger.valueOf(2);
    BIG_INTEGER_3 = BigInteger.valueOf(3);
    BIG_INTEGER_4 = BigInteger.valueOf(4);
    BIG_INTEGER_5 = BigInteger.valueOf(5);
    BIG_INTEGER_6 = BigInteger.valueOf(6);
    BIG_INTEGER_7 = BigInteger.valueOf(7);
    BIG_INTEGER_8 = BigInteger.valueOf(8);
    BIG_INTEGER_10 = BigInteger.TEN;
    BIG_INTEGER_11 = BigInteger.valueOf(11);
    BIG_INTEGER_12 = BigInteger.valueOf(12);
    BIG_INTEGER_17 = BigInteger.valueOf(17);
    BIG_INTEGER_18 = BigInteger.valueOf(18);
    BIG_INTEGER_20 = BigInteger.valueOf(20);
    BIG_INTEGER_40 = BigInteger.valueOf(40);
    BIG_INTEGER_50 = BigInteger.valueOf(50);
    BIG_INTEGER_60 = BigInteger.valueOf(60);
    BIG_INTEGER_70 = BigInteger.valueOf(70);
    BIG_INTEGER_80 = BigInteger.valueOf(80);
    BIG_INTEGER_90 = BigInteger.valueOf(90);
    BIG_INTEGER_100 = BigInteger.valueOf(100);
    BIG_INTEGER_200 = BigInteger.valueOf(200);
    BIG_INTEGER_300 = BigInteger.valueOf(300);
    BIG_INTEGER_400 = BigInteger.valueOf(400);
    BIG_INTEGER_500 = BigInteger.valueOf(500);
    BIG_INTEGER_600 = BigInteger.valueOf(600);
    BIG_INTEGER_700 = BigInteger.valueOf(700);
    BIG_INTEGER_800 = BigInteger.valueOf(800);
    BIG_INTEGER_900 = BigInteger.valueOf(900);
    BIG_INTEGER_1_000 = BigInteger.valueOf(1_000);

    BIG_DECIMAL_0 = BigDecimal.ZERO;
    BIG_DECIMAL_1 = BigDecimal.ONE;
    BIG_DECIMAL_2 = BigDecimal.valueOf(2);
    BIG_DECIMAL_3 = BigDecimal.valueOf(3);
    BIG_DECIMAL_4 = BigDecimal.valueOf(4);
    BIG_DECIMAL_5 = BigDecimal.valueOf(5);
    BIG_DECIMAL_6 = BigDecimal.valueOf(6);
    BIG_DECIMAL_7 = BigDecimal.valueOf(7);
    BIG_DECIMAL_8 = BigDecimal.valueOf(8);
    BIG_DECIMAL_9 = BigDecimal.valueOf(9);
    BIG_DECIMAL_10 = BigDecimal.TEN;
    BIG_DECIMAL_11 = BigDecimal.valueOf(11);
    BIG_DECIMAL_12 = BigDecimal.valueOf(12);
    BIG_DECIMAL_13 = BigDecimal.valueOf(13);
    BIG_DECIMAL_14 = BigDecimal.valueOf(14);
    BIG_DECIMAL_80 = BigDecimal.valueOf(80);
    BIG_DECIMAL_100 = BigDecimal.valueOf(100);
    BIG_DECIMAL_800 = BigDecimal.valueOf(800);

    ORDINALITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Ordinality.values()).collect(Collectors.toMap(ordinality -> ordinality.name(), ordinality -> ordinality)));
  }

  /**
   * Gets an appropriate plural ordinality for the given number and locale.
   * <p>
   * See <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html</a>
   * for a cheat sheet.
   *
   * @param number the number that drives pluralization, not null
   * @param locale the locale that drives pluralization, not null
   * @return an appropriate plural ordinality, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   */
  @Nonnull
  public static Ordinality forNumber(@Nonnull Number number, @Nonnull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    BigDecimal numberAsBigDecimal = NumberUtils.toBigDecimal(number);

    Optional<OrdinalityFamily> ordinalityFamily = OrdinalityFamily.ordinalityFamilyForLocale(locale);

    // TODO: throwing an exception might not be the best solution here...need to think about it
    if (!ordinalityFamily.isPresent())
      throw new UnsupportedLocaleException(locale);

    return ordinalityFamily.get().getOrdinalityFunction().apply(numberAsBigDecimal);
  }

  /**
   * Gets the set of ordinalities supported for the given locale.
   * <p>
   * The empty set will be returned if the locale is not supported.
   * <p>
   * The set's values are sorted by the natural ordering of the {@link Ordinality} enumeration.
   *
   * @param locale the locale to use for lookup, not null
   * @return the ordinalities supported by the given locale, not null
   */
  @Nonnull
  public static SortedSet<Ordinality> supportedOrdinalitiesForLocale(@Nonnull Locale locale) {
    requireNonNull(locale);

    Optional<OrdinalityFamily> ordinalityFamily = OrdinalityFamily.ordinalityFamilyForLocale(locale);
    return ordinalityFamily.isPresent() ? ordinalityFamily.get().getSupportedOrdinalities() : Collections.emptySortedSet();
  }

  /**
   * Gets a mapping of ordinalities to example integer values for the given locale.
   * <p>
   * The empty map will be returned if the locale is not supported or if no example values are available.
   * <p>
   * The map's keys are sorted by the natural ordering of the {@link Ordinality} enumeration.
   *
   * @param locale the locale to use for lookup, not null
   * @return a mapping of ordinalities to example integer values, not null
   */
  @Nonnull
  public static SortedMap<Ordinality, Range<Integer>> exampleIntegerValuesForLocale(@Nonnull Locale locale) {
    requireNonNull(locale);

    Optional<OrdinalityFamily> ordinalityFamily = OrdinalityFamily.ordinalityFamilyForLocale(locale);
    return ordinalityFamily.isPresent() ? ordinalityFamily.get().getExampleIntegerValuesByOrdinality() : Collections.emptySortedMap();
  }

  /**
   * Gets the ISO 639 language codes for which ordinality operations are supported.
   * <p>
   * The set's values are ISO 639 codes and therefore sorted using English collation.
   *
   * @return the ISO 639 language codes for which ordinality operations are supported, not null
   */
  @Nonnull
  public static SortedSet<String> getSupportedLanguageCodes() {
    return OrdinalityFamily.getSupportedLanguageCodes();
  }

  /**
   * Gets the mapping of ordinality names to values.
   *
   * @return the mapping of ordinality names to values, not null
   */
  @Nonnull
  static Map<String, Ordinality> getOrdinalitiesByName() {
    return ORDINALITIES_BY_NAME;
  }

  /**
   * Plural ordinality forms grouped by language family.
   * <p>
   * Each family has a distinct ordinality calculation rule.
   * <p>
   * See <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html</a>
   * for a cheat sheet.
   */
  enum OrdinalityFamily {
    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Afrikaans (af)</li>
     * <li>Akan (ak) (no CLDR data available)</li>
     * <li>Amharic (am)</li>
     * <li>Arabic (ar)</li>
     * <li>Najdi Arabic (ars) (no CLDR data available)</li>
     * <li>Asu (asa) (no CLDR data available)</li>
     * <li>Asturian (ast) (no CLDR data available)</li>
     * <li>Bemba (bem) (no CLDR data available)</li>
     * <li>Bena (bez) (no CLDR data available)</li>
     * <li>Bulgarian (bg)</li>
     * <li>Bihari (bh) (no CLDR data available)</li>
     * <li>Bambara (bm) (no CLDR data available)</li>
     * <li>Tibetan (bo) (no CLDR data available)</li>
     * <li>Breton (br) (no CLDR data available)</li>
     * <li>Bodo (brx) (no CLDR data available)</li>
     * <li>Bosnian (bs)</li>
     * <li>Chechen (ce)</li>
     * <li>Chiga (cgg) (no CLDR data available)</li>
     * <li>Cherokee (chr) (no CLDR data available)</li>
     * <li>Central Kurdish (ckb) (no CLDR data available)</li>
     * <li>Czech (cs)</li>
     * <li>Danish (da)</li>
     * <li>German (de)</li>
     * <li>Lower Sorbian (dsb)</li>
     * <li>Divehi (dv) (no CLDR data available)</li>
     * <li>Dzongkha (dz) (no CLDR data available)</li>
     * <li>Ewe (ee) (no CLDR data available)</li>
     * <li>Greek (el)</li>
     * <li>Esperanto (eo) (no CLDR data available)</li>
     * <li>Spanish (es)</li>
     * <li>Estonian (et)</li>
     * <li>Basque (eu)</li>
     * <li>Persian (fa)</li>
     * <li>Fulah (ff) (no CLDR data available)</li>
     * <li>Finnish (fi)</li>
     * <li>Faroese (fo) (no CLDR data available)</li>
     * <li>Friulian (fur) (no CLDR data available)</li>
     * <li>Western Frisian (fy)</li>
     * <li>Scottish Gaelic (gd) (no CLDR data available)</li>
     * <li>Galician (gl)</li>
     * <li>Swiss German (gsw)</li>
     * <li>Gun (guw) (no CLDR data available)</li>
     * <li>Manx (gv) (no CLDR data available)</li>
     * <li>Hausa (ha) (no CLDR data available)</li>
     * <li>Hawaiian (haw) (no CLDR data available)</li>
     * <li>Hebrew (he)</li>
     * <li>Croatian (hr)</li>
     * <li>Upper Sorbian (hsb)</li>
     * <li>Indonesian (id)</li>
     * <li>Igbo (ig) (no CLDR data available)</li>
     * <li>Sichuan Yi (ii) (no CLDR data available)</li>
     * <li>Icelandic (is)</li>
     * <li>Inuktitut (iu) (no CLDR data available)</li>
     * <li>Japanese (ja)</li>
     * <li>Lojban (jbo) (no CLDR data available)</li>
     * <li>Ngomba (jgo) (no CLDR data available)</li>
     * <li>Machame (jmc) (no CLDR data available)</li>
     * <li>Javanese (jv) (no CLDR data available)</li>
     * <li>Javanese (jw) (no CLDR data available)</li>
     * <li>Kabyle (kab) (no CLDR data available)</li>
     * <li>Jju (kaj) (no CLDR data available)</li>
     * <li>Tyap (kcg) (no CLDR data available)</li>
     * <li>Makonde (kde) (no CLDR data available)</li>
     * <li>Kabuverdianu (kea) (no CLDR data available)</li>
     * <li>Kako (kkj) (no CLDR data available)</li>
     * <li>Greenlandic (kl) (no CLDR data available)</li>
     * <li>Khmer (km)</li>
     * <li>Kannada (kn)</li>
     * <li>Korean (ko)</li>
     * <li>Kashmiri (ks) (no CLDR data available)</li>
     * <li>Shambala (ksb) (no CLDR data available)</li>
     * <li>Colognian (ksh) (no CLDR data available)</li>
     * <li>Kurdish (ku) (no CLDR data available)</li>
     * <li>Cornish (kw) (no CLDR data available)</li>
     * <li>Kirghiz (ky)</li>
     * <li>Langi (lag) (no CLDR data available)</li>
     * <li>Luxembourgish (lb) (no CLDR data available)</li>
     * <li>Ganda (lg) (no CLDR data available)</li>
     * <li>Lakota (lkt) (no CLDR data available)</li>
     * <li>Lingala (ln) (no CLDR data available)</li>
     * <li>Lithuanian (lt)</li>
     * <li>Latvian (lv)</li>
     * <li>Masai (mas) (no CLDR data available)</li>
     * <li>Malagasy (mg) (no CLDR data available)</li>
     * <li>Metaʼ (mgo) (no CLDR data available)</li>
     * <li>Malayalam (ml)</li>
     * <li>Mongolian (mn)</li>
     * <li>Maltese (mt) (no CLDR data available)</li>
     * <li>Burmese (my)</li>
     * <li>Nahuatl (nah) (no CLDR data available)</li>
     * <li>Nama (naq) (no CLDR data available)</li>
     * <li>Norwegian Bokmål (nb)</li>
     * <li>North Ndebele (nd) (no CLDR data available)</li>
     * <li>Dutch (nl)</li>
     * <li>Norwegian Nynorsk (nn) (no CLDR data available)</li>
     * <li>Ngiemboon (nnh) (no CLDR data available)</li>
     * <li>Norwegian (no) (no CLDR data available)</li>
     * <li>N’Ko (nqo) (no CLDR data available)</li>
     * <li>South Ndebele (nr) (no CLDR data available)</li>
     * <li>Northern Sotho (nso) (no CLDR data available)</li>
     * <li>Nyanja (ny) (no CLDR data available)</li>
     * <li>Nyankole (nyn) (no CLDR data available)</li>
     * <li>Oromo (om) (no CLDR data available)</li>
     * <li>Odia (or) (no CLDR data available)</li>
     * <li>Ossetian (os) (no CLDR data available)</li>
     * <li>Punjabi (pa)</li>
     * <li>Papiamento (pap) (no CLDR data available)</li>
     * <li>Polish (pl)</li>
     * <li>Prussian (prg)</li>
     * <li>Pushto (ps) (no CLDR data available)</li>
     * <li>Portuguese (pt)</li>
     * <li>Romansh (rm) (no CLDR data available)</li>
     * <li>Rombo (rof) (no CLDR data available)</li>
     * <li>Root (root)</li>
     * <li>Russian (ru)</li>
     * <li>Rwa (rwk) (no CLDR data available)</li>
     * <li>Sakha (sah) (no CLDR data available)</li>
     * <li>Samburu (saq) (no CLDR data available)</li>
     * <li>Southern Kurdish (sdh) (no CLDR data available)</li>
     * <li>Northern Sami (se) (no CLDR data available)</li>
     * <li>Sena (seh) (no CLDR data available)</li>
     * <li>Koyraboro Senni (ses) (no CLDR data available)</li>
     * <li>Sango (sg) (no CLDR data available)</li>
     * <li>Serbo-Croatian (sh)</li>
     * <li>Tachelhit (shi) (no CLDR data available)</li>
     * <li>Sinhalese (si)</li>
     * <li>Slovak (sk)</li>
     * <li>Slovenian (sl)</li>
     * <li>Southern Sami (sma) (no CLDR data available)</li>
     * <li>Sami (smi) (no CLDR data available)</li>
     * <li>Lule Sami (smj) (no CLDR data available)</li>
     * <li>Inari Sami (smn) (no CLDR data available)</li>
     * <li>Skolt Sami (sms) (no CLDR data available)</li>
     * <li>Shona (sn) (no CLDR data available)</li>
     * <li>Somali (so) (no CLDR data available)</li>
     * <li>Serbian (sr)</li>
     * <li>Swati (ss) (no CLDR data available)</li>
     * <li>Saho (ssy) (no CLDR data available)</li>
     * <li>Southern Sotho (st) (no CLDR data available)</li>
     * <li>Swahili (sw)</li>
     * <li>Syriac (syr) (no CLDR data available)</li>
     * <li>Tamil (ta)</li>
     * <li>Telugu (te)</li>
     * <li>Teso (teo) (no CLDR data available)</li>
     * <li>Thai (th)</li>
     * <li>Tigrinya (ti) (no CLDR data available)</li>
     * <li>Tigre (tig) (no CLDR data available)</li>
     * <li>Turkmen (tk) (no CLDR data available)</li>
     * <li>Tswana (tn) (no CLDR data available)</li>
     * <li>Tongan (to) (no CLDR data available)</li>
     * <li>Turkish (tr)</li>
     * <li>Tsonga (ts) (no CLDR data available)</li>
     * <li>Central Atlas Tamazight (tzm) (no CLDR data available)</li>
     * <li>Uighur (ug) (no CLDR data available)</li>
     * <li>Urdu (ur)</li>
     * <li>Uzbek (uz)</li>
     * <li>Venda (ve) (no CLDR data available)</li>
     * <li>Volapük (vo) (no CLDR data available)</li>
     * <li>Vunjo (vun) (no CLDR data available)</li>
     * <li>Walloon (wa) (no CLDR data available)</li>
     * <li>Walser (wae) (no CLDR data available)</li>
     * <li>Wolof (wo) (no CLDR data available)</li>
     * <li>Xhosa (xh) (no CLDR data available)</li>
     * <li>Soga (xog) (no CLDR data available)</li>
     * <li>Yiddish (yi) (no CLDR data available)</li>
     * <li>Yoruba (yo) (no CLDR data available)</li>
     * <li>Cantonese (yue)</li>
     * <li>Mandarin Chinese (zh)</li>
     * <li>Zulu (zu)</li>
     * </ul>
     */
    FAMILY_1(
        (n) -> {
          // No ordinality rules for this family
          return OTHER;
        },
        Sets.sortedSet(
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Filipino (fil)</li>
     * <li>French (fr)</li>
     * <li>Irish (ga)</li>
     * <li>Armenian (hy)</li>
     * <li>Lao (lo)</li>
     * <li>Moldovan (mo)</li>
     * <li>Malay (ms)</li>
     * <li>Romanian (ro)</li>
     * <li>Tagalog (tl)</li>
     * <li>Vietnamese (vi)</li>
     * </ul>
     */
    FAMILY_2(
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
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Assamese (as)</li>
     * <li>Bangla (bn)</li>
     * </ul>
     */
    FAMILY_3(
        (n) -> {
          // n = 1,5,7,8,9,10
          if (inSet(n, BIG_DECIMAL_1, BIG_DECIMAL_5, BIG_DECIMAL_7, BIG_DECIMAL_8, BIG_DECIMAL_9, BIG_DECIMAL_10))
            return ONE;
          // n = 2,3
          if (inSet(n, BIG_DECIMAL_2, BIG_DECIMAL_3))
            return TWO;
          // n = 4
          if (equal(n, BIG_DECIMAL_4))
            return FEW;
          // n = 6
          if (equal(n, BIG_DECIMAL_6))
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
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1, 5, 7, 8, 9, 10)),
            MapEntry.of(Ordinality.TWO, Range.ofFiniteValues(2, 3)),
            MapEntry.of(Ordinality.FEW, Range.ofFiniteValues(4)),
            MapEntry.of(Ordinality.MANY, Range.ofFiniteValues(6)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Gujarati (gu)</li>
     * <li>Hindi (hi)</li>
     * </ul>
     */
    FAMILY_4(
        (n) -> {
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2,3
          if (inSet(n, BIG_DECIMAL_2, BIG_DECIMAL_3))
            return TWO;
          // n = 4
          if (equal(n, BIG_DECIMAL_4))
            return FEW;
          // n = 6
          if (equal(n, BIG_DECIMAL_6))
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
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Ordinality.TWO, Range.ofFiniteValues(2, 3)),
            MapEntry.of(Ordinality.FEW, Range.ofFiniteValues(4)),
            MapEntry.of(Ordinality.MANY, Range.ofFiniteValues(6)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Azeri (az)</li>
     * </ul>
     */
    FAMILY_5(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);

          // i % 10 = 1,2,5,7,8 or i % 100 = 20,50,70,80
          if (inSet(i.mod(BIG_INTEGER_10), BIG_INTEGER_1, BIG_INTEGER_2, BIG_INTEGER_5, BIG_INTEGER_7, BIG_INTEGER_8)
              || inSet(i.mod(BIG_INTEGER_100), BIG_INTEGER_20, BIG_INTEGER_50, BIG_INTEGER_70, BIG_INTEGER_80))
            return ONE;
          // i % 10 = 3,4 or i % 1000 = 100,200,300,400,500,600,700,800,900
          if (inSet(i.mod(BIG_INTEGER_10), BIG_INTEGER_3, BIG_INTEGER_4)
              || inSet(i.mod(BIG_INTEGER_1_000), BIG_INTEGER_100, BIG_INTEGER_200, BIG_INTEGER_300, BIG_INTEGER_400, BIG_INTEGER_500, BIG_INTEGER_600, BIG_INTEGER_700, BIG_INTEGER_800, BIG_INTEGER_900))
            return FEW;
          // i = 0 or i % 10 = 6 or i % 100 = 40,60,90
          if (equal(i, BIG_INTEGER_0)
              || equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_6)
              || inSet(i.mod(BIG_INTEGER_100), BIG_INTEGER_40, BIG_INTEGER_60, BIG_INTEGER_90))
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
            MapEntry.of(Ordinality.ONE, Range.ofInfiniteValues(1, 2, 5, 7, 8, 11, 12, 15, 17, 18, 20, 21, 22, 25, 101, 1001)),
            MapEntry.of(Ordinality.FEW, Range.ofInfiniteValues(3, 4, 13, 14, 23, 24, 33, 34, 43, 44, 53, 54, 63, 64, 73, 74, 100, 1003)),
            MapEntry.of(Ordinality.MANY, Range.ofInfiniteValues(0, 6, 16, 26, 36, 40, 46, 56, 106, 1006)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(9, 10, 19, 29, 30, 39, 49, 59, 69, 79, 109, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Belarusian (be)</li>
     * </ul>
     */
    FAMILY_6(
        (n) -> {
          // n % 10 = 2,3 and n % 100 != 12,13
          if (inSet(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_2, BIG_DECIMAL_3)
              && notInSet(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_12, BIG_DECIMAL_13))
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.FEW, Range.ofInfiniteValues(2, 3, 22, 23, 32, 33, 42, 43, 52, 53, 62, 63, 72, 73, 82, 83, 102, 1002)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Catalan (ca)</li>
     * </ul>
     */
    FAMILY_7(
        (n) -> {
          // n = 1,3
          if (inSet(n, BIG_DECIMAL_1, BIG_DECIMAL_3))
            return ONE;
          // n = 2
          if (equal(n, BIG_DECIMAL_2))
            return TWO;
          // n = 4
          if (equal(n, BIG_DECIMAL_4))
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
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1, 3)),
            MapEntry.of(Ordinality.TWO, Range.ofFiniteValues(2)),
            MapEntry.of(Ordinality.FEW, Range.ofFiniteValues(4)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Welsh (cy)</li>
     * </ul>
     */
    FAMILY_8(
        (n) -> {
          // n = 0,7,8,9
          if (inSet(n, BIG_DECIMAL_0, BIG_DECIMAL_7, BIG_DECIMAL_8, BIG_DECIMAL_9))
            return ZERO;
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2
          if (equal(n, BIG_DECIMAL_2))
            return TWO;
          // n = 3,4
          if (inSet(n, BIG_DECIMAL_3, BIG_DECIMAL_4))
            return FEW;
          // n = 5,6
          if (inSet(n, BIG_DECIMAL_5, BIG_DECIMAL_6))
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
            MapEntry.of(Ordinality.ZERO, Range.ofFiniteValues(0, 7, 8, 9)),
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Ordinality.TWO, Range.ofFiniteValues(2)),
            MapEntry.of(Ordinality.FEW, Range.ofFiniteValues(3, 4)),
            MapEntry.of(Ordinality.MANY, Range.ofFiniteValues(5, 6)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>English (en)</li>
     * </ul>
     */
    FAMILY_9(
        (n) -> {
          // n % 10 = 1 and n % 100 != 11
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_1) && notEqual(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11))
            return ONE;
          // n % 10 = 2 and n % 100 != 12
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_2) && notEqual(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_12))
            return TWO;
          // n % 10 = 3 and n % 100 != 13
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_3) && notEqual(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_13))
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
            MapEntry.of(Ordinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Ordinality.TWO, Range.ofInfiniteValues(2, 22, 32, 42, 52, 62, 72, 82, 102, 1002)),
            MapEntry.of(Ordinality.FEW, Range.ofInfiniteValues(3, 23, 33, 43, 53, 63, 73, 83, 103, 1003)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Hungarian (hu)</li>
     * </ul>
     */
    FAMILY_10(
        (n) -> {
          // n = 1,5
          if (inSet(n, BIG_DECIMAL_1, BIG_DECIMAL_5))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1, 5)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Italian (it)</li>
     * </ul>
     */
    FAMILY_11(
        (n) -> {
          // n = 11,8,80,800
          if (inSet(n, BIG_DECIMAL_11, BIG_DECIMAL_8, BIG_DECIMAL_80, BIG_DECIMAL_800))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.MANY, Range.ofFiniteValues(8, 11, 80, 800)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Georgian (ka)</li>
     * </ul>
     */
    FAMILY_12(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);

          // i = 1
          if (equal(i, BIG_INTEGER_1))
            return ONE;
          // i = 0 or i % 100 = 2..20,40,60,80
          if (equal(i, BIG_INTEGER_0) || inRange(i.mod(BIG_INTEGER_100), BIG_INTEGER_2, BIG_INTEGER_20) || inSet(i.mod(BIG_INTEGER_100), BIG_INTEGER_40, BIG_INTEGER_60, BIG_INTEGER_80))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Ordinality.MANY, Range.ofInfiniteValues(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 102, 1002)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Kazakh (kk)</li>
     * </ul>
     */
    FAMILY_13(
        (n) -> {
          // n % 10 = 6 or n % 10 = 9 or n % 10 = 0 and n != 0
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_6)
              || equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_9)
              || (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_0) && notEqual(n, BIG_DECIMAL_0)))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.MANY, Range.ofInfiniteValues(6, 9, 10, 16, 19, 20, 26, 29, 30, 36, 39, 40, 100, 1000, 10000, 100000, 1000000)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 1, 2, 3, 4, 5, 7, 8, 11, 12, 13, 14, 15, 17, 18, 21, 101, 1001))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Macedonian (mk)</li>
     * </ul>
     */
    FAMILY_14(
        (n) -> {
          BigInteger i = NumberUtils.integerComponent(n);

          // i % 10 = 1 and i % 100 != 11
          if (equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_1) && notEqual(i.mod(BIG_INTEGER_100), BIG_INTEGER_11))
            return ONE;
          // i % 10 = 2 and i % 100 != 12
          if (equal(i.mod(BIG_INTEGER_10), BIG_INTEGER_2) && notEqual(i.mod(BIG_INTEGER_100), BIG_INTEGER_12))
            return TWO;
          // i % 10 = 7,8 and i % 100 != 17,18
          if (inSet(i.mod(BIG_INTEGER_10), BIG_INTEGER_7, BIG_INTEGER_8) && notInSet(i.mod(BIG_INTEGER_100), BIG_INTEGER_17, BIG_INTEGER_18))
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
            MapEntry.of(Ordinality.ONE, Range.ofInfiniteValues(1, 21, 31, 41, 51, 61, 71, 81, 101, 1001)),
            MapEntry.of(Ordinality.TWO, Range.ofInfiniteValues(2, 22, 32, 42, 52, 62, 72, 82, 102, 1002)),
            MapEntry.of(Ordinality.MANY, Range.ofInfiniteValues(7, 8, 27, 28, 37, 38, 47, 48, 57, 58, 67, 68, 77, 78, 87, 88, 107, 1007)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Marathi (mr)</li>
     * </ul>
     */
    FAMILY_15(
        (n) -> {
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n = 2,3
          if (inSet(n, BIG_DECIMAL_2, BIG_DECIMAL_3))
            return TWO;
          // n = 4
          if (equal(n, BIG_DECIMAL_4))
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
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Ordinality.TWO, Range.ofFiniteValues(2, 3)),
            MapEntry.of(Ordinality.FEW, Range.ofFiniteValues(4)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Nepali (ne)</li>
     * </ul>
     */
    FAMILY_16(
        (n) -> {
          // n = 1..4
          if (inRange(n, BIG_DECIMAL_1, BIG_DECIMAL_4))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1, 2, 3, 4)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Albanian (sq)</li>
     * </ul>
     */
    FAMILY_17(
        (n) -> {
          // n = 1
          if (equal(n, BIG_DECIMAL_1))
            return ONE;
          // n % 10 = 4 and n % 100 != 14
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_4) && notEqual(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_14))
            return MANY;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            MANY,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.ONE, Range.ofFiniteValues(1)),
            MapEntry.of(Ordinality.MANY, Range.ofInfiniteValues(4, 24, 34, 44, 54, 64, 74, 84, 104, 1004)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Swedish (sv)</li>
     * </ul>
     */
    FAMILY_18(
        (n) -> {
          // n % 10 = 1,2 and n % 100 != 11,12
          if (inSet(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_1, BIG_DECIMAL_2) && notInSet(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_11, BIG_DECIMAL_12))
            return ONE;

          return OTHER;
        },
        Sets.sortedSet(
            ONE,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.ONE, Range.ofInfiniteValues(1, 2, 21, 22, 31, 32, 41, 42, 51, 52, 61, 62, 71, 72, 81, 82, 101, 1001)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 100, 1000, 10000, 100000, 1000000))
        )
    ),

    /**
     * Languages Include:
     * <p>
     * <ul>
     * <li>Ukrainian (uk)</li>
     * </ul>
     */
    FAMILY_19(
        (n) -> {
          // n % 10 = 3 and n % 100 != 13
          if (equal(n.remainder(BIG_DECIMAL_10), BIG_DECIMAL_3) && notEqual(n.remainder(BIG_DECIMAL_100), BIG_DECIMAL_13))
            return FEW;

          return OTHER;
        },
        Sets.sortedSet(
            FEW,
            OTHER
        ),
        Maps.sortedMap(
            MapEntry.of(Ordinality.FEW, Range.ofInfiniteValues(3, 23, 33, 43, 53, 63, 73, 83, 103, 1003)),
            MapEntry.of(Ordinality.OTHER, Range.ofInfiniteValues(0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 1000, 10000, 100000, 1000000))
        )
    );

    @Nonnull
    private static final Map<String, OrdinalityFamily> ORDINALITY_FAMILIES_BY_LANGUAGE_CODE;
    @Nonnull
    private static final SortedSet<String> SUPPORTED_LANGUAGE_CODES;

    @Nonnull
    private final Function<BigDecimal, Ordinality> ordinalityFunction;
    @Nonnull
    private final SortedSet<Ordinality> supportedOrdinalities;
    @Nonnull
    private final SortedMap<Ordinality, Range<Integer>> exampleIntegerValuesByOrdinality;

    /**
     * Constructs an ordinality family.
     *
     * @param ordinalityFunction               the ordinality-determining function for this ordinality family, not null
     * @param supportedOrdinalities            the ordinalities supported by this family sorted by the natural ordering of {@link Ordinality}, not null
     * @param exampleIntegerValuesByOrdinality a mapping of ordinalities to example integer values for this ordinality family sorted by the natural ordering of {@link Ordinality}, not null
     */
    OrdinalityFamily(@Nonnull Function<BigDecimal, Ordinality> ordinalityFunction,
                     @Nonnull SortedSet<Ordinality> supportedOrdinalities,
                     @Nonnull SortedMap<Ordinality, Range<Integer>> exampleIntegerValuesByOrdinality) {
      requireNonNull(ordinalityFunction);
      requireNonNull(supportedOrdinalities);
      requireNonNull(exampleIntegerValuesByOrdinality);

      this.ordinalityFunction = ordinalityFunction;
      this.supportedOrdinalities = supportedOrdinalities;
      this.exampleIntegerValuesByOrdinality = exampleIntegerValuesByOrdinality;
    }

    static {
      ORDINALITY_FAMILIES_BY_LANGUAGE_CODE = Collections.unmodifiableMap(new HashMap<String, OrdinalityFamily>() {{
        put("af", OrdinalityFamily.FAMILY_1); // Afrikaans
        put("ak", OrdinalityFamily.FAMILY_1); // Akan (no CLDR data available)
        put("am", OrdinalityFamily.FAMILY_1); // Amharic
        put("ar", OrdinalityFamily.FAMILY_1); // Arabic
        put("ars", OrdinalityFamily.FAMILY_1); // Najdi Arabic (no CLDR data available)
        put("as", OrdinalityFamily.FAMILY_3); // Assamese
        put("asa", OrdinalityFamily.FAMILY_1); // Asu (no CLDR data available)
        put("ast", OrdinalityFamily.FAMILY_1); // Asturian (no CLDR data available)
        put("az", OrdinalityFamily.FAMILY_5); // Azeri
        put("be", OrdinalityFamily.FAMILY_6); // Belarusian
        put("bem", OrdinalityFamily.FAMILY_1); // Bemba (no CLDR data available)
        put("bez", OrdinalityFamily.FAMILY_1); // Bena (no CLDR data available)
        put("bg", OrdinalityFamily.FAMILY_1); // Bulgarian
        put("bh", OrdinalityFamily.FAMILY_1); // Bihari (no CLDR data available)
        put("bm", OrdinalityFamily.FAMILY_1); // Bambara (no CLDR data available)
        put("bn", OrdinalityFamily.FAMILY_3); // Bangla
        put("bo", OrdinalityFamily.FAMILY_1); // Tibetan (no CLDR data available)
        put("br", OrdinalityFamily.FAMILY_1); // Breton (no CLDR data available)
        put("brx", OrdinalityFamily.FAMILY_1); // Bodo (no CLDR data available)
        put("bs", OrdinalityFamily.FAMILY_1); // Bosnian
        put("ca", OrdinalityFamily.FAMILY_7); // Catalan
        put("ce", OrdinalityFamily.FAMILY_1); // Chechen
        put("cgg", OrdinalityFamily.FAMILY_1); // Chiga (no CLDR data available)
        put("chr", OrdinalityFamily.FAMILY_1); // Cherokee (no CLDR data available)
        put("ckb", OrdinalityFamily.FAMILY_1); // Central Kurdish (no CLDR data available)
        put("cs", OrdinalityFamily.FAMILY_1); // Czech
        put("cy", OrdinalityFamily.FAMILY_8); // Welsh
        put("da", OrdinalityFamily.FAMILY_1); // Danish
        put("de", OrdinalityFamily.FAMILY_1); // German
        put("dsb", OrdinalityFamily.FAMILY_1); // Lower Sorbian
        put("dv", OrdinalityFamily.FAMILY_1); // Divehi (no CLDR data available)
        put("dz", OrdinalityFamily.FAMILY_1); // Dzongkha (no CLDR data available)
        put("ee", OrdinalityFamily.FAMILY_1); // Ewe (no CLDR data available)
        put("el", OrdinalityFamily.FAMILY_1); // Greek
        put("en", OrdinalityFamily.FAMILY_9); // English
        put("eo", OrdinalityFamily.FAMILY_1); // Esperanto (no CLDR data available)
        put("es", OrdinalityFamily.FAMILY_1); // Spanish
        put("et", OrdinalityFamily.FAMILY_1); // Estonian
        put("eu", OrdinalityFamily.FAMILY_1); // Basque
        put("fa", OrdinalityFamily.FAMILY_1); // Persian
        put("ff", OrdinalityFamily.FAMILY_1); // Fulah (no CLDR data available)
        put("fi", OrdinalityFamily.FAMILY_1); // Finnish
        put("fil", OrdinalityFamily.FAMILY_2); // Filipino
        put("fo", OrdinalityFamily.FAMILY_1); // Faroese (no CLDR data available)
        put("fr", OrdinalityFamily.FAMILY_2); // French
        put("fur", OrdinalityFamily.FAMILY_1); // Friulian (no CLDR data available)
        put("fy", OrdinalityFamily.FAMILY_1); // Western Frisian
        put("ga", OrdinalityFamily.FAMILY_2); // Irish
        put("gd", OrdinalityFamily.FAMILY_1); // Scottish Gaelic (no CLDR data available)
        put("gl", OrdinalityFamily.FAMILY_1); // Galician
        put("gsw", OrdinalityFamily.FAMILY_1); // Swiss German
        put("gu", OrdinalityFamily.FAMILY_4); // Gujarati
        put("guw", OrdinalityFamily.FAMILY_1); // Gun (no CLDR data available)
        put("gv", OrdinalityFamily.FAMILY_1); // Manx (no CLDR data available)
        put("ha", OrdinalityFamily.FAMILY_1); // Hausa (no CLDR data available)
        put("haw", OrdinalityFamily.FAMILY_1); // Hawaiian (no CLDR data available)
        put("he", OrdinalityFamily.FAMILY_1); // Hebrew
        put("hi", OrdinalityFamily.FAMILY_4); // Hindi
        put("hr", OrdinalityFamily.FAMILY_1); // Croatian
        put("hsb", OrdinalityFamily.FAMILY_1); // Upper Sorbian
        put("hu", OrdinalityFamily.FAMILY_10); // Hungarian
        put("hy", OrdinalityFamily.FAMILY_2); // Armenian
        put("id", OrdinalityFamily.FAMILY_1); // Indonesian
        put("ig", OrdinalityFamily.FAMILY_1); // Igbo (no CLDR data available)
        put("ii", OrdinalityFamily.FAMILY_1); // Sichuan Yi (no CLDR data available)
        put("is", OrdinalityFamily.FAMILY_1); // Icelandic
        put("it", OrdinalityFamily.FAMILY_11); // Italian
        put("iu", OrdinalityFamily.FAMILY_1); // Inuktitut (no CLDR data available)
        put("ja", OrdinalityFamily.FAMILY_1); // Japanese
        put("jbo", OrdinalityFamily.FAMILY_1); // Lojban (no CLDR data available)
        put("jgo", OrdinalityFamily.FAMILY_1); // Ngomba (no CLDR data available)
        put("jmc", OrdinalityFamily.FAMILY_1); // Machame (no CLDR data available)
        put("jv", OrdinalityFamily.FAMILY_1); // Javanese (no CLDR data available)
        put("jw", OrdinalityFamily.FAMILY_1); // Javanese (no CLDR data available)
        put("ka", OrdinalityFamily.FAMILY_12); // Georgian
        put("kab", OrdinalityFamily.FAMILY_1); // Kabyle (no CLDR data available)
        put("kaj", OrdinalityFamily.FAMILY_1); // Jju (no CLDR data available)
        put("kcg", OrdinalityFamily.FAMILY_1); // Tyap (no CLDR data available)
        put("kde", OrdinalityFamily.FAMILY_1); // Makonde (no CLDR data available)
        put("kea", OrdinalityFamily.FAMILY_1); // Kabuverdianu (no CLDR data available)
        put("kk", OrdinalityFamily.FAMILY_13); // Kazakh
        put("kkj", OrdinalityFamily.FAMILY_1); // Kako (no CLDR data available)
        put("kl", OrdinalityFamily.FAMILY_1); // Greenlandic (no CLDR data available)
        put("km", OrdinalityFamily.FAMILY_1); // Khmer
        put("kn", OrdinalityFamily.FAMILY_1); // Kannada
        put("ko", OrdinalityFamily.FAMILY_1); // Korean
        put("ks", OrdinalityFamily.FAMILY_1); // Kashmiri (no CLDR data available)
        put("ksb", OrdinalityFamily.FAMILY_1); // Shambala (no CLDR data available)
        put("ksh", OrdinalityFamily.FAMILY_1); // Colognian (no CLDR data available)
        put("ku", OrdinalityFamily.FAMILY_1); // Kurdish (no CLDR data available)
        put("kw", OrdinalityFamily.FAMILY_1); // Cornish (no CLDR data available)
        put("ky", OrdinalityFamily.FAMILY_1); // Kirghiz
        put("lag", OrdinalityFamily.FAMILY_1); // Langi (no CLDR data available)
        put("lb", OrdinalityFamily.FAMILY_1); // Luxembourgish (no CLDR data available)
        put("lg", OrdinalityFamily.FAMILY_1); // Ganda (no CLDR data available)
        put("lkt", OrdinalityFamily.FAMILY_1); // Lakota (no CLDR data available)
        put("ln", OrdinalityFamily.FAMILY_1); // Lingala (no CLDR data available)
        put("lo", OrdinalityFamily.FAMILY_2); // Lao
        put("lt", OrdinalityFamily.FAMILY_1); // Lithuanian
        put("lv", OrdinalityFamily.FAMILY_1); // Latvian
        put("mas", OrdinalityFamily.FAMILY_1); // Masai (no CLDR data available)
        put("mg", OrdinalityFamily.FAMILY_1); // Malagasy (no CLDR data available)
        put("mgo", OrdinalityFamily.FAMILY_1); // Metaʼ (no CLDR data available)
        put("mk", OrdinalityFamily.FAMILY_14); // Macedonian
        put("ml", OrdinalityFamily.FAMILY_1); // Malayalam
        put("mn", OrdinalityFamily.FAMILY_1); // Mongolian
        put("mo", OrdinalityFamily.FAMILY_2); // Moldovan
        put("mr", OrdinalityFamily.FAMILY_15); // Marathi
        put("ms", OrdinalityFamily.FAMILY_2); // Malay
        put("mt", OrdinalityFamily.FAMILY_1); // Maltese (no CLDR data available)
        put("my", OrdinalityFamily.FAMILY_1); // Burmese
        put("nah", OrdinalityFamily.FAMILY_1); // Nahuatl (no CLDR data available)
        put("naq", OrdinalityFamily.FAMILY_1); // Nama (no CLDR data available)
        put("nb", OrdinalityFamily.FAMILY_1); // Norwegian Bokmål
        put("nd", OrdinalityFamily.FAMILY_1); // North Ndebele (no CLDR data available)
        put("ne", OrdinalityFamily.FAMILY_16); // Nepali
        put("nl", OrdinalityFamily.FAMILY_1); // Dutch
        put("nn", OrdinalityFamily.FAMILY_1); // Norwegian Nynorsk (no CLDR data available)
        put("nnh", OrdinalityFamily.FAMILY_1); // Ngiemboon (no CLDR data available)
        put("no", OrdinalityFamily.FAMILY_1); // Norwegian (no CLDR data available)
        put("nqo", OrdinalityFamily.FAMILY_1); // N’Ko (no CLDR data available)
        put("nr", OrdinalityFamily.FAMILY_1); // South Ndebele (no CLDR data available)
        put("nso", OrdinalityFamily.FAMILY_1); // Northern Sotho (no CLDR data available)
        put("ny", OrdinalityFamily.FAMILY_1); // Nyanja (no CLDR data available)
        put("nyn", OrdinalityFamily.FAMILY_1); // Nyankole (no CLDR data available)
        put("om", OrdinalityFamily.FAMILY_1); // Oromo (no CLDR data available)
        put("or", OrdinalityFamily.FAMILY_1); // Odia (no CLDR data available)
        put("os", OrdinalityFamily.FAMILY_1); // Ossetian (no CLDR data available)
        put("pa", OrdinalityFamily.FAMILY_1); // Punjabi
        put("pap", OrdinalityFamily.FAMILY_1); // Papiamento (no CLDR data available)
        put("pl", OrdinalityFamily.FAMILY_1); // Polish
        put("prg", OrdinalityFamily.FAMILY_1); // Prussian
        put("ps", OrdinalityFamily.FAMILY_1); // Pushto (no CLDR data available)
        put("pt", OrdinalityFamily.FAMILY_1); // Portuguese
        put("rm", OrdinalityFamily.FAMILY_1); // Romansh (no CLDR data available)
        put("ro", OrdinalityFamily.FAMILY_2); // Romanian
        put("rof", OrdinalityFamily.FAMILY_1); // Rombo (no CLDR data available)
        put("root", OrdinalityFamily.FAMILY_1); // Root
        put("ru", OrdinalityFamily.FAMILY_1); // Russian
        put("rwk", OrdinalityFamily.FAMILY_1); // Rwa (no CLDR data available)
        put("sah", OrdinalityFamily.FAMILY_1); // Sakha (no CLDR data available)
        put("saq", OrdinalityFamily.FAMILY_1); // Samburu (no CLDR data available)
        put("sdh", OrdinalityFamily.FAMILY_1); // Southern Kurdish (no CLDR data available)
        put("se", OrdinalityFamily.FAMILY_1); // Northern Sami (no CLDR data available)
        put("seh", OrdinalityFamily.FAMILY_1); // Sena (no CLDR data available)
        put("ses", OrdinalityFamily.FAMILY_1); // Koyraboro Senni (no CLDR data available)
        put("sg", OrdinalityFamily.FAMILY_1); // Sango (no CLDR data available)
        put("sh", OrdinalityFamily.FAMILY_1); // Serbo-Croatian
        put("shi", OrdinalityFamily.FAMILY_1); // Tachelhit (no CLDR data available)
        put("si", OrdinalityFamily.FAMILY_1); // Sinhalese
        put("sk", OrdinalityFamily.FAMILY_1); // Slovak
        put("sl", OrdinalityFamily.FAMILY_1); // Slovenian
        put("sma", OrdinalityFamily.FAMILY_1); // Southern Sami (no CLDR data available)
        put("smi", OrdinalityFamily.FAMILY_1); // Sami (no CLDR data available)
        put("smj", OrdinalityFamily.FAMILY_1); // Lule Sami (no CLDR data available)
        put("smn", OrdinalityFamily.FAMILY_1); // Inari Sami (no CLDR data available)
        put("sms", OrdinalityFamily.FAMILY_1); // Skolt Sami (no CLDR data available)
        put("sn", OrdinalityFamily.FAMILY_1); // Shona (no CLDR data available)
        put("so", OrdinalityFamily.FAMILY_1); // Somali (no CLDR data available)
        put("sq", OrdinalityFamily.FAMILY_17); // Albanian
        put("sr", OrdinalityFamily.FAMILY_1); // Serbian
        put("ss", OrdinalityFamily.FAMILY_1); // Swati (no CLDR data available)
        put("ssy", OrdinalityFamily.FAMILY_1); // Saho (no CLDR data available)
        put("st", OrdinalityFamily.FAMILY_1); // Southern Sotho (no CLDR data available)
        put("sv", OrdinalityFamily.FAMILY_18); // Swedish
        put("sw", OrdinalityFamily.FAMILY_1); // Swahili
        put("syr", OrdinalityFamily.FAMILY_1); // Syriac (no CLDR data available)
        put("ta", OrdinalityFamily.FAMILY_1); // Tamil
        put("te", OrdinalityFamily.FAMILY_1); // Telugu
        put("teo", OrdinalityFamily.FAMILY_1); // Teso (no CLDR data available)
        put("th", OrdinalityFamily.FAMILY_1); // Thai
        put("ti", OrdinalityFamily.FAMILY_1); // Tigrinya (no CLDR data available)
        put("tig", OrdinalityFamily.FAMILY_1); // Tigre (no CLDR data available)
        put("tk", OrdinalityFamily.FAMILY_1); // Turkmen (no CLDR data available)
        put("tl", OrdinalityFamily.FAMILY_2); // Tagalog
        put("tn", OrdinalityFamily.FAMILY_1); // Tswana (no CLDR data available)
        put("to", OrdinalityFamily.FAMILY_1); // Tongan (no CLDR data available)
        put("tr", OrdinalityFamily.FAMILY_1); // Turkish
        put("ts", OrdinalityFamily.FAMILY_1); // Tsonga (no CLDR data available)
        put("tzm", OrdinalityFamily.FAMILY_1); // Central Atlas Tamazight (no CLDR data available)
        put("ug", OrdinalityFamily.FAMILY_1); // Uighur (no CLDR data available)
        put("uk", OrdinalityFamily.FAMILY_19); // Ukrainian
        put("ur", OrdinalityFamily.FAMILY_1); // Urdu
        put("uz", OrdinalityFamily.FAMILY_1); // Uzbek
        put("ve", OrdinalityFamily.FAMILY_1); // Venda (no CLDR data available)
        put("vi", OrdinalityFamily.FAMILY_2); // Vietnamese
        put("vo", OrdinalityFamily.FAMILY_1); // Volapük (no CLDR data available)
        put("vun", OrdinalityFamily.FAMILY_1); // Vunjo (no CLDR data available)
        put("wa", OrdinalityFamily.FAMILY_1); // Walloon (no CLDR data available)
        put("wae", OrdinalityFamily.FAMILY_1); // Walser (no CLDR data available)
        put("wo", OrdinalityFamily.FAMILY_1); // Wolof (no CLDR data available)
        put("xh", OrdinalityFamily.FAMILY_1); // Xhosa (no CLDR data available)
        put("xog", OrdinalityFamily.FAMILY_1); // Soga (no CLDR data available)
        put("yi", OrdinalityFamily.FAMILY_1); // Yiddish (no CLDR data available)
        put("yo", OrdinalityFamily.FAMILY_1); // Yoruba (no CLDR data available)
        put("yue", OrdinalityFamily.FAMILY_1); // Cantonese
        put("zh", OrdinalityFamily.FAMILY_1); // Mandarin Chinese
        put("zu", OrdinalityFamily.FAMILY_1); // Zulu
      }});

      // Language codes are in English - force collation for sorting
      SortedSet<String> supportedLanguageCodes = new TreeSet<>(Collator.getInstance(Locale.ENGLISH));
      supportedLanguageCodes.addAll(ORDINALITY_FAMILIES_BY_LANGUAGE_CODE.keySet());

      SUPPORTED_LANGUAGE_CODES = Collections.unmodifiableSortedSet(supportedLanguageCodes);
    }

    /**
     * Gets the ordinality-determining function for this ordinality family.
     * <p>
     * The function takes a numeric value as input and returns the appropriate ordinal form.
     * <p>
     * The function's input must not be null and its output is guaranteed non-null.
     *
     * @return the ordinality-determining function for this ordinality family, not null
     */
    @Nonnull
    public Function<BigDecimal, Ordinality> getOrdinalityFunction() {
      return ordinalityFunction;
    }

    /**
     * Gets the ordinalities supported by this ordinality family.
     * <p>
     * There will always be at least one value - {@link Ordinality#OTHER} - in the set.
     * <p>
     * The set's values are sorted by the natural ordering of the {@link Ordinality} enumeration.
     *
     * @return the ordinalities supported by this ordinality family, not null
     */
    @Nonnull
    SortedSet<Ordinality> getSupportedOrdinalities() {
      return supportedOrdinalities;
    }

    /**
     * Gets a mapping of ordinalities to example integer values for this ordinality family.
     * <p>
     * The map may be empty.
     * <p>
     * The map's keys are sorted by the natural ordering of the {@link Ordinality} enumeration.
     *
     * @return a mapping of ordinalities to example integer values, not null
     */
    @Nonnull
    SortedMap<Ordinality, Range<Integer>> getExampleIntegerValuesByOrdinality() {
      return exampleIntegerValuesByOrdinality;
    }

    /**
     * Gets the ISO 639 language codes for which ordinality operations are supported.
     * <p>
     * The set's values are ISO 639 codes and therefore sorted using English collation.
     *
     * @return the ISO 639 language codes for which ordinality operations are supported, not null
     */
    @Nonnull
    static SortedSet<String> getSupportedLanguageCodes() {
      return SUPPORTED_LANGUAGE_CODES;
    }

    /**
     * Gets an appropriate plural ordinality family for the given locale.
     *
     * @param locale the locale to check, not null
     * @return the appropriate plural ordinality family (if one exists) for the given locale, not null
     */
    @Nonnull
    static Optional<OrdinalityFamily> ordinalityFamilyForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      String language = LocaleUtils.normalizedLanguage(locale).orElse(null);
      String country = locale.getCountry();

      OrdinalityFamily ordinalityFamily = null;

      if (language != null && country != null)
        ordinalityFamily = ORDINALITY_FAMILIES_BY_LANGUAGE_CODE.get(format("%s-%s", language, country));

      if (ordinalityFamily != null)
        return Optional.of(ordinalityFamily);

      if (language != null)
        ordinalityFamily = ORDINALITY_FAMILIES_BY_LANGUAGE_CODE.get(language);

      return Optional.ofNullable(ordinalityFamily);
    }
  }
}