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
   * See <a target="_blank" href="https://docs.oracle.com/javase/9/docs/api/java/util/Locale.html#getLanguage--">Javadoc for {@code Locale#getLanguage()}</a>.
   * <p>
   * On older supported JDK releases, {@code Locale#getLanguage()} preserves these superseded codes for compatibility:
   * <p>
   * <ul>
   * <li>{@code iw} for {@code he} (Hebrew)</li>
   * <li>{@code ji} for {@code yi} (Yiddish)</li>
   * <li>{@code in} for {@code id} (Indonesian)</li>
   * </ul>
   * <p>
   * This method canonicalizes the full locale tag before extracting its language, so compound CLDR language aliases
   * such as {@code aa-Saaho} to {@code ssy} are handled as well as one-subtag aliases.
   *
   * @param locale the locale for which the language code is extracted, not null
   * @return the normalized language for the locale (if present), not null
   */
  static Optional<String> normalizedLanguage(@NonNull Locale locale) {
    requireNonNull(locale);

    String language = locale.getLanguage();

    if (language == null || "".equals(language) || "*".equals(language))
      return Optional.empty();

    return languageForCanonicalTag(CldrLocaleData.canonicalLanguageTag(locale.toLanguageTag()));
  }

  /**
   * Extracts the primary language from an already-canonical BCP 47 tag without round-tripping through
   * {@link Locale#getLanguage()}, whose legacy-code behavior differs across supported JDK releases.
   */
  static Optional<String> languageForCanonicalTag(@NonNull String canonicalLanguageTag) {
    requireNonNull(canonicalLanguageTag);

    if (canonicalLanguageTag.equalsIgnoreCase("x") ||
        canonicalLanguageTag.toLowerCase(Locale.ROOT).startsWith("x-"))
      return Optional.empty();

    int separatorIndex = canonicalLanguageTag.indexOf('-');
    String language = separatorIndex < 0
        ? canonicalLanguageTag
        : canonicalLanguageTag.substring(0, separatorIndex);

    if ("".equals(language) || "und".equalsIgnoreCase(language))
      return Optional.empty();

    return Optional.of(language);
  }
}
