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

  private String deeplyNestedArray(int depth) {
    StringBuilder stringBuilder = new StringBuilder(depth * 2);

    for (int i = 0; i < depth; ++i)
      stringBuilder.append('[');

    for (int i = 0; i < depth; ++i)
      stringBuilder.append(']');

    return stringBuilder.toString();
  }
}
