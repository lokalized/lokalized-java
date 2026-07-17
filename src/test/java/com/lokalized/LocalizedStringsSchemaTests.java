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
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
  @NonNull
  private static final String CANONICAL_SCHEMA_ID = "https://lokalized.com/schema/lokalized-strings.schema.json";
  @NonNull
  private static final List<@NonNull IdentifierCase> IDENTIFIER_CASES = Arrays.asList(
      accepted("ASCII letter", "name"),
      accepted("underscore only", "_"),
      accepted("underscore start", "_private"),
      accepted("hyphen and ASCII number suffixes", "item-name2"),
      accepted("trailing hyphen", "name-"),
      accepted("Cyrillic letters", "книги"),
      accepted("Devanagari letters and marks", "नाम"),
      accepted("Arabic-Indic number suffix", "item\u0661"),
      accepted("supplementary-plane letter", "\uD801\uDC00name"),
      accepted("nonspacing mark suffix (Mn)", "a\u0301"),
      accepted("spacing combining mark suffix (Mc)", "a\u0903"),
      accepted("enclosing mark suffix (Me)", "a\u20DD"),
      accepted("letter number suffix (Nl)", "a\u216B"),
      accepted("other number suffix (No)", "a\u00B2"),
      rejected("empty", ""),
      rejected("ASCII number start", "1name"),
      rejected("Unicode number start", "\u0661name"),
      rejected("nonspacing mark start (Mn)", "\u0301a"),
      rejected("spacing combining mark start (Mc)", "\u0903a"),
      rejected("enclosing mark start (Me)", "\u20DDa"),
      rejected("hyphen start", "-name"),
      rejected("embedded whitespace", "first name"),
      rejected("period", "first.name"),
      rejected("slash", "first/name"),
      rejected("format character suffix", "name\u200D"),
      rejected("symbol suffix", "name\uD83D\uDE42"),
      rejected("reserved cardinality name", "CARDINALITY_ONE"),
      rejected("reserved phonetic name", "PHONETIC_OTHER")
  );

  @Test
  public void schemaIsValidJsonAndPackaged() throws IOException {
    byte[] schemaBytes = Files.readAllBytes(SCHEMA_PATH);
    String schemaContents = new String(schemaBytes, StandardCharsets.UTF_8);
    MinimalJson.JsonObject schemaObject = MinimalJson.Json.parse(schemaContents).asObject();

    assertEquals(CANONICAL_SCHEMA_ID, schemaObject.getString("$id", null));

    try (InputStream schemaResource = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(SCHEMA_RESOURCE_PATH)) {
      assertNotNull(schemaResource, "Expected Lokalized strings schema to be packaged as a classpath resource");
      assertArrayEquals(schemaBytes, schemaResource.readAllBytes(),
          "Expected the packaged schema to be byte-identical to the source schema");
    }
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
  public void schemaRejectsRemovedSelectorsField() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello\" : {\n" +
        "    \"translation\" : \"{{item}}\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"item\" : {\n" +
        "        \"value\" : \"count\",\n" +
        "        \"selectors\" : [ { \"value\" : \"count\", \"form\" : \"CARDINALITY\" } ],\n" +
        "        \"translations\" : { \"CARDINALITY_ONE\" : \"item\", \"CARDINALITY_OTHER\" : \"items\" }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertFalse(validationMessages.isEmpty(), "Expected the removed selectors field to fail schema validation");
  }

  @Test
  public void schemaRejectsRemovedRuleArrayTranslations() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello\" : {\n" +
        "    \"translation\" : \"{{item}}\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"item\" : {\n" +
        "        \"value\" : \"count\",\n" +
        "        \"translations\" : [\n" +
        "          { \"when\" : { \"CARDINALITY\" : \"CARDINALITY_ONE\" }, \"value\" : \"item\" },\n" +
        "          { \"value\" : \"items\" }\n" +
        "        ]\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertFalse(validationMessages.isEmpty(), "Expected removed rule-array translations to fail schema validation");
  }

  @Test
  public void schemaValidatesUnicodePlaceholderNames() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello {{имя}}\" : {\n" +
        "    \"translation\" : \"Hello {{имя}} {{नाम}} {{книги}}\",\n" +
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
  public void schemaAndRuntimeAgreeOnIdentifierCorpus() throws IOException {
    Schema schema = loadSchema();

    for (IdentifierCase identifierCase : IDENTIFIER_CASES) {
      String localizedStringsFile = localizedStringsFileForIdentifier(identifierCase.identifier);
      boolean acceptedByRuntime = acceptedByRuntime(localizedStringsFile);
      boolean acceptedBySchema = schema.validate(localizedStringsFile, InputFormat.JSON).isEmpty();
      String message = String.format("Identifier corpus case '%s' (%s)", identifierCase.description,
          codePointsIn(identifierCase.identifier));

      assertEquals(identifierCase.accepted, acceptedByRuntime, message + " had unexpected runtime result");
      assertEquals(identifierCase.accepted, acceptedBySchema, message + " had unexpected schema result");
      assertEquals(acceptedByRuntime, acceptedBySchema, message + " differed between runtime and schema");
    }
  }

  @Test
  public void schemaValidatesTemplatePlaceholders() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Search completed.\" : {\n" +
        "    \"translation\" : \"Found {{resultSummary}} {{timing}}.\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"resultSummary\" : {\n" +
        "        \"translation\" : \"{{formattedResultCount}} results\",\n" +
        "        \"alternatives\" : [\n" +
        "          { \"resultCount == 0\" : \"no results\" },\n" +
        "          { \"resultCount >= resultLimit\" : \"at least {{formattedResultLimit}} results\" }\n" +
        "        ]\n" +
        "      },\n" +
        "      \"timing\" : { \"translation\" : \"in {{formattedDuration}}\" }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertTrue(validationMessages.isEmpty(), () -> validationMessageSummary(validationMessages));
  }

  @Test
  public void schemaRejectsInvalidTemplatePlaceholderShapes() throws IOException {
    String[] invalidLocalizedStringsFiles = {
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"alternatives\":[{\"count == 0\":\"none\"}]}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"translation\":null}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"translation\":\"default\",\"alternatives\":null}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"translation\":\"default\",\"alternatives\":[]}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"translation\":\"default\",\"alternatives\":[{}]}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"translation\":\"default\",\"alternatives\":[{\"a == 1\":\"one\",\"a == 2\":\"two\"}]}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"translation\":\"default\",\"alternatives\":[{\"count == 0\":{\"translation\":\"none\"}}]}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"translation\":\"default\",\"value\":\"count\",\"translations\":{\"CARDINALITY_OTHER\":\"items\"}}}}}"
    };

    Schema schema = loadSchema();
    for (String invalidLocalizedStringsFile : invalidLocalizedStringsFiles) {
      List<Error> validationMessages = schema.validate(invalidLocalizedStringsFile, InputFormat.JSON);
      assertFalse(validationMessages.isEmpty(), invalidLocalizedStringsFile);
    }
  }

  @Test
  public void schemaRejectsInvalidPlaceholderShape() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello\" : {\n" +
        "    \"translation\" : \"{{items}}\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"items\" : {\n" +
        "        \"value\" : \"count\",\n" +
        "        \"range\" : { \"start\" : \"min\", \"end\" : \"max\" },\n" +
        "        \"translations\" : { \"CARDINALITY_ONE\" : \"item\", \"CARDINALITY_OTHER\" : \"items\" }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertFalse(validationMessages.isEmpty(), "Expected mixed value and range placeholder shapes to fail schema validation");
  }

  @Test
  public void schemaRejectsLoaderInvalidMixedAndRangePlaceholderShapes() throws IOException {
    List<String> invalidPlaceholderDefinitions = new ArrayList<>(List.of(
        "{\"value\":\"count\",\"range\":null}",
        "{\"value\":null,\"range\":{\"start\":\"min\",\"end\":\"max\"}}",
        "{\"value\":\"count\",\"translation\":null}",
        "{\"value\":null,\"translation\":\"default\"}",
        "{\"translations\":null,\"alternatives\":null}"
    ));

    for (String languageFormMember : List.of("value", "range", "translations"))
      for (String templateMember : List.of("translation", "alternatives"))
        invalidPlaceholderDefinitions.add(String.format("{\"%s\":null,\"%s\":null}",
            languageFormMember, templateMember));

    Schema schema = loadSchema();
    for (String invalidPlaceholderDefinition : invalidPlaceholderDefinitions) {
      String localizedStringsFile = "{\"root\":{\"translation\":\"{{summary}}\",\"placeholders\":{\"summary\":" +
          invalidPlaceholderDefinition + "}}}";
      List<Error> validationMessages = schema.validate(localizedStringsFile, InputFormat.JSON);

      assertFalse(validationMessages.isEmpty(), invalidPlaceholderDefinition);
    }
  }

  @Test
  public void schemaRejectsRemovedPlaceholderMetadataSyntax() throws IOException {
    List<Error> validationMessages = loadSchema().validate("{\n" +
        "  \"Hello {{name}}\" : {\n" +
        "    \"translation\" : \"Hello {{name}}\",\n" +
        "    \"placeholderMetadata\" : {\n" +
        "      \"name\" : { \"type\" : \"STRING\", \"example\" : \"Ada\" }\n" +
        "    }\n" +
        "  }\n" +
        "}", InputFormat.JSON);

    assertFalse(validationMessages.isEmpty(), "Expected removed placeholder metadata syntax to fail schema validation");
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

  @NonNull
  private String localizedStringsFileForIdentifier(@NonNull String identifier) {
    MinimalJson.JsonObject placeholder = MinimalJson.Json.object()
        .add("translation", "value");
    MinimalJson.JsonObject placeholders = MinimalJson.Json.object()
        .add(identifier, placeholder);
    MinimalJson.JsonObject localizedString = MinimalJson.Json.object()
        .add("translation", "value")
        .add("placeholders", placeholders);
    return MinimalJson.Json.object()
        .add("key", localizedString)
        .toString();
  }

  private boolean acceptedByRuntime(@NonNull String localizedStringsFile) {
    try {
      LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH, "identifier-corpus");
      return true;
    } catch (LocalizedStringLoadingException e) {
      return false;
    }
  }

  @NonNull
  private String codePointsIn(@NonNull String value) {
    if (value.isEmpty())
      return "empty string";

    return value.codePoints()
        .mapToObj(codePoint -> String.format("U+%04X", codePoint))
        .collect(Collectors.joining(" "));
  }

  @NonNull
  private static IdentifierCase accepted(@NonNull String description, @NonNull String identifier) {
    return new IdentifierCase(description, identifier, true);
  }

  @NonNull
  private static IdentifierCase rejected(@NonNull String description, @NonNull String identifier) {
    return new IdentifierCase(description, identifier, false);
  }

  private static final class IdentifierCase {
    @NonNull
    private final String description;
    @NonNull
    private final String identifier;
    private final boolean accepted;

    private IdentifierCase(@NonNull String description, @NonNull String identifier, boolean accepted) {
      this.description = description;
      this.identifier = identifier;
      this.accepted = accepted;
    }
  }
}
