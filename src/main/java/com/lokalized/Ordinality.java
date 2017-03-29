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
  static final Map<String, Ordinality> ORDINALITIES_BY_NAME;

  static {
    ORDINALITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Ordinality.values()).collect(Collectors.toMap(ordinality -> ordinality.name(), ordinality -> ordinality)));
  }

  /**
   * Gets the mapping of ordinality names to values.
   *
   * @return the mapping of ordinality names to values, not null
   */
  @Nonnull
  public static Map<String, Ordinality> getOrdinalitiesByName() {
    return ORDINALITIES_BY_NAME;
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

    Optional<Function<Number, Ordinality>> ordinalityFunction = OrdinalityFamily.ordinalityFunctionForLocale(locale);

    if (!ordinalityFunction.isPresent())
      throw new UnsupportedLocaleException(locale);

    return ordinalityFunction.get().apply(number);
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
    NO_ORDINALS, ENGLISH;

    @Nonnull
    static final Map<OrdinalityFamily, Function<Number, Ordinality>> ORDINALITY_FUNCTIONS_BY_ORDINALITY_FAMILY;

    @Nonnull
    static final Map<String, OrdinalityFamily> ORDINALITY_FAMILIES_BY_LANGUAGE_TAG;

    static {
      ORDINALITY_FUNCTIONS_BY_ORDINALITY_FAMILY = Collections.unmodifiableMap(new HashMap<OrdinalityFamily, Function<Number, Ordinality>>() {{
        // TODO: add the rest
        put(OrdinalityFamily.NO_ORDINALS, (number) -> {
          return OTHER;
        });
        put(OrdinalityFamily.ENGLISH, (number) -> {
          if (number != null) {
            double value = number.doubleValue();

            if (value % 10 == 1 && value % 100 != 11)
              return ONE;
            if (value % 10 == 2 && value % 100 != 12)
              return TWO;
            if (value % 10 == 3 && value % 100 != 13)
              return FEW;
          }

          return OTHER;
        });
      }});

      ORDINALITY_FAMILIES_BY_LANGUAGE_TAG = Collections.unmodifiableMap(new HashMap<String, OrdinalityFamily>() {{
        // TODO: add the rest
        put("en", OrdinalityFamily.ENGLISH); // English
        put("es", OrdinalityFamily.NO_ORDINALS); // English
      }});
    }

    /**
     * Gets an appropriate plural ordinality family for the given locale.
     *
     * @param locale the locale to check, not null
     * @return the appropriate plural ordinality family (if one exists) for the given locale, not null
     */
    @Nonnull
    public static Optional<OrdinalityFamily> ordinalityFamilyForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      String language = locale.getLanguage();
      String country = locale.getCountry();

      OrdinalityFamily ordinalityFamily = null;

      if (language != null && country != null)
        ordinalityFamily = ORDINALITY_FAMILIES_BY_LANGUAGE_TAG.get(format("%s-%s", language, country));

      if (ordinalityFamily != null)
        return Optional.of(ordinalityFamily);

      if (language != null)
        ordinalityFamily = ORDINALITY_FAMILIES_BY_LANGUAGE_TAG.get(language);

      return Optional.ofNullable(ordinalityFamily);
    }

    /**
     * Gets an appropriate plural ordinality determination function for the given locale.
     *
     * @param locale the locale to check, not null
     * @return the appropriate plural ordinality determination function (if one exists) for the given locale, not null
     */
    @Nonnull
    public static Optional<Function<Number, Ordinality>> ordinalityFunctionForLocale(@Nonnull Locale locale) {
      requireNonNull(locale);

      Optional<OrdinalityFamily> ordinalityFamily = ordinalityFamilyForLocale(locale);

      if (!ordinalityFamily.isPresent())
        return Optional.empty();

      return Optional.ofNullable(ORDINALITY_FUNCTIONS_BY_ORDINALITY_FAMILY.get(ordinalityFamily.get()));
    }
  }
}