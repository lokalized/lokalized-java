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

import org.junit.Assert;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Locale;
import java.util.Map;

import static java.lang.String.format;

/**
 * Exercises {@link Cardinality}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class OrdinalityTests {
  @Test
  public void ordinalityForms() {
    Locale usEnglishLocale = Locale.forLanguageTag("en-US");

    Assert.assertEquals(format("Incorrect %s ordinality for 1", usEnglishLocale.toLanguageTag()),
        Ordinality.ONE, Ordinality.forNumber(1, usEnglishLocale));
    Assert.assertEquals(format("Incorrect %s ordinality for 2", usEnglishLocale.toLanguageTag()),
        Ordinality.TWO, Ordinality.forNumber(2, usEnglishLocale));
    Assert.assertEquals(format("Incorrect %s ordinality for 3", usEnglishLocale.toLanguageTag()),
        Ordinality.FEW, Ordinality.forNumber(3, usEnglishLocale));
    Assert.assertEquals(format("Incorrect %s ordinality for 4", usEnglishLocale.toLanguageTag()),
        Ordinality.OTHER, Ordinality.forNumber(4, usEnglishLocale));
    Assert.assertEquals(format("Incorrect %s ordinality for 138", usEnglishLocale.toLanguageTag()),
        Ordinality.OTHER, Ordinality.forNumber(138, usEnglishLocale));
  }

  @Test
  public void exampleIntegerValues() {
    for (String languageCode : Ordinality.getSupportedLanguageCodes()) {
      Locale locale = Locale.forLanguageTag(languageCode);

      for (Map.Entry<Ordinality, Range<Integer>> entry : Ordinality.exampleIntegerValuesForLocale(locale).entrySet()) {
        Ordinality ordinality = entry.getKey();
        Range<Integer> integers = entry.getValue();

        for (Integer integer : integers) {
          Ordinality calculatedOrdinality = Ordinality.forNumber(integer, locale);
          Assert.assertEquals(format("Mismatched '%s' cardinalities for %s. Range was %s",
              locale.toLanguageTag(), integer, integers), ordinality, calculatedOrdinality);
        }
      }
    }
  }
}