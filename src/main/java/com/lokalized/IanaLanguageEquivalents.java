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
 *
 * This generated source contains data derived from the IANA Language Subtag Registry
 * (File-Date 2026-09-17). See THIRD-PARTY-NOTICES.md.
 */

package com.lokalized;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * IANA language-range equivalences, sourced from a pinned Language Subtag Registry snapshot rather
 * than from the running JDK.
 * <p>
 * <strong>Why this exists.</strong> {@link LanguageRange#parse(String)} expands a range using an
 * equivalence table baked into the JDK, and that table moves between JDK releases. Measured: the
 * same unmodified Lokalized jar answers {@code Accept-Language: yol} with the {@code en} catalog on
 * a JDK whose bundled registry is stamped 2025-05-15 and with the {@code enm} catalog on one
 * stamped 2026-05-05. A library whose selected catalog depends on the consumer's JVM is not
 * reproducible, and no existing test could see it, because every test runs on a single JDK.
 * <p>
 * This pins the LANGUAGE half of that table to registry {@code File-Date: 2026-09-17} —
 * 781 keys across 369 equivalence classes — so the answer is a function of the
 * library rather than of the platform. It is what {@link LanguageRangeEquivalents#IANA_REGISTRY},
 * the default, selects; {@link LanguageRangeEquivalents#JDK} bypasses it for the running JDK's
 * {@code LanguageRange#parse(String)}.
 * <p>
 * Every datum below is generated from the registry snapshot by
 * {@code src/build/java/com/lokalized/iana/IanaEquivalencesGenerator.java}, which states the rules:
 * class membership from {@code Preferred-Value} and extlang records, each class's member order from
 * which member has no {@code Preferred-Value} and the record order of the rest, and the region/variant
 * substitutions from the registry's region and variant records.
 * <p>
 * <strong>What it deliberately does not change.</strong> Region and variant substitution keeps the
 * JDK's semantics, including the order JDK 21 tries the substitutions in. That behaviour is a house
 * dialect rather than registry content — it expands {@code de-DE} to {@code de-dd}, resurrecting a
 * region deprecated in 1990, and the registry states no such mapping — but it is long-standing
 * observable behaviour and is inert in practice, so changing it would move answers for existing
 * callers to no measurable benefit. Pinning the ORDER is itself a correction: which substitution
 * applies when two are applicable was previously decided by {@code java.util.HashMap} iteration order.
 */
final class IanaLanguageEquivalents {
	/** The pinned registry snapshot this table was generated from. */
	static final String REGISTRY_FILE_DATE = "2026-09-17";

	/**
	 * The SHA-256 of that snapshot's bytes.
	 * <p>
	 * A {@code File-Date} names a registry RELEASE; it does not identify the bytes. Two fetches
	 * stamped with the same date, or a snapshot edited after fetching, are indistinguishable by
	 * date alone — so the digest travels beside it and downstream consumers of this table can
	 * record which snapshot it actually came from rather than which one it claims.
	 */
	static final String REGISTRY_SHA256 = "755fad43283be7b41ebe3c89ad054b6eaf928f404f9c0edb74799e0eab74beb1";

	/**
	 * Lowercased subtag to the OTHER members of its equivalence class, from the pinned registry.
	 * Generated; do not edit by hand.
	 * <p>
	 * Filled by plain {@code put} calls in the static initializer rather than one
	 * {@code Map.ofEntries(...)} expression. The table is the same either way; the difference is
	 * javac's: type inference over a single varargs call with 781 generic arguments took
	 * minutes, and these calls take well under a second. The calls are split across several methods
	 * so no one method approaches the JVM's 64 KB code-size limit as the registry grows.
	 */
	private static final Map<String, List<String>> LANGUAGE_EQUIVALENTS;

	static {
		Map<String, List<String>> languageEquivalents = new HashMap<>(1042);
		putLanguageEquivalents0(languageEquivalents);
		putLanguageEquivalents1(languageEquivalents);
		putLanguageEquivalents2(languageEquivalents);
		putLanguageEquivalents3(languageEquivalents);
		LANGUAGE_EQUIVALENTS = Collections.unmodifiableMap(languageEquivalents);
	}

	/**
	 * The 14 region and variant substitutions, as {@code {from, to}} pairs in the order
	 * they are tried.
	 * <p>
	 * Generated from the registry's region and variant records that carry a {@code Preferred-Value},
	 * in both directions — the set the JDK keeps in
	 * {@code sun.util.locale.LocaleEquivalentMaps.regionVariantEquivMap}. The order is the one JDK 21's
	 * {@code HashMap} iterates that map in, computed by the generator rather than read from a JDK, and
	 * it matters: it decides which substitution applies when a range contains two.
	 */
	private static final List<String[]> REGION_VARIANT_EQUIVALENTS = List.of(
			new String[] { "-bu", "-mm" },
			new String[] { "-tl", "-tp" },
			new String[] { "-zr", "-cd" },
			new String[] { "-tp", "-tl" },
			new String[] { "-dd", "-de" },
			new String[] { "-mm", "-bu" },
			new String[] { "-cd", "-zr" },
			new String[] { "-de", "-dd" },
			new String[] { "-heploc", "-alalc97" },
			new String[] { "-alalc97", "-heploc" },
			new String[] { "-yd", "-ye" },
			new String[] { "-fr", "-fx" },
			new String[] { "-ye", "-yd" },
			new String[] { "-fx", "-fr" });

	/** {@code LanguageRange}'s MAX_WEIGHT and MIN_WEIGHT, which it does not expose. */
	private static final double MAXIMUM_WEIGHT = 1.0;
	private static final double MINIMUM_WEIGHT = 0.0;

	private IanaLanguageEquivalents() {}

	/**
	 * Parses an {@code Accept-Language} field value, expanding equivalences from the pinned registry.
	 * <p>
	 * This is {@code Locale.LanguageRange.parse}'s algorithm with ONE substitution: the language
	 * equivalence table. Everything else is reproduced because it is observable — the normalisation,
	 * Java's comma splitting (which drops trailing empty members and keeps interior ones), the
	 * {@code ;q=} weight grammar, the WEIGHT-ORDERED STABLE INSERTION, first-wins de-duplication, and
	 * the insertion of each derived range immediately after its original, which is why an equivalence
	 * class appears in reverse insertion order.
	 * <p>
	 * <strong>The range grammar and the weight bound stay the JDK's, deliberately.</strong>
	 * {@code new LanguageRange(range, weight)} validates both and expands NOTHING — verified rather
	 * than assumed: the constructor returns {@code iw} for {@code iw} and {@code de-de} for
	 * {@code de-DE}, where {@code parse} returns two ranges for each. Reimplementing the grammar
	 * would be a second thing to keep faithful for no benefit.
	 */
	static List<LanguageRange> parse(String ranges) {
		requireNonNull(ranges);

		String normalized = ranges.replace(" ", "").toLowerCase(Locale.ROOT);

		if (normalized.startsWith("accept-language:"))
			normalized = normalized.substring("accept-language:".length());

		List<LanguageRange> list = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();

		for (String member : normalized.split(",")) {
			String range = member;
			double weight = MAXIMUM_WEIGHT;
			int weightIndex = member.indexOf(";q=");

			if (weightIndex != -1) {
				range = member.substring(0, weightIndex);
				String text = member.substring(weightIndex + 3);

				try {
					weight = Double.parseDouble(text);
				} catch (NumberFormatException exception) {
					throw new IllegalArgumentException("weight=\"" + text + "\" for language range \"" + range + "\"");
				}

				if (weight < MINIMUM_WEIGHT || weight > MAXIMUM_WEIGHT)
					throw new IllegalArgumentException("weight=" + weight + " for language range \"" + range
							+ "\". It must be between " + MINIMUM_WEIGHT + " and " + MAXIMUM_WEIGHT + ".");
			}

			if (seen.contains(range))
				continue;

			// The JDK's own grammar check and its own refusal, without its equivalence table.
			LanguageRange parsed = new LanguageRange(range, weight);

			int index = list.size();

			for (int position = 0; position < list.size(); ++position)
				if (list.get(position).getWeight() < weight) {
					index = position;
					break;
				}

			list.add(index, parsed);
			seen.add(range);

			for (String equivalent : expansionsFor(range))
				if (seen.add(equivalent))
					list.add(index + 1, new LanguageRange(equivalent, weight));
		}

		return Collections.unmodifiableList(list);
	}


	/** Insertion-ordered expansions for one range: the region/variant arm, then each language arm. */
	private static List<String> expansionsFor(String range) {
		List<String> expansions = new ArrayList<>();

		String regionVariant = equivalentForRegionAndVariant(range);
		if (regionVariant != null)
			expansions.add(regionVariant);

		for (String equivalent : equivalentsForLanguage(range)) {
			expansions.add(equivalent);
			String nested = equivalentForRegionAndVariant(equivalent);
			if (nested != null)
				expansions.add(nested);
		}

		return expansions;
	}

	/** The longest registry-known prefix of the range, with the remainder carried across. */
	private static List<String> equivalentsForLanguage(String range) {
		String prefix = range;

		while (!prefix.isEmpty()) {
			List<String> equivalenceClass = LANGUAGE_EQUIVALENTS.get(prefix);

			if (equivalenceClass != null) {
				String suffix = range.substring(prefix.length());
				List<String> equivalents = new ArrayList<>(equivalenceClass.size());

				for (String equivalent : equivalenceClass)
					equivalents.add(equivalent + suffix);

				return equivalents;
			}

			int index = prefix.lastIndexOf('-');
			if (index == -1)
				break;

			prefix = prefix.substring(0, index);
		}

		return List.of();
	}

	/**
	 * {@code sun.util.locale.LocaleMatcher#getEquivalentForRegionAndVariant}, reproduced including its
	 * substring semantics: the subtag may sit anywhere in the range provided it ends at the range's
	 * end or at a hyphen and is not inside a singleton extension.
	 */
	private static String equivalentForRegionAndVariant(String range) {
		int keyIndex = extensionKeyIndex(range);

		for (String[] pair : REGION_VARIANT_EQUIVALENTS) {
			int index = range.indexOf(pair[0]);
			if (index == -1)
				continue;
			if (keyIndex != Integer.MIN_VALUE && index > keyIndex)
				continue;

			int end = index + pair[0].length();
			if (range.length() == end || range.charAt(end) == '-')
				return range.substring(0, index) + pair[1] + range.substring(end);
		}

		return null;
	}

	/** {@code LocaleMatcher#getExtentionKeyIndex}, misspelling and all. */
	private static int extensionKeyIndex(String range) {
		int index = Integer.MIN_VALUE;

		for (int position = 1; position < range.length(); ++position)
			if (range.charAt(position) == '-') {
				if (position - index == 2)
					return index;
				index = position;
			}

		return Integer.MIN_VALUE;
	}

	/** One table row; {@code List.of} copies the array, so the stored list is immutable. */
	private static void put(Map<String, List<String>> map, String key, String... equivalents) {
		map.put(key, List.of(equivalents));
	}

	private static void putLanguageEquivalents0(Map<String, List<String>> map) {
		put(map, "aam", "aas");
		put(map, "aao", "ar-aao");
		put(map, "aas", "aam");
		put(map, "abh", "ar-abh");
		put(map, "abv", "ar-abv");
		put(map, "acm", "ar-acm");
		put(map, "acn", "xia");
		put(map, "acq", "ar-acq");
		put(map, "acw", "ar-acw");
		put(map, "acx", "ar-acx");
		put(map, "acy", "ar-acy");
		put(map, "adf", "ar-adf");
		put(map, "adp", "dz");
		put(map, "ads", "sgn-ads");
		put(map, "adx", "pcr");
		put(map, "aeb", "ajt", "ar-aeb");
		put(map, "aec", "ar-aec");
		put(map, "aed", "sgn-aed");
		put(map, "aen", "sgn-aen");
		put(map, "afb", "ar-afb");
		put(map, "afg", "sgn-afg");
		put(map, "ajp", "apc", "ar-ajp", "ar-apc");
		put(map, "ajs", "sgn-ajs");
		put(map, "ajt", "aeb", "ar-aeb");
		put(map, "ami", "i-ami");
		put(map, "aog", "myd");
		put(map, "apc", "ajp", "ar-ajp", "ar-apc");
		put(map, "apd", "ar-apd");
		put(map, "ar-aao", "aao");
		put(map, "ar-abh", "abh");
		put(map, "ar-abv", "abv");
		put(map, "ar-acm", "acm");
		put(map, "ar-acq", "acq");
		put(map, "ar-acw", "acw");
		put(map, "ar-acx", "acx");
		put(map, "ar-acy", "acy");
		put(map, "ar-adf", "adf");
		put(map, "ar-aeb", "aeb", "ajt");
		put(map, "ar-aec", "aec");
		put(map, "ar-afb", "afb");
		put(map, "ar-ajp", "apc", "ajp", "ar-apc");
		put(map, "ar-apc", "apc", "ajp", "ar-ajp");
		put(map, "ar-apd", "apd");
		put(map, "ar-arb", "arb");
		put(map, "ar-arq", "arq");
		put(map, "ar-ars", "ars");
		put(map, "ar-ary", "ary");
		put(map, "ar-arz", "arz");
		put(map, "ar-auz", "auz");
		put(map, "ar-avl", "avl");
		put(map, "ar-ayh", "ayh");
		put(map, "ar-ayl", "ayl");
		put(map, "ar-ayn", "ayn");
		put(map, "ar-ayp", "ayp");
		put(map, "ar-bbz", "bbz");
		put(map, "ar-pga", "pga");
		put(map, "ar-shu", "shu");
		put(map, "ar-ssh", "ssh");
		put(map, "arb", "ar-arb");
		put(map, "arq", "ar-arq");
		put(map, "ars", "ar-ars");
		put(map, "art-lojban", "jbo");
		put(map, "ary", "ar-ary");
		put(map, "arz", "ar-arz");
		put(map, "asd", "snz");
		put(map, "ase", "sgn-ase", "sgn-us");
		put(map, "asf", "sgn-asf");
		put(map, "asp", "sgn-asp");
		put(map, "asq", "sgn-asq");
		put(map, "asw", "sgn-asw");
		put(map, "aue", "ktz");
		put(map, "auz", "ar-auz");
		put(map, "avl", "ar-avl");
		put(map, "ayh", "ar-ayh");
		put(map, "ayl", "ar-ayl");
		put(map, "ayn", "ar-ayn");
		put(map, "ayp", "ar-ayp");
		put(map, "ayx", "nun");
		put(map, "bbz", "ar-bbz");
		put(map, "bcg", "bgm");
		put(map, "bfi", "sgn-bfi", "sgn-gb");
		put(map, "bfk", "sgn-bfk");
		put(map, "bfy", "ppa");
		put(map, "bgm", "bcg");
		put(map, "bh", "bih");
		put(map, "bic", "bir");
		put(map, "bih", "bh");
		put(map, "bir", "bic");
		put(map, "bjd", "drl");
		put(map, "bjn", "ms-bjn");
		put(map, "blg", "iba", "snb");
		put(map, "bmf", "krm");
		put(map, "bnn", "i-bnn");
		put(map, "bog", "sgn-bog");
		put(map, "bpp", "nxu");
		put(map, "bqn", "sgn-bqn");
		put(map, "bqy", "sgn-bqy");
		put(map, "btj", "ms-btj");
		put(map, "bve", "ms-bve");
		put(map, "bvl", "sgn-bvl");
		put(map, "bvu", "ms-bvu");
		put(map, "bzs", "sgn-bzs", "sgn-br");
		put(map, "cax", "xba");
		put(map, "cbr", "nom");
		put(map, "ccq", "rki", "ybd");
		put(map, "cdo", "zh-cdo");
		put(map, "cds", "sgn-cds");
		put(map, "cir", "meg");
		put(map, "cjr", "mom");
		put(map, "cjy", "zh-cjy");
		put(map, "cka", "cmr");
		put(map, "cmk", "xch");
		put(map, "cmn", "zh-cmn", "zh-guoyu");
		put(map, "cmn-hans", "zh-cmn-hans");
		put(map, "cmn-hant", "zh-cmn-hant");
		put(map, "cmr", "cka");
		put(map, "cnp", "zh-cnp");
		put(map, "coa", "ms-coa");
		put(map, "coy", "pij", "nts");
		put(map, "cpx", "zh-cpx");
		put(map, "cqu", "quh");
		put(map, "crr", "pmk");
		put(map, "csc", "sgn-csc");
		put(map, "csd", "sgn-csd");
		put(map, "cse", "sgn-cse");
		put(map, "csf", "sgn-csf");
		put(map, "csg", "sgn-csg");
		put(map, "csl", "sgn-csl");
		put(map, "csn", "sgn-csn", "sgn-co");
		put(map, "csp", "zh-csp");
		put(map, "csq", "sgn-csq");
		put(map, "csr", "sgn-csr");
		put(map, "csx", "sgn-csx");
		put(map, "czh", "zh-czh");
		put(map, "czo", "zh-czo");
		put(map, "dek", "sqm");
		put(map, "dev", "gav");
		put(map, "dif", "dit");
		put(map, "dit", "dif");
		put(map, "dmw", "xrq");
		put(map, "doq", "sgn-doq");
		put(map, "drh", "khk");
		put(map, "drl", "bjd");
		put(map, "drr", "kzk", "gli");
		put(map, "drw", "prs", "tnf");
		put(map, "dse", "sgn-dse", "sgn-nl");
		put(map, "dsl", "sgn-dsl", "sgn-dk");
		put(map, "dsz", "sgn-dsz");
		put(map, "dtp", "ktr", "kzj", "kzt", "tdu");
		put(map, "dup", "ms-dup");
		put(map, "duz", "guv");
		put(map, "dyl", "sgn-dyl");
		put(map, "dz", "adp");
		put(map, "ecs", "sgn-ecs");
		put(map, "ehs", "sgn-ehs");
		put(map, "eko", "nte");
		put(map, "ema", "uok");
		put(map, "en-gb-oed", "en-gb-oxendict");
		put(map, "en-gb-oxendict", "en-gb-oed");
		put(map, "enm", "yol");
		put(map, "esl", "sgn-esl");
		put(map, "esn", "sgn-esn");
		put(map, "eso", "sgn-eso");
		put(map, "eth", "sgn-eth");
		put(map, "fcs", "sgn-fcs");
		put(map, "fse", "sgn-fse");
		put(map, "fsl", "sgn-fsl", "sgn-fr");
		put(map, "fss", "sgn-fss");
		put(map, "gal", "ilw");
		put(map, "gan", "zh-gan");
		put(map, "gav", "dev");
		put(map, "gdj", "kvs");
		put(map, "gds", "sgn-gds");
		put(map, "gfx", "vaj", "mwj", "oun");
		put(map, "ggn", "gvr");
		put(map, "gli", "kzk", "drr");
		put(map, "gom", "kok-gom");
		put(map, "gse", "sgn-gse");
		put(map, "gsg", "sgn-gsg", "sgn-de");
		put(map, "gsm", "sgn-gsm");
		put(map, "gss", "sgn-gss", "sgn-gr");
		put(map, "gti", "nyc");
		put(map, "gu", "prp");
		put(map, "gus", "sgn-gus");
		put(map, "guv", "duz");
		put(map, "gvr", "ggn");
		put(map, "hab", "sgn-hab");
		put(map, "haf", "sgn-haf");
		put(map, "hak", "zh-hak", "i-hak", "zh-hakka");
		put(map, "hds", "sgn-hds");
		put(map, "he", "iw");
		put(map, "hji", "ms-hji");
		put(map, "hks", "sgn-hks");
		put(map, "hle", "sca");
		put(map, "hnm", "zh-hnm");
		put(map, "hos", "sgn-hos");
		put(map, "hps", "sgn-hps");
		put(map, "hrr", "jal");
		put(map, "hsh", "sgn-hsh");
		put(map, "hsl", "sgn-hsl");
	}

	private static void putLanguageEquivalents1(Map<String, List<String>> map) {
		put(map, "hsn", "zh-hsn", "zh-xiang");
		put(map, "huw", "pmc");
		put(map, "i-ami", "ami");
		put(map, "i-bnn", "bnn");
		put(map, "i-hak", "hak", "zh-hak", "zh-hakka");
		put(map, "i-klingon", "tlh");
		put(map, "i-lux", "lb");
		put(map, "i-navajo", "nv");
		put(map, "i-pwn", "pwn");
		put(map, "i-tao", "tao");
		put(map, "i-tay", "tay");
		put(map, "i-tsu", "tsu");
		put(map, "iba", "blg", "snb");
		put(map, "ibi", "opa");
		put(map, "icl", "sgn-icl");
		put(map, "id", "in");
		put(map, "iks", "sgn-iks");
		put(map, "ils", "sgn-ils");
		put(map, "ilw", "gal");
		put(map, "in", "id");
		put(map, "inl", "sgn-inl");
		put(map, "ins", "sgn-ins");
		put(map, "ise", "sgn-ise", "sgn-it");
		put(map, "isg", "sgn-isg", "sgn-ie");
		put(map, "isr", "sgn-isr");
		put(map, "iw", "he");
		put(map, "jak", "ms-jak");
		put(map, "jal", "hrr");
		put(map, "jax", "ms-jax");
		put(map, "jbo", "art-lojban");
		put(map, "jcs", "sgn-jcs");
		put(map, "jeg", "oyb", "skk", "thx");
		put(map, "jhs", "sgn-jhs");
		put(map, "ji", "yi");
		put(map, "jks", "sgn-jks");
		put(map, "jls", "sgn-jls");
		put(map, "jos", "sgn-jos");
		put(map, "jsl", "sgn-jsl", "sgn-jp");
		put(map, "jus", "sgn-jus");
		put(map, "jv", "jw");
		put(map, "jw", "jv");
		put(map, "kak", "tne");
		put(map, "kdz", "ncp");
		put(map, "kgc", "tdf");
		put(map, "kgh", "kml");
		put(map, "kgi", "sgn-kgi");
		put(map, "kgm", "plu");
		put(map, "khk", "drh");
		put(map, "kjh", "zkb");
		put(map, "kmb", "smd");
		put(map, "kml", "kgh");
		put(map, "knn", "kok-knn");
		put(map, "koj", "kwv");
		put(map, "kok-gom", "gom");
		put(map, "kok-knn", "knn");
		put(map, "krm", "bmf");
		put(map, "kru", "kxl");
		put(map, "ksp", "lak");
		put(map, "ktr", "dtp", "kzj", "kzt", "tdu");
		put(map, "ktz", "aue");
		put(map, "kvb", "ms-kvb");
		put(map, "kvk", "sgn-kvk");
		put(map, "kvr", "ms-kvr");
		put(map, "kvs", "gdj");
		put(map, "kwq", "yam");
		put(map, "kwv", "koj");
		put(map, "kxd", "ms-kxd");
		put(map, "kxe", "tvd");
		put(map, "kxl", "kru");
		put(map, "kxr", "pat");
		put(map, "kzj", "dtp", "ktr", "kzt", "tdu");
		put(map, "kzk", "drr", "gli");
		put(map, "kzt", "dtp", "ktr", "kzj", "tdu");
		put(map, "lak", "ksp");
		put(map, "lb", "i-lux");
		put(map, "lbs", "sgn-lbs");
		put(map, "lce", "ms-lce");
		put(map, "lcf", "ms-lcf");
		put(map, "lcq", "ppr");
		put(map, "lgs", "sgn-lgs");
		put(map, "lii", "raq");
		put(map, "liw", "ms-liw");
		put(map, "llo", "ngt");
		put(map, "lls", "sgn-lls");
		put(map, "lmm", "rmx");
		put(map, "lrr", "yma");
		put(map, "lsb", "sgn-lsb");
		put(map, "lsc", "sgn-lsc");
		put(map, "lsg", "sgn-lsg");
		put(map, "lsl", "sgn-lsl");
		put(map, "lsn", "sgn-lsn");
		put(map, "lso", "sgn-lso");
		put(map, "lsp", "sgn-lsp");
		put(map, "lst", "sgn-lst");
		put(map, "lsv", "sgn-lsv");
		put(map, "lsw", "sgn-lsw");
		put(map, "lsy", "sgn-lsy");
		put(map, "ltg", "lv-ltg");
		put(map, "luh", "zh-luh");
		put(map, "lv-ltg", "ltg");
		put(map, "lv-lvs", "lvs");
		put(map, "lvs", "lv-lvs");
		put(map, "lws", "sgn-lws");
		put(map, "lzh", "zh-lzh");
		put(map, "max", "ms-max");
		put(map, "mdl", "sgn-mdl");
		put(map, "meg", "cir");
		put(map, "meo", "ms-meo");
		put(map, "mfa", "ms-mfa");
		put(map, "mfb", "ms-mfb");
		put(map, "mfs", "sgn-mfs", "sgn-mx");
		put(map, "mgp", "mrd");
		put(map, "min", "ms-min");
		put(map, "mnp", "zh-mnp");
		put(map, "mo", "ro");
		put(map, "mom", "cjr");
		put(map, "mqg", "ms-mqg");
		put(map, "mrd", "mgp");
		put(map, "mre", "sgn-mre");
		put(map, "mrh", "shl");
		put(map, "mry", "mst", "myt");
		put(map, "ms-bjn", "bjn");
		put(map, "ms-btj", "btj");
		put(map, "ms-bve", "bve");
		put(map, "ms-bvu", "bvu");
		put(map, "ms-coa", "coa");
		put(map, "ms-dup", "dup");
		put(map, "ms-hji", "hji");
		put(map, "ms-jak", "jak");
		put(map, "ms-jax", "jax");
		put(map, "ms-kvb", "kvb");
		put(map, "ms-kvr", "kvr");
		put(map, "ms-kxd", "kxd");
		put(map, "ms-lce", "lce");
		put(map, "ms-lcf", "lcf");
		put(map, "ms-liw", "liw");
		put(map, "ms-max", "max");
		put(map, "ms-meo", "meo");
		put(map, "ms-mfa", "mfa");
		put(map, "ms-mfb", "mfb");
		put(map, "ms-min", "min");
		put(map, "ms-mqg", "mqg");
		put(map, "ms-msi", "msi");
		put(map, "ms-mui", "mui");
		put(map, "ms-orn", "orn");
		put(map, "ms-ors", "ors");
		put(map, "ms-pel", "pel");
		put(map, "ms-pse", "pse");
		put(map, "ms-tmw", "tmw");
		put(map, "ms-urk", "urk");
		put(map, "ms-vkk", "vkk");
		put(map, "ms-vkt", "vkt");
		put(map, "ms-xmm", "xmm");
		put(map, "ms-zlm", "zlm");
		put(map, "ms-zmi", "zmi");
		put(map, "ms-zsm", "zsm");
		put(map, "msd", "sgn-msd");
		put(map, "msi", "ms-msi");
		put(map, "msr", "sgn-msr");
		put(map, "mst", "mry", "myt");
		put(map, "mtm", "ymt");
		put(map, "mui", "ms-mui");
		put(map, "mwj", "vaj", "gfx", "oun");
		put(map, "myd", "aog");
		put(map, "myt", "mry", "mst");
		put(map, "mzc", "sgn-mzc");
		put(map, "mzg", "sgn-mzg");
		put(map, "mzy", "sgn-mzy");
		put(map, "nad", "xny");
		put(map, "nan", "zh-nan", "zh-min-nan");
		put(map, "nb", "no-bok");
		put(map, "nbr", "nns");
		put(map, "nbs", "sgn-nbs");
		put(map, "ncp", "kdz");
		put(map, "ncs", "sgn-ncs", "sgn-ni");
		put(map, "ngt", "llo");
		put(map, "ngv", "nnx");
		put(map, "nn", "no-nyn");
		put(map, "nns", "nbr");
		put(map, "nnx", "ngv");
		put(map, "no-bok", "nb");
		put(map, "no-nyn", "nn");
		put(map, "nom", "cbr");
		put(map, "nsi", "sgn-nsi");
		put(map, "nsl", "sgn-nsl", "sgn-no");
		put(map, "nsp", "sgn-nsp");
		put(map, "nsr", "sgn-nsr");
		put(map, "nte", "eko");
		put(map, "nts", "pij", "coy");
		put(map, "nun", "ayx");
		put(map, "nv", "i-navajo");
		put(map, "nxu", "bpp");
		put(map, "nyc", "gti");
		put(map, "nzs", "sgn-nzs");
		put(map, "okl", "sgn-okl");
		put(map, "ola", "thw");
		put(map, "opa", "ibi");
		put(map, "orn", "ms-orn");
		put(map, "ors", "ms-ors");
		put(map, "oun", "vaj", "gfx", "mwj");
	}

	private static void putLanguageEquivalents2(Map<String, List<String>> map) {
		put(map, "oyb", "jeg", "skk", "thx");
		put(map, "pat", "kxr");
		put(map, "pcr", "adx");
		put(map, "pel", "ms-pel");
		put(map, "pga", "ar-pga");
		put(map, "pgz", "sgn-pgz");
		put(map, "phr", "pmu");
		put(map, "pij", "coy", "nts");
		put(map, "pks", "sgn-pks");
		put(map, "plu", "kgm");
		put(map, "pmc", "huw");
		put(map, "pmk", "crr");
		put(map, "pmu", "phr");
		put(map, "ppa", "bfy");
		put(map, "ppr", "lcq");
		put(map, "prl", "sgn-prl");
		put(map, "prp", "gu");
		put(map, "prs", "drw", "tnf");
		put(map, "prt", "pry");
		put(map, "pry", "prt");
		put(map, "prz", "sgn-prz");
		put(map, "psc", "sgn-psc");
		put(map, "psd", "sgn-psd");
		put(map, "pse", "ms-pse");
		put(map, "psg", "sgn-psg");
		put(map, "psl", "sgn-psl");
		put(map, "pso", "sgn-pso");
		put(map, "psp", "sgn-psp");
		put(map, "psr", "sgn-psr", "sgn-pt");
		put(map, "pub", "puz");
		put(map, "puz", "pub");
		put(map, "pwn", "i-pwn");
		put(map, "pys", "sgn-pys");
		put(map, "quh", "cqu");
		put(map, "raq", "lii");
		put(map, "ras", "tie");
		put(map, "rib", "sgn-rib");
		put(map, "rki", "ccq", "ybd");
		put(map, "rms", "sgn-rms");
		put(map, "rmx", "lmm");
		put(map, "rnb", "sgn-rnb");
		put(map, "ro", "mo");
		put(map, "rsi", "sgn-rsi");
		put(map, "rsl", "sgn-rsl");
		put(map, "rsm", "sgn-rsm");
		put(map, "rsn", "sgn-rsn");
		put(map, "sca", "hle");
		put(map, "scv", "zir");
		put(map, "sdl", "sgn-sdl");
		put(map, "sfb", "sgn-sfb", "sgn-be-fr");
		put(map, "sfs", "sgn-sfs", "sgn-za");
		put(map, "sgg", "sgn-sgg", "sgn-ch-de");
		put(map, "sgn-ads", "ads");
		put(map, "sgn-aed", "aed");
		put(map, "sgn-aen", "aen");
		put(map, "sgn-afg", "afg");
		put(map, "sgn-ajs", "ajs");
		put(map, "sgn-ase", "ase", "sgn-us");
		put(map, "sgn-asf", "asf");
		put(map, "sgn-asp", "asp");
		put(map, "sgn-asq", "asq");
		put(map, "sgn-asw", "asw");
		put(map, "sgn-be-fr", "sfb", "sgn-sfb");
		put(map, "sgn-be-nl", "vgt", "sgn-vgt");
		put(map, "sgn-bfi", "bfi", "sgn-gb");
		put(map, "sgn-bfk", "bfk");
		put(map, "sgn-bog", "bog");
		put(map, "sgn-bqn", "bqn");
		put(map, "sgn-bqy", "bqy");
		put(map, "sgn-br", "bzs", "sgn-bzs");
		put(map, "sgn-bvl", "bvl");
		put(map, "sgn-bzs", "bzs", "sgn-br");
		put(map, "sgn-cds", "cds");
		put(map, "sgn-ch-de", "sgg", "sgn-sgg");
		put(map, "sgn-co", "csn", "sgn-csn");
		put(map, "sgn-csc", "csc");
		put(map, "sgn-csd", "csd");
		put(map, "sgn-cse", "cse");
		put(map, "sgn-csf", "csf");
		put(map, "sgn-csg", "csg");
		put(map, "sgn-csl", "csl");
		put(map, "sgn-csn", "csn", "sgn-co");
		put(map, "sgn-csq", "csq");
		put(map, "sgn-csr", "csr");
		put(map, "sgn-csx", "csx");
		put(map, "sgn-de", "gsg", "sgn-gsg");
		put(map, "sgn-dk", "dsl", "sgn-dsl");
		put(map, "sgn-doq", "doq");
		put(map, "sgn-dse", "dse", "sgn-nl");
		put(map, "sgn-dsl", "dsl", "sgn-dk");
		put(map, "sgn-dsz", "dsz");
		put(map, "sgn-dyl", "dyl");
		put(map, "sgn-ecs", "ecs");
		put(map, "sgn-ehs", "ehs");
		put(map, "sgn-es", "ssp", "sgn-ssp");
		put(map, "sgn-esl", "esl");
		put(map, "sgn-esn", "esn");
		put(map, "sgn-eso", "eso");
		put(map, "sgn-eth", "eth");
		put(map, "sgn-fcs", "fcs");
		put(map, "sgn-fr", "fsl", "sgn-fsl");
		put(map, "sgn-fse", "fse");
		put(map, "sgn-fsl", "fsl", "sgn-fr");
		put(map, "sgn-fss", "fss");
		put(map, "sgn-gb", "bfi", "sgn-bfi");
		put(map, "sgn-gds", "gds");
		put(map, "sgn-gr", "gss", "sgn-gss");
		put(map, "sgn-gse", "gse");
		put(map, "sgn-gsg", "gsg", "sgn-de");
		put(map, "sgn-gsm", "gsm");
		put(map, "sgn-gss", "gss", "sgn-gr");
		put(map, "sgn-gus", "gus");
		put(map, "sgn-hab", "hab");
		put(map, "sgn-haf", "haf");
		put(map, "sgn-hds", "hds");
		put(map, "sgn-hks", "hks");
		put(map, "sgn-hos", "hos");
		put(map, "sgn-hps", "hps");
		put(map, "sgn-hsh", "hsh");
		put(map, "sgn-hsl", "hsl");
		put(map, "sgn-icl", "icl");
		put(map, "sgn-ie", "isg", "sgn-isg");
		put(map, "sgn-iks", "iks");
		put(map, "sgn-ils", "ils");
		put(map, "sgn-inl", "inl");
		put(map, "sgn-ins", "ins");
		put(map, "sgn-ise", "ise", "sgn-it");
		put(map, "sgn-isg", "isg", "sgn-ie");
		put(map, "sgn-isr", "isr");
		put(map, "sgn-it", "ise", "sgn-ise");
		put(map, "sgn-jcs", "jcs");
		put(map, "sgn-jhs", "jhs");
		put(map, "sgn-jks", "jks");
		put(map, "sgn-jls", "jls");
		put(map, "sgn-jos", "jos");
		put(map, "sgn-jp", "jsl", "sgn-jsl");
		put(map, "sgn-jsl", "jsl", "sgn-jp");
		put(map, "sgn-jus", "jus");
		put(map, "sgn-kgi", "kgi");
		put(map, "sgn-kvk", "kvk");
		put(map, "sgn-lbs", "lbs");
		put(map, "sgn-lgs", "lgs");
		put(map, "sgn-lls", "lls");
		put(map, "sgn-lsb", "lsb");
		put(map, "sgn-lsc", "lsc");
		put(map, "sgn-lsg", "lsg");
		put(map, "sgn-lsl", "lsl");
		put(map, "sgn-lsn", "lsn");
		put(map, "sgn-lso", "lso");
		put(map, "sgn-lsp", "lsp");
		put(map, "sgn-lst", "lst");
		put(map, "sgn-lsv", "lsv");
		put(map, "sgn-lsw", "lsw");
		put(map, "sgn-lsy", "lsy");
		put(map, "sgn-lws", "lws");
		put(map, "sgn-mdl", "mdl");
		put(map, "sgn-mfs", "mfs", "sgn-mx");
		put(map, "sgn-mre", "mre");
		put(map, "sgn-msd", "msd");
		put(map, "sgn-msr", "msr");
		put(map, "sgn-mx", "mfs", "sgn-mfs");
		put(map, "sgn-mzc", "mzc");
		put(map, "sgn-mzg", "mzg");
		put(map, "sgn-mzy", "mzy");
		put(map, "sgn-nbs", "nbs");
		put(map, "sgn-ncs", "ncs", "sgn-ni");
		put(map, "sgn-ni", "ncs", "sgn-ncs");
		put(map, "sgn-nl", "dse", "sgn-dse");
		put(map, "sgn-no", "nsl", "sgn-nsl");
		put(map, "sgn-nsi", "nsi");
		put(map, "sgn-nsl", "nsl", "sgn-no");
		put(map, "sgn-nsp", "nsp");
		put(map, "sgn-nsr", "nsr");
		put(map, "sgn-nzs", "nzs");
		put(map, "sgn-okl", "okl");
		put(map, "sgn-pgz", "pgz");
		put(map, "sgn-pks", "pks");
		put(map, "sgn-prl", "prl");
		put(map, "sgn-prz", "prz");
		put(map, "sgn-psc", "psc");
		put(map, "sgn-psd", "psd");
		put(map, "sgn-psg", "psg");
		put(map, "sgn-psl", "psl");
		put(map, "sgn-pso", "pso");
		put(map, "sgn-psp", "psp");
		put(map, "sgn-psr", "psr", "sgn-pt");
		put(map, "sgn-pt", "psr", "sgn-psr");
		put(map, "sgn-pys", "pys");
		put(map, "sgn-rib", "rib");
		put(map, "sgn-rms", "rms");
		put(map, "sgn-rnb", "rnb");
		put(map, "sgn-rsi", "rsi");
		put(map, "sgn-rsl", "rsl");
		put(map, "sgn-rsm", "rsm");
		put(map, "sgn-rsn", "rsn");
		put(map, "sgn-sdl", "sdl");
		put(map, "sgn-se", "swl", "sgn-swl");
		put(map, "sgn-sfb", "sfb", "sgn-be-fr");
		put(map, "sgn-sfs", "sfs", "sgn-za");
		put(map, "sgn-sgg", "sgg", "sgn-ch-de");
	}

	private static void putLanguageEquivalents3(Map<String, List<String>> map) {
		put(map, "sgn-sgx", "sgx");
		put(map, "sgn-slf", "slf");
		put(map, "sgn-sls", "sls");
		put(map, "sgn-sqk", "sqk");
		put(map, "sgn-sqs", "sqs");
		put(map, "sgn-sqx", "sqx");
		put(map, "sgn-ssp", "ssp", "sgn-es");
		put(map, "sgn-ssr", "ssr");
		put(map, "sgn-svk", "svk");
		put(map, "sgn-swl", "swl", "sgn-se");
		put(map, "sgn-syy", "syy");
		put(map, "sgn-szs", "szs");
		put(map, "sgn-tse", "tse");
		put(map, "sgn-tsm", "tsm");
		put(map, "sgn-tsq", "tsq");
		put(map, "sgn-tss", "tss");
		put(map, "sgn-tsy", "tsy");
		put(map, "sgn-tza", "tza");
		put(map, "sgn-ugn", "ugn");
		put(map, "sgn-ugy", "ugy");
		put(map, "sgn-ukl", "ukl");
		put(map, "sgn-uks", "uks");
		put(map, "sgn-us", "ase", "sgn-ase");
		put(map, "sgn-vgt", "vgt", "sgn-be-nl");
		put(map, "sgn-vsi", "vsi");
		put(map, "sgn-vsl", "vsl");
		put(map, "sgn-vsv", "vsv");
		put(map, "sgn-wbs", "wbs");
		put(map, "sgn-xki", "xki");
		put(map, "sgn-xml", "xml");
		put(map, "sgn-xms", "xms");
		put(map, "sgn-yds", "yds");
		put(map, "sgn-ygs", "ygs");
		put(map, "sgn-yhs", "yhs");
		put(map, "sgn-ysl", "ysl");
		put(map, "sgn-ysm", "ysm");
		put(map, "sgn-za", "sfs", "sgn-sfs");
		put(map, "sgn-zhk", "zhk");
		put(map, "sgn-zib", "zib");
		put(map, "sgn-zsl", "zsl");
		put(map, "sgx", "sgn-sgx");
		put(map, "shl", "mrh");
		put(map, "shu", "ar-shu");
		put(map, "sjc", "zh-sjc");
		put(map, "skk", "oyb", "jeg", "thx");
		put(map, "slf", "sgn-slf");
		put(map, "sls", "sgn-sls");
		put(map, "smd", "kmb");
		put(map, "snb", "iba", "blg");
		put(map, "snz", "asd");
		put(map, "sqk", "sgn-sqk");
		put(map, "sqm", "dek");
		put(map, "sqs", "sgn-sqs");
		put(map, "sqx", "sgn-sqx");
		put(map, "ssh", "ar-ssh");
		put(map, "ssp", "sgn-ssp", "sgn-es");
		put(map, "ssr", "sgn-ssr");
		put(map, "svk", "sgn-svk");
		put(map, "sw-swc", "swc");
		put(map, "sw-swh", "swh");
		put(map, "swc", "sw-swc");
		put(map, "swh", "sw-swh");
		put(map, "swl", "sgn-swl", "sgn-se");
		put(map, "syy", "sgn-syy");
		put(map, "szd", "umi");
		put(map, "szs", "sgn-szs");
		put(map, "taj", "tsf");
		put(map, "tao", "i-tao");
		put(map, "tay", "i-tay");
		put(map, "tdf", "kgc");
		put(map, "tdg", "tmk");
		put(map, "tdu", "dtp", "ktr", "kzj", "kzt");
		put(map, "thc", "tpo");
		put(map, "thw", "ola");
		put(map, "thx", "oyb", "jeg", "skk");
		put(map, "tie", "ras");
		put(map, "tkk", "twm");
		put(map, "tlh", "i-klingon");
		put(map, "tlw", "weo");
		put(map, "tmk", "tdg");
		put(map, "tmp", "tyj");
		put(map, "tmw", "ms-tmw");
		put(map, "tne", "kak");
		put(map, "tnf", "prs", "drw");
		put(map, "tpn", "tpw");
		put(map, "tpo", "thc");
		put(map, "tpw", "tpn");
		put(map, "tse", "sgn-tse");
		put(map, "tsf", "taj");
		put(map, "tsm", "sgn-tsm");
		put(map, "tsq", "sgn-tsq");
		put(map, "tss", "sgn-tss");
		put(map, "tsu", "i-tsu");
		put(map, "tsy", "sgn-tsy");
		put(map, "tvd", "kxe");
		put(map, "twm", "tkk");
		put(map, "tyj", "tmp");
		put(map, "tza", "sgn-tza");
		put(map, "ugn", "sgn-ugn");
		put(map, "ugy", "sgn-ugy");
		put(map, "ukl", "sgn-ukl");
		put(map, "uks", "sgn-uks");
		put(map, "umi", "szd");
		put(map, "uok", "ema");
		put(map, "urk", "ms-urk");
		put(map, "uz-uzn", "uzn");
		put(map, "uz-uzs", "uzs");
		put(map, "uzn", "uz-uzn");
		put(map, "uzs", "uz-uzs");
		put(map, "vaj", "gfx", "mwj", "oun");
		put(map, "vgt", "sgn-vgt", "sgn-be-nl");
		put(map, "vkk", "ms-vkk");
		put(map, "vkt", "ms-vkt");
		put(map, "vsi", "sgn-vsi");
		put(map, "vsl", "sgn-vsl");
		put(map, "vsv", "sgn-vsv");
		put(map, "waw", "xkh");
		put(map, "wbs", "sgn-wbs");
		put(map, "weo", "tlw");
		put(map, "wuu", "zh-wuu");
		put(map, "xba", "cax");
		put(map, "xch", "cmk");
		put(map, "xia", "acn");
		put(map, "xkh", "waw");
		put(map, "xki", "sgn-xki");
		put(map, "xml", "sgn-xml");
		put(map, "xmm", "ms-xmm");
		put(map, "xms", "sgn-xms");
		put(map, "xny", "nad");
		put(map, "xrq", "dmw");
		put(map, "xss", "zko");
		put(map, "yam", "kwq");
		put(map, "ybd", "rki", "ccq");
		put(map, "yds", "sgn-yds");
		put(map, "ygs", "sgn-ygs");
		put(map, "yhs", "sgn-yhs");
		put(map, "yi", "ji");
		put(map, "yma", "lrr");
		put(map, "ymt", "mtm");
		put(map, "yol", "enm");
		put(map, "yos", "zom");
		put(map, "ysl", "sgn-ysl");
		put(map, "ysm", "sgn-ysm");
		put(map, "yue", "zh-yue");
		put(map, "yug", "yuu");
		put(map, "yuu", "yug");
		put(map, "zh-cdo", "cdo");
		put(map, "zh-cjy", "cjy");
		put(map, "zh-cmn", "cmn", "zh-guoyu");
		put(map, "zh-cmn-hans", "cmn-hans");
		put(map, "zh-cmn-hant", "cmn-hant");
		put(map, "zh-cnp", "cnp");
		put(map, "zh-cpx", "cpx");
		put(map, "zh-csp", "csp");
		put(map, "zh-czh", "czh");
		put(map, "zh-czo", "czo");
		put(map, "zh-gan", "gan");
		put(map, "zh-guoyu", "cmn", "zh-cmn");
		put(map, "zh-hak", "hak", "i-hak", "zh-hakka");
		put(map, "zh-hakka", "hak", "zh-hak", "i-hak");
		put(map, "zh-hnm", "hnm");
		put(map, "zh-hsn", "hsn", "zh-xiang");
		put(map, "zh-luh", "luh");
		put(map, "zh-lzh", "lzh");
		put(map, "zh-min-nan", "nan", "zh-nan");
		put(map, "zh-mnp", "mnp");
		put(map, "zh-nan", "nan", "zh-min-nan");
		put(map, "zh-sjc", "sjc");
		put(map, "zh-wuu", "wuu");
		put(map, "zh-xiang", "hsn", "zh-hsn");
		put(map, "zh-yue", "yue");
		put(map, "zhk", "sgn-zhk");
		put(map, "zib", "sgn-zib");
		put(map, "zir", "scv");
		put(map, "zkb", "kjh");
		put(map, "zko", "xss");
		put(map, "zlm", "ms-zlm");
		put(map, "zmi", "ms-zmi");
		put(map, "zom", "yos");
		put(map, "zsl", "sgn-zsl");
		put(map, "zsm", "ms-zsm");
	}
}
