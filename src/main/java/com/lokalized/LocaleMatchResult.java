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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Objects;
import java.util.Optional;

import static com.lokalized.Diagnostics.format;
import static java.util.Objects.requireNonNull;

/**
 * Strict, immutable diagnostic result for locale negotiation.
 * <p>
 * This value supports Java serialization, including its requested {@link LanguageRange} values.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
@Immutable
public final class LocaleMatchResult implements Serializable {
	private static final long serialVersionUID = 1L;

	/** Language ranges supplied for negotiation, in caller order. */
	@NonNull private final List<@NonNull LanguageRange> requestedLanguageRanges;
	/** Selected supported locale, or null for no match. */
	@Nullable private final Locale locale;
	/** Language range associated with the selection, or null for no match. */
	@Nullable private final LanguageRange languageRange;
	/** Effective quality of the selected locale, or null for no match. */
	@Nullable private final Double effectiveWeight;
	/** Relationship used for the selection. */
	@NonNull private final LocaleMatchType matchType;
	/** Configured fallback locale. */
	@NonNull private final Locale fallbackLocale;
	/** Supported locales considered during negotiation. */
	@NonNull private final List<@NonNull Locale> consideredLocales;

	/**
	 * Constructs a locale match result. This is public so custom {@link LocaleMatcher} implementations can expose the
	 * same diagnostics.
	 *
	 * @param requestedLanguageRanges ranges supplied for negotiation, not null
	 * @param locale selected supported locale, or null for no match
	 * @param languageRange range associated with the selection, or null for no match
	 * @param effectiveWeight selected locale's effective quality, or null for no match
	 * @param matchType match relationship, not null
	 * @param fallbackLocale configured fallback locale, not null
	 * @param consideredLocales supported locales considered, not null
	 * @throws IllegalArgumentException if more than {@link LocaleMatcher#MAXIMUM_LANGUAGE_RANGES} requested language
	 *                                  ranges are supplied, any locale is malformed, considered locales have duplicate
	 *                                  language tags, matched and unmatched state is mixed, the effective weight is
	 *                                  outside {@code (0, 1]}, or selected/fallback locales were not considered
	 */
	public LocaleMatchResult(@NonNull List<@NonNull LanguageRange> requestedLanguageRanges,
								@Nullable Locale locale, @Nullable LanguageRange languageRange, @Nullable Double effectiveWeight,
								@NonNull LocaleMatchType matchType, @NonNull Locale fallbackLocale,
								@NonNull List<@NonNull Locale> consideredLocales) {
		requireNonNull(requestedLanguageRanges);

		if (requestedLanguageRanges.size() > LocaleMatcher.MAXIMUM_LANGUAGE_RANGES)
			throw new IllegalArgumentException(format("At most %d language ranges are supported, but received %d",
					LocaleMatcher.MAXIMUM_LANGUAGE_RANGES, requestedLanguageRanges.size()));

		List<@NonNull LanguageRange> requestedLanguageRangeCopy =
				new ArrayList<>(requestedLanguageRanges.size());

		for (LanguageRange requestedLanguageRange : requestedLanguageRanges)
			requestedLanguageRangeCopy.add(requireNonNull(requestedLanguageRange));

		this.requestedLanguageRanges = Collections.unmodifiableList(requestedLanguageRangeCopy);
		this.locale = locale == null ? null : LocaleUtils.requireWellFormed(locale, "Selected locale");
		this.languageRange = languageRange;
		this.effectiveWeight = effectiveWeight;
		this.matchType = requireNonNull(matchType);
		this.fallbackLocale = LocaleUtils.requireWellFormed(fallbackLocale, "Fallback locale");

		if (locale == null && (languageRange != null || effectiveWeight != null || matchType != LocaleMatchType.NONE))
			throw new IllegalArgumentException("An unmatched locale result must use NONE and omit range and weight");

		if (locale != null && (languageRange == null || effectiveWeight == null || matchType == LocaleMatchType.NONE))
			throw new IllegalArgumentException("A matched locale result requires a range, weight, and non-NONE match type");
		if (languageRange != null && !requestedLanguageRangeCopy.contains(languageRange))
			throw new IllegalArgumentException("The matched language range must be present in requested language ranges");

		if (effectiveWeight != null && (!Double.isFinite(effectiveWeight) || effectiveWeight <= 0.0 || effectiveWeight > 1.0))
			throw new IllegalArgumentException("A matched locale result requires a finite effective weight greater than 0 and at most 1");
		List<@NonNull Locale> consideredLocaleCopy = new ArrayList<>(requireNonNull(consideredLocales).size());
		LinkedHashSet<@NonNull String> consideredLanguageTags = new LinkedHashSet<>();

		for (Locale consideredLocale : consideredLocales) {
			Locale validatedLocale = LocaleUtils.requireWellFormed(consideredLocale, "Considered locale");
			String normalizedLanguageTag = validatedLocale.toLanguageTag().toLowerCase(Locale.ROOT);

			if (!consideredLanguageTags.add(normalizedLanguageTag))
				throw new IllegalArgumentException(format(
						"Considered locales must not contain duplicate language tag '%s'", validatedLocale.toLanguageTag()));

			consideredLocaleCopy.add(validatedLocale);
		}

		if (new LinkedHashSet<>(consideredLocaleCopy).size() != consideredLocaleCopy.size())
			throw new IllegalArgumentException("Considered locales must not contain duplicates");
		if (!consideredLocaleCopy.contains(fallbackLocale))
			throw new IllegalArgumentException("The fallback locale must be present in considered locales");
		if (locale != null && !consideredLocaleCopy.contains(locale))
			throw new IllegalArgumentException("The selected locale must be present in considered locales");

		this.consideredLocales = Collections.unmodifiableList(consideredLocaleCopy);
	}

	/**
	 * Gets the language ranges supplied for negotiation in caller order.
	 *
	 * @return language ranges supplied for negotiation in caller order, not null
	 */
	@NonNull
	public List<@NonNull LanguageRange> getRequestedLanguageRanges() {
		return requestedLanguageRanges;
	}

	/**
	 * Gets the selected supported locale.
	 *
	 * @return selected supported locale, or empty when no locale was acceptable, not null
	 */
	@NonNull
	public Optional<@NonNull Locale> getLocale() {
		return Optional.ofNullable(locale);
	}

	/**
	 * Gets the preference range associated with the selection.
	 *
	 * @return preference range associated with the selection, or empty for no match, not null
	 */
	@NonNull
	public Optional<@NonNull LanguageRange> getLanguageRange() {
		return Optional.ofNullable(languageRange);
	}

	/**
	 * Gets the selected locale's effective preference weight after specific-range overrides.
	 *
	 * @return selected locale's effective preference weight after specific-range overrides, or empty for no match,
	 * not null
	 */
	@NonNull
	public Optional<@NonNull Double> getEffectiveWeight() {
		return Optional.ofNullable(effectiveWeight);
	}

	/**
	 * Gets the relationship used for the selection.
	 *
	 * @return relationship used for the selection, or {@link LocaleMatchType#NONE}, not null
	 */
	@NonNull
	public LocaleMatchType getMatchType() {
		return matchType;
	}

	/**
	 * Gets the configured fallback that {@link LocaleMatcher#bestMatchFor(List)} would use for no match.
	 *
	 * @return configured fallback that {@link LocaleMatcher#bestMatchFor(List)} would use for no match, not null
	 */
	@NonNull
	public Locale getFallbackLocale() {
		return fallbackLocale;
	}

	/**
	 * Gets the ordered supported locales considered by negotiation.
	 *
	 * @return ordered supported locales considered by negotiation, not null
	 */
	@NonNull
	public List<@NonNull Locale> getConsideredLocales() {
		return consideredLocales;
	}

	/**
	 * Reports whether negotiation selected an acceptable supported locale.
	 *
	 * @return whether negotiation selected an acceptable supported locale, not null
	 */
	@NonNull
	public Boolean isMatch() {
		return locale != null;
	}

	@Override
	public boolean equals(@Nullable Object object) {
		if (this == object)
			return true;
		if (!(object instanceof LocaleMatchResult))
			return false;
		LocaleMatchResult that = (LocaleMatchResult) object;
		return Objects.equals(requestedLanguageRanges, that.requestedLanguageRanges)
				&& Objects.equals(locale, that.locale)
				&& Objects.equals(languageRange, that.languageRange)
				&& Objects.equals(effectiveWeight, that.effectiveWeight)
				&& matchType == that.matchType
				&& Objects.equals(fallbackLocale, that.fallbackLocale)
				&& Objects.equals(consideredLocales, that.consideredLocales);
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestedLanguageRanges, locale, languageRange, effectiveWeight, matchType, fallbackLocale,
				consideredLocales);
	}

	@Override
	@NonNull
	public String toString() {
		return format("%s{requestedLanguageRanges=%s, locale=%s, languageRange=%s, effectiveWeight=%s, matchType=%s, fallbackLocale=%s, consideredLocales=%s}",
				getClass().getSimpleName(), requestedLanguageRanges, locale, languageRange, effectiveWeight, matchType,
				fallbackLocale, consideredLocales);
	}

	/**
	 * Replaces this value with a proxy that serializes language ranges as range/weight pairs.
	 *
	 * @return serialization proxy, not null
	 */
	private Object writeReplace() {
		return new SerializationProxy(this);
	}

	/**
	 * Rejects direct deserialization so construction always passes through the validating proxy.
	 *
	 * @param inputStream serialized input, not null
	 * @throws InvalidObjectException always
	 */
	private void readObject(ObjectInputStream inputStream) throws InvalidObjectException {
		throw new InvalidObjectException("Serialization proxy required");
	}

	private static final class SerializationProxy implements Serializable {
		private static final long serialVersionUID = 1L;

		@NonNull private final String[] requestedLanguageRanges;
		@NonNull private final double[] requestedLanguageRangeWeights;
		@Nullable private final Locale locale;
		private final int languageRangeIndex;
		@Nullable private final Double effectiveWeight;
		@NonNull private final LocaleMatchType matchType;
		@NonNull private final Locale fallbackLocale;
		@NonNull private final Locale[] consideredLocales;

		private SerializationProxy(@NonNull LocaleMatchResult localeMatchResult) {
			int requestedLanguageRangeCount = localeMatchResult.requestedLanguageRanges.size();
			this.requestedLanguageRanges = new String[requestedLanguageRangeCount];
			this.requestedLanguageRangeWeights = new double[requestedLanguageRangeCount];

			for (int index = 0; index < requestedLanguageRangeCount; ++index) {
				LanguageRange requestedLanguageRange = localeMatchResult.requestedLanguageRanges.get(index);
				this.requestedLanguageRanges[index] = requestedLanguageRange.getRange();
				this.requestedLanguageRangeWeights[index] = requestedLanguageRange.getWeight();
			}

			this.locale = localeMatchResult.locale;
			this.languageRangeIndex = localeMatchResult.languageRange == null ? -1
					: localeMatchResult.requestedLanguageRanges.indexOf(localeMatchResult.languageRange);
			this.effectiveWeight = localeMatchResult.effectiveWeight;
			this.matchType = localeMatchResult.matchType;
			this.fallbackLocale = localeMatchResult.fallbackLocale;
			this.consideredLocales = localeMatchResult.consideredLocales.toArray(new Locale[0]);
		}

		private Object readResolve() throws InvalidObjectException {
			try {
				if (requestedLanguageRanges.length != requestedLanguageRangeWeights.length)
					throw new IllegalArgumentException("Language range values and weights must have equal lengths");

				List<@NonNull LanguageRange> reconstructedLanguageRanges =
						new ArrayList<>(requestedLanguageRanges.length);

				for (int index = 0; index < requestedLanguageRanges.length; ++index)
					reconstructedLanguageRanges.add(
							new LanguageRange(requestedLanguageRanges[index], requestedLanguageRangeWeights[index]));

				LanguageRange reconstructedLanguageRange = null;

				if (languageRangeIndex < -1 || languageRangeIndex >= reconstructedLanguageRanges.size())
					throw new IllegalArgumentException("Matched language range index is outside the requested ranges");
				if (languageRangeIndex >= 0)
					reconstructedLanguageRange = reconstructedLanguageRanges.get(languageRangeIndex);

				return new LocaleMatchResult(reconstructedLanguageRanges, locale, reconstructedLanguageRange,
						effectiveWeight, matchType, fallbackLocale, List.of(consideredLocales));
			} catch (RuntimeException exception) {
				InvalidObjectException invalidObjectException =
						new InvalidObjectException("Invalid serialized LocaleMatchResult");
				invalidObjectException.initCause(exception);
				throw invalidObjectException;
			}
		}
	}
}
