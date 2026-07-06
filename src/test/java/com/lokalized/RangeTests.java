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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    assertEquals(3, range.size());
    assertFalse(range.isEmpty());
    assertFalse(range.getInfinite());
    assertEquals(List.of("a", "b", "c"), range.getValues());
    assertThrows(UnsupportedOperationException.class,
        () -> range.getValues().add("d"),
        "Expected range values to be immutable");
  }

  @Test
  public void infiniteRangesExposeOrderedImmutableValues() {
    Range<Integer> range = Range.ofInfiniteValues(1, 10, 100, 1_000);

    assertEquals(4, range.size());
    assertFalse(range.isEmpty());
    assertTrue(range.getInfinite());
    assertEquals(List.of(1, 10, 100, 1_000), range.getValues());
  }

  @Test
  public void emptyFactoriesReturnSharedFiniteAndInfiniteInstances() {
    String[] nullValues = null;

    assertSame(Range.emptyFiniteRange(), Range.ofFiniteValues());
    assertSame(Range.emptyFiniteRange(), Range.ofFiniteValues(nullValues));
    assertSame(Range.emptyFiniteRange(), Range.ofFiniteValues(List.of()));
    assertFalse(Range.emptyFiniteRange().getInfinite());

    assertSame(Range.emptyInfiniteRange(), Range.ofInfiniteValues());
    assertSame(Range.emptyInfiniteRange(), Range.ofInfiniteValues(nullValues));
    assertSame(Range.emptyInfiniteRange(), Range.ofInfiniteValues(List.of()));
    assertTrue(Range.emptyInfiniteRange().getInfinite());

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
  public void collectionFactoriesRejectNullCollections() {
    assertThrows(NullPointerException.class,
        () -> Range.ofFiniteValues((List<String>) null));
    assertThrows(NullPointerException.class,
        () -> Range.ofInfiniteValues((List<String>) null));
  }

  @Test
  public void collectionOperationsDelegateToValues() {
    Range<String> range = Range.ofFiniteValues("a", "b", "c");

    assertTrue(range.contains("a"));
    assertFalse(range.contains("z"));
    assertTrue(range.containsAll(List.of("a", "c")));
    assertFalse(range.containsAll(List.of("a", "z")));
    assertThrows(NullPointerException.class,
        () -> range.containsAll(null));
  }

  @Test
  public void arrayConversionsFollowCollectionContract() {
    Range<String> range = Range.ofFiniteValues("a", "b");

    assertArrayEquals(new Object[] {"a", "b"}, range.toArray());

    String[] exact = range.toArray(new String[2]);
    assertArrayEquals(new String[] {"a", "b"}, exact);

    String[] oversized = range.toArray(new String[] {"x", "y", "z"});
    assertArrayEquals(new String[] {"a", "b", null}, oversized);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void mutationMethodsThrow() {
    Range<String> range = Range.ofFiniteValues("a", "b");

    assertThrows(UnsupportedOperationException.class,
        () -> range.add("c"));
    assertThrows(UnsupportedOperationException.class,
        () -> range.remove("a"));
    assertThrows(UnsupportedOperationException.class,
        () -> range.addAll(List.of("c")));
    assertThrows(UnsupportedOperationException.class,
        () -> range.removeAll(List.of("a")));
    assertThrows(UnsupportedOperationException.class,
        () -> range.retainAll(List.of("a")));
    assertThrows(UnsupportedOperationException.class, range::clear);

    Iterator<String> iterator = range.iterator();
    iterator.next();
    assertThrows(UnsupportedOperationException.class, iterator::remove);

    assertEquals(List.of("a", "b"), range.getValues());
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
