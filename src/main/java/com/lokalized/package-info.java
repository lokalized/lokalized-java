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

/**
 * Lokalized facilitates natural-sounding software translations.
 * <p>
 * Localized messages are represented by {@link com.lokalized.LocalizedString}. A message may declare
 * {@link com.lokalized.LocalizedString.LanguageFormTranslation language-form generated fragments} or
 * {@link com.lokalized.LocalizedString.ExpressionTranslation expression-selected generated fragments}. An
 * expression-selected fragment uses its first matching alternative and otherwise its required default translation.
 * <p>
 * Whole-message alternatives form a selected branch. Generated-placeholder definitions inherit from the root through
 * every selected descendant; the nearest descendant's complete same-named definition replaces its ancestor, and
 * unselected siblings are invisible. Lokalized freezes that effective scope before resolving any generated fragment.
 * <p>
 * Each {@link com.lokalized.Strings} lookup uses one unmodifiable shallow snapshot of caller-supplied placeholders for
 * all locale candidates. Whole-message predicates, expression-fragment predicates, and language-form selectors read
 * only from this snapshot. Generated values do not become predicate operands or selector inputs, so a language-form
 * {@code value} or range endpoint always denotes raw caller input even when a generated placeholder has the same name.
 * <p>
 * Evaluated fragment predicate and selected/default fragment interpolation failures are
 * {@link com.lokalized.TranslationFailureReason#RESOLUTION_FAILURE}. The
 * {@link com.lokalized.TranslationFallbackPolicy#fallbackOnMissingTranslationOrNoMatchingAlternative() default
 * fallback policy} stops on resolution failures;
 * {@link com.lokalized.TranslationFallbackPolicy#fallbackOnAnyFailure()} may continue to another locale.
 * <p>
 * See <a href="https://lokalized.com">lokalized.com</a> for complete documentation and examples.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
package com.lokalized;
