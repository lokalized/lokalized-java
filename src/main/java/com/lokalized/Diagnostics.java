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
import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Locale-independent formatting for fixed-English diagnostics.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class Diagnostics {
  private Diagnostics() {
    // Non-instantiable
  }

  /**
   * Formats a fixed-English diagnostic using locale-independent digits and separators.
   *
   * @param template format template, not null
   * @param arguments format arguments, not null; elements may be null
   * @return formatted diagnostic, not null
   */
  @NonNull
  static String format(@NonNull String template, @Nullable Object @NonNull ... arguments) {
    requireNonNull(template);
    requireNonNull(arguments);
    return String.format(Locale.ROOT, template, arguments);
  }
}
