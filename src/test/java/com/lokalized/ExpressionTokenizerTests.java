/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2025 Revetware LLC.
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

import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Exercises {@link ExpressionTokenizer}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ExpressionTokenizerTests {
  @Test
  public void basicTokenization() {
    List<Token> tokens = new ExpressionTokenizer().extractTokens("chickenCount == CARDINALITY_ZERO");

    List<Token> expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.VARIABLE, "chickenCount"));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.CARDINALITY_ZERO));

    assertEquals(expectedTokens, tokens);
  }

  @Test
  public void numberTokenization() {
    List<Token> tokens = new ExpressionTokenizer().extractTokens("chickenCount == -12.5");

    List<Token> expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.VARIABLE, "chickenCount"));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.NUMBER, "-12.5"));

    assertEquals(expectedTokens, tokens);
  }

  @Test
  public void shortMalformedTokenization() {
    List<Token> tokens = new ExpressionTokenizer().extractTokens("chickenCount");

    List<Token> expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.VARIABLE, "chickenCount"));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer().extractTokens("==");

    expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.EQUAL_TO));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer().extractTokens("<=");

    expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.LESS_THAN_OR_EQUAL_TO));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer().extractTokens("<");

    expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.LESS_THAN));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer().extractTokens("");

    expectedTokens = new ArrayList<>();

    assertEquals(expectedTokens, tokens);
  }

  @Test
  public void complexTokenization() {
    List<Token> tokens =
        new ExpressionTokenizer()
            .extractTokens("(chickenCount == CARDINALITY_ZERO && rabbitCount == CARDINALITY_ZERO) || (rabbitCount >= CARDINALITY_ONE)");

    List<Token> expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.VARIABLE, "chickenCount"));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.CARDINALITY_ZERO));
    expectedTokens.add(new Token(TokenType.AND));
    expectedTokens.add(new Token(TokenType.VARIABLE, "rabbitCount"));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.CARDINALITY_ZERO));
    expectedTokens.add(new Token(TokenType.GROUP_END));
    expectedTokens.add(new Token(TokenType.OR));
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.VARIABLE, "rabbitCount"));
    expectedTokens.add(new Token(TokenType.GREATER_THAN_OR_EQUAL_TO));
    expectedTokens.add(new Token(TokenType.CARDINALITY_ONE));
    expectedTokens.add(new Token(TokenType.GROUP_END));

    assertEquals(expectedTokens, tokens);
  }

  @Test
  public void malformedTokenization() {
    List<Token> tokens = new ExpressionTokenizer().extractTokens("(((<=)");

    List<Token> expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.LESS_THAN_OR_EQUAL_TO));
    expectedTokens.add(new Token(TokenType.GROUP_END));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer().extractTokens("(<===>=()))(<=(asdf<");

    expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.LESS_THAN_OR_EQUAL_TO));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.GREATER_THAN_OR_EQUAL_TO));
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.GROUP_END));
    expectedTokens.add(new Token(TokenType.GROUP_END));
    expectedTokens.add(new Token(TokenType.GROUP_END));
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.LESS_THAN_OR_EQUAL_TO));
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.VARIABLE, "asdf"));
    expectedTokens.add(new Token(TokenType.LESS_THAN));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer()
        .extractTokens("(chickenCount == CARDINALITY_ZERO && rabbitCount == CARDINALITY_ZERO) || (r||<=abbitCount >= CARDINALITY_ONE)");

    expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.VARIABLE, "chickenCount"));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.CARDINALITY_ZERO));
    expectedTokens.add(new Token(TokenType.AND));
    expectedTokens.add(new Token(TokenType.VARIABLE, "rabbitCount"));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.CARDINALITY_ZERO));
    expectedTokens.add(new Token(TokenType.GROUP_END));
    expectedTokens.add(new Token(TokenType.OR));
    expectedTokens.add(new Token(TokenType.GROUP_START));
    expectedTokens.add(new Token(TokenType.VARIABLE, "r"));
    expectedTokens.add(new Token(TokenType.OR));
    expectedTokens.add(new Token(TokenType.LESS_THAN_OR_EQUAL_TO));
    expectedTokens.add(new Token(TokenType.VARIABLE, "abbitCount"));
    expectedTokens.add(new Token(TokenType.GREATER_THAN_OR_EQUAL_TO));
    expectedTokens.add(new Token(TokenType.CARDINALITY_ONE));
    expectedTokens.add(new Token(TokenType.GROUP_END));

    assertEquals(expectedTokens, tokens);
  }

  @Test
  public void embeddedReservedWordTokenization() {
    List<Token> tokens = new ExpressionTokenizer().extractTokens("justCARDINALITY_ONEtesting == example");

    List<Token> expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.VARIABLE, "justCARDINALITY_ONEtesting"));
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.VARIABLE, "example"));

    assertEquals(expectedTokens, tokens);
  }

  @Test
  public void shortInvalidTokenization() {
    List<Token> tokens = new ExpressionTokenizer().extractTokens("== example");

    List<Token> expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.EQUAL_TO));
    expectedTokens.add(new Token(TokenType.VARIABLE, "example"));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer().extractTokens("example");

    expectedTokens = new ArrayList<>();
    expectedTokens.add(new Token(TokenType.VARIABLE, "example"));

    assertEquals(expectedTokens, tokens);

    tokens = new ExpressionTokenizer().extractTokens("     ");

    expectedTokens = new ArrayList<>();

    assertEquals(expectedTokens, tokens);
  }
}