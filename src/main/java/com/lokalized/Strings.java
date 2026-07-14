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
 * Contract for localized string providers - given a key and optional caller-supplied placeholders, return a localized
 * string.
 * <p>
 * Format is {@code "You are missing {{requiredFieldCount}} required fields."}
 * <p>
 * Each lookup observes one unmodifiable shallow snapshot of the caller-supplied placeholder map. Whole-message
 * alternative predicates, {@link LocalizedString.ExpressionTranslation expression-fragment} predicates, and
 * {@link LocalizedString.LanguageFormTranslation language-form} selectors read from that same snapshot for every
 * locale candidate. Generated placeholder values do not become expression operands or language-form selector inputs;
 * in particular, a language-form {@code value} or range endpoint always names raw caller input.
 * <p>
 * Generated-placeholder definitions declared by a {@link LocalizedString} are inherited along the selected
 * whole-message alternative branch. The nearest selected descendant's complete definition replaces a same-named
 * ancestor definition, unselected sibling definitions are invisible, and the resulting definition takes precedence
 * over a same-named caller value when rendering output. Definitions are frozen for the selected branch before any
 * generated fragment is resolved.
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
	 * Resolves a localized string and returns diagnostic information about the lookup.
	 *
	 * @param key localization key, not null
	 * @return translation result, not null
	 */
	@NonNull
	default TranslationResult getResult(@NonNull String key) {
		return getResult(key, null, TranslationOptions.none());
	}

	/**
	 * Resolves a localized string with per-invocation options and returns diagnostic information.
	 *
	 * @param key     localization key, not null
	 * @param options per-invocation options, not null
	 * @return translation result, not null
	 */
	@NonNull
	default TranslationResult getResult(@NonNull String key, @NonNull TranslationOptions options) {
		return getResult(key, null, options);
	}

	/**
	 * Resolves a localized string with placeholders and returns diagnostic information.
	 *
	 * @param key          localization key, not null
	 * @param placeholders caller-supplied placeholders, may be null
	 * @return translation result, not null
	 */
	@NonNull
	default TranslationResult getResult(@NonNull String key,
															 @Nullable Map<@NonNull String, @Nullable Object> placeholders) {
		return getResult(key, placeholders, TranslationOptions.none());
	}

	/**
	 * Resolves a localized string with placeholders and options and returns diagnostic information.
	 * <p>
	 * A handler response that throws still throws; successful translations and return-key/return-string responses are
	 * represented by the returned result.
	 * <p>
	 * The supplied map is shallow-copied once before the first locale candidate is attempted. Alternative predicates,
	 * language-form selectors, failure reporting, and every fallback candidate observe that same snapshot. Generated
	 * fragments are resolved separately and cannot change expression operands or selector inputs.
	 *
	 * @param key          localization key, not null
	 * @param placeholders caller-supplied placeholders, may be null
	 * @param options      per-invocation options, not null
	 * @return translation result, not null
	 */
	@NonNull
	TranslationResult getResult(@NonNull String key,
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
		private Function<LocaleMatcher, LocaleMatchResult> localeMatchSupplier;
		@Nullable
		private Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode;
		@Nullable
		private TranslationFailureHandler translationFailureHandler;
		@Nullable
		private TranslationFallbackPolicy translationFallbackPolicy;
		@Nullable
		private TranslationRuntimeLimits runtimeLimits;
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
		 * <p>
		 * The supplier may be invoked concurrently after the {@link Strings} instance is built and must be thread-safe.
		 *
		 * @param localeSupplier locale supplier, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder localeSupplier(@Nullable Function<LocaleMatcher, Locale> localeSupplier) {
			this.localeSupplier = localeSupplier;

			if (localeSupplier != null)
				this.localeMatchSupplier = null;

			return this;
		}

		/**
		 * Applies a locale-negotiation-result supplier to this builder.
		 * <p>
		 * Prefer this over {@link #localeSupplier(Function)} when callers of {@link #getResult(String)} should receive the
		 * original negotiation diagnostics. The supplier may be invoked concurrently and must be thread-safe. Supplying a
		 * non-null value replaces any locale supplier.
		 *
		 * @param localeMatchSupplier locale-match supplier, may be null
		 * @return this builder, not null
		 * @since 3.0.0
		 */
		@NonNull
		public Builder localeMatchSupplier(@Nullable Function<LocaleMatcher, LocaleMatchResult> localeMatchSupplier) {
			this.localeMatchSupplier = localeMatchSupplier;

			if (localeMatchSupplier != null)
				this.localeSupplier = null;

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
		 * <p>
		 * The resolver may be invoked concurrently after the {@link Strings} instance is built and must be thread-safe.
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
		 * <p>
		 * The handler may be invoked concurrently after the {@link Strings} instance is built and must be thread-safe.
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
		 * Applies the policy that decides whether failed locale attempts continue to fallback candidates.
		 * <p>
		 * The policy may be invoked concurrently after the {@link Strings} instance is built and must be thread-safe.
		 * The default {@link TranslationFallbackPolicy#fallbackOnMissingTranslationOrNoMatchingAlternative()} policy stops
		 * on {@link TranslationFailureReason#RESOLUTION_FAILURE}, including failures while evaluating an expression-fragment
		 * predicate or interpolating its selected/default fragment.
		 *
		 * @param translationFallbackPolicy locale fallback policy, may be null to use the safe default
		 * @return this builder, not null
		 */
		@NonNull
		public Builder translationFallbackPolicy(@Nullable TranslationFallbackPolicy translationFallbackPolicy) {
			this.translationFallbackPolicy = translationFallbackPolicy;
			return this;
		}

		/**
		 * Applies safety limits to localized strings construction and translation evaluation.
		 * <p>
		 * Expression limits apply to both whole-message alternatives and
		 * {@link LocalizedString.ExpressionAlternative expression-fragment alternatives}. Generated-placeholder depth and
		 * expansion limits are shared by {@link LocalizedString.LanguageFormTranslation language-form} and
		 * {@link LocalizedString.ExpressionTranslation expression-selected} fragments.
		 *
		 * @param runtimeLimits runtime limits, may be null to use the library defaults
		 * @return this builder, not null
		 * @since 3.0.0
		 */
		@NonNull
		public Builder runtimeLimits(@Nullable TranslationRuntimeLimits runtimeLimits) {
			this.runtimeLimits = runtimeLimits;
			return this;
		}

		/**
		 * Applies bidirectional isolation behavior for caller-supplied placeholder values.
		 * <p>
		 * Translation-owned text in generated fragments is not isolated; caller-supplied values interpolated into those
		 * fragments use this policy.
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
			return new DefaultStrings(fallbackLocale, localizedStringSupplier, localeSupplier, localeMatchSupplier,
					tiebreakerLocalesByLanguageCode,
					translationFailureHandler, phoneticResolver, bidiIsolation, translationFallbackPolicy, runtimeLimits);
		}
	}
}
