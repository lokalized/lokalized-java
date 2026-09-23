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

import com.lokalized.iana.IanaEquivalencesGenerator;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the checked-in IANA equivalence table against generator drift, pins the two properties
 * the table exists for, and pins the two ORDER derivations the generator makes from the registry
 * alone — each with inputs on which a plausible wrong rule gives a different answer.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class IanaEquivalencesGeneratorTests {
  @Test
  public void checkedInIanaTableMatchesPinnedRegistryAndGenerator() {
    IanaEquivalencesGenerator.main(new String[] {System.getProperty("user.dir"), "--check"});
  }

  @Test
  public void theTableResolvesDeprecationsNewerThanAnyBundledJdkSnapshot() {
    // THE POINT OF THE CHANGE. Each of these was deprecated after the registry snapshot bundled in
    // the reference JDK, so `LanguageRange.parse` does not expand them and a deployment's answer
    // depends on its JVM. Asserted through the table rather than against `parse`, because `parse`
    // is exactly what this no longer consults.
    assertEquals(List.of("yol", "enm"), ranges("yol"));
    assertEquals(List.of("enm", "yol"), ranges("enm"));
    assertEquals(List.of("mrd", "mgp"), ranges("mrd"));
    assertEquals(List.of("shl", "mrh"), ranges("shl"));
  }

  @Test
  public void whatJdk17AndLaterAlreadyAnsweredIsUnchanged() {
    // THE OTHER HALF, and without it the test above is satisfied by a table that resolves the four
    // and mangles everything else. These are what `LanguageRange.parse` answers on JDK 17, 21, 25, 26 and
    // 27 (measured; JDK 11 was not, though CI's JDK 11 leg passed the comparison this replaced),
    // including a region substitution that is deliberately still the JDK's.
    //
    // PINNED AS LITERALS, NOT COMPARED WITH THE RUNNING JDK. This test used to assert equality with
    // `LanguageRange.parse` on whatever JDK ran it, on the premise that every supported JDK carries
    // these equivalences. JDK 9, the supported floor, does not: its parse answers `sgn-be-fr` without
    // `sgn-sfb`, and it differs from this table on 646 of its 781 tags, 592 of them expanding to
    // nothing at all (measured 2026-09-23 on Zulu 9.0.7.1, where this test was the only failure in the
    // build). The table answers the same on every JDK, which is the point of it, so the expected
    // answers are the table's, measured identical on JDK 9, 17, 21 and 27.
    assertEquals(List.of(new LanguageRange("iw"), new LanguageRange("he")), IanaLanguageEquivalents.parse("iw"));
    assertEquals(List.of(new LanguageRange("zh-cmn"), new LanguageRange("zh-guoyu"), new LanguageRange("cmn")),
        IanaLanguageEquivalents.parse("zh-cmn"));
    assertEquals(List.of(new LanguageRange("sgn-be-fr"), new LanguageRange("sgn-sfb"), new LanguageRange("sfb"),
        new LanguageRange("sgn-be-fx")), IanaLanguageEquivalents.parse("sgn-be-fr"));
    assertEquals(List.of(new LanguageRange("de-de"), new LanguageRange("de-dd")), IanaLanguageEquivalents.parse("de-DE"));
    assertEquals(List.of(new LanguageRange("en-us"), new LanguageRange("fr", 0.5)),
        IanaLanguageEquivalents.parse("en-US,fr;q=0.5"));
  }

  @Test
  public void theRegistrySnapshotIsPinnedAndDated() {
    assertTrue(IanaLanguageEquivalents.REGISTRY_FILE_DATE.matches("\\d{4}-\\d{2}-\\d{2}"),
        "the generated table must record the File-Date of the snapshot it came from, and it is "
            + IanaLanguageEquivalents.REGISTRY_FILE_DATE);
  }

  @Test
  public void classMembersFollowTheirPreferredValueThenTheRecordThatFirstNamesThem() {
    // zz is named LAST and is the only member without a Preferred-Value, and bb is named before aa.
    // Alphabetical order, record order alone, and "preferred member first, then alphabetical" each
    // give a different answer for at least one key.
    String registry = registry(
        "Type: language\nSubtag: bb\nDescription: B\nAdded: 2000-01-01\nPreferred-Value: zz",
        "Type: language\nSubtag: aa\nDescription: A\nAdded: 2000-01-01\nPreferred-Value: zz",
        "Type: language\nSubtag: zz\nDescription: Z\nAdded: 2000-01-01",
        // An extlang's own subtag names itself as its Preferred-Value; only its Prefix-Subtag form is
        // deprecated in favour of another tag, so qq, not sgn-qq, is the preferred member.
        "Type: language\nSubtag: qq\nDescription: Q\nAdded: 2000-01-01",
        "Type: extlang\nSubtag: qq\nDescription: Q\nAdded: 2000-01-01\nPreferred-Value: qq\nPrefix: sgn");

    Map<String, List<String>> expected = new HashMap<>();
    expected.put("aa", List.of("zz", "bb"));
    expected.put("bb", List.of("zz", "aa"));
    expected.put("zz", List.of("bb", "aa"));
    expected.put("qq", List.of("sgn-qq"));
    expected.put("sgn-qq", List.of("qq"));

    assertEquals(expected, IanaEquivalencesGenerator.languageEquivalents(registry));
  }

  @Test
  public void aClassWithoutExactlyOnePreferredMemberIsRefused() {
    // The order is defined only when one member has no Preferred-Value; guessing would be silent.
    String twoWithoutPreferredValue = registry(
        "Type: language\nSubtag: xx\nDescription: X\nAdded: 2000-01-01",
        "Type: extlang\nSubtag: ab\nDescription: A\nAdded: 2000-01-01\nPrefix: xx");

    assertThrows(IllegalStateException.class, () -> IanaEquivalencesGenerator.languageEquivalents(twoWithoutPreferredValue));
  }

  @Test
  public void aMemberThatIsNotTheFirstTagItsRecordNamesIsRefused() {
    // An extlang whose Prefix and Preferred-Value are different languages puts two tags named by one record
    // into one class; their relative order is then undefined, so the generator must refuse rather than guess.
    String sharedRecord = registry(
        "Type: language\nSubtag: zz\nDescription: Z\nAdded: 2000-01-01",
        "Type: language\nSubtag: xx\nDescription: X\nAdded: 2000-01-01",
        "Type: extlang\nSubtag: ab\nDescription: A\nAdded: 2000-01-01\nPreferred-Value: zz\nPrefix: xx");

    assertThrows(IllegalStateException.class, () -> IanaEquivalencesGenerator.languageEquivalents(sharedRecord));
  }

  @Test
  public void aHashMapBucketTheOrderModelCannotDescribeIsRefused() {
    // Four region records whose eight keys all land in bucket 0 of the 16-bucket table HashMap uses for
    // eight keys. The generator refuses once a bucket reaches eight, one entry before HashMap would
    // reorder it, so this must be refused rather than ordered.
    List<String> bucketZero = new ArrayList<>();
    for (char first = 'A'; first <= 'Z'; ++first)
      for (char second = 'A'; second <= 'Z'; ++second) {
        int hash = ("-" + Character.toLowerCase(first) + Character.toLowerCase(second)).hashCode();
        if (((hash ^ (hash >>> 16)) & 15) == 0)
          bucketZero.add("" + first + second);
      }

    List<String> records = new ArrayList<>();
    for (int index = 0; index + 1 < bucketZero.size() && records.size() < 4; index += 2)
      records.add("Type: region\nSubtag: " + bucketZero.get(index) + "\nDescription: R\nAdded: 2000-01-01\nPreferred-Value: "
          + bucketZero.get(index + 1));
    assertEquals(4, records.size(), "the search must find eight bucket-0 codes, or this test proves nothing");

    String crowded = registry(records.toArray(new String[0]));
    assertThrows(IllegalStateException.class, () -> IanaEquivalencesGenerator.regionVariantEquivalents(crowded));
  }

  @Test
  public void regionAndVariantSubstitutionsAreTriedInJdkHashMapOrder() throws IOException {
    // Read reflectively from sun.util.locale.LocaleEquivalentMaps.regionVariantEquivMap on JDK 17, 21,
    // 25, 26 and 27, which all iterate it in this order; neither alphabetical nor registry order.
    List<String> jdkOrder = List.of("-bu>-mm", "-tl>-tp", "-zr>-cd", "-tp>-tl", "-dd>-de", "-mm>-bu", "-cd>-zr",
        "-de>-dd", "-heploc>-alalc97", "-alalc97>-heploc", "-yd>-ye", "-fr>-fx", "-ye>-yd", "-fx>-fr");

    List<String> derived = substitutions(IanaEquivalencesGenerator.regionVariantEquivalents(pinnedRegistry()));
    assertEquals(jdkOrder, derived);

    // The generator computes HashMap's bucket order rather than asking a HashMap. Cross-check it
    // against a real one built as JDK 21 builds the map: HashMap.newHashMap(14) is new HashMap<>(19),
    // with the keys put in ascending order.
    Map<String, String> ascending = new TreeMap<>();
    for (String substitution : jdkOrder)
      ascending.put(substitution.split(">")[0], substitution.split(">")[1]);
    Map<String, String> hashMap = new HashMap<>(19);
    for (Map.Entry<String, String> entry : ascending.entrySet())
      hashMap.put(entry.getKey(), entry.getValue());
    List<String> iterated = new ArrayList<>();
    for (Map.Entry<String, String> entry : hashMap.entrySet())
      iterated.add(entry.getKey() + ">" + entry.getValue());
    assertEquals(iterated, derived);
  }

  @Test
  public void regionAndVariantOrderDoesNotDependOnRecordOrder() throws IOException {
    // The same seven records in reverse registry order must give the same substitution order.
    String reversed = registry(
        "Type: variant\nSubtag: heploc\nDescription: H\nAdded: 2000-01-01\nPreferred-Value: alalc97",
        "Type: region\nSubtag: ZR\nDescription: Z\nAdded: 2000-01-01\nPreferred-Value: CD",
        "Type: region\nSubtag: YD\nDescription: Y\nAdded: 2000-01-01\nPreferred-Value: YE",
        "Type: region\nSubtag: TP\nDescription: T\nAdded: 2000-01-01\nPreferred-Value: TL",
        "Type: region\nSubtag: FX\nDescription: F\nAdded: 2000-01-01\nPreferred-Value: FR",
        "Type: region\nSubtag: DD\nDescription: D\nAdded: 2000-01-01\nPreferred-Value: DE",
        "Type: region\nSubtag: BU\nDescription: B\nAdded: 2000-01-01\nPreferred-Value: MM");

    assertEquals(substitutions(IanaEquivalencesGenerator.regionVariantEquivalents(pinnedRegistry())),
        substitutions(IanaEquivalencesGenerator.regionVariantEquivalents(reversed)));
  }

  @Test
  public void anAmbiguousRegionSubstitutionIsRefused() {
    // Two regions deprecated in favour of one would map it back to both.
    String ambiguous = registry(
        "Type: region\nSubtag: AA\nDescription: A\nAdded: 2000-01-01\nPreferred-Value: CC",
        "Type: region\nSubtag: BB\nDescription: B\nAdded: 2000-01-01\nPreferred-Value: CC");

    assertThrows(IllegalStateException.class, () -> IanaEquivalencesGenerator.regionVariantEquivalents(ambiguous));
  }

  private static String registry(String... records) {
    return "File-Date: 2000-01-01\n%%\n" + String.join("\n%%\n", records) + "\n";
  }

  private static String pinnedRegistry() throws IOException {
    return new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir"),
        "src/build/resources/iana/language-subtag-registry.txt")), StandardCharsets.UTF_8);
  }

  private static List<String> substitutions(List<String[]> pairs) {
    List<String> substitutions = new ArrayList<>(pairs.size());
    for (String[] pair : pairs)
      substitutions.add(pair[0] + ">" + pair[1]);
    return substitutions;
  }

  private static List<String> ranges(String header) {
    return IanaLanguageEquivalents.parse(header).stream().map(LanguageRange::getRange).collect(java.util.stream.Collectors.toList());
  }
}
