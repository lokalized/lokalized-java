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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link BidiUtils}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class BidiUtilsTests {
  @Test
  public void rightToLeftScriptDetectionUsesLikelySubtags() {
    assertTrue(BidiUtils.localeUsesRightToLeftScript(Locale.forLanguageTag("ar")));
    assertTrue(BidiUtils.localeUsesRightToLeftScript(Locale.forLanguageTag("he")));
    assertTrue(BidiUtils.localeUsesRightToLeftScript(Locale.forLanguageTag("fa")));
    assertTrue(BidiUtils.localeUsesRightToLeftScript(Locale.forLanguageTag("ur")));
    assertTrue(BidiUtils.localeUsesRightToLeftScript(Locale.forLanguageTag("az-Arab")));

    assertFalse(BidiUtils.localeUsesRightToLeftScript(Locale.forLanguageTag("en")));
    assertFalse(BidiUtils.localeUsesRightToLeftScript(Locale.forLanguageTag("az-Latn")));
  }

  @Test
  public void isolateWrapsWithFirstStrongIsolateAndPopDirectionalIsolate() {
    assertEquals("\u2068ACME-42\u2069", BidiUtils.isolate("ACME-42"));
    assertEquals("\u2068ACME-42\u2069", BidiUtils.isolate("\u2068ACME-42\u2069"));
    assertEquals("", BidiUtils.isolate(""));
  }
}
