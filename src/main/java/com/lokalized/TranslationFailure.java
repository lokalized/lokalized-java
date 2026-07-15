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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Describes a failed localized string lookup.
 * <p>
 * Instances are supplied to a {@link TranslationFailureHandler}. Lokalized constructs these objects; application
 * code should normally only inspect them.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public interface TranslationFailure {
	/**
	 * Gets a concise description of this failure suitable for application logging.
	 * <p>
	 * The message includes the key, lookup locale, failure reason, and attempted locale tags. It does not inspect or
	 * include placeholder values or the runtime cause's message because either may contain sensitive data. The key is
	 * included verbatim and may itself contain placeholder names such as {@code {{name}}}.
	 *
	 * @return the redacted failure message, not null
	 * @since 3.0.0
	 */
	@NonNull
	default String getMessage() {
		return format("Unable to resolve translation key '%s' for locale '%s'. Reason: %s. Attempted locales: [%s]",
				getKey(),
				getLookupLocale().toLanguageTag(),
				getReason(),
				getAttemptedLocales().stream()
						.map(Locale::toLanguageTag)
						.collect(Collectors.joining(", ")));
	}

	/**
	 * Gets the translation key that could not be resolved.
	 *
	 * @return the translation key, not null
	 */
	@NonNull
	String getKey();

	/**
	 * Gets the locale used to begin per-key locale fallback after any language-range negotiation.
	 *
	 * @return the lookup locale, not null
	 */
	@NonNull
	Locale getLookupLocale();

	/**
	 * Gets strict locale-negotiation diagnostics when the implementation can provide them.
	 *
	 * @return locale-negotiation result, or empty when unavailable, not null
	 */
	@NonNull
	default Optional<@NonNull LocaleMatchResult> getLocaleMatchResult() {
		return Optional.empty();
	}

	/**
	 * Gets the ordered locales actually attempted for this lookup.
	 *
	 * @return the attempted locales, not null
	 */
	@NonNull
	List<@NonNull Locale> getAttemptedLocales();

	/**
	 * Gets the placeholders supplied by the caller.
	 * <p>
	 * The returned map is an unmodifiable shallow copy. Placeholder values may contain sensitive data;
	 * {@link #getMessage()} deliberately excludes them.
	 *
	 * @return the supplied placeholders, not null
	 */
	@NonNull
	Map<@NonNull String, @Nullable Object> getPlaceholders();

	/**
	 * Gets the reason the lookup failed.
	 *
	 * @return the reason, not null
	 */
	@NonNull
	TranslationFailureReason getReason();

	/**
	 * Gets the runtime cause if an attempted translation existed but could not be resolved. Missing translations and
	 * unmatched alternatives do not have causes.
	 *
	 * @return the cause, not null
	 */
	@NonNull
	Optional<@NonNull Throwable> getCause();
}
