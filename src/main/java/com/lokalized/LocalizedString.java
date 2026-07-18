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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
public final class LocalizedString {
  private static final int MAXIMUM_DIAGNOSTIC_NODES = 256;
  private static final int MAXIMUM_DIAGNOSTIC_CHARACTERS = 16 * 1024;
  @NonNull
  private static final String DIAGNOSTIC_TRUNCATION_MARKER = "<truncated>";

  @NonNull
  private final String key;
  @Nullable
  private final String translation;
  @Nullable
  private final String commentary;
  @NonNull
  private final Map<@NonNull String, @NonNull PlaceholderDefinition> placeholderDefinitions;
  @NonNull
  private final List<@NonNull LocalizedString> alternatives;

  /**
   * Constructs a localized string with a key, default translation, and additional translation rules.
   *
   * @param key                    this string's translation key, not null
   * @param translation            this string's default translation, may be null
   * @param commentary             this string's commentary (usage/translation notes), may be null
   * @param placeholderDefinitions generated-placeholder definitions, may be null
   * @param alternatives           alternative expression-driven translations for this string, may be null
   */
  private LocalizedString(@NonNull String key, @Nullable String translation, @Nullable String commentary,
                          @Nullable Map<@NonNull String, ? extends @NonNull PlaceholderDefinition> placeholderDefinitions,
                          @Nullable List<@NonNull LocalizedString> alternatives) {
    requireNonNull(key);

    this.key = key;
    this.translation = translation;
    this.commentary = commentary;

    if (placeholderDefinitions == null) {
      this.placeholderDefinitions = Collections.emptyMap();
    } else {
      Map<@NonNull String, @NonNull PlaceholderDefinition> placeholderDefinitionsCopy = new LinkedHashMap<>();

      for (Map.Entry<@NonNull String, ? extends @NonNull PlaceholderDefinition> entry
          : placeholderDefinitions.entrySet())
        placeholderDefinitionsCopy.put(
            requireNonNull(entry.getKey(), "placeholderDefinitions must not contain a null key"),
            requireNonNull(entry.getValue(), "placeholderDefinitions must not contain a null value"));

      this.placeholderDefinitions = Collections.unmodifiableMap(placeholderDefinitionsCopy);
    }

    if (alternatives == null) {
      this.alternatives = Collections.emptyList();
    } else {
      List<@NonNull LocalizedString> alternativesCopy = new ArrayList<>(alternatives.size());

      for (LocalizedString alternative : alternatives)
        alternativesCopy.add(requireNonNull(alternative, "alternatives must not contain null elements"));

      this.alternatives = Collections.unmodifiableList(alternativesCopy);
    }

    if (translation == null && this.alternatives.isEmpty())
      throw new IllegalArgumentException(format("You must provide either a translation or at least one alternative expression. " +
          "Offending key was '%s'", key));
  }

  /**
   * Generates a bounded diagnostic {@code String} representation of this object.
   * <p>
   * Deep, shared, cyclic, or oversized values may be abbreviated with reference, cycle, or truncation markers.
   *
   * @return a string representation of this object, not null
   */
  @Override
  @NonNull
  public String toString() {
    DiagnosticRenderer renderer = new DiagnosticRenderer();
    return renderer.render(this);
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
    Deque<LocalizedStringPair> pending = new ArrayDeque<>();
    IdentityHashMap<LocalizedString, IdentityHashMap<LocalizedString, Boolean>> compared = new IdentityHashMap<>();
    pending.push(new LocalizedStringPair(this, localizedString));

    while (!pending.isEmpty()) {
      LocalizedStringPair pair = pending.pop();
      LocalizedString left = pair.left;
      LocalizedString right = pair.right;

      if (left == right)
        continue;

      IdentityHashMap<LocalizedString, Boolean> comparedRights = compared.get(left);
      if (comparedRights == null) {
        comparedRights = new IdentityHashMap<>();
        compared.put(left, comparedRights);
      } else if (comparedRights.containsKey(right)) {
        continue;
      }
      comparedRights.put(right, Boolean.TRUE);

      if (!Objects.equals(left.key, right.key)
          || !Objects.equals(left.translation, right.translation)
          || !Objects.equals(left.commentary, right.commentary)
          || !Objects.equals(left.placeholderDefinitions, right.placeholderDefinitions)
          || left.alternatives.size() != right.alternatives.size())
        return false;

      for (int index = 0; index < left.alternatives.size(); ++index) {
        LocalizedString leftAlternative = left.alternatives.get(index);
        LocalizedString rightAlternative = right.alternatives.get(index);

        if (leftAlternative == rightAlternative)
          continue;
        if (leftAlternative == null || rightAlternative == null)
          return false;

        pending.push(new LocalizedStringPair(leftAlternative, rightAlternative));
      }
    }

    return true;
  }

