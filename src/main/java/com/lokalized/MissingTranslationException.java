/*
 * Copyright 2017 Product Mog LLC.
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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when no translation is found when {@link com.lokalized.DefaultStrings.FailureMode} is configured to be {@link com.lokalized.DefaultStrings.FailureMode#FAIL_FAST}.
 * <p>
 * In production, you would normally prefer {@link com.lokalized.DefaultStrings.FailureMode#USE_FALLBACK}.
 * <p>
 * This class is intended for use by a single thread.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class MissingTranslationException extends RuntimeException {
	@Nonnull
	private final String key;
	@Nonnull
	private final Locale locale;
	@Nonnull
	private final Map<String, Object> placeholders;

	/**
	 * Constructs a new exception with the unsupported locale.
	 *
	 * @param locale the unsupported locale which triggered this exception, not null
	 */
	public MissingTranslationException(@Nonnull String message,
																		 @Nonnull String key,
																		 @Nonnull Map<String, Object> placeholders,
																		 @Nonnull Locale locale) {
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
	@Nonnull
	public String getKey() {
		return this.key;
	}

	/**
	 * The placeholders specified for the failed translation attempt.
	 *
	 * @return the placeholders, not null
	 */
	@Nonnull
	public Map<String, Object> getPlaceholders() {
		return this.placeholders;
	}

	/**
	 * The locale that triggered this exception.
	 *
	 * @return the locale, not null
	 */
	@Nonnull
	public Locale getLocale() {
		return this.locale;
	}
}