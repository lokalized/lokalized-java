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
	@NonNull
	private final Function<LocaleMatcher, Locale> localeSupplier;
	@NonNull
	private final Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode;
	@NonNull
	private final TranslationFailureHandler translationFailureHandler;
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
				phoneticResolver, null);
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
		requireNonNull(fallbackLocale);
		requireNonNull(localizedStringSupplier, format("You must specify a 'localizedStringSupplier' when creating a %s instance", DefaultStrings.class.getSimpleName()));
		requireNonNull(localeSupplier, format("You must specify a 'localeSupplier' when creating a %s instance", DefaultStrings.class.getSimpleName()));

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

		this.fallbackLocale = fallbackLocale;
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

		this.translationFailureHandler = translationFailureHandler == null ? TranslationFailureHandler.returnKey() : translationFailureHandler;
		this.bidiIsolation = bidiIsolation == null ? DEFAULT_BIDI_ISOLATION : bidiIsolation;
		this.stringInterpolator = new StringInterpolator();
		this.phoneticResolver = phoneticResolver == null ? DEFAULT_PHONETIC_RESOLVER : phoneticResolver;
		this.expressionEvaluator = new ExpressionEvaluator(null, this.phoneticResolver);

		Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> localizedStringsByKeyByLocale =
				new HashMap<>(localizedStringsByLocale.size());

		for (Entry<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> entry : localizedStringsByLocale.entrySet()) {
			Locale locale = entry.getKey();
			Map<@NonNull String, @NonNull LocalizedString> localizedStringsByKey = new LinkedHashMap<>();

			for (LocalizedString localizedString : entry.getValue()) {
				if (localizedString == null)
					throw new IllegalArgumentException(format("Null localized string encountered for locale '%s'", locale.toLanguageTag()));

				validateLocalizedString(locale, localizedString);

				String key = localizedString.getKey();
				LocalizedString existing = localizedStringsByKey.putIfAbsent(key, localizedString);

				if (existing != null)
					throw new IllegalArgumentException(format("Duplicate localized string key '%s' encountered for locale '%s'", key, locale.toLanguageTag()));
			}

			localizedStringsByKeyByLocale.put(locale, Collections.unmodifiableMap(localizedStringsByKey));
		}

		this.localizedStringsByKeyByLocale = Collections.unmodifiableMap(localizedStringsByKeyByLocale);

		if (!localizedStringsByLocale.containsKey(getFallbackLocale()))
			throw new IllegalArgumentException(format("Specified fallback locale is '%s' but no matching " +
							"localized strings locale was found. Known locales: [%s]", fallbackLocale.toLanguageTag(),
					localizedStringsByLocale.keySet().stream()
							.map(locale -> locale.toLanguageTag())
							.sorted()
							.collect(Collectors.joining(", "))));

		this.localeSupplier = localeSupplier;
	}

	private static void validateLocalizedString(@NonNull Locale locale, @NonNull LocalizedString localizedString) {
		requireNonNull(locale);
		requireNonNull(localizedString);

		if (localizedString.getTranslation().isPresent()) {
			try {
				StringInterpolator.placeholderNamesIn(localizedString.getTranslation().get());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(format("Invalid placeholder reference in translation for key '%s' and locale '%s': %s",
						localizedString.getKey(), locale.toLanguageTag(), e.getMessage()), e);
			}
		}

		for (LocalizedString alternative : localizedString.getAlternatives())
			validateLocalizedString(locale, alternative);
	}

	@NonNull
	@Override
	public String get(@NonNull String key) {
		requireNonNull(key);
		return get(key, null, TranslationOptions.none());
	}

	@NonNull
	@Override
	public String get(@NonNull String key,
										@NonNull TranslationOptions options) {
		requireNonNull(key);
		requireNonNull(options);
		return get(key, null, options);
	}

	@NonNull
	@Override
	public String get(@NonNull String key,
										@Nullable Map<@NonNull String, @Nullable Object> placeholders) {
		requireNonNull(key);
		return get(key, placeholders, TranslationOptions.none());
	}

	@NonNull
	@Override
	public String get(@NonNull String key,
										@Nullable Map<@NonNull String, @Nullable Object> placeholders,
										@NonNull TranslationOptions options) {
		requireNonNull(key);
		requireNonNull(options);

		if (placeholders == null)
			placeholders = Collections.emptyMap();

		Locale locale = localeFor(options);
		BidiIsolation bidiIsolation = options.getBidiIsolation().orElse(getBidiIsolation());
		TranslationFailureHandler translationFailureHandler = options.getTranslationFailureHandler().orElse(getTranslationFailureHandler());
		Map<@NonNull String, @Nullable Object> immutableContext = Collections.unmodifiableMap(new HashMap<>(placeholders));
		RuntimeException firstFallbackFailure = null;
		List<@NonNull Locale> candidateLocales = fallbackCandidateLocales(locale);

		for (Locale candidateLocale : candidateLocales) {
			Map<@NonNull String, @Nullable Object> mutableContext = new HashMap<>(placeholders);
			@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = localizedStringsByKeyFor(candidateLocale);

			if (localizedStrings == null)
				continue;

			LocalizedString localizedString = localizedStrings.get(key);

			if (localizedString == null)
				continue;

			try {
				Optional<String> translation = getInternal(key, localizedString, mutableContext, immutableContext, candidateLocale, bidiIsolation);

				if (translation.isPresent())
					return translation.get();
			} catch (RuntimeException e) {
				if (firstFallbackFailure == null)
					firstFallbackFailure = e;
				else if (firstFallbackFailure != e)
					firstFallbackFailure.addSuppressed(e);

				logger.finer(format("Unable to resolve key '%s' for locale '%s'; trying fallback candidates. Cause: %s",
						key, candidateLocale.toLanguageTag(), e.getMessage()));
			}
		}

		String message = format("No match for '%s' was found for locale '%s'.", key, locale.toLanguageTag());
		logger.finer(message);

		if (firstFallbackFailure != null)
			logger.finer(format("%s Invoking translation failure handler after resolution failure: %s", message, firstFallbackFailure.getMessage()));

		TranslationFailure translationFailure = new DefaultTranslationFailure(key, locale, candidateLocales, placeholders,
				firstFallbackFailure == null ? TranslationFailureReason.MISSING_TRANSLATION : TranslationFailureReason.RESOLUTION_FAILURE,
				firstFallbackFailure);
		TranslationFailureResponse translationFailureResponse = requireNonNull(translationFailureHandler.handle(translationFailure),
				format("%s returned null", TranslationFailureHandler.class.getSimpleName()));

		switch (translationFailureResponse.getAction()) {
			case RETURN_KEY:
				return interpolateFailureKey(key, placeholders, immutableContext, locale, bidiIsolation);
			case RETURN_STRING:
				return translationFailureResponse.getTranslation();
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
	 * @param immutableContext the original user-supplied translation context, not null
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
				logger.finer(format("An alternative match for '%s' was found for key '%s' and context %s", alternative.getKey(), key, mutableContext));

				// If we have a matching alternative, recurse into it
				Optional<String> translation = getInternal(key, alternative, mutableContext, immutableContext, locale, bidiIsolation);

				if (translation.isPresent())
					return translation;
			}
		}

		if (!localizedString.getTranslation().isPresent())
			return Optional.empty();

		String translation = localizedString.getTranslation().get();

		for (Entry<@NonNull String, @NonNull LanguageFormTranslation> entry : localizedString.getLanguageFormTranslationsByPlaceholder().entrySet()) {
			String placeholderName = entry.getKey();
			LanguageFormTranslation languageFormTranslation = entry.getValue();

			if (languageFormTranslation.isSelectorDriven()) {
				mutableContext.put(placeholderName, resolveSelectorDrivenLanguageFormTranslation(key, placeholderName,
						languageFormTranslation, localizedString, immutableContext, locale));
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

					if (!(rangeStart instanceof Number)) {
						throw new IllegalArgumentException(format("Range start placeholder '%s' for key '%s' must be a %s but was %s",
								languageFormTranslationRange.getStart(), key, Number.class.getSimpleName(), rangeStart.getClass().getSimpleName()));
					}

					if (!(rangeEnd instanceof Number)) {
						throw new IllegalArgumentException(format("Range end placeholder '%s' for key '%s' must be a %s but was %s",
								languageFormTranslationRange.getEnd(), key, Number.class.getSimpleName(), rangeEnd.getClass().getSimpleName()));
					}

					Cardinality startCardinality = Cardinality.forNumber((Number) rangeStart, locale);
					Cardinality endCardinality = Cardinality.forNumber((Number) rangeEnd, locale);
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

					if (!(value instanceof Number)) {
						throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
								languageFormTranslation.getValue().get(), key, Number.class.getSimpleName(), value.getClass().getSimpleName()));
					}

					Cardinality cardinality = Cardinality.forNumber((Number) value, locale);
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

				if (!(value instanceof Number)) {
					throw new IllegalArgumentException(format("Placeholder '%s' in key '%s' must be a %s but was %s",
							languageFormTranslation.getValue().get(), key, Number.class.getSimpleName(), value.getClass().getSimpleName()));
				}

				Ordinality ordinality = Ordinality.forNumber((Number) value, locale);
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
		}

		StringInterpolator.InterpolationResult interpolationResult = getStringInterpolator().interpolateStrictly(translation,
				interpolationContextFor(mutableContext, immutableContext, localizedString.getLanguageFormTranslationsByPlaceholder().keySet(), locale,
						bidiIsolation));

		if (!interpolationResult.getUnresolvedPlaceholderNames().isEmpty())
			throw new IllegalArgumentException(format("Missing value for placeholder(s) [%s] in key '%s'",
					interpolationResult.getUnresolvedPlaceholderNames().stream().collect(Collectors.joining(", ")), key));

		return Optional.of(interpolationResult.getValue());
	}

	@NonNull
	private String interpolateFailureKey(@NonNull String key,
																			 @NonNull Map<@NonNull String, @Nullable Object> placeholders,
																			 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
																			 @NonNull Locale locale,
																			 @NonNull BidiIsolation bidiIsolation) {
		requireNonNull(key);
		requireNonNull(placeholders);
		requireNonNull(immutableContext);
		requireNonNull(locale);
		requireNonNull(bidiIsolation);

		try {
			Map<@NonNull String, @Nullable Object> interpolationContext = new HashMap<>(placeholders);
			return getStringInterpolator().interpolate(key, interpolationContextFor(interpolationContext, immutableContext,
					Collections.emptySet(), locale, bidiIsolation));
		} catch (RuntimeException e) {
			logger.finer(format("Unable to interpolate failure key '%s'; returning the raw key. Cause: %s", key, e.getMessage()));
			return key;
		}
	}

	@NonNull
	private Map<@NonNull String, @Nullable Object> interpolationContextFor(@NonNull Map<@NonNull String, @Nullable Object> mutableContext,
																																				 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
																																				 @NonNull Set<@NonNull String> fileDefinedPlaceholderNames,
																																				 @NonNull Locale locale,
																																				 @NonNull BidiIsolation bidiIsolation) {
		requireNonNull(mutableContext);
		requireNonNull(immutableContext);
		requireNonNull(fileDefinedPlaceholderNames);
		requireNonNull(locale);
		requireNonNull(bidiIsolation);

		switch (bidiIsolation) {
			case NONE:
				return mutableContext;
			case RTL_LOCALES:
				if (!BidiUtils.localeUsesRightToLeftScript(locale))
					return mutableContext;
				break;
			default:
				throw new IllegalArgumentException(format("Unsupported %s value %s",
						BidiIsolation.class.getSimpleName(), bidiIsolation));
		}

		Map<@NonNull String, @Nullable Object> isolatedContext = new HashMap<>(mutableContext);

		for (String placeholderName : immutableContext.keySet()) {
			if (fileDefinedPlaceholderNames.contains(placeholderName))
				continue;

			Object value = unwrapOptional(isolatedContext.get(placeholderName));

			if (value != null)
				isolatedContext.put(placeholderName, BidiUtils.isolate(value.toString()));
		}

		return isolatedContext;
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
				if (!(selectorValue instanceof Number))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Number.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return Cardinality.forNumber((Number) selectorValue, locale);
			case ORDINALITY:
				if (!(selectorValue instanceof Number))
					throw new IllegalArgumentException(format("Selector value '%s' in key '%s' must be a %s but was %s",
							selectorValueName, key, Number.class.getSimpleName(), selectorValue.getClass().getSimpleName()));

				return Ordinality.forNumber((Number) selectorValue, locale);
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

		List<@NonNull Token> expressionTokens = alternative.getExpressionTokens();

		if (expressionTokens != null)
			return getExpressionEvaluator().evaluateReversePolishNotationTokens(expressionTokens, context, locale);

		return getExpressionEvaluator().evaluate(alternative.getKey(), context, locale);
	}

	@NonNull
	@Override
	public Locale bestMatchFor(@NonNull Locale locale) {
		requireNonNull(locale);
		return bestMatchFor(List.of(new LanguageRange(locale.toLanguageTag())));
	}

	@NonNull
	@Override
	public Locale bestMatchFor(@NonNull List<@NonNull LanguageRange> languageRanges) {
		requireNonNull(languageRanges);

		if (languageRanges.isEmpty())
			return getFallbackLocale();

		List<@NonNull LanguageRange> sortedLanguageRanges = new ArrayList<>(languageRanges);
		sortedLanguageRanges.sort(Comparator.comparingDouble(LanguageRange::getWeight).reversed());
		List<@NonNull Locale> availableLocales = new ArrayList<>(getLocalizedStringsByLocale().keySet());
		availableLocales.sort(Comparator.comparing(Locale::toLanguageTag));

		// Walk through each LanguageRange in preference order
		for (LanguageRange languageRange : sortedLanguageRanges) {
			String range = languageRange.getRange(); // e.g. "pt" or "pt-PT"
			double weight = languageRange.getWeight();

			if (weight <= 0)
				continue;

			if ("*".equals(range))
				return getFallbackLocale();

			if (CldrLocaleData.hasUndeterminedLanguage(range))
				continue;

			String canonicalRange = CldrLocaleData.canonicalLanguageTag(range);

			// Exact or CLDR-canonical tag match?
			for (Locale locale : availableLocales)
				if (locale.toLanguageTag().equalsIgnoreCase(range) ||
						CldrLocaleData.canonicalLanguageTag(locale.toLanguageTag()).equalsIgnoreCase(canonicalRange))
					return locale;

			Optional<Locale> lookupMatch = lookupMatchByFallbackCandidates(range, availableLocales);

			if (lookupMatch.isPresent())
				return lookupMatch.get();

			Optional<Locale> likelySubtagMatch = lookupMatchByLikelySubtag(range, availableLocales);

			if (likelySubtagMatch.isPresent())
				return likelySubtagMatch.get();

			// Primary-tag candidates (e.g. "pt" or "pt-XX")
			String primary = normalizedLanguageCode(range.split("-")[0]); // e.g. "pt"

			if ("*".equals(primary)) {
				List<Locale> filteredCandidates = Locale.filter(Collections.singletonList(languageRange), availableLocales,
						Locale.FilteringMode.EXTENDED_FILTERING);

				if (!filteredCandidates.isEmpty())
					return filteredCandidates.get(0);

				continue;
			}

			List<@NonNull Locale> candidates = availableLocales.stream()
					.filter(locale -> LocaleUtils.normalizedLanguage(locale)
							.map(language -> language.equalsIgnoreCase(primary))
							.orElse(false))
					.collect(Collectors.toList());

			if (candidates.isEmpty())
				continue; // try the next LanguageRange

			List<Locale> filteredCandidates = Locale.filter(Collections.singletonList(languageRange), candidates,
					Locale.FilteringMode.EXTENDED_FILTERING);

			if (!filteredCandidates.isEmpty()) {
				boolean hasSpecificMatch = filteredCandidates.stream()
						.anyMatch(locale -> !locale.toLanguageTag().equalsIgnoreCase(locale.getLanguage()));

				if (hasSpecificMatch)
					candidates = filteredCandidates;
			}

			if (candidates.size() == 1)
				return candidates.get(0);

			// Tie‐breaker list for this primary tag?
			@Nullable List<@NonNull Locale> tiebreakers = getTiebreakerLocalesByLanguageCode().get(primary);

			if (tiebreakers != null)
				for (Locale tiebreaker : tiebreakers)
					if (candidates.contains(tiebreaker))
						return tiebreaker;

			return candidates.get(0);
		}

		// 4) Nothing matched at all
		return getFallbackLocale();
	}

	@NonNull
	private Locale localeFor(@NonNull TranslationOptions options) {
		requireNonNull(options);

		Optional<@NonNull Locale> locale = options.getLocale();

		if (locale.isPresent())
			return locale.get();

		Optional<@NonNull List<@NonNull LanguageRange>> languageRanges = options.getLanguageRanges();

		if (languageRanges.isPresent())
			return bestMatchFor(languageRanges.get());

		return requireNonNull(getLocaleSupplier().apply(this), "localeSupplier returned null");
	}

	@NonNull
	private List<@NonNull Locale> fallbackCandidateLocales(@NonNull Locale locale) {
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

		return new ArrayList<>(candidates);
	}

	@Nullable
	private Map<@NonNull String, @NonNull LocalizedString> localizedStringsByKeyFor(@NonNull Locale locale) {
		requireNonNull(locale);

		@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = getLocalizedStringsByKeyByLocale().get(locale);

		if (localizedStrings != null)
			return localizedStrings;

		for (Entry<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> entry : getLocalizedStringsByKeyByLocale().entrySet())
			if (CldrLocaleData.equivalent(entry.getKey(), locale))
				return entry.getValue();

		return null;
	}

	@NonNull
	private Optional<@NonNull Locale> lookupMatchByFallbackCandidates(@NonNull String range,
																																		@NonNull List<@NonNull Locale> availableLocales) {
		requireNonNull(range);
		requireNonNull(availableLocales);

		if (range.contains("*"))
			return Optional.empty();

		for (Locale candidateLocale : CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag(range))) {
			String candidateTag = candidateLocale.toLanguageTag();

			for (Locale locale : availableLocales)
				if (locale.toLanguageTag().equalsIgnoreCase(candidateTag) || CldrLocaleData.equivalent(locale, candidateLocale))
					return Optional.of(locale);
		}

		return Optional.empty();
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
	 * @return the localized string keys for the locale, or an empty set if the locale is not supported, not null
	 */
	@NonNull
	public Set<@NonNull String> getKeysForLocale(@NonNull Locale locale) {
		requireNonNull(locale);

		@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = localizedStringsByKeyFor(locale);

		if (localizedStrings == null)
			return Collections.emptySet();

		Set<@NonNull String> keys = new TreeSet<>(localizedStrings.keySet());
		return Collections.unmodifiableSet(keys);
	}

	/**
	 * Gets the keys supplied by {@code sourceLocale} but missing from {@code targetLocale}.
	 *
	 * @param sourceLocale locale whose keys are used as the source set, not null
	 * @param targetLocale locale whose keys are compared against the source set, not null
	 * @return keys present in {@code sourceLocale} and missing from {@code targetLocale}, not null
	 */
	@NonNull
	public Set<@NonNull String> getMissingKeys(@NonNull Locale sourceLocale, @NonNull Locale targetLocale) {
		requireNonNull(sourceLocale);
		requireNonNull(targetLocale);

		if (localizedStringsByKeyFor(sourceLocale) == null)
			throw new IllegalArgumentException(format("Source locale '%s' is not supported", sourceLocale.toLanguageTag()));

		if (localizedStringsByKeyFor(targetLocale) == null)
			throw new IllegalArgumentException(format("Target locale '%s' is not supported", targetLocale.toLanguageTag()));

		Set<@NonNull String> missingKeys = new TreeSet<>(getKeysForLocale(sourceLocale));
		missingKeys.removeAll(getKeysForLocale(targetLocale));
		return Collections.unmodifiableSet(missingKeys);
	}

	/**
	 * Gets the locale supplier.
	 *
	 * @return the locale supplier, not null
	 */
	@NonNull
	public Function<LocaleMatcher, Locale> getLocaleSupplier() {
		return this.localeSupplier;
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
				translationFailure.getRequestedLocale());
	}
}
