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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Replays the versioned portable fallback-observer profile using public APIs. */
public class FallbackObserverProfileTests {
  private static final String SHA256 = "4c844d73e8d333dde8432cb9e76fcdeb22b4937b50a632205fe74855b6e57d18";

  @Test
  public void sharedObserverCases() throws Exception {
    byte[] bytes;
    try (InputStream input = getClass().getResourceAsStream("/fallback-observer-v1.json")) {
      assertNotNull(input);
      bytes = input.readAllBytes();
    }
    StringBuilder digest = new StringBuilder();
    for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
      digest.append(String.format(Locale.ROOT, "%02x", value & 0xff));
    assertEquals(SHA256, digest.toString());
    MinimalJson.JsonObject profile = MinimalJson.Json.parse(new String(bytes, StandardCharsets.UTF_8)).asObject();
    assertEquals("fallback-observer-v1", profile.get("profileID").asString());
    assertEquals("1.0.0", profile.get("profileVersion").asString());
    MinimalJson.JsonObject fixture = profile.get("fixture").asObject();
    int count = 0;
    for (MinimalJson.JsonValue value : profile.get("cases").asArray()) {
      runCase(fixture, value.asObject());
      count++;
    }
    assertEquals(12, count);
  }

  private static void runCase(MinimalJson.JsonObject fixture, MinimalJson.JsonObject row) throws Exception {
    String id = row.get("id").asString();
    String key = row.get("key").asString();
    String behavior = row.get("observer").asString();
    MinimalJson.JsonObject expected = row.get("expected").asObject();
    Map<Locale, List<LocalizedString>> catalogs = new LinkedHashMap<>();
    MinimalJson.JsonObject catalogJSON = row.get("catalogVariant") == null ? fixture.get("catalogs").asObject()
        : fixture.get("catalogVariants").asObject().get(row.get("catalogVariant").asString()).asObject();
    for (String tag : catalogJSON.names()) {
      MinimalJson.JsonObject catalog = catalogJSON.get(tag).asObject();
      Locale locale = Locale.forLanguageTag(tag);
      catalogs.put(locale, new ArrayList<>(LocalizedStringLoader.parse(new StringReader(catalog.toString()), locale, "observer-profile")));
    }
    Map<String, List<Locale>> ties = new LinkedHashMap<>();
    MinimalJson.JsonObject tieJSON = fixture.get("tiebreakerLocalesByLanguageCode").asObject();
    for (String language : tieJSON.names())
      ties.put(language, tags(tieJSON.get(language)));
    List<TranslationFallbackEvent> instanceEvents = new ArrayList<>();
    List<TranslationFallbackEvent> perCallEvents = new ArrayList<>();
    List<TranslationResult> nestedResults = new ArrayList<>();
    AtomicReference<Strings> reentry = new AtomicReference<>();
    List<String> policyCalls = new ArrayList<>();
    List<Throwable> policyCauses = new ArrayList<>();
    int[] handlerCalls = {0};
    RuntimeException marker = new IllegalStateException("observer marker");
    RuntimeException firstCause = new IllegalStateException("first cause");
    RuntimeException secondCause = new IllegalStateException("second cause");
    Map<String, Object> placeholders = null;
    if (row.get("placeholderMode") != null) {
      switch (row.get("placeholderMode").asString()) {
        case "tier-two": placeholders = Map.of("tier", 2); break;
        case "throwing-two": placeholders = Map.of(
            "x", new Object() { @Override public String toString() { throw firstCause; } },
            "y", new Object() { @Override public String toString() { throw secondCause; } }); break;
        default: throw new IllegalArgumentException("Unknown placeholder mode");
      }
    }
    Strings.Builder builder = Strings.withFallbackLocale(Locale.forLanguageTag(fixture.get("fallbackLocale").asString()))
        .localizedStringSupplier(() -> catalogs)
        .tiebreakerLocalesByLanguageCode(ties);
    if (row.get("requestMode") != null && row.get("requestMode").asString().equals("negotiated"))
      builder.localeMatchSupplier(matcher -> matcher.matchFor(Locale.forLanguageTag(fixture.get("negotiationRequestLocale").asString())));
    else builder.localeSupplier(matcher -> Locale.forLanguageTag(fixture.get("requestLocale").asString()));
    Strings strings = builder
        .translationFallbackObserver(event -> {
          instanceEvents.add(event);
          if (behavior.equals("throw")) throw marker;
          if (row.get("nestedKey") != null && event.getKey().equals(key))
            nestedResults.add(reentry.get().getResult(row.get("nestedKey").asString()));
        })
        .translationFallbackPolicy((reason, locale, cause) -> {
          policyCalls.add(locale.toLanguageTag() + ":" + reason(reason));
          policyCauses.add(cause);
          return row.get("policy").asString().equals("advance");
        })
        .translationFailureHandler(failure -> {
          handlerCalls[0]++;
          return TranslationFailureResponse.returnKey();
        }).build();
    reentry.set(strings);
    TranslationOptions options = behavior.equals("replace")
        ? TranslationOptions.builder().translationFallbackObserver(perCallEvents::add).build()
        : behavior.equals("inherit")
        ? TranslationOptions.builder().translationFallbackObserver(null).build()
        : TranslationOptions.builder().build();
    Map<String, Object> values = placeholders;
    if (expected.get("outcome").asString().equals("observer-threw"))
      assertSame(marker, assertThrows(RuntimeException.class, () -> strings.getResult(key, values, options), id), id);
    else {
      TranslationResult result = strings.getResult(key, values, options);
      String outcome = result.getStatus() == TranslationResultStatus.TRANSLATED ? "translated" : "returned-key";
      assertEquals(expected.get("outcome").asString(), outcome, id);
      assertEquals(expected.get("translation").asString(), result.getTranslation(), id);
      assertEquals(tags(expected.get("attemptedLocales")), result.getAttemptedLocales(), id);
      if (expected.get("isFallback") != null)
        assertEquals(expected.get("isFallback").asBoolean(), result.isFallback(), id);
      List<TranslationFallbackEvent> observed = instanceEvents.isEmpty() ? perCallEvents : instanceEvents;
      if (!observed.isEmpty()) {
        assertNotNull(result.getLocaleMatchResult().orElse(null), id);
        assertSame(result.getLocaleMatchResult().orElse(null), observed.get(0).getLocaleMatchResult().orElse(null), id);
      }
    }
    assertEquals(strings(expected.get("policyCalls")), policyCalls, id);
    assertEquals(expected.get("handlerCalls").asInt(), handlerCalls[0], id);
    assertEvents(expected.get("instanceEvents"), instanceEvents, id);
    assertEvents(expected.get("perCallEvents"), perCallEvents, id);
    if (expected.get("nestedResult") == null) assertEquals(0, nestedResults.size(), id);
    else {
      assertEquals(1, nestedResults.size(), id);
      MinimalJson.JsonObject nested = expected.get("nestedResult").asObject();
      TranslationResult nestedResult = nestedResults.get(0);
      assertEquals(nested.get("key").asString(), nestedResult.getKey(), id);
      assertEquals(nested.get("translation").asString(), nestedResult.getTranslation(), id);
      assertEquals(tags(nested.get("attemptedLocales")), nestedResult.getAttemptedLocales(), id);
      assertSame(nestedResult.getLocaleMatchResult().orElse(null),
          instanceEvents.get(1).getLocaleMatchResult().orElse(null), id);
    }
    if (expected.get("causeIdentity") != null) {
      assertEquals("distinct-policy-matched", expected.get("causeIdentity").asString(), id);
      List<TranslationFallbackEvent.PrecedingFailure> failures = instanceEvents.get(0).getPrecedingFailures();
      assertSame(firstCause, failures.get(0).getCause().orElse(null), id);
      assertSame(secondCause, failures.get(1).getCause().orElse(null), id);
      assertSame(firstCause, policyCauses.get(0), id);
      assertSame(secondCause, policyCauses.get(1), id);
    }
  }