  /**
   * A hash code for this object.
   *
   * @return a suitable hash code
   */
  @Override
  public int hashCode() {
    IdentityHashMap<LocalizedString, Integer> computedHashes = new IdentityHashMap<>();
    IdentityHashMap<LocalizedString, Boolean> active = new IdentityHashMap<>();
    Deque<LocalizedStringHashFrame> pending = new ArrayDeque<>();
    active.put(this, Boolean.TRUE);
    pending.push(new LocalizedStringHashFrame(this));

    while (!pending.isEmpty()) {
      LocalizedStringHashFrame frame = pending.peek();

      if (frame.nextAlternativeIndex < frame.localizedString.alternatives.size()) {
        LocalizedString alternative = frame.localizedString.alternatives.get(frame.nextAlternativeIndex++);

        if (alternative == null)
          continue;
        if (computedHashes.containsKey(alternative))
          continue;
        if (active.containsKey(alternative))
          throw new IllegalStateException("Localized string alternative graph contains an identity cycle");

        active.put(alternative, Boolean.TRUE);
        pending.push(new LocalizedStringHashFrame(alternative));
        continue;
      }

      int alternativesHash = 1;
      for (LocalizedString alternative : frame.localizedString.alternatives)
        alternativesHash = 31 * alternativesHash + (alternative == null ? 0 : computedHashes.get(alternative));

      int hash = 1;
      hash = 31 * hash + Objects.hashCode(frame.localizedString.key);
      hash = 31 * hash + Objects.hashCode(frame.localizedString.translation);
      hash = 31 * hash + Objects.hashCode(frame.localizedString.commentary);
      hash = 31 * hash + Objects.hashCode(frame.localizedString.placeholderDefinitions);
      hash = 31 * hash + alternativesHash;
      computedHashes.put(frame.localizedString, hash);
      active.remove(frame.localizedString);
      pending.pop();
    }

    return computedHashes.get(this);
  }

  private static final class LocalizedStringPair {
    @NonNull private final LocalizedString left;
    @NonNull private final LocalizedString right;

    private LocalizedStringPair(@NonNull LocalizedString left, @NonNull LocalizedString right) {
      this.left = requireNonNull(left);
      this.right = requireNonNull(right);
    }
  }

  private static final class LocalizedStringHashFrame {
    @NonNull private final LocalizedString localizedString;
    private int nextAlternativeIndex;

    private LocalizedStringHashFrame(@NonNull LocalizedString localizedString) {
      this.localizedString = requireNonNull(localizedString);
    }
  }

  @NotThreadSafe
  private static final class DiagnosticRenderer {
    @NonNull private final BoundedDiagnosticOutput output = new BoundedDiagnosticOutput();
    @NonNull private final IdentityHashMap<@NonNull LocalizedString, @NonNull DiagnosticNodeState> states = new IdentityHashMap<>();
    @NonNull private final Deque<@NonNull LocalizedStringDiagnosticFrame> pending = new ArrayDeque<>();

