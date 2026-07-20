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
import org.jspecify.annotations.NonNull;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.lokalized.Diagnostics.format;
import static java.util.Objects.requireNonNull;

/**
 * Validates the public {@link LocalizedString} object model independently of how it was constructed.
 *
 * <p>The JSON loader performs additional source-shape validation while parsing. This validator enforces the common
 * semantic invariants so programmatically constructed and file-backed localized strings fail at construction time in the same places.
 */
@ThreadSafe
final class LocalizedStringValidator {
  private static final int MAXIMUM_ALTERNATIVE_DEPTH = 128;
  @NonNull
  private static final ExpressionEvaluator EXPRESSION_EVALUATOR = new ExpressionEvaluator(null, null,
      TranslationRuntimeLimits.hardCeilings());
  @NonNull
  private static final Set<@NonNull String> RESERVED_LANGUAGE_FORM_NAMES;

  static {
    List<@NonNull LanguageForm> languageForms = new ArrayList<>();
    Collections.addAll(languageForms, Cardinality.values());
    Collections.addAll(languageForms, Ordinality.values());
    Collections.addAll(languageForms, Gender.values());
    Collections.addAll(languageForms, GrammaticalCase.values());
    Collections.addAll(languageForms, Definiteness.values());
    Collections.addAll(languageForms, Classifier.values());
    Collections.addAll(languageForms, Formality.values());
    Collections.addAll(languageForms, Clusivity.values());
    Collections.addAll(languageForms, Animacy.values());
    Collections.addAll(languageForms, Phonetic.values());
    Map<@NonNull String, @NonNull LanguageForm> languageFormsByName = new HashMap<>();
    for (LanguageForm languageForm : languageForms) {
      String enumName = ((Enum<?>) languageForm).name();
      languageFormsByName.put(localizedStringNameFor(enumName, languageForm), languageForm);
    }
    RESERVED_LANGUAGE_FORM_NAMES = Collections.unmodifiableSet(new HashSet<>(languageFormsByName.keySet()));
  }

  private LocalizedStringValidator() {
    // Non-instantiable
  }

  @NonNull
  private static String localizedStringNameFor(@NonNull String enumName, @NonNull LanguageForm languageForm) {
    if (languageForm instanceof Cardinality)
      return LocalizedStringUtils.localizedStringNameForCardinalityName(enumName);
    if (languageForm instanceof Ordinality)
      return LocalizedStringUtils.localizedStringNameForOrdinalityName(enumName);
    if (languageForm instanceof Gender)
      return LocalizedStringUtils.localizedStringNameForGenderName(enumName);
    if (languageForm instanceof GrammaticalCase)
      return LocalizedStringUtils.localizedStringNameForGrammaticalCaseName(enumName);
    if (languageForm instanceof Definiteness)
      return LocalizedStringUtils.localizedStringNameForDefinitenessName(enumName);
    if (languageForm instanceof Classifier)
      return LocalizedStringUtils.localizedStringNameForClassifierName(enumName);
    if (languageForm instanceof Formality)
      return LocalizedStringUtils.localizedStringNameForFormalityName(enumName);
    if (languageForm instanceof Clusivity)
      return LocalizedStringUtils.localizedStringNameForClusivityName(enumName);
    if (languageForm instanceof Animacy)
      return LocalizedStringUtils.localizedStringNameForAnimacyName(enumName);
    if (languageForm instanceof Phonetic)
      return LocalizedStringUtils.localizedStringNameForPhoneticName(enumName);
    throw new IllegalArgumentException(format("Unsupported language form %s", languageForm));
  }

  static void validate(@NonNull Locale locale, @NonNull LocalizedString localizedString) {
    requireNonNull(locale);
    requireNonNull(localizedString);
    Map<@NonNull LocalizedString, @NonNull Integer> maximumValidatedDepth = new IdentityHashMap<>();
    Set<@NonNull LocalizedString> active = Collections.newSetFromMap(new IdentityHashMap<>());
    validate(locale, localizedString, localizedString.getKey(), false, 0, maximumValidatedDepth, active);
  }

