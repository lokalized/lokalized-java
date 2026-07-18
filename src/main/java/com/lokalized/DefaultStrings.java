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

import com.lokalized.ExpressionEvaluator.CompiledExpression;
import com.lokalized.LocalizedString.ExpressionAlternative;
import com.lokalized.LocalizedString.ExpressionTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslationRange;
import com.lokalized.LocalizedString.PlaceholderDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.IllformedLocaleException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of a localized string provider.
 * <p>
 * It is recommended to use a single instance of this class across your entire application.
 * <p>
 * In multi-tenant systems like a web application where each user might have a different locale,
 * your {@code localeSupplier} might return the locale specified by the current request, e.g.
 * from a set of {@link LanguageRange} parsed from the {@code Accept-Language} header.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class DefaultStrings implements Strings {
	@NonNull
	private static final PhoneticResolver DEFAULT_PHONETIC_RESOLVER;
	@NonNull
	private static final BidiIsolation DEFAULT_BIDI_ISOLATION;

	static {
		DEFAULT_PHONETIC_RESOLVER = (term, locale) -> {
			throw new IllegalStateException(format("No %s was configured. Provide one via %s.Builder#phoneticResolver(...)",
					PhoneticResolver.class.getSimpleName(), Strings.class.getSimpleName()));
		};
		DEFAULT_BIDI_ISOLATION = BidiIsolation.RTL_LOCALES;
	}

	@NonNull
	private final Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale;
	@Nullable
	private final Function<@NonNull LocaleMatcher, @NonNull Locale> localeSupplier;
	@Nullable
	private final Function<@NonNull LocaleMatcher, @NonNull LocaleMatchResult> localeMatchSupplier;
	@NonNull
	private final Map<@NonNull String, @NonNull List<@NonNull Locale>> tiebreakerLocalesByLanguageCode;
	@NonNull
	private final TranslationFailureHandler translationFailureHandler;
	@NonNull
	private final TranslationFallbackPolicy translationFallbackPolicy;
	@NonNull
	private final TranslationRuntimeLimits runtimeLimits;
	@NonNull
	private final Locale fallbackLocale;
	@NonNull
	private final StringInterpolator stringInterpolator;
	@NonNull
	private final ExpressionEvaluator expressionEvaluator;
	@NonNull
	private final PhoneticResolver phoneticResolver;
	@NonNull
	private final BidiIsolation bidiIsolation;

	/**
	 * Cache of localized strings by key by locale.
	 * <p>
	 * This is our "master" reference localized string storage that other data structures will point to.
	 */
	@NonNull
	private final Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> localizedStringsByKeyByLocale;
	@NonNull
	private final Map<@NonNull LocalizedString, @NonNull CompiledExpression> compiledExpressionsByAlternative;
	@NonNull
	private final Map<@NonNull ExpressionAlternative, @NonNull CompiledExpression> compiledExpressionsByFragmentAlternative;

	/**
	 * Constructs a localized string provider with builder-supplied data.
	 *
	 * @param fallbackLocale                  fallback locale, not null
	 * @param localizedStringSupplier         supplier of localized strings, not null
	 * @param localeSupplier                  locale supplier, may not be null
	 * @param tiebreakerLocalesByLanguageCode "tiebreaker" fallbacks, may be null
	 * @param translationFailureHandler       handler for lookup failures, may be null
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
												 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
												 @NonNull Function<@NonNull LocaleMatcher, @NonNull Locale> localeSupplier,
											 @Nullable Map<@NonNull String, @NonNull List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
													 @Nullable TranslationFailureHandler translationFailureHandler) {
		this(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode, translationFailureHandler, null);
	}

	/**
	 * Constructs a localized string provider with builder-supplied data.
	 *
	 * @param fallbackLocale                  fallback locale, not null
	 * @param localizedStringSupplier         supplier of localized strings, not null
	 * @param localeSupplier                  locale supplier, may not be null
	 * @param tiebreakerLocalesByLanguageCode "tiebreaker" fallbacks, may be null
	 * @param translationFailureHandler       handler for lookup failures, may be null
	 * @param phoneticResolver                resolver for phonetic categories, may be null (defaults to fail-fast resolver)
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
												 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
												 @NonNull Function<@NonNull LocaleMatcher, @NonNull Locale> localeSupplier,
											 @Nullable Map<@NonNull String, @NonNull List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
													 @Nullable TranslationFailureHandler translationFailureHandler,
													 @Nullable PhoneticResolver phoneticResolver) {
		this(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode, translationFailureHandler,
				phoneticResolver, null, null);
	}

	/**
	 * Constructs a localized string provider with builder-supplied data.
	 *
	 * @param fallbackLocale                  fallback locale, not null
	 * @param localizedStringSupplier         supplier of localized strings, not null
	 * @param localeSupplier                  locale supplier, may not be null
	 * @param tiebreakerLocalesByLanguageCode "tiebreaker" fallbacks, may be null
	 * @param translationFailureHandler       handler for lookup failures, may be null
	 * @param phoneticResolver                resolver for phonetic categories, may be null (defaults to fail-fast resolver)
	 * @param bidiIsolation                   bidi isolation behavior, may be null (defaults to isolating caller-supplied values in RTL locales)
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
												 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
												 @NonNull Function<@NonNull LocaleMatcher, @NonNull Locale> localeSupplier,
											 @Nullable Map<@NonNull String, @NonNull List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
															 @Nullable TranslationFailureHandler translationFailureHandler,
															 @Nullable PhoneticResolver phoneticResolver,
															 @Nullable BidiIsolation bidiIsolation) {
		this(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode,
				translationFailureHandler, phoneticResolver, bidiIsolation, null, null);
	}

	/**
	 * Constructs a localized string provider with builder-supplied data and locale-fallback policy.
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
														 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
														 @NonNull Function<@NonNull LocaleMatcher, @NonNull Locale> localeSupplier,
												 @Nullable Map<@NonNull String, @NonNull List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
															 @Nullable TranslationFailureHandler translationFailureHandler,
															 @Nullable PhoneticResolver phoneticResolver,
																 @Nullable BidiIsolation bidiIsolation,
																 @Nullable TranslationFallbackPolicy translationFallbackPolicy) {
		this(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode,
				translationFailureHandler, phoneticResolver, bidiIsolation, translationFallbackPolicy, null);
	}

	/**
	 * Constructs a localized string provider with builder-supplied data, fallback policy, and safety limits.
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
															 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
															 @Nullable Function<@NonNull LocaleMatcher, @NonNull Locale> localeSupplier,
													 @Nullable Map<@NonNull String, @NonNull List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
																 @Nullable TranslationFailureHandler translationFailureHandler,
																 @Nullable PhoneticResolver phoneticResolver,
																 @Nullable BidiIsolation bidiIsolation,
																 @Nullable TranslationFallbackPolicy translationFallbackPolicy,
																 @Nullable TranslationRuntimeLimits runtimeLimits) {
		this(fallbackLocale, localizedStringSupplier, localeSupplier, null, tiebreakerLocalesByLanguageCode,
				translationFailureHandler, phoneticResolver, bidiIsolation, translationFallbackPolicy, runtimeLimits);
	}

	/**
	 * Constructs a localized string provider with either a locale or locale-match supplier.
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
															 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
															 @Nullable Function<@NonNull LocaleMatcher, @NonNull Locale> localeSupplier,
															 @Nullable Function<@NonNull LocaleMatcher, @NonNull LocaleMatchResult> localeMatchSupplier,
												 @Nullable Map<@NonNull String, @NonNull List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
																 @Nullable TranslationFailureHandler translationFailureHandler,
																 @Nullable PhoneticResolver phoneticResolver,
																 @Nullable BidiIsolation bidiIsolation,
																 @Nullable TranslationFallbackPolicy translationFallbackPolicy,
																 @Nullable TranslationRuntimeLimits runtimeLimits) {
		LocaleUtils.requireWellFormed(fallbackLocale, "Fallback locale");
		requireNonNull(localizedStringSupplier, format("You must specify a 'localizedStringSupplier' when creating a %s instance", DefaultStrings.class.getSimpleName()));

		if ((localeSupplier == null) == (localeMatchSupplier == null))
			throw new IllegalArgumentException(format("You must specify exactly one of 'localeSupplier' or 'localeMatchSupplier' when creating a %s instance",
					DefaultStrings.class.getSimpleName()));

		Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>> suppliedLocalizedStringsByLocale = localizedStringSupplier.get();

		if (suppliedLocalizedStringsByLocale == null)
			suppliedLocalizedStringsByLocale = Collections.emptyMap();

		// Preserve insertion order without structurally hashing LocalizedString graphs. A deep graph can overflow while
		// hashing, and a shared DAG can make recursive hashing exponentially expensive even when its depth is valid.
		Map<@NonNull Locale, @NonNull List<@NonNull LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
		Map<@NonNull String, @NonNull Locale> localesByLanguageTag = new LinkedHashMap<>();

		for (Entry<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>> entry :
				suppliedLocalizedStringsByLocale.entrySet()) {
			Locale locale = entry.getKey();

			if (locale == null)
				throw new IllegalArgumentException("Null locale encountered in supplied localized strings");

			LocaleUtils.requireWellFormed(locale, "Localized strings locale");
			String normalizedLanguageTag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
			@Nullable Locale existingLocale = localesByLanguageTag.putIfAbsent(normalizedLanguageTag, locale);

			if (existingLocale != null)
				throw new IllegalArgumentException(format("Localized strings locales '%s' and '%s' both use IETF BCP 47 " +
						"language tag '%s'", existingLocale, locale, locale.toLanguageTag()));

			Iterable<@NonNull LocalizedString> suppliedLocalizedStrings = entry.getValue();

			if (suppliedLocalizedStrings == null)
				throw new IllegalArgumentException(format(
						"Null localized strings iterable encountered for locale '%s'", locale.toLanguageTag()));

			List<@NonNull LocalizedString> validatedLocalizedStrings = new ArrayList<>();

			for (LocalizedString localizedString : suppliedLocalizedStrings) {
				if (localizedString == null)
					throw new IllegalArgumentException(format(
							"Null localized string encountered for locale '%s'", locale.toLanguageTag()));

				validateLocalizedString(locale, localizedString);
				validatedLocalizedStrings.add(localizedString);
			}

			localizedStringsByLocale.put(locale, Collections.unmodifiableList(validatedLocalizedStrings));
		}

		List<@NonNull Locale> equivalentFallbackLocales = localizedStringsByLocale.keySet().stream()
				.filter(locale -> CldrLocaleData.equivalent(locale, fallbackLocale))
				.sorted(Comparator.comparing(Locale::toLanguageTag))
				.collect(Collectors.toList());

		if (equivalentFallbackLocales.isEmpty())
			throw new IllegalArgumentException(format("Specified fallback locale is '%s' but no matching " +
							"localized strings locale was found. Known locales: [%s]", fallbackLocale.toLanguageTag(),
					localizedStringsByLocale.keySet().stream()
							.map(locale -> locale.toLanguageTag())
							.sorted()
							.collect(Collectors.joining(", "))));

		// Make our own mapping of tiebreakers based on the provided mapping.
		// First, defensive copy, then add to the map as needed below.
		Map<@NonNull String, @NonNull List<@NonNull Locale>> internalTiebreakerLocalesByLanguageCode = new HashMap<>();
		Map<@NonNull String, @NonNull String> suppliedLanguageCodesByNormalizedLanguageCode = new HashMap<>();

		if (tiebreakerLocalesByLanguageCode != null) {
			for (Entry<@NonNull String, @NonNull List<@NonNull Locale>> entry : tiebreakerLocalesByLanguageCode.entrySet()) {
				String suppliedLanguageCode = entry.getKey();
				String languageCode = normalizedTiebreakerLanguageCode(suppliedLanguageCode);
				@Nullable String existingSuppliedLanguageCode =
						suppliedLanguageCodesByNormalizedLanguageCode.putIfAbsent(languageCode, suppliedLanguageCode);

				if (existingSuppliedLanguageCode != null)
					throw new IllegalArgumentException(format("Tiebreaker language codes '%s' and '%s' both normalize to '%s'",
							existingSuppliedLanguageCode, suppliedLanguageCode, languageCode));

				List<@NonNull Locale> locales = entry.getValue();

				if (locales == null)
					throw new IllegalArgumentException(format("Null tiebreaker locale list encountered for language code '%s'",
							suppliedLanguageCode));

				List<@NonNull Locale> validatedLocales = new ArrayList<>(locales.size());
				Set<@NonNull Locale> uniqueLocales = new LinkedHashSet<>(locales.size());

				for (Locale locale : locales) {
					if (locale == null)
						throw new IllegalArgumentException(format("Null tiebreaker locale encountered for language code '%s'",
								suppliedLanguageCode));

					Locale validatedLocale = LocaleUtils.requireWellFormed(locale, "Tiebreaker locale");

					if (!uniqueLocales.add(validatedLocale))
						throw new IllegalArgumentException(format("Duplicate tiebreaker locale '%s' encountered for language code '%s'",
								validatedLocale.toLanguageTag(), suppliedLanguageCode));

					validatedLocales.add(validatedLocale);
				}

				internalTiebreakerLocalesByLanguageCode.put(languageCode, validatedLocales);
			}
		}

		// Verify tiebreakers are provided to support locale resolution when ambiguity exists.
		// For each language code, if there is more than 1 localized strings file that matches it, tiebreakers must be provided.
		Map<@NonNull String, @NonNull Set<@NonNull Locale>> supportedLocalesByLanguageCode = new HashMap<>(localizedStringsByLocale.size());

		for (Locale supportedLocale : localizedStringsByLocale.keySet()) {
			Optional<@NonNull String> languageCode = LocaleUtils.normalizedLanguage(supportedLocale);

			// Private-use and undetermined tags have no primary-language matching semantics. They can be selected exactly,
			// but multiple such localized strings sources do not create the broad-language ambiguity that tiebreakers resolve.
			if (!languageCode.isPresent())
				continue;

			Set<@NonNull Locale> locales = supportedLocalesByLanguageCode.get(languageCode.get());

			if (locales == null) {
				locales = new HashSet<>();
				supportedLocalesByLanguageCode.put(languageCode.get(), locales);
			}

			locales.add(supportedLocale);
		}

		for (Entry<@NonNull String, @NonNull List<@NonNull Locale>> entry :
				internalTiebreakerLocalesByLanguageCode.entrySet()) {
			String languageCode = entry.getKey();
			List<@NonNull Locale> providedLocales = entry.getValue();
			@Nullable Set<@NonNull Locale> supportedLocales = supportedLocalesByLanguageCode.get(languageCode);

			if (supportedLocales == null)
				throw new IllegalArgumentException(format("Tiebreaker language code '%s' has no localized strings locales",
						languageCode));

			Set<@NonNull Locale> providedLocaleSet = new LinkedHashSet<>(providedLocales);

			if (providedLocaleSet.size() != supportedLocales.size() || !providedLocaleSet.equals(supportedLocales)) {
				List<@NonNull Locale> missingLocales = supportedLocales.stream()
						.filter(locale -> !providedLocaleSet.contains(locale))
						.sorted(Comparator.comparing(Locale::toLanguageTag))
						.collect(Collectors.toList());
				List<@NonNull Locale> unrelatedLocales = providedLocales.stream()
						.filter(locale -> !supportedLocales.contains(locale))
						.sorted(Comparator.comparing(Locale::toLanguageTag))
						.collect(Collectors.toList());

				throw new IllegalArgumentException(format("Tiebreaker locales for language code '%s' must be an exact " +
							"permutation of loaded locales %s; missing: %s; unrelated: %s", languageCode,
						supportedLocales.stream().map(Locale::toLanguageTag).sorted().collect(Collectors.toList()),
						missingLocales.stream().map(Locale::toLanguageTag).collect(Collectors.toList()),
						unrelatedLocales.stream().map(Locale::toLanguageTag).collect(Collectors.toList())));
			}
		}

		for (Entry<@NonNull String, @NonNull Set<@NonNull Locale>> entry : supportedLocalesByLanguageCode.entrySet()) {
			String languageCode = entry.getKey();
			List<@NonNull Locale> locales = entry.getValue().stream()
					.sorted(Comparator.comparing(Locale::toLanguageTag))
					.collect(Collectors.toList());
			@Nullable List<@NonNull Locale> providedTiebreakerLocales =
					internalTiebreakerLocalesByLanguageCode.get(languageCode);

			if (locales.size() == 1) {
				// If there is exactly 1 locale for the language code, it's its own "identity" tiebreaker.
				if (providedTiebreakerLocales == null)
					internalTiebreakerLocalesByLanguageCode.put(languageCode, new ArrayList<>(locales));
			} else if (locales.size() > 1) {
				// We need to provide tiebreakers if a locale has more than 1 localized strings file.
				if (providedTiebreakerLocales == null || providedTiebreakerLocales.size() == 0) {
					throw new IllegalArgumentException(format("You must specify tiebreaker locales via 'tiebreakerLocalesByLanguageCode' to resolve ambiguity for language code '%s' because localized strings exist for the following locale[s]: %s",
							languageCode, locales.stream().map(locale -> locale.toLanguageTag()).collect(Collectors.toList())));
				}
			} else {
				// Should never occur
				throw new IllegalStateException("No locales match language code");
			}
		}

		Map<@NonNull String, @NonNull List<@NonNull Locale>> finalizedTiebreakerLocalesByLanguageCode = new HashMap<>(internalTiebreakerLocalesByLanguageCode.size());

		for (Entry<@NonNull String, @NonNull List<@NonNull Locale>> entry : internalTiebreakerLocalesByLanguageCode.entrySet()) {
			List<@NonNull Locale> locales = entry.getValue();
			finalizedTiebreakerLocalesByLanguageCode.put(entry.getKey(),
					Collections.unmodifiableList(new ArrayList<>(locales)));
		}

		this.tiebreakerLocalesByLanguageCode = Collections.unmodifiableMap(finalizedTiebreakerLocalesByLanguageCode);

		Locale resolvedFallbackLocale = localizedStringsByLocale.containsKey(fallbackLocale) ? fallbackLocale : null;

		if (resolvedFallbackLocale == null && equivalentFallbackLocales.size() == 1)
			resolvedFallbackLocale = equivalentFallbackLocales.get(0);

		if (resolvedFallbackLocale == null) {
			String canonicalFallbackTag = CldrLocaleData.canonicalLanguageTag(fallbackLocale.toLanguageTag());
			String fallbackLanguageCode = normalizedLanguageCode(canonicalFallbackTag.split("-")[0]);
			@Nullable List<@NonNull Locale> fallbackTiebreakers =
					finalizedTiebreakerLocalesByLanguageCode.get(fallbackLanguageCode);

			if (fallbackTiebreakers != null)
				for (Locale fallbackTiebreaker : fallbackTiebreakers)
					if (equivalentFallbackLocales.contains(fallbackTiebreaker)) {
						resolvedFallbackLocale = fallbackTiebreaker;
						break;
					}
		}

		if (resolvedFallbackLocale == null)
			throw new IllegalArgumentException(format("Fallback locale '%s' is canonically equivalent to multiple loaded locales %s; " +
						"configure tiebreakerLocalesByLanguageCode to choose one", fallbackLocale.toLanguageTag(),
					equivalentFallbackLocales.stream().map(Locale::toLanguageTag).collect(Collectors.toList())));

		this.fallbackLocale = resolvedFallbackLocale;

		this.translationFailureHandler = translationFailureHandler == null ? TranslationFailureHandler.returnKey() : translationFailureHandler;
		this.translationFallbackPolicy = translationFallbackPolicy == null
				? TranslationFallbackPolicy.fallbackOnMissingTranslationOrNoMatchingAlternative()
				: translationFallbackPolicy;
		this.runtimeLimits = runtimeLimits == null ? TranslationRuntimeLimits.defaults() : runtimeLimits;
		this.bidiIsolation = bidiIsolation == null ? DEFAULT_BIDI_ISOLATION : bidiIsolation;
		this.stringInterpolator = new StringInterpolator();
		this.phoneticResolver = phoneticResolver == null ? DEFAULT_PHONETIC_RESOLVER : phoneticResolver;
		this.expressionEvaluator = new ExpressionEvaluator(null, this.phoneticResolver, this.runtimeLimits);

		Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> localizedStringsByKeyByLocale =
				new HashMap<>(localizedStringsByLocale.size());
		Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> inspectedLocalizedStringsByLocale =
				new LinkedHashMap<>();
		Map<@NonNull LocalizedString, @NonNull CompiledExpression> compiledExpressionsByAlternative =
				new IdentityHashMap<>();
		Map<@NonNull ExpressionAlternative, @NonNull CompiledExpression> compiledExpressionsByFragmentAlternative =
				new IdentityHashMap<>();
		Set<@NonNull LocalizedString> compiledLocalizedStrings = Collections.newSetFromMap(new IdentityHashMap<>());

		for (Entry<@NonNull Locale, @NonNull List<@NonNull LocalizedString>> entry : localizedStringsByLocale.entrySet()) {
			Locale locale = entry.getKey();
			Map<@NonNull String, @NonNull LocalizedString> localizedStringsByKey = new LinkedHashMap<>();

			for (LocalizedString localizedString : entry.getValue()) {
				String key = localizedString.getKey();
				LocalizedString existing = localizedStringsByKey.putIfAbsent(key, localizedString);

				if (existing != null)
					throw new IllegalArgumentException(format("Duplicate localized string key '%s' encountered for locale '%s'", key, locale.toLanguageTag()));

				compileExpressions(localizedString, locale, key, key, compiledExpressionsByAlternative,
						compiledExpressionsByFragmentAlternative, compiledLocalizedStrings);
			}

			Map<@NonNull String, @NonNull LocalizedString> immutableLocalizedStringsByKey =
					Collections.unmodifiableMap(localizedStringsByKey);
			localizedStringsByKeyByLocale.put(locale, immutableLocalizedStringsByKey);
			inspectedLocalizedStringsByLocale.put(locale,
					Collections.unmodifiableSet(new LocalizedStringSet(immutableLocalizedStringsByKey)));
		}

		this.localizedStringsByLocale = Collections.unmodifiableMap(inspectedLocalizedStringsByLocale);
		this.localizedStringsByKeyByLocale = Collections.unmodifiableMap(localizedStringsByKeyByLocale);
		this.compiledExpressionsByAlternative = Collections.unmodifiableMap(compiledExpressionsByAlternative);
		this.compiledExpressionsByFragmentAlternative =
				Collections.unmodifiableMap(compiledExpressionsByFragmentAlternative);

		this.localeSupplier = localeSupplier;
		this.localeMatchSupplier = localeMatchSupplier;
	}

	private void compileExpressions(@NonNull LocalizedString localizedString,
														@NonNull Locale locale,
														@NonNull String rootKey,
														@NonNull String declarationPath,
											@NonNull Map<@NonNull LocalizedString, @NonNull CompiledExpression> compiledAlternatives,
											@NonNull Map<@NonNull ExpressionAlternative, @NonNull CompiledExpression> compiledFragmentAlternatives,
											@NonNull Set<@NonNull LocalizedString> compiledLocalizedStrings) {
		requireNonNull(localizedString);
		requireNonNull(locale);
		requireNonNull(rootKey);
		requireNonNull(declarationPath);
		requireNonNull(compiledAlternatives);
		requireNonNull(compiledFragmentAlternatives);
		requireNonNull(compiledLocalizedStrings);

		if (!compiledLocalizedStrings.add(localizedString))
			return;

		for (Entry<@NonNull String, @NonNull PlaceholderDefinition> placeholderEntry :
				localizedString.getPlaceholderDefinitions().entrySet()) {
			String placeholderName = placeholderEntry.getKey();
			PlaceholderDefinition placeholderDefinition = placeholderEntry.getValue();

			if (!(placeholderDefinition instanceof ExpressionTranslation))
				continue;

			ExpressionTranslation expressionTranslation = (ExpressionTranslation) placeholderDefinition;
			for (int alternativeIndex = 0; alternativeIndex < expressionTranslation.getAlternatives().size();
					 ++alternativeIndex) {
				ExpressionAlternative alternative = expressionTranslation.getAlternatives().get(alternativeIndex);

				if (compiledFragmentAlternatives.containsKey(alternative))
					continue;

				try {
					compiledFragmentAlternatives.put(alternative,
							getExpressionEvaluator().compile(alternative.getExpression()));
				} catch (ExpressionEvaluationException e) {
					throw new ExpressionEvaluationException(format(
							"Unable to compile generated-fragment alternative %d expression '%s' for placeholder '%s' " +
									"declared at %s in root key '%s' for locale '%s': %s",
							alternativeIndex, alternative.getExpression(), placeholderName, declarationPath, rootKey,
							locale.toLanguageTag(),
							e.getMessage()), e);
				}
			}
		}

		for (LocalizedString alternative : localizedString.getAlternatives()) {
			String alternativePath = format("%s -> alternative[%s]", declarationPath, alternative.getKey());

			if (!compiledAlternatives.containsKey(alternative)) {
				try {
					compiledAlternatives.put(alternative,
							getExpressionEvaluator().compile(alternative.getKey()));
				} catch (ExpressionEvaluationException e) {
					throw new ExpressionEvaluationException(format(
							"Unable to compile whole-message alternative expression '%s' at %s in root key '%s' " +
									"for locale '%s': %s",
							alternative.getKey(), alternativePath, rootKey, locale.toLanguageTag(), e.getMessage()), e);
				}
			}

			compileExpressions(alternative, locale, rootKey, alternativePath, compiledAlternatives, compiledFragmentAlternatives,
					compiledLocalizedStrings);
		}
	}

	private static void validateLocalizedString(@NonNull Locale locale, @NonNull LocalizedString localizedString) {
		requireNonNull(locale);
		requireNonNull(localizedString);
		LocalizedStringValidator.validate(locale, localizedString);
	}

	@NonNull
	@Override
	public String get(@NonNull String key) {
		requireNonNull(key);
		return getResult(key).getTranslation();
	}

	@NonNull
	@Override
	public String get(@NonNull String key,
										 @NonNull TranslationOptions options) {
		requireNonNull(key);
		requireNonNull(options);
		return getResult(key, options).getTranslation();
	}

	@NonNull
	@Override
	public String get(@NonNull String key,
										 @Nullable Map<@NonNull String, @Nullable Object> placeholders) {
		requireNonNull(key);
		return getResult(key, placeholders).getTranslation();
	}

	@NonNull
	@Override
	public String get(@NonNull String key,
										 @Nullable Map<@NonNull String, @Nullable Object> placeholders,
										 @NonNull TranslationOptions options) {
		return getResult(key, placeholders, options).getTranslation();
	}

	@NonNull
	@Override
	public TranslationResult getResult(@NonNull String key) {
		requireNonNull(key);
		return getResult(key, null, TranslationOptions.none());
	}

	@NonNull
	@Override
	public TranslationResult getResult(@NonNull String key, @NonNull TranslationOptions options) {
		requireNonNull(key);
		requireNonNull(options);
		return getResult(key, null, options);
	}

	@NonNull
	@Override
	public TranslationResult getResult(@NonNull String key,
															 @Nullable Map<@NonNull String, @Nullable Object> placeholders) {
		requireNonNull(key);
		return getResult(key, placeholders, TranslationOptions.none());
	}

	@NonNull
	@Override
	public TranslationResult getResult(@NonNull String key,
															 @Nullable Map<@NonNull String, @Nullable Object> placeholders,
															 @NonNull TranslationOptions options) {
		requireNonNull(key);
		requireNonNull(options);

		if (placeholders == null)
			placeholders = Collections.emptyMap();

		LocaleLookup localeLookup = localeLookupFor(options);
		Locale locale = localeLookup.getLocale();
		LocaleMatchResult localeMatchResult = localeLookup.getLocaleMatchResult();
		BidiIsolation bidiIsolation = options.getBidiIsolation().orElse(getBidiIsolation());
		TranslationFailureHandler translationFailureHandler = options.getTranslationFailureHandler().orElse(getTranslationFailureHandler());
		TranslationFallbackPolicy translationFallbackPolicy = options.getTranslationFallbackPolicy()
				.orElse(getTranslationFallbackPolicy());
		// All locale candidates, failure reporting, and interpolation must observe one coherent caller-input snapshot.
		Map<@NonNull String, @Nullable Object> contextSnapshot = new HashMap<>(placeholders);

		if (contextSnapshot.containsKey(null))
			throw new IllegalArgumentException("Placeholder names must not be null");

		Map<@NonNull String, @Nullable Object> immutableContext = Collections.unmodifiableMap(contextSnapshot);
		RuntimeException firstFallbackFailure = null;
		boolean noMatchingAlternativeEncountered = false;
		List<@NonNull LocaleFallbackCandidate> fallbackCandidates = localeFallbackCandidates(locale);
		List<@NonNull Locale> attemptedLocales = new ArrayList<>(fallbackCandidates.size());

		for (int candidateIndex = 0; candidateIndex < fallbackCandidates.size(); ++candidateIndex) {
			LocaleFallbackCandidate fallbackCandidate = fallbackCandidates.get(candidateIndex);
			Locale candidateLocale = fallbackCandidate.getLocale();
			attemptedLocales.add(candidateLocale);
			@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = fallbackCandidate.getLocalizedStrings();
			TranslationFailureReason attemptFailureReason = TranslationFailureReason.MISSING_TRANSLATION;
			@Nullable Throwable attemptCause = null;

			if (localizedStrings != null) {
				LocalizedString localizedString = localizedStrings.get(key);

				if (localizedString != null) {
					try {
						Optional<String> translation = getInternal(key, localizedString, Collections.emptyMap(),
								immutableContext, candidateLocale, bidiIsolation, key);

						if (translation.isPresent())
							return new TranslationResult(key, translation.get(), locale, localeMatchResult, candidateLocale, attemptedLocales,
									TranslationResultStatus.TRANSLATED, null, null);

						attemptFailureReason = TranslationFailureReason.NO_MATCHING_ALTERNATIVE;
						noMatchingAlternativeEncountered = true;
					} catch (RuntimeException e) {
						attemptFailureReason = TranslationFailureReason.RESOLUTION_FAILURE;
						attemptCause = e;

						if (firstFallbackFailure == null)
							firstFallbackFailure = e;
					}
				}
			}

			if (candidateIndex + 1 >= fallbackCandidates.size())
				break;

			Boolean shouldTryNextLocale = requireNonNull(translationFallbackPolicy.shouldTryNextLocale(
					attemptFailureReason, candidateLocale, attemptCause), "translationFallbackPolicy returned null");

			if (!shouldTryNextLocale)
				break;
		}

		String message = format("No match for '%s' was found for locale '%s'.", key, locale.toLanguageTag());
		TranslationFailureReason failureReason = firstFallbackFailure != null
				? TranslationFailureReason.RESOLUTION_FAILURE
				: noMatchingAlternativeEncountered
				? TranslationFailureReason.NO_MATCHING_ALTERNATIVE
				: TranslationFailureReason.MISSING_TRANSLATION;
		TranslationFailure translationFailure = new DefaultTranslationFailure(key, locale, localeMatchResult, attemptedLocales, immutableContext,
				failureReason, firstFallbackFailure);
		TranslationFailureResponse translationFailureResponse = requireNonNull(translationFailureHandler.handle(translationFailure),
				format("%s returned null", TranslationFailureHandler.class.getSimpleName()));

		switch (translationFailureResponse.getAction()) {
			case RETURN_KEY: {
				String translation = interpolateFailureKey(key, immutableContext, locale, bidiIsolation);
				return new TranslationResult(key, translation, locale, localeMatchResult, null, attemptedLocales,
						TranslationResultStatus.RETURNED_KEY, failureReason, firstFallbackFailure);
			}
			case RETURN_STRING:
				return new TranslationResult(key, translationFailureResponse.getTranslation(), locale, localeMatchResult, null, attemptedLocales,
						TranslationResultStatus.RETURNED_STRING, failureReason, firstFallbackFailure);
			case THROW_EXCEPTION:
				throwExceptionFor(translationFailure, message);
				throw new IllegalStateException("Unreachable code");
			default:
				throw new IllegalArgumentException(format("Unsupported %s action %s",
						TranslationFailureResponse.class.getSimpleName(), translationFailureResponse.getAction()));
		}
	}

	/**
	 * Recursive method which attempts to translate a localized string.
	 *
	 * @param key              the toplevel translation key (always the same regardless of recursion depth), not null
	 * @param localizedString  the localized string on which to operate, not null
	 * @param inheritedPlaceholderBindings placeholder bindings inherited from selected ancestors, not null
	 * @param immutableContext an immutable snapshot of the user-supplied translation context, not null
	 * @param locale           the locale to use for evaluation, not null
	 * @param bidiIsolation    the bidirectional isolation behavior to apply, not null
	 * @return the translation, if possible (may not be possible if no translation value specified and no alternative expressions match), not null
	 */
	@NonNull
	protected Optional<String> getInternal(@NonNull String key, @NonNull LocalizedString localizedString,
																 @NonNull Map<@NonNull String, @NonNull PlaceholderBinding> inheritedPlaceholderBindings,
																 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
																 @NonNull Locale locale,
																 @NonNull BidiIsolation bidiIsolation,
																 @NonNull String selectedPath) {
		requireNonNull(key);
		requireNonNull(localizedString);
		requireNonNull(inheritedPlaceholderBindings);
		requireNonNull(immutableContext);
		requireNonNull(locale);
		requireNonNull(bidiIsolation);
		requireNonNull(selectedPath);

		Map<@NonNull String, @NonNull PlaceholderBinding> effectivePlaceholderBindings =
				new LinkedHashMap<>(inheritedPlaceholderBindings);
		for (Entry<@NonNull String, @NonNull PlaceholderDefinition> entry :
				localizedString.getPlaceholderDefinitions().entrySet())
			effectivePlaceholderBindings.put(entry.getKey(),
					new PlaceholderBinding(entry.getValue(), selectedPath));
		effectivePlaceholderBindings = Collections.unmodifiableMap(effectivePlaceholderBindings);

		// First, see if any alternatives match by evaluating them
		for (LocalizedString alternative : localizedString.getAlternatives()) {
			if (alternativeMatches(alternative, immutableContext, locale)) {
				// If we have a matching alternative, recurse into it
				// Alternatives are ordered first-match rules. Once a condition matches, only that branch may resolve;
				// an unmatched nested subtree must not fall through to a later sibling.
				return getInternal(key, alternative, effectivePlaceholderBindings, immutableContext, locale, bidiIsolation,
						format("%s -> alternative[%s]", selectedPath, alternative.getKey()));
			}
		}

		if (!localizedString.getTranslation().isPresent())
			return Optional.empty();

		String translation = localizedString.getTranslation().get();
		Map<@NonNull String, @Nullable Object> generatedContext = new HashMap<>();
		Map<@NonNull String, @NonNull String> generatedSelectionDescriptions = new HashMap<>();
		Set<@NonNull String> pendingGeneratedPlaceholderNames = new LinkedHashSet<>();
		Set<@NonNull String> resolvedGeneratedPlaceholderNames = new HashSet<>();
		enqueueGeneratedPlaceholderDependencies(translation, effectivePlaceholderBindings.keySet(),
				pendingGeneratedPlaceholderNames, resolvedGeneratedPlaceholderNames);

		while (!pendingGeneratedPlaceholderNames.isEmpty()) {
			String placeholderName = pendingGeneratedPlaceholderNames.iterator().next();
			pendingGeneratedPlaceholderNames.remove(placeholderName);

			if (!resolvedGeneratedPlaceholderNames.add(placeholderName))
				continue;

			PlaceholderBinding placeholderBinding = effectivePlaceholderBindings.get(placeholderName);
			if (placeholderBinding == null)
				throw new IllegalStateException(format(
						"No effective definition was found for generated placeholder '%s' in key '%s'", placeholderName, key));

			PlaceholderDefinition placeholderDefinition = placeholderBinding.getDefinition();
			if (placeholderDefinition instanceof ExpressionTranslation) {
				try {
					ResolvedFragment resolvedFragment = resolveExpressionTranslation(
							(ExpressionTranslation) placeholderDefinition,
							immutableContext, locale);
					generatedContext.put(placeholderName, resolvedFragment.getTemplate());
					generatedSelectionDescriptions.put(placeholderName,
							resolvedFragment.getSelectionDescription());
					enqueueGeneratedPlaceholderDependencies(resolvedFragment.getTemplate(),
							effectivePlaceholderBindings.keySet(),
							pendingGeneratedPlaceholderNames, resolvedGeneratedPlaceholderNames);
				} catch (RuntimeException e) {
					throw contextualizePlaceholderFailure(key, placeholderName, placeholderBinding, e);
				}
				continue;
			}

			if (!(placeholderDefinition instanceof LanguageFormTranslation))
				throw new IllegalStateException(format("Unsupported generated placeholder definition type %s for '%s' in key '%s'",
						placeholderDefinition.getClass().getName(), placeholderName, key));

			LanguageFormTranslation languageFormTranslation = (LanguageFormTranslation) placeholderDefinition;
			try {
				Object value = null;
				Object rangeStart = null;
				Object rangeEnd = null;
				Map<@NonNull Cardinality, @NonNull String> translationsByCardinality = new HashMap<>();
				Map<@NonNull Ordinality, @NonNull String> translationsByOrdinality = new HashMap<>();
				Map<@NonNull Gender, @NonNull String> translationsByGender = new HashMap<>();
				Map<@NonNull GrammaticalCase, @NonNull String> translationsByGrammaticalCase = new HashMap<>();
				Map<@NonNull Definiteness, @NonNull String> translationsByDefiniteness = new HashMap<>();
				Map<@NonNull Classifier, @NonNull String> translationsByClassifier = new HashMap<>();
				Map<@NonNull Formality, @NonNull String> translationsByFormality = new HashMap<>();
				Map<@NonNull Clusivity, @NonNull String> translationsByClusivity = new HashMap<>();
				Map<@NonNull Animacy, @NonNull String> translationsByAnimacy = new HashMap<>();
				Map<@NonNull Phonetic, @NonNull String> translationsByPhonetic = new HashMap<>();
				String selectionDescription = null;

				if (languageFormTranslation.getRange().isPresent()) {
					LanguageFormTranslationRange languageFormTranslationRange = languageFormTranslation.getRange().get();
					rangeStart = unwrapOptional(immutableContext.get(languageFormTranslationRange.getStart()));
					rangeEnd = unwrapOptional(immutableContext.get(languageFormTranslationRange.getEnd()));
				} else {
					value = unwrapOptional(immutableContext.get(languageFormTranslation.getValue().get()));
				}

				for (Entry<@NonNull LanguageForm, @NonNull String> translationEntry : languageFormTranslation.getTranslationsByLanguageForm().entrySet()) {
					LanguageForm languageForm = translationEntry.getKey();
					String translatedLanguageForm = translationEntry.getValue();

					if (languageForm instanceof Cardinality)
						translationsByCardinality.put((Cardinality) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Ordinality)
						translationsByOrdinality.put((Ordinality) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Gender)
						translationsByGender.put((Gender) languageForm, translatedLanguageForm);
					else if (languageForm instanceof GrammaticalCase)
						translationsByGrammaticalCase.put((GrammaticalCase) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Definiteness)
						translationsByDefiniteness.put((Definiteness) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Classifier)
						translationsByClassifier.put((Classifier) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Formality)
						translationsByFormality.put((Formality) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Clusivity)
						translationsByClusivity.put((Clusivity) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Animacy)
						translationsByAnimacy.put((Animacy) languageForm, translatedLanguageForm);
					else if (languageForm instanceof Phonetic)
						translationsByPhonetic.put((Phonetic) languageForm, translatedLanguageForm);
					else
						throw new IllegalArgumentException(format("Encountered unrecognized language form %s", languageForm));
				}

				int distinctLanguageForms = (translationsByCardinality.size() > 0 ? 1 : 0) +
						(translationsByOrdinality.size() > 0 ? 1 : 0) +
						(translationsByGender.size() > 0 ? 1 : 0) +
						(translationsByGrammaticalCase.size() > 0 ? 1 : 0) +
						(translationsByDefiniteness.size() > 0 ? 1 : 0) +
						(translationsByClassifier.size() > 0 ? 1 : 0) +
						(translationsByFormality.size() > 0 ? 1 : 0) +
						(translationsByClusivity.size() > 0 ? 1 : 0) +
						(translationsByAnimacy.size() > 0 ? 1 : 0) +
						(translationsByPhonetic.size() > 0 ? 1 : 0);

				if (distinctLanguageForms > 1)
					throw new IllegalArgumentException("You cannot mix-and-match language forms in one placeholder definition");

				if (distinctLanguageForms == 0)
					continue;

				if (languageFormTranslation.getRange().isPresent() && translationsByCardinality.isEmpty())
					throw new IllegalArgumentException(format("Range-based translations are only supported for %s",
							Cardinality.class.getSimpleName()));

				// Handle plural cardinalities
				if (translationsByCardinality.size() > 0) {
					// Special case: calculate range from min and max if this is a range-driven cardinality
					if (languageFormTranslation.getRange().isPresent()) {
						LanguageFormTranslationRange languageFormTranslationRange = languageFormTranslation.getRange().get();

						if (rangeStart == null)
							throw new IllegalArgumentException(format("Missing range start placeholder '%s' for key '%s'",
									languageFormTranslationRange.getStart(), key));

						if (rangeEnd == null)
							throw new IllegalArgumentException(format("Missing range end placeholder '%s' for key '%s'",
									languageFormTranslationRange.getEnd(), key));

						Cardinality startCardinality = cardinalityForValue(key, languageFormTranslationRange.getStart(),
								rangeStart, locale, "Range start placeholder");
						Cardinality endCardinality = cardinalityForValue(key, languageFormTranslationRange.getEnd(),
								rangeEnd, locale, "Range end placeholder");
						Cardinality rangeCardinality = Cardinality.forRange(startCardinality, endCardinality, locale);

						String cardinalityTranslation = translationsByCardinality.get(rangeCardinality);

						if (cardinalityTranslation == null)
							throw new IllegalStateException(format("Missing %s translation for range cardinality %s (start was %s, end was %s)",
									Cardinality.class.getSimpleName(), rangeCardinality.name(), startCardinality.name(), endCardinality.name()));

						generatedContext.put(placeholderName, cardinalityTranslation);
						selectionDescription = format("%s.%s range (start %s, end %s)",
								Cardinality.class.getSimpleName(), rangeCardinality.name(), startCardinality.name(),
								endCardinality.name());
					} else {
						// Normal "non-range" cardinality
						if (value == null)
							throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
									languageFormTranslation.getValue().get(), key));

						Cardinality cardinality = cardinalityForValue(key, languageFormTranslation.getValue().get(),
								value, locale, "Placeholder");
						String cardinalityTranslation = translationsByCardinality.get(cardinality);

						if (cardinalityTranslation == null)
							throw new IllegalStateException(format("Missing %s translation for %s",
									Cardinality.class.getSimpleName(), cardinality.name()));

						generatedContext.put(placeholderName, cardinalityTranslation);
						selectionDescription = format("%s.%s", Cardinality.class.getSimpleName(), cardinality.name());
					}
				}

				// Handle plural ordinalities
				if (translationsByOrdinality.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					Ordinality ordinality = ordinalityForValue(key, languageFormTranslation.getValue().get(),
							value, locale, "Placeholder");
					String ordinalityTranslation = translationsByOrdinality.get(ordinality);

					if (ordinalityTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Ordinality.class.getSimpleName(), ordinality.name()));

					generatedContext.put(placeholderName, ordinalityTranslation);
					selectionDescription = format("%s.%s", Ordinality.class.getSimpleName(), ordinality.name());
				}

				// Handle genders
				if (translationsByGender.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					if (!(value instanceof Gender))
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, Gender.class.getSimpleName(), value.getClass().getSimpleName()));

					Gender gender = (Gender) value;
					String genderTranslation = translationsByGender.get(gender);

					if (genderTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Gender.class.getSimpleName(), gender.name()));

					generatedContext.put(placeholderName, genderTranslation);
					selectionDescription = format("%s.%s", Gender.class.getSimpleName(), gender.name());
				}

				// Handle grammatical cases
				if (translationsByGrammaticalCase.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					if (!(value instanceof GrammaticalCase))
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, GrammaticalCase.class.getSimpleName(), value.getClass().getSimpleName()));

					GrammaticalCase grammaticalCase = (GrammaticalCase) value;
					String grammaticalCaseTranslation = translationsByGrammaticalCase.get(grammaticalCase);

					if (grammaticalCaseTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								GrammaticalCase.class.getSimpleName(), grammaticalCase.name()));

					generatedContext.put(placeholderName, grammaticalCaseTranslation);
					selectionDescription = format("%s.%s", GrammaticalCase.class.getSimpleName(), grammaticalCase.name());
				}

				// Handle definiteness
				if (translationsByDefiniteness.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					if (!(value instanceof Definiteness))
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, Definiteness.class.getSimpleName(), value.getClass().getSimpleName()));

					Definiteness definiteness = (Definiteness) value;
					String definitenessTranslation = translationsByDefiniteness.get(definiteness);

					if (definitenessTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Definiteness.class.getSimpleName(), definiteness.name()));

					generatedContext.put(placeholderName, definitenessTranslation);
					selectionDescription = format("%s.%s", Definiteness.class.getSimpleName(), definiteness.name());
				}

				// Handle classifiers
				if (translationsByClassifier.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					if (!(value instanceof Classifier))
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, Classifier.class.getSimpleName(), value.getClass().getSimpleName()));

					Classifier classifier = (Classifier) value;
					String classifierTranslation = translationsByClassifier.get(classifier);

					if (classifierTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Classifier.class.getSimpleName(), classifier.name()));

					generatedContext.put(placeholderName, classifierTranslation);
					selectionDescription = format("%s.%s", Classifier.class.getSimpleName(), classifier.name());
				}

				// Handle formality
				if (translationsByFormality.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					if (!(value instanceof Formality))
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, Formality.class.getSimpleName(), value.getClass().getSimpleName()));

					Formality formality = (Formality) value;
					String formalityTranslation = translationsByFormality.get(formality);

					if (formalityTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Formality.class.getSimpleName(), formality.name()));

					generatedContext.put(placeholderName, formalityTranslation);
					selectionDescription = format("%s.%s", Formality.class.getSimpleName(), formality.name());
				}

				// Handle clusivity
				if (translationsByClusivity.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					if (!(value instanceof Clusivity))
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, Clusivity.class.getSimpleName(), value.getClass().getSimpleName()));

					Clusivity clusivity = (Clusivity) value;
					String clusivityTranslation = translationsByClusivity.get(clusivity);

					if (clusivityTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Clusivity.class.getSimpleName(), clusivity.name()));

					generatedContext.put(placeholderName, clusivityTranslation);
					selectionDescription = format("%s.%s", Clusivity.class.getSimpleName(), clusivity.name());
				}

				// Handle animacy
				if (translationsByAnimacy.size() > 0) {
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					if (!(value instanceof Animacy))
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, Animacy.class.getSimpleName(), value.getClass().getSimpleName()));

					Animacy animacy = (Animacy) value;
					String animacyTranslation = translationsByAnimacy.get(animacy);

					if (animacyTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Animacy.class.getSimpleName(), animacy.name()));

					generatedContext.put(placeholderName, animacyTranslation);
					selectionDescription = format("%s.%s", Animacy.class.getSimpleName(), animacy.name());
				}

				// Handle phonetics
				if (translationsByPhonetic.size() > 0) {
					if (languageFormTranslation.getRange().isPresent())
						throw new IllegalArgumentException("Phonetic translations cannot use ranges");

					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					Phonetic phonetic;

					if (value instanceof Phonetic) {
						phonetic = (Phonetic) value;
					} else if (value instanceof CharSequence) {
						PhoneticResolver resolver = getPhoneticResolver();
						String term = CharSequenceUtils.toString((CharSequence) value,
								getRuntimeLimits().getMaximumInterpolatedOutputCharacters(),
								format("Phonetic input for placeholder '%s' in key '%s'",
										languageFormTranslation.getValue().get(), key));
						phonetic = resolver.resolve(term, locale);

						if (phonetic == null)
							throw new IllegalArgumentException(format("%s returned null for placeholder '%s' in key '%s'",
									PhoneticResolver.class.getSimpleName(), languageFormTranslation.getValue().get(), key));
					} else {
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s or %s but was %s",
								languageFormTranslation.getValue().get(), key, Phonetic.class.getSimpleName(),
								CharSequence.class.getSimpleName(), value.getClass().getSimpleName()));
					}

					String phoneticTranslation = translationsByPhonetic.get(phonetic);

					if (phoneticTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s",
								Phonetic.class.getSimpleName(), phonetic.name()));

					generatedContext.put(placeholderName, phoneticTranslation);
					selectionDescription = format("%s.%s", Phonetic.class.getSimpleName(), phonetic.name());
				}

				Object generatedTranslation = generatedContext.get(placeholderName);

				if (generatedTranslation instanceof String) {
					generatedSelectionDescriptions.put(placeholderName,
							selectionDescription == null ? "language-form translation" : selectionDescription);
					enqueueGeneratedPlaceholderDependencies((String) generatedTranslation, effectivePlaceholderBindings.keySet(),
							pendingGeneratedPlaceholderNames, resolvedGeneratedPlaceholderNames);
				}
			} catch (RuntimeException e) {
				throw contextualizePlaceholderFailure(key, placeholderName, placeholderBinding, e);
			}
		}

		return Optional.of(interpolateTemplate(key, translation, generatedContext, generatedSelectionDescriptions,
				immutableContext, effectivePlaceholderBindings, locale, bidiIsolation, new HashMap<>(), new ArrayList<>(),
				new GeneratedExpansionBudget(getRuntimeLimits().getMaximumGeneratedExpansionCharacters()), 0));
	}

	@NonNull
	private ResolvedFragment resolveExpressionTranslation(@NonNull ExpressionTranslation expressionTranslation,
															 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
															 @NonNull Locale locale) {
		requireNonNull(expressionTranslation);
		requireNonNull(immutableContext);
		requireNonNull(locale);

		for (ExpressionAlternative alternative : expressionTranslation.getAlternatives()) {
			CompiledExpression compiledExpression = compiledExpressionsByFragmentAlternative.get(alternative);

			if (compiledExpression == null)
				throw new IllegalStateException(format(
						"No compiled expression was found for generated-fragment alternative '%s'",
						alternative.getExpression()));

			try {
				if (getExpressionEvaluator().evaluateCompiledExpression(compiledExpression, immutableContext, locale))
					return new ResolvedFragment(alternative.getTranslation(),
							format("expression '%s'", alternative.getExpression()));
			} catch (ExpressionEvaluationException e) {
				throw new ExpressionEvaluationException(format(
						"Unable to evaluate generated-fragment expression '%s': %s",
						alternative.getExpression(), e.getMessage()), e);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(format(
						"Unable to evaluate generated-fragment expression '%s': %s",
						alternative.getExpression(), e.getMessage()), e);
			} catch (IllegalStateException e) {
				throw new IllegalStateException(format(
						"Unable to evaluate generated-fragment expression '%s': %s",
						alternative.getExpression(), e.getMessage()), e);
			}
		}

		return new ResolvedFragment(expressionTranslation.getTranslation(), "default translation");
	}

	@NonNull
	private static RuntimeException contextualizePlaceholderFailure(@NonNull String key,
																							@NonNull String placeholderName,
																							@NonNull PlaceholderBinding placeholderBinding,
																							@NonNull RuntimeException cause) {
		return contextualizePlaceholderFailure(key, placeholderName, placeholderBinding, null, cause);
	}

	@NonNull
	private static RuntimeException contextualizePlaceholderFailure(@NonNull String key,
																							@NonNull String placeholderName,
																							@NonNull PlaceholderBinding placeholderBinding,
																							@Nullable String selectionDescription,
																							@NonNull RuntimeException cause) {
		requireNonNull(key);
		requireNonNull(placeholderName);
		requireNonNull(placeholderBinding);
		requireNonNull(cause);

		String causeMessage = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
		String selectionContext = selectionDescription == null ? "" : format("; selected %s", selectionDescription);
		String message = format(
				"Unable to resolve generated placeholder '%s' (%s) for key '%s'; definition declared at %s%s: %s",
				placeholderName, placeholderBinding.getDefinition().getClass().getSimpleName(), key,
				placeholderBinding.getDeclaringPath(), selectionContext, causeMessage);

		if (cause instanceof ExpressionEvaluationException)
			return new ExpressionEvaluationException(message, cause);

		if (cause instanceof IllegalArgumentException)
			return new IllegalArgumentException(message, cause);

		if (cause instanceof IllegalStateException)
			return new IllegalStateException(message, cause);

		// Preserve application exception identity and type (for example, a custom resolver failure). These exceptions
		// are already attributable to the configured extension point and may be inspected directly by failure handlers.
		return cause;
	}

	private static void enqueueGeneratedPlaceholderDependencies(@NonNull String template,
																							@NonNull Set<@NonNull String> fileDefinedPlaceholderNames,
																							@NonNull Set<@NonNull String> pendingGeneratedPlaceholderNames,
																							@NonNull Set<@NonNull String> resolvedGeneratedPlaceholderNames) {
		requireNonNull(template);
		requireNonNull(fileDefinedPlaceholderNames);
		requireNonNull(pendingGeneratedPlaceholderNames);
		requireNonNull(resolvedGeneratedPlaceholderNames);

		for (String placeholderName : StringInterpolator.placeholderNamesIn(template))
			if (fileDefinedPlaceholderNames.contains(placeholderName) &&
					!resolvedGeneratedPlaceholderNames.contains(placeholderName))
				pendingGeneratedPlaceholderNames.add(placeholderName);
	}

	@NonNull
	private String interpolateTemplate(@NonNull String key, @NonNull String template,
												 @NonNull Map<@NonNull String, @Nullable Object> generatedContext,
												 @NonNull Map<@NonNull String, @NonNull String> generatedSelectionDescriptions,
												 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
												 @NonNull Map<@NonNull String, @NonNull PlaceholderBinding> placeholderBindings,
												 @NonNull Locale locale, @NonNull BidiIsolation bidiIsolation,
																 @NonNull Map<@NonNull String, @NonNull String> expandedGeneratedValues,
																 @NonNull List<@NonNull String> generatedPlaceholderPath,
																 @NonNull GeneratedExpansionBudget generatedExpansionBudget, int depth) {
		requireNonNull(key);
		requireNonNull(template);
		requireNonNull(generatedContext);
		requireNonNull(generatedSelectionDescriptions);
		requireNonNull(immutableContext);
		requireNonNull(placeholderBindings);
		requireNonNull(locale);
		requireNonNull(bidiIsolation);
		requireNonNull(expandedGeneratedValues);
		requireNonNull(generatedPlaceholderPath);
		requireNonNull(generatedExpansionBudget);

		if (depth > getRuntimeLimits().getMaximumGeneratedPlaceholderDepth())
			throw new IllegalStateException(format(
					"Generated placeholder nesting for key '%s' exceeds the maximum depth of %d: %s",
					key, getRuntimeLimits().getMaximumGeneratedPlaceholderDepth(), generatedPlaceholderPath));

		Map<@NonNull String, @Nullable Object> interpolationContext = new HashMap<>();

		for (String placeholderName : StringInterpolator.placeholderNamesIn(template)) {
			if (placeholderBindings.containsKey(placeholderName)) {
				if (expandedGeneratedValues.containsKey(placeholderName)) {
					interpolationContext.put(placeholderName, expandedGeneratedValues.get(placeholderName));
					continue;
				}

				int cycleStart = generatedPlaceholderPath.indexOf(placeholderName);

				if (cycleStart >= 0) {
					List<@NonNull String> cycle = new ArrayList<>(generatedPlaceholderPath.subList(cycleStart,
							generatedPlaceholderPath.size()));
					cycle.add(placeholderName);
					throw new IllegalStateException(format("Generated placeholder cycle for key '%s': %s",
							key, cycle.stream().collect(Collectors.joining(" -> "))));
				}

				Object generatedValue = generatedContext.get(placeholderName);

				if (generatedValue instanceof String) {
					generatedPlaceholderPath.add(placeholderName);
					try {
						String expandedValue = interpolateTemplate(key, (String) generatedValue,
								generatedContext, generatedSelectionDescriptions, immutableContext, placeholderBindings,
								locale, bidiIsolation,
								expandedGeneratedValues, generatedPlaceholderPath, generatedExpansionBudget, depth + 1);
						expandedGeneratedValues.put(placeholderName, expandedValue);
						interpolationContext.put(placeholderName, expandedValue);
					} catch (RuntimeException e) {
						throw contextualizePlaceholderFailure(key, placeholderName,
								placeholderBindings.get(placeholderName),
								generatedSelectionDescriptions.get(placeholderName), e);
					} finally {
						generatedPlaceholderPath.remove(generatedPlaceholderPath.size() - 1);
					}
				}
			} else {
				Object value = unwrapOptional(immutableContext.get(placeholderName));

				if (value != null && shouldApplyBidiIsolation(locale, bidiIsolation))
					value = BidiUtils.isolatedValue(value,
							getRuntimeLimits().getMaximumInterpolatedOutputCharacters());

				interpolationContext.put(placeholderName, value);
			}
		}

		StringInterpolator.InterpolationResult interpolationResult = getStringInterpolator().interpolateStrictly(template,
				interpolationContext, getRuntimeLimits().getMaximumInterpolatedOutputCharacters());

		if (!interpolationResult.getUnresolvedPlaceholderNames().isEmpty())
			throw new IllegalArgumentException(format("Missing value for placeholder(s) [%s] in key '%s'",
					interpolationResult.getUnresolvedPlaceholderNames().stream().collect(Collectors.joining(", ")), key));

		String interpolatedValue = interpolationResult.getValue();

		if (depth > 0)
			generatedExpansionBudget.consume(interpolatedValue.length(), key);

		return interpolatedValue;
	}

	@NonNull
	private String interpolateFailureKey(@NonNull String key,
														 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
														 @NonNull Locale locale,
														 @NonNull BidiIsolation bidiIsolation) {
		requireNonNull(key);
		requireNonNull(immutableContext);
		requireNonNull(locale);
		requireNonNull(bidiIsolation);

		try {
			Map<@NonNull String, @Nullable Object> interpolationContext = new HashMap<>();

			for (String placeholderName : StringInterpolator.placeholderNamesInLeniently(key)) {
				Object value = unwrapOptional(immutableContext.get(placeholderName));

				if (value != null && shouldApplyBidiIsolation(locale, bidiIsolation))
					value = BidiUtils.isolatedValue(value,
							getRuntimeLimits().getMaximumInterpolatedOutputCharacters());

				interpolationContext.put(placeholderName, value);
			}

			return getStringInterpolator().interpolate(key, interpolationContext,
					getRuntimeLimits().getMaximumInterpolatedOutputCharacters());
		} catch (RuntimeException e) {
			return key;
		}
	}

	private static boolean shouldApplyBidiIsolation(@NonNull Locale locale, @NonNull BidiIsolation bidiIsolation) {
		requireNonNull(locale);
		requireNonNull(bidiIsolation);

		switch (bidiIsolation) {
			case NONE:
				return false;
			case ALWAYS:
				return true;
			case RTL_LOCALES:
				return BidiUtils.localeUsesRightToLeftScript(locale);
			default:
				throw new IllegalArgumentException(format("Unsupported %s value %s",
						BidiIsolation.class.getSimpleName(), bidiIsolation));
		}
	}

	@NonNull
	private Cardinality cardinalityForValue(@NonNull String key, @NonNull String valueName,
																							 @NonNull Object value, @NonNull Locale locale,
																							 @NonNull String description) {
		requireNonNull(key);
		requireNonNull(valueName);
		requireNonNull(value);
		requireNonNull(locale);
		requireNonNull(description);

		if (value instanceof Cardinality)
			return (Cardinality) value;

		if (value instanceof PluralOperands) {
			PluralOperands operands = validatePluralOperands((PluralOperands) value, description);
			return Cardinality.forOperands(operands, locale);
		}

		if (value instanceof Number)
			return Cardinality.forOperands(PluralOperands.forNumber((Number) value)
					.runtimeLimits(getRuntimeLimits()).build(), locale);

		throw new IllegalArgumentException(format(
				"%s '%s' in key '%s' must be a %s, %s, or %s but was %s", description, valueName, key,
				Number.class.getSimpleName(), PluralOperands.class.getSimpleName(), Cardinality.class.getSimpleName(),
				value.getClass().getSimpleName()));
	}

	@NonNull
	private Ordinality ordinalityForValue(@NonNull String key, @NonNull String valueName,
																							 @NonNull Object value, @NonNull Locale locale,
																							 @NonNull String description) {
		requireNonNull(key);
		requireNonNull(valueName);
		requireNonNull(value);
		requireNonNull(locale);
		requireNonNull(description);

		if (value instanceof Ordinality)
			return (Ordinality) value;

		if (value instanceof PluralOperands) {
			PluralOperands operands = validatePluralOperands((PluralOperands) value, description);
			return Ordinality.forOperands(operands, locale);
		}

		if (value instanceof Number)
			return Ordinality.forOperands(PluralOperands.forNumber((Number) value)
					.runtimeLimits(getRuntimeLimits()).build(), locale);

		throw new IllegalArgumentException(format(
				"%s '%s' in key '%s' must be a %s, %s, or %s but was %s", description, valueName, key,
				Number.class.getSimpleName(), PluralOperands.class.getSimpleName(), Ordinality.class.getSimpleName(),
				value.getClass().getSimpleName()));
	}

	@NonNull
	private PluralOperands validatePluralOperands(@NonNull PluralOperands operands, @NonNull String description) {
		requireNonNull(operands);
		requireNonNull(description);

		PluralOperands.validateNumericValue(operands.sourceNumber(), description, getRuntimeLimits());

		if (operands.getCompactExponent() > getRuntimeLimits().getMaximumCompactExponent())
			throw new IllegalArgumentException(format("%s compact exponent %d exceeds the configured maximum of %d",
					description, operands.getCompactExponent(), getRuntimeLimits().getMaximumCompactExponent()));

		if (operands.explicitVisibleDecimalPlaces().isPresent() &&
				operands.explicitVisibleDecimalPlaces().get() > getRuntimeLimits().getMaximumVisibleDecimalPlaces())
			throw new IllegalArgumentException(format("%s visible decimal places %s exceeds the configured maximum of %d",
					description, operands.explicitVisibleDecimalPlaces().get(),
					getRuntimeLimits().getMaximumVisibleDecimalPlaces()));

		return operands;
	}

	private boolean alternativeMatches(@NonNull LocalizedString alternative,
																		 @NonNull Map<@NonNull String, @Nullable Object> context,
																		 @NonNull Locale locale) {
		requireNonNull(alternative);
		requireNonNull(context);
		requireNonNull(locale);

		CompiledExpression compiledExpression = compiledExpressionsByAlternative.get(alternative);

		if (compiledExpression == null)
			throw new IllegalStateException(format("No compiled expression was found for alternative '%s'", alternative.getKey()));

		return getExpressionEvaluator().evaluateCompiledExpression(compiledExpression, context, locale);
	}

	@NonNull
	@Override
	public LocaleMatchResult matchFor(@NonNull Locale locale) {
		LocaleUtils.requireWellFormed(locale, "Requested locale");
		return matchFor(List.of(new LanguageRange(locale.toLanguageTag())));
	}

	@NonNull
	@Override
	public Locale bestMatchFor(@NonNull Locale locale) {
		requireNonNull(locale);
		return matchFor(locale).getLocale().orElse(getFallbackLocale());
	}

	@NonNull
	@Override
	public Locale bestMatchFor(@NonNull List<@NonNull LanguageRange> languageRanges) {
		requireNonNull(languageRanges);
		return matchFor(languageRanges).getLocale().orElse(getFallbackLocale());
	}

	@NonNull
	@Override
	public LocaleMatchResult matchFor(@NonNull List<@NonNull LanguageRange> languageRanges) {
		requireNonNull(languageRanges);

		if (languageRanges.size() > LocaleMatcher.MAXIMUM_LANGUAGE_RANGES)
			throw new IllegalArgumentException(format("At most %d language ranges are supported, but received %d",
					LocaleMatcher.MAXIMUM_LANGUAGE_RANGES, languageRanges.size()));

		List<@NonNull LanguageRange> requestedLanguageRanges = new ArrayList<>(languageRanges.size());

		for (LanguageRange languageRange : languageRanges)
			requestedLanguageRanges.add(requireNonNull(languageRange));

		requestedLanguageRanges = Collections.unmodifiableList(requestedLanguageRanges);
		List<@NonNull LanguageRange> sortedLanguageRanges = new ArrayList<>(requestedLanguageRanges);
		sortedLanguageRanges.sort(Comparator.comparingDouble(LanguageRange::getWeight).reversed());
		List<@NonNull Locale> availableLocales = new ArrayList<>(getLocalizedStringsByLocale().keySet());
		availableLocales.sort(Comparator.comparing(Locale::toLanguageTag));
		List<@NonNull Locale> consideredLocales = Collections.unmodifiableList(new ArrayList<>(availableLocales));

		if (languageRanges.isEmpty())
			return noLocaleMatch(requestedLanguageRanges, consideredLocales);

		// A locale can match more than one language range. Its effective quality is taken from the
		// most-specific matching range so, for example, "en;q=1,en-US;q=0" excludes en-US without
		// excluding en-GB. Restricting the candidate set up front also prevents a broad, high-quality
		// range from selecting a locale whose more-specific range has a lower quality.
		Map<@NonNull Locale, @NonNull EffectiveLanguageRangeMatch> effectiveMatchesByLocale = new LinkedHashMap<>();
		double highestEffectiveWeight = 0.0;

		for (Locale availableLocale : availableLocales) {
			@Nullable EffectiveLanguageRangeMatch effectiveMatch =
					effectiveLanguageRangeMatchFor(availableLocale, sortedLanguageRanges);

			if (effectiveMatch != null && effectiveMatch.getWeight() > 0.0) {
				effectiveMatchesByLocale.put(availableLocale, effectiveMatch);
				highestEffectiveWeight = Math.max(highestEffectiveWeight, effectiveMatch.getWeight());
			}
		}

		if (highestEffectiveWeight > 0.0) {
			double requiredWeight = highestEffectiveWeight;
			availableLocales.removeIf(locale -> {
				@Nullable EffectiveLanguageRangeMatch effectiveMatch = effectiveMatchesByLocale.get(locale);
				return effectiveMatch == null || Double.compare(effectiveMatch.getWeight(), requiredWeight) != 0;
			});
		} else {
			return noLocaleMatch(requestedLanguageRanges, consideredLocales);
		}

		// Walk through each LanguageRange in preference order
		for (LanguageRange languageRange : sortedLanguageRanges) {
			String range = languageRange.getRange(); // e.g. "pt" or "pt-PT"
			double weight = languageRange.getWeight();
			boolean privateUseRange = CldrLocaleData.isPrivateUseLanguageTag(range);

			if (weight <= 0)
				continue;

			if ("*".equals(range))
				return localeMatch(preferredLocaleForWildcard(availableLocales), effectiveMatchesByLocale,
						requestedLanguageRanges, consideredLocales);

			if (CldrLocaleData.hasUndeterminedLanguage(range) && !privateUseRange)
				continue;

			String canonicalRange = CldrLocaleData.canonicalLanguageTag(range);

			// An actual exact localized strings source must win over a different source with a canonically equivalent tag.
			for (Locale locale : availableLocales)
				if (locale.toLanguageTag().equalsIgnoreCase(range))
					return localeMatch(locale, effectiveMatchesByLocale,
							requestedLanguageRanges, consideredLocales);

			// Noninitial wildcards have RFC 4647 structural semantics only. In particular, an extended range that has no
			// structural candidates must not be broadened through canonical, CLDR, likely-subtag, or primary-language
			// matching. This also applies to private-use ranges such as x-*. Probe independently of quality.
			if (range.contains("*")) {
				List<@NonNull Locale> filteredCandidates = structurallyFilteredLocales(range, availableLocales);

				if (filteredCandidates.isEmpty())
					continue;

				String primary = normalizedLanguageCode(range.split("-")[0]);
				Locale preferredLocale = "*".equals(primary)
						? preferredLocaleForWildcard(filteredCandidates)
						: preferredLocaleForRange(range, filteredCandidates).orElse(filteredCandidates.get(0));

				return localeMatch(preferredLocale, effectiveMatchesByLocale,
						requestedLanguageRanges, consideredLocales);
			}

			// Private-use tags have no language semantics to broaden. Without a wildcard, they can select an exact
			// localized strings source but must not be treated as an ordinary primary language such as "x".
			if (privateUseRange)
				continue;

			// CLDR-canonical tag match? Multiple deprecated aliases can collapse to the same canonical tag,
			// so honor configured tiebreakers rather than returning the first lexicographic alias.
			List<@NonNull Locale> canonicalMatches = availableLocales.stream()
					.filter(locale -> CldrLocaleData.canonicalLanguageTag(locale.toLanguageTag()).equalsIgnoreCase(canonicalRange))
					.collect(Collectors.toList());
			Optional<@NonNull Locale> canonicalMatch = preferredLocaleForRange(canonicalRange, canonicalMatches);

			if (canonicalMatch.isPresent())
				return localeMatch(canonicalMatch.get(), effectiveMatchesByLocale,
						requestedLanguageRanges, consideredLocales);

			Optional<Locale> lookupMatch = lookupMatchByFallbackCandidates(range, availableLocales);

			if (lookupMatch.isPresent())
				return localeMatch(lookupMatch.get(), effectiveMatchesByLocale,
						requestedLanguageRanges, consideredLocales);

			Optional<Locale> likelySubtagMatch = lookupMatchByLikelySubtag(range, availableLocales);

			if (likelySubtagMatch.isPresent())
				return localeMatch(likelySubtagMatch.get(), effectiveMatchesByLocale,
						requestedLanguageRanges, consideredLocales);

			// Primary-tag candidates (e.g. "pt" or "pt-XX")
			String primary = normalizedLanguageCode(range.split("-")[0]); // e.g. "pt"

			List<@NonNull Locale> candidates = availableLocales.stream()
					.filter(locale -> LocaleUtils.normalizedLanguage(locale)
							.map(language -> language.equalsIgnoreCase(primary))
							.orElse(false))
					.filter(locale -> hasCompatibleLikelyScript(range, locale))
					.collect(Collectors.toList());

			if (candidates.isEmpty())
				continue; // try the next LanguageRange

			List<@NonNull Locale> filteredCandidates = structurallyFilteredLocales(range, candidates);

			boolean extendedRangeMatched = !filteredCandidates.isEmpty();

			if (extendedRangeMatched) {
				boolean hasSpecificMatch = filteredCandidates.stream()
						.anyMatch(locale -> !locale.toLanguageTag().equalsIgnoreCase(locale.getLanguage()));

				if (hasSpecificMatch)
					candidates = filteredCandidates;
			}

			if (candidates.size() == 1)
				return localeMatch(candidates.get(0), effectiveMatchesByLocale,
						requestedLanguageRanges, consideredLocales);

			// Tie‐breaker list for this primary tag?
			@Nullable List<@NonNull Locale> tiebreakers = getTiebreakerLocalesByLanguageCode().get(primary);

			if (tiebreakers != null)
				for (Locale tiebreaker : tiebreakers)
					if (candidates.contains(tiebreaker))
						return localeMatch(tiebreaker, effectiveMatchesByLocale,
								requestedLanguageRanges, consideredLocales);

			return localeMatch(candidates.get(0), effectiveMatchesByLocale,
					requestedLanguageRanges, consideredLocales);
		}

		return noLocaleMatch(requestedLanguageRanges, consideredLocales);
	}

	@NonNull
	private LocaleMatchResult localeMatch(@NonNull Locale locale,
																				 @NonNull Map<@NonNull Locale, @NonNull EffectiveLanguageRangeMatch> effectiveMatchesByLocale,
																				 @NonNull List<@NonNull LanguageRange> requestedLanguageRanges,
																				 @NonNull List<@NonNull Locale> consideredLocales) {
		EffectiveLanguageRangeMatch effectiveMatch = requireNonNull(effectiveMatchesByLocale.get(locale));
		return new LocaleMatchResult(requestedLanguageRanges, locale, effectiveMatch.getLanguageRange(),
				effectiveMatch.getWeight(), effectiveMatch.getMatchType(),
				getFallbackLocale(), consideredLocales);
	}

	@NonNull
	private LocaleMatchResult noLocaleMatch(@NonNull List<@NonNull LanguageRange> requestedLanguageRanges,
																			 @NonNull List<@NonNull Locale> consideredLocales) {
		return new LocaleMatchResult(requestedLanguageRanges, null, null, null, LocaleMatchType.NONE,
				getFallbackLocale(), consideredLocales);
	}

	@NonNull
	private Locale preferredLocaleForWildcard(@NonNull List<@NonNull Locale> availableLocales) {
		requireNonNull(availableLocales);

		if (availableLocales.isEmpty())
			throw new IllegalArgumentException("At least one available locale is required");

		if (availableLocales.contains(getFallbackLocale()))
			return getFallbackLocale();

		// When the exact fallback locale was explicitly excluded, preserve its language preference before
		// considering unrelated languages. The candidate list is already restricted to locales at the winning
		// quality, so only acceptable configured tiebreakers can be selected here.
		Optional<@NonNull Locale> fallbackLanguageTiebreaker = LocaleUtils.normalizedLanguage(getFallbackLocale())
				.flatMap(language -> lookupMatchByTiebreakers(language, availableLocales));

		return fallbackLanguageTiebreaker.orElse(availableLocales.get(0));
	}

	@Nullable
	private EffectiveLanguageRangeMatch effectiveLanguageRangeMatchFor(@NonNull Locale locale,
																									 @NonNull List<@NonNull LanguageRange> languageRanges) {
		requireNonNull(locale);
		requireNonNull(languageRanges);

		@Nullable LanguageRangeSpecificity bestSpecificity = null;
		@Nullable LanguageRange bestLanguageRange = null;
		double effectiveWeight = -1.0;

		for (LanguageRange languageRange : languageRanges) {
			@Nullable LanguageRangeSpecificity specificity = languageRangeSpecificityFor(locale, languageRange);

			if (specificity == null)
				continue;

			// Negative ranges exclude syntactic or canonical matches, not locales that are merely related
			// through Lokalized's CLDR parent/likely-subtag fallback heuristics. For example, en-US;q=0
			// must not also exclude en-GB.
			if (languageRange.getWeight() <= 0.0 && !specificity.isEligibleForExclusion())
				continue;

			int comparison = bestSpecificity == null ? 1 : specificity.compareTo(bestSpecificity);

			if (comparison > 0 || (comparison == 0 && languageRange.getWeight() > effectiveWeight)) {
				bestSpecificity = specificity;
				bestLanguageRange = languageRange;
				effectiveWeight = languageRange.getWeight();
			}
		}

		return bestLanguageRange == null ? null :
				new EffectiveLanguageRangeMatch(bestLanguageRange, requireNonNull(bestSpecificity),
						languageRangeMatchTypeFor(locale, bestLanguageRange), effectiveWeight);
	}

	@NonNull
	private LocaleMatchType languageRangeMatchTypeFor(@NonNull Locale locale,
																							 @NonNull LanguageRange languageRange) {
		requireNonNull(locale);
		requireNonNull(languageRange);
		String range = languageRange.getRange();
		String localeTag = locale.toLanguageTag();

		if ("*".equals(range))
			return LocaleMatchType.WILDCARD;
		if (localeTag.equalsIgnoreCase(range))
			return LocaleMatchType.EXACT;
		if (range.contains("*"))
			return LocaleMatchType.EXTENDED_RANGE;

		String canonicalRange = CldrLocaleData.canonicalLanguageTag(range);

		if (CldrLocaleData.canonicalLanguageTag(localeTag).equalsIgnoreCase(canonicalRange))
			return LocaleMatchType.CANONICAL;

		List<@NonNull Locale> selectedLocaleOnly = Collections.singletonList(locale);

		if (lookupMatchByFallbackCandidates(range, selectedLocaleOnly).isPresent())
			return LocaleMatchType.CLDR_FALLBACK;
		if (lookupMatchByLikelySubtag(range, selectedLocaleOnly).isPresent())
			return LocaleMatchType.LIKELY_SUBTAG;
		if (!structurallyFilteredLocales(range, selectedLocaleOnly).isEmpty())
			return LocaleMatchType.EXTENDED_RANGE;

		return LocaleMatchType.PRIMARY_LANGUAGE;
	}

	@Nullable
	private LanguageRangeSpecificity languageRangeSpecificityFor(@NonNull Locale locale,
																							@NonNull LanguageRange languageRange) {
		requireNonNull(locale);
		requireNonNull(languageRange);

		String range = languageRange.getRange();
		String structuralRange = normalizedExtendedLanguageRange(range);
		String localeTag = locale.toLanguageTag();
		boolean privateUseRange = CldrLocaleData.isPrivateUseLanguageTag(range);

		if ("*".equals(structuralRange))
			return new LanguageRangeSpecificity(LanguageRangeMatchCategory.WILDCARD, 0, 0);

		if (CldrLocaleData.hasUndeterminedLanguage(range) && !privateUseRange)
			return null;

		int structuralDepth = structuralConstraintCountFor(structuralRange);

		if (localeTag.equalsIgnoreCase(structuralRange))
			return new LanguageRangeSpecificity(LanguageRangeMatchCategory.EXACT, structuralDepth, 0);

		if (privateUseRange && !range.contains("*"))
			return null;

		// Specificity needs a structural match probe independent of quality so that a negative range such as
		// en-US;q=0 also excludes en-US-posix and en-US-u-nu-latn.
		List<@NonNull Locale> directlyFiltered = structurallyFilteredLocales(range, Collections.singletonList(locale));

		if (!directlyFiltered.isEmpty())
			return new LanguageRangeSpecificity(LanguageRangeMatchCategory.DIRECT_STRUCTURAL, structuralDepth, 0);

		if (range.contains("*"))
			return null;

		String canonicalRange = CldrLocaleData.canonicalLanguageTag(range);
		String canonicalLocaleTag = CldrLocaleData.canonicalLanguageTag(localeTag);

		if (canonicalLocaleTag.equalsIgnoreCase(canonicalRange))
			return new LanguageRangeSpecificity(LanguageRangeMatchCategory.CANONICAL, structuralDepth, 0);

		if (structurallyMatches(canonicalRange, canonicalLocaleTag))
			return new LanguageRangeSpecificity(LanguageRangeMatchCategory.CANONICAL, structuralDepth, 0);

		List<@NonNull Locale> fallbackLocales = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag(range));

		for (int index = 0; index < fallbackLocales.size(); ++index)
			if (CldrLocaleData.equivalent(locale, fallbackLocales.get(index)))
				return new LanguageRangeSpecificity(LanguageRangeMatchCategory.CLDR_FALLBACK, structuralDepth, index);

		Optional<String> requestedLanguageScript = CldrLocaleData.languageScriptForLikelySubtag(range);
		Optional<@NonNull String> availableLanguageScript = CldrLocaleData.hasUndeterminedLanguage(localeTag)
				? Optional.empty()
				: CldrLocaleData.languageScriptForLikelySubtag(locale);

		if (requestedLanguageScript.isPresent() && availableLanguageScript.isPresent() &&
				requestedLanguageScript.get().equalsIgnoreCase(availableLanguageScript.get()))
			return new LanguageRangeSpecificity(LanguageRangeMatchCategory.LIKELY_SUBTAG, structuralDepth, 0);

		String requestedPrimary = normalizedLanguageCode(range.split("-")[0]);
		Optional<String> availablePrimary = LocaleUtils.normalizedLanguage(locale);

		if (availablePrimary.isPresent() && availablePrimary.get().equalsIgnoreCase(requestedPrimary) &&
				hasCompatibleLikelyScript(range, locale))
			return new LanguageRangeSpecificity(LanguageRangeMatchCategory.PRIMARY_LANGUAGE, structuralDepth, 0);

		return null;
	}

	private static int structuralConstraintCountFor(@NonNull String range) {
		requireNonNull(range);
		int constraintCount = 0;
		int subtagStart = 0;

		for (int index = 0; index <= range.length(); ++index) {
			if (index < range.length() && range.charAt(index) != '-')
				continue;

			boolean wildcardSubtag = index - subtagStart == 1 && range.charAt(subtagStart) == '*';

			if (!wildcardSubtag)
				++constraintCount;

			subtagStart = index + 1;
		}

		return constraintCount;
	}

	@NonNull
	private static List<@NonNull Locale> structurallyFilteredLocales(@NonNull String range,
																							 @NonNull List<@NonNull Locale> locales) {
		requireNonNull(range);
		requireNonNull(locales);
		List<@NonNull Locale> filteredLocales = new ArrayList<>();

		for (Locale locale : locales)
			if (structurallyMatches(range, locale.toLanguageTag()))
				filteredLocales.add(locale);

		return filteredLocales;
	}

	/** Implements the extended-filtering algorithm in RFC 4647 section 3.3.2. */
	private static boolean structurallyMatches(@NonNull String range, @NonNull String languageTag) {
		requireNonNull(range);
		requireNonNull(languageTag);
		String[] rangeSubtags = range.split("-");
		String[] tagSubtags = languageTag.split("-");

		if (!"*".equals(rangeSubtags[0]) && !rangeSubtags[0].equalsIgnoreCase(tagSubtags[0]))
			return false;

		int rangeIndex = 1;
		int tagIndex = 1;

		while (rangeIndex < rangeSubtags.length) {
			String rangeSubtag = rangeSubtags[rangeIndex];

			// RFC 4647 ignores wildcard subtags outside the first position.
			if ("*".equals(rangeSubtag)) {
				++rangeIndex;
				continue;
			}

			if (tagIndex >= tagSubtags.length)
				return false;

			String tagSubtag = tagSubtags[tagIndex];

			if (rangeSubtag.equalsIgnoreCase(tagSubtag)) {
				++rangeIndex;
				++tagIndex;
			} else if (tagSubtag.length() == 1) {
				return false;
			} else {
				++tagIndex;
			}
		}

		return true;
	}

	/** Removes noninitial wildcards, which RFC 4647 defines as ignored during extended filtering. */
	@NonNull
	private static String normalizedExtendedLanguageRange(@NonNull String range) {
		requireNonNull(range);
		String[] subtags = range.split("-");
		StringBuilder normalizedRange = new StringBuilder(subtags[0]);

		for (int index = 1; index < subtags.length; ++index)
			if (!"*".equals(subtags[index]))
				normalizedRange.append('-').append(subtags[index]);

		return normalizedRange.toString();
	}

	private boolean hasCompatibleLikelyScript(@NonNull String requestedLanguageTag, @NonNull Locale availableLocale) {
		requireNonNull(requestedLanguageTag);
		requireNonNull(availableLocale);

		Optional<String> requestedLanguageScript = CldrLocaleData.languageScriptForLikelySubtag(requestedLanguageTag);
		Optional<String> availableLanguageScript = CldrLocaleData.languageScriptForLikelySubtag(availableLocale);
		return !requestedLanguageScript.isPresent() || !availableLanguageScript.isPresent() ||
				requestedLanguageScript.get().equalsIgnoreCase(availableLanguageScript.get());
	}

	@NonNull
	private LocaleLookup localeLookupFor(@NonNull TranslationOptions options) {
		requireNonNull(options);

		Optional<@NonNull Locale> locale = options.getLocale();

		if (locale.isPresent()) {
			Locale requestedLocale = locale.get();
			return new LocaleLookup(requestedLocale, matchFor(requestedLocale));
		}

		Optional<@NonNull List<@NonNull LanguageRange>> languageRanges = options.getLanguageRanges();

		if (languageRanges.isPresent()) {
			LocaleMatchResult matchResult = matchFor(languageRanges.get());
			return new LocaleLookup(matchResult.getLocale().orElse(getFallbackLocale()), matchResult);
		}

		if (localeMatchSupplier != null) {
			LocaleMatchResult suppliedMatch = requireNonNull(localeMatchSupplier.apply(this),
					"localeMatchSupplier returned null");
			validateSuppliedLocaleMatchResult(suppliedMatch);
			return new LocaleLookup(suppliedMatch.getLocale().orElse(suppliedMatch.getFallbackLocale()), suppliedMatch);
		}

		Locale suppliedLocale = requireNonNull(requireNonNull(localeSupplier).apply(this), "localeSupplier returned null");
		LocaleUtils.requireWellFormed(suppliedLocale, "localeSupplier result");
		return new LocaleLookup(suppliedLocale, matchFor(suppliedLocale));
	}

	private void validateSuppliedLocaleMatchResult(@NonNull LocaleMatchResult matchResult) {
		requireNonNull(matchResult);

		if (!getFallbackLocale().equals(matchResult.getFallbackLocale()))
			throw new IllegalArgumentException("localeMatchSupplier returned a result for a different fallback locale");

		Set<@NonNull Locale> supportedLocales = getSupportedLocales();
		Set<@NonNull Locale> consideredLocales = new LinkedHashSet<>(matchResult.getConsideredLocales());

		if (!supportedLocales.equals(consideredLocales))
			throw new IllegalArgumentException("localeMatchSupplier returned a result for different supported locales");
	}

	@NonNull
	private List<@NonNull LocaleFallbackCandidate> localeFallbackCandidates(@NonNull Locale locale) {
		requireNonNull(locale);

		LinkedHashSet<@NonNull Locale> candidates = new LinkedHashSet<>();
		candidates.addAll(CldrLocaleData.fallbackLocalesFor(locale));

		List<@NonNull Locale> availableLocales = new ArrayList<>(getLocalizedStringsByLocale().keySet());
		availableLocales.sort(Comparator.comparing(Locale::toLanguageTag));
		lookupMatchByLikelySubtag(locale.toLanguageTag(), availableLocales).ifPresent(candidates::add);

		LocaleUtils.normalizedLanguage(locale)
				.map(getTiebreakerLocalesByLanguageCode()::get)
				.ifPresent(tiebreakerLocales -> {
					for (Locale tiebreakerLocale : tiebreakerLocales)
						if (matchesLikelyLanguageScript(locale, tiebreakerLocale))
							candidates.add(tiebreakerLocale);
				});
		candidates.add(getFallbackLocale());

		Map<@NonNull Locale, @NonNull LocaleFallbackCandidate> candidatesByAttemptedLocale = new LinkedHashMap<>();

		for (Locale candidate : candidates) {
			@Nullable LocalizedStringsMatch localizedStringsMatch = localizedStringsFor(candidate);
			Locale attemptedLocale = localizedStringsMatch == null ? candidate : localizedStringsMatch.getLocale();
			@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings =
					localizedStringsMatch == null ? null : localizedStringsMatch.getLocalizedStrings();
			candidatesByAttemptedLocale.putIfAbsent(attemptedLocale,
					new LocaleFallbackCandidate(attemptedLocale, localizedStrings));
		}

		return new ArrayList<>(candidatesByAttemptedLocale.values());
	}

	@Nullable
	private LocalizedStringsMatch localizedStringsFor(@NonNull Locale locale) {
		requireNonNull(locale);

		@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = getLocalizedStringsByKeyByLocale().get(locale);

		if (localizedStrings != null)
			return new LocalizedStringsMatch(locale, localizedStrings);

		List<@NonNull Locale> equivalentLocales = getLocalizedStringsByKeyByLocale().keySet().stream()
				.filter(candidate -> CldrLocaleData.equivalent(candidate, locale))
				.sorted(Comparator.comparing(Locale::toLanguageTag))
				.collect(Collectors.toList());
		Optional<@NonNull Locale> preferredLocale = preferredLocaleForRange(locale.toLanguageTag(), equivalentLocales);

		if (preferredLocale.isPresent())
			return new LocalizedStringsMatch(preferredLocale.get(),
					requireNonNull(getLocalizedStringsByKeyByLocale().get(preferredLocale.get())));

		return null;
	}

	@NonNull
	private Optional<@NonNull Locale> lookupMatchByFallbackCandidates(@NonNull String range,
																																		@NonNull List<@NonNull Locale> availableLocales) {
		requireNonNull(range);
		requireNonNull(availableLocales);

		if (range.contains("*"))
			return Optional.empty();

		List<@NonNull Locale> fallbackLocales = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag(range));

		for (Locale candidateLocale : fallbackLocales) {
			String candidateTag = candidateLocale.toLanguageTag();

			for (Locale locale : availableLocales)
				if (locale.toLanguageTag().equalsIgnoreCase(candidateTag))
					return Optional.of(locale);
		}

		for (Locale candidateLocale : fallbackLocales) {
			List<@NonNull Locale> equivalentMatches = availableLocales.stream()
					.filter(locale -> CldrLocaleData.equivalent(locale, candidateLocale))
					.collect(Collectors.toList());
			Optional<@NonNull Locale> equivalentMatch = preferredLocaleForRange(candidateLocale.toLanguageTag(),
					equivalentMatches);

			if (equivalentMatch.isPresent())
				return equivalentMatch;
		}

		return Optional.empty();
	}

	@NonNull
	private Optional<@NonNull Locale> preferredLocaleForRange(@NonNull String range,
																								 @NonNull List<@NonNull Locale> candidates) {
		requireNonNull(range);
		requireNonNull(candidates);

		if (candidates.isEmpty())
			return Optional.empty();

		if (candidates.size() == 1)
			return Optional.of(candidates.get(0));

		String canonicalRange = CldrLocaleData.canonicalLanguageTag(range);
		String primary = normalizedLanguageCode(canonicalRange.split("-")[0]);
		Optional<@NonNull Locale> tiebreakerMatch = lookupMatchByTiebreakers(primary, candidates);

		if (tiebreakerMatch.isPresent())
			return tiebreakerMatch;

		if (candidates.contains(getFallbackLocale()))
			return Optional.of(getFallbackLocale());

		return Optional.of(candidates.get(0));
	}

	private boolean matchesLikelyLanguageScript(@NonNull Locale requestedLocale, @NonNull Locale candidateLocale) {
		requireNonNull(requestedLocale);
		requireNonNull(candidateLocale);

		Optional<String> requestedLanguageScript = CldrLocaleData.languageScriptForLikelySubtag(requestedLocale);
		Optional<String> candidateLanguageScript = CldrLocaleData.languageScriptForLikelySubtag(candidateLocale);
		return !requestedLanguageScript.isPresent() || !candidateLanguageScript.isPresent() ||
				requestedLanguageScript.get().equalsIgnoreCase(candidateLanguageScript.get());
	}

	@NonNull
	private Optional<@NonNull Locale> lookupMatchByLikelySubtag(@NonNull String range,
																															@NonNull List<@NonNull Locale> availableLocales) {
		requireNonNull(range);
		requireNonNull(availableLocales);

		if (range.contains("*"))
			return Optional.empty();

		if (CldrLocaleData.hasUndeterminedLanguage(range))
			return Optional.empty();

		Optional<String> likelySubtag = CldrLocaleData.languageScriptForLikelySubtag(range);

		if (!likelySubtag.isPresent())
			return Optional.empty();

		List<@NonNull Locale> matchingLocales = new ArrayList<>();

		for (Locale locale : availableLocales) {
			if (CldrLocaleData.hasUndeterminedLanguage(locale.toLanguageTag()))
				continue;

			Optional<String> availableLikelySubtag = CldrLocaleData.languageScriptForLikelySubtag(locale);

			if (availableLikelySubtag.isPresent() && availableLikelySubtag.get().equalsIgnoreCase(likelySubtag.get()))
				matchingLocales.add(locale);
		}

		if (matchingLocales.isEmpty())
			return Optional.empty();

		if (matchingLocales.size() == 1)
			return Optional.of(matchingLocales.get(0));

		String primary = normalizedLanguageCode(range.split("-")[0]);
		Optional<@NonNull Locale> tiebreakerMatch = lookupMatchByTiebreakers(primary, matchingLocales);

		if (tiebreakerMatch.isPresent())
			return tiebreakerMatch;

		if (matchingLocales.contains(getFallbackLocale()))
			return Optional.of(getFallbackLocale());

		return Optional.of(matchingLocales.get(0));
	}

	@NonNull
	private Optional<@NonNull Locale> lookupMatchByTiebreakers(@NonNull String languageCode,
																							 @NonNull List<@NonNull Locale> candidates) {
		requireNonNull(languageCode);
		requireNonNull(candidates);

		@Nullable List<@NonNull Locale> tiebreakers = getTiebreakerLocalesByLanguageCode().get(languageCode);

		if (tiebreakers != null)
			for (Locale tiebreaker : tiebreakers)
				if (candidates.contains(tiebreaker))
					return Optional.of(tiebreaker);

		return Optional.empty();
	}

	@NonNull
	private static String normalizedTiebreakerLanguageCode(@NonNull String languageCode) {
		if (languageCode == null)
			throw new IllegalArgumentException("A tiebreaker language code must not be null");

		Locale languageLocale;

		try {
			languageLocale = new Locale.Builder().setLanguage(languageCode).build();
		} catch (IllformedLocaleException exception) {
			throw new IllegalArgumentException(format(
					"Tiebreaker language code '%s' must be a well-formed primary language subtag", languageCode),
					exception);
		}

		return LocaleUtils.normalizedLanguage(languageLocale)
				.orElseThrow(() -> new IllegalArgumentException(format(
						"Tiebreaker language code '%s' must identify a primary language", languageCode)));
	}

	@NonNull
	private static String normalizedLanguageCode(@NonNull String languageCode) {
		requireNonNull(languageCode);
		return LocaleUtils.normalizedLanguage(Locale.forLanguageTag(languageCode))
				.orElse(languageCode)
				.toLowerCase(Locale.ROOT);
	}

	@Nullable
	private static Object unwrapOptional(@Nullable Object value) {
		if (value instanceof Optional)
			return ((Optional<?>) value).orElse(null);

		return value;
	}

	/**
	 * Gets the set of localized strings for each locale.
	 *
	 * @return the set of localized strings for each locale, not null
	 */
	@NonNull
	public Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> getLocalizedStringsByLocale() {
		return localizedStringsByLocale;
	}

	/**
	 * Gets the locales for which localized strings were supplied.
	 *
	 * @return the supported locales, not null
	 */
	@NonNull
	public Set<@NonNull Locale> getSupportedLocales() {
		List<@NonNull Locale> sortedLocales = new ArrayList<>(getLocalizedStringsByLocale().keySet());
		sortedLocales.sort(Comparator.comparing(Locale::toLanguageTag));
		return Collections.unmodifiableSet(new LinkedHashSet<>(sortedLocales));
	}

	/**
	 * Gets the localized string keys supplied for the given locale.
	 *
	 * @param locale locale to inspect, not null
	 * @return the localized string keys for the locale, not null
	 * @throws IllegalArgumentException if the locale is not supported
	 */
	@NonNull
	public Set<@NonNull String> getKeysForLocale(@NonNull Locale locale) {
		LocaleUtils.requireWellFormed(locale, "Locale");

		@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings =
				getLocalizedStringsByKeyByLocale().get(locale);

		if (localizedStrings == null)
			throw new IllegalArgumentException(format("Locale '%s' is not supported", locale.toLanguageTag()));

		Set<@NonNull String> keys = new TreeSet<>(localizedStrings.keySet());
		return Collections.unmodifiableSet(keys);
	}

	/**
	 * Gets the keys supplied by {@code sourceLocale} but missing from {@code targetLocale}.
	 *
	 * @param sourceLocale locale whose keys are used as the source set, not null
	 * @param targetLocale locale whose keys are compared against the source set, not null
	 * @return keys present in {@code sourceLocale} and missing from {@code targetLocale}, not null
	 * @throws IllegalArgumentException if either locale is not supported
	 */
	@NonNull
	public Set<@NonNull String> getMissingKeys(@NonNull Locale sourceLocale, @NonNull Locale targetLocale) {
		LocaleUtils.requireWellFormed(sourceLocale, "Source locale");
		LocaleUtils.requireWellFormed(targetLocale, "Target locale");

		if (!getLocalizedStringsByKeyByLocale().containsKey(sourceLocale))
			throw new IllegalArgumentException(format("Source locale '%s' is not supported", sourceLocale.toLanguageTag()));

		if (!getLocalizedStringsByKeyByLocale().containsKey(targetLocale))
			throw new IllegalArgumentException(format("Target locale '%s' is not supported", targetLocale.toLanguageTag()));

		Set<@NonNull String> missingKeys = new TreeSet<>(getKeysForLocale(sourceLocale));
		missingKeys.removeAll(getKeysForLocale(targetLocale));
		return Collections.unmodifiableSet(missingKeys);
	}

	/**
	 * Gets the locale supplier.
	 *
	 * @return the locale supplier, or null when a locale-match supplier is configured
	 */
	@Nullable
	public Function<@NonNull LocaleMatcher, @NonNull Locale> getLocaleSupplier() {
		return this.localeSupplier;
	}

	/** @return locale-match supplier, or null when a locale supplier is configured */
	@Nullable
	public Function<@NonNull LocaleMatcher, @NonNull LocaleMatchResult> getLocaleMatchSupplier() {
		return localeMatchSupplier;
	}

	/**
	 * Gets the mapping of a mapping of an ISO 639 language code to its ordered "tiebreaker" fallback locales.
	 *
	 * @return the per-language-code "tiebreaker" locales, not null
	 */
	@NonNull
	public Map<@NonNull String, @NonNull List<@NonNull Locale>> getTiebreakerLocalesByLanguageCode() {
		return this.tiebreakerLocalesByLanguageCode;
	}

	/**
	 * Gets the fallback locale.
	 *
	 * @return the fallback locale, not null
	 */
	@NonNull
	public Locale getFallbackLocale() {
		return fallbackLocale;
	}

	/**
	 * Gets the string interpolator used to merge placeholders into translations.
	 *
	 * @return the string interpolator, not null
	 */
	@NonNull
	protected StringInterpolator getStringInterpolator() {
		return stringInterpolator;
	}

	/**
	 * Gets the expression evaluator used to determine if alternative expressions match the evaluation context.
	 *
	 * @return the expression evaluator, not null
	 */
	@NonNull
	protected ExpressionEvaluator getExpressionEvaluator() {
		return expressionEvaluator;
	}

	/**
	 * Gets the phonetic resolver used to determine phonetic categories.
	 *
	 * @return the phonetic resolver, not null
	 */
	@NonNull
	protected PhoneticResolver getPhoneticResolver() {
		return phoneticResolver;
	}

	/**
	 * Gets the handler used to decide how lookup failures are handled.
	 *
	 * @return the handler, not null
	 */
	@NonNull
	public TranslationFailureHandler getTranslationFailureHandler() {
		return translationFailureHandler;
	}

	/**
	 * Gets the policy used to decide whether failed locale attempts continue to fallback candidates.
	 *
	 * @return locale-fallback policy, not null
	 */
	@NonNull
	public TranslationFallbackPolicy getTranslationFallbackPolicy() {
		return translationFallbackPolicy;
	}

	/**
	 * Gets the safety limits used for localized strings construction and translation evaluation.
	 *
	 * @return runtime limits, not null
	 */
	@NonNull
	public TranslationRuntimeLimits getRuntimeLimits() {
		return runtimeLimits;
	}

	/**
	 * Gets the bidirectional isolation behavior used for caller-supplied placeholder values.
	 *
	 * @return the bidi isolation behavior, not null
	 */
	@NonNull
	public BidiIsolation getBidiIsolation() {
		return bidiIsolation;
	}

	/**
	 * Gets our "master" cache of localized strings by key by locale.
	 *
	 * @return the cache of localized strings by key by locale, not null
	 */
	@NonNull
	protected Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> getLocalizedStringsByKeyByLocale() {
		return localizedStringsByKeyByLocale;
	}

	@Immutable
	private static final class ResolvedFragment {
		@NonNull private final String template;
		@NonNull private final String selectionDescription;

		private ResolvedFragment(@NonNull String template, @NonNull String selectionDescription) {
			this.template = requireNonNull(template);
			this.selectionDescription = requireNonNull(selectionDescription);
		}

		@NonNull
		private String getTemplate() {
			return template;
		}

		@NonNull
		private String getSelectionDescription() {
			return selectionDescription;
		}
	}

	@Immutable
	private static final class PlaceholderBinding {
		@NonNull private final PlaceholderDefinition definition;
		@NonNull private final String declaringPath;

		private PlaceholderBinding(@NonNull PlaceholderDefinition definition, @NonNull String declaringPath) {
			this.definition = requireNonNull(definition);
			this.declaringPath = requireNonNull(declaringPath);
		}

		@NonNull
		private PlaceholderDefinition getDefinition() {
			return definition;
		}

		@NonNull
		private String getDeclaringPath() {
			return declaringPath;
		}
	}

	private static final class GeneratedExpansionBudget {
		private final long maximumCharacters;
		private long consumedCharacters;

		private GeneratedExpansionBudget(long maximumCharacters) {
			this.maximumCharacters = maximumCharacters;
		}

		private void consume(long characters, @NonNull String key) {
			consumedCharacters = Math.addExact(consumedCharacters, characters);

			if (consumedCharacters > maximumCharacters)
				throw new IllegalStateException(format(
						"Generated placeholder expansion for key '%s' exceeds the cumulative limit of %d characters",
						key, maximumCharacters));
		}
	}

	private static final class LocaleLookup {
		@NonNull private final Locale locale;
		@NonNull private final LocaleMatchResult localeMatchResult;

		private LocaleLookup(@NonNull Locale locale, @NonNull LocaleMatchResult localeMatchResult) {
			this.locale = requireNonNull(locale);
			this.localeMatchResult = requireNonNull(localeMatchResult);
		}

		@NonNull
		private Locale getLocale() {
			return locale;
		}

		@NonNull
		private LocaleMatchResult getLocaleMatchResult() {
			return localeMatchResult;
		}
	}

	private static final class LocalizedStringsMatch {
		@NonNull private final Locale locale;
		@NonNull private final Map<@NonNull String, @NonNull LocalizedString> localizedStrings;

		private LocalizedStringsMatch(@NonNull Locale locale,
											@NonNull Map<@NonNull String, @NonNull LocalizedString> localizedStrings) {
			this.locale = requireNonNull(locale);
			this.localizedStrings = requireNonNull(localizedStrings);
		}

		@NonNull private Locale getLocale() { return locale; }
		@NonNull private Map<@NonNull String, @NonNull LocalizedString> getLocalizedStrings() { return localizedStrings; }
	}

	private static final class LocaleFallbackCandidate {
		@NonNull private final Locale locale;
		@Nullable private final Map<@NonNull String, @NonNull LocalizedString> localizedStrings;

		private LocaleFallbackCandidate(@NonNull Locale locale,
												 @Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings) {
			this.locale = requireNonNull(locale);
			this.localizedStrings = localizedStrings;
		}

		@NonNull private Locale getLocale() { return locale; }
		@Nullable private Map<@NonNull String, @NonNull LocalizedString> getLocalizedStrings() { return localizedStrings; }
	}

	@Immutable
	private static final class EffectiveLanguageRangeMatch {
		@NonNull private final LanguageRange languageRange;
		@NonNull private final LanguageRangeSpecificity specificity;
		@NonNull private final LocaleMatchType matchType;
		private final double weight;

		private EffectiveLanguageRangeMatch(@NonNull LanguageRange languageRange,
																				@NonNull LanguageRangeSpecificity specificity,
																				@NonNull LocaleMatchType matchType, double weight) {
			this.languageRange = requireNonNull(languageRange);
			this.specificity = requireNonNull(specificity);
			this.matchType = requireNonNull(matchType);
			this.weight = weight;
		}

		@NonNull private LanguageRange getLanguageRange() { return languageRange; }
		@NonNull private LocaleMatchType getMatchType() { return matchType; }
		private double getWeight() { return weight; }
	}

	@Immutable
	private enum LanguageRangeMatchCategory {
		WILDCARD(false),
		PRIMARY_LANGUAGE(false),
		LIKELY_SUBTAG(false),
		CLDR_FALLBACK(false),
		DIRECT_STRUCTURAL(true),
		CANONICAL(true),
		EXACT(true);

		private final boolean eligibleForExclusion;

		LanguageRangeMatchCategory(boolean eligibleForExclusion) {
			this.eligibleForExclusion = eligibleForExclusion;
		}

		private boolean isEligibleForExclusion() {
			return eligibleForExclusion;
		}
	}

	/**
	 * Locale-range specificity ordered without additive score bands. Match category always outranks structural
	 * depth, so even an arbitrarily long range cannot spill into another category. RFC 4647 ignores noninitial wildcard
	 * subtags, so they are removed before categorization and cannot manufacture either depth or specificity. Fallback
	 * distance is considered last, with a nearer fallback considered more specific.
	 */
	@Immutable
	private static final class LanguageRangeSpecificity implements Comparable<@NonNull LanguageRangeSpecificity> {
		@NonNull private final LanguageRangeMatchCategory category;
		private final int structuralDepth;
		private final int fallbackDistance;

		private LanguageRangeSpecificity(@NonNull LanguageRangeMatchCategory category, int structuralDepth,
											 int fallbackDistance) {
			this.category = requireNonNull(category);
			this.structuralDepth = structuralDepth;
			this.fallbackDistance = fallbackDistance;
		}

		private boolean isEligibleForExclusion() {
			return category.isEligibleForExclusion();
		}

		@Override
		public int compareTo(@NonNull LanguageRangeSpecificity other) {
			requireNonNull(other);
			int categoryComparison = category.compareTo(other.category);

			if (categoryComparison != 0)
				return categoryComparison;

			int depthComparison = Integer.compare(structuralDepth, other.structuralDepth);

			if (depthComparison != 0)
				return depthComparison;

			return Integer.compare(other.fallbackDistance, fallbackDistance);
		}
	}

	/**
	 * Insertion-ordered set view backed by the already key-validated localized-string map.
	 * Construction and iteration never invoke structural {@link LocalizedString#hashCode()}.
	 */
	@Immutable
	private static final class LocalizedStringSet extends AbstractSet<@NonNull LocalizedString> {
		@NonNull private final Map<@NonNull String, @NonNull LocalizedString> localizedStringsByKey;

		private LocalizedStringSet(
				@NonNull Map<@NonNull String, @NonNull LocalizedString> localizedStringsByKey) {
			this.localizedStringsByKey = requireNonNull(localizedStringsByKey);
		}

		@Override
		@NonNull
		public Iterator<@NonNull LocalizedString> iterator() {
			return localizedStringsByKey.values().iterator();
		}

		@Override
		public int size() {
			return localizedStringsByKey.size();
		}

		@Override
		public boolean contains(@Nullable Object value) {
			if (!(value instanceof LocalizedString))
				return false;

			LocalizedString localizedString = (LocalizedString) value;
			LocalizedString candidate = localizedStringsByKey.get(localizedString.getKey());
			return localizedString.equals(candidate);
		}
	}

	private void throwExceptionFor(@NonNull TranslationFailure translationFailure, @NonNull String message) {
		requireNonNull(translationFailure);
		requireNonNull(message);

		if (translationFailure.getCause().isPresent()) {
			Throwable cause = translationFailure.getCause().get();

			if (cause instanceof RuntimeException)
				throw (RuntimeException) cause;

			if (cause instanceof Error)
				throw (Error) cause;
		}

		throw new MissingTranslationException(message, translationFailure.getKey(), translationFailure.getPlaceholders(),
				translationFailure.getLookupLocale(), translationFailure.getLocaleMatchResult().orElse(null),
				translationFailure.getReason(), translationFailure.getAttemptedLocales());
	}
}
