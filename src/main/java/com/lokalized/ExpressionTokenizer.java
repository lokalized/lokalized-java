/*
 * Copyright 2017 Product Mog LLC.
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


import javax.annotation.Nonnull;
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
  @Nonnull
  private static final Map<TokenType, String> PATTERNS_BY_TOKEN_TYPE;
  @Nonnull
  private static final Map<TokenType, String> GROUP_NAMES_BY_TOKEN_TYPE;
  @Nonnull
  private static final Pattern TOKEN_PATTERN;
  @Nonnull
  private static final String WHITESPACE_GROUP_NAME = "WHITESPACE";
  @Nonnull
  private static final String WHITESPACE_GROUP_PATTERN = "\\p{Space}";
  @Nonnull
  private static final String ERROR_GROUP_NAME = "ERROR";
  @Nonnull
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
      put(TokenType.CARDINALITY_ZERO, "CARDINALITY_ZERO");
      put(TokenType.CARDINALITY_ONE, "CARDINALITY_ONE");
      put(TokenType.CARDINALITY_TWO, "CARDINALITY_TWO");
      put(TokenType.CARDINALITY_FEW, "CARDINALITY_FEW");
      put(TokenType.CARDINALITY_MANY, "CARDINALITY_MANY");
      put(TokenType.CARDINALITY_OTHER, "CARDINALITY_OTHER");
      put(TokenType.MASCULINE, "MASCULINE");
      put(TokenType.FEMININE, "FEMININE");
      put(TokenType.NEUTER, "NEUTER");
      put(TokenType.NUMBER, "-?(0|([1-9]\\d*))(\\.\\d+)?");
      put(TokenType.VARIABLE, "\\p{Alnum}+");
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
  public List<Token> extractTokens(@Nonnull String expression) {
    requireNonNull(expression);

    List<Token> tokens = new ArrayList<>();
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
        if (errorGroup.startsWith("= "))
          errorMessage = format("%s Did you mean '=%s'?", errorMessage, errorGroup);

        throw new ExpressionEvaluationException(errorMessage);
      }
    }

    return tokens;
  }
}