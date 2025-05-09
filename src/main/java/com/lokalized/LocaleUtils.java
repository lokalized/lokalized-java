/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2025 Revetware LLC.
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
import javax.annotation.concurrent.ThreadSafe;
import java.util.Locale;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Collection of utility methods for working with Locales.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class LocaleUtils {
  private LocaleUtils() {
    // Non-instantiable
  }

  /**
   * Normalizes a locale's language code to its canonical form.
   * <p>
   * See <a target="_blank" href="https://docs.oracle.com/javase/8/docs/api/java/util/Locale.html#getLanguage--">Javadoc for {@code Locale#getLanguage()}</a>.
   * <p>
   * For backwards compatibility, the {@code Locale#convertOldISOCodes()} method ensures {@code Locale#getLanguage()} will always return these now-superseded codes:
   * <p>
   * <ul>
   * <li>{@code iw} for {@code he} (Hebrew)</li>
   * <li>{@code ji} for {@code yi} (Yiddish)</li>
   * <li>{@code in} for {@code id} (Indonesian)</li>
   * </ul>
   * <p>
   * This method ensures we always work with the "modern" versions.
   *
   * @param locale the locale for which the language code is extracted, not null
   * @return the normalized language for the locale (if present), not null
   */
  static Optional<String> normalizedLanguage(@Nonnull Locale locale) {
    requireNonNull(locale);

    String language = locale.getLanguage();

    if (language == null || "".equals(language) || "*".equals(language))
      return Optional.empty();

    if ("iw".equals(language))
      language = "he";
    else if ("ji".equals(language))
      language = "yi";
    else if ("in".equals(language))
      language = "id";

    return Optional.of(language);
  }
}