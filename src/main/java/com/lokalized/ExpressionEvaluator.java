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

	// TRUE and FALSE are magic tokens used at RPN evaluation time to hold result of boolean expressions.
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
	 * Evaluates an expression given context and locale.
	 * <p>
	 * Locale is necessary for plural form evaluation.
	 *
	 * @param expression the expression to evaluate, not null
	 * @param context    the context for the expression, not null
	 * @param locale     the locale to use for evaluation, not null
	 * @return the result of expression evaluation, not null
	 * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
	 */
	@Nonnull
	public Boolean evaluate(@Nonnull String expression, @Nonnull Map<String, Object> context, @Nonnull Locale locale) {
		requireNonNull(expression);
		requireNonNull(context);
		requireNonNull(locale);

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
	 *
	 * @param tokens  the RPN-format tokens to evaluate, not null
	 * @param context the context for the expression, not null
	 * @param locale  the locale to use for evaluation, not null
	 * @return the result of expression evaluation, not null
	 * @throws ExpressionEvaluationException if an error occurs while evaluating the expression
	 */
	@Nonnull
	protected Boolean evaluateReversePolishNotationTokens(@Nonnull List<Token> tokens, @Nonnull Map<String, Object> context, @Nonnull Locale locale) {
		requireNonNull(tokens);
		requireNonNull(context);
		requireNonNull(locale);

		throw new UnsupportedOperationException();
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