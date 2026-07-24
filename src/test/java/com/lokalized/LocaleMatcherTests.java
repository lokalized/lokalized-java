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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  public void zeroWeightRangeExcludesMatchingDescendantsAndExtensions() {
    Locale posixEnglish = Locale.forLanguageTag("en-US-posix");
    Locale numberedEnglish = Locale.forLanguageTag("en-US-u-nu-latn");
    Locale britishEnglish = Locale.forLanguageTag("en-GB");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(posixEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("posix").build()));
    localizedStringsByLocale.put(numberedEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("numbered").build()));
    localizedStringsByLocale.put(britishEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("british").build()));

    Strings strings = Strings.withFallbackLocale(britishEnglish)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> britishEnglish)
        .tiebreakerLocalesByLanguageCode(Map.of("en", List.of(posixEnglish, numberedEnglish, britishEnglish)))
        .build();

    assertEquals(britishEnglish, strings.bestMatchFor(LanguageRange.parse("en;q=1,en-US;q=0")));
  }

  @Test
  public void zeroWeightExtendedRangeExcludesStructuralMatches() {
    Locale americanLatinEnglish = Locale.forLanguageTag("en-Latn-US");
    Locale britishEnglish = Locale.forLanguageTag("en-GB");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(americanLatinEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("american").build()));
    localizedStringsByLocale.put(britishEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("british").build()));

    Strings strings = Strings.withFallbackLocale(britishEnglish)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> britishEnglish)
        .tiebreakerLocalesByLanguageCode(Map.of("en", List.of(americanLatinEnglish, britishEnglish)))
        .build();

    assertEquals(britishEnglish,
        strings.bestMatchFor(LanguageRange.parse("en;q=1,en-*-US;q=0")));
  }

	@Test
	public void internalWildcardRangeUsesExtendedFiltering() {
		Locale english = Locale.ENGLISH;
		Locale traditionalChinese = Locale.forLanguageTag("zh-Hant-TW");
		Strings strings = stringsForLocales(english, traditionalChinese);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("zh-*-TW")));

		assertEquals(traditionalChinese, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
	}

	@Test
	public void nonmatchingInternalWildcardContinuesToTheNextRange() {
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, britishEnglish);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("en-*-US,*"));

		assertEquals(french, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("*", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(LocaleMatchType.WILDCARD, result.getMatchType());
	}

	@Test
	public void internalWildcardUsesLanguageTiebreakerAndReportsExtendedRange() {
		Locale french = Locale.FRENCH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = localizedStringsFor(
				french, americanEnglish, britishEnglish);
		Strings strings = Strings.withFallbackLocale(french)
				.localizedStringSupplier(() -> localizedStringsByLocale)
				.localeSupplier(matcher -> french)
				.tiebreakerLocalesByLanguageCode(Map.of("en", List.of(americanEnglish, britishEnglish)))
				.build();

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("en-*")));

		assertEquals(americanEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
	}

	@Test
	public void trailingWildcardMatchesABareLanguageTag() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, english);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("en-*")));

		assertEquals(english, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
	}

	@Test
	public void repeatedNoninitialWildcardsMatchABareLanguageTag() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, english);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("en-*-*")));

		assertEquals(english, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
	}

	@Test
	public void repeatedCatchallWildcardsHaveBareWildcardSpecificity() {
		Locale english = Locale.ENGLISH;
		Strings strings = stringsForLocales(english);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("*;q=1,*-*;q=0"));

		assertEquals(english, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.WILDCARD, result.getMatchType());
	}

	@Test
	public void nonBareCatchallWildcardReportsExtendedRangeWithoutManufacturingSpecificity() {
		Locale english = Locale.ENGLISH;
		Strings strings = stringsForLocales(english);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("*-*")));

		assertEquals(english, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
	}

	@Test
	public void trailingWildcardCanExcludeABareLanguageTag() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, english);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("*;q=1,en-*;q=0"));

		assertEquals(french, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.WILDCARD, result.getMatchType());
	}

	@Test
	public void privateUseTrailingWildcardUsesExtendedFiltering() {
		Locale english = Locale.ENGLISH;
		Locale privateUse = Locale.forLanguageTag("x-acme");
		Strings strings = stringsForLocales(english, privateUse);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("x-*")));

		assertEquals(privateUse, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
		assertEquals(english, strings.bestMatchFor(LanguageRange.parse("*;q=1,x-*;q=0")));
	}

	@Test
	public void wildcardPrimaryExtendedRangePrefersSupportedFallback() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale americanFrench = Locale.forLanguageTag("fr-US");
		Strings strings = stringsForLocales(americanFrench, americanEnglish);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("*-US")));

		assertEquals(americanFrench, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
	}

	@Test
	public void repeatedWildcardSubtagsCannotManufactureExclusionSpecificity() {
		Locale french = Locale.FRENCH;
		Locale posixEnglish = Locale.forLanguageTag("en-US-posix");
		Strings strings = stringsForLocales(french, posixEnglish);
		List<LanguageRange> ranges = List.of(
				new LanguageRange("en-US", 1.0),
				new LanguageRange("en-*-*-US", 0.0));

		LocaleMatchResult result = strings.matchFor(ranges);

		assertEquals(posixEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
	}

	@Test
	public void repeatedWildcardCatchallCannotOutrankItsConcreteConstraint() {
		Locale french = Locale.FRENCH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Strings strings = stringsForLocales(french, americanEnglish);
		List<LanguageRange> ranges = List.of(
				new LanguageRange("*-US", 1.0),
				new LanguageRange("*-*-US", 0.0));

		LocaleMatchResult result = strings.matchFor(ranges);

		assertEquals(americanEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
	}

	@Test
	public void trailingWildcardDoesNotRequireADescendantSubtag() {
		Locale french = Locale.FRENCH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale posixEnglish = Locale.forLanguageTag("en-US-posix");
		Strings strings = stringsForLocales(french, americanEnglish);
		List<LanguageRange> ranges = List.of(
				new LanguageRange("en-US", 1.0),
				new LanguageRange("en-US-*", 0.0));

		LocaleMatchResult result = strings.matchFor(ranges);

		assertEquals(americanEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));

		Strings descendantsOnly = stringsForLocales(french, posixEnglish);
		assertEquals(posixEnglish,
				descendantsOnly.matchFor(ranges).getLocale().orElseThrow(AssertionError::new));
		assertEquals(posixEnglish, descendantsOnly.bestMatchFor(ranges));
	}

	@Test
	public void repeatedTrailingWildcardsCannotManufactureSpecificity() {
		Locale french = Locale.FRENCH;
		Locale posixEnglish = Locale.forLanguageTag("en-US-posix");
		Strings strings = stringsForLocales(french, posixEnglish);
		List<LanguageRange> ranges = List.of(
				new LanguageRange("en-US-*", 1.0),
				new LanguageRange("en-US-*-*", 0.0));

		LocaleMatchResult result = strings.matchFor(ranges);

		assertEquals(posixEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
	}

	@Test
	public void moreSpecificLowerWeightOverridesBroaderWeightForThatLocale() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Strings strings = englishStrings(americanEnglish, britishEnglish);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("en;q=1,en-US;q=0.5"));

		assertEquals(britishEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("en", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void broadRangeCannotSelectLocaleOwnedByLaterEqualWeightSpecificRange() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(americanEnglish, french);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("en;q=1,fr;q=0.5,en-US;q=0.5"));

		assertEquals(french, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("fr", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, result.getMatchType());
	}

	@Test
	public void reversingEqualWeightSpecificRangesReversesTheWinner() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(americanEnglish, french);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("en;q=1,en-US;q=0.5,fr;q=0.5"));

		assertEquals(americanEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("en-us", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, result.getMatchType());
	}

	@Test
	public void wildcardCannotSelectLocaleOwnedByLaterEqualWeightSpecificRange() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(americanEnglish, french);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("*;q=1,fr;q=0.5,en-US;q=0.5"));

		assertEquals(french, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("fr", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, result.getMatchType());
	}

	@Test
	public void extendedWildcardCannotSelectLocaleOwnedByLaterEqualWeightSpecificRange() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(americanEnglish, french);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("en-*;q=1,fr;q=0.5,en-US;q=0.5"));

		assertEquals(french, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("fr", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, result.getMatchType());
	}

	@Test
	public void zeroWeightSpecificRangeCannotReenterBroadRangeSelection() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(americanEnglish, french);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("en;q=1,fr;q=0.5,en-US;q=0"));

		assertEquals(french, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("fr", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, result.getMatchType());
	}

	@Test
	public void diagnosticsUseTheRangeThatDeterminedTheSelectedLocalesEffectiveWeight() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		List<LanguageRange> languageRanges = LanguageRange.parse("en;q=1,en-US;q=0.5,en-GB;q=0.8");
		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
		localizedStringsByLocale.put(americanEnglish,
				Set.of(new LocalizedString.Builder("hello").translation("hello").build()));
		localizedStringsByLocale.put(britishEnglish,
				Set.of(new LocalizedString.Builder("hello").translation("hello").build()));
		Strings strings = Strings.withFallbackLocale(americanEnglish)
				.localizedStringSupplier(() -> localizedStringsByLocale)
				.localeMatchSupplier(matcher -> matcher.matchFor(languageRanges))
				.tiebreakerLocalesByLanguageCode(Map.of("en", List.of(americanEnglish, britishEnglish)))
				.build();

		LocaleMatchResult matchResult = strings.matchFor(languageRanges);

		assertEquals(britishEnglish, matchResult.getLocale().orElseThrow(AssertionError::new));
		assertEquals("en-gb", matchResult.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.8), matchResult.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, matchResult.getMatchType());
		assertFalse(strings.getResult("hello").isFallback());
	}

	@Test
	public void effectiveRangeRetainsTheSelectionHierarchysDiagnosticRelationship() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		List<LanguageRange> languageRanges = List.of(new LanguageRange("en"));
		Strings strings = Strings.withFallbackLocale(americanEnglish)
				.localizedStringSupplier(() -> Map.of(americanEnglish,
						Set.of(new LocalizedString.Builder("hello").translation("hello").build())))
				.localeMatchSupplier(matcher -> matcher.matchFor(languageRanges))
				.build();

		LocaleMatchResult matchResult = strings.matchFor(languageRanges);

		assertEquals(americanEnglish, matchResult.getLocale().orElseThrow(AssertionError::new));
		assertEquals("en", matchResult.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), matchResult.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, matchResult.getMatchType());
		assertTrue(strings.getResult("hello").isFallback());
	}

	@Test
	public void weightedRegionalChineseRangesRetainScriptAwareOwnership() {
		Locale english = Locale.ENGLISH;
		Locale simplifiedChinese = Locale.forLanguageTag("zh-Hans");
		Locale traditionalChinese = Locale.forLanguageTag("zh-Hant");
		Strings strings = dualScriptChineseStrings(english, simplifiedChinese, traditionalChinese);
		List<String> regionalRanges = List.of("zh-TW", "zh-HK", "zh-CN");
		List<Locale> expectedLocales = List.of(
				traditionalChinese, traditionalChinese, simplifiedChinese);

		for (int index = 0; index < regionalRanges.size(); ++index) {
			String regionalRange = regionalRanges.get(index);
			String header = regionalRange + ",zh;q=0.9,en;q=0.8";
			LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

			assertEquals(expectedLocales.get(index),
					result.getLocale().orElseThrow(AssertionError::new), header);
			assertEquals(expectedLocales.get(index),
					strings.bestMatchForAcceptLanguage(header), header);
			assertEquals(regionalRange.toLowerCase(Locale.ROOT),
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(), header);
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), header);
			assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType(), header);
		}
	}

	@Test
	public void higherQualityBroadChineseRangeStillWinsAfterRegionalDowngrade() {
		Locale english = Locale.ENGLISH;
		Locale simplifiedChinese = Locale.forLanguageTag("zh-Hans");
		Locale traditionalChinese = Locale.forLanguageTag("zh-Hant");
		Strings strings = dualScriptChineseStrings(english, simplifiedChinese, traditionalChinese);

		LocaleMatchResult result = strings.matchFor(
				LanguageRange.parse("zh;q=1,zh-TW;q=0.5,en;q=0.4"));

		assertEquals(simplifiedChinese, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("zh", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void specificLikelySubtagRangeAdvancesPastExcludedOrDowngradedTiebreaker() {
		Locale french = Locale.FRENCH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Map<Locale, Set<LocalizedString>> localizedStrings =
				localizedStringsFor(french, americanEnglish, britishEnglish);
		Strings strings = Strings.withFallbackLocale(french)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(americanEnglish, britishEnglish)))
				.build();
		List<String> languageRangeHeaders = List.of(
				"en-AU;q=1,en-US;q=0",
				"en-AU;q=1,en-US;q=0.5");

		for (String languageRangeHeader : languageRangeHeaders) {
			LocaleMatchResult result = strings.matchFor(LanguageRange.parse(languageRangeHeader));

			assertEquals(britishEnglish,
					result.getLocale().orElseThrow(AssertionError::new), languageRangeHeader);
			assertEquals("en-au",
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(), languageRangeHeader);
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), languageRangeHeader);
			assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType(), languageRangeHeader);
		}
	}

	@Test
	public void structuralAnchorPreventsSpecificRangeFromOwningHeuristicSibling() {
		Locale french = Locale.FRENCH;
		Locale posixEnglish = Locale.forLanguageTag("en-US-posix");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Map<Locale, Set<LocalizedString>> localizedStrings =
				localizedStringsFor(french, posixEnglish, britishEnglish);
		Strings strings = Strings.withFallbackLocale(french)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(britishEnglish, posixEnglish)))
				.build();

		LocaleMatchResult result = strings.matchFor(
				LanguageRange.parse("en;q=1,fr;q=0.8,en-US;q=0.5"));

		assertEquals(britishEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("en", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void releasedSiblingCanBeClaimedByNextSpecificHeuristicRange() {
		Locale french = Locale.FRENCH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Map<Locale, Set<LocalizedString>> localizedStrings =
				localizedStringsFor(french, americanEnglish, britishEnglish);
		Strings strings = Strings.withFallbackLocale(french)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(americanEnglish, britishEnglish)))
				.build();
		List<LanguageRange> languageRanges = List.of(
				new LanguageRange("en-NZ", 1.0),
				new LanguageRange("en-Latn-AU", 0.5));

		LocaleMatchResult result = strings.matchFor(languageRanges);

		assertEquals(britishEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("en-nz", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void duplicateSpecificHeuristicRangeDoesNotConsumeAnotherLocale() {
		Locale french = Locale.FRENCH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Map<Locale, Set<LocalizedString>> localizedStrings =
				localizedStringsFor(french, americanEnglish, britishEnglish);
		Strings strings = Strings.withFallbackLocale(french)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"en", List.of(americanEnglish, britishEnglish)))
				.build();
		LanguageRange broadEnglish = new LanguageRange("en", 1.0);
		LanguageRange australianEnglish = new LanguageRange("en-AU", 0.5);

		LocaleMatchResult singleResult =
				strings.matchFor(List.of(broadEnglish, australianEnglish));
		LocaleMatchResult duplicateResult =
				strings.matchFor(List.of(broadEnglish, australianEnglish, australianEnglish));

		for (LocaleMatchResult result : List.of(singleResult, duplicateResult)) {
			assertEquals(britishEnglish, result.getLocale().orElseThrow(AssertionError::new));
			assertEquals("en", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
			assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
			assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
		}
	}

	@Test
	public void lowerWeightDuplicateCannotOverrideItsRepresentative() {
		Locale french = Locale.FRENCH;
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Strings strings = stringsForLocales(french, americanEnglish);
		List<LanguageRange> languageRanges = List.of(
				new LanguageRange("en", 1.0),
				new LanguageRange("en", 0.0));

		LocaleMatchResult result = strings.matchFor(languageRanges);

		assertEquals(americanEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("en", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void equalWeightUnknownDuplicateDoesNotMoveSemanticOwner() {
		Locale french = Locale.FRENCH;
		Locale unknownRegionalLocale = Locale.forLanguageTag("zz-US");
		Strings strings = stringsForLocales(french, unknownRegionalLocale);
		LanguageRange unknownRange = new LanguageRange("zz", 1.0);
		LanguageRange frenchRange = new LanguageRange("fr", 1.0);
		List<List<LanguageRange>> requests = List.of(
				List.of(unknownRange, frenchRange),
				List.of(unknownRange, frenchRange, unknownRange));

		for (List<LanguageRange> request : requests) {
			LocaleMatchResult result = strings.matchFor(request);

			assertEquals(unknownRegionalLocale,
					result.getLocale().orElseThrow(AssertionError::new), request.toString());
			assertEquals("zz",
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(),
					request.toString());
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), request.toString());
			assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType(), request.toString());
		}
	}

	@Test
	public void parserAddedAliasSharesOneSpecificHeuristicOwner() {
		Locale french = Locale.FRENCH;
		Locale americanHebrew = Locale.forLanguageTag("he-US");
		Locale britishHebrew = Locale.forLanguageTag("he-GB");
		Map<Locale, Set<LocalizedString>> localizedStrings =
				localizedStringsFor(french, americanHebrew, britishHebrew);
		Strings strings = Strings.withFallbackLocale(french)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"he", List.of(americanHebrew, britishHebrew)))
				.build();
		String header = "he;q=1,fr;q=0.8,he-IL;q=0.5";

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

		assertEquals(britishHebrew, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(britishHebrew, strings.bestMatchForAcceptLanguage(header));
		assertEquals("he", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void parserAddedAliasCannotEvadeItsExactAnchor() {
		Locale french = Locale.FRENCH;
		Locale israeliHebrew = Locale.forLanguageTag("he-IL");
		Locale americanHebrew = Locale.forLanguageTag("he-US");
		Map<Locale, Set<LocalizedString>> localizedStrings =
				localizedStringsFor(french, israeliHebrew, americanHebrew);
		Strings strings = Strings.withFallbackLocale(french)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> french)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"he", List.of(israeliHebrew, americanHebrew)))
				.build();
		String header = "he;q=1,fr;q=0.8,iw-IL;q=0.5";

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

		assertEquals(americanHebrew, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(americanHebrew, strings.bestMatchForAcceptLanguage(header));
		assertEquals("he", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void parserAddedRegionalExtlangAliasRetainsItsExactAnchor() {
		Locale french = Locale.FRENCH;
		Locale easternMinChinese = Locale.forLanguageTag("cdo-CN");
		Locale simplifiedChinese = Locale.forLanguageTag("zh-CN");
		Strings strings = stringsForLocales(french, easternMinChinese, simplifiedChinese);
		String header = "zh-cdo-CN;q=1,fr;q=0.8";

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

		assertEquals(easternMinChinese, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(easternMinChinese, strings.bestMatchForAcceptLanguage(header));
		assertEquals("cdo-cn", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXACT, result.getMatchType());
	}

	@Test
	public void parserAddedBareExtlangAliasOwnsHeuristicMatches() {
		Locale french = Locale.FRENCH;
		Locale moroccanArabic = Locale.forLanguageTag("ary-MA");
		Locale egyptianArabic = Locale.forLanguageTag("ar-EG");
		Strings strings = stringsForLocales(french, moroccanArabic, egyptianArabic);
		String header = "ar-ary;q=1,fr;q=0.8";

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

		assertEquals(moroccanArabic, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(moroccanArabic, strings.bestMatchForAcceptLanguage(header));
		assertEquals("ary", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void programmaticBareExtlangRangeUsesItsSemanticAlias() {
		Locale french = Locale.FRENCH;
		Locale moroccanArabic = Locale.forLanguageTag("ary-MA");
		Locale egyptianArabic = Locale.forLanguageTag("ar-EG");
		Strings strings = stringsForLocales(french, moroccanArabic, egyptianArabic);
		LanguageRange extlangRange = new LanguageRange("ar-ary", 1.0);
		LanguageRange frenchRange = new LanguageRange("fr", 0.1);
		List<List<LanguageRange>> requests = List.of(
				List.of(extlangRange, frenchRange),
				List.of(extlangRange, new LanguageRange("ary", 0.5), frenchRange));

		for (List<LanguageRange> request : requests) {
			LocaleMatchResult result = strings.matchFor(request);

			assertEquals(moroccanArabic,
					result.getLocale().orElseThrow(AssertionError::new), request.toString());
			assertEquals(moroccanArabic, strings.bestMatchFor(request), request.toString());
			assertEquals("ar-ary",
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(),
					request.toString());
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), request.toString());
			assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType(), request.toString());
		}
	}

	@Test
	public void interleavedSemanticAliasRetainsRepresentativeGroupPriority() {
		Locale french = Locale.FRENCH;
		List<Locale> expectedLocales = List.of(
				Locale.forLanguageTag("ary"),
				Locale.forLanguageTag("ary-MA"));
		List<LocaleMatchType> expectedMatchTypes = List.of(
				LocaleMatchType.EXACT,
				LocaleMatchType.LIKELY_SUBTAG);

		for (int index = 0; index < expectedLocales.size(); ++index) {
			Locale expectedLocale = expectedLocales.get(index);
			Strings strings = stringsForLocales(french, expectedLocale);
			List<LanguageRange> request = List.of(
					new LanguageRange("ar-ary", 1.0),
					new LanguageRange("fr", 1.0),
					new LanguageRange("ary", 1.0));
			LocaleMatchResult result = strings.matchFor(request);

			assertEquals(expectedLocale,
					result.getLocale().orElseThrow(AssertionError::new), expectedLocale.toString());
			assertEquals(expectedLocale, strings.bestMatchFor(request), expectedLocale.toString());
			assertEquals("ary",
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(),
					expectedLocale.toString());
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), expectedLocale.toString());
			assertEquals(expectedMatchTypes.get(index), result.getMatchType(), expectedLocale.toString());
		}
	}

	@Test
	public void programmaticRegionalExtlangRangeUsesItsSemanticAlias() {
		Locale french = Locale.FRENCH;
		Locale easternMinChinese = Locale.forLanguageTag("cdo-CN");
		Locale simplifiedChinese = Locale.forLanguageTag("zh-CN");
		Strings strings = stringsForLocales(french, easternMinChinese, simplifiedChinese);
		List<LanguageRange> request = List.of(
				new LanguageRange("zh-cdo-CN", 1.0),
				new LanguageRange("fr", 0.1));

		LocaleMatchResult result = strings.matchFor(request);

		assertEquals(easternMinChinese, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(easternMinChinese, strings.bestMatchFor(request));
		assertEquals("zh-cdo-cn",
				result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.CANONICAL, result.getMatchType());
	}

	@Test
	public void programmaticSignExtlangRangeUsesItsSemanticAlias() {
		Locale french = Locale.FRENCH;
		Locale norwegianSignLanguage = Locale.forLanguageTag("nsl-SE");
		Locale nigerianSignLanguage = Locale.forLanguageTag("nsi-NG");
		Strings strings = stringsForLocales(
				french, norwegianSignLanguage, nigerianSignLanguage);
		LanguageRange extlangRange = new LanguageRange("sgn-nsl", 1.0);
		LanguageRange frenchRange = new LanguageRange("fr", 0.1);
		List<List<LanguageRange>> requests = List.of(
				List.of(extlangRange, frenchRange),
				List.of(extlangRange, new LanguageRange("sgn-no", 1.0), frenchRange));

		for (List<LanguageRange> request : requests) {
			LocaleMatchResult result = strings.matchFor(request);

			assertEquals(norwegianSignLanguage,
					result.getLocale().orElseThrow(AssertionError::new), request.toString());
			assertEquals(norwegianSignLanguage, strings.bestMatchFor(request), request.toString());
			assertEquals("sgn-nsl",
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(),
					request.toString());
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), request.toString());
			assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType(), request.toString());
		}
	}

	@Test
	public void semanticCanonicalIdentityGroupsLowerWeightProgrammaticAlias() {
		Locale french = Locale.FRENCH;
		Locale egyptianArabic = Locale.forLanguageTag("ar-EG");
		Strings strings = stringsForLocales(french, egyptianArabic);
		List<LanguageRange> request = List.of(
				new LanguageRange("ar-arb", 1.0),
				new LanguageRange("ar", 0.0),
				new LanguageRange("fr", 0.8));

		LocaleMatchResult result = strings.matchFor(request);

		assertEquals(egyptianArabic, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(egyptianArabic, strings.bestMatchFor(request));
		assertEquals("ar-arb",
				result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void programmaticAliasUsesParserExpansionInsteadOfLossyLocaleTag() {
		Locale french = Locale.FRENCH;
		List<String> requestedRanges = List.of("sgn-be-fx", "sgn-ch-dd");
		List<Locale> expectedLocales = List.of(
				Locale.forLanguageTag("sfb"),
				Locale.forLanguageTag("sgg"));
		List<Locale> lossyLocales = List.of(
				Locale.forLanguageTag("sgn-BE"),
				Locale.forLanguageTag("sgn-CH"));

		for (int index = 0; index < requestedRanges.size(); ++index) {
			String requestedRange = requestedRanges.get(index);
			Locale expectedLocale = expectedLocales.get(index);
			Strings strings = stringsForLocales(
					french, expectedLocale, lossyLocales.get(index));
			List<LanguageRange> request = List.of(
					new LanguageRange(requestedRange, 1.0),
					new LanguageRange("fr", 0.1));

			LocaleMatchResult result = strings.matchFor(request);

			assertEquals(expectedLocale,
					result.getLocale().orElseThrow(AssertionError::new), requestedRange);
			assertEquals(expectedLocale, strings.bestMatchFor(request), requestedRange);
			assertEquals(requestedRange,
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(),
					requestedRange);
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), requestedRange);
			assertEquals(LocaleMatchType.CANONICAL, result.getMatchType(), requestedRange);
		}
	}

	@Test
	public void malformedProgrammaticRangeDoesNotManufactureExclusion() {
		Locale fallback = Locale.forLanguageTag("zz-US");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(fallback, french);
		List<LanguageRange> request = List.of(
				new LanguageRange("*", 1.0),
				new LanguageRange("zz-a", 0.0));

		LocaleMatchResult result = strings.matchFor(request);

		assertEquals(fallback, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(fallback, strings.bestMatchFor(request));
		assertEquals("*",
				result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(1.0), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.WILDCARD, result.getMatchType());
	}

	@Test
	public void parserAliasUsesStableSemanticMemberAcrossRegistryConflict() {
		Locale french = Locale.FRENCH;
		Locale norwegianSignLanguage = Locale.forLanguageTag("nsl-SE");
		Locale nigerianSignLanguage = Locale.forLanguageTag("nsi-NG");
		Strings strings = stringsForLocales(
				french, norwegianSignLanguage, nigerianSignLanguage);
		List<String> headers = List.of(
				"nsl;q=1,fr;q=0.8",
				"sgn-nsl;q=1,fr;q=0.8");

		for (String header : headers) {
			LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

			assertEquals(norwegianSignLanguage,
					result.getLocale().orElseThrow(AssertionError::new), header);
			assertEquals(norwegianSignLanguage, strings.bestMatchForAcceptLanguage(header), header);
			assertEquals("nsl",
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(), header);
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), header);
			assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType(), header);
		}
	}

	@Test
	public void parserAddedExactAliasPreventsHeuristicSiblingSpill() {
		Locale french = Locale.FRENCH;
		Locale norwegianSignLanguage = Locale.forLanguageTag("nsl-SE");
		Locale exactParserAlias = Locale.forLanguageTag("sgn-NO");
		Strings strings = stringsForLocales(
				french, norwegianSignLanguage, exactParserAlias);
		List<String> headers = List.of(
				"nsl;q=1,fr;q=0.1",
				"sgn-nsl;q=1,fr;q=0.1");

		for (String header : headers) {
			LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

			assertEquals(exactParserAlias,
					result.getLocale().orElseThrow(AssertionError::new), header);
			assertEquals(exactParserAlias, strings.bestMatchForAcceptLanguage(header), header);
			assertEquals("sgn-no",
					result.getLanguageRange().orElseThrow(AssertionError::new).getRange(), header);
			assertEquals(Double.valueOf(1.0),
					result.getEffectiveWeight().orElseThrow(AssertionError::new), header);
			assertEquals(LocaleMatchType.EXACT, result.getMatchType(), header);
		}
	}

	@Test
	public void parserAliasClosureDoesNotAbsorbDistinctCldrCanonicalRange() {
		Locale french = Locale.FRENCH;
		Locale nigerianSignLanguage = Locale.forLanguageTag("nsi-NG");
		Strings strings = stringsForLocales(french, nigerianSignLanguage);
		String header = "nsl;q=1,nsi;q=0.5,fr;q=0.4";

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse(header));

		assertEquals(nigerianSignLanguage,
				result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(nigerianSignLanguage, strings.bestMatchForAcceptLanguage(header));
		assertEquals("nsi", result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, result.getMatchType());
	}

	@Test
	public void explicitChineseScriptRangeOutranksInferredRegionalRelationship() {
		Locale english = Locale.ENGLISH;
		Locale simplifiedChinese = Locale.forLanguageTag("zh-Hans");
		Locale traditionalHongKongChinese = Locale.forLanguageTag("zh-Hant-HK");
		Strings strings = dualScriptChineseStrings(
				english, simplifiedChinese, traditionalHongKongChinese);

		LocaleMatchResult result = strings.matchFor(
				LanguageRange.parse("zh-TW;q=1,zh-Hant-*;q=0.5,en;q=0.4"));

		assertEquals(traditionalHongKongChinese,
				result.getLocale().orElseThrow(AssertionError::new));
		assertEquals("zh-hant-*",
				result.getLanguageRange().orElseThrow(AssertionError::new).getRange());
		assertEquals(Double.valueOf(0.5),
				result.getEffectiveWeight().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.EXTENDED_RANGE, result.getMatchType());
	}

	@Test
	public void tiebreakerOrderControlsOtherwiseEquivalentLikelySubtagCandidates() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Locale canadianEnglish = Locale.forLanguageTag("en-CA");
		Map<Locale, Set<LocalizedString>> localizedStrings = localizedStringsFor(
				americanEnglish, britishEnglish);
		Strings preferAmerica = Strings.withFallbackLocale(americanEnglish)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> americanEnglish)
				.tiebreakerLocalesByLanguageCode(Map.of("en", List.of(americanEnglish, britishEnglish)))
				.build();
		Strings preferBritain = Strings.withFallbackLocale(americanEnglish)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> americanEnglish)
				.tiebreakerLocalesByLanguageCode(Map.of("en", List.of(britishEnglish, americanEnglish)))
				.build();

		LocaleMatchResult americanMatch = preferAmerica.matchFor(canadianEnglish);
		LocaleMatchResult britishMatch = preferBritain.matchFor(canadianEnglish);

		assertEquals(americanEnglish, americanMatch.getLocale().orElseThrow(AssertionError::new));
		assertEquals(britishEnglish, britishMatch.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, americanMatch.getMatchType());
		assertEquals(LocaleMatchType.LIKELY_SUBTAG, britishMatch.getMatchType());
	}

	@Test
	public void longNonmatchingZeroWeightRangeCannotAcquireExclusionAuthority() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale britishEnglish = Locale.forLanguageTag("en-GB");
		Strings strings = englishStrings(americanEnglish, britishEnglish);
		LanguageRange longNonmatchingRange = new LanguageRange(
				rangeWithRepeatedSubtags("en-US", 1_000), 0.0);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("en"), longNonmatchingRange));

		assertEquals(americanEnglish, result.getLocale().orElseThrow(AssertionError::new));
	}

	@Test
	public void veryLongNonmatchingZeroWeightRangeCannotExcludeAnEntireLanguage() {
		Locale english = Locale.ENGLISH;
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, english);
		LanguageRange longNonmatchingRange = new LanguageRange(
				rangeWithRepeatedSubtags("en", 2_100), 0.0);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("en"), longNonmatchingRange));

		assertEquals(english, result.getLocale().orElseThrow(AssertionError::new));
	}

	@Test
	public void matchCategoryOutranksSubtagCountWhenCalculatingEffectiveWeight() {
		Locale americanEnglish = Locale.forLanguageTag("en-US");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, americanEnglish);
		LanguageRange longNonmatchingRange = new LanguageRange(
				rangeWithRepeatedSubtags("en", 5_000), 1.0);
		LanguageRange exactRange = new LanguageRange("en-US", 0.5);

		LocaleMatchResult result = strings.matchFor(List.of(longNonmatchingRange, exactRange));

		assertEquals(americanEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
	}

	@Test
	public void structuralDepthStillBreaksTiesWithinAMatchCategory() {
		Locale posixEnglish = Locale.forLanguageTag("en-US-posix");
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, posixEnglish);

		LocaleMatchResult result = strings.matchFor(LanguageRange.parse("en;q=1,en-US;q=0.5"));

		assertEquals(posixEnglish, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(Double.valueOf(0.5), result.getEffectiveWeight().orElseThrow(AssertionError::new));
	}

	@Test
	public void undeterminedAvailableLocaleDoesNotMasqueradeAsEnglish() {
		Locale french = Locale.FRENCH;
		Strings strings = stringsForLocales(french, Locale.ROOT);

		LocaleMatchResult englishResult = strings.matchFor(List.of(new LanguageRange("en")));
		LocaleMatchResult weightedResult = strings.matchFor(LanguageRange.parse("en;q=1,fr;q=0.5"));

		assertFalse(englishResult.isMatch());
		assertEquals(LocaleMatchType.NONE, englishResult.getMatchType());
		assertEquals(french, strings.bestMatchFor(List.of(new LanguageRange("en"))));
		assertEquals(french, weightedResult.getLocale().orElseThrow(AssertionError::new));
	}

	@Test
	public void wildcardCanStillSelectAnUndeterminedFallbackLocale() {
		Strings strings = stringsForLocales(Locale.ROOT, Locale.FRENCH);

		LocaleMatchResult result = strings.matchFor(List.of(new LanguageRange("*")));

		assertEquals(Locale.ROOT, result.getLocale().orElseThrow(AssertionError::new));
		assertEquals(LocaleMatchType.WILDCARD, result.getMatchType());
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
  public void wildcardUsesFallbackLanguageTiebreakerAfterFallbackExclusion() {
    Locale americanEnglish = Locale.forLanguageTag("en-US");
    Locale australianEnglish = Locale.forLanguageTag("en-AU");
    Locale britishEnglish = Locale.forLanguageTag("en-GB");
    Locale french = Locale.forLanguageTag("fr");
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
    localizedStringsByLocale.put(americanEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("american").build()));
    localizedStringsByLocale.put(australianEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("australian").build()));
    localizedStringsByLocale.put(britishEnglish,
        Set.of(new LocalizedString.Builder("hello").translation("british").build()));
    localizedStringsByLocale.put(french,
        Set.of(new LocalizedString.Builder("hello").translation("french").build()));

    Strings strings = Strings.withFallbackLocale(americanEnglish)
        .localizedStringSupplier(() -> localizedStringsByLocale)
        .localeSupplier(matcher -> americanEnglish)
        .tiebreakerLocalesByLanguageCode(Map.of(
            "en", List.of(americanEnglish, britishEnglish, australianEnglish)))
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
		LocaleMatchResult strictResult = strings.matchFor(LanguageRange.parse("en;q=0"));
		assertFalse(strictResult.isMatch());
		assertEquals(LocaleMatchType.NONE, strictResult.getMatchType());
		assertEquals(english, strictResult.getFallbackLocale());
  }

	@Test
	public void languageRangesDoNotCrossKnownScriptBoundaries() {
		Locale english = Locale.ENGLISH;
		Locale genericChinese = Locale.forLanguageTag("zh");
		Strings strings = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(
						english, Set.of(new LocalizedString.Builder("hello").translation("English").build()),
						genericChinese, Set.of(new LocalizedString.Builder("hello").translation("Simplified Chinese").build())))
				.localeSupplier(matcher -> english)
				.build();

		Locale traditionalChinese = Locale.forLanguageTag("zh-TW");
		assertFalse(strings.matchFor(traditionalChinese).isMatch());
		assertEquals(english, strings.bestMatchFor(traditionalChinese));
		assertEquals("English", strings.get("hello", TranslationOptions.forLanguageRanges(
				LanguageRange.parse("zh-TW"))));
	}

	@Test
	public void zeroWeightCanonicalAliasesExcludeDescendants() {
		Locale german = Locale.GERMAN;
		Locale serbianLatin = Locale.forLanguageTag("sr-Latn-RS");
		Strings strings = Strings.withFallbackLocale(german)
				.localizedStringSupplier(() -> Map.of(
						german, Set.of(new LocalizedString.Builder("hello").translation("German").build()),
						serbianLatin, Set.of(new LocalizedString.Builder("hello").translation("Serbian Latin").build())))
				.localeSupplier(matcher -> german)
				.build();

		LocaleMatchResult matchResult = strings.matchFor(LanguageRange.parse("*;q=1,de;q=0,sh;q=0"));
		assertFalse(matchResult.isMatch());
		assertEquals(german, strings.bestMatchFor(LanguageRange.parse("*;q=1,de;q=0,sh;q=0")));
	}

  @Test
  public void excessiveLanguageRangeListsAreRejected() {
    Locale english = Locale.forLanguageTag("en");
    Strings strings = Strings.withFallbackLocale(english)
        .localizedStringSupplier(() -> Map.of(
            english, Set.of(new LocalizedString.Builder("hello").translation("hello").build())))
        .localeSupplier(matcher -> english)
        .build();

		assertEquals(Integer.valueOf(32), LocaleMatcher.MAXIMUM_LANGUAGE_RANGES);
		List<LanguageRange> maximumLanguageRanges = Collections.nCopies(LocaleMatcher.MAXIMUM_LANGUAGE_RANGES,
				new LanguageRange("en"));
		List<LanguageRange> excessiveLanguageRanges = Collections.nCopies(LocaleMatcher.MAXIMUM_LANGUAGE_RANGES + 1,
				new LanguageRange("en"));

		assertEquals(english, strings.bestMatchFor(maximumLanguageRanges));
		assertEquals(english, strings.matchFor(maximumLanguageRanges).getLocale().orElseThrow(AssertionError::new));
		assertEquals(maximumLanguageRanges, new LocaleMatchResult(maximumLanguageRanges, null, null, null,
				LocaleMatchType.NONE, english, List.of(english)).getRequestedLanguageRanges());
		assertThrows(IllegalArgumentException.class, () -> strings.bestMatchFor(excessiveLanguageRanges));
		assertThrows(IllegalArgumentException.class, () -> strings.matchFor(excessiveLanguageRanges));
		assertThrows(IllegalArgumentException.class, () -> new LocaleMatchResult(excessiveLanguageRanges, null,
				null, null, LocaleMatchType.NONE, english, List.of(english)));
  }

	@Test
	public void parsedLegacyAliasesCountTowardTheLanguageRangeLimit() {
		Locale english = Locale.ENGLISH;
		Strings strings = stringsForLocales(english);
		List<LanguageRange> expandedAtLimit = LanguageRange.parse(legacyHebrewRanges(16));
		List<LanguageRange> expandedOverLimit = LanguageRange.parse(legacyHebrewRanges(17));

		assertEquals(LocaleMatcher.MAXIMUM_LANGUAGE_RANGES.intValue(), expandedAtLimit.size());
		assertEquals(LocaleMatcher.MAXIMUM_LANGUAGE_RANGES + 2, expandedOverLimit.size());
		assertFalse(strings.matchFor(expandedAtLimit).isMatch());
		assertEquals(english, strings.bestMatchFor(expandedAtLimit));
		assertThrows(IllegalArgumentException.class, () -> strings.matchFor(expandedOverLimit));
		assertThrows(IllegalArgumentException.class, () -> strings.bestMatchFor(expandedOverLimit));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void malformedProgrammaticLocalesAreRejectedAtConfigurationAndLookupBoundaries() {
		Locale english = Locale.ENGLISH;
		Locale malformed = new Locale("e");
		Map<Locale, Set<LocalizedString>> malformedLocalizedStrings = new LinkedHashMap<>();
		malformedLocalizedStrings.put(english, localizedStringsFor(english));
		malformedLocalizedStrings.put(malformed, localizedStringsFor(malformed));

		assertThrows(IllegalArgumentException.class, () -> Strings.withFallbackLocale(malformed)
				.localizedStringSupplier(() -> Map.of(malformed, localizedStringsFor(malformed)))
				.localeSupplier(matcher -> malformed)
				.build());
		assertThrows(IllegalArgumentException.class, () -> Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> malformedLocalizedStrings)
				.localeSupplier(matcher -> english)
				.build());
		assertThrows(IllegalArgumentException.class, () -> Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, localizedStringsFor(english)))
				.localeSupplier(matcher -> english)
				.tiebreakerLocalesByLanguageCode(Map.of("en", List.of(malformed)))
				.build());

		Strings malformedSupplier = Strings.withFallbackLocale(english)
				.localizedStringSupplier(() -> Map.of(english, localizedStringsFor(english)))
				.localeSupplier(matcher -> malformed)
				.build();
		Strings strings = stringsForLocales(english);

		assertThrows(IllegalArgumentException.class, () -> malformedSupplier.get("hello"));
		assertThrows(IllegalArgumentException.class, () -> strings.matchFor(malformed));
		assertThrows(IllegalArgumentException.class, () -> strings.bestMatchFor(malformed));
		assertThrows(IllegalArgumentException.class, () -> strings.getKeysForLocale(malformed));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void supportedLocaleSetUsesLocaleEqualityAndDuplicateLanguageTagsAreRejected() {
		Locale undetermined = new Locale("und");
		Strings rootStrings = stringsForLocales(Locale.ROOT);

		assertEquals(Set.of(Locale.ROOT), rootStrings.getSupportedLocales());
		assertFalse(rootStrings.getSupportedLocales().contains(undetermined));

		Map<Locale, Set<LocalizedString>> duplicateTags = new LinkedHashMap<>();
		duplicateTags.put(Locale.ROOT, localizedStringsFor(Locale.ROOT));
		duplicateTags.put(undetermined, localizedStringsFor(undetermined));

		assertThrows(IllegalArgumentException.class, () -> Strings.withFallbackLocale(Locale.ROOT)
				.localizedStringSupplier(() -> duplicateTags)
				.localeSupplier(matcher -> Locale.ROOT)
				.build());
		assertThrows(IllegalArgumentException.class, () -> new LocaleMatchResult(Collections.emptyList(), null,
				null, null, LocaleMatchType.NONE, Locale.ROOT, List.of(Locale.ROOT, undetermined)));
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

	private Strings stringsForLocales(Locale fallbackLocale, Locale... otherLocales) {
		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = localizedStringsFor(fallbackLocale, otherLocales);
		return Strings.withFallbackLocale(fallbackLocale)
				.localizedStringSupplier(() -> localizedStringsByLocale)
				.localeSupplier(matcher -> fallbackLocale)
				.build();
	}

	private Strings dualScriptChineseStrings(Locale fallbackLocale, Locale simplifiedChinese,
																					 Locale traditionalChinese) {
		Map<Locale, Set<LocalizedString>> localizedStrings =
				localizedStringsFor(fallbackLocale, simplifiedChinese, traditionalChinese);
		return Strings.withFallbackLocale(fallbackLocale)
				.localizedStringSupplier(() -> localizedStrings)
				.localeSupplier(matcher -> fallbackLocale)
				.tiebreakerLocalesByLanguageCode(Map.of(
						"zh", List.of(simplifiedChinese, traditionalChinese)))
				.build();
	}

	private Map<Locale, Set<LocalizedString>> localizedStringsFor(Locale firstLocale, Locale... otherLocales) {
		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new LinkedHashMap<>();
		localizedStringsByLocale.put(firstLocale, localizedStringsFor(firstLocale));

		for (Locale locale : otherLocales)
			localizedStringsByLocale.put(locale, localizedStringsFor(locale));

		return localizedStringsByLocale;
	}

	private Set<LocalizedString> localizedStringsFor(Locale locale) {
		return Set.of(new LocalizedString.Builder("hello").translation(locale.toLanguageTag()).build());
	}

	private String rangeWithRepeatedSubtags(String prefix, int subtagCount) {
		StringBuilder range = new StringBuilder(prefix);

		for (int index = 0; index < subtagCount; ++index)
			range.append("-a");

		return range.toString();
	}

	private String legacyHebrewRanges(int explicitRangeCount) {
		StringBuilder ranges = new StringBuilder();

		for (int index = 0; index < explicitRangeCount; ++index) {
			if (index > 0)
				ranges.append(',');
			ranges.append("iw-x").append(index);
		}

		return ranges.toString();
	}
}
