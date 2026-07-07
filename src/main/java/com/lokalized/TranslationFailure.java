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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Describes a failed localized string lookup.
 * <p>
 * Instances are supplied to a {@link TranslationFailureHandler}. Lokalized constructs these objects; application
 * code should inspect them, not implement them.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public sealed interface TranslationFailure permits DefaultTranslationFailure {
	/**
	 * Gets the translation key that could not be resolved.
	 *
	 * @return the translation key, not null
	 */
	@NonNull
	String getKey();

	/**
	 * Gets the locale originally requested by the caller.
	 *
	 * @return the requested locale, not null
	 */
	@NonNull
	Locale getRequestedLocale();

	/**
	 * Gets the ordered candidate locales attempted for this lookup.
	 *
	 * @return the candidate locales, not null
	 */
	@NonNull
	List<@NonNull Locale> getCandidateLocales();

	/**
	 * Gets the placeholders supplied by the caller.
	 * <p>
	 * The returned map is an unmodifiable shallow copy. Placeholder values may contain sensitive data; built-in
	 * handlers do not log placeholder values.
	 *
	 * @return the supplied placeholders, not null
	 */
	@NonNull
	Map<@NonNull String, @Nullable Object> getPlaceholders();

	/**
	 * Gets the reason the lookup failed.
	 *
	 * @return the reason, not null
	 */
	@NonNull
	TranslationFailureReason getReason();

	/**
	 * Gets the runtime cause if an attempted translation existed but could not be resolved.
	 *
	 * @return the cause, not null
	 */
	@NonNull
	Optional<@NonNull Throwable> getCause();
}
