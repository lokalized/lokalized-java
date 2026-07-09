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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Merges data into a string using {@code {{placeholder}} syntax}.
 * <p>
 * Prefix the opening mustaches with a backslash to render literal mustaches instead of a placeholder.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class StringInterpolator {
  @NonNull
  private static final String PLACEHOLDER_START;
  @NonNull
  private static final String PLACEHOLDER_END;
  private static final char ESCAPE_CHARACTER;

  static {
    PLACEHOLDER_START = "{{";
    PLACEHOLDER_END = "}}";
    ESCAPE_CHARACTER = '\\';
  }

  @NonNull
  public String interpolate(@NonNull String string, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(string);
    requireNonNull(context);

    return interpolate(string, context, false, 0).getValue();
  }

  @NonNull
  public InterpolationResult interpolateStrictly(@NonNull String string, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(string);
    requireNonNull(context);

    return interpolate(string, context, true, 0);
  }

  @NonNull
  InterpolationResult interpolateStrictly(@NonNull String string,
                                          @NonNull Map<@NonNull String, @Nullable Object> context,
                                          int maximumOutputCharacters) {
    requireNonNull(string);
    requireNonNull(context);

    if (maximumOutputCharacters <= 0)
      throw new IllegalArgumentException("maximumOutputCharacters must be positive");

    return interpolate(string, context, true, maximumOutputCharacters);
  }

  @NonNull
  private static InterpolationResult interpolate(@NonNull String string,
                                                 @NonNull Map<@NonNull String, @Nullable Object> context,
                                                 boolean strict,
                                                 int maximumOutputCharacters) {
    requireNonNull(string);
    requireNonNull(context);

    StringBuilder stringBuilder = new StringBuilder(maximumOutputCharacters == 0
        ? string.length()
        : Math.min(string.length(), maximumOutputCharacters));
    Set<@NonNull String> unresolvedPlaceholderNames = new LinkedHashSet<>();
    int index = 0;

    while (index < string.length()) {
      if (string.charAt(index) == ESCAPE_CHARACTER) {
        if (startsWith(string, String.valueOf(ESCAPE_CHARACTER), index + 1)) {
          appendChecked(stringBuilder, ESCAPE_CHARACTER, maximumOutputCharacters);
          index += 2;
          continue;
        }

        if (startsWith(string, PLACEHOLDER_START, index + 1)) {
          int escapedPlaceholderStart = index + 1;
          int escapedPlaceholderEnd = string.indexOf(PLACEHOLDER_END, escapedPlaceholderStart + PLACEHOLDER_START.length());

          if (escapedPlaceholderEnd < 0) {
            appendChecked(stringBuilder, string, escapedPlaceholderStart, string.length(), maximumOutputCharacters);
            break;
          }

          appendChecked(stringBuilder, string, escapedPlaceholderStart,
              escapedPlaceholderEnd + PLACEHOLDER_END.length(), maximumOutputCharacters);
          index = escapedPlaceholderEnd + PLACEHOLDER_END.length();
          continue;
        }

        if (startsWith(string, PLACEHOLDER_END, index + 1)) {
          appendChecked(stringBuilder, PLACEHOLDER_END, maximumOutputCharacters);
          index += 1 + PLACEHOLDER_END.length();
          continue;
        }

        appendChecked(stringBuilder, ESCAPE_CHARACTER, maximumOutputCharacters);
        ++index;
        continue;
      }

      if (startsWith(string, PLACEHOLDER_END, index)) {
        if (strict)
          throw new IllegalArgumentException(format("Unexpected placeholder closing delimiter '%s' at index %d",
              PLACEHOLDER_END, index));

        appendChecked(stringBuilder, PLACEHOLDER_END, maximumOutputCharacters);
        index += PLACEHOLDER_END.length();
        continue;
      }

      if (!startsWith(string, PLACEHOLDER_START, index)) {
        appendChecked(stringBuilder, string.charAt(index), maximumOutputCharacters);
        ++index;
        continue;
      }

      int placeholderStart = index;
      int placeholderEnd = string.indexOf(PLACEHOLDER_END, placeholderStart + PLACEHOLDER_START.length());

      if (placeholderEnd < 0) {
        if (strict)
          throw new IllegalArgumentException(format("Unclosed placeholder starting at index %d", placeholderStart));

        appendChecked(stringBuilder, string, placeholderStart, string.length(), maximumOutputCharacters);
        break;
      }

      String placeholderName = string.substring(placeholderStart + PLACEHOLDER_START.length(), placeholderEnd);

      if (!LocalizedStringUtils.isValidLocalizedStringIdentifier(placeholderName)) {
        if (strict)
          throw new IllegalArgumentException(format("Malformed placeholder '%s%s%s'. Placeholder names must start with a Unicode letter or underscore " +
              "and contain only Unicode letters, Unicode digits, Unicode combining marks, underscores, or hyphens",
              PLACEHOLDER_START, placeholderName, PLACEHOLDER_END));

        appendChecked(stringBuilder, string, placeholderStart,
            placeholderEnd + PLACEHOLDER_END.length(), maximumOutputCharacters);
        index = placeholderEnd + PLACEHOLDER_END.length();
        continue;
      }

      Object value = context.get(placeholderName);

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value == null) {
        unresolvedPlaceholderNames.add(placeholderName);
        appendChecked(stringBuilder, PLACEHOLDER_START, maximumOutputCharacters);
        appendChecked(stringBuilder, placeholderName, maximumOutputCharacters);
        appendChecked(stringBuilder, PLACEHOLDER_END, maximumOutputCharacters);
      } else {
        appendChecked(stringBuilder, String.valueOf(value), maximumOutputCharacters);
      }

      index = placeholderEnd + PLACEHOLDER_END.length();
    }

    return new InterpolationResult(stringBuilder.toString(), unresolvedPlaceholderNames);
  }

  @NonNull
  static Set<@NonNull String> placeholderNamesIn(@NonNull String string) {
    requireNonNull(string);
    return interpolate(string, Collections.emptyMap(), true, 0).getUnresolvedPlaceholderNames();
  }

  @NonNull
  static Set<@NonNull String> placeholderNamesInLeniently(@NonNull String string) {
    requireNonNull(string);
    return interpolate(string, Collections.emptyMap(), false, 0).getUnresolvedPlaceholderNames();
  }

  private static void appendChecked(@NonNull StringBuilder target, char value, int maximumOutputCharacters) {
    requireNonNull(target);

    if (maximumOutputCharacters > 0 && target.length() >= maximumOutputCharacters)
      throw outputLimitExceeded(maximumOutputCharacters);

    target.append(value);
  }

  private static void appendChecked(@NonNull StringBuilder target, @NonNull CharSequence value,
                                    int maximumOutputCharacters) {
    requireNonNull(target);
    requireNonNull(value);
    appendChecked(target, value, 0, value.length(), maximumOutputCharacters);
  }

  private static void appendChecked(@NonNull StringBuilder target, @NonNull CharSequence value,
                                    int start, int end, int maximumOutputCharacters) {
    requireNonNull(target);
    requireNonNull(value);

    int charactersToAppend = end - start;

    if (maximumOutputCharacters > 0 && charactersToAppend > maximumOutputCharacters - target.length())
      throw outputLimitExceeded(maximumOutputCharacters);

    target.append(value, start, end);
  }

  @NonNull
  private static IllegalStateException outputLimitExceeded(int maximumOutputCharacters) {
    return new IllegalStateException(format(
        "Interpolated output exceeds the maximum of %d characters", maximumOutputCharacters));
  }

  private static boolean startsWith(@NonNull String string, @NonNull String prefix, int index) {
    requireNonNull(string);
    requireNonNull(prefix);
    return index >= 0 && string.startsWith(prefix, index);
  }

  static final class InterpolationResult {
    @NonNull
    private final String value;
    @NonNull
    private final Set<@NonNull String> unresolvedPlaceholderNames;

    private InterpolationResult(@NonNull String value, @NonNull Set<@NonNull String> unresolvedPlaceholderNames) {
      requireNonNull(value);
      requireNonNull(unresolvedPlaceholderNames);

      this.value = value;
      this.unresolvedPlaceholderNames = Collections.unmodifiableSet(new LinkedHashSet<>(unresolvedPlaceholderNames));
    }

    @NonNull
    public String getValue() {
      return value;
    }

    @NonNull
    public Set<@NonNull String> getUnresolvedPlaceholderNames() {
      return unresolvedPlaceholderNames;
    }
  }
}
