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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link TranslationFailure}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class DefaultTranslationFailure implements TranslationFailure {
	@NonNull
	private final String key;
	@NonNull
	private final Locale lookupLocale;
	@NonNull
	private final Optional<@NonNull LocaleMatchResult> localeMatchResult;
	@NonNull
	private final List<@NonNull Locale> attemptedLocales;
	@NonNull
	private final Map<@NonNull String, @Nullable Object> placeholders;
	@NonNull
	private final TranslationFailureReason reason;
	@NonNull
	private final Optional<@NonNull Throwable> cause;

	DefaultTranslationFailure(@NonNull String key,
														@NonNull Locale lookupLocale,
														@Nullable LocaleMatchResult localeMatchResult,
														@NonNull List<@NonNull Locale> attemptedLocales,
														@NonNull Map<@NonNull String, @Nullable Object> placeholders,
														@NonNull TranslationFailureReason reason,
														@Nullable Throwable cause) {
		requireNonNull(key);
		requireNonNull(lookupLocale);
		requireNonNull(attemptedLocales);
		requireNonNull(placeholders);
		requireNonNull(reason);

		this.key = key;
		this.lookupLocale = lookupLocale;
		this.localeMatchResult = Optional.ofNullable(localeMatchResult);
		this.attemptedLocales = Collections.unmodifiableList(new ArrayList<>(attemptedLocales));
		this.placeholders = Collections.unmodifiableMap(new HashMap<>(placeholders));
		this.reason = reason;
		this.cause = Optional.ofNullable(cause);
	}

	@NonNull
	@Override
	public String getKey() {
		return key;
	}

	@NonNull
	@Override
	public Locale getLookupLocale() {
		return lookupLocale;
	}

	@NonNull
	@Override
	public Optional<@NonNull LocaleMatchResult> getLocaleMatchResult() {
		return localeMatchResult;
	}

	@NonNull
	@Override
	public List<@NonNull Locale> getAttemptedLocales() {
		return attemptedLocales;
	}

	@NonNull
	@Override
	public Map<@NonNull String, @Nullable Object> getPlaceholders() {
		return placeholders;
	}

	@NonNull
	@Override
	public TranslationFailureReason getReason() {
		return reason;
	}

	@NonNull
	@Override
	public Optional<@NonNull Throwable> getCause() {
		return cause;
	}
}
