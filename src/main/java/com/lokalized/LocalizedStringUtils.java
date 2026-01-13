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

  static {
    CARDINALITY_NAME_PREFIX = "CARDINALITY_";
    ORDINALITY_NAME_PREFIX = "ORDINALITY_";
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
}