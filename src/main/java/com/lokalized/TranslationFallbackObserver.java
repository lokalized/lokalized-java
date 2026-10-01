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

/**
 * Observes a translation supplied by a later locale candidate after earlier candidates failed.
 * <p>
 * The observer is called once, synchronously, after successful translation and before the lookup returns. It is not
 * called when the first candidate succeeds, even if locale negotiation used fallback, or when the lookup fails.
 * Unlike {@link TranslationFallbackPolicy}, it does not decide whether lookup continues, and unlike
 * {@link TranslationFailureHandler}, it observes a successful outcome.
 * <p>
 * An observer exception propagates directly to the caller; it does not become a translation resolution failure or
 * cause another locale attempt. Implementations shared by a {@link Strings} instance or {@link TranslationOptions}
 * may be invoked concurrently and must be thread-safe.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.1.1
 */
@ThreadSafe
@FunctionalInterface
public interface TranslationFallbackObserver {

	/**
	 * Observes successful locale fallback.
	 *
	 * @param translationFallbackEvent diagnostic event for this lookup, not null
	 */
	void observe(@NonNull TranslationFallbackEvent translationFallbackEvent);
}
