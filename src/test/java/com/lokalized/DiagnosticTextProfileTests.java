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
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executes the shared 1.1 diagnostic profile through the public catalog parser. */
public class DiagnosticTextProfileTests {
  @Test
  public void sharedCatalogDiagnostics() throws Exception {
    byte[] bytes;
    try (InputStream input = getClass().getResourceAsStream("/diagnostic-text-v1.1.json")) {
      assertNotNull(input);
      bytes = input.readAllBytes();
    }
    StringBuilder digest = new StringBuilder();
    for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
      digest.append(String.format(Locale.ROOT, "%02x", value & 0xff));
    assertEquals("1394c9136089b2b343f562f4ff7ade8d209f7b049b784fe1cc02eb041045c2a2", digest.toString());
    MinimalJson.JsonObject profile = MinimalJson.Json.parse(new String(bytes, StandardCharsets.UTF_8)).asObject();
    int executed = 0;
    for (MinimalJson.JsonValue value : profile.get("cases").asArray()) {
      MinimalJson.JsonObject row = value.asObject();
      if (!row.get("door").asString().equals("catalog")) continue;
      String id = row.get("id").asString();
      LocalizedStringLoadingException error = assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.parse(new StringReader(row.get("document").asString()),
              Locale.ENGLISH, row.get("source").asString()), id);
      assertEquals(row.get("expected").asObject().get("message").asString(), error.getMessage(), id);
      assertWellFormed(error.getMessage(), id);
      executed++;
    }
    // Java has no manifest parser; those rows are qualified by JS and Swift.
    assertEquals(18, executed);
  }

  private static void assertWellFormed(String text, String id) {
    for (int i = 0; i < text.length(); i++) {
      char unit = text.charAt(i);
      if (Character.isHighSurrogate(unit)) {
        assertTrue(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)), id);
        i++;
      } else assertFalse(Character.isLowSurrogate(unit), id);
    }
  }
}
