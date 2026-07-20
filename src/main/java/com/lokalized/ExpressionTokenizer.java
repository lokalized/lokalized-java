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

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.lokalized.Diagnostics.format;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

/**
 * Breaks an expression into its {@link Token} components.
 * ASCII space, horizontal tab, carriage return, line feed, and form feed characters are ignored between tokens.
 * Other whitespace and separator characters are rejected.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class ExpressionTokenizer {
  @NonNull
  private static final Map<@NonNull TokenType, @NonNull String> PATTERNS_BY_TOKEN_TYPE;
  @NonNull
  private static final Map<@NonNull TokenType, @NonNull String> GROUP_NAMES_BY_TOKEN_TYPE;
  @NonNull
  private static final Pattern TOKEN_PATTERN;
  @NonNull
  private static final String WHITESPACE_GROUP_NAME = "WHITESPACE";
  @NonNull
  private static final String WHITESPACE_GROUP_PATTERN = "[ \\t\\r\\n\\f]";

  static {
    // Performs double-duty: keyset maintains insertion order so the regexes are always attempted in correct order.
    // Must escape \.[]{}()*+-?^$|
    PATTERNS_BY_TOKEN_TYPE = Collections.unmodifiableMap(new LinkedHashMap<TokenType, String>() {{
      put(TokenType.GROUP_START, "\\(");
      put(TokenType.GROUP_END, "\\)");
      put(TokenType.AND, "&&");
      put(TokenType.OR, "\\|\\|");
      put(TokenType.LESS_THAN_OR_EQUAL_TO, "<=");
      put(TokenType.GREATER_THAN_OR_EQUAL_TO, ">=");
      put(TokenType.LESS_THAN, "<");
      put(TokenType.GREATER_THAN, ">");
      put(TokenType.EQUAL_TO, "==");
      put(TokenType.NOT_EQUAL_TO, "!=");
      put(TokenType.NUMBER, "[+-]?((\\d+\\.\\d*)|(\\.\\d+)|(\\d+))([eE][+-]?\\d+)?");
      put(TokenType.VARIABLE, LocalizedStringUtils.localizedStringIdentifierPattern());
      put(TokenType.CARDINALITY_ZERO, "\\bCARDINALITY_ZERO\\b");
      put(TokenType.CARDINALITY_ONE, "\\bCARDINALITY_ONE\\b");
      put(TokenType.CARDINALITY_TWO, "\\bCARDINALITY_TWO\\b");
      put(TokenType.CARDINALITY_FEW, "\\bCARDINALITY_FEW\\b");
      put(TokenType.CARDINALITY_MANY, "\\bCARDINALITY_MANY\\b");
      put(TokenType.CARDINALITY_OTHER, "\\bCARDINALITY_OTHER\\b");
      put(TokenType.ORDINALITY_ZERO, "\\bORDINALITY_ZERO\\b");
      put(TokenType.ORDINALITY_ONE, "\\bORDINALITY_ONE\\b");
      put(TokenType.ORDINALITY_TWO, "\\bORDINALITY_TWO\\b");
      put(TokenType.ORDINALITY_FEW, "\\bORDINALITY_FEW\\b");
      put(TokenType.ORDINALITY_MANY, "\\bORDINALITY_MANY\\b");
      put(TokenType.ORDINALITY_OTHER, "\\bORDINALITY_OTHER\\b");
      put(TokenType.PHONETIC_VOWEL, "\\bPHONETIC_VOWEL\\b");
      put(TokenType.PHONETIC_CONSONANT, "\\bPHONETIC_CONSONANT\\b");
      put(TokenType.PHONETIC_OTHER, "\\bPHONETIC_OTHER\\b");
      put(TokenType.PHONETIC_H_SILENT, "\\bPHONETIC_H_SILENT\\b");
      put(TokenType.PHONETIC_H_ASPIRATED, "\\bPHONETIC_H_ASPIRATED\\b");
      put(TokenType.PHONETIC_S_IMPURE, "\\bPHONETIC_S_IMPURE\\b");
      put(TokenType.PHONETIC_Z, "\\bPHONETIC_Z\\b");
      put(TokenType.PHONETIC_GN, "\\bPHONETIC_GN\\b");
      put(TokenType.PHONETIC_PS, "\\bPHONETIC_PS\\b");
      put(TokenType.PHONETIC_PN, "\\bPHONETIC_PN\\b");
      put(TokenType.PHONETIC_X, "\\bPHONETIC_X\\b");
      put(TokenType.PHONETIC_GLIDE_Y, "\\bPHONETIC_GLIDE_Y\\b");
      put(TokenType.PHONETIC_GLIDE_W, "\\bPHONETIC_GLIDE_W\\b");
      put(TokenType.PHONETIC_STRESSED_A, "\\bPHONETIC_STRESSED_A\\b");
      put(TokenType.PHONETIC_SOLAR, "\\bPHONETIC_SOLAR\\b");
      put(TokenType.PHONETIC_LUNAR, "\\bPHONETIC_LUNAR\\b");
      put(TokenType.GENDER_MASCULINE, "\\bGENDER_MASCULINE\\b");
      put(TokenType.GENDER_FEMININE, "\\bGENDER_FEMININE\\b");
      put(TokenType.GENDER_COMMON, "\\bGENDER_COMMON\\b");
      put(TokenType.GENDER_NEUTER, "\\bGENDER_NEUTER\\b");
      put(TokenType.CASE_NOMINATIVE, "\\bCASE_NOMINATIVE\\b");
      put(TokenType.CASE_ACCUSATIVE, "\\bCASE_ACCUSATIVE\\b");
      put(TokenType.CASE_GENITIVE, "\\bCASE_GENITIVE\\b");
      put(TokenType.CASE_DATIVE, "\\bCASE_DATIVE\\b");
      put(TokenType.CASE_INSTRUMENTAL, "\\bCASE_INSTRUMENTAL\\b");
      put(TokenType.CASE_LOCATIVE, "\\bCASE_LOCATIVE\\b");
      put(TokenType.CASE_PREPOSITIONAL, "\\bCASE_PREPOSITIONAL\\b");
      put(TokenType.CASE_VOCATIVE, "\\bCASE_VOCATIVE\\b");
      put(TokenType.CASE_ABLATIVE, "\\bCASE_ABLATIVE\\b");
      put(TokenType.DEFINITENESS_DEFINITE, "\\bDEFINITENESS_DEFINITE\\b");
      put(TokenType.DEFINITENESS_INDEFINITE, "\\bDEFINITENESS_INDEFINITE\\b");
      put(TokenType.DEFINITENESS_CONSTRUCT, "\\bDEFINITENESS_CONSTRUCT\\b");
      put(TokenType.CLASSIFIER_GENERAL, "\\bCLASSIFIER_GENERAL\\b");
      put(TokenType.CLASSIFIER_PERSON, "\\bCLASSIFIER_PERSON\\b");
      put(TokenType.CLASSIFIER_ANIMAL, "\\bCLASSIFIER_ANIMAL\\b");
      put(TokenType.CLASSIFIER_LONG_THIN, "\\bCLASSIFIER_LONG_THIN\\b");
      put(TokenType.CLASSIFIER_FLAT, "\\bCLASSIFIER_FLAT\\b");
      put(TokenType.CLASSIFIER_BOUND, "\\bCLASSIFIER_BOUND\\b");
      put(TokenType.CLASSIFIER_MACHINE, "\\bCLASSIFIER_MACHINE\\b");
      put(TokenType.CLASSIFIER_VEHICLE, "\\bCLASSIFIER_VEHICLE\\b");
      put(TokenType.FORMALITY_CASUAL, "\\bFORMALITY_CASUAL\\b");
      put(TokenType.FORMALITY_INFORMAL, "\\bFORMALITY_INFORMAL\\b");
      put(TokenType.FORMALITY_FORMAL, "\\bFORMALITY_FORMAL\\b");
      put(TokenType.FORMALITY_HUMBLE, "\\bFORMALITY_HUMBLE\\b");
      put(TokenType.FORMALITY_HONORIFIC, "\\bFORMALITY_HONORIFIC\\b");
      put(TokenType.CLUSIVITY_INCLUSIVE, "\\bCLUSIVITY_INCLUSIVE\\b");
      put(TokenType.CLUSIVITY_EXCLUSIVE, "\\bCLUSIVITY_EXCLUSIVE\\b");
      put(TokenType.ANIMACY_ANIMATE, "\\bANIMACY_ANIMATE\\b");
      put(TokenType.ANIMACY_INANIMATE, "\\bANIMACY_INANIMATE\\b");
    }});

    // Underscore is illegal in regex group names.
    GROUP_NAMES_BY_TOKEN_TYPE = Collections.unmodifiableMap(stream(TokenType.values())
        .collect(Collectors.toMap(tokenType -> tokenType, (TokenType tokenType) -> tokenType.name().replace("_", ""))));

    StringBuilder tokenPatterns = new StringBuilder();

    tokenPatterns.append(format("(?<%s>%s)", WHITESPACE_GROUP_NAME, WHITESPACE_GROUP_PATTERN));

    for (Map.Entry<TokenType, String> entry : PATTERNS_BY_TOKEN_TYPE.entrySet())
      tokenPatterns.append(format("|(?<%s>%s)", GROUP_NAMES_BY_TOKEN_TYPE.get(entry.getKey()),
          entry.getValue()));

    // Compile and cache pattern for performance
    TOKEN_PATTERN = Pattern.compile(tokenPatterns.toString());
  }

  /**
   * Given an {@code expression}, scan it into a set of {@link Token} components.
   *
   * @param expression the expression to tokenize
   * @return the tokens that comprise the expression
   * @throws ExpressionEvaluationException if an error occurs while extracting tokens
   */
  public List<@NonNull Token> extractTokens(@NonNull String expression) {
    requireNonNull(expression);

    List<@NonNull Token> tokens = new ArrayList<>();
    Matcher matcher = TOKEN_PATTERN.matcher(expression);
    int position = 0;

    while (position < expression.length()) {
      matcher.region(position, expression.length());

      if (!matcher.lookingAt())
        throw unexpectedContent(expression, position);

      for (TokenType tokenType : PATTERNS_BY_TOKEN_TYPE.keySet()) {
        String group = matcher.group(GROUP_NAMES_BY_TOKEN_TYPE.get(tokenType));

        if (group != null)
          tokens.add(tokenFor(tokenType, group));
      }

      position = matcher.end();
    }

    return tokens;
  }

  @NonNull
  private static ExpressionEvaluationException unexpectedContent(@NonNull String expression, int position) {
    requireNonNull(expression);

    int codePoint = expression.codePointAt(position);
    String errorMessage = format(
        "Unexpected code point U+%04X at index %d while evaluating expression '%s'.",
        codePoint, position, expression);

    // Special message for the common error of using "=" instead of "==" for equality checks.
    if (codePoint == '=')
      errorMessage = format("%s Did you mean '=='?", errorMessage);

    return new ExpressionEvaluationException(errorMessage);
  }

  @NonNull
  private static Token tokenFor(@NonNull TokenType tokenType, @NonNull String symbol) {
    requireNonNull(tokenType);
    requireNonNull(symbol);

    if (tokenType == TokenType.VARIABLE) {
      TokenType exactSymbolTokenType = TokenType.getTokenTypesBySymbol().get(symbol);

      if (exactSymbolTokenType != null)
        return new Token(exactSymbolTokenType);
    }

    return new Token(tokenType, symbol);
  }
}