  private static void validate(@NonNull Locale locale, @NonNull LocalizedString localizedString,
                               @NonNull String rootKey, boolean alternative, int depth,
                               @NonNull Map<@NonNull LocalizedString, @NonNull Integer> maximumValidatedDepth,
                               @NonNull Set<@NonNull LocalizedString> active) {
    requireNonNull(locale);
    requireNonNull(localizedString);
    requireNonNull(rootKey);
    requireNonNull(maximumValidatedDepth);
    requireNonNull(active);

    if (depth > MAXIMUM_ALTERNATIVE_DEPTH)
      throw invalid(locale, rootKey, format(
          "Alternative nesting exceeds the maximum depth of %d", MAXIMUM_ALTERNATIVE_DEPTH));

    if (active.contains(localizedString))
      throw invalid(locale, rootKey, "Alternative graph contains an identity cycle");

    Integer previousValidatedDepth = maximumValidatedDepth.get(localizedString);

    // A validation completed at an equal or deeper placement proves this subtree fits from the current placement.
    // A shallower cached placement cannot prove that a shared subtree still fits when reused deeper in a DAG.
    if (previousValidatedDepth != null && previousValidatedDepth >= depth)
      return;

    active.add(localizedString);

    boolean validationCompleted = false;

    try {
      validateCurrent(locale, localizedString, rootKey, alternative, depth, maximumValidatedDepth, active);
      validationCompleted = true;
    } finally {
      active.remove(localizedString);

      if (validationCompleted)
        maximumValidatedDepth.put(localizedString, depth);
    }
  }

  private static void validateCurrent(@NonNull Locale locale, @NonNull LocalizedString localizedString,
                                      @NonNull String rootKey, boolean alternative, int depth,
                                      @NonNull Map<@NonNull LocalizedString, @NonNull Integer> maximumValidatedDepth,
                                      @NonNull Set<@NonNull LocalizedString> active) {

    if (alternative) {
      try {
        EXPRESSION_EVALUATOR.parseAndValidateExpressionTokens(localizedString.getKey());
      } catch (ExpressionEvaluationException e) {
        throw invalid(locale, rootKey, format("Invalid alternative expression '%s': %s",
            localizedString.getKey(), e.getMessage()), e);
      }
    }

    localizedString.getTranslation().ifPresent(translation ->
        validateTemplate(locale, rootKey, "translation", translation));

    for (Map.Entry<@NonNull String, @NonNull PlaceholderDefinition> entry :
        localizedString.getPlaceholderDefinitions().entrySet()) {
      String placeholderName = entry.getKey();
      PlaceholderDefinition placeholderDefinition = entry.getValue();

      validateIdentifier(locale, rootKey, placeholderName, "generated placeholder");
      if (placeholderDefinition == null)
        throw invalid(locale, rootKey, format("Generated placeholder '%s' has a null placeholder definition", placeholderName));

      if (placeholderDefinition instanceof LanguageFormTranslation) {
        validateLanguageFormTranslation(locale, rootKey, placeholderName,
            (LanguageFormTranslation) placeholderDefinition);
      } else if (placeholderDefinition instanceof ExpressionTranslation) {
        validateExpressionTranslation(locale, rootKey, placeholderName,
            (ExpressionTranslation) placeholderDefinition);
      } else {
        throw invalid(locale, rootKey, format("Generated placeholder '%s' uses an unsupported definition type %s",
            placeholderName, placeholderDefinition.getClass().getName()));
      }
    }

    for (LocalizedString nestedAlternative : localizedString.getAlternatives()) {
      if (nestedAlternative == null)
        throw invalid(locale, rootKey, "Alternative lists may not contain null entries");
      validate(locale, nestedAlternative, rootKey, true, depth + 1, maximumValidatedDepth, active);
    }
  }

  private static void validateLanguageFormTranslation(@NonNull Locale locale, @NonNull String rootKey,
                                                      @NonNull String placeholderName,
                                                      @NonNull LanguageFormTranslation languageFormTranslation) {
    if (languageFormTranslation.getValue().isPresent())
      validateIdentifier(locale, rootKey, languageFormTranslation.getValue().get(),
          format("input for generated placeholder '%s'", placeholderName));
    else if (languageFormTranslation.getRange().isPresent()) {
      validateIdentifier(locale, rootKey, languageFormTranslation.getRange().get().getStart(),
          format("range start for generated placeholder '%s'", placeholderName));
      validateIdentifier(locale, rootKey, languageFormTranslation.getRange().get().getEnd(),
          format("range end for generated placeholder '%s'", placeholderName));
    } else {
      throw invalid(locale, rootKey, format(
          "Generated placeholder '%s' must define a value or range", placeholderName));
    }

    Map<@NonNull LanguageForm, @NonNull String> translations = languageFormTranslation.getTranslationsByLanguageForm();
    if (translations.isEmpty())
      throw invalid(locale, rootKey, format("Generated placeholder '%s' must define translations", placeholderName));

    Set<@NonNull Class<?>> languageFormTypes = new HashSet<>();
    for (Map.Entry<@NonNull LanguageForm, @NonNull String> entry : translations.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null)
        throw invalid(locale, rootKey, format("Generated placeholder '%s' contains a null language form or value", placeholderName));

      try {
        languageFormTypes.add(languageFormClassFor(entry.getKey()));
      } catch (IllegalArgumentException e) {
        throw invalid(locale, rootKey, format("Generated placeholder '%s' uses an unsupported language form %s",
            placeholderName, entry.getKey()), e);
      }

      validateTemplate(locale, rootKey, format("translation for generated placeholder '%s'", placeholderName), entry.getValue());
    }

