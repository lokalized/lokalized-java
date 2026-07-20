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

import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.lokalized.Diagnostics.format;
import static java.util.Objects.requireNonNull;

/**
 * Represents a structurally immutable, ordered range of values.
 * <p>
 * This class is not designed to hold large or "infinite" ranges; it is not stream-based.
 * Instead, you might supply a small representative range of values and specify the range is "infinite"
 * if it is understood that the value pattern repeats indefinitely.
 * <p>
 * For example, you might generate an infinite powers-of-ten range with the 4 values {@code 1, 10, 100, 1_000}.
 * <p>
 * A range is {@link Iterable}, but deliberately does not implement {@link Collection}: mutation is not part of its
 * contract. Use {@link #getValues()} when list operations are needed.
 * <p>
 * The range copies its input collection and never mutates or exposes its internal list, but it does not copy the
 * elements themselves. Mutable elements can therefore change this object's observed equality, hash code, and string
 * representation. Elements should be immutable or otherwise safely shared when a range is used concurrently or as a
 * map key or set member.
 * <p>
 * Ranges are constructed via static methods.
 * <p>
 * Examples:
 * <ul>
 * <li>{@code Range.ofFiniteValues("a", "b", "c")}</li>
 * <li>{@code Range.ofInfiniteValues(1, 10, 100, 1_000, 10_000)}</li>
 * <li>{@code Range.emptyFiniteRange()}</li>
 * <li>{@code Range.emptyInfiniteRange()}</li>
 * </ul>
 *
 * @param <T> the type of values contained in the range
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public final class Range<T> implements Iterable<@NonNull T> {
  @NonNull
  private static final Range<?> EMPTY_FINITE_RANGE = new Range<>(Collections.emptyList(), false);
  @NonNull
  private static final Range<?> EMPTY_INFINITE_RANGE = new Range<>(Collections.emptyList(), true);

  @NonNull
  private final List<@NonNull T> values;
  @NonNull
  private final Boolean infinite;

  /**
   * Provides an infinite range for the given values.
   *
   * @param values the values of the range, not null and containing no null elements
   * @param <T>    the type of values contained in the range
   * @return an infinite range, not null
   */
  @NonNull
  public static <T> Range<T> ofInfiniteValues(@NonNull Collection<@NonNull T> values) {
    requireNonNull(values);
    return values.isEmpty() ? emptyInfiniteRange() : new Range<>(values, true);
  }

  /**
   * Provides an infinite range for the given values.
   *
   * @param values the values of the range, not null and containing no null elements
   * @param <T>    the type of values contained in the range
   * @return an infinite range, not null
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  @NonNull
  public static <T> Range<T> ofInfiniteValues(@NonNull T @NonNull ... values) {
    requireNonNull(values);
    return values.length == 0 ? emptyInfiniteRange() : new Range<>(Arrays.asList(values), true);
  }

  /**
   * Provides a finite range for the given values.
   *
   * @param values the values of the range, not null and containing no null elements
   * @param <T>    the type of values contained in the range
   * @return a finite range, not null
   */
  @NonNull
  public static <T> Range<T> ofFiniteValues(@NonNull Collection<@NonNull T> values) {
    requireNonNull(values);
    return values.isEmpty() ? emptyFiniteRange() : new Range<>(values, false);
  }

  /**
   * Provides a finite range for the given values.
   *
   * @param values the values of the range, not null and containing no null elements
   * @param <T>    the type of values contained in the range
   * @return a finite range, not null
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  @NonNull
  public static <T> Range<T> ofFiniteValues(@NonNull T @NonNull ... values) {
    requireNonNull(values);
    return values.length == 0 ? emptyFiniteRange() : new Range<>(Arrays.asList(values), false);
  }

  /**
   * Gets the empty finite range.
   *
   * @param <T> the type of values contained in the range
   * @return the empty finite range, not null
   */
  @SuppressWarnings("unchecked")
  @NonNull
  public static <T> Range<T> emptyFiniteRange() {
    return (Range<T>) EMPTY_FINITE_RANGE;
  }

  /**
   * Gets the empty infinite range.
   *
   * @param <T> the type of values contained in the range
   * @return the empty infinite range, not null
   */
  @SuppressWarnings("unchecked")
  @NonNull
  public static <T> Range<T> emptyInfiniteRange() {
    return (Range<T>) EMPTY_INFINITE_RANGE;
  }

  private Range(@NonNull Collection<@NonNull T> values, @NonNull Boolean infinite) {
    requireNonNull(values);
    requireNonNull(infinite);

    List<@NonNull T> copiedValues = new ArrayList<>(values.size());
    for (T value : values)
      copiedValues.add(requireNonNull(value, "Range values may not contain null"));

    this.values = copiedValues.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(copiedValues);
    this.infinite = infinite;
  }

  /**
   * Returns an iterator over the values in this range in proper sequence.
   *
   * @return an immutable iterator over the values in this range, not null
   */
  @NonNull
  @Override
  public Iterator<@NonNull T> iterator() {
    return getValues().iterator();
  }

  /**
   * Generates a {@code String} representation of this object.
   *
   * @return a string representation of this object, not null
   */
  @Override
  @NonNull
  public String toString() {
    return format("%s{values=%s, infinite=%s}", getClass().getSimpleName(), getValues().stream()
        .map(Object::toString)
        .collect(Collectors.joining(", ")), isInfinite());
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

    Range<?> valueRange = (Range<?>) other;

    return Objects.equals(getValues(), valueRange.getValues())
        && Objects.equals(isInfinite(), valueRange.isInfinite());
  }

  /**
   * A hash code for this object.
   *
   * @return a suitable hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(getValues(), isInfinite());
  }

  /**
   * Gets the ordered values that comprise this range.
   *
   * @return an immutable list of the values that comprise this range, not null
   */
  @NonNull
  public List<@NonNull T> getValues() {
    return values;
  }

  /**
   * Gets whether this range is infinite.
   *
   * @return whether this range's pattern repeats indefinitely, not null
   * @since 3.0.0
   */
  @NonNull
  public Boolean isInfinite() {
    return infinite;
  }
}
