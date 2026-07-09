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

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link LocalizedStringLoader}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class LocalizedStringLoaderTests {
  @Test
  public void testClasspathLoading() {
    verifyLocalizedStringsByLocale(LocalizedStringLoader.loadFromClasspath("strings"));
  }

  @Test
  public void testFilesystemLoading() {
    verifyLocalizedStringsByLocale(LocalizedStringLoader.loadFromFilesystem(Paths.get("src/test/resources/strings")));
  }

  @Test
  public void testIncompletePluralMapWarnsButStillLoads() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    // Russian requires CARDINALITY_MANY; this file omits it.
    String incompleteRussian = "{\n" +
        "  \"I read {{bookCount}} books\" : {\n" +
        "    \"translation\" : \"Я прочитал {{bookCount}} {{books}}\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"books\" : {\n" +
        "        \"value\" : \"bookCount\",\n" +
        "        \"translations\" : {\n" +
        "          \"CARDINALITY_ONE\" : \"книга\",\n" +
        "          \"CARDINALITY_FEW\" : \"книги\",\n" +
        "          \"CARDINALITY_OTHER\" : \"книг\"\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}";

    // English is fully specified (ONE + OTHER) and must not warn.
    String completeEnglish = "{\n" +
        "  \"I read {{bookCount}} books\" : {\n" +
        "    \"translation\" : \"I read {{bookCount}} {{books}}\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"books\" : {\n" +
        "        \"value\" : \"bookCount\",\n" +
        "        \"translations\" : { \"CARDINALITY_ONE\" : \"book\", \"CARDINALITY_OTHER\" : \"books\" }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}";

    Files.write(tempDirectory.resolve("ru"), incompleteRussian.getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("en"), completeEnglish.getBytes(StandardCharsets.UTF_8));

    // Capture warnings via the validation warning-handler hook.
    List<@NonNull LocalizedStringWarning> warnings = new ArrayList<>();
    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, warnings::add);

    // The incomplete file must still load (this is a warning, not a hard failure).
    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("ru")),
        "Incomplete plural file should still load");
    assertFalse(localizedStringsByLocale.get(Locale.forLanguageTag("ru")).isEmpty(),
        "Incomplete plural file should retain its translation");

    // Exactly one warning, for the Russian file's missing MANY form; the complete English file must not warn.
    assertEquals(1, warnings.size(), format("Expected one completeness warning, got %s", warnings));

    LocalizedStringWarning warning = warnings.get(0);
    assertEquals(LocalizedStringWarning.Type.INCOMPLETE_CARDINALITY_TRANSLATIONS, warning.getType());
    assertEquals(Locale.forLanguageTag("ru"), warning.getLocale());
    assertTrue(warning.getMissingLanguageForms().contains("CARDINALITY_MANY"),
        format("Warning should identify the missing form: %s", warning.getMissingLanguageForms()));
    assertTrue(warning.getMessage().contains("CARDINALITY_MANY"),
        format("Warning message should name the missing form: %s", warning.getMessage()));

    // The throwException() handler makes an incomplete file a hard failure (opt-in strictness).
    assertThrows(LocalizedStringLoadingException.class, () ->
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, LocalizedStringWarningHandler.throwException()));

    // The ignore() handler suppresses the warning while still loading.
    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> ignored =
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, LocalizedStringWarningHandler.ignore());
    assertTrue(ignored.containsKey(Locale.forLanguageTag("ru")),
        "Incomplete plural file should still load when warnings are ignored");
  }

  @Test
  public void testIncompleteOrdinalMapWarnsButStillLoads() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    // English ordinals require ONE, TWO, FEW, and OTHER; this file omits TWO and FEW.
    String incompleteEnglish = "{\n" +
        "  \"{{year}}th birthday\" : {\n" +
        "    \"translation\" : \"{{year}}{{suffix}} birthday\",\n" +
        "    \"placeholders\" : {\n" +
        "      \"suffix\" : {\n" +
        "        \"value\" : \"year\",\n" +
        "        \"translations\" : {\n" +
        "          \"ORDINALITY_ONE\" : \"st\",\n" +
        "          \"ORDINALITY_OTHER\" : \"th\"\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}";

    Files.write(tempDirectory.resolve("en"), incompleteEnglish.getBytes(StandardCharsets.UTF_8));

    List<@NonNull LocalizedStringWarning> warnings = new ArrayList<>();
    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, warnings::add);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")),
        "Incomplete ordinal file should still load");
    assertFalse(localizedStringsByLocale.get(Locale.forLanguageTag("en")).isEmpty(),
        "Incomplete ordinal file should retain its translation");

    assertEquals(1, warnings.size(), format("Expected one ordinality completeness warning, got %s", warnings));

    LocalizedStringWarning warning = warnings.get(0);
    assertEquals(LocalizedStringWarning.Type.INCOMPLETE_ORDINALITY_TRANSLATIONS, warning.getType());
    assertEquals(Locale.forLanguageTag("en"), warning.getLocale());
    assertTrue(warning.getMissingLanguageForms().contains("ORDINALITY_TWO"),
        format("Warning should identify the missing TWO form: %s", warning.getMissingLanguageForms()));
    assertTrue(warning.getMissingLanguageForms().contains("ORDINALITY_FEW"),
        format("Warning should identify the missing FEW form: %s", warning.getMissingLanguageForms()));
    assertTrue(warning.getMessage().contains("ORDINALITY_TWO"),
        format("Warning message should name the missing TWO form: %s", warning.getMessage()));
    assertTrue(warning.getMessage().contains("ORDINALITY_FEW"),
        format("Warning message should name the missing FEW form: %s", warning.getMessage()));
  }

  @Test
  public void testIncompleteAlternativeWarningRetainsRootTranslationKey() {
    String catalog = "{\"root.key\":{\"translation\":\"fallback\",\"alternatives\":[" +
        "{\"count == 1\":{\"translation\":\"{{count}} {{books}}\",\"placeholders\":{" +
        "\"books\":{\"value\":\"count\",\"translations\":{" +
        "\"CARDINALITY_ONE\":\"книга\",\"CARDINALITY_OTHER\":\"книг\"}}}}}]}}";
    List<@NonNull LocalizedStringWarning> warnings = new ArrayList<>();

    LocalizedStringLoader.parse(new StringReader(catalog), Locale.forLanguageTag("ru"),
        "alternative-warning-test", warnings::add, LocalizedStringLoadingOptions.defaults());

    assertEquals(1, warnings.size());
    assertEquals("root.key", warnings.get(0).getKey());
    assertTrue(warnings.get(0).getMessage().contains("key 'root.key'"));
    assertFalse(warnings.get(0).getMessage().contains("key 'count == 1'"));
  }

  @Test
  public void testFilesystemLoadingAcceptsNonJreTagsAndCase() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Path lowercaseRegion = tempDirectory.resolve("en-gb");
    Path privateUse = tempDirectory.resolve("x-private");

    Files.write(lowercaseRegion, "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(privateUse, "{\"hi\":\"there\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en-GB")));
    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("x-private")));
  }

  @Test
  public void testFilesystemLoadingAcceptsCldrAliasLocaleFileNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("mo.json"), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("sh.json"), "{\"hi\":\"there\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("zh-yue.json"), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("i-klingon.json"), "{\"nuqneH\":\"world\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("mo")));
    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("sh")));
    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("zh-yue")));
    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("i-klingon")));
  }

  @Test
  public void testFilesystemLoadingJsonExtension() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Path jsonFile = tempDirectory.resolve("en-US.json");
    Files.write(jsonFile, "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en-US")));
  }

  @Test
  public void testFilesystemLoadingJsonExtensionCaseInsensitive() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Path jsonFile = tempDirectory.resolve("en-US.JSON");
    Files.write(jsonFile, "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en-US")));
  }

  @Test
  public void testFilesystemLoadingStripsUtf8Bom() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"), "\uFEFF{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
  }

  @Test
  public void testFilesystemLoadingRejectsBlankAndBomOnlyCatalogs() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings-blank");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"), " \t\r\n".getBytes(StandardCharsets.UTF_8));
    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory));

    Files.write(tempDirectory.resolve("en"), "\uFEFF \t\r\n".getBytes(StandardCharsets.UTF_8));
    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory));
  }

  @Test
  public void testFilesystemLoadingRejectsStructurallyInvalidLanguageTags() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings-invalid-tag");
    tempDirectory.toFile().deleteOnExit();
    Files.write(tempDirectory.resolve("en-a.json"), "{}".getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory));

    assertTrue(exception.getMessage().contains("en-a.json"));
    assertTrue(exception.getMessage().contains("IETF BCP 47"));
  }

  @Test
  public void testFilesystemLoadingRejectsMalformedUtf8() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings-invalid-utf8");
    tempDirectory.toFile().deleteOnExit();
    byte[] invalidUtf8 = new byte[]{'{', '"', 'k', '"', ':', '"', (byte) 0xC3, 0x28, '"', '}'};
    Files.write(tempDirectory.resolve("en"), invalidUtf8);

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory));

    assertTrue(exception.getMessage().contains("valid UTF-8"));
  }

  @Test
  public void testSingleResourceParseOverloads() throws IOException {
    String catalog = "{\"hello\":\"world\"}";
    Path file = Files.createTempFile("lokalized-single-resource", ".json");
    file.toFile().deleteOnExit();
    Files.write(file, catalog.getBytes(StandardCharsets.UTF_8));

    Set<LocalizedString> fromPath = LocalizedStringLoader.parse(file, Locale.ENGLISH);
    Set<LocalizedString> fromInputStream = LocalizedStringLoader.parse(
        new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8)), Locale.ENGLISH, "memory-bytes");
    Set<LocalizedString> fromReader = LocalizedStringLoader.parse(
        new StringReader(catalog), Locale.ENGLISH, "memory-characters");

    assertEquals(1, fromPath.size());
    assertEquals(fromPath, fromInputStream);
    assertEquals(fromPath, fromReader);
  }

  @Test
  public void testSingleResourceParseEnforcesByteCharacterAndNestingLimits() {
    String catalog = "{\"hello\":\"world\"}";
    LocalizedStringLoadingOptions byteLimited = LocalizedStringLoadingOptions.builder()
        .maximumInputBytes(catalog.getBytes(StandardCharsets.UTF_8).length - 1)
        .build();
    LocalizedStringLoadingOptions characterLimited = LocalizedStringLoadingOptions.builder()
        .maximumReaderCharacters(catalog.length() - 1)
        .build();
    LocalizedStringLoadingOptions nestingLimited = LocalizedStringLoadingOptions.builder()
        .maximumJsonNestingDepth(1)
        .build();

    LocalizedStringLoadingException byteException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8)),
            Locale.ENGLISH, "byte-limited", LocalizedStringWarningHandler.ignore(), byteLimited));
    LocalizedStringLoadingException characterException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(catalog), Locale.ENGLISH, "character-limited",
            LocalizedStringWarningHandler.ignore(), characterLimited));
    LocalizedStringLoadingException nestingException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader("{\"hello\":{\"translation\":\"world\"}}"),
            Locale.ENGLISH, "nesting-limited", LocalizedStringWarningHandler.ignore(), nestingLimited));

    assertTrue(byteException.getMessage().contains("maximum size"));
    assertTrue(characterException.getMessage().contains("maximum size"));
    assertTrue(nestingException.getMessage().contains("nesting depth"));
  }

  @Test
  public void testLoadingOptionsRejectInvalidLimits() {
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumInputBytes(0));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumInputBytes(Integer.MAX_VALUE));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumReaderCharacters(0));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumJsonNestingDepth(0));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumJsonNestingDepth(
            LocalizedStringLoadingOptions.MAXIMUM_JSON_NESTING_DEPTH + 1));
  }

  @Test
  public void testLoaderRejectsExplicitNullsAndIncompleteAlternativeShapes() {
    String[] invalidCatalogs = {
        "{\"key\":{\"translation\":null}}",
        "{\"key\":{\"translation\":\"value\",\"commentary\":null}}",
        "{\"key\":{\"translation\":\"value\",\"placeholderMetadata\":null}}",
        "{\"key\":{\"translation\":\"value\",\"placeholderMetadata\":{\"name\":{\"type\":null}}}}",
        "{\"key\":{\"translation\":\"value\",\"placeholders\":null}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"value\":\"count\",\"range\":null}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"value\":null,\"range\":{\"start\":\"a\",\"end\":\"b\"}}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"value\":\"count\",\"selectors\":null}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"value\":null,\"selectors\":[{\"value\":\"count\",\"form\":\"CARDINALITY\"}]}}}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":null}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":[]}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":[null]}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":[{}]}}",
        "{\"key\":{\"translation\":\"{{article}}\",\"placeholders\":{\"article\":{" +
            "\"selectors\":[{\"value\":\"grammaticalCase\",\"form\":\"CASE\"}]," +
            "\"translations\":[{\"when\":null,\"value\":\"the\"}]}}}}"
    };

    for (String invalidCatalog : invalidCatalogs)
      assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.parse(new StringReader(invalidCatalog), Locale.ENGLISH,
              "schema-parity-test", LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults()),
          invalidCatalog);
  }

  @Test
  public void testClasspathLoadingRejectsEscapingPackagePaths() {
    ClassLoader classLoader = getClass().getClassLoader();

    for (String invalidPackage : List.of("", "/strings", "strings/", "../strings", "strings/../other", "strings\\other"))
      assertThrows(IllegalArgumentException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, invalidPackage), invalidPackage);
  }

  @Test
  public void testDuplicateMemberDiagnosticPathIsBounded() {
    String longMemberName = String.join("", java.util.Collections.nCopies(10_000, "a"));
    String catalog = "{\"root\":{\"translation\":\"value\",\"" + longMemberName +
        "\":{\"duplicate\":1,\"duplicate\":2}}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(catalog), Locale.ENGLISH, "bounded-path"));

    assertTrue(exception.getMessage().length() < 5_000);
    assertTrue(exception.getMessage().contains("duplicate JSON object member"));

    String duplicateLongMemberCatalog = "{\"root\":{\"" + longMemberName + "\":1,\"" +
        longMemberName + "\":2}}";
    LocalizedStringLoadingException longMemberException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(duplicateLongMemberCatalog), Locale.ENGLISH,
            "bounded-member"));

    assertTrue(longMemberException.getMessage().length() < 5_000);
  }

  @Test
  public void testFilesystemLoadingSkipsDirectories() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.createDirectory(tempDirectory.resolve("en"));
    Files.write(tempDirectory.resolve("en-GB"), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en-GB")));
    assertFalse(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
  }

  @Test
  public void testFilesystemLoadingSkipsNonLocaleNonJsonFiles() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("NOTES"), "Not a localized strings file.".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("en"), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertEquals(1, localizedStringsByLocale.size());
    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
  }

  @Test
  public void testFilesystemLoadingSkipsHiddenJsonSidecars() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("._en.json"), "{}".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("en.json"), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertEquals(1, localizedStringsByLocale.size());
    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
  }

  @Test
  public void testFilesystemLoadingRejectsInvalidJsonLocaleFileNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en_US.json"), "{}".getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected locale JSON files named with underscores to fail during load");

    assertTrue(exception.getMessage().contains("en_US.json"));
    assertTrue(exception.getMessage().contains("IETF BCP 47"));
  }

  @Test
  public void testFilesystemLoadingRejectsUnknownJsonLocaleFileNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("template.json"), "{}".getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected unknown locale JSON files to fail during load");

    assertTrue(exception.getMessage().contains("template.json"));
    assertTrue(exception.getMessage().contains("IETF BCP 47"));
  }

  @Test
  public void testFilesystemLoadingRejectsUnknownCldrScriptJsonLocaleFileNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en-Abcd.json"), "{}".getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected unknown CLDR script subtags to fail during load");

    assertTrue(exception.getMessage().contains("en-Abcd.json"));
    assertTrue(exception.getMessage().contains("IETF BCP 47"));
  }

  @Test
  public void testFilesystemLoadingRejectsDuplicateKeys() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        "{\"hello\":\"world\",\"hello\":\"again\"}".getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected duplicate translation keys in a single file to throw");
  }

  @Test
  public void testFilesystemLoadingRejectsInvalidAlternativeExpressions() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello\",\"alternatives\":[{\"bookCount = 1\":{\"translation\":\"Hi\"}}]}}")
            .getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected invalid alternative expression to fail fast during load");
  }

  @Test
  public void testFilesystemLoadingRejectsAlternativeExpressionsWithMissingOperands() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello\",\"alternatives\":[{\"bookCount ==\":{\"translation\":\"Hi\"}}]}}")
            .getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected alternative expressions with missing operands to fail fast during load");
  }

  @Test
  public void testFilesystemLoadingRejectsChainedAlternativeComparisons() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello\",\"alternatives\":[{\"bookCount == pageCount == chapterCount\":{\"translation\":\"Hi\"}}]}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected chained alternative comparisons to fail fast during load");

    assertTrue(exception.getCause().getMessage().contains("Chained comparisons are not supported"));
    assertFalse(exception.getCause().getMessage().contains("TRUE"));
    assertFalse(exception.getCause().getMessage().contains("boolean result"));
  }

  @Test
  public void testFilesystemLoadingRejectsBareBooleanAlternativeOperands() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello\",\"alternatives\":[{\"bookCount && pageCount\":{\"translation\":\"Hi\"}}]}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected bare boolean alternative operands to fail fast during load");

    assertTrue(exception.getCause().getMessage().contains("requires boolean operands"));
  }

  @Test
  public void testFilesystemLoadingRejectsEmptyAlternativeExpressions() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello\",\"alternatives\":[{\"\":{\"translation\":\"Hi\"}}]}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected empty alternative expressions to fail fast during load");

    assertTrue(exception.getCause().getMessage().contains("must not be empty"));
  }

  @Test
  public void testFilesystemLoadingRejectsOversizedAlternativeExpressions() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        format("{\"Hello\":{\"translation\":\"Hello\",\"alternatives\":[{\"%s\":{\"translation\":\"Hi\"}}]}}",
            orExpressionWithClauseCount(129)).getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected oversized alternative expressions to fail fast during load");

    assertTrue(exception.getCause().getMessage().contains("maximum supported token count"));
  }

  @Test
  public void testFilesystemLoadingRejectsMalformedSimpleTranslationPlaceholders() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        "{\"Hello\":\"Hello {{ name }}\"}".getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected malformed simple translation placeholders to fail during load");

    assertTrue(exception.getMessage().contains("invalid placeholder reference"));
    assertTrue(exception.getMessage().contains("Hello"));
  }

  @Test
  public void testFilesystemLoadingRejectsUnclosedTranslationPlaceholders() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{name\"}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected unclosed translation placeholders to fail during load");

    assertTrue(exception.getMessage().contains("Unclosed placeholder"));
  }

  @Test
  public void testFilesystemLoadingAllowsEscapedLiteralMustaches() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        "{\"Hello\":\"Literal \\\\{{ name }} and {{name}}\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
  }

  @Test
  public void testFilesystemLoadingRejectsInvalidJson() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"), "{".getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected invalid JSON to fail fast during load");
  }

  @Test
  public void testFilesystemLoadingRejectsInvalidJsonWithLocation() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"), ("{\n" +
        "  \"hello\":\n" +
        "}").getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected invalid JSON to fail fast during load");

    assertTrue(exception.getMessage().matches("(?s).*:\\d+:\\d+: unable to parse localized strings file.*"));
  }

  @Test
  public void testFilesystemLoadingRejectsInvalidPlaceholderNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{name}}\",\"placeholders\":{\"1st\":{\"value\":\"name\",\"translations\":{\"CARDINALITY_ONE\":\"one\"}}}}}")
            .getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected digit-leading placeholder names to be rejected");
  }

  @Test
  public void testFilesystemLoadingAcceptsUnicodePlaceholderNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello {{имя}}\":{\"translation\":\"Hello {{имя}} {{नाम}} {{книги}}\",\"placeholderMetadata\":{" +
            "\"имя\":{\"type\":\"STRING\",\"example\":\"Ада\"}," +
            "\"नाम\":{\"type\":\"STRING\",\"example\":\"Ada\"}" +
            "},\"placeholders\":{" +
            "\"книги\":{\"value\":\"caféCount\",\"translations\":{\"CARDINALITY_ONE\":\"book\",\"CARDINALITY_OTHER\":\"books\"}}" +
            "},\"alternatives\":[{\"caféCount == количество2\":{\"translation\":\"Matched {{имя}}\"}}]}}")
            .getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);
    LocalizedString localizedString = localizedStringsByLocale.get(Locale.forLanguageTag("en")).iterator().next();

    assertTrue(localizedString.getPlaceholderMetadataByPlaceholder().containsKey("имя"));
    assertTrue(localizedString.getPlaceholderMetadataByPlaceholder().containsKey("नाम"));
    assertTrue(localizedString.getLanguageFormTranslationsByPlaceholder().containsKey("книги"));
    assertEquals(1, localizedString.getAlternatives().size());
  }

  @Test
  public void testFilesystemLoadingRejectsUnknownLocalizedStringObjectFields() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        "{\"Hello\":{\"translation\":\"Hello\",\"notes\":\"unexpected\"}}".getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected unknown localized string fields to be rejected");

    assertTrue(exception.getMessage().contains("unexpected field 'notes'"));
  }

  @Test
  public void testFilesystemLoadingRejectsUnknownPlaceholderFields() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"I read {{bookCount}} books\":{\"translation\":\"{{bookCount}} {{books}}\",\"placeholders\":{\"books\":{\"value\":\"bookCount\"," +
            "\"translations\":{\"CARDINALITY_ONE\":\"book\",\"CARDINALITY_OTHER\":\"books\"},\"notes\":\"unexpected\"}}}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected unknown placeholder fields to be rejected");

    assertTrue(exception.getMessage().contains("unexpected field 'notes'"));
  }

  @Test
  public void testFilesystemLoadingRejectsUnknownSelectorRuleFields() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Article\":{\"translation\":\"{{article}} {{noun}}\",\"placeholders\":{\"article\":{\"selectors\":[" +
            "{\"value\":\"gender\",\"form\":\"GENDER\"}" +
            "],\"translations\":[" +
            "{\"when\":{\"GENDER\":\"GENDER_MASCULINE\"},\"value\":\"el\",\"notes\":\"unexpected\"}," +
            "{\"value\":\"la\"}" +
            "]}}}}").getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected unknown selector rule fields to be rejected");

    assertTrue(exception.getMessage().contains("unexpected field 'notes'"));
  }

  @Test
  public void testFilesystemLoadingRejectsReservedPlaceholderNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{CARDINALITY_ONE}}\",\"placeholders\":{\"CARDINALITY_ONE\":{\"value\":\"count\",\"translations\":{" +
            "\"CARDINALITY_ONE\":\"one\",\"CARDINALITY_OTHER\":\"other\"}}}}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected reserved placeholder names to be rejected");

    assertTrue(exception.getMessage().contains("reserved expression constants"));
    assertTrue(exception.getMessage().contains("CARDINALITY_ONE"));
  }

  @Test
  public void testFilesystemLoadingRejectsReservedTranslationPlaceholderReferences() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        "{\"Hello\":{\"translation\":\"Hello {{GENDER_MASCULINE}}\"}}"
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected reserved translation placeholder references to be rejected");

    assertTrue(exception.getMessage().contains("reserved expression constants"));
    assertTrue(exception.getMessage().contains("GENDER_MASCULINE"));
  }

  @Test
  public void testFilesystemLoadingRejectsMalformedLanguageFormTranslationPlaceholders() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"I read {{bookCount}} books\":{\"translation\":\"{{bookCount}} {{books}}\",\"placeholders\":{\"books\":{\"value\":\"bookCount\",\"translations\":{" +
            "\"CARDINALITY_ONE\":\"{{ count }} book\",\"CARDINALITY_OTHER\":\"books\"}}}}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected malformed placeholder references in language-form fragments to be rejected");

    assertTrue(exception.getMessage().contains("invalid placeholder reference in placeholder translation"));
    assertTrue(exception.getMessage().contains("Malformed placeholder"));
  }

  @Test
  public void testFilesystemLoadingRejectsMalformedSelectorTranslationPlaceholders() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Article\":{\"translation\":\"{{article}} {{noun}}\",\"placeholders\":{\"article\":{\"selectors\":[" +
            "{\"value\":\"grammaticalCase\",\"form\":\"CASE\"}" +
            "],\"translations\":[" +
            "{\"when\":{\"CASE\":\"CASE_NOMINATIVE\"},\"value\":\"the {{ noun }}\"}," +
            "{\"value\":\"the\"}" +
            "]}}}}").getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected malformed placeholder references in selector fragments to be rejected");

    assertTrue(exception.getMessage().contains("invalid placeholder reference in selector-based translation rule"));
    assertTrue(exception.getMessage().contains("Malformed placeholder"));
  }

  @Test
  public void testFilesystemLoadingRejectsReservedPlaceholderMetadataNames() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello\",\"placeholderMetadata\":{" +
            "\"GENDER_MASCULINE\":{\"type\":\"STRING\",\"commentary\":\"Reserved name.\"}" +
            "}}}").getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected reserved placeholder metadata names to be rejected");

    assertTrue(exception.getMessage().contains("reserved expression constants"));
    assertTrue(exception.getMessage().contains("GENDER_MASCULINE"));
  }

  @Test
  public void testFilesystemLoadingRejectsMissingPlaceholderTranslations() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{name}}\",\"placeholders\":{\"name\":{\"value\":\"name\"}}}}")
            .getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected missing placeholder translations to fail during load");
  }

  @Test
  public void testFilesystemLoadingAcceptsIncompleteCardinalityTranslations() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("ru"),
        ("{\"I read {{bookCount}} books\":{\"translation\":\"{{bookCount}} {{books}}\",\"placeholders\":{\"books\":{\"value\":\"bookCount\",\"translations\":{" +
            "\"CARDINALITY_ONE\":\"книга\",\"CARDINALITY_FEW\":\"книги\",\"CARDINALITY_OTHER\":\"книги\"}}}}}")
            .getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, LocalizedStringWarningHandler.ignore());

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("ru")));
  }

  @Test
  public void testFilesystemLoadingAcceptsIncompleteOrdinalityTranslations() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Birthday\":{\"translation\":\"{{year}}{{suffix}}\",\"placeholders\":{\"suffix\":{\"value\":\"year\",\"translations\":{" +
            "\"ORDINALITY_ONE\":\"st\",\"ORDINALITY_OTHER\":\"th\"}}}}}")
            .getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, LocalizedStringWarningHandler.ignore());

    assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
  }

  @Test
  public void testFilesystemLoadingAcceptsPlaceholderMetadata() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{name}}\",\"placeholderMetadata\":{" +
            "\"name\":{\"type\":\"STRING\",\"commentary\":\"Customer first name.\",\"example\":\"Ada\"}," +
            "\"gender\":{\"type\":\"GENDER\",\"commentary\":\"Recipient grammatical gender.\",\"allowedValues\":[\"GENDER_MASCULINE\",\"GENDER_FEMININE\"]}" +
            "}}}").getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);
    LocalizedString localizedString = localizedStringsByLocale.get(Locale.forLanguageTag("en")).iterator().next();

    assertEquals("Ada", localizedString.getPlaceholderMetadataByPlaceholder().get("name").getExample().orElse(null));
    assertEquals(2, localizedString.getPlaceholderMetadataByPlaceholder().get("gender").getAllowedValues().size());
  }

  @Test
  public void testFilesystemLoadingRejectsInvalidPlaceholderMetadataAllowedValues() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{name}}\",\"placeholderMetadata\":{" +
            "\"gender\":{\"type\":\"GENDER\",\"allowedValues\":[\"GENDER_MASCULINE\",\"CASE_DATIVE\"]}" +
            "}}}").getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected invalid placeholder metadata allowed values to fail during load");
  }

  @Test
  public void testFilesystemLoadingRejectsDuplicatePlaceholderMetadataAllowedValues() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{name}}\",\"placeholderMetadata\":{" +
            "\"gender\":{\"type\":\"GENDER\",\"allowedValues\":[\"GENDER_MASCULINE\",\"GENDER_MASCULINE\"]}" +
            "}}}").getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected duplicate placeholder metadata allowed values to fail during load");
  }

  @Test
  public void testFilesystemLoadingRejectsNestedDuplicateObjectMembers() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Hello\":{\"translation\":\"Hello {{books}}\",\"placeholders\":{\"books\":{\"value\":\"count\",\"translations\":{" +
            "\"CARDINALITY_ONE\":\"book\",\"CARDINALITY_ONE\":\"book duplicate\"}}}}}")
            .getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected duplicate nested JSON object members to fail during load");

    assertTrue(exception.getMessage().contains("duplicate JSON object member 'CARDINALITY_ONE'"));
  }

  @Test
  public void testFilesystemLoadingAcceptsSelectorDrivenPlaceholderTranslations() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Article\":{\"translation\":\"{{article}} {{noun}}\",\"placeholders\":{\"article\":{\"selectors\":[" +
            "{\"value\":\"grammaticalCase\",\"form\":\"CASE\"}," +
            "{\"value\":\"gender\",\"form\":\"GENDER\"}" +
            "],\"translations\":[" +
            "{\"when\":{\"CASE\":\"CASE_NOMINATIVE\",\"GENDER\":\"GENDER_MASCULINE\"},\"value\":\"der\"}," +
            "{\"value\":\"die\"}" +
            "]}}}}").getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);
    LocalizedString localizedString = localizedStringsByLocale.get(Locale.forLanguageTag("en")).iterator().next();
    LocalizedString.LanguageFormTranslation translation = localizedString.getLanguageFormTranslationsByPlaceholder().get("article");

    assertTrue(translation.isSelectorDriven());
    assertEquals(2, translation.getSelectors().size());
    assertEquals(2, translation.getTranslationRules().size());
  }

  @Test
  public void testFilesystemLoadingRejectsAmbiguousSelectorDrivenPlaceholderTranslations() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"),
        ("{\"Article\":{\"translation\":\"{{article}} {{noun}}\",\"placeholders\":{\"article\":{\"selectors\":[" +
            "{\"value\":\"grammaticalCase\",\"form\":\"CASE\"}," +
            "{\"value\":\"gender\",\"form\":\"GENDER\"}" +
            "],\"translations\":[" +
            "{\"when\":{\"CASE\":\"CASE_DATIVE\"},\"value\":\"dem\"}," +
            "{\"when\":{\"GENDER\":\"GENDER_FEMININE\"},\"value\":\"die\"}" +
            "]}}}}").getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected ambiguous selector-based translation rules to fail during load");
  }

  @Test
  public void testClasspathLoadingFromJar() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings", ".jar");
    tempJar.toFile().deleteOnExit();

    Path stringsPath = Paths.get("src/test/resources/strings");

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(tempJar));
         DirectoryStream<Path> directoryStream = Files.newDirectoryStream(stringsPath)) {
      JarEntry directoryEntry = new JarEntry("strings/");
      jarOutputStream.putNextEntry(directoryEntry);
      jarOutputStream.closeEntry();

      for (Path filePath : directoryStream) {
        if (!Files.isRegularFile(filePath))
          continue;

        JarEntry entry = new JarEntry("strings/" + filePath.getFileName().toString());
        jarOutputStream.putNextEntry(entry);
        jarOutputStream.write(Files.readAllBytes(filePath));
        jarOutputStream.closeEntry();
      }
    }

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      verifyLocalizedStringsByLocale(LocalizedStringLoader.loadFromClasspath(classLoader, "strings"));
    }
  }

  @Test
  public void testClasspathLoadingJsonExtensionFromJar() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-json", ".jar");
    tempJar.toFile().deleteOnExit();

    writeJarEntry(tempJar, "strings/en-US.json", "{\"hello\":\"world\"}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspath(classLoader, "strings");
      assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en-US")));
    }
  }

  @Test
  public void testClasspathLoadingFromJarWithoutDirectoryEntry() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-no-directory-entry", ".jar");
    tempJar.toFile().deleteOnExit();
    writeJarEntryWithoutDirectory(tempJar, "strings/en.json", "{\"hello\":\"world\"}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings");

      assertTrue(localizedStringsByLocale.containsKey(Locale.ENGLISH));
      assertEquals(1, localizedStringsByLocale.get(Locale.ENGLISH).size());
    }
  }

  @Test
  public void testClasspathLoadingStripsUtf8BomFromJar() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-bom", ".jar");
    tempJar.toFile().deleteOnExit();

    writeJarEntry(tempJar, "strings/en", "\uFEFF{\"hello\":\"world\"}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspath(classLoader, "strings");
      assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
    }
  }

  @Test
  public void testClasspathLoadingUsesThreadContextClassLoader() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-context", ".jar");
    tempJar.toFile().deleteOnExit();

    writeJarEntry(tempJar, "context-strings/en", "{\"hello\":\"world\"}");

    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      Thread.currentThread().setContextClassLoader(classLoader);

      Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspath("context-strings");
      assertTrue(localizedStringsByLocale.containsKey(Locale.forLanguageTag("en")));
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }
  }

  @Test
  public void testClasspathLoadingMergesResources() throws IOException {
    Path tempJar1 = Files.createTempFile("lokalized-strings-one", ".jar");
    Path tempJar2 = Files.createTempFile("lokalized-strings-two", ".jar");
    tempJar1.toFile().deleteOnExit();
    tempJar2.toFile().deleteOnExit();

    writeJarEntry(tempJar1, "strings/en", "{\"hello\":\"world\"}");
    writeJarEntry(tempJar2, "strings/en", "{\"goodbye\":\"world\"}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{
        tempJar1.toUri().toURL(),
        tempJar2.toUri().toURL()
    }, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspath(classLoader, "strings");
      Set<LocalizedString> localizedStrings = localizedStringsByLocale.get(Locale.forLanguageTag("en"));

      assertNotNull(localizedStrings);
      assertEquals(2, localizedStrings.size());
    }
  }

  @Test
  public void testClasspathLoadingReportsOriginsForConflictingDuplicateKeys() throws IOException {
    Path tempJar1 = Files.createTempFile("lokalized-strings-conflict-one", ".jar");
    Path tempJar2 = Files.createTempFile("lokalized-strings-conflict-two", ".jar");
    tempJar1.toFile().deleteOnExit();
    tempJar2.toFile().deleteOnExit();

    writeJarEntry(tempJar1, "strings/en", "{\"hello\":\"world\"}");
    writeJarEntry(tempJar2, "strings/en", "{\"hello\":\"there\"}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{
        tempJar1.toUri().toURL(),
        tempJar2.toUri().toURL()
    }, null)) {
      LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings"),
          "Expected duplicate keys across classpath resources to fail during merge");

      assertTrue(exception.getMessage().contains("Duplicate localized string key 'hello'"));
      assertTrue(exception.getMessage().contains("locale 'en'"));
      assertTrue(exception.getMessage().contains(tempJar1.getFileName().toString()));
      assertTrue(exception.getMessage().contains(tempJar2.getFileName().toString()));
      assertTrue(exception.getMessage().contains("strings/en"));
    }
  }

  @Test
  public void testClasspathLoadingIgnoresIdenticalDuplicateKeys() throws IOException {
    Path tempJar1 = Files.createTempFile("lokalized-strings-identical-one", ".jar");
    Path tempJar2 = Files.createTempFile("lokalized-strings-identical-two", ".jar");
    tempJar1.toFile().deleteOnExit();
    tempJar2.toFile().deleteOnExit();

    writeJarEntry(tempJar1, "strings/en", "{\"hello\":\"world\"}");
    writeJarEntry(tempJar2, "strings/en", "{\"hello\":\"world\"}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{
        tempJar1.toUri().toURL(),
        tempJar2.toUri().toURL()
    }, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspath(classLoader, "strings");
      Set<LocalizedString> localizedStrings = localizedStringsByLocale.get(Locale.forLanguageTag("en"));

      assertNotNull(localizedStrings);
      assertEquals(1, localizedStrings.size());
    }
  }

  private void writeJarEntry(Path jarPath, String entryName, String json) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
      JarEntry directoryEntry = new JarEntry(entryName.substring(0, entryName.lastIndexOf('/') + 1));
      jarOutputStream.putNextEntry(directoryEntry);
      jarOutputStream.closeEntry();

      JarEntry entry = new JarEntry(entryName);
      jarOutputStream.putNextEntry(entry);
      jarOutputStream.write(json.getBytes(StandardCharsets.UTF_8));
      jarOutputStream.closeEntry();
    }
  }

  private void writeJarEntryWithoutDirectory(Path jarPath, String entryName, String json) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
      JarEntry entry = new JarEntry(entryName);
      jarOutputStream.putNextEntry(entry);
      jarOutputStream.write(json.getBytes(StandardCharsets.UTF_8));
      jarOutputStream.closeEntry();
    }
  }

  protected void verifyLocalizedStringsByLocale(@NonNull Map<Locale, Set<LocalizedString>> localizedStringsByLocale) {
    requireNonNull(localizedStringsByLocale);

    assertEquals(4, localizedStringsByLocale.size(), "Unexpected number of strings files");

    for (Entry<Locale, Set<LocalizedString>> entry : localizedStringsByLocale.entrySet())
      assertTrue(entry.getValue().size() > 0, format("The '%s' strings file has no data", entry.getKey().toLanguageTag()));
  }

  private String orExpressionWithClauseCount(int clauseCount) {
    StringBuilder expression = new StringBuilder(clauseCount * 10);

    for (int i = 0; i < clauseCount; ++i) {
      if (i > 0)
        expression.append(" || ");

      expression.append("count == 1");
    }

    return expression.toString();
  }
}
