/*
 * Copyright 2017 Product Mog LLC.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
  @Nonnull
  private final String key;
  @Nullable
  private final String translation;
  @Nullable
  private final String commentary;
  @Nonnull
  private final Map<String, LanguageFormTranslation> languageFormTranslationsByPlaceholder;
  @Nonnull
  private final List<LocalizedString> alternatives;

  /**
   * Constructs a localized string with a key, default translation, and additional translation rules.
   *
   * @param key                                   this string's translation key, not null
   * @param translation                           this string's default translation, may be null
   * @param commentary                            this string's commentary (usage/translation notes), may be null
   * @param languageFormTranslationsByPlaceholder per-language-form translations that correspond to a placeholder value, may be null
   * @param alternatives                          alternative expression-driven translations for this string, may be null
   */
  protected LocalizedString(@Nonnull String key, @Nullable String translation, @Nullable String commentary,
                            @Nullable Map<String, LanguageFormTranslation> languageFormTranslationsByPlaceholder,
                            @Nullable List<LocalizedString> alternatives) {
    requireNonNull(key);

    this.key = key;
    this.translation = translation;
    this.commentary = commentary;

    if (languageFormTranslationsByPlaceholder == null) {
      this.languageFormTranslationsByPlaceholder = Collections.emptyMap();
    } else {
      // Defensive copy to unmodifiable map
      this.languageFormTranslationsByPlaceholder = Collections.unmodifiableMap(languageFormTranslationsByPlaceholder);
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
  @Nonnull
  public String toString() {
    List<String> components = new ArrayList<>(5);

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
  @Nonnull
  public String getKey() {
    return key;
  }

  /**
   * Gets this string's default translation, if available.
   *
   * @return this string's default translation, not null
   */
  @Nonnull
  public Optional<String> getTranslation() {
    return Optional.ofNullable(translation);
  }

  /**
   * Gets this string's commentary (usage/translation notes).
   *
   * @return this string's commentary, not null
   */
  @Nonnull
  public Optional<String> getCommentary() {
    return Optional.ofNullable(commentary);
  }

  /**
   * Gets per-language-form translations that correspond to a placeholder value.
   * <p>
   * For example, language form {@code MASCULINE} might be translated as {@code He} for placeholder {@code subject}.
   *
   * @return per-language-form translations that correspond to a placeholder value, not null
   */
  @Nonnull
  public Map<String, LanguageFormTranslation> getLanguageFormTranslationsByPlaceholder() {
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
  @Nonnull
  public List<LocalizedString> getAlternatives() {
    return alternatives;
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
    @Nonnull
    private final String key;
    @Nullable
    private String translation;
    @Nullable
    private String commentary;
    @Nullable
    private Map<String, LanguageFormTranslation> languageFormTranslationsByPlaceholder;
    @Nullable
    private List<LocalizedString> alternatives;

    /**
     * Constructs a localized string builder with the given key.
     *
     * @param key this string's translation key, not null
     */
    public Builder(@Nonnull String key) {
      requireNonNull(key);
      this.key = key;
    }

    /**
     * Applies a default translation to this builder.
     *
     * @param translation a default translation, may be null
     * @return this builder instance, useful for chaining. not null
     */
    @Nonnull
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
    @Nonnull
    public Builder commentary(@Nullable String commentary) {
      this.commentary = commentary;
      return this;
    }

    /**
     * Applies per-language-form translations to this builder.
     *
     * @param languageFormTranslationsByPlaceholder per-language-form translations, may be null
     * @return this builder instance, useful for chaining. not null
     */
    @Nonnull
    public Builder languageFormTranslationsByPlaceholder(
        @Nullable Map<String, LanguageFormTranslation> languageFormTranslationsByPlaceholder) {
      this.languageFormTranslationsByPlaceholder = languageFormTranslationsByPlaceholder;
      return this;
    }

    /**
     * Applies alternative expression-driven translations to this builder.
     *
     * @param alternatives alternative expression-driven translations, may be null
     * @return this builder instance, useful for chaining. not null
     */
    @Nonnull
    public Builder alternatives(@Nullable List<LocalizedString> alternatives) {
      this.alternatives = alternatives;
      return this;
    }

    /**
     * Constructs an instance of {@link LocalizedString}.
     *
     * @return an instance of {@link LocalizedString}, not null
     */
    @Nonnull
    public LocalizedString build() {
      return new LocalizedString(key, translation, commentary, languageFormTranslationsByPlaceholder, alternatives);
    }
  }

  /**
   * Container for per-language-form (gender, cardinal, ordinal) translation information.
   * <p>
   * Translations can be keyed either on a single value or a range of values (start and end) in the case of cardinality ranges.
   * <p>
   * It is required to have either a {@code value} or {@code range}, but not both.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static class LanguageFormTranslation {
    @Nullable
    private final String value;
    @Nullable
    private final LanguageFormTranslationRange range;
    @Nonnull
    private final Map<LanguageForm, String> translationsByLanguageForm;

    /**
     * Constructs a per-language-form translation set with the given placeholder value and mapping of translations by language form.
     *
     * @param value                      the placeholder value to compare against for translation, not null
     * @param translationsByLanguageForm the possible translations keyed by language form, not null
     */
    public LanguageFormTranslation(@Nonnull String value, @Nonnull Map<LanguageForm, String> translationsByLanguageForm) {
      requireNonNull(value);
      requireNonNull(translationsByLanguageForm);

      this.value = value;
      this.range = null;
      this.translationsByLanguageForm = Collections.unmodifiableMap(new LinkedHashMap<>(translationsByLanguageForm));
    }

    /**
     * Constructs a per-language-form translation set with the given placeholder range and mapping of translations by language form.
     *
     * @param range                      the placeholder range to compare against for translation, not null
     * @param translationsByLanguageForm the possible translations keyed by language form, not null
     */
    public LanguageFormTranslation(@Nonnull LanguageFormTranslationRange range, @Nonnull Map<LanguageForm, String> translationsByLanguageForm) {
      requireNonNull(range);
      requireNonNull(translationsByLanguageForm);

      this.value = null;
      this.range = range;
      this.translationsByLanguageForm = Collections.unmodifiableMap(new LinkedHashMap<>(translationsByLanguageForm));
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @Nonnull
    public String toString() {
      if (getRange().isPresent())
        return format("%s{range=%s, translationsByLanguageForm=%s", getClass().getSimpleName(), getRange().get(), getTranslationsByLanguageForm());

      return format("%s{value=%s, translationsByLanguageForm=%s", getClass().getSimpleName(), getValue().get(), getTranslationsByLanguageForm());
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
          && Objects.equals(getTranslationsByLanguageForm(), languageFormTranslation.getTranslationsByLanguageForm());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getValue(), getRange(), getTranslationsByLanguageForm());
    }

    /**
     * Gets the value for this per-language-form translation set.
     *
     * @return the value for this per-language-form translation set, not null
     */
    @Nonnull
    public Optional<String> getValue() {
      return Optional.ofNullable(value);
    }

    /**
     * Gets the range for this per-language-form translation set.
     *
     * @return the range for this per-language-form translation set, not null
     */
    @Nonnull
    public Optional<LanguageFormTranslationRange> getRange() {
      return Optional.ofNullable(range);
    }

    /**
     * Gets the translations by language form for this per-language-form translation set.
     *
     * @return the translations by language form for this per-language-form translation set, not null
     */
    @Nonnull
    public Map<LanguageForm, String> getTranslationsByLanguageForm() {
      return translationsByLanguageForm;
    }
  }

  /**
   * Container for per-language-form cardinality translation information over a range (start, end) of values.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static class LanguageFormTranslationRange {
    @Nonnull
    private String start;
    @Nonnull
    private String end;

    /**
     * Constructs a translation range with the given start and end values.
     *
     * @param start the start value of the range, not null
     * @param end   the end value of the range, not null
     */
    public LanguageFormTranslationRange(@Nonnull String start, @Nonnull String end) {
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
    @Nonnull
    public String toString() {
      return format("%s{start=%s, end=%s", getClass().getSimpleName(), getStart(), getEnd());
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
    @Nonnull
    public String getStart() {
      return start;
    }

    /**
     * The end value for this range.
     *
     * @return the end value for this range, not null
     */
    @Nonnull
    public String getEnd() {
      return end;
    }
  }
}