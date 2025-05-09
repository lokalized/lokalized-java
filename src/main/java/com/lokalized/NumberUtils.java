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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.math.BigInteger;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Collection of utility methods for working with numbers.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class NumberUtils {
  private NumberUtils() {
    // Non-instantiable
  }

  /**
   * Determines the number of decimal places in the given number.
   * <p>
   * If the number is a {@link BigDecimal}, then the scale (which may include trailing zeroes) is returned.
   * <p>
   * Otherwise, the number of decimal places without trailing zeroes is returned.
   * <p>
   * For example:
   * <p>
   * <ul>
   * <li>{@code 2} has {@code 0} decimal places</li>
   * <li>{@code 2.1} has {@code 1} decimal place</li>
   * <li>{@code 2.150} has {@code 2} decimal places (if non-{@code BigDecimal}) and 3 decimal places (if {@code BigDecimal})</li>
   * </ul>
   *
   * @param number the number for which we are counting decimal places, not null
   * @return the number of decimal places, not null
   */
  @Nonnull
  static Integer numberOfDecimalPlaces(@Nonnull Number number) {
    requireNonNull(number);

    if (number instanceof BigDecimal) {
      // Can determine trailing zeroes in this case
      BigDecimal numberAsBigDecimal = (BigDecimal) number;
      return normalizedScale(numberAsBigDecimal);
    }

    // Cannot determine trailing zeroes in this case
    BigDecimal numberAsBigDecimal = toBigDecimal(number);
    return Math.max(0, normalizedScale(numberAsBigDecimal.stripTrailingZeros()));
  }

  /**
   * Determines the integer component of the given number.
   * <p>
   * For example:
   * <p>
   * <ul>
   * <li>{@code 2} has an integer component of {@code 2}</li>
   * <li>{@code 1.234} has an integer component of {@code 1}</li>
   * <li>{@code -1.5} has an integer component of {@code -1}</li>
   * <li>{@code .5} has an integer component of {@code 0}</li>
   * </ul>
   *
   * @param number the number for which we are determining the integer component, not null
   * @return the integer component, not null
   */
  @Nonnull
  static BigInteger integerComponent(@Nonnull Number number) {
    requireNonNull(number);

    if (number instanceof BigDecimal) {
      BigDecimal numberAsBigDecimal = (BigDecimal) number;
      return numberAsBigDecimal.toBigInteger();
    }

    return toBigDecimal(number).toBigInteger();
  }

  /**
   * Determines the fractional component of the given number.
   * <p>
   * If the number is a {@link BigDecimal}, then fractional value according to its scale (which may include trailing
   * zeroes) is returned.
   * <p>
   * Otherwise, the fractional value without trailing zeroes is returned.
   * <p>
   * For example:
   * <p>
   * <ul>
   * <li>{@code 2} has a fractional component of {@code 0}</li>
   * <li>{@code 1.234} has a fractional component of {@code 234}</li>
   * <li>{@code -1.5} has a fractional component of {@code 5}</li>
   * <li>{@code .5} has a fractional component of {@code 5}</li>
   * <li>{@code 2.150} has a fractional component of {@code 15} (if non-{@code BigDecimal}) and {@code 150} (if {@code BigDecimal})</li>
   * </ul>
   *
   * @param number the number for which we are determining the fractional component, not null
   * @return the fractional component, not null
   */
  @Nonnull
  static BigInteger fractionalComponent(@Nonnull Number number) {
    requireNonNull(number);

    BigDecimal numberAsBigDecimal = null;

    if (number instanceof BigDecimal)
      numberAsBigDecimal = (BigDecimal) number;
    else
      numberAsBigDecimal = toBigDecimal(number);

    return numberAsBigDecimal.remainder(BigDecimal.ONE)
        .movePointRight(normalizedScale(numberAsBigDecimal))
        .abs()
        .toBigInteger();
  }

  /**
   * Provides a {@code BigDecimal} representation of the given number.
   *
   * @param number the number to represent as a {@code BigDecimal}, not null
   * @return a {@code BigDecimal} representation of the given number, not null
   */
  @Nonnull
  static BigDecimal toBigDecimal(@Nonnull Number number) {
    requireNonNull(number);

    if (number instanceof BigDecimal)
      return (BigDecimal) number;

    return new BigDecimal(String.valueOf(number.doubleValue())).stripTrailingZeros();
  }

  /**
   * Are the given numbers equal?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the numbers are equal, not null
   */
  @Nonnull
  static Boolean equal(@Nonnull BigDecimal number1, @Nonnull BigDecimal number2) {
    requireNonNull(number1);
    requireNonNull(number2);

    return number1.compareTo(number2) == 0;
  }

  /**
   * Are the given numbers not equal?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the numbers are not equal, not null
   */
  @Nonnull
  static Boolean notEqual(@Nonnull BigDecimal number1, @Nonnull BigDecimal number2) {
    return !equal(number1, number2);
  }

  /**
   * Is the first number less than the second number?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the first number is less than the second number, not null
   */
  @Nonnull
  static Boolean lessThan(@Nonnull BigDecimal number1, @Nonnull BigDecimal number2) {
    requireNonNull(number1);
    requireNonNull(number2);

    return number1.compareTo(number2) < 0;
  }

  /**
   * Is the first number greater than the second number?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the first number is greater than the second number, not null
   */
  @Nonnull
  static Boolean greaterThan(@Nonnull BigDecimal number1, @Nonnull BigDecimal number2) {
    requireNonNull(number1);
    requireNonNull(number2);

    return number1.compareTo(number2) > 0;
  }

  /**
   * Does the given number fall within the range defined by its minimum and maximum (inclusive)?
   * <p>
   * <strong>Note:</strong> the scale of the number must match the scale of the range.  That is, a range with minimum {@code 2}
   * and maximum of {@code 5} would contain {@code 3} but <strong>not</strong> {@code 3.5}.
   *
   * @param number  the number to compare, not null
   * @param minimum the low end of the range (inclusive), not null
   * @param maximum the high end of the range (inclusive), not null
   * @return true if the number falls within the range, not null
   * @throws IllegalArgumentException if the minimum and maximum values have mismatched scales
   */
  @Nonnull
  static Boolean inRange(@Nonnull BigDecimal number, @Nonnull BigDecimal minimum, @Nonnull BigDecimal maximum) {
    requireNonNull(number);
    requireNonNull(minimum);
    requireNonNull(maximum);

    int minimumScale = normalizedScale(minimum);
    int maximumScale = normalizedScale(maximum);

    if (minimumScale != maximumScale)
      throw new IllegalArgumentException(format("Minimum scale %d does not match maximum scale %d", minimumScale, maximumScale));

    int numberScale = normalizedScale(number);

    if (numberScale != minimumScale)
      return false;

    return number.compareTo(minimum) >= 0 && number.compareTo(maximum) <= 0;
  }

  /**
   * Does the given number not fall within the range defined by its minimum and maximum (inclusive)?
   *
   * @param number  the number to compare, not null
   * @param minimum the low end of the range (inclusive), not null
   * @param maximum the high end of the range (inclusive), not null
   * @return true if the number falls within the range, not null
   */
  @Nonnull
  static Boolean notInRange(@Nonnull BigDecimal number, @Nonnull BigDecimal minimum, @Nonnull BigDecimal maximum) {
    return !inRange(number, minimum, maximum);
  }

  /**
   * Is the given number in the set of values?
   *
   * @param number the number to compare, not null
   * @param values the set of values against which to check, may be null
   * @return true if the number is in the set of values
   */
  @Nonnull
  static Boolean inSet(@Nonnull BigDecimal number, @Nullable BigDecimal... values) {
    requireNonNull(number);

    if (values == null || values.length == 0)
      return false;

    for (BigDecimal value : values)
      if (equal(number, value))
        return true;

    return false;
  }

  /**
   * Is the given number not in the set of values?
   *
   * @param number the number to compare, not null
   * @param values the set of values against which to check, may be null
   * @return true if the number is not in the set of values
   */
  @Nonnull
  static Boolean notInSet(@Nonnull BigDecimal number, @Nullable BigDecimal... values) {
    return !inSet(number, values);
  }

  /**
   * Are the given numbers equal?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the numbers are equal, not null
   */
  @Nonnull
  static Boolean equal(@Nonnull BigInteger number1, @Nonnull BigInteger number2) {
    requireNonNull(number1);
    requireNonNull(number2);

    return number1.compareTo(number2) == 0;
  }

  /**
   * Are the given numbers not equal?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the numbers are not equal, not null
   */
  @Nonnull
  static Boolean notEqual(@Nonnull BigInteger number1, @Nonnull BigInteger number2) {
    return !equal(number1, number2);
  }

  /**
   * Is the first number less than the second number?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the first number is less than the second number, not null
   */
  @Nonnull
  static Boolean lessThan(@Nonnull BigInteger number1, @Nonnull BigInteger number2) {
    requireNonNull(number1);
    requireNonNull(number2);

    return number1.compareTo(number2) < 0;
  }

  /**
   * Is the first number greater than the second number?
   *
   * @param number1 the first number to compare, not null
   * @param number2 the second number to compare, not null
   * @return true if the first number is greater than the second number, not null
   */
  @Nonnull
  static Boolean greaterThan(@Nonnull BigInteger number1, @Nonnull BigInteger number2) {
    requireNonNull(number1);
    requireNonNull(number2);

    return number1.compareTo(number2) > 0;
  }

  /**
   * Does the given number fall within the range defined by its minimum and maximum (inclusive)?
   *
   * @param number  the number to compare, not null
   * @param minimum the low end of the range (inclusive), not null
   * @param maximum the high end of the range (inclusive), not null
   * @return true if the number falls within the range, not null
   */
  @Nonnull
  static Boolean inRange(@Nonnull BigInteger number, @Nonnull BigInteger minimum, @Nonnull BigInteger maximum) {
    requireNonNull(number);
    requireNonNull(minimum);
    requireNonNull(maximum);

    return number.compareTo(minimum) >= 0 && number.compareTo(maximum) <= 0;
  }

  /**
   * Does the given number not fall within the range defined by its minimum and maximum (inclusive)?
   *
   * @param number  the number to compare, not null
   * @param minimum the low end of the range (inclusive), not null
   * @param maximum the high end of the range (inclusive), not null
   * @return true if the number falls within the range, not null
   */
  @Nonnull
  static Boolean notInRange(@Nonnull BigInteger number, @Nonnull BigInteger minimum, @Nonnull BigInteger maximum) {
    return !inRange(number, minimum, maximum);
  }

  /**
   * Is the given number in the set of values?
   *
   * @param number the number to compare, not null
   * @param values the set of values against which to check, may be null
   * @return true if the number is in the set of values
   */
  @Nonnull
  static Boolean inSet(@Nonnull BigInteger number, @Nullable BigInteger... values) {
    requireNonNull(number);

    if (values == null || values.length == 0)
      return false;

    for (BigInteger value : values)
      if (equal(number, value))
        return true;

    return false;
  }

  /**
   * Is the given number not in the set of values?
   *
   * @param number the number to compare, not null
   * @param values the set of values against which to check, may be null
   * @return true if the number is not in the set of values
   */
  @Nonnull
  static Boolean notInSet(@Nonnull BigInteger number, @Nullable BigInteger... values) {
    return !inSet(number, values);
  }

  /**
   * Determines the "normalized" scale for the given number.
   * <p>
   * The standard behavior of {@link BigDecimal#scale()} is to return a negative number for each tens place,
   * e.g. {@code 10} has scale of {@code -1} and {@code 100} has scale {@code -2}.
   * <p>
   * This method instead returns {@code 0} for any negative scale, which is usually the behavior we want for our calculations.
   *
   * @param number the number for which normalized scale is determined, not null
   * @return the normalized scale, not null
   */
  @Nonnull
  private static Integer normalizedScale(@Nonnull BigDecimal number) {
    requireNonNull(number);

    int scale = number.scale();
    return scale < 0 ? 0 : scale;
  }
}