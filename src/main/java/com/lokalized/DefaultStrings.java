/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2025 Revetware LLC.
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

import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslationRange;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;
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
public class DefaultStrings implements Strings {
	@NonNull
	private static final PhoneticResolver DEFAULT_PHONETIC_RESOLVER;

	static {
		DEFAULT_PHONETIC_RESOLVER = (term, locale) -> {
			throw new IllegalStateException(format("No %s was configured. Provide one via %s.Builder#phoneticResolver(...)",
					PhoneticResolver.class.getSimpleName(), Strings.class.getSimpleName()));
		};
	}

	@NonNull
	private final Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale;
	@NonNull
	private final Function<LocaleMatcher, Locale> localeSupplier;
	@NonNull
	private final Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode;
	@NonNull
	private final FailureMode failureMode;
	@NonNull
	private final Locale fallbackLocale;
	@NonNull
	private final StringInterpolator stringInterpolator;
	@NonNull
	private final ExpressionEvaluator expressionEvaluator;
	@NonNull
	private final PhoneticResolver phoneticResolver;
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
	 * Vends a builder suitable for constructing {@link DefaultStrings) instances.
	 * <p>
	 * This method is package-private and designed to be invoked via {@link Strings#withFallbackLocale(Locale)}.
	 *
	 * @param fallbackLocale the fallback locale used if no others match, not null
	 * @return the builder, not null
	 */
	@NonNull
	static Builder withFallbackLocale(@NonNull Locale fallbackLocale) {
		requireNonNull(fallbackLocale);
		return new Builder(fallbackLocale);
	}

	/**
	 * Constructs a localized string provider with builder-supplied data.
	 *
	 * @param fallbackLocale                  fallback locale, not null
	 * @param localizedStringSupplier         supplier of localized strings, not null
	 * @param localeSupplier                  locale supplier, may not be null
	 * @param tiebreakerLocalesByLanguageCode "tiebreaker" fallbacks, may be null
	 * @param failureMode                     strategy for dealing with lookup failures, may be null
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
													 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
													 @NonNull Function<LocaleMatcher, Locale> localeSupplier,
													 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
													 @Nullable FailureMode failureMode) {
		this(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode, failureMode, null);
	}

	/**
	 * Constructs a localized string provider with builder-supplied data.
	 *
	 * @param fallbackLocale                  fallback locale, not null
	 * @param localizedStringSupplier         supplier of localized strings, not null
	 * @param localeSupplier                  locale supplier, may not be null
	 * @param tiebreakerLocalesByLanguageCode "tiebreaker" fallbacks, may be null
	 * @param failureMode                     strategy for dealing with lookup failures, may be null
	 * @param phoneticResolver                resolver for phonetic categories, may be null (defaults to fail-fast resolver)
	 */
	protected DefaultStrings(@NonNull Locale fallbackLocale,
													 @NonNull Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier,
													 @NonNull Function<LocaleMatcher, Locale> localeSupplier,
													 @Nullable Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode,
													 @Nullable FailureMode failureMode,
													 @Nullable PhoneticResolver phoneticResolver) {
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
				internalTiebreakerLocalesByLanguageCode.put(entry.getKey(),
						locales == null ? null : new ArrayList<>(locales));
			}
		}

		// Verify tiebreakers are provided to support locale resolution when ambiguity exists.
		// For each language code, if there is more than 1 localized strings file that matches it, tiebreakers must be provided.
		Map<@NonNull String, @NonNull Set<@NonNull Locale>> supportedLocalesByLanguageCode = new HashMap<>(localizedStringsByLocale.size());

