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
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents a cardinality range - for example, {@code "1-3 meters"}.
 * <p>
 * The start of the above range would be the cardinality that maps to {@code 1} and the end
 * would be the cardinality that maps to {@code 3}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
class CardinalityRange implements Comparable<CardinalityRange> {
  @Nonnull
  private final Cardinality start;
  @Nonnull
  private final Cardinality end;

  /**
   * Provides a cardinality range with the given start and end cardinalities.
   *
   * @param start the range's start cardinality, not null
   * @param end   the range's end cardinality, not null
   * @return a cardinality range with the given start and end cardinalities, not null
   */
  @Nonnull
  static CardinalityRange of(@Nonnull Cardinality start, @Nonnull Cardinality end) {
    requireNonNull(start);
    requireNonNull(end);

    // Future: share/recycle instances?
    return new CardinalityRange(start, end);
  }

  /**
   * Constructs a cardinality range with the given start and end cardinalities.
   * <p>
   * Currently private and only invoked via {@link #of(Cardinality, Cardinality)} for futureproofing (perhaps we share/recycle instances).
   *
   * @param start the range's start cardinality, not null
   * @param end   the range's end cardinality, not null
   */
  private CardinalityRange(@Nonnull Cardinality start, @Nonnull Cardinality end) {
    requireNonNull(start);
    requireNonNull(end);

    this.start = start;
    this.end = end;
  }

  /**
   * Compares this cardinality range to another.
   *
   * @param cardinalityRange the cardinality range against which to compare, not null
   * @return the comparison result
   */
  @Override
  public int compareTo(@Nonnull CardinalityRange cardinalityRange) {
    requireNonNull(cardinalityRange);

    int comparison = getStart().compareTo(cardinalityRange.getStart());

    if (comparison == 0)
      comparison = getEnd().compareTo(cardinalityRange.getEnd());

    return comparison;
  }

  /**
   * Generates a {@code String} representation of this object.
   *
   * @return a string representation of this object, not null
   */
  @Override
  @Nonnull
  public String toString() {
    return String.format("%s{start=%s, end=%s}", getClass().getSimpleName(), getStart().name(), getEnd().name());
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

    CardinalityRange cardinalityRange = (CardinalityRange) other;

    return Objects.equals(getStart(), cardinalityRange.getStart())
        && Objects.equals(getEnd(), cardinalityRange.getEnd());
  }

  /**
   * A hash code for this object.
   *
   * @return a suitable hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(getStart(), getEnd());
  }

  /**
   * Gets the range's start cardinality.
   *
   * @return the range's start cardinality, not null
   */
  @Nonnull
  public Cardinality getStart() {
    return start;
  }

  /**
   * Gets the range's end cardinality.
   *
   * @return the range's end cardinality, not null
   */
  @Nonnull
  public Cardinality getEnd() {
    return end;
  }
}