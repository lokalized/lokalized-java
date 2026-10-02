/*
 * Copyright 2026 Revetware LLC.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 * See the License for the specific language governing permissions and limitations.
 */
package com.lokalized;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static java.util.Objects.requireNonNull;

/** Immutable collection snapshots using APIs available on Android API 26. */
final class ImmutableCollections {
  private ImmutableCollections() {
  }

  @SafeVarargs
  static <E> List<E> listOf(E... elements) {
    requireNonNull(elements);
    List<E> copy = new ArrayList<>(elements.length);
    for (E element : elements)
      copy.add(requireNonNull(element));
    return Collections.unmodifiableList(copy);
  }

  @SafeVarargs
  static <E> Set<E> setOf(E... elements) {
    requireNonNull(elements);
    Set<E> copy = new LinkedHashSet<>();
    for (E element : elements)
      if (!copy.add(requireNonNull(element)))
        throw new IllegalArgumentException("duplicate element: " + element);
    return Collections.unmodifiableSet(copy);
  }
}
