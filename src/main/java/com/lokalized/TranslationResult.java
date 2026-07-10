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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Diagnostic result for a translation lookup. Collection state is defensively copied; an optional failure cause is
 * exposed by reference.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
public final class TranslationResult {
	@NonNull private final String key;
	@NonNull private final String translation;
	@NonNull private final Locale lookupLocale;
	@Nullable private final LocaleMatchResult localeMatchResult;
	@Nullable private final Locale resolvedLocale;
	@NonNull private final List<@NonNull Locale> attemptedLocales;
	@NonNull private final TranslationResultStatus status;
	@Nullable private final TranslationFailureReason failureReason;
	@Nullable private final Throwable cause;

	/**
	 * Constructs a translation result. This is public so custom {@link Strings} implementations can participate in the
	 * diagnostic result API.
	 *
	 * @param key translation key, not null
	 * @param translation returned string, not null
	 * @param lookupLocale locale used to begin per-key catalog fallback, not null
	 * @param resolvedLocale successfully resolved catalog locale, or null for a failure-handler response
	 * @param attemptedLocales ordered locales actually attempted, not null
	 * @param status result status, not null
	 * @param failureReason final failure reason for a handler response, otherwise null
	 * @param cause first runtime resolution cause, otherwise null
	 * @throws IllegalArgumentException if success and failure state is mixed, a resolution cause and reason disagree,
	 *                                  attempted locales contain duplicates, or the resolved locale was not attempted
	 */
	public TranslationResult(@NonNull String key, @NonNull String translation, @NonNull Locale lookupLocale,
								@Nullable Locale resolvedLocale, @NonNull List<@NonNull Locale> attemptedLocales,
								@NonNull TranslationResultStatus status, @Nullable TranslationFailureReason failureReason,
								@Nullable Throwable cause) {
		this(key, translation, lookupLocale, null, resolvedLocale, attemptedLocales, status, failureReason, cause);
	}

	/**
	 * Constructs a translation result with locale-negotiation diagnostics.
	 *
	 * @param key translation key, not null
	 * @param translation returned string, not null
	 * @param lookupLocale locale used to begin per-key catalog fallback, not null
	 * @param localeMatchResult strict locale-negotiation result, or null when unavailable
	 * @param resolvedLocale successfully resolved catalog locale, or null for a failure-handler response
	 * @param attemptedLocales ordered locales actually attempted, not null
	 * @param status result status, not null
	 * @param failureReason final failure reason for a handler response, otherwise null
	 * @param cause runtime resolution cause, otherwise null
	 * @throws IllegalArgumentException if success and failure state is mixed, a resolution cause and reason disagree,
	 *                                  attempted locales contain duplicates, or the resolved locale was not attempted
	 */
	public TranslationResult(@NonNull String key, @NonNull String translation, @NonNull Locale lookupLocale,
								@Nullable LocaleMatchResult localeMatchResult, @Nullable Locale resolvedLocale,
								@NonNull List<@NonNull Locale> attemptedLocales, @NonNull TranslationResultStatus status,
								@Nullable TranslationFailureReason failureReason, @Nullable Throwable cause) {
		this.key = requireNonNull(key);
		this.translation = requireNonNull(translation);
		this.lookupLocale = requireNonNull(lookupLocale);
		this.localeMatchResult = localeMatchResult;
		this.resolvedLocale = resolvedLocale;
		List<@NonNull Locale> attemptedLocaleCopy = new ArrayList<>(requireNonNull(attemptedLocales).size());

		for (Locale attemptedLocale : attemptedLocales)
			attemptedLocaleCopy.add(requireNonNull(attemptedLocale));

		if (new LinkedHashSet<>(attemptedLocaleCopy).size() != attemptedLocaleCopy.size())
			throw new IllegalArgumentException("Attempted locales must not contain duplicates");

		this.attemptedLocales = Collections.unmodifiableList(attemptedLocaleCopy);
		this.status = requireNonNull(status);
		this.failureReason = failureReason;
		this.cause = cause;

		if (status == TranslationResultStatus.TRANSLATED &&
				(resolvedLocale == null || failureReason != null || cause != null))
			throw new IllegalArgumentException("A translated result requires a resolved locale and no failure outcome");

		if (status != TranslationResultStatus.TRANSLATED && (resolvedLocale != null || failureReason == null))
			throw new IllegalArgumentException("A failure-handler result requires a failure reason and no resolved locale");

		if (status == TranslationResultStatus.TRANSLATED && !attemptedLocaleCopy.contains(resolvedLocale))
			throw new IllegalArgumentException("A translated result's resolved locale must be present in attempted locales");

		if (status != TranslationResultStatus.TRANSLATED &&
				((failureReason == TranslationFailureReason.RESOLUTION_FAILURE) != (cause != null)))
			throw new IllegalArgumentException("A failure result must carry a cause if and only if its reason is RESOLUTION_FAILURE");
	}

	/** @return translation key, not null */
	@NonNull
	public String getKey() {
		return key;
	}

	/** @return returned translation, key, or handler-supplied string, not null */
	@NonNull
	public String getTranslation() {
		return translation;
	}

	/** @return locale used to begin per-key catalog fallback after any negotiation, not null */
	@NonNull
	public Locale getLookupLocale() {
		return lookupLocale;
	}

	/** @return strict locale-negotiation diagnostics when available, otherwise empty, not null */
	@NonNull
	public Optional<@NonNull LocaleMatchResult> getLocaleMatchResult() {
		return Optional.ofNullable(localeMatchResult);
	}

	/** @return catalog locale that resolved successfully, or empty for a failure response, not null */
	@NonNull
	public Optional<@NonNull Locale> getResolvedLocale() {
		return Optional.ofNullable(resolvedLocale);
	}

	/** @return ordered locales actually attempted, not null */
	@NonNull
	public List<@NonNull Locale> getAttemptedLocales() {
		return attemptedLocales;
	}

	/** @return how the returned string was produced, not null */
	@NonNull
	public TranslationResultStatus getStatus() {
		return status;
	}

	/** @return final lookup failure reason for handler-produced results, otherwise empty, not null */
	@NonNull
	public Optional<@NonNull TranslationFailureReason> getFailureReason() {
		return Optional.ofNullable(failureReason);
	}

	/** @return first runtime resolution cause for a failed lookup, otherwise empty, not null */
	@NonNull
	public Optional<@NonNull Throwable> getCause() {
		return Optional.ofNullable(cause);
	}

	/**
	 * @return whether negotiation used the configured fallback or per-key resolution used a non-equivalent locale,
	 * not null
	 */
	@NonNull
	public Boolean isFallback() {
		return (localeMatchResult != null && !localeMatchResult.isMatch())
				|| (resolvedLocale != null && !CldrLocaleData.equivalent(lookupLocale, resolvedLocale));
	}

	/**
	 * Generates a diagnostic representation without rendering translation contents or throwable messages.
	 *
	 * @return a safe diagnostic representation, not null
	 */
	@Override
	@NonNull
	public String toString() {
		return format("%s{key=%s, translationLength=%d, lookupLocale=%s, localeMatchResult=%s, resolvedLocale=%s, " +
				"attemptedLocales=%s, status=%s, failureReason=%s, causeType=%s}", getClass().getSimpleName(), key,
				translation.length(), lookupLocale, localeMatchResult, resolvedLocale, attemptedLocales, status, failureReason,
				cause == null ? null : cause.getClass().getName());
	}
}
