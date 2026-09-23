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
	 * Parsing a header, whether with {@link #parseLanguageRanges(String)} or {@link LanguageRange#parse(String)}, may
	 * add IANA-equivalent ranges, so this limit applies to the parsed list, not the number of comma-separated ranges in
	 * the source header. The two parsers can add different equivalents, and {@link LanguageRange#parse(String)} adds
	 * whatever the running JDK's table holds, so the same header can be within the limit under one and over it under
	 * the other.
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
	 * is bounded independently of the parsed-range limit. HTTP optional whitespace and empty list elements are
	 * normalized before parsing, and the value is then parsed with {@link #parseLanguageRanges(String)}. The configured
	 * fallback is also returned if parsing produces more than {@link #MAXIMUM_LANGUAGE_RANGES} ranges, which can happen
	 * when parsing adds IANA-equivalent ranges. A valid parsed list is passed through whole; preferences are never
	 * truncated. Use {@link #matchFor(List)} or {@link #bestMatchFor(List)} when language ranges have already been
	 * parsed and strict limit enforcement is desired.
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

		String normalizedAcceptLanguage = normalizeAcceptLanguage(acceptLanguage);

		if (normalizedAcceptLanguage.isEmpty())
			return bestMatchFor(List.of());

		List<@NonNull LanguageRange> languageRanges;

		try {
			languageRanges = parseLanguageRanges(normalizedAcceptLanguage);
		} catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
			return bestMatchFor(List.of());
		}

		if (languageRanges.size() > MAXIMUM_LANGUAGE_RANGES)
			return bestMatchFor(List.of());

		return bestMatchFor(languageRanges);
	}

	/**
	 * Parses a language-range list, such as an {@code Accept-Language} field value, adding IANA-equivalent ranges.
	 * <p>
	 * The grammar, weights, result order, de-duplication, and exceptions are those of {@link LanguageRange#parse(String)}:
	 * spaces are removed, the value is lowercased, an {@code accept-language:} prefix is dropped, members are separated by
	 * commas, and each member may carry a {@code ;q=} weight. Only the source of the added equivalent ranges differs. This
	 * default takes them from the IANA Language Subtag Registry snapshot bundled in Lokalized
	 * ({@link LanguageRangeEquivalents#IANA_REGISTRY}), so the equivalents it adds are the same on every JDK, where
	 * {@link LanguageRange#parse(String)} uses the running JDK's table. A {@link Strings} instance uses the source
	 * configured with {@link Strings.Builder#languageRangeEquivalents(LanguageRangeEquivalents)}.
	 * <p>
	 * Unlike {@link #bestMatchForAcceptLanguage(String)}, this method is strict: it applies no length limit, rejects an
	 * empty value, horizontal tabs, and empty list elements other than trailing ones instead of normalizing them, and
	 * propagates every failure. It does not enforce {@link #MAXIMUM_LANGUAGE_RANGES}; {@link #matchFor(List)} and
	 * {@link #bestMatchFor(List)} reject a longer list.
	 *
	 * @param ranges comma-separated language ranges, optionally weighted, not null
	 * @return the parsed ranges and their equivalents in descending weight order, as an unmodifiable list, not null
	 * @throws NullPointerException      if {@code ranges} is null
	 * @throws IllegalArgumentException  if a language range or weight is ill-formed, or a weight is not between
	 *                                   {@code 0.0} and {@code 1.0}
	 * @throws IndexOutOfBoundsException if a language range consists only of hyphens and the running JDK's
	 *                                   {@link LanguageRange} constructor rejects it this way, as it does on JDK 17
	 *                                   and 21 (JDK 25 through 27 throw {@link IllegalArgumentException} instead);
	 *                                   {@link LanguageRange#parse(String)} fails the same way on each JDK
	 * @since 3.1.0
	 */
	@NonNull
	default List<@NonNull LanguageRange> parseLanguageRanges(@NonNull String ranges) {
		return IanaLanguageEquivalents.parse(ranges);
	}

	/**
	 * Converts RFC 9110 horizontal-tab whitespace to the space form understood by
	 * {@link #parseLanguageRanges(String)} and removes empty HTTP list elements.
	 */
	@NonNull
	private static String normalizeAcceptLanguage(@NonNull String acceptLanguage) {
		StringBuilder normalizedAcceptLanguage = new StringBuilder(acceptLanguage.length());
		int memberStartIndex = 0;

		for (int index = 0; index <= acceptLanguage.length(); ++index) {
			if (index < acceptLanguage.length() && acceptLanguage.charAt(index) != ',')
				continue;

			int firstContentIndex = memberStartIndex;

			while (firstContentIndex < index && isOptionalWhitespace(acceptLanguage.charAt(firstContentIndex)))
				++firstContentIndex;

			int contentEndIndex = index;

			while (contentEndIndex > firstContentIndex &&
					isOptionalWhitespace(acceptLanguage.charAt(contentEndIndex - 1)))
				--contentEndIndex;

			if (firstContentIndex < contentEndIndex) {
				if (normalizedAcceptLanguage.length() > 0)
					normalizedAcceptLanguage.append(',');

				for (int contentIndex = firstContentIndex; contentIndex < contentEndIndex; ++contentIndex) {
					char character = acceptLanguage.charAt(contentIndex);
					normalizedAcceptLanguage.append(character == '\t' ? ' ' : character);
				}
			}

			memberStartIndex = index + 1;
		}

		return normalizedAcceptLanguage.toString();
	}

	private static boolean isOptionalWhitespace(char character) {
		return character == ' ' || character == '\t';
	}
}
