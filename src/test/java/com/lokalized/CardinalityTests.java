/*
 * Copyright 2017 Product Mog LLC.
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
import java.util.Map.Entry;

import static java.lang.String.format;

/**
 * Exercises {@link Cardinality}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class CardinalityTests {
  @Test
  public void cardinalityForms() {
    Locale usEnglishLocale = Locale.forLanguageTag("en-US");

    Assert.assertEquals(format("Incorrect %s cardinality for 1", usEnglishLocale.toLanguageTag()),
        Cardinality.ONE, Cardinality.forNumber(1, usEnglishLocale));
    Assert.assertEquals(format("Incorrect %s cardinality for 0", usEnglishLocale.toLanguageTag()),
        Cardinality.OTHER, Cardinality.forNumber(0, usEnglishLocale));
    Assert.assertEquals(format("Incorrect %s cardinality for 1.5", usEnglishLocale.toLanguageTag()),
        Cardinality.OTHER, Cardinality.forNumber(1.5, usEnglishLocale));
  }

  @Test
  public void exampleIntegerValues() {
    for (String languageCode : Cardinality.getSupportedLanguageCodes()) {
      Locale locale = Locale.forLanguageTag(languageCode);

      for (Entry<Cardinality, Range<Integer>> entry : Cardinality.exampleIntegerValuesForLocale(locale).entrySet()) {
        Cardinality cardinality = entry.getKey();
        Range<Integer> integers = entry.getValue();

        for (Integer integer : integers) {
          Cardinality calculatedCardinality = Cardinality.forNumber(integer, locale);
          Assert.assertEquals(format("Mismatched '%s' cardinalities for %s. Range was %s",
              locale.toLanguageTag(), integer, integers), cardinality, calculatedCardinality);
        }
      }
    }
  }
}