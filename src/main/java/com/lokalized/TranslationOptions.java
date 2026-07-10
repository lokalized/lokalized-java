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

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Per-invocation options for localized string lookup.
 * <p>
 * These options override the defaults configured on a {@link Strings} instance for a single lookup.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class TranslationOptions {
	static final int MAXIMUM_LANGUAGE_RANGES = 1000;

	@NonNull
	private static final TranslationOptions NONE;

	static {
		NONE = new TranslationOptions(null, null, null, null, null);
	}

	@Nullable
	private final Locale locale;
	@Nullable
	private final List<@NonNull LanguageRange> languageRanges;
	@Nullable
	private final BidiIsolation bidiIsolation;
	@Nullable
	private final TranslationFailureHandler translationFailureHandler;
	@Nullable
	private final TranslationFallbackPolicy translationFallbackPolicy;

	private TranslationOptions(@Nullable Locale locale,
														 @Nullable List<@NonNull LanguageRange> languageRanges,
														 @Nullable BidiIsolation bidiIsolation,
														 @Nullable TranslationFailureHandler translationFailureHandler,
														 @Nullable TranslationFallbackPolicy translationFallbackPolicy) {
		if (locale != null && languageRanges != null)
			throw new IllegalArgumentException("Specify either locale or languageRanges, not both");

		this.locale = locale;
		this.languageRanges = languageRanges == null ? null : immutableLanguageRanges(languageRanges);
		this.bidiIsolation = bidiIsolation;
		this.translationFailureHandler = translationFailureHandler;
		this.translationFallbackPolicy = translationFallbackPolicy;
	}

	/**
	 * Gets options which do not override any {@link Strings} instance defaults.
	 *
	 * @return empty translation options, not null
	 */
	@NonNull
	public static TranslationOptions none() {
		return NONE;
	}

	/**
	 * Gets options which use the specified locale for a single lookup.
	 *
	 * @param locale locale to use, not null
	 * @return translation options, not null
	 */
	@NonNull
	public static TranslationOptions forLocale(@NonNull Locale locale) {
		requireNonNull(locale);
		return builder().locale(locale).build();
	}

	/**
	 * Gets options which use the specified language ranges for a single lookup.
	 *
	 * @param languageRanges language ranges to use, not null
	 * @return translation options, not null
	 * @throws IllegalArgumentException if more than 1,000 language ranges are supplied
	 */
	@NonNull
	public static TranslationOptions forLanguageRanges(@NonNull List<@NonNull LanguageRange> languageRanges) {
		requireNonNull(languageRanges);
		return builder().languageRanges(languageRanges).build();
	}

	/**
	 * Creates a translation options builder.
	 *
	 * @return the builder, not null
	 */
	@NonNull
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Gets the locale override, if configured.
	 *
	 * @return an optional containing the locale override when configured, otherwise empty. not null
	 */
	@NonNull
	public Optional<@NonNull Locale> getLocale() {
		return Optional.ofNullable(locale);
	}

	/**
	 * Gets the language-range override, if configured.
	 *
	 * @return an optional containing the language-range override when configured, otherwise empty. not null
	 */
	@NonNull
	public Optional<@NonNull List<@NonNull LanguageRange>> getLanguageRanges() {
		return Optional.ofNullable(languageRanges);
	}

	/**
	 * Gets the bidirectional isolation override, if configured.
	 *
	 * @return an optional containing the bidirectional isolation override when configured, otherwise empty. not null
	 */
	@NonNull
	public Optional<@NonNull BidiIsolation> getBidiIsolation() {
		return Optional.ofNullable(bidiIsolation);
	}

	/**
	 * Gets the translation failure handler override, if configured.
	 *
	 * @return an optional containing the translation failure handler override when configured, otherwise empty. not null
	 */
	@NonNull
	public Optional<@NonNull TranslationFailureHandler> getTranslationFailureHandler() {
		return Optional.ofNullable(translationFailureHandler);
	}

	/**
	 * Gets the locale-fallback policy override, if configured.
	 *
	 * @return configured fallback policy, or empty, not null
	 */
	@NonNull
	public Optional<@NonNull TranslationFallbackPolicy> getTranslationFallbackPolicy() {
		return Optional.ofNullable(translationFallbackPolicy);
	}

	/**
	 * Creates a builder initialized with this instance's values.
	 *
	 * @return the builder, not null
	 */
	@NonNull
	public Builder toBuilder() {
		return builder()
				.locale(locale)
				.languageRanges(languageRanges)
				.bidiIsolation(bidiIsolation)
				.translationFailureHandler(translationFailureHandler)
				.translationFallbackPolicy(translationFallbackPolicy);
	}

	/**
	 * Generates a {@code String} representation of this object.
	 *
	 * @return a string representation of this object, not null
	 */
	@Override
	@NonNull
	public String toString() {
		List<@NonNull String> components = new ArrayList<>(4);

		if (locale != null)
			components.add("locale=" + locale.toLanguageTag());

		if (languageRanges != null)
			components.add("languageRanges=" + languageRanges);

		if (bidiIsolation != null)
			components.add("bidiIsolation=" + bidiIsolation);

		if (translationFailureHandler != null)
			components.add("translationFailureHandler=" + translationFailureHandler);

		if (translationFallbackPolicy != null)
			components.add("translationFallbackPolicy=" + translationFallbackPolicy);

		return String.format("%s{%s}", getClass().getSimpleName(), String.join(", ", components));
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

		TranslationOptions translationOptions = (TranslationOptions) other;
		return Objects.equals(locale, translationOptions.locale)
				&& Objects.equals(languageRanges, translationOptions.languageRanges)
				&& Objects.equals(bidiIsolation, translationOptions.bidiIsolation)
				&& Objects.equals(translationFailureHandler, translationOptions.translationFailureHandler)
				&& Objects.equals(translationFallbackPolicy, translationOptions.translationFallbackPolicy);
	}

	/**
	 * A hash code for this object.
	 *
	 * @return a suitable hash code
	 */
	@Override
	public int hashCode() {
		return Objects.hash(locale, languageRanges, bidiIsolation, translationFailureHandler, translationFallbackPolicy);
	}

	@NonNull
	private static List<@NonNull LanguageRange> immutableLanguageRanges(@NonNull List<@NonNull LanguageRange> languageRanges) {
		requireNonNull(languageRanges);

		if (languageRanges.size() > MAXIMUM_LANGUAGE_RANGES)
			throw new IllegalArgumentException(String.format("At most %d language ranges are supported, but received %d",
					MAXIMUM_LANGUAGE_RANGES, languageRanges.size()));

		List<@NonNull LanguageRange> copy = new ArrayList<>(languageRanges.size());

		for (LanguageRange languageRange : languageRanges)
			copy.add(requireNonNull(languageRange));

		return Collections.unmodifiableList(copy);
	}

	/**
	 * Builder used to construct {@link TranslationOptions} instances.
	 * <p>
	 * This class is intended for use by a single thread.
	 * <p>
	 * Locale and language-range overrides are mutually exclusive. If both setters are used, the last non-null setter wins
	 * and clears the earlier override.
	 */
	@NotThreadSafe
	public static final class Builder {
		@Nullable
		private Locale locale;
		@Nullable
		private List<@NonNull LanguageRange> languageRanges;
		@Nullable
		private BidiIsolation bidiIsolation;
		@Nullable
		private TranslationFailureHandler translationFailureHandler;
		@Nullable
		private TranslationFallbackPolicy translationFallbackPolicy;

		private Builder() {
			// Use TranslationOptions.builder()
		}

		/**
		 * Applies a locale override.
		 * <p>
		 * Supplying a non-null locale clears any language-range override.
		 *
		 * @param locale locale to use, may be null
		 * @return this builder, not null
		 */
		@NonNull
		public Builder locale(@Nullable Locale locale) {
			this.locale = locale;

			if (locale != null)
				this.languageRanges = null;

			return this;
		}

		/**
		 * Applies a language-range override.
		 * <p>
		 * Supplying non-null language ranges clears any locale override.
		 *
		 * @param languageRanges language ranges to use, may be null
		 * @return this builder, not null
		 * @throws IllegalArgumentException if more than 1,000 language ranges are supplied
		 */
		@NonNull
		public Builder languageRanges(@Nullable List<@NonNull LanguageRange> languageRanges) {
			this.languageRanges = languageRanges == null ? null : immutableLanguageRanges(languageRanges);

			if (languageRanges != null)
				this.locale = null;

			return this;
		}

		/**
		 * Applies a bidirectional isolation override.
		 *
		 * @param bidiIsolation bidirectional isolation behavior, may be null
		 * @return this builder, not null
		 */
		@NonNull
		public Builder bidiIsolation(@Nullable BidiIsolation bidiIsolation) {
			this.bidiIsolation = bidiIsolation;
			return this;
		}

		/**
		 * Applies a translation failure handler override.
		 *
		 * @param translationFailureHandler handler for failed lookups, may be null
		 * @return this builder, not null
		 */
		@NonNull
		public Builder translationFailureHandler(@Nullable TranslationFailureHandler translationFailureHandler) {
			this.translationFailureHandler = translationFailureHandler;
			return this;
		}

		/** Applies a locale-fallback policy override for this lookup. */
		@NonNull
		public Builder translationFallbackPolicy(@Nullable TranslationFallbackPolicy translationFallbackPolicy) {
			this.translationFallbackPolicy = translationFallbackPolicy;
			return this;
		}

		/**
		 * Constructs a {@link TranslationOptions} instance.
		 *
		 * @return translation options, not null
		 */
		@NonNull
		public TranslationOptions build() {
			if (locale == null && languageRanges == null && bidiIsolation == null && translationFailureHandler == null &&
					translationFallbackPolicy == null)
				return TranslationOptions.none();

			return new TranslationOptions(locale, languageRanges, bidiIsolation, translationFailureHandler,
					translationFallbackPolicy);
		}
	}
}
