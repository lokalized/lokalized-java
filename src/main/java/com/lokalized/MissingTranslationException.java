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

import static com.lokalized.Diagnostics.format;
import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when no translation is found and the configured {@link TranslationFailureHandler} chooses to throw.
 * <p>
 * This class is intended for use by a single thread.
 * <p>
 * Java serialization is supported when every non-null placeholder value and its reachable object graph implement
 * {@link java.io.Serializable}. Otherwise, serialization fails with {@link java.io.NotSerializableException}.
 * The supported serialized form begins with 3.0.0 and is not compatible with streams written by 2.x releases.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class MissingTranslationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** Translation key that could not be resolved. */
	@NonNull
	private final String key;
	/** Locale used to begin per-key locale fallback. */
	@NonNull
	private final Locale lookupLocale;
	/** Locale-negotiation diagnostics, when available. */
	@Nullable
	private final LocaleMatchResult localeMatchResult;
	/** Caller-supplied placeholders for the failed translation attempt. */
	@NonNull
	private final Map<@NonNull String, @Nullable Object> placeholders;
	/** Final reason that the translation attempt failed. */
	@NonNull
	private final TranslationFailureReason reason;
	/** Ordered locales attempted before the failure. */
	@NonNull
	private final List<@NonNull Locale> attemptedLocales;

	/**
	 * Constructs a new missing-translation exception.
	 *
	 * @param message failure message, not null
	 * @param key translation key, not null
	 * @param placeholders caller placeholders, not null
	 * @param lookupLocale the locale used to begin locale fallback, not null
	 * @throws IllegalArgumentException if the lookup locale is malformed or the placeholders contain a null key
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
	 * @throws IllegalArgumentException if the placeholders contain a null key, any locale is malformed, attempted
	 *                                  locales have duplicate language tags, or the reason is {@code RESOLUTION_FAILURE}
	 * @since 3.0.0
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
	 * @throws IllegalArgumentException if the placeholders contain a null key, any locale is malformed, attempted
	 *                                  locales have duplicate language tags, or the reason is {@code RESOLUTION_FAILURE}
	 * @since 3.0.0
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
		requireNonNull(reason);
		requireNonNull(attemptedLocales);

		if (reason == TranslationFailureReason.RESOLUTION_FAILURE)
			throw new IllegalArgumentException("MissingTranslationException cannot represent a resolution failure cause");

		Map<@NonNull String, @Nullable Object> placeholderCopy = new HashMap<>(placeholders);

		if (placeholderCopy.containsKey(null))
			throw new IllegalArgumentException("Placeholder names must not be null");

		this.key = key;
		this.placeholders = Collections.unmodifiableMap(placeholderCopy);
		this.lookupLocale = LocaleUtils.requireWellFormed(lookupLocale, "Lookup locale");
		this.localeMatchResult = localeMatchResult;
		this.reason = reason;
		List<@NonNull Locale> attemptedLocaleCopy = new ArrayList<>(attemptedLocales.size());
		LinkedHashSet<@NonNull String> attemptedLanguageTags = new LinkedHashSet<>();

		for (Locale attemptedLocale : attemptedLocales) {
			Locale validatedLocale = LocaleUtils.requireWellFormed(attemptedLocale, "Attempted locale");
			String normalizedLanguageTag = validatedLocale.toLanguageTag().toLowerCase(Locale.ROOT);

			if (!attemptedLanguageTags.add(normalizedLanguageTag))
				throw new IllegalArgumentException(format(
						"Attempted locales must not contain duplicate language tag '%s'", validatedLocale.toLanguageTag()));

			attemptedLocaleCopy.add(validatedLocale);
		}

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
	 * @since 3.0.0
	 */
	@NonNull
	public Locale getLookupLocale() {
		return this.lookupLocale;
	}

	/**
	 * Gets strict locale-negotiation diagnostics when available.
	 *
	 * @return strict locale-negotiation diagnostics when available, otherwise empty, not null
	 * @since 3.0.0
	 */
	@NonNull
	public Optional<@NonNull LocaleMatchResult> getLocaleMatchResult() {
		return Optional.ofNullable(localeMatchResult);
	}

	/**
	 * Gets the final failure reason.
	 *
	 * @return final failure reason, not null
	 * @since 3.0.0
	 */
	@NonNull
	public TranslationFailureReason getReason() {
		return reason;
	}

	/**
	 * Gets the ordered locales attempted before failure.
	 *
	 * @return ordered locales attempted before failure, not null
	 * @since 3.0.0
	 */
	@NonNull
	public List<@NonNull Locale> getAttemptedLocales() {
		return attemptedLocales;
	}
}
