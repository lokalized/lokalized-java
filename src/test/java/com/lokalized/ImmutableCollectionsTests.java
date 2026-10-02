package com.lokalized;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImmutableCollectionsTests {
  @Test
  public void listsSnapshotInputAndPreserveOrderAndDuplicates() {
    String[] input = { "fr", "en", "fr" };
    List<String> values = ImmutableCollections.listOf(input);
    input[0] = "de";
    assertEquals(Arrays.asList("fr", "en", "fr"), values);
    assertThrows(UnsupportedOperationException.class, () -> values.set(0, "de"));
    assertThrows(UnsupportedOperationException.class, () -> values.subList(0, 1).clear());
  }

  @Test
  public void setsSnapshotInputAndPreventMutation() {
    String[] input = { "translation", "alternatives" };
    Set<String> values = ImmutableCollections.setOf(input);
    input[0] = "commentary";
    assertEquals(Set.of("translation", "alternatives"), values);
    assertThrows(UnsupportedOperationException.class, () -> values.remove("translation"));
    java.util.Iterator<String> iterator = values.iterator();
    iterator.next();
    assertThrows(UnsupportedOperationException.class, iterator::remove);
  }

  @Test
  public void nullArraysAndElementsAreRejected() {
    assertThrows(NullPointerException.class, () -> ImmutableCollections.listOf((String[]) null));
    assertThrows(NullPointerException.class, () -> ImmutableCollections.setOf((String[]) null));
    assertThrows(NullPointerException.class, () -> ImmutableCollections.listOf("en", null));
    assertThrows(NullPointerException.class, () -> ImmutableCollections.setOf("en", null));
  }

  @Test
  public void duplicateSetElementsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> ImmutableCollections.setOf("en", "en"));
  }

  @Test
  public void emptyCollectionsAreImmutable() {
    List<String> list = ImmutableCollections.listOf();
    Set<String> set = ImmutableCollections.setOf();
    assertEquals(0, list.size());
    assertEquals(0, set.size());
    assertThrows(UnsupportedOperationException.class, () -> list.add("en"));
    assertThrows(UnsupportedOperationException.class, () -> set.add("en"));
  }
}
