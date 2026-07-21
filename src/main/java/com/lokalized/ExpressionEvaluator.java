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

import javax.annotation.concurrent.Immutable;
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

import static com.lokalized.Diagnostics.format;
import static java.util.Objects.requireNonNull;

/**
 * Evaluator for localized string "alternative" expressions.
 * <p>
 * Expression grammar:
 * <p>
 * <pre>
 * EXPRESSION = OR_EXPRESSION ;
 * OR_EXPRESSION = AND_EXPRESSION { "||" AND_EXPRESSION } ;
 * AND_EXPRESSION = PRIMARY_EXPRESSION { "&&" PRIMARY_EXPRESSION } ;
 * PRIMARY_EXPRESSION = COMPARISON | "(" EXPRESSION ")" ;
 * COMPARISON = OPERAND COMPARISON_OPERATOR OPERAND ;
 * OPERAND = VARIABLE | LANGUAGE_FORM | NUMBER ;
 * LANGUAGE_FORM = CARDINALITY | ORDINALITY | GENDER | GRAMMATICAL_CASE
 *               | DEFINITENESS | CLASSIFIER | FORMALITY | CLUSIVITY
 *               | ANIMACY | PHONETIC ;
 * CARDINALITY = "CARDINALITY_ZERO" | "CARDINALITY_ONE" | "CARDINALITY_TWO"
 *             | "CARDINALITY_FEW" | "CARDINALITY_MANY" | "CARDINALITY_OTHER" ;
 * ORDINALITY = "ORDINALITY_ZERO" | "ORDINALITY_ONE" | "ORDINALITY_TWO"
 *            | "ORDINALITY_FEW" | "ORDINALITY_MANY" | "ORDINALITY_OTHER" ;
 * GENDER = "GENDER_MASCULINE" | "GENDER_FEMININE"
 *        | "GENDER_COMMON" | "GENDER_NEUTER" ;
 * GRAMMATICAL_CASE = "CASE_NOMINATIVE" | "CASE_ACCUSATIVE"
 *                  | "CASE_GENITIVE" | "CASE_DATIVE" | "CASE_INSTRUMENTAL"
 *                  | "CASE_LOCATIVE" | "CASE_PREPOSITIONAL"
 *                  | "CASE_VOCATIVE" | "CASE_ABLATIVE" ;
 * DEFINITENESS = "DEFINITENESS_DEFINITE" | "DEFINITENESS_INDEFINITE"
 *              | "DEFINITENESS_CONSTRUCT" ;
 * CLASSIFIER = "CLASSIFIER_GENERAL" | "CLASSIFIER_PERSON" | "CLASSIFIER_ANIMAL"
 *            | "CLASSIFIER_LONG_THIN" | "CLASSIFIER_FLAT" | "CLASSIFIER_BOUND"
 *            | "CLASSIFIER_MACHINE" | "CLASSIFIER_VEHICLE" ;
 * FORMALITY = "FORMALITY_CASUAL" | "FORMALITY_INFORMAL" | "FORMALITY_FORMAL"
 *           | "FORMALITY_HUMBLE" | "FORMALITY_HONORIFIC" ;
 * CLUSIVITY = "CLUSIVITY_INCLUSIVE" | "CLUSIVITY_EXCLUSIVE" ;
 * ANIMACY = "ANIMACY_ANIMATE" | "ANIMACY_INANIMATE" ;
 * PHONETIC = "PHONETIC_VOWEL" | "PHONETIC_CONSONANT"
 *          | "PHONETIC_H_SILENT" | "PHONETIC_H_ASPIRATED"
 *          | "PHONETIC_S_IMPURE" | "PHONETIC_Z" | "PHONETIC_GN" | "PHONETIC_PS"
 *          | "PHONETIC_PN" | "PHONETIC_X"
 *          | "PHONETIC_GLIDE_Y" | "PHONETIC_GLIDE_W"
 *          | "PHONETIC_STRESSED_A"
 *          | "PHONETIC_SOLAR" | "PHONETIC_LUNAR"
 *          | "PHONETIC_OTHER" ;
 * NUMBER = [ SIGN ],
 *          ( DIGITS, [ ".", { DIGIT } ] | ".", DIGITS ),
 *          [ EXPONENT ] ;
 * EXPONENT = ( "e" | "E" ), [ SIGN ], DIGITS ;
 * SIGN = "+" | "-" ;
 * DIGITS = DIGIT, { DIGIT } ;
 * DIGIT = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;
 * VARIABLE = ( Unicode letter | "_" )
 *            { Unicode letter | Unicode number | Unicode combining mark
 *            | "_" | "-" } ;
 * COMPARISON_OPERATOR = "&lt;" | "&gt;" | "&lt;=" | "&gt;=" | "==" | "!=" ;
 * </pre>
 * Comparison operators bind more tightly than {@code &&}, which binds more tightly than {@code ||}. Parentheses
 * override that precedence. ASCII space, horizontal tab, carriage return, line feed, and form feed are ignored
 * between tokens; other Unicode whitespace and separator characters are rejected.
 * <p>
 * Caller-supplied {@link CharSequence} variable values are phonetic inputs, not string literals. They may be compared
 * with an explicit {@code PHONETIC_*} constant or {@link Phonetic} value, but two raw character sequences cannot be
 * compared for textual equality.
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
  private static final Set<@NonNull TokenType> GRAMMATICAL_CASE_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> DEFINITENESS_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> CLASSIFIER_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> FORMALITY_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> CLUSIVITY_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> ANIMACY_TOKEN_TYPES;
  @NonNull
  private static final Set<@NonNull TokenType> PHONETIC_TOKEN_TYPES;
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
  @Nullable
  private final PhoneticResolver phoneticResolver;
  @NonNull
  private final TranslationRuntimeLimits runtimeLimits;

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
        add(TokenType.GENDER_MASCULINE);
        add(TokenType.GENDER_FEMININE);
        add(TokenType.GENDER_COMMON);
        add(TokenType.GENDER_NEUTER);
      }
    });

    GRAMMATICAL_CASE_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.CASE_NOMINATIVE);
        add(TokenType.CASE_ACCUSATIVE);
        add(TokenType.CASE_GENITIVE);
        add(TokenType.CASE_DATIVE);
        add(TokenType.CASE_INSTRUMENTAL);
        add(TokenType.CASE_LOCATIVE);
        add(TokenType.CASE_PREPOSITIONAL);
        add(TokenType.CASE_VOCATIVE);
        add(TokenType.CASE_ABLATIVE);
      }
    });

    DEFINITENESS_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.DEFINITENESS_DEFINITE);
        add(TokenType.DEFINITENESS_INDEFINITE);
        add(TokenType.DEFINITENESS_CONSTRUCT);
      }
    });

    CLASSIFIER_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.CLASSIFIER_GENERAL);
        add(TokenType.CLASSIFIER_PERSON);
        add(TokenType.CLASSIFIER_ANIMAL);
        add(TokenType.CLASSIFIER_LONG_THIN);
        add(TokenType.CLASSIFIER_FLAT);
        add(TokenType.CLASSIFIER_BOUND);
        add(TokenType.CLASSIFIER_MACHINE);
        add(TokenType.CLASSIFIER_VEHICLE);
      }
    });

    FORMALITY_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.FORMALITY_CASUAL);
        add(TokenType.FORMALITY_INFORMAL);
        add(TokenType.FORMALITY_FORMAL);
        add(TokenType.FORMALITY_HUMBLE);
        add(TokenType.FORMALITY_HONORIFIC);
      }
    });

    CLUSIVITY_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.CLUSIVITY_INCLUSIVE);
        add(TokenType.CLUSIVITY_EXCLUSIVE);
      }
    });

    ANIMACY_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.ANIMACY_ANIMATE);
        add(TokenType.ANIMACY_INANIMATE);
      }
    });

    PHONETIC_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
      {
        add(TokenType.PHONETIC_VOWEL);
        add(TokenType.PHONETIC_CONSONANT);
        add(TokenType.PHONETIC_OTHER);
        add(TokenType.PHONETIC_H_SILENT);
        add(TokenType.PHONETIC_H_ASPIRATED);
        add(TokenType.PHONETIC_S_IMPURE);
        add(TokenType.PHONETIC_Z);
        add(TokenType.PHONETIC_GN);
        add(TokenType.PHONETIC_PS);
        add(TokenType.PHONETIC_PN);
        add(TokenType.PHONETIC_X);
        add(TokenType.PHONETIC_GLIDE_Y);
        add(TokenType.PHONETIC_GLIDE_W);
        add(TokenType.PHONETIC_STRESSED_A);
        add(TokenType.PHONETIC_SOLAR);
        add(TokenType.PHONETIC_LUNAR);
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
    operandTokenTypes.addAll(GRAMMATICAL_CASE_TOKEN_TYPES);
    operandTokenTypes.addAll(DEFINITENESS_TOKEN_TYPES);
    operandTokenTypes.addAll(CLASSIFIER_TOKEN_TYPES);
    operandTokenTypes.addAll(FORMALITY_TOKEN_TYPES);
    operandTokenTypes.addAll(CLUSIVITY_TOKEN_TYPES);
    operandTokenTypes.addAll(ANIMACY_TOKEN_TYPES);
    operandTokenTypes.addAll(PHONETIC_TOKEN_TYPES);
    operandTokenTypes.add(TokenType.VARIABLE);
    operandTokenTypes.add(TokenType.NUMBER);

    OPERAND_TOKEN_TYPES = Collections.unmodifiableSet(operandTokenTypes);

    Set<@NonNull TokenType> operatorTokenTypes = new HashSet<>();
    operatorTokenTypes.addAll(COMPARISON_OPERATOR_TOKEN_TYPES);
    operatorTokenTypes.addAll(BOOLEAN_OPERATOR_TOKEN_TYPES);

    OPERATOR_TOKEN_TYPES = Collections.unmodifiableSet(operatorTokenTypes);

    TRUE_RESULT_TOKEN = new Token(TokenType.BOOLEAN_RESULT, "true");
    FALSE_RESULT_TOKEN = new Token(TokenType.BOOLEAN_RESULT, "false");
  }

  /**
   * Constructs an expression evaluation with a default tokenizer.
   */
  public ExpressionEvaluator() {
    this(null, null, null);
  }

  /**
   * Constructs an expression evaluation with the provided tokenizer.
   * <p>
   * If no tokenizer is provided, a default will be used.
   *
   * @param expressionTokenizer the expression tokenizer to use, may be null
   */
  public ExpressionEvaluator(@Nullable ExpressionTokenizer expressionTokenizer) {
    this(expressionTokenizer, null, null);
  }

  /**
   * Constructs an expression evaluation with the provided tokenizer and phonetic resolver.
   * <p>
   * If no tokenizer is provided, a default will be used.
   *
   * @param expressionTokenizer the expression tokenizer to use, may be null
   * @param phoneticResolver    the phonetic resolver to use, may be null
   */
  public ExpressionEvaluator(@Nullable ExpressionTokenizer expressionTokenizer,
                             @Nullable PhoneticResolver phoneticResolver) {
    this(expressionTokenizer, phoneticResolver, null);
  }

  /**
   * Constructs an expression evaluator with the provided collaborators and safety limits.
   * <p>
   * Null arguments select their library defaults.
   *
   * @param expressionTokenizer tokenizer to use, may be null
   * @param phoneticResolver phonetic resolver to use, may be null
   * @param runtimeLimits runtime safety limits, may be null
   * @since 3.0.0
   */
  public ExpressionEvaluator(@Nullable ExpressionTokenizer expressionTokenizer,
                             @Nullable PhoneticResolver phoneticResolver,
                             @Nullable TranslationRuntimeLimits runtimeLimits) {
    this.expressionTokenizer = expressionTokenizer == null ? new ExpressionTokenizer() : expressionTokenizer;
    this.phoneticResolver = phoneticResolver;
    this.runtimeLimits = runtimeLimits == null ? TranslationRuntimeLimits.defaults() : runtimeLimits;
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

    return evaluateCompiledExpression(compile(expression), context, locale);
  }

  /**
   * Compiles an expression into an immutable tree which may be evaluated repeatedly.
   *
   * @param expression expression source, not null
   * @return the compiled expression, not null
   * @throws ExpressionEvaluationException if the expression is invalid or exceeds configured limits
   */
  @NonNull
  protected CompiledExpression compile(@NonNull String expression) {
    requireNonNull(expression);

    List<@NonNull Token> tokens = parseAndValidateExpressionTokens(expression);
    return new CompiledExpression(expression, buildExpressionTreeFromReversePolishNotationTokens(tokens));
  }

  /**
   * Evaluates a previously compiled expression.
   *
   * @param compiledExpression compiled expression, not null
   * @param context caller-supplied expression context, not null
   * @param locale locale used for locale-sensitive language forms, not null
   * @return the expression result, not null
   * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
   */
  @NonNull
  protected Boolean evaluateCompiledExpression(@NonNull CompiledExpression compiledExpression,
                                               @NonNull Map<@NonNull String, @Nullable Object> context,
                                               @NonNull Locale locale) {
    requireNonNull(compiledExpression);
    requireNonNull(context);
    requireNonNull(locale);

    Token resultToken = evaluateExpressionNode(compiledExpression.getRoot(), context, locale);

    if (resultToken == TRUE_RESULT_TOKEN)
      return true;
    if (resultToken == FALSE_RESULT_TOKEN)
      return false;

    throw new ExpressionEvaluationException(format("Unexpected final symbol encountered while evaluating '%s': '%s'",
        compiledExpression.getExpression(), resultToken.getSymbol()));
  }

  @NonNull
  protected List<@NonNull Token> parseAndValidateExpressionTokens(@NonNull String expression) {
    requireNonNull(expression);

    validateExpressionSourceLength(expression);
    List<@NonNull Token> tokens = getExpressionTokenizer().extractTokens(expression);
    validateNumericLiteralTokens(tokens);
    tokens = convertTokensToReversePolishNotation(tokens);

    try {
      validateReversePolishNotationTokens(tokens);
    } catch (ExpressionEvaluationException e) {
      throw new ExpressionEvaluationException(format("Invalid expression '%s': %s", expression, e.getMessage()), e);
    }

    return tokens;
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
    validateInfixTokens(tokens);

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
   * An internal boolean-result token is returned to indicate the result.
   *
   * @param leftHandOperand  the left-hand-side token, not null
   * @param operator         the binary operator to apply, not null
   * @param rightHandOperand the right-hand-side token, not null
   * @param context          the context for the expression, not null
   * @param locale           the locale to use for evaluation, not null
   * @return the result of the binary operator evaluation, not null
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

      boolean lhsIsCallerSuppliedCharacterSequence = isCallerSuppliedCharacterSequence(leftHandOperand, context);
      boolean rhsIsCallerSuppliedCharacterSequence = isCallerSuppliedCharacterSequence(rightHandOperand, context);

      if (lhsOperandType == OperandType.PHONETIC && rhsOperandType == OperandType.PHONETIC
          && lhsIsCallerSuppliedCharacterSequence && rhsIsCallerSuppliedCharacterSequence) {
        if (operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          throw new ExpressionEvaluationException(format(
              "Raw CharSequence placeholders '%s' and '%s' cannot be compared with '%s': " +
                  "expressions do not support textual equality. Compare phonetic input with a PHONETIC_* constant " +
                  "or an explicit %s value instead",
              leftHandOperand.getSymbol(), rightHandOperand.getSymbol(), operator.getSymbol(),
              Phonetic.class.getSimpleName()));

        throw new ExpressionEvaluationException(format(
            "Raw CharSequence placeholders '%s' and '%s' cannot be compared with '%s': " +
                "expressions do not support textual ordering. Use numeric operands for ordering, or compare " +
                "phonetic input with a PHONETIC_* constant or an explicit %s value using '==' or '!='",
            leftHandOperand.getSymbol(), rightHandOperand.getSymbol(), operator.getSymbol(),
            Phonetic.class.getSimpleName()));
      }

      Token characterSequenceOperand = null;

      if (lhsOperandType == OperandType.NUMBER && rhsIsCallerSuppliedCharacterSequence)
        characterSequenceOperand = rightHandOperand;
      else if (rhsOperandType == OperandType.NUMBER && lhsIsCallerSuppliedCharacterSequence)
        characterSequenceOperand = leftHandOperand;

      if (characterSequenceOperand != null)
        throw new ExpressionEvaluationException(format(
            "Numeric comparison '%s %s %s' requires numeric operands supplied as %s or %s values, " +
                "but placeholder '%s' resolved to %s",
            leftHandOperand.getSymbol(), operator.getSymbol(), rightHandOperand.getSymbol(),
            Number.class.getSimpleName(), PluralOperands.class.getSimpleName(), characterSequenceOperand.getSymbol(),
            runtimeTypeName(characterSequenceOperand, context)));

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

      // Grammatical case (operators: ==, !=)
      if (lhsOperandType == OperandType.GRAMMATICAL_CASE && rhsOperandType == OperandType.GRAMMATICAL_CASE) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing grammatical case comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        GrammaticalCase lhsValue = grammaticalCaseFromOperand(leftHandOperand, context);
        GrammaticalCase rhsValue = grammaticalCaseFromOperand(rightHandOperand, context);
        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Definiteness (operators: ==, !=)
      if (lhsOperandType == OperandType.DEFINITENESS && rhsOperandType == OperandType.DEFINITENESS) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing definiteness comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Definiteness lhsValue = definitenessFromOperand(leftHandOperand, context);
        Definiteness rhsValue = definitenessFromOperand(rightHandOperand, context);
        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Classifier (operators: ==, !=)
      if (lhsOperandType == OperandType.CLASSIFIER && rhsOperandType == OperandType.CLASSIFIER) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing classifier comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Classifier lhsValue = classifierFromOperand(leftHandOperand, context);
        Classifier rhsValue = classifierFromOperand(rightHandOperand, context);
        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Formality (operators: ==, !=)
      if (lhsOperandType == OperandType.FORMALITY && rhsOperandType == OperandType.FORMALITY) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing formality comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Formality lhsValue = formalityFromOperand(leftHandOperand, context);
        Formality rhsValue = formalityFromOperand(rightHandOperand, context);
        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Clusivity (operators: ==, !=)
      if (lhsOperandType == OperandType.CLUSIVITY && rhsOperandType == OperandType.CLUSIVITY) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing clusivity comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Clusivity lhsValue = clusivityFromOperand(leftHandOperand, context);
        Clusivity rhsValue = clusivityFromOperand(rightHandOperand, context);
        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      // Animacy (operators: ==, !=)
      if (lhsOperandType == OperandType.ANIMACY && rhsOperandType == OperandType.ANIMACY) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing animacy comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Animacy lhsValue = animacyFromOperand(leftHandOperand, context);
        Animacy rhsValue = animacyFromOperand(rightHandOperand, context);
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

      // Phonetic (operators: ==, !=)
      if (lhsOperandType == OperandType.PHONETIC || rhsOperandType == OperandType.PHONETIC) {
        if (!(operator.getTokenType() == TokenType.EQUAL_TO || operator.getTokenType() == TokenType.NOT_EQUAL_TO))
          throw new ExpressionEvaluationException(
              format(
                  "You may only use the '%s' and '%s' operators when performing phonetic comparisons. Offending comparison: '%s %s %s'",
                  TokenType.EQUAL_TO.getSymbol().get(), TokenType.NOT_EQUAL_TO.getSymbol().get(), leftHandOperand.getSymbol(),
                  operator.getSymbol(), rightHandOperand.getSymbol()));

        Phonetic lhsValue = phoneticFromOperand(leftHandOperand, context, locale);
        Phonetic rhsValue = phoneticFromOperand(rightHandOperand, context, locale);
        boolean result = false;

        if (operator.getTokenType() == TokenType.EQUAL_TO)
          result = lhsValue == rhsValue;
        if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
          result = lhsValue != rhsValue;

        return result ? TRUE_RESULT_TOKEN : FALSE_RESULT_TOKEN;
      }

      throw new ExpressionEvaluationException(format(
          "Unable to evaluate expression '%s %s %s'. Operand runtime types %s and %s are incompatible",
          leftHandOperand.getSymbol(), operator.getSymbol(), rightHandOperand.getSymbol(),
          runtimeTypeName(leftHandOperand, context), runtimeTypeName(rightHandOperand, context)));
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
    return token.getTokenType() == TokenType.BOOLEAN_RESULT;
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
   * Does the specified token represent a grammatical case?
   *
   * @param token the token to check, not null
   * @return whether the token represents a grammatical case, not null
   */
  @NonNull
  protected Boolean isGrammaticalCase(@NonNull Token token) {
    requireNonNull(token);
    return GRAMMATICAL_CASE_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent definiteness?
   *
   * @param token the token to check, not null
   * @return whether the token represents definiteness, not null
   */
  @NonNull
  protected Boolean isDefiniteness(@NonNull Token token) {
    requireNonNull(token);
    return DEFINITENESS_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a classifier?
   *
   * @param token the token to check, not null
   * @return whether the token represents a classifier, not null
   */
  @NonNull
  protected Boolean isClassifier(@NonNull Token token) {
    requireNonNull(token);
    return CLASSIFIER_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a formality?
   *
   * @param token the token to check, not null
   * @return whether the token represents a formality, not null
   */
  @NonNull
  protected Boolean isFormality(@NonNull Token token) {
    requireNonNull(token);
    return FORMALITY_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent a clusivity?
   *
   * @param token the token to check, not null
   * @return whether the token represents a clusivity, not null
   */
  @NonNull
  protected Boolean isClusivity(@NonNull Token token) {
    requireNonNull(token);
    return CLUSIVITY_TOKEN_TYPES.contains(token.getTokenType());
  }

  /**
   * Does the specified token represent animacy?
   *
   * @param token the token to check, not null
   * @return whether the token represents animacy, not null
   */
  @NonNull
  protected Boolean isAnimacy(@NonNull Token token) {
    requireNonNull(token);
    return ANIMACY_TOKEN_TYPES.contains(token.getTokenType());
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
   * Does the specified token represent a phonetic category?
   *
   * @param token the token to check, not null
   * @return whether the token represents a phonetic category, not null
   */
  @NonNull
  protected Boolean isPhonetic(@NonNull Token token) {
    requireNonNull(token);
    return PHONETIC_TOKEN_TYPES.contains(token.getTokenType());
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

    if (isBooleanResult(operand))
      return OperandType.BOOLEAN;
    if (operand.getTokenType() == TokenType.NUMBER)
      return OperandType.NUMBER;
    if (isCardinality(operand))
      return OperandType.CARDINALITY;
    if (isOrdinality(operand))
      return OperandType.ORDINALITY;
    if (isGender(operand))
      return OperandType.GENDER;
    if (isGrammaticalCase(operand))
      return OperandType.GRAMMATICAL_CASE;
    if (isDefiniteness(operand))
      return OperandType.DEFINITENESS;
    if (isClassifier(operand))
      return OperandType.CLASSIFIER;
    if (isFormality(operand))
      return OperandType.FORMALITY;
    if (isClusivity(operand))
      return OperandType.CLUSIVITY;
    if (isAnimacy(operand))
      return OperandType.ANIMACY;
    if (isPhonetic(operand))
      return OperandType.PHONETIC;

    if (operand.getTokenType() == TokenType.VARIABLE) {
      if (!context.containsKey(operand.getSymbol()))
        throw new ExpressionEvaluationException(format("No value was provided for placeholder '%s'", operand.getSymbol()));

      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value == null)
        throw new ExpressionEvaluationException(format("Placeholder '%s' resolved to null", operand.getSymbol()));
      if (value instanceof PluralOperands)
        return OperandType.NUMBER;
      if (value instanceof Number)
        return OperandType.NUMBER;
      if (value instanceof Cardinality)
        return OperandType.CARDINALITY;
      if (value instanceof Ordinality)
        return OperandType.ORDINALITY;
      if (value instanceof Gender)
        return OperandType.GENDER;
      if (value instanceof GrammaticalCase)
        return OperandType.GRAMMATICAL_CASE;
      if (value instanceof Definiteness)
        return OperandType.DEFINITENESS;
      if (value instanceof Classifier)
        return OperandType.CLASSIFIER;
      if (value instanceof Formality)
        return OperandType.FORMALITY;
      if (value instanceof Clusivity)
        return OperandType.CLUSIVITY;
      if (value instanceof Animacy)
        return OperandType.ANIMACY;
      if (value instanceof Phonetic)
        return OperandType.PHONETIC;
      if (value instanceof CharSequence)
        return OperandType.PHONETIC;
    }

    return OperandType.UNKNOWN;
  }

  @NonNull
  private String runtimeTypeName(@NonNull Token operand,
                                 @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (operand.getTokenType() != TokenType.VARIABLE) {
      switch (operandType(operand, context)) {
        case NUMBER:
          return Number.class.getSimpleName();
        case BOOLEAN:
          return Boolean.class.getSimpleName();
        case GENDER:
          return Gender.class.getSimpleName();
        case GRAMMATICAL_CASE:
          return GrammaticalCase.class.getSimpleName();
        case DEFINITENESS:
          return Definiteness.class.getSimpleName();
        case CLASSIFIER:
          return Classifier.class.getSimpleName();
        case FORMALITY:
          return Formality.class.getSimpleName();
        case CLUSIVITY:
          return Clusivity.class.getSimpleName();
        case ANIMACY:
          return Animacy.class.getSimpleName();
        case CARDINALITY:
          return Cardinality.class.getSimpleName();
        case ORDINALITY:
          return Ordinality.class.getSimpleName();
        case PHONETIC:
          return Phonetic.class.getSimpleName();
        case UNKNOWN:
        default:
          return operand.getTokenType().name();
      }
    }

    Object value = context.get(operand.getSymbol());

    if (value instanceof Optional)
      value = ((Optional<?>) value).orElse(null);

    return value == null ? "null" : value.getClass().getSimpleName();
  }

  private static boolean isCallerSuppliedCharacterSequence(
      @NonNull Token operand,
      @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (operand.getTokenType() != TokenType.VARIABLE)
      return false;

    Object value = context.get(operand.getSymbol());

    if (value instanceof Optional)
      value = ((Optional<?>) value).orElse(null);

    return value instanceof CharSequence;
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
      return numericLiteralValue(operand);

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      try {
        if (value instanceof PluralOperands)
          return validatePluralOperands((PluralOperands) value, operand.getSymbol()).sourceNumber();
        if (value instanceof Number)
          return PluralOperands.validateNumericValue(NumberUtils.toBigDecimal((Number) value),
              format("Numeric value '%s'", operand.getSymbol()), runtimeLimits);
      } catch (IllegalArgumentException e) {
        throw new ExpressionEvaluationException(format(
            "Unable to extract numeric value from '%s': %s", operand.getSymbol(), e.getMessage()), e);
      }
    }

    throw new ExpressionEvaluationException(format("Unable to extract numeric value from '%s'", operand.getSymbol()));
  }

  private void validateNumericLiteralTokens(@NonNull List<@NonNull Token> tokens) {
    requireNonNull(tokens);

    for (Token token : tokens)
      if (token.getTokenType() == TokenType.NUMBER)
        numericLiteralValue(token);
  }

  @NonNull
  private BigDecimal numericLiteralValue(@NonNull Token token) {
    requireNonNull(token);

    try {
      BigDecimal value = new BigDecimal(token.getSymbol());
      return PluralOperands.validateNumericValue(value, format("Numeric literal '%s'", token.getSymbol()), runtimeLimits);
    } catch (IllegalArgumentException e) {
      throw new ExpressionEvaluationException(format("Invalid numeric literal '%s': %s",
          token.getSymbol(), e.getMessage()), e);
    }
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

    if (isGender(operand)) {
      String genderName = LocalizedStringUtils.genderNameForLocalizedStringName(operand.getSymbol());
      Gender gender = Gender.getGendersByName().get(genderName);

      if (gender == null)
        throw new ExpressionEvaluationException(format("Unexpected gender token '%s'", operand.getSymbol()));

      return gender;
    }

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
   * Determines the grammatical case value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the grammatical case value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine grammatical case value (operand is of invalid type, etc.)
   */
  @NonNull
  protected GrammaticalCase grammaticalCaseFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (isGrammaticalCase(operand)) {
      String grammaticalCaseName = LocalizedStringUtils.grammaticalCaseNameForLocalizedStringName(operand.getSymbol());
      GrammaticalCase grammaticalCase = GrammaticalCase.getGrammaticalCasesByName().get(grammaticalCaseName);

      if (grammaticalCase == null)
        throw new ExpressionEvaluationException(format("Unexpected grammatical case token '%s'", operand.getSymbol()));

      return grammaticalCase;
    }

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof GrammaticalCase)
        return (GrammaticalCase) value;
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        GrammaticalCase.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the definiteness value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the definiteness value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine definiteness value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Definiteness definitenessFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (isDefiniteness(operand)) {
      String definitenessName = LocalizedStringUtils.definitenessNameForLocalizedStringName(operand.getSymbol());
      Definiteness definiteness = Definiteness.getDefinitenessByName().get(definitenessName);

      if (definiteness == null)
        throw new ExpressionEvaluationException(format("Unexpected definiteness token '%s'", operand.getSymbol()));

      return definiteness;
    }

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Definiteness)
        return (Definiteness) value;
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Definiteness.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the classifier value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the classifier value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine classifier value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Classifier classifierFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (isClassifier(operand)) {
      String classifierName = LocalizedStringUtils.classifierNameForLocalizedStringName(operand.getSymbol());
      Classifier classifier = Classifier.getClassifiersByName().get(classifierName);

      if (classifier == null)
        throw new ExpressionEvaluationException(format("Unexpected classifier token '%s'", operand.getSymbol()));

      return classifier;
    }

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Classifier)
        return (Classifier) value;
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Classifier.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the formality value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the formality value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine formality value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Formality formalityFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (isFormality(operand)) {
      String formalityName = LocalizedStringUtils.formalityNameForLocalizedStringName(operand.getSymbol());
      Formality formality = Formality.getFormalitiesByName().get(formalityName);

      if (formality == null)
        throw new ExpressionEvaluationException(format("Unexpected formality token '%s'", operand.getSymbol()));

      return formality;
    }

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Formality)
        return (Formality) value;
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Formality.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the clusivity value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the clusivity value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine clusivity value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Clusivity clusivityFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (isClusivity(operand)) {
      String clusivityName = LocalizedStringUtils.clusivityNameForLocalizedStringName(operand.getSymbol());
      Clusivity clusivity = Clusivity.getClusivitiesByName().get(clusivityName);

      if (clusivity == null)
        throw new ExpressionEvaluationException(format("Unexpected clusivity token '%s'", operand.getSymbol()));

      return clusivity;
    }

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Clusivity)
        return (Clusivity) value;
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Clusivity.class.getSimpleName(), operand.getSymbol()));
  }

  /**
   * Determines the animacy value of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @return the animacy value of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine animacy value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Animacy animacyFromOperand(@NonNull Token operand, @NonNull Map<@NonNull String, @Nullable Object> context) {
    requireNonNull(operand);
    requireNonNull(context);

    if (isAnimacy(operand)) {
      String animacyName = LocalizedStringUtils.animacyNameForLocalizedStringName(operand.getSymbol());
      Animacy animacy = Animacy.getAnimaciesByName().get(animacyName);

      if (animacy == null)
        throw new ExpressionEvaluationException(format("Unexpected animacy token '%s'", operand.getSymbol()));

      return animacy;
    }

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Animacy)
        return (Animacy) value;
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Animacy.class.getSimpleName(), operand.getSymbol()));
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
      return Cardinality.forOperands(pluralOperandsFor(bigDecimalFromOperand(operand, context), operand.getSymbol()), locale);

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Cardinality)
        return (Cardinality) value;
      if (value instanceof PluralOperands)
        return Cardinality.forOperands(validatePluralOperands((PluralOperands) value, operand.getSymbol()), locale);
      if (value instanceof Number)
        return Cardinality.forOperands(pluralOperandsFor((Number) value, operand.getSymbol()), locale);
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
      return Ordinality.forOperands(pluralOperandsFor(bigDecimalFromOperand(operand, context), operand.getSymbol()), locale);

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Ordinality)
        return (Ordinality) value;
      if (value instanceof PluralOperands)
        return Ordinality.forOperands(validatePluralOperands((PluralOperands) value, operand.getSymbol()), locale);
      if (value instanceof Number)
        return Ordinality.forOperands(pluralOperandsFor((Number) value, operand.getSymbol()), locale);
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Ordinality.class.getSimpleName(), operand.getSymbol()));
  }

  @NonNull
  private PluralOperands pluralOperandsFor(@NonNull Number number, @NonNull String description) {
    requireNonNull(number);
    requireNonNull(description);

    try {
      return PluralOperands.forNumber(number).runtimeLimits(runtimeLimits).build();
    } catch (IllegalArgumentException e) {
      throw numericOperandFailure(description, e);
    }
  }

  @NonNull
  private PluralOperands validatePluralOperands(@NonNull PluralOperands operands, @NonNull String description) {
    requireNonNull(operands);
    requireNonNull(description);

    try {
      PluralOperands.validateNumericValue(operands.sourceNumber(),
          format("Plural operands value '%s'", description), runtimeLimits);

      if (operands.getCompactExponent() > runtimeLimits.getMaximumCompactExponent())
        throw new IllegalArgumentException(format("Plural operands compact exponent %d exceeds the maximum of %d",
            operands.getCompactExponent(), runtimeLimits.getMaximumCompactExponent()));

      if (operands.explicitVisibleDecimalPlaces().isPresent() &&
          operands.explicitVisibleDecimalPlaces().get() > runtimeLimits.getMaximumVisibleDecimalPlaces())
        throw new IllegalArgumentException(format("Plural operands visible decimal places %s exceeds the maximum of %d",
            operands.explicitVisibleDecimalPlaces().get(), runtimeLimits.getMaximumVisibleDecimalPlaces()));

      return operands;
    } catch (IllegalArgumentException e) {
      throw numericOperandFailure(description, e);
    }
  }

  @NonNull
  private static ExpressionEvaluationException numericOperandFailure(@NonNull String description,
                                                                      @NonNull IllegalArgumentException cause) {
    requireNonNull(description);
    requireNonNull(cause);
    return new ExpressionEvaluationException(format(
        "Unable to extract numeric value from '%s': %s", description, cause.getMessage()), cause);
  }

  /**
   * Determines the phonetic category of an operand.
   *
   * @param operand the operand to examine, not null
   * @param context the context for the expression, not null
   * @param locale  the locale to use for evaluation, not null
   * @return the phonetic category of the operand, not null
   * @throws ExpressionEvaluationException if unable to determine phonetic value (operand is of invalid type, etc.)
   */
  @NonNull
  protected Phonetic phoneticFromOperand(@NonNull Token operand,
                                         @NonNull Map<@NonNull String, @Nullable Object> context,
                                         @NonNull Locale locale) {
    requireNonNull(operand);
    requireNonNull(context);
    requireNonNull(locale);

    if (isPhonetic(operand)) {
      String phoneticName = LocalizedStringUtils.phoneticNameForLocalizedStringName(operand.getSymbol());
      Phonetic phonetic = Phonetic.getPhoneticsByName().get(phoneticName);

      if (phonetic == null)
        throw new ExpressionEvaluationException(format("Unexpected phonetic token '%s'", operand.getSymbol()));

      return phonetic;
    }

    if (operand.getTokenType() == TokenType.VARIABLE) {
      Object value = context.get(operand.getSymbol());

      if (value instanceof Optional)
        value = ((Optional<?>) value).orElse(null);

      if (value instanceof Phonetic)
        return (Phonetic) value;

      if (value instanceof CharSequence) {
        if (phoneticResolver == null)
          throw new ExpressionEvaluationException(format("No %s was provided to resolve placeholder '%s'",
              PhoneticResolver.class.getSimpleName(), operand.getSymbol()));

        String term;

        try {
          term = CharSequenceUtils.toString((CharSequence) value,
              runtimeLimits.getMaximumInterpolatedOutputCharacters(),
              format("Phonetic input for placeholder '%s'", operand.getSymbol()));
        } catch (IllegalArgumentException e) {
          throw new ExpressionEvaluationException(e.getMessage(), e);
        }

        Phonetic phonetic = phoneticResolver.resolve(term, locale);

        if (phonetic == null)
          throw new ExpressionEvaluationException(format("%s returned null for placeholder '%s'",
              PhoneticResolver.class.getSimpleName(), operand.getSymbol()));

        return phonetic;
      }
    }

    throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
        Phonetic.class.getSimpleName(), operand.getSymbol()));
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

  /**
   * Gets the safety limits used by this evaluator.
   *
   * @return runtime limits, not null
   * @since 3.0.0
   */
  @NonNull
  protected TranslationRuntimeLimits getRuntimeLimits() {
    return runtimeLimits;
  }

  protected void validateExpressionSourceLength(@NonNull String expression) {
    requireNonNull(expression);

    if (expression.length() > runtimeLimits.getMaximumExpressionCharacters())
      throw new ExpressionEvaluationException(format("Expression length %d exceeds maximum supported length %d",
          expression.length(), runtimeLimits.getMaximumExpressionCharacters()));
  }

  protected void validateInfixTokens(@NonNull List<@NonNull Token> tokens) {
    requireNonNull(tokens);

    if (tokens.size() > runtimeLimits.getMaximumExpressionTokens())
      throw new ExpressionEvaluationException(format("Expression contains %d tokens, which exceeds maximum supported token count %d",
          tokens.size(), runtimeLimits.getMaximumExpressionTokens()));

    int groupDepth = 0;

    for (Token token : tokens) {
      if (token.getTokenType() == TokenType.GROUP_START) {
        ++groupDepth;

        if (groupDepth > runtimeLimits.getMaximumExpressionNestingDepth())
          throw new ExpressionEvaluationException(format("Expression grouping depth exceeds maximum supported depth %d",
              runtimeLimits.getMaximumExpressionNestingDepth()));
      } else if (token.getTokenType() == TokenType.GROUP_END && groupDepth > 0) {
        --groupDepth;
      }
    }
  }

  protected void validateReversePolishNotationTokens(@NonNull List<@NonNull Token> tokens) {
    requireNonNull(tokens);

    if (tokens.isEmpty())
      throw new ExpressionEvaluationException("Expression must not be empty");

    Deque<ExpressionValidationValue> valueStack = new ArrayDeque<>();

    for (Token token : tokens) {
      if (isOperand(token)) {
        valueStack.push(new ExpressionValidationValue(expressionValueTypeForToken(token), token.getSymbol()));
      } else if (isOperator(token)) {
        if (valueStack.size() < 2)
          throw new ExpressionEvaluationException(format("Insufficient arguments provided for operator '%s'", token.getSymbol()));

        ExpressionValidationValue rightHandOperand = valueStack.pop();
        ExpressionValidationValue leftHandOperand = valueStack.pop();
        validateOperatorOperands(leftHandOperand, token, rightHandOperand);
        valueStack.push(new ExpressionValidationValue(ExpressionValueType.BOOLEAN, "previous comparison"));
      } else {
        throw new ExpressionEvaluationException(format("Unexpected symbol encountered: '%s'", token.getSymbol()));
      }
    }

    if (valueStack.size() == 1) {
      ExpressionValidationValue result = valueStack.pop();

      if (result.getExpressionValueType() == ExpressionValueType.BOOLEAN)
        return;

      throw new ExpressionEvaluationException(format(
          "Expression must evaluate to a boolean result but ended with operand '%s' (%s)",
          result.getSymbol(), result.getExpressionValueType()));
    }

    throw new ExpressionEvaluationException(format("Unexpected extra values exist on the stack: %s", valueStack
        .stream().map(value -> value.getSymbol()).collect(Collectors.toList())));
  }

  @NonNull
  protected ExpressionValueType expressionValueTypeForToken(@NonNull Token token) {
    requireNonNull(token);

    if (token.getTokenType() == TokenType.NUMBER)
      return ExpressionValueType.NUMBER;
    if (isCardinality(token))
      return ExpressionValueType.CARDINALITY;
    if (isOrdinality(token))
      return ExpressionValueType.ORDINALITY;
    if (isGender(token))
      return ExpressionValueType.GENDER;
    if (isGrammaticalCase(token))
      return ExpressionValueType.GRAMMATICAL_CASE;
    if (isDefiniteness(token))
      return ExpressionValueType.DEFINITENESS;
    if (isClassifier(token))
      return ExpressionValueType.CLASSIFIER;
    if (isFormality(token))
      return ExpressionValueType.FORMALITY;
    if (isClusivity(token))
      return ExpressionValueType.CLUSIVITY;
    if (isAnimacy(token))
      return ExpressionValueType.ANIMACY;
    if (isPhonetic(token))
      return ExpressionValueType.PHONETIC;
    if (token.getTokenType() == TokenType.VARIABLE)
      return ExpressionValueType.UNKNOWN_VARIABLE;

    throw new ExpressionEvaluationException(format("Unexpected operand symbol encountered: '%s'", token.getSymbol()));
  }

  protected void validateOperatorOperands(@NonNull ExpressionValidationValue leftHandOperand,
                                          @NonNull Token operator,
                                          @NonNull ExpressionValidationValue rightHandOperand) {
    requireNonNull(leftHandOperand);
    requireNonNull(operator);
    requireNonNull(rightHandOperand);

    ExpressionValueType lhsType = leftHandOperand.getExpressionValueType();
    ExpressionValueType rhsType = rightHandOperand.getExpressionValueType();

    if (isBooleanOperator(operator)) {
      if (lhsType == ExpressionValueType.BOOLEAN && rhsType == ExpressionValueType.BOOLEAN)
        return;

      throw new ExpressionEvaluationException(format(
          "Operator '%s' requires boolean operands but encountered %s and %s in '%s %s %s'",
          operator.getSymbol(), lhsType, rhsType, leftHandOperand.getSymbol(), operator.getSymbol(),
          rightHandOperand.getSymbol()));
    }

    if (isComparisonOperator(operator)) {
      if (lhsType == ExpressionValueType.BOOLEAN || rhsType == ExpressionValueType.BOOLEAN)
        throw new ExpressionEvaluationException(format(
            "Chained comparisons are not supported. Operator '%s' cannot compare %s and %s in '%s %s %s'",
            operator.getSymbol(), lhsType, rhsType, leftHandOperand.getSymbol(), operator.getSymbol(),
            rightHandOperand.getSymbol()));

      if (isOrderingOperator(operator)) {
        if (canBeNumericExpressionValue(lhsType) && canBeNumericExpressionValue(rhsType))
          return;

        throw new ExpressionEvaluationException(format(
            "Operator '%s' requires numeric operands but encountered %s and %s in '%s %s %s'",
            operator.getSymbol(), lhsType, rhsType, leftHandOperand.getSymbol(), operator.getSymbol(),
            rightHandOperand.getSymbol()));
      }

      if (canBeEqualExpressionValue(lhsType, rhsType))
        return;

      throw new ExpressionEvaluationException(format(
          "Operator '%s' cannot compare %s and %s operands in '%s %s %s'",
          operator.getSymbol(), lhsType, rhsType, leftHandOperand.getSymbol(), operator.getSymbol(),
          rightHandOperand.getSymbol()));
    }

    throw new ExpressionEvaluationException(format("Expected operator but encountered '%s'", operator.getSymbol()));
  }

  protected boolean isOrderingOperator(@NonNull Token operator) {
    requireNonNull(operator);
    return operator.getTokenType() == TokenType.LESS_THAN
        || operator.getTokenType() == TokenType.LESS_THAN_OR_EQUAL_TO
        || operator.getTokenType() == TokenType.GREATER_THAN
        || operator.getTokenType() == TokenType.GREATER_THAN_OR_EQUAL_TO;
  }

  protected boolean canBeNumericExpressionValue(@NonNull ExpressionValueType expressionValueType) {
    requireNonNull(expressionValueType);
    return expressionValueType == ExpressionValueType.NUMBER
        || expressionValueType == ExpressionValueType.UNKNOWN_VARIABLE;
  }

  protected boolean canBeEqualExpressionValue(@NonNull ExpressionValueType lhsType,
                                              @NonNull ExpressionValueType rhsType) {
    requireNonNull(lhsType);
    requireNonNull(rhsType);

    if (lhsType == ExpressionValueType.UNKNOWN_VARIABLE || rhsType == ExpressionValueType.UNKNOWN_VARIABLE)
      return true;
    if (lhsType == rhsType)
      return true;

    return (lhsType == ExpressionValueType.NUMBER && (rhsType == ExpressionValueType.CARDINALITY || rhsType == ExpressionValueType.ORDINALITY))
        || (rhsType == ExpressionValueType.NUMBER && (lhsType == ExpressionValueType.CARDINALITY || lhsType == ExpressionValueType.ORDINALITY));
  }

  /**
   * Expression operand types.
   *
   * @author <a href="https://revetkn.com">Mark Allen</a>
   */
  protected enum OperandType {
    NUMBER, BOOLEAN, GENDER, GRAMMATICAL_CASE, DEFINITENESS, CLASSIFIER, FORMALITY, CLUSIVITY, ANIMACY, CARDINALITY, ORDINALITY, PHONETIC, UNKNOWN;
  }

  protected enum ExpressionValueType {
    NUMBER, BOOLEAN, GENDER, GRAMMATICAL_CASE, DEFINITENESS, CLASSIFIER, FORMALITY, CLUSIVITY, ANIMACY, CARDINALITY, ORDINALITY, PHONETIC, UNKNOWN_VARIABLE;
  }

  protected static final class ExpressionValidationValue {
    @NonNull
    private final ExpressionValueType expressionValueType;
    @NonNull
    private final String symbol;

    private ExpressionValidationValue(@NonNull ExpressionValueType expressionValueType, @NonNull String symbol) {
      requireNonNull(expressionValueType);
      requireNonNull(symbol);

      this.expressionValueType = expressionValueType;
      this.symbol = symbol;
    }

    @NonNull
    private ExpressionValueType getExpressionValueType() {
      return expressionValueType;
    }

    @NonNull
    private String getSymbol() {
      return symbol;
    }
  }

  /**
   * Immutable compiled representation of an expression.
   */
  @Immutable
  protected static final class CompiledExpression {
    @NonNull
    private final String expression;
    @NonNull
    private final ExpressionNode root;

    private CompiledExpression(@NonNull String expression, @NonNull ExpressionNode root) {
      this.expression = requireNonNull(expression);
      this.root = requireNonNull(root);
    }

    @NonNull
    protected String getExpression() {
      return expression;
    }

    @NonNull
    private ExpressionNode getRoot() {
      return root;
    }
  }

  @Immutable
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
