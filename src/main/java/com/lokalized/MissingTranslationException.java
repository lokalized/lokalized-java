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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
	private final Locale locale;
	@NonNull
	private final Map<@NonNull String, @Nullable Object> placeholders;

	/**
	 * Constructs a new exception with the unsupported locale.
	 *
	 * @param locale the unsupported locale which triggered this exception, not null
	 */
	public MissingTranslationException(@NonNull String message,
																		 @NonNull String key,
																		 @NonNull Map<@NonNull String, @Nullable Object> placeholders,
																		 @NonNull Locale locale) {
		super(requireNonNull(message));

		requireNonNull(key);
		requireNonNull(placeholders);
		requireNonNull(locale);

		this.key = key;
		this.placeholders = Collections.unmodifiableMap(new HashMap<>(placeholders));
		this.locale = locale;
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
	 * The locale that triggered this exception.
	 *
	 * @return the locale, not null
	 */
	@NonNull
	public Locale getLocale() {
		return this.locale;
	}
}
