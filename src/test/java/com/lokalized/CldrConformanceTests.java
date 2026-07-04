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
import java.util.Formatter;
import java.util.Locale;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
  }

  @Test
  public void cardinalitySamplesMatchPinnedCldr48_2() {
    for (GeneratedCldrConformanceData.CardinalitySample sample : GeneratedCldrConformanceData.cardinalitySamples())
      assertCardinality(sample);
  }

  @Test
  public void ordinalitySamplesMatchPinnedCldr48_2() {
    for (GeneratedCldrConformanceData.OrdinalitySample sample : GeneratedCldrConformanceData.ordinalitySamples())
      assertOrdinality(sample);
  }

  @Test
  public void cardinalityRangesMatchPinnedCldr48_2() {
    for (GeneratedCldrConformanceData.CardinalityRangeSample sample : GeneratedCldrConformanceData.cardinalityRangeSamples())
      assertRange(sample);
  }

  private void assertCardinality(GeneratedCldrConformanceData.CardinalitySample sample) {
    BigDecimal number = new BigDecimal(sample.getSample());
    Locale locale = Locale.forLanguageTag(sample.getCldrLocale().replace('_', '-'));
    Cardinality actual = Cardinality.forNumber(number, locale);

    assertEquals(sample.getExpected(), actual, format("Mismatched CLDR %s cardinality for locale %s and sample %s",
        GeneratedCldrConformanceData.CLDR_VERSION, sample.getCldrLocale(), sample.getSample()));
  }

  private void assertOrdinality(GeneratedCldrConformanceData.OrdinalitySample sample) {
    BigDecimal number = new BigDecimal(sample.getSample());
    Locale locale = Locale.forLanguageTag(sample.getCldrLocale().replace('_', '-'));
    Ordinality actual = Ordinality.forNumber(number, locale);

    assertEquals(sample.getExpected(), actual, format("Mismatched CLDR %s ordinality for locale %s and sample %s",
        GeneratedCldrConformanceData.CLDR_VERSION, sample.getCldrLocale(), sample.getSample()));
  }

  private void assertRange(GeneratedCldrConformanceData.CardinalityRangeSample sample) {
    Locale locale = Locale.forLanguageTag(sample.getCldrLocale().replace('_', '-'));
    Cardinality actual = Cardinality.forRange(sample.getStart(), sample.getEnd(), locale);

    assertEquals(sample.getExpected(), actual, format("Mismatched CLDR %s range cardinality for locale %s and range %s-%s",
        GeneratedCldrConformanceData.CLDR_VERSION, sample.getCldrLocale(), sample.getStart(), sample.getEnd()));
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
}
