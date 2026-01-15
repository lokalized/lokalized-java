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

import org.jspecify.annotations.NonNull;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Collection of utility methods for working with localized strings.
 * <p>
 * This is for internal use only!
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
final class LocalizedStringUtils {
  @NonNull
  private static final String CARDINALITY_NAME_PREFIX;
  @NonNull
  private static final String ORDINALITY_NAME_PREFIX;
  @NonNull
  private static final String GENDER_NAME_PREFIX;
  @NonNull
  private static final String FORMALITY_NAME_PREFIX;
  @NonNull
  private static final String CLUSIVITY_NAME_PREFIX;
  @NonNull
  private static final String ANIMACY_NAME_PREFIX;
  @NonNull
  private static final String PHONETIC_NAME_PREFIX;

  static {
    CARDINALITY_NAME_PREFIX = "CARDINALITY_";
    ORDINALITY_NAME_PREFIX = "ORDINALITY_";
    GENDER_NAME_PREFIX = "GENDER_";
    FORMALITY_NAME_PREFIX = "FORMALITY_";
    CLUSIVITY_NAME_PREFIX = "CLUSIVITY_";
    ANIMACY_NAME_PREFIX = "ANIMACY_";
    PHONETIC_NAME_PREFIX = "PHONETIC_";
  }

  private LocalizedStringUtils() {
    // Non-instantiable
  }

  /**
   * Massages Cardinality name ({@code ONE}) to match localized strings file format {@code "CARDINALITY_ONE"}.
   *
   * @param cardinalityName the cardinality name to massage, not null
   * @return the localized strings file representation of a cardinality name, not null
   */
  @NonNull
  static String localizedStringNameForCardinalityName(@NonNull String cardinalityName) {
    requireNonNull(cardinalityName);
    return format("%s%s", CARDINALITY_NAME_PREFIX, cardinalityName);
  }

  /**
   * Massages localized strings file format {@code "CARDINALITY_ONE"} to match cardinality name ({@code ONE}).
   *
   * @param localizedStringName the localized strings cardinality name to massage, not null
   * @return the cardinality name of the localized strings file representation, not null
   * @throws IllegalArgumentException if the localized strings name is malformed
   */
  @NonNull
  static String cardinalityNameForLocalizedStringName(@NonNull String localizedStringName) {
    requireNonNull(localizedStringName);

    if (!localizedStringName.startsWith(CARDINALITY_NAME_PREFIX))
      throw new IllegalArgumentException(format("Cardinality value '%s' does not start with prefix '%s'",
          localizedStringName, CARDINALITY_NAME_PREFIX));

    return localizedStringName.substring(CARDINALITY_NAME_PREFIX.length());
  }

  /**
   * Massages Ordinality name ({@code ONE}) to match localized strings file format {@code "ORDINALITY_ONE"}.
   *
   * @param ordinalityName the ordinality name to massage, not null
   * @return the localized strings file representation of an ordinality name, not null
   */
  @NonNull
  static String localizedStringNameForOrdinalityName(@NonNull String ordinalityName) {
    requireNonNull(ordinalityName);
    return format("%s%s", ORDINALITY_NAME_PREFIX, ordinalityName);
  }

  /**
   * Massages localized strings file format {@code "ORDINALITY_ONE"} to match ordinality name ({@code ONE}).
   *
   * @param localizedStringName the localized strings ordinality name to massage, not null
   * @return the ordinality name of the localized strings file representation, not null
   * @throws IllegalArgumentException if the localized strings name is malformed
   */
  @NonNull
  static String ordinalityNameForLocalizedStringName(@NonNull String localizedStringName) {
    requireNonNull(localizedStringName);

    if (!localizedStringName.startsWith(ORDINALITY_NAME_PREFIX))
      throw new IllegalArgumentException(format("Ordinality value '%s' does not start with prefix '%s'",
          localizedStringName, ORDINALITY_NAME_PREFIX));

    return localizedStringName.substring(ORDINALITY_NAME_PREFIX.length());
  }

  /**
   * Massages Gender name ({@code MASCULINE}) to match localized strings file format {@code "GENDER_MASCULINE"}.
   *
   * @param genderName the gender name to massage, not null
   * @return the localized strings file representation of a gender name, not null
   */
  @NonNull
  static String localizedStringNameForGenderName(@NonNull String genderName) {
    requireNonNull(genderName);
    return format("%s%s", GENDER_NAME_PREFIX, genderName);
  }

  /**
   * Massages localized strings file format {@code "GENDER_MASCULINE"} to match gender name ({@code MASCULINE}).
   *
   * @param localizedStringName the localized strings gender name to massage, not null
   * @return the gender name of the localized strings file representation, not null
   * @throws IllegalArgumentException if the localized strings name is malformed
   */
  @NonNull
  static String genderNameForLocalizedStringName(@NonNull String localizedStringName) {
    requireNonNull(localizedStringName);

    if (!localizedStringName.startsWith(GENDER_NAME_PREFIX))
      throw new IllegalArgumentException(format("Gender value '%s' does not start with prefix '%s'",
          localizedStringName, GENDER_NAME_PREFIX));

    return localizedStringName.substring(GENDER_NAME_PREFIX.length());
  }

