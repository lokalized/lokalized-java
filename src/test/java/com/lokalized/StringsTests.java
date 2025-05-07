/*
 * Copyright 2017 Product Mog LLC.
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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.logging.Level;

/**
 * Exercises {@link Strings}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class StringsTests {
	@BeforeClass
	public static void configureLogging() {
		LoggingUtils.setRootLoggerLevel(Level.FINER);
	}

	@Test
	public void basicLanguageSpecificityTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.localeSupplier(() -> Locale.forLanguageTag("en-GB"))
				.build();

		String translation = strings.get("I am going on vacation");

		Assert.assertEquals("I am going on holiday", translation);
	}

	@Test
	public void cardinalityPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 3);
				}});

		Assert.assertEquals("I read 3 books", translation);

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 1);
				}});

		Assert.assertEquals("I read 1 book", translation);

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", new BigDecimal("1.0"));
				}});

		Assert.assertEquals("I read 1.0 books", translation);

		translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 3);
				}}, Locale.forLanguageTag("ru"));

		Assert.assertEquals("I прочитал 3 книг", translation);
	}

	@Test
	public void ordinalityPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.MASCULINE);
					put("year", 18);
				}});

		Assert.assertEquals("His 18th birthday party is next week.", translation);

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.FEMININE);
					put("year", 21);
				}});

		Assert.assertEquals("Her 21st birthday party is next week.", translation);

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.MASCULINE);
					put("year", 18);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Su fiesta de cumpleaños número 18 es la próxima semana.", translation);

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("year", 1);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Su primera fiesta de cumpleaños es la próxima semana.", translation);

		translation = strings.get("{{hisOrHer}} {{year}}th birthday party is next week.",
				new HashMap<String, Object>() {{
					put("hisOrHer", Gender.FEMININE);
					put("year", 15);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Su quinceañera es la próxima semana.", translation);
	}

	@Test
	public void genderPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
				}});

		Assert.assertEquals("He is a good actor.", translation);

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
				}});

		Assert.assertEquals("She is a good actress.", translation);

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Él es un buen actor.", translation);

		translation = strings.get("{{heOrShe}} is a good actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Ella es una buena actriz.", translation);

		translation = strings.get("{{heOrShe}} is a great actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Él es un gran actor.", translation);

		translation = strings.get("{{heOrShe}} is a great actor.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Ella es una gran actriz.", translation);
	}

	@Test
	public void alternativesTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 0);
				}});

		Assert.assertEquals("I didn't read any books", translation);
	}

	@Test
	public void complexTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
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

		Assert.assertEquals("He was one of the 10 best baseball players.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
					put("groupSize", 1);
				}});

		Assert.assertEquals("He was the best baseball player.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 10);
				}});

		Assert.assertEquals("She was one of the 10 best baseball players.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 1);
				}});

		Assert.assertEquals("She was the best baseball player.", translation);

		// Spanish

		// Fue uno de los 10 mejores jugadores de béisbol.
		// Fue una de las 10 mejores jugadoras de béisbol.
		// Él era el mejor jugador de béisbol.
		// Ella era la mejor jugadora de béisbol.

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
					put("groupSize", 10);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Fue uno de los 10 mejores jugadores de béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.MASCULINE);
					put("groupSize", 1);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Él era el mejor jugador de béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 10);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Fue una de las 10 mejores jugadoras de béisbol.", translation);

		translation = strings.get("{{heOrShe}} was one of the {{groupSize}} best baseball players.",
				new HashMap<String, Object>() {{
					put("heOrShe", Gender.FEMININE);
					put("groupSize", 1);
				}}, Locale.forLanguageTag("es"));

		Assert.assertEquals("Ella era la mejor jugadora de béisbol.", translation);
	}

	@Test
	public void missingPlaceholders() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String translation = strings.get("I read {{bookCount}} books");

		Assert.assertEquals("I read {{bookCount}} books", translation);
	}

	@Test
	public void languageRange() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.languageRangesSupplier(() -> LanguageRange.parse("en-US;q=1.0,en-GB;q=0.5,fr-FR;q=0.25"))
				.build();

		String translation = strings.get("I am going on vacation");

		Assert.assertEquals("I am going on vacation", translation);

		Strings enGbStrings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.languageRangesSupplier(() -> LanguageRange.parse("en-GB;q=1.0,en;q=0.75,en-US;q=0.5,fr-FR;q=0.25"))
				.build();

		String enGbTranslation = enGbStrings.get("I am going on vacation");

		Assert.assertEquals("I am going on holiday", enGbTranslation);

		Strings enUsStrings = Strings.withFallbackLocale(Locale.forLanguageTag("ru"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.languageRangesSupplier(() -> LanguageRange.parse("en-US;q=1.0,en-GB;q=0.5,fr-FR;q=0.25"))
				.build();

		String enUsTranslation = enUsStrings.get("I am going on vacation");

		Assert.assertEquals("I am going on vacation", enUsTranslation);

		Strings ruStrings = Strings.withFallbackLocale(Locale.forLanguageTag("ru"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.languageRangesSupplier(() -> LanguageRange.parse("fr;q=1.0,ru;q=0.25"))
				.build();

		String ruTranslation = ruStrings.get("I am going on vacation - MISSING KEY");

		Assert.assertEquals("I am going on vacation - MISSING KEY", ruTranslation);

		Strings ru2Strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.languageRangesSupplier(() -> LanguageRange.parse("fr;q=1.0,ru;q=0.25")).build();

		String ru2Translation = ru2Strings.get("Hello, world!");

		Assert.assertEquals("Приветствую, мир", ru2Translation);
	}

	@Test
	public void cardinalityRanges() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String enTranslation = strings.get("The meeting will be {{minHours}}-{{maxHours}} hours long.", new HashMap<String, Object>() {{
			put("minHours", 1.5);
			put("maxHours", 2);
		}});

		Assert.assertEquals("The meeting will be 1.5-2 hours long.", enTranslation);
	}

	@Test
	public void noTranslationKeyPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String translation = strings.get("There is no key for this");

		Assert.assertEquals("There is no key for this", translation);

		translation = strings.get("There is no key for {{this}}", new HashMap<String, Object>() {{
			put("this", "that");
		}});

		Assert.assertEquals("There is no key for that", translation);
	}

	@Test
	public void specialCharacterPlaceholderTest() {
		Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en"))
				.localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
				.build();

		String translation = strings.get("We were unable to charge {{amount}} to your credit card.", new HashMap<String, Object>() {{
			put("amount", "$24.99");
		}});

		Assert.assertEquals("We were unable to charge $24.99 to your credit card.", translation);
	}
}