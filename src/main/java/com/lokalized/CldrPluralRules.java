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
import org.jspecify.annotations.Nullable;

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
  @NonNull
  private static final Pattern RELATION_PATTERN;
  @NonNull
  private static final Map<@NonNull String, @NonNull LocaleRules> CARDINAL_RULES_BY_LOCALE;
  @NonNull
  private static final Map<@NonNull String, @NonNull LocaleRules> ORDINAL_RULES_BY_LOCALE;
  @NonNull
  private static final Map<@NonNull String, @NonNull LocaleRanges> CARDINAL_RANGES_BY_LOCALE;
  @NonNull
  private static final SortedSet<@NonNull String> CARDINAL_SUPPORTED_LOCALES;
  @NonNull
  private static final SortedSet<@NonNull String> ORDINAL_SUPPORTED_LOCALES;

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

  @NonNull
  static Cardinality cardinalityForNumber(@NonNull BigDecimal number, @NonNull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    Optional<@NonNull LocaleRules> localeRules = cardinalRulesForLocale(locale);

    if (!localeRules.isPresent())
      throw new UnsupportedLocaleException(locale);

    return cardinalityForCount(localeRules.get().countFor(number));
  }

  @NonNull
  static Ordinality ordinalityForNumber(@NonNull BigDecimal number, @NonNull Locale locale) {
    requireNonNull(number);
    requireNonNull(locale);

    Optional<@NonNull LocaleRules> localeRules = ordinalRulesForLocale(locale);

    if (!localeRules.isPresent())
      throw new UnsupportedLocaleException(locale);

    return ordinalityForCount(localeRules.get().countFor(number));
  }

  @NonNull
  static Cardinality cardinalityForRange(@NonNull Cardinality start, @NonNull Cardinality end, @NonNull Locale locale) {
    requireNonNull(start);
    requireNonNull(end);
    requireNonNull(locale);

    if (!cardinalRulesForLocale(locale).isPresent())
      throw new UnsupportedLocaleException(locale);

    SortedMap<@NonNull CardinalityRange, @NonNull Cardinality> cardinalitiesByRange = cardinalityRangesForLocale(locale);
    @Nullable Cardinality cardinality = cardinalitiesByRange.get(CardinalityRange.of(start, end));

    return cardinality == null ? end : cardinality;
  }

  @NonNull
  static SortedSet<@NonNull Cardinality> supportedCardinalitiesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    Optional<@NonNull LocaleRules> localeRules = cardinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().supportedCardinalities() : Collections.emptySortedSet();
  }

  @NonNull
  static SortedSet<@NonNull Ordinality> supportedOrdinalitiesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    Optional<@NonNull LocaleRules> localeRules = ordinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().supportedOrdinalities() : Collections.emptySortedSet();
  }

  @NonNull
  static SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull Integer>> cardinalityIntegerExamplesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    Optional<@NonNull LocaleRules> localeRules = cardinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().cardinalityIntegerExamples() : Collections.emptySortedMap();
  }

  @NonNull
  static SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull BigDecimal>> cardinalityDecimalExamplesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    Optional<@NonNull LocaleRules> localeRules = cardinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().cardinalityDecimalExamples() : Collections.emptySortedMap();
  }

  @NonNull
  static SortedMap<@NonNull Ordinality, @NonNull Range<@NonNull Integer>> ordinalityIntegerExamplesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    Optional<@NonNull LocaleRules> localeRules = ordinalRulesForLocale(locale);
    return localeRules.isPresent() ? localeRules.get().ordinalityIntegerExamples() : Collections.emptySortedMap();
  }

  @NonNull
  static SortedSet<@NonNull String> cardinalitySupportedLocales() {
    return CARDINAL_SUPPORTED_LOCALES;
  }

  @NonNull
  static SortedSet<@NonNull String> ordinalitySupportedLocales() {
    return ORDINAL_SUPPORTED_LOCALES;
  }

  @NonNull
  static SortedMap<@NonNull CardinalityRange, @NonNull Cardinality> cardinalityRangesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    Optional<@NonNull LocaleRanges> localeRanges = localeRangesForLocale(locale);
    return localeRanges.isPresent() ? localeRanges.get().cardinalitiesByRange() : Collections.emptySortedMap();
  }

  @NonNull
  static Example<@NonNull Integer> integerExample(boolean infinite, @NonNull Integer @Nullable ... values) {
    return new Example<>(infinite, values == null ? Collections.emptyList() : Arrays.asList(values));
  }

  @NonNull
  static Example<@NonNull BigDecimal> decimalExample(boolean infinite, @NonNull String @Nullable ... values) {
    List<@NonNull BigDecimal> decimals = new ArrayList<>();

    if (values != null)
      for (String value : values)
        decimals.add(new BigDecimal(value));

    return new Example<>(infinite, decimals);
  }

  @NonNull
  private static Optional<@NonNull LocaleRules> cardinalRulesForLocale(@NonNull Locale locale) {
    return rulesForLocale(CARDINAL_RULES_BY_LOCALE, locale);
  }

  @NonNull
  private static Optional<@NonNull LocaleRules> ordinalRulesForLocale(@NonNull Locale locale) {
    Optional<@NonNull LocaleRules> localeRules = rulesForLocale(ORDINAL_RULES_BY_LOCALE, locale);

    if (localeRules.isPresent())
      return localeRules;

    if (cardinalRulesForLocale(locale).isPresent())
      return Optional.of(ORDINAL_RULES_BY_LOCALE.get("root"));

    return Optional.empty();
  }

  @NonNull
  private static Optional<@NonNull LocaleRanges> localeRangesForLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    for (String candidate : localeCandidates(locale)) {
      @Nullable LocaleRanges localeRanges = CARDINAL_RANGES_BY_LOCALE.get(candidate);

      if (localeRanges != null)
        return Optional.of(localeRanges);
    }

    return Optional.empty();
  }

  @NonNull
  private static Optional<@NonNull LocaleRules> rulesForLocale(@NonNull Map<@NonNull String, @NonNull LocaleRules> rulesByLocale,
                                                               @NonNull Locale locale) {
    requireNonNull(rulesByLocale);
    requireNonNull(locale);

    for (String candidate : localeCandidates(locale)) {
      @Nullable LocaleRules localeRules = rulesByLocale.get(candidate);

      if (localeRules != null)
        return Optional.of(localeRules);
    }

    return Optional.empty();
  }

  @NonNull
  private static List<@NonNull String> localeCandidates(@NonNull Locale locale) {
    Optional<@NonNull String> language = LocaleUtils.normalizedLanguage(locale);

    if (!language.isPresent())
      return Collections.emptyList();

    @NonNull String script = locale.getScript();
    @NonNull String country = locale.getCountry();
    LinkedHashSet<@NonNull String> candidates = new LinkedHashSet<>();

    if (script != null && script.length() > 0 && country != null && country.length() > 0)
      candidates.add(format("%s-%s-%s", language.get(), script, country));

    if (script != null && script.length() > 0)
      candidates.add(format("%s-%s", language.get(), script));

    if (country != null && country.length() > 0)
      candidates.add(format("%s-%s", language.get(), country));

    candidates.add(language.get());
    return Collections.unmodifiableList(new ArrayList<>(candidates));
  }

  @NonNull
  private static Map<@NonNull String, @NonNull LocaleRules> rulesByLocale(@NonNull LocaleRules @NonNull [] localeRules) {
    Map<@NonNull String, @NonNull LocaleRules> rulesByLocale = new HashMap<>();

    for (LocaleRules rules : localeRules)
      rulesByLocale.put(rules.getLocale(), rules);

    return Collections.unmodifiableMap(rulesByLocale);
  }

  @NonNull
  private static Map<@NonNull String, @NonNull LocaleRanges> rangesByLocale(@NonNull LocaleRanges @NonNull [] localeRanges) {
    Map<@NonNull String, @NonNull LocaleRanges> rangesByLocale = new HashMap<>();

    for (LocaleRanges ranges : localeRanges)
      rangesByLocale.put(ranges.getLocale(), ranges);

    return Collections.unmodifiableMap(rangesByLocale);
  }

  @NonNull
  private static SortedSet<@NonNull String> supportedLocales(@NonNull Map<@NonNull String, @NonNull LocaleRules> rulesByLocale) {
    return Collections.unmodifiableSortedSet(new TreeSet<>(rulesByLocale.keySet()));
  }

  @NonNull
  private static SortedSet<@NonNull String> ordinalSupportedLocales(
      @NonNull Map<@NonNull String, @NonNull LocaleRules> cardinalRulesByLocale,
      @NonNull Map<@NonNull String, @NonNull LocaleRules> ordinalRulesByLocale) {
    SortedSet<@NonNull String> supportedLocales = new TreeSet<>(cardinalRulesByLocale.keySet());
    supportedLocales.addAll(ordinalRulesByLocale.keySet());
    return Collections.unmodifiableSortedSet(supportedLocales);
  }

  @NonNull
  private static Cardinality cardinalityForCount(@NonNull String count) {
    return Cardinality.valueOf(count.toUpperCase(Locale.ENGLISH));
  }

  @NonNull
  private static Ordinality ordinalityForCount(@NonNull String count) {
    return Ordinality.valueOf(count.toUpperCase(Locale.ENGLISH));
  }

  static final class LocaleRules {
    @NonNull
    private final String locale;
    @NonNull
    private final Rule @NonNull [] rules;

    LocaleRules(@NonNull String locale, @NonNull Rule @NonNull [] rules) {
      requireNonNull(locale);
      requireNonNull(rules);

      this.locale = locale;
      this.rules = Arrays.copyOf(rules, rules.length);
    }

    @NonNull
    String getLocale() {
      return locale;
    }

    @NonNull
    String countFor(@NonNull BigDecimal number) {
      Operands operands = new Operands(number);

      for (Rule rule : rules)
        if (rule.matches(operands))
          return rule.getCount();

      return "other";
    }

    @NonNull
    SortedSet<@NonNull Cardinality> supportedCardinalities() {
      SortedSet<@NonNull Cardinality> supportedCardinalities = new TreeSet<>();

      for (Rule rule : rules)
        supportedCardinalities.add(cardinalityForCount(rule.getCount()));

      return Collections.unmodifiableSortedSet(supportedCardinalities);
    }

    @NonNull
    SortedSet<@NonNull Ordinality> supportedOrdinalities() {
      SortedSet<@NonNull Ordinality> supportedOrdinalities = new TreeSet<>();

      for (Rule rule : rules)
        supportedOrdinalities.add(ordinalityForCount(rule.getCount()));

      return Collections.unmodifiableSortedSet(supportedOrdinalities);
    }

    @NonNull
    SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull Integer>> cardinalityIntegerExamples() {
      SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull Integer>> examples = new TreeMap<>();

      for (Rule rule : rules)
        if (!rule.getIntegerExample().isEmpty())
          examples.put(cardinalityForCount(rule.getCount()), rule.getIntegerExample().range());

      return Collections.unmodifiableSortedMap(examples);
    }

    @NonNull
    SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull BigDecimal>> cardinalityDecimalExamples() {
      SortedMap<@NonNull Cardinality, @NonNull Range<@NonNull BigDecimal>> examples = new TreeMap<>();

      for (Rule rule : rules)
        if (!rule.getDecimalExample().isEmpty())
          examples.put(cardinalityForCount(rule.getCount()), rule.getDecimalExample().range());

      return Collections.unmodifiableSortedMap(examples);
    }

    @NonNull
    SortedMap<@NonNull Ordinality, @NonNull Range<@NonNull Integer>> ordinalityIntegerExamples() {
      SortedMap<@NonNull Ordinality, @NonNull Range<@NonNull Integer>> examples = new TreeMap<>();

      for (Rule rule : rules)
        if (!rule.getIntegerExample().isEmpty())
          examples.put(ordinalityForCount(rule.getCount()), rule.getIntegerExample().range());

      return Collections.unmodifiableSortedMap(examples);
    }
  }

  static final class Rule {
    @NonNull
    private final String count;
    @NonNull
    private final Condition condition;
    @NonNull
    private final Example<@NonNull Integer> integerExample;
    @NonNull
    private final Example<@NonNull BigDecimal> decimalExample;

    Rule(@NonNull String count, @NonNull String condition, @NonNull Example<@NonNull Integer> integerExample,
         @NonNull Example<@NonNull BigDecimal> decimalExample) {
      requireNonNull(count);
      requireNonNull(condition);
      requireNonNull(integerExample);
      requireNonNull(decimalExample);

      this.count = count;
      this.condition = compile(condition);
      this.integerExample = integerExample;
      this.decimalExample = decimalExample;
    }

    @NonNull
    String getCount() {
      return count;
    }

    @NonNull
    Example<@NonNull Integer> getIntegerExample() {
      return integerExample;
    }

    @NonNull
    Example<@NonNull BigDecimal> getDecimalExample() {
      return decimalExample;
    }

    boolean matches(@NonNull Operands operands) {
      return condition.matches(operands);
    }
  }

  static final class LocaleRanges {
    @NonNull
    private final String locale;
    @NonNull
    private final RangeRule @NonNull [] rangeRules;

    LocaleRanges(@NonNull String locale, @NonNull RangeRule @NonNull [] rangeRules) {
      requireNonNull(locale);
      requireNonNull(rangeRules);

      this.locale = locale;
      this.rangeRules = Arrays.copyOf(rangeRules, rangeRules.length);
    }

    @NonNull
    String getLocale() {
      return locale;
    }

    @NonNull
    SortedMap<@NonNull CardinalityRange, @NonNull Cardinality> cardinalitiesByRange() {
      SortedMap<@NonNull CardinalityRange, @NonNull Cardinality> cardinalitiesByRange = new TreeMap<>();

      for (RangeRule rangeRule : rangeRules)
        cardinalitiesByRange.put(CardinalityRange.of(cardinalityForCount(rangeRule.getStart()), cardinalityForCount(rangeRule.getEnd())),
            cardinalityForCount(rangeRule.getResult()));

      return Collections.unmodifiableSortedMap(cardinalitiesByRange);
    }
  }

  static final class RangeRule {
    @NonNull
    private final String start;
    @NonNull
    private final String end;
    @NonNull
    private final String result;

    RangeRule(@NonNull String start, @NonNull String end, @NonNull String result) {
      requireNonNull(start);
      requireNonNull(end);
      requireNonNull(result);

      this.start = start;
      this.end = end;
      this.result = result;
    }

    @NonNull
    String getStart() {
      return start;
    }

    @NonNull
    String getEnd() {
      return end;
    }

    @NonNull
    String getResult() {
      return result;
    }
  }

  static final class Example<T> {
    @NonNull
    private final List<@NonNull T> values;
    private final boolean infinite;

    Example(boolean infinite, @NonNull List<@NonNull T> values) {
      requireNonNull(values);

      this.infinite = infinite;
      this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    boolean isEmpty() {
      return values.isEmpty();
    }

    @NonNull
    Range<@NonNull T> range() {
      return infinite ? Range.ofInfiniteValues(values) : Range.ofFiniteValues(values);
    }
  }

  private interface Condition {
    boolean matches(@NonNull Operands operands);
  }

  @NonNull
  private static Condition compile(@NonNull String condition) {
    @NonNull String trimmedCondition = condition.trim();

    if (trimmedCondition.length() == 0)
      return operands -> true;

    List<@NonNull Condition> disjuncts = new ArrayList<>();

    for (String orPart : trimmedCondition.split("\\s+or\\s+")) {
      List<@NonNull Condition> conjuncts = new ArrayList<>();

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

  @NonNull
  private static Condition compileRelation(@NonNull String relation) {
    Matcher matcher = RELATION_PATTERN.matcher(relation);

    if (!matcher.matches())
      throw new IllegalArgumentException(format("Unsupported CLDR plural relation '%s'", relation));

    Operand operand = Operand.forName(matcher.group(1));
    @Nullable BigDecimal modulus = matcher.group(2) == null ? null : new BigDecimal(matcher.group(2));
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

    @NonNull
    static Operand forName(@NonNull String name) {
      return Operand.valueOf(name.toUpperCase(Locale.ENGLISH));
    }
  }

  private static final class Operands {
    @NonNull
    private final BigDecimal n;
    @NonNull
    private final BigDecimal i;
    @NonNull
    private final BigDecimal v;
    @NonNull
    private final BigDecimal w;
    @NonNull
    private final BigDecimal f;
    @NonNull
    private final BigDecimal t;
    @NonNull
    private final BigDecimal c;
    @NonNull
    private final BigDecimal e;

    private Operands(@NonNull BigDecimal n) {
      requireNonNull(n);

      @NonNull BigDecimal strippedNumber = n.stripTrailingZeros();

      this.n = n;
      this.i = new BigDecimal(NumberUtils.integerComponent(n));
      this.v = BigDecimal.valueOf(NumberUtils.numberOfDecimalPlaces(n));
      this.w = BigDecimal.valueOf(Math.max(0, strippedNumber.scale()));
      this.f = new BigDecimal(NumberUtils.fractionalComponent(n));
      this.t = new BigDecimal(NumberUtils.fractionalComponent(strippedNumber));
      this.c = BigDecimal.ZERO;
      this.e = BigDecimal.ZERO;
    }

    @NonNull
    private BigDecimal valueFor(@NonNull Operand operand) {
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
    @NonNull
    private final List<@NonNull ValueRange> ranges;

    private ValueSet(@NonNull List<@NonNull ValueRange> ranges) {
      requireNonNull(ranges);

      this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
    }

    @NonNull
    static ValueSet parse(@NonNull String valueList) {
      List<@NonNull ValueRange> ranges = new ArrayList<>();

      for (String rawValue : valueList.split(",")) {
        @NonNull String value = rawValue.trim();

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

    boolean contains(@NonNull BigDecimal value) {
      for (ValueRange range : ranges)
        if (range.contains(value))
          return true;

      return false;
    }
  }

  private static final class ValueRange {
    @NonNull
    private final BigDecimal minimum;
    @NonNull
    private final BigDecimal maximum;

    private ValueRange(@NonNull BigDecimal minimum, @NonNull BigDecimal maximum) {
      requireNonNull(minimum);
      requireNonNull(maximum);

      this.minimum = minimum;
      this.maximum = maximum;
    }

    boolean contains(@NonNull BigDecimal value) {
      if (!integerValued(value))
        return minimum.compareTo(maximum) == 0 && value.compareTo(minimum) == 0;

      return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private boolean integerValued(@NonNull BigDecimal value) {
      return value.stripTrailingZeros().scale() <= 0;
    }
  }
}
