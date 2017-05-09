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
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Collection of utility methods for working with Sets.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class Sets {
  private Sets() {
    // Non-instantiable
  }

  /**
   * Creates an immutable sorted set with the given values.
   *
   * @param values the values for the set, may be null
   * @param <T>    the type of values in the set
   * @return an immutable sorted set, not null
   */
  @Nonnull
  static <T> SortedSet<T> sortedSet(@Nullable T... values) {
    if (values == null || values.length == 0)
      return Collections.emptySortedSet();

    SortedSet<T> sortedSet = new TreeSet<>();

    for (T value : values)
      sortedSet.add(value);

    return Collections.unmodifiableSortedSet(sortedSet);
  }
}
