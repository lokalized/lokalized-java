/*
 * Copyright 2017 Transmogrify LLC.
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
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
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
 * EXPRESSION = OPERAND COMPARISON_OPERATOR OPERAND | '(' EXPRESSION ')' | EXPRESSION BOOLEAN_OPERATOR EXPRESSION
 * OPERAND = VARIABLE | PLURAL | GENDER | NUMBER
 * PLURAL = 'ZERO' | 'ONE' | 'TWO' | 'FEW' | 'MANY' | 'OTHER'
 * GENDER = 'MASCULINE' | 'FEMININE' | 'NEUTER'
 * VARIABLE = alphanumeric
 * BOOLEAN_OPERATOR = '&&' | '||'
 * COMPARISON_OPERATOR = '<' | '>' | '<=' | '>=' | '==' | '!='
 * </pre>
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
class ExpressionEvaluator {
	@Nonnull
	private static final Set<TokenType> PLURAL_TOKEN_TYPES;
	@Nonnull
	private static final Set<TokenType> GENDER_TOKEN_TYPES;
	@Nonnull
	private static final Set<TokenType> COMPARISON_OPERATOR_TOKEN_TYPES;
	@Nonnull
	private static final Set<TokenType> BOOLEAN_OPERATOR_TOKEN_TYPES;
	@Nonnull
	private static final Set<TokenType> OPERAND_TOKEN_TYPES;
	@Nonnull
	private static final Set<TokenType> OPERATOR_TOKEN_TYPES;
	@Nonnull
	private static final Token TRUE_RESULT_TOKEN;
	@Nonnull
	private static final Token FALSE_RESULT_TOKEN;

	@Nonnull
	private final ExpressionTokenizer expressionTokenizer;

	static {
		PLURAL_TOKEN_TYPES = Collections.unmodifiableSet(new HashSet<TokenType>() {
			{
				add(TokenType.ZERO);
				add(TokenType.ONE);
				add(TokenType.TWO);
				add(TokenType.FEW);
				add(TokenType.MANY);
				add(TokenType.OTHER);
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

		Set<TokenType> operandTokenTypes = new HashSet<>();
		operandTokenTypes.addAll(PLURAL_TOKEN_TYPES);
		operandTokenTypes.addAll(GENDER_TOKEN_TYPES);
		operandTokenTypes.add(TokenType.VARIABLE);
		operandTokenTypes.add(TokenType.NUMBER);

		OPERAND_TOKEN_TYPES = Collections.unmodifiableSet(operandTokenTypes);

		Set<TokenType> operatorTokenTypes = new HashSet<>();
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
		this.expressionTokenizer = expressionTokenizer == null ? new ExpressionTokenizer() : null;
	}

	/**
	 * Evaluates an expression given a locale.
	 * <p>
	 * Locale is necessary for plural form evaluation.
	 *
	 * @param expression the expression to evaluate, not null
	 * @param locale     the locale to use for evaluation, not null
	 * @return the result of expression evaluation, not null
	 * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
	 */
	@Nonnull
	public Boolean evaluate(@Nonnull String expression, @Nonnull Locale locale) {
		return evaluate(expression, null, locale);
	}

	/**
	 * Evaluates an expression given context and locale.
	 * <p>
	 * Locale is necessary for plural form evaluation.
	 *
	 * @param expression the expression to evaluate, not null
	 * @param context    the context for the expression, may be null
	 * @param locale     the locale to use for evaluation, not null
	 * @return the result of expression evaluation, not null
	 * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
	 */
	@Nonnull
	public Boolean evaluate(@Nonnull String expression, @Nullable Map<String, Object> context, @Nonnull Locale locale) {
		requireNonNull(expression);
		requireNonNull(locale);

		if (context == null)
			context = Collections.emptyMap();

		List<Token> tokens = getExpressionTokenizer().extractTokens(expression);
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
	@Nonnull
	protected List<Token> convertTokensToReversePolishNotation(@Nonnull List<Token> tokens) {
		requireNonNull(tokens);

		List<Token> outputTokens = new ArrayList<>(tokens.size());
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
	@Nonnull
	protected Boolean evaluateReversePolishNotationTokens(@Nonnull List<Token> tokens,
																												@Nonnull Map<String, Object> context, @Nonnull Locale locale) {
		requireNonNull(tokens);
		requireNonNull(context);
		requireNonNull(locale);

		Deque<Token> valueStack = new ArrayDeque<>();

		// Evaluate RPN tokens.
		// See http://en.wikipedia.org/wiki/Reverse_Polish_notation
		for (Token token : tokens) {
			// If the token is a value
			if (isOperand(token)) {
				// Push it onto the stack.
				valueStack.push(token);
			} else if (isOperator(token)) {
				// It is known a priori that an operator takes 2 arguments.
				// If there are fewer than 2 values on the stack, the user has not input sufficient values in the expression.
				if (valueStack.size() < 2)
					throw new ExpressionEvaluationException(format("Insufficient arguments provided for operator '%s' (%s)",
							token.getSymbol(), valueStack.stream().map(operand -> operand.getSymbol()).collect(Collectors.toList())));

				// Else, Pop the top 2 values from the stack.
				Token rightHandOperand = valueStack.pop();
				Token leftHandOperand = valueStack.pop();

				// Evaluate the operator, with the values as arguments.
				Token result = evaluateBinaryOperator(leftHandOperand, token, rightHandOperand, context, locale);

				// Push the returned results, if any, back onto the stack.
				valueStack.push(result);
			} else {
				throw new ExpressionEvaluationException(format("Unexpected symbol encountered: '%s'", token.getSymbol()));
			}
		}

		// If there is only one value in the stack, that value is the result of the calculation.
		if (valueStack.size() == 1) {
			Token resultToken = valueStack.pop();

			if (resultToken == TRUE_RESULT_TOKEN)
				return true;
			if (resultToken == FALSE_RESULT_TOKEN)
				return false;

			throw new ExpressionEvaluationException(format("Unexpected final symbol encountered: '%s'", resultToken.getSymbol()));
		}

		// Otherwise, there are more values in the stack. The user input has too many values.
		throw new ExpressionEvaluationException(format("Unexpected extra values exist on the stack: %s", valueStack
				.stream().map(operand -> operand.getSymbol()).collect(Collectors.toList())));
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
	protected Token evaluateBinaryOperator(@Nonnull Token leftHandOperand, @Nonnull Token operator, @Nonnull Token rightHandOperand,
																				 @Nonnull Map<String, Object> context, @Nonnull Locale locale) {
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

			// If either side is unknown we evaluate to false
			// TODO: fail-fast here?
			if (lhsOperandType == OperandType.UNKNOWN || rhsOperandType == OperandType.UNKNOWN)
				return FALSE_RESULT_TOKEN;

			// null == null
			if (lhsOperandType == OperandType.NULL && rhsOperandType == OperandType.NULL)
				return TRUE_RESULT_TOKEN;

			// null on either side evaluates to false (no coercing to some default value)
			if (lhsOperandType == OperandType.NULL || rhsOperandType == OperandType.NULL)
				return FALSE_RESULT_TOKEN;

			// Number (operators: any)
			if (lhsOperandType == OperandType.NUMBER && rhsOperandType == OperandType.NUMBER) {
				double lhsValue = doubleFromOperand(leftHandOperand, context);
				double rhsValue = doubleFromOperand(rightHandOperand, context);
				boolean result = false;

				if (operator.getTokenType() == TokenType.LESS_THAN)
					result = lhsValue < rhsValue;
				else if (operator.getTokenType() == TokenType.LESS_THAN_OR_EQUAL_TO)
					result = lhsValue <= rhsValue;
				else if (operator.getTokenType() == TokenType.GREATER_THAN)
					result = lhsValue > rhsValue;
				else if (operator.getTokenType() == TokenType.GREATER_THAN_OR_EQUAL_TO)
					result = lhsValue >= rhsValue;
				else if (operator.getTokenType() == TokenType.EQUAL_TO)
					result = lhsValue == rhsValue;
				else if (operator.getTokenType() == TokenType.NOT_EQUAL_TO)
					result = lhsValue != rhsValue;
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

			// Plural (operators: ==, !=)
			if (lhsOperandType == OperandType.PLURAL || rhsOperandType == OperandType.PLURAL) {
				Plural lhsValue = pluralFromOperand(leftHandOperand, context, locale);
				Plural rhsValue = pluralFromOperand(rightHandOperand, context, locale);

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
	@Nonnull
	protected Integer precedence(@Nonnull Token token) {
		requireNonNull(token);

		if (COMPARISON_OPERATOR_TOKEN_TYPES.contains(token.getTokenType()))
			return 1;
		if (BOOLEAN_OPERATOR_TOKEN_TYPES.contains(token.getTokenType()))
			return 0;

		throw new ExpressionEvaluationException(format("Cannot determine precedence for '%s'", token.getSymbol()));
	}

	/**
	 * Does the specified token represent an operand?
	 *
	 * @param token the token to check, not null
	 * @return whether the token represents an operand, not null
	 */
	@Nonnull
	protected Boolean isOperand(@Nonnull Token token) {
		requireNonNull(token);
		return OPERAND_TOKEN_TYPES.contains(token.getTokenType());
	}

	/**
	 * Does the specified token represent an operator?
	 *
	 * @param token the token to check, not null
	 * @return whether the token represents an operator, not null
	 */
	@Nonnull
	protected Boolean isOperator(@Nonnull Token token) {
		requireNonNull(token);
		return OPERATOR_TOKEN_TYPES.contains(token.getTokenType());
	}

	/**
	 * Does the specified token represent a boolean operator?
	 *
	 * @param token the token to check, not null
	 * @return whether the token represents a boolean operator, not null
	 */
	@Nonnull
	protected Boolean isBooleanOperator(@Nonnull Token token) {
		requireNonNull(token);
		return BOOLEAN_OPERATOR_TOKEN_TYPES.contains(token.getTokenType());
	}

	/**
	 * Does the specified token represent a comparison operator?
	 *
	 * @param token the token to check, not null
	 * @return whether the token represents a comparison operation, not null
	 */
	@Nonnull
	protected Boolean isComparisonOperator(@Nonnull Token token) {
		requireNonNull(token);
		return COMPARISON_OPERATOR_TOKEN_TYPES.contains(token.getTokenType());
	}

	/**
	 * Does the specified token represent a boolean result?
	 *
	 * @param token the token to check, not null
	 * @return whether the token represents a boolean result, not null
	 */
	@Nonnull
	protected Boolean isBooleanResult(@Nonnull Token token) {
		requireNonNull(token);
		return token == TRUE_RESULT_TOKEN || token == FALSE_RESULT_TOKEN;
	}

	/**
	 * Does the specified token represent a gender?
	 *
	 * @param token the token to check, not null
	 * @return whether the token represents a gender, not null
	 */
	@Nonnull
	protected Boolean isGender(@Nonnull Token token) {
		requireNonNull(token);
		return GENDER_TOKEN_TYPES.contains(token.getTokenType());
	}

	/**
	 * Does the specified token represent a plural?
	 *
	 * @param token the token to check, not null
	 * @return whether the token represents a plural, not null
	 */
	@Nonnull
	protected Boolean isPlural(@Nonnull Token token) {
		requireNonNull(token);
		return PLURAL_TOKEN_TYPES.contains(token.getTokenType());
	}

	/**
	 * Determines the type of an operand.
	 *
	 * @param operand the operand to examine, not null
	 * @param context the context for the expression, not null
	 * @return the type of the operand (or {@link OperandType#UNKNOWN} if indeterminate), not null
	 */
	@Nonnull
	protected OperandType operandType(@Nonnull Token operand, @Nonnull Map<String, Object> context) {
		requireNonNull(operand);
		requireNonNull(context);

		if (operand.getTokenType() == TokenType.NUMBER)
			return OperandType.NUMBER;
		if (isPlural(operand))
			return OperandType.PLURAL;
		if (isGender(operand))
			return OperandType.GENDER;

		if (operand.getTokenType() == TokenType.VARIABLE) {
			Object value = context.get(operand.getSymbol());

			if (value instanceof Optional)
				value = ((Optional<?>) value).orElse(null);

			if (value == null)
				return OperandType.NULL;
			if (value instanceof Number)
				return OperandType.NUMBER;
			if (value instanceof Plural)
				return OperandType.PLURAL;
			if (value instanceof Gender)
				return OperandType.GENDER;
		}

		return OperandType.UNKNOWN;
	}

	/**
	 * Determines the double value of an operand.
	 *
	 * @param operand the operand to examine, not null
	 * @param context the context for the expression, not null
	 * @return the double value of the operand, not null
	 * @throws ExpressionEvaluationException if unable to determine double value (operand is of invalid type, etc.)
	 */
	@Nonnull
	protected Double doubleFromOperand(@Nonnull Token operand, @Nonnull Map<String, Object> context) {
		requireNonNull(operand);
		requireNonNull(context);

		if (operand.getTokenType() == TokenType.NUMBER)
			return Double.parseDouble(operand.getSymbol());

		if (operand.getTokenType() == TokenType.VARIABLE) {
			Object value = context.get(operand.getSymbol());

			if (value instanceof Optional)
				value = ((Optional<?>) value).orElse(null);

			if (value instanceof Number)
				return ((Number) value).doubleValue();
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
	@Nonnull
	protected Gender genderFromOperand(@Nonnull Token operand, @Nonnull Map<String, Object> context) {
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
	 * Determines the plural value of an operand.
	 *
	 * @param operand the operand to examine, not null
	 * @param context the context for the expression, not null
	 * @param locale  the locale to use for evaluation, not null
	 * @return the plural value of the operand, not null
	 * @throws ExpressionEvaluationException if unable to determine plural value (operand is of invalid type, etc.)
	 */
	@Nonnull
	protected Plural pluralFromOperand(@Nonnull Token operand, @Nonnull Map<String, Object> context, @Nonnull Locale locale) {
		requireNonNull(operand);
		requireNonNull(context);
		requireNonNull(locale);

		if (isPlural(operand))
			return Plural.getPluralsByName().get(operand.getSymbol());

		if (operand.getTokenType() == TokenType.NUMBER)
			return Plural.pluralForNumber(doubleFromOperand(operand, context), locale);

		if (operand.getTokenType() == TokenType.VARIABLE) {
			Object value = context.get(operand.getSymbol());

			if (value instanceof Optional)
				value = ((Optional<?>) value).orElse(null);

			if (value instanceof Plural)
				return (Plural) value;
			if (value instanceof Number)
				return Plural.pluralForNumber((Number) value, locale);
		}

		throw new ExpressionEvaluationException(format("Unable to extract %s value from '%s'",
				Plural.class.getSimpleName(), operand.getSymbol()));
	}

	/**
	 * Determines the boolean value of a token.
	 *
	 * @param token the token to examine, not null
	 * @return the boolean value of the token, not null
	 * @throws ExpressionEvaluationException if unable to determine boolean value (token is of invalid type, etc.)
	 */
	@Nonnull
	protected Boolean booleanValue(@Nonnull Token token) {
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
	@Nonnull
	protected ExpressionTokenizer getExpressionTokenizer() {
		return expressionTokenizer;
	}

	/**
	 * Expression operand types.
	 *
	 * @author <a href="https://revetkn.com">Mark Allen</a>
	 */
	protected enum OperandType {
		NUMBER, GENDER, PLURAL, NULL, UNKNOWN;
	}
}