  private static void assertEvents(MinimalJson.JsonValue expected, List<TranslationFallbackEvent> actual, String id) {
    assertEquals(expected.asArray().size(), actual.size(), id);
    for (int index = 0; index < actual.size(); index++) {
      MinimalJson.JsonObject value = expected.asArray().get(index).asObject();
      TranslationFallbackEvent event = actual.get(index);
      assertEquals(value.get("key").asString(), event.getKey(), id);
      assertEquals(value.get("lookupLocale").asString(), event.getLookupLocale().toLanguageTag(), id);
      assertEquals(value.get("resolvedLocale").asString(), event.getResolvedLocale().toLanguageTag(), id);
      assertEquals(tags(value.get("attemptedLocales")), event.getAttemptedLocales(), id);
      List<String> failures = event.getPrecedingFailures().stream()
          .map(failure -> failure.getLocale().toLanguageTag() + ":" + reason(failure.getReason())).collect(Collectors.toList());
      assertEquals(strings(value.get("precedingFailures")), failures, id);
      for (TranslationFallbackEvent.PrecedingFailure failure : event.getPrecedingFailures())
        assertEquals(failure.getReason() == TranslationFailureReason.RESOLUTION_FAILURE,
            failure.getCause().isPresent(), id);
    }
  }

  private static String reason(TranslationFailureReason reason) {
    return reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static List<String> strings(MinimalJson.JsonValue value) {
    List<String> values = new ArrayList<>();
    for (MinimalJson.JsonValue item : value.asArray()) values.add(item.asString());
    return values;
  }

  private static List<Locale> tags(MinimalJson.JsonValue value) {
    return strings(value).stream().map(Locale::forLanguageTag).collect(Collectors.toList());
  }
}
