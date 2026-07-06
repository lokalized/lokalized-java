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
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Utilities for Unicode bidirectional isolation.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class BidiUtils {
  private static final char FIRST_STRONG_ISOLATE;
  private static final char POP_DIRECTIONAL_ISOLATE;
  @NonNull
  private static final Set<@NonNull String> RIGHT_TO_LEFT_SCRIPT_CODES;

  static {
    FIRST_STRONG_ISOLATE = '\u2068';
    POP_DIRECTIONAL_ISOLATE = '\u2069';
    RIGHT_TO_LEFT_SCRIPT_CODES = Set.of(
        "Adlm",
        "Arab",
        "Armi",
        "Avst",
        "Chrs",
        "Cprt",
        "Elym",
        "Hatr",
        "Hebr",
        "Khar",
        "Lydi",
        "Mand",
        "Mani",
        "Mend",
        "Merc",
        "Mero",
        "Narb",
        "Nbat",
        "Nkoo",
        "Orkh",
        "Palm",
        "Phli",
        "Phlp",
        "Phnx",
        "Prti",
        "Rohg",
        "Samr",
        "Sarb",
        "Sogd",
        "Syrc",
        "Thaa",
        "Yezi"
    );
  }

  private BidiUtils() {
    // Non-instantiable
  }

  static boolean localeUsesRightToLeftScript(@NonNull Locale locale) {
    requireNonNull(locale);

    String script = locale.getScript();

    if (script.length() == 0) {
      Optional<String> likelySubtag = CldrLocaleData.likelySubtagFor(locale);

      if (likelySubtag.isPresent())
        script = Locale.forLanguageTag(likelySubtag.get()).getScript();
    }

    return RIGHT_TO_LEFT_SCRIPT_CODES.contains(script);
  }

  @NonNull
  static String isolate(@NonNull String value) {
    requireNonNull(value);

    if (value.length() == 0 || isIsolated(value))
      return value;

    return new StringBuilder(value.length() + 2)
        .append(FIRST_STRONG_ISOLATE)
        .append(value)
        .append(POP_DIRECTIONAL_ISOLATE)
        .toString();
  }

  private static boolean isIsolated(@NonNull String value) {
    requireNonNull(value);
    return value.length() >= 2 &&
        value.charAt(0) == FIRST_STRONG_ISOLATE &&
        value.charAt(value.length() - 1) == POP_DIRECTIONAL_ISOLATE;
  }
}
