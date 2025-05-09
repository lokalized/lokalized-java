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
import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
 * if it is understood that the value pattern repeats indefinitely.
 * <p>
 * For example, you might generate an infinite powers-of-ten range with the 4 values {@code 1, 10, 100, 1_000}.
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
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
public class Range<T> implements Collection<T> {
  @Nonnull
  private static final Range<?> EMPTY_FINITE_RANGE;
  @Nonnull
  private static final Range<?> EMPTY_INFINITE_RANGE;

  @Nonnull
  private final List<T> values;
  @Nonnull
  private final Boolean infinite;

  static {
    EMPTY_FINITE_RANGE = new Range<>(Collections.emptySet(), false);
    EMPTY_INFINITE_RANGE = new Range<>(Collections.emptySet(), true);
  }

  /**
   * Provides an infinite range for the given values.
   *
   * @param values the values of the range, not null
   * @param <T>    the type of values contained in the range
   * @return an infinite range, not null
   */
  @Nonnull
  public static <T> Range<T> ofInfiniteValues(@Nonnull Collection<T> values) {
    requireNonNull(values);
    return values.size() == 0 ? emptyInfiniteRange() : new Range(values, true);
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
    return values == null || values.length == 0 ? emptyInfiniteRange() : new Range(values, true);
  }

  /**
   * Provides a finite range for the given values.
   *
   * @param values the values of the range, not null
   * @param <T>    the type of values contained in the range
   * @return a finite range, not null
   */
  @Nonnull
  public static <T> Range<T> ofFiniteValues(@Nonnull Collection<T> values) {
    requireNonNull(values);
    return values.size() == 0 ? emptyFiniteRange() : new Range(values, false);
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
    return values == null || values.length == 0 ? emptyFiniteRange() : new Range(values, false);
  }

  /**
   * Gets the empty finite range.
   *
   * @param <T> the type of values contained in the range
   * @return the empty finite range, not null
   */
  public static <T> Range<T> emptyFiniteRange() {
    return (Range<T>) EMPTY_FINITE_RANGE;
  }

  /**
   * Gets the empty infinite range.
   *
   * @param <T> the type of values contained in the range
   * @return the empty infinite range, not null
   */
  public static <T> Range<T> emptyInfiniteRange() {
    return (Range<T>) EMPTY_INFINITE_RANGE;
  }

  /**
   * Creates a range with the given values and "infinite" flag.
   *
   * @param values   the values that comprise this range, not null
   * @param infinite whether this range is infinite - that is, whether the range's pattern repeats indefinitely, not null
   */
  private Range(@Nonnull Collection<T> values, @Nonnull Boolean infinite) {
    requireNonNull(values);
    requireNonNull(infinite);

    this.values = values.size() == 0 ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
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
   * Returns the number of elements in this range.
   *
   * @return the number of elements in this range
   */
  @Override
  public int size() {
    return getValues().size();
  }

  /**
   * Returns true if this range contains no elements.
   *
   * @return true if this range contains no elements
   */
  @Override
  public boolean isEmpty() {
    return getValues().isEmpty();
  }

  /**
   * Returns true if this range contains the specified value.
   * <p>
   * More formally, returns true if and only if this range contains at least one value v such that {@code (o==null ? v==null : o.equals(v))}.
   *
   * @param value value whose presence in this range is to be tested
   * @return true if this range contains the specified value
   */
  @Override
  public boolean contains(@Nullable Object value) {
    return getValues().contains(value);
  }

  /**
   * Returns an iterator over the values in this range in proper sequence.
   * <p>
   * The returned iterator is <a href="https://docs.oracle.com/javase/8/docs/api/java/util/ArrayList.html#fail-fast" target="_blank">fail-fast</a>.
   *
   * @return an iterator over the values in this range in proper sequence, not null
   */
  @Nonnull
  @Override
  public Iterator<T> iterator() {
    return getValues().iterator();
  }

  /**
   * Returns an array containing all of the values in this range in proper sequence (from first to last value).
   * <p>
   * The returned array will be "safe" in that no references to it are maintained by this range. (In other words, this method must allocate a new array). The caller is thus free to modify the returned array.
   * <p>
   * This method acts as bridge between array-based and collection-based APIs.
   *
   * @return an array containing all of the values in this range in proper sequence, not null
   */
  @Nonnull
  @Override
  public Object[] toArray() {
    return getValues().toArray();
  }

  /**
   * Returns an array containing all of the values in this range in proper sequence (from first to last element); the runtime type of the returned array is that of the specified array. If the range fits in the specified array, it is returned therein. Otherwise, a new array is allocated with the runtime type of the specified array and the size of this range.
   * <p>
   * If the range fits in the specified array with room to spare (i.e., the array has more elements than the range), the element in the array immediately following the end of the collection is set to null. (This is useful in determining the length of the range only if the caller knows that the range does not contain any null elements.)
   *
   * @param a    the array into which the values of the range are to be stored, if it is big enough; otherwise, a new array of the same runtime type is allocated for this purpose. not null
   * @param <T1> the runtime type of the array to contain the collection
   * @return an array containing the values of the range, not null
   */
  @Nonnull
  @Override
  public <T1> T1[] toArray(@Nonnull T1[] a) {
    return getValues().toArray(a);
  }

  /**
   * Guaranteed to throw an exception and leave the range unmodified.
   *
   * @param t the value to add, ignored
   * @return no return value; this method always throws UnsupportedOperationException
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation; this type is immutable.
   */
  @Override
  @Deprecated
  public boolean add(@Nullable T t) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the range unmodified.
   *
   * @param o the value to remove, ignored
   * @return no return value; this method always throws UnsupportedOperationException
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation; this type is immutable.
   */
  @Override
  @Deprecated
  public boolean remove(@Nullable Object o) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns true if this range contains all of the elements of the specified collection.
   *
   * @param c collection to be checked for containment in this range, not null
   * @return true if this range contains all of the elements of the specified collection
   */
  @Override
  public boolean containsAll(@Nonnull Collection<?> c) {
    requireNonNull(c);
    return getValues().containsAll(c);
  }

  /**
   * Guaranteed to throw an exception and leave the range unmodified.
   *
   * @param c collection containing elements to be added to this range, ignored
   * @return no return value; this method always throws UnsupportedOperationException
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation; this type is immutable.
   */
  @Override
  @Deprecated
  public boolean addAll(@Nullable Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the range unmodified.
   *
   * @param c collection containing elements to be removed from this range, ignored
   * @return no return value; this method always throws UnsupportedOperationException
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation; this type is immutable.
   */
  @Override
  @Deprecated
  public boolean removeAll(@Nullable Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the range unmodified.
   *
   * @param c collection containing elements to be retained in this range, ignored
   * @return no return value; this method always throws UnsupportedOperationException
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation; this type is immutable.
   */
  @Override
  @Deprecated
  public boolean retainAll(@Nullable Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the range unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation; this type is immutable.
   */
  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException();
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