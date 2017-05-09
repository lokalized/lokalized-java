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
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.util.Objects.requireNonNull;

/**
 * Collection of utility methods for working with Maps.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class Maps {
  private Maps() {
    // Non-instantiable
  }

  /**
   * Creates an immutable sorted map with the given values.
   *
   * @param mapEntries the entries for the map, may be null
   * @param <K>        the type of keys in the map
   * @param <V>        type type of values in the map
   * @return an immutable sorted map, not null
   */
  @Nonnull
  static <K, V> SortedMap<K, V> sortedMap(@Nullable MapEntry<K, V>... mapEntries) {
    if (mapEntries == null || mapEntries.length == 0)
      return Collections.emptySortedMap();

    SortedMap<K, V> sortedMap = new TreeMap<>();

    for (MapEntry<K, V> mapEntry : mapEntries)
      sortedMap.put(mapEntry.getKey(), mapEntry.getValue());

    return Collections.unmodifiableSortedMap(sortedMap);
  }

  /**
   * A representation of a map entry (key-value pair).
   *
   * @param <K> the type of key in the map entry
   * @param <V> the type of value in the map entry
   */
  @Immutable
  static class MapEntry<K, V> {
    @Nonnull
    private final K key;
    @Nonnull
    private final V value;

    /**
     * Provides a map entry with the given key and value.
     *
     * @param key   the key for the map entry, not null
     * @param value the value for the map entry, not null
     * @param <K>
     * @param <V>
     * @return a map entry, not null
     */
    @Nonnull
    public static <K, V> MapEntry<K, V> of(@Nonnull K key, @Nonnull V value) {
      requireNonNull(key);
      requireNonNull(value);

      return new MapEntry<>(key, value);
    }

    /**
     * Creates a map entry with the given key and value.
     *
     * @param key   the key for this map entry, not null
     * @param value the value for this map entry, not null
     */
    private MapEntry(@Nonnull K key, @Nonnull V value) {
      requireNonNull(key);
      requireNonNull(value);

      this.key = key;
      this.value = value;
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @Nonnull
    public String toString() {
      return String.format("%s{key=%s, value=%s}", getClass().getSimpleName(), getKey(), getValue());
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

      MapEntry mapEntry = (MapEntry) other;

      return Objects.equals(getKey(), mapEntry.getKey())
          && Objects.equals(getValue(), mapEntry.getValue());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getKey(), getValue());
    }


    /**
     * The key for this map entry.
     *
     * @return the key, not null
     */
    @Nonnull
    public K getKey() {
      return key;
    }

    /**
     * The value for this map entry.
     *
     * @return the value, not null
     */
    @Nonnull
    public V getValue() {
      return value;
    }
  }
}
