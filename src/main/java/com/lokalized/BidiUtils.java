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

import static java.lang.String.format;
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
    return isolate(value, -1);
  }

  static StringInterpolator.@NonNull BoundedReplacementValue isolatedValue(@NonNull Object value,
                                                                             int maximumOutputCharacters) {
    requireNonNull(value);

    if (maximumOutputCharacters <= 0)
      throw new IllegalArgumentException("maximumOutputCharacters must be positive");

    return new BoundedIsolatedValue(value, maximumOutputCharacters);
  }

  @NonNull
  static String isolate(@NonNull CharSequence value, int maximumCharacters) {
    return isolate(value, maximumCharacters, maximumCharacters);
  }

  @NonNull
  private static String isolate(@NonNull CharSequence value, int maximumCharacters,
                                int reportedMaximumCharacters) {
    requireNonNull(value);

    if (maximumCharacters < -1)
      throw new IllegalArgumentException("maximumCharacters must be non-negative or -1 for no limit");

    int valueLength = value.length();

    // Reject before scanning or materializing an oversized CharSequence. Although balancing could discard stray pop
    // markers, processing an arbitrarily large value to discover that fact would defeat the runtime work limit.
    if (maximumCharacters >= 0 && valueLength > maximumCharacters)
      throw outputLimitExceeded(reportedMaximumCharacters);

    if (valueLength == 0)
      return "";

    if (isIsolated(value, valueLength)) {
      if (value instanceof String)
        return (String) value;

      // Materialize through the bounded CharSequence contract instead of invoking application-defined toString().
      StringBuilder isolatedValue = new StringBuilder(valueLength);

      for (int i = 0; i < valueLength; ++i)
        appendChecked(isolatedValue, value.charAt(i), maximumCharacters, reportedMaximumCharacters);

      return isolatedValue.toString();
    }

    int initialCapacity = maximumCharacters < 0
        ? valueLength
        : Math.min(valueLength, maximumCharacters);
    StringBuilder stringBuilder = new StringBuilder(initialCapacity);
    int isolateDepth = 0;

    appendChecked(stringBuilder, FIRST_STRONG_ISOLATE, maximumCharacters, reportedMaximumCharacters);

    for (int i = 0; i < valueLength; ++i) {
      char character = value.charAt(i);

      if (isIsolateInitiator(character)) {
        ++isolateDepth;
        appendChecked(stringBuilder, character, maximumCharacters, reportedMaximumCharacters);
      } else if (character == POP_DIRECTIONAL_ISOLATE) {
        if (isolateDepth > 0) {
          --isolateDepth;
          appendChecked(stringBuilder, character, maximumCharacters, reportedMaximumCharacters);
        }
      } else {
        appendChecked(stringBuilder, character, maximumCharacters, reportedMaximumCharacters);
      }
    }

    for (int i = 0; i < isolateDepth; ++i)
      appendChecked(stringBuilder, POP_DIRECTIONAL_ISOLATE, maximumCharacters, reportedMaximumCharacters);

    appendChecked(stringBuilder, POP_DIRECTIONAL_ISOLATE, maximumCharacters, reportedMaximumCharacters);
    return stringBuilder.toString();
  }

  private static boolean isIsolated(@NonNull CharSequence value, int valueLength) {
    requireNonNull(value);

    if (valueLength < 2 || !isIsolateInitiator(value.charAt(0)))
      return false;

    int isolateDepth = 0;

    for (int i = 0; i < valueLength; ++i) {
      char character = value.charAt(i);

      if (isIsolateInitiator(character)) {
        ++isolateDepth;
      } else if (character == POP_DIRECTIONAL_ISOLATE) {
        --isolateDepth;

        if (isolateDepth < 0)
          return false;

        if (isolateDepth == 0 && i < valueLength - 1)
          return false;
      }
    }

    return isolateDepth == 0;
  }

  private static void appendChecked(@NonNull StringBuilder target, char value, int maximumCharacters,
                                    int reportedMaximumCharacters) {
    requireNonNull(target);

    if (maximumCharacters >= 0 && target.length() >= maximumCharacters)
      throw outputLimitExceeded(reportedMaximumCharacters);

    target.append(value);
  }

  @NonNull
  private static IllegalStateException outputLimitExceeded(int maximumCharacters) {
    return new IllegalStateException(format(
        "Interpolated output exceeds the maximum of %d characters", maximumCharacters));
  }

  private static boolean isIsolateInitiator(char character) {
    return character == LEFT_TO_RIGHT_ISOLATE || character == RIGHT_TO_LEFT_ISOLATE || character == FIRST_STRONG_ISOLATE;
  }

  private static final class BoundedIsolatedValue implements StringInterpolator.BoundedReplacementValue {
    @NonNull
    private final Object value;
    private final int maximumOutputCharacters;
    private String renderedValue;

    private BoundedIsolatedValue(@NonNull Object value, int maximumOutputCharacters) {
      this.value = requireNonNull(value);
      this.maximumOutputCharacters = maximumOutputCharacters;
    }

    @Override
    @NonNull
    public synchronized CharSequence render(int maximumCharacters) {
      if (renderedValue != null) {
        if (maximumCharacters >= 0 && renderedValue.length() > maximumCharacters)
          throw outputLimitExceeded(maximumOutputCharacters);

        return renderedValue;
      }

      CharSequence characterSequence = value instanceof CharSequence
          ? (CharSequence) value
          : String.valueOf(value);
      renderedValue = isolate(characterSequence, maximumCharacters, maximumOutputCharacters);
      return renderedValue;
    }
  }
}
