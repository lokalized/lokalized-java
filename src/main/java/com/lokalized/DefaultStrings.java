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
import com.lokalized.LocalizedString.LanguageFormTranslationRange;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
import java.util.logging.Logger;
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
	private final Function<LocaleMatcher, Locale> localeSupplier;
	@Nullable
	private final Function<LocaleMatcher, LocaleMatchResult> localeMatchSupplier;
	@NonNull
	private final Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode;
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
	@NonNull
	private final Logger logger;

	/**
	 * Cache of localized strings by key by locale.
	 * <p>
	 * This is our "master" reference localized string storage that other data structures will point to.
	 */
	@NonNull
	private final Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> localizedStringsByKeyByLocale;
	@NonNull
	private final Map<@NonNull LocalizedString, @NonNull List<@NonNull Token>> compiledExpressionTokensByAlternative;

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
													 @NonNull Function<LocaleMatcher, Locale> localeSupplier,
													 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
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
													 @NonNull Function<LocaleMatcher, Locale> localeSupplier,
													 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
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
													 @NonNull Function<LocaleMatcher, Locale> localeSupplier,
													 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
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
															 @NonNull Function<LocaleMatcher, Locale> localeSupplier,
															 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
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
																 @Nullable Function<LocaleMatcher, Locale> localeSupplier,
																 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
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
																 @Nullable Function<LocaleMatcher, Locale> localeSupplier,
																 @Nullable Function<LocaleMatcher, LocaleMatchResult> localeMatchSupplier,
																 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
																 @Nullable TranslationFailureHandler translationFailureHandler,
																 @Nullable PhoneticResolver phoneticResolver,
																 @Nullable BidiIsolation bidiIsolation,
																 @Nullable TranslationFallbackPolicy translationFallbackPolicy,
																 @Nullable TranslationRuntimeLimits runtimeLimits) {
		requireNonNull(fallbackLocale);
		requireNonNull(localizedStringSupplier, format("You must specify a 'localizedStringSupplier' when creating a %s instance", DefaultStrings.class.getSimpleName()));

		if ((localeSupplier == null) == (localeMatchSupplier == null))
			throw new IllegalArgumentException(format("You must specify exactly one of 'localeSupplier' or 'localeMatchSupplier' when creating a %s instance",
					DefaultStrings.class.getSimpleName()));

		this.logger = Logger.getLogger(LoggerType.STRINGS.getLoggerName());

		Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>> suppliedLocalizedStringsByLocale = localizedStringSupplier.get();

		if (suppliedLocalizedStringsByLocale == null)
			suppliedLocalizedStringsByLocale = Collections.emptyMap();

		// Defensive copy of iterator to unmodifiable set
		Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = suppliedLocalizedStringsByLocale.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> entry.getKey(),
						entry -> {
							Set<@NonNull LocalizedString> localizedStrings = new LinkedHashSet<>();
							entry.getValue().forEach(localizedStrings::add);
							return Collections.unmodifiableSet(localizedStrings);
						}
				));

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

		this.localizedStringsByLocale = Collections.unmodifiableMap(localizedStringsByLocale);

		// Make our own mapping of tiebreakers based on the provided mapping.
		// First, defensive copy, then add to the map as needed below.
		Map<@NonNull String, @Nullable List<@NonNull Locale>> internalTiebreakerLocalesByLanguageCode = new HashMap<>();

		if (tiebreakerLocalesByLanguageCode != null) {
			for (Entry<@NonNull String, @Nullable List<@NonNull Locale>> entry : tiebreakerLocalesByLanguageCode.entrySet()) {
				@Nullable List<@NonNull Locale> locales = entry.getValue();
				internalTiebreakerLocalesByLanguageCode.put(normalizedLanguageCode(entry.getKey()),
						locales == null ? null : new ArrayList<>(locales));
			}
		}

		// Verify tiebreakers are provided to support locale resolution when ambiguity exists.
		// For each language code, if there is more than 1 localized strings file that matches it, tiebreakers must be provided.
		Map<@NonNull String, @NonNull Set<@NonNull Locale>> supportedLocalesByLanguageCode = new HashMap<>(localizedStringsByLocale.size());

		for (Locale supportedLocale : localizedStringsByLocale.keySet()) {
			String languageCode = LocaleUtils.normalizedLanguage(supportedLocale)
					.orElse(supportedLocale.getLanguage());
			Set<@NonNull Locale> locales = supportedLocalesByLanguageCode.get(languageCode);

			if (locales == null) {
				locales = new HashSet<>();
				supportedLocalesByLanguageCode.put(languageCode, locales);
			}

			locales.add(supportedLocale);
		}

		for (Entry<@NonNull String, @NonNull Set<@NonNull Locale>> entry : supportedLocalesByLanguageCode.entrySet()) {
			String languageCode = entry.getKey();
			List<@NonNull Locale> locales = entry.getValue().stream()
					.sorted(Comparator.comparing(Locale::toLanguageTag))
					.collect(Collectors.toList());

			if (locales.size() == 1) {
				// If there is exactly 1 locale for the language code, it's its own "identity" tiebreaker.
				internalTiebreakerLocalesByLanguageCode.put(languageCode, new ArrayList<>(locales));
			} else if (locales.size() > 1) {
				// We need to provide tiebreakers if a locale has more than 1 strings file.
				@Nullable List<@NonNull Locale> providedTiebreakerLocales = internalTiebreakerLocalesByLanguageCode.get(languageCode);

				if (providedTiebreakerLocales == null || providedTiebreakerLocales.size() == 0) {
					throw new IllegalArgumentException(format("You must specify tiebreaker locales via 'tiebreakerLocalesByLanguageCode' to resolve ambiguity for language code '%s' because localized strings exist for the following locale[s]: %s",
							languageCode, locales.stream().map(locale -> locale.toLanguageTag()).collect(Collectors.toList())));
				} else {
					// First, verify that all tiebreakers actually exist
					Set<@NonNull Locale> supportedLocales = localizedStringsByLocale.keySet();

					for (Locale providedTiebreakerLocale : providedTiebreakerLocales)
						if (!supportedLocales.contains(providedTiebreakerLocale))
							throw new IllegalArgumentException(format("Tiebreaker locale '%s' specified in 'tiebreakerLocalesByLanguageCode' does not have a localized strings file. Supported locales are: %s",
									providedTiebreakerLocale.toLanguageTag(), supportedLocales.stream().map(supportedLocale -> supportedLocale.toLanguageTag()).sorted().collect(Collectors.toList())));

					// Next, verify that tiebreakers are exhaustively specified
					List<@NonNull Locale> missingLocales = new ArrayList<>(locales.size());

					for (Locale locale : locales)
						if (!providedTiebreakerLocales.contains(locale))
							missingLocales.add(locale);

					if (missingLocales.size() > 0)
						throw new IllegalArgumentException(format("Your 'tiebreakerLocalesByLanguageCode' specifies locale[s] %s for language code '%s', but you are missing entries for the following locale[s]: %s",
								providedTiebreakerLocales.stream().map(providedTiebreakerLocale -> providedTiebreakerLocale.toLanguageTag()).sorted().collect(Collectors.toList()),
								languageCode,
								missingLocales.stream().map(missingLocale -> missingLocale.toLanguageTag()).sorted().collect(Collectors.toList())));
				}

				internalTiebreakerLocalesByLanguageCode.put(languageCode, new ArrayList<>(providedTiebreakerLocales));
			} else {
				// Should never occur
				throw new IllegalStateException("No locales match language code");
			}
		}

		Map<@NonNull String, @Nullable List<@NonNull Locale>> finalizedTiebreakerLocalesByLanguageCode = new HashMap<>(internalTiebreakerLocalesByLanguageCode.size());

		for (Entry<@NonNull String, @Nullable List<@NonNull Locale>> entry : internalTiebreakerLocalesByLanguageCode.entrySet()) {
			@Nullable List<@NonNull Locale> locales = entry.getValue();
			finalizedTiebreakerLocalesByLanguageCode.put(entry.getKey(),
					locales == null ? null : Collections.unmodifiableList(new ArrayList<>(locales)));
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
		Map<@NonNull LocalizedString, @NonNull List<@NonNull Token>> compiledExpressionTokensByAlternative =
				new IdentityHashMap<>();

		for (Entry<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> entry : localizedStringsByLocale.entrySet()) {
			Locale locale = entry.getKey();
			Map<@NonNull String, @NonNull LocalizedString> localizedStringsByKey = new LinkedHashMap<>();

			for (LocalizedString localizedString : entry.getValue()) {
				if (localizedString == null)
					throw new IllegalArgumentException(format("Null localized string encountered for locale '%s'", locale.toLanguageTag()));

				validateLocalizedString(locale, localizedString);
				validateRuntimeLimits(localizedString);
				compileAlternativeExpressions(localizedString, compiledExpressionTokensByAlternative);

				String key = localizedString.getKey();
				LocalizedString existing = localizedStringsByKey.putIfAbsent(key, localizedString);

				if (existing != null)
					throw new IllegalArgumentException(format("Duplicate localized string key '%s' encountered for locale '%s'", key, locale.toLanguageTag()));
			}

			localizedStringsByKeyByLocale.put(locale, Collections.unmodifiableMap(localizedStringsByKey));
		}

		this.localizedStringsByKeyByLocale = Collections.unmodifiableMap(localizedStringsByKeyByLocale);
		this.compiledExpressionTokensByAlternative = Collections.unmodifiableMap(compiledExpressionTokensByAlternative);

		this.localeSupplier = localeSupplier;
		this.localeMatchSupplier = localeMatchSupplier;
	}

	private void compileAlternativeExpressions(@NonNull LocalizedString localizedString,
																			 @NonNull Map<@NonNull LocalizedString, @NonNull List<@NonNull Token>> compiledTokens) {
		requireNonNull(localizedString);
		requireNonNull(compiledTokens);

		for (LocalizedString alternative : localizedString.getAlternatives()) {
			if (compiledTokens.containsKey(alternative))
				continue;

			List<@NonNull Token> expressionTokens =
					getExpressionEvaluator().parseAndValidateExpressionTokens(alternative.getKey());

			compiledTokens.put(alternative, Collections.unmodifiableList(new ArrayList<>(expressionTokens)));
			compileAlternativeExpressions(alternative, compiledTokens);
		}
	}

	private static void validateLocalizedString(@NonNull Locale locale, @NonNull LocalizedString localizedString) {
		requireNonNull(locale);
		requireNonNull(localizedString);
		LocalizedStringValidator.validate(locale, localizedString);
	}

	private void validateRuntimeLimits(@NonNull LocalizedString localizedString) {
		requireNonNull(localizedString);

		for (Entry<@NonNull String, @NonNull LanguageFormTranslation> entry :
				localizedString.getLanguageFormTranslationsByPlaceholder().entrySet()) {
			int ruleCount = entry.getValue().getTranslationRules().size();
			int maximumRuleCount = getRuntimeLimits().getMaximumSelectorRules();

			if (ruleCount > maximumRuleCount)
				throw new IllegalArgumentException(format(
						"Selector-based translation for placeholder '%s' in key '%s' contains %d rules, which exceeds the configured maximum of %d",
						entry.getKey(), localizedString.getKey(), ruleCount, maximumRuleCount));
		}

		for (LocalizedString alternative : localizedString.getAlternatives())
			validateRuntimeLimits(alternative);
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
		Map<@NonNull String, @Nullable Object> immutableContext = Collections.unmodifiableMap(new HashMap<>(placeholders));
		RuntimeException firstFallbackFailure = null;
		boolean noMatchingAlternativeEncountered = false;
		List<@NonNull CatalogCandidate> catalogCandidates = fallbackCatalogCandidates(locale);
		List<@NonNull Locale> attemptedLocales = new ArrayList<>(catalogCandidates.size());

		for (int candidateIndex = 0; candidateIndex < catalogCandidates.size(); ++candidateIndex) {
			CatalogCandidate catalogCandidate = catalogCandidates.get(candidateIndex);
			Locale candidateLocale = catalogCandidate.getLocale();
			attemptedLocales.add(candidateLocale);
			Map<@NonNull String, @Nullable Object> mutableContext = new HashMap<>(immutableContext);
			@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = catalogCandidate.getLocalizedStrings();
			TranslationFailureReason attemptFailureReason = TranslationFailureReason.MISSING_TRANSLATION;
			@Nullable Throwable attemptCause = null;

			if (localizedStrings != null) {
				LocalizedString localizedString = localizedStrings.get(key);

				if (localizedString != null) {
					try {
						Optional<String> translation = getInternal(key, localizedString, mutableContext, immutableContext,
								candidateLocale, bidiIsolation);

						if (translation.isPresent())
							return new TranslationResult(key, translation.get(), locale, localeMatchResult, candidateLocale, attemptedLocales,
									TranslationResultStatus.TRANSLATED, null, null);

						attemptFailureReason = TranslationFailureReason.NO_MATCHING_ALTERNATIVE;
						noMatchingAlternativeEncountered = true;
						logger.finer(format(
								"No alternative produced a translation and no default translation was provided for key '%s' and locale '%s'",
								key, candidateLocale.toLanguageTag()));
					} catch (RuntimeException e) {
						attemptFailureReason = TranslationFailureReason.RESOLUTION_FAILURE;
						attemptCause = e;

						if (firstFallbackFailure == null)
							firstFallbackFailure = e;
						else if (firstFallbackFailure != e)
							firstFallbackFailure.addSuppressed(e);

						logger.finer(format("Unable to resolve key '%s' for locale '%s'. Cause: %s",
								key, candidateLocale.toLanguageTag(), e.getMessage()));
					}
				}
			}

			if (candidateIndex + 1 >= catalogCandidates.size())
				break;

			Boolean shouldTryNextLocale = requireNonNull(translationFallbackPolicy.shouldTryNextLocale(
					attemptFailureReason, candidateLocale, attemptCause), "translationFallbackPolicy returned null");

			if (!shouldTryNextLocale)
				break;
		}

		String message = format("No match for '%s' was found for locale '%s'.", key, locale.toLanguageTag());
		logger.finer(message);

		if (firstFallbackFailure != null)
			logger.finer(format("%s Invoking translation failure handler after resolution failure: %s", message, firstFallbackFailure.getMessage()));

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
	 * @param mutableContext   the mutable context for the translation, not null
	 * @param immutableContext an immutable snapshot of the user-supplied translation context, not null
	 * @param locale           the locale to use for evaluation, not null
	 * @param bidiIsolation    the bidirectional isolation behavior to apply, not null
	 * @return the translation, if possible (may not be possible if no translation value specified and no alternative expressions match), not null
	 */
	@NonNull
	protected Optional<String> getInternal(@NonNull String key, @NonNull LocalizedString localizedString,
																				 @NonNull Map<@NonNull String, @Nullable Object> mutableContext,
																				 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
																				 @NonNull Locale locale,
																				 @NonNull BidiIsolation bidiIsolation) {
		requireNonNull(key);
		requireNonNull(localizedString);
		requireNonNull(mutableContext);
		requireNonNull(immutableContext);
		requireNonNull(locale);
		requireNonNull(bidiIsolation);

		// First, see if any alternatives match by evaluating them
		for (LocalizedString alternative : localizedString.getAlternatives()) {
			if (alternativeMatches(alternative, mutableContext, locale)) {
				logger.finer(format("An alternative match for '%s' was found for key '%s'", alternative.getKey(), key));

				// If we have a matching alternative, recurse into it
				// Alternatives are ordered first-match rules. Once a condition matches, only that branch may resolve;
				// an unmatched nested subtree must not fall through to a later sibling.
				return getInternal(key, alternative, mutableContext, immutableContext, locale, bidiIsolation);
			}
		}

		if (!localizedString.getTranslation().isPresent())
			return Optional.empty();

		String translation = localizedString.getTranslation().get();
		Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder =
				localizedString.getLanguageFormTranslationsByPlaceholder();
		Set<@NonNull String> pendingGeneratedPlaceholderNames = new LinkedHashSet<>();
		Set<@NonNull String> resolvedGeneratedPlaceholderNames = new HashSet<>();
		enqueueGeneratedPlaceholderDependencies(translation, languageFormTranslationsByPlaceholder,
				pendingGeneratedPlaceholderNames, resolvedGeneratedPlaceholderNames);

		while (!pendingGeneratedPlaceholderNames.isEmpty()) {
			String placeholderName = pendingGeneratedPlaceholderNames.iterator().next();
			pendingGeneratedPlaceholderNames.remove(placeholderName);

			if (!resolvedGeneratedPlaceholderNames.add(placeholderName))
				continue;

			// File-defined generated placeholders take precedence over same-named caller values.
			mutableContext.remove(placeholderName);
			LanguageFormTranslation languageFormTranslation = languageFormTranslationsByPlaceholder.get(placeholderName);

			if (languageFormTranslation.isSelectorDriven()) {
				String generatedTranslation = resolveSelectorDrivenLanguageFormTranslation(key, placeholderName,
						languageFormTranslation, localizedString, immutableContext, locale);
				mutableContext.put(placeholderName, generatedTranslation);
				enqueueGeneratedPlaceholderDependencies(generatedTranslation, languageFormTranslationsByPlaceholder,
						pendingGeneratedPlaceholderNames, resolvedGeneratedPlaceholderNames);
				continue;
			}

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
				throw new IllegalArgumentException(format("You cannot mix-and-match language forms. Offending localized string was %s", localizedString));

			if (distinctLanguageForms == 0)
				continue;

			if (languageFormTranslation.getRange().isPresent() && translationsByCardinality.isEmpty())
				throw new IllegalArgumentException(format("Range-based translations are only supported for %s. Offending localized string was %s",
						Cardinality.class.getSimpleName(), localizedString));

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
						throw new IllegalStateException(format("Missing %s translation for range cardinality %s (start was %s, end was %s). Localized string was %s",
								Cardinality.class.getSimpleName(), rangeCardinality.name(), startCardinality.name(), endCardinality.name(), localizedString));

					mutableContext.put(placeholderName, cardinalityTranslation);
				} else {
					// Normal "non-range" cardinality
					if (value == null)
						throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
								languageFormTranslation.getValue().get(), key));

					Cardinality cardinality = cardinalityForValue(key, languageFormTranslation.getValue().get(),
							value, locale, "Placeholder");
					String cardinalityTranslation = translationsByCardinality.get(cardinality);

					if (cardinalityTranslation == null)
						throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
								Cardinality.class.getSimpleName(), cardinality.name(), localizedString));

					mutableContext.put(placeholderName, cardinalityTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Ordinality.class.getSimpleName(), ordinality.name(), localizedString));

				mutableContext.put(placeholderName, ordinalityTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Gender.class.getSimpleName(), gender.name(), localizedString));

				mutableContext.put(placeholderName, genderTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							GrammaticalCase.class.getSimpleName(), grammaticalCase.name(), localizedString));

				mutableContext.put(placeholderName, grammaticalCaseTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Definiteness.class.getSimpleName(), definiteness.name(), localizedString));

				mutableContext.put(placeholderName, definitenessTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Classifier.class.getSimpleName(), classifier.name(), localizedString));

				mutableContext.put(placeholderName, classifierTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Formality.class.getSimpleName(), formality.name(), localizedString));

				mutableContext.put(placeholderName, formalityTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Clusivity.class.getSimpleName(), clusivity.name(), localizedString));

				mutableContext.put(placeholderName, clusivityTranslation);
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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Animacy.class.getSimpleName(), animacy.name(), localizedString));

				mutableContext.put(placeholderName, animacyTranslation);
			}

			// Handle phonetics
			if (translationsByPhonetic.size() > 0) {
				if (languageFormTranslation.getRange().isPresent())
					throw new IllegalArgumentException(format("Phonetic translations cannot use ranges. Offending localized string was %s", localizedString));

				if (value == null)
					throw new IllegalArgumentException(format("Missing value for placeholder '%s' in key '%s'",
							languageFormTranslation.getValue().get(), key));

				Phonetic phonetic;

				if (value instanceof Phonetic) {
					phonetic = (Phonetic) value;
				} else if (value instanceof CharSequence) {
					PhoneticResolver resolver = getPhoneticResolver();
					phonetic = resolver.resolve(value.toString(), locale);

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
					throw new IllegalStateException(format("Missing %s translation for %s. Localized string was %s",
							Phonetic.class.getSimpleName(), phonetic.name(), localizedString));

				mutableContext.put(placeholderName, phoneticTranslation);
			}

			Object generatedTranslation = mutableContext.get(placeholderName);

			if (generatedTranslation instanceof String)
				enqueueGeneratedPlaceholderDependencies((String) generatedTranslation, languageFormTranslationsByPlaceholder,
						pendingGeneratedPlaceholderNames, resolvedGeneratedPlaceholderNames);
		}

		return Optional.of(interpolateTemplate(key, translation, mutableContext, immutableContext,
				languageFormTranslationsByPlaceholder.keySet(), locale, bidiIsolation, new HashMap<>(), new ArrayList<>(),
				new GeneratedExpansionBudget(getRuntimeLimits().getMaximumGeneratedExpansionCharacters()), 0));
	}

	private static void enqueueGeneratedPlaceholderDependencies(@NonNull String template,
																							@NonNull Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder,
																							@NonNull Set<@NonNull String> pendingGeneratedPlaceholderNames,
																							@NonNull Set<@NonNull String> resolvedGeneratedPlaceholderNames) {
		requireNonNull(template);
		requireNonNull(languageFormTranslationsByPlaceholder);
		requireNonNull(pendingGeneratedPlaceholderNames);
		requireNonNull(resolvedGeneratedPlaceholderNames);

		for (String placeholderName : StringInterpolator.placeholderNamesIn(template))
			if (languageFormTranslationsByPlaceholder.containsKey(placeholderName) &&
					!resolvedGeneratedPlaceholderNames.contains(placeholderName))
				pendingGeneratedPlaceholderNames.add(placeholderName);
	}

	@NonNull
	private String interpolateTemplate(@NonNull String key, @NonNull String template,
														 @NonNull Map<@NonNull String, @Nullable Object> generatedContext,
														 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
														 @NonNull Set<@NonNull String> fileDefinedPlaceholderNames,
														 @NonNull Locale locale, @NonNull BidiIsolation bidiIsolation,
																 @NonNull Map<@NonNull String, @NonNull String> expandedGeneratedValues,
																 @NonNull List<@NonNull String> generatedPlaceholderPath,
																 @NonNull GeneratedExpansionBudget generatedExpansionBudget, int depth) {
		requireNonNull(key);
		requireNonNull(template);
		requireNonNull(generatedContext);
		requireNonNull(immutableContext);
		requireNonNull(fileDefinedPlaceholderNames);
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
			if (fileDefinedPlaceholderNames.contains(placeholderName)) {
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
								generatedContext, immutableContext, fileDefinedPlaceholderNames, locale, bidiIsolation,
								expandedGeneratedValues, generatedPlaceholderPath, generatedExpansionBudget, depth + 1);
						expandedGeneratedValues.put(placeholderName, expandedValue);
						interpolationContext.put(placeholderName, expandedValue);
					} finally {
						generatedPlaceholderPath.remove(generatedPlaceholderPath.size() - 1);
					}
				}
			} else {
				Object value = unwrapOptional(immutableContext.get(placeholderName));

				if (value != null && shouldApplyBidiIsolation(locale, bidiIsolation))
					value = BidiUtils.isolate(value.toString());

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
					value = BidiUtils.isolate(value.toString());

				interpolationContext.put(placeholderName, value);
			}

			return getStringInterpolator().interpolate(key, interpolationContext,
					getRuntimeLimits().getMaximumInterpolatedOutputCharacters());
		} catch (RuntimeException e) {
			logger.finer(format("Unable to interpolate failure key '%s'; returning the raw key. Cause: %s", key, e.getMessage()));
			return key;
		}
	}

	private static boolean shouldApplyBidiIsolation(@NonNull Locale locale, @NonNull BidiIsolation bidiIsolation) {
		requireNonNull(locale);
		requireNonNull(bidiIsolation);

		switch (bidiIsolation) {
			case NONE:
				return false;
			case RTL_LOCALES:
				return BidiUtils.localeUsesRightToLeftScript(locale);
			default:
				throw new IllegalArgumentException(format("Unsupported %s value %s",
						BidiIsolation.class.getSimpleName(), bidiIsolation));
		}
	}

	@NonNull
	private String resolveSelectorDrivenLanguageFormTranslation(@NonNull String key, @NonNull String placeholderName,
																													 @NonNull LanguageFormTranslation languageFormTranslation,
																													 @NonNull LocalizedString localizedString,
																													 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
																													 @NonNull Locale locale) {
		requireNonNull(key);
		requireNonNull(placeholderName);
		requireNonNull(languageFormTranslation);
		requireNonNull(localizedString);
		requireNonNull(immutableContext);
		requireNonNull(locale);

		Map<@NonNull LanguageFormType, @NonNull LanguageForm> resolvedLanguageFormsByType = new LinkedHashMap<>();

		for (LanguageFormSelector selector : languageFormTranslation.getSelectors()) {
			Object selectorValue = unwrapOptional(immutableContext.get(selector.getValue()));

			if (selectorValue == null)
				throw new IllegalArgumentException(format("Missing value for selector '%s' in placeholder '%s' for key '%s'",
						selector.getValue(), placeholderName, key));

			resolvedLanguageFormsByType.put(selector.getForm(),
					resolveSelectorLanguageFormValue(key, selector.getValue(), selector.getForm(), selectorValue, locale));
		}

		@Nullable LanguageFormTranslationRule matchedRule = null;
		int matchedSpecificity = -1;

		for (LanguageFormTranslationRule translationRule : languageFormTranslation.getTranslationRules()) {
			if (!selectorTranslationRuleMatches(translationRule, resolvedLanguageFormsByType))
				continue;

			int specificity = translationRule.getWhenByLanguageFormType().size();

			if (matchedRule != null && specificity == matchedSpecificity)
				throw new IllegalStateException(format("Ambiguous selector-based translations for placeholder '%s' with selector values %s. " +
						"Localized string was %s", placeholderName, resolvedLanguageFormsByType, localizedString));

			if (matchedRule == null || specificity > matchedSpecificity) {
				matchedRule = translationRule;
				matchedSpecificity = specificity;
			}
		}

		if (matchedRule == null)
			throw new IllegalStateException(format("Missing selector-based translation for placeholder '%s' with selector values %s. " +
					"Localized string was %s", placeholderName, resolvedLanguageFormsByType, localizedString));

		return matchedRule.getValue();
	}

	private boolean selectorTranslationRuleMatches(@NonNull LanguageFormTranslationRule translationRule,
																								 @NonNull Map<@NonNull LanguageFormType, @NonNull LanguageForm> resolvedLanguageFormsByType) {
		requireNonNull(translationRule);
		requireNonNull(resolvedLanguageFormsByType);

		for (Entry<@NonNull LanguageFormType, @NonNull LanguageForm> ruleEntry : translationRule.getWhenByLanguageFormType().entrySet()) {
			LanguageForm resolvedLanguageForm = resolvedLanguageFormsByType.get(ruleEntry.getKey());

			if (!ruleEntry.getValue().equals(resolvedLanguageForm))
				return false;
		}

		return true;
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

	@NonNull
	private LanguageForm resolveSelectorLanguageFormValue(@NonNull String key, @NonNull String selectorValueName,
																										 @NonNull LanguageFormType languageFormType,
																										 @NonNull Object selectorValue,
																										 @NonNull Locale locale) {
		requireNonNull(key);
		requireNonNull(selectorValueName);
		requireNonNull(languageFormType);
		requireNonNull(selectorValue);
		requireNonNull(locale);

		switch (languageFormType) {
			case CARDINALITY:
				return cardinalityForValue(key, selectorValueName, selectorValue, locale, "Selector value");
			case ORDINALITY:
				return ordinalityForValue(key, selectorValueName, selectorValue, locale, "Selector value");
			case GENDER:
				if (!(selectorValue instanceof Gender))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Gender.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return (Gender) selectorValue;
			case CASE:
				if (!(selectorValue instanceof GrammaticalCase))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, GrammaticalCase.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return (GrammaticalCase) selectorValue;
			case DEFINITENESS:
				if (!(selectorValue instanceof Definiteness))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Definiteness.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return (Definiteness) selectorValue;
			case CLASSIFIER:
				if (!(selectorValue instanceof Classifier))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Classifier.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return (Classifier) selectorValue;
			case FORMALITY:
				if (!(selectorValue instanceof Formality))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Formality.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return (Formality) selectorValue;
			case CLUSIVITY:
				if (!(selectorValue instanceof Clusivity))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Clusivity.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return (Clusivity) selectorValue;
			case ANIMACY:
				if (!(selectorValue instanceof Animacy))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Animacy.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return (Animacy) selectorValue;
			case PHONETIC:
				if (selectorValue instanceof Phonetic)
					return (Phonetic) selectorValue;

				if (selectorValue instanceof CharSequence) {
					Phonetic phonetic = getPhoneticResolver().resolve(selectorValue.toString(), locale);

					if (phonetic == null)
						throw new IllegalArgumentException(format("%s returned null for selector value '%s' in key '%s'",
								PhoneticResolver.class.getSimpleName(), selectorValueName, key));

					return phonetic;
				}

				throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s or %s but was %s",
						selectorValueName, key, Phonetic.class.getSimpleName(), CharSequence.class.getSimpleName(),
						selectorValue.getClass().getSimpleName()));
			default:
				throw new IllegalArgumentException(format("Encountered unrecognized selector language form type %s", languageFormType));
		}
	}

	private boolean alternativeMatches(@NonNull LocalizedString alternative,
																		 @NonNull Map<@NonNull String, @Nullable Object> context,
																		 @NonNull Locale locale) {
		requireNonNull(alternative);
		requireNonNull(context);
		requireNonNull(locale);

		List<@NonNull Token> expressionTokens = compiledExpressionTokensByAlternative.get(alternative);

		if (expressionTokens == null)
			throw new IllegalStateException(format("No compiled expression was found for alternative '%s'", alternative.getKey()));

		return getExpressionEvaluator().evaluateReversePolishNotationTokens(expressionTokens, context, locale);
	}

	@NonNull
	@Override
	public LocaleMatchResult matchFor(@NonNull Locale locale) {
		requireNonNull(locale);
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

		if (languageRanges.size() > TranslationOptions.MAXIMUM_LANGUAGE_RANGES)
			throw new IllegalArgumentException(format("At most %d language ranges are supported, but received %d",
					TranslationOptions.MAXIMUM_LANGUAGE_RANGES, languageRanges.size()));

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
		Map<@NonNull Locale, @NonNull Double> effectiveWeightsByLocale = new LinkedHashMap<>();
		double highestEffectiveWeight = 0.0;

		for (Locale availableLocale : availableLocales) {
			double effectiveWeight = effectiveWeightFor(availableLocale, sortedLanguageRanges);

			if (effectiveWeight > 0.0) {
				effectiveWeightsByLocale.put(availableLocale, effectiveWeight);
				highestEffectiveWeight = Math.max(highestEffectiveWeight, effectiveWeight);
			}
		}

		if (highestEffectiveWeight > 0.0) {
			double requiredWeight = highestEffectiveWeight;
			availableLocales.removeIf(locale -> Double.compare(effectiveWeightsByLocale.getOrDefault(locale, 0.0), requiredWeight) != 0);
		} else {
			return noLocaleMatch(requestedLanguageRanges, consideredLocales);
		}

		// Walk through each LanguageRange in preference order
		for (LanguageRange languageRange : sortedLanguageRanges) {
			String range = languageRange.getRange(); // e.g. "pt" or "pt-PT"
			double weight = languageRange.getWeight();

			if (weight <= 0)
				continue;

			if ("*".equals(range))
				return localeMatch(preferredLocaleForWildcard(availableLocales), languageRange,
						highestEffectiveWeight, LocaleMatchType.WILDCARD, requestedLanguageRanges, consideredLocales);

			if (CldrLocaleData.hasUndeterminedLanguage(range))
				continue;

			String canonicalRange = CldrLocaleData.canonicalLanguageTag(range);

			// An actual exact catalog must win over a different catalog with a canonically equivalent tag.
			for (Locale locale : availableLocales)
				if (locale.toLanguageTag().equalsIgnoreCase(range))
					return localeMatch(locale, languageRange, highestEffectiveWeight, LocaleMatchType.EXACT,
							requestedLanguageRanges, consideredLocales);

			// CLDR-canonical tag match? Multiple deprecated aliases can collapse to the same canonical tag,
			// so honor configured tiebreakers rather than returning the first lexicographic alias.
			List<@NonNull Locale> canonicalMatches = availableLocales.stream()
					.filter(locale -> CldrLocaleData.canonicalLanguageTag(locale.toLanguageTag()).equalsIgnoreCase(canonicalRange))
					.collect(Collectors.toList());
			Optional<@NonNull Locale> canonicalMatch = preferredLocaleForRange(canonicalRange, canonicalMatches);

			if (canonicalMatch.isPresent())
				return localeMatch(canonicalMatch.get(), languageRange, highestEffectiveWeight,
						LocaleMatchType.CANONICAL, requestedLanguageRanges, consideredLocales);

			Optional<Locale> lookupMatch = lookupMatchByFallbackCandidates(range, availableLocales);

			if (lookupMatch.isPresent())
				return localeMatch(lookupMatch.get(), languageRange, highestEffectiveWeight,
						LocaleMatchType.CLDR_FALLBACK, requestedLanguageRanges, consideredLocales);

			Optional<Locale> likelySubtagMatch = lookupMatchByLikelySubtag(range, availableLocales);

			if (likelySubtagMatch.isPresent())
				return localeMatch(likelySubtagMatch.get(), languageRange, highestEffectiveWeight,
						LocaleMatchType.LIKELY_SUBTAG, requestedLanguageRanges, consideredLocales);

			// Primary-tag candidates (e.g. "pt" or "pt-XX")
			String primary = normalizedLanguageCode(range.split("-")[0]); // e.g. "pt"

			if ("*".equals(primary)) {
				List<Locale> filteredCandidates = Locale.filter(Collections.singletonList(languageRange), availableLocales,
						Locale.FilteringMode.EXTENDED_FILTERING);

				if (!filteredCandidates.isEmpty())
					return localeMatch(filteredCandidates.get(0), languageRange, highestEffectiveWeight,
							LocaleMatchType.EXTENDED_RANGE, requestedLanguageRanges, consideredLocales);

				continue;
			}

			List<@NonNull Locale> candidates = availableLocales.stream()
					.filter(locale -> LocaleUtils.normalizedLanguage(locale)
							.map(language -> language.equalsIgnoreCase(primary))
							.orElse(false))
					.filter(locale -> hasCompatibleLikelyScript(range, locale))
					.collect(Collectors.toList());

			if (candidates.isEmpty())
				continue; // try the next LanguageRange

			List<Locale> filteredCandidates = Locale.filter(Collections.singletonList(languageRange), candidates,
					Locale.FilteringMode.EXTENDED_FILTERING);

			boolean extendedRangeMatched = !filteredCandidates.isEmpty();

			if (extendedRangeMatched) {
				boolean hasSpecificMatch = filteredCandidates.stream()
						.anyMatch(locale -> !locale.toLanguageTag().equalsIgnoreCase(locale.getLanguage()));

				if (hasSpecificMatch)
					candidates = filteredCandidates;
			}

			if (candidates.size() == 1)
				return localeMatch(candidates.get(0), languageRange, highestEffectiveWeight,
						extendedRangeMatched ? LocaleMatchType.EXTENDED_RANGE : LocaleMatchType.PRIMARY_LANGUAGE,
						requestedLanguageRanges, consideredLocales);

			// Tie‐breaker list for this primary tag?
			@Nullable List<@NonNull Locale> tiebreakers = getTiebreakerLocalesByLanguageCode().get(primary);

			if (tiebreakers != null)
				for (Locale tiebreaker : tiebreakers)
					if (candidates.contains(tiebreaker))
						return localeMatch(tiebreaker, languageRange, highestEffectiveWeight,
								LocaleMatchType.PRIMARY_LANGUAGE, requestedLanguageRanges, consideredLocales);

			return localeMatch(candidates.get(0), languageRange, highestEffectiveWeight,
					LocaleMatchType.PRIMARY_LANGUAGE, requestedLanguageRanges, consideredLocales);
		}

		return noLocaleMatch(requestedLanguageRanges, consideredLocales);
	}

	@NonNull
	private LocaleMatchResult localeMatch(@NonNull Locale locale, @NonNull LanguageRange languageRange,
																					 double effectiveWeight, @NonNull LocaleMatchType matchType,
																					 @NonNull List<@NonNull LanguageRange> requestedLanguageRanges,
																					 @NonNull List<@NonNull Locale> consideredLocales) {
		return new LocaleMatchResult(requestedLanguageRanges, locale, languageRange, effectiveWeight, matchType,
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

	private double effectiveWeightFor(@NonNull Locale locale,
															@NonNull List<@NonNull LanguageRange> languageRanges) {
		requireNonNull(locale);
		requireNonNull(languageRanges);

		int bestSpecificity = -1;
		double effectiveWeight = -1.0;

		for (LanguageRange languageRange : languageRanges) {
			int specificity = languageRangeSpecificityFor(locale, languageRange);

			if (specificity < 0)
				continue;

			// Negative ranges exclude syntactic or canonical matches, not locales that are merely related
			// through Lokalized's CLDR parent/likely-subtag fallback heuristics. For example, en-US;q=0
			// must not also exclude en-GB.
			if (languageRange.getWeight() <= 0.0 && specificity < 8_000)
				continue;

			if (specificity > bestSpecificity ||
					(specificity == bestSpecificity && languageRange.getWeight() > effectiveWeight)) {
				bestSpecificity = specificity;
				effectiveWeight = languageRange.getWeight();
			}
		}

		return effectiveWeight;
	}

	private int languageRangeSpecificityFor(@NonNull Locale locale, @NonNull LanguageRange languageRange) {
		requireNonNull(locale);
		requireNonNull(languageRange);

		String range = languageRange.getRange();
		String localeTag = locale.toLanguageTag();

		if ("*".equals(range))
			return 0;

		if (CldrLocaleData.hasUndeterminedLanguage(range))
			return -1;

		int subtagCount = range.split("-").length;

		if (localeTag.equalsIgnoreCase(range))
			return 10_000 + subtagCount;

		// Locale.filter applies q=0 exclusions and therefore returns no matches when handed a singleton
		// zero-weight range. Specificity needs a structural match probe independent of quality so that a
		// negative range such as en-US;q=0 also excludes en-US-posix and en-US-u-nu-latn.
		LanguageRange structuralLanguageRange = new LanguageRange(range, LanguageRange.MAX_WEIGHT);
		List<Locale> directlyFiltered = Locale.filter(Collections.singletonList(structuralLanguageRange),
				Collections.singletonList(locale), Locale.FilteringMode.EXTENDED_FILTERING);

		if (!directlyFiltered.isEmpty())
			return 8_000 + subtagCount;

		if (range.contains("*"))
			return -1;

		String canonicalRange = CldrLocaleData.canonicalLanguageTag(range);
		String canonicalLocaleTag = CldrLocaleData.canonicalLanguageTag(localeTag);

		if (canonicalLocaleTag.equalsIgnoreCase(canonicalRange))
			return 9_000 + subtagCount;

		LanguageRange canonicalStructuralRange = new LanguageRange(canonicalRange, LanguageRange.MAX_WEIGHT);
		Locale canonicalLocale = Locale.forLanguageTag(canonicalLocaleTag);
		List<Locale> canonicallyFiltered = Locale.filter(Collections.singletonList(canonicalStructuralRange),
				Collections.singletonList(canonicalLocale), Locale.FilteringMode.EXTENDED_FILTERING);

		if (!canonicallyFiltered.isEmpty())
			return 9_000 + subtagCount;

		List<@NonNull Locale> fallbackLocales = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag(range));

		for (int index = 0; index < fallbackLocales.size(); ++index)
			if (CldrLocaleData.equivalent(locale, fallbackLocales.get(index)))
				return 7_000 + subtagCount - Math.min(index, 999);

		Optional<String> requestedLanguageScript = CldrLocaleData.languageScriptForLikelySubtag(range);
		Optional<String> availableLanguageScript = CldrLocaleData.languageScriptForLikelySubtag(locale);

		if (requestedLanguageScript.isPresent() && availableLanguageScript.isPresent() &&
				requestedLanguageScript.get().equalsIgnoreCase(availableLanguageScript.get()))
			return 6_000 + subtagCount;

		String requestedPrimary = normalizedLanguageCode(range.split("-")[0]);
		Optional<String> availablePrimary = LocaleUtils.normalizedLanguage(locale);

		if (availablePrimary.isPresent() && availablePrimary.get().equalsIgnoreCase(requestedPrimary) &&
				hasCompatibleLikelyScript(range, locale))
			return 5_000 + subtagCount;

		return -1;
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
	private List<@NonNull CatalogCandidate> fallbackCatalogCandidates(@NonNull Locale locale) {
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

		Map<@NonNull Locale, @NonNull CatalogCandidate> candidatesByAttemptedLocale = new LinkedHashMap<>();

		for (Locale candidate : candidates) {
			@Nullable LocaleCatalog localeCatalog = localizedStringsFor(candidate);
			Locale attemptedLocale = localeCatalog == null ? candidate : localeCatalog.getLocale();
			@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings =
					localeCatalog == null ? null : localeCatalog.getLocalizedStrings();
			candidatesByAttemptedLocale.putIfAbsent(attemptedLocale,
					new CatalogCandidate(attemptedLocale, localizedStrings));
		}

		return new ArrayList<>(candidatesByAttemptedLocale.values());
	}

	@Nullable
	private LocaleCatalog localizedStringsFor(@NonNull Locale locale) {
		requireNonNull(locale);

		@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = getLocalizedStringsByKeyByLocale().get(locale);

		if (localizedStrings != null)
			return new LocaleCatalog(locale, localizedStrings);

		List<@NonNull Locale> equivalentLocales = getLocalizedStringsByKeyByLocale().keySet().stream()
				.filter(candidate -> CldrLocaleData.equivalent(candidate, locale))
				.sorted(Comparator.comparing(Locale::toLanguageTag))
				.collect(Collectors.toList());
		Optional<@NonNull Locale> preferredLocale = preferredLocaleForRange(locale.toLanguageTag(), equivalentLocales);

		if (preferredLocale.isPresent())
			return new LocaleCatalog(preferredLocale.get(),
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
		Set<@NonNull Locale> supportedLocales = new TreeSet<>(Comparator.comparing(Locale::toLanguageTag));
		supportedLocales.addAll(getLocalizedStringsByLocale().keySet());
		return Collections.unmodifiableSet(supportedLocales);
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
		requireNonNull(locale);

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
		requireNonNull(sourceLocale);
		requireNonNull(targetLocale);

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
	public Function<LocaleMatcher, Locale> getLocaleSupplier() {
		return this.localeSupplier;
	}

	/** @return locale-match supplier, or null when a locale supplier is configured */
	@Nullable
	public Function<LocaleMatcher, LocaleMatchResult> getLocaleMatchSupplier() {
		return localeMatchSupplier;
	}

	/**
	 * Gets the mapping of a mapping of an ISO 639 language code to its ordered "tiebreaker" fallback locales.
	 *
	 * @return the per-language-code "tiebreaker" locales, not null
	 */
	@NonNull
	public Map<@NonNull String, @Nullable List<@NonNull Locale>> getTiebreakerLocalesByLanguageCode() {
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
	 * Gets the safety limits used for catalog construction and translation evaluation.
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

	private static final class LocaleCatalog {
		@NonNull private final Locale locale;
		@NonNull private final Map<@NonNull String, @NonNull LocalizedString> localizedStrings;

		private LocaleCatalog(@NonNull Locale locale,
											@NonNull Map<@NonNull String, @NonNull LocalizedString> localizedStrings) {
			this.locale = requireNonNull(locale);
			this.localizedStrings = requireNonNull(localizedStrings);
		}

		@NonNull private Locale getLocale() { return locale; }
		@NonNull private Map<@NonNull String, @NonNull LocalizedString> getLocalizedStrings() { return localizedStrings; }
	}

	private static final class CatalogCandidate {
		@NonNull private final Locale locale;
		@Nullable private final Map<@NonNull String, @NonNull LocalizedString> localizedStrings;

		private CatalogCandidate(@NonNull Locale locale,
												 @Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings) {
			this.locale = requireNonNull(locale);
			this.localizedStrings = localizedStrings;
		}

		@NonNull private Locale getLocale() { return locale; }
		@Nullable private Map<@NonNull String, @NonNull LocalizedString> getLocalizedStrings() { return localizedStrings; }
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
