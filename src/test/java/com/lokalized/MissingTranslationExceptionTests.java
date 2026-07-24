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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link MissingTranslationException}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class MissingTranslationExceptionTests {
	@Test
	@SuppressWarnings("deprecation")
	public void constructorsRejectMalformedLocales() {
		Locale malformed = new Locale("e");

		assertThrows(IllegalArgumentException.class,
				() -> new MissingTranslationException("message", "key", Map.of(), malformed));
		assertThrows(IllegalArgumentException.class,
				() -> new MissingTranslationException("message", "key", Map.of(), Locale.ENGLISH,
						TranslationFailureReason.MISSING_TRANSLATION, List.of(malformed)));
		assertThrows(IllegalArgumentException.class,
				() -> new MissingTranslationException("message", "key", Map.of(), malformed, null,
						TranslationFailureReason.MISSING_TRANSLATION, List.of(Locale.ENGLISH)));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void attemptedLocalesRejectDuplicateLanguageTags() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new MissingTranslationException("message", "key", Map.of(), Locale.ENGLISH, null,
						TranslationFailureReason.MISSING_TRANSLATION, List.of(Locale.ROOT, new Locale("und"))));

		assertTrue(exception.getMessage().contains("duplicate language tag 'und'"));
	}

	@Test
	public void constructorsRejectNullPlaceholderKeys() {
		Map<String, Object> placeholders = new HashMap<>();
		placeholders.put(null, "value");

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new MissingTranslationException("message", "key", placeholders, Locale.ENGLISH));

		assertTrue(exception.getMessage().contains("Placeholder names must not be null"));
	}

	@Test
	public void serializationRoundTripPreservesAbsentMatchDiagnostics() throws IOException, ClassNotFoundException {
		MissingTranslationException exception = new MissingTranslationException("No translation for 'welcome'",
				"welcome", Map.of(), Locale.GERMAN);
		exception.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("ExampleService", "translate", "ExampleService.java", 42)
		});

		MissingTranslationException copy = roundTrip(exception);

		assertExceptionStateEquals(exception, copy);
		assertFalse(copy.getLocaleMatchResult().isPresent());
	}

	@Test
	public void serializationRoundTripPreservesNoMatchDiagnostics() throws IOException, ClassNotFoundException {
		List<LanguageRange> requestedLanguageRanges = LanguageRange.parse("de;q=0.9,fr;q=0.7");
		Locale fallbackLocale = Locale.ENGLISH;
		LocaleMatchResult localeMatchResult = new LocaleMatchResult(requestedLanguageRanges, null, null, null,
				LocaleMatchType.NONE, fallbackLocale, List.of(fallbackLocale));
		MissingTranslationException exception = new MissingTranslationException("No translation for 'welcome'",
				"welcome", Map.of(), Locale.GERMAN, localeMatchResult, TranslationFailureReason.MISSING_TRANSLATION,
				List.of(Locale.GERMAN, fallbackLocale));
		exception.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("ExampleService", "translate", "ExampleService.java", 42)
		});

		MissingTranslationException copy = roundTrip(exception);

		assertExceptionStateEquals(exception, copy);
		assertEquals(localeMatchResult, copy.getLocaleMatchResult().orElseThrow(AssertionError::new));
	}

	@Test
	public void serializationRoundTripPreservesCompleteMatchDiagnostics() throws IOException, ClassNotFoundException {
		List<LanguageRange> requestedLanguageRanges =
				List.of(new LanguageRange("fr-ca", 0.875), new LanguageRange("*", 0.25));
		Locale selectedLocale = Locale.CANADA_FRENCH;
		Locale fallbackLocale = Locale.ENGLISH;
		LocaleMatchResult localeMatchResult = new LocaleMatchResult(requestedLanguageRanges, selectedLocale,
				requestedLanguageRanges.get(0), 0.875, LocaleMatchType.EXACT, fallbackLocale,
				List.of(selectedLocale, fallbackLocale));
		Map<String, Object> placeholders = new HashMap<>();
		placeholders.put("count", 3);
		placeholders.put("name", "Ada");
		placeholders.put("optional", null);
		MissingTranslationException exception = new MissingTranslationException("No matching alternative for 'items'",
				"items", placeholders, selectedLocale, localeMatchResult,
				TranslationFailureReason.NO_MATCHING_ALTERNATIVE, List.of(selectedLocale, fallbackLocale));
		exception.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("ExampleController", "items", "ExampleController.java", 81)
		});

		MissingTranslationException copy = roundTrip(exception);

		assertExceptionStateEquals(exception, copy);
		LocaleMatchResult localeMatchResultCopy =
				copy.getLocaleMatchResult().orElseThrow(AssertionError::new);
		assertEquals(localeMatchResult, localeMatchResultCopy);
		assertEquals(requestedLanguageRanges, localeMatchResultCopy.getRequestedLanguageRanges());
		assertEquals(requestedLanguageRanges.get(0),
				localeMatchResultCopy.getLanguageRange().orElseThrow(AssertionError::new));
	}

	@Test
	public void serializationRejectsNonserializablePlaceholderValues() {
		MissingTranslationException exception = new MissingTranslationException("message", "key",
				Map.of("value", new Object()), Locale.ENGLISH);

		assertThrows(NotSerializableException.class, () -> serialize(exception));
	}

	private void assertExceptionStateEquals(MissingTranslationException expected,
																					MissingTranslationException actual) {
		assertEquals(expected.getMessage(), actual.getMessage());
		assertEquals(expected.getKey(), actual.getKey());
		assertEquals(expected.getPlaceholders(), actual.getPlaceholders());
		assertEquals(expected.getLookupLocale(), actual.getLookupLocale());
		assertEquals(expected.getLocaleMatchResult(), actual.getLocaleMatchResult());
		assertEquals(expected.getReason(), actual.getReason());
		assertEquals(expected.getAttemptedLocales(), actual.getAttemptedLocales());
		assertArrayEquals(expected.getStackTrace(), actual.getStackTrace());
	}

	private MissingTranslationException roundTrip(MissingTranslationException exception)
			throws IOException, ClassNotFoundException {
		byte[] serializedException = serialize(exception);

		try (ObjectInputStream inputStream =
						 new ObjectInputStream(new ByteArrayInputStream(serializedException))) {
			return (MissingTranslationException) inputStream.readObject();
		}
	}

	private byte[] serialize(MissingTranslationException exception) throws IOException {
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();

		try (ObjectOutputStream outputStream = new ObjectOutputStream(byteOutputStream)) {
			outputStream.writeObject(exception);
		}

		return byteOutputStream.toByteArray();
	}
}
