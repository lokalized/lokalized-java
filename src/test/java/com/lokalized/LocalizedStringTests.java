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
import java.util.Map;

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
    assertTrue(Modifier.isFinal(LocalizedString.LanguageFormTranslation.class.getModifiers()));
    assertTrue(Modifier.isFinal(LocalizedString.LanguageFormTranslationRange.class.getModifiers()));
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

    translationsByLanguageForm.clear();
    translationsByPlaceholder.clear();

    assertTrue(localizedString.getLanguageFormTranslationsByPlaceholder().containsKey("books"));
    LanguageFormTranslation books = localizedString.getLanguageFormTranslationsByPlaceholder().get("books");
    assertTrue(books.getTranslationsByLanguageForm().containsKey(Cardinality.ONE));

    assertThrows(UnsupportedOperationException.class,
        () -> localizedString.getLanguageFormTranslationsByPlaceholder().put("other", null),
        "Expected language form translations map to be unmodifiable");
    assertThrows(UnsupportedOperationException.class,
        () -> books.getTranslationsByLanguageForm().put(Cardinality.OTHER, "books"),
        "Expected each language form map to be unmodifiable");
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
