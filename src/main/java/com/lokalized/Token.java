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
	 * @param tokenType what kind of token this is, not null
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
	 * @param tokenType what kind of token this is, not null
	 * @param symbol    the symbol for this token, not null
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
	 * * Generates a {@code String} representation of this object.
	 *
	 * @return a string representation of this object, not null
	 */
	@Override
	@Nonnull
	public String toString() {
		return format("%s{tokenType=%s, symbol=%s}", getClass().getSimpleName(), getTokenType().name(), getSymbol());
	}

	/**
	 * Checks if this object is equal to another one.
	 *
	 * @param other the object to check, null returns false
	 * @return true if this is equal to the other object, false otherwise
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
	 * A hash code for this object.
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
	 * @return the type of this token, not null
	 */
	@Nonnull
	public TokenType getTokenType() {
		return tokenType;
	}

	/**
	 * Gets the symbol for this token.
	 *
	 * @return the symbol for this token, not null
	 */
	@Nonnull
	public String getSymbol() {
		return symbol;
	}
}