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
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CLDR conformance checks backed by generated data from pinned CLDR XML.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class CldrConformanceTests {
  @Test
  public void pinnedCldrResourcesMatchGeneratedChecksums() throws Exception {
    assertSha256(GeneratedCldrConformanceData.PLURALS_RESOURCE, GeneratedCldrConformanceData.PLURALS_SHA_256);
    assertSha256(GeneratedCldrConformanceData.ORDINALS_RESOURCE, GeneratedCldrConformanceData.ORDINALS_SHA_256);
    assertSha256(GeneratedCldrConformanceData.PLURAL_RANGES_RESOURCE, GeneratedCldrConformanceData.PLURAL_RANGES_SHA_256);
    assertSha256(GeneratedCldrLocaleData.LIKELY_SUBTAGS_RESOURCE, GeneratedCldrLocaleData.LIKELY_SUBTAGS_SHA_256);
    assertSha256(GeneratedCldrLocaleData.SUPPLEMENTAL_METADATA_RESOURCE, GeneratedCldrLocaleData.SUPPLEMENTAL_METADATA_SHA_256);
    assertSha256(GeneratedCldrLocaleData.SUPPLEMENTAL_DATA_RESOURCE, GeneratedCldrLocaleData.SUPPLEMENTAL_DATA_SHA_256);
    assertSha256(GeneratedCldrLocaleData.SCRIPT_METADATA_RESOURCE, GeneratedCldrLocaleData.SCRIPT_METADATA_SHA_256);
    assertSha256(GeneratedCldrLocaleData.VALIDITY_LANGUAGE_RESOURCE, GeneratedCldrLocaleData.VALIDITY_LANGUAGE_SHA_256);
    assertSha256(GeneratedCldrLocaleData.VALIDITY_SCRIPT_RESOURCE, GeneratedCldrLocaleData.VALIDITY_SCRIPT_SHA_256);
    assertSha256(GeneratedCldrLocaleData.VALIDITY_REGION_RESOURCE, GeneratedCldrLocaleData.VALIDITY_REGION_SHA_256);
    assertSha256(GeneratedCldrLocaleData.VALIDITY_VARIANT_RESOURCE, GeneratedCldrLocaleData.VALIDITY_VARIANT_SHA_256);
  }

  @Test
  public void cardinalityRulesAndSamplesMatchPinnedCldr48_2() {
    Map<String, Set<Cardinality>> expectedCardinalitiesByLocale = new TreeMap<>();

    for (GeneratedCldrConformanceData.PluralRule<Cardinality> rule : GeneratedCldrConformanceData.cardinalityRules()) {
      for (String localeTag : valuesIn(rule.getLocales())) {
        expectedCardinalitiesByLocale.computeIfAbsent(localeTag, ignored -> EnumSet.noneOf(Cardinality.class)).add(rule.getExpected());

        for (String sample : valuesIn(rule.getSamples()))
          assertCardinality(localeTag, sample, rule.getExpected());
      }
    }

    assertEquals(Cardinality.getSupportedLocaleTags(), expectedCardinalitiesByLocale.keySet());

    for (Map.Entry<String, Set<Cardinality>> entry : expectedCardinalitiesByLocale.entrySet())
      assertEquals(entry.getValue(), Cardinality.supportedCardinalitiesForLocale(Locale.forLanguageTag(entry.getKey())),
          format("Mismatched CLDR %s cardinality categories for locale %s", GeneratedCldrConformanceData.CLDR_VERSION, entry.getKey()));
  }

  @Test
  public void ordinalityRulesAndSamplesMatchPinnedCldr48_2() {
    Map<String, Set<Ordinality>> expectedOrdinalitiesByLocale = new TreeMap<>();

    for (GeneratedCldrConformanceData.PluralRule<Ordinality> rule : GeneratedCldrConformanceData.ordinalityRules()) {
      for (String localeTag : valuesIn(rule.getLocales())) {
        expectedOrdinalitiesByLocale.computeIfAbsent(localeTag, ignored -> EnumSet.noneOf(Ordinality.class)).add(rule.getExpected());

        for (String sample : valuesIn(rule.getSamples()))
          assertOrdinality(localeTag, sample, rule.getExpected());
      }
    }

    Set<Ordinality> rootOrdinalities = expectedOrdinalitiesByLocale.get("und");
    assertNotNull(rootOrdinalities, "Expected CLDR root ordinality rules");
    assertEquals(Cardinality.getSupportedLocaleTags(), Ordinality.getSupportedLocaleTags(),
        "Every cardinality locale should inherit explicit or root ordinality rules");

    for (String localeTag : Ordinality.getSupportedLocaleTags()) {
      Set<Ordinality> expected = expectedOrdinalitiesByLocale.getOrDefault(localeTag, rootOrdinalities);
      assertEquals(expected, Ordinality.supportedOrdinalitiesForLocale(Locale.forLanguageTag(localeTag)),
          format("Mismatched CLDR %s ordinality categories for locale %s", GeneratedCldrConformanceData.CLDR_VERSION, localeTag));
    }
  }

  @Test
  public void cardinalityRangesMatchPinnedCldr48_2() {
    Map<String, SortedMap<CardinalityRange, Cardinality>> expectedRangesByLocale = new TreeMap<>();

    for (GeneratedCldrConformanceData.CardinalityRangeRule rule : GeneratedCldrConformanceData.cardinalityRangeRules()) {
      for (String localeTag : valuesIn(rule.getLocales())) {
        expectedRangesByLocale.computeIfAbsent(localeTag, ignored -> new TreeMap<>())
            .put(CardinalityRange.of(rule.getStart(), rule.getEnd()), rule.getExpected());
        assertRange(localeTag, rule.getStart(), rule.getEnd(), rule.getExpected());
      }
    }

    for (Map.Entry<String, SortedMap<CardinalityRange, Cardinality>> entry : expectedRangesByLocale.entrySet()) {
      Locale locale = Locale.forLanguageTag(entry.getKey());
      SortedMap<CardinalityRange, Cardinality> firstLookup = CldrPluralRules.cardinalityRangesForLocale(locale);

      assertEquals(entry.getValue(), firstLookup,
          format("Mismatched compiled CLDR %s range table for locale %s",
              GeneratedCldrConformanceData.CLDR_VERSION, entry.getKey()));
      assertSame(firstLookup, CldrPluralRules.cardinalityRangesForLocale(locale),
          format("Expected the compiled CLDR range table for locale %s to be cached", entry.getKey()));
    }

    SortedMap<CardinalityRange, Cardinality> arabicRanges =
        CldrPluralRules.cardinalityRangesForLocale(Locale.forLanguageTag("ar"));
    assertThrows(UnsupportedOperationException.class,
        () -> arabicRanges.put(CardinalityRange.of(Cardinality.ONE, Cardinality.ONE), Cardinality.ONE),
        "Expected cached CLDR range tables to remain immutable");
  }

  @Test
  public void generatedLanguageAliasesUseCanonicalPluralBehavior() {
    List<Number> sampleNumbers = List.of(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 20, 21, 100, 1_000,
        new BigDecimal("0.1"), new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("2.0"));

    for (String[] alias : GeneratedCldrLocaleData.LANGUAGE_ALIASES) {
      Locale aliasLocale = Locale.forLanguageTag(alias[0]);
      Locale canonicalLocale = CldrLocaleData.canonicalLocale(aliasLocale);
      String message = format("Mismatched CLDR language alias %s -> %s (Java locale %s)",
          alias[0], canonicalLocale.toLanguageTag(), aliasLocale.toLanguageTag());
      Set<Cardinality> canonicalCardinalities = Cardinality.supportedCardinalitiesForLocale(canonicalLocale);
      Set<Ordinality> canonicalOrdinalities = Ordinality.supportedOrdinalitiesForLocale(canonicalLocale);

      assertEquals(canonicalCardinalities, Cardinality.supportedCardinalitiesForLocale(aliasLocale), message);
      assertEquals(canonicalOrdinalities, Ordinality.supportedOrdinalitiesForLocale(aliasLocale), message);
      assertEquals(Cardinality.exampleIntegerValuesForLocale(canonicalLocale),
          Cardinality.exampleIntegerValuesForLocale(aliasLocale), message);
      assertEquals(Cardinality.exampleDecimalValuesForLocale(canonicalLocale),
          Cardinality.exampleDecimalValuesForLocale(aliasLocale), message);
      assertEquals(Ordinality.exampleIntegerValuesForLocale(canonicalLocale),
          Ordinality.exampleIntegerValuesForLocale(aliasLocale), message);

      if (!canonicalCardinalities.isEmpty()) {
        for (Number sampleNumber : sampleNumbers)
          assertEquals(Cardinality.forNumber(sampleNumber, canonicalLocale),
              Cardinality.forNumber(sampleNumber, aliasLocale), message + " for " + sampleNumber);

        for (Cardinality start : Cardinality.values())
          for (Cardinality end : Cardinality.values())
            assertEquals(Cardinality.forRange(start, end, canonicalLocale),
                Cardinality.forRange(start, end, aliasLocale), message + " for " + start + "-" + end);
      }

      if (!canonicalOrdinalities.isEmpty())
        for (Number sampleNumber : sampleNumbers)
          assertEquals(Ordinality.forNumber(sampleNumber, canonicalLocale),
              Ordinality.forNumber(sampleNumber, aliasLocale), message + " for " + sampleNumber);
    }
  }

  @Test
  public void privateUseLocalesUseRootPluralRules() {
    Locale privateUseLocale = Locale.forLanguageTag("x-acme");

    assertEquals(Set.of(Cardinality.OTHER), Cardinality.supportedCardinalitiesForLocale(privateUseLocale));
    assertEquals(Set.of(Ordinality.OTHER), Ordinality.supportedOrdinalitiesForLocale(privateUseLocale));
    assertEquals(Cardinality.OTHER, Cardinality.forNumber(1, privateUseLocale));
    assertEquals(Ordinality.OTHER, Ordinality.forNumber(1, privateUseLocale));
    assertEquals(Cardinality.ONE, Cardinality.forRange(Cardinality.OTHER, Cardinality.ONE, privateUseLocale));
  }

  private void assertCardinality(String localeTag, String sample, Cardinality expected) {
    Locale locale = Locale.forLanguageTag(localeTag);
    SampleValue sampleValue = SampleValue.forToken(sample);
    Cardinality actual = sampleValue.hasCompactExponent()
        ? Cardinality.forOperands(sampleValue.toOperands(), locale)
        : Cardinality.forNumber(sampleValue.getNumber(), locale);

    assertEquals(expected, actual, format("Mismatched CLDR %s cardinality for locale %s and sample %s",
        GeneratedCldrConformanceData.CLDR_VERSION, localeTag, sample));
  }

  private void assertOrdinality(String localeTag, String sample, Ordinality expected) {
    Locale locale = Locale.forLanguageTag(localeTag);
    SampleValue sampleValue = SampleValue.forToken(sample);
    Ordinality actual = sampleValue.hasCompactExponent()
        ? Ordinality.forOperands(sampleValue.toOperands(), locale)
        : Ordinality.forNumber(sampleValue.getNumber(), locale);

    assertEquals(expected, actual, format("Mismatched CLDR %s ordinality for locale %s and sample %s",
        GeneratedCldrConformanceData.CLDR_VERSION, localeTag, sample));
  }

  private void assertRange(String localeTag, Cardinality start, Cardinality end, Cardinality expected) {
    Cardinality actual = Cardinality.forRange(start, end, Locale.forLanguageTag(localeTag));

    assertEquals(expected, actual, format("Mismatched CLDR %s range cardinality for locale %s and range %s-%s",
        GeneratedCldrConformanceData.CLDR_VERSION, localeTag, start, end));
  }

  private String[] valuesIn(String values) {
    return values.length() == 0 ? new String[0] : values.split(" ");
  }

  private void assertSha256(String resourceName, String expectedSha256) throws Exception {
    try (InputStream inputStream = CldrConformanceTests.class.getResourceAsStream(format("/cldr/%s/%s",
        GeneratedCldrConformanceData.CLDR_VERSION, resourceName))) {
      assertNotNull(inputStream, format("Expected CLDR resource '%s' to be available", resourceName));
      assertEquals(expectedSha256, sha256(inputStream), format("Unexpected SHA-256 for CLDR resource '%s'", resourceName));
    }
  }

  private String sha256(InputStream inputStream) throws Exception {
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[8192];
    int bytesRead;

    while ((bytesRead = inputStream.read(buffer)) >= 0)
      messageDigest.update(buffer, 0, bytesRead);

    try (Formatter formatter = new Formatter()) {
      for (byte b : messageDigest.digest())
        formatter.format("%02x", b);

      return formatter.toString();
    }
  }

  private static final class SampleValue {
    private final BigDecimal number;
    private final int compactExponent;

    private SampleValue(BigDecimal number, int compactExponent) {
      this.number = number;
      this.compactExponent = compactExponent;
    }

    private static SampleValue forToken(String token) {
      int compactSeparator = token.indexOf('c');

      if (compactSeparator < 0)
        return new SampleValue(new BigDecimal(token), -1);

      return new SampleValue(new BigDecimal(token.substring(0, compactSeparator)),
          Integer.parseInt(token.substring(compactSeparator + 1)));
    }

    private BigDecimal getNumber() {
      return number;
    }

    private boolean hasCompactExponent() {
      return compactExponent >= 0;
    }

    private PluralOperands toOperands() {
      return PluralOperands.forNumber(number).compactExponent(compactExponent).build();
    }
  }
}
