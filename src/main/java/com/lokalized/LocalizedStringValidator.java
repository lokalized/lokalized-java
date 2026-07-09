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

import com.lokalized.LocalizedString.LanguageFormSelector;
import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslationRule;
import com.lokalized.LocalizedString.PlaceholderMetadata;
import org.jspecify.annotations.NonNull;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Validates the public {@link LocalizedString} object model independently of how it was constructed.
 *
 * <p>The JSON loader performs additional source-shape validation while parsing. This validator enforces the common
 * semantic invariants so programmatic catalogs and file-backed catalogs fail at construction time in the same places.
 */
@ThreadSafe
final class LocalizedStringValidator {
  private static final int MAXIMUM_ALTERNATIVE_DEPTH = 128;
  @NonNull
  private static final ExpressionEvaluator EXPRESSION_EVALUATOR = new ExpressionEvaluator();
  @NonNull
  private static final Set<@NonNull String> RESERVED_LANGUAGE_FORM_NAMES;
  @NonNull
  private static final Map<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> LANGUAGE_FORM_NAMES_BY_TYPE;

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

    Map<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> languageFormNamesByType = new HashMap<>();
    for (Map.Entry<@NonNull String, @NonNull LanguageForm> entry : languageFormsByName.entrySet())
      languageFormNamesByType.computeIfAbsent(LanguageFormType.forLanguageForm(entry.getValue()), ignored -> new HashSet<>())
          .add(entry.getKey());

