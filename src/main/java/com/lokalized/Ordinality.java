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

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Language plural ordinality forms.
 * <p>
 * For example, English has four: {@code 1st, 2nd, 3rd, 4th}, while Swedish has two: {@code 1:a, 3:e}.
 * <p>
 * See the <a href="http://cldr.unicode.org/index/cldr-spec/plural-rules">Unicode Common Locale Data Repository</a>
 * and its <a href="https://www.unicode.org/cldr/charts/48/supplemental/language_plural_rules.html">Language Plural Rules</a> for details.
 * <p>
 * CLDR category names are mnemonics whose membership is locale-specific; they do not imply cardinal singular or plural
 * meaning. For English ordinals, values ending in 1, 2, or 3 normally select {@link #ONE}, {@link #TWO}, or
 * {@link #FEW}, except for values ending in 11, 12, or 13; remaining values select {@link #OTHER}. French ordinals use
 * {@link #ONE} for 1 and {@link #OTHER} for other values. Applications should always use the generated locale rules
 * rather than inferring ordinal categories from their names.
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

  @NonNull
  static final Map<@NonNull String, @NonNull Ordinality> ORDINALITIES_BY_NAME;

  static {
    ORDINALITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Ordinality.values()).collect(Collectors.toMap(ordinality -> ordinality.name(), ordinality -> ordinality)));
  }

  /**
   * Gets an appropriate plural ordinality for the given number and locale.
   * <p>
   * Negative numbers are evaluated using their absolute value.
   * Supported {@link Number} implementations and their conversion semantics are documented by
   * {@link PluralOperands#forNumber(Number)}.
   * This convenience method uses {@link TranslationRuntimeLimits#defaults()}. To apply different limits, construct
   * {@link PluralOperands} with {@link PluralOperands.Builder#runtimeLimits(TranslationRuntimeLimits)} and call
   * {@link #forOperands(PluralOperands, Locale)}.
   * <p>
   * See <a href="https://www.unicode.org/cldr/charts/48/supplemental/language_plural_rules.html">CLDR 48 Language Plural Rules</a>
   * for a cheat sheet.
   *
   * @param number the number that drives pluralization, not null
   * @param locale the locale that drives pluralization, not null
   * @return an appropriate plural ordinality, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   * @throws IllegalArgumentException if the locale is malformed, the number implementation is unsupported, the
   *                                  number is non-finite, or the number exceeds the safety limits documented by
   *                                  {@link PluralOperands}
   */
  @NonNull
  public static Ordinality forNumber(@NonNull Number number, @NonNull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    return forOperands(PluralOperands.forNumber(number).build(), locale);
  }

  /**
   * Gets an appropriate plural ordinality for the given CLDR plural operands and locale.
   * <p>
   * Most applications should use {@link #forNumber(Number, Locale)}. Use this overload when the displayed number has
   * details that are not fully represented by the Java {@link Number}, such as a compact-decimal exponent.
   * <p>
   * See <a href="https://www.unicode.org/cldr/charts/48/supplemental/language_plural_rules.html">CLDR 48 Language Plural Rules</a>
   * for a cheat sheet.
   *
   * @param operands the CLDR plural operands that drive pluralization, not null
   * @param locale   the locale that drives pluralization, not null
   * @return an appropriate plural ordinality, not null
   * @throws UnsupportedLocaleException if the locale is not supported
   * @throws IllegalArgumentException if the locale is malformed
   * @since 3.0.0
   */
  @NonNull
  public static Ordinality forOperands(@NonNull PluralOperands operands, @NonNull Locale locale) {
    requireNonNull(operands);
    requireNonNull(locale);

    return CldrPluralRules.ordinalityForOperands(operands, locale);
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
   * @throws IllegalArgumentException if the locale is malformed
   */
  @NonNull
  public static SortedSet<@NonNull Ordinality> supportedOrdinalitiesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);
    return CldrPluralRules.supportedOrdinalitiesForLocale(locale);
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
   * @throws IllegalArgumentException if the locale is malformed
   */
  @NonNull
  public static SortedMap<@NonNull Ordinality, @NonNull Range<@NonNull Integer>> exampleIntegerValuesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);
    return CldrPluralRules.ordinalityIntegerExamplesForLocale(locale);
  }

  /**
   * Gets the BCP 47 locale tags represented directly in the generated CLDR plural-rule data and supported for
   * ordinality operations.
   * <p>
   * This is not an exhaustive list of concrete locale tags accepted by ordinality operations. A locale with
   * additional region or script subtags may be supported through fallback to a less-specific rule tag. Use
   * {@link #supportedOrdinalitiesForLocale(Locale)} to check a concrete locale.
   * <p>
   * The set's values are sorted by natural string ordering.
   *
   * @return the directly represented BCP 47 locale tags supported for ordinality operations, not null
   * @since 3.0.0
   */
  @NonNull
  public static SortedSet<@NonNull String> getSupportedLocaleTags() {
    return CldrPluralRules.ordinalitySupportedLocales();
  }

  /**
   * Gets the mapping of ordinality names to values.
   *
   * @return the mapping of ordinality names to values, not null
   */
  @NonNull
  static Map<@NonNull String, @NonNull Ordinality> getOrdinalitiesByName() {
    return ORDINALITIES_BY_NAME;
  }
}