    @NonNull
    private String render(@NonNull LocalizedString localizedString) {
      DiagnosticNodeState rootState = new DiagnosticNodeState(1);
      states.put(localizedString, rootState);
      pending.push(new LocalizedStringDiagnosticFrame(localizedString, rootState));

      while (!pending.isEmpty() && !output.isTruncated()) {
        LocalizedStringDiagnosticFrame frame = pending.peek();

        if (!frame.headerRendered) {
          renderHeader(frame.localizedString);
          frame.headerRendered = true;
        }

        if (output.isTruncated())
          break;

        if (frame.nextAlternativeIndex < frame.localizedString.alternatives.size()) {
          if (frame.nextAlternativeIndex > 0)
            output.append(", ");

          @Nullable LocalizedString alternative = frame.localizedString.alternatives.get(frame.nextAlternativeIndex++);

          if (alternative == null) {
            output.append("null");
            continue;
          }

          @Nullable DiagnosticNodeState existingState = states.get(alternative);

          if (existingState != null) {
            output.append(existingState.active ? "<cycle#" : "<ref#");
            output.append(Integer.toString(existingState.identifier));
            output.append(">");
            continue;
          }

          if (states.size() >= MAXIMUM_DIAGNOSTIC_NODES) {
            output.truncate();
            break;
          }

          DiagnosticNodeState alternativeState = new DiagnosticNodeState(states.size() + 1);
          states.put(alternative, alternativeState);
          pending.push(new LocalizedStringDiagnosticFrame(alternative, alternativeState));
          continue;
        }

        if (!frame.localizedString.alternatives.isEmpty())
          output.append("]");
        output.append("}");
        frame.state.active = false;
        pending.pop();
      }

      return output.toString();
    }

    private void renderHeader(@NonNull LocalizedString localizedString) {
      output.append("LocalizedString{key=");
      output.append(localizedString.key);

      if (localizedString.translation != null) {
        output.append(", translation=");
        output.append(localizedString.translation);
      }

      if (localizedString.commentary != null) {
        output.append(", commentary=");
        output.append(localizedString.commentary);
      }

      if (!localizedString.placeholderDefinitions.isEmpty()) {
        output.append(", placeholderDefinitions=");
        renderPlaceholderDefinitions(localizedString.placeholderDefinitions);
      }

      if (!localizedString.alternatives.isEmpty())
        output.append(", alternatives=[");
    }

    private void renderPlaceholderDefinitions(
        @NonNull Map<@NonNull String, @NonNull PlaceholderDefinition> placeholderDefinitions) {
      output.append("{");
      int index = 0;

      for (Map.Entry<@NonNull String, @NonNull PlaceholderDefinition> entry : placeholderDefinitions.entrySet()) {
        if (index++ > 0)
          output.append(", ");
        output.append(entry.getKey());
        output.append("=");
        renderPlaceholderDefinition(entry.getValue());

        if (output.isTruncated())
          return;
      }

      output.append("}");
    }

    private void renderPlaceholderDefinition(@Nullable PlaceholderDefinition placeholderDefinition) {
      if (placeholderDefinition == null) {
        output.append("null");
      } else if (placeholderDefinition instanceof LanguageFormTranslation) {
        renderLanguageFormTranslation((LanguageFormTranslation) placeholderDefinition);
      } else if (placeholderDefinition instanceof ExpressionTranslation) {
        renderExpressionTranslation((ExpressionTranslation) placeholderDefinition);
      } else {
        output.append("<unknown-placeholder-definition>");
      }
    }

    private void renderLanguageFormTranslation(@NonNull LanguageFormTranslation languageFormTranslation) {
      output.append("LanguageFormTranslation{");

      if (languageFormTranslation.range != null) {
        output.append("range=LanguageFormTranslationRange{start=");
        output.append(languageFormTranslation.range.start);
        output.append(", end=");
        output.append(languageFormTranslation.range.end);
        output.append("}");
      } else {
        output.append("value=");
        output.append(languageFormTranslation.value);
      }

      output.append(", translationsByLanguageForm={");
      int index = 0;

      for (Map.Entry<@NonNull LanguageForm, @NonNull String> entry
          : languageFormTranslation.translationsByLanguageForm.entrySet()) {
        if (index++ > 0)
          output.append(", ");
        renderLanguageForm(entry.getKey());
        output.append("=");
        output.append(entry.getValue());

        if (output.isTruncated())
          return;
      }

      output.append("}}");
    }

    private void renderLanguageForm(@Nullable LanguageForm languageForm) {
      if (languageForm == null) {
        output.append("null");
      } else if (languageForm instanceof Enum<?>) {
        output.append(((Enum<?>) languageForm).name());
      } else {
        output.append("<unsupported-language-form>");
      }
    }

