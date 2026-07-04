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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Merges data into a string using {@code {{placeholder}} syntax}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class StringInterpolator {
  @NonNull
  private static final Pattern PLACEHOLDER_PATTERN;
  @NonNull
  private static final Pattern PLACEHOLDER_NAME_PATTERN;

  static {
    String placeholderNamePattern = "[\\p{Alpha}_][\\p{Alnum}_-]*";
    PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(" + placeholderNamePattern + ")}}");
    PLACEHOLDER_NAME_PATTERN = Pattern.compile(placeholderNamePattern);
  }

  @NonNull
  public String interpolate(@NonNull String string, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(string);
    requireNonNull(context);

    Matcher matcher = PLACEHOLDER_PATTERN.matcher(string);

    // Matcher#appendReplacement only accepts StringBuffer, not StringBuilder
    StringBuffer stringBuffer = new StringBuffer();

    while (matcher.find()) {
      String name = matcher.group(1);
      Object value = context.get(name);

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value == null) {
        matcher.appendReplacement(stringBuffer, format("{{%s}}", name));
      } else {
        String valueAsString = value.toString();
        matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement(valueAsString));
      }
    }

    matcher.appendTail(stringBuffer);

    return stringBuffer.toString();
  }

  @NonNull
  public InterpolationResult interpolateStrictly(@NonNull String string, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(string);
    requireNonNull(context);

    StringBuilder stringBuilder = new StringBuilder(string.length());
    Set<@NonNull String> unresolvedPlaceholderNames = new LinkedHashSet<>();
    int index = 0;

    while (index < string.length()) {
      int placeholderStart = string.indexOf("{{", index);
      int unexpectedPlaceholderEnd = string.indexOf("}}", index);

      if (unexpectedPlaceholderEnd >= 0 && (placeholderStart < 0 || unexpectedPlaceholderEnd < placeholderStart))
        throw new IllegalArgumentException(format("Unexpected placeholder closing delimiter '}}' at index %d", unexpectedPlaceholderEnd));

      if (placeholderStart < 0) {
        stringBuilder.append(string, index, string.length());
        break;
      }

      stringBuilder.append(string, index, placeholderStart);

      int placeholderEnd = string.indexOf("}}", placeholderStart + "{{".length());

      if (placeholderEnd < 0)
        throw new IllegalArgumentException(format("Unclosed placeholder starting at index %d", placeholderStart));

      String placeholderName = string.substring(placeholderStart + "{{".length(), placeholderEnd);

      if (!PLACEHOLDER_NAME_PATTERN.matcher(placeholderName).matches())
        throw new IllegalArgumentException(format("Malformed placeholder '{{%s}}'. Placeholder names must start with a letter or underscore " +
            "and contain only letters, digits, underscores, or hyphens", placeholderName));

      Object value = context.get(placeholderName);

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value == null) {
        unresolvedPlaceholderNames.add(placeholderName);
        stringBuilder.append("{{").append(placeholderName).append("}}");
      } else {
        stringBuilder.append(value);
      }

      index = placeholderEnd + "}}".length();
    }

    return new InterpolationResult(stringBuilder.toString(), unresolvedPlaceholderNames);
  }

  @NonNull
  static Set<@NonNull String> placeholderNamesIn(@NonNull String string) {
    requireNonNull(string);

    Set<@NonNull String> placeholderNames = new LinkedHashSet<>();
    int index = 0;

    while (index < string.length()) {
      int placeholderStart = string.indexOf("{{", index);
      int unexpectedPlaceholderEnd = string.indexOf("}}", index);

      if (unexpectedPlaceholderEnd >= 0 && (placeholderStart < 0 || unexpectedPlaceholderEnd < placeholderStart))
        throw new IllegalArgumentException(format("Unexpected placeholder closing delimiter '}}' at index %d", unexpectedPlaceholderEnd));

      if (placeholderStart < 0)
        break;

      int placeholderEnd = string.indexOf("}}", placeholderStart + "{{".length());

      if (placeholderEnd < 0)
        throw new IllegalArgumentException(format("Unclosed placeholder starting at index %d", placeholderStart));

      String placeholderName = string.substring(placeholderStart + "{{".length(), placeholderEnd);

      if (!PLACEHOLDER_NAME_PATTERN.matcher(placeholderName).matches())
        throw new IllegalArgumentException(format("Malformed placeholder '{{%s}}'. Placeholder names must start with a letter or underscore " +
            "and contain only letters, digits, underscores, or hyphens", placeholderName));

      placeholderNames.add(placeholderName);
      index = placeholderEnd + "}}".length();
    }

    return Collections.unmodifiableSet(placeholderNames);
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