    Map<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> immutableLanguageFormNamesByType = new HashMap<>();
    for (Map.Entry<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> entry : languageFormNamesByType.entrySet())
      immutableLanguageFormNamesByType.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
    LANGUAGE_FORM_NAMES_BY_TYPE = Collections.unmodifiableMap(immutableLanguageFormNamesByType);
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
    Set<@NonNull LocalizedString> validated = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<@NonNull LocalizedString> active = Collections.newSetFromMap(new IdentityHashMap<>());
    validate(locale, localizedString, localizedString.getKey(), false, 0, validated, active);
  }

  private static void validate(@NonNull Locale locale, @NonNull LocalizedString localizedString,
                               @NonNull String rootKey, boolean alternative, int depth,
                               @NonNull Set<@NonNull LocalizedString> validated,
                               @NonNull Set<@NonNull LocalizedString> active) {
    requireNonNull(locale);
    requireNonNull(localizedString);
    requireNonNull(rootKey);
    requireNonNull(validated);
    requireNonNull(active);

    if (depth > MAXIMUM_ALTERNATIVE_DEPTH)
      throw invalid(locale, rootKey, format(
          "Alternative nesting exceeds the maximum depth of %d", MAXIMUM_ALTERNATIVE_DEPTH));

    if (active.contains(localizedString))
      throw invalid(locale, rootKey, "Alternative graph contains an identity cycle");

    if (validated.contains(localizedString))
      return;

    active.add(localizedString);

    boolean validationCompleted = false;

    try {
      validateCurrent(locale, localizedString, rootKey, alternative, depth, validated, active);
      validationCompleted = true;
    } finally {
      active.remove(localizedString);

      if (validationCompleted)
        validated.add(localizedString);
    }
  }

  private static void validateCurrent(@NonNull Locale locale, @NonNull LocalizedString localizedString,
                                      @NonNull String rootKey, boolean alternative, int depth,
                                      @NonNull Set<@NonNull LocalizedString> validated,
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

    validateMetadata(locale, rootKey, localizedString.getPlaceholderMetadataByPlaceholder());

    for (Map.Entry<@NonNull String, @NonNull LanguageFormTranslation> entry :
        localizedString.getLanguageFormTranslationsByPlaceholder().entrySet()) {
      String placeholderName = entry.getKey();
      LanguageFormTranslation languageFormTranslation = entry.getValue();

      validateIdentifier(locale, rootKey, placeholderName, "generated placeholder");
      if (languageFormTranslation == null)
        throw invalid(locale, rootKey, format("Generated placeholder '%s' has a null translation definition", placeholderName));

      validateLanguageFormTranslation(locale, rootKey, placeholderName, languageFormTranslation);
    }

    for (LocalizedString nestedAlternative : localizedString.getAlternatives()) {
      if (nestedAlternative == null)
        throw invalid(locale, rootKey, "Alternative lists may not contain null entries");
      validate(locale, nestedAlternative, rootKey, true, depth + 1, validated, active);
    }
  }

  private static void validateMetadata(@NonNull Locale locale, @NonNull String rootKey,
                                       @NonNull Map<@NonNull String, @NonNull PlaceholderMetadata> metadataByPlaceholder) {
    for (Map.Entry<@NonNull String, @NonNull PlaceholderMetadata> entry : metadataByPlaceholder.entrySet()) {
      String placeholderName = entry.getKey();
      PlaceholderMetadata metadata = entry.getValue();
      validateIdentifier(locale, rootKey, placeholderName, "placeholder metadata name");

      if (metadata == null)
        throw invalid(locale, rootKey, format("Placeholder metadata for '%s' is null", placeholderName));

      for (String allowedValue : metadata.getAllowedValues())
        if (allowedValue == null)
          throw invalid(locale, rootKey, format("Placeholder metadata for '%s' contains a null allowed value", placeholderName));

      metadata.getType().ifPresent(typeName -> {
        LanguageFormType languageFormType = LanguageFormType.getLanguageFormTypesByName().get(typeName);
        if (languageFormType == null)
          return;

        Set<@NonNull String> allowedNames = LANGUAGE_FORM_NAMES_BY_TYPE.get(languageFormType);
        for (String allowedValue : metadata.getAllowedValues())
          if (!allowedNames.contains(allowedValue))
            throw invalid(locale, rootKey, format(
                "Placeholder metadata allowed value '%s' is invalid for type '%s' and placeholder '%s'",
                allowedValue, typeName, placeholderName));
      });
    }
  }

  private static void validateLanguageFormTranslation(@NonNull Locale locale, @NonNull String rootKey,
                                                      @NonNull String placeholderName,
                                                      @NonNull LanguageFormTranslation languageFormTranslation) {
    if (languageFormTranslation.isSelectorDriven()) {
      validateSelectorTranslation(locale, rootKey, placeholderName, languageFormTranslation);
      return;
    }

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
          "Generated placeholder '%s' must define a value, range, or selectors", placeholderName));
    }

    Map<@NonNull LanguageForm, @NonNull String> translations = languageFormTranslation.getTranslationsByLanguageForm();
    if (translations.isEmpty())
      throw invalid(locale, rootKey, format("Generated placeholder '%s' must define translations", placeholderName));

    Set<@NonNull LanguageFormType> languageFormTypes = new HashSet<>();
    for (Map.Entry<@NonNull LanguageForm, @NonNull String> entry : translations.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null)
        throw invalid(locale, rootKey, format("Generated placeholder '%s' contains a null language form or value", placeholderName));

      try {
        languageFormTypes.add(LanguageFormType.forLanguageForm(entry.getKey()));
      } catch (IllegalArgumentException e) {
        throw invalid(locale, rootKey, format("Generated placeholder '%s' uses an unsupported language form %s",
            placeholderName, entry.getKey()), e);
      }

      validateTemplate(locale, rootKey, format("translation for generated placeholder '%s'", placeholderName), entry.getValue());
    }

    if (languageFormTypes.size() != 1)
      throw invalid(locale, rootKey, format("Generated placeholder '%s' may not mix language-form types", placeholderName));

    if (languageFormTranslation.getRange().isPresent() && !languageFormTypes.contains(LanguageFormType.CARDINALITY))
      throw invalid(locale, rootKey, format("Range-driven placeholder '%s' only supports cardinality", placeholderName));
  }

  private static void validateSelectorTranslation(@NonNull Locale locale, @NonNull String rootKey,
                                                  @NonNull String placeholderName,
                                                  @NonNull LanguageFormTranslation languageFormTranslation) {
    List<@NonNull LanguageFormSelector> selectors = languageFormTranslation.getSelectors();
    List<@NonNull LanguageFormTranslationRule> rules = languageFormTranslation.getTranslationRules();
    if (selectors.isEmpty() || rules.isEmpty())
      throw invalid(locale, rootKey, format("Selector-driven placeholder '%s' requires selectors and rules", placeholderName));

    Set<@NonNull LanguageFormType> selectorTypes = new LinkedHashSet<>();
    Map<@NonNull String, @NonNull Set<@NonNull LanguageFormType>> selectorTypesByValue = new HashMap<>();
    for (LanguageFormSelector selector : selectors) {
      if (selector == null)
        throw invalid(locale, rootKey, format("Selector-driven placeholder '%s' contains a null selector", placeholderName));
      validateIdentifier(locale, rootKey, selector.getValue(), format("selector value for placeholder '%s'", placeholderName));
      if (!selectorTypes.add(selector.getForm()))
        throw invalid(locale, rootKey, format("Selector-driven placeholder '%s' contains duplicate selector type %s",
            placeholderName, selector.getForm()));

      Set<@NonNull LanguageFormType> typesForValue = selectorTypesByValue.computeIfAbsent(selector.getValue(),
          ignored -> new LinkedHashSet<>());
      typesForValue.add(selector.getForm());

      if (typesForValue.size() > 1 &&
          !Set.of(LanguageFormType.CARDINALITY, LanguageFormType.ORDINALITY).containsAll(typesForValue))
        throw invalid(locale, rootKey, format(
            "Selector-driven placeholder '%s' reuses input '%s' for incompatible selector types %s",
            placeholderName, selector.getValue(), typesForValue));
    }

    for (LanguageFormTranslationRule rule : rules) {
      if (rule == null)
        throw invalid(locale, rootKey, format("Selector-driven placeholder '%s' contains a null rule", placeholderName));
      validateTemplate(locale, rootKey, format("selector rule for placeholder '%s'", placeholderName), rule.getValue());

      for (Map.Entry<@NonNull LanguageFormType, @NonNull LanguageForm> condition :
          rule.getWhenByLanguageFormType().entrySet()) {
        LanguageFormType conditionType = condition.getKey();
        LanguageForm conditionValue = condition.getValue();
        if (conditionType == null || conditionValue == null)
          throw invalid(locale, rootKey, format("Selector rule for placeholder '%s' contains a null condition", placeholderName));
        if (!selectorTypes.contains(conditionType))
          throw invalid(locale, rootKey, format("Selector rule for placeholder '%s' uses unconfigured selector type %s",
              placeholderName, conditionType));
        if (!conditionType.getLanguageFormClass().isInstance(conditionValue))
          throw invalid(locale, rootKey, format("Selector rule for placeholder '%s' pairs selector type %s with %s",
              placeholderName, conditionType, conditionValue));
      }
    }

    for (int leftIndex = 0; leftIndex < rules.size(); ++leftIndex) {
      LanguageFormTranslationRule leftRule = rules.get(leftIndex);
      for (int rightIndex = leftIndex + 1; rightIndex < rules.size(); ++rightIndex) {
        LanguageFormTranslationRule rightRule = rules.get(rightIndex);
        if (leftRule.getWhenByLanguageFormType().size() == rightRule.getWhenByLanguageFormType().size() &&
            selectorRuleConditionsOverlap(leftRule.getWhenByLanguageFormType(), rightRule.getWhenByLanguageFormType()))
          throw invalid(locale, rootKey, format(
              "Selector rules for placeholder '%s' are ambiguous at equal specificity: %s and %s",
              placeholderName, leftRule, rightRule));
      }
    }
  }

  private static boolean selectorRuleConditionsOverlap(
      @NonNull Map<@NonNull LanguageFormType, @NonNull LanguageForm> leftConditions,
      @NonNull Map<@NonNull LanguageFormType, @NonNull LanguageForm> rightConditions) {
    for (Map.Entry<@NonNull LanguageFormType, @NonNull LanguageForm> leftCondition : leftConditions.entrySet()) {
      LanguageForm rightLanguageForm = rightConditions.get(leftCondition.getKey());
      if (rightLanguageForm != null && !rightLanguageForm.equals(leftCondition.getValue()))
        return false;
    }
    return true;
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
