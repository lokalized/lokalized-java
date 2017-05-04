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
import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Represents a concrete range of values.
 * <p>
 * This class is not designed to hold large or "infinite" ranges; it is not stream-based.
 * Instead, you might supply a small representative range of values and specify the range is "infinite"
 * if it is understood that the range's pattern repeats indefinitely.
 * <p>
 * For example, you might generate an infinite powers-of-ten range with the 4 values {@code 1, 10, 100, 1000}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
public class Range<T> {
  @Nonnull
  private final List<T> values;
  @Nonnull
  private final Boolean infinite;

  /**
   * Provides an infinite range for the given values.
   *
   * @param values the values of the range, not null
   * @param <T>    the type of values contained in the range
   * @return an infinite range, not null
   */
  @Nonnull
  public static <T> Range<T> ofInfiniteValues(@Nonnull Iterable<T> values) {
    requireNonNull(values);
    return new Range(values, true);
  }

  /**
   * Provides an infinite range for the given values.
   *
   * @param values the values of the range, may be null
   * @param <T>    the type of values contained in the range
   * @return an infinite range, not null
   */
  @Nonnull
  public static <T> Range<T> ofInfiniteValues(@Nullable T... values) {
    return new Range(values, true);
  }

  /**
   * Provides a finite range for the given values.
   *
   * @param values the values of the range, not null
   * @param <T>    the type of values contained in the range
   * @return a finite range, not null
   */
  @Nonnull
  public static <T> Range<T> ofFiniteValues(@Nonnull Iterable<T> values) {
    requireNonNull(values);
    return new Range(values, false);
  }

  /**
   * Provides a finite range for the given values.
   *
   * @param values the values of the range, may be null
   * @param <T>    the type of values contained in the range
   * @return a finite range, not null
   */
  @Nonnull
  public static <T> Range<T> ofFiniteValues(@Nullable T... values) {
    return new Range(values, false);
  }

  /**
   * Creates a range with the given values and "infinite" flag.
   *
   * @param values   the values that comprise this range, not null
   * @param infinite whether this range is infinite - that is, whether the range's pattern repeats indefinitely, not null
   */
  private Range(@Nonnull Iterable<T> values, @Nonnull Boolean infinite) {
    requireNonNull(values);
    requireNonNull(infinite);

    List<T> valuesAsList = new ArrayList<>();
    values.forEach(valuesAsList::add);

    this.values = Collections.unmodifiableList(valuesAsList);
    this.infinite = infinite;
  }

  /**
   * Creates a range with the given values and "infinite" flag.
   *
   * @param values   the values that comprise this range, may be null
   * @param infinite whether this range is infinite - that is, whether the range's pattern repeats indefinitely, not null
   */
  private Range(@Nullable T[] values, @Nonnull Boolean infinite) {
    requireNonNull(values);
    requireNonNull(infinite);

    this.values = values == null ? Collections.emptyList() : Collections.unmodifiableList(Arrays.asList(values));
    this.infinite = infinite;
  }

  /**
   * Generates a {@code String} representation of this object.
   *
   * @return a string representation of this object, not null
   */
  @Override
  @Nonnull
  public String toString() {
    return format("%s{values=%s, infinite=%s}", getClass().getSimpleName(), getValues().stream()
        .map(value -> value.toString())
        .collect(Collectors.joining(", ")), getInfinite());
  }

  /**
   * Checks if this object is equal to another one.
   *
   * @param other the object to check, null returns false
   * @return true if this is equal to the other object, false otherwise
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other)
      return true;

    if (other == null || !getClass().equals(other.getClass()))
      return false;

    Range valueRange = (Range) other;

    return Objects.equals(getValues(), valueRange.getValues())
        && Objects.equals(getInfinite(), valueRange.getInfinite());
  }

  /**
   * A hash code for this object.
   *
   * @return a suitable hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(getValues(), getInfinite());
  }

  /**
   * Gets the values that comprise this range.
   *
   * @return the values that comprise this range, not null
   */
  @Nonnull
  public List<T> getValues() {
    return values;
  }

  /**
   * Gets whether this range is infinite.
   *
   * @return whether this range is infinite - that is, whether the range's pattern repeats indefinitely, not null
   */
  @Nonnull
  public Boolean getInfinite() {
    return infinite;
  }
}