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

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
/**
 * Exercises {@link StringInterpolator}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class StringInterpolatorTests {
  @Test
  public void placeholderNamesWithUnderscoreAndHyphen() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate("Hi {{user_name}} and {{user-name}}", Map.of(
        "user_name", "Ada",
        "user-name", "Bob"
    ));

    assertEquals("Hi Ada and Bob", result);
  }

  @Test
  public void replacementValuesAreSafelyEscaped() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate("Total: {{amount}}", Map.of(
        "amount", "$24.99"
    ));

    assertEquals("Total: $24.99", result);
  }
}
