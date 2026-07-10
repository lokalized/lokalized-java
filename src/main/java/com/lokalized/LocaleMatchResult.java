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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Objects;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Strict, immutable diagnostic result for locale negotiation.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
@Immutable
public final class LocaleMatchResult {
	@NonNull private final List<@NonNull LanguageRange> requestedLanguageRanges;
	@Nullable private final Locale locale;
	@Nullable private final LanguageRange languageRange;
	@Nullable private final Double effectiveWeight;
	@NonNull private final LocaleMatchType matchType;
	@NonNull private final Locale fallbackLocale;
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
	 * @throws IllegalArgumentException if matched and unmatched state is mixed, the effective weight is outside
	 *                                  {@code (0, 1]}, or selected/fallback locales were not considered
	 */
	public LocaleMatchResult(@NonNull List<@NonNull LanguageRange> requestedLanguageRanges,
								@Nullable Locale locale, @Nullable LanguageRange languageRange, @Nullable Double effectiveWeight,
								@NonNull LocaleMatchType matchType, @NonNull Locale fallbackLocale,
								@NonNull List<@NonNull Locale> consideredLocales) {
		List<@NonNull LanguageRange> requestedLanguageRangeCopy =
				new ArrayList<>(requireNonNull(requestedLanguageRanges).size());

		for (LanguageRange requestedLanguageRange : requestedLanguageRanges)
			requestedLanguageRangeCopy.add(requireNonNull(requestedLanguageRange));

		this.requestedLanguageRanges = Collections.unmodifiableList(requestedLanguageRangeCopy);
		this.locale = locale;
		this.languageRange = languageRange;
		this.effectiveWeight = effectiveWeight;
		this.matchType = requireNonNull(matchType);
		this.fallbackLocale = requireNonNull(fallbackLocale);

		if (locale == null && (languageRange != null || effectiveWeight != null || matchType != LocaleMatchType.NONE))
			throw new IllegalArgumentException("An unmatched locale result must use NONE and omit range and weight");

		if (locale != null && (languageRange == null || effectiveWeight == null || matchType == LocaleMatchType.NONE))
			throw new IllegalArgumentException("A matched locale result requires a range, weight, and non-NONE match type");
		if (languageRange != null && !requestedLanguageRangeCopy.contains(languageRange))
			throw new IllegalArgumentException("The matched language range must be present in requested language ranges");

		if (effectiveWeight != null && (!Double.isFinite(effectiveWeight) || effectiveWeight <= 0.0 || effectiveWeight > 1.0))
			throw new IllegalArgumentException("A matched locale result requires a finite effective weight greater than 0 and at most 1");
		List<@NonNull Locale> consideredLocaleCopy = new ArrayList<>(requireNonNull(consideredLocales).size());

		for (Locale consideredLocale : consideredLocales)
			consideredLocaleCopy.add(requireNonNull(consideredLocale));

		if (new LinkedHashSet<>(consideredLocaleCopy).size() != consideredLocaleCopy.size())
			throw new IllegalArgumentException("Considered locales must not contain duplicates");
		if (!consideredLocaleCopy.contains(fallbackLocale))
			throw new IllegalArgumentException("The fallback locale must be present in considered locales");
		if (locale != null && !consideredLocaleCopy.contains(locale))
			throw new IllegalArgumentException("The selected locale must be present in considered locales");

		this.consideredLocales = Collections.unmodifiableList(consideredLocaleCopy);
	}

	/** @return language ranges supplied for negotiation in caller order, not null */
	@NonNull
	public List<@NonNull LanguageRange> getRequestedLanguageRanges() {
		return requestedLanguageRanges;
	}

	/** @return selected supported locale, or empty when no locale was acceptable, not null */
	@NonNull
	public Optional<@NonNull Locale> getLocale() {
		return Optional.ofNullable(locale);
	}

	/** @return preference range associated with the selection, or empty for no match, not null */
	@NonNull
	public Optional<@NonNull LanguageRange> getLanguageRange() {
		return Optional.ofNullable(languageRange);
	}

	/**
	 * @return selected locale's effective preference weight after specific-range overrides, or empty for no match,
	 * not null
	 */
	@NonNull
	public Optional<@NonNull Double> getEffectiveWeight() {
		return Optional.ofNullable(effectiveWeight);
	}

	/** @return relationship used for the selection, or {@link LocaleMatchType#NONE}, not null */
	@NonNull
	public LocaleMatchType getMatchType() {
		return matchType;
	}

	/** @return configured fallback that {@link LocaleMatcher#bestMatchFor(List)} would use for no match, not null */
	@NonNull
	public Locale getFallbackLocale() {
		return fallbackLocale;
	}

	/** @return ordered supported locales considered by negotiation, not null */
	@NonNull
	public List<@NonNull Locale> getConsideredLocales() {
		return consideredLocales;
	}

	/** @return whether negotiation selected an acceptable supported locale, not null */
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
}
