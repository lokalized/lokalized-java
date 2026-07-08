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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Contract for localized string providers - given a key and optional placeholders, return a localized string.
 * <p>
 * Format is {@code "You are missing {{requiredFieldCount}} required fields."}
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public interface Strings extends LocaleMatcher {
	/**
	 * Gets a localized string for the given key.
	 * <p>
	 * If no localized string is available, the configured {@link TranslationFailureHandler} decides what happens.
	 *
	 * @param key the localization key, not null
	 * @return a localized string for the key, not null
	 */
	@NonNull
	String get(@NonNull String key);

	/**
	 * Gets a localized string for the given key and options.
	 * <p>
	 * If no localized string is available, the configured or per-invocation {@link TranslationFailureHandler} decides what happens.
	 *
	 * @param key     the localization key, not null
	 * @param options per-invocation options, not null
	 * @return a localized string for the key, not null
	 */
	@NonNull
	String get(@NonNull String key, @NonNull TranslationOptions options);

	/**
	 * Gets a localized string for the given key.
	 * <p>
	 * If no localized string is available, the configured {@link TranslationFailureHandler} decides what happens.
	 *
	 * @param key          the localization key, not null
	 * @param placeholders the placeholders to insert into the string, may be null
	 * @return a localized string for the key, not null
	 */
	@NonNull
	String get(@NonNull String key, @Nullable Map<@NonNull String, @Nullable Object> placeholders);

	/**
	 * Gets a localized string for the given key, placeholders, and options.
	 * <p>
	 * If no localized string is available, the configured or per-invocation {@link TranslationFailureHandler} decides what happens.
	 *
	 * @param key          the localization key, not null
	 * @param placeholders the placeholders to insert into the string, may be null
	 * @param options      per-invocation options, not null
	 * @return a localized string for the key, not null
	 */
	@NonNull
	String get(@NonNull String key,
						 @Nullable Map<@NonNull String, @Nullable Object> placeholders,
						 @NonNull TranslationOptions options);

	/**
	 * Gets the locales for which localized strings were supplied.
	 *
	 * @return the supported locales, not null
	 */
	@NonNull
	Set<@NonNull Locale> getSupportedLocales();

	/**
	 * Gets the localized string keys supplied for the given locale.
	 *
	 * @param locale locale to inspect, not null
	 * @return the localized string keys for the locale, not null
	 * @throws IllegalArgumentException if the locale is not supported
	 */
	@NonNull
	Set<@NonNull String> getKeysForLocale(@NonNull Locale locale);

	/**
	 * Gets the keys supplied by {@code sourceLocale} but missing from {@code targetLocale}.
	 *
	 * @param sourceLocale locale whose keys are used as the source set, not null
	 * @param targetLocale locale whose keys are compared against the source set, not null
	 * @return keys present in {@code sourceLocale} and missing from {@code targetLocale}, not null
	 * @throws IllegalArgumentException if either locale is not supported
	 */
	@NonNull
	Set<@NonNull String> getMissingKeys(@NonNull Locale sourceLocale, @NonNull Locale targetLocale);

	/**
	 * Vends a {@link Strings} instance builder for the specified fallback locale.
	 * <p>
	 * <pre>{@code
	 * Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
	 *     .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
	 *     .localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-GB")))
	 *     .build();
	 * }</pre>
	 *
	 * @param fallbackLocale the fallback locale, not null
	 * @return a builder for a {@link Strings} instance, not null
	 */
	static @NonNull Builder withFallbackLocale(@NonNull Locale fallbackLocale) {
		requireNonNull(fallbackLocale);
		return new Builder(fallbackLocale);
	}

	/**
	 * Builder used to construct {@link Strings} instances.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	class Builder {
		@NonNull
		private final Locale fallbackLocale;
		@Nullable
		private Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier;
		@Nullable
		private Function<LocaleMatcher, Locale> localeSupplier;
		@Nullable
		private Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode;
		@Nullable
		private TranslationFailureHandler translationFailureHandler;
		@Nullable
		private PhoneticResolver phoneticResolver;
		@Nullable
		private BidiIsolation bidiIsolation;

		/**
		 * Constructs a strings builder with a default locale.
		 *
		 * @param fallbackLocale fallback locale, not null
		 */
		Builder(@NonNull Locale fallbackLocale) {
			requireNonNull(fallbackLocale);
			this.fallbackLocale = fallbackLocale;
		}

		/**
		 * Applies a localized string supplier to this builder.
		 *
		 * @param localizedStringSupplier localized string supplier, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder localizedStringSupplier(@Nullable Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier) {
			this.localizedStringSupplier = localizedStringSupplier;
			return this;
		}

		/**
		 * Applies a locale supplier to this builder.
		 *
		 * @param localeSupplier locale supplier, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder localeSupplier(@Nullable Function<LocaleMatcher, Locale> localeSupplier) {
			this.localeSupplier = localeSupplier;
			return this;
		}

		/**
		 * Applies a mapping of an ISO 639 language code to its ordered "tiebreaker" fallback locales to this builder.
		 *
		 * @param tiebreakerLocalesByLanguageCode "tiebreaker" fallback locales, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder tiebreakerLocalesByLanguageCode(@Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode) {
			this.tiebreakerLocalesByLanguageCode = tiebreakerLocalesByLanguageCode;
			return this;
		}

		/**
		 * Applies a phonetic resolver to this builder.
		 *
		 * @param phoneticResolver phonetic resolver, may be null (defaults to fail-fast resolver)
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder phoneticResolver(@Nullable PhoneticResolver phoneticResolver) {
			this.phoneticResolver = phoneticResolver;
			return this;
		}

		/**
		 * Applies a translation failure handler to this builder.
		 *
		 * @param translationFailureHandler handler for failed lookups, may be null (defaults to returning the key)
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder translationFailureHandler(@Nullable TranslationFailureHandler translationFailureHandler) {
			this.translationFailureHandler = translationFailureHandler;
			return this;
		}

		/**
		 * Applies bidirectional isolation behavior for caller-supplied placeholder values.
		 *
		 * @param bidiIsolation bidi isolation behavior, may be null (defaults to isolating caller-supplied values in RTL locales)
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder bidiIsolation(@Nullable BidiIsolation bidiIsolation) {
			this.bidiIsolation = bidiIsolation;
			return this;
		}

		/**
		 * Constructs a {@link Strings} instance.
		 *
		 * @return a {@link Strings} instance, not null
		 */
		@NonNull
		public Strings build() {
			return new DefaultStrings(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode,
					translationFailureHandler, phoneticResolver, bidiIsolation);
		}
	}
}
