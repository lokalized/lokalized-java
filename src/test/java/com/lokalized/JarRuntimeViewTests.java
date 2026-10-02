package com.lokalized;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JVM properties are cached by JarFile, so exercise actual loading in fresh VMs. */
public class JarRuntimeViewTests {
  @Test
  public void preservesJvmRuntimeVersionAndMultiReleaseOverrides() throws Exception {
    Path jar = Files.createTempFile("lokalized-runtime-view", ".jar");
    try {
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().putValue("Multi-Release", "true");
      try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
        entry(output, "strings/", null);
        entry(output, "strings/en.json", "base");
        entry(output, "META-INF/versions/9/strings/en.json", "version9");
        entry(output, "META-INF/versions/17/strings/en.json", "version17");
      }
      verify(jar, "-Djdk.util.jar.version=9", "version9");
      verify(jar, "-Djdk.util.jar.enableMultiRelease=false", "base");
    } finally {
      Files.deleteIfExists(jar);
    }
  }

  private static void entry(JarOutputStream output, String name, String translation) throws IOException {
    output.putNextEntry(new JarEntry(name));
    if (translation != null)
      output.write(("{\"message\":\"" + translation + "\"}").getBytes(StandardCharsets.UTF_8));
    output.closeEntry();
  }

  private static void verify(Path jar, String property, String expected) throws Exception {
    Process process = new ProcessBuilder(
        Paths.get(System.getProperty("java.home"), "bin", "java").toString(), property,
        "-cp", System.getProperty("java.class.path"), Probe.class.getName(), jar.toString(), expected)
        .redirectErrorStream(true).start();
    try {
      assertTrue(process.waitFor(30, TimeUnit.SECONDS), "JAR loading probe timed out");
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), output);
    } finally {
      process.destroyForcibly();
    }
  }

  public static class Probe {
    public static void main(String[] arguments) throws Exception {
      try (URLClassLoader loader = new URLClassLoader(new URL[] { Paths.get(arguments[0]).toUri().toURL() }, null)) {
        String actual = LocalizedStringLoader.loadFromClasspath(loader, "strings")
            .get(Locale.ENGLISH).iterator().next().getTranslation().orElse("");
        if (!arguments[1].equals(actual))
          throw new AssertionError("Expected " + arguments[1] + "; got " + actual);
      }
    }
  }
}
