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
import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;

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
      return numberAsBigDecimal.scale();
    }

    // Cannot determine trailing zeroes in this case
    BigDecimal numberAsBigDecimal = new BigDecimal(String.valueOf(number.doubleValue()));
    return Math.max(0, numberAsBigDecimal.stripTrailingZeros().scale());
  }
}