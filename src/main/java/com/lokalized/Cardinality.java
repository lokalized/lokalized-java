/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2026 Revetware LLC.
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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Collectors;

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

  @NonNull
  static final Map<@NonNull String, @NonNull Cardinality> CARDINALITIES_BY_NAME;

  static {
    CARDINALITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Cardinality.values()).collect(Collectors.toMap(cardinality -> cardinality.name(), cardinality -> cardinality)));
  }

  /**
   * Gets an appropriate plural cardinality for the given number and locale.
   * <p>
   * Negative numbers are evaluated using their absolute value.
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
  @NonNull
  public static Cardinality forNumber(@NonNull Number number, @NonNull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    return forNumber(number, null, locale);
  }

  /**
   * Gets an appropriate plural cardinality for the given number, visible decimal places, and locale.
   * <p>
   * Negative numbers are evaluated using their absolute value.
   * <p>
   * If {@code visibleDecimalPlaces} is null, then the decimal places of {@code number} will be computed and used.
   * Note that if trailing zeroes are important, e.g. {@code 1.00} instead of {@code 1}, you must either specify a {@link BigDecimal} with appropriate
   * scale or supply a non-null {@code visibleDecimalPlaces} value.
   * Reducing the number's scale does not round implicitly; callers must supply an already-rounded value.
   * <p>
   * See the <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
   * for further details.
   *
   * @param number               the number that drives pluralization, not null
   * @param visibleDecimalPlaces the number of decimal places that will ultimately be displayed, may be null
   * @param locale               the locale that drives pluralization, not null
   * @return an appropriate plural cardinality, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   * @throws ArithmeticException if the requested visible decimal places would require rounding
   */
  @NonNull
  public static Cardinality forNumber(@NonNull Number number, @Nullable Integer visibleDecimalPlaces, @NonNull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    return forOperands(PluralOperands.forNumber(number).visibleDecimalPlaces(visibleDecimalPlaces).build(), locale);
  }

  /**
   * Gets an appropriate plural cardinality for the given CLDR plural operands and locale.
   * <p>
   * Most applications should use {@link #forNumber(Number, Locale)}. Use this overload when the displayed number has
   * details that are not fully represented by the Java {@link Number}, such as a compact-decimal exponent.
   * <p>
   * See the <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
   * for further details.
   *
   * @param operands the CLDR plural operands that drive pluralization, not null
   * @param locale   the locale that drives pluralization, not null
   * @return an appropriate plural cardinality, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   */
  @NonNull
  public static Cardinality forOperands(@NonNull PluralOperands operands, @NonNull Locale locale) {
    requireNonNull(operands);
    requireNonNull(locale);

    return CldrPluralRules.cardinalityForOperands(operands, locale);
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
  @NonNull
  public static Cardinality forRange(@NonNull Cardinality start, @NonNull Cardinality end, @NonNull Locale locale) {
    requireNonNull(start);
    requireNonNull(end);
    requireNonNull(locale);

    return CldrPluralRules.cardinalityForRange(start, end, locale);
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
  @NonNull
  public static SortedSet<@NonNull Cardinality> supportedCardinalitiesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);
    return CldrPluralRules.supportedCardinalitiesForLocale(locale);
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
  @NonNull
  public static SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull Integer>> exampleIntegerValuesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);
    return CldrPluralRules.cardinalityIntegerExamplesForLocale(locale);
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
  @NonNull
  public static SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull BigDecimal>> exampleDecimalValuesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);
    return CldrPluralRules.cardinalityDecimalExamplesForLocale(locale);
  }

  /**
   * Gets the BCP 47 locale tags for which cardinality operations are supported.
   * <p>
   * The set's values are sorted by natural string ordering.
   *
   * @return the BCP 47 locale tags for which cardinality operations are supported, not null
   */
  @NonNull
  public static SortedSet<@NonNull String> getSupportedLocaleTags() {
    return CldrPluralRules.cardinalitySupportedLocales();
  }

  /**
   * Gets the mapping of cardinality names to values.
   *
   * @return the mapping of cardinality names to values, not null
   */
  @NonNull
  static Map<@NonNull String, @NonNull Cardinality> getCardinalitiesByName() {
    return CARDINALITIES_BY_NAME;
  }
}
