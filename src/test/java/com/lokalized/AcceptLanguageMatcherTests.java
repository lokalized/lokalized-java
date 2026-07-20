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

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ThreadSafe
public class AcceptLanguageMatcherTests {
	private static final int MAXIMUM_HEADER_CHARACTERS = 4_096;
	private static final Locale FALLBACK_LOCALE = Locale.forLanguageTag("en-US");
	private static final Locale BRITISH_ENGLISH = Locale.forLanguageTag("en-GB");
	private static final Locale FRENCH = Locale.forLanguageTag("fr");
	private static final Locale HEBREW = Locale.forLanguageTag("he");

	@Test
	public void missingBlankMalformedAndOversizedHeadersUseFallback() {
		Strings strings = strings();

		assertEquals(FALLBACK_LOCALE, strings.bestMatchForAcceptLanguage(null));
		assertEquals(FALLBACK_LOCALE, strings.bestMatchForAcceptLanguage(""));
		assertEquals(FALLBACK_LOCALE, strings.bestMatchForAcceptLanguage(" \t\r\n"));
		assertEquals(FALLBACK_LOCALE, strings.bestMatchForAcceptLanguage("fr;q=invalid"));
		assertEquals(FALLBACK_LOCALE, strings.bestMatchForAcceptLanguage(
				paddedHeader("fr", MAXIMUM_HEADER_CHARACTERS + 1)));
	}

	@Test
	public void validWeightedHeaderMatchesStrictParsedListApi() {
		Strings strings = strings();
		String header = "fr-CA,fr;q=0.8,en-US;q=0.5";

		assertEquals(strings.bestMatchFor(LanguageRange.parse(header)),
				strings.bestMatchForAcceptLanguage(header));
	}

	@Test
	public void zeroWeightExclusionIsPreserved() {
		Strings strings = strings();

		assertEquals(BRITISH_ENGLISH,
				strings.bestMatchForAcceptLanguage("en;q=1,fr;q=0.5,en-US;q=0"));
	}

	@Test
	public void ianaExpansionPastParsedRangeLimitUsesFallbackWithoutTruncation() {
		Strings strings = strings();
		String header = "he,id,yi,en,fr,de,es,it,pt,nl,sv,no,da,fi,is,pl,cs,sk,sl,hr,sr,ro,bg,ru,uk,el,tr,ar,fa,ur";
		List<LanguageRange> expandedRanges = LanguageRange.parse(header);

		assertTrue(expandedRanges.size() > LocaleMatcher.MAXIMUM_LANGUAGE_RANGES);
		assertEquals(FALLBACK_LOCALE, strings.bestMatchForAcceptLanguage(header));
	}

	@Test
	public void rawHeaderCharacterLimitIsInclusive() {
		Strings strings = strings();
		String atLimit = paddedHeader("fr", MAXIMUM_HEADER_CHARACTERS);
		String overLimit = paddedHeader("fr", MAXIMUM_HEADER_CHARACTERS + 1);

		assertEquals(MAXIMUM_HEADER_CHARACTERS, atLimit.length());
		assertEquals(FRENCH, strings.bestMatchForAcceptLanguage(atLimit));
		assertEquals(FALLBACK_LOCALE, strings.bestMatchForAcceptLanguage(overLimit));
	}

	@Test
	public void strictParsedListApiStillRejectsTooManyRanges() {
		Strings strings = strings();
		List<LanguageRange> excessiveRanges = Collections.nCopies(LocaleMatcher.MAXIMUM_LANGUAGE_RANGES + 1,
				new LanguageRange("fr"));

		assertThrows(IllegalArgumentException.class, () -> strings.bestMatchFor(excessiveRanges));
		assertThrows(IllegalArgumentException.class, () -> strings.matchFor(excessiveRanges));
	}

	@Test
	public void rawHeaderMatchingIsSafeForConcurrentUse() throws Exception {
		Strings strings = strings();
		ExecutorService executorService = Executors.newFixedThreadPool(8);

		try {
			List<Future<Locale>> results = new ArrayList<>();

			for (int index = 0; index < 100; ++index)
				results.add(executorService.submit(() -> strings.bestMatchForAcceptLanguage("fr")));

			for (Future<Locale> result : results)
				assertEquals(FRENCH, result.get(10, TimeUnit.SECONDS));
		} finally {
			executorService.shutdownNow();
			assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private static Strings strings() {
		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
		localizedStringsByLocale.put(FALLBACK_LOCALE, localizedStrings("American English"));
		localizedStringsByLocale.put(BRITISH_ENGLISH, localizedStrings("British English"));
		localizedStringsByLocale.put(FRENCH, localizedStrings("French"));
		localizedStringsByLocale.put(HEBREW, localizedStrings("Hebrew"));

		return Strings.withFallbackLocale(FALLBACK_LOCALE)
				.localizedStringSupplier(() -> localizedStringsByLocale)
				.localeSupplier(matcher -> FALLBACK_LOCALE)
				.tiebreakerLocalesByLanguageCode(Map.of("en", List.of(FALLBACK_LOCALE, BRITISH_ENGLISH)))
				.build();
	}

	private static Set<LocalizedString> localizedStrings(String translation) {
		return Set.of(new LocalizedString.Builder("hello").translation(translation).build());
	}

	private static String paddedHeader(String languageRange, int length) {
		StringBuilder header = new StringBuilder(length).append(languageRange);

		while (header.length() < length)
			header.append(' ');

		return header.toString();
	}
}
