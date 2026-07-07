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

import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;

/**
 * Contract for matching an input {@link Locale} or {@link List}{@code <}{@link LanguageRange}{@code >} to an appropriate localized strings {@link Locale}.
 * <p>
 * Lokalized's implementation prefers exact and CLDR-canonical matches, then CLDR parent-locale fallback,
 * then script-aware likely-subtag matches. If multiple supported strings files still share the same language,
 * configured tiebreakers determine which locale wins. Unmatched, root, and undetermined requests resolve to the
 * configured fallback locale.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public interface LocaleMatcher {
	/**
	 * Given a locale, determine the best-matching localized strings file's locale.
	 *
	 * @param locale the locale for which to find the best match.
	 * @return the best-matching locale, not null
	 */
	@NonNull
	Locale bestMatchFor(@NonNull Locale locale);

	/**
	 * Given a list of language ranges (e.g. as parsed from an {@code Accept-Language} HTTP request header), determine the best-matching localized strings file's locale.
	 *
	 * @param languageRanges the ordered list of language ranges for which to find the best match.
	 * @return the best-matching locale, not null
	 */
	@NonNull
	Locale bestMatchFor(@NonNull List<@NonNull LanguageRange> languageRanges);
}
