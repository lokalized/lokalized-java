/*
 * Copyright 2026 Revetware LLC.
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Replays the shared exact Unicode identifier profile through public parser and runtime APIs. */
public class ExactIdentifierProfileTests {
  private static final String SHA256 = "3b20ec306ec5da919909e28a6db04085ad4cf9133d76adb6cf87f3631ee72e6f";

  @Test
  public void sharedExactIdentifierCases() throws Exception {
    byte[] bytes;
    try (InputStream input = getClass().getResourceAsStream("/exact-identifier-v1.json")) {
      assertNotNull(input);
      bytes = input.readAllBytes();
    }
    StringBuilder digest = new StringBuilder();
    for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
      digest.append(String.format(Locale.ROOT, "%02x", value & 0xff));
    assertEquals(SHA256, digest.toString());
    MinimalJson.JsonObject profile = MinimalJson.Json.parse(new String(bytes, StandardCharsets.UTF_8)).asObject();
    assertEquals("exact-identifier-v1", profile.get("profileID").asString());
    assertEquals("1.0.0", profile.get("profileVersion").asString());
    MinimalJson.JsonObject fixture = profile.get("fixture").asObject();
    Locale locale = Locale.forLanguageTag(fixture.get("locale").asString());
    int count = 0;
    for (MinimalJson.JsonValue value : profile.get("cases").asArray()) {
      runCase(fixture, locale, value.asObject());
      count++;
    }
    assertEquals(15, count);
  }

  private static void runCase(MinimalJson.JsonObject fixture, Locale locale, MinimalJson.JsonObject row) {
    String id = row.get("id").asString();
    String source = fixture.get("catalogs").asObject().get(row.get("catalog").asString()).asString();
    MinimalJson.JsonObject expected = row.get("expected").asObject();
    if (row.get("operation").asString().equals("parse")) {
      assertEquals("refused", expected.get("status").asString(), id);
      LocalizedStringLoadingException refusal = assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.parse(new StringReader(source), locale, id), id);
      assertEquals(expected.get("message").asString(), refusal.getMessage(), id);
      return;
    }
    List<LocalizedString> parsed = new ArrayList<>(LocalizedStringLoader.parse(new StringReader(source), locale, id));
    Map<Locale, List<LocalizedString>> catalogs = Map.of(locale, parsed);
    Strings strings = Strings.withFallbackLocale(locale)
        .localizedStringSupplier(() -> catalogs)
        .localeSupplier(matcher -> locale)
        .translationFailureHandler(failure -> TranslationFailureResponse.returnKey())
        .build();
    Map<String, Object> values = new LinkedHashMap<>();
    if (row.get("values") != null) {
      for (MinimalJson.JsonValue value : row.get("values").asArray()) {
        MinimalJson.JsonObject item = value.asObject();
        values.put(item.get("name").asString(), item.get("text").asString());
      }
    }
    TranslationResult result = strings.getResult(row.get("key").asString(), values, TranslationOptions.builder().build());
    assertEquals(expected.get("status").asString().equals("translated")
        ? TranslationResultStatus.TRANSLATED : TranslationResultStatus.RETURNED_KEY, result.getStatus(), id);
    assertEquals(expected.get("key").asString(), result.getKey(), id);
    assertEquals(expected.get("translation").asString(), result.getTranslation(), id);
    List<Locale> attempts = new ArrayList<>();
    for (MinimalJson.JsonValue value : expected.get("attemptedLocales").asArray())
      attempts.add(Locale.forLanguageTag(value.asString()));
    assertEquals(attempts, result.getAttemptedLocales(), id);
  }
}
