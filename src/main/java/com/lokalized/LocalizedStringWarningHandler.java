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

import static java.util.Objects.requireNonNull;

/**
 * Decides how Lokalized should respond to a non-fatal validation problem detected while loading a localized strings file.
 * <p>
 * A handler may be supplied to {@link LocalizedStringLoader}'s load methods. Overloads without a handler silently
 * {@link #ignore() ignore} warnings. Provide {@link #throwException()} to make warnings fatal, or a custom handler to
 * collect them, forward them to your logging framework, increment a metric, and so on.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@FunctionalInterface
public interface LocalizedStringWarningHandler {
	/**
	 * Handles a localized strings validation warning.
	 * <p>
	 * Throwing from this method aborts loading and propagates to the caller of the load method.
	 *
	 * @param warning the warning to handle, not null
	 */
	void handle(@NonNull LocalizedStringWarning warning);

	/**
	 * Returns a handler that silently ignores every warning and allows loading to continue.
	 * <p>
	 * This is the default when a loading or parsing overload does not accept a handler.
	 *
	 * @return the handler, not null
	 */
	@NonNull
	static LocalizedStringWarningHandler ignore() {
		return (warning) -> {
			requireNonNull(warning);
		};
	}

	/**
	 * Returns a handler that turns every warning into a fatal {@link LocalizedStringLoadingException}, aborting the load.
	 * <p>
	 * This is useful for build-time or test-time strictness where an incomplete localized strings file should fail fast.
	 *
	 * @return the handler, not null
	 */
	@NonNull
	static LocalizedStringWarningHandler throwException() {
		return (warning) -> {
			requireNonNull(warning);
			throw new LocalizedStringLoadingException(warning.getMessage());
		};
	}
}