		for (Locale supportedLocale : localizedStringsByLocale.keySet()) {
			String languageCode = supportedLocale.getLanguage();
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

		this.failureMode = failureMode == null ? FailureMode.USE_FALLBACK : failureMode;
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

	@NonNull
	@Override
	public String get(@NonNull String key) {
		requireNonNull(key);
		return get(key, null, getLocaleSupplier().apply(this));
	}

	@NonNull
	@Override
	public String get(@NonNull String key,
										@Nullable Map<@NonNull String, @Nullable Object> placeholders) {
		requireNonNull(key);
		return get(key, placeholders, getLocaleSupplier().apply(this));
	}

	@NonNull
	protected String get(@NonNull String key,
											 @Nullable Map<@NonNull String, @Nullable Object> placeholders,
											 @NonNull Locale locale) {
		requireNonNull(key);
		requireNonNull(locale);

		if (placeholders == null)
			placeholders = Collections.emptyMap();

		Locale finalLocale = locale;
		Map<@NonNull String, @Nullable Object> mutableContext = new HashMap<>(placeholders);
		Map<@NonNull String, @Nullable Object> immutableContext = Collections.unmodifiableMap(new HashMap<>(placeholders));

		@Nullable Map<@NonNull String, @NonNull LocalizedString> localizedStrings = getLocalizedStringsByKeyByLocale().get(locale);

		if (localizedStrings == null) {
			finalLocale = getFallbackLocale();
			localizedStrings = getLocalizedStringsByKeyByLocale().get(getFallbackLocale());
		}

		// Should never occur
		if (localizedStrings == null)
			throw new IllegalStateException(format("Unable to find strings file for both '%s' and fallback locale '%s'",
					locale.toLanguageTag(), getFallbackLocale().toLanguageTag()));

		LocalizedString localizedString = localizedStrings.get(key);
		String translation = null;

		if (localizedString != null)
			translation = getInternal(key, localizedString, mutableContext, immutableContext, finalLocale).orElse(null);

		if (translation == null) {
			String message = format("No match for '%s' was found for locale '%s'.", key, locale.toLanguageTag());
			logger.finer(message);

			if (getFailureMode() == FailureMode.FAIL_FAST)
				throw new MissingTranslationException(message, key, placeholders, locale);

			// Not fail-fast?  Merge against the key itself in hopes that the key is a meaningful natural-language value
			translation = getStringInterpolator().interpolate(key, mutableContext);
		}

		return translation;
	}

	/**
	 * Recursive method which attempts to translate a localized string.
	 *
	 * @param key              the toplevel translation key (always the same regardless of recursion depth), not null
	 * @param localizedString  the localized string on which to operate, not null
	 * @param mutableContext   the mutable context for the translation, not null
	 * @param immutableContext the original user-supplied translation context, not null
	 * @param locale           the locale to use for evaluation, not null
	 * @return the translation, if possible (may not be possible if no translation value specified and no alternative expressions match), not null
	 */
	@NonNull
	protected Optional<String> getInternal(@NonNull String key, @NonNull LocalizedString localizedString,
																				 @NonNull Map<@NonNull String, @Nullable Object> mutableContext,
																				 @NonNull Map<@NonNull String, @Nullable Object> immutableContext,
																				 @NonNull Locale locale) {
		requireNonNull(key);
		requireNonNull(localizedString);
		requireNonNull(mutableContext);
		requireNonNull(immutableContext);
		requireNonNull(locale);

		// First, see if any alternatives match by evaluating them
		for (LocalizedString alternative : localizedString.getAlternatives()) {
			if (alternativeMatches(alternative, mutableContext, locale)) {
				logger.finer(format("An alternative match for '%s' was found for key '%s' and context %s", alternative.getKey(), key, mutableContext));

				// If we have a matching alternative, recurse into it
				return getInternal(key, alternative, mutableContext, immutableContext, locale);
			}
		}

		if (!localizedString.getTranslation().isPresent())
			return Optional.empty();

		String translation = localizedString.getTranslation().get();

		for (Entry<@NonNull String, @NonNull LanguageFormTranslation> entry : localizedString.getLanguageFormTranslationsByPlaceholder().entrySet()) {
			String placeholderName = entry.getKey();
			LanguageFormTranslation languageFormTranslation = entry.getValue();
			Object value = null;
			Object rangeStart = null;
			Object rangeEnd = null;
			Map<@NonNull Cardinality, @NonNull String> translationsByCardinality = new HashMap<>();
			Map<@NonNull Ordinality, @NonNull String> translationsByOrdinality = new HashMap<>();
			Map<@NonNull Gender, @NonNull String> translationsByGender = new HashMap<>();
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

		translation = getStringInterpolator().interpolate(translation, mutableContext);

		return Optional.of(translation);
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

		// Walk through each LanguageRange in preference order
		for (LanguageRange languageRange : sortedLanguageRanges) {
			String range = languageRange.getRange(); // e.g. "pt" or "pt-PT"
			double weight = languageRange.getWeight();

			if (weight <= 0)
				continue;

			if ("*".equals(range))
				return getFallbackLocale();

			// Exact tag match?
			for (Locale locale : availableLocales)
				if (locale.toLanguageTag().equalsIgnoreCase(range))
					return locale;

			// Primary-tag candidates (e.g. "pt" or "pt-XX")
			String primary = range.split("-")[0]; // e.g. "pt"

			if ("*".equals(primary)) {
				List<Locale> filteredCandidates = Locale.filter(Collections.singletonList(languageRange), availableLocales,
						Locale.FilteringMode.EXTENDED_FILTERING);

				if (!filteredCandidates.isEmpty())
					return filteredCandidates.get(0);

				continue;
			}

			List<@NonNull Locale> candidates = availableLocales.stream()
					.filter(locale -> locale.getLanguage().equalsIgnoreCase(primary))
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
	 * Gets the strategy for handling string lookup failures.
	 *
	 * @return the strategy for handling string lookup failures, not null
	 */
	@NonNull
	public FailureMode getFailureMode() {
		return failureMode;
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
	 * Gets our "master" cache of localized strings by key by locale.
	 *
	 * @return the cache of localized strings by key by locale, not null
	 */
	@NonNull
	protected Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> getLocalizedStringsByKeyByLocale() {
		return localizedStringsByKeyByLocale;
	}

	/**
	 * Strategies for handling localized string lookup failures.
	 */
	public enum FailureMode {
		/**
		 * The system will attempt a series of fallbacks in order to not throw an exception at runtime.
		 * <p>
		 * This mode is useful for production, where we often want program execution to continue in the face of
		 * localization errors.
		 */
		USE_FALLBACK,
		/**
		 * The system will throw an exception if a localization is missing for the specified locale.
		 * <p>
		 * This mode is useful for testing, since problems are uncovered right away when execution halts.
		 */
		FAIL_FAST
	}

	/**
	 * Builder used to construct instances of {@link DefaultStrings}.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	public static class Builder {
		@NonNull
		private final Locale fallbackLocale;
		@Nullable
		private Supplier<Map<@NonNull Locale, ? extends Iterable<@NonNull LocalizedString>>> localizedStringSupplier;
		@Nullable
		private Function<LocaleMatcher, Locale> localeSupplier;
		@Nullable
		private Map<@NonNull String, @Nullable List<@NonNull Locale>> tiebreakerLocalesByLanguageCode;
		@Nullable
		private FailureMode failureMode;
		@Nullable
		private PhoneticResolver phoneticResolver;

		/**
		 * Constructs a strings builder with a default locale.
		 *
		 * @param fallbackLocale fallback locale, not null
		 */
		protected Builder(@NonNull Locale fallbackLocale) {
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
		 * Applies a failure mode to this builder.
		 *
		 * @param failureMode strategy for dealing with lookup failures, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@NonNull
		public Builder failureMode(@Nullable FailureMode failureMode) {
			this.failureMode = failureMode;
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
		 * Constructs an instance of {@link DefaultStrings}.
		 *
		 * @return an instance of {@link DefaultStrings}, not null
		 */
		@NonNull
		public DefaultStrings build() {
			return new DefaultStrings(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode,
					failureMode, phoneticResolver);
		}
	}
}
