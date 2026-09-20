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

import java.util.List;
import java.util.Locale.LanguageRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the checked-in IANA equivalence table against generator drift, and pins the two
 * properties the table exists for.
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
  public void everythingTheJdkAlreadyKnewIsUnchanged() {
    // THE OTHER HALF, and without it the test above is satisfied by a table that resolves the four
    // and mangles everything else. These are equivalences both the registry and every supported
    // JDK carry, including a region substitution that is deliberately still the JDK's.
    assertEquals(LanguageRange.parse("iw"), IanaLanguageEquivalents.parse("iw"));
    assertEquals(LanguageRange.parse("zh-cmn"), IanaLanguageEquivalents.parse("zh-cmn"));
    assertEquals(LanguageRange.parse("sgn-be-fr"), IanaLanguageEquivalents.parse("sgn-be-fr"));
    assertEquals(LanguageRange.parse("de-DE"), IanaLanguageEquivalents.parse("de-DE"));
    assertEquals(LanguageRange.parse("en-US,fr;q=0.5"), IanaLanguageEquivalents.parse("en-US,fr;q=0.5"));
  }

  @Test
  public void theRegistrySnapshotIsPinnedAndDated() {
    assertTrue(IanaLanguageEquivalents.REGISTRY_FILE_DATE.matches("\\d{4}-\\d{2}-\\d{2}"),
        "the generated table must record the File-Date of the snapshot it came from, and it is "
            + IanaLanguageEquivalents.REGISTRY_FILE_DATE);
  }

  private static List<String> ranges(String header) {
    return IanaLanguageEquivalents.parse(header).stream().map(LanguageRange::getRange).collect(java.util.stream.Collectors.toList());
  }
}
