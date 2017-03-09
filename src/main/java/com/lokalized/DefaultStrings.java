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
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of a localized string provider.
 * <p>
 * It is recommended to use a single instance of this class across your entire application.
 * <p>
 * In multi-tenant systems like a web application where each user might have a different locale,
 * your {@code localeSupplier} might return the locale specified by current request.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class DefaultStrings implements Strings {
	@Nonnull
	private final Locale defaultLocale;
	@Nonnull
	private final Supplier<Locale> localeSupplier;
	@Nonnull
	private final FailureMode failureMode;

	/**
	 * Constructs a localized string provider with builder-supplied data.
	 *
	 * @param defaultLocale  default fallback locale, not null
	 * @param localeSupplier standard locale supplier, may be null
	 * @param failureMode    strategy for dealing with lookup failures, may be null
	 */
	protected DefaultStrings(@Nonnull Locale defaultLocale,
													 @Nullable Supplier<Locale> localeSupplier,
													 @Nullable FailureMode failureMode) {
		requireNonNull(defaultLocale);

		this.defaultLocale = defaultLocale;
		this.localeSupplier = localeSupplier == null ? () -> defaultLocale : localeSupplier;
		this.failureMode = failureMode == null ? FailureMode.USE_FALLBACK : failureMode;
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key) {
		requireNonNull(key);
		return get(key, getLocale(), Collections.emptyMap());
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key, @Nonnull Map<String, Object> placeholders) {
		requireNonNull(key);
		requireNonNull(placeholders);

		return get(key, getLocale(), Collections.emptyMap());
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key, @Nonnull Locale locale) {
		requireNonNull(key);
		requireNonNull(locale);

		return get(key, locale, Collections.emptyMap());
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key, @Nonnull Locale locale, @Nonnull Map<String, Object> placeholders) {
		requireNonNull(key);
		requireNonNull(locale);
		requireNonNull(placeholders);

		throw new UnsupportedOperationException();
	}

	/**
	 * Gets the default fallback locale.
	 *
	 * @return the default fallback locale, not null
	 */
	@Nonnull
	public Locale getDefaultLocale() {
		return defaultLocale;
	}

	/**
	 * Gets the standard locale supplier.
	 *
	 * @return the standard locale supplier, not null
	 */
	@Nonnull
	public Supplier<Locale> getLocaleSupplier() {
		return localeSupplier;
	}

	/**
	 * Gets the strategy for handling string lookup failures.
	 *
	 * @return the strategy for handling string lookup failures, not null
	 */
	@Nonnull
	public FailureMode getFailureMode() {
		return failureMode;
	}

	@Nonnull
	protected Locale getLocale() {
		Locale locale = getLocaleSupplier().get();
		return locale == null ? getDefaultLocale() : locale;
	}

	/**
	 * Strategies for handling localized string lookup failures.
	 */
	public enum FailureMode {
		/**
		 * The system will attempt a series of fallbacks in order to not throw an exception at runtime.
		 * <p>
		 * This mode is useful for production, where we often want program execution to continue in the face of
		 * localization errors.
		 */
		USE_FALLBACK,
		/**
		 * The system will throw an exception if a localization is missing for the specified locale.
		 * <p>
		 * This mode is useful for testing, since problems are uncovered right away when execution halts.
		 */
		FAIL_FAST
	}

	/**
	 * Builder used to construct instances of {@code DefaultStrings}.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	public static class Builder {
		@Nonnull
		private final Locale defaultLocale;
		@Nullable
		private Supplier<Locale> localeSupplier;
		@Nullable
		private FailureMode failureMode;

		public Builder(@Nonnull Locale defaultLocale) {
			requireNonNull(defaultLocale);
			this.defaultLocale = defaultLocale;
		}

		@Nonnull
		public Builder localeSupplier(@Nullable Supplier<Locale> localeSupplier) {
			this.localeSupplier = localeSupplier;
			return this;
		}

		@Nonnull
		public Builder failureMode(@Nullable FailureMode failureMode) {
			this.failureMode = failureMode;
			return this;
		}

		public DefaultStrings build() {
			return new DefaultStrings(defaultLocale, localeSupplier, failureMode);
		}
	}
}