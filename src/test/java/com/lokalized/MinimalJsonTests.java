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

import com.lokalized.MinimalJson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link MinimalJson}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class MinimalJsonTests {
  @Test
  public void hashIndexTableRemoveHandlesUnsignedByteIndexes() {
    JsonObject jsonObject = MinimalJson.Json.object();

    for (int i = 0; i < 127; ++i)
      jsonObject.add("name" + i, i);

    jsonObject.add("target", "value");
    jsonObject.remove("name0");

    assertEquals("value", jsonObject.getString("target", null));
  }

  @Test
  public void jsonParserResetsNestingLevelBetweenParsesAfterFailure() {
    MinimalJson.JsonParser jsonParser = new MinimalJson.JsonParser(new MinimalJson.JsonHandler<Object, Object>() {
    });

    assertThrows(MinimalJson.ParseException.class,
        () -> jsonParser.parse(deeplyNestedArray(1_001)),
        "Expected arrays beyond the nesting limit to fail");

    assertDoesNotThrow(() -> jsonParser.parse("[]"));
  }

  @Test
  public void jsonParserResetsCaptureBufferBetweenParsesAfterFailure() {
    List<String> strings = new ArrayList<>();
    MinimalJson.JsonParser jsonParser = new MinimalJson.JsonParser(new MinimalJson.JsonHandler<Object, Object>() {
      @Override
      public void endString(String string) {
        strings.add(string);
      }
    });

    assertThrows(MinimalJson.ParseException.class,
        () -> jsonParser.parse("\"unterminated"),
        "Expected unterminated strings to fail");

    jsonParser.parse("\"ok\"");

    assertEquals(List.of("ok"), strings);
  }

  @Test
  public void jsonParserAcceptsPairedSurrogatesAcrossLiteralAndEscapedCharacters() throws Exception {
    String smilingFace = new String(Character.toChars(0x1F642));
    char highSurrogate = (char) 0xD83D;
    char lowSurrogate = (char) 0xDE42;

    assertEquals(smilingFace, MinimalJson.Json.parse("\"\\uD83D\\uDE42\"").asString());
    assertEquals(smilingFace, MinimalJson.Json.parse("\"" + highSurrogate + "\\uDE42\"").asString());
    assertEquals(smilingFace, MinimalJson.Json.parse("\"\\uD83D" + lowSurrogate + "\"").asString());

    List<String> strings = new ArrayList<>();
    MinimalJson.JsonParser jsonParser = new MinimalJson.JsonParser(new MinimalJson.JsonHandler<Object, Object>() {
      @Override
      public void endString(String string) {
        strings.add(string);
      }
    });
    jsonParser.parse(new StringReader("\"12345678\\uD83D\\uDE42\""), 10);
    assertEquals(List.of("12345678" + smilingFace), strings,
        "Expected a surrogate pair spanning parser buffers to remain valid");
  }

  @Test
  public void jsonParserRejectsUnpairedSurrogatesAtTheirSourceLocations() {
    assertParseFailureAt("\"\\uD800\"", 1, 1, 2);
    assertParseFailureAt("\"\\uDC00\"", 1, 1, 2);
    assertParseFailureAt("\"" + (char) 0xD800 + "\"", 1, 1, 2);
    assertParseFailureAt("\"" + (char) 0xDC00 + "\"", 1, 1, 2);
    assertParseFailureAt("\"\\uD800x\"", 1, 1, 2);
  }

  @Test
  public void jsonParserCountsLfCrLfAndBareCrAsLineEndings() {
    assertParseFailureAt("{\n!", 2, 2, 1);
    assertParseFailureAt("{\r\n!", 3, 2, 1);
    assertParseFailureAt("{\r!", 2, 2, 1);
    assertParseFailureAt("{\n", 2, 2, 1);
    assertParseFailureAt("{\r\n", 3, 2, 1);
    assertParseFailureAt("{\r", 2, 2, 1);
  }

  private void assertParseFailureAt(String json, int expectedOffset, int expectedLine, int expectedColumn) {
    MinimalJson.ParseException exception = assertThrows(MinimalJson.ParseException.class,
        () -> MinimalJson.Json.parse(json));

    assertEquals(expectedOffset, exception.getLocation().offset, exception.getMessage());
    assertEquals(expectedLine, exception.getLocation().line, exception.getMessage());
    assertEquals(expectedColumn, exception.getLocation().column, exception.getMessage());
  }

  private String deeplyNestedArray(int depth) {
    StringBuilder stringBuilder = new StringBuilder(depth * 2);

    for (int i = 0; i < depth; ++i)
      stringBuilder.append('[');

    for (int i = 0; i < depth; ++i)
      stringBuilder.append(']');

    return stringBuilder.toString();
  }
}