  /**
   * Massages Formality name ({@code FORMAL}) to match localized strings file format {@code "FORMALITY_FORMAL"}.
   *
   * @param formalityName the formality name to massage, not null
   * @return the localized strings file representation of a formality name, not null
   */
  @NonNull
  static String localizedStringNameForFormalityName(@NonNull String formalityName) {
    requireNonNull(formalityName);
    return format("%s%s", FORMALITY_NAME_PREFIX, formalityName);
  }

  /**
   * Massages localized strings file format {@code "FORMALITY_FORMAL"} to match formality name ({@code FORMAL}).
   *
   * @param localizedStringName the localized strings formality name to massage, not null
   * @return the formality name of the localized strings file representation, not null
   * @throws IllegalArgumentException if the localized strings name is malformed
   */
  @NonNull
  static String formalityNameForLocalizedStringName(@NonNull String localizedStringName) {
    requireNonNull(localizedStringName);

    if (!localizedStringName.startsWith(FORMALITY_NAME_PREFIX))
      throw new IllegalArgumentException(format("Formality value '%s' does not start with prefix '%s'",
          localizedStringName, FORMALITY_NAME_PREFIX));

    return localizedStringName.substring(FORMALITY_NAME_PREFIX.length());
  }

  /**
   * Massages Clusivity name ({@code INCLUSIVE}) to match localized strings file format {@code "CLUSIVITY_INCLUSIVE"}.
   *
   * @param clusivityName the clusivity name to massage, not null
   * @return the localized strings file representation of a clusivity name, not null
   */
  @NonNull
  static String localizedStringNameForClusivityName(@NonNull String clusivityName) {
    requireNonNull(clusivityName);
    return format("%s%s", CLUSIVITY_NAME_PREFIX, clusivityName);
  }

  /**
   * Massages localized strings file format {@code "CLUSIVITY_INCLUSIVE"} to match clusivity name ({@code INCLUSIVE}).
   *
   * @param localizedStringName the localized strings clusivity name to massage, not null
   * @return the clusivity name of the localized strings file representation, not null
   * @throws IllegalArgumentException if the localized strings name is malformed
   */
  @NonNull
  static String clusivityNameForLocalizedStringName(@NonNull String localizedStringName) {
    requireNonNull(localizedStringName);

    if (!localizedStringName.startsWith(CLUSIVITY_NAME_PREFIX))
      throw new IllegalArgumentException(format("Clusivity value '%s' does not start with prefix '%s'",
          localizedStringName, CLUSIVITY_NAME_PREFIX));

    return localizedStringName.substring(CLUSIVITY_NAME_PREFIX.length());
  }

  /**
   * Massages Animacy name ({@code ANIMATE}) to match localized strings file format {@code "ANIMACY_ANIMATE"}.
   *
   * @param animacyName the animacy name to massage, not null
   * @return the localized strings file representation of an animacy name, not null
   */
  @NonNull
  static String localizedStringNameForAnimacyName(@NonNull String animacyName) {
    requireNonNull(animacyName);
    return format("%s%s", ANIMACY_NAME_PREFIX, animacyName);
  }

  /**
   * Massages localized strings file format {@code "ANIMACY_ANIMATE"} to match animacy name ({@code ANIMATE}).
   *
   * @param localizedStringName the localized strings animacy name to massage, not null
   * @return the animacy name of the localized strings file representation, not null
   * @throws IllegalArgumentException if the localized strings name is malformed
   */
  @NonNull
  static String animacyNameForLocalizedStringName(@NonNull String localizedStringName) {
    requireNonNull(localizedStringName);

    if (!localizedStringName.startsWith(ANIMACY_NAME_PREFIX))
      throw new IllegalArgumentException(format("Animacy value '%s' does not start with prefix '%s'",
          localizedStringName, ANIMACY_NAME_PREFIX));

    return localizedStringName.substring(ANIMACY_NAME_PREFIX.length());
  }

  /**
   * Massages Phonetic name ({@code VOWEL}) to match localized strings file format {@code "PHONETIC_VOWEL"}.
   *
   * @param phoneticName the phonetic name to massage, not null
   * @return the localized strings file representation of a phonetic name, not null
   */
  @NonNull
  static String localizedStringNameForPhoneticName(@NonNull String phoneticName) {
    requireNonNull(phoneticName);
    return format("%s%s", PHONETIC_NAME_PREFIX, phoneticName);
  }

  /**
   * Massages localized strings file format {@code "PHONETIC_VOWEL"} to match phonetic name ({@code VOWEL}).
   *
   * @param localizedStringName the localized strings phonetic name to massage, not null
   * @return the phonetic name of the localized strings file representation, not null
   * @throws IllegalArgumentException if the localized strings name is malformed
   */
  @NonNull
  static String phoneticNameForLocalizedStringName(@NonNull String localizedStringName) {
    requireNonNull(localizedStringName);

    if (!localizedStringName.startsWith(PHONETIC_NAME_PREFIX))
      throw new IllegalArgumentException(format("Phonetic value '%s' does not start with prefix '%s'",
          localizedStringName, PHONETIC_NAME_PREFIX));

    return localizedStringName.substring(PHONETIC_NAME_PREFIX.length());
  }
}
