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
import org.junit.jupiter.api.parallel.Isolated;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link Diagnostics}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Isolated("Temporarily changes the process-wide default formatting locale")
@NotThreadSafe
public class DiagnosticsTests {
  @Test
  public void fixedEnglishNumericDiagnosticsIgnoreNonLatinDefaultFormattingLocales() {
    Locale previousFormattingLocale = Locale.getDefault(Locale.Category.FORMAT);

    try {
      for (Locale formattingLocale : List.of(
          Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("hi-IN-u-nu-deva"))) {
        Locale.setDefault(Locale.Category.FORMAT, formattingLocale);

        assertNotEquals("At most 32 values", String.format("At most %d values", 32));
        assertEquals("At most 32 values", Diagnostics.format("At most %d values", 32));

        IllegalArgumentException languageRangeException = assertThrows(IllegalArgumentException.class,
            () -> new LocaleMatchResult(
                Collections.nCopies(LocaleMatcher.MAXIMUM_LANGUAGE_RANGES + 1, new LanguageRange("en")),
                null, null, null, LocaleMatchType.NONE, Locale.ENGLISH, List.of(Locale.ENGLISH)));
        assertEquals("At most 32 language ranges are supported, but received 33",
            languageRangeException.getMessage());

        IllegalArgumentException runtimeLimitException = assertThrows(IllegalArgumentException.class,
            () -> TranslationRuntimeLimits.builder().maximumNumberPrecision(0).build());
        assertEquals("maximumNumberPrecision must be positive, but was 0", runtimeLimitException.getMessage());
      }
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, previousFormattingLocale);
    }
  }
}