    if (languageFormTypes.size() != 1)
      throw invalid(locale, rootKey, format("Generated placeholder '%s' may not mix language-form types", placeholderName));

    if (languageFormTranslation.getRange().isPresent() && !languageFormTypes.contains(Cardinality.class))
      throw invalid(locale, rootKey, format("Range-driven placeholder '%s' only supports cardinality", placeholderName));
  }

  private static void validateExpressionTranslation(@NonNull Locale locale, @NonNull String rootKey,
                                                    @NonNull String placeholderName,
                                                    @NonNull ExpressionTranslation expressionTranslation) {
    validateTemplate(locale, rootKey,
        format("default translation for generated placeholder '%s'", placeholderName),
        expressionTranslation.getTranslation());

    List<@NonNull ExpressionAlternative> alternatives = expressionTranslation.getAlternatives();

    for (int alternativeIndex = 0; alternativeIndex < alternatives.size(); ++alternativeIndex) {
      ExpressionAlternative alternative = alternatives.get(alternativeIndex);

      if (alternative == null)
        throw invalid(locale, rootKey, format(
            "Generated placeholder '%s' contains a null expression alternative at index %d",
            placeholderName, alternativeIndex));

      try {
        EXPRESSION_EVALUATOR.parseAndValidateExpressionTokens(alternative.getExpression());
      } catch (ExpressionEvaluationException e) {
        throw invalid(locale, rootKey, format(
            "Invalid expression alternative %d for generated placeholder '%s', expression '%s': %s",
            alternativeIndex, placeholderName, alternative.getExpression(), e.getMessage()), e);
      }

      validateTemplate(locale, rootKey, format(
          "translation for generated placeholder '%s' expression alternative %d ('%s')",
          placeholderName, alternativeIndex, alternative.getExpression()), alternative.getTranslation());
    }
  }

  @NonNull
  private static Class<? extends LanguageForm> languageFormClassFor(@NonNull LanguageForm languageForm) {
    requireNonNull(languageForm);

    if (languageForm instanceof Cardinality)
      return Cardinality.class;
    if (languageForm instanceof Ordinality)
      return Ordinality.class;
    if (languageForm instanceof Gender)
      return Gender.class;
    if (languageForm instanceof GrammaticalCase)
      return GrammaticalCase.class;
    if (languageForm instanceof Definiteness)
      return Definiteness.class;
    if (languageForm instanceof Classifier)
      return Classifier.class;
    if (languageForm instanceof Formality)
      return Formality.class;
    if (languageForm instanceof Clusivity)
      return Clusivity.class;
    if (languageForm instanceof Animacy)
      return Animacy.class;
    if (languageForm instanceof Phonetic)
      return Phonetic.class;

    throw new IllegalArgumentException(format("Unsupported language form %s", languageForm));
  }

  private static void validateTemplate(@NonNull Locale locale, @NonNull String rootKey,
                                       @NonNull String description, @NonNull String template) {
    Set<@NonNull String> placeholderNames;

    try {
      placeholderNames = StringInterpolator.placeholderNamesIn(template);
    } catch (IllegalArgumentException e) {
      throw invalid(locale, rootKey, format("Invalid placeholder reference in %s: %s", description, e.getMessage()), e);
    }

    for (String placeholderName : placeholderNames)
      validateIdentifier(locale, rootKey, placeholderName, description + " placeholder reference");
  }

  private static void validateIdentifier(@NonNull Locale locale, @NonNull String rootKey,
                                         @NonNull String identifier, @NonNull String description) {
    if (identifier == null || !LocalizedStringUtils.isValidLocalizedStringIdentifier(identifier))
      throw invalid(locale, rootKey, format("Invalid %s '%s'", description, identifier));
    if (RESERVED_LANGUAGE_FORM_NAMES.contains(identifier))
      throw invalid(locale, rootKey, format("Invalid %s '%s': language-form constants are reserved", description, identifier));
  }

  @NonNull
  private static IllegalArgumentException invalid(@NonNull Locale locale, @NonNull String key, @NonNull String message) {
    return invalid(locale, key, message, null);
  }

  @NonNull
  private static IllegalArgumentException invalid(@NonNull Locale locale, @NonNull String key, @NonNull String message,
                                                  Exception cause) {
    String contextualMessage = format("Invalid localized string '%s' for locale '%s': %s", key, locale.toLanguageTag(), message);
    return cause == null ? new IllegalArgumentException(contextualMessage) : new IllegalArgumentException(contextualMessage, cause);
  }
}
