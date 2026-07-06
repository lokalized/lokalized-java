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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.SortedMap;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    assertEquals(Cardinality.ONE, Cardinality.forNumber(1, usEnglishLocale),
        format("Incorrect %s cardinality for 1", usEnglishLocale.toLanguageTag()));
    assertEquals(Cardinality.ONE, Cardinality.forNumber(-1, usEnglishLocale),
        format("Incorrect %s cardinality for -1", usEnglishLocale.toLanguageTag()));
    assertEquals(Cardinality.OTHER, Cardinality.forNumber(0, usEnglishLocale),
        format("Incorrect %s cardinality for 0", usEnglishLocale.toLanguageTag()));
    assertEquals(Cardinality.OTHER, Cardinality.forNumber(1.5, usEnglishLocale),
        format("Incorrect %s cardinality for 1.5", usEnglishLocale.toLanguageTag()));
    assertEquals(Cardinality.OTHER, Cardinality.forNumber(-1.5, usEnglishLocale),
        format("Incorrect %s cardinality for -1.5", usEnglishLocale.toLanguageTag()));
  }

  @Test
  public void visibleDecimalPlacesAreHonoredForNonBigDecimal() {
    Locale usEnglishLocale = Locale.forLanguageTag("en-US");

    assertEquals(Cardinality.OTHER, Cardinality.forNumber(1, 2, usEnglishLocale),
        "Expected visible decimal places to affect cardinality for non-BigDecimal inputs");
  }

  @Test
  public void operandsCanProvideCompactExponent() {
    Locale frenchLocale = Locale.forLanguageTag("fr");

    assertEquals(Cardinality.OTHER, Cardinality.forNumber(2, frenchLocale),
        "Expected plain French 2 to use the normal cardinality rule");
    assertEquals(Cardinality.MANY, Cardinality.forOperands(PluralOperands.forNumber(2).compactExponent(6).build(), frenchLocale),
        "Expected compact exponent operands to be available to CLDR rules");
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
          assertEquals(cardinality, calculatedCardinality, format("Mismatched '%s' cardinalities for %s. Range was %s",
              locale.toLanguageTag(), integer, integers));
        }
      }
    }
  }

  @Test
  public void exampleDecimalValues() {
    for (String languageCode : Cardinality.getSupportedLanguageCodes()) {
      Locale locale = Locale.forLanguageTag(languageCode);

      for (Entry<Cardinality, Range<BigDecimal>> entry : Cardinality.exampleDecimalValuesForLocale(locale).entrySet()) {
        Cardinality cardinality = entry.getKey();
        Range<BigDecimal> bigDecimals = entry.getValue();

        for (BigDecimal bigDecimal : bigDecimals) {
          Cardinality calculatedCardinality = Cardinality.forNumber(bigDecimal, locale);
          assertEquals(cardinality, calculatedCardinality, format("Mismatched '%s' cardinalities for %s. Range was %s",
              locale.toLanguageTag(), bigDecimal, bigDecimals));
        }
      }
    }
  }

  @Test
  public void ranges() {
    List<CardinalityRange> allCardinalityRanges = new ArrayList<>(Cardinality.values().length * Cardinality.values().length);

    // Cartesian product
    for (Cardinality start : Cardinality.values())
      for (Cardinality end : Cardinality.values())
        allCardinalityRanges.add(CardinalityRange.of(start, end));

    for (String languageCode : Cardinality.getSupportedLanguageCodes()) {
      Locale locale = Locale.forLanguageTag(languageCode);
      SortedMap<CardinalityRange, Cardinality> cardinalitiesByCardinalityRange = CldrPluralRules.cardinalityRangesForLocale(locale);

      for (CardinalityRange cardinalityRange : allCardinalityRanges) {
        Cardinality expectedCardinality = cardinalitiesByCardinalityRange.get(cardinalityRange);

        if (expectedCardinality == null)
          expectedCardinality = cardinalityRange.getEnd();

        Cardinality actualCardinality = Cardinality.forRange(cardinalityRange.getStart(), cardinalityRange.getEnd(), locale);

        assertEquals(expectedCardinality, actualCardinality,
            format("Mismatched cardinality for range %s and locale %s", cardinalityRange, locale.toLanguageTag()));
      }
    }
  }

  @Test
  public void missingRangePairsDefaultToEndCardinality() {
    assertEquals(Cardinality.ONE,
        Cardinality.forRange(Cardinality.OTHER, Cardinality.ONE, Locale.forLanguageTag("fa")),
        "Expected missing range pairs to follow UTS #35's default-to-end rule");
  }
}
