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

import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
enum LoggerType {
  ROOT("com.lokalized"),
  STRINGS("com.lokalized.STRINGS"),
  LOCALIZED_STRING_LOADER("com.lokalized.LOCALIZED_STRING_LOADER"),
  TEST("com.lokalized.TEST");

  @Nonnull
  private final String loggerName;

  LoggerType(@Nonnull String loggerName) {
    requireNonNull(loggerName);
    this.loggerName = loggerName;
  }

  @Nonnull
  public String getLoggerName() {
    return loggerName;
  }
}
