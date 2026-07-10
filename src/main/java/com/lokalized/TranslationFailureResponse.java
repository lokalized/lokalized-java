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

import static java.util.Objects.requireNonNull;

/**
 * Response returned by a {@link TranslationFailureHandler}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class TranslationFailureResponse {
	@NonNull
	private static final TranslationFailureResponse RETURN_KEY;
	@NonNull
	private static final TranslationFailureResponse THROW_EXCEPTION;

	static {
		RETURN_KEY = new TranslationFailureResponse(Action.RETURN_KEY, null);
		THROW_EXCEPTION = new TranslationFailureResponse(Action.THROW_EXCEPTION, null);
	}

	@NonNull
	private final Action action;
	@Nullable
	private final String translation;

	private TranslationFailureResponse(@NonNull Action action, @Nullable String translation) {
		requireNonNull(action);

		this.action = action;
		this.translation = translation;
	}

	/**
	 * Returns the lookup key itself after interpolating supplied placeholders into it.
	 *
	 * @return the response, not null
	 */
	@NonNull
	public static TranslationFailureResponse returnKey() {
		return RETURN_KEY;
	}

	/**
	 * Returns a caller-specified string.
	 *
	 * @param translation translation to return, not null
	 * @return the response, not null
	 */
	@NonNull
	public static TranslationFailureResponse returnString(@NonNull String translation) {
		requireNonNull(translation);
		return new TranslationFailureResponse(Action.RETURN_STRING, translation);
	}

	/**
	 * Throws an exception for the failed lookup.
	 * <p>
	 * Resolution failures rethrow their original runtime cause. Missing translations and lookups for which no
	 * alternative matched throw {@link MissingTranslationException}.
	 *
	 * @return the response, not null
	 */
	@NonNull
	public static TranslationFailureResponse throwException() {
		return THROW_EXCEPTION;
	}

	@NonNull
	Action getAction() {
		return action;
	}

	@NonNull
	String getTranslation() {
		if (translation == null)
			throw new IllegalStateException("No translation is available for this response");

		return translation;
	}

	enum Action {
		RETURN_KEY,
		RETURN_STRING,
		THROW_EXCEPTION
	}
}
