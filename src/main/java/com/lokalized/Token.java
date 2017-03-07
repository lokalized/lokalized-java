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
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
final class Token {
	@Nonnull
	private final TokenType tokenType;
	@Nullable
	private final String symbol;

	private Token(TokenType tokenType, String symbol) {
		requireNonNull(symbol);

		this.tokenType = tokenType;
		this.symbol = symbol;
	}

	@Nonnull
	public static Token forTokenType(@Nonnull TokenType tokenType) {
		requireNonNull(tokenType);

		if (!tokenType.getSymbol().isPresent())
			throw new IllegalArgumentException(format("You must provide a symbol for %s.%s values.",
					TokenType.class.getSimpleName(), tokenType.name()));

		return new Token(tokenType, null);
	}

	@Nonnull
	public static Token forTokenTypeAndSymbol(@Nonnull TokenType tokenType, @Nonnull String symbol) {
		requireNonNull(tokenType);
		requireNonNull(symbol);

		if (tokenType.getSymbol().isPresent())
			throw new IllegalArgumentException(format("It is illegal to provide a value (you provided '%s') for %s.%s.",
					symbol, tokenType.getSymbol().get(), TokenType.class.getSimpleName(), tokenType.name()));

		return new Token(tokenType, symbol);
	}

	@Override
	public String toString() {
		return format("%s{tokenType=%s, symbol=%s}", getClass().getSimpleName(), getTokenType().name(), getSymbol());
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;

		if (other == null || !getClass().equals(other.getClass()))
			return false;

		Token token = (Token) other;

		return Objects.equals(getTokenType(), token.getTokenType())
				&& Objects.equals(getSymbol(), token.getSymbol());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getTokenType(), getSymbol());
	}

	@Nonnull
	public TokenType getTokenType() {
		return tokenType;
	}

	@Nonnull
	public Optional<String> getSymbol() {
		return Optional.ofNullable(symbol);
	}
}