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
	private final Locale requestedLocale;
	@NonNull
	private final List<@NonNull Locale> candidateLocales;
	@NonNull
	private final Map<@NonNull String, @Nullable Object> placeholders;
	@NonNull
	private final TranslationFailureReason reason;
	@NonNull
	private final Optional<@NonNull Throwable> cause;

	DefaultTranslationFailure(@NonNull String key,
														@NonNull Locale requestedLocale,
														@NonNull List<@NonNull Locale> candidateLocales,
														@NonNull Map<@NonNull String, @Nullable Object> placeholders,
														@NonNull TranslationFailureReason reason,
														@Nullable Throwable cause) {
		requireNonNull(key);
		requireNonNull(requestedLocale);
		requireNonNull(candidateLocales);
		requireNonNull(placeholders);
		requireNonNull(reason);

		this.key = key;
		this.requestedLocale = requestedLocale;
		this.candidateLocales = Collections.unmodifiableList(new ArrayList<>(candidateLocales));
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
	public Locale getRequestedLocale() {
		return requestedLocale;
	}

	@NonNull
	@Override
	public List<@NonNull Locale> getCandidateLocales() {
		return candidateLocales;
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
