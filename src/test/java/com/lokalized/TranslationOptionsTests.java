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
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link TranslationOptions}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class TranslationOptionsTests {
	@Test
	public void valueSemantics() {
		TranslationFailureHandler translationFailureHandler = TranslationFailureHandler.throwException();
		TranslationOptions options = TranslationOptions.builder()
				.locale(Locale.forLanguageTag("en-US"))
				.bidiIsolation(BidiIsolation.NONE)
				.translationFailureHandler(translationFailureHandler)
				.build();
		TranslationOptions sameOptions = TranslationOptions.builder()
				.locale(Locale.forLanguageTag("en-US"))
				.bidiIsolation(BidiIsolation.NONE)
				.translationFailureHandler(translationFailureHandler)
				.build();
		TranslationOptions differentOptions = TranslationOptions.forLocale(Locale.forLanguageTag("en-GB"));

		assertEquals(options, sameOptions);
		assertEquals(options.hashCode(), sameOptions.hashCode());
		assertFalse(options.equals(differentOptions));
		assertFalse(options.equals(null));
		assertTrue(options.toString().contains("locale=en-US"));
		assertTrue(options.toString().contains("bidiIsolation=NONE"));
		assertTrue(TranslationOptions.none().toString().contains("TranslationOptions"));
	}

	@Test
	public void languageRangesAreDefensivelyCopied() {
		List<LanguageRange> languageRanges = new ArrayList<>(LanguageRange.parse("en-GB,en;q=0.5"));
		TranslationOptions options = TranslationOptions.forLanguageRanges(languageRanges);
		languageRanges.clear();

		assertEquals(2, options.getLanguageRanges().orElseThrow(AssertionError::new).size());
		assertThrows(UnsupportedOperationException.class,
				() -> options.getLanguageRanges().orElseThrow(AssertionError::new).add(new LanguageRange("fr")));
	}

	@Test
	public void localeAndLanguageRangeBuilderSettersUseLastNonNullSetter() {
		Locale locale = Locale.forLanguageTag("en-US");
		List<LanguageRange> languageRanges = LanguageRange.parse("en-GB,en;q=0.5");

		TranslationOptions languageRangeOptions = TranslationOptions.builder()
				.locale(locale)
				.languageRanges(languageRanges)
				.build();

		assertFalse(languageRangeOptions.getLocale().isPresent());
		assertEquals(languageRanges, languageRangeOptions.getLanguageRanges().orElseThrow(AssertionError::new));

		TranslationOptions localeOptions = TranslationOptions.builder()
				.languageRanges(languageRanges)
				.locale(locale)
				.build();

		assertEquals(locale, localeOptions.getLocale().orElseThrow(AssertionError::new));
		assertFalse(localeOptions.getLanguageRanges().isPresent());
	}
}
