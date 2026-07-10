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

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link Range}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RangeTests {
  @Test
  public void finiteRangesExposeOrderedImmutableValues() {
    Range<String> range = Range.ofFiniteValues("a", "b", "c");

    assertEquals(3, range.getValues().size());
    assertFalse(range.getValues().isEmpty());
    assertFalse(range.isInfinite());
    assertEquals(List.of("a", "b", "c"), range.getValues());
    assertThrows(UnsupportedOperationException.class,
        () -> range.getValues().add("d"),
        "Expected range values to be immutable");
  }

  @Test
  public void infiniteRangesExposeOrderedImmutableValues() {
    Range<Integer> range = Range.ofInfiniteValues(1, 10, 100, 1_000);

    assertEquals(4, range.getValues().size());
    assertFalse(range.getValues().isEmpty());
    assertTrue(range.isInfinite());
    assertEquals(List.of(1, 10, 100, 1_000), range.getValues());
  }

  @Test
  public void emptyFactoriesReturnSharedFiniteAndInfiniteInstances() {
    assertSame(Range.emptyFiniteRange(), Range.ofFiniteValues());
    assertSame(Range.emptyFiniteRange(), Range.ofFiniteValues(List.of()));
    assertFalse(Range.emptyFiniteRange().isInfinite());

    assertSame(Range.emptyInfiniteRange(), Range.ofInfiniteValues());
    assertSame(Range.emptyInfiniteRange(), Range.ofInfiniteValues(List.of()));
    assertTrue(Range.emptyInfiniteRange().isInfinite());

    assertNotEquals(Range.emptyFiniteRange(), Range.emptyInfiniteRange());
  }

  @Test
  public void collectionFactoriesDefensivelyCopyInput() {
    List<String> values = new ArrayList<>(List.of("a", "b"));
    Range<String> range = Range.ofFiniteValues(values);

    values.add("c");

    assertEquals(List.of("a", "b"), range.getValues());
  }

  @Test
  public void factoriesRejectNullInputsAndElements() {
    assertThrows(NullPointerException.class,
        () -> Range.ofFiniteValues((List<String>) null));
    assertThrows(NullPointerException.class,
        () -> Range.ofInfiniteValues((List<String>) null));
    assertThrows(NullPointerException.class,
        () -> Range.ofFiniteValues((String[]) null));
    assertThrows(NullPointerException.class,
        () -> Range.ofInfiniteValues((String[]) null));
    assertThrows(NullPointerException.class,
        () -> Range.ofFiniteValues(Arrays.asList("a", null)));
    assertThrows(NullPointerException.class,
        () -> Range.ofInfiniteValues("a", null));
  }

  @Test
  public void rangesAreImmutableIterablesRatherThanCollections() {
    Range<String> range = Range.ofFiniteValues("a", "b");
    List<String> iteratedValues = new ArrayList<>();

    for (String value : range)
      iteratedValues.add(value);

    assertEquals(List.of("a", "b"), iteratedValues);
    assertFalse(Collection.class.isAssignableFrom(Range.class));
    assertTrue(Modifier.isFinal(Range.class.getModifiers()));
    assertEquals(Boolean.class, assertDoesNotThrowIsInfiniteReturnType());

    Iterator<String> iterator = range.iterator();
    iterator.next();
    assertThrows(UnsupportedOperationException.class, iterator::remove);
  }

  private static Class<?> assertDoesNotThrowIsInfiniteReturnType() {
    try {
      return Range.class.getMethod("isInfinite").getReturnType();
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  public void equalityAndHashCodeIncludeValuesAndInfiniteFlag() {
    Range<String> finite = Range.ofFiniteValues("a", "b");
    Range<String> sameFinite = Range.ofFiniteValues(List.of("a", "b"));
    Range<String> differentOrder = Range.ofFiniteValues("b", "a");
    Range<String> infinite = Range.ofInfiniteValues("a", "b");

    assertEquals(finite, sameFinite);
    assertEquals(finite.hashCode(), sameFinite.hashCode());
    assertNotEquals(finite, differentOrder);
    assertNotEquals(finite, infinite);
    assertNotEquals(finite, List.of("a", "b"));
  }

  @Test
  public void stringRepresentationIncludesValuesAndInfiniteFlag() {
    assertEquals("Range{values=a, b, infinite=false}", Range.ofFiniteValues("a", "b").toString());
    assertEquals("Range{values=1, 10, infinite=true}", Range.ofInfiniteValues(1, 10).toString());
  }
}
