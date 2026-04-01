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

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * High-level categories of language forms used to drive placeholder agreement.
 * <p>
 * These values correspond to the JSON {@code form} names used by selector-based placeholder translations, for example
 * {@code GENDER} or {@code CASE}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public enum LanguageFormType {
  /**
   * Cardinality/plural form.
   */
  CARDINALITY(Cardinality.class),
  /**
   * Ordinality/ordinal-number form.
   */
  ORDINALITY(Ordinality.class),
  /**
   * Grammatical gender form.
   */
  GENDER(Gender.class),
  /**
   * Grammatical case form.
   */
  CASE(GrammaticalCase.class),
  /**
   * Definiteness form.
   */
  DEFINITENESS(Definiteness.class),
  /**
   * Classifier or counter-word form.
   */
  CLASSIFIER(Classifier.class),
  /**
   * Speech-level or formality form.
   */
  FORMALITY(Formality.class),
  /**
   * Clusivity form.
   */
  CLUSIVITY(Clusivity.class),
  /**
   * Animacy form.
   */
  ANIMACY(Animacy.class),
  /**
   * Phonetic form.
   */
  PHONETIC(Phonetic.class);

  @NonNull
  private static final Map<@NonNull String, @NonNull LanguageFormType> LANGUAGE_FORM_TYPES_BY_NAME;
  @NonNull
  private final Class<? extends LanguageForm> languageFormClass;

  static {
    LANGUAGE_FORM_TYPES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(LanguageFormType.values())
        .collect(Collectors.toMap(languageFormType -> languageFormType.name(), languageFormType -> languageFormType)));
  }

  LanguageFormType(@NonNull Class<? extends LanguageForm> languageFormClass) {
    requireNonNull(languageFormClass);
    this.languageFormClass = languageFormClass;
  }

  /**
   * Gets the concrete {@link LanguageForm} class represented by this type.
   *
   * @return the concrete {@link LanguageForm} class represented by this type, not null
   */
  @NonNull
  public Class<? extends LanguageForm> getLanguageFormClass() {
    return languageFormClass;
  }

  /**
   * Gets the mapping of selector-form names to selector-form values.
   *
   * @return the mapping of selector-form names to selector-form values, not null
   */
  @NonNull
  static Map<@NonNull String, @NonNull LanguageFormType> getLanguageFormTypesByName() {
    return LANGUAGE_FORM_TYPES_BY_NAME;
  }

  /**
   * Determines the selector-form type for a concrete language-form value.
   *
   * @param languageForm the language-form value for which to determine a type, not null
   * @return the selector-form type for the supplied language-form value, not null
   * @throws IllegalArgumentException if the language form is not recognized
   */
  @NonNull
  public static LanguageFormType forLanguageForm(@NonNull LanguageForm languageForm) {
    requireNonNull(languageForm);

    for (LanguageFormType languageFormType : LanguageFormType.values()) {
      if (languageFormType.getLanguageFormClass().isInstance(languageForm))
        return languageFormType;
    }

    throw new IllegalArgumentException(format("Encountered unrecognized language form %s", languageForm));
  }
}
