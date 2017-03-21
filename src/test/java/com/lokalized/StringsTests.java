/*
 * Copyright 2017 Transmogrify LLC.
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
import java.util.HashMap;
import java.util.Locale;
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
		Strings strings = new DefaultStrings.Builder("en", () ->
				LocalizedStringLoader.loadFromClasspath("strings")
		).localeSupplier(() -> Locale.forLanguageTag("en-GB"))
				.build();

		String translation = strings.get("I am going on vacation");

		Assert.assertEquals("I am going on holiday", translation);
	}

	@Test
	public void pluralPlaceholderTest() {
		Strings strings = new DefaultStrings.Builder("en", () ->
				LocalizedStringLoader.loadFromClasspath("strings")
		).build();

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
					put("bookCount", 3);
				}}, Locale.forLanguageTag("ru"));

		Assert.assertEquals("I прочитал 3 книги", translation);
	}

	@Test
	public void genderPlaceholderTest() {
		Strings strings = new DefaultStrings.Builder("en", () ->
				LocalizedStringLoader.loadFromClasspath("strings")
		).build();

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
		Strings strings = new DefaultStrings.Builder("en", () ->
				LocalizedStringLoader.loadFromClasspath("strings")
		).build();

		String translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 0);
				}});

		Assert.assertEquals("I didn't read any books", translation);
	}

	@Test
	public void complexTest() {
		Strings strings = new DefaultStrings.Builder("en", () ->
				LocalizedStringLoader.loadFromClasspath("strings")
		).build();

		// English

		// He was one of the 10 best baseball players.
		// She was one of the 10 best baseball players.
		// He was the best baseball player.
		// SHe was the best baseball player.

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
}