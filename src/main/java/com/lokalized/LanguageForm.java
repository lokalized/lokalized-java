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

/**
 * Closed marker interface which signifies a Lokalized-defined language construct (genders, grammatical cases,
 * definiteness, classifiers, animacy, clusivity, formality, cardinalities, ordinalities, phonetics).
 * <p>
 * Only the enum types shipped by Lokalized are supported. Application code must not implement this interface;
 * external implementations cannot be resolved by the translation runtime and are rejected. The interface remains
 * syntactically implementable because Lokalized targets Java 9, which predates sealed interfaces.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public interface LanguageForm {
  // Marker interface
}
