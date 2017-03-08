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
import javax.annotation.concurrent.Immutable;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A localization expression token.
 * <p>
 * A list of tokens is the output of lexical analysis as performed by {@link ExpressionTokenizer}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
final class Token {
	@Nonnull
	private final TokenType tokenType;
	@Nonnull
	private final String symbol;

	/**
	 * Constructs a new token with the given type.
	 *
	 * @param tokenType what kind of token this is, not {@code null}
	 * @throws IllegalArgumentException if the token type does not have a predefined symbol
	 */
	public Token(@Nonnull TokenType tokenType) {
		requireNonNull(tokenType);

		if (TokenType.getTokenTypesWithUndefinedSymbol().contains(tokenType))
			throw new IllegalArgumentException(format("You must provide a symbol for %s.%s values.",
					TokenType.class.getSimpleName(), tokenType.name()));

		this.tokenType = tokenType;
		this.symbol = tokenType.getSymbol().get();
	}

	/**
	 * Constructs a new token with the given type and symbol.
	 *
	 * @param tokenType what kind of token this is, not {@code null}
	 * @param symbol    the symbol for this token, not {@code null}
	 * @throws IllegalArgumentException if the token type has a predefined symbol that does not match the provided symbol
	 */
	public Token(@Nonnull TokenType tokenType, @Nonnull String symbol) {
		requireNonNull(tokenType);
		requireNonNull(symbol);

		if (TokenType.getTokenTypesWithDefinedSymbol().contains(tokenType) && !tokenType.getSymbol().get().equals(symbol))
			throw new IllegalArgumentException(format("Provided symbol value '%s' does not match required value '%s' for %s.%s.",
					symbol, tokenType.getSymbol().get(), TokenType.class.getSimpleName(), tokenType.name()));

		this.tokenType = tokenType;
		this.symbol = symbol;
	}

	/**
	 * Generates a representation of this token as a {@link java.lang.String}.
	 *
	 * @return a string representation of this token, not {@code null}
	 */
	@Override
	@Nonnull
	public String toString() {
		return format("%s{tokenType=%s, symbol=%s}", getClass().getSimpleName(), getTokenType().name(), getSymbol());
	}

	/**
	 * Checks if this token is equal to another token.
	 *
	 * @param other the object to check, {@code null} returns {@code false}
	 * @return {@code true} if this is equal to the other token, {@code false} otherwise
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other)
			return true;

		if (other == null || !getClass().equals(other.getClass()))
			return false;

		Token token = (Token) other;

		return Objects.equals(getTokenType(), token.getTokenType())
				&& Objects.equals(getSymbol(), token.getSymbol());
	}

	/**
	 * A hash code for this token.
	 *
	 * @return a suitable hash code
	 */
	@Override
	public int hashCode() {
		return Objects.hash(getTokenType(), getSymbol());
	}

	/**
	 * Gets the type of this token.
	 *
	 * @return the type of this token, not {@code null}
	 */
	@Nonnull
	public TokenType getTokenType() {
		return tokenType;
	}

	/**
	 * Gets the symbol for this token.
	 *
	 * @return the symbol for this token, not {@code null}
	 */
	@Nonnull
	public String getSymbol() {
		return symbol;
	}
}