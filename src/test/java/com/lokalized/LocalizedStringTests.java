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
  public void expressionValueTypesRejectInvalidConstructorArguments() {
    assertThrows(NullPointerException.class, () -> new ExpressionTranslation(null));
    assertThrows(NullPointerException.class, () -> new ExpressionTranslation("default", null));
    assertThrows(IllegalArgumentException.class, () -> new ExpressionTranslation("default", List.of()));
    assertThrows(NullPointerException.class, () -> new ExpressionAlternative(null, "translation"));
    assertThrows(NullPointerException.class, () -> new ExpressionAlternative("count == 0", null));
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
