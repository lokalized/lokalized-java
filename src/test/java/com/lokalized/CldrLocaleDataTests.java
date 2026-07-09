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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises CLDR-backed locale metadata.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class CldrLocaleDataTests {
  @Test
  public void canonicalLanguageTagsUseCldrAliases() {
    assertEquals("ro", CldrLocaleData.canonicalLanguageTag("mo"));
    assertEquals("sr-Latn-BA", CldrLocaleData.canonicalLanguageTag("sh-BA"));
    assertEquals("en-GB-oxendict", CldrLocaleData.canonicalLanguageTag("en-GB-oed"));
    assertEquals("und-Zinh", CldrLocaleData.canonicalLanguageTag("und-Qaai"));
    assertEquals("yue-HK-u-nu-hanidec", CldrLocaleData.canonicalLanguageTag("zh-yue-HK-u-nu-hanidec"));
  }

  @Test
  public void canonicalLanguageTagsUseContextualMultiTerritoryAliases() {
    assertEquals("hy-AM", CldrLocaleData.canonicalLanguageTag("hy-SU"));
    assertEquals("ru-RU", CldrLocaleData.canonicalLanguageTag("ru-SU"));
    assertEquals("pap-CW", CldrLocaleData.canonicalLanguageTag("pap-AN"));
  }

  @Test
  public void canonicalLanguageTagsRemoveUnknownRegionAndScriptPlaceholders() {
    assertEquals("en", CldrLocaleData.canonicalLanguageTag("en-Zzzz-ZZ"));
    assertEquals("und-Latn", CldrLocaleData.canonicalLanguageTag("und-Latn-ZZ"));
    assertEquals("und-US", CldrLocaleData.canonicalLanguageTag("und-Zzzz-US"));
  }

  @Test
  public void generatedAliasesCanonicalizeIdempotently() {
    for (String[] alias : GeneratedCldrLocaleData.LANGUAGE_ALIASES)
      assertCanonicalizationIsStable(alias[0]);

    for (String[] alias : GeneratedCldrLocaleData.SCRIPT_ALIASES)
      assertCanonicalizationIsStable("und-" + alias[0]);

    for (String[] alias : GeneratedCldrLocaleData.REGION_ALIASES)
      assertCanonicalizationIsStable("und-" + alias[0]);

    for (String[] alias : GeneratedCldrLocaleData.VARIANT_ALIASES)
      assertCanonicalizationIsStable("und-" + alias[0]);
  }

  @Test
  public void fullTagAliasesAreKnownLanguageTags() {
    assertTrue(CldrLocaleData.isKnownLanguageTag("zh-yue"));
    assertTrue(CldrLocaleData.isKnownLanguageTag("i-klingon"));
  }

  @Test
  public void likelySubtagsUsePinnedCldrData() {
    assertEquals("zh-Hant-TW", CldrLocaleData.likelySubtagFor("zh-TW").get());
    assertEquals("zh-Hant-TW", CldrLocaleData.likelySubtagFor("zh-Hant").get());
    assertEquals("sr-Latn-RS", CldrLocaleData.likelySubtagFor("sr-Latn").get());
    assertEquals("sr-Latn-RS", CldrLocaleData.likelySubtagFor("sh").get());
    assertEquals("sr-Cyrl-RS", CldrLocaleData.likelySubtagFor("sr").get());
  }

  @Test
  public void fallbackLocalesIncludeCldrParentLocales() {
    List<String> languageTags = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag("en-AU")).stream()
        .map(Locale::toLanguageTag)
        .collect(Collectors.toList());

    assertEquals(List.of("en-AU", "en-001", "en"), languageTags);
  }

  @Test
  public void fallbackLocalesBridgeNorwegianMacrolanguageAndBokmal() {
    List<String> macrolanguageTags = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag("no-NO")).stream()
        .map(Locale::toLanguageTag)
        .collect(Collectors.toList());
    List<String> bokmalTags = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag("nb-NO")).stream()
        .map(Locale::toLanguageTag)
        .collect(Collectors.toList());

    assertEquals(List.of("no-NO", "no", "nb-NO", "nb"), macrolanguageTags);
    assertEquals(List.of("nb-NO", "nb", "no", "no-NO"), bokmalTags);
  }

  @Test
  public void fallbackLocalesDoNotCrossLikelyScriptBoundaries() {
    List<String> traditionalChineseTags = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag("zh-Hant-TW")).stream()
        .map(Locale::toLanguageTag)
        .collect(Collectors.toList());
    List<String> taiwanChineseTags = CldrLocaleData.fallbackLocalesFor(Locale.forLanguageTag("zh-TW")).stream()
        .map(Locale::toLanguageTag)
        .collect(Collectors.toList());

    assertEquals(List.of("zh-Hant-TW", "zh-Hant"), traditionalChineseTags);
    assertEquals(List.of("zh-TW"), taiwanChineseTags);
  }

  private void assertCanonicalizationIsStable(String languageTag) {
    String canonical = CldrLocaleData.canonicalLanguageTag(languageTag);
    assertEquals(canonical, CldrLocaleData.canonicalLanguageTag(canonical),
        "Expected canonicalization to be idempotent for " + languageTag);
    assertFalse(canonical.contains("-Zzzz"), "Expected unknown script to be removed from " + languageTag);
    assertFalse(canonical.endsWith("-ZZ") || canonical.contains("-ZZ-"),
        "Expected unknown region to be removed from " + languageTag);
  }
}
