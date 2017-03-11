/*
 * Copyright 2017 Transmogrify LLC.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	@Nonnull
	private final String translation;
	@Nonnull
	private final Map<String, Map<LanguageForm, String>> languageFormTranslationsByPlaceholder;
	@Nonnull
	private final List<LocalizedString> alternatives;

	/**
	 * Constructs a localized string with a key and translation.
	 *
	 * @param key         this string's translation key, not null
	 * @param translation this string's default translation, not null
	 */
	public LocalizedString(@Nonnull String key, @Nonnull String translation) {
		this(key, translation, null, null);
	}

	/**
	 * Constructs a localized string with a key, default translation, and additional translation rules.
	 *
	 * @param key                                   this string's translation key, not null
	 * @param translation                           this string's default translation, not null
	 * @param languageFormTranslationsByPlaceholder per-language-form translations that correspond to a placeholder value, may be null
	 * @param alternatives                          alternative expression-driven translations for this string, may be null
	 */
	public LocalizedString(@Nonnull String key, @Nonnull String translation,
												 @Nullable Map<String, Map<LanguageForm, String>> languageFormTranslationsByPlaceholder,
												 @Nullable Iterable<LocalizedString> alternatives) {
		requireNonNull(key);
		requireNonNull(translation);

		this.key = key;
		this.translation = translation;

		if (languageFormTranslationsByPlaceholder == null)
			languageFormTranslationsByPlaceholder = Collections.emptyMap();

		// Defensive copy to unmodifiable map
		languageFormTranslationsByPlaceholder = languageFormTranslationsByPlaceholder.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> entry.getKey(),
						entry -> Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue()))
				));

		this.languageFormTranslationsByPlaceholder = Collections.unmodifiableMap(languageFormTranslationsByPlaceholder);

		List<LocalizedString> alternativesAsList = new ArrayList<>();

		if (alternatives != null)
			alternatives.forEach(alternativesAsList::add);

		this.alternatives = Collections.unmodifiableList(alternativesAsList);
	}

	/**
	 * Generates a {@code String} representation of this object.
	 *
	 * @return a string representation of this object, not null
	 */
	@Override
	@Nonnull
	public String toString() {
		return format("%s{key=%s, translation=%s, languageFormTranslationsByPlaceholder=%s, alternatives=%s}",
				getClass().getSimpleName(), getKey(), getTranslation(), getLanguageFormTranslationsByPlaceholder(),
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

		LocalizedString localizedString = (LocalizedString) other;

		return Objects.equals(getKey(), localizedString.getKey())
				&& Objects.equals(getTranslation(), localizedString.getTranslation())
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
		return Objects.hash(getKey(), getTranslation(), getLanguageFormTranslationsByPlaceholder(), getAlternatives());
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
	 * Gets this string's default translation.
	 *
	 * @return this string's default translation, not null
	 */
	@Nonnull
	public String getTranslation() {
		return translation;
	}

	/**
	 * Gets per-language-form translations that correspond to a placeholder value.
	 * <p>
	 * For example, language form {@code MASCULINE} might be translated as {@code He} for placeholder {@code subject}.
	 *
	 * @return per-language-form translations that correspond to a placeholder value, not null
	 */
	@Nonnull
	public Map<String, Map<LanguageForm, String>> getLanguageFormTranslationsByPlaceholder() {
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
}