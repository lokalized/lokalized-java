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

import com.lokalized.LocalizedString.ExpressionAlternative;
import com.lokalized.LocalizedString.ExpressionTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.PlaceholderDefinition;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.NotThreadSafe;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link LocalizedString}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class LocalizedStringTests {
  @Test
  public void immutableRuntimeModelTypesHaveExpectedFinality() throws NoSuchMethodException {
    assertTrue(Modifier.isFinal(LocalizedString.class.getModifiers()));
    assertTrue(Modifier.isAbstract(PlaceholderDefinition.class.getModifiers()));
    assertFalse(Modifier.isFinal(PlaceholderDefinition.class.getModifiers()));
    assertTrue(Modifier.isFinal(LanguageFormTranslation.class.getModifiers()));
    assertTrue(Modifier.isFinal(ExpressionTranslation.class.getModifiers()));
    assertTrue(Modifier.isFinal(ExpressionAlternative.class.getModifiers()));
    assertTrue(Modifier.isFinal(LocalizedString.LanguageFormTranslationRange.class.getModifiers()));

    Constructor<?>[] constructors = PlaceholderDefinition.class.getDeclaredConstructors();
    assertTrue(constructors.length > 0);
    for (Constructor<?> constructor : constructors)
      assertFalse(Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers()),
          "Expected the placeholder-definition hierarchy to be closed to callers");
    assertTrue(Modifier.isPrivate(PlaceholderDefinition.class.getDeclaredConstructor().getModifiers()));
  }

  @Test
  public void placeholderDefinitionsAreDefensivelyCopiedAcrossDefinitionKinds() {
    Map<LanguageForm, String> translationsByLanguageForm = new HashMap<>();
    translationsByLanguageForm.put(Cardinality.ONE, "book");
    LanguageFormTranslation books = new LanguageFormTranslation("bookCount", translationsByLanguageForm);

    List<ExpressionAlternative> alternatives = new ArrayList<>();
    alternatives.add(new ExpressionAlternative("bookCount == 0", "no books"));
    ExpressionTranslation summary = new ExpressionTranslation("{{bookCount}} {{books}}", alternatives);

    Map<String, PlaceholderDefinition> definitions = new LinkedHashMap<>();
    definitions.put("books", books);
    definitions.put("summary", summary);

    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .placeholderDefinitions(definitions)
        .build();

    translationsByLanguageForm.clear();
    alternatives.clear();
    definitions.clear();

    assertEquals(List.of("books", "summary"), new ArrayList<>(localizedString.getPlaceholderDefinitions().keySet()));
    assertTrue(books.getTranslationsByLanguageForm().containsKey(Cardinality.ONE));
    assertEquals(List.of(new ExpressionAlternative("bookCount == 0", "no books")), summary.getAlternatives());

    assertThrows(UnsupportedOperationException.class,
        () -> localizedString.getPlaceholderDefinitions().put("other", null),
        "Expected placeholder definitions map to be unmodifiable");
    assertThrows(UnsupportedOperationException.class,
        () -> books.getTranslationsByLanguageForm().put(Cardinality.OTHER, "books"),
        "Expected each language-form map to be unmodifiable");
    assertThrows(UnsupportedOperationException.class,
        () -> summary.getAlternatives().add(new ExpressionAlternative("bookCount == 1", "one book")),
        "Expected expression alternatives to be unmodifiable");
  }

  @Test
  public void builderAcceptsDefinitionMapForOneConcreteSubtype() {
    Map<String, ExpressionTranslation> definitions = Map.of(
        "productName", new ExpressionTranslation("Firefox"));

    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{productName}}")
        .placeholderDefinitions(definitions)
        .build();

    assertEquals(definitions, localizedString.getPlaceholderDefinitions());
  }

  @Test
  public void translationOnlyExpressionTranslationHasNoAlternatives() {
    ExpressionTranslation translation = new ExpressionTranslation("Firefox");

    assertEquals("Firefox", translation.getTranslation());
    assertTrue(translation.getAlternatives().isEmpty());
    assertThrows(UnsupportedOperationException.class,
        () -> translation.getAlternatives().add(new ExpressionAlternative("count == 1", "value")));
  }

  @Test
  public void expressionAlternativesPreserveDeclarationOrder() {
    ExpressionAlternative first = new ExpressionAlternative("count == 0", "none");
    ExpressionAlternative second = new ExpressionAlternative("count >= limit", "many");
    ExpressionTranslation translation = new ExpressionTranslation("ordinary", List.of(first, second));

    assertEquals(List.of(first, second), translation.getAlternatives());
  }

  @Test
  public void expressionValueTypesSupportEqualityHashingAndStringRendering() {
    ExpressionAlternative alternative = new ExpressionAlternative("count == 0", "none");
    ExpressionAlternative equalAlternative = new ExpressionAlternative("count == 0", "none");
    ExpressionAlternative differentAlternative = new ExpressionAlternative("count == 1", "one");

    assertEquals(alternative, equalAlternative);
    assertEquals(alternative.hashCode(), equalAlternative.hashCode());
    assertNotEquals(alternative, differentAlternative);
    assertEquals("ExpressionAlternative{expression=count == 0, translation=none}", alternative.toString());

    ExpressionTranslation translation = new ExpressionTranslation("default", List.of(alternative));
    ExpressionTranslation equalTranslation = new ExpressionTranslation("default", List.of(equalAlternative));
    ExpressionTranslation differentTranslation = new ExpressionTranslation("different", List.of(equalAlternative));
    ExpressionTranslation differentAlternatives = new ExpressionTranslation("default", List.of(differentAlternative));

    assertEquals(translation, equalTranslation);
    assertEquals(translation.hashCode(), equalTranslation.hashCode());
    assertNotEquals(translation, differentTranslation);
    assertNotEquals(translation, differentAlternatives);
    assertEquals("ExpressionTranslation{translation=default, alternatives=[" + alternative + "]}",
        translation.toString());
  }

  @Test
  public void localizedStringValueSemanticsIncludeUnifiedPlaceholderDefinitions() {
    LocalizedString first = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .commentary("commentary")
        .placeholderDefinitions(Map.of("summary", new ExpressionTranslation("summary")))
        .build();
    LocalizedString equal = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .commentary("commentary")
        .placeholderDefinitions(Map.of("summary", new ExpressionTranslation("summary")))
        .build();
    LocalizedString different = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .commentary("commentary")
        .placeholderDefinitions(Map.of("summary", new ExpressionTranslation("different")))
        .build();

    assertEquals(first, equal);
    assertEquals(first.hashCode(), equal.hashCode());
    assertNotEquals(first, different);
    assertTrue(first.toString().contains("placeholderDefinitions={summary=ExpressionTranslation"));
  }

  @Test
  public void localizedStringEqualityAndHashingAreIterativeForDeepGraphs() {
    LocalizedString first = new LocalizedString.Builder("count == 1").translation("leaf").build();
    LocalizedString second = new LocalizedString.Builder("count == 1").translation("leaf").build();

    for (int depth = 0; depth < 5_000; ++depth) {
      first = new LocalizedString.Builder("count == 1").alternatives(List.of(first)).build();
      second = new LocalizedString.Builder("count == 1").alternatives(List.of(second)).build();
    }

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  public void localizedStringHashingMemoizesSharedAlternativeDags() {
    LocalizedString first = new LocalizedString.Builder("count == 1").translation("leaf").build();
    LocalizedString second = new LocalizedString.Builder("count == 1").translation("leaf").build();

    for (int depth = 0; depth < 120; ++depth) {
      first = new LocalizedString.Builder("count == 1").alternatives(List.of(first, first)).build();
      second = new LocalizedString.Builder("count == 1").alternatives(List.of(second, second)).build();
    }

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  public void localizedStringRenderingIsIterativeAndBoundedForDeepGraphs() {
    LocalizedString localizedString = new LocalizedString.Builder("leaf").translation("leaf").build();

    for (int depth = 0; depth < 5_000; ++depth)
      localizedString = new LocalizedString.Builder("count == 1").alternatives(List.of(localizedString)).build();

    String diagnostic = localizedString.toString();

    assertTrue(diagnostic.length() <= 16 * 1024);
    assertTrue(diagnostic.endsWith("<truncated>"));
    assertEquals(diagnostic, localizedString.toString());
  }

  @Test
  public void localizedStringRenderingUsesStableReferencesForSharedAlternativeDags() {
    LocalizedString localizedString = new LocalizedString.Builder("leaf").translation("leaf").build();

    for (int depth = 0; depth < 120; ++depth)
      localizedString = new LocalizedString.Builder("count == 1")
          .alternatives(List.of(localizedString, localizedString))
          .build();

    String diagnostic = localizedString.toString();

    assertTrue(diagnostic.contains("<ref#"));
    assertTrue(diagnostic.length() <= 16 * 1024);
    assertFalse(diagnostic.endsWith("<truncated>"));
    assertEquals(diagnostic, localizedString.toString());
  }

  @Test
  public void localizedStringRenderingTerminatesForIdentityCycles() throws ReflectiveOperationException {
    LocalizedString localizedString = new LocalizedString.Builder("cycle").translation("fallback").build();
    Field alternativesField = LocalizedString.class.getDeclaredField("alternatives");
    alternativesField.setAccessible(true);
    alternativesField.set(localizedString, List.of(localizedString));

    assertEquals("LocalizedString{key=cycle, translation=fallback, alternatives=[<cycle#1>]}",
        localizedString.toString());
  }

  @Test
  public void localizedStringRenderingCapsIndividualScalarValuesIncrementally() {
    StringBuilder oversizedKeyBuilder = new StringBuilder(100_000);

    for (int index = 0; index < 50_000; ++index)
      oversizedKeyBuilder.append("\uD83D\uDE42");

    String oversizedKey = oversizedKeyBuilder.toString();
    LocalizedString localizedString = new LocalizedString.Builder(oversizedKey).translation("translation").build();

    String diagnostic = localizedString.toString();

    assertTrue(diagnostic.length() <= 16 * 1024);
    assertTrue(diagnostic.endsWith("<truncated>"));
    assertTrue(diagnostic.codePoints().noneMatch(codePoint ->
        codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE));
  }

  @Test
  public void localizedStringRenderingPreservesOrdinaryShallowDiagnostics() {
    LocalizedString alternative = new LocalizedString.Builder("count == 1").translation("one").build();
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("default")
        .commentary("note")
        .alternatives(List.of(alternative))
        .build();

    assertEquals("LocalizedString{key=key, translation=default, commentary=note, alternatives=[" +
        "LocalizedString{key=count == 1, translation=one}]}", localizedString.toString());
  }

  @Test
  public void expressionValueTypesRejectInvalidConstructorArguments() {
    assertThrows(NullPointerException.class, () -> new ExpressionTranslation(null));
    assertThrows(NullPointerException.class, () -> new ExpressionTranslation("default", null));
    assertThrows(IllegalArgumentException.class, () -> new ExpressionTranslation("default", List.of()));
    assertThrows(NullPointerException.class, () -> new ExpressionAlternative(null, "translation"));
    assertThrows(NullPointerException.class, () -> new ExpressionAlternative("count == 0", null));
  }

  @Test
  public void publicValueCollectionsRejectNullContentsAtConstruction() {
    LocalizedString child = new LocalizedString.Builder("child").translation("child").build();
    List<LocalizedString> localizedStringAlternatives = new ArrayList<>();
    localizedStringAlternatives.add(child);
    localizedStringAlternatives.add(null);
    NullPointerException nullAlternative = assertThrows(NullPointerException.class,
        () -> new LocalizedString.Builder("root").alternatives(localizedStringAlternatives).build());
    assertTrue(nullAlternative.getMessage().contains("alternatives"));

    PlaceholderDefinition definition = new ExpressionTranslation("generated");
    Map<String, PlaceholderDefinition> nullDefinitionKey = new HashMap<>();
    nullDefinitionKey.put(null, definition);
    NullPointerException nullPlaceholderKey = assertThrows(NullPointerException.class,
        () -> new LocalizedString.Builder("root").translation("root")
            .placeholderDefinitions(nullDefinitionKey).build());
    assertTrue(nullPlaceholderKey.getMessage().contains("null key"));

    Map<String, PlaceholderDefinition> nullDefinitionValue = new HashMap<>();
    nullDefinitionValue.put("generated", null);
    NullPointerException nullPlaceholderValue = assertThrows(NullPointerException.class,
        () -> new LocalizedString.Builder("root").translation("root")
            .placeholderDefinitions(nullDefinitionValue).build());
    assertTrue(nullPlaceholderValue.getMessage().contains("null value"));

    List<ExpressionAlternative> expressionAlternatives = new ArrayList<>();
    expressionAlternatives.add(new ExpressionAlternative("count == 1", "one"));
    expressionAlternatives.add(null);
    NullPointerException nullExpressionAlternative = assertThrows(NullPointerException.class,
        () -> new ExpressionTranslation("default", expressionAlternatives));
    assertTrue(nullExpressionAlternative.getMessage().contains("alternatives"));

    Map<LanguageForm, String> nullLanguageFormKey = new HashMap<>();
    nullLanguageFormKey.put(null, "other");
    NullPointerException nullFormKey = assertThrows(NullPointerException.class,
        () -> new LanguageFormTranslation("count", nullLanguageFormKey));
    assertTrue(nullFormKey.getMessage().contains("null key"));

    Map<LanguageForm, String> nullLanguageFormValue = new HashMap<>();
    nullLanguageFormValue.put(Cardinality.OTHER, null);
    NullPointerException nullFormValue = assertThrows(NullPointerException.class,
        () -> new LanguageFormTranslation("count", nullLanguageFormValue));
    assertTrue(nullFormValue.getMessage().contains("null value"));
  }

  @Test
  public void alternativesAreDefensivelyCopied() {
    List<LocalizedString> alternatives = new ArrayList<>();
    alternatives.add(new LocalizedString.Builder("count == 0").translation("none").build());
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("default")
        .alternatives(alternatives)
        .build();

    alternatives.clear();

    assertEquals(1, localizedString.getAlternatives().size());
    assertThrows(UnsupportedOperationException.class, () -> localizedString.getAlternatives().clear());
  }

  @Test
  public void missingTranslationAndAlternativesThrowsHelpfulException() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new LocalizedString.Builder("empty").build(),
        "Expected empty localized strings to fail with a validation exception");

    assertTrue(exception.getMessage().contains("either a translation or at least one alternative"),
        "Expected validation message to describe the missing translation/alternatives");
  }
}
