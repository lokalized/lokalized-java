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

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Decides whether a failed locale attempt should continue to the next locale candidate.
 * <p>
 * This policy is intentionally separate from {@link TranslationFailureHandler}: fallback policy controls which
 * catalogs are attempted, while the failure handler controls the final response after fallback stops or all candidates
 * are exhausted.
 * <p>
 * Implementations may be invoked concurrently and must be thread-safe when shared by a {@link Strings} instance.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
@FunctionalInterface
public interface TranslationFallbackPolicy {
	/**
	 * Determines whether resolution should continue with the next locale candidate.
	 *
	 * @param reason          reason the current locale attempt failed, not null
	 * @param attemptedLocale locale whose catalog was attempted, not null
	 * @param cause           runtime cause for {@link TranslationFailureReason#RESOLUTION_FAILURE}, otherwise null
	 * @return whether to try the next candidate, not null
	 */
	@NonNull
	Boolean shouldTryNextLocale(@NonNull TranslationFailureReason reason, @NonNull Locale attemptedLocale,
															@Nullable Throwable cause);

	/**
	 * Falls back for missing translations and unmatched alternatives, but surfaces corrupt translation resolution.
	 *
	 * @return the recommended safe default policy, not null
	 */
	@NonNull
	static TranslationFallbackPolicy fallbackOnMissingTranslationOrNoMatchingAlternative() {
		return DefaultTranslationFallbackPolicy.MISSING_OR_NO_MATCH;
	}

	/**
	 * Falls back after every failed locale attempt, including runtime resolution failures.
	 * <p>
	 * This preserves Lokalized 2.x behavior.
	 *
	 * @return the permissive fallback policy, not null
	 */
	@NonNull
	static TranslationFallbackPolicy fallbackOnAnyFailure() {
		return DefaultTranslationFallbackPolicy.ANY_FAILURE;
	}

	/**
	 * Never falls back after a locale attempt fails.
	 *
	 * @return the fail-fast fallback policy, not null
	 */
	@NonNull
	static TranslationFallbackPolicy neverFallback() {
		return DefaultTranslationFallbackPolicy.NEVER;
	}
}

enum DefaultTranslationFallbackPolicy implements TranslationFallbackPolicy {
	MISSING_OR_NO_MATCH {
		@Override
		public @NonNull Boolean shouldTryNextLocale(@NonNull TranslationFailureReason reason,
				@NonNull Locale attemptedLocale, @Nullable Throwable cause) {
			requireNonNull(reason);
			requireNonNull(attemptedLocale);
			return reason != TranslationFailureReason.RESOLUTION_FAILURE;
		}
	},
	ANY_FAILURE {
		@Override
		public @NonNull Boolean shouldTryNextLocale(@NonNull TranslationFailureReason reason,
				@NonNull Locale attemptedLocale, @Nullable Throwable cause) {
			requireNonNull(reason);
			requireNonNull(attemptedLocale);
			return true;
		}
	},
	NEVER {
		@Override
		public @NonNull Boolean shouldTryNextLocale(@NonNull TranslationFailureReason reason,
				@NonNull Locale attemptedLocale, @Nullable Throwable cause) {
			requireNonNull(reason);
			requireNonNull(attemptedLocale);
			return false;
		}
	}
}
