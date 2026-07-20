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

import com.lokalized.LocalizedString.ExpressionAlternative;
import com.lokalized.LocalizedString.ExpressionTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.PlaceholderDefinition;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
	public void loadingOptionsHaveValueSemanticsAndCopyBuilder() {
		LocalizedStringLoadingOptions defaults = LocalizedStringLoadingOptions.defaults();
		LocalizedStringLoadingOptions copy = defaults.toBuilder().build();
		LocalizedStringLoadingOptions lower = defaults.toBuilder().maximumLocalizedStringsFiles(10).build();
		LocalizedStringLoadingOptions lowerTranslationNodeLimit = defaults.toBuilder()
				.maximumTranslationNodes(99_999)
				.build();
		LocalizedStringLoadingOptions lowerDiscoveryLimit = defaults.toBuilder()
				.maximumDiscoveryEntries(99_999)
				.build();

		assertEquals(Integer.valueOf(8_388_608), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_INPUT_BYTES);
		assertEquals(Integer.valueOf(8_388_608), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_READER_CHARACTERS);
		assertEquals(Integer.valueOf(64), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_JSON_NESTING_DEPTH);
		assertEquals(Boolean.FALSE, LocalizedStringLoadingOptions.DEFAULT_EXHAUSTIVE_CLASSPATH_SEARCH);
		assertEquals(Integer.valueOf(128), LocalizedStringLoadingOptions.MAXIMUM_JSON_NESTING_DEPTH);
		assertEquals(Long.valueOf(33_554_432L), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_TOTAL_INPUT_BYTES);
		assertEquals(Integer.valueOf(256), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_LOCALIZED_STRINGS_FILES);
		assertEquals(Integer.valueOf(100_000), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_TRANSLATION_NODES);
		assertEquals(Integer.valueOf(1_000), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_WARNINGS);
		assertEquals(Integer.valueOf(100_000), LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_DISCOVERY_ENTRIES);
		assertEquals(Integer.valueOf(1_000_000), LocalizedStringLoadingOptions.MAXIMUM_DISCOVERY_ENTRIES);
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_INPUT_BYTES, defaults.getMaximumInputBytes());
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_READER_CHARACTERS,
				defaults.getMaximumReaderCharacters());
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_JSON_NESTING_DEPTH,
				defaults.getMaximumJsonNestingDepth());
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_TOTAL_INPUT_BYTES,
				defaults.getMaximumTotalInputBytes());
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_LOCALIZED_STRINGS_FILES,
				defaults.getMaximumLocalizedStringsFiles());
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_TRANSLATION_NODES,
				defaults.getMaximumTranslationNodes());
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_WARNINGS, defaults.getMaximumWarnings());
		assertEquals(LocalizedStringLoadingOptions.DEFAULT_MAXIMUM_DISCOVERY_ENTRIES,
				defaults.getMaximumDiscoveryEntries());
		assertEquals(defaults, copy);
		assertEquals(defaults.hashCode(), copy.hashCode());
		assertNotEquals(defaults, lower);
		assertNotEquals(defaults, lowerTranslationNodeLimit);
		assertNotEquals(defaults, lowerDiscoveryLimit);
		assertTrue(lower.toString().contains("maximumLocalizedStringsFiles=10"));
		assertTrue(defaults.toString().contains("maximumTranslationNodes=100000"));
		assertTrue(defaults.toString().contains("maximumDiscoveryEntries=100000"));
	}
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

    // The default is intentionally silent: warnings remain non-fatal unless the application supplies a handler.
    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> silentlyLoaded =
        LocalizedStringLoader.loadFromFilesystem(tempDirectory);
    assertTrue(silentlyLoaded.containsKey(Locale.forLanguageTag("ru")),
        "The default warning policy should silently retain files with non-fatal warnings");

    // An explicit lambda receives warnings via the validation warning-handler hook.
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
    assertEquals(Locale.forLanguageTag("ru"), warning.getLocale().orElseThrow(AssertionError::new));
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
    assertEquals(Locale.forLanguageTag("en"), warning.getLocale().orElseThrow(AssertionError::new));
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
    String localizedStringsFile = "{\"root.key\":{\"translation\":\"fallback\",\"alternatives\":[" +
        "{\"count == 1\":{\"translation\":\"{{count}} {{books}}\",\"placeholders\":{" +
        "\"books\":{\"value\":\"count\",\"translations\":{" +
        "\"CARDINALITY_ONE\":\"книга\",\"CARDINALITY_OTHER\":\"книг\"}}}}}]}}";
    List<@NonNull LocalizedStringWarning> warnings = new ArrayList<>();

    LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.forLanguageTag("ru"),
        "alternative-warning-test", warnings::add, LocalizedStringLoadingOptions.defaults());

    assertEquals(1, warnings.size());
    assertEquals("root.key", warnings.get(0).getKey().orElseThrow(AssertionError::new));
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
  public void testFilesystemLoadingRejectsBlankAndBomOnlyLocalizedStringsFiles() throws IOException {
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
    String localizedStringsFile = "{\"hello\":\"world\"}";
    Path file = Files.createTempFile("lokalized-single-resource", ".json");
    file.toFile().deleteOnExit();
    Files.write(file, localizedStringsFile.getBytes(StandardCharsets.UTF_8));

    Set<LocalizedString> fromPath = LocalizedStringLoader.parse(file, Locale.ENGLISH);
    Set<LocalizedString> fromInputStream = LocalizedStringLoader.parse(
        new ByteArrayInputStream(localizedStringsFile.getBytes(StandardCharsets.UTF_8)), Locale.ENGLISH,
        "memory-bytes");
    Set<LocalizedString> fromReader = LocalizedStringLoader.parse(
        new StringReader(localizedStringsFile), Locale.ENGLISH, "memory-characters");

    assertEquals(1, fromPath.size());
    assertEquals(fromPath, fromInputStream);
    assertEquals(fromPath, fromReader);
    assertThrows(UnsupportedOperationException.class, fromPath::clear);
    assertThrows(UnsupportedOperationException.class, fromInputStream::clear);
    assertThrows(UnsupportedOperationException.class, fromReader::clear);
  }

  @Test
  public void testSingleResourceParseOverloadsRejectMalformedLocales() throws IOException {
    String localizedStringsFile = "{\"hello\":\"world\"}";
    byte[] localizedStringsFileBytes = localizedStringsFile.getBytes(StandardCharsets.UTF_8);
    Path file = Files.createTempFile("lokalized-single-resource-malformed-locale", ".json");
    file.toFile().deleteOnExit();
    Files.write(file, localizedStringsFileBytes);
    Locale malformedLocale = new Locale("x");

    assertThrows(IllegalArgumentException.class, () -> LocalizedStringLoader.parse(file, malformedLocale));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoader.parse(new ByteArrayInputStream(localizedStringsFileBytes), malformedLocale,
            "malformed-locale-default-bytes"));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), malformedLocale,
            "malformed-locale-default-characters"));

    IllegalArgumentException pathException = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoader.parse(file, malformedLocale, LocalizedStringWarningHandler.ignore(),
            LocalizedStringLoadingOptions.defaults()));
    IllegalArgumentException inputStreamException = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoader.parse(new ByteArrayInputStream(localizedStringsFileBytes), malformedLocale,
            "malformed-locale-bytes", LocalizedStringWarningHandler.ignore(),
            LocalizedStringLoadingOptions.defaults()));
    IllegalArgumentException readerException = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), malformedLocale,
            "malformed-locale-characters", LocalizedStringWarningHandler.ignore(),
            LocalizedStringLoadingOptions.defaults()));

    assertTrue(pathException.getMessage().contains("well-formed IETF BCP 47 locale"));
    assertTrue(inputStreamException.getMessage().contains("well-formed IETF BCP 47 locale"));
    assertTrue(readerException.getMessage().contains("well-formed IETF BCP 47 locale"));
  }

  @Test
  public void testSingleResourceParseEnforcesByteCharacterAndNestingLimits() {
    String localizedStringsFile = "{\"hello\":\"world\"}";
    byte[] localizedStringsFileBytes = localizedStringsFile.getBytes(StandardCharsets.UTF_8);
    LocalizedStringLoadingOptions exactByteLimit = LocalizedStringLoadingOptions.builder()
        .maximumInputBytes(localizedStringsFileBytes.length)
        .build();
    LocalizedStringLoadingOptions oneOverByteLimit = LocalizedStringLoadingOptions.builder()
        .maximumInputBytes(localizedStringsFileBytes.length - 1)
        .build();
    LocalizedStringLoadingOptions exactCharacterLimit = LocalizedStringLoadingOptions.builder()
        .maximumReaderCharacters(localizedStringsFile.length())
        .build();
    LocalizedStringLoadingOptions oneOverCharacterLimit = LocalizedStringLoadingOptions.builder()
        .maximumReaderCharacters(localizedStringsFile.length() - 1)
        .build();
    String nestedLocalizedStringsFile = "{\"hello\":{\"translation\":\"world\"}}";
    LocalizedStringLoadingOptions exactNestingLimit = LocalizedStringLoadingOptions.builder()
        .maximumJsonNestingDepth(2)
        .build();
    LocalizedStringLoadingOptions oneOverNestingLimit = LocalizedStringLoadingOptions.builder()
        .maximumJsonNestingDepth(1)
        .build();

    assertEquals(1, LocalizedStringLoader.parse(new ByteArrayInputStream(localizedStringsFileBytes),
        Locale.ENGLISH, "exact-byte-limit", LocalizedStringWarningHandler.ignore(), exactByteLimit).size());
    assertEquals(1, LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
        "exact-character-limit", LocalizedStringWarningHandler.ignore(), exactCharacterLimit).size());
    assertEquals(1, LocalizedStringLoader.parse(new StringReader(nestedLocalizedStringsFile), Locale.ENGLISH,
        "exact-nesting-limit", LocalizedStringWarningHandler.ignore(), exactNestingLimit).size(),
        "The root object counts as depth 1 and its localized-string object counts as depth 2");

    LocalizedStringLoadingException byteException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(
            new ByteArrayInputStream(localizedStringsFileBytes),
            Locale.ENGLISH, "one-over-byte-limit", LocalizedStringWarningHandler.ignore(), oneOverByteLimit));
    LocalizedStringLoadingException characterException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "one-over-character-limit",
            LocalizedStringWarningHandler.ignore(), oneOverCharacterLimit));
    LocalizedStringLoadingException nestingException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(nestedLocalizedStringsFile),
            Locale.ENGLISH, "one-over-nesting-limit", LocalizedStringWarningHandler.ignore(),
            oneOverNestingLimit));

    assertTrue(byteException.getMessage().contains("maximum size"));
    assertTrue(characterException.getMessage().contains("maximum size"));
    assertTrue(nestingException.getMessage().contains("nesting depth"));
  }

  @Test
  public void testLoadingOptionsRejectInvalidLimits() {
    assertFalse(LocalizedStringLoadingOptions.defaults().isExhaustiveClasspathSearchEnabled());
    assertTrue(LocalizedStringLoadingOptions.builder().exhaustiveClasspathSearch(true).build()
        .isExhaustiveClasspathSearchEnabled());
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
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumTotalInputBytes(0L));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumLocalizedStringsFiles(0));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumTranslationNodes(-1));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumWarnings(-1));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumDiscoveryEntries(0));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoadingOptions.builder().maximumDiscoveryEntries(
            LocalizedStringLoadingOptions.MAXIMUM_DISCOVERY_ENTRIES + 1));
  }

  @Test
  public void testFilesystemDiscoveryBudgetCountsEveryDirectChildAndAcceptsExactLimit() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-discovery-budget");
    tempDirectory.toFile().deleteOnExit();
    Files.write(tempDirectory.resolve("en.json"), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("notes.txt"), "not localized strings".getBytes(StandardCharsets.UTF_8));
    LocalizedStringLoadingOptions exactLimit = LocalizedStringLoadingOptions.builder()
        .maximumDiscoveryEntries(2)
        .build();
    LocalizedStringLoadingOptions oneBelowLimit = LocalizedStringLoadingOptions.builder()
        .maximumDiscoveryEntries(1)
        .build();

    assertEquals(Set.of(Locale.ENGLISH),
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, exactLimit).keySet());
    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory, oneBelowLimit)).getMessage()
        .contains("aggregate maximum of 1 discovery entries"));
  }

  @Test
  public void testInputStreamThatReturnsZeroFromBulkReadStillMakesProgress() {
    byte[] localizedStringsFile = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
    InputStream zeroReturningInputStream = new InputStream() {
      private int offset;
      private boolean returnZero = true;

      @Override
      public int read() {
        return offset == localizedStringsFile.length ? -1 : localizedStringsFile[offset++] & 0xFF;
      }

      @Override
      public int read(byte[] buffer, int bufferOffset, int length) {
        if (returnZero) {
          returnZero = false;
          return 0;
        }

        if (offset == localizedStringsFile.length)
          return -1;

        int bytesToRead = Math.min(length, localizedStringsFile.length - offset);
        System.arraycopy(localizedStringsFile, offset, buffer, bufferOffset, bytesToRead);
        offset += bytesToRead;
        return bytesToRead;
      }
    };

    assertEquals(1, LocalizedStringLoader.parse(zeroReturningInputStream, Locale.ENGLISH, "zero-read").size());
  }

  @Test
  public void testFilesystemLoadingEnforcesAggregateLimits() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-aggregate-limits");
    tempDirectory.toFile().deleteOnExit();
    String englishLocalizedStringsFile = "{\"hello\":\"world\"}";
    String frenchLocalizedStringsFile = "{\"goodbye\":\"world\"}";
    Files.write(tempDirectory.resolve("en.json"), englishLocalizedStringsFile.getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("fr.json"), frenchLocalizedStringsFile.getBytes(StandardCharsets.UTF_8));

    LocalizedStringLoadingOptions localizedStringsFilesLimited = LocalizedStringLoadingOptions.builder()
        .maximumLocalizedStringsFiles(1)
        .build();
    long totalInputBytes = (long) englishLocalizedStringsFile.getBytes(StandardCharsets.UTF_8).length +
        frenchLocalizedStringsFile.getBytes(StandardCharsets.UTF_8).length;
    LocalizedStringLoadingOptions exactAggregateByteLimit = LocalizedStringLoadingOptions.builder()
        .maximumTotalInputBytes(totalInputBytes)
        .build();
    LocalizedStringLoadingOptions oneOverAggregateByteLimit = LocalizedStringLoadingOptions.builder()
        .maximumTotalInputBytes(totalInputBytes - 1L)
        .build();
    LocalizedStringLoadingOptions translationNodeLimited = LocalizedStringLoadingOptions.builder()
        .maximumTranslationNodes(1)
        .build();

    assertEquals(Set.of(Locale.ENGLISH, Locale.FRENCH),
        LocalizedStringLoader.loadFromFilesystem(tempDirectory, exactAggregateByteLimit).keySet());
    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory, localizedStringsFilesLimited)).getMessage()
        .contains("aggregate localized strings file limit of 1"));
    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory, oneOverAggregateByteLimit)).getMessage()
        .contains("aggregate maximum"));
    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory, translationNodeLimited)).getMessage()
        .contains("aggregate maximum of 1 translation nodes"));

  }

  @Test
  public void testSingleResourceParseEnforcesLoadWideLimits() throws IOException {
    String localizedStringsFile = "{\"hello\":\"world\"}";
    byte[] localizedStringsFileBytes = localizedStringsFile.getBytes(StandardCharsets.UTF_8);
    Path file = Files.createTempFile("lokalized-single-resource-limits", ".json");
    file.toFile().deleteOnExit();
    Files.write(file, localizedStringsFileBytes);
    LocalizedStringLoadingOptions noTranslationNodes = LocalizedStringLoadingOptions.builder()
        .maximumTranslationNodes(0)
        .build();

    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(file, Locale.ENGLISH, LocalizedStringWarningHandler.ignore(),
            noTranslationNodes)).getMessage().contains("aggregate maximum of 0 translation nodes"));
    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new ByteArrayInputStream(localizedStringsFileBytes), Locale.ENGLISH,
            "input-stream",
            LocalizedStringWarningHandler.ignore(), noTranslationNodes)).getMessage()
        .contains("aggregate maximum of 0 translation nodes"));
    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH, "reader",
            LocalizedStringWarningHandler.ignore(), noTranslationNodes)).getMessage()
        .contains("aggregate maximum of 0 translation nodes"));

    LocalizedStringLoadingOptions aggregateByteLimited = LocalizedStringLoadingOptions.builder()
        .maximumTotalInputBytes(localizedStringsFileBytes.length - 1L)
        .build();
    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new ByteArrayInputStream(localizedStringsFileBytes), Locale.ENGLISH,
            "input-stream",
            LocalizedStringWarningHandler.ignore(), aggregateByteLimited)).getMessage()
        .contains("aggregate maximum"));
  }

  @Test
  public void testSingleResourceParseEnforcesWarningLimit() {
    String incompleteRussian = "{\"books\":{\"translation\":\"{{books}}\",\"placeholders\":{\"books\":{" +
        "\"value\":\"count\",\"translations\":{\"CARDINALITY_ONE\":\"книга\"," +
        "\"CARDINALITY_OTHER\":\"книг\"}}}}}";
    LocalizedStringLoadingOptions noWarnings = LocalizedStringLoadingOptions.builder()
        .maximumWarnings(0)
        .build();

    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(incompleteRussian), Locale.forLanguageTag("ru"),
            "single-warning", LocalizedStringWarningHandler.ignore(), noWarnings)).getMessage()
        .contains("aggregate maximum of 0 warnings"));
  }

  @Test
  public void testNestedAlternativesCountTowardTranslationNodeLimit() {
    String localizedStringsFile = "{\"root\":{\"translation\":\"fallback\",\"alternatives\":[" +
        "{\"count == 1\":\"one\"}]}}";
    LocalizedStringLoadingOptions oneTranslationNode = LocalizedStringLoadingOptions.builder()
        .maximumTranslationNodes(1)
        .build();
    LocalizedStringLoadingOptions twoTranslationNodes = LocalizedStringLoadingOptions.builder()
        .maximumTranslationNodes(2)
        .build();

    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "nested-alternative",
            LocalizedStringWarningHandler.ignore(), oneTranslationNode)).getMessage()
        .contains("aggregate maximum of 1 translation nodes"));
    assertEquals(1, LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
        "nested-alternative",
        LocalizedStringWarningHandler.ignore(), twoTranslationNodes).size());
  }

	@Test
	public void testTranslationNodeLimitIsReservedBeforeLocalizedStringsEntryValidation() throws IOException {
		Path tempDirectory = Files.createTempDirectory("lokalized-translation-node-limit");
		tempDirectory.toFile().deleteOnExit();
		Files.write(tempDirectory.resolve("en.json"),
				"{\"first\":\"ok\",\"second\":{\"translation\":null}}".getBytes(StandardCharsets.UTF_8));
		LocalizedStringLoadingOptions oneTranslationNode = LocalizedStringLoadingOptions.builder()
				.maximumTranslationNodes(1)
				.build();

		LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
				() -> LocalizedStringLoader.loadFromFilesystem(tempDirectory, oneTranslationNode));
		assertTrue(exception.getMessage().contains("aggregate maximum of 1 translation nodes"));
	}

  @Test
  public void testFilesystemLoadingEnforcesAggregateWarningLimit() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-warning-limit");
    tempDirectory.toFile().deleteOnExit();
    String incompleteRussian = "{\"books\":{\"translation\":\"{{books}}\",\"placeholders\":{\"books\":{" +
        "\"value\":\"count\",\"translations\":{\"CARDINALITY_ONE\":\"книга\"," +
        "\"CARDINALITY_OTHER\":\"книг\"}}}}}";
    Files.write(tempDirectory.resolve("ru.json"), incompleteRussian.getBytes(StandardCharsets.UTF_8));
    LocalizedStringLoadingOptions noWarnings = LocalizedStringLoadingOptions.builder()
        .maximumWarnings(0)
        .build();

    assertTrue(assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory, LocalizedStringWarningHandler.ignore(),
            noWarnings)).getMessage().contains("aggregate maximum of 0 warnings"));
  }

  @Test
  public void testLoaderRejectsExplicitNullsAndIncompleteAlternativeShapes() {
    String[] invalidLocalizedStringsFiles = {
        "{\"key\":{\"translation\":null}}",
        "{\"key\":{\"translation\":\"value\",\"commentary\":null}}",
        "{\"key\":{\"translation\":\"value\",\"placeholders\":null}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"value\":\"count\",\"range\":null}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"value\":null,\"range\":{\"start\":\"a\",\"end\":\"b\"}}}}}",
        "{\"key\":{\"translation\":\"{{p}}\",\"placeholders\":{\"p\":{\"value\":\"count\",\"translations\":null}}}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":null}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":[]}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":[null]}}",
        "{\"key\":{\"translation\":\"value\",\"alternatives\":[{}]}}"
    };

    for (String invalidLocalizedStringsFile : invalidLocalizedStringsFiles)
      assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.parse(new StringReader(invalidLocalizedStringsFile), Locale.ENGLISH,
              "schema-parity-test", LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults()),
          invalidLocalizedStringsFile);
  }

  @Test
  public void testLoaderRejectsMultipleExpressionsInOneAlternativeObject() {
    String localizedStringsFile = "{\"key\":{\"alternatives\":[{" +
        "\"count == 1\":{\"translation\":\"one\"}," +
        "\"count > 0\":{\"translation\":\"positive\"}}]}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "ordered-alternatives"));

    assertTrue(exception.getMessage().contains("exactly one expression"));
    assertTrue(exception.getMessage().contains("first-match precedence"));
  }

  @Test
  public void testLoaderParsesConstantAndConditionalTemplatePlaceholders() {
    String localizedStringsFile = "{\"Search completed.\":{\"translation\":\"Found {{resultSummary}} {{timing}}.\"," +
        "\"placeholders\":{" +
        "\"resultSummary\":{\"translation\":\"{{formattedResultCount}} {{resultNoun}}\"," +
        "\"alternatives\":[{\"resultCount == 0\":\"no results\"}," +
        "{\"resultCount >= resultLimit\":\"at least {{formattedResultLimit}} results\"}]}," +
        "\"timing\":{\"translation\":\"in {{formattedDuration}}\"}," +
        "\"resultNoun\":{\"value\":\"resultCount\",\"translations\":{" +
        "\"CARDINALITY_ONE\":\"result\",\"CARDINALITY_OTHER\":\"results\"}}}}}";

    LocalizedString localizedString = LocalizedStringLoader.parse(new StringReader(localizedStringsFile),
        Locale.ENGLISH,
        "template-placeholder-test", LocalizedStringWarningHandler.ignore(),
        LocalizedStringLoadingOptions.defaults()).iterator().next();
    Map<String, PlaceholderDefinition> definitions = localizedString.getPlaceholderDefinitions();

    assertEquals(3, definitions.size());
    assertTrue(definitions.get("resultSummary") instanceof ExpressionTranslation);
    assertTrue(definitions.get("timing") instanceof ExpressionTranslation);
    assertTrue(definitions.get("resultNoun") instanceof LanguageFormTranslation);

    ExpressionTranslation resultSummary = (ExpressionTranslation) definitions.get("resultSummary");
    assertEquals("{{formattedResultCount}} {{resultNoun}}", resultSummary.getTranslation());
    assertEquals(List.of(
        new ExpressionAlternative("resultCount == 0", "no results"),
        new ExpressionAlternative("resultCount >= resultLimit", "at least {{formattedResultLimit}} results")),
        resultSummary.getAlternatives());

    ExpressionTranslation timing = (ExpressionTranslation) definitions.get("timing");
    assertEquals("in {{formattedDuration}}", timing.getTranslation());
    assertTrue(timing.getAlternatives().isEmpty());
  }

  @Test
  public void testLoaderRejectsInvalidTemplatePlaceholderShapes() {
    String[] invalidPlaceholderDefinitions = {
        "{\"alternatives\":[{\"count == 0\":\"none\"}]}",
        "{\"translation\":null}",
        "{\"translation\":{}}",
        "{\"translation\":\"default\",\"alternatives\":null}",
        "{\"translation\":\"default\",\"alternatives\":{}}",
        "{\"translation\":\"default\",\"alternatives\":[]}",
        "{\"translation\":\"default\",\"alternatives\":[null]}",
        "{\"translation\":\"default\",\"alternatives\":[\"not an object\"]}",
        "{\"translation\":\"default\",\"alternatives\":[{}]}",
        "{\"translation\":\"default\",\"alternatives\":[{\"a == 1\":\"one\",\"a == 2\":\"two\"}]}",
        "{\"translation\":\"default\",\"alternatives\":[{\"count == 0\":null}]}",
        "{\"translation\":\"default\",\"alternatives\":[{\"count == 0\":{\"translation\":\"none\"}}]}"
    };

    for (String placeholderDefinition : invalidPlaceholderDefinitions) {
      String localizedStringsFile = "{\"root\":{\"translation\":\"{{summary}}\",\"placeholders\":{\"summary\":" +
          placeholderDefinition + "}}}";
      assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
              "invalid-template-placeholder", LocalizedStringWarningHandler.ignore(),
              LocalizedStringLoadingOptions.defaults()), placeholderDefinition);
    }
  }

  @Test
  public void testLoaderReportsContextForInvalidTemplateExpressionsAndFragments() {
    String invalidExpressionLocalizedStringsFile =
        "{\"root.key\":{\"translation\":\"fixed\",\"placeholders\":{" +
        "\"summary\":{\"translation\":\"default\",\"alternatives\":[{\"count ==\":\"none\"}]}}}}";
    LocalizedStringLoadingException expressionException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(invalidExpressionLocalizedStringsFile), Locale.ENGLISH,
            "context-source", LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults()));

    assertTrue(expressionException.getMessage().contains("context-source"));
    assertTrue(expressionException.getMessage().contains("root.key"));
    assertTrue(expressionException.getMessage().contains("summary"));
    assertTrue(expressionException.getMessage().contains("alternative 0"));
    assertTrue(expressionException.getMessage().contains("count =="));

    String invalidDefaultLocalizedStringsFile =
        "{\"root.key\":{\"translation\":\"fixed\",\"placeholders\":{" +
        "\"summary\":{\"translation\":\"{{ malformed }}\"}}}}";
    LocalizedStringLoadingException defaultException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(invalidDefaultLocalizedStringsFile), Locale.ENGLISH,
            "context-source", LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults()));

    assertTrue(defaultException.getMessage().contains("default fragment"));
    assertTrue(defaultException.getMessage().contains("summary"));
    assertTrue(defaultException.getMessage().contains("root.key"));

    String invalidAlternativeLocalizedStringsFile =
        "{\"root.key\":{\"translation\":\"fixed\",\"placeholders\":{" +
        "\"summary\":{\"translation\":\"default\",\"alternatives\":[" +
        "{\"count == 0\":\"{{ malformed }}\"}]}}}}";
    LocalizedStringLoadingException alternativeException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(invalidAlternativeLocalizedStringsFile), Locale.ENGLISH,
            "context-source", LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults()));

    assertTrue(alternativeException.getMessage().contains("fragment alternative 0"));
    assertTrue(alternativeException.getMessage().contains("count == 0"));
    assertTrue(alternativeException.getMessage().contains("summary"));
    assertTrue(alternativeException.getMessage().contains("root.key"));
  }

  @Test
  public void testLoaderReportsSelectedPathForInvalidPlaceholdersInNestedWholeMessageAlternatives() {
    String localizedStringsFile = "{\"root.key\":{\"translation\":\"default\",\"alternatives\":[" +
        "{\"count >= 1\":{\"translation\":\"outer\",\"alternatives\":[" +
        "{\"priority == 1\":{\"translation\":\"Broken {{ name }}\"}}]}}]}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "nested-placeholder-context", LocalizedStringWarningHandler.ignore(),
            LocalizedStringLoadingOptions.defaults()));

    assertTrue(exception.getMessage().contains("invalid placeholder reference"));
    assertTrue(exception.getMessage().contains("root.key"));
    assertTrue(exception.getMessage().contains(
        "root.key -> alternative[count >= 1] -> alternative[priority == 1]"));
  }

  @Test
  public void testLoaderRejectsMixedPlaceholderModesByMemberPresenceBeforeNullChecks() {
    List<String> mixedPlaceholderDefinitions = new ArrayList<>(List.of(
        "{\"value\":\"count\",\"translation\":null}",
        "{\"value\":null,\"translation\":\"default\"}",
        "{\"translations\":null,\"alternatives\":null}"
    ));

    for (String languageFormMember : List.of("value", "range", "translations"))
      for (String templateMember : List.of("translation", "alternatives"))
        mixedPlaceholderDefinitions.add(format("{\"%s\":null,\"%s\":null}",
            languageFormMember, templateMember));

    for (String placeholderDefinition : mixedPlaceholderDefinitions) {
      String localizedStringsFile =
          "{\"root.key\":{\"translation\":\"{{summary}}\",\"placeholders\":{\"summary\":" +
          placeholderDefinition + "}}}";
      LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
              "mixed-placeholder-mode",
              LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults()));

      assertTrue(exception.getMessage().contains("mixes language-form members"), placeholderDefinition);
      assertTrue(exception.getMessage().contains("template members"), placeholderDefinition);
      assertTrue(exception.getMessage().contains("summary"), placeholderDefinition);
      assertTrue(exception.getMessage().contains("root.key"), placeholderDefinition);
    }
  }

  @Test
  public void testLoaderRejectsUnknownPlaceholderMembersBeforeMixedModeChecks() {
    String localizedStringsFile =
        "{\"root\":{\"translation\":\"{{summary}}\",\"placeholders\":{\"summary\":{" +
        "\"value\":\"count\",\"translation\":\"default\",\"unknown\":null}}}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "unknown-before-mode"));

    assertTrue(exception.getMessage().contains("unexpected field 'unknown'"));
    assertFalse(exception.getMessage().contains("mixes language-form members"));
  }

  @Test
  public void testUnexpectedObjectMemberDiagnosticSortsValidFields() {
    String localizedStringsFile =
        "{\"hello\":{\"translation\":\"world\",\"unknown\":true}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "deterministic-valid-fields"));

    assertEquals("deterministic-valid-fields: unexpected field 'unknown' in localized string for key 'hello'. " +
        "Valid fields are [alternatives, commentary, placeholders, translation]", exception.getMessage());
  }

  @Test
  public void testAllPlaceholderKindsCountTowardTranslationNodeLimit() {
    String localizedStringsFile = "{\"root\":{\"translation\":\"{{noun}} {{constant}} {{summary}}\"," +
        "\"placeholders\":{" +
        "\"noun\":{\"value\":\"count\",\"translations\":{" +
        "\"CARDINALITY_ONE\":\"item\",\"CARDINALITY_OTHER\":\"items\"}}," +
        "\"constant\":{\"translation\":\"ready\"}," +
        "\"summary\":{\"translation\":\"normal\",\"alternatives\":[" +
        "{\"count == 0\":\"none\"},{\"count > 5\":\"many\"}]}}," +
        "\"alternatives\":[{\"count < 0\":\"negative\"}]}}";
    LocalizedStringLoadingOptions sixTranslationNodes = LocalizedStringLoadingOptions.builder()
        .maximumTranslationNodes(6)
        .build();
    LocalizedStringLoadingOptions sevenTranslationNodes = LocalizedStringLoadingOptions.builder()
        .maximumTranslationNodes(7)
        .build();

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "translation-node-count",
            LocalizedStringWarningHandler.ignore(), sixTranslationNodes));
    assertTrue(exception.getMessage().contains("aggregate maximum of 6 translation nodes"));
    assertEquals(1, LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
        "translation-node-count", LocalizedStringWarningHandler.ignore(), sevenTranslationNodes).size());
  }

  @Test
  public void testEveryTranslationNodeKindHasAnExactIndependentBoundary() {
    List<Entry<String, Integer>> localizedStringsFilesAndExpectedNodes = List.of(
        Map.entry("{}", 0),
        Map.entry("{\"root\":\"value\"}", 1),
        Map.entry("{\"root\":{\"translation\":\"{{noun}}\",\"placeholders\":{\"noun\":{" +
            "\"value\":\"count\",\"translations\":{\"CARDINALITY_ONE\":\"item\"}}}}}", 2),
        Map.entry("{\"root\":{\"translation\":\"{{range}}\",\"placeholders\":{\"range\":{" +
            "\"range\":{\"start\":\"min\",\"end\":\"max\"},\"translations\":{" +
            "\"CARDINALITY_OTHER\":\"items\"}}}}}", 2),
        Map.entry("{\"root\":{\"translation\":\"{{constant}}\",\"placeholders\":{\"constant\":{" +
            "\"translation\":\"ready\"}}}}", 2),
        Map.entry("{\"root\":{\"translation\":\"{{fragment}}\",\"placeholders\":{\"fragment\":{" +
            "\"translation\":\"default\",\"alternatives\":[{\"count == 0\":\"none\"}]}}}}", 3),
        Map.entry("{\"root\":{\"translation\":\"{{fragment}}\",\"placeholders\":{\"fragment\":{" +
            "\"translation\":\"default\",\"alternatives\":[{\"count == 0\":\"none\"}," +
            "{\"count == 1\":\"one\"}]}}}}", 4),
        Map.entry("{\"root\":{\"translation\":\"default\",\"alternatives\":[{" +
            "\"count == 1\":\"one\"}]}}", 2)
    );

    for (Entry<String, Integer> localizedStringsFileAndExpectedNodes : localizedStringsFilesAndExpectedNodes) {
      String localizedStringsFile = localizedStringsFileAndExpectedNodes.getKey();
      Integer expectedNodes = localizedStringsFileAndExpectedNodes.getValue();
      LocalizedStringLoadingOptions exactBoundary = LocalizedStringLoadingOptions.builder()
          .maximumTranslationNodes(expectedNodes)
          .build();

      assertEquals(expectedNodes == 0 ? 0 : 1,
          LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
              "isolated-node-boundary", LocalizedStringWarningHandler.ignore(), exactBoundary).size(),
          localizedStringsFile);

      if (expectedNodes > 0) {
        LocalizedStringLoadingOptions oneBelowBoundary = LocalizedStringLoadingOptions.builder()
            .maximumTranslationNodes(expectedNodes - 1)
            .build();
        LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
            () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
                "isolated-node-boundary", LocalizedStringWarningHandler.ignore(), oneBelowBoundary),
            localizedStringsFile);
        assertTrue(exception.getMessage().contains(
            format("aggregate maximum of %d translation nodes", expectedNodes - 1)), localizedStringsFile);
      }
    }
  }

  @Test
  public void testClasspathLoadingRejectsEscapingPackagePaths() {
    ClassLoader classLoader = getClass().getClassLoader();

    for (String invalidPackage : List.of("", "/", "/strings", "../strings", "strings/../other",
        "strings//other", "strings/./other", "strings\\other", "D:strings", "z:localized-strings/strings"))
      assertThrows(IllegalArgumentException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, invalidPackage), invalidPackage);
  }

  @Test
  public void testFilesystemLoadingAcceptsUndeterminedLanguageTags() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-undetermined-language");
    tempDirectory.toFile().deleteOnExit();
    Files.write(tempDirectory.resolve("und.json"), "{\"root\":\"value\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDirectory.resolve("und-Latn.json"), "{\"latin\":\"value\"}".getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromFilesystem(tempDirectory);

    assertEquals(Set.of(Locale.ROOT, Locale.forLanguageTag("und-Latn")), localizedStringsByLocale.keySet());
    assertEquals("und", Locale.ROOT.toLanguageTag());
    assertEquals("und-Latn", Locale.forLanguageTag("und-Latn").toLanguageTag());
  }

  @Test
  public void testClasspathLoadingNormalizesTrailingSlashes() {
    Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromClasspath(getClass().getClassLoader(), "strings///");

    verifyLocalizedStringsByLocale(localizedStringsByLocale);
  }

  @Test
  public void testDuplicateMemberDiagnosticPathIsBounded() {
    String longMemberName = String.join("", java.util.Collections.nCopies(10_000, "a"));
    String localizedStringsFile = "{\"root\":{\"translation\":\"value\",\"" + longMemberName +
        "\":{\"duplicate\":1,\"duplicate\":2}}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "bounded-path"));

    assertTrue(exception.getMessage().length() < 5_000);
    assertTrue(exception.getMessage().contains("duplicate JSON object member"));

    String duplicateLongMemberLocalizedStringsFile = "{\"root\":{\"" + longMemberName + "\":1,\"" +
        longMemberName + "\":2}}";
    LocalizedStringLoadingException longMemberException = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(duplicateLongMemberLocalizedStringsFile), Locale.ENGLISH,
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
        ("{\"Hello {{имя}}\":{\"translation\":\"Hello {{имя}} {{नाम}} {{книги}}\",\"placeholders\":{" +
            "\"книги\":{\"value\":\"caféCount\",\"translations\":{\"CARDINALITY_ONE\":\"book\",\"CARDINALITY_OTHER\":\"books\"}}" +
            "},\"alternatives\":[{\"caféCount == количество2\":{\"translation\":\"Matched {{имя}}\"}}]}}")
            .getBytes(StandardCharsets.UTF_8));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromFilesystem(tempDirectory);
    LocalizedString localizedString = localizedStringsByLocale.get(Locale.forLanguageTag("en")).iterator().next();

    assertTrue(localizedString.getPlaceholderDefinitions().containsKey("книги"));
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
  public void testLoadingRejectsRemovedPlaceholderMetadataSyntax() {
    String localizedStringsFile =
        "{\"Hello\":{\"translation\":\"Hello {{name}}\",\"placeholderMetadata\":{" +
        "\"name\":{\"type\":\"STRING\",\"example\":\"Ada\"}}}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "removed-metadata"));

    assertTrue(exception.getMessage().contains("unexpected field 'placeholderMetadata'"));
  }

  @Test
  public void testLoadingRejectsRemovedSelectorSyntax() {
    String localizedStringsFile =
        "{\"Items\":{\"translation\":\"{{items}}\",\"placeholders\":{\"items\":{" +
        "\"selectors\":[{\"value\":\"count\",\"form\":\"CARDINALITY\"}]," +
        "\"translations\":[{\"when\":{\"CARDINALITY\":\"CARDINALITY_ONE\"},\"value\":\"item\"}]}}}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "removed-selectors"));

    assertTrue(exception.getMessage().contains("unexpected field 'selectors'"));
  }

  @Test
  public void testLoadingRejectsRemovedRuleArrayTranslationsSyntax() {
    String localizedStringsFile =
        "{\"Items\":{\"translation\":\"{{items}}\",\"placeholders\":{\"items\":{" +
        "\"value\":\"count\",\"translations\":[" +
        "{\"when\":{\"CARDINALITY\":\"CARDINALITY_ONE\"},\"value\":\"item\"}]}}}}";

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.parse(new StringReader(localizedStringsFile), Locale.ENGLISH,
            "removed-rule-array"));

    assertTrue(exception.getMessage().contains("placeholder translations value must be an object"));
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
  public void testClasspathLoadingUsesRuntimeEntryFromMultiReleaseJar() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-multi-release", ".jar");
    tempJar.toFile().deleteOnExit();
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Multi-Release", "true");

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
      writeJarEntry(jarOutputStream, "strings/", null);
      writeJarEntry(jarOutputStream, "strings/en.json", "{\"message\":\"base\"}");
      writeJarEntry(jarOutputStream, "META-INF/versions/9/strings/en.json", "{\"message\":\"versioned\"}");
    }

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings");
      LocalizedString localizedString = localizedStringsByLocale.get(Locale.ENGLISH).iterator().next();

      assertEquals("versioned", localizedString.getTranslation().orElse(null));
    }
  }

  @Test
  public void testClasspathLoadingRejectsDuplicateBaseJarEntries() throws IOException {
    verifyDuplicatePhysicalJarEntriesRejected(false);
  }

  @Test
  public void testClasspathLoadingRejectsDuplicateSameVersionMultiReleaseJarEntries() throws IOException {
    verifyDuplicatePhysicalJarEntriesRejected(true);
  }

  @Test
  public void testClasspathLoadingIgnoresMalformedMultiReleaseJarVersionDirectories() throws IOException {
    for (String malformedVersion : List.of("09", "+9", "\u0669", "\uFF19")) {
      verifyMalformedMultiReleaseVersionIgnored(malformedVersion, true, false);
      verifyMalformedMultiReleaseVersionIgnored(malformedVersion, false, true);
    }
  }

	@Test
	public void testMultiReleaseJarDoesNotOverlayLogicalMetaInfResources() throws IOException {
		Path tempJar = Files.createTempFile("lokalized-strings-meta-inf-multi-release", ".jar");
		tempJar.toFile().deleteOnExit();
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().putValue("Multi-Release", "true");

		try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
			writeJarEntry(jarOutputStream, "META-INF/lokalized/", null);
			writeJarEntry(jarOutputStream, "META-INF/lokalized/en.json", "{\"message\":\"base\"}");
			writeJarEntry(jarOutputStream, "META-INF/versions/9/META-INF/lokalized/en.json",
					"{\"message\":\"versioned\"}");
		}

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
			Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
					LocalizedStringLoader.loadFromClasspath(classLoader, "META-INF/lokalized");
			LocalizedString localizedString = localizedStringsByLocale.get(Locale.ENGLISH).iterator().next();

			assertEquals("base", localizedString.getTranslation().orElse(null));
		}
	}

  @Test
  public void testExhaustiveClasspathLoadingFollowsManifestClasspath() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-manifest-classpath");
    tempDirectory.toFile().deleteOnExit();
    Path localizedStringsFilesJar = tempDirectory.resolve("localized-strings-files.jar");
    Path applicationJar = tempDirectory.resolve("application.jar");
    writeJarEntryWithoutDirectory(localizedStringsFilesJar, "strings/en.json", "{\"hello\":\"world\"}");

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
        localizedStringsFilesJar.getFileName().toString());

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(applicationJar), manifest)) {
      jarOutputStream.flush(); // Manifest-only application JAR.
    }

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{applicationJar.toUri().toURL()}, null)) {
      assertNotNull(classLoader.getResource("strings/en.json"),
          "The manifest Class-Path resource should be visible through ordinary resource lookup");
      LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
          .exhaustiveClasspathSearch(true)
          .build();
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions);

      assertEquals(Set.of(Locale.ENGLISH), localizedStringsByLocale.keySet());
      assertEquals(1, localizedStringsByLocale.get(Locale.ENGLISH).size());
    }
  }

  @Test
  public void testExhaustiveClasspathLoadingSkipsUnusableManifestClasspathEntries() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-unusable-manifest-classpath");
    tempDirectory.toFile().deleteOnExit();
    Path localizedStringsFilesJar = tempDirectory.resolve("localized-strings-files.jar");
    Path applicationJar = tempDirectory.resolve("application.jar");
    writeJarEntryWithoutDirectory(localizedStringsFilesJar, "strings/en.json", "{\"hello\":\"world\"}");

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
        "ignored.jar?query ignored.jar#fragment % " + localizedStringsFilesJar.getFileName());

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(applicationJar), manifest)) {
      writeJarEntry(jarOutputStream, "strings/", null);
      writeJarEntry(jarOutputStream, "strings/fr.json", "{\"bonjour\":\"monde\"}");
    }

    URL applicationPackageUrl = new URL("jar:" + applicationJar.toUri().toURL() + "!/strings");

    // Enumerate the primary package directly so this test exercises Lokalized's manifest traversal consistently.
    // Some JDK URLClassLoader versions inspect malformed optional entries while enumerating and throw before a
    // caller can perform its own exhaustive traversal; that boundary has separate wrapping coverage below.
    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{applicationJar.toUri().toURL()}, null) {
      @Override
      public Enumeration<URL> getResources(String name) {
        return Collections.enumeration(List.of(applicationPackageUrl));
      }
    }) {
      assertEquals(Set.of(Locale.FRENCH),
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings").keySet(),
          "Optional manifest entries must not affect ordinary package discovery");

      LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
          .exhaustiveClasspathSearch(true)
          .build();
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions);

      assertEquals(Set.of(Locale.ENGLISH, Locale.FRENCH), localizedStringsByLocale.keySet());
      assertEquals(1, localizedStringsByLocale.get(Locale.ENGLISH).size());
      assertEquals(1, localizedStringsByLocale.get(Locale.FRENCH).size());
    }
  }

  @Test
  public void testClasspathLoadingWrapsResourceEnumerationFailure() throws IOException {
    ClassLoader classLoader = new ClassLoader(null) {
      @Override
      public Enumeration<URL> getResources(String name) {
        return new Enumeration<>() {
          @Override
          public boolean hasMoreElements() {
            throw new IllegalArgumentException("Malformed optional classpath URL");
          }

          @Override
          public URL nextElement() {
            throw new AssertionError("Should not be called");
          }
        };
      }
    };

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings"));

    assertTrue(exception.getMessage().contains("Unable to enumerate classpath resources"));
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
  }

  @Test
  public void testClasspathLoadingWrapsNonJarConnectionDuringLocationIdentity() throws IOException {
    URL customJarUrl = new URL(null, "jar:file:/not-used.jar!/strings", new URLStreamHandler() {
      @Override
      protected URLConnection openConnection(URL url) {
        return inertUrlConnection(url);
      }
    });
    ClassLoader classLoader = classLoaderForClasspathUrl(customJarUrl);

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings"));

    assertTrue(exception.getMessage().contains("Unable to resolve classpath location"));
    assertTrue(exception.getCause() instanceof IOException);
    assertTrue(exception.getCause().getMessage().contains("instead of 'java.net.JarURLConnection'"));
  }

  @Test
  public void testClasspathLoadingWrapsNonJarConnectionDuringJarLoad() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-custom-jar-handler", ".jar");
    tempJar.toFile().deleteOnExit();
    URL standardJarUrl = new URL("jar:" + tempJar.toUri().toURL() + "!/strings");
    AtomicInteger connectionCount = new AtomicInteger();
    URL customJarUrl = new URL(null, standardJarUrl.toExternalForm(), new URLStreamHandler() {
      @Override
      protected URLConnection openConnection(URL url) throws IOException {
        if (connectionCount.getAndIncrement() == 0)
          return standardJarUrl.openConnection();

        return inertUrlConnection(url);
      }
    });
    ClassLoader classLoader = classLoaderForClasspathUrl(customJarUrl);

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings"));

    assertEquals(2, connectionCount.get());
    assertTrue(exception.getMessage().contains("Unable to load localized strings"));
    assertTrue(exception.getCause() instanceof IOException);
    assertTrue(exception.getCause().getMessage().contains("instead of 'java.net.JarURLConnection'"));
  }

  @Test
  public void testClasspathLoadingWrapsMalformedPrimaryPackageUrl() throws IOException {
    Path classpathRoot = Files.createTempDirectory("lokalized-malformed-package-url");
    classpathRoot.toFile().deleteOnExit();
    URL malformedPackageUrl = new URL(classpathRoot.toUri().toURL().toExternalForm() + "?query");
    ClassLoader classLoader = new ClassLoader(null) {
      @Override
      public Enumeration<URL> getResources(String name) {
        return Collections.enumeration(List.of(malformedPackageUrl));
      }
    };

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings"));

    assertTrue(exception.getMessage().contains("Unable to resolve classpath location"));
    assertTrue(exception.getCause() instanceof IllegalArgumentException);
  }

  @Test
  public void testExhaustiveClasspathLoadingWrapsMalformedPrimaryRootUrl() throws IOException {
    Path classpathRoot = Files.createTempDirectory("lokalized-malformed-root-url");
    classpathRoot.toFile().deleteOnExit();
    URL malformedRootUrl = new URL(classpathRoot.toUri().toURL().toExternalForm() + "?query");
    LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
        .exhaustiveClasspathSearch(true)
        .build();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{malformedRootUrl}, null) {
      @Override
      public Enumeration<URL> getResources(String name) {
        return Collections.emptyEnumeration();
      }
    }) {
      LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions));

      assertTrue(exception.getMessage().contains("Unable to resolve classpath root"));
      assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void testExplicitClasspathResourceMappingSupportsNonEnumerableClassloader() {
    ClassLoader classLoader = new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String resourcePath) {
        if ("localized-strings/en.json".equals(resourcePath))
          return new ByteArrayInputStream("{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

        if ("localized-strings/root.json".equals(resourcePath))
          return new ByteArrayInputStream("{\"fallback\":\"value\"}".getBytes(StandardCharsets.UTF_8));

        return null;
      }
    };

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromClasspathResources(classLoader,
            Map.of(Locale.ENGLISH, "localized-strings/en.json", Locale.ROOT,
                "localized-strings/root.json"));

    assertEquals(Set.of(Locale.ENGLISH, Locale.ROOT), localizedStringsByLocale.keySet());
    assertEquals(1, localizedStringsByLocale.get(Locale.ENGLISH).size());
    assertEquals(1, localizedStringsByLocale.get(Locale.ROOT).size());

    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      assertEquals(localizedStringsByLocale, LocalizedStringLoader.loadFromClasspathResources(
          Map.of(Locale.ENGLISH, "localized-strings/en.json", Locale.ROOT,
              "localized-strings/root.json")));
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromClasspathResources(classLoader,
            Map.of(Locale.FRENCH, "localized-strings/missing.json")));
    assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoader.loadFromClasspathResources(classLoader,
            Map.of(Locale.FRENCH, "C:localized-strings/fr.json")));
  }

  @Test
  public void testLoaderResultMapUsesLocaleEqualityAndDeterministicTagOrder() {
    ClassLoader classLoader = new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String resourcePath) {
        return new ByteArrayInputStream(("{\"" + resourcePath + "\":\"value\"}")
            .getBytes(StandardCharsets.UTF_8));
      }
    };
    Map<Locale, String> resourcePathByLocale = new LinkedHashMap<>();
    resourcePathByLocale.put(Locale.ROOT, "localized-strings/root.json");
    resourcePathByLocale.put(Locale.FRENCH, "localized-strings/fr.json");
    resourcePathByLocale.put(Locale.ENGLISH, "localized-strings/en.json");

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
        LocalizedStringLoader.loadFromClasspathResources(classLoader, resourcePathByLocale);
    Locale explicitUndeterminedLocale = new Locale("und");
    Locale malformedRootRenderingLocale = new Locale("x");

    assertEquals(List.of(Locale.ENGLISH, Locale.FRENCH, Locale.ROOT),
        new ArrayList<>(localizedStringsByLocale.keySet()));
    assertFalse(localizedStringsByLocale.containsKey(explicitUndeterminedLocale));
    assertEquals(null, localizedStringsByLocale.get(explicitUndeterminedLocale));
    assertFalse(localizedStringsByLocale.containsKey(malformedRootRenderingLocale));
    assertEquals(null, localizedStringsByLocale.get(malformedRootRenderingLocale));

    Map<Locale, Set<LocalizedString>> mapWithExplicitUndeterminedLocale = new LinkedHashMap<>();
    mapWithExplicitUndeterminedLocale.put(Locale.ENGLISH, localizedStringsByLocale.get(Locale.ENGLISH));
    mapWithExplicitUndeterminedLocale.put(Locale.FRENCH, localizedStringsByLocale.get(Locale.FRENCH));
    mapWithExplicitUndeterminedLocale.put(explicitUndeterminedLocale, localizedStringsByLocale.get(Locale.ROOT));

    assertFalse(localizedStringsByLocale.equals(mapWithExplicitUndeterminedLocale));
    assertFalse(mapWithExplicitUndeterminedLocale.equals(localizedStringsByLocale));

    Map<Locale, Set<LocalizedString>> mapWithMalformedRootRenderingLocale = new LinkedHashMap<>();
    mapWithMalformedRootRenderingLocale.put(Locale.ENGLISH, localizedStringsByLocale.get(Locale.ENGLISH));
    mapWithMalformedRootRenderingLocale.put(Locale.FRENCH, localizedStringsByLocale.get(Locale.FRENCH));
    mapWithMalformedRootRenderingLocale.put(malformedRootRenderingLocale,
        localizedStringsByLocale.get(Locale.ROOT));

    assertFalse(localizedStringsByLocale.equals(mapWithMalformedRootRenderingLocale));
    assertFalse(mapWithMalformedRootRenderingLocale.equals(localizedStringsByLocale));
    assertThrows(UnsupportedOperationException.class, localizedStringsByLocale::clear);
    Set<LocalizedString> englishLocalizedStrings =
        requireNonNull(localizedStringsByLocale.get(Locale.ENGLISH));
    assertThrows(UnsupportedOperationException.class, englishLocalizedStrings::clear);
  }

  @Test
  public void testExplicitClasspathResourceMappingPreflightsFileLimit() {
    ClassLoader unopenedClassLoader = new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String resourcePath) {
        throw new AssertionError("An oversized mapping must be rejected before resources are opened");
      }
    };
    Map<Locale, String> resourcePathByLocale = new LinkedHashMap<>();
    resourcePathByLocale.put(Locale.ENGLISH, "localized-strings/en.json");
    resourcePathByLocale.put(Locale.FRENCH, "localized-strings/fr.json");
    LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
        .maximumLocalizedStringsFiles(1)
        .build();

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromClasspathResources(
            unopenedClassLoader, resourcePathByLocale, loadingOptions));

    assertTrue(exception.getMessage().contains("contains 2 localized strings files"));
    assertTrue(exception.getMessage().contains("limit of 1"));
  }

  @Test
  public void testPackageDiscoveryRejectsReservedMultiReleaseNamespaceButExactResourcesRemainAvailable() {
    ClassLoader classLoader = new ClassLoader(null) {
      @Override
      public Enumeration<URL> getResources(String name) {
        throw new AssertionError("A reserved package must be rejected before classpath lookup");
      }

      @Override
      public InputStream getResourceAsStream(String resourcePath) {
        if ("META-INF/versions/9/strings/en.json".equals(resourcePath))
          return new ByteArrayInputStream("{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));

        return null;
      }
    };

    for (String reservedPackage : List.of("META-INF/versions", "META-INF/versions/9/strings")) {
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, reservedPackage));
      assertTrue(exception.getMessage().contains("reserved physical multi-release JAR namespace"));
    }

    assertEquals(Set.of(Locale.ENGLISH), LocalizedStringLoader.loadFromClasspathResources(classLoader,
        Map.of(Locale.ENGLISH, "META-INF/versions/9/strings/en.json")).keySet());
  }

  @Test
  public void testExplicitClasspathResourceMappingRejectsMalformedLocaleBeforeTagCollision() {
    ClassLoader unopenedClassLoader = new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String resourcePath) {
        throw new AssertionError("Malformed locale mappings must be rejected before resources are opened");
      }
    };
    Locale malformedUndeterminedLocale = new Locale("x");
    Map<Locale, String> resourcePathByLocale = Map.of(
        Locale.ROOT, "localized-strings/root.json",
        malformedUndeterminedLocale, "localized-strings/malformed.json");

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> LocalizedStringLoader.loadFromClasspathResources(unopenedClassLoader, resourcePathByLocale));

    assertEquals("und", Locale.ROOT.toLanguageTag());
    assertEquals("und", malformedUndeterminedLocale.toLanguageTag());
    assertTrue(exception.getMessage().contains("Locale key 'x'"));
  }

  @Test
  public void testExplicitClasspathResourceMappingRejectsDuplicateRenderedLanguageTags() {
    ClassLoader unopenedClassLoader = new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String resourcePath) {
        throw new AssertionError("Duplicate locale mappings must be rejected before resources are opened");
      }
    };
    Locale explicitUndeterminedLocale = new Locale("und");
    Map<Locale, String> resourcePathByLocale = Map.of(
        Locale.ROOT, "localized-strings/root.json",
        explicitUndeterminedLocale, "localized-strings/und.json");

    LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromClasspathResources(unopenedClassLoader, resourcePathByLocale));

    assertNotEquals(Locale.ROOT, explicitUndeterminedLocale);
    assertEquals("und", Locale.ROOT.toLanguageTag());
    assertEquals("und", explicitUndeterminedLocale.toLanguageTag());
    assertTrue(exception.getMessage().contains("Duplicate localized strings resource mapping for locale 'und'"));
    assertTrue(exception.getMessage().contains("localized-strings/root.json"));
    assertTrue(exception.getMessage().contains("localized-strings/und.json"));
  }

  @Test
  public void testExplicitClasspathResourceMappingPreservesLocaleKeysAndDistinctAliases() {
    ClassLoader classLoader = new ClassLoader(null) {
      @Override
      public InputStream getResourceAsStream(String resourcePath) {
        return new ByteArrayInputStream(("{\"" + resourcePath + "\":\"value\"}")
            .getBytes(StandardCharsets.UTF_8));
      }
    };
    Locale extendedLocale = new Locale.Builder()
        .setLanguage("en")
        .setScript("Latn")
        .setRegion("US")
        .setExtension('x', "lokalize")
        .build();

    Map<Locale, Set<LocalizedString>> extendedResult = LocalizedStringLoader.loadFromClasspathResources(classLoader,
        Map.of(extendedLocale, "localized-strings/extended.json"));
    Map<Locale, Set<LocalizedString>> aliasResult = LocalizedStringLoader.loadFromClasspathResources(classLoader,
        Map.of(Locale.forLanguageTag("mo"), "localized-strings/mo.json",
            Locale.forLanguageTag("ro"), "localized-strings/ro.json"));

    assertSame(extendedLocale, extendedResult.keySet().iterator().next());
    assertEquals(Set.of(Locale.forLanguageTag("mo"), Locale.forLanguageTag("ro")), aliasResult.keySet());
  }

  @Test
  public void testExplodedClasspathFiltersNamesBeforeResolvingRealPaths() throws IOException {
    Path classpathRoot = Files.createTempDirectory("lokalized-exploded-classpath");
    classpathRoot.toFile().deleteOnExit();
    Path stringsDirectory = Files.createDirectories(classpathRoot.resolve("strings"));
    Files.write(stringsDirectory.resolve("en.json"), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
    Files.createSymbolicLink(stringsDirectory.resolve("notes.txt"), stringsDirectory.resolve("missing-notes"));
    Files.createSymbolicLink(stringsDirectory.resolve("template.json"), stringsDirectory.resolve("missing-template"));
    List<LocalizedStringWarning> warnings = new ArrayList<>();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspath(
          classLoader, "strings", warnings::add, LocalizedStringLoadingOptions.defaults());

      assertEquals(Set.of(Locale.ENGLISH), localizedStringsByLocale.keySet());
      assertEquals(1, warnings.size());
      assertEquals(LocalizedStringWarning.Type.INVALID_CLASSPATH_LOCALE_FILENAME, warnings.get(0).getType());
      assertTrue(warnings.get(0).getSource().contains("template.json"));
    }
  }

  @Test
  public void testExplodedClasspathFailsSafelyForDanglingLocalizedStringsSymlink() throws IOException {
    Path classpathRoot = Files.createTempDirectory("lokalized-dangling-classpath-resource");
    classpathRoot.toFile().deleteOnExit();
    Path stringsDirectory = Files.createDirectories(classpathRoot.resolve("strings"));
    Path localizedStringsFile = stringsDirectory.resolve("en.json");
    Files.createSymbolicLink(localizedStringsFile, stringsDirectory.resolve("missing-en.json"));

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, null)) {
      LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings"));

      assertTrue(exception.getMessage().contains("Unable to determine canonical path"));
      assertTrue(exception.getMessage().contains(localizedStringsFile.toString()));
      assertNotNull(exception.getCause());
    }
  }

  @Test
  public void testClasspathLoadingFromJarWithoutDirectoryEntryRequiresExhaustiveSearch() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-no-directory-entry", ".jar");
    tempJar.toFile().deleteOnExit();
    writeJarEntryWithoutDirectory(tempJar, "strings/en.json", "{\"hello\":\"world\"}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings"));

      LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
          .exhaustiveClasspathSearch(true)
          .build();
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions);

      assertTrue(localizedStringsByLocale.containsKey(Locale.ENGLISH));
      assertEquals(1, localizedStringsByLocale.get(Locale.ENGLISH).size());
    }
  }

  @Test
  public void testOrdinaryJarDiscoveryBudgetCountsPhysicalEntriesAndPackageLocation() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-discovery-budget", ".jar");
    tempJar.toFile().deleteOnExit();

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(tempJar))) {
      writeJarEntry(jarOutputStream, "strings/", null);
      writeJarEntry(jarOutputStream, "strings/en.json", "{\"hello\":\"world\"}");
      writeJarEntry(jarOutputStream, "foreign/data.txt", "foreign");
    }

    LocalizedStringLoadingOptions exactLimit = LocalizedStringLoadingOptions.builder()
        .maximumDiscoveryEntries(4)
        .build();
    LocalizedStringLoadingOptions oneBelowLimit = LocalizedStringLoadingOptions.builder()
        .maximumDiscoveryEntries(3)
        .build();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      assertEquals(Set.of(Locale.ENGLISH),
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", exactLimit).keySet());
      assertTrue(assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings", oneBelowLimit)).getMessage()
          .contains("aggregate maximum of 3 discovery entries"));
    }
  }

  @Test
  public void testExhaustiveDiscoveryBudgetIncludesRootsManifestsAndJarEntries() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-exhaustive-discovery-budget");
    tempDirectory.toFile().deleteOnExit();
    Path localizedStringsJar = tempDirectory.resolve("localized-strings.jar");
    Path applicationJar = tempDirectory.resolve("application.jar");
    writeJarEntryWithoutDirectory(localizedStringsJar, "strings/en.json", "{\"hello\":\"world\"}");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, localizedStringsJar.getFileName().toString());

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(applicationJar), manifest)) {
      jarOutputStream.flush(); // Manifest-only application JAR.
    }

    LocalizedStringLoadingOptions exactLimit = LocalizedStringLoadingOptions.builder()
        .exhaustiveClasspathSearch(true)
        .maximumDiscoveryEntries(4)
        .build();
    LocalizedStringLoadingOptions oneBelowLimit = exactLimit.toBuilder()
        .maximumDiscoveryEntries(3)
        .build();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{applicationJar.toUri().toURL()}, null)) {
      assertEquals(Set.of(Locale.ENGLISH),
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", exactLimit).keySet());
      assertTrue(assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings", oneBelowLimit)).getMessage()
          .contains("aggregate maximum of 3 discovery entries"));
    }
  }

	@Test
	public void testManifestDiscoveryBudgetStopsBeforeConvertingAnOverBudgetToken() throws IOException {
		Path applicationJar = Files.createTempFile("lokalized-manifest-discovery-budget", ".jar");
		applicationJar.toFile().deleteOnExit();
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, "https://example.invalid/first.jar %");

		try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(applicationJar), manifest)) {
			jarOutputStream.flush();
		}

		LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
				.exhaustiveClasspathSearch(true)
				.maximumDiscoveryEntries(2)
				.build();

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{applicationJar.toUri().toURL()}, null) {
			@Override
			public Enumeration<URL> getResources(String name) {
				return Collections.emptyEnumeration();
			}
		}) {
			LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
					() -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions));
			assertTrue(exception.getMessage().contains("manifest Class-Path"));
			assertTrue(exception.getMessage().contains("aggregate maximum of 2 discovery entries"));
		}
	}

  @Test
  public void testClasspathLoadingIgnoresForeignJsonDuringExhaustiveSearch() throws IOException {
    Path applicationJar = Files.createTempFile("lokalized-application-strings", ".jar");
    Path foreignJar = Files.createTempFile("lokalized-foreign-strings", ".jar");
    applicationJar.toFile().deleteOnExit();
    foreignJar.toFile().deleteOnExit();

    writeJarEntry(applicationJar, "strings/en.json", "{\"hello\":\"world\"}");
    writeJarEntryWithoutDirectory(foreignJar, "strings/template.json", "{\"foreign\":true}");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{
        applicationJar.toUri().toURL(),
        foreignJar.toUri().toURL()
    }, null)) {
      LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
          .exhaustiveClasspathSearch(true)
          .build();
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions);

      assertEquals(Set.of(Locale.ENGLISH), localizedStringsByLocale.keySet());
      assertEquals(1, localizedStringsByLocale.get(Locale.ENGLISH).size());
    }
  }

  @Test
  public void testForeignOnlyClasspathPackageReturnsEmptyForOrdinaryAndExhaustiveDiscovery() throws IOException {
    for (boolean exhaustiveClasspathSearch : List.of(false, true)) {
      Path tempJar = Files.createTempFile("lokalized-foreign-only-strings", ".jar");
      tempJar.toFile().deleteOnExit();

      if (exhaustiveClasspathSearch)
        writeJarEntryWithoutDirectory(tempJar, "strings/template.json", "{\"foreign\":true}");
      else
        writeJarEntry(tempJar, "strings/template.json", "{\"foreign\":true}");

      try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
        List<LocalizedStringWarning> warnings = new ArrayList<>();
        LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
            .exhaustiveClasspathSearch(exhaustiveClasspathSearch)
            .build();
        Map<Locale, Set<LocalizedString>> localizedStringsByLocale = LocalizedStringLoader.loadFromClasspath(
            classLoader, "strings", warnings::add, loadingOptions);

        assertTrue(localizedStringsByLocale.isEmpty());
        assertEquals(1, warnings.size());
        assertEquals(LocalizedStringWarning.Type.INVALID_CLASSPATH_LOCALE_FILENAME,
            warnings.get(0).getType());
      }
    }
  }

  @Test
  public void testClasspathLoadingIgnoresInvalidJsonNameFromOrdinaryDiscovery() throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-invalid-name", ".jar");
    tempJar.toFile().deleteOnExit();

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(tempJar))) {
      jarOutputStream.putNextEntry(new JarEntry("strings/"));
      jarOutputStream.closeEntry();

      jarOutputStream.putNextEntry(new JarEntry("strings/en.json"));
      jarOutputStream.write("{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
      jarOutputStream.closeEntry();

      jarOutputStream.putNextEntry(new JarEntry("strings/template.json"));
      jarOutputStream.write("{\"foreign\":true}".getBytes(StandardCharsets.UTF_8));
      jarOutputStream.closeEntry();
    }

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      List<LocalizedStringWarning> warnings = new ArrayList<>();
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", warnings::add,
              LocalizedStringLoadingOptions.defaults());

      assertEquals(Set.of(Locale.ENGLISH), localizedStringsByLocale.keySet());
      assertEquals(1, localizedStringsByLocale.get(Locale.ENGLISH).size());
      assertEquals(1, warnings.size());
      assertEquals(LocalizedStringWarning.Type.INVALID_CLASSPATH_LOCALE_FILENAME, warnings.get(0).getType());
      assertTrue(warnings.get(0).getSource().contains("template.json"));
      assertFalse(warnings.get(0).getLocale().isPresent());
      assertFalse(warnings.get(0).getKey().isPresent());
      assertFalse(warnings.get(0).getPlaceholder().isPresent());
      assertTrue(warnings.get(0).getMissingLanguageForms().isEmpty());

      assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings",
              LocalizedStringWarningHandler.throwException(), LocalizedStringLoadingOptions.defaults()));
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
  public void testClasspathLoadingSharesAggregateLimitsAcrossDiscoveredLocations() throws IOException {
    Path tempJar1 = Files.createTempFile("lokalized-aggregate-one", ".jar");
    Path tempJar2 = Files.createTempFile("lokalized-aggregate-two", ".jar");
    tempJar1.toFile().deleteOnExit();
    tempJar2.toFile().deleteOnExit();
    writeJarEntry(tempJar1, "strings/en.json", "{\"hello\":\"world\"}");
    writeJarEntry(tempJar2, "strings/fr.json", "{\"bonjour\":\"monde\"}");
    LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
        .maximumLocalizedStringsFiles(1)
        .build();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{
        tempJar1.toUri().toURL(),
        tempJar2.toUri().toURL()
    }, null)) {
      assertTrue(assertThrows(LocalizedStringLoadingException.class,
          () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions)).getMessage()
          .contains("aggregate localized strings file limit of 1"));
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

  @NonNull
  private static ClassLoader classLoaderForClasspathUrl(@NonNull URL classpathUrl) {
    requireNonNull(classpathUrl);

    return new ClassLoader(null) {
      @Override
      public Enumeration<URL> getResources(String name) {
        return Collections.enumeration(List.of(classpathUrl));
      }
    };
  }

  @NonNull
  private static URLConnection inertUrlConnection(@NonNull URL url) {
    requireNonNull(url);

    return new URLConnection(url) {
      @Override
      public void connect() {
        // No connection is needed: the loader must reject this type before using it as a JAR connection.
      }
    };
  }

  private void verifyDuplicatePhysicalJarEntriesRejected(boolean multiRelease) throws IOException {
    for (boolean exhaustiveClasspathSearch : List.of(false, true)) {
      Path tempJar = Files.createTempFile("lokalized-duplicate-physical-entries", ".jar");
      tempJar.toFile().deleteOnExit();
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

      if (multiRelease)
        manifest.getMainAttributes().putValue("Multi-Release", "true");

      String firstEntryName = multiRelease
          ? "META-INF/versions/9/strings/en.json"
          : "strings/en.json";
      String secondEntryName = multiRelease
          ? "META-INF/versions/9/strings/fr.json"
          : "strings/fr.json";

      try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
        if (!exhaustiveClasspathSearch)
          writeJarEntry(jarOutputStream, "strings/", null);

        if (multiRelease)
          writeJarEntry(jarOutputStream, "strings/en.json", "{\"message\":\"base\"}");

        writeJarEntry(jarOutputStream, firstEntryName, "{\"message\":\"first\"}");

        // A higher selected entry between the duplicate version-9 entries verifies that duplicate accounting is
        // independent from highest-version selection. Version 10 is simply ignored when tests run on Java 9.
        if (multiRelease)
          writeJarEntry(jarOutputStream, "META-INF/versions/10/strings/en.json",
              "{\"message\":\"higher\"}");

        writeJarEntry(jarOutputStream, secondEntryName, "{\"message\":\"second\"}");
      }

      replaceJarEntryName(tempJar, secondEntryName, firstEntryName);
      LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
          .exhaustiveClasspathSearch(exhaustiveClasspathSearch)
          .build();

      try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
        LocalizedStringLoadingException exception = assertThrows(LocalizedStringLoadingException.class,
            () -> LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions));

        assertTrue(exception.getMessage().contains("Duplicate physical JAR entry"));
        assertTrue(exception.getMessage().contains(firstEntryName));
        assertTrue(exception.getMessage().contains(multiRelease ? "version 9" : "the base version"));
      }
    }
  }

  private void replaceJarEntryName(Path jarPath, String originalEntryName, String replacementEntryName)
      throws IOException {
    assertEquals(originalEntryName.length(), replacementEntryName.length());
    byte[] jarBytes = Files.readAllBytes(jarPath);
    byte[] originalBytes = originalEntryName.getBytes(StandardCharsets.UTF_8);
    byte[] replacementBytes = replacementEntryName.getBytes(StandardCharsets.UTF_8);
    int replacements = 0;

    for (int offset = 0; offset <= jarBytes.length - originalBytes.length; ++offset) {
      boolean matches = true;

      for (int i = 0; i < originalBytes.length; ++i)
        if (jarBytes[offset + i] != originalBytes[i]) {
          matches = false;
          break;
        }

      if (!matches)
        continue;

      System.arraycopy(replacementBytes, 0, jarBytes, offset, replacementBytes.length);
      ++replacements;
      offset += originalBytes.length - 1;
    }

    assertEquals(2, replacements, "Expected to rewrite local and central ZIP entry names");
    Files.write(jarPath, jarBytes);
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

  private void verifyMalformedMultiReleaseVersionIgnored(String malformedVersion, boolean includeDirectoryEntry,
                                                         boolean exhaustiveClasspathSearch) throws IOException {
    Path tempJar = Files.createTempFile("lokalized-strings-malformed-multi-release", ".jar");
    tempJar.toFile().deleteOnExit();
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Multi-Release", "true");

    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
      if (includeDirectoryEntry)
        writeJarEntry(jarOutputStream, "strings/", null);

      writeJarEntry(jarOutputStream, "strings/en.json", "{\"message\":\"base\"}");
      writeJarEntry(jarOutputStream, format("META-INF/versions/%s/strings/en.json", malformedVersion),
          "{\"message\":\"malformed-version\"}");
    }

    LocalizedStringLoadingOptions loadingOptions = LocalizedStringLoadingOptions.builder()
        .exhaustiveClasspathSearch(exhaustiveClasspathSearch)
        .build();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempJar.toUri().toURL()}, null)) {
      Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
          LocalizedStringLoader.loadFromClasspath(classLoader, "strings", loadingOptions);
      LocalizedString localizedString = localizedStringsByLocale.get(Locale.ENGLISH).iterator().next();

      assertEquals("base", localizedString.getTranslation().orElse(null),
          format("Malformed version directory '%s' must not overlay the base resource", malformedVersion));
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

  private void writeJarEntry(JarOutputStream jarOutputStream, String entryName, String contents) throws IOException {
    JarEntry entry = new JarEntry(entryName);
    jarOutputStream.putNextEntry(entry);

    if (contents != null)
      jarOutputStream.write(contents.getBytes(StandardCharsets.UTF_8));

    jarOutputStream.closeEntry();
  }

  protected void verifyLocalizedStringsByLocale(@NonNull Map<Locale, Set<LocalizedString>> localizedStringsByLocale) {
    requireNonNull(localizedStringsByLocale);

    assertEquals(4, localizedStringsByLocale.size(), "Unexpected number of localized strings files");

    for (Entry<Locale, Set<LocalizedString>> entry : localizedStringsByLocale.entrySet())
      assertTrue(entry.getValue().size() > 0, format("The '%s' localized strings file has no data", entry.getKey().toLanguageTag()));
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
