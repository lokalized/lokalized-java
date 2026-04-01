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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link Strings}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class StringsTests {
	@BeforeAll
	public static void configureLogging() {
		LoggingUtils.setRootLoggerLevel(Level.WARNING);
	}

	@Test
	public void configurationVerificationTest() {
		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("fake"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.build();
		}, "Should not be able to construct a DefaultStrings instance with a fallback locale that doesn't have a corresponding strings file");

		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("en"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.build();
		}, "Should not be able to construct a DefaultStrings instance with missing tiebreaker information");

		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("en"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.tiebreakerLocalesByLanguageCode(Map.of(
							"en", List.of(Locale.forLanguageTag("en"))
					))
					.build();
		}, "Should not be able to construct a DefaultStrings instance with incomplete tiebreaker information");

		assertThrows(IllegalArgumentException.class, () -> {
			Strings.withFallbackLocale(Locale.forLanguageTag("en"))
					.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
					.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
					.tiebreakerLocalesByLanguageCode(Map.of(
							"en", List.of(Locale.forLanguageTag("ja-JA"))
					))
					.build();
		}, "Should not be able to construct a DefaultStrings instance with invalid tiebreaker information");

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
	public void tiebreakerOrderIsRespected() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en-GB"), Locale.forLanguageTag("en"))
				))
				.build();

		Locale bestMatch = strings.bestMatchFor(Locale.forLanguageTag("en-US"));

		assertEquals(Locale.forLanguageTag("en-GB"), bestMatch);
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

		assertEquals("I прочитал 3 книг", translation);
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
				.languageFormTranslationsByPlaceholder(Map.of(
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
	public void selectorDrivenPlaceholderTest() {
		LocalizedString localizedString = new LocalizedString.Builder("{{article}} {{noun}}")
				.translation("{{article}} {{noun}}")
				.languageFormTranslationsByPlaceholder(Map.of(
						"article", new LocalizedString.LanguageFormTranslation(
								List.of(
										new LocalizedString.LanguageFormSelector("grammaticalCase", LanguageFormType.CASE),
										new LocalizedString.LanguageFormSelector("gender", LanguageFormType.GENDER)
								),
								List.of(
										new LocalizedString.LanguageFormTranslationRule(Map.of(
												LanguageFormType.CASE, GrammaticalCase.NOMINATIVE,
												LanguageFormType.GENDER, Gender.MASCULINE
										), "der"),
										new LocalizedString.LanguageFormTranslationRule(Map.of(
												LanguageFormType.CASE, GrammaticalCase.ACCUSATIVE,
												LanguageFormType.GENDER, Gender.MASCULINE
										), "den"),
										new LocalizedString.LanguageFormTranslationRule(Map.of(
												LanguageFormType.GENDER, Gender.FEMININE
										), "die"),
										new LocalizedString.LanguageFormTranslationRule("das")
								)
						)
				))
				.build();

		Strings strings = buildStrings(localizedString);

		String translation = strings.get("{{article}} {{noun}}", Map.of(
				"grammaticalCase", GrammaticalCase.NOMINATIVE,
				"gender", Gender.MASCULINE,
				"noun", "Baum"
		));

		assertEquals("der Baum", translation);

		translation = strings.get("{{article}} {{noun}}", Map.of(
				"grammaticalCase", GrammaticalCase.ACCUSATIVE,
				"gender", Gender.MASCULINE,
				"noun", "Baum"
		));

		assertEquals("den Baum", translation);

		translation = strings.get("{{article}} {{noun}}", Map.of(
				"grammaticalCase", GrammaticalCase.NOMINATIVE,
				"gender", Gender.FEMININE,
				"noun", "Frau"
		));

		assertEquals("die Frau", translation);

		translation = strings.get("{{article}} {{noun}}", Map.of(
				"grammaticalCase", GrammaticalCase.NOMINATIVE,
				"gender", Gender.NEUTER,
				"noun", "Haus"
		));

		assertEquals("das Haus", translation);
	}

	@Test
	public void missingSelectorDrivenPlaceholderTranslationsThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("{{article}} {{noun}}")
				.translation("{{article}} {{noun}}")
				.languageFormTranslationsByPlaceholder(Map.of(
						"article", new LocalizedString.LanguageFormTranslation(
								List.of(
										new LocalizedString.LanguageFormSelector("grammaticalCase", LanguageFormType.CASE),
										new LocalizedString.LanguageFormSelector("gender", LanguageFormType.GENDER)
								),
								List.of(
										new LocalizedString.LanguageFormTranslationRule(Map.of(
												LanguageFormType.CASE, GrammaticalCase.NOMINATIVE,
												LanguageFormType.GENDER, Gender.MASCULINE
										), "der"),
										new LocalizedString.LanguageFormTranslationRule(Map.of(
												LanguageFormType.CASE, GrammaticalCase.ACCUSATIVE,
												LanguageFormType.GENDER, Gender.MASCULINE
										), "den")
								)
						)
				))
				.build();

		Strings strings = buildStrings(localizedString);

		assertThrows(IllegalStateException.class,
				() -> strings.get("{{article}} {{noun}}", Map.of(
						"grammaticalCase", GrammaticalCase.NOMINATIVE,
						"gender", Gender.NEUTER,
						"noun", "Haus"
				)),
				"Expected unmatched selector-driven placeholders without a default rule to throw");
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
		// He was the best baseball player.
		// She was the best baseball player.

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
		// Él era el mejor jugador de béisbol.
		// Ella era la mejor jugadora de béisbol.

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
	}

	@Test
	public void missingPlaceholders() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier((matcher) -> matcher.bestMatchFor(Locale.forLanguageTag("en-US")))
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("en-GB"))
				))
				.build();

		assertThrows(ExpressionEvaluationException.class,
				() -> strings.get("I read {{bookCount}} books"),
				"Expected missing placeholders in expressions to throw");
	}

	@Test
	public void missingCardinalityPlaceholderValuesThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("You have {{count}} {{items}}")
				.translation("You have {{count}} {{items}}")
				.languageFormTranslationsByPlaceholder(Map.of(
						"items", new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item",
								Cardinality.OTHER, "items"
						))
				))
				.build();

		Strings strings = buildStrings(localizedString);

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("You have {{count}} {{items}}"),
				"Expected missing cardinality placeholders to throw");
	}

	@Test
	public void invalidOrdinalityPlaceholderValuesThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("It is your {{year}}{{suffix}} birthday")
				.translation("It is your {{year}}{{suffix}} birthday")
				.languageFormTranslationsByPlaceholder(Map.of(
						"suffix", new LocalizedString.LanguageFormTranslation("year", Map.of(
								Ordinality.ONE, "st",
								Ordinality.OTHER, "th"
						))
				))
				.build();

		Strings strings = buildStrings(localizedString);

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("It is your {{year}}{{suffix}} birthday", Map.of("year", "one")),
				"Expected invalid ordinality placeholders to throw");
	}

	@Test
	public void invalidGenderPlaceholderValuesThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("{{title}} Doe")
				.translation("{{title}} Doe")
				.languageFormTranslationsByPlaceholder(Map.of(
						"title", new LocalizedString.LanguageFormTranslation("gender", Map.of(
								Gender.MASCULINE, "Mr",
								Gender.FEMININE, "Ms"
						))
				))
				.build();

		Strings strings = buildStrings(localizedString);

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("{{title}} Doe", Map.of("gender", "MASCULINE")),
				"Expected invalid gender placeholders to throw");
	}

	@Test
	public void nonCardinalityRangePlaceholdersThrow() {
		LocalizedString localizedString = new LocalizedString.Builder("Range example")
				.translation("{{form}}")
				.languageFormTranslationsByPlaceholder(Map.of(
						"form", new LocalizedString.LanguageFormTranslation(
								new LocalizedString.LanguageFormTranslationRange("start", "end"),
								Map.of(
										Formality.INFORMAL, "Hi",
										Formality.FORMAL, "Hello"
								)
						)
				))
				.build();

		Strings strings = buildStrings(localizedString);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> strings.get("Range example", Map.of("start", 1, "end", 2)),
				"Expected non-cardinality range placeholders to throw");

		assertTrue(exception.getMessage().contains("Range-based translations are only supported"),
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
				.languageFormTranslationsByPlaceholder(Map.of(
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
				.build();

		assertThrows(IllegalStateException.class,
				() -> strings.get("You have {{count}} {{itemLabel}}", Map.of("count", 2)),
				"Expected missing placeholder translations to throw");
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

	private Strings buildStrings(LocalizedString localizedString) {
		return Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> Map.of(
						Locale.forLanguageTag("en"), Set.of(localizedString)
				))
				.localeSupplier((matcher) -> Locale.forLanguageTag("en"))
				.build();
	}
}
