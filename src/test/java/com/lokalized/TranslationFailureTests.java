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
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Exercises {@link TranslationFailure}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class TranslationFailureTests {
	private static final String PLACEHOLDER_SECRET = "SECRET-PLACEHOLDER-VALUE";
	private static final String CAUSE_SECRET = "SECRET-CAUSE-VALUE";

	@Test
	public void messageIsExactAndRedactedForEveryFailureReason() {
		for (TranslationFailureReason reason : TranslationFailureReason.values()) {
			Throwable cause = reason == TranslationFailureReason.RESOLUTION_FAILURE
					? new IllegalStateException(CAUSE_SECRET) : null;
			TranslationFailure failure = new TestTranslationFailure(reason, cause);
			String expectedMessage = String.format("Unable to resolve translation key 'Missing {{name}}' for locale "
					+ "'en-US'. Reason: %s. Attempted locales: [en-US, en]", reason);

			assertEquals(expectedMessage, failure.getMessage(), reason.name());
			assertFalse(failure.getMessage().contains(PLACEHOLDER_SECRET), reason.name());
			assertFalse(failure.getMessage().contains(CAUSE_SECRET), reason.name());
		}
	}

	@ThreadSafe
	private static final class TestTranslationFailure implements TranslationFailure {
		@NonNull
		private final TranslationFailureReason reason;
		@Nullable
		private final Throwable cause;

		private TestTranslationFailure(@NonNull TranslationFailureReason reason, @Nullable Throwable cause) {
			this.reason = reason;
			this.cause = cause;
		}

		@Override
		@NonNull
		public String getKey() {
			return "Missing {{name}}";
		}

		@Override
		@NonNull
		public Locale getLookupLocale() {
			return Locale.forLanguageTag("en-US");
		}

		@Override
		@NonNull
		public List<@NonNull Locale> getAttemptedLocales() {
			return Arrays.asList(Locale.forLanguageTag("en-US"), Locale.ENGLISH);
		}

		@Override
		@NonNull
		public Map<@NonNull String, @Nullable Object> getPlaceholders() {
			return Map.of(
					"name", PLACEHOLDER_SECRET,
					"unevaluated", new ThrowingToString()
			);
		}

		@Override
		@NonNull
		public TranslationFailureReason getReason() {
			return this.reason;
		}

		@Override
		@NonNull
		public Optional<@NonNull Throwable> getCause() {
			return Optional.ofNullable(this.cause);
		}
	}

	private static final class ThrowingToString {
		@Override
		public String toString() {
			throw new AssertionError("TranslationFailure.getMessage() must not inspect placeholder values");
		}
	}
}
