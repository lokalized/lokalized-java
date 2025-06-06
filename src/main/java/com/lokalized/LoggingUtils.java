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
import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collection of utility methods for working with logging.
 * <p>
 * This is for internal testing and debugging only!
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class LoggingUtils {
  private LoggingUtils() {
    // Non-instantiable
  }

  /**
   * Overrides the system's root logger level.
   * <p>
   * This is for internal testing and debugging only!
   *
   * @param level the level to use, not null
   */
  @Nonnull
  public static void setRootLoggerLevel(@Nonnull Level level) {
    Objects.requireNonNull(level);

    Logger rootLogger = Logger.getLogger("");

    for (Handler handler : rootLogger.getHandlers())
      handler.setLevel(Level.FINEST);

    rootLogger.setLevel(level);
  }
}
