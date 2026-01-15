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
}
