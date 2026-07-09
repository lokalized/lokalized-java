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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocaleMatcherTests {
  @Test
  public void exactLocaleWinsBeforeCanonicalAlias() {
    Locale moldavian = Locale.forLanguageTag("mo");
    Locale romanian = Locale.forLanguageTag("ro");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(moldavian, Set.of(new LocalizedString.Builder("hello").translation("mo").build()));
    localizedStringsByLocale.put(romanian, Set.of(new LocalizedString.Builder("hello").translation("ro").build()));

    Strings strings = Strings.withFallbackLocale(romanian)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> romanian)
        .tiebreakerLocalesByLanguageCode(Map.of("ro", List.of(romanian, moldavian)))
        .build();

    assertEquals(romanian, strings.bestMatchFor(romanian));
    assertEquals(romanian, strings.bestMatchFor(Locale.forLanguageTag("ro-MD")));
    assertEquals(romanian, strings.bestMatchFor(Locale.forLanguageTag("ron")));
  }

  @Test
  public void ambiguousCanonicalFallbackUsesConfiguredTiebreaker() {
    Locale moldavian = Locale.forLanguageTag("mo");
    Locale romanian = Locale.forLanguageTag("ro");
    Locale bibliographicRomanian = Locale.forLanguageTag("ron");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(moldavian, Set.of(new LocalizedString.Builder("hello").translation("mo").build()));
    localizedStringsByLocale.put(romanian, Set.of(new LocalizedString.Builder("hello").translation("ro").build()));

    Strings strings = Strings.withFallbackLocale(bibliographicRomanian)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> bibliographicRomanian)
        .tiebreakerLocalesByLanguageCode(Map.of("ro", List.of(romanian, moldavian)))
        .build();

    assertEquals(romanian, strings.bestMatchFor(List.of()));
    assertEquals("ro", strings.get("hello"));
  }

  @Test
  public void canonicalParentFallbackUsesConfiguredTiebreaker() {
    Locale moldavian = Locale.forLanguageTag("mo");
    Locale bibliographicRomanian = Locale.forLanguageTag("rum");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(moldavian, Set.of(new LocalizedString.Builder("hello").translation("mo").build()));
    localizedStringsByLocale.put(bibliographicRomanian,
        Set.of(new LocalizedString.Builder("hello").translation("rum").build()));

    Strings strings = Strings.withFallbackLocale(moldavian)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> moldavian)
        .tiebreakerLocalesByLanguageCode(Map.of("ro", List.of(bibliographicRomanian, moldavian)))
        .build();

    assertEquals(bibliographicRomanian, strings.bestMatchFor(Locale.forLanguageTag("ro-MD")));
  }

  @Test
  public void directLocaleLookupUsesTheSameCanonicalTiebreakerAsMatching() {
    Locale moldavian = Locale.forLanguageTag("mo");
    Locale bibliographicRomanian = Locale.forLanguageTag("rum");
    Locale requestedLocale = Locale.forLanguageTag("ro-MD");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(moldavian,
        Set.of(new LocalizedString.Builder("hello").translation("mo").build()));
    localizedStringsByLocale.put(bibliographicRomanian,
        Set.of(new LocalizedString.Builder("hello").translation("rum").build()));

    Strings strings = Strings.withFallbackLocale(moldavian)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> requestedLocale)
        .tiebreakerLocalesByLanguageCode(Map.of("ro", List.of(bibliographicRomanian, moldavian)))
        .build();

    assertEquals(bibliographicRomanian, strings.bestMatchFor(requestedLocale));
    assertEquals("rum", strings.get("hello"));
    assertEquals("rum", strings.get("hello", TranslationOptions.forLocale(requestedLocale)));
  }

  @Test
  public void canonicalAliasCanSatisfyConfiguredFallbackLocale() {
    Locale moldavian = Locale.forLanguageTag("mo");
    Locale romanian = Locale.forLanguageTag("ro");
    Strings strings = Strings.withFallbackLocale(romanian)
        .localizedStringSupplier(() -> Map.of(
            moldavian, Set.of(new LocalizedString.Builder("hello").translation("salut").build())))
        .localeSupplier(matcher -> romanian)
        .build();

    assertEquals("salut", strings.get("hello"));
    assertEquals(moldavian, strings.bestMatchFor(List.of()));
    assertEquals(moldavian, strings.bestMatchFor(Locale.ROOT));
    assertTrue(strings.getSupportedLocales().contains(strings.bestMatchFor(Locale.ROOT)));
  }

  @Test
  public void zeroWeightSpecificRangeExcludesLocaleFromBroaderRange() {
    Locale americanEnglish = Locale.forLanguageTag("en-US");
    Locale britishEnglish = Locale.forLanguageTag("en-GB");
    Strings strings = englishStrings(americanEnglish, britishEnglish);

    assertEquals(britishEnglish, strings.bestMatchFor(LanguageRange.parse("en;q=1,en-US;q=0")));
  }

  @Test
  public void moreSpecificLowerWeightOverridesBroaderWeightForThatLocale() {
    Locale americanEnglish = Locale.forLanguageTag("en-US");
    Locale britishEnglish = Locale.forLanguageTag("en-GB");
    Strings strings = englishStrings(americanEnglish, britishEnglish);

    assertEquals(britishEnglish, strings.bestMatchFor(LanguageRange.parse("en;q=1,en-US;q=0.5")));
  }

  @Test
  public void wildcardDoesNotRestoreExplicitlyExcludedFallback() {
    Locale americanEnglish = Locale.forLanguageTag("en-US");
    Locale britishEnglish = Locale.forLanguageTag("en-GB");
    Locale french = Locale.forLanguageTag("fr");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(americanEnglish, Set.of(new LocalizedString.Builder("hello").translation("hello").build()));
    localizedStringsByLocale.put(britishEnglish, Set.of(new LocalizedString.Builder("hello").translation("hello").build()));
    localizedStringsByLocale.put(french, Set.of(new LocalizedString.Builder("hello").translation("bonjour").build()));

    Strings strings = Strings.withFallbackLocale(americanEnglish)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> americanEnglish)
        .tiebreakerLocalesByLanguageCode(Map.of("en", List.of(americanEnglish, britishEnglish)))
        .build();

    assertEquals(britishEnglish, strings.bestMatchFor(LanguageRange.parse("*;q=1,en-US;q=0")));
  }

  @Test
  public void specificPositiveRangeUsesCldrFallbackBeforeLowerQualityWildcard() {
    Locale english = Locale.forLanguageTag("en");
    Locale french = Locale.forLanguageTag("fr-FR");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(english, Set.of(new LocalizedString.Builder("hello").translation("hello").build()));
    localizedStringsByLocale.put(french, Set.of(new LocalizedString.Builder("hello").translation("bonjour").build()));

    Strings strings = Strings.withFallbackLocale(english)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> english)
        .build();

    assertEquals(french, strings.bestMatchFor(LanguageRange.parse("fr-BE;q=1,*;q=0.5")));
  }

  @Test
  public void allExcludedLocalesUseTheConfiguredFallback() {
    Locale english = Locale.forLanguageTag("en");
    Strings strings = Strings.withFallbackLocale(english)
        .localizedStringSupplier(() -> Map.of(
            english, Set.of(new LocalizedString.Builder("hello").translation("hello").build())))
        .localeSupplier(matcher -> english)
        .build();

    assertEquals(english, strings.bestMatchFor(LanguageRange.parse("en;q=0")));
  }

  @Test
  public void excessiveLanguageRangeListsAreRejected() {
    Locale english = Locale.forLanguageTag("en");
    Strings strings = Strings.withFallbackLocale(english)
        .localizedStringSupplier(() -> Map.of(
            english, Set.of(new LocalizedString.Builder("hello").translation("hello").build())))
        .localeSupplier(matcher -> english)
        .build();

    assertThrows(IllegalArgumentException.class, () -> strings.bestMatchFor(
        Collections.nCopies(1001, new LanguageRange("*"))));
  }

  private Strings englishStrings(Locale fallbackLocale, Locale otherLocale) {
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(fallbackLocale,
        Set.of(new LocalizedString.Builder("hello").translation(fallbackLocale.toLanguageTag()).build()));
    localizedStringsByLocale.put(otherLocale,
        Set.of(new LocalizedString.Builder("hello").translation(otherLocale.toLanguageTag()).build()));

    return Strings.withFallbackLocale(fallbackLocale)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> fallbackLocale)
        .tiebreakerLocalesByLanguageCode(Map.of("en", List.of(fallbackLocale, otherLocale)))
        .build();
  }
}
