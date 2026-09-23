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

package com.lokalized.iana;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates {@code com.lokalized.IanaLanguageEquivalents} from a pinned IANA Language Subtag
 * Registry snapshot.
 * <p>
 * Run by {@code IanaEquivalencesGeneratorTests}, which calls {@code main(root, "--check")} so the
 * checked-in artifact cannot drift from the input it claims to come from. Same contract as
 * {@code CldrDataGenerator}.
 * <p>
 * <strong>The registry snapshot is the only input.</strong> Everything the generated class carries is
 * derived from {@code language-subtag-registry.txt}: which language tags are equivalent, the order
 * each class's members are expanded in, and the region/variant substitutions together with their
 * order. Nothing is recorded from a JDK and nothing is hand-typed, so generation is deterministic on
 * any JDK and a newer snapshot regenerates all of it. Both orders are observable in
 * {@code LanguageRange} lists and both reproduce what {@code Locale.LanguageRange#parse(String)} does
 * on JDK 21; the rules that produce that agreement are written down below
 * ({@link #languageEquivalents(String)}, {@link #regionVariantEquivalents(String)}) and are checked by
 * tests, rather than being copied out of a JDK.
 *
 * <p>
 * <strong>This module targets Java 9</strong>, which is older than it looks: {@code Path.of},
 * {@code Files.readString}, {@code List.copyOf} and {@code String.isBlank} are all Java 10 or 11 and
 * are unavailable here. That is not incidental to this change — a Java 9 deployment carries the
 * OLDEST bundled registry snapshot, so it is the population this artifact helps most.
 *
 * <pre>
 *   mvn -q test -Dtest=IanaEquivalencesGeneratorTests   # checks
 *   java ...IanaEquivalencesGenerator &lt;repo-root&gt; --write
 * </pre>
 */
public final class IanaEquivalencesGenerator {
	private static final String REGISTRY = "src/build/resources/iana/language-subtag-registry.txt";
	private static final String OUTPUT = "src/main/java/com/lokalized/IanaLanguageEquivalents.java";
	private static final String TEMPLATE = "src/build/resources/iana/IanaLanguageEquivalents.template";

	/**
	 * Table rows per generated initializer method. A row costs roughly 11 + 7n bytes of bytecode for n
	 * equivalents, so 200 rows keep each method a small fraction of the JVM's 64 KB code-size limit.
	 */
	private static final int ENTRIES_PER_METHOD = 200;

	/**
	 * The bucket size at which the generator stops trusting its model of {@code java.util.HashMap}. A bucket
	 * iterates in insertion order through eight entries; a ninth makes HashMap treeify it or, below 64 buckets,
	 * resize the table, and either reorders iteration. Refusing at eight is one entry early, deliberately.
	 */
	private static final int HASH_MAP_TREEIFY_THRESHOLD = 8;

	private static final Pattern FIELD = Pattern.compile("^([A-Za-z-]+):\\s*(.*)$");

	private IanaEquivalencesGenerator() {}

	public static void main(String[] args) {
		if (args.length < 2)
			throw new IllegalArgumentException("usage: IanaEquivalencesGenerator <repo-root> --check|--write");

		Path root = Paths.get(args[0]);
		boolean write = "--write".equals(args[1]);
		String generated = generate(root);
		Path output = root.resolve(OUTPUT);

		try {
			if (write) {
				Files.write(output, generated.getBytes(StandardCharsets.UTF_8));
				System.out.println("wrote " + OUTPUT);
				return;
			}

			String current = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);

			if (!current.equals(generated))
				throw new IllegalStateException(OUTPUT + " is not what this generator produces from "
						+ REGISTRY + ". Re-record deliberately with --write and review the diff.");
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/**
	 * The SHA-256 of a file's bytes, lowercase hex.
	 * <p>
	 * {@code String.format("%02x")} per byte rather than any of the newer helpers: this module
	 * targets Java 9, where {@code HexFormat} (17) does not exist.
	 */
	private static String sha256(Path path) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
					.digest(Files.readAllBytes(path));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest)
				hex.append(String.format("%02x", b));
			return hex.toString();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", e);
		}
	}

	private static String generate(Path root) {
		String registry = read(root.resolve(REGISTRY));
		String registrySha256 = sha256(root.resolve(REGISTRY));

		Matcher fileDate = Pattern.compile("^File-Date:\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.MULTILINE).matcher(registry);

		if (!fileDate.find())
			throw new IllegalStateException(REGISTRY + " carries no File-Date header");

		return emit(root, fileDate.group(1), registrySha256, languageEquivalents(registry),
				regionVariantEquivalents(registry));
	}

	/**
	 * The registry's records in file order, each as its fields with the first occurrence of a
	 * repeated field winning. Index 0 is the {@code File-Date} header.
	 */
	private static List<Map<String, String>> records(String registry) {
		List<Map<String, String>> records = new ArrayList<>();

		for (String block : registry.split("\n%%\n")) {
			Map<String, String> record = new HashMap<>();

			for (String line : block.split("\n")) {
				Matcher field = FIELD.matcher(line);

				if (field.matches())
					record.putIfAbsent(field.group(1), field.group(2));
			}

			records.add(record);
		}

		return records;
	}

	/** The tag a record defines: its {@code Subtag}, or for grandfathered and redundant records its {@code Tag}. */
	private static String tagOf(Map<String, String> record) {
		return record.containsKey("Subtag") ? record.get("Subtag") : record.get("Tag");
	}

	/**
	 * Region and variant records stay out of the language namespace; every other record type is in it.
	 * <p>
	 * <strong>Record TYPE is discriminated, and the first version of this did not do it.</strong>
	 * Running union-find over every {@code Preferred-Value} regardless of type flattens six REGION
	 * records (DD-&gt;DE, FX-&gt;FR, BU-&gt;MM, ZR-&gt;CD, TP-&gt;TL, YD-&gt;YE) and one VARIANT record
	 * (heploc-&gt;alalc97) into the language namespace. That is not a keying nuance: it claims
	 * {@code de} is equivalent to {@code dd}, so the range {@code de} — German — would expand to a
	 * region deprecated in 1990. Those seven records feed {@link #regionVariantEquivalents(String)}
	 * instead, whose keys all carry a leading hyphen, which is what confines them to non-initial
	 * subtag positions.
	 */
	private static boolean inLanguageNamespace(Map<String, String> record) {
		String type = record.get("Type");
		return !"region".equals(type) && !"variant".equals(type);
	}

	/**
	 * The language-equivalence table the generated class carries: each lowercased tag to the OTHER
	 * members of its equivalence class, in the order {@code parse} expands them.
	 * <p>
	 * <strong>Membership.</strong> Two tags are equivalent when a language-namespace record names one
	 * as the other's {@code Preferred-Value}, and an extlang subtag is equivalent to its
	 * {@code Prefix-Subtag} form; classes are the transitive closure of those pairs.
	 * <p>
	 * <strong>Order.</strong> Every class has exactly one member that the registry gives no
	 * {@code Preferred-Value} of its own — the tag the others are deprecated in favour of — and it comes
	 * first. The remaining members follow in the order the registry first NAMES them, where a record
	 * names its own {@code Subtag} or {@code Tag} and an extlang record also names its
	 * {@code Prefix-Subtag} form. Wording matters here: "the one non-deprecated member" would be
	 * wrong, because many classes hold two members with no {@code Deprecated} field; it is the
	 * {@code Preferred-Value} field that singles one out. This rule reproduces the order
	 * {@code Locale.LanguageRange#parse(String)} uses on JDK 21 for every key that JDK knows, and the
	 * generator refuses to run rather than guess if a class ever has no such member, more than one,
	 * or two members first named by the same record.
	 *
	 * @param registry the text of a Language Subtag Registry file, not null
	 * @return each lowercased tag to the other members of its class, in expansion order, sorted by key
	 */
	public static Map<String, List<String>> languageEquivalents(String registry) {
		List<Map<String, String>> records = records(registry);
		Map<String, String> parent = new HashMap<>();
		List<String[]> pairs = new ArrayList<>();
		Map<String, Integer> firstNamedAt = new HashMap<>();
		Set<String> hasPreferredValue = new HashSet<>();

		for (int index = 0; index < records.size(); ++index) {
			Map<String, String> record = records.get(index);
			String subtag = tagOf(record);

			if (subtag == null || !inLanguageNamespace(record))
				continue;

			String tag = lower(subtag);
			String preferred = record.get("Preferred-Value");
			firstNamedAt.putIfAbsent(tag, index);

			if (preferred != null) {
				pairs.add(new String[] { tag, lower(preferred) });

				if (!lower(preferred).equals(tag))
					hasPreferredValue.add(tag);
			}

			if ("extlang".equals(record.get("Type")) && record.get("Prefix") != null) {
				String extended = lower(record.get("Prefix") + "-" + subtag);
				pairs.add(new String[] { tag, extended });
				firstNamedAt.putIfAbsent(extended, index);

				if (preferred != null && !lower(preferred).equals(extended))
					hasPreferredValue.add(extended);
			}
		}

		for (String[] pair : pairs) {
			String left = find(parent, pair[0]), right = find(parent, pair[1]);
			if (!left.equals(right))
				parent.put(left, right);
		}

		Map<String, Set<String>> grouped = new TreeMap<>();
		for (String member : new ArrayList<>(parent.keySet()))
			grouped.computeIfAbsent(find(parent, member), unused -> new LinkedHashSet<>()).add(member);

		Map<String, List<String>> equivalents = new TreeMap<>();

		for (Set<String> members : grouped.values()) {
			List<String> ordered = orderedClass(members, firstNamedAt, hasPreferredValue);

			for (String member : ordered) {
				List<String> others = new ArrayList<>(ordered);
				others.remove(member);
				equivalents.put(member, Collections.unmodifiableList(others));
			}
		}

		return Collections.unmodifiableMap(equivalents);
	}

	/**
	 * One class in expansion order: its only member without a {@code Preferred-Value}, then the rest by
	 * the record that first names them. A key's list is this order with the key itself removed.
	 */
	private static List<String> orderedClass(Set<String> members, Map<String, Integer> firstNamedAt,
																					 Set<String> hasPreferredValue) {
		List<String> withoutPreferredValue = new ArrayList<>();
		List<String> withPreferredValue = new ArrayList<>();

		for (String member : members)
			(hasPreferredValue.contains(member) ? withPreferredValue : withoutPreferredValue).add(member);

		if (withoutPreferredValue.size() != 1)
			throw new IllegalStateException("Equivalence class " + new TreeSet<>(members) + " has "
					+ withoutPreferredValue.size() + " members without a Preferred-Value " + withoutPreferredValue
					+ "; its expansion order is defined only when there is exactly one");

		Set<Integer> positions = new HashSet<>();

		for (String member : withPreferredValue) {
			Integer position = firstNamedAt.get(member);

			if (position == null || !positions.add(position))
				throw new IllegalStateException("Equivalence class " + new TreeSet<>(members) + " member '" + member
						+ "' is not the first tag its record names, so its place in the expansion order is undefined");
		}

		withPreferredValue.sort(Comparator.comparingInt(firstNamedAt::get));

		List<String> ordered = new ArrayList<>(members.size());
		ordered.add(withoutPreferredValue.get(0));
		ordered.addAll(withPreferredValue);
		return ordered;
	}

	private static String find(Map<String, String> parent, String node) {
		parent.putIfAbsent(node, node);

		while (!parent.get(node).equals(node)) {
			parent.put(node, parent.get(parent.get(node)));
			node = parent.get(node);
		}

		return node;
	}

	/**
	 * The region and variant substitutions the generated class applies inside a range, as
	 * {@code {from, to}} pairs in the order they are tried.
	 * <p>
	 * <strong>The set</strong> is every region or variant record carrying a {@code Preferred-Value}
	 * (seven in the 2026-09-17 snapshot: BU, DD, FX, TP, YD, ZR and heploc), in BOTH directions,
	 * lowercased and prefixed with a hyphen — which is how the JDK builds
	 * {@code sun.util.locale.LocaleEquivalentMaps.regionVariantEquivMap}, and why {@code de-DE}
	 * expands to {@code de-dd}.
	 * <p>
	 * <strong>The order</strong> decides which substitution applies when a range contains two, and in
	 * the JDK it is nothing more than {@code java.util.HashMap} iteration order. It is computed here
	 * rather than obtained from a running JDK. JDK 21 builds that map with
	 * {@code HashMap.newHashMap(n)}, a table of {@code tableSizeFor(ceil(n / 0.75))} buckets (32 for
	 * n = 14), and inserts the keys in ascending order. {@code HashMap} places a key in bucket
	 * {@code (h ^ (h >>> 16)) & (buckets - 1)}, where {@code h} is {@code String#hashCode()} — whose
	 * formula the {@code String} specification fixes — and iterates bucket by bucket, each bucket in
	 * insertion order through eight entries. So the order is the keys in ascending order, stably
	 * sorted by bucket. A ninth entry in one bucket would make {@code HashMap} treeify it or, below 64
	 * buckets, resize the table, and either reorders iteration; the generator refuses to run once a
	 * bucket reaches eight, one entry before that.
	 *
	 * @param registry the text of a Language Subtag Registry file, not null
	 * @return the substitutions as {@code {from, to}} pairs in the order they are tried, not null
	 */
	public static List<String[]> regionVariantEquivalents(String registry) {
		// Ascending: the order the JDK's generator inserts them in.
		Map<String, String> substitutions = new TreeMap<>();

		for (Map<String, String> record : records(registry)) {
			String type = record.get("Type");
			String subtag = record.get("Subtag");
			String preferred = record.get("Preferred-Value");

			if (inLanguageNamespace(record) || subtag == null || preferred == null)
				continue;

			putSubstitution(substitutions, "-" + lower(subtag), "-" + lower(preferred), type);
			putSubstitution(substitutions, "-" + lower(preferred), "-" + lower(subtag), type);
		}

		int buckets = hashMapTableSize(substitutions.size());
		List<String> keys = new ArrayList<>(substitutions.keySet());
		// List.sort is stable, so keys sharing a bucket keep their insertion order.
		keys.sort(Comparator.comparingInt(key -> hashMapBucket(key, buckets)));

		Map<Integer, Integer> occupancy = new HashMap<>();
		List<String[]> ordered = new ArrayList<>(keys.size());

		for (String key : keys) {
			if (occupancy.merge(hashMapBucket(key, buckets), 1, Integer::sum) >= HASH_MAP_TREEIFY_THRESHOLD)
				throw new IllegalStateException("Region/variant key '" + key + "' lands in a HashMap bucket holding "
						+ HASH_MAP_TREEIFY_THRESHOLD + " keys, where the iteration-order model no longer applies");

			ordered.add(new String[] { key, substitutions.get(key) });
		}

		return Collections.unmodifiableList(ordered);
	}

	private static void putSubstitution(Map<String, String> substitutions, String from, String to, String type) {
		String existing = substitutions.putIfAbsent(from, to);

		if (existing != null && !existing.equals(to))
			throw new IllegalStateException("The registry's " + type + " records map '" + from + "' to both '"
					+ existing + "' and '" + to + "'; a substitution must be unambiguous");
	}

	/** The table length of {@code HashMap.newHashMap(mappings)}: the power of two at least {@code ceil(mappings / 0.75)}. */
	private static int hashMapTableSize(int mappings) {
		int capacity = (int) Math.ceil(mappings / 0.75);
		int size = 1;

		while (size < capacity)
			size <<= 1;

		return size;
	}

	/** {@code HashMap}'s bucket index for a key: its spread hash masked to the table length. */
	private static int hashMapBucket(String key, int buckets) {
		int hash = key.hashCode();
		return (hash ^ (hash >>> 16)) & (buckets - 1);
	}

	/**
	 * Fills the checked-in template. A template rather than a string literal in here because the
	 * thing being generated is Java: as a resource it can be read, reviewed and syntax-highlighted as
	 * the code it becomes, and this class stays about the derivation rather than about quoting.
	 */
	private static String emit(Path root, String fileDate, String registrySha256,
														 Map<String, List<String>> languageEquivalents, List<String[]> regionVariantEquivalents) {
		List<String> entries = new ArrayList<>();

		for (Map.Entry<String, List<String>> entry : languageEquivalents.entrySet()) {
			StringBuilder call = new StringBuilder("\t\tput(map, \"").append(entry.getKey()).append('"');

			for (String member : entry.getValue())
				call.append(", \"").append(member).append('"');

			entries.add(call.append(");").toString());
		}

		StringBuilder initializerCalls = new StringBuilder();
		StringBuilder initializerMethods = new StringBuilder();

		for (int start = 0, method = 0; start < entries.size(); start += ENTRIES_PER_METHOD, ++method) {
			String name = "putLanguageEquivalents" + method;
			initializerCalls.append("\t\t").append(name).append("(languageEquivalents);\n");
			initializerMethods.append("\n\tprivate static void ").append(name)
					.append("(Map<String, List<String>> map) {\n")
					.append(String.join("\n", entries.subList(start, Math.min(start + ENTRIES_PER_METHOD, entries.size()))))
					.append("\n\t}\n");
		}

		List<String> regionVariantEntries = new ArrayList<>();

		for (String[] substitution : regionVariantEquivalents)
			regionVariantEntries.add("\t\t\tnew String[] { \"" + substitution[0] + "\", \"" + substitution[1] + "\" }");

		// The whole class, not the members MINUS the key: keyed on the members alone every key
		// produces a different list and the count degenerates to the number of keys (781 for 369).
		Set<String> distinctClasses = new LinkedHashSet<>();

		for (Map.Entry<String, List<String>> entry : languageEquivalents.entrySet()) {
			List<String> whole = new ArrayList<>(entry.getValue());
			whole.add(entry.getKey());
			Collections.sort(whole);
			distinctClasses.add(whole.toString());
		}

		String generated = read(root.resolve(TEMPLATE))
				.replace("${FILE_DATE}", fileDate)
				.replace("${REGISTRY_SHA256}", registrySha256)
				.replace("${KEY_COUNT}", String.valueOf(languageEquivalents.size()))
				.replace("${CLASS_COUNT}", String.valueOf(distinctClasses.size()))
				.replace("${MAP_CAPACITY}", String.valueOf((int) Math.ceil(languageEquivalents.size() / 0.75)))
				.replace("${INITIALIZER_CALLS}", initializerCalls.toString().replaceAll("\n$", ""))
				.replace("${INITIALIZER_METHODS}", initializerMethods.toString())
				.replace("${REGION_VARIANT_COUNT}", String.valueOf(regionVariantEquivalents.size()))
				.replace("${REGION_VARIANT_ENTRIES}", String.join(",\n", regionVariantEntries));

		if (generated.contains("${"))
			throw new IllegalStateException(TEMPLATE + " has a placeholder this generator does not fill");

		return generated;
	}

	private static String read(Path path) {
		try {
			return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}
}
