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

import javax.annotation.concurrent.ThreadSafe;
import java.util.function.Consumer;

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
 * @since 3.0.0
 */
@ThreadSafe
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
	 * Notifies an observer about each failed lookup and then returns the lookup key itself after interpolating supplied
	 * placeholders into it.
	 * <p>
	 * Use {@link TranslationFailure#getMessage()} when the observer needs a message suitable for logging without
	 * exposing placeholder values. The observer may be invoked concurrently when the returned handler is shared by a
	 * {@link Strings} instance and must therefore be thread-safe. An exception thrown by the observer propagates to the
	 * caller.
	 *
	 * @param observer observer to notify before returning the key, not null
	 * @return the handler, not null
	 * @since 3.0.0
	 */
	@NonNull
	static TranslationFailureHandler returnKey(@NonNull Consumer<? super @NonNull TranslationFailure> observer) {
		requireNonNull(observer);

		return (translationFailure) -> {
			requireNonNull(translationFailure);
			observer.accept(translationFailure);
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
}
