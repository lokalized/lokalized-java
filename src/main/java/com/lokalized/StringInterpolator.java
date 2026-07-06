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

    return interpolate(string, context, false).getValue();
  }

  @NonNull
  public InterpolationResult interpolateStrictly(@NonNull String string, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(string);
    requireNonNull(context);

    return interpolate(string, context, true);
  }

  @NonNull
  private static InterpolationResult interpolate(@NonNull String string,
                                                 @NonNull Map<@NonNull String, @Nullable Object> context,
                                                 boolean strict) {
    requireNonNull(string);
    requireNonNull(context);

    StringBuilder stringBuilder = new StringBuilder(string.length());
    Set<@NonNull String> unresolvedPlaceholderNames = new LinkedHashSet<>();
    int index = 0;

    while (index < string.length()) {
      int placeholderStart = string.indexOf(PLACEHOLDER_START, index);
      int unexpectedPlaceholderEnd = string.indexOf(PLACEHOLDER_END, index);

      if (unexpectedPlaceholderEnd >= 0 && (placeholderStart < 0 || unexpectedPlaceholderEnd < placeholderStart)) {
        if (strict)
          throw new IllegalArgumentException(format("Unexpected placeholder closing delimiter '%s' at index %d",
              PLACEHOLDER_END, unexpectedPlaceholderEnd));

        stringBuilder.append(string, index, unexpectedPlaceholderEnd + PLACEHOLDER_END.length());
        index = unexpectedPlaceholderEnd + PLACEHOLDER_END.length();
        continue;
      }

      if (placeholderStart < 0) {
        stringBuilder.append(string, index, string.length());
        break;
      }

      if (isEscapedPlaceholderStart(string, placeholderStart)) {
        stringBuilder.append(string, index, placeholderStart - 1);

        int escapedPlaceholderEnd = string.indexOf(PLACEHOLDER_END, placeholderStart + PLACEHOLDER_START.length());

        if (escapedPlaceholderEnd < 0) {
          stringBuilder.append(string, placeholderStart, string.length());
          break;
        }

        stringBuilder.append(string, placeholderStart, escapedPlaceholderEnd + PLACEHOLDER_END.length());
        index = escapedPlaceholderEnd + PLACEHOLDER_END.length();
        continue;
      }

      stringBuilder.append(string, index, placeholderStart);

      int placeholderEnd = string.indexOf(PLACEHOLDER_END, placeholderStart + PLACEHOLDER_START.length());

      if (placeholderEnd < 0) {
        if (strict)
          throw new IllegalArgumentException(format("Unclosed placeholder starting at index %d", placeholderStart));

        stringBuilder.append(string, placeholderStart, string.length());
        break;
      }

      String placeholderName = string.substring(placeholderStart + PLACEHOLDER_START.length(), placeholderEnd);

      if (!LocalizedStringUtils.isValidLocalizedStringIdentifier(placeholderName)) {
        if (strict)
          throw new IllegalArgumentException(format("Malformed placeholder '%s%s%s'. Placeholder names must start with a Unicode letter or underscore " +
              "and contain only Unicode letters, Unicode digits, underscores, or hyphens", PLACEHOLDER_START, placeholderName, PLACEHOLDER_END));

        stringBuilder.append(string, placeholderStart, placeholderEnd + PLACEHOLDER_END.length());
        index = placeholderEnd + PLACEHOLDER_END.length();
        continue;
      }

      Object value = context.get(placeholderName);

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value == null) {
        unresolvedPlaceholderNames.add(placeholderName);
        stringBuilder.append(PLACEHOLDER_START).append(placeholderName).append(PLACEHOLDER_END);
      } else {
        stringBuilder.append(value);
      }

      index = placeholderEnd + PLACEHOLDER_END.length();
    }

    return new InterpolationResult(stringBuilder.toString(), unresolvedPlaceholderNames);
  }

  @NonNull
  static Set<@NonNull String> placeholderNamesIn(@NonNull String string) {
    requireNonNull(string);
    return interpolate(string, Collections.emptyMap(), true).getUnresolvedPlaceholderNames();
  }

  private static boolean isEscapedPlaceholderStart(@NonNull String string, int placeholderStart) {
    requireNonNull(string);
    return placeholderStart > 0 && string.charAt(placeholderStart - 1) == ESCAPE_CHARACTER;
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
