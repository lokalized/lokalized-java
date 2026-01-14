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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * Merges data into a string using {@code {{placeholder}} syntax}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class StringInterpolator {
  @NonNull
  private static final Pattern PLACEHOLDER_PATTERN;

  static {
    PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[\\p{Alpha}_][\\p{Alnum}_-]*}}");
  }

  @NonNull
  public String interpolate(@NonNull String string, @NonNull Map<@NonNull String, @Nullable Object> context) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(string);

    // Matcher#appendReplacement only accepts StringBuffer, not StringBuilder
    StringBuffer stringBuffer = new StringBuffer();

    while (matcher.find()) {
      String name = matcher.group();
      name = name.substring("{{".length(), name.length() - "}}".length());
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
}
