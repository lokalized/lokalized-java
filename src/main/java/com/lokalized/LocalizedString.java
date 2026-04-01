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
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Represents a single localized string - its key, translated value, and any associated translation rules.
 * <p>
 * Normally instances are sourced from a file which contains all localized strings for a given locale.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
public class LocalizedString {
  @NonNull
  private final String key;
  @Nullable
  private final String translation;
  @Nullable
  private final String commentary;
  @Nullable
  private final List<@NonNull Token> expressionTokens;
  @NonNull
  private final Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder;
  @NonNull
  private final List<@NonNull LocalizedString> alternatives;

  /**
   * Constructs a localized string with a key, default translation, and additional translation rules.
   *
   * @param key                                   this string's translation key, not null
   * @param translation                           this string's default translation, may be null
   * @param commentary                            this string's commentary (usage/translation notes), may be null
   * @param languageFormTranslationsByPlaceholder per-language-form translations that correspond to a placeholder value, may be null
   * @param alternatives                          alternative expression-driven translations for this string, may be null
   */
  protected LocalizedString(@NonNull String key, @Nullable String translation, @Nullable String commentary,
                            @Nullable Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder,
                            @Nullable List<@NonNull LocalizedString> alternatives,
                            @Nullable List<@NonNull Token> expressionTokens) {
    requireNonNull(key);

    this.key = key;
    this.translation = translation;
    this.commentary = commentary;
    this.expressionTokens = expressionTokens == null ? null : Collections.unmodifiableList(new ArrayList<>(expressionTokens));

    if (languageFormTranslationsByPlaceholder == null) {
      this.languageFormTranslationsByPlaceholder = Collections.emptyMap();
    } else {
      // Defensive copy to unmodifiable map
      this.languageFormTranslationsByPlaceholder = Collections.unmodifiableMap(new LinkedHashMap<>(languageFormTranslationsByPlaceholder));
    }

    // Defensive copy to unmodifiable list
    this.alternatives = alternatives == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(alternatives));

