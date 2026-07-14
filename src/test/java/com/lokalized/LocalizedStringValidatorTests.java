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
import com.lokalized.LocalizedString.LanguageFormTranslationRange;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalizedStringValidatorTests {
  private static final Locale ENGLISH = Locale.forLanguageTag("en");

  @Test
  public void ordinaryEnumNameIsNotReservedPlaceholderName() {
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{ONE}}")
        .build();

    assertDoesNotThrow(() -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void expressionConstantIsReservedPlaceholderName() {
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{word}}")
        .placeholderDefinitions(Map.of(
            "CARDINALITY_ONE", new LanguageFormTranslation("count", Map.of(Cardinality.ONE, "word"))))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void invalidProgrammaticAlternativeExpressionIsRejected() {
    LocalizedString alternative = new LocalizedString.Builder("count ==")
        .translation("invalid")
        .build();
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("default")
        .alternatives(List.of(alternative))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void unsafeNumericLiteralIsRejectedDuringLocalizedStringsValidation() {
    LocalizedString alternative = new LocalizedString.Builder(
        "count == 1e" + (PluralOperands.MAXIMUM_ABSOLUTE_NUMBER_SCALE + 1))
        .translation("invalid")
        .build();
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("default")
        .alternatives(List.of(alternative))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void validProgrammaticExpressionDefinitionsAreAccepted() {
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("Found {{resultSummary}} {{timing}}.")
        .placeholderDefinitions(Map.of(
            "resultSummary", new ExpressionTranslation("{{formattedResultCount}} results", List.of(
                new ExpressionAlternative("resultCount == 0", "no results"),
                new ExpressionAlternative("resultCount >= resultLimit", "at least {{formattedResultLimit}} results"))),
            "timing", new ExpressionTranslation("in {{formattedDuration}}")))
        .build();

    assertDoesNotThrow(() -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void invalidProgrammaticFragmentExpressionIsRejectedWithContext() {
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .placeholderDefinitions(Map.of(
            "summary", new ExpressionTranslation("default", List.of(
                new ExpressionAlternative("count ==", "invalid")))))
        .build();

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));

    assertTrue(exception.getMessage().contains("expression alternative 0"));
    assertTrue(exception.getMessage().contains("generated placeholder 'summary'"));
    assertTrue(exception.getMessage().contains("count =="));
  }

  @Test
  public void unsafeNumericLiteralInFragmentIsRejectedDuringLocalizedStringsValidation() {
    String expression = "count == 1e" + (PluralOperands.MAXIMUM_ABSOLUTE_NUMBER_SCALE + 1);
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .placeholderDefinitions(Map.of(
            "summary", new ExpressionTranslation("default", List.of(
                new ExpressionAlternative(expression, "invalid")))))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void malformedExpressionDefaultTemplateIsRejected() {
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .placeholderDefinitions(Map.of(
            "summary", new ExpressionTranslation("{{malformed placeholder}}")))
        .build();

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));

    assertTrue(exception.getMessage().contains("default translation for generated placeholder 'summary'"));
  }

  @Test
  public void malformedExpressionAlternativeTemplateIsRejected() {
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .placeholderDefinitions(Map.of(
            "summary", new ExpressionTranslation("default", List.of(
                new ExpressionAlternative("count == 0", "{{malformed placeholder}}")))))
        .build();

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));

    assertTrue(exception.getMessage().contains("expression alternative 0"));
    assertTrue(exception.getMessage().contains("generated placeholder 'summary'"));
  }

  @Test
  public void reservedLanguageFormReferenceInExpressionFragmentIsRejected() {
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .placeholderDefinitions(Map.of(
            "summary", new ExpressionTranslation("{{CARDINALITY_ONE}}")))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void nullExpressionAlternativeIsRejected() {
    ExpressionTranslation expressionTranslation = new ExpressionTranslation(
        "default", Collections.singletonList(null));
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{summary}}")
        .placeholderDefinitions(Map.of("summary", expressionTranslation))
        .build();

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));

    assertTrue(exception.getMessage().contains("null expression alternative at index 0"));
  }

  @Test
  public void mixedSimpleLanguageFormMapIsRejected() {
    Map<LanguageForm, String> translations = new LinkedHashMap<>();
    translations.put(Cardinality.ONE, "one");
    translations.put(Gender.MASCULINE, "masculine");
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{value}}")
        .placeholderDefinitions(Map.of(
            "value", new LanguageFormTranslation("input", translations)))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void rangesOnlySupportCardinality() {
    LanguageFormTranslation translation = new LanguageFormTranslation(
        new LanguageFormTranslationRange("start", "end"), Map.of(Gender.MASCULINE, "value"));
    LocalizedString localizedString = new LocalizedString.Builder("key")
        .translation("{{result}}")
        .placeholderDefinitions(Map.of("result", translation))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, localizedString));
  }

  @Test
  public void deeplyNestedProgrammaticAlternativesAreRejectedBeforeStackOverflow() {
    LocalizedString alternative = new LocalizedString.Builder("count == 1")
        .translation("value")
        .build();

    for (int depth = 0; depth < 130; ++depth)
      alternative = new LocalizedString.Builder("count == 1")
          .translation("value")
          .alternatives(List.of(alternative))
          .build();

    LocalizedString root = new LocalizedString.Builder("root")
        .translation("value")
        .alternatives(List.of(alternative))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, root));
  }

  @Test
  public void memoizedAlternativeCannotBypassTheDepthLimitOnAnotherPath() {
    LocalizedString sharedGrandchild = new LocalizedString.Builder("count == 1")
        .translation("grandchild")
        .build();
    LocalizedString sharedChild = new LocalizedString.Builder("count == 1")
        .translation("child")
        .alternatives(List.of(sharedGrandchild))
        .build();
    LocalizedString shared = new LocalizedString.Builder("count == 1")
        .translation("shared")
        .alternatives(List.of(sharedChild))
        .build();
    LocalizedString deepPath = shared;

    // The shared root itself still enters at depth 127, but its grandchild would enter at depth 129.
    for (int depth = 0; depth < 126; ++depth)
      deepPath = new LocalizedString.Builder("count == 1")
          .translation("value")
          .alternatives(List.of(deepPath))
          .build();

    LocalizedString root = new LocalizedString.Builder("root")
        .translation("value")
        .alternatives(List.of(shared, deepPath))
        .build();

    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringValidator.validate(ENGLISH, root));
  }
}
