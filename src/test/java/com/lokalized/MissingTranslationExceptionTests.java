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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
}
