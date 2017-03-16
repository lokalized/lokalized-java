/*
 * Copyright 2017 Transmogrify LLC.
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
import java.util.Locale;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when an operation was performed on a locale that is not recognized by the system.
 * <p>
 * This class is intended for use by a single thread.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class UnsupportedLocaleException extends RuntimeException {
	@Nonnull
	private final Locale locale;

	/**
	 * Constructs a new exception with the unsupported locale.
	 *
	 * @param locale the unsupported locale which triggered this exception, not null
	 */
	public UnsupportedLocaleException(@Nonnull Locale locale) {
		super(format("Unsupported locale '%s' was provided", requireNonNull(locale).toLanguageTag()));
		this.locale = locale;
	}

	/**
	 * The unsupported locale that triggered this exception.
	 *
	 * @return the unsupported locale, not null
	 */
	@Nonnull
	public Locale getLocale() {
		return locale;
	}
}