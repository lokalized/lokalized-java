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

import com.lokalized.LocalizedString.LanguageFormTranslation;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.NotThreadSafe;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  public void immutableRuntimeModelTypesAreFinal() {
    assertTrue(Modifier.isFinal(LocalizedString.class.getModifiers()));
    assertTrue(Modifier.isFinal(LocalizedString.PlaceholderMetadata.class.getModifiers()));
    assertTrue(Modifier.isFinal(LocalizedString.LanguageFormTranslation.class.getModifiers()));
    assertTrue(Modifier.isFinal(LocalizedString.LanguageFormSelector.class.getModifiers()));
    assertTrue(Modifier.isFinal(LocalizedString.LanguageFormTranslationRule.class.getModifiers()));
    assertTrue(Modifier.isFinal(LocalizedString.LanguageFormTranslationRange.class.getModifiers()));
  }

  @Test
  public void selectorDrivenFlagUsesBoxedPublicApiConvention() throws NoSuchMethodException {
    assertTrue(LocalizedString.LanguageFormTranslation.class.getMethod("isSelectorDriven").getReturnType()
        .equals(Boolean.class));
  }

  @Test
  public void languageFormTranslationsAreDefensivelyCopied() {
    Map<LanguageForm, String> translationsByLanguageForm = new HashMap<>();
    translationsByLanguageForm.put(Cardinality.ONE, "book");

    Map<String, LanguageFormTranslation> translationsByPlaceholder = new HashMap<>();
    translationsByPlaceholder.put("books", new LanguageFormTranslation("bookCount", translationsByLanguageForm));

    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{books}}")
        .languageFormTranslationsByPlaceholder(translationsByPlaceholder)
        .build();

    translationsByPlaceholder.clear();

    assertTrue(localizedString.getLanguageFormTranslationsByPlaceholder().containsKey("books"));

    assertThrows(UnsupportedOperationException.class,
        () -> localizedString.getLanguageFormTranslationsByPlaceholder().put("other", null),
        "Expected language form translations map to be unmodifiable");
  }

  @Test
  public void placeholderMetadataIsDefensivelyCopied() {
    Map<String, LocalizedString.PlaceholderMetadata> placeholderMetadataByPlaceholder = new HashMap<>();
    placeholderMetadataByPlaceholder.put("lastName",
        new LocalizedString.PlaceholderMetadata("STRING", "Recipient family name without title.", "Weber", Set.of("Weber", "Nguyen")));

    LocalizedString localizedString = new LocalizedString.Builder("Hello {{lastName}}")
        .translation("Hello {{lastName}}")
        .placeholderMetadataByPlaceholder(placeholderMetadataByPlaceholder)
        .build();

    placeholderMetadataByPlaceholder.clear();

    assertTrue(localizedString.getPlaceholderMetadataByPlaceholder().containsKey("lastName"));

    assertThrows(UnsupportedOperationException.class,
        () -> localizedString.getPlaceholderMetadataByPlaceholder().put("other", null),
        "Expected placeholder metadata map to be unmodifiable");
    assertThrows(UnsupportedOperationException.class,
        () -> localizedString.getPlaceholderMetadataByPlaceholder().get("lastName").getAllowedValues().add("Singh"),
        "Expected placeholder metadata allowed values to be unmodifiable");
  }

  @Test
  public void selectorDrivenLanguageFormTranslationsAreDefensivelyCopied() {
    Map<LanguageFormType, LanguageForm> whenByLanguageFormType = new HashMap<>();
    whenByLanguageFormType.put(LanguageFormType.GENDER, Gender.MASCULINE);

    List<LocalizedString.LanguageFormSelector> selectors = List.of(
        new LocalizedString.LanguageFormSelector("gender", LanguageFormType.GENDER),
        new LocalizedString.LanguageFormSelector("grammaticalCase", LanguageFormType.CASE)
    );
    List<LocalizedString.LanguageFormTranslationRule> translationRules = List.of(
        new LocalizedString.LanguageFormTranslationRule(whenByLanguageFormType, "der"),
        new LocalizedString.LanguageFormTranslationRule("die")
    );

    Map<String, LanguageFormTranslation> translationsByPlaceholder = new HashMap<>();
    translationsByPlaceholder.put("article", new LanguageFormTranslation(selectors, translationRules));

    LocalizedString localizedString = new LocalizedString.Builder("{{article}} {{noun}}")
        .translation("{{article}} {{noun}}")
        .languageFormTranslationsByPlaceholder(translationsByPlaceholder)
        .build();

    whenByLanguageFormType.clear();
    translationsByPlaceholder.clear();

    LanguageFormTranslation languageFormTranslation = localizedString.getLanguageFormTranslationsByPlaceholder().get("article");

    assertTrue(languageFormTranslation.isSelectorDriven());
    assertTrue(languageFormTranslation.getSelectors().size() == 2);
    assertTrue(languageFormTranslation.getTranslationRules().size() == 2);

    assertThrows(UnsupportedOperationException.class,
        () -> languageFormTranslation.getSelectors().add(new LocalizedString.LanguageFormSelector("other", LanguageFormType.GENDER)),
        "Expected selector list to be unmodifiable");
    assertThrows(UnsupportedOperationException.class,
        () -> languageFormTranslation.getTranslationRules().add(new LocalizedString.LanguageFormTranslationRule("other")),
        "Expected translation rules list to be unmodifiable");
    assertThrows(UnsupportedOperationException.class,
        () -> languageFormTranslation.getTranslationRules().get(0).getWhenByLanguageFormType().put(LanguageFormType.CASE, GrammaticalCase.DATIVE),
        "Expected translation rule conditions map to be unmodifiable");
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
