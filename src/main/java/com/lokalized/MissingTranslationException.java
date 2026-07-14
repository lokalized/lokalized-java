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

import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when no translation is found and the configured {@link TranslationFailureHandler} chooses to throw.
 * <p>
 * This class is intended for use by a single thread.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class MissingTranslationException extends RuntimeException {
	@NonNull
	private final String key;
	@NonNull
	private final Locale lookupLocale;
	@NonNull
	private final Optional<@NonNull LocaleMatchResult> localeMatchResult;
	@NonNull
	private final Map<@NonNull String, @Nullable Object> placeholders;
	@NonNull
	private final TranslationFailureReason reason;
	@NonNull
	private final List<@NonNull Locale> attemptedLocales;

	/**
	 * Constructs a new exception with the unsupported locale.
	 *
	 * @param message failure message, not null
	 * @param key translation key, not null
	 * @param placeholders caller placeholders, not null
	 * @param lookupLocale the locale used to begin locale fallback, not null
	 */
	public MissingTranslationException(@NonNull String message,
																		 @NonNull String key,
																		 @NonNull Map<@NonNull String, @Nullable Object> placeholders,
																		 @NonNull Locale lookupLocale) {
		this(message, key, placeholders, lookupLocale, null, TranslationFailureReason.MISSING_TRANSLATION,
				Collections.singletonList(lookupLocale));
	}

	/**
	 * Constructs an exception with the complete failed-lookup outcome.
	 *
	 * @param message          failure message, not null
	 * @param key              translation key, not null
	 * @param placeholders     caller placeholders, not null
	 * @param lookupLocale     locale used to begin locale fallback, not null
	 * @param reason           final failure reason, not null
	 * @param attemptedLocales ordered locales attempted, not null
	 * @throws IllegalArgumentException if the reason is {@code RESOLUTION_FAILURE} or attempted locales contain
	 *                                  duplicates
	 */
	public MissingTranslationException(@NonNull String message,
																 @NonNull String key,
																	 @NonNull Map<@NonNull String, @Nullable Object> placeholders,
																	 @NonNull Locale lookupLocale,
																	 @NonNull TranslationFailureReason reason,
																	 @NonNull List<@NonNull Locale> attemptedLocales) {
		this(message, key, placeholders, lookupLocale, null, reason, attemptedLocales);
	}

	/**
	 * Constructs an exception with complete failed-lookup and locale-negotiation diagnostics.
	 *
	 * @param message          failure message, not null
	 * @param key              translation key, not null
	 * @param placeholders     caller placeholders, not null
	 * @param lookupLocale     locale used to begin locale fallback, not null
	 * @param localeMatchResult strict locale-negotiation diagnostics, or null when unavailable
	 * @param reason           final failure reason, not null
	 * @param attemptedLocales ordered locales attempted, not null
	 * @throws IllegalArgumentException if the reason is {@code RESOLUTION_FAILURE} or attempted locales contain
	 *                                  duplicates
	 */
	public MissingTranslationException(@NonNull String message,
																	 @NonNull String key,
																	 @NonNull Map<@NonNull String, @Nullable Object> placeholders,
																	 @NonNull Locale lookupLocale,
																	 @Nullable LocaleMatchResult localeMatchResult,
																	 @NonNull TranslationFailureReason reason,
																	 @NonNull List<@NonNull Locale> attemptedLocales) {
		super(requireNonNull(message));

		requireNonNull(key);
		requireNonNull(placeholders);
		requireNonNull(lookupLocale);
		requireNonNull(reason);
		requireNonNull(attemptedLocales);

		if (reason == TranslationFailureReason.RESOLUTION_FAILURE)
			throw new IllegalArgumentException("MissingTranslationException cannot represent a resolution failure cause");

		this.key = key;
		this.placeholders = Collections.unmodifiableMap(new HashMap<>(placeholders));
		this.lookupLocale = lookupLocale;
		this.localeMatchResult = Optional.ofNullable(localeMatchResult);
		this.reason = reason;
		List<@NonNull Locale> attemptedLocaleCopy = new ArrayList<>(attemptedLocales.size());

		for (Locale attemptedLocale : attemptedLocales)
			attemptedLocaleCopy.add(requireNonNull(attemptedLocale));

		if (new LinkedHashSet<>(attemptedLocaleCopy).size() != attemptedLocaleCopy.size())
			throw new IllegalArgumentException("Attempted locales must not contain duplicates");

		this.attemptedLocales = Collections.unmodifiableList(attemptedLocaleCopy);
	}

	/**
	 * The translation key that triggered this exception.
	 *
	 * @return the translation key, not null
	 */
	@NonNull
	public String getKey() {
		return this.key;
	}

	/**
	 * The placeholders specified for the failed translation attempt.
	 *
	 * @return the placeholders, not null
	 */
	@NonNull
	public Map<@NonNull String, @Nullable Object> getPlaceholders() {
		return this.placeholders;
	}

	/**
	 * The locale used to begin per-key locale fallback.
	 *
	 * @return the locale, not null
	 */
	@NonNull
	public Locale getLookupLocale() {
		return this.lookupLocale;
	}

	/** @return strict locale-negotiation diagnostics when available, otherwise empty, not null */
	@NonNull
	public Optional<@NonNull LocaleMatchResult> getLocaleMatchResult() {
		return localeMatchResult;
	}

	/** @return final failure reason, not null */
	@NonNull
	public TranslationFailureReason getReason() {
		return reason;
	}

	/** @return ordered locales attempted before failure, not null */
	@NonNull
	public List<@NonNull Locale> getAttemptedLocales() {
		return attemptedLocales;
	}
}
