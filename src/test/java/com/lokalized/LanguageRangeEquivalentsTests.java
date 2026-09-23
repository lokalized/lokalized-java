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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link LanguageRangeEquivalents}: the default, what {@code null} means, and that one setting governs every place
 * a {@link Strings} instance takes IANA equivalents from.
 * <p>
 * The discriminating tags are {@code yol} and {@code mrd}. The registry snapshot bundled in Lokalized makes them
 * equivalent to {@code enm} and {@code mgp}; measured, the tables bundled in JDK 17 and 21 know neither pair and JDK
 * 27's knows {@code yol} but not {@code mrd}. Assertions about the {@code JDK} setting are therefore stated against what
 * {@link LanguageRange#parse(String)} says on the JDK running the suite, which makes a pair discriminating on exactly
 * the JDKs that lack it. On a JDK whose table knows both pairs the two settings cannot be told apart through them.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class LanguageRangeEquivalentsTests {
	private static final Locale ENGLISH = Locale.forLanguageTag("en");
	private static final Locale MIDDLE_ENGLISH = Locale.forLanguageTag("enm");
	private static final Locale MAGAR = Locale.forLanguageTag("mgp");
	/** Each discriminating tag, and the supported locale the bundled registry makes it equivalent to. */
	private static final Map<String, Locale> REGISTRY_ONLY_EQUIVALENTS = Map.of("yol", MIDDLE_ENGLISH, "mrd", MAGAR);
	private static final List<String> HEADERS = List.of(
			"yol", "enm", "mrd", "mgp", "iw", "he", "zh-cmn", "sgn-be-fr", "de-DE", "en-US,fr;q=0.5", "he,id,yi",
			"Accept-Language: nsl;q=0.3, de-DD", "i-klingon", "*", "x-private", "fr-FX-heploc;q=0.2,bh");

	@Test
	public void theDefaultIsTheBundledRegistry() {
		DefaultStrings strings = (DefaultStrings) unconfiguredStrings();

		assertSame(LanguageRangeEquivalents.IANA_REGISTRY, strings.getLanguageRangeEquivalents());
		assertEquals(IanaLanguageEquivalents.parse("yol"), strings.parseLanguageRanges("yol"));
	}

	@Test
	public void nullMeansTheDefault() {
		assertSame(LanguageRangeEquivalents.IANA_REGISTRY, ((DefaultStrings) strings(null)).getLanguageRangeEquivalents());

		Strings reset = builder()
				.languageRangeEquivalents(LanguageRangeEquivalents.JDK)
				.languageRangeEquivalents(null)
				.build();

		assertSame(LanguageRangeEquivalents.IANA_REGISTRY, ((DefaultStrings) reset).getLanguageRangeEquivalents());
	}

	@Test
	public void theJdkSettingIsStored() {
		assertSame(LanguageRangeEquivalents.JDK,
				((DefaultStrings) strings(LanguageRangeEquivalents.JDK)).getLanguageRangeEquivalents());
	}

	@Test
	public void parseLanguageRangesUsesTheConfiguredTable() {
		Strings registry = strings(LanguageRangeEquivalents.IANA_REGISTRY);
		Strings jdk = strings(LanguageRangeEquivalents.JDK);
		Strings unconfigured = unconfiguredStrings();

		for (String header : HEADERS) {
			assertEquals(IanaLanguageEquivalents.parse(header), registry.parseLanguageRanges(header), header);
			assertEquals(IanaLanguageEquivalents.parse(header), unconfigured.parseLanguageRanges(header), header);
			assertEquals(LanguageRange.parse(header), jdk.parseLanguageRanges(header), header);
		}

		for (Map.Entry<String, Locale> pair : REGISTRY_ONLY_EQUIVALENTS.entrySet()) {
			String tag = pair.getKey(), equivalent = pair.getValue().toLanguageTag();
			assertEquals(List.of(tag, equivalent), rangesOf(registry.parseLanguageRanges(tag)));
			assertEquals(jdkKnows(tag, equivalent) ? List.of(tag, equivalent) : List.of(tag),
					rangesOf(jdk.parseLanguageRanges(tag)));
		}
	}

	@Test
	public void theInterfaceDefaultUsesTheBundledRegistry() {
		RecordingLocaleMatcher matcher = new RecordingLocaleMatcher();

		for (String header : HEADERS)
			assertEquals(IanaLanguageEquivalents.parse(header), matcher.parseLanguageRanges(header), header);
	}

	@Test
	public void bestMatchForAcceptLanguageParsesThroughParseLanguageRanges() {
		// The default method must dispatch to parseLanguageRanges rather than to a parser of its own, or an
		// implementation's override (DefaultStrings' is how the builder setting takes effect) would be bypassed.
		RecordingLocaleMatcher defaultParser = new RecordingLocaleMatcher();
		defaultParser.bestMatchForAcceptLanguage("yol");
		assertEquals(List.of(IanaLanguageEquivalents.parse("yol")), defaultParser.received);

		List<LanguageRange> sentinel = List.of(new LanguageRange("zu"));
		RecordingLocaleMatcher overridingParser = new RecordingLocaleMatcher() {
			@Override
			public List<LanguageRange> parseLanguageRanges(String ranges) {
				return sentinel;
			}
		};
		overridingParser.bestMatchForAcceptLanguage("yol");
		assertEquals(List.of(sentinel), overridingParser.received);
	}

	@Test
	public void parseLanguageRangesKeepsLanguageRangeParseExceptions() {
		for (Strings strings : List.of(strings(LanguageRangeEquivalents.IANA_REGISTRY), strings(LanguageRangeEquivalents.JDK))) {
			assertThrows(NullPointerException.class, () -> strings.parseLanguageRanges(null));
			assertThrows(IllegalArgumentException.class, () -> strings.parseLanguageRanges(""));
			assertThrows(IllegalArgumentException.class, () -> strings.parseLanguageRanges("en;q=2"));
			assertThrows(IllegalArgumentException.class, () -> strings.parseLanguageRanges("en;q=x"));
			assertThrows(IllegalArgumentException.class, () -> strings.parseLanguageRanges("en,,fr"));
			assertThrows(IllegalArgumentException.class, () -> strings.parseLanguageRanges("en,\tfr"));
			assertThrows(IllegalArgumentException.class, () -> strings.parseLanguageRanges("en-abcdefghi"));
			// A hyphen-only range fails inside LanguageRange's constructor, and HOW depends on the JDK: an
			// ArrayIndexOutOfBoundsException on JDK 17 and 21, an IllegalArgumentException on JDK 25 through 27. Either
			// way both settings must fail exactly as LanguageRange.parse does on the running JDK.
			Class<? extends RuntimeException> hyphenOnly = failureOf(() -> LanguageRange.parse("-"));
			assertTrue(hyphenOnly == ArrayIndexOutOfBoundsException.class || hyphenOnly == IllegalArgumentException.class,
					hyphenOnly.getName());
			assertEquals(hyphenOnly, failureOf(() -> strings.parseLanguageRanges("-")));
			assertEquals(hyphenOnly, failureOf(() -> strings.parseLanguageRanges("en,--")));
			assertThrows(UnsupportedOperationException.class,
					() -> strings.parseLanguageRanges("fr").add(new LanguageRange("de")));
		}
	}

	@Test
	public void bestMatchForAcceptLanguageHonoursTheSetting() {
		for (Map.Entry<String, Locale> pair : REGISTRY_ONLY_EQUIVALENTS.entrySet()) {
			String tag = pair.getKey();
			Locale equivalent = pair.getValue();

			assertEquals(equivalent, unconfiguredStrings().bestMatchForAcceptLanguage(tag), tag);
			assertEquals(equivalent, strings(LanguageRangeEquivalents.IANA_REGISTRY).bestMatchForAcceptLanguage(tag), tag);
			assertEquals(jdkKnows(tag, equivalent.toLanguageTag()) ? equivalent : ENGLISH,
					strings(LanguageRangeEquivalents.JDK).bestMatchForAcceptLanguage(tag), tag);
		}
	}

	@Test
	public void matchingAnUnparsedRangeListHonoursTheSetting() {
		// A range list the caller built without parsing carries no equivalents, so any match on enm here comes from the
		// identities DefaultStrings derives for each requested range — the second internal consumer of the table.
		Strings registry = strings(LanguageRangeEquivalents.IANA_REGISTRY);
		Strings jdk = strings(LanguageRangeEquivalents.JDK);

		for (Map.Entry<String, Locale> pair : REGISTRY_ONLY_EQUIVALENTS.entrySet()) {
			String tag = pair.getKey();
			Locale equivalent = pair.getValue();
			List<LanguageRange> ranges = List.of(new LanguageRange(tag));

			LocaleMatchResult registryMatch = registry.matchFor(ranges);
			assertEquals(Optional.of(equivalent), registryMatch.getLocale(), tag);
			assertEquals(LocaleMatchType.CANONICAL, registryMatch.getMatchType(), tag);
			assertEquals(equivalent, registry.bestMatchFor(ranges), tag);
			assertEquals(equivalent.toLanguageTag(),
					registry.getResult("hello", TranslationOptions.forLanguageRanges(ranges)).getTranslation(), tag);

			LocaleMatchResult jdkMatch = jdk.matchFor(ranges);

			if (jdkKnows(tag, equivalent.toLanguageTag())) {
				assertEquals(Optional.of(equivalent), jdkMatch.getLocale(), tag);
			} else {
				assertFalse(jdkMatch.getLocale().isPresent(), tag);
				assertEquals(ENGLISH, jdk.bestMatchFor(ranges), tag);
				assertEquals("en", jdk.getResult("hello", TranslationOptions.forLanguageRanges(ranges)).getTranslation(), tag);
			}
		}
	}

	@Test
	public void theExtlangFallbackHonoursTheSetting() {
		// ar-yol parses to itself under both settings, and only the extlang fallback's SECOND parse (it reads
		// the range as extlang yol) reaches enm — a route the tests above never take. Ignoring the setting
		// there, in either direction, left all 574 tests green while the default instance's answer moved
		// on 33 of 115,491 probes.
		Strings registry = strings(LanguageRangeEquivalents.IANA_REGISTRY);
		Strings jdk = strings(LanguageRangeEquivalents.JDK);

		for (Map.Entry<String, Locale> pair : REGISTRY_ONLY_EQUIVALENTS.entrySet()) {
			String tag = pair.getKey();
			Locale equivalent = pair.getValue();
			List<LanguageRange> ranges = List.of(new LanguageRange("ar-" + tag));

			assertEquals(Optional.of(equivalent), registry.matchFor(ranges).getLocale(), tag);
			assertEquals(equivalent, registry.bestMatchForAcceptLanguage("ar-" + tag), tag);
			assertEquals(jdkKnows(tag, equivalent.toLanguageTag()) ? Optional.of(equivalent) : Optional.empty(),
					jdk.matchFor(ranges).getLocale(), tag);
		}
	}

	@Test
	public void theDocumentationNamesTheBundledSnapshot() throws IOException {
		// The enum and the README are written by hand while the snapshot is generated; this keeps the File-Date and
		// digest they state from going stale when the snapshot is replaced.
		String enumSource = read("src/main/java/com/lokalized/LanguageRangeEquivalents.java");
		String readme = read("README.md");

		assertTrue(enumSource.contains("File-Date: " + IanaLanguageEquivalents.REGISTRY_FILE_DATE),
				"LanguageRangeEquivalents' Javadoc must name the bundled File-Date " + IanaLanguageEquivalents.REGISTRY_FILE_DATE);
		assertTrue(enumSource.contains(IanaLanguageEquivalents.REGISTRY_SHA256),
				"LanguageRangeEquivalents' Javadoc must name the bundled digest " + IanaLanguageEquivalents.REGISTRY_SHA256);
		assertTrue(readme.contains("(`File-Date: " + IanaLanguageEquivalents.REGISTRY_FILE_DATE + "`)"),
				"README.md must name the bundled File-Date " + IanaLanguageEquivalents.REGISTRY_FILE_DATE);
	}

	private static String read(String path) throws IOException {
		return new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir"), path)), StandardCharsets.UTF_8);
	}

	private static Class<? extends RuntimeException> failureOf(Runnable runnable) {
		try {
			runnable.run();
		} catch (RuntimeException exception) {
			return exception.getClass();
		}

		throw new AssertionError("expected a failure");
	}

	/** Whether the running JDK's own table makes {@code tag} equivalent to {@code equivalent}. */
	private static boolean jdkKnows(String tag, String equivalent) {
		return rangesOf(LanguageRange.parse(tag)).contains(equivalent);
	}

	private static List<String> rangesOf(List<LanguageRange> languageRanges) {
		List<String> ranges = new ArrayList<>(languageRanges.size());

		for (LanguageRange languageRange : languageRanges)
			ranges.add(languageRange.getRange());

		return ranges;
	}

	/** Supported {@code en}, {@code enm} and {@code mgp}, falling back to {@code en}, built with the given setting (which may be null). */
	private static Strings strings(LanguageRangeEquivalents languageRangeEquivalents) {
		return builder().languageRangeEquivalents(languageRangeEquivalents).build();
	}

	/** The same instance, built without calling the builder method at all. */
	private static Strings unconfiguredStrings() {
		return builder().build();
	}

	private static Strings.Builder builder() {
		return Strings.withFallbackLocale(ENGLISH)
				.localizedStringSupplier(LanguageRangeEquivalentsTests::catalogs)
				.localeSupplier(matcher -> ENGLISH);
	}

	private static Map<Locale, Set<LocalizedString>> catalogs() {
		Map<Locale, Set<LocalizedString>> catalogs = new LinkedHashMap<>();

		for (Locale locale : List.of(ENGLISH, MIDDLE_ENGLISH, MAGAR))
			catalogs.put(locale, Set.of(new LocalizedString.Builder("hello").translation(locale.toLanguageTag()).build()));

		return catalogs;
	}

	/** A minimal matcher that inherits the interface defaults and records the ranges they hand on. */
	private static class RecordingLocaleMatcher implements LocaleMatcher {
		private final List<List<LanguageRange>> received = new ArrayList<>();

		@Override
		public LocaleMatchResult matchFor(List<LanguageRange> languageRanges) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Locale bestMatchFor(Locale locale) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Locale bestMatchFor(List<LanguageRange> languageRanges) {
			received.add(languageRanges);
			return Locale.ROOT;
		}
	}
}
