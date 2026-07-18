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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link Strings}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class StringsTests {
	@Test
	public void configurationVerificationTest() {
		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("fake"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.build();
		}, "Should not be able to construct a Strings instance with a fallback locale that doesn't have a corresponding localized strings file");

		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("en"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.build();
		}, "Should not be able to construct a Strings instance with missing tiebreaker information");

		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("en"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.tiebreakerLocalesByLanguageCode(Map.of(
							"en", List.of(Locale.forLanguageTag("en"))
					))
					.build();
		}, "Should not be able to construct a Strings instance with incomplete tiebreaker information");

		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("en"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.tiebreakerLocalesByLanguageCode(Map.of(
							"en", List.of(Locale.forLanguageTag("ja-JA"))
					))
					.build();
		}, "Should not be able to construct a Strings instance with invalid tiebreaker information");

		// This is a legal construction because it provides all necessary fallbacks
		Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();
	}

	@Test
	public void basicLanguageSpecificityTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en-GB"))
				.build();

		String translation = strings.get("I am going on vacation");

		assertEquals("I am going on holiday", translation);
	}

	@Test
	public void sharedStringsInstanceSupportsConcurrentLookup() throws Exception {
		Locale english = Locale.forLanguageTag("en");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(english))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(english, britishEnglish)
				))
				.build();

		ExecutorService executorService = Executors.newFixedThreadPool(8);

		try {
			List<Future<?>> futures = new ArrayList<>();

			for (int i = 0; i < 8; ++i) {
				futures.add(executorService.submit(() -> {
					for (int j = 0; j < 100; ++j) {
						assertEquals("I read 1 book",
								strings.get("I read {{bookCount}} books", Map.of("bookCount", 1)));
						assertEquals("I didn't read any books",
								strings.get("I read {{bookCount}} books", Map.of("bookCount", 0)));
						assertEquals("I am going on holiday",
								strings.get("I am going on vacation", TranslationOptions.forLocale(britishEnglish)));
						assertEquals("Found at least 10 results instantly.",
								searchResult(strings, 10, "10", 10, "10", 250, "2 seconds"));
					}
				}));
			}

			for (Future<?> future : futures)
				future.get(10, TimeUnit.SECONDS);
		} finally {
			executorService.shutdownNow();
		}
	}

	@Test
	public void tiebreakerOrderIsRespected() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en-GB"), Locale.forLanguageTag("en"))
				))
				.build();

		Locale bestMatch = strings.bestMatchFor(Locale.forLanguageTag("en-US"));

		assertEquals(Locale.forLanguageTag("en"), bestMatch);
	}

	@Test
	public void rootAndUndeterminedLocalesUseFallbackLocale() {
		LocalizedString localizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("fr"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString),
						Locale.forLanguageTag("fr"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("fr"))
				.build();

		assertEquals(Locale.forLanguageTag("fr"), strings.bestMatchFor(Locale.ROOT));
		assertEquals(Locale.forLanguageTag("fr"), strings.bestMatchFor(Locale.forLanguageTag("und")));
	}

	@Test
	public void undeterminedLanguageRangesDoNotAbortRangeResolution() {
		LocalizedString localizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString),
						Locale.forLanguageTag("de"), Set.of(localizedString),
						Locale.forLanguageTag("fr"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();

		assertEquals(Locale.forLanguageTag("fr"), strings.bestMatchFor(LanguageRange.parse("und,fr;q=0.9")));
		assertEquals(Locale.forLanguageTag("de"), strings.bestMatchFor(LanguageRange.parse("und,de;q=0.9,fr;q=0.8")));
		assertEquals(Locale.forLanguageTag("en"), strings.bestMatchFor(LanguageRange.parse("und")));
	}

	@Test
	public void undeterminedLanguageSubtagsUseFallbackLocale() {
		LocalizedString fallbackLocalizedString = new LocalizedString.Builder("Hello").translation("Bonjour").build();
		LocalizedString englishLocalizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("fr"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(englishLocalizedString),
						Locale.forLanguageTag("fr"), Set.of(fallbackLocalizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("fr"))
				.build();

		assertEquals(Locale.forLanguageTag("fr"), strings.bestMatchFor(Locale.forLanguageTag("und-Latn")));
		assertEquals(Locale.forLanguageTag("fr"), strings.bestMatchFor(Locale.forLanguageTag("und-US")));
		assertEquals("Bonjour", strings.get("Hello", TranslationOptions.forLocale(Locale.forLanguageTag("und-Latn"))));
	}

	@Test
	public void exactPrivateUseLocalizedStringsFilesCanBeSelectedConsistentlyWithoutLanguageTiebreakers() {
		Locale english = Locale.ENGLISH;
		Locale acmeLocale = Locale.forLanguageTag("x-acme");
		Locale betaLocale = Locale.forLanguageTag("x-beta");
		LocalizedString englishGreeting = new LocalizedString.Builder("Hello").translation("English").build();
		LocalizedString acmeGreeting = new LocalizedString.Builder("Hello").translation("Acme").build();
		LocalizedString betaGreeting = new LocalizedString.Builder("Hello").translation("Beta").build();

		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(englishGreeting),
						acmeLocale, Set.of(acmeGreeting),
						betaLocale, Set.of(betaGreeting)))
				.localeSupplier(matcher -> english)
				.build();

		LocaleMatchResult acmeMatchResult = strings.matchFor(acmeLocale);
		assertEquals(acmeLocale, acmeMatchResult.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, acmeMatchResult.getMatchType());
		assertEquals(acmeLocale, strings.bestMatchFor(acmeLocale));
		assertEquals("Acme", strings.get("Hello", TranslationOptions.forLocale(acmeLocale)));
		assertEquals("Acme", strings.get("Hello", TranslationOptions.forLanguageRanges(
				List.of(new LanguageRange("x-acme")))));
		assertEquals("Beta", strings.get("Hello", TranslationOptions.forLanguageRanges(
				List.of(new LanguageRange("x-beta")))));
	}

	@Test
	public void likelySubtagTiesUseConfiguredTiebreakers() {
		LocalizedString enUs = new LocalizedString.Builder("Hello").translation("Hello from en-US").build();
		LocalizedString enGb = new LocalizedString.Builder("Hello").translation("Hello from en-GB").build();
		LocalizedString frFr = new LocalizedString.Builder("Hello").translation("Bonjour de fr-FR").build();
		LocalizedString frCa = new LocalizedString.Builder("Hello").translation("Bonjour de fr-CA").build();
		LocalizedString deDe = new LocalizedString.Builder("Hello").translation("Hallo aus de-DE").build();
		LocalizedString deAt = new LocalizedString.Builder("Hello").translation("Servus aus de-AT").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en-US"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en-US"), Set.of(enUs),
						Locale.forLanguageTag("en-GB"), Set.of(enGb),
						Locale.forLanguageTag("fr-FR"), Set.of(frFr),
						Locale.forLanguageTag("fr-CA"), Set.of(frCa),
						Locale.forLanguageTag("de-DE"), Set.of(deDe),
						Locale.forLanguageTag("de-AT"), Set.of(deAt)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en-US"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("en-GB")),
						"fr", List.of(Locale.forLanguageTag("fr-FR"), Locale.forLanguageTag("fr-CA")),
						"de", List.of(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("de-AT"))
				))
				.build();

		assertEquals(Locale.forLanguageTag("en-US"), strings.bestMatchFor(Locale.forLanguageTag("en")));
		assertEquals(Locale.forLanguageTag("fr-FR"), strings.bestMatchFor(Locale.forLanguageTag("fr")));
		assertEquals(Locale.forLanguageTag("de-DE"), strings.bestMatchFor(Locale.forLanguageTag("de")));
		assertEquals(Locale.forLanguageTag("fr-FR"), strings.bestMatchFor(Locale.forLanguageTag("fr-BE")));

		assertEquals("Bonjour de fr-FR", strings.get("Hello", TranslationOptions.forLocale(Locale.forLanguageTag("fr-BE"))));
		assertEquals("Hello from en-US", strings.get("Hello", TranslationOptions.forLanguageRanges(LanguageRange.parse("en"))));
	}

	@Test
	public void lookupTruncationBeatsLanguageTiebreakers() {
		LocalizedString localizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString),
						Locale.forLanguageTag("zh"), Set.of(localizedString),
						Locale.forLanguageTag("zh-Hant"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"zh", List.of(Locale.forLanguageTag("zh"), Locale.forLanguageTag("zh-Hant"))
				))
				.build();

		assertEquals(Locale.forLanguageTag("zh-Hant"), strings.bestMatchFor(Locale.forLanguageTag("zh-Hant-TW")));
	}

	@Test
	public void bestMatchUsesCldrLikelySubtags() {
		LocalizedString localizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString),
						Locale.forLanguageTag("zh-Hant"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();

		assertEquals(Locale.forLanguageTag("zh-Hant"), strings.bestMatchFor(Locale.forLanguageTag("zh-TW")));
	}

	@Test
	public void bestMatchUsesCldrLikelySubtagScriptsAcrossRegions() {
		LocalizedString localizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString),
						Locale.forLanguageTag("zh"), Set.of(localizedString),
						Locale.forLanguageTag("zh-Hant"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"zh", List.of(Locale.forLanguageTag("zh"), Locale.forLanguageTag("zh-Hant"))
				))
				.build();

		assertEquals(Locale.forLanguageTag("zh-Hant"), strings.bestMatchFor(Locale.forLanguageTag("zh-HK")));
	}

	@Test
	public void bestMatchUsesCldrLanguageAliases() {
		LocalizedString localizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString),
						Locale.forLanguageTag("sr-Latn"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();

		assertEquals(Locale.forLanguageTag("sr-Latn"), strings.bestMatchFor(Locale.forLanguageTag("sh-BA")));
	}

	@Test
	public void norwegianMacrolanguageBridgesToBokmal() {
		LocalizedString englishLocalizedString = new LocalizedString.Builder("Checkout.Title").translation("Checkout").build();
		LocalizedString bokmalLocalizedString = new LocalizedString.Builder("Checkout.Title").translation("Kasse").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(englishLocalizedString),
						Locale.forLanguageTag("nb"), Set.of(bokmalLocalizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();

		assertEquals(Locale.forLanguageTag("nb"), strings.bestMatchFor(Locale.forLanguageTag("no")));
		assertEquals("Kasse", strings.get("Checkout.Title", TranslationOptions.forLocale(Locale.forLanguageTag("no"))));
	}

	@Test
	public void regionalLocalesFallbackPerKeyToCldrParentLocale() {
		LocalizedString fallbackLocalizedString = new LocalizedString.Builder("Colour").translation("Fallback").build();
		LocalizedString parentLocalizedString = new LocalizedString.Builder("Colour").translation("Colour").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(fallbackLocalizedString),
						Locale.forLanguageTag("en-001"), Set.of(parentLocalizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en-001"), Locale.forLanguageTag("en"))
				))
				.build();

		assertEquals("Colour", strings.get("Colour", TranslationOptions.forLocale(Locale.forLanguageTag("en-AU"))));
	}

	@Test
	public void perKeyFallbackDoesNotCrossLikelyScriptBoundaries() {
		LocalizedString fallbackLocalizedString = new LocalizedString.Builder("Checkout.Title").translation("Checkout").build();
		LocalizedString simplifiedLocalizedString = new LocalizedString.Builder("Checkout.Title").translation("结账").build();
		LocalizedString traditionalLocalizedString = new LocalizedString.Builder("Checkout.Submit").translation("結帳").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(fallbackLocalizedString),
						Locale.forLanguageTag("zh"), Set.of(simplifiedLocalizedString),
						Locale.forLanguageTag("zh-Hant"), Set.of(traditionalLocalizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"zh", List.of(Locale.forLanguageTag("zh"), Locale.forLanguageTag("zh-Hant"))
				))
				.build();

		assertEquals("Checkout", strings.get("Checkout.Title", TranslationOptions.forLocale(Locale.forLanguageTag("zh-Hant"))));
		assertEquals("Checkout", strings.get("Checkout.Title", TranslationOptions.forLocale(Locale.forLanguageTag("zh-TW"))));
	}

	@Test
	public void regionalLocalesFallbackPerKeyToBaseLocale() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en-GB"))
				.build();

		assertEquals("I read 1 book", strings.get("I read {{bookCount}} books", Map.of("bookCount", 1)));
	}

	@Test
	public void wildcardLanguageRangesMatchSubtags() {
		LocalizedString localizedString = new LocalizedString.Builder("Hello").translation("Hello").build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en-GB"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en-GB"), Set.of(localizedString),
						Locale.forLanguageTag("en-US"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en-GB"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		Locale bestMatch = strings.bestMatchFor(LanguageRange.parse("*-US"));

		assertEquals(Locale.forLanguageTag("en-US"), bestMatch);
	}

	@Test
	public void cardinalityPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 3);
				}});

		assertEquals("I read 3 books", translation);

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 1);
				}});

		assertEquals("I read 1 book", translation);

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", new BigDecimal("1.0"));
				}});

		assertEquals("I read 1.0 books", translation);

		// Switch to Russian
		strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("ru")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 3);
				}});

		assertEquals("I прочитал 3 книги", translation);

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 5);
				}});

		assertEquals("I прочитал 5 книг", translation);

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", new BigDecimal("1.5"));
				}});

		assertEquals("I прочитал 1.5 книги", translation);
	}

	@Test
	public void ordinalityPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.MASCULINE);
					put("year", 18);
				}});

		assertEquals("His 18th birthday party is next week.", translation);

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.FEMININE);
					put("year", 21);
				}});

		assertEquals("Her 21st birthday party is next week.", translation);

		// Switch to Spanish
		strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("es")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.MASCULINE);
					put("year", 18);
				}});

		assertEquals("Su fiesta de cumpleaños número 18 es la próxima semana.", translation);

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("year", 1);
				}});

		assertEquals("Su primera fiesta de cumpleaños es la próxima semana.", translation);

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.FEMININE);
					put("year", 15);
				}});

		assertEquals("Su quinceañera es la próxima semana.", translation);
	}

	@Test
	public void genderPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
				}});

		assertEquals("He is a good actor.", translation);

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
				}});

		assertEquals("She is a good actress.", translation);

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.COMMON);
				}});

		assertEquals("This person is a good actor.", translation);

		// Switch to Spanish
		strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("es")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
				}});

		assertEquals("Él es un buen actor.", translation);

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
				}});

		assertEquals("Ella es una buena actriz.", translation);

		translation = strings.get("{{heOrShe}} is a great actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
				}});

		assertEquals("Él es un gran actor.", translation);

		translation = strings.get("{{heOrShe}} is a great actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
				}});

		assertEquals("Ella es una gran actriz.", translation);
	}

	@Test
	public void formalityClusivityAnimacyPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("Hello, {{name}}.",
				new HashMap<String, Object>() {{
					put("formality", Formality.CASUAL);
					put("name", "Sam");
				}});

		assertEquals("Hey, Sam.", translation);

		translation = strings.get("Hello, {{name}}.",
				new HashMap<String, Object>() {{
					put("formality", Formality.HONORIFIC);
					put("name", "Dr Smith");
				}});

		assertEquals("Greetings, Dr Smith.", translation);

		translation = strings.get("Hello, {{name}}.",
				new HashMap<String, Object>() {{
					put("formality", Formality.HUMBLE);
					put("name", "Professor Tanaka");
				}});

		assertEquals("I humbly greet you, Professor Tanaka.", translation);

		translation = strings.get("We will meet at noon.",
				new HashMap<String, Object>() {{
					put("clusivity", Clusivity.INCLUSIVE);
				}});

		assertEquals("We (including you) will meet at noon.", translation);

		translation = strings.get("We will meet at noon.",
				new HashMap<String, Object>() {{
					put("clusivity", Clusivity.EXCLUSIVE);
				}});

		assertEquals("We (excluding you) will meet at noon.", translation);

		translation = strings.get("I see {{object}}.",
				new HashMap<String, Object>() {{
					put("animacy", Animacy.ANIMATE);
				}});

		assertEquals("I see him.", translation);

		translation = strings.get("I see {{object}}.",
				new HashMap<String, Object>() {{
					put("animacy", Animacy.INANIMATE);
				}});

		assertEquals("I see it.", translation);
	}

	@Test
	public void grammaticalCaseDefinitenessAndClassifierPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("I gave the book to the recipient.",
				new HashMap<String, Object>() {{
					put("grammaticalCase", GrammaticalCase.DATIVE);
				}});

		assertEquals("Я дал книгу Ивану.", translation);

		translation = strings.get("I gave the book to the recipient.",
				new HashMap<String, Object>() {{
					put("grammaticalCase", GrammaticalCase.ACCUSATIVE);
				}});

		assertEquals("Я дал книгу Ивана.", translation);

		translation = strings.get("Open the document.",
				new HashMap<String, Object>() {{
					put("definiteness", Definiteness.DEFINITE);
				}});

		assertEquals("افتح الكتاب.", translation);

		translation = strings.get("Open the document.",
				new HashMap<String, Object>() {{
					put("definiteness", Definiteness.CONSTRUCT);
				}});

		assertEquals("افتح كتاب.", translation);

		translation = strings.get("I bought {{count}} items.",
				new HashMap<String, Object>() {{
					put("count", 3);
					put("classifier", Classifier.BOUND);
				}});

		assertEquals("3冊買いました。", translation);

		translation = strings.get("I bought {{count}} items.",
				new HashMap<String, Object>() {{
					put("count", 2);
					put("classifier", Classifier.MACHINE);
				}});

		assertEquals("2台買いました。", translation);
	}

	@Test
	public void phoneticPlaceholderTest() {
		LocalizedString localizedString = new LocalizedString.Builder("{{article}} {{noun}}")
				.translation("{{article}} {{noun}}")
				.placeholderDefinitions(Map.of(
						"article", new LocalizedString.LanguageFormTranslation("noun", Map.of(
								Phonetic.VOWEL, "an",
								Phonetic.CONSONANT, "a"
						))
				))
				.build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.phoneticResolver((term, locale) -> {
					String value = term.toString().toLowerCase(Locale.ROOT);
					return value.startsWith("hon") ? Phonetic.VOWEL : Phonetic.CONSONANT;
				})
				.build();

		String translation = strings.get("{{article}} {{noun}}", Map.of(
				"noun", "honor"
		));

		assertEquals("an honor", translation);

		translation = strings.get("{{article}} {{noun}}", Map.of(
				"noun", "user"
		));

		assertEquals("a user", translation);
	}

	@Test
	public void generatedPhoneticInputIsBoundedBeforeMaterialization() {
		LocalizedString localizedString = new LocalizedString.Builder("Phonetic input")
				.translation("{{article}}")
				.placeholderDefinitions(Map.of(
						"article", new LocalizedString.LanguageFormTranslation("term", Map.of(
								Phonetic.VOWEL, "an",
								Phonetic.CONSONANT, "a"))))
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, List.of(localizedString)))
				.localeSupplier((matcher) -> Locale.ENGLISH)
				.phoneticResolver((term, locale) -> {
					throw new AssertionError("Oversized phonetic input must not reach the resolver");
				})
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.runtimeLimits(TranslationRuntimeLimits.builder()
						.maximumInterpolatedOutputCharacters(4)
						.build())
				.build();

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> strings.get("Phonetic input", Map.of(
						"term", new UnmaterializableCharSequence(1_000_000))));

		assertTrue(exception.getMessage().contains("Phonetic input"));
		assertTrue(exception.getMessage().contains("maximum of 4 characters"));
	}

	@Test
	public void optionalPlaceholderValuesAreUnwrapped() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", Optional.of(1));
				}});

		assertEquals("I read 1 book", translation);

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Optional.of(Gender.MASCULINE));
				}});

		assertEquals("He is a good actor.", translation);
	}

	@Test
	public void alternativesTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 0);
				}});

		assertEquals("I didn't read any books", translation);
	}

	@Test
	public void complexTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		// English

		// He was one of the 10 best baseball players.
		// She was one of the 10 best baseball players.
		// This person was one of the 10 best baseball players.
		// He was the best baseball player.
		// She was the best baseball player.
		// This person was the best baseball player.

		String translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
					put("groupSize", 10);
				}});

		assertEquals("He was one of the 10 best baseball players.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
					put("groupSize", 1);
				}});

		assertEquals("He was the best baseball player.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 10);
				}});

		assertEquals("She was one of the 10 best baseball players.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 1);
				}});

		assertEquals("She was the best baseball player.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.", Map.of(
				"heOrShe", Gender.COMMON,
				"groupSize", 10
		));

		assertEquals("This person was one of the 10 best baseball players.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.", Map.of(
				"heOrShe", Gender.COMMON,
				"groupSize", 1
		));

		assertEquals("This person was the best baseball player.", translation);

		// Switch to Spanish
		strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("es")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		// Fue uno de los 10 mejores jugadores de béisbol.
		// Fue una de las 10 mejores jugadoras de béisbol.
		// Esta persona estaba entre las 10 personas que mejor jugaban al béisbol.
		// Él era el mejor jugador de béisbol.
		// Ella era la mejor jugadora de béisbol.
		// Esta persona era quien mejor jugaba al béisbol.

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
					put("groupSize", 10);
				}});

		assertEquals("Fue uno de los 10 mejores jugadores de béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
					put("groupSize", 1);
				}});

		assertEquals("Él era el mejor jugador de béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 10);
				}});

		assertEquals("Fue una de las 10 mejores jugadoras de béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 1);
				}});

		assertEquals("Ella era la mejor jugadora de béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.", Map.of(
				"heOrShe", Gender.COMMON,
				"groupSize", 10
		));

		assertEquals("Esta persona estaba entre las 10 personas que mejor jugaban al béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.", Map.of(
				"heOrShe", Gender.COMMON,
				"groupSize", 1
		));

		assertEquals("Esta persona era quien mejor jugaba al béisbol.", translation);
	}

	@Test
	public void fileBackedExpressionFragmentsCoverIndependentSearchResultAxes() {
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.ENGLISH, Locale.forLanguageTag("en-GB"))))
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		assertEquals("Found no results instantly.", searchResult(strings, 0, "0", 10, "10", 250, "2 seconds"));
		assertEquals("Found no results in 2 seconds.", searchResult(strings, 0, "0", 10, "10", 1_500,
				"2 seconds"));
		assertEquals("Found at least 10 results instantly.", searchResult(strings, 10, "10", 10, "10", 250,
				"2 seconds"));
		assertEquals("Found at least 10 results in 2 seconds.", searchResult(strings, 10, "10", 10, "10", 1_500,
				"2 seconds"));
		assertEquals("Found 3 results instantly.", searchResult(strings, 3, "3", 10, "10", 250, "2 seconds"));
		assertEquals("Found 3 results in 2 seconds.", searchResult(strings, 3, "3", 10, "10", 1_500,
				"2 seconds"));
		assertEquals("Found 1 result instantly.", searchResult(strings, 1, "1", 10, "10", 250, "2 seconds"));
	}

	@Test
	public void missingPlaceholders() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		assertThrows(ExpressionEvaluationException.class,
				() -> strings.get("I read {{bookCount}} books"),
				"Expected missing placeholders in expressions to throw");
	}

	@Test
	public void missingCardinalityPlaceholderValuesThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("You have {{count}} {{items}}")
				.translation("You have {{count}} {{items}}")
				.placeholderDefinitions(Map.of(
						"items", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item",
								Cardinality.OTHER, "items"
						))
				))
				.build();

		Strings strings = buildFailFastStrings(localizedString);

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("You have {{count}} {{items}}"),
				"Expected missing cardinality placeholders to throw");
	}

	@Test
	public void invalidOrdinalityPlaceholderValuesThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("It is your {{year}}{{suffix}} birthday")
				.translation("It is your {{year}}{{suffix}} birthday")
				.placeholderDefinitions(Map.of(
						"suffix", new LocalizedString.LanguageFormTranslation("year", Map.of(
								Ordinality.ONE, "st",
								Ordinality.OTHER, "th"
						))
				))
				.build();

		Strings strings = buildFailFastStrings(localizedString);

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("It is your {{year}}{{suffix}} birthday", Map.of("year", "one")),
				"Expected invalid ordinality placeholders to throw");
	}

	@Test
	public void invalidGenderPlaceholderValuesThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("{{title}} Doe")
				.translation("{{title}} Doe")
				.placeholderDefinitions(Map.of(
						"title", new LocalizedString.LanguageFormTranslation("gender", Map.of(
								Gender.MASCULINE, "Mr",
								Gender.FEMININE, "Ms"
						))
				))
				.build();

		Strings strings = buildFailFastStrings(localizedString);

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("{{title}} Doe", Map.of("gender", "MASCULINE")),
				"Expected invalid gender placeholders to throw");
	}

	@Test
	public void nonCardinalityRangePlaceholdersThrowAtBuildTime() {
		LocalizedString localizedString = new LocalizedString.Builder("Range example")
				.translation("{{form}}")
				.placeholderDefinitions(Map.of(
						"form", new LocalizedString.LanguageFormTranslation(
								new LocalizedString.LanguageFormTranslationRange("start", "end"),
								Map.of(
										Formality.INFORMAL, "Hi",
										Formality.FORMAL, "Hello"
								)
						)
				))
				.build();

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> buildFailFastStrings(localizedString),
				"Expected non-cardinality range placeholders to throw while building Strings");

		assertTrue(exception.getMessage().contains("only supports cardinality"),
				"Expected range error message to mention cardinality-only support");
	}

	@Test
	public void nonCardinalityRangePlaceholdersThrowDuringLoad() {
		assertThrows(LocalizedStringLoadingException.class,
				() -> LocalizedStringLoader.loadFromClasspath("strings-invalid-range"),
				"Expected non-cardinality range placeholders to fail during load");
	}

	@Test
	public void mixedLanguageFormPlaceholdersThrowDuringLoad() {
		assertThrows(LocalizedStringLoadingException.class,
				() -> LocalizedStringLoader.loadFromClasspath("strings-invalid-mixed"),
				"Expected mixed language forms to fail during load");
	}

	@Test
	public void missingPlaceholderTranslationsThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("You have {{count}} {{itemLabel}}")
				.translation("You have {{count}} {{itemLabel}}")
				.placeholderDefinitions(Map.of(
						"itemLabel", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item"
						))
				))
				.build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		assertThrows(IllegalStateException.class,
				() -> strings.get("You have {{count}} {{itemLabel}}", Map.of("count", 2)),
				"Expected missing placeholder translations to throw");
	}

	@Test
	public void useFallbackDoesNotThrowForResolutionFailures() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		assertEquals("I read {{bookCount}} books", strings.get("I read {{bookCount}} books"));
	}

	@Test
	public void missingDirectPlaceholderValuesThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("Hello {{name}}")
				.build();

		Strings strings = buildFailFastStrings(localizedString);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> strings.get("Greeting"),
				"Expected missing direct placeholders to throw");

		assertTrue(exception.getMessage().contains("name"));
	}

	@Test
	public void defaultHandlerReturnsKeyForMissingDirectPlaceholderValues() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("Hello {{name}}")
				.build();

		Strings strings = buildStrings(localizedString);

		assertEquals("Greeting", strings.get("Greeting"),
				"Expected default failure handling to return the key after unresolved placeholders fail resolution");
	}

	@Test
	public void programmaticLocalizedStringsValidatePlaceholderSyntaxAtBuildTime() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("Hello name}}")
				.build();

		assertThrows(IllegalArgumentException.class,
				() -> buildStrings(localizedString),
				"Expected malformed programmatic translation placeholders to fail while building Strings");
	}

	@Test
	public void overDepthProgrammaticAlternativesFailBeforeStructuralHashing() {
		LocalizedString nested = new LocalizedString.Builder("count == 1")
				.translation("done")
				.build();

		for (int depth = 0; depth < 5_000; ++depth)
			nested = new LocalizedString.Builder("count == 1")
					.alternatives(List.of(nested))
					.build();

		LocalizedString root = new LocalizedString.Builder("Deep alternatives")
				.alternatives(List.of(nested))
				.build();
		Locale english = Locale.ENGLISH;

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> Strings.withFallbackLocale(english)
						.localizedStringSupplier(() -> Map.of(english, List.of(root)))
						.localeSupplier((matcher) -> english)
						.build());

		assertTrue(exception.getMessage().contains("Alternative nesting exceeds the maximum depth of 128"));
	}

	@Test
	public void validSharedAlternativeDagDoesNotRequireStructuralHashing() {
		LocalizedString shared = new LocalizedString.Builder("count == 1")
				.translation("selected")
				.build();

		for (int depth = 0; depth < 120; ++depth)
			shared = new LocalizedString.Builder("count == 1")
					.translation("selected")
					.alternatives(List.of(shared, shared))
					.build();

		LocalizedString root = new LocalizedString.Builder("Shared DAG")
				.translation("default")
				.alternatives(List.of(shared))
				.build();
		Strings strings = buildStrings(root);

		assertEquals("default", strings.get("Shared DAG", Map.of("count", 0)));
		assertEquals(List.of(root), new ArrayList<>(
				((DefaultStrings) strings).getLocalizedStringsByLocale().get(Locale.ENGLISH)));
	}

	@Test
	public void equalProgrammaticRootsStillFailAsDuplicateKeys() {
		LocalizedString first = new LocalizedString.Builder("Duplicate").translation("same").build();
		LocalizedString second = new LocalizedString.Builder("Duplicate").translation("same").build();

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> Strings.withFallbackLocale(Locale.ENGLISH)
						.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, List.of(first, second)))
						.localeSupplier((matcher) -> Locale.ENGLISH)
						.build());

		assertTrue(exception.getMessage().contains("Duplicate localized string key 'Duplicate'"));
	}

	@Test
	public void translationFailureHandlerReceivesUnresolvedPlaceholderResolutionFailure() {
		AtomicReference<TranslationFailure> translationFailureHolder = new AtomicReference<>();
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("Hello {{name}}")
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.translationFailureHandler((translationFailure) -> {
					translationFailureHolder.set(translationFailure);
					return TranslationFailureResponse.returnString("handled");
				})
				.build();

		assertEquals("handled", strings.get("Greeting"));

		TranslationFailure translationFailure = translationFailureHolder.get();
		assertTrue(translationFailure != null);
		assertEquals(TranslationFailureReason.RESOLUTION_FAILURE, translationFailure.getReason());
		assertTrue(translationFailure.getCause().isPresent());
		assertTrue(translationFailure.getCause().get() instanceof IllegalArgumentException);
		assertTrue(translationFailure.getCause().get().getMessage().contains("name"));
	}

	@Test
	public void placeholderValuesContainingMustachesAreNotUnresolvedPlaceholders() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("Hello {{name}}")
				.build();

		Strings strings = buildFailFastStrings(localizedString);

		assertEquals("Hello {{Ada}}", strings.get("Greeting", Map.of("name", "{{Ada}}")));
	}

	@Test
	public void escapedLiteralMustachesAreNotInterpolated() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("Hello \\{{name}} and {{name}}")
				.build();

		Strings strings = buildFailFastStrings(localizedString);

		assertEquals("Hello {{name}} and Ada", strings.get("Greeting", Map.of("name", "Ada")));
	}

	@Test
	public void bidiIsolationWrapsExternalPlaceholdersForRtlLocalesByDefault() {
		LocalizedString localizedString = new LocalizedString.Builder("Shipment")
				.translation("تم تجهيز {{code}}")
				.build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ar"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("ar"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("ar"))
				.build();

		assertEquals("تم تجهيز \u2068ACME-42\u2069", strings.get("Shipment", Map.of("code", "ACME-42")));
	}

	@Test
	public void bidiIsolationCanBeDisabled() {
		LocalizedString localizedString = new LocalizedString.Builder("Shipment")
				.translation("تم تجهيز {{code}}")
				.build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ar"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("ar"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("ar"))
				.bidiIsolation(BidiIsolation.NONE)
				.build();

		assertEquals("تم تجهيز ACME-42", strings.get("Shipment", Map.of("code", "ACME-42")));
	}

	@Test
	public void translationOptionsCanOverrideBidiIsolation() {
		LocalizedString localizedString = new LocalizedString.Builder("Shipment")
				.translation("تم تجهيز {{code}}")
				.build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ar"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("ar"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("ar"))
				.build();
		TranslationOptions options = TranslationOptions.builder()
				.bidiIsolation(BidiIsolation.NONE)
				.build();

		assertEquals("تم تجهيز \u2068ACME-42\u2069", strings.get("Shipment", Map.of("code", "ACME-42")));
		assertEquals("تم تجهيز ACME-42", strings.get("Shipment", Map.of("code", "ACME-42"), options));
	}

	@Test
	public void bidiIsolationDoesNotWrapFileDefinedPlaceholders() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("{{title}} {{name}}")
				.placeholderDefinitions(Map.of(
						"title", new LocalizedString.LanguageFormTranslation("gender", Map.of(
								Gender.MASCULINE, "السيد",
								Gender.FEMININE, "السيدة",
								Gender.NEUTER, "العضو",
								Gender.COMMON, "العضو"
						))
				))
				.build();

		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ar"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("ar"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("ar"))
				.build();

		assertEquals("السيد \u2068ACME-42\u2069", strings.get("Greeting", Map.of(
				"gender", Gender.MASCULINE,
				"name", "ACME-42"
		)));
	}

	@Test
	public void bidiIsolationWrapsCallerValuesInsideGeneratedFragments() {
		Locale arabic = Locale.forLanguageTag("ar");
		LocalizedString localizedString = new LocalizedString.Builder("LeadTime")
				.translation("{{leadTime}}")
				.placeholderDefinitions(Map.of(
						"leadTime", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ZERO, "{{formattedCount}} يوم",
								Cardinality.ONE, "{{formattedCount}} يوم",
								Cardinality.TWO, "{{formattedCount}} يومان",
								Cardinality.FEW, "{{formattedCount}} أيام",
								Cardinality.MANY, "{{formattedCount}} يومًا",
								Cardinality.OTHER, "{{formattedCount}} يوم"))))
				.build();
		Strings strings = Strings.withFallbackLocale(arabic)
				.localizedStringSupplier(() -> Map.of(arabic, Set.of(localizedString)))
				.localeSupplier(matcher -> arabic)
				.build();

		assertEquals("\u20680\u2069 يوم", strings.get("LeadTime", Map.of("count", 0, "formattedCount", "0")));
	}

	@Test
	public void bidiIsolationDoesNotWrapExternalPlaceholdersForLtrLocales() {
		LocalizedString localizedString = new LocalizedString.Builder("Shipment")
				.translation("Order {{code}} is ready")
				.build();

		Strings strings = buildFailFastStrings(localizedString);

		assertEquals("Order ACME-42 is ready", strings.get("Shipment", Map.of("code", "ACME-42")));
	}

	@Test
	public void bidiIsolationAlwaysProtectsCallerValuesInLtrTranslationsAndGeneratedFragments() {
		Locale english = Locale.ENGLISH;
		LocalizedString localizedString = new LocalizedString.Builder("Direction")
				.translation("{{arabic}},{{detail}}")
				.placeholderDefinitions(Map.of(
						"detail", new LocalizedString.ExpressionTranslation("{{hebrew}}!")
				))
				.build();
		Map<String, Object> context = Map.of("arabic", "مرحبا", "hebrew", "שלום");
		Map<BidiIsolation, String> expectedByMode = Map.of(
				BidiIsolation.NONE, "مرحبا,שלום!",
				BidiIsolation.RTL_LOCALES, "مرحبا,שלום!",
				BidiIsolation.ALWAYS, "\u2068مرحبا\u2069,\u2068שלום\u2069!"
		);

		for (BidiIsolation mode : BidiIsolation.values()) {
			Strings strings = Strings.withFallbackLocale(english)
					.localizedStringSupplier(() -> Map.of(english, Set.of(localizedString)))
					.localeSupplier(matcher -> english)
					.bidiIsolation(mode)
					.build();

			assertEquals(expectedByMode.get(mode), strings.get("Direction", context));
		}
	}

	@Test
	public void translationFailureHandlerReceivesMissingTranslation() {
		AtomicReference<TranslationFailure> translationFailureHolder = new AtomicReference<>();
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(new LocalizedString.Builder("Hello").translation("Hello").build())
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en-US"))
				.translationFailureHandler((translationFailure) -> {
					translationFailureHolder.set(translationFailure);
					assertThrows(UnsupportedOperationException.class,
							() -> translationFailure.getPlaceholders().put("other", "value"),
							"Expected translation failure placeholders to be immutable");
					assertThrows(UnsupportedOperationException.class,
							() -> translationFailure.getAttemptedLocales().add(Locale.forLanguageTag("fr")),
							"Expected translation failure attempted locales to be immutable");
					return TranslationFailureResponse.returnString("handled");
				})
				.build();

		String translation = strings.get("Missing {{name}}", Map.of("name", "Ada"));
		TranslationFailure translationFailure = translationFailureHolder.get();

		assertEquals("handled", translation);
		assertTrue(translationFailure != null);
		assertEquals("Missing {{name}}", translationFailure.getKey());
		assertEquals(Locale.forLanguageTag("en-US"), translationFailure.getLookupLocale());
		assertEquals(List.of(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("en")),
				translationFailure.getAttemptedLocales());
		assertEquals(Map.of("name", "Ada"), translationFailure.getPlaceholders());
		assertEquals(TranslationFailureReason.MISSING_TRANSLATION, translationFailure.getReason());
		assertTrue(!translationFailure.getCause().isPresent());
	}

	@Test
	public void returnKeyConsumerObservesFailureOnceAndInterpolatesKey() {
		AtomicInteger invocationCount = new AtomicInteger();
		AtomicReference<TranslationFailure> translationFailureHolder = new AtomicReference<>();
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(new LocalizedString.Builder("Hello").translation("Hello").build())
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en-US"))
				.translationFailureHandler(TranslationFailureHandler.returnKey((translationFailure) -> {
					invocationCount.incrementAndGet();
					translationFailureHolder.set(translationFailure);
				}))
				.build();

		assertEquals("Missing Ada", strings.get("Missing {{name}}", Map.of("name", "Ada")));
		assertEquals(1, invocationCount.get());
		assertEquals("Missing {{name}}", translationFailureHolder.get().getKey());
		assertEquals(TranslationFailureReason.MISSING_TRANSLATION, translationFailureHolder.get().getReason());
	}

	@Test
	public void returnKeyRejectsNullConsumer() {
		assertThrows(NullPointerException.class, () -> TranslationFailureHandler.returnKey(null));
	}

	@Test
	public void returnKeyConsumerExceptionPropagates() {
		IllegalStateException consumerException = new IllegalStateException("consumer failed");
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(new LocalizedString.Builder("Hello").translation("Hello").build())
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.translationFailureHandler(TranslationFailureHandler.returnKey((translationFailure) -> {
					throw consumerException;
				}))
				.build();

		assertSame(consumerException, assertThrows(IllegalStateException.class, () -> strings.get("Missing")));
	}

	@Test
	public void translationOptionsCanOverrideTranslationFailureHandler() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(new LocalizedString.Builder("Hello").translation("Hello").build())
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.translationFailureHandler((translationFailure) -> TranslationFailureResponse.returnString("configured"))
				.build();
		TranslationOptions options = TranslationOptions.builder()
				.translationFailureHandler((translationFailure) -> TranslationFailureResponse.returnString("override"))
				.build();

		assertEquals("configured", strings.get("Missing"));
		assertEquals("override", strings.get("Missing", options));
	}

	@Test
	public void translationFailureHandlerReceivesResolutionFailure() {
		AtomicReference<TranslationFailure> translationFailureHolder = new AtomicReference<>();
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.translationFailureHandler((translationFailure) -> {
					translationFailureHolder.set(translationFailure);
					return TranslationFailureResponse.returnString("handled");
				})
				.build();

		String translation = strings.get("I read {{bookCount}} books");
		TranslationFailure translationFailure = translationFailureHolder.get();

		assertEquals("handled", translation);
		assertTrue(translationFailure != null);
		assertEquals("I read {{bookCount}} books", translationFailure.getKey());
		assertEquals(TranslationFailureReason.RESOLUTION_FAILURE, translationFailure.getReason());
		assertTrue(translationFailure.getCause().isPresent());
		assertTrue(translationFailure.getCause().get() instanceof ExpressionEvaluationException);
	}

	@Test
	public void translationFailureHandlerReceivesUnexpectedRuntimeResolutionFailure() {
		AtomicReference<TranslationFailure> translationFailureHolder = new AtomicReference<>();
		LocalizedString localizedString = new LocalizedString.Builder("Phonetic")
				.translation("{{term}}")
				.placeholderDefinitions(Map.of(
						"term", new LocalizedString.LanguageFormTranslation("term", Map.of(
								Phonetic.CONSONANT, "consonant",
								Phonetic.VOWEL, "vowel"
						))
				))
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.phoneticResolver((term, locale) -> {
					throw new UnsupportedOperationException("resolver failed");
				})
				.translationFailureHandler((translationFailure) -> {
					translationFailureHolder.set(translationFailure);
					return TranslationFailureResponse.returnString("handled");
				})
				.build();

		assertEquals("handled", strings.get("Phonetic", Map.of("term", "apple")));
		assertTrue(translationFailureHolder.get().getCause().get() instanceof UnsupportedOperationException);
	}

	@Test
	public void defaultReturnKeyHandlerDoesNotThrowWhenKeyInterpolationFails() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("ar"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("ar"), Set.of(new LocalizedString.Builder("Present").translation("Present").build())
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("ar"))
				.build();

		String translation = strings.get("Missing {{value}}", Map.of("value", new ThrowingToString()));

		assertEquals("Missing {{value}}", translation);
	}

	@Test
	public void throwExceptionTranslationFailureHandlerPreservesFirstResolutionFailureWithoutMutatingIt() {
		LocalizedString requestedLocalizedString = new LocalizedString.Builder("Count")
				.translation("{{count}} {{items}}")
				.placeholderDefinitions(Map.of(
						"items", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item"
						))
				))
				.build();
		LocalizedString fallbackLocalizedString = new LocalizedString.Builder("Count")
				.translation("{{count}} {{items}}")
				.placeholderDefinitions(Map.of(
						"items", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.FEW, "items"
						))
				))
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(fallbackLocalizedString),
						Locale.forLanguageTag("ru"), Set.of(requestedLocalizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("ru"))
				.translationFallbackPolicy(TranslationFallbackPolicy.fallbackOnAnyFailure())
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> strings.get("Count", Map.of("count", 5), TranslationOptions.forLocale(Locale.forLanguageTag("ru"))),
				"Expected throwException() to rethrow the first resolution failure");

		assertTrue(exception.getMessage().contains("MANY"));
		assertEquals(0, exception.getSuppressed().length);
	}

	@Test
	public void localeFallbackDoesNotAccumulateDiagnosticsOnApplicationExceptions() {
		Locale english = Locale.forLanguageTag("en");
		Locale french = Locale.forLanguageTag("fr");
		RuntimeException sharedResolverFailure = new UnsupportedOperationException("shared resolver failure");
		LocalizedString phoneticFrench = new LocalizedString.Builder("Phonetic")
				.translation("{{article}}")
				.placeholderDefinitions(Map.of(
						"article", new LocalizedString.LanguageFormTranslation("term", Map.of(
								Phonetic.VOWEL, "voyelle",
								Phonetic.CONSONANT, "consonne"
						))
				))
				.build();
		LocalizedString brokenEnglish = new LocalizedString.Builder("Phonetic")
				.translation("{{missing}}")
				.build();
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(brokenEnglish),
						french, Set.of(phoneticFrench)
				))
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy(TranslationFallbackPolicy.fallbackOnAnyFailure())
				.phoneticResolver((term, locale) -> {
					throw sharedResolverFailure;
				})
				.build();

		for (int lookup = 0; lookup < 5; ++lookup)
			assertEquals("Phonetic", strings.get("Phonetic", Map.of("term", "apple")));

		assertEquals(0, sharedResolverFailure.getSuppressed().length);
	}

	@Test
	public void throwExceptionTranslationFailureHandlerThrowsForMissingTranslations() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(new LocalizedString.Builder("Hello").translation("Hello").build())
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en-US"))
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		MissingTranslationException exception = assertThrows(MissingTranslationException.class,
				() -> strings.get("Missing {{name}}", Map.of("name", "Ada")),
				"Expected missing translations to throw when configured to throw");

		assertEquals("Missing {{name}}", exception.getKey());
		assertEquals(Map.of("name", "Ada"), exception.getPlaceholders());
		assertEquals(Locale.forLanguageTag("en-US"), exception.getLookupLocale());
		assertTrue(exception.getLocaleMatchResult().isPresent());
		assertEquals(TranslationFailureReason.MISSING_TRANSLATION, exception.getReason());
		assertEquals(List.of(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("en")),
				exception.getAttemptedLocales());
	}

	@Test
	public void translationOptionsLocaleUsesProvidedLocale() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();

		assertEquals("I am going on holiday", strings.get("I am going on vacation",
				TranslationOptions.forLocale(Locale.forLanguageTag("en-GB"))));
	}

	@Test
	public void translationOptionsLanguageRangesUseProvidedLanguageRanges() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();

		assertEquals("I am going on holiday", strings.get("I am going on vacation",
				TranslationOptions.forLanguageRanges(LanguageRange.parse("en-GB;q=1.0,en;q=0.75"))));
	}

	@Test
	public void languageRange() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> matcher.bestMatchFor(LanguageRange.parse("en-US;q=1.0,en-GB;q=0.5,fr-FR;q=0.25")))
				.build();

		String translation = strings.get("I am going on vacation");

		assertEquals("I am going on vacation", translation);

		Strings enGbStrings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> matcher.bestMatchFor(LanguageRange.parse("en-GB;q=1.0,en;q=0.75,en-US;q=0.5,fr-FR;q=0.25")))
				.build();

		String enGbTranslation = enGbStrings.get("I am going on vacation");

		assertEquals("I am going on holiday", enGbTranslation);

		Strings enUsStrings = Strings.withFallbackLocale(Locale.forLanguageTag("ru"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> matcher.bestMatchFor(LanguageRange.parse("en-US;q=1.0,en-GB;q=0.5,fr-FR;q=0.25")))
				.build();

		String enUsTranslation = enUsStrings.get("I am going on vacation");

		assertEquals("I am going on vacation", enUsTranslation);

		Strings ruStrings = Strings.withFallbackLocale(Locale.forLanguageTag("ru"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> matcher.bestMatchFor(LanguageRange.parse("fr;q=1.0,ru;q=0.25")))
				.build();

		String ruTranslation = ruStrings.get("I am going on vacation - MISSING KEY");

		assertEquals("I am going on vacation - MISSING KEY", ruTranslation);

		Strings ru2Strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.localeSupplier((matcher) -> matcher.bestMatchFor(LanguageRange.parse("fr;q=1.0,ru;q=0.25"))).build();

		String ru2Translation = ru2Strings.get("Hello, world!");

		assertEquals("Приветствую, мир", ru2Translation);
	}

	@Test
	public void cardinalityRanges() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String enTranslation = strings.get("The meeting will be {{minHours}}-{{maxHours}} hours long.", new HashMap<String, Object>() {{
			put("minHours", 1.5);
			put("maxHours", 2);
		}});

		assertEquals("The meeting will be 1.5-2 hours long.", enTranslation);
	}

	@Test
	public void noTranslationKeyPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("There is no key for this");

		assertEquals("There is no key for this", translation);

		translation = strings.get("There is no key for {{this}}", new HashMap<String, Object>() {{
			put("this", "that");
		}});

		assertEquals("There is no key for that", translation);
	}

	@Test
	public void specialCharacterPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		String translation = strings.get("We were unable to charge {{amount}} to your credit card.", new HashMap<String, Object>() {{
			put("amount", "$24.99");
		}});

		assertEquals("We were unable to charge $24.99 to your credit card.", translation);
	}

	@Test
	public void matchingAlternativesDoNotStringifyOrLogCallerValues() {
		LocalizedString alternative = new LocalizedString.Builder("count == 1")
				.translation("matched")
				.build();
		LocalizedString localizedString = new LocalizedString.Builder("Choice")
				.translation("default")
				.alternatives(List.of(alternative))
				.build();
		Map<String, Object> context = new HashMap<>();
		context.put("count", 1);
		context.put("privateValue", new ThrowingToString());

		assertEquals("matched", buildFailFastStrings(localizedString).get("Choice", context));
	}

	@Test
	public void fallbackCandidatesUseOneCallerInputSnapshot() {
		Locale english = Locale.forLanguageTag("en");
		Locale french = Locale.forLanguageTag("fr");
		LocalizedString frenchString = new LocalizedString.Builder("Snapshot")
				.translation("{{article}}")
				.placeholderDefinitions(Map.of(
						"article", new LocalizedString.LanguageFormTranslation("word", Map.of(
								Phonetic.VOWEL, "vowel",
								Phonetic.CONSONANT, "consonant"
						))
				))
				.build();
		LocalizedString englishString = new LocalizedString.Builder("Snapshot")
				.translation("{{name}}")
				.build();
		Map<String, Object> context = new HashMap<>();
		context.put("word", "apple");
		context.put("name", "apple");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(englishString),
						french, Set.of(frenchString)
				))
				.localeSupplier((matcher) -> french)
				.translationFallbackPolicy(TranslationFallbackPolicy.fallbackOnAnyFailure())
				.phoneticResolver((term, locale) -> {
					context.put("name", "banana");
					throw new UnsupportedOperationException("primary locale failed");
				})
				.build();

		assertEquals("apple", strings.get("Snapshot", context));
		assertEquals("banana", context.get("name"), "Expected the resolver to have mutated only the caller's live map");
	}

	@Test
	public void cardinalityAndOrdinalityInputsAcceptResolvedFormsAndPluralOperands() {
		LocalizedString localizedString = new LocalizedString.Builder("Forms")
				.translation("{{cardinal}} {{ordinal}}")
				.placeholderDefinitions(Map.of(
						"cardinal", new LocalizedString.LanguageFormTranslation("cardinalValue", Map.of(
								Cardinality.ONE, "one",
								Cardinality.OTHER, "other"
						)),
						"ordinal", new LocalizedString.LanguageFormTranslation("ordinalValue", Map.of(
								Ordinality.ONE, "first",
								Ordinality.OTHER, "otherth"
						))
				))
				.build();
		Strings strings = buildFailFastStrings(localizedString);
		PluralOperands one = PluralOperands.forNumber(1).build();

		assertEquals("one first", strings.get("Forms", Map.of(
				"cardinalValue", Cardinality.ONE,
				"ordinalValue", Ordinality.ONE
		)));
		assertEquals("one first", strings.get("Forms", Map.of(
				"cardinalValue", one,
				"ordinalValue", one
		)));
	}

	@Test
	public void rangeInputsAcceptResolvedFormsAndPluralOperands() {
		Map<LanguageForm, String> rangeTranslations = new HashMap<>();

		for (Cardinality cardinality : Cardinality.values())
			rangeTranslations.put(cardinality, cardinality.name());

		LocalizedString rangeString = new LocalizedString.Builder("Range")
				.translation("{{range}}")
				.placeholderDefinitions(Map.of(
						"range", new LocalizedString.LanguageFormTranslation(
								new LocalizedString.LanguageFormTranslationRange("start", "end"), rangeTranslations)
				))
				.build();
		Locale english = Locale.forLanguageTag("en");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(rangeString)))
				.localeSupplier((matcher) -> english)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();
		PluralOperands one = PluralOperands.forNumber(1).build();

		assertEquals(Cardinality.forRange(Cardinality.ONE, Cardinality.forOperands(one, english), english).name(),
				strings.get("Range", Map.of("start", Cardinality.ONE, "end", one)));
	}

	@Test
	public void alternativeOnlyNoMatchHasDedicatedFailureReason() {
		AtomicReference<TranslationFailure> failureHolder = new AtomicReference<>();
		LocalizedString localizedString = new LocalizedString.Builder("Choice")
				.alternatives(List.of(new LocalizedString.Builder("count == 1").translation("one").build()))
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(Locale.forLanguageTag("en"), Set.of(localizedString)))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.translationFailureHandler((failure) -> {
					failureHolder.set(failure);
					return TranslationFailureResponse.returnString("handled");
				})
				.build();

		assertEquals("handled", strings.get("Choice", Map.of("count", 2)));
		assertEquals(TranslationFailureReason.NO_MATCHING_ALTERNATIVE, failureHolder.get().getReason());
		assertTrue(!failureHolder.get().getCause().isPresent());
	}

	@Test
	public void noMatchingAlternativeReasonIsPreservedByThrowingHandler() {
		Locale english = Locale.ENGLISH;
		LocalizedString localizedString = new LocalizedString.Builder("Choice")
				.alternatives(List.of(new LocalizedString.Builder("count == 1").translation("one").build()))
				.build();
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(localizedString)))
				.localeSupplier(matcher -> english)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		MissingTranslationException exception = assertThrows(MissingTranslationException.class,
				() -> strings.get("Choice", Map.of("count", 2)));
		assertEquals(TranslationFailureReason.NO_MATCHING_ALTERNATIVE, exception.getReason());
		assertEquals(List.of(english), exception.getAttemptedLocales());
	}

	@Test
	public void alternativeOnlyNoMatchContinuesToFallbackLocale() {
		Locale english = Locale.forLanguageTag("en");
		Locale french = Locale.forLanguageTag("fr");
		LocalizedString frenchLocalizedString = new LocalizedString.Builder("Choice")
				.alternatives(List.of(new LocalizedString.Builder("count == 1").translation("un").build()))
				.build();
		LocalizedString englishLocalizedString = new LocalizedString.Builder("Choice")
				.translation("fallback")
				.build();
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(englishLocalizedString),
						french, Set.of(frenchLocalizedString)
				))
				.localeSupplier((matcher) -> french)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		assertEquals("fallback", strings.get("Choice", Map.of("count", 2)));
	}

	@Test
	public void matchingNestedAlternativeDoesNotFallThroughToLaterSibling() {
		LocalizedString unmatchedNested = new LocalizedString.Builder("y == 1")
				.translation("nested")
				.build();
		LocalizedString first = new LocalizedString.Builder("x == 1")
				.alternatives(List.of(unmatchedNested))
				.build();
		LocalizedString second = new LocalizedString.Builder("x > 0")
				.translation("second")
				.build();
		LocalizedString root = new LocalizedString.Builder("Choice")
				.alternatives(List.of(first, second))
				.build();
		AtomicReference<TranslationFailure> failureHolder = new AtomicReference<>();
		Locale english = Locale.ENGLISH;
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(root)))
				.localeSupplier(matcher -> english)
				.translationFailureHandler(failure -> {
					failureHolder.set(failure);
					return TranslationFailureResponse.returnString("handled");
				})
				.build();

		assertEquals("handled", strings.get("Choice", Map.of("x", 1, "y", 2)));
		assertEquals(TranslationFailureReason.NO_MATCHING_ALTERNATIVE, failureHolder.get().getReason());
	}

	@Test
	public void resolutionFailuresDoNotSilentlyFallbackByDefault() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		LocalizedString brokenFrench = new LocalizedString.Builder("Greeting")
				.translation("Bonjour {{name}}")
				.build();
		LocalizedString validEnglish = new LocalizedString.Builder("Greeting")
				.translation("Hello")
				.build();
		Strings safeStrings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(validEnglish), french, Set.of(brokenFrench)))
				.localeSupplier(matcher -> french)
				.translationFailureHandler(failure -> TranslationFailureResponse.returnString(failure.getReason().name()))
				.build();
		Strings legacyStrings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(validEnglish), french, Set.of(brokenFrench)))
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy(TranslationFallbackPolicy.fallbackOnAnyFailure())
				.build();

		assertEquals("RESOLUTION_FAILURE", safeStrings.get("Greeting"));
		assertEquals("Hello", legacyStrings.get("Greeting"));
	}

	@Test
	public void neverFallbackStopsAfterTheFirstMissingTranslation() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(new LocalizedString.Builder("Hello").translation("Hello").build()),
						french, Collections.emptySet()))
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy(TranslationFallbackPolicy.neverFallback())
				.translationFailureHandler(failure -> TranslationFailureResponse.returnString("handled"))
				.build();

		TranslationResult result = strings.getResult("Hello");
		assertEquals("handled", result.getTranslation());
		assertEquals(List.of(french), result.getAttemptedLocales());
	}

	@Test
	public void perInvocationFallbackPolicyOverridesTheConfiguredPolicy() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(new LocalizedString.Builder("Hello").translation("Hello").build()),
						french, Collections.emptySet()))
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy(TranslationFallbackPolicy.neverFallback())
				.build();

		TranslationOptions options = TranslationOptions.builder()
				.translationFallbackPolicy(TranslationFallbackPolicy.fallbackOnMissingTranslationOrNoMatchingAlternative())
				.build();
		assertEquals("Hello", strings.get("Hello", options));
	}

	@Test
	public void fallbackPolicyReceivesTheAttemptOutcome() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		AtomicReference<TranslationFailureReason> reasonHolder = new AtomicReference<>();
		AtomicReference<Locale> localeHolder = new AtomicReference<>();
		AtomicReference<Throwable> causeHolder = new AtomicReference<>();
		LocalizedString brokenFrench = new LocalizedString.Builder("Greeting")
				.translation("Bonjour {{name}}")
				.build();
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(new LocalizedString.Builder("Greeting").translation("Hello").build()),
						french, Set.of(brokenFrench)))
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy((reason, attemptedLocale, cause) -> {
					reasonHolder.set(reason);
					localeHolder.set(attemptedLocale);
					causeHolder.set(cause);
					return false;
				})
				.translationFailureHandler(failure -> TranslationFailureResponse.returnString("handled"))
				.build();

		assertEquals("handled", strings.get("Greeting"));
		assertEquals(TranslationFailureReason.RESOLUTION_FAILURE, reasonHolder.get());
		assertEquals(french, localeHolder.get());
		assertTrue(causeHolder.get() instanceof IllegalArgumentException);
	}

	@Test
	public void nullFallbackPolicyResponseIsRejected() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(new LocalizedString.Builder("Hello").translation("Hello").build()),
						french, Collections.emptySet()))
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy((reason, attemptedLocale, cause) -> null)
				.build();

		NullPointerException exception = assertThrows(NullPointerException.class, () -> strings.get("Hello"));
		assertTrue(exception.getMessage().contains("translationFallbackPolicy returned null"));
	}

	@Test
	public void translationResultReportsResolvedFallbackAndFailureOutcomes() {
		Locale english = Locale.ENGLISH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(
						new LocalizedString.Builder("Hello").translation("Hello").build())))
				.localeSupplier(matcher -> americanEnglish)
				.translationFailureHandler(failure -> TranslationFailureResponse.returnString("handled"))
				.build();

		TranslationResult translated = strings.getResult("Hello");
		assertEquals("Hello", translated.getTranslation());
		assertEquals(TranslationResultStatus.TRANSLATED, translated.getStatus());
		assertEquals(english, translated.getResolvedLocale().orElseThrow(AssertionError::new));
		assertTrue(translated.isFallback());
		assertEquals(List.of(americanEnglish, english), translated.getAttemptedLocales());
		assertThrows(UnsupportedOperationException.class,
				() -> translated.getAttemptedLocales().add(Locale.FRENCH));

		TranslationResult failed = strings.getResult("Missing");
		assertEquals("handled", failed.getTranslation());
		assertEquals(TranslationResultStatus.RETURNED_STRING, failed.getStatus());
		assertEquals(TranslationFailureReason.MISSING_TRANSLATION,
				failed.getFailureReason().orElseThrow(AssertionError::new));
		assertTrue(!failed.getResolvedLocale().isPresent());
	}

	@Test
	public void translationResultPreservesUnmatchedLanguageRangeNegotiation() {
		Locale english = Locale.ENGLISH;
		List<LanguageRange> requestedRanges = LanguageRange.parse("fr-CA,fr;q=0.8");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(
						new LocalizedString.Builder("Hello").translation("Hello").build())))
				.localeSupplier(matcher -> english)
				.build();

		TranslationResult result = strings.getResult("Hello", TranslationOptions.forLanguageRanges(requestedRanges));
		LocaleMatchResult matchResult = result.getLocaleMatchResult().orElseThrow(AssertionError::new);

		assertEquals(english, result.getLookupLocale());
		assertEquals(requestedRanges, matchResult.getRequestedLanguageRanges());
		assertTrue(!matchResult.isMatch());
		assertTrue(result.isFallback());
	}

	@Test
	public void translationResultReportsCldrFallbackForLocaleAndLanguageRangeLookups() {
		Locale english = Locale.ENGLISH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(
						new LocalizedString.Builder("Hello").translation("Hello").build())))
				.localeSupplier(matcher -> english)
				.build();

		TranslationResult localeResult = strings.getResult("Hello", TranslationOptions.forLocale(americanEnglish));
		TranslationResult rangesResult = strings.getResult("Hello",
				TranslationOptions.forLanguageRanges(List.of(new LanguageRange("en-US"))));

		assertEquals(LocaleMatchType.CLDR_FALLBACK, localeResult.getLocaleMatchResult()
				.orElseThrow(AssertionError::new).getMatchType());
		assertEquals(LocaleMatchType.CLDR_FALLBACK, rangesResult.getLocaleMatchResult()
				.orElseThrow(AssertionError::new).getMatchType());
		assertTrue(localeResult.isFallback());
		assertTrue(rangesResult.isFallback());
	}

	@Test
	public void translationResultNegotiationFallbackClassificationIsExplicit() {
		Locale english = Locale.ENGLISH;
		List<Locale> consideredLocales = List.of(english);

		for (LocaleMatchType matchType : List.of(LocaleMatchType.CLDR_FALLBACK, LocaleMatchType.LIKELY_SUBTAG,
				LocaleMatchType.PRIMARY_LANGUAGE)) {
			LanguageRange range = new LanguageRange("en-US");
			LocaleMatchResult matchResult = new LocaleMatchResult(List.of(range), english, range, 1.0, matchType,
					english, consideredLocales);
			TranslationResult result = new TranslationResult("Hello", "Hello", english, matchResult, english,
					consideredLocales, TranslationResultStatus.TRANSLATED, null, null);

			assertTrue(result.isFallback(), () -> matchType + " should be classified as fallback");
		}

		for (LocaleMatchType matchType : List.of(LocaleMatchType.EXACT, LocaleMatchType.CANONICAL,
				LocaleMatchType.EXTENDED_RANGE, LocaleMatchType.WILDCARD)) {
			LanguageRange range = new LanguageRange(matchType == LocaleMatchType.WILDCARD ? "*" : "en");
			LocaleMatchResult matchResult = new LocaleMatchResult(List.of(range), english, range, 1.0, matchType,
					english, consideredLocales);
			TranslationResult result = new TranslationResult("Hello", "Hello", english, matchResult, english,
					consideredLocales, TranslationResultStatus.TRANSLATED, null, null);

			assertTrue(!result.isFallback(), () -> matchType + " should not be classified as fallback");
		}
	}

	@Test
	public void localeMatchSupplierPreservesDefaultNegotiationDiagnostics() {
		Locale english = Locale.ENGLISH;
		List<LanguageRange> requestedRanges = LanguageRange.parse("fr-CA,fr;q=0.8");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(
						new LocalizedString.Builder("Hello").translation("Hello").build())))
				.localeMatchSupplier(matcher -> matcher.matchFor(requestedRanges))
				.build();

		TranslationResult result = strings.getResult("Hello");

		assertEquals(requestedRanges, result.getLocaleMatchResult().orElseThrow(AssertionError::new)
				.getRequestedLanguageRanges());
		assertTrue(result.isFallback());
	}

	@Test
	public void translationResultReportsTheActualCanonicalAliasLocalizedStringsFile() {
		Locale romanian = Locale.forLanguageTag("ro");
		Locale moldovan = Locale.forLanguageTag("mo");
		Strings strings = Strings.withFallbackLocale(moldovan)
				.localizedStringSupplier(() -> Map.of(moldovan, Set.of(
						new LocalizedString.Builder("Hello").translation("Salut").build())))
				.localeSupplier(matcher -> romanian)
				.build();

		TranslationResult result = strings.getResult("Hello");

		assertEquals(romanian, result.getLookupLocale());
		assertEquals(moldovan, result.getResolvedLocale().orElseThrow(AssertionError::new));
		assertEquals(List.of(moldovan), result.getAttemptedLocales());
		assertTrue(strings.getSupportedLocales().contains(result.getResolvedLocale().orElseThrow(AssertionError::new)));
		assertThrows(IllegalArgumentException.class, () -> strings.getKeysForLocale(romanian));
		assertThrows(IllegalArgumentException.class, () -> strings.getMissingKeys(moldovan, romanian));
	}

	@Test
	public void translationResultConstructorEnforcesCorrelatedState() {
		Locale english = Locale.ENGLISH;

		assertThrows(IllegalArgumentException.class, () -> new TranslationResult("key", "value", english,
				null, List.of(english), TranslationResultStatus.RETURNED_STRING,
				TranslationFailureReason.MISSING_TRANSLATION, new RuntimeException("wrong cause")));
		assertThrows(IllegalArgumentException.class, () -> new TranslationResult("key", "value", english,
				english, Collections.emptyList(), TranslationResultStatus.TRANSLATED, null, null));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void translationResultConstructorRejectsMalformedLocales() {
		Locale english = Locale.ENGLISH;
		Locale malformed = new Locale("e");

		assertThrows(IllegalArgumentException.class, () -> new TranslationResult("key", "value", malformed,
				english, List.of(english), TranslationResultStatus.TRANSLATED, null, null));
		assertThrows(IllegalArgumentException.class, () -> new TranslationResult("key", "value", english,
				malformed, List.of(malformed), TranslationResultStatus.TRANSLATED, null, null));
	}

	@Test
	public void nullPlaceholderNamesAreRejectedBeforeLookup() {
		Locale english = Locale.ENGLISH;
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(
						new LocalizedString.Builder("Hello").translation("Hello").build())))
				.localeSupplier(matcher -> english)
				.build();
		Map<String, Object> placeholders = new HashMap<>();
		placeholders.put(null, "value");

		assertThrows(IllegalArgumentException.class, () -> strings.getResult("Hello", placeholders));
	}

	@Test
	public void failureKeyInterpolationReturnsRawKeyWhenOutputWouldExceedLimit() {
		String key = "Missing {{value}}";
		char[] oversizedCharacters = new char[
				TranslationRuntimeLimits.DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS + 1];
		Arrays.fill(oversizedCharacters, 'x');
		String oversizedValue = new String(oversizedCharacters);
		Locale english = Locale.forLanguageTag("en");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(
						new LocalizedString.Builder("Known").translation("Known").build()
				)))
				.localeSupplier((matcher) -> english)
				.build();

		assertEquals(key, strings.get(key, Map.of("value", oversizedValue)));
	}

	@Test
	public void generatedPlaceholderExpansionHasACumulativeBudget() {
		Map<String, LocalizedString.LanguageFormTranslation> generated = new LinkedHashMap<>();
		StringBuilder translation = new StringBuilder();

		for (int index = 0; index < 20; ++index) {
			String placeholderName = "A" + index;
			translation.append("{{").append(placeholderName).append("}}");
			generated.put(placeholderName, new LocalizedString.LanguageFormTranslation("count", Map.of(
					Cardinality.ONE, "{{B}}",
					Cardinality.OTHER, "{{B}}")));
		}

		char[] largeCharacters = new char[
				TranslationRuntimeLimits.DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS / 2];
		Arrays.fill(largeCharacters, 'x');
		String largeValue = new String(largeCharacters);
		generated.put("B", new LocalizedString.LanguageFormTranslation("count", Map.of(
				Cardinality.ONE, largeValue,
				Cardinality.OTHER, largeValue)));
		LocalizedString localizedString = new LocalizedString.Builder("Large")
				.translation(translation.toString())
				.placeholderDefinitions(generated)
				.build();
		Locale english = Locale.ENGLISH;
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, Set.of(localizedString)))
				.localeSupplier(matcher -> english)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> strings.get("Large", Map.of("count", 1)));
		assertTrue(exception.getMessage().contains("cumulative limit"));
	}

	@Test
	public void unusedGeneratedPlaceholderTranslationsAreNotResolved() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("Hello")
				.placeholderDefinitions(Map.of(
						"unused", new LocalizedString.LanguageFormTranslation("missingCount", Map.of(
								Cardinality.ONE, "one",
								Cardinality.OTHER, "other"
						))
				))
				.build();

		assertEquals("Hello", buildFailFastStrings(localizedString).get("Greeting"));
	}

	@Test
	public void expressionFragmentsUseDefaultsAndOrderedExactThresholdAndCompoundRules() {
		LocalizedString.ExpressionTranslation fragment = new LocalizedString.ExpressionTranslation("default", List.of(
				new LocalizedString.ExpressionAlternative("count == 1", "exact"),
				new LocalizedString.ExpressionAlternative("count > 2 && enabled == 1", "compound"),
				new LocalizedString.ExpressionAlternative("count >= 2", "threshold")
		));
		LocalizedString localizedString = new LocalizedString.Builder("Selection")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", fragment))
				.build();
		Strings strings = buildFailFastStrings(localizedString);

		assertEquals("default", strings.get("Selection", Map.of("count", 0, "enabled", 1)));
		assertEquals("exact", strings.get("Selection", Map.of("count", 1, "enabled", 1)));
		assertEquals("threshold", strings.get("Selection", Map.of("count", 2, "enabled", 1)));
		assertEquals("compound", strings.get("Selection", Map.of("count", 3, "enabled", 1)));
		assertEquals("threshold", strings.get("Selection", Map.of("count", 3, "enabled", 0)));
	}

	@Test
	public void firstMatchingFragmentPreventsLaterBrokenPredicateEvaluation() {
		LocalizedString localizedString = new LocalizedString.Builder("Ordered")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("default", List.of(
						new LocalizedString.ExpressionAlternative("count == 1", "first"),
						new LocalizedString.ExpressionAlternative("missing == 1", "broken")
				))))
				.build();

		assertEquals("first", buildFailFastStrings(localizedString).get("Ordered", Map.of("count", 1)));
	}

	@Test
	public void expressionFragmentsSupportNumericPluralCardinalityAndOrdinalityOperands() {
		PluralOperands one = PluralOperands.forNumber(1).build();
		PluralOperands two = PluralOperands.forNumber(2).build();
		LocalizedString localizedString = new LocalizedString.Builder("Expression forms")
				.translation("{{exact}} {{cardinal}} {{ordinal}}")
				.placeholderDefinitions(Map.of(
						"exact", new LocalizedString.ExpressionTranslation("not-exact", List.of(
								new LocalizedString.ExpressionAlternative("exactValue == 1", "exact-one"))),
						"cardinal", new LocalizedString.ExpressionTranslation("not-one", List.of(
								new LocalizedString.ExpressionAlternative("cardinalValue == CARDINALITY_ONE", "one"))),
						"ordinal", new LocalizedString.ExpressionTranslation("not-second", List.of(
								new LocalizedString.ExpressionAlternative("ordinalValue == ORDINALITY_TWO", "second")))
				))
				.build();

		assertEquals("exact-one one second", buildFailFastStrings(localizedString).get("Expression forms", Map.of(
				"exactValue", one,
				"cardinalValue", one,
				"ordinalValue", two
		)));
	}

	@Test
	public void expressionFragmentNumericOrderingRejectsFormattedStringsDirectly() {
		LocalizedString localizedString = new LocalizedString.Builder("Numeric")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("slow", List.of(
						new LocalizedString.ExpressionAlternative("duration < 1000", "fast")))))
				.build();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> buildFailFastStrings(localizedString).get("Numeric", Map.of("duration", "999")));
		assertTrue(exception.getMessage().contains("requires numeric operands"));
		assertTrue(exception.getMessage().contains("Number or PluralOperands"));
	}

	@Test
	public void fragmentPredicateOperandFailuresAreResolutionFailures() {
		AtomicReference<TranslationFailure> failureHolder = new AtomicReference<>();
		LocalizedString localizedString = new LocalizedString.Builder("Fragment operand failure")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("slow", List.of(
						new LocalizedString.ExpressionAlternative("duration < 1000", "fast")))))
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.translationFailureHandler(failure -> {
					failureHolder.set(failure);
					return TranslationFailureResponse.returnString("handled");
				})
				.build();

		assertEquals("handled", strings.get("Fragment operand failure"));
		assertEquals(TranslationFailureReason.RESOLUTION_FAILURE, failureHolder.get().getReason());
		assertTrue(failureHolder.get().getCause().orElseThrow(AssertionError::new)
				instanceof ExpressionEvaluationException);

		failureHolder.set(null);
		assertEquals("handled", strings.get("Fragment operand failure", Map.of("duration", "999")));
		assertEquals(TranslationFailureReason.RESOLUTION_FAILURE, failureHolder.get().getReason());
		assertTrue(failureHolder.get().getCause().orElseThrow(AssertionError::new)
				instanceof ExpressionEvaluationException);
	}

	@Test
	public void translationOnlyFragmentsCanComposeCallerAndGeneratedValues() {
		LocalizedString localizedString = new LocalizedString.Builder("Product")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of(
						"productName", new LocalizedString.ExpressionTranslation("Firefox"),
						"label", new LocalizedString.ExpressionTranslation("{{productName}} for {{name}}")
				))
				.build();

		assertEquals("Firefox for Ada", buildFailFastStrings(localizedString).get("Product", Map.of("name", "Ada")));
	}

	@Test
	public void rootIntermediateAndTerminalDefinitionsContributeToSelectedScope() {
		LocalizedString terminal = new LocalizedString.Builder("terminal == 1")
				.translation("{{root}} {{middle}} {{nearest}} {{leaf}}")
				.placeholderDefinitions(Map.of("leaf", new LocalizedString.ExpressionTranslation("leaf")))
				.build();
		LocalizedString intermediate = new LocalizedString.Builder("middle == 1")
				.placeholderDefinitions(Map.of(
						"middle", new LocalizedString.ExpressionTranslation("middle"),
						"nearest", new LocalizedString.ExpressionTranslation("intermediate")))
				.alternatives(List.of(terminal))
				.build();
		LocalizedString root = new LocalizedString.Builder("Scope")
				.placeholderDefinitions(Map.of(
						"root", new LocalizedString.ExpressionTranslation("root"),
						"nearest", new LocalizedString.ExpressionTranslation("root-nearest")))
				.alternatives(List.of(intermediate))
				.build();

		assertEquals("root middle intermediate leaf", buildFailFastStrings(root).get("Scope", Map.of(
				"middle", 1,
				"terminal", 1,
				"root", "caller root",
				"nearest", "caller nearest"
		)));
	}

	@Test
	public void inheritedFragmentsLateBindDependenciesFromTheSelectedChild() {
		LocalizedString child = new LocalizedString.Builder("branch == 1")
				.translation("{{summary}}")
				.placeholderDefinitions(Map.of("noun", new LocalizedString.ExpressionTranslation("child noun")))
				.build();
		LocalizedString root = new LocalizedString.Builder("Late binding")
				.placeholderDefinitions(Map.of("summary", new LocalizedString.ExpressionTranslation("parent {{noun}}")))
				.alternatives(List.of(child))
				.build();

		assertEquals("parent child noun", buildFailFastStrings(root).get("Late binding", Map.of(
				"branch", 1,
				"noun", "caller noun"
		)));
	}

	@Test
	public void inheritedParentFragmentsUseChildOverridesForTheirDependencies() {
		LocalizedString child = new LocalizedString.Builder("branch == 1")
				.translation("{{summary}}")
				.placeholderDefinitions(Map.of("noun", new LocalizedString.ExpressionTranslation("child noun")))
				.build();
		LocalizedString root = new LocalizedString.Builder("Overridden dependency")
				.placeholderDefinitions(Map.of(
						"summary", new LocalizedString.ExpressionTranslation("parent {{noun}}"),
						"noun", new LocalizedString.ExpressionTranslation("parent noun")
				))
				.alternatives(List.of(child))
				.build();

		assertEquals("parent child noun", buildFailFastStrings(root).get("Overridden dependency", Map.of("branch", 1)));
	}

	@Test
	public void nearestChildReplacesTheWholeDefinitionWithoutMergingAlternativesOrForms() {
		LocalizedString child = new LocalizedString.Builder("branch == 1")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("child default")))
				.build();
		LocalizedString root = new LocalizedString.Builder("Replacement")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("parent default", List.of(
						new LocalizedString.ExpressionAlternative("count == 1", "parent one")))))
				.alternatives(List.of(child))
				.build();

		assertEquals("child default", buildFailFastStrings(root).get("Replacement", Map.of(
				"branch", 1,
				"count", 1
		)));

		LocalizedString formChild = new LocalizedString.Builder("branch == 1")
				.translation("{{noun}}")
				.placeholderDefinitions(Map.of("noun", new LocalizedString.LanguageFormTranslation("count", Map.of(
						Cardinality.ONE, "child item"))))
				.build();
		LocalizedString formRoot = new LocalizedString.Builder("Form replacement")
				.placeholderDefinitions(Map.of("noun", new LocalizedString.LanguageFormTranslation("count", Map.of(
						Cardinality.ONE, "parent item",
						Cardinality.OTHER, "parent items"))))
				.alternatives(List.of(formChild))
				.build();
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> buildFailFastStrings(formRoot).get("Form replacement", Map.of("branch", 1, "count", 2)));
		assertTrue(exception.getMessage().contains("Missing Cardinality translation for OTHER"));
	}

	@Test
	public void selectedChildrenCanReplaceDefinitionsAcrossKinds() {
		LocalizedString formChild = new LocalizedString.Builder("branch == 1")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.LanguageFormTranslation("gender", Map.of(
						Gender.MASCULINE, "he",
						Gender.FEMININE, "she",
						Gender.COMMON, "they",
						Gender.NEUTER, "it"
				))))
				.build();
		LocalizedString expressionParent = new LocalizedString.Builder("Expression parent")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("parent")))
				.alternatives(List.of(formChild))
				.build();

		assertEquals("she", buildFailFastStrings(expressionParent).get("Expression parent", Map.of(
				"branch", 1,
				"gender", Gender.FEMININE
		)));

		LocalizedString expressionChild = new LocalizedString.Builder("branch == 1")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("child")))
				.build();
		LocalizedString formParent = new LocalizedString.Builder("Form parent")
				.placeholderDefinitions(Map.of("label", new LocalizedString.LanguageFormTranslation("gender", Map.of(
						Gender.MASCULINE, "parent",
						Gender.FEMININE, "parent",
						Gender.COMMON, "parent",
						Gender.NEUTER, "parent"
				))))
				.alternatives(List.of(expressionChild))
				.build();

		assertEquals("child", buildFailFastStrings(formParent).get("Form parent", Map.of(
				"branch", 1,
				"gender", Gender.FEMININE
		)));
	}

	@Test
	public void selectedBranchDefinitionsShadowCallerValuesAndDoNotLeakAcrossSiblings() {
		LocalizedString first = new LocalizedString.Builder("branch == 1")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("first")))
				.build();
		LocalizedString second = new LocalizedString.Builder("branch == 2")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("second")))
				.build();
		LocalizedString root = new LocalizedString.Builder("Branches")
				.alternatives(List.of(first, second))
				.build();
		Strings strings = buildFailFastStrings(root);

		assertEquals("first", strings.get("Branches", Map.of("branch", 1, "label", "caller")));
		assertEquals("second", strings.get("Branches", Map.of("branch", 2, "label", "caller")));
		assertEquals("first", strings.get("Branches", Map.of("branch", 1, "label", "caller")));
	}

	@Test
	public void expressionAndLanguageFormFragmentsCanFormMixedDependencyChains() {
		LocalizedString localizedString = new LocalizedString.Builder("Chain")
				.translation("{{wrapper}}")
				.placeholderDefinitions(Map.of(
						"wrapper", new LocalizedString.ExpressionTranslation("{{choice}} for {{name}}"),
						"choice", new LocalizedString.ExpressionTranslation("{{noun}}", List.of(
								new LocalizedString.ExpressionAlternative("count == 0", "nothing"))),
						"noun", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item",
								Cardinality.OTHER, "items"))
				))
				.build();
		Strings strings = buildFailFastStrings(localizedString);

		assertEquals("nothing for Ada", strings.get("Chain", Map.of("count", 0, "name", "Ada")));
		assertEquals("item for Ada", strings.get("Chain", Map.of("count", 1, "name", "Ada")));
		assertEquals("items for Ada", strings.get("Chain", Map.of("count", 2, "name", "Ada")));
	}

	@Test
	public void expressionConditionsAndLanguageFormSelectorsAlwaysReadRawCallerValues() {
		LocalizedString localizedString = new LocalizedString.Builder("Raw values")
				.translation("{{count}} {{status}} {{noun}}")
				.placeholderDefinitions(Map.of(
						"count", new LocalizedString.ExpressionTranslation("generated-count"),
						"status", new LocalizedString.ExpressionTranslation("many", List.of(
								new LocalizedString.ExpressionAlternative("count == 1", "single"))),
						"noun", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item",
								Cardinality.OTHER, "items"))
				))
				.build();
		Strings strings = buildFailFastStrings(localizedString);

		assertEquals("generated-count single item", strings.get("Raw values", Map.of("count", 1)));
		assertEquals("generated-count many items", strings.get("Raw values", Map.of("count", 2)));
	}

	@Test
	public void generatedDefinitionsCannotSatisfyMissingRawLanguageFormSelectors() {
		LocalizedString localizedString = new LocalizedString.Builder("Missing raw selector")
				.translation("{{count}} {{noun}}")
				.placeholderDefinitions(Map.of(
						"count", new LocalizedString.ExpressionTranslation("1"),
						"noun", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item",
								Cardinality.OTHER, "items"))
				))
				.build();

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> buildFailFastStrings(localizedString).get("Missing raw selector"));
		assertTrue(exception.getMessage().contains("Missing value for placeholder 'count'"));
	}

	@Test
	public void rangeEndpointsAlwaysReadRawCallerValuesDespiteGeneratedNameCollisions() {
		Map<LanguageForm, String> translations = new LinkedHashMap<>();
		for (Cardinality cardinality : Cardinality.values())
			translations.put(cardinality, cardinality.name());

		LocalizedString localizedString = new LocalizedString.Builder("Raw range")
				.translation("{{start}} {{end}} {{range}}")
				.placeholderDefinitions(Map.of(
						"start", new LocalizedString.ExpressionTranslation("generated-start"),
						"end", new LocalizedString.ExpressionTranslation("generated-end"),
						"range", new LocalizedString.LanguageFormTranslation(
								new LocalizedString.LanguageFormTranslationRange("start", "end"), translations)
				))
				.build();
		Strings strings = buildFailFastStrings(localizedString);
		Cardinality expected = Cardinality.forRange(Cardinality.ONE, Cardinality.OTHER, Locale.ENGLISH);

		assertEquals("generated-start generated-end " + expected.name(),
				strings.get("Raw range", Map.of("start", 1, "end", 2)));

		IllegalArgumentException missingStart = assertThrows(IllegalArgumentException.class,
				() -> strings.get("Raw range", Map.of("end", 2)));
		assertTrue(missingStart.getMessage().contains("Missing range start placeholder 'start'"));

		IllegalArgumentException missingEnd = assertThrows(IllegalArgumentException.class,
				() -> strings.get("Raw range", Map.of("start", 1)));
		assertTrue(missingEnd.getMessage().contains("Missing range end placeholder 'end'"));
	}

	@Test
	public void shadowedUnusedAndUnselectedBrokenDefinitionsRemainUnevaluated() {
		LocalizedString selected = new LocalizedString.Builder("branch == 1")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of(
						"label", new LocalizedString.ExpressionTranslation("safe"),
						"unused", new LocalizedString.ExpressionTranslation("default", List.of(
								new LocalizedString.ExpressionAlternative("missingUnused == 1", "broken")))
				))
				.build();
		LocalizedString unselected = new LocalizedString.Builder("branch == 2")
				.translation("{{broken}}")
				.placeholderDefinitions(Map.of("broken", new LocalizedString.ExpressionTranslation("default", List.of(
						new LocalizedString.ExpressionAlternative("missingSibling == 1", "broken")))))
				.build();
		LocalizedString root = new LocalizedString.Builder("Demand driven")
				.placeholderDefinitions(Map.of("label", new LocalizedString.LanguageFormTranslation("missingShadowed", Map.of(
						Cardinality.ONE, "parent",
						Cardinality.OTHER, "parent"))))
				.alternatives(List.of(selected, unselected))
				.build();

		assertEquals("safe", buildFailFastStrings(root).get("Demand driven", Map.of("branch", 1)));
	}

	@Test
	public void onlyTheSelectedFragmentContributesDependencies() {
		LocalizedString localizedString = new LocalizedString.Builder("Selected dependencies")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of(
						"fragment", new LocalizedString.ExpressionTranslation("{{broken}}", List.of(
								new LocalizedString.ExpressionAlternative("choice == 1", "safe"))),
						"broken", new LocalizedString.LanguageFormTranslation("missing", Map.of(
								Cardinality.ONE, "one",
								Cardinality.OTHER, "other"))
				))
				.build();

		assertEquals("safe", buildFailFastStrings(localizedString).get("Selected dependencies", Map.of("choice", 1)));
	}

	@Test
	public void matchedFragmentResolutionFailureDoesNotFallThroughToLaterRulesOrDefault() {
		LocalizedString localizedString = new LocalizedString.Builder("No fallthrough")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("default", List.of(
						new LocalizedString.ExpressionAlternative("count == 1", "{{missing}}"),
						new LocalizedString.ExpressionAlternative("count >= 1", "later")))))
				.build();

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> buildFailFastStrings(localizedString).get("No fallthrough", Map.of("count", 1)));
		assertTrue(exception.getMessage().contains("missing"));
		assertTrue(exception.getMessage().contains("definition declared at No fallthrough"));
		assertTrue(exception.getMessage().contains("selected expression 'count == 1'"));
	}

	@Test
	public void selectedLanguageFormInterpolationFailuresIdentifyTheSelectedForm() {
		LocalizedString localizedString = new LocalizedString.Builder("Form interpolation provenance")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.LanguageFormTranslation("count", Map.of(
						Cardinality.ONE, "{{missing}}",
						Cardinality.OTHER, "other"))))
				.build();

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> buildFailFastStrings(localizedString).get("Form interpolation provenance", Map.of("count", 1)));
		assertTrue(exception.getMessage().contains("selected Cardinality.ONE"));
		assertTrue(exception.getMessage().contains("definition declared at Form interpolation provenance"));
	}

	@Test
	public void expressionAndMixedGeneratedPlaceholderCyclesFailClearly() {
		LocalizedString direct = new LocalizedString.Builder("Direct expression cycle")
				.translation("{{a}}")
				.placeholderDefinitions(Map.of("a", new LocalizedString.ExpressionTranslation("{{a}}")))
				.build();
		IllegalStateException directException = assertThrows(IllegalStateException.class,
				() -> buildFailFastStrings(direct).get("Direct expression cycle"));
		assertTrue(directException.getMessage().contains("a -> a"));

		LocalizedString mixed = new LocalizedString.Builder("Mixed cycle")
				.translation("{{a}}")
				.placeholderDefinitions(Map.of(
						"a", new LocalizedString.ExpressionTranslation("{{b}}"),
						"b", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "{{a}}",
								Cardinality.OTHER, "{{a}}"))
				))
				.build();
		IllegalStateException mixedException = assertThrows(IllegalStateException.class,
				() -> buildFailFastStrings(mixed).get("Mixed cycle", Map.of("count", 1)));
		assertTrue(mixedException.getMessage().contains("a -> b -> a"));
	}

	@Test
	public void fragmentFailuresUseResolutionFailurePolicyAndReevaluateForEachCandidateLocale() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		LocalizedString frenchString = new LocalizedString.Builder("Locale-sensitive fragment")
				.translation("{{fragment}} {{missingOutput}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("French other", List.of(
						new LocalizedString.ExpressionAlternative("count == CARDINALITY_ONE", "French one")))))
				.build();
		LocalizedString englishString = new LocalizedString.Builder("Locale-sensitive fragment")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("English other", List.of(
						new LocalizedString.ExpressionAlternative("count == CARDINALITY_ONE", "English one")))))
				.build();
		Map<Locale, Set<LocalizedString>> localizedStrings = Map.of(
				english, Set.of(englishString),
				french, Set.of(frenchString)
		);
		Strings defaultPolicy = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.translationFailureHandler(failure ->
						TranslationFailureResponse.returnString(failure.getReason().name()))
				.build();
		Strings fallbackOnAnyFailure = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy(TranslationFallbackPolicy.fallbackOnAnyFailure())
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		assertEquals("RESOLUTION_FAILURE", defaultPolicy.get("Locale-sensitive fragment", Map.of("count", 0)));
		assertEquals("English other", fallbackOnAnyFailure.get("Locale-sensitive fragment", Map.of("count", 0)),
				"English must reevaluate cardinality after French classified zero as ONE");
	}

	@Test
	public void successfulFragmentSelectionUsesTheCurrentCandidateLocalesGrammar() {
		Locale english = Locale.ENGLISH;
		Locale russian = Locale.forLanguageTag("ru");
		LocalizedString russianString = new LocalizedString.Builder("Successful locale-sensitive fragment")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("Russian other", List.of(
						new LocalizedString.ExpressionAlternative("count == CARDINALITY_ONE", "Russian one")))))
				.build();
		LocalizedString englishString = new LocalizedString.Builder("Successful locale-sensitive fragment")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("English other", List.of(
						new LocalizedString.ExpressionAlternative("count == CARDINALITY_ONE", "English one")))))
				.build();
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						russian, Set.of(russianString),
						english, Set.of(englishString)))
				.localeSupplier(matcher -> russian)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		TranslationResult result = strings.getResult("Successful locale-sensitive fragment",
				Map.of("count", Long.valueOf(21)));

		assertEquals("Russian one", result.getTranslation());
		assertEquals(russian, result.getResolvedLocale().orElseThrow(AssertionError::new));
		assertEquals(List.of(russian), result.getAttemptedLocales());
	}

	@Test
	public void generatedScopeIsFreshForEveryFallbackCandidate() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		LocalizedString frenchString = new LocalizedString.Builder("Fresh fallback scope")
				.translation("{{label}} {{missing}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("français")))
				.build();
		LocalizedString englishString = new LocalizedString.Builder("Fresh fallback scope")
				.translation("{{label}}")
				.build();
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						french, Set.of(frenchString),
						english, Set.of(englishString)))
				.localeSupplier(matcher -> french)
				.translationFallbackPolicy(TranslationFallbackPolicy.fallbackOnAnyFailure())
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		assertEquals("caller", strings.get("Fresh fallback scope", Map.of("label", "caller")));
	}

	@Test
	public void inheritedDefinitionFailuresReportDeclarationProvenance() {
		LocalizedString expressionChild = new LocalizedString.Builder("branch == 1")
				.translation("{{label}}")
				.build();
		LocalizedString expressionRoot = new LocalizedString.Builder("Expression provenance")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("default", List.of(
						new LocalizedString.ExpressionAlternative("missing == 1", "selected")))))
				.alternatives(List.of(expressionChild))
				.build();
		ExpressionEvaluationException expressionException = assertThrows(ExpressionEvaluationException.class,
				() -> buildFailFastStrings(expressionRoot).get("Expression provenance", Map.of("branch", 1)));
		assertTrue(expressionException.getMessage().contains("definition declared at Expression provenance"));
		assertTrue(!expressionException.getMessage().contains("definition declared at Expression provenance ->"));

		LocalizedString formChild = new LocalizedString.Builder("branch == 1")
				.translation("{{label}}")
				.build();
		LocalizedString formRoot = new LocalizedString.Builder("Form provenance")
				.placeholderDefinitions(Map.of("label", new LocalizedString.LanguageFormTranslation("missing", Map.of(
						Cardinality.ONE, "one",
						Cardinality.OTHER, "other"))))
				.alternatives(List.of(formChild))
				.build();
		IllegalArgumentException formException = assertThrows(IllegalArgumentException.class,
				() -> buildFailFastStrings(formRoot).get("Form provenance", Map.of("branch", 1)));
		assertTrue(formException.getMessage().contains("definition declared at Form provenance"));
		assertTrue(!formException.getMessage().contains("definition declared at Form provenance ->"));
	}

	@Test
	public void selectedChildDeclaredExpressionFailuresReportChildDeclarationProvenance() {
		LocalizedString child = new LocalizedString.Builder("count >= 1")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of("label", new LocalizedString.ExpressionTranslation("default", List.of(
						new LocalizedString.ExpressionAlternative("missing == 1", "selected")))))
				.build();
		LocalizedString root = new LocalizedString.Builder("Child provenance")
				.alternatives(List.of(child))
				.build();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> buildFailFastStrings(root).get("Child provenance", Map.of("count", Long.valueOf(1))));

		assertTrue(exception.getMessage().contains(
				"definition declared at Child provenance -> alternative[count >= 1]"));
	}

	@Test
	public void bidiIsolationWrapsCallerValuesButNotTranslationOwnedExpressionFragments() {
		Locale arabic = Locale.forLanguageTag("ar");
		LocalizedString localizedString = new LocalizedString.Builder("Expression bidi")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment",
						new LocalizedString.ExpressionTranslation("ثابت {{code}}")))
				.build();
		Strings strings = Strings.withFallbackLocale(arabic)
				.localizedStringSupplier(() -> Map.of(arabic, Set.of(localizedString)))
				.localeSupplier(matcher -> arabic)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();

		assertEquals("ثابت \u2068ACME-42\u2069", strings.get("Expression bidi", Map.of("code", "ACME-42")));
	}

	@Test
	public void generatedPlaceholderFragmentsCanReferenceExternalAndGeneratedPlaceholders() {
		LocalizedString localizedString = new LocalizedString.Builder("Books")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of(
						"label", new LocalizedString.LanguageFormTranslation("quantity", Map.of(
								Cardinality.ONE, "{{count}} {{noun}}",
								Cardinality.OTHER, "{{count}} {{noun}}"
						)),
						"noun", new LocalizedString.LanguageFormTranslation("quantity", Map.of(
								Cardinality.ONE, "book",
								Cardinality.OTHER, "books"
						))
				))
				.build();

		assertEquals("1 book", buildFailFastStrings(localizedString).get("Books", Map.of("quantity", 1, "count", 1)));
		assertEquals("{{Ada}} books", buildFailFastStrings(localizedString).get("Books", Map.of(
				"quantity", 2,
				"count", "{{Ada}}"
		)),
				"Caller values must remain opaque even when inserted into a generated fragment");
	}

	@Test
	public void generatedPlaceholderCyclesFailClearly() {
		LocalizedString localizedString = new LocalizedString.Builder("Cycle")
				.translation("{{a}}")
				.placeholderDefinitions(Map.of(
						"a", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "{{b}}",
								Cardinality.OTHER, "{{b}}"
						)),
						"b", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "{{a}}",
								Cardinality.OTHER, "{{a}}"
						))
				))
				.build();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> buildFailFastStrings(localizedString).get("Cycle", Map.of("count", 1)));

		assertTrue(exception.getMessage().contains("a -> b -> a"));
	}

	@Test
	public void generatedPlaceholderExpansionIsBounded() {
		Map<String, LocalizedString.LanguageFormTranslation> generatedPlaceholders = new HashMap<>();

		for (int index = 0; index < 21; ++index) {
			String nextPlaceholder = "p" + (index + 1);
			String fragment = "{{" + nextPlaceholder + "}}{{" + nextPlaceholder + "}}";
			generatedPlaceholders.put("p" + index, new LocalizedString.LanguageFormTranslation("count", Map.of(
					Cardinality.ONE, fragment,
					Cardinality.OTHER, fragment
			)));
		}

		generatedPlaceholders.put("p21", new LocalizedString.LanguageFormTranslation("count", Map.of(
				Cardinality.ONE, "x",
				Cardinality.OTHER, "x"
		)));
		LocalizedString localizedString = new LocalizedString.Builder("Expansion")
				.translation("{{p0}}")
				.placeholderDefinitions(generatedPlaceholders)
				.build();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> buildFailFastStrings(localizedString).get("Expansion", Map.of("count", 1)));

		assertTrue(exception.getMessage().contains("maximum of " +
				TranslationRuntimeLimits.DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS + " characters"));
	}

	@Test
	public void programmaticGeneratedFragmentsValidatePlaceholderSyntaxAtBuildTime() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("{{label}}")
				.placeholderDefinitions(Map.of(
						"label", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "bad}}",
								Cardinality.OTHER, "good"
						))
				))
				.build();

		assertThrows(IllegalArgumentException.class, () -> buildStrings(localizedString));
	}

	@Test
	public void bidiIsolationOnlyStringifiesReferencedCallerValues() {
		Locale arabic = Locale.forLanguageTag("ar");
		LocalizedString localizedString = new LocalizedString.Builder("Constant")
				.translation("ثابت")
				.build();
		Strings strings = Strings.withFallbackLocale(arabic)
				.localizedStringSupplier(() -> Map.of(arabic, Set.of(localizedString)))
				.localeSupplier((matcher) -> arabic)
				.build();
		Map<String, Object> context = new HashMap<>();
		context.put("name", "Ada");
		context.put("unused", new ThrowingToString());

		assertEquals("ثابت", strings.get("Constant", context));
		assertEquals("مفقود \u2068Ada\u2069", strings.get("مفقود {{name}}", context));
	}

	@Test
	public void bidiIsolationRejectsOversizedCallerValuesBeforeScanningOrMaterializing() {
		Locale arabic = Locale.forLanguageTag("ar");
		LocalizedString localizedString = new LocalizedString.Builder("Bounded")
				.translation("{{value}}")
				.build();
		Strings strings = Strings.withFallbackLocale(arabic)
				.localizedStringSupplier(() -> Map.of(arabic, Set.of(localizedString)))
				.localeSupplier((matcher) -> arabic)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.runtimeLimits(TranslationRuntimeLimits.builder()
						.maximumInterpolatedOutputCharacters(4)
						.build())
				.build();
		CharSequence oversized = new UnmaterializableCharSequence(1_000_000);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> strings.get("Bounded", Map.of("value", oversized)));
		assertTrue(exception.getMessage().contains("maximum of 4 characters"));
	}

	@Test
	public void failureKeyReturnsRawKeyWithoutScanningOversizedBidiValues() {
		Locale arabic = Locale.forLanguageTag("ar");
		Strings strings = Strings.withFallbackLocale(arabic)
				.localizedStringSupplier(() -> Map.of(arabic, Collections.emptySet()))
				.localeSupplier((matcher) -> arabic)
				.runtimeLimits(TranslationRuntimeLimits.builder()
						.maximumInterpolatedOutputCharacters(4)
						.build())
				.build();
		CharSequence oversized = new UnmaterializableCharSequence(1_000_000);

		assertEquals("{{value}}", strings.get("{{value}}", Map.of("value", oversized)));
	}

	@Test
	public void inspectionApiReportsSupportedLocalesKeysAndMissingKeys() {
		Locale en = Locale.forLanguageTag("en");
		Locale enGb = Locale.forLanguageTag("en-GB");
		LocalizedString shared = new LocalizedString.Builder("Shared").translation("Shared").build();
		LocalizedString fallbackOnly = new LocalizedString.Builder("Fallback only").translation("Fallback only").build();
		LocalizedString britishShared = new LocalizedString.Builder("Shared").translation("Shared").build();

		Strings strings = Strings.withFallbackLocale(en)
				.localizedStringSupplier(() -> Map.of(
						en, Set.of(shared, fallbackOnly),
						enGb, Set.of(britishShared)
				))
				.localeSupplier((matcher) -> en)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(en, enGb)
				))
				.build();

		assertEquals(Set.of(en, enGb), strings.getSupportedLocales());
		assertEquals(Set.of("Shared", "Fallback only"), strings.getKeysForLocale(en));
		assertEquals(Set.of("Shared"), strings.getKeysForLocale(enGb));
		assertThrows(IllegalArgumentException.class,
				() -> strings.getKeysForLocale(Locale.forLanguageTag("fr")),
				"Expected unsupported locales to fail");

		assertEquals(Collections.emptySet(), strings.getMissingKeys(en, en));
		assertEquals(Set.of("Fallback only"), strings.getMissingKeys(en, enGb));
		assertEquals(Collections.emptySet(), strings.getMissingKeys(enGb, en));
		assertThrows(IllegalArgumentException.class,
				() -> strings.getMissingKeys(Locale.forLanguageTag("fr"), en),
				"Expected unsupported source locales to fail");
		assertThrows(IllegalArgumentException.class,
				() -> strings.getMissingKeys(en, Locale.forLanguageTag("fr")),
				"Expected unsupported target locales to fail");
	}

	@Test
	public void inspectionApiReturnsImmutableCollections() {
		Locale en = Locale.forLanguageTag("en");
		LocalizedString localizedString = new LocalizedString.Builder("Shared").translation("Shared").build();

		Strings strings = Strings.withFallbackLocale(en)
				.localizedStringSupplier(() -> Map.of(en, Set.of(localizedString)))
				.localeSupplier((matcher) -> en)
				.build();

		assertThrows(UnsupportedOperationException.class,
				() -> strings.getSupportedLocales().add(Locale.forLanguageTag("fr")));
		assertThrows(UnsupportedOperationException.class,
				() -> strings.getKeysForLocale(en).add("Other"));
		assertThrows(UnsupportedOperationException.class,
				() -> strings.getMissingKeys(en, en).add("Other"));
	}

	private String searchResult(Strings strings, Integer resultCount, String formattedResultCount,
									Integer resultLimit, String formattedResultLimit, Integer elapsedMilliseconds,
									String formattedDuration) {
		return strings.get("Search completed.", Map.of(
				"resultCount", resultCount,
				"formattedResultCount", formattedResultCount,
				"resultLimit", resultLimit,
				"formattedResultLimit", formattedResultLimit,
				"elapsedMilliseconds", elapsedMilliseconds,
				"formattedDuration", formattedDuration
		));
	}

	private Strings buildStrings(LocalizedString localizedString) {
		return Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();
	}

	private Strings buildFailFastStrings(LocalizedString localizedString) {
		return Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.build();
	}

	private static final class ThrowingToString {
		@Override
		public String toString() {
			throw new UnsupportedOperationException("toString failed");
		}
	}

	private static final class UnmaterializableCharSequence implements CharSequence {
		private final int length;

		private UnmaterializableCharSequence(int length) {
			this.length = length;
		}

		@Override
		public int length() {
			return length;
		}

		@Override
		public char charAt(int index) {
			throw new AssertionError("Oversized input must be rejected before scanning");
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			throw new AssertionError("Oversized input must be rejected before slicing");
		}

		@Override
		public String toString() {
			throw new AssertionError("Oversized input must be rejected before materialization");
		}
	}
}
