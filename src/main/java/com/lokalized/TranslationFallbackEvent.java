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

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Diagnostic event for a lookup whose later locale candidate supplied a translation.
 * <p>
 * The event records every preceding candidate's own failure reason and cause, in attempt order. It contains no
 * rendered translation or caller placeholder values. Collection state is unmodifiable; runtime causes are exposed
 * by reference and are outside this class's thread-safety guarantee.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.1.1
 */
@ThreadSafe
public final class TranslationFallbackEvent {
	@NonNull private final String key;
	@NonNull private final Locale lookupLocale;
	@Nullable private final LocaleMatchResult localeMatchResult;
	@NonNull private final List<@NonNull Locale> attemptedLocales;
	@NonNull private final Locale resolvedLocale;
	@NonNull private final List<@NonNull PrecedingFailure> precedingFailures;

	/**
	 * Constructs an event from a successful result and the failures preceding it. Custom {@link Strings}
	 * implementations can use this constructor to expose the same diagnostics.
	 * <p>
	 * The locale-match result and unmodifiable attempted-locales list are shared with {@code translationResult}. The supplied
	 * failure list is defensively copied. This event does not retain the rendered translation from {@code translationResult}.
	 *
	 * @param translationResult a translated result whose successful locale follows at least one failed candidate, not null
	 * @param precedingFailures failures for every preceding candidate, in attempt order, not null
	 * @throws IllegalArgumentException if the result is not translated, there are no preceding candidates, or the
	 *                                  failures do not correspond exactly to the preceding attempted locales
	 */
	public TranslationFallbackEvent(@NonNull TranslationResult translationResult,
			@NonNull List<@NonNull PrecedingFailure> precedingFailures) {
		requireNonNull(translationResult);
		requireNonNull(precedingFailures);

		if (translationResult.getStatus() != TranslationResultStatus.TRANSLATED)
			throw new IllegalArgumentException("A fallback event requires a translated result");

		List<@NonNull Locale> attemptedLocales = translationResult.getAttemptedLocales();

		if (precedingFailures.isEmpty() || precedingFailures.size() != attemptedLocales.size() - 1)
			throw new IllegalArgumentException("A fallback event requires one failure for each preceding locale candidate");

		Locale resolvedLocale = translationResult.getResolvedLocale().orElseThrow(IllegalArgumentException::new);

		if (!resolvedLocale.equals(attemptedLocales.get(attemptedLocales.size() - 1)))
			throw new IllegalArgumentException("The final attempted locale must supply the fallback translation");

		List<@NonNull PrecedingFailure> precedingFailuresCopy = new ArrayList<>(precedingFailures.size());

		for (int index = 0; index < precedingFailures.size(); ++index) {
			PrecedingFailure precedingFailure = requireNonNull(precedingFailures.get(index));

			if (!precedingFailure.getLocale().equals(attemptedLocales.get(index)))
				throw new IllegalArgumentException("Preceding failures must follow the attempted locale order");

			precedingFailuresCopy.add(precedingFailure);
		}

		this.key = translationResult.getKey();
		this.lookupLocale = translationResult.getLookupLocale();
		this.localeMatchResult = translationResult.getLocaleMatchResult().orElse(null);
		this.attemptedLocales = attemptedLocales;
		this.resolvedLocale = resolvedLocale;
		this.precedingFailures = Collections.unmodifiableList(precedingFailuresCopy);
	}

	/**
	 * Gets the translation key.
	 *
	 * @return translation key, not null
	 */
	@NonNull
	public String getKey() {
		return key;
	}

	/**
	 * Gets the locale used to begin per-key lookup.
	 *
	 * @return lookup locale, not null
	 */
	@NonNull
	public Locale getLookupLocale() {
		return lookupLocale;
	}

	/**
	 * Gets the locale-negotiation diagnostics when available.
	 *
	 * @return locale-match result when available, otherwise empty, not null
	 */
	@NonNull
	public Optional<@NonNull LocaleMatchResult> getLocaleMatchResult() {
		return Optional.ofNullable(localeMatchResult);
	}

	/**
	 * Gets all attempted locales through the successful candidate.
	 *
	 * @return attempted locales in attempt order, not null
	 */
	@NonNull
	public List<@NonNull Locale> getAttemptedLocales() {
		return attemptedLocales;
	}

	/**
	 * Gets the locale that supplied the translation.
	 *
	 * @return resolved locale, not null
	 */
	@NonNull
	public Locale getResolvedLocale() {
		return resolvedLocale;
	}

	/**
	 * Gets the failures preceding the successful candidate.
	 *
	 * @return preceding failures in attempt order, not null
	 */
	@NonNull
	public List<@NonNull PrecedingFailure> getPrecedingFailures() {
		return precedingFailures;
	}

	/** @return diagnostic representation omitting translation text and throwable messages, not null */
	@Override
	@NonNull
	public String toString() {
		return Diagnostics.format("%s{key='%s', lookupLocale=%s, resolvedLocale=%s, precedingFailures=%s}",
				getClass().getSimpleName(), key, lookupLocale.toLanguageTag(), resolvedLocale.toLanguageTag(), precedingFailures);
	}

	/**
	 * Failure of one locale candidate preceding a successful fallback translation.
	 * <p>
	 * The runtime cause is exposed by reference and is outside this class's thread-safety guarantee.
	 *
	 * @since 3.1.1
	 */
	@ThreadSafe
	public static final class PrecedingFailure {
		@NonNull private final Locale locale;
		@NonNull private final TranslationFailureReason reason;
		@Nullable private final Throwable cause;

		/**
		 * Constructs a preceding candidate failure.
		 *
		 * @param locale attempted locale, not null
		 * @param reason reason the candidate failed, not null
		 * @param cause runtime cause for a resolution failure, otherwise null
		 * @throws IllegalArgumentException if the locale is malformed, a resolution failure has no cause, or another
		 *                                  failure reason has a cause
		 */
		public PrecedingFailure(@NonNull Locale locale, @NonNull TranslationFailureReason reason, @Nullable Throwable cause) {
			this.locale = LocaleUtils.requireWellFormed(locale, "Attempted locale");
			this.reason = requireNonNull(reason);

			if ((reason == TranslationFailureReason.RESOLUTION_FAILURE) != (cause != null))
				throw new IllegalArgumentException("A preceding failure must carry a cause if and only if its reason is RESOLUTION_FAILURE");

			this.cause = cause;
		}

		/**
		 * Gets the attempted locale.
		 *
		 * @return attempted locale, not null
		 */
		@NonNull
		public Locale getLocale() {
			return locale;
		}

		/**
		 * Gets the reason this candidate failed.
		 *
		 * @return failure reason, not null
		 */
		@NonNull
		public TranslationFailureReason getReason() {
			return reason;
		}

		/**
		 * Gets this candidate's runtime cause when present.
		 *
		 * @return runtime cause when present, otherwise empty, not null
		 */
		@NonNull
		public Optional<@NonNull Throwable> getCause() {
			return Optional.ofNullable(cause);
		}

		/** @return diagnostic representation omitting throwable messages, not null */
		@Override
		@NonNull
		public String toString() {
			return Diagnostics.format("%s{locale=%s, reason=%s}", getClass().getSimpleName(), locale.toLanguageTag(), reason);
		}
	}
}
