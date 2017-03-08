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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
enum TokenType {
	VARIABLE(null),
	NUMBER(null),
	GROUP_START("("),
	GROUP_END(")"),
	AND("&&"),
	OR("||"),
	LESS_THAN("<"),
	GREATER_THAN(">"),
	EQUAL_TO("=="),
	NOT_EQUAL_TO("!="),
	LESS_THAN_OR_EQUAL_TO("<="),
	GREATER_THAN_OR_EQUAL_TO(">="),
	ZERO("ZERO"),
	ONE("ONE"),
	TWO("TWO"),
	FEW("FEW"),
	MANY("MANY"),
	OTHER("OTHER"),
	MASCULINE("MASCULINE"),
	FEMININE("FEMININE"),
	NEUTER("NEUTER");

	@Nullable
	private final String symbol;
	@Nonnull
	private static final Set<TokenType> TOKEN_TYPES_WITH_DEFINED_SYMBOL;
	@Nonnull
	private static final Set<TokenType> TOKEN_TYPES_WITH_UNDEFINED_SYMBOL;
	@Nonnull
	private static final Map<String, TokenType> TOKEN_TYPES_BY_SYMBOL;

	static {
		TOKEN_TYPES_WITH_DEFINED_SYMBOL = Collections.unmodifiableSet(Arrays.asList(TokenType.values()).stream()
				.filter(tokenType -> tokenType.getSymbol().isPresent())
				.collect(Collectors.toSet()));

		TOKEN_TYPES_WITH_UNDEFINED_SYMBOL = Collections.unmodifiableSet(Arrays.asList(TokenType.values()).stream()
				.filter(tokenType -> !tokenType.getSymbol().isPresent())
				.collect(Collectors.toSet()));

		TOKEN_TYPES_BY_SYMBOL = Collections.unmodifiableMap(TOKEN_TYPES_WITH_DEFINED_SYMBOL.stream()
				.collect(Collectors.toMap(tokenType -> tokenType.getSymbol().get(), tokenType -> tokenType)));
	}

	TokenType(@Nullable String symbol) {
		this.symbol = symbol;
	}

	@Nonnull
	public Optional<String> getSymbol() {
		return Optional.ofNullable(symbol);
	}

	@Nonnull
	public static Set<TokenType> getTokenTypesWithDefinedSymbol() {
		return TOKEN_TYPES_WITH_DEFINED_SYMBOL;
	}

	@Nonnull
	public static Set<TokenType> getTokenTypesWithUndefinedSymbol() {
		return TOKEN_TYPES_WITH_UNDEFINED_SYMBOL;
	}

	@Nonnull
	public static Map<String, TokenType> getTokenTypesBySymbol() {
		return TOKEN_TYPES_BY_SYMBOL;
	}
}