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

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

/**
 * Breaks an expression into its {@link Token} components.
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
  private static final String WHITESPACE_GROUP_PATTERN = "\\p{Space}";
  @NonNull
  private static final String ERROR_GROUP_NAME = "ERROR";
  @NonNull
  private static final String ERROR_GROUP_PATTERN = ".+";

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
      put(TokenType.FORMALITY_INFORMAL, "\\bFORMALITY_INFORMAL\\b");
      put(TokenType.FORMALITY_FORMAL, "\\bFORMALITY_FORMAL\\b");
      put(TokenType.FORMALITY_HONORIFIC, "\\bFORMALITY_HONORIFIC\\b");
      put(TokenType.CLUSIVITY_INCLUSIVE, "\\bCLUSIVITY_INCLUSIVE\\b");
      put(TokenType.CLUSIVITY_EXCLUSIVE, "\\bCLUSIVITY_EXCLUSIVE\\b");
      put(TokenType.ANIMACY_ANIMATE, "\\bANIMACY_ANIMATE\\b");
      put(TokenType.ANIMACY_INANIMATE, "\\bANIMACY_INANIMATE\\b");
      put(TokenType.NUMBER, "[+-]?((\\d+\\.\\d*)|(\\.\\d+)|(\\d+))([eE][+-]?\\d+)?");
      put(TokenType.VARIABLE, "[\\p{Alpha}_][\\p{Alnum}_-]*");
    }});

    // Underscore is illegal in regex group names.
    GROUP_NAMES_BY_TOKEN_TYPE = Collections.unmodifiableMap(stream(TokenType.values())
        .collect(Collectors.toMap(tokenType -> tokenType, (TokenType tokenType) -> tokenType.name().replace("_", ""))));

    StringBuilder tokenPatterns = new StringBuilder();

    tokenPatterns.append(format("(?<%s>%s)", WHITESPACE_GROUP_NAME, WHITESPACE_GROUP_PATTERN));

    for (TokenType tokenType : PATTERNS_BY_TOKEN_TYPE.keySet())
      tokenPatterns.append(format("|(?<%s>%s)", GROUP_NAMES_BY_TOKEN_TYPE.get(tokenType),
          PATTERNS_BY_TOKEN_TYPE.get(tokenType)));

    tokenPatterns.append(format("|(?<%s>%s)", ERROR_GROUP_NAME, ERROR_GROUP_PATTERN));

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

    while (matcher.find()) {
      for (TokenType tokenType : TokenType.values()) {
        String group = matcher.group(GROUP_NAMES_BY_TOKEN_TYPE.get(tokenType));

        if (group != null)
          tokens.add(new Token(tokenType, group));
      }

      if (matcher.group(WHITESPACE_GROUP_NAME) != null)
        continue;

      if (matcher.group(ERROR_GROUP_NAME) != null) {
        String errorGroup = matcher.group(ERROR_GROUP_NAME);

        String errorMessage =
            format("Unexpected content '%s' encountered while evaluating expression '%s'.", errorGroup, expression);

        // Special message for common error of using "=" instead of "==" for equality checks
        if (errorGroup.startsWith("="))
          errorMessage = format("%s Did you mean '=%s'?", errorMessage, errorGroup);

        throw new ExpressionEvaluationException(errorMessage);
      }
    }

    return tokens;
  }
}
