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

import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Decides how Lokalized should respond when a localized string lookup fails.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@FunctionalInterface
public interface TranslationFailureHandler {
	/**
	 * Handles a failed localized string lookup.
	 *
	 * @param translationFailure the failed lookup, not null
	 * @return the response Lokalized should apply, not null
	 */
	@NonNull
	TranslationFailureResponse handle(@NonNull TranslationFailure translationFailure);

	/**
	 * Returns the lookup key itself after interpolating supplied placeholders into it.
	 * <p>
	 * Runtime resolution failures are handled the same way as missing translations. Use {@link #throwException()} or
	 * a custom handler that throws for {@link TranslationFailureReason#RESOLUTION_FAILURE} to surface broken placeholder
	 * rules, expressions, or custom resolvers.
	 *
	 * @return the handler, not null
	 */
	@NonNull
	static TranslationFailureHandler returnKey() {
		return (translationFailure) -> {
			requireNonNull(translationFailure);
			return TranslationFailureResponse.returnKey();
		};
	}

	/**
	 * Throws an exception for failed lookups.
	 *
	 * @return the handler, not null
	 */
	@NonNull
	static TranslationFailureHandler throwException() {
		return (translationFailure) -> {
			requireNonNull(translationFailure);
			return TranslationFailureResponse.throwException();
		};
	}

	/**
	 * Logs the failed lookup at warning level and then returns the lookup key itself.
	 * <p>
	 * Placeholder values are not logged.
	 * <p>
	 * Runtime resolution failures are handled the same way as missing translations after logging. Use
	 * {@link #throwException()} or a custom handler that throws for {@link TranslationFailureReason#RESOLUTION_FAILURE}
	 * to surface broken placeholder rules, expressions, or custom resolvers.
	 *
	 * @param logger logger to use, not null
	 * @return the handler, not null
	 */
	@NonNull
	static TranslationFailureHandler logAndReturnKey(@NonNull Logger logger) {
		requireNonNull(logger);

		return (translationFailure) -> {
			requireNonNull(translationFailure);
			logger.warning(format("Unable to resolve translation key '%s' for locale '%s'. Reason: %s. Candidate locales: [%s]",
					translationFailure.getKey(),
					translationFailure.getRequestedLocale().toLanguageTag(),
					translationFailure.getReason(),
					translationFailure.getCandidateLocales().stream()
							.map(locale -> locale.toLanguageTag())
							.collect(Collectors.joining(", "))));
			return TranslationFailureResponse.returnKey();
		};
	}
}
