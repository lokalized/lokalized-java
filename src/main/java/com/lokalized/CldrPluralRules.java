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

import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Internal evaluator for generated CLDR plural rule data.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class CldrPluralRules {
  private static final Pattern RELATION_PATTERN;
  private static final Map<String, LocaleRules> CARDINAL_RULES_BY_LOCALE;
  private static final Map<String, LocaleRules> ORDINAL_RULES_BY_LOCALE;
  private static final Map<String, LocaleRanges> CARDINAL_RANGES_BY_LOCALE;
  private static final SortedSet<String> CARDINAL_SUPPORTED_LOCALES;
  private static final SortedSet<String> ORDINAL_SUPPORTED_LOCALES;

  static {
    RELATION_PATTERN = Pattern.compile("([nivwftec])(?:\\s*%\\s*([0-9]+))?\\s*(!=|=)\\s*(.+)");
    CARDINAL_RULES_BY_LOCALE = rulesByLocale(GeneratedCldrPluralData.CARDINAL_RULES);
    ORDINAL_RULES_BY_LOCALE = rulesByLocale(GeneratedCldrPluralData.ORDINAL_RULES);
    CARDINAL_RANGES_BY_LOCALE = rangesByLocale(GeneratedCldrPluralData.CARDINAL_RANGES);
    CARDINAL_SUPPORTED_LOCALES = supportedLocales(CARDINAL_RULES_BY_LOCALE);
    ORDINAL_SUPPORTED_LOCALES = ordinalSupportedLocales(CARDINAL_RULES_BY_LOCALE, ORDINAL_RULES_BY_LOCALE);
  }

  private CldrPluralRules() {
    // Non-instantiable
  }

  static Cardinality cardinalityForNumber(BigDecimal number, Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    Optional<LocaleRules> localeRules = cardinalRulesForLocale(locale);

    if (!localeRules.isPresent())
      throw new UnsupportedLocaleException(locale);

    return cardinalityForCount(localeRules.get().countFor(number));
  }

  static Ordinality ordinalityForNumber(BigDecimal number, Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    Optional<LocaleRules> localeRules = ordinalRulesForLocale(locale);

    if (!localeRules.isPresent())
      throw new UnsupportedLocaleException(locale);

    return ordinalityForCount(localeRules.get().countFor(number));
  }

  static Cardinality cardinalityForRange(Cardinality start, Cardinality end, Locale locale) {
    requireNonNull(start);
    requireNonNull(end);
    requireNonNull(locale);

    if (!cardinalRulesForLocale(locale).isPresent())
      throw new UnsupportedLocaleException(locale);

    SortedMap<CardinalityRange, Cardinality> cardinalitiesByRange = cardinalityRangesForLocale(locale);
    Cardinality cardinality = cardinalitiesByRange.get(CardinalityRange.of(start, end));

    return cardinality == null ? end : cardinality;
  }

  static SortedSet<Cardinality> supportedCardinalitiesForLocale(Locale locale) {
    requireNonNull(locale);

    Optional<LocaleRules> localeRules = cardinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().supportedCardinalities() : Collections.emptySortedSet();
  }

  static SortedSet<Ordinality> supportedOrdinalitiesForLocale(Locale locale) {
    requireNonNull(locale);

    Optional<LocaleRules> localeRules = ordinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().supportedOrdinalities() : Collections.emptySortedSet();
  }

  static SortedMap<Cardinality, Range<Integer>> cardinalityIntegerExamplesForLocale(Locale locale) {
    requireNonNull(locale);

    Optional<LocaleRules> localeRules = cardinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().cardinalityIntegerExamples() : Collections.emptySortedMap();
  }

  static SortedMap<Cardinality, Range<BigDecimal>> cardinalityDecimalExamplesForLocale(Locale locale) {
    requireNonNull(locale);

    Optional<LocaleRules> localeRules = cardinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().cardinalityDecimalExamples() : Collections.emptySortedMap();
  }

  static SortedMap<Ordinality, Range<Integer>> ordinalityIntegerExamplesForLocale(Locale locale) {
    requireNonNull(locale);

    Optional<LocaleRules> localeRules = ordinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().ordinalityIntegerExamples() : Collections.emptySortedMap();
  }

  static SortedSet<String> cardinalitySupportedLocales() {
    return CARDINAL_SUPPORTED_LOCALES;
  }

  static SortedSet<String> ordinalitySupportedLocales() {
    return ORDINAL_SUPPORTED_LOCALES;
  }

  static SortedMap<CardinalityRange, Cardinality> cardinalityRangesForLocale(Locale locale) {
    requireNonNull(locale);

    Optional<LocaleRanges> localeRanges = localeRangesForLocale(locale);
    return localeRanges.isPresent() ? localeRanges.get().cardinalitiesByRange() : Collections.emptySortedMap();
  }

  static Example<Integer> integerExample(boolean infinite, Integer... values) {
    return new Example<>(infinite, values == null ? Collections.emptyList() : Arrays.asList(values));
  }

  static Example<BigDecimal> decimalExample(boolean infinite, String... values) {
    List<BigDecimal> decimals = new ArrayList<>();

    if (values != null)
      for (String value : values)
        decimals.add(new BigDecimal(value));

    return new Example<>(infinite, decimals);
  }

  private static Optional<LocaleRules> cardinalRulesForLocale(Locale locale) {
    return rulesForLocale(CARDINAL_RULES_BY_LOCALE, locale);
  }

  private static Optional<LocaleRules> ordinalRulesForLocale(Locale locale) {
    Optional<LocaleRules> localeRules = rulesForLocale(ORDINAL_RULES_BY_LOCALE, locale);

    if (localeRules.isPresent())
      return localeRules;

    if (cardinalRulesForLocale(locale).isPresent())
      return Optional.of(ORDINAL_RULES_BY_LOCALE.get("root"));

    return Optional.empty();
  }

  private static Optional<LocaleRanges> localeRangesForLocale(Locale locale) {
    requireNonNull(locale);

    for (String candidate : localeCandidates(locale)) {
      LocaleRanges localeRanges = CARDINAL_RANGES_BY_LOCALE.get(candidate);

      if (localeRanges != null)
        return Optional.of(localeRanges);
    }

    return Optional.empty();
  }

  private static Optional<LocaleRules> rulesForLocale(Map<String, LocaleRules> rulesByLocale, Locale locale) {
    requireNonNull(rulesByLocale);
    requireNonNull(locale);

    for (String candidate : localeCandidates(locale)) {
      LocaleRules localeRules = rulesByLocale.get(candidate);

      if (localeRules != null)
        return Optional.of(localeRules);
    }

    return Optional.empty();
  }

  private static List<String> localeCandidates(Locale locale) {
    Optional<String> language = LocaleUtils.normalizedLanguage(locale);

    if (!language.isPresent())
      return Collections.emptyList();

    String script = locale.getScript();
    String country = locale.getCountry();
    LinkedHashSet<String> candidates = new LinkedHashSet<>();

    if (script != null && script.length() > 0 && country != null && country.length() > 0)
      candidates.add(format("%s-%s-%s", language.get(), script, country));

    if (script != null && script.length() > 0)
      candidates.add(format("%s-%s", language.get(), script));

    if (country != null && country.length() > 0)
      candidates.add(format("%s-%s", language.get(), country));

    candidates.add(language.get());
    return Collections.unmodifiableList(new ArrayList<>(candidates));
  }

  private static Map<String, LocaleRules> rulesByLocale(LocaleRules[] localeRules) {
    Map<String, LocaleRules> rulesByLocale = new HashMap<>();

    for (LocaleRules rules : localeRules)
      rulesByLocale.put(rules.getLocale(), rules);

    return Collections.unmodifiableMap(rulesByLocale);
  }

  private static Map<String, LocaleRanges> rangesByLocale(LocaleRanges[] localeRanges) {
    Map<String, LocaleRanges> rangesByLocale = new HashMap<>();

    for (LocaleRanges ranges : localeRanges)
      rangesByLocale.put(ranges.getLocale(), ranges);

    return Collections.unmodifiableMap(rangesByLocale);
  }

  private static SortedSet<String> supportedLocales(Map<String, LocaleRules> rulesByLocale) {
    return Collections.unmodifiableSortedSet(new TreeSet<>(rulesByLocale.keySet()));
  }

  private static SortedSet<String> ordinalSupportedLocales(Map<String, LocaleRules> cardinalRulesByLocale,
                                                           Map<String, LocaleRules> ordinalRulesByLocale) {
    SortedSet<String> supportedLocales = new TreeSet<>(cardinalRulesByLocale.keySet());
    supportedLocales.addAll(ordinalRulesByLocale.keySet());
    return Collections.unmodifiableSortedSet(supportedLocales);
  }

  private static Cardinality cardinalityForCount(String count) {
    return Cardinality.valueOf(count.toUpperCase(Locale.ENGLISH));
  }

  private static Ordinality ordinalityForCount(String count) {
    return Ordinality.valueOf(count.toUpperCase(Locale.ENGLISH));
  }

  static final class LocaleRules {
    private final String locale;
    private final Rule[] rules;

    LocaleRules(String locale, Rule[] rules) {
      requireNonNull(locale);
      requireNonNull(rules);

      this.locale = locale;
      this.rules = Arrays.copyOf(rules, rules.length);
    }

    String getLocale() {
      return locale;
    }

    String countFor(BigDecimal number) {
      Operands operands = new Operands(number);

      for (Rule rule : rules)
        if (rule.matches(operands))
          return rule.getCount();

      return "other";
    }

    SortedSet<Cardinality> supportedCardinalities() {
      SortedSet<Cardinality> supportedCardinalities = new TreeSet<>();

      for (Rule rule : rules)
        supportedCardinalities.add(cardinalityForCount(rule.getCount()));

      return Collections.unmodifiableSortedSet(supportedCardinalities);
    }

    SortedSet<Ordinality> supportedOrdinalities() {
      SortedSet<Ordinality> supportedOrdinalities = new TreeSet<>();

      for (Rule rule : rules)
        supportedOrdinalities.add(ordinalityForCount(rule.getCount()));

      return Collections.unmodifiableSortedSet(supportedOrdinalities);
    }

    SortedMap<Cardinality, Range<Integer>> cardinalityIntegerExamples() {
      SortedMap<Cardinality, Range<Integer>> examples = new TreeMap<>();

      for (Rule rule : rules)
        if (!rule.getIntegerExample().isEmpty())
          examples.put(cardinalityForCount(rule.getCount()), rule.getIntegerExample().range());

      return Collections.unmodifiableSortedMap(examples);
    }

    SortedMap<Cardinality, Range<BigDecimal>> cardinalityDecimalExamples() {
      SortedMap<Cardinality, Range<BigDecimal>> examples = new TreeMap<>();

      for (Rule rule : rules)
        if (!rule.getDecimalExample().isEmpty())
          examples.put(cardinalityForCount(rule.getCount()), rule.getDecimalExample().range());

      return Collections.unmodifiableSortedMap(examples);
    }

    SortedMap<Ordinality, Range<Integer>> ordinalityIntegerExamples() {
      SortedMap<Ordinality, Range<Integer>> examples = new TreeMap<>();

      for (Rule rule : rules)
        if (!rule.getIntegerExample().isEmpty())
          examples.put(ordinalityForCount(rule.getCount()), rule.getIntegerExample().range());

      return Collections.unmodifiableSortedMap(examples);
    }
  }

  static final class Rule {
    private final String count;
    private final Condition condition;
    private final Example<Integer> integerExample;
    private final Example<BigDecimal> decimalExample;

    Rule(String count, String condition, Example<Integer> integerExample, Example<BigDecimal> decimalExample) {
      requireNonNull(count);
      requireNonNull(condition);
      requireNonNull(integerExample);
      requireNonNull(decimalExample);

      this.count = count;
      this.condition = compile(condition);
      this.integerExample = integerExample;
      this.decimalExample = decimalExample;
    }

    String getCount() {
      return count;
    }

    Example<Integer> getIntegerExample() {
      return integerExample;
    }

    Example<BigDecimal> getDecimalExample() {
      return decimalExample;
    }

    boolean matches(Operands operands) {
      return condition.matches(operands);
    }
  }

  static final class LocaleRanges {
    private final String locale;
    private final RangeRule[] rangeRules;

    LocaleRanges(String locale, RangeRule[] rangeRules) {
      requireNonNull(locale);
      requireNonNull(rangeRules);

      this.locale = locale;
      this.rangeRules = Arrays.copyOf(rangeRules, rangeRules.length);
    }

    String getLocale() {
      return locale;
    }

    SortedMap<CardinalityRange, Cardinality> cardinalitiesByRange() {
      SortedMap<CardinalityRange, Cardinality> cardinalitiesByRange = new TreeMap<>();

      for (RangeRule rangeRule : rangeRules)
        cardinalitiesByRange.put(CardinalityRange.of(cardinalityForCount(rangeRule.getStart()), cardinalityForCount(rangeRule.getEnd())),
            cardinalityForCount(rangeRule.getResult()));

      return Collections.unmodifiableSortedMap(cardinalitiesByRange);
    }
  }

  static final class RangeRule {
    private final String start;
    private final String end;
    private final String result;

    RangeRule(String start, String end, String result) {
      requireNonNull(start);
      requireNonNull(end);
      requireNonNull(result);

      this.start = start;
      this.end = end;
      this.result = result;
    }

    String getStart() {
      return start;
    }

    String getEnd() {
      return end;
    }

    String getResult() {
      return result;
    }
  }

  static final class Example<T> {
    private final boolean infinite;
    private final List<T> values;

    Example(boolean infinite, List<T> values) {
      requireNonNull(values);

      this.infinite = infinite;
      this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    boolean isEmpty() {
      return values.isEmpty();
    }

    Range<T> range() {
      return infinite ? Range.ofInfiniteValues(values) : Range.ofFiniteValues(values);
    }
  }

  private interface Condition {
    boolean matches(Operands operands);
  }

  private static Condition compile(String condition) {
    String trimmedCondition = condition.trim();

    if (trimmedCondition.length() == 0)
      return operands -> true;

    List<Condition> disjuncts = new ArrayList<>();

    for (String orPart : trimmedCondition.split("\\s+or\\s+")) {
      List<Condition> conjuncts = new ArrayList<>();

      for (String andPart : orPart.split("\\s+and\\s+"))
        conjuncts.add(compileRelation(andPart.trim()));

      disjuncts.add(operands -> {
        for (Condition conjunct : conjuncts)
          if (!conjunct.matches(operands))
            return false;

        return true;
      });
    }

    return operands -> {
      for (Condition disjunct : disjuncts)
        if (disjunct.matches(operands))
          return true;

      return false;
    };
  }

  private static Condition compileRelation(String relation) {
    Matcher matcher = RELATION_PATTERN.matcher(relation);

    if (!matcher.matches())
      throw new IllegalArgumentException(format("Unsupported CLDR plural relation '%s'", relation));

    Operand operand = Operand.forName(matcher.group(1));
    BigDecimal modulus = matcher.group(2) == null ? null : new BigDecimal(matcher.group(2));
    boolean negated = "!=".equals(matcher.group(3));
    ValueSet valueSet = ValueSet.parse(matcher.group(4));

    return operands -> {
      BigDecimal value = operands.valueFor(operand);

      if (modulus != null)
        value = value.remainder(modulus);

      boolean contains = valueSet.contains(value);
      return negated ? !contains : contains;
    };
  }

  private enum Operand {
    N,
    I,
    V,
    W,
    F,
    T,
    C,
    E;

    static Operand forName(String name) {
      return Operand.valueOf(name.toUpperCase(Locale.ENGLISH));
    }
  }

  private static final class Operands {
    private final BigDecimal n;
    private final BigDecimal i;
    private final BigDecimal v;
    private final BigDecimal w;
    private final BigDecimal f;
    private final BigDecimal t;
    private final BigDecimal c;
    private final BigDecimal e;

    private Operands(BigDecimal n) {
      requireNonNull(n);

      BigDecimal strippedNumber = n.stripTrailingZeros();

      this.n = n;
      this.i = new BigDecimal(NumberUtils.integerComponent(n));
      this.v = BigDecimal.valueOf(NumberUtils.numberOfDecimalPlaces(n));
      this.w = BigDecimal.valueOf(Math.max(0, strippedNumber.scale()));
      this.f = new BigDecimal(NumberUtils.fractionalComponent(n));
      this.t = new BigDecimal(NumberUtils.fractionalComponent(strippedNumber));
      this.c = BigDecimal.ZERO;
      this.e = BigDecimal.ZERO;
    }

    private BigDecimal valueFor(Operand operand) {
      switch (operand) {
        case N:
          return n;
        case I:
          return i;
        case V:
          return v;
        case W:
          return w;
        case F:
          return f;
        case T:
          return t;
        case C:
          return c;
        case E:
          return e;
        default:
          throw new IllegalArgumentException(format("Unsupported CLDR plural operand '%s'", operand));
      }
    }
  }

  private static final class ValueSet {
    private final List<ValueRange> ranges;

    private ValueSet(List<ValueRange> ranges) {
      this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
    }

    static ValueSet parse(String valueList) {
      List<ValueRange> ranges = new ArrayList<>();

      for (String rawValue : valueList.split(",")) {
        String value = rawValue.trim();

        if (value.length() == 0)
          continue;

        int rangeIndex = value.indexOf("..");

        if (rangeIndex >= 0) {
          ranges.add(new ValueRange(new BigDecimal(value.substring(0, rangeIndex).trim()),
              new BigDecimal(value.substring(rangeIndex + 2).trim())));
        } else {
          BigDecimal exact = new BigDecimal(value);
          ranges.add(new ValueRange(exact, exact));
        }
      }

      return new ValueSet(ranges);
    }

    boolean contains(BigDecimal value) {
      for (ValueRange range : ranges)
        if (range.contains(value))
          return true;

      return false;
    }
  }

  private static final class ValueRange {
    private final BigDecimal minimum;
    private final BigDecimal maximum;

    private ValueRange(BigDecimal minimum, BigDecimal maximum) {
      this.minimum = minimum;
      this.maximum = maximum;
    }

    boolean contains(BigDecimal value) {
      if (!integerValued(value))
        return minimum.compareTo(maximum) == 0 && value.compareTo(minimum) == 0;

      return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private boolean integerValued(BigDecimal value) {
      return value.stripTrailingZeros().scale() <= 0;
    }
  }
}
