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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
enum TokenType {
  VARIABLE(null),
  NUMBER(null),
  BOOLEAN_RESULT(null),
  GROUP_START("("),
  GROUP_END(")"),
  AND("&&"),
  OR("||"),
  LESS_THAN("<"),
  GREATER_THAN(">"),
  EQUAL_TO("=="),
  NOT_EQUAL_TO("!="),
  LESS_THAN_OR_EQUAL_TO("<="),
  GREATER_THAN_OR_EQUAL_TO(">="),
  CARDINALITY_ZERO("CARDINALITY_ZERO"),
  CARDINALITY_ONE("CARDINALITY_ONE"),
  CARDINALITY_TWO("CARDINALITY_TWO"),
  CARDINALITY_FEW("CARDINALITY_FEW"),
  CARDINALITY_MANY("CARDINALITY_MANY"),
  CARDINALITY_OTHER("CARDINALITY_OTHER"),
  ORDINALITY_ZERO("ORDINALITY_ZERO"),
  ORDINALITY_ONE("ORDINALITY_ONE"),
  ORDINALITY_TWO("ORDINALITY_TWO"),
  ORDINALITY_FEW("ORDINALITY_FEW"),
  ORDINALITY_MANY("ORDINALITY_MANY"),
  ORDINALITY_OTHER("ORDINALITY_OTHER"),
  PHONETIC_VOWEL("PHONETIC_VOWEL"),
  PHONETIC_CONSONANT("PHONETIC_CONSONANT"),
  PHONETIC_OTHER("PHONETIC_OTHER"),
  PHONETIC_H_SILENT("PHONETIC_H_SILENT"),
  PHONETIC_H_ASPIRATED("PHONETIC_H_ASPIRATED"),
  PHONETIC_S_IMPURE("PHONETIC_S_IMPURE"),
  PHONETIC_Z("PHONETIC_Z"),
  PHONETIC_GN("PHONETIC_GN"),
  PHONETIC_PS("PHONETIC_PS"),
  PHONETIC_PN("PHONETIC_PN"),
  PHONETIC_X("PHONETIC_X"),
  PHONETIC_GLIDE_Y("PHONETIC_GLIDE_Y"),
  PHONETIC_GLIDE_W("PHONETIC_GLIDE_W"),
  PHONETIC_STRESSED_A("PHONETIC_STRESSED_A"),
  PHONETIC_SOLAR("PHONETIC_SOLAR"),
  PHONETIC_LUNAR("PHONETIC_LUNAR"),
  GENDER_MASCULINE("GENDER_MASCULINE"),
  GENDER_FEMININE("GENDER_FEMININE"),
  GENDER_COMMON("GENDER_COMMON"),
  GENDER_NEUTER("GENDER_NEUTER"),
  CASE_NOMINATIVE("CASE_NOMINATIVE"),
  CASE_ACCUSATIVE("CASE_ACCUSATIVE"),
  CASE_GENITIVE("CASE_GENITIVE"),
  CASE_DATIVE("CASE_DATIVE"),
  CASE_INSTRUMENTAL("CASE_INSTRUMENTAL"),
  CASE_LOCATIVE("CASE_LOCATIVE"),
  CASE_PREPOSITIONAL("CASE_PREPOSITIONAL"),
  CASE_VOCATIVE("CASE_VOCATIVE"),
  CASE_ABLATIVE("CASE_ABLATIVE"),
  DEFINITENESS_DEFINITE("DEFINITENESS_DEFINITE"),
  DEFINITENESS_INDEFINITE("DEFINITENESS_INDEFINITE"),
  DEFINITENESS_CONSTRUCT("DEFINITENESS_CONSTRUCT"),
  CLASSIFIER_GENERAL("CLASSIFIER_GENERAL"),
  CLASSIFIER_PERSON("CLASSIFIER_PERSON"),
  CLASSIFIER_ANIMAL("CLASSIFIER_ANIMAL"),
  CLASSIFIER_LONG_THIN("CLASSIFIER_LONG_THIN"),
  CLASSIFIER_FLAT("CLASSIFIER_FLAT"),
  CLASSIFIER_BOUND("CLASSIFIER_BOUND"),
  CLASSIFIER_MACHINE("CLASSIFIER_MACHINE"),
  CLASSIFIER_VEHICLE("CLASSIFIER_VEHICLE"),
  FORMALITY_CASUAL("FORMALITY_CASUAL"),
  FORMALITY_INFORMAL("FORMALITY_INFORMAL"),
  FORMALITY_FORMAL("FORMALITY_FORMAL"),
  FORMALITY_HUMBLE("FORMALITY_HUMBLE"),
  FORMALITY_HONORIFIC("FORMALITY_HONORIFIC"),
  CLUSIVITY_INCLUSIVE("CLUSIVITY_INCLUSIVE"),
  CLUSIVITY_EXCLUSIVE("CLUSIVITY_EXCLUSIVE"),
  ANIMACY_ANIMATE("ANIMACY_ANIMATE"),
  ANIMACY_INANIMATE("ANIMACY_INANIMATE");

  @Nullable
  private final String symbol;
  @NonNull
  private static final Set<@NonNull TokenType> TOKEN_TYPES_WITH_DEFINED_SYMBOL;
  @NonNull
  private static final Set<@NonNull TokenType> TOKEN_TYPES_WITH_UNDEFINED_SYMBOL;
  @NonNull
  private static final Map<@NonNull String, @NonNull TokenType> TOKEN_TYPES_BY_SYMBOL;

  static {
    TOKEN_TYPES_WITH_DEFINED_SYMBOL = Collections.unmodifiableSet(Arrays.asList(TokenType.values()).stream()
        .filter(tokenType -> tokenType.getSymbol().isPresent())
        .collect(Collectors.toSet()));

    TOKEN_TYPES_WITH_UNDEFINED_SYMBOL = Collections.unmodifiableSet(Arrays.asList(TokenType.values()).stream()
        .filter(tokenType -> !tokenType.getSymbol().isPresent())
        .collect(Collectors.toSet()));

    TOKEN_TYPES_BY_SYMBOL = Collections.unmodifiableMap(TOKEN_TYPES_WITH_DEFINED_SYMBOL.stream()
        .collect(Collectors.toMap(tokenType -> tokenType.getSymbol().get(), tokenType -> tokenType)));
  }

  TokenType(@Nullable String symbol) {
    this.symbol = symbol;
  }

  @NonNull
  public Optional<String> getSymbol() {
    return Optional.ofNullable(symbol);
  }

  @NonNull
  public static Set<@NonNull TokenType> getTokenTypesWithDefinedSymbol() {
    return TOKEN_TYPES_WITH_DEFINED_SYMBOL;
  }

  @NonNull
  public static Set<@NonNull TokenType> getTokenTypesWithUndefinedSymbol() {
    return TOKEN_TYPES_WITH_UNDEFINED_SYMBOL;
  }

  @NonNull
  public static Map<@NonNull String, @NonNull TokenType> getTokenTypesBySymbol() {
    return TOKEN_TYPES_BY_SYMBOL;
  }
}
