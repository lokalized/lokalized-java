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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates {@code com.lokalized.IanaLanguageEquivalents} from a pinned IANA Language Subtag
 * Registry snapshot.
 * <p>
 * Run by {@code IanaEquivalencesGeneratorTests}, which calls {@code main(root, "--check")} so the
 * checked-in artifact cannot drift from the inputs it claims to come from. Same contract as
 * {@code CldrDataGenerator}.
 * <p>
 * <strong>BOTH INPUTS ARE VENDORED, and that is the point rather than a convenience.</strong>
 * {@code language-subtag-registry.txt} supplies the equivalences; {@code
 * jdk-language-range-equivalents.json} supplies the ORDER. The order could be recovered by calling
 * {@link java.util.Locale.LanguageRange#parse(String)} here instead — this class runs on a JVM,
 * after all — and that would make the generated output depend on the generating JDK, which is the
 * exact disease this whole change exists to cure. Vendored, generation is deterministic on any JDK.
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
	private static final String JDK_CLOSURE = "src/build/resources/iana/jdk-language-range-closure.txt";
	private static final String OUTPUT = "src/main/java/com/lokalized/IanaLanguageEquivalents.java";
	private static final String TEMPLATE = "src/build/resources/iana/IanaLanguageEquivalents.template";

	/**
	 * The JDK's {@code regionVariantEquivMap}, needed only to UNPICK it back out of the recorded
	 * closure: that artifact stores whole {@code parse} outputs, with region equivalents mixed in
	 * among the language ones.
	 */
	private static final String[][] REGION_VARIANT = {
			{ "-bu", "-mm" }, { "-tl", "-tp" }, { "-zr", "-cd" }, { "-tp", "-tl" }, { "-dd", "-de" },
			{ "-mm", "-bu" }, { "-cd", "-zr" }, { "-de", "-dd" }, { "-heploc", "-alalc97" },
			{ "-alalc97", "-heploc" }, { "-yd", "-ye" }, { "-fr", "-fx" }, { "-ye", "-yd" }, { "-fx", "-fr" } };

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
		Map<String, List<String>> jdkClosure = readJdkClosure(root.resolve(JDK_CLOSURE));

		Matcher fileDate = Pattern.compile("^File-Date:\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.MULTILINE).matcher(registry);

		if (!fileDate.find())
			throw new IllegalStateException(REGISTRY + " carries no File-Date header");

		Map<String, List<String>> closure = registryClosure(registry);
		return emit(root, fileDate.group(1), registrySha256, closure, jdkClosure);
	}

	/**
	 * The registry's own equivalences, as a map from each subtag to the OTHERS in its class.
	 * <p>
	 * <strong>Record TYPE is discriminated, and the first version of this did not do it.</strong>
	 * Running union-find over every {@code Preferred-Value} regardless of type flattens six REGION
	 * records (DD-&gt;DE, FX-&gt;FR, BU-&gt;MM, ZR-&gt;CD, TP-&gt;TL, YD-&gt;YE) and one VARIANT record
	 * (heploc-&gt;alalc97) into the language namespace. That is not a keying nuance: it claims
	 * {@code de} is equivalent to {@code dd}, so the range {@code de} — German — would expand to a
	 * region deprecated in 1990. The JDK keeps those fourteen in a separate map whose keys all carry
	 * a leading hyphen, which is what confines them to non-initial subtag positions; they are kept
	 * out here for the same reason and are reproduced verbatim in the generated class.
	 */
	private static Map<String, List<String>> registryClosure(String registry) {
		Map<String, String> parent = new HashMap<>();
		List<String[]> pairs = new ArrayList<>();

		for (String block : registry.split("\n%%\n")) {
			Map<String, String> record = new HashMap<>();
			String key = null;

			for (String line : block.split("\n")) {
				Matcher field = Pattern.compile("^([A-Za-z-]+):\\s*(.*)$").matcher(line);

				if (field.matches()) {
					key = field.group(1);
					record.putIfAbsent(key, field.group(2));
				}
			}

			String type = record.get("Type");
			String subtag = record.containsKey("Subtag") ? record.get("Subtag") : record.get("Tag");

			if (subtag == null)
				continue;

			String preferred = record.get("Preferred-Value");

			if (preferred != null && !"region".equals(type) && !"variant".equals(type))
				pairs.add(new String[] { lower(subtag), lower(preferred) });

			if ("extlang".equals(type) && record.get("Prefix") != null)
				pairs.add(new String[] { lower(subtag), lower(record.get("Prefix") + "-" + subtag) });
		}

		for (String[] pair : pairs) {
			String left = find(parent, pair[0]), right = find(parent, pair[1]);
			if (!left.equals(right))
				parent.put(left, right);
		}

		Map<String, Set<String>> grouped = new TreeMap<>();
		for (String member : new ArrayList<>(parent.keySet()))
			grouped.computeIfAbsent(find(parent, member), unused -> new LinkedHashSet<>()).add(member);

		Map<String, List<String>> closure = new TreeMap<>();
		for (Set<String> members : grouped.values()) {
			List<String> sorted = new ArrayList<>(members);
			java.util.Collections.sort(sorted);

			for (String member : sorted) {
				List<String> others = new ArrayList<>(sorted);
				others.remove(member);
				closure.put(member, others);
			}
		}

		return closure;
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
	 * The JDK's language-equivalent order for one key, recovered from its recorded {@code parse}
	 * output.
	 * <p>
	 * <strong>A plain index lookup is WRONG and was measured so — it took the divergence from the
	 * JDK to 8,928 of 115,487 probes.</strong> The recorded class is a whole {@code parse} output
	 * with the REGION equivalents mixed in: {@code sgn-be-fr}'s is
	 * {@code [sgn-be-fr, sgn-sfb, sfb, sgn-be-fx]} and {@code sgn-be-fx} is a region equivalent, not
	 * a language one, so ranking against it scrambles the sequence it appears to preserve.
	 * <p>
	 * This inverts {@code parse}'s insertion instead. Every derived range is inserted at
	 * {@code index + 1}, so the class is the key followed by its insertions in REVERSE time order,
	 * and the insertions are, in time order, {@code rv(key)} then for each language equivalent both
	 * {@code e} and {@code rv(e)}. Walking the reversed tail with {@code rv} in hand recovers the
	 * language equivalents in the JDK's own order.
	 */
	private static List<String> recoverJdkOrder(String key, List<String> recordedClass) {
		List<String> insertions = new ArrayList<>(recordedClass.subList(1, recordedClass.size()));
		java.util.Collections.reverse(insertions);

		Set<String> seen = new LinkedHashSet<>(List.of(key));
		List<String> equivalents = new ArrayList<>();
		int[] cursor = { 0 };

		String regionVariant = regionVariantEquivalent(key);
		if (regionVariant != null && !seen.contains(regionVariant)
				&& cursor[0] < insertions.size() && insertions.get(cursor[0]).equals(regionVariant)) {
			seen.add(regionVariant);
			++cursor[0];
		}

		while (cursor[0] < insertions.size()) {
			String equivalent = insertions.get(cursor[0]++);
			equivalents.add(equivalent);
			seen.add(equivalent);

			String derived = regionVariantEquivalent(equivalent);
			if (derived != null && !seen.contains(derived)
					&& cursor[0] < insertions.size() && insertions.get(cursor[0]).equals(derived)) {
				seen.add(derived);
				++cursor[0];
			}
		}

		return equivalents;
	}

	/** {@code LocaleMatcher#getEquivalentForRegionAndVariant}, needed only by the recovery above. */
	private static String regionVariantEquivalent(String range) {
		int keyIndex = extensionKeyIndex(range);

		for (String[] pair : REGION_VARIANT) {
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

	private static Map<String, List<String>> readJdkClosure(Path path) {
		Map<String, List<String>> closure = new TreeMap<>();

		for (String line : read(path).split("\n")) {
			if (line.trim().isEmpty() || line.startsWith("#"))
				continue;

			List<String> parts = Arrays.asList(line.trim().split(" "));
			closure.put(parts.get(0), parts);
		}

		return closure;
	}

	/**
	 * Fills the checked-in template. A template rather than a string literal in here because the
	 * thing being generated is Java: as a resource it can be read, reviewed and syntax-highlighted as
	 * the code it becomes, and this class stays about the derivation rather than about quoting.
	 */
	private static String emit(Path root, String fileDate, String registrySha256,
			Map<String, List<String>> closure,
															 Map<String, List<String>> jdkClosure) {
		List<String> entries = new ArrayList<>();

		for (Map.Entry<String, List<String>> entry : closure.entrySet()) {
			List<String> members = orderedMembers(entry.getKey(), entry.getValue(), jdkClosure);
			List<String> quoted = new ArrayList<>();

			for (String member : members)
				quoted.add('"' + member + '"');

			entries.add("\t\t\tMap.entry(\"" + entry.getKey() + "\", List.of(" + String.join(", ", quoted) + "))");
		}

		// The whole class, not the members MINUS the key: keyed on the members alone every key
		// produces a different list and the count degenerates to the number of keys (781 for 369).
		Set<String> distinctClasses = new LinkedHashSet<>();

		for (Map.Entry<String, List<String>> entry : closure.entrySet()) {
			List<String> whole = new ArrayList<>(entry.getValue());
			whole.add(entry.getKey());
			java.util.Collections.sort(whole);
			distinctClasses.add(whole.toString());
		}

		int classes = distinctClasses.size();

		return read(root.resolve(TEMPLATE))
				.replace("${FILE_DATE}", fileDate)
				.replace("${REGISTRY_SHA256}", registrySha256)
				.replace("${KEY_COUNT}", String.valueOf(closure.size()))
				.replace("${CLASS_COUNT}", String.valueOf(classes))
				.replace("${ENTRIES}", String.join(",\n", entries));
	}

	/**
	 * A class's members in the JDK's own order where the JDK knows the key, alphabetical otherwise.
	 * <p>
	 * Ordering by anything else turns a table swap into a table-and-ordering swap: measured,
	 * alphabetical throughout diverged from the JDK on 41 of 115,487 probes with identical
	 * membership. The keys the JDK does not know are the registry-only ones, where it has no opinion.
	 */
	private static List<String> orderedMembers(String key, List<String> members,
																				 Map<String, List<String>> jdkClosure) {
		List<String> ordered = new ArrayList<>(members);
		List<String> recorded = jdkClosure.get(key);

		if (recorded == null) {
			java.util.Collections.sort(ordered);
			return ordered;
		}

		List<String> jdkOrder = recoverJdkOrder(key, recorded);
		ordered.sort((left, right) -> {
			int leftRank = jdkOrder.indexOf(left), rightRank = jdkOrder.indexOf(right);
			if (leftRank == -1) leftRank = Integer.MAX_VALUE;
			if (rightRank == -1) rightRank = Integer.MAX_VALUE;
			return leftRank != rightRank ? Integer.compare(leftRank, rightRank) : left.compareTo(right);
		});

		return ordered;
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
