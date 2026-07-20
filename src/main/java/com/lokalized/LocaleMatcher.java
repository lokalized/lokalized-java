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

import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;

/**
 * Contract for matching an input {@link Locale} or {@link List}{@code <}{@link LanguageRange}{@code >} to an appropriate localized strings {@link Locale}.
 * <p>
 * Lokalized's implementation prefers exact and CLDR-canonical matches, then CLDR parent-locale fallback,
 * then script-aware likely-subtag matches. If multiple supported localized strings files still share the same language,
 * configured tiebreakers determine which locale wins. Unmatched, root, and undetermined requests resolve to the
 * configured fallback locale when using {@code bestMatchFor(...)}. The strict {@code matchFor(...)} methods represent
 * the same state as an unmatched {@link LocaleMatchResult} instead of manufacturing a match.
 * <p>
 * Non-bare wildcard language ranges use RFC 4647 extended filtering only; they are not broadened through
 * CLDR, likely-subtag, or primary-language heuristics, and successful results report
 * {@link LocaleMatchType#EXTENDED_RANGE}. When multiple ranges match one supported locale, the most-specific exact,
 * canonical, or structural range determines its effective quality, including a {@code q=0} exclusion.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public interface LocaleMatcher {
	/**
	 * Maximum number of parsed language ranges accepted by one matching operation: 32.
	 * <p>
	 * {@link LanguageRange#parse(String)} may add IANA-equivalent ranges, so this limit applies to the returned list,
	 * not the number of comma-separated ranges in the source header.
	 *
	 * @since 3.0.0
	 */
	@NonNull
	public static final Integer MAXIMUM_LANGUAGE_RANGES = 32;

	/**
	 * Strictly negotiates a locale without manufacturing a configured-fallback match.
	 *
	 * @param locale requested locale, not null
	 * @return diagnostic match result, not null
	 * @throws IllegalArgumentException if the locale is not well-formed
	 * @since 3.0.0
	 */
	@NonNull
	default LocaleMatchResult matchFor(@NonNull Locale locale) {
		LocaleUtils.requireWellFormed(locale, "Requested locale");
		return matchFor(List.of(new LanguageRange(locale.toLanguageTag())));
	}

	/**
	 * Strictly negotiates language ranges without manufacturing a configured-fallback match.
	 *
	 * @param languageRanges requested language ranges, not null
	 * @return diagnostic match result, not null
	 * @throws IllegalArgumentException if more than {@link #MAXIMUM_LANGUAGE_RANGES} language ranges are supplied
	 * @since 3.0.0
	 */
	@NonNull
	LocaleMatchResult matchFor(@NonNull List<@NonNull LanguageRange> languageRanges);

	/**
	 * Given a locale, determine the best-matching localized strings file's locale.
	 *
	 * @param locale the locale for which to find the best match.
	 * @return the best-matching locale, not null
	 * @throws IllegalArgumentException if the locale is not well-formed
	 */
	@NonNull
	Locale bestMatchFor(@NonNull Locale locale);

	/**
	 * Given a list of language ranges (e.g. as parsed from an {@code Accept-Language} HTTP request header), determine the best-matching localized strings file's locale.
	 *
	 * @param languageRanges the ordered list of language ranges for which to find the best match.
	 * @return the best-matching locale, not null
	 * @throws IllegalArgumentException if more than {@link #MAXIMUM_LANGUAGE_RANGES} language ranges are supplied
	 */
	@NonNull
	Locale bestMatchFor(@NonNull List<@NonNull LanguageRange> languageRanges);

	/**
	 * Given a raw {@code Accept-Language} HTTP field value, determines the best-matching localized strings
	 * file's locale.
	 * <p>
	 * This is a fail-soft convenience for request handling. A missing, blank, malformed, or longer than 4,096 UTF-16
	 * code-unit value returns the configured fallback locale. The length limit is applied before parsing so parser work
	 * is bounded independently of the parsed-range limit. The configured fallback is also returned if parsing produces
	 * more than {@link #MAXIMUM_LANGUAGE_RANGES} ranges, which can happen when {@link LanguageRange#parse(String)} adds
	 * IANA-equivalent ranges. A valid parsed list is passed through whole; preferences are never truncated. Use
	 * {@link #matchFor(List)} or {@link #bestMatchFor(List)} when language ranges have already been parsed and strict
	 * limit enforcement is desired.
	 *
	 * @param acceptLanguage raw, already-combined {@code Accept-Language} field value, or null if absent
	 * @return the best-matching locale, or the configured fallback for unusable input, not null
	 * @since 3.0.0
	 */
	@NonNull
	default Locale bestMatchForAcceptLanguage(@Nullable String acceptLanguage) {
		if (acceptLanguage == null ||
				acceptLanguage.length() > 4_096 ||
				acceptLanguage.trim().isEmpty())
			return bestMatchFor(List.of());

		List<@NonNull LanguageRange> languageRanges;

		try {
			languageRanges = LanguageRange.parse(acceptLanguage);
		} catch (IllegalArgumentException exception) {
			return bestMatchFor(List.of());
		}

		if (languageRanges.size() > MAXIMUM_LANGUAGE_RANGES)
			return bestMatchFor(List.of());

		return bestMatchFor(languageRanges);
	}
}
