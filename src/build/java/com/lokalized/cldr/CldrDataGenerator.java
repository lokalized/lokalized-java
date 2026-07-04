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

package com.lokalized.cldr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.lang.String.format;

/**
 * Generates checked-in CLDR runtime data and conformance fixtures from pinned CLDR XML.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public final class CldrDataGenerator {
  private static final String CLDR_VERSION = "48.2";
  private static final String SOURCE_URL = "https://github.com/unicode-org/cldr/tree/release-48-2/common/supplemental";
  private static final String PLURALS_RESOURCE = "common/supplemental/plurals.xml";
  private static final String ORDINALS_RESOURCE = "common/supplemental/ordinals.xml";
  private static final String PLURAL_RANGES_RESOURCE = "common/supplemental/pluralRanges.xml";
  private static final String PLURALS_SHA_256 = "d701d8b461afd2ba5eb21f9c1d645c8a661de225596afcf0f492ee36c69d727d";
  private static final String ORDINALS_SHA_256 = "129bf4aa6f41d47931bf908ebaa261ec0b73b768f1fe3be90c06ab11fb49880a";
  private static final String PLURAL_RANGES_SHA_256 = "42c82db9baaa8667921b4dea32b6322fd5e43004e166a43ae33b51cef5dc0f52";
  private static final int GENERATED_DATA_CHUNK_SIZE = 24;
  private static final SampleRequest[] CARDINALITY_SAMPLE_REQUESTS = new SampleRequest[] {
      sample("he", "0.5"),
      sample("he", "100"),
      sample("is", "0.1"),
      sample("is", "0.2"),
      sample("mk", "11"),
      sample("mk", "21"),
      sample("mt", "2"),
      sample("mt", "20"),
      sample("fr", "1000000"),
      sample("es", "1000000"),
      sample("pt", "1000000"),
      sample("ca", "1000000"),
      sample("it", "1000000"),
      sample("pt_PT", "0"),
      sample("pt_PT", "1"),
      sample("pt_PT", "1000000")
  };
  private static final SampleRequest[] ORDINALITY_SAMPLE_REQUESTS = new SampleRequest[] {
      sample("en", "1"),
      sample("en", "2"),
      sample("en", "3"),
      sample("en", "4"),
      sample("en", "11"),
      sample("en", "12"),
      sample("en", "13"),
      sample("en", "21"),
      sample("it", "8"),
      sample("it", "11"),
      sample("it", "80"),
      sample("it", "800"),
      sample("it", "1"),
      sample("cy", "0"),
      sample("cy", "1"),
      sample("cy", "2"),
      sample("cy", "3"),
      sample("cy", "5"),
      sample("cy", "10"),
      sample("as", "1"),
      sample("as", "2"),
      sample("as", "4"),
      sample("as", "6"),
      sample("as", "11"),
      sample("gu", "1"),
      sample("gu", "2"),
      sample("gu", "4"),
      sample("gu", "6"),
      sample("gu", "5"),
      sample("mk", "1"),
      sample("mk", "2"),
      sample("mk", "7"),
      sample("mk", "8"),
      sample("mk", "11"),
      sample("mk", "12"),
      sample("mk", "17"),
      sample("mk", "18"),
      sample("mk", "21"),
      sample("sv", "1"),
      sample("sv", "2"),
      sample("sv", "11"),
      sample("sv", "12"),
      sample("sv", "21"),
      sample("sv", "22"),
      sample("be", "2"),
      sample("be", "3"),
      sample("be", "12"),
      sample("be", "13"),
      sample("be", "22"),
      sample("az", "0"),
      sample("az", "1"),
      sample("az", "3"),
      sample("az", "6"),
      sample("az", "9"),
      sample("tk", "6"),
      sample("tk", "10"),
      sample("gd", "11"),
      sample("or", "6"),
      sample("kw", "5")
  };
  private static final RangeRequest[] CARDINALITY_RANGE_SAMPLE_REQUESTS = new RangeRequest[] {
      range("fa", "other", "one"),
      range("ak", "other", "one"),
      range("or", "other", "one"),
      range("as", "one", "one"),
      range("ps", "one", "one"),
      range("tk", "other", "one")
  };

  private CldrDataGenerator() {
    // Non-instantiable
  }

  /**
   * Runs the generator.
   *
   * @param args optional project root path; defaults to the current working directory
   */
  public static void main(String[] args) {
    try {
      Path projectRoot = args.length == 0 ? Paths.get("").toAbsolutePath() : Paths.get(args[0]).toAbsolutePath();
      new Generator(projectRoot).generate();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to generate CLDR data", e);
    }
  }

  private static SampleRequest sample(String cldrLocale, String sample) {
    return new SampleRequest(cldrLocale, sample);
  }

  private static RangeRequest range(String cldrLocale, String start, String end) {
    return new RangeRequest(cldrLocale, start, end);
  }

  private static final class Generator {
    private final Path cldrRoot;
    private final Path runtimeOutputPath;
    private final Path conformanceOutputPath;

    private Generator(Path projectRoot) {
      this.cldrRoot = projectRoot.resolve("src/test/resources/cldr").resolve(CLDR_VERSION);
      this.runtimeOutputPath = projectRoot.resolve("src/main/java/com/lokalized/GeneratedCldrPluralData.java");
      this.conformanceOutputPath = projectRoot.resolve("src/test/java/com/lokalized/GeneratedCldrConformanceData.java");
    }

    private void generate() throws Exception {
      verifySha256(PLURALS_RESOURCE, PLURALS_SHA_256);
      verifySha256(ORDINALS_RESOURCE, ORDINALS_SHA_256);
      verifySha256(PLURAL_RANGES_RESOURCE, PLURAL_RANGES_SHA_256);

      List<LocaleRules> cardinalRules = localeRulesFor(PLURALS_RESOURCE);
      List<LocaleRules> ordinalRules = localeRulesFor(ORDINALS_RESOURCE);
      List<LocaleRanges> cardinalRanges = localeRangesFor();
      List<CardinalitySample> cardinalitySamples = cardinalitySamples();
      List<OrdinalitySample> ordinalitySamples = ordinalitySamples();
      List<CardinalityRangeSample> cardinalityRangeSamples = cardinalityRangeSamples();

      Files.createDirectories(runtimeOutputPath.getParent());
      Files.write(runtimeOutputPath, runtimeSourceFor(cardinalRules, ordinalRules, cardinalRanges).getBytes(StandardCharsets.UTF_8));

      Files.createDirectories(conformanceOutputPath.getParent());
      Files.write(conformanceOutputPath, conformanceSourceFor(cardinalitySamples, ordinalitySamples, cardinalityRangeSamples).getBytes(StandardCharsets.UTF_8));
    }

    private List<LocaleRules> localeRulesFor(String resourceName) {
      Map<String, LocaleRules> rulesByLocale = new LinkedHashMap<>();
      Document document = documentFor(resourceName);
      NodeList pluralRules = document.getElementsByTagName("pluralRules");

      for (int i = 0; i < pluralRules.getLength(); ++i) {
        Element pluralRuleGroup = (Element) pluralRules.item(i);
        List<Rule> rules = rulesFor(pluralRuleGroup);

        for (String rawLocale : pluralRuleGroup.getAttribute("locales").split("\\s+")) {
          String locale = canonicalLocale(rawLocale);

          if (locale.length() > 0)
            rulesByLocale.put(locale, new LocaleRules(locale, rules));
        }
      }

      List<LocaleRules> localeRules = new ArrayList<>(rulesByLocale.values());
      localeRules.sort(Comparator.comparing(LocaleRules::getLocale));
      return Collections.unmodifiableList(localeRules);
    }

    private List<Rule> rulesFor(Element pluralRuleGroup) {
      List<Rule> rules = new ArrayList<>();
      NodeList pluralRuleElements = pluralRuleGroup.getElementsByTagName("pluralRule");

      for (int i = 0; i < pluralRuleElements.getLength(); ++i) {
        Element pluralRule = (Element) pluralRuleElements.item(i);
        String count = pluralRule.getAttribute("count");
        String ruleText = pluralRule.getTextContent();
        String condition = conditionFor(ruleText);
        SampleList integerSamples = samplesFor(ruleText, "@integer");
        SampleList decimalSamples = samplesFor(ruleText, "@decimal");

        rules.add(new Rule(count, condition, integerSamples, decimalSamples));
      }

      return Collections.unmodifiableList(rules);
    }

    private List<LocaleRanges> localeRangesFor() {
      Map<String, LocaleRanges> rangesByLocale = new LinkedHashMap<>();
      Document document = documentFor(PLURAL_RANGES_RESOURCE);
      NodeList pluralRanges = document.getElementsByTagName("pluralRanges");

      for (int i = 0; i < pluralRanges.getLength(); ++i) {
        Element pluralRangeGroup = (Element) pluralRanges.item(i);
        List<RangeRule> rangeRules = rangeRulesFor(pluralRangeGroup);

        for (String rawLocale : pluralRangeGroup.getAttribute("locales").split("\\s+")) {
          String locale = canonicalLocale(rawLocale);

          if (locale.length() > 0)
            rangesByLocale.put(locale, new LocaleRanges(locale, rangeRules));
        }
      }

      List<LocaleRanges> localeRanges = new ArrayList<>(rangesByLocale.values());
      localeRanges.sort(Comparator.comparing(LocaleRanges::getLocale));
      return Collections.unmodifiableList(localeRanges);
    }

    private List<RangeRule> rangeRulesFor(Element pluralRangeGroup) {
      List<RangeRule> rangeRules = new ArrayList<>();
      NodeList pluralRangeElements = pluralRangeGroup.getElementsByTagName("pluralRange");

      for (int i = 0; i < pluralRangeElements.getLength(); ++i) {
        Element pluralRange = (Element) pluralRangeElements.item(i);
        rangeRules.add(new RangeRule(pluralRange.getAttribute("start"),
            pluralRange.getAttribute("end"), pluralRange.getAttribute("result")));
      }

      return Collections.unmodifiableList(rangeRules);
    }

    private List<CardinalitySample> cardinalitySamples() {
      List<CardinalitySample> samples = new ArrayList<>();

      for (SampleRequest request : CARDINALITY_SAMPLE_REQUESTS)
        samples.add(new CardinalitySample(canonicalLocale(request.getCldrLocale()), request.getSample(),
            countForSample(PLURALS_RESOURCE, request.getCldrLocale(), new BigDecimal(request.getSample()))));

      return Collections.unmodifiableList(samples);
    }

    private List<OrdinalitySample> ordinalitySamples() {
      List<OrdinalitySample> samples = new ArrayList<>();

      for (SampleRequest request : ORDINALITY_SAMPLE_REQUESTS)
        samples.add(new OrdinalitySample(canonicalLocale(request.getCldrLocale()), request.getSample(),
            countForSample(ORDINALS_RESOURCE, request.getCldrLocale(), new BigDecimal(request.getSample()))));

      return Collections.unmodifiableList(samples);
    }

    private List<CardinalityRangeSample> cardinalityRangeSamples() {
      List<CardinalityRangeSample> samples = new ArrayList<>();

      for (RangeRequest request : CARDINALITY_RANGE_SAMPLE_REQUESTS)
        samples.add(new CardinalityRangeSample(canonicalLocale(request.getCldrLocale()), request.getStart(), request.getEnd(),
            countForRange(request.getCldrLocale(), request.getStart(), request.getEnd())));

      return Collections.unmodifiableList(samples);
    }

    private String runtimeSourceFor(List<LocaleRules> cardinalRules,
                                    List<LocaleRules> ordinalRules,
                                    List<LocaleRanges> cardinalRanges) {
      StringBuilder source = new StringBuilder();
      appendCopyrightAndPackage(source);
      source.append("import com.lokalized.CldrPluralRules.LocaleRanges;\n")
          .append("import com.lokalized.CldrPluralRules.LocaleRules;\n")
          .append("import com.lokalized.CldrPluralRules.RangeRule;\n")
          .append("import com.lokalized.CldrPluralRules.Rule;\n")
          .append("\n")
          .append("// Generated by src/build/java/com/lokalized/cldr/CldrDataGenerator.java from CLDR ")
          .append(CLDR_VERSION).append(". Do not edit by hand.\n")
          .append("final class GeneratedCldrPluralData {\n")
          .append("  @NonNull\n")
          .append("  static final String CLDR_VERSION = \"").append(CLDR_VERSION).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String SOURCE_URL = \"").append(SOURCE_URL).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURALS_RESOURCE = \"").append(PLURALS_RESOURCE).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String ORDINALS_RESOURCE = \"").append(ORDINALS_RESOURCE).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURAL_RANGES_RESOURCE = \"").append(PLURAL_RANGES_RESOURCE).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURALS_SHA_256 = \"").append(PLURALS_SHA_256).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String ORDINALS_SHA_256 = \"").append(ORDINALS_SHA_256).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURAL_RANGES_SHA_256 = \"").append(PLURAL_RANGES_SHA_256).append("\";\n");
      appendLocaleRulesField(source, "CARDINAL_RULES", "cardinalRules", cardinalRules);
      appendLocaleRulesField(source, "ORDINAL_RULES", "ordinalRules", ordinalRules);
      appendLocaleRangesField(source, "CARDINAL_RANGES", "cardinalRanges", cardinalRanges);
      source.append("\n")
          .append("  private GeneratedCldrPluralData() {\n")
          .append("    // Non-instantiable\n")
          .append("  }\n")
          .append("\n");
      appendRuntimeConcatenationMethods(source);
      appendLocaleRulesMethods(source, "cardinalRules", cardinalRules);
      appendLocaleRulesMethods(source, "ordinalRules", ordinalRules);
      appendLocaleRangesMethods(source, "cardinalRanges", cardinalRanges);
      source.append("}\n");

      return source.toString();
    }

    private String conformanceSourceFor(List<CardinalitySample> cardinalitySamples,
                                        List<OrdinalitySample> ordinalitySamples,
                                        List<CardinalityRangeSample> cardinalityRangeSamples) {
      StringBuilder source = new StringBuilder();
      appendCopyrightAndPackage(source);
      source.append("import java.util.Arrays;\n")
          .append("import java.util.Collections;\n")
          .append("import java.util.List;\n")
          .append("\n")
          .append("import static java.util.Objects.requireNonNull;\n")
          .append("\n")
          .append("// Generated by src/build/java/com/lokalized/cldr/CldrDataGenerator.java from CLDR ")
          .append(CLDR_VERSION).append(". Do not edit by hand.\n")
          .append("final class GeneratedCldrConformanceData {\n")
          .append("  @NonNull\n")
          .append("  static final String CLDR_VERSION = \"").append(CLDR_VERSION).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String SOURCE_URL = \"").append(SOURCE_URL).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURALS_RESOURCE = \"").append(PLURALS_RESOURCE).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String ORDINALS_RESOURCE = \"").append(ORDINALS_RESOURCE).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURAL_RANGES_RESOURCE = \"").append(PLURAL_RANGES_RESOURCE).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURALS_SHA_256 = \"").append(PLURALS_SHA_256).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String ORDINALS_SHA_256 = \"").append(ORDINALS_SHA_256).append("\";\n")
          .append("  @NonNull\n")
          .append("  static final String PLURAL_RANGES_SHA_256 = \"").append(PLURAL_RANGES_SHA_256).append("\";\n")
          .append("  @NonNull\n")
          .append("  private static final List<@NonNull CardinalitySample> CARDINALITY_SAMPLES = Collections.unmodifiableList(Arrays.asList(\n");

      appendCardinalitySamples(source, cardinalitySamples);
      source.append("  ));\n")
          .append("  @NonNull\n")
          .append("  private static final List<@NonNull OrdinalitySample> ORDINALITY_SAMPLES = Collections.unmodifiableList(Arrays.asList(\n");
      appendOrdinalitySamples(source, ordinalitySamples);
      source.append("  ));\n")
          .append("  @NonNull\n")
          .append("  private static final List<@NonNull CardinalityRangeSample> CARDINALITY_RANGE_SAMPLES = Collections.unmodifiableList(Arrays.asList(\n");
      appendCardinalityRangeSamples(source, cardinalityRangeSamples);
      source.append("  ));\n")
          .append("\n")
          .append("  private GeneratedCldrConformanceData() {\n")
          .append("    // Non-instantiable\n")
          .append("  }\n")
          .append("\n")
          .append("  @NonNull\n")
          .append("  static List<@NonNull CardinalitySample> cardinalitySamples() {\n")
          .append("    return CARDINALITY_SAMPLES;\n")
          .append("  }\n")
          .append("\n")
          .append("  @NonNull\n")
          .append("  static List<@NonNull OrdinalitySample> ordinalitySamples() {\n")
          .append("    return ORDINALITY_SAMPLES;\n")
          .append("  }\n")
          .append("\n")
          .append("  @NonNull\n")
          .append("  static List<@NonNull CardinalityRangeSample> cardinalityRangeSamples() {\n")
          .append("    return CARDINALITY_RANGE_SAMPLES;\n")
          .append("  }\n");
      appendSampleClasses(source);
      source.append("}\n");

      return source.toString();
    }

    private void appendCopyrightAndPackage(StringBuilder source) {
      source.append("/*\n")
          .append(" * Copyright 2017-2022 Product Mog LLC, 2022-2026 Revetware LLC.\n")
          .append(" *\n")
          .append(" * Licensed under the Apache License, Version 2.0 (the \"License\");\n")
          .append(" * you may not use this file except in compliance with the License.\n")
          .append(" * You may obtain a copy of the License at\n")
          .append(" *\n")
          .append(" * http://www.apache.org/licenses/LICENSE-2.0\n")
          .append(" *\n")
          .append(" * Unless required by applicable law or agreed to in writing, software\n")
          .append(" * distributed under the License is distributed on an \"AS IS\" BASIS,\n")
          .append(" * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n")
          .append(" * See the License for the specific language governing permissions and\n")
          .append(" * limitations under the License.\n")
          .append(" *\n")
          .append(" * This generated source contains data derived from Unicode CLDR ")
          .append(CLDR_VERSION).append(". CLDR data files are\n")
          .append(" * Copyright © 1991-2026 Unicode, Inc. and licensed under Unicode License v3.\n")
          .append(" * See THIRD-PARTY-NOTICES.md.\n")
          .append(" */\n")
          .append("\n")
          .append("package com.lokalized;\n")
          .append("\n")
          .append("import org.jspecify.annotations.NonNull;\n")
          .append("\n");
    }

    private void appendLocaleRulesField(StringBuilder source, String fieldName, String methodPrefix, List<LocaleRules> localeRules) {
      source.append("  @NonNull\n")
          .append("  static final LocaleRules @NonNull [] ").append(fieldName).append(" = concatenateLocaleRules(\n");

      for (int i = 0; i < numberOfChunks(localeRules.size()); ++i) {
        source.append("      ").append(methodPrefix).append(i).append("()");

        if (i < numberOfChunks(localeRules.size()) - 1)
          source.append(",");

        source.append("\n");
      }

      source.append("  );\n");
    }

    private void appendLocaleRangesField(StringBuilder source, String fieldName, String methodPrefix, List<LocaleRanges> localeRanges) {
      source.append("  @NonNull\n")
          .append("  static final LocaleRanges @NonNull [] ").append(fieldName).append(" = concatenateLocaleRanges(\n");

      for (int i = 0; i < numberOfChunks(localeRanges.size()); ++i) {
        source.append("      ").append(methodPrefix).append(i).append("()");

        if (i < numberOfChunks(localeRanges.size()) - 1)
          source.append(",");

        source.append("\n");
      }

      source.append("  );\n");
    }

    private void appendRuntimeConcatenationMethods(StringBuilder source) {
      source.append("  @NonNull\n")
          .append("  private static LocaleRules @NonNull [] concatenateLocaleRules(@NonNull LocaleRules @NonNull [] @NonNull ... arrays) {\n")
          .append("    int size = 0;\n")
          .append("\n")
          .append("    for (LocaleRules @NonNull [] array : arrays)\n")
          .append("      size += array.length;\n")
          .append("\n")
          .append("    LocaleRules @NonNull [] result = new LocaleRules[size];\n")
          .append("    int offset = 0;\n")
          .append("\n")
          .append("    for (LocaleRules @NonNull [] array : arrays) {\n")
          .append("      System.arraycopy(array, 0, result, offset, array.length);\n")
          .append("      offset += array.length;\n")
          .append("    }\n")
          .append("\n")
          .append("    return result;\n")
          .append("  }\n")
          .append("\n")
          .append("  @NonNull\n")
          .append("  private static LocaleRanges @NonNull [] concatenateLocaleRanges(@NonNull LocaleRanges @NonNull [] @NonNull ... arrays) {\n")
          .append("    int size = 0;\n")
          .append("\n")
          .append("    for (LocaleRanges @NonNull [] array : arrays)\n")
          .append("      size += array.length;\n")
          .append("\n")
          .append("    LocaleRanges @NonNull [] result = new LocaleRanges[size];\n")
          .append("    int offset = 0;\n")
          .append("\n")
          .append("    for (LocaleRanges @NonNull [] array : arrays) {\n")
          .append("      System.arraycopy(array, 0, result, offset, array.length);\n")
          .append("      offset += array.length;\n")
          .append("    }\n")
          .append("\n")
          .append("    return result;\n")
          .append("  }\n")
          .append("\n");
    }

    private void appendLocaleRulesMethods(StringBuilder source, String methodPrefix, List<LocaleRules> localeRules) {
      for (int chunkIndex = 0; chunkIndex < numberOfChunks(localeRules.size()); ++chunkIndex) {
        source.append("  @NonNull\n")
            .append("  private static LocaleRules @NonNull [] ").append(methodPrefix).append(chunkIndex).append("() {\n")
            .append("    return new LocaleRules[] {\n");
        appendLocaleRules(source, chunk(localeRules, chunkIndex));
        source.append("    };\n")
            .append("  }\n")
            .append("\n");
      }
    }

    private void appendLocaleRangesMethods(StringBuilder source, String methodPrefix, List<LocaleRanges> localeRanges) {
      for (int chunkIndex = 0; chunkIndex < numberOfChunks(localeRanges.size()); ++chunkIndex) {
        source.append("  @NonNull\n")
            .append("  private static LocaleRanges @NonNull [] ").append(methodPrefix).append(chunkIndex).append("() {\n")
            .append("    return new LocaleRanges[] {\n");
        appendLocaleRanges(source, chunk(localeRanges, chunkIndex));
        source.append("    };\n")
            .append("  }\n")
            .append("\n");
      }
    }

    private <T> List<T> chunk(List<T> values, int chunkIndex) {
      int start = chunkIndex * GENERATED_DATA_CHUNK_SIZE;
      int end = Math.min(values.size(), start + GENERATED_DATA_CHUNK_SIZE);
      return values.subList(start, end);
    }

    private int numberOfChunks(int size) {
      return Math.max(1, (size + GENERATED_DATA_CHUNK_SIZE - 1) / GENERATED_DATA_CHUNK_SIZE);
    }

    private void appendLocaleRules(StringBuilder source, List<LocaleRules> localeRules) {
      for (int i = 0; i < localeRules.size(); ++i) {
        LocaleRules rules = localeRules.get(i);
        source.append("        new LocaleRules(\"").append(escape(rules.getLocale()))
            .append("\", new Rule[] {\n");

        for (int j = 0; j < rules.getRules().size(); ++j) {
          Rule rule = rules.getRules().get(j);
          source.append("            new Rule(\"").append(escape(rule.getCount())).append("\", \"")
              .append(escape(rule.getCondition())).append("\", ");
          appendIntegerSampleList(source, rule.getIntegerSamples());
          source.append(", ");
          appendDecimalSampleList(source, rule.getDecimalSamples());
          source.append(")");

          if (j < rules.getRules().size() - 1)
            source.append(",");

          source.append("\n");
        }

        source.append("        })");

        if (i < localeRules.size() - 1)
          source.append(",");

        source.append("\n");
      }
    }

    private void appendLocaleRanges(StringBuilder source, List<LocaleRanges> localeRanges) {
      for (int i = 0; i < localeRanges.size(); ++i) {
        LocaleRanges ranges = localeRanges.get(i);
        source.append("        new LocaleRanges(\"").append(escape(ranges.getLocale()))
            .append("\", new RangeRule[] {\n");

        for (int j = 0; j < ranges.getRangeRules().size(); ++j) {
          RangeRule rangeRule = ranges.getRangeRules().get(j);
          source.append("            new RangeRule(\"").append(escape(rangeRule.getStart())).append("\", \"")
              .append(escape(rangeRule.getEnd())).append("\", \"").append(escape(rangeRule.getResult())).append("\")");

          if (j < ranges.getRangeRules().size() - 1)
            source.append(",");

          source.append("\n");
        }

        source.append("        })");

        if (i < localeRanges.size() - 1)
          source.append(",");

        source.append("\n");
      }
    }

    private void appendIntegerSampleList(StringBuilder source, SampleList samples) {
      source.append("CldrPluralRules.integerExample(").append(samples.isInfinite());

      for (String value : samples.getValues())
        source.append(", ").append(value);

      source.append(")");
    }

    private void appendDecimalSampleList(StringBuilder source, SampleList samples) {
      source.append("CldrPluralRules.decimalExample(").append(samples.isInfinite());

      for (String value : samples.getValues())
        source.append(", \"").append(escape(value)).append("\"");

      source.append(")");
    }

    private void appendCardinalitySamples(StringBuilder source, List<CardinalitySample> samples) {
      for (int i = 0; i < samples.size(); ++i) {
        CardinalitySample sample = samples.get(i);
        source.append("      new CardinalitySample(\"").append(sample.getCldrLocale()).append("\", \"")
            .append(sample.getSample()).append("\", Cardinality.").append(javaEnumName(sample.getExpected())).append(")");

        if (i < samples.size() - 1)
          source.append(",");

        source.append("\n");
      }
    }

    private void appendOrdinalitySamples(StringBuilder source, List<OrdinalitySample> samples) {
      for (int i = 0; i < samples.size(); ++i) {
        OrdinalitySample sample = samples.get(i);
        source.append("      new OrdinalitySample(\"").append(sample.getCldrLocale()).append("\", \"")
            .append(sample.getSample()).append("\", Ordinality.").append(javaEnumName(sample.getExpected())).append(")");

        if (i < samples.size() - 1)
          source.append(",");

        source.append("\n");
      }
    }

    private void appendCardinalityRangeSamples(StringBuilder source, List<CardinalityRangeSample> samples) {
      for (int i = 0; i < samples.size(); ++i) {
        CardinalityRangeSample sample = samples.get(i);
        source.append("      new CardinalityRangeSample(\"").append(sample.getCldrLocale()).append("\", Cardinality.")
            .append(javaEnumName(sample.getStart())).append(", Cardinality.").append(javaEnumName(sample.getEnd()))
            .append(", Cardinality.").append(javaEnumName(sample.getExpected())).append(")");

        if (i < samples.size() - 1)
          source.append(",");

        source.append("\n");
      }
    }

    private void appendSampleClasses(StringBuilder source) {
      source.append("\n")
          .append("  static final class CardinalitySample {\n")
          .append("    @NonNull\n")
          .append("    private final String cldrLocale;\n")
          .append("    @NonNull\n")
          .append("    private final String sample;\n")
          .append("    @NonNull\n")
          .append("    private final Cardinality expected;\n")
          .append("\n")
          .append("    private CardinalitySample(@NonNull String cldrLocale, @NonNull String sample, @NonNull Cardinality expected) {\n")
          .append("      requireNonNull(cldrLocale);\n")
          .append("      requireNonNull(sample);\n")
          .append("      requireNonNull(expected);\n")
          .append("\n")
          .append("      this.cldrLocale = cldrLocale;\n")
          .append("      this.sample = sample;\n")
          .append("      this.expected = expected;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    String getCldrLocale() {\n")
          .append("      return cldrLocale;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    String getSample() {\n")
          .append("      return sample;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    Cardinality getExpected() {\n")
          .append("      return expected;\n")
          .append("    }\n")
          .append("  }\n")
          .append("\n")
          .append("  static final class OrdinalitySample {\n")
          .append("    @NonNull\n")
          .append("    private final String cldrLocale;\n")
          .append("    @NonNull\n")
          .append("    private final String sample;\n")
          .append("    @NonNull\n")
          .append("    private final Ordinality expected;\n")
          .append("\n")
          .append("    private OrdinalitySample(@NonNull String cldrLocale, @NonNull String sample, @NonNull Ordinality expected) {\n")
          .append("      requireNonNull(cldrLocale);\n")
          .append("      requireNonNull(sample);\n")
          .append("      requireNonNull(expected);\n")
          .append("\n")
          .append("      this.cldrLocale = cldrLocale;\n")
          .append("      this.sample = sample;\n")
          .append("      this.expected = expected;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    String getCldrLocale() {\n")
          .append("      return cldrLocale;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    String getSample() {\n")
          .append("      return sample;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    Ordinality getExpected() {\n")
          .append("      return expected;\n")
          .append("    }\n")
          .append("  }\n")
          .append("\n")
          .append("  static final class CardinalityRangeSample {\n")
          .append("    @NonNull\n")
          .append("    private final String cldrLocale;\n")
          .append("    @NonNull\n")
          .append("    private final Cardinality start;\n")
          .append("    @NonNull\n")
          .append("    private final Cardinality end;\n")
          .append("    @NonNull\n")
          .append("    private final Cardinality expected;\n")
          .append("\n")
          .append("    private CardinalityRangeSample(@NonNull String cldrLocale, @NonNull Cardinality start, @NonNull Cardinality end,\n")
          .append("                                   @NonNull Cardinality expected) {\n")
          .append("      requireNonNull(cldrLocale);\n")
          .append("      requireNonNull(start);\n")
          .append("      requireNonNull(end);\n")
          .append("      requireNonNull(expected);\n")
          .append("\n")
          .append("      this.cldrLocale = cldrLocale;\n")
          .append("      this.start = start;\n")
          .append("      this.end = end;\n")
          .append("      this.expected = expected;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    String getCldrLocale() {\n")
          .append("      return cldrLocale;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    Cardinality getStart() {\n")
          .append("      return start;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    Cardinality getEnd() {\n")
          .append("      return end;\n")
          .append("    }\n")
          .append("\n")
          .append("    @NonNull\n")
          .append("    Cardinality getExpected() {\n")
          .append("      return expected;\n")
          .append("    }\n")
          .append("  }\n");
    }

    private String countForSample(String resourceName, String cldrLocale, BigDecimal sample) {
      Document document = documentFor(resourceName);
      NodeList pluralRules = document.getElementsByTagName("pluralRules");

      for (int i = 0; i < pluralRules.getLength(); ++i) {
        Element pluralRuleGroup = (Element) pluralRules.item(i);

        if (!containsLocale(pluralRuleGroup.getAttribute("locales"), cldrLocale))
          continue;

        NodeList pluralRuleElements = pluralRuleGroup.getElementsByTagName("pluralRule");

        for (int j = 0; j < pluralRuleElements.getLength(); ++j) {
          Element pluralRule = (Element) pluralRuleElements.item(j);

          if (samplesContain(pluralRule.getTextContent(), sample))
            return pluralRule.getAttribute("count");
        }

        throw new IllegalArgumentException(format("No pinned CLDR sample '%s' for locale '%s' in %s", sample, cldrLocale, resourceName));
      }

      throw new IllegalArgumentException(format("No pinned CLDR pluralRules data for locale '%s' in %s", cldrLocale, resourceName));
    }

    private String countForRange(String cldrLocale, String start, String end) {
      Document document = documentFor(PLURAL_RANGES_RESOURCE);
      NodeList pluralRanges = document.getElementsByTagName("pluralRanges");

      for (int i = 0; i < pluralRanges.getLength(); ++i) {
        Element pluralRangeGroup = (Element) pluralRanges.item(i);

        if (!containsLocale(pluralRangeGroup.getAttribute("locales"), cldrLocale))
          continue;

        NodeList pluralRangeElements = pluralRangeGroup.getElementsByTagName("pluralRange");

        for (int j = 0; j < pluralRangeElements.getLength(); ++j) {
          Element pluralRange = (Element) pluralRangeElements.item(j);

          if (pluralRange.getAttribute("start").equals(start) && pluralRange.getAttribute("end").equals(end))
            return pluralRange.getAttribute("result");
        }

        return end;
      }

      throw new IllegalArgumentException(format("No pinned CLDR pluralRanges data for locale '%s'", cldrLocale));
    }

    private String conditionFor(String ruleText) {
      int index = ruleText.indexOf('@');
      return index < 0 ? ruleText.trim() : ruleText.substring(0, index).trim();
    }

    private SampleList samplesFor(String ruleText, String marker) {
      int startIndex = ruleText.indexOf(marker);
      boolean infinite = false;
      List<String> values = new ArrayList<>();

      if (startIndex < 0)
        return new SampleList(false, values);

      startIndex += marker.length();
      int endIndex = ruleText.indexOf("@", startIndex);
      String sampleList = endIndex < 0 ? ruleText.substring(startIndex) : ruleText.substring(startIndex, endIndex);

      for (String rawToken : sampleList.split(",")) {
        String token = rawToken.trim();

        if (token.length() == 0)
          continue;

        if (token.indexOf('\u2026') >= 0) {
          infinite = true;
          continue;
        }

        if (token.indexOf('c') >= 0) {
          infinite = true;
          continue;
        }

        if (token.indexOf('~') >= 0) {
          String[] endpoints = token.split("~");

          if (endpoints.length == 2) {
            addSampleValue(values, endpoints[0]);
            addSampleValue(values, endpoints[1]);
          }
        } else {
          addSampleValue(values, token);
        }
      }

      return new SampleList(infinite, values);
    }

    private void addSampleValue(List<String> values, String rawValue) {
      String value = rawValue.trim();

      if (value.length() > 0 && !values.contains(value))
        values.add(value);
    }

    private boolean samplesContain(String ruleText, BigDecimal sample) {
      String marker = sample.scale() > 0 ? "@decimal" : "@integer";
      int startIndex = ruleText.indexOf(marker);

      if (startIndex < 0)
        return false;

      startIndex += marker.length();
      int endIndex = ruleText.indexOf("@", startIndex);
      String sampleList = endIndex < 0 ? ruleText.substring(startIndex) : ruleText.substring(startIndex, endIndex);

      for (String rawToken : sampleList.split(",")) {
        String token = rawToken.trim();

        if (token.length() == 0 || token.indexOf('c') >= 0 || token.indexOf('\u2026') >= 0)
          continue;

        if (token.indexOf('~') >= 0) {
          String[] endpoints = token.split("~");

          if (endpoints.length == 2 && inRange(sample, endpoints[0], endpoints[1]))
            return true;
        } else if (sample.compareTo(new BigDecimal(token)) == 0) {
          return true;
        }
      }

      return false;
    }

    private boolean inRange(BigDecimal sample, String minimum, String maximum) {
      return sample.compareTo(new BigDecimal(minimum.trim())) >= 0 && sample.compareTo(new BigDecimal(maximum.trim())) <= 0;
    }

    private Document documentFor(String resourceName) {
      Path path = cldrRoot.resolve(resourceName);

      try (InputStream inputStream = Files.newInputStream(path)) {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return documentBuilderFactory.newDocumentBuilder().parse(new InputSource(inputStream));
      } catch (Exception e) {
        throw new IllegalStateException(format("Unable to parse CLDR resource '%s'", path), e);
      }
    }

    private boolean containsLocale(String locales, String cldrLocale) {
      for (String locale : locales.split("\\s+"))
        if (locale.equals(cldrLocale))
          return true;

      return false;
    }

    private String canonicalLocale(String locale) {
      String canonicalLocale = locale.replace('_', '-');

      if ("iw".equals(canonicalLocale))
        return "he";
      if ("in".equals(canonicalLocale))
        return "id";
      if ("ji".equals(canonicalLocale))
        return "yi";

      return canonicalLocale;
    }

    private void verifySha256(String resourceName, String expected) throws Exception {
      Path path = cldrRoot.resolve(resourceName);
      String actual = sha256(path);

      if (!actual.equals(expected))
        throw new IllegalStateException(format("Expected %s to have SHA-256 %s, but was %s", path, expected, actual));
    }

    private String sha256(Path path) throws Exception {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = Files.readAllBytes(path);
      byte[] digest = messageDigest.digest(bytes);

      try (Formatter formatter = new Formatter()) {
        for (byte b : digest)
          formatter.format("%02x", b);

        return formatter.toString();
      }
    }
  }

  private static String javaEnumName(String cldrName) {
    return cldrName.toUpperCase(Locale.ENGLISH);
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static final class LocaleRules {
    private final String locale;
    private final List<Rule> rules;

    private LocaleRules(String locale, List<Rule> rules) {
      this.locale = locale;
      this.rules = rules;
    }

    private String getLocale() {
      return locale;
    }

    private List<Rule> getRules() {
      return rules;
    }
  }

  private static final class Rule {
    private final String count;
    private final String condition;
    private final SampleList integerSamples;
    private final SampleList decimalSamples;

    private Rule(String count, String condition, SampleList integerSamples, SampleList decimalSamples) {
      this.count = count;
      this.condition = condition;
      this.integerSamples = integerSamples;
      this.decimalSamples = decimalSamples;
    }

    private String getCount() {
      return count;
    }

    private String getCondition() {
      return condition;
    }

    private SampleList getIntegerSamples() {
      return integerSamples;
    }

    private SampleList getDecimalSamples() {
      return decimalSamples;
    }
  }

  private static final class SampleList {
    private final boolean infinite;
    private final List<String> values;

    private SampleList(boolean infinite, List<String> values) {
      this.infinite = infinite;
      this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    private boolean isInfinite() {
      return infinite;
    }

    private List<String> getValues() {
      return values;
    }
  }

  private static final class LocaleRanges {
    private final String locale;
    private final List<RangeRule> rangeRules;

    private LocaleRanges(String locale, List<RangeRule> rangeRules) {
      this.locale = locale;
      this.rangeRules = rangeRules;
    }

    private String getLocale() {
      return locale;
    }

    private List<RangeRule> getRangeRules() {
      return rangeRules;
    }
  }

  private static final class RangeRule {
    private final String start;
    private final String end;
    private final String result;

    private RangeRule(String start, String end, String result) {
      this.start = start;
      this.end = end;
      this.result = result;
    }

    private String getStart() {
      return start;
    }

    private String getEnd() {
      return end;
    }

    private String getResult() {
      return result;
    }
  }

  private static final class SampleRequest {
    private final String cldrLocale;
    private final String sample;

    private SampleRequest(String cldrLocale, String sample) {
      this.cldrLocale = cldrLocale;
      this.sample = sample;
    }

    private String getCldrLocale() {
      return cldrLocale;
    }

    private String getSample() {
      return sample;
    }
  }

  private static final class RangeRequest {
    private final String cldrLocale;
    private final String start;
    private final String end;

    private RangeRequest(String cldrLocale, String start, String end) {
      this.cldrLocale = cldrLocale;
      this.start = start;
      this.end = end;
    }

    private String getCldrLocale() {
      return cldrLocale;
    }

    private String getStart() {
      return start;
    }

    private String getEnd() {
      return end;
    }
  }

  private static final class CardinalitySample {
    private final String cldrLocale;
    private final String sample;
    private final String expected;

    private CardinalitySample(String cldrLocale, String sample, String expected) {
      this.cldrLocale = cldrLocale;
      this.sample = sample;
      this.expected = expected;
    }

    private String getCldrLocale() {
      return cldrLocale;
    }

    private String getSample() {
      return sample;
    }

    private String getExpected() {
      return expected;
    }
  }

  private static final class OrdinalitySample {
    private final String cldrLocale;
    private final String sample;
    private final String expected;

    private OrdinalitySample(String cldrLocale, String sample, String expected) {
      this.cldrLocale = cldrLocale;
      this.sample = sample;
      this.expected = expected;
    }

    private String getCldrLocale() {
      return cldrLocale;
    }

    private String getSample() {
      return sample;
    }

    private String getExpected() {
      return expected;
    }
  }

  private static final class CardinalityRangeSample {
    private final String cldrLocale;
    private final String start;
    private final String end;
    private final String expected;

    private CardinalityRangeSample(String cldrLocale, String start, String end, String expected) {
      this.cldrLocale = cldrLocale;
      this.start = start;
      this.end = end;
      this.expected = expected;
    }

    private String getCldrLocale() {
      return cldrLocale;
    }

    private String getStart() {
      return start;
    }

    private String getEnd() {
      return end;
    }

    private String getExpected() {
      return expected;
    }
  }
}
