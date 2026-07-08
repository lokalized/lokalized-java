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

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the packaged Lokalized strings JSON Schema.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class LocalizedStringsSchemaTests {
  @NonNull
  private static final Path SCHEMA_PATH = Paths.get("src/main/resources/schema/lokalized-strings.schema.json");
  @NonNull
  private static final Path STRINGS_RESOURCE_PATH = Paths.get("src/test/resources/strings");
  @NonNull
  private static final String SCHEMA_RESOURCE_PATH = "schema/lokalized-strings.schema.json";

  @Test
  public void schemaIsValidJsonAndPackaged() throws IOException {
    String schemaContents = readUtf8(SCHEMA_PATH);
    MinimalJson.Json.parse(schemaContents);

    URL schemaResource = Thread.currentThread().getContextClassLoader().getResource(SCHEMA_RESOURCE_PATH);
    assertNotNull(schemaResource, "Expected Lokalized strings schema to be packaged as a classpath resource");
  }

  @Test
  public void schemaValidatesShippedTestResources() throws IOException {
    Schema schema = loadSchema();

    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(STRINGS_RESOURCE_PATH)) {
      for (Path resourcePath : directoryStream) {
        if (!Files.isRegularFile(resourcePath))
          continue;

        List<Error> validationMessages = schema.validate(readUtf8(resourcePath), InputFormat.JSON);

        assertTrue(validationMessages.isEmpty(), () -> String.format("%s failed Lokalized schema validation: %s",
            resourcePath, validationMessageSummary(validationMessages)));
      }
    }
  }

  @Test
  public void schemaValidatesSelectorDrivenPlaceholderResources() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Send the invoice to {{honorific}} {{lastName}}.\" : {\n" +
        "    \"translation\" : \"Senden Sie die Rechnung an {{honorific}} {{lastName}}.\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"honorific\" : {\n" +
        "        \"selectors\" : [\n" +
        "          { \"value\" : \"grammaticalCase\", \"form\" : \"CASE\" },\n" +
        "          { \"value\" : \"gender\", \"form\" : \"GENDER\" }\n" +
        "        ],\n" +
        "        \"translations\" : [\n" +
        "          { \"when\" : { \"CASE\" : \"CASE_DATIVE\", \"GENDER\" : \"GENDER_MASCULINE\" }, \"value\" : \"Herrn\" },\n" +
        "          { \"when\" : { \"GENDER\" : \"GENDER_FEMININE\" }, \"value\" : \"Frau\" },\n" +
        "          { \"value\" : \"Herr\" }\n" +
        "        ]\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertTrue(validationMessages.isEmpty(), () -> validationMessageSummary(validationMessages));
  }

  @Test
  public void schemaValidatesUnicodePlaceholderNames() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello {{имя}}\" : {\n" +
        "    \"translation\" : \"Hello {{имя}} {{नाम}} {{книги}}\",\n" +
        "    \"placeholderMetadata\" : {\n" +
        "      \"имя\" : { \"type\" : \"STRING\", \"example\" : \"Ада\" },\n" +
        "      \"नाम\" : { \"type\" : \"STRING\", \"example\" : \"Ada\" }\n" +
        "    },\n" +
        "    \"placeholders\" : {\n" +
        "      \"книги\" : {\n" +
        "        \"value\" : \"caféCount\",\n" +
        "        \"translations\" : { \"CARDINALITY_ONE\" : \"book\", \"CARDINALITY_OTHER\" : \"books\" }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertTrue(validationMessages.isEmpty(), () -> validationMessageSummary(validationMessages));
  }

  @Test
  public void schemaRejectsInvalidPlaceholderShape() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello\" : {\n" +
        "    \"translation\" : \"Hello {{name}}\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"name\" : {\n" +
        "        \"value\" : \"name\",\n" +
        "        \"selectors\" : [ { \"value\" : \"gender\", \"form\" : \"GENDER\" } ],\n" +
        "        \"translations\" : { \"GENDER_FEMININE\" : \"Ada\" }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertFalse(validationMessages.isEmpty(), "Expected mixed simple and selector-driven placeholder shapes to fail schema validation");
  }

  @Test
  public void schemaRejectsReservedPlaceholderNames() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello\" : {\n" +
        "    \"translation\" : \"Hello {{CARDINALITY_ONE}}\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"CARDINALITY_ONE\" : {\n" +
        "        \"value\" : \"count\",\n" +
        "        \"translations\" : { \"CARDINALITY_ONE\" : \"one\", \"CARDINALITY_OTHER\" : \"other\" }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertFalse(validationMessages.isEmpty(), "Expected reserved placeholder names to fail schema validation");
  }

  @NonNull
  private Schema loadSchema() throws IOException {
    SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    return schemaRegistry.getSchema(readUtf8(SCHEMA_PATH), InputFormat.JSON);
  }

  @NonNull
  private String readUtf8(@NonNull Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  @NonNull
  private String validationMessageSummary(@NonNull List<@NonNull Error> validationMessages) {
    return validationMessages.stream()
        .map(Error::getMessage)
        .collect(Collectors.joining("; "));
  }
}
