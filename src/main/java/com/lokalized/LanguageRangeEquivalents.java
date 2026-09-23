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

/**
 * Controls where Lokalized takes IANA language-range equivalences from when it parses language ranges, such as the
 * {@code he}/{@code iw} pair that makes a request for either language also consider the other.
 * <p>
 * The source decides which equivalent ranges {@link LocaleMatcher#parseLanguageRanges(String)} and
 * {@link LocaleMatcher#bestMatchForAcceptLanguage(String)} add, and which ranges locale matching treats as equivalent,
 * so it can change which loaded locale a request selects. Configure it with
 * {@link Strings.Builder#languageRangeEquivalents(LanguageRangeEquivalents)}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.1.0
 */
public enum LanguageRangeEquivalents {
  /**
   * Use the IANA Language Subtag Registry snapshot bundled in Lokalized: {@code File-Date: 2026-09-17}, SHA-256
   * {@code 755fad43283be7b41ebe3c89ad054b6eaf928f404f9c0edb74799e0eab74beb1}.
   * <p>
   * The equivalents it supplies are identical on every JDK. This is the default.
   */
  IANA_REGISTRY,
  /**
   * Use the running JDK's own table, through {@link java.util.Locale.LanguageRange#parse(String)}.
   * <p>
   * That table is bundled with the JDK and differs between JDK releases, including update releases, so the same
   * request can select a different locale on a different JVM. This reproduces the behavior of Lokalized 3.0.0, which
   * always used the running JDK's table.
   */
  JDK
}
