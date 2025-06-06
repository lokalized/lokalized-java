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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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
	@Nonnull
	private final Map<Locale, Set<LocalizedString>> localizedStringsByLocale;
	@Nullable
	private final Function<LocaleMatcher, Locale> localeSupplier;
	@Nonnull
	private final Map<String, List<Locale>> tiebreakerLocalesByLanguageCode;
	@Nonnull
	private final FailureMode failureMode;
	@Nonnull
	private final Locale fallbackLocale;
	@Nonnull
	private final StringInterpolator stringInterpolator;
	@Nonnull
	private final ExpressionEvaluator expressionEvaluator;
	@Nonnull
	private final Logger logger;

	/**
	 * Cache of localized strings by key by locale.
	 * <p>
	 * This is our "master" reference localized string storage that other data structures will point to.
	 */
	@Nonnull
	private final Map<Locale, Map<String, LocalizedString>> localizedStringsByKeyByLocale;

	/**
	 * Vends a builder suitable for constructing {@link DefaultStrings) instances.
	 * <p>
	 * This method is package-private and designed to be invoked via {@link Strings#withFallbackLocale(Locale)}.
	 *
	 * @param fallbackLocale the fallback locale used if no others match, not null
	 * @return the builder, not null
	 */
	@Nonnull
	static Builder withFallbackLocale(@Nonnull Locale fallbackLocale) {
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
	protected DefaultStrings(@Nonnull Locale fallbackLocale,
													 @Nonnull Supplier<Map<Locale, ? extends Iterable<LocalizedString>>> localizedStringSupplier,
													 @Nonnull Function<LocaleMatcher, Locale> localeSupplier,
													 @Nullable Map<String, List<Locale>> tiebreakerLocalesByLanguageCode,
													 @Nullable FailureMode failureMode) {
		requireNonNull(fallbackLocale);
		requireNonNull(localizedStringSupplier, format("You must specify a 'localizedStringSupplier' when creating a %s instance", DefaultStrings.class.getSimpleName()));
		requireNonNull(localeSupplier, format("You must specify a 'localeSupplier' when creating a %s instance", DefaultStrings.class.getSimpleName()));

		this.logger = Logger.getLogger(LoggerType.STRINGS.getLoggerName());

		Map<Locale, ? extends Iterable<LocalizedString>> suppliedLocalizedStringsByLocale = localizedStringSupplier.get();

		if (suppliedLocalizedStringsByLocale == null)
			suppliedLocalizedStringsByLocale = Collections.emptyMap();

		// Defensive copy of iterator to unmodifiable set
		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = suppliedLocalizedStringsByLocale.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> entry.getKey(),
						entry -> {
							Set<LocalizedString> localizedStrings = new LinkedHashSet<>();
							entry.getValue().forEach(localizedStrings::add);
							return Collections.unmodifiableSet(localizedStrings);
						}
				));

		this.fallbackLocale = fallbackLocale;
		this.localizedStringsByLocale = Collections.unmodifiableMap(localizedStringsByLocale);

		// Make our own mapping of tiebreakers based on the provided mapping.
		// First, defensive copy, then add to the map as needed below.
		Map<String, List<Locale>> internalTiebreakerLocalesByLanguageCode = tiebreakerLocalesByLanguageCode == null ? new HashMap<>() : new HashMap<>(tiebreakerLocalesByLanguageCode);

		// Verify tiebreakers are provided to support locale resolution when ambiguity exists.
		// For each language code, if there is more than 1 localized strings file that matches it, tiebreakers must be provided.
		Map<String, Set<Locale>> supportedLocalesByLanguageCode = new HashMap<>(localizedStringsByLocale.size());

		for (Locale supportedLocale : localizedStringsByLocale.keySet()) {
			String languageCode = supportedLocale.getLanguage();
			Set<Locale> locales = supportedLocalesByLanguageCode.get(languageCode);

			if (locales == null) {
				locales = new HashSet<>();
				supportedLocalesByLanguageCode.put(languageCode, locales);
			}

			locales.add(supportedLocale);
		}

		for (Entry<String, Set<Locale>> entry : supportedLocalesByLanguageCode.entrySet()) {
			String languageCode = entry.getKey();
			List<Locale> locales = entry.getValue().stream()
					.sorted(Comparator.comparing(Locale::toLanguageTag))
					.collect(Collectors.toList());

			if (locales.size() == 1) {
				// If there is exactly 1 locale for the language code, it's its own "identity" tiebreaker.
				internalTiebreakerLocalesByLanguageCode.put(languageCode, locales);
			} else if (locales.size() > 1) {
				// We need to provide tiebreakers if a locale has more than 1 strings file.
				List<Locale> providedTiebreakerLocales = internalTiebreakerLocalesByLanguageCode.get(languageCode);

				if (providedTiebreakerLocales == null || providedTiebreakerLocales.size() == 0) {
					throw new IllegalArgumentException(format("You must specify tiebreaker locales via 'tiebreakerLocalesByLanguageCode' to resolve ambiguity for language code '%s' because localized strings exist for the following locale[s]: %s",
							languageCode, locales.stream().map(locale -> locale.toLanguageTag()).collect(Collectors.toList())));
				} else {
					// First, verify that all tiebreakers actually exist
					Set<Locale> supportedLocales = localizedStringsByLocale.keySet();

					for (Locale providedTiebreakerLocale : providedTiebreakerLocales)
						if (!supportedLocales.contains(providedTiebreakerLocale))
							throw new IllegalArgumentException(format("Tiebreaker locale '%s' specified in 'tiebreakerLocalesByLanguageCode' does not have a localized strings file. Supported locales are: %s",
									providedTiebreakerLocale.toLanguageTag(), supportedLocales.stream().map(supportedLocale -> supportedLocale.toLanguageTag()).sorted().collect(Collectors.toList())));

					// Next, verify that tiebreakers are exhaustively specified
					List<Locale> missingLocales = new ArrayList<>(locales.size());

					for (Locale locale : locales)
						if (!providedTiebreakerLocales.contains(locale))
							missingLocales.add(locale);

					if (missingLocales.size() > 0)
						throw new IllegalArgumentException(format("Your 'tiebreakerLocalesByLanguageCode' specifies locale[s] %s for language code '%s', but you are missing entries for the following locale[s]: %s",
								providedTiebreakerLocales.stream().map(providedTiebreakerLocale -> providedTiebreakerLocale.toLanguageTag()).sorted().collect(Collectors.toList()),
								languageCode,
								missingLocales.stream().map(missingLocale -> missingLocale.toLanguageTag()).sorted().collect(Collectors.toList())));
				}

				internalTiebreakerLocalesByLanguageCode.put(languageCode, locales);
			} else {
				// Should never occur
				throw new IllegalStateException("No locales match language code");
			}
		}

		this.tiebreakerLocalesByLanguageCode = Collections.unmodifiableMap(internalTiebreakerLocalesByLanguageCode);

		this.failureMode = failureMode == null ? FailureMode.USE_FALLBACK : failureMode;
		this.stringInterpolator = new StringInterpolator();
		this.expressionEvaluator = new ExpressionEvaluator();

		this.localizedStringsByKeyByLocale = Collections.unmodifiableMap(localizedStringsByLocale.entrySet().stream()
				.collect(Collectors.toMap(
						entry1 -> entry1.getKey(),
						entry1 ->
								Collections.unmodifiableMap(entry1.getValue().stream()
										.collect(Collectors.toMap(
														entry2 -> entry2.getKey(),
														entry2 -> entry2
												)
										)))));

		if (!localizedStringsByLocale.containsKey(getFallbackLocale()))
			throw new IllegalArgumentException(format("Specified fallback locale is '%s' but no matching " +
							"localized strings locale was found. Known locales: [%s]", fallbackLocale.toLanguageTag(),
					localizedStringsByLocale.keySet().stream()
							.map(locale -> locale.toLanguageTag())
							.sorted()
							.collect(Collectors.joining(", "))));

		this.localeSupplier = localeSupplier;
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key) {
		requireNonNull(key);
		return get(key, null, getLocaleSupplier().apply(this));
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key,
										@Nullable Map<String, Object> placeholders) {
		requireNonNull(key);
		return get(key, placeholders, getLocaleSupplier().apply(this));
	}

	@Nonnull
	protected String get(@Nonnull String key,
											 @Nullable Map<String, Object> placeholders,
											 @Nonnull Locale locale) {
		requireNonNull(key);
		requireNonNull(locale);

		if (placeholders == null)
			placeholders = Collections.emptyMap();

		Locale finalLocale = locale;
		Map<String, Object> mutableContext = new HashMap<>(placeholders);
		Map<String, Object> immutableContext = Collections.unmodifiableMap(placeholders);

		Map<String, LocalizedString> localizedStrings = getLocalizedStringsByKeyByLocale().get(locale);

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
	@Nonnull
	protected Optional<String> getInternal(@Nonnull String key, @Nonnull LocalizedString localizedString,
																				 @Nonnull Map<String, Object> mutableContext, @Nonnull Map<String, Object> immutableContext,
																				 @Nonnull Locale locale) {
		requireNonNull(key);
		requireNonNull(localizedString);
		requireNonNull(mutableContext);
		requireNonNull(immutableContext);
		requireNonNull(locale);

		// First, see if any alternatives match by evaluating them
		for (LocalizedString alternative : localizedString.getAlternatives()) {
			if (getExpressionEvaluator().evaluate(alternative.getKey(), mutableContext, locale)) {
				logger.finer(format("An alternative match for '%s' was found for key '%s' and context %s", alternative.getKey(), key, mutableContext));

				// If we have a matching alternative, recurse into it
				return getInternal(key, alternative, mutableContext, immutableContext, locale);
			}
		}

		if (!localizedString.getTranslation().isPresent())
			return Optional.empty();

		String translation = localizedString.getTranslation().get();

		for (Entry<String, LanguageFormTranslation> entry : localizedString.getLanguageFormTranslationsByPlaceholder().entrySet()) {
			String placeholderName = entry.getKey();
			LanguageFormTranslation languageFormTranslation = entry.getValue();
			Object value = null;
			Object rangeStart = null;
			Object rangeEnd = null;
			Map<Cardinality, String> translationsByCardinality = new HashMap<>();
			Map<Ordinality, String> translationsByOrdinality = new HashMap<>();
			Map<Gender, String> translationsByGender = new HashMap<>();

			if (languageFormTranslation.getRange().isPresent()) {
				LanguageFormTranslationRange languageFormTranslationRange = languageFormTranslation.getRange().get();
				rangeStart = immutableContext.get(languageFormTranslationRange.getStart());
				rangeEnd = immutableContext.get(languageFormTranslationRange.getEnd());
			} else {
				value = immutableContext.get(languageFormTranslation.getValue().get());
			}

			for (Entry<LanguageForm, String> translationEntry : languageFormTranslation.getTranslationsByLanguageForm().entrySet()) {
				LanguageForm languageForm = translationEntry.getKey();
				String translatedLanguageForm = translationEntry.getValue();

				if (languageForm instanceof Cardinality)
					translationsByCardinality.put((Cardinality) languageForm, translatedLanguageForm);
				else if (languageForm instanceof Ordinality)
					translationsByOrdinality.put((Ordinality) languageForm, translatedLanguageForm);
				else if (languageForm instanceof Gender)
					translationsByGender.put((Gender) languageForm, translatedLanguageForm);
				else
					throw new IllegalArgumentException(format("Encountered unrecognized language form %s", languageForm));
			}

			int distinctLanguageForms = (translationsByCardinality.size() > 0 ? 1 : 0) +
					(translationsByOrdinality.size() > 0 ? 1 : 0) +
					(translationsByGender.size() > 0 ? 1 : 0);

			if (distinctLanguageForms > 1)
				throw new IllegalArgumentException(format("You cannot mix-and-match language forms. Offending localized string was %s", localizedString));

			if (distinctLanguageForms == 0)
				continue;

			// Handle plural cardinalities
			if (translationsByCardinality.size() > 0) {
				// Special case: calculate range from min and max if this is a range-driven cardinality
				if (languageFormTranslation.getRange().isPresent()) {
					if (rangeStart == null)
						rangeStart = 0;
					if (rangeEnd == null)
						rangeEnd = 0;

					if (!(rangeStart instanceof Number)) {
						logger.warning(format("Range start '%s' for '%s' is not a number, falling back to 0.",
								rangeStart, languageFormTranslation.getValue()));
						rangeStart = 0;
					}

					if (!(rangeEnd instanceof Number)) {
						logger.warning(format("Range value end '%s' for '%s' is not a number, falling back to 0.",
								rangeEnd, languageFormTranslation.getValue()));
						rangeEnd = 0;
					}

					Cardinality startCardinality = Cardinality.forNumber((Number) rangeStart, locale);
					Cardinality endCardinality = Cardinality.forNumber((Number) rangeEnd, locale);
					Cardinality rangeCardinality = Cardinality.forRange(startCardinality, endCardinality, locale);

					String cardinalityTranslation = translationsByCardinality.get(rangeCardinality);

					if (cardinalityTranslation == null)
						logger.warning(format("Unable to find %s translation for range cardinality %s (start was %s, end was %s). Localized string was %s",
								Cardinality.class.getSimpleName(), rangeCardinality.name(), startCardinality.name(), endCardinality.name(), localizedString));

					mutableContext.put(placeholderName, cardinalityTranslation);
				} else {
					// Normal "non-range" cardinality
					if (value == null)
						value = 0;

					if (!(value instanceof Number)) {
						logger.warning(format("Value '%s' for '%s' is not a number, falling back to 0.",
								value, languageFormTranslation.getValue()));
						value = 0;
					}

					Cardinality cardinality = Cardinality.forNumber((Number) value, locale);
					String cardinalityTranslation = translationsByCardinality.get(cardinality);

					if (cardinalityTranslation == null)
						logger.warning(format("Unable to find %s translation for %s. Localized string was %s",
								Cardinality.class.getSimpleName(), cardinality.name(), localizedString));

					mutableContext.put(placeholderName, cardinalityTranslation);
				}
			}

			// Handle plural ordinalities
			if (translationsByOrdinality.size() > 0) {
				if (value == null)
					value = 0;

				if (!(value instanceof Number)) {
					logger.warning(format("Value '%s' for '%s' is not a number, falling back to 0.",
							value, languageFormTranslation.getValue()));
					value = 0;
				}

				Ordinality ordinality = Ordinality.forNumber((Number) value, locale);
				String ordinalityTranslation = translationsByOrdinality.get(ordinality);

				if (ordinalityTranslation == null)
					logger.warning(format("Unable to find %s translation for %s. Localized string was %s",
							Ordinality.class.getSimpleName(), ordinality.name(), localizedString));

				mutableContext.put(placeholderName, ordinalityTranslation);
			}

			// Handle genders
			if (translationsByGender.size() > 0) {
				if (value == null) {
					logger.warning(format("Value '%s' for '%s' is null. No replacement will be performed.", value,
							languageFormTranslation.getValue()));
					continue;
				}

				if (!(value instanceof Gender)) {
					logger.warning(format("Value '%s' for '%s' is not a %s. No replacement will be performed.", value,
							languageFormTranslation.getValue(), Gender.class.getSimpleName()));
					continue;
				}

				Gender gender = (Gender) value;
				String genderTranslation = translationsByGender.get(gender);

				if (genderTranslation == null)
					logger.warning(format("Unable to find %s translation for %s. Localized string was %s",
							Gender.class.getSimpleName(), gender.name(), localizedString));

				mutableContext.put(placeholderName, genderTranslation);
			}
		}

		translation = getStringInterpolator().interpolate(translation, mutableContext);

		return Optional.of(translation);
	}

	@Nonnull
	@Override
	public Locale bestMatchFor(@Nonnull Locale locale) {
		requireNonNull(locale);
		return bestMatchFor(List.of(new LanguageRange(locale.toLanguageTag())));
	}

	@Nonnull
	@Override
	public Locale bestMatchFor(@Nonnull List<LanguageRange> languageRanges) {
		requireNonNull(languageRanges);

		if (languageRanges.isEmpty())
			return getFallbackLocale();

		// Walk through each LanguageRange in preference order
		for (LanguageRange languageRange : languageRanges) {
			String range = languageRange.getRange(); // e.g. "pt" or "pt-PT"
			double weight = languageRange.getWeight();

			if (weight <= 0)
				continue;

			// Exact tag match?
			for (Locale locale : getLocalizedStringsByLocale().keySet())
				if (locale.toLanguageTag().equalsIgnoreCase(range))
					return locale;

			// Primary‐tag match (e.g. range="pt" or "pt-XX")
			String primary = range.split("-")[0]; // e.g. "pt"
			List<Locale> candidates = getLocalizedStringsByLocale().keySet().stream()
					.filter(locale -> locale.getLanguage().equalsIgnoreCase(primary))
					.collect(Collectors.toList());

			if (candidates.isEmpty())
				continue; // try the next LanguageRange

			if (candidates.size() == 1)
				return candidates.get(0);

			// Tie‐breaker list for this primary tag?
			List<Locale> tiebreakers = getTiebreakerLocalesByLanguageCode().get(primary);

			if (tiebreakers != null)
				for (Locale tiebreaker : tiebreakers)
					if (candidates.contains(tiebreaker))
						return tiebreaker;
		}

		// 4) Nothing matched at all
		return getFallbackLocale();
	}

	/**
	 * Gets the set of localized strings for each locale.
	 *
	 * @return the set of localized strings for each locale, not null
	 */
	@Nonnull
	public Map<Locale, Set<LocalizedString>> getLocalizedStringsByLocale() {
		return localizedStringsByLocale;
	}

	/**
	 * Gets the locale supplier.
	 *
	 * @return the locale supplier, not null
	 */
	@Nonnull
	public Function<LocaleMatcher, Locale> getLocaleSupplier() {
		return this.localeSupplier;
	}

	/**
	 * Gets the mapping of a mapping of an ISO 639 language code to its ordered "tiebreaker" fallback locales.
	 *
	 * @return the per-language-code "tiebreaker" locales, not null
	 */
	@Nonnull
	public Map<String, List<Locale>> getTiebreakerLocalesByLanguageCode() {
		return this.tiebreakerLocalesByLanguageCode;
	}

	/**
	 * Gets the strategy for handling string lookup failures.
	 *
	 * @return the strategy for handling string lookup failures, not null
	 */
	@Nonnull
	public FailureMode getFailureMode() {
		return failureMode;
	}

	/**
	 * Gets the fallback locale.
	 *
	 * @return the fallback locale, not null
	 */
	@Nonnull
	public Locale getFallbackLocale() {
		return fallbackLocale;
	}

	/**
	 * Gets the string interpolator used to merge placeholders into translations.
	 *
	 * @return the string interpolator, not null
	 */
	@Nonnull
	protected StringInterpolator getStringInterpolator() {
		return stringInterpolator;
	}

	/**
	 * Gets the expression evaluator used to determine if alternative expressions match the evaluation context.
	 *
	 * @return the expression evaluator, not null
	 */
	@Nonnull
	protected ExpressionEvaluator getExpressionEvaluator() {
		return expressionEvaluator;
	}

	/**
	 * Gets our "master" cache of localized strings by key by locale.
	 *
	 * @return the cache of localized strings by key by locale, not null
	 */
	@Nonnull
	protected Map<Locale, Map<String, LocalizedString>> getLocalizedStringsByKeyByLocale() {
		return localizedStringsByKeyByLocale;
	}

	/**
	 * Data structure which holds a locale and the localized strings for it, with the strings mapped by key for fast access.
	 *
	 * @author <a href="https://revetkn.com">Mark Allen</a>
	 */
	@Immutable
	static class LocalizedStringSource {
		@Nonnull
		private final Locale locale;
		@Nonnull
		private final Map<String, LocalizedString> localizedStringsByKey;

		/**
		 * Constructs a localized string source with the given locale and map of keys to localized strings.
		 *
		 * @param locale                the locale for these localized strings, not null
		 * @param localizedStringsByKey localized strings by translation key, not null
		 */
		public LocalizedStringSource(@Nonnull Locale locale, @Nonnull Map<String, LocalizedString> localizedStringsByKey) {
			requireNonNull(locale);
			requireNonNull(localizedStringsByKey);

			this.locale = locale;
			this.localizedStringsByKey = localizedStringsByKey;
		}

		/**
		 * Generates a {@code String} representation of this object.
		 *
		 * @return a string representation of this object, not null
		 */
		@Override
		@Nonnull
		public String toString() {
			return format("%s{locale=%s, localizedStringsByKey=%s", getClass().getSimpleName(), getLocale(), getLocalizedStringsByKey());
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

			LocalizedStringSource localizedStringSource = (LocalizedStringSource) other;

			return Objects.equals(getLocale(), localizedStringSource.getLocale())
					&& Objects.equals(getLocalizedStringsByKey(), localizedStringSource.getLocalizedStringsByKey());
		}

		/**
		 * A hash code for this object.
		 *
		 * @return a suitable hash code
		 */
		@Override
		public int hashCode() {
			return Objects.hash(getLocale(), getLocalizedStringsByKey());
		}

		@Nonnull
		public Locale getLocale() {
			return locale;
		}

		@Nonnull
		public Map<String, LocalizedString> getLocalizedStringsByKey() {
			return localizedStringsByKey;
		}
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
		@Nonnull
		private final Locale fallbackLocale;
		@Nullable
		private Supplier<Map<Locale, ? extends Iterable<LocalizedString>>> localizedStringSupplier;
		@Nullable
		private Function<LocaleMatcher, Locale> localeSupplier;
		@Nullable
		private Supplier<List<LanguageRange>> languageRangesSupplier;
		@Nullable
		private Map<String, List<Locale>> tiebreakerLocalesByLanguageCode;
		@Nullable
		private FailureMode failureMode;

		/**
		 * Constructs a strings builder with a default locale.
		 *
		 * @param fallbackLocale fallback locale, not null
		 */
		protected Builder(@Nonnull Locale fallbackLocale) {
			requireNonNull(fallbackLocale);
			this.fallbackLocale = fallbackLocale;
		}

		/**
		 * Applies a localized string supplier to this builder.
		 *
		 * @param localizedStringSupplier localized string supplier, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@Nonnull
		public Builder localizedStringSupplier(@Nullable Supplier<Map<Locale, ? extends Iterable<LocalizedString>>> localizedStringSupplier) {
			this.localizedStringSupplier = localizedStringSupplier;
			return this;
		}

		/**
		 * Applies a locale supplier to this builder.
		 *
		 * @param localeSupplier locale supplier, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@Nonnull
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
		@Nonnull
		public Builder tiebreakerLocalesByLanguageCode(@Nullable Map<String, List<Locale>> tiebreakerLocalesByLanguageCode) {
			this.tiebreakerLocalesByLanguageCode = tiebreakerLocalesByLanguageCode;
			return this;
		}

		/**
		 * Applies a failure mode to this builder.
		 *
		 * @param failureMode strategy for dealing with lookup failures, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@Nonnull
		public Builder failureMode(@Nullable FailureMode failureMode) {
			this.failureMode = failureMode;
			return this;
		}

		/**
		 * Constructs an instance of {@link DefaultStrings}.
		 *
		 * @return an instance of {@link DefaultStrings}, not null
		 */
		@Nonnull
		public DefaultStrings build() {
			return new DefaultStrings(fallbackLocale, localizedStringSupplier, localeSupplier, tiebreakerLocalesByLanguageCode, failureMode);
		}
	}
}