    if (translation == null && alternatives.size() == 0)
      throw new IllegalArgumentException(format("You must provide either a translation or at least one alternative expression. " +
          "Offending key was '%s'", key));
  }

  /**
   * Generates a {@code String} representation of this object.
   *
   * @return a string representation of this object, not null
   */
  @Override
  @NonNull
  public String toString() {
    List<@NonNull String> components = new ArrayList<>(5);

    components.add(format("key=%s", getKey()));

    if (getTranslation().isPresent())
      components.add(format("translation=%s", getTranslation().get()));

    if (getCommentary().isPresent())
      components.add(format("commentary=%s", getCommentary().get()));

    if (getLanguageFormTranslationsByPlaceholder().size() > 0)
      components.add(format("languageFormTranslationsByPlaceholder=%s", getLanguageFormTranslationsByPlaceholder()));

    if (getAlternatives().size() > 0)
      components.add(format("alternatives=%s", getAlternatives()));

    return format("%s{%s}", getClass().getSimpleName(), components.stream().collect(Collectors.joining(", ")));
  }

  /**
   * Checks if this object is equal to another one.
   *
   * @param other the object to check, null returns false
   * @return true if this is equal to the other object, false otherwise
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other)
      return true;

    if (other == null || !getClass().equals(other.getClass()))
      return false;

    LocalizedString localizedString = (LocalizedString) other;

    return Objects.equals(getKey(), localizedString.getKey())
        && Objects.equals(getTranslation(), localizedString.getTranslation())
        && Objects.equals(getCommentary(), localizedString.getCommentary())
        && Objects.equals(getLanguageFormTranslationsByPlaceholder(), localizedString.getLanguageFormTranslationsByPlaceholder())
        && Objects.equals(getAlternatives(), localizedString.getAlternatives());
  }

  /**
   * A hash code for this object.
   *
   * @return a suitable hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(getKey(), getTranslation(), getCommentary(), getLanguageFormTranslationsByPlaceholder(), getAlternatives());
  }

  /**
   * Gets this string's translation key.
   *
   * @return this string's translation key, not null
   */
  @NonNull
  public String getKey() {
    return key;
  }

  /**
   * Gets this string's default translation, if available.
   *
   * @return this string's default translation, not null
   */
  @NonNull
  public Optional<String> getTranslation() {
    return Optional.ofNullable(translation);
  }

  /**
   * Gets this string's commentary (usage/translation notes).
   *
   * @return this string's commentary, not null
   */
  @NonNull
  public Optional<String> getCommentary() {
    return Optional.ofNullable(commentary);
  }

  /**
   * Gets per-language-form translations that correspond to a placeholder value.
   * <p>
   * For example, language form {@code GENDER_MASCULINE} might be translated as {@code He} for placeholder {@code subject},
   * or a selector-based placeholder might use both {@code GENDER} and {@code CASE} to choose a single translation rule.
   *
   * @return per-language-form translations that correspond to a placeholder value, not null
   */
  @NonNull
  public Map<@NonNull String, @NonNull LanguageFormTranslation> getLanguageFormTranslationsByPlaceholder() {
    return languageFormTranslationsByPlaceholder;
  }

  /**
   * Gets alternative expression-driven translations for this string.
   * <p>
   * In this context, the {@code key} for each alternative is a localization expression, not a translation key.
   * <p>
   * For example, if {@code bookCount == 0} you might want to say {@code I haven't read any books} instead of {@code I read 0 books}.
   *
   * @return alternative expression-driven translations for this string, not null
   */
  @NonNull
  public List<@NonNull LocalizedString> getAlternatives() {
    return alternatives;
  }

  @Nullable
  List<@NonNull Token> getExpressionTokens() {
    return expressionTokens;
  }


  /**
   * Builder used to construct instances of {@link LocalizedString}.
   * <p>
   * This class is intended for use by a single thread.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @NotThreadSafe
  public static class Builder {
    @NonNull
    private final String key;
    @Nullable
    private String translation;
    @Nullable
    private String commentary;
    @Nullable
    private Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder;
    @Nullable
    private List<@NonNull LocalizedString> alternatives;
    @Nullable
    private List<@NonNull Token> expressionTokens;

    /**
     * Constructs a localized string builder with the given key.
     *
     * @param key this string's translation key, not null
     */
    public Builder(@NonNull String key) {
      requireNonNull(key);
      this.key = key;
    }

    /**
     * Applies a default translation to this builder.
     *
     * @param translation a default translation, may be null
     * @return this builder instance, useful for chaining. not null
     */
    @NonNull
    public Builder translation(@Nullable String translation) {
      this.translation = translation;
      return this;
    }

    /**
     * Applies commentary (usage/translation notes) to this builder.
     *
     * @param commentary commentary (usage/translation notes), may be null
     * @return this builder instance, useful for chaining. not null
     */
    @NonNull
    public Builder commentary(@Nullable String commentary) {
      this.commentary = commentary;
      return this;
    }

    /**
     * Applies per-language-form translations to this builder.
     *
     * @param languageFormTranslationsByPlaceholder per-language-form or selector-driven translations, may be null
     * @return this builder instance, useful for chaining. not null
     */
    @NonNull
    public Builder languageFormTranslationsByPlaceholder(
        @Nullable Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder) {
      this.languageFormTranslationsByPlaceholder = languageFormTranslationsByPlaceholder;
      return this;
    }

    /**
     * Applies alternative expression-driven translations to this builder.
     *
     * @param alternatives alternative expression-driven translations, may be null
     * @return this builder instance, useful for chaining. not null
     */
    @NonNull
    public Builder alternatives(@Nullable List<@NonNull LocalizedString> alternatives) {
      this.alternatives = alternatives;
      return this;
    }

    @NonNull
    Builder expressionTokens(@Nullable List<@NonNull Token> expressionTokens) {
      this.expressionTokens = expressionTokens;
      return this;
    }

    /**
     * Constructs an instance of {@link LocalizedString}.
     *
     * @return an instance of {@link LocalizedString}, not null
     */
    @NonNull
    public LocalizedString build() {
      return new LocalizedString(key, translation, commentary, languageFormTranslationsByPlaceholder, alternatives, expressionTokens);
    }
  }

  /**
   * Container for per-language-form (gender, case, definiteness, classifier, formality, clusivity, animacy,
   * cardinal, ordinal, phonetic) translation information.
   * <p>
   * Translations can be keyed either on a single value, a range of values (start and end) in the case of cardinality ranges,
   * or a selector-based rule set for multi-axis agreement.
   * <p>
   * Exactly one translation mode is active for each instance.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static class LanguageFormTranslation {
    @Nullable
    private final String value;
    @Nullable
    private final LanguageFormTranslationRange range;
    @NonNull
    private final Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm;
    @NonNull
    private final List<@NonNull LanguageFormSelector> selectors;
    @NonNull
    private final List<@NonNull LanguageFormTranslationRule> translationRules;

    /**
     * Constructs a per-language-form translation set with the given placeholder value and mapping of translations by language form.
     *
     * @param value                      the placeholder value to compare against for translation, not null
     * @param translationsByLanguageForm the possible translations keyed by language form, not null
     */
    public LanguageFormTranslation(@NonNull String value, @NonNull Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm) {
      requireNonNull(value);
      requireNonNull(translationsByLanguageForm);

      this.value = value;
      this.range = null;
      this.translationsByLanguageForm = Collections.unmodifiableMap(new LinkedHashMap<>(translationsByLanguageForm));
      this.selectors = Collections.emptyList();
      this.translationRules = Collections.emptyList();
    }

    /**
     * Constructs a per-language-form translation set with the given placeholder range and mapping of translations by language form.
     *
     * @param range                      the placeholder range to compare against for translation, not null
     * @param translationsByLanguageForm the possible translations keyed by language form, not null
     */
    public LanguageFormTranslation(@NonNull LanguageFormTranslationRange range, @NonNull Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm) {
      requireNonNull(range);
      requireNonNull(translationsByLanguageForm);

      this.value = null;
      this.range = range;
      this.translationsByLanguageForm = Collections.unmodifiableMap(new LinkedHashMap<>(translationsByLanguageForm));
      this.selectors = Collections.emptyList();
      this.translationRules = Collections.emptyList();
    }

    /**
     * Constructs a selector-based translation set with multiple agreement dimensions and ordered translation rules.
     *
     * @param selectors        the selector definitions that determine which language forms to evaluate, not null
     * @param translationRules the translation rules to evaluate against the selector values, not null
     */
    public LanguageFormTranslation(@NonNull List<@NonNull LanguageFormSelector> selectors,
                                   @NonNull List<@NonNull LanguageFormTranslationRule> translationRules) {
      requireNonNull(selectors);
      requireNonNull(translationRules);

      if (selectors.isEmpty())
        throw new IllegalArgumentException(format("Selector-based %s instances require at least one selector",
            getClass().getSimpleName()));

      if (translationRules.isEmpty())
        throw new IllegalArgumentException(format("Selector-based %s instances require at least one translation rule",
            getClass().getSimpleName()));

      this.value = null;
      this.range = null;
      this.translationsByLanguageForm = Collections.emptyMap();
      this.selectors = Collections.unmodifiableList(new ArrayList<>(selectors));
      this.translationRules = Collections.unmodifiableList(new ArrayList<>(translationRules));
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @NonNull
    public String toString() {
      if (isSelectorDriven())
        return format("%s{selectors=%s, translationRules=%s}", getClass().getSimpleName(), getSelectors(), getTranslationRules());

      if (getRange().isPresent())
        return format("%s{range=%s, translationsByLanguageForm=%s}", getClass().getSimpleName(), getRange().get(), getTranslationsByLanguageForm());

      return format("%s{value=%s, translationsByLanguageForm=%s}", getClass().getSimpleName(), getValue().get(), getTranslationsByLanguageForm());
    }

    /**
     * Checks if this object is equal to another one.
     *
     * @param other the object to check, null returns false
     * @return true if this is equal to the other object, false otherwise
     */
    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other)
        return true;

      if (other == null || !getClass().equals(other.getClass()))
        return false;

      LanguageFormTranslation languageFormTranslation = (LanguageFormTranslation) other;

      return Objects.equals(getValue(), languageFormTranslation.getValue())
          && Objects.equals(getRange(), languageFormTranslation.getRange())
          && Objects.equals(getTranslationsByLanguageForm(), languageFormTranslation.getTranslationsByLanguageForm())
          && Objects.equals(getSelectors(), languageFormTranslation.getSelectors())
          && Objects.equals(getTranslationRules(), languageFormTranslation.getTranslationRules());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getValue(), getRange(), getTranslationsByLanguageForm(), getSelectors(), getTranslationRules());
    }

    /**
     * Gets the value for this per-language-form translation set.
     *
     * @return the value for this per-language-form translation set, not null
     */
    @NonNull
    public Optional<String> getValue() {
      return Optional.ofNullable(value);
    }

    /**
     * Gets the range for this per-language-form translation set.
     *
     * @return the range for this per-language-form translation set, not null
     */
    @NonNull
    public Optional<LanguageFormTranslationRange> getRange() {
      return Optional.ofNullable(range);
    }

    /**
     * Indicates whether this translation uses selector-based multi-axis agreement.
     *
     * @return true if this translation is selector-driven, false otherwise
     */
    public boolean isSelectorDriven() {
      return getSelectors().size() > 0;
    }

    /**
     * Gets the translations by language form for this per-language-form translation set.
     *
     * @return the translations by language form for this per-language-form translation set, not null
     */
    @NonNull
    public Map<@NonNull LanguageForm, @NonNull String> getTranslationsByLanguageForm() {
      return translationsByLanguageForm;
    }

    /**
     * Gets the selector definitions for this translation set.
     *
     * @return the selector definitions for this translation set, not null
     */
    @NonNull
    public List<@NonNull LanguageFormSelector> getSelectors() {
      return selectors;
    }

    /**
     * Gets the ordered translation rules for this translation set.
     *
     * @return the ordered translation rules for this translation set, not null
     */
    @NonNull
    public List<@NonNull LanguageFormTranslationRule> getTranslationRules() {
      return translationRules;
    }
  }

  /**
   * Defines a selector used by a multi-axis placeholder translation.
   * <p>
   * Each selector identifies an application-supplied placeholder value and the language-form family to derive from it.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static class LanguageFormSelector {
    @NonNull
    private final String value;
    @NonNull
    private final LanguageFormType form;

    /**
     * Constructs a selector with the given placeholder value name and language-form type.
     *
     * @param value the placeholder value to inspect, not null
     * @param form  the language-form family to derive from the value, not null
     */
    public LanguageFormSelector(@NonNull String value, @NonNull LanguageFormType form) {
      requireNonNull(value);
      requireNonNull(form);

      this.value = value;
      this.form = form;
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @NonNull
    public String toString() {
      return format("%s{value=%s, form=%s}", getClass().getSimpleName(), getValue(), getForm());
    }

    /**
     * Checks if this object is equal to another one.
     *
     * @param other the object to check, null returns false
     * @return true if this is equal to the other object, false otherwise
     */
    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other)
        return true;

      if (other == null || !getClass().equals(other.getClass()))
        return false;

      LanguageFormSelector languageFormSelector = (LanguageFormSelector) other;

      return Objects.equals(getValue(), languageFormSelector.getValue())
          && Objects.equals(getForm(), languageFormSelector.getForm());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getValue(), getForm());
    }

    /**
     * Gets the placeholder value name for this selector.
     *
     * @return the placeholder value name for this selector, not null
     */
    @NonNull
    public String getValue() {
      return value;
    }

    /**
     * Gets the language-form family for this selector.
     *
     * @return the language-form family for this selector, not null
     */
    @NonNull
    public LanguageFormType getForm() {
      return form;
    }
  }

  /**
   * Defines a selector-based translation rule for a multi-axis placeholder translation.
   * <p>
   * Rules may optionally provide a {@code when} map. An empty map represents the default rule.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static class LanguageFormTranslationRule {
    @NonNull
    private final Map<@NonNull LanguageFormType, @NonNull LanguageForm> whenByLanguageFormType;
    @NonNull
    private final String value;

    /**
     * Constructs a default translation rule with no conditions.
     *
     * @param value the value to use when this rule matches, not null
     */
    public LanguageFormTranslationRule(@NonNull String value) {
      this(Collections.emptyMap(), value);
    }

    /**
     * Constructs a translation rule with the given selector conditions and value.
     *
     * @param whenByLanguageFormType selector conditions that must be satisfied for this rule to match, not null
     * @param value                  the value to use when this rule matches, not null
     */
    public LanguageFormTranslationRule(@NonNull Map<@NonNull LanguageFormType, @NonNull LanguageForm> whenByLanguageFormType,
                                       @NonNull String value) {
      requireNonNull(whenByLanguageFormType);
      requireNonNull(value);

      this.whenByLanguageFormType = Collections.unmodifiableMap(new LinkedHashMap<>(whenByLanguageFormType));
      this.value = value;
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @NonNull
    public String toString() {
      return format("%s{whenByLanguageFormType=%s, value=%s}", getClass().getSimpleName(), getWhenByLanguageFormType(), getValue());
    }

    /**
     * Checks if this object is equal to another one.
     *
     * @param other the object to check, null returns false
     * @return true if this is equal to the other object, false otherwise
     */
    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other)
        return true;

      if (other == null || !getClass().equals(other.getClass()))
        return false;

      LanguageFormTranslationRule languageFormTranslationRule = (LanguageFormTranslationRule) other;

      return Objects.equals(getWhenByLanguageFormType(), languageFormTranslationRule.getWhenByLanguageFormType())
          && Objects.equals(getValue(), languageFormTranslationRule.getValue());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getWhenByLanguageFormType(), getValue());
    }

    /**
     * Gets the selector conditions for this rule.
     *
     * @return the selector conditions for this rule, not null
     */
    @NonNull
    public Map<@NonNull LanguageFormType, @NonNull LanguageForm> getWhenByLanguageFormType() {
      return whenByLanguageFormType;
    }

    /**
     * Gets the value for this rule.
     *
     * @return the value for this rule, not null
     */
    @NonNull
    public String getValue() {
      return value;
    }
  }

  /**
   * Container for per-language-form cardinality translation information over a range (start, end) of values.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static class LanguageFormTranslationRange {
    @NonNull
    private final String start;
    @NonNull
    private final String end;

    /**
     * Constructs a translation range with the given start and end values.
     *
     * @param start the start value of the range, not null
     * @param end   the end value of the range, not null
     */
    public LanguageFormTranslationRange(@NonNull String start, @NonNull String end) {
      requireNonNull(start);
      requireNonNull(end);

      this.start = start;
      this.end = end;
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @NonNull
    public String toString() {
      return format("%s{start=%s, end=%s}", getClass().getSimpleName(), getStart(), getEnd());
    }

    /**
     * Checks if this object is equal to another one.
     *
     * @param other the object to check, null returns false
     * @return true if this is equal to the other object, false otherwise
     */
    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other)
        return true;

      if (other == null || !getClass().equals(other.getClass()))
        return false;

      LanguageFormTranslationRange languageFormTranslationRange = (LanguageFormTranslationRange) other;

      return Objects.equals(getStart(), languageFormTranslationRange.getStart())
          && Objects.equals(getEnd(), languageFormTranslationRange.getEnd());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getStart(), getEnd());
    }

    /**
     * The start value for this range.
     *
     * @return the start value for this range, not null
     */
    @NonNull
    public String getStart() {
      return start;
    }

    /**
     * The end value for this range.
     *
     * @return the end value for this range, not null
     */
    @NonNull
    public String getEnd() {
      return end;
    }
  }
}
