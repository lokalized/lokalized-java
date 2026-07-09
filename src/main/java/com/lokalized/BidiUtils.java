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
 * Utilities for Unicode bidirectional isolation.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class BidiUtils {
  private static final char LEFT_TO_RIGHT_ISOLATE;
  private static final char RIGHT_TO_LEFT_ISOLATE;
  private static final char FIRST_STRONG_ISOLATE;
  private static final char POP_DIRECTIONAL_ISOLATE;

  static {
    LEFT_TO_RIGHT_ISOLATE = '\u2066';
    RIGHT_TO_LEFT_ISOLATE = '\u2067';
    FIRST_STRONG_ISOLATE = '\u2068';
    POP_DIRECTIONAL_ISOLATE = '\u2069';
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

    return CldrLocaleData.isRightToLeftScript(script);
  }

  @NonNull
  static String isolate(@NonNull String value) {
    requireNonNull(value);

    if (value.length() == 0 || isIsolated(value))
      return value;

    String balancedValue = balanceEmbeddedIsolates(value);
    return new StringBuilder(balancedValue.length() + 2)
        .append(FIRST_STRONG_ISOLATE)
        .append(balancedValue)
        .append(POP_DIRECTIONAL_ISOLATE)
        .toString();
  }

  private static boolean isIsolated(@NonNull String value) {
    requireNonNull(value);

    if (value.length() < 2 || !isIsolateInitiator(value.charAt(0)))
      return false;

    int isolateDepth = 0;

    for (int i = 0; i < value.length(); ++i) {
      char character = value.charAt(i);

      if (isIsolateInitiator(character)) {
        ++isolateDepth;
      } else if (character == POP_DIRECTIONAL_ISOLATE) {
        --isolateDepth;

        if (isolateDepth < 0)
          return false;

        if (isolateDepth == 0 && i < value.length() - 1)
          return false;
      }
    }

    return isolateDepth == 0;
  }

  @NonNull
  private static String balanceEmbeddedIsolates(@NonNull String value) {
    requireNonNull(value);

    StringBuilder stringBuilder = new StringBuilder(value.length());
    int isolateDepth = 0;

    for (int i = 0; i < value.length(); ++i) {
      char character = value.charAt(i);

      if (isIsolateInitiator(character)) {
        ++isolateDepth;
        stringBuilder.append(character);
      } else if (character == POP_DIRECTIONAL_ISOLATE) {
        if (isolateDepth > 0) {
          --isolateDepth;
          stringBuilder.append(character);
        }
      } else {
        stringBuilder.append(character);
      }
    }

    for (int i = 0; i < isolateDepth; ++i)
      stringBuilder.append(POP_DIRECTIONAL_ISOLATE);

    return stringBuilder.toString();
  }

  private static boolean isIsolateInitiator(char character) {
    return character == LEFT_TO_RIGHT_ISOLATE || character == RIGHT_TO_LEFT_ISOLATE || character == FIRST_STRONG_ISOLATE;
  }
}