    private void renderExpressionTranslation(@NonNull ExpressionTranslation expressionTranslation) {
      output.append("ExpressionTranslation{translation=");
      output.append(expressionTranslation.translation);
      output.append(", alternatives=[");

      for (int index = 0; index < expressionTranslation.alternatives.size(); ++index) {
        if (index > 0)
          output.append(", ");

        @Nullable ExpressionAlternative alternative = expressionTranslation.alternatives.get(index);

        if (alternative == null) {
          output.append("null");
        } else {
          output.append("ExpressionAlternative{expression=");
          output.append(alternative.expression);
          output.append(", translation=");
          output.append(alternative.translation);
          output.append("}");
        }

        if (output.isTruncated())
          return;
      }

      output.append("]}");
    }
  }

  @NotThreadSafe
  private static final class DiagnosticNodeState {
    private final int identifier;
    private boolean active = true;

    private DiagnosticNodeState(int identifier) {
      this.identifier = identifier;
    }
  }

  @NotThreadSafe
  private static final class LocalizedStringDiagnosticFrame {
    @NonNull private final LocalizedString localizedString;
    @NonNull private final DiagnosticNodeState state;
    private boolean headerRendered;
    private int nextAlternativeIndex;

    private LocalizedStringDiagnosticFrame(@NonNull LocalizedString localizedString,
                                           @NonNull DiagnosticNodeState state) {
      this.localizedString = requireNonNull(localizedString);
      this.state = requireNonNull(state);
    }
  }

  @NotThreadSafe
  private static final class BoundedDiagnosticOutput {
    @NonNull private final StringBuilder stringBuilder = new StringBuilder();
    private boolean truncated;

    private void append(@Nullable String value) {
      if (truncated)
        return;

      String renderedValue = value == null ? "null" : value;
      int availableCharacters = MAXIMUM_DIAGNOSTIC_CHARACTERS - stringBuilder.length();

      if (renderedValue.length() <= availableCharacters) {
        stringBuilder.append(renderedValue);
        return;
      }

      if (availableCharacters > 0)
        stringBuilder.append(renderedValue, 0, availableCharacters);
      truncate();
    }

    private void truncate() {
      if (truncated)
        return;

      int markerStart = MAXIMUM_DIAGNOSTIC_CHARACTERS - DIAGNOSTIC_TRUNCATION_MARKER.length();

      if (stringBuilder.length() > markerStart) {
        int safeMarkerStart = markerStart;

        if (safeMarkerStart > 0 && Character.isHighSurrogate(stringBuilder.charAt(safeMarkerStart - 1))
            && Character.isLowSurrogate(stringBuilder.charAt(safeMarkerStart)))
          --safeMarkerStart;

        stringBuilder.setLength(safeMarkerStart);
      }
      stringBuilder.append(DIAGNOSTIC_TRUNCATION_MARKER);
      truncated = true;
    }

    private boolean isTruncated() {
      return truncated;
    }

    @Override
    @NonNull
    public String toString() {
      return stringBuilder.toString();
    }
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
  public Optional<@NonNull String> getTranslation() {
    return Optional.ofNullable(translation);
  }

  /**
   * Gets this string's commentary (usage/translation notes).
   *
   * @return this string's commentary, not null
   */
  @NonNull
  public Optional<@NonNull String> getCommentary() {
    return Optional.ofNullable(commentary);
  }

