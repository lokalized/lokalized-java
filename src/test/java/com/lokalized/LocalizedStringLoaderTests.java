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
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
  public void testFilesystemLoadingRejectsInvalidJson() throws IOException {
    Path tempDirectory = Files.createTempDirectory("lokalized-strings");
    tempDirectory.toFile().deleteOnExit();

    Files.write(tempDirectory.resolve("en"), "{".getBytes(StandardCharsets.UTF_8));

    assertThrows(LocalizedStringLoadingException.class,
        () -> LocalizedStringLoader.loadFromFilesystem(tempDirectory),
        "Expected invalid JSON to fail fast during load");
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

  private void writeJarEntry(Path jarPath, String entryName, String json) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
      JarEntry directoryEntry = new JarEntry("strings/");
      jarOutputStream.putNextEntry(directoryEntry);
      jarOutputStream.closeEntry();

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
}
