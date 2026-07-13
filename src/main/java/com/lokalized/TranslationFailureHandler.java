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
 * Decides how Lokalized should respond when a localized string lookup fails after the configured
 * {@link TranslationFallbackPolicy} stops fallback or all locale candidates are exhausted.
 * <p>
 * Failures from evaluating a reachable {@link LocalizedString.ExpressionAlternative expression-fragment predicate}
 * or interpolating its selected/default fragment are reported as
 * {@link TranslationFailureReason#RESOLUTION_FAILURE}. The
 * {@link TranslationFallbackPolicy#fallbackOnMissingTranslationOrNoMatchingAlternative() default fallback policy}
 * stops on these failures, while
 * {@link TranslationFallbackPolicy#fallbackOnAnyFailure()} may try another locale before invoking this handler. An
 * expression-selected fragment always has a default translation, so its selection cannot itself produce
 * {@link TranslationFailureReason#NO_MATCHING_ALTERNATIVE}.
 * <p>
 * Handlers shared by a {@link Strings} instance may be invoked concurrently and must be thread-safe.
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
	 * Unmatched whole-message alternatives and runtime resolution failures are handled the same way as missing
	 * translations. This includes failures in evaluated expression-fragment predicates and selected/default fragments.
	 * Use {@link #throwException()} or a custom handler that throws for
	 * {@link TranslationFailureReason#RESOLUTION_FAILURE} to surface broken generated-placeholder rules, expressions,
	 * interpolation, or custom resolvers.
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
	 * <p>
	 * A runtime cause supplied for {@link TranslationFailureReason#RESOLUTION_FAILURE} is rethrown. Other failure reasons
	 * produce a {@link MissingTranslationException}.
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
	 * Unmatched whole-message alternatives and runtime resolution failures are handled the same way as missing
	 * translations after logging. This includes failures in evaluated expression-fragment predicates and
	 * selected/default fragments. Use {@link #throwException()} or a custom handler that throws for
	 * {@link TranslationFailureReason#RESOLUTION_FAILURE} to surface broken generated-placeholder rules, expressions,
	 * interpolation, or custom resolvers.
	 *
	 * @param logger logger to use, not null
	 * @return the handler, not null
	 */
	@NonNull
	static TranslationFailureHandler logAndReturnKey(@NonNull Logger logger) {
		requireNonNull(logger);

		return (translationFailure) -> {
			requireNonNull(translationFailure);
			logger.warning(format("Unable to resolve translation key '%s' for locale '%s'. Reason: %s. Attempted locales: [%s]",
					translationFailure.getKey(),
					translationFailure.getLookupLocale().toLanguageTag(),
					translationFailure.getReason(),
					translationFailure.getAttemptedLocales().stream()
							.map(locale -> locale.toLanguageTag())
							.collect(Collectors.joining(", "))));
			return TranslationFailureResponse.returnKey();
		};
	}
}
