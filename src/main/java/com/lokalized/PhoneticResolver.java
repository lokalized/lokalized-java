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
import java.util.Locale;

/**
 * Resolves {@link Phonetic} categories for terms at runtime.
 * <p>
 * This allows callers to supply language-specific phonetic logic without
 * Lokalized needing a built-in phonetic dictionary.
 * Implementations shared by a {@link Strings} instance may be invoked concurrently and must be thread-safe.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 1.2.0
 */
@ThreadSafe
@FunctionalInterface
public interface PhoneticResolver {
  /**
   * Determines the phonetic category for a term.
   *
   * @param term the term to classify, not null
   * @param locale the locale in which to resolve the term, not null
   * @return the phonetic category for the term, not null
   */
  @NonNull
  Phonetic resolve(@NonNull String term, @NonNull Locale locale);
}