  /**
   * Gets the generated-placeholder definitions declared by this localized string.
   * <p>
   * Current concrete types are {@link LanguageFormTranslation} and {@link ExpressionTranslation}. Consumers should
   * inspect the definition type with {@code instanceof} before accessing subtype-specific state and handle unfamiliar
   * future library-defined types defensively.
   *
   * @return generated-placeholder definitions keyed by placeholder name, not null
   * @since 3.0.0
   */
  @NonNull
  public Map<@NonNull String, @NonNull PlaceholderDefinition> getPlaceholderDefinitions() {
    return placeholderDefinitions;
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
    private Map<@NonNull String, ? extends @NonNull PlaceholderDefinition> placeholderDefinitions;
    @Nullable
    private List<@NonNull LocalizedString> alternatives;

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
     * Applies generated-placeholder definitions to this builder.
     *
     * @param placeholderDefinitions generated-placeholder definitions keyed by placeholder name, may be null
     * @return this builder instance, useful for chaining. not null
     * @since 3.0.0
     */
    @NonNull
    public Builder placeholderDefinitions(
        @Nullable Map<@NonNull String, ? extends @NonNull PlaceholderDefinition> placeholderDefinitions) {
      this.placeholderDefinitions = placeholderDefinitions;
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

    /**
     * Constructs an instance of {@link LocalizedString}.
     *
     * @return an instance of {@link LocalizedString}, not null
     * @throws NullPointerException if a configured collection contains a null key, value, or element
     */
    @NonNull
    public LocalizedString build() {
      return new LocalizedString(key, translation, commentary, placeholderDefinitions, alternatives);
    }
  }

  /**
   * Closed base type for generated-placeholder definitions.
   * <p>
   * The constructor is private so callers cannot introduce definition types that Lokalized does not know how to
   * validate or resolve. Library releases may add new concrete subtypes; consumers should therefore inspect values
   * returned by {@link LocalizedString#getPlaceholderDefinitions()} before casting.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   * @since 3.0.0
   */
  @Immutable
  public abstract static class PlaceholderDefinition {
    private PlaceholderDefinition() {}
  }

  /**
   * Container for per-language-form (gender, case, definiteness, classifier, formality, clusivity, animacy,
   * cardinal, ordinal, phonetic) translation information.
   * <p>
   * Translations can be keyed either on a single value or a range of values (start and end) in the case of cardinality
   * ranges.
   * Runtime values may be an explicit matching {@link LanguageForm}; cardinality and ordinality additionally accept
   * {@link PluralOperands} and the supported {@link Number} implementations documented by
   * {@link PluralOperands#forNumber(Number)}. Phonetic maps accept {@link CharSequence} values, which are bounded by
   * {@link TranslationRuntimeLimits#getMaximumInterpolatedOutputCharacters()} before conversion and delegation to the
   * configured {@link PhoneticResolver}.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static final class LanguageFormTranslation extends PlaceholderDefinition {
    @Nullable
    private final String value;
    @Nullable
    private final LanguageFormTranslationRange range;
    @NonNull
    private final Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm;

    /**
     * Constructs a per-language-form translation set with the given placeholder value and mapping of translations by language form.
     *
     * @param value                      the placeholder value to compare against for translation, not null
     * @param translationsByLanguageForm the possible translations keyed by language form, not null
     * @throws NullPointerException if the map contains a null key or value
     */
    public LanguageFormTranslation(@NonNull String value, @NonNull Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm) {
      requireNonNull(value);
      requireNonNull(translationsByLanguageForm);

      this.value = value;
      this.range = null;
      this.translationsByLanguageForm = immutableTranslationsByLanguageForm(translationsByLanguageForm);
    }

    /**
     * Constructs a per-language-form translation set with the given placeholder range and mapping of translations by language form.
     *
     * @param range                      the placeholder range to compare against for translation, not null
     * @param translationsByLanguageForm the possible translations keyed by language form, not null
     * @throws NullPointerException if the map contains a null key or value
     */
    public LanguageFormTranslation(@NonNull LanguageFormTranslationRange range, @NonNull Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm) {
      requireNonNull(range);
      requireNonNull(translationsByLanguageForm);

      this.value = null;
      this.range = range;
      this.translationsByLanguageForm = immutableTranslationsByLanguageForm(translationsByLanguageForm);
    }

    @NonNull
    private static Map<@NonNull LanguageForm, @NonNull String> immutableTranslationsByLanguageForm(
        @NonNull Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm) {
      Map<@NonNull LanguageForm, @NonNull String> copy = new LinkedHashMap<>();

      for (Map.Entry<@NonNull LanguageForm, @NonNull String> entry : translationsByLanguageForm.entrySet())
        copy.put(
            requireNonNull(entry.getKey(), "translationsByLanguageForm must not contain a null key"),
            requireNonNull(entry.getValue(), "translationsByLanguageForm must not contain a null value"));

      return Collections.unmodifiableMap(copy);
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @NonNull
    public String toString() {
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
    @NonNull
    public Optional<@NonNull String> getValue() {
      return Optional.ofNullable(value);
    }

    /**
     * Gets the range for this per-language-form translation set.
     *
     * @return the range for this per-language-form translation set, not null
     */
    @NonNull
    public Optional<@NonNull LanguageFormTranslationRange> getRange() {
      return Optional.ofNullable(range);
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
  }

  /**
   * A generated fragment with a required default translation and optional ordered expression-driven alternatives.
   * <p>
   * The first alternative whose expression matches supplies the fragment. If none match, the default translation is
   * used. The one-argument constructor creates a translation-only scoped fragment.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   * @since 3.0.0
   */
  @Immutable
  public static final class ExpressionTranslation extends PlaceholderDefinition {
    @NonNull
    private final String translation;
    @NonNull
    private final List<@NonNull ExpressionAlternative> alternatives;

    /**
     * Constructs a translation-only generated fragment.
     *
     * @param translation the fragment translation, not null
     */
    public ExpressionTranslation(@NonNull String translation) {
      requireNonNull(translation);

      this.translation = translation;
      this.alternatives = Collections.emptyList();
    }

    /**
     * Constructs a generated fragment with a default translation and ordered expression-driven alternatives.
     *
     * @param translation  the default fragment translation, not null
     * @param alternatives the ordered expression-driven alternatives, not null or empty
     * @throws NullPointerException if {@code alternatives} contains a null element
     */
    public ExpressionTranslation(@NonNull String translation,
                                 @NonNull List<@NonNull ExpressionAlternative> alternatives) {
      requireNonNull(translation);
      requireNonNull(alternatives);

      if (alternatives.isEmpty())
        throw new IllegalArgumentException("alternatives must not be empty; use ExpressionTranslation(String) for a translation-only fragment");

      this.translation = translation;
      List<@NonNull ExpressionAlternative> alternativesCopy = new ArrayList<>(alternatives.size());

      for (ExpressionAlternative alternative : alternatives)
        alternativesCopy.add(requireNonNull(alternative, "alternatives must not contain null elements"));

      this.alternatives = Collections.unmodifiableList(alternativesCopy);
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @NonNull
    public String toString() {
      return format("%s{translation=%s, alternatives=%s}", getClass().getSimpleName(), getTranslation(),
          getAlternatives());
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

      ExpressionTranslation expressionTranslation = (ExpressionTranslation) other;

      return Objects.equals(getTranslation(), expressionTranslation.getTranslation())
          && Objects.equals(getAlternatives(), expressionTranslation.getAlternatives());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getTranslation(), getAlternatives());
    }

    /**
     * Gets the default fragment translation.
     *
     * @return the default fragment translation, not null
     */
    @NonNull
    public String getTranslation() {
      return translation;
    }

    /**
     * Gets the ordered expression-driven alternatives.
     *
     * @return the ordered expression-driven alternatives, empty for a translation-only fragment, not null
     */
    @NonNull
    public List<@NonNull ExpressionAlternative> getAlternatives() {
      return alternatives;
    }
  }

  /**
   * An expression and the generated fragment translation selected when that expression matches.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   * @since 3.0.0
   */
  @Immutable
  public static final class ExpressionAlternative {
    @NonNull
    private final String expression;
    @NonNull
    private final String translation;

    /**
     * Constructs an expression-driven fragment alternative.
     *
     * @param expression  the localization expression, not null
     * @param translation the fragment translation, not null
     */
    public ExpressionAlternative(@NonNull String expression, @NonNull String translation) {
      requireNonNull(expression);
      requireNonNull(translation);

      this.expression = expression;
      this.translation = translation;
    }

    /**
     * Generates a {@code String} representation of this object.
     *
     * @return a string representation of this object, not null
     */
    @Override
    @NonNull
    public String toString() {
      return format("%s{expression=%s, translation=%s}", getClass().getSimpleName(), getExpression(),
          getTranslation());
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

      ExpressionAlternative expressionAlternative = (ExpressionAlternative) other;

      return Objects.equals(getExpression(), expressionAlternative.getExpression())
          && Objects.equals(getTranslation(), expressionAlternative.getTranslation());
    }

    /**
     * A hash code for this object.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
      return Objects.hash(getExpression(), getTranslation());
    }

    /**
     * Gets the localization expression.
     *
     * @return the localization expression, not null
     */
    @NonNull
    public String getExpression() {
      return expression;
    }

    /**
     * Gets the fragment translation selected when this expression matches.
     *
     * @return the fragment translation, not null
     */
    @NonNull
    public String getTranslation() {
      return translation;
    }
  }

  /**
   * Container for per-language-form cardinality translation information over a range (start, end) of values.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  @Immutable
  public static final class LanguageFormTranslationRange {
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
