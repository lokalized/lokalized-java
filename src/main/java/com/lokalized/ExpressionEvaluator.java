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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Evaluator for localized string "alternative" expressions.
 * <p>
 * Rough grammar:
 * <p>
 * <pre>
 * EXPRESSION = OPERAND COMPARISON_OPERATOR OPERAND | "(" EXPRESSION ")" | EXPRESSION BOOLEAN_OPERATOR EXPRESSION ;
 * OPERAND = VARIABLE | LANGUAGE_FORM | NUMBER ;
 * LANGUAGE_FORM = CARDINALITY | ORDINALITY | GENDER ;
 * CARDINALITY = "CARDINALITY_ZERO" | "CARDINALITY_ONE" | "CARDINALITY_TWO" | "CARDINALITY_FEW" | "CARDINALITY_MANY" | "CARDINALITY_OTHER" ;
 * ORDINALITY = "ORDINALITY_ZERO" | "ORDINALITY_ONE" | "ORDINALITY_TWO" | "ORDINALITY_FEW" | "ORDINALITY_MANY" | "ORDINALITY_OTHER" ;
 * GENDER = "MASCULINE" | "FEMININE" | "NEUTER" ;
 * VARIABLE = { alphabetic character | digit } ;
 * BOOLEAN_OPERATOR = "&&" | "||" ;
 * COMPARISON_OPERATOR = "<" | ">" | "<=" | ">=" | "==" | "!=" ;
 * </pre>
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class ExpressionEvaluator {
  @NonNull
  private static final Set<@NonNull TokenType> CARDINALITY_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> ORDINALITY_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> GENDER_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> COMPARISON_OPERATOR_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> BOOLEAN_OPERATOR_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> OPERAND_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> OPERATOR_TOKEN_TYPES;
  @NonNull
  private static final Token TRUE_RESULT_TOKEN;
  @NonNull
  private static final Token FALSE_RESULT_TOKEN;

  @NonNull
  private final ExpressionTokenizer expressionTokenizer;

  static {
    CARDINALITY_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.CARDINALITY_ZERO);
        add(TokenType.CARDINALITY_ONE);
        add(TokenType.CARDINALITY_TWO);
        add(TokenType.CARDINALITY_FEW);
        add(TokenType.CARDINALITY_MANY);
        add(TokenType.CARDINALITY_OTHER);
      }
    });

    ORDINALITY_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.ORDINALITY_ZERO);
        add(TokenType.ORDINALITY_ONE);
        add(TokenType.ORDINALITY_TWO);
        add(TokenType.ORDINALITY_FEW);
        add(TokenType.ORDINALITY_MANY);
        add(TokenType.ORDINALITY_OTHER);
      }
    });

    GENDER_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.MASCULINE);
        add(TokenType.FEMININE);
        add(TokenType.NEUTER);
      }
    });

    COMPARISON_OPERATOR_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.LESS_THAN);
        add(TokenType.LESS_THAN_OR_EQUAL_TO);
        add(TokenType.GREATER_THAN);
        add(TokenType.GREATER_THAN_OR_EQUAL_TO);
        add(TokenType.EQUAL_TO);
        add(TokenType.NOT_EQUAL_TO);
      }
    });

    BOOLEAN_OPERATOR_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.AND);
        add(TokenType.OR);
      }
    });

    Set<@NonNull TokenType> operandTokenTypes = new HashSet<>();
    operandTokenTypes.addAll(CARDINALITY_TOKEN_TYPES);
    operandTokenTypes.addAll(ORDINALITY_TOKEN_TYPES);
    operandTokenTypes.addAll(GENDER_TOKEN_TYPES);
    operandTokenTypes.add(TokenType.VARIABLE);
    operandTokenTypes.add(TokenType.NUMBER);

    OPERAND_TOKEN_TYPES = Collections.unmodifiableSet(operandTokenTypes);

    Set<@NonNull TokenType> operatorTokenTypes = new HashSet<>();
    operatorTokenTypes.addAll(COMPARISON_OPERATOR_TOKEN_TYPES);
    operatorTokenTypes.addAll(BOOLEAN_OPERATOR_TOKEN_TYPES);

    OPERATOR_TOKEN_TYPES = Collections.unmodifiableSet(operatorTokenTypes);

    // TRUE and FALSE are magic tokens used at RPN evaluation time to hold result of binary operator expressions
    TRUE_RESULT_TOKEN = new Token(TokenType.VARIABLE, "TRUE");
    FALSE_RESULT_TOKEN = new Token(TokenType.VARIABLE, "FALSE");
  }

  /**
   * Constructs an expression evaluation with a default tokenizer.
   */
  public ExpressionEvaluator() {
    this(null);
  }

  /**
   * Constructs an expression evaluation with the provided tokenizer.
   * <p>
   * If no tokenizer is provided, a default will be used.
   *
   * @param expressionTokenizer the expression tokenizer to use, may be null
   */
  public ExpressionEvaluator(@Nullable ExpressionTokenizer expressionTokenizer) {
    this.expressionTokenizer = expressionTokenizer == null ? new ExpressionTokenizer() : expressionTokenizer;
  }

  /**
   * Evaluates an expression given a locale.
   * <p>
   * Locale is necessary for plural cardinality and ordinal form evaluation.
   *
   * @param expression the expression to evaluate, not null
   * @param locale     the locale to use for evaluation, not null
   * @return the result of expression evaluation, not null
   * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
   */
  @NonNull
  public Boolean evaluate(@NonNull String expression, @NonNull Locale locale) {
    return evaluate(expression, null, locale);
  }

  /**
   * Evaluates an expression given context and locale.
   * <p>
   * Locale is necessary for plural cardinality and ordinal form evaluation.
   *
   * @param expression the expression to evaluate, not null
   * @param context    the context for the expression, may be null
   * @param locale     the locale to use for evaluation, not null
   * @return the result of expression evaluation, not null
   * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
   */
  @NonNull
  public Boolean evaluate(@NonNull String expression,
                          @Nullable Map<@NonNull String, @Nullable Object> context,
                          @NonNull Locale locale) {
    requireNonNull(expression);
    requireNonNull(locale);

    if (context == null)
      context = Collections.emptyMap();

    List<@NonNull Token> tokens = getExpressionTokenizer().extractTokens(expression);
    tokens = convertTokensToReversePolishNotation(tokens);
    return evaluateReversePolishNotationTokens(tokens, context, locale);
  }

  /**
   * Given an list of tokens in infix notation, convert it to postfix (RPN).
   * <p>
   * The input list is not modified.
   * <p>
   * This implementation uses <a href="https://en.wikipedia.org/wiki/Shunting-yard_algorithm">Dijkstra's shunting-yard algorithm</a>.
   *
   * @param tokens the tokens to convert to RPN, not null
   * @return the input tokens in RPN format, not null
   * @throws ExpressionEvaluationException if an error occurs while converting to RPN
   */
  @NonNull
  protected List<@NonNull Token> convertTokensToReversePolishNotation(@NonNull List<@NonNull Token> tokens) {
    requireNonNull(tokens);

    List<@NonNull Token> outputTokens = new ArrayList<>(tokens.size());
    Deque<Token> operatorStack = new ArrayDeque<>();

    // Perform Dijkstra's shunting-yard algorithm
    for (Token token : tokens) {
      // If the token is a number, then add it to the output queue.
      if (isOperand(token))
        outputTokens.add(token);

        // If the token is an operator, o1, then:
      else if (isOperator(token)) {
        // While there is an operator token, o2, at the top of the stack, and o1's precedence is less than or equal to
        // that of o2, then pop o2 off the stack, onto the output queue
        while (!operatorStack.isEmpty() && isOperator(operatorStack.peek())
            && (precedence(token) <= precedence(operatorStack.peek())))
          outputTokens.add(operatorStack.pop());

        // Push o1 onto the stack.
        operatorStack.push(token);
      }

      // If the token is a left parenthesis, then push it onto the stack.
      else if (token.getTokenType() == TokenType.GROUP_START) {
        operatorStack.push(token);
      }

      // If the token is a right parenthesis:
      else if (token.getTokenType() == TokenType.GROUP_END) {
        // Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue.
        while (!operatorStack.isEmpty() && operatorStack.peek().getTokenType() != TokenType.GROUP_START)
          outputTokens.add(operatorStack.pop());

        // If the stack runs out without finding a left parenthesis, then there are mismatched parentheses.
        if (operatorStack.isEmpty())
          throw new ExpressionEvaluationException(format("Unbalanced %s detected", TokenType.GROUP_END.getSymbol().get()));

        // Pop the left parenthesis from the stack, but not onto the output queue.
        operatorStack.pop();
      }
    }

    // When there are no more tokens to read:

    // While there are still operator tokens in the stack:
    while (!operatorStack.isEmpty()) {
      // If the operator token on the top of the stack is a parenthesis, then there are mismatched parentheses.
      if (!operatorStack.isEmpty()
          && (operatorStack.peek().getTokenType() == TokenType.GROUP_START || operatorStack.peek().getTokenType() == TokenType.GROUP_END))
        throw new ExpressionEvaluationException(format("Unbalanced %s detected", operatorStack.peek().getSymbol()));

      // Pop the operator onto the output queue.
      outputTokens.add(operatorStack.pop());
    }

    return Collections.unmodifiableList(outputTokens);
  }

  /**
   * Given a list of tokens in RPN format, evaluate the expression they comprise against the given context
   * and locale and return a true or false result.
   * <p>
   * RPN evaluation algorithm is outlined at
   * <a href="http://en.wikipedia.org/wiki/Reverse_Polish_notation">http://en.wikipedia.org/wiki/Reverse_Polish_notation</a>.
   *
   * @param tokens  the RPN-format tokens to evaluate, not null
   * @param context the context for the expression, not null
   * @param locale  the locale to use for evaluation, not null
   * @return the result of expression evaluation, not null
   * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
   */
  @NonNull
  protected Boolean evaluateReversePolishNotationTokens(@NonNull List<@NonNull Token> tokens,
                                                        @NonNull Map<@NonNull String, @Nullable Object> context,
                                                        @NonNull Locale locale) {
    requireNonNull(tokens);
    requireNonNull(context);
    requireNonNull(locale);

    ExpressionNode root = buildExpressionTreeFromReversePolishNotationTokens(tokens);
    Token resultToken = evaluateExpressionNode(root, context, locale);

    if (resultToken == TRUE_RESULT_TOKEN)
      return true;
    if (resultToken == FALSE_RESULT_TOKEN)
      return false;

    throw new ExpressionEvaluationException(format("Unexpected final symbol encountered: '%s'", resultToken.getSymbol()));
  }

  @NonNull
  protected ExpressionNode buildExpressionTreeFromReversePolishNotationTokens(@NonNull List<@NonNull Token> tokens) {
    requireNonNull(tokens);

    Deque<ExpressionNode> nodeStack = new ArrayDeque<>();

    for (Token token : tokens) {
      if (isOperand(token)) {
        nodeStack.push(new ExpressionNode(token));
      } else if (isOperator(token)) {
        if (nodeStack.size() < 2)
          throw new ExpressionEvaluationException(format("Insufficient arguments provided for operator '%s' (%s)",
              token.getSymbol(), nodeStack.stream().map(node -> node.getToken().getSymbol()).collect(Collectors.toList())));

        ExpressionNode rightHandOperand = nodeStack.pop();
        ExpressionNode leftHandOperand = nodeStack.pop();
        nodeStack.push(new ExpressionNode(token, leftHandOperand, rightHandOperand));
      } else {
        throw new ExpressionEvaluationException(format("Unexpected symbol encountered: '%s'", token.getSymbol()));
      }
    }

    if (nodeStack.size() == 1)
      return nodeStack.pop();

    throw new ExpressionEvaluationException(format("Unexpected extra values exist on the stack: %s", nodeStack
        .stream().map(node -> node.getToken().getSymbol()).collect(Collectors.toList())));
  }

  @NonNull
  protected Token evaluateExpressionNode(@NonNull ExpressionNode node,
                                         @NonNull Map<@NonNull String, @Nullable Object> context,
                                         @NonNull Locale locale) {
    requireNonNull(node);
    requireNonNull(context);
    requireNonNull(locale);

    Token token = node.getToken();

    if (isOperand(token))
      return token;

    if (!isOperator(token))
      throw new ExpressionEvaluationException(format("Unexpected symbol encountered: '%s'", token.getSymbol()));

    if (isBooleanOperator(token)) {
      Token leftResult = evaluateExpressionNode(node.getLeft(), context, locale);
      boolean leftValue = booleanValue(leftResult);

      if (token.getTokenType() == TokenType.AND && !leftValue)
        return FALSE_RESULT_TOKEN;

      if (token.getTokenType() == TokenType.OR && leftValue)
        return TRUE_RESULT_TOKEN;

      Token rightResult = evaluateExpressionNode(node.getRight(), context, locale);
      boolean rightValue = booleanValue(rightResult);
      boolean result = token.getTokenType() == TokenType.AND ? (leftValue && rightValue) : (leftValue || rightValue);

      return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
    }

    Token leftResult = evaluateExpressionNode(node.getLeft(), context, locale);
    Token rightResult = evaluateExpressionNode(node.getRight(), context, locale);

    return evaluateBinaryOperator(leftResult, token, rightResult, context, locale);
  }

  /**
   * Applies a binary operator to the given left- and right-hand operands.
   * <p>
   * A special {@link #TRUE_RESULT_TOKEN} or {@link #FALSE_RESULT_TOKEN} is returned to indicate the result.
   *
   * @param leftHandOperand  the left-hand-side token, not null
   * @param operator         the binary operator to apply, not null
   * @param rightHandOperand the right-hand-side token, not null
   * @param context          the context for the expression, not null
   * @param locale           the locale to use for evaluation, not null
   * @return the result of the binary operator evaluation (magic TRUE_RESULT_TOKEN or FALSE_RESULT_TOKEN), not null
   * @throws ExpressionEvaluationException if an error occurs while evaluating the operator
   */
  protected Token evaluateBinaryOperator(@NonNull Token leftHandOperand,
                                         @NonNull Token operator,
                                         @NonNull Token rightHandOperand,
                                         @NonNull Map<@NonNull String, @Nullable Object> context,
                                         @NonNull Locale locale) {
    requireNonNull(leftHandOperand);
    requireNonNull(operator);
    requireNonNull(rightHandOperand);
    requireNonNull(context);
    requireNonNull(locale);

    if (isBooleanOperator(operator)) {
      boolean lhsValue = booleanValue(leftHandOperand);
      boolean rhsValue = booleanValue(rightHandOperand);

      if (operator.getTokenType() == TokenType.AND)
        return lhsValue && rhsValue ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      else if (operator.getTokenType() == TokenType.OR)
        return lhsValue || rhsValue ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      else
        throw new ExpressionEvaluationException(format("Expected boolean operator (one of %s) but encountered '%s'",
            BOOLEAN_OPERATOR_TOKEN_TYPES.stream()
                .map(tokenType -> tokenType.getSymbol().get())
                .collect(Collectors.toList()), operator));
    } else if (isComparisonOperator(operator)) {
      OperandType lhsOperandType = operandType(leftHandOperand, context);
      OperandType rhsOperandType = operandType(rightHandOperand, context);

      if (lhsOperandType == OperandType.UNKNOWN || rhsOperandType == OperandType.UNKNOWN)
        throw new ExpressionEvaluationException(format(
            "Unable to evaluate expression '%s %s %s'. Operand types %s and %s are unsupported",
            leftHandOperand.getSymbol(), operator.getSymbol(), rightHandOperand.getSymbol(),
            lhsOperandType.name(), rhsOperandType.name()));

      // Number (operators: any)
      if (lhsOperandType == OperandType.NUMBER && rhsOperandType == OperandType.NUMBER) {
        BigDecimal lhsValue = bigDecimalFromOperand(leftHandOperand, context);
        BigDecimal rhsValue = bigDecimalFromOperand(rightHandOperand, context);
        int comparison = lhsValue.compareTo(rhsValue);
        boolean result = false;

        if (operator.getTokenType() == TokenType.LESS_THAN)
          result = comparison < 0;
        else if (operator.getTokenType() == TokenType.LESS_THAN_OR_EQUAL_TO)
          result = comparison <= 0;
        else if (operator.getTokenType() == TokenType.GREATER_THAN)
          result = comparison > 0;
        else if (operator.getTokenType() == TokenType.GREATER_THAN_OR_EQUAL_TO)
          result = comparison >= 0;
        else if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = comparison == 0;
        else if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = comparison != 0;
        else
          throw new ExpressionEvaluationException(format("Encountered unexpected operator '%s'", operator.getSymbol()));

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Gender (operators: ==, !=)
      if (lhsOperandType == OperandType.GENDER && rhsOperandType == OperandType.GENDER) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing gender comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Gender lhsValue = genderFromOperand(leftHandOperand, context);
        Gender rhsValue = genderFromOperand(rightHandOperand, context);
        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Cardinality (operators: ==, !=)
      if (lhsOperandType == OperandType.CARDINALITY || rhsOperandType == OperandType.CARDINALITY) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing cardinality comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Cardinality lhsValue = cardinalityFromOperand(leftHandOperand, context, locale);
        Cardinality rhsValue = cardinalityFromOperand(rightHandOperand, context, locale);

        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Ordinality (operators: ==, !=)
      if (lhsOperandType == OperandType.ORDINALITY || rhsOperandType == OperandType.ORDINALITY) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing ordinality comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Ordinality lhsValue = ordinalityFromOperand(leftHandOperand, context, locale);
        Ordinality rhsValue = ordinalityFromOperand(rightHandOperand, context, locale);

        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      throw new ExpressionEvaluationException(format(
          "Unable to evaluate expression '%s %s %s'. Operand types %s and %s are incompatible", leftHandOperand.getSymbol(),
          operator.getSymbol(), rightHandOperand.getSymbol(), lhsOperandType.name(), rhsOperandType.name()));
    } else {
      throw new ExpressionEvaluationException(format("Expected operator but encountered '%s'", operator.getSymbol()));
    }
  }

  /**
   * Determines the evaluation precedence of a token.
   *
   * @param token the token to check, not null
   * @return the precedence value for the token, not null
   * @throws ExpressionEvaluationException if the token does not support precedence
   */
  @NonNull
  protected Integer precedence(@NonNull Token token) {
    requireNonNull(token);

    if (COMPARISON_OPERATOR_TOKEN_TYPES.contains(token.getTokenType()))
      return 2;
    if (token.getTokenType() == TokenType.AND)
      return 1;
    if (token.getTokenType() == TokenType.OR)
      return 0;

    throw new ExpressionEvaluationException(format("Cannot determine precedence for '%s'", token.getSymbol()));
  }

  /**
   * Does the specified token represent an operand?
   *
   * @param token the token to check, not null
   * @return whether the token represents an operand, not null
   */
  @NonNull
  protected Boolean isOperand(@NonNull Token token) {
    requireNonNull(token);
    return OPERAND_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent an operator?
   *
   * @param token the token to check, not null
   * @return whether the token represents an operator, not null
   */
  @NonNull
  protected Boolean isOperator(@NonNull Token token) {
    requireNonNull(token);
    return OPERATOR_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a boolean operator?
   *
   * @param token the token to check, not null
   * @return whether the token represents a boolean operator, not null
   */
  @NonNull
  protected Boolean isBooleanOperator(@NonNull Token token) {
    requireNonNull(token);
    return BOOLEAN_OPERATOR_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a comparison operator?
   *
   * @param token the token to check, not null
   * @return whether the token represents a comparison operation, not null
   */
  @NonNull
  protected Boolean isComparisonOperator(@NonNull Token token) {
    requireNonNull(token);
    return COMPARISON_OPERATOR_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a boolean result?
   *
   * @param token the token to check, not null
   * @return whether the token represents a boolean result, not null
   */
  @NonNull
  protected Boolean isBooleanResult(@NonNull Token token) {
    requireNonNull(token);
    return token == TRUE_RESULT_TOKEN || token == FALSE_RESULT_TOKEN;
  }

  /**
   * Does the specified token represent a gender?
   *
   * @param token the token to check, not null
   * @return whether the token represents a gender, not null
   */
  @NonNull
  protected Boolean isGender(@NonNull Token token) {
    requireNonNull(token);
    return GENDER_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a plural cardinality?
   *
   * @param token the token to check, not null
   * @return whether the token represents a plural cardinality, not null
   */
  @NonNull
  protected Boolean isCardinality(@NonNull Token token) {
    requireNonNull(token);
    return CARDINALITY_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a plural ordinality?
   *
   * @param token the token to check, not null
   * @return whether the token represents a plural ordinality, not null
   */
  @NonNull
  protected Boolean isOrdinality(@NonNull Token token) {
    requireNonNull(token);
    return ORDINALITY_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Determines the type of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the type of the operand (or {@link OperandType#UNKNOWN} if indeterminate), not null
   */
  @NonNull
  protected OperandType operandType(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (operand.getTokenType() == TokenType.NUMBER)
      return OperandType.NUMBER;
    if (isCardinality(operand))
      return OperandType.CARDINALITY;
    if (isOrdinality(operand))
      return OperandType.ORDINALITY;
    if (isGender(operand))
      return OperandType.GENDER;

    if (operand.getTokenType() == TokenType.VARIABLE) {
      if (!context.containsKey(operand.getSymbol()))
        throw new ExpressionEvaluationException(format("No value was provided for placeholder '%s'", operand.getSymbol()));

      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value == null)
        throw new ExpressionEvaluationException(format("Placeholder '%s' resolved to null", operand.getSymbol()));
      if (value instanceof Number)
        return OperandType.NUMBER;
      if (value instanceof Cardinality)
        return OperandType.CARDINALITY;
      if (value instanceof Ordinality)
        return OperandType.ORDINALITY;
      if (value instanceof Gender)
        return OperandType.GENDER;
    }

    return OperandType.UNKNOWN;
  }

  /**
   * Determines the decimal value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the decimal value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine decimal value (operand is of invalid type, etc.)
   */
  @NonNull
  protected BigDecimal bigDecimalFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (operand.getTokenType() == TokenType.NUMBER)
      return new BigDecimal(operand.getSymbol());

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Number)
        return NumberUtils.toBigDecimal((Number) value);
    }

    throw new ExpressionEvaluationException(format("Unable to extract numeric value from '%s'", operand.getSymbol()));
  }

  /**
   * Determines the gender value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the gender value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine gender value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Gender genderFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (isGender(operand))
      return Gender.getGendersByName().get(operand.getSymbol());

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Gender)
        return (Gender) value;
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Gender.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the plural cardinality of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @param locale  the locale to use for evaluation, not null
   * @return the plural cardinality of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine plural cardinality value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Cardinality cardinalityFromOperand(@NonNull Token operand,
                                               @NonNull Map<@NonNull String, @Nullable Object> context,
                                               @NonNull Locale locale) {
    requireNonNull(operand);
    requireNonNull(context);
    requireNonNull(locale);

    if (isCardinality(operand))
      return Cardinality.getCardinalitiesByName().get(LocalizedStringUtils.cardinalityNameForLocalizedStringName(operand.getSymbol()));

    if (operand.getTokenType() == TokenType.NUMBER)
      return Cardinality.forNumber(bigDecimalFromOperand(operand, context), locale);

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Cardinality)
        return (Cardinality) value;
      if (value instanceof Number)
        return Cardinality.forNumber((Number) value, locale);
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Cardinality.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the plural ordinality of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @param locale  the locale to use for evaluation, not null
   * @return the plural ordinality of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine plural ordinality value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Ordinality ordinalityFromOperand(@NonNull Token operand,
                                             @NonNull Map<@NonNull String, @Nullable Object> context,
                                             @NonNull Locale locale) {
    requireNonNull(operand);
    requireNonNull(context);
    requireNonNull(locale);

    if (isOrdinality(operand))
      return Ordinality.getOrdinalitiesByName().get(LocalizedStringUtils.ordinalityNameForLocalizedStringName(operand.getSymbol()));

    if (operand.getTokenType() == TokenType.NUMBER)
      return Ordinality.forNumber(bigDecimalFromOperand(operand, context), locale);

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Ordinality)
        return (Ordinality) value;
      if (value instanceof Number)
        return Ordinality.forNumber((Number) value, locale);
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Ordinality.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the boolean value of a token.
   *
   * @param token the token to examine, not null
   * @return the boolean value of the token, not null
   * @throws ExpressionEvaluationException if unable to determine boolean value (token is of invalid type, etc.)
   */
  @NonNull
  protected Boolean booleanValue(@NonNull Token token) {
    requireNonNull(token);

    if (token == TRUE_RESULT_TOKEN)
      return true;
    if (token == FALSE_RESULT_TOKEN)
      return false;

    throw new ExpressionEvaluationException(format("Expected boolean but encountered '%s'", token.getSymbol()));
  }

  /**
   * Gets the expression tokenizer.
   *
   * @return the expression tokenizer, not null
   */
  @NonNull
  protected ExpressionTokenizer getExpressionTokenizer() {
    return expressionTokenizer;
  }

  protected void validateReversePolishNotationTokens(@NonNull List<@NonNull Token> tokens) {
    requireNonNull(tokens);

    Deque<Token> valueStack = new ArrayDeque<>();

    for (Token token : tokens) {
      if (isOperand(token)) {
        valueStack.push(token);
      } else if (isOperator(token)) {
        if (valueStack.size() < 2)
          throw new ExpressionEvaluationException(format("Insufficient arguments provided for operator '%s' (%s)",
              token.getSymbol(), valueStack.stream().map(operand -> operand.getSymbol()).collect(Collectors.toList())));

        valueStack.pop();
        valueStack.pop();
        valueStack.push(TRUE_RESULT_TOKEN);
      } else {
        throw new ExpressionEvaluationException(format("Unexpected symbol encountered: '%s'", token.getSymbol()));
      }
    }

    if (valueStack.size() == 1) {
      Token resultToken = valueStack.pop();

      if (resultToken == TRUE_RESULT_TOKEN || resultToken == FALSE_RESULT_TOKEN)
        return;

      throw new ExpressionEvaluationException(format("Unexpected final symbol encountered: '%s'", resultToken.getSymbol()));
    }

    throw new ExpressionEvaluationException(format("Unexpected extra values exist on the stack: %s", valueStack
        .stream().map(operand -> operand.getSymbol()).collect(Collectors.toList())));
  }

  /**
   * Expression operand types.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  protected enum OperandType {
    NUMBER, GENDER, CARDINALITY, ORDINALITY, UNKNOWN;
  }

  protected static final class ExpressionNode {
    @NonNull
    private final Token token;
    @Nullable
    private final ExpressionNode left;
    @Nullable
    private final ExpressionNode right;

    private ExpressionNode(@NonNull Token token) {
      this(token, null, null);
    }

    private ExpressionNode(@NonNull Token token, @Nullable ExpressionNode left, @Nullable ExpressionNode right) {
      requireNonNull(token);
      this.token = token;
      this.left = left;
      this.right = right;
    }

    @NonNull
    private Token getToken() {
      return token;
    }

    @NonNull
    private ExpressionNode getLeft() {
      if (left == null)
        throw new ExpressionEvaluationException(format("Missing left operand for '%s'", token.getSymbol()));

      return left;
    }

    @NonNull
    private ExpressionNode getRight() {
      if (right == null)
        throw new ExpressionEvaluationException(format("Missing right operand for '%s'", token.getSymbol()));

      return right;
    }
  }
}
