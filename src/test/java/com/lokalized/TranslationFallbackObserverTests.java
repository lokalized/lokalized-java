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

import com.lokalized.TranslationFallbackEvent.PrecedingFailure;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises successful fallback observation and its separation from translation failure handling. */
public class TranslationFallbackObserverTests {
	private static final Locale BRITISH = Locale.forLanguageTag("en-GB");
	private static final Locale WORLD = Locale.forLanguageTag("en-001");
	private static final Locale ENGLISH = Locale.ENGLISH;
	private static final Locale FRENCH = Locale.FRENCH;

	private static LocalizedString text(String key, String translation) {
		return new LocalizedString.Builder(key).translation(translation).build();
	}

	private static Strings.Builder fixture() {
		return Strings.withFallbackLocale(FRENCH)
				.localizedStringSupplier(() -> Map.of(
						BRITISH, List.of(text("InEvery", "British")),
						WORLD, List.of(text("InEvery", "World")),
						ENGLISH, List.of(text("InEvery", "English"), text("OnlyInEn", "English only")),
						FRENCH, List.of(text("InEvery", "French"), text("OnlyInFallback", "French only"))))
				.tiebreakerLocalesByLanguageCode(Map.of("en", List.of(BRITISH, WORLD, ENGLISH)))
				.localeSupplier(matcher -> BRITISH);
	}

	@Test
	public void successfulFallbackFiresOnceWithOrderedFailuresAndSharedResultDiagnostics() {
		List<TranslationFallbackEvent> translationFallbackEvents = new ArrayList<>();
		Strings strings = fixture().translationFallbackObserver(translationFallbackEvents::add).build();

		TranslationResult translationResult = strings.getResult("OnlyInEn");
		assertEquals("English only", translationResult.getTranslation());
		assertEquals(1, translationFallbackEvents.size());
		TranslationFallbackEvent translationFallbackEvent = translationFallbackEvents.get(0);
		assertEquals("OnlyInEn", translationFallbackEvent.getKey());
		assertEquals(BRITISH, translationFallbackEvent.getLookupLocale());
		assertEquals(ENGLISH, translationFallbackEvent.getResolvedLocale());
		assertEquals(List.of(BRITISH, WORLD, ENGLISH), translationFallbackEvent.getAttemptedLocales());
		assertSame(translationResult.getAttemptedLocales(), translationFallbackEvent.getAttemptedLocales());
		assertSame(translationResult.getLocaleMatchResult().get(), translationFallbackEvent.getLocaleMatchResult().get());
		assertEquals(2, translationFallbackEvent.getPrecedingFailures().size());
		for (int index = 0; index < 2; ++index) {
			PrecedingFailure precedingFailure = translationFallbackEvent.getPrecedingFailures().get(index);
			assertEquals(translationFallbackEvent.getAttemptedLocales().get(index), precedingFailure.getLocale());
			assertEquals(TranslationFailureReason.MISSING_TRANSLATION, precedingFailure.getReason());
			assertFalse(precedingFailure.getCause().isPresent());
		}

		strings.get("OnlyInFallback");
		assertEquals(2, translationFallbackEvents.size());
		assertEquals(3, translationFallbackEvents.get(1).getPrecedingFailures().size());
		assertEquals(FRENCH, translationFallbackEvents.get(1).getResolvedLocale());
	}

	@Test
	public void firstCandidateAndFinalFailuresDoNotNotifyTheObserver() {
		AtomicInteger observations = new AtomicInteger();
		Strings strings = fixture().translationFallbackObserver(translationFallbackEvent -> observations.incrementAndGet()).build();

		assertEquals("British", strings.get("InEvery"));
		assertEquals("Nowhere", strings.get("Nowhere"));
		assertEquals("handled", strings.get("Nowhere", TranslationOptions.builder()
				.translationFailureHandler(translationFailure -> TranslationFailureResponse.returnString("handled")).build()));
		assertThrows(MissingTranslationException.class, () -> strings.get("Nowhere", TranslationOptions.builder()
				.translationFailureHandler(TranslationFailureHandler.throwException()).build()));
		assertEquals("OnlyInEn", strings.get("OnlyInEn", TranslationOptions.builder()
				.translationFallbackPolicy(TranslationFallbackPolicy.neverFallback()).build()));
		assertEquals(0, observations.get());
	}

	@Test
	public void negotiationFallbackAloneDoesNotNotifyTheObserver() {
		AtomicInteger observations = new AtomicInteger();
		Strings strings = fixture()
				.localeMatchSupplier(matcher -> matcher.matchFor(Locale.forLanguageTag("en-AU")))
				.translationFallbackObserver(translationFallbackEvent -> observations.incrementAndGet()).build();

		TranslationResult translationResult = strings.getResult("InEvery");
		assertTrue(translationResult.isFallback());
		assertEquals(List.of(WORLD), translationResult.getAttemptedLocales());
		assertEquals(0, observations.get());
	}

	@Test
	public void unmatchedAlternativesAndMissingKeysHaveDistinctReasons() {
		LocalizedString choice = new LocalizedString.Builder("Choice")
				.alternatives(List.of(text("count == 1", "one"))).build();
		AtomicReference<TranslationFallbackEvent> observedTranslationFallbackEvent = new AtomicReference<>();
		Strings strings = fixture().localizedStringSupplier(() -> Map.of(
				BRITISH, List.of(choice), WORLD, List.of(),
				ENGLISH, List.of(text("Choice", "English")), FRENCH, List.of()))
				.translationFallbackObserver(observedTranslationFallbackEvent::set).build();

		assertEquals("English", strings.get("Choice", Map.of("count", 2)));
		assertEquals(TranslationFailureReason.NO_MATCHING_ALTERNATIVE, observedTranslationFallbackEvent.get().getPrecedingFailures().get(0).getReason());
		assertEquals(TranslationFailureReason.MISSING_TRANSLATION, observedTranslationFallbackEvent.get().getPrecedingFailures().get(1).getReason());
	}

	@Test
	public void resolutionFailuresPreserveEachCandidatesOwnCause() {
		List<Throwable> causes = new ArrayList<>();
		AtomicReference<TranslationFallbackEvent> observedTranslationFallbackEvent = new AtomicReference<>();
		Strings.Builder builder = fixture().localizedStringSupplier(() -> Map.of(
				BRITISH, List.of(text("Broken", "{{missingBritish}}")),
				WORLD, List.of(text("Broken", "{{missingWorld}}")),
				ENGLISH, List.of(text("Broken", "Recovered")), FRENCH, List.of()))
				.translationFallbackObserver(observedTranslationFallbackEvent::set);

		TranslationResult stoppedTranslationResult = builder.build().getResult("Broken");
		assertEquals(TranslationFailureReason.RESOLUTION_FAILURE, stoppedTranslationResult.getFailureReason().get());
		assertEquals(null, observedTranslationFallbackEvent.get());

		Strings strings = builder.translationFallbackPolicy((reason, locale, cause) -> {
			assertEquals(TranslationFailureReason.RESOLUTION_FAILURE, reason);
			causes.add(cause);
			return true;
		}).build();
		TranslationResult recoveredTranslationResult = strings.getResult("Broken");
		assertEquals("Recovered", recoveredTranslationResult.getTranslation());
		assertFalse(recoveredTranslationResult.getCause().isPresent());
		assertEquals(2, causes.size());
		List<PrecedingFailure> precedingFailures = observedTranslationFallbackEvent.get().getPrecedingFailures();
		assertSame(causes.get(0), precedingFailures.get(0).getCause().get());
		assertSame(causes.get(1), precedingFailures.get(1).getCause().get());
		assertNotSame(precedingFailures.get(0).getCause().get(), precedingFailures.get(1).getCause().get());
	}

	@Test
	public void observerExceptionsPropagateWithoutFurtherFallbackOrFailureHandling() {
		RuntimeException observerFailure = new IllegalStateException("Observer failed");
		AtomicInteger policyCalls = new AtomicInteger();
		AtomicInteger observerCalls = new AtomicInteger();
		AtomicInteger handlerCalls = new AtomicInteger();
		Strings strings = fixture().translationFallbackObserver(translationFallbackEvent -> {
			observerCalls.incrementAndGet();
			throw observerFailure;
		}).translationFallbackPolicy((reason, locale, cause) -> {
			policyCalls.incrementAndGet();
			return true;
		}).translationFailureHandler(translationFailure -> {
			handlerCalls.incrementAndGet();
			return TranslationFailureResponse.returnKey();
		}).build();

		assertSame(observerFailure, assertThrows(RuntimeException.class, () -> strings.get("OnlyInEn")));
		assertEquals(1, observerCalls.get());
		assertEquals(2, policyCalls.get());
		assertEquals(0, handlerCalls.get());
		assertEquals("British", strings.get("InEvery"));
		assertEquals(1, observerCalls.get());
	}

	@Test
	public void perCallObserversReplaceInstanceObserversAndNullInherits() {
		AtomicInteger instanceCalls = new AtomicInteger();
		AtomicInteger perCallCalls = new AtomicInteger();
		Strings strings = fixture().translationFallbackObserver(translationFallbackEvent -> instanceCalls.incrementAndGet()).build();
		TranslationOptions override = TranslationOptions.builder()
				.translationFallbackObserver(translationFallbackEvent -> perCallCalls.incrementAndGet()).build();

		assertEquals("English only", strings.get("OnlyInEn", override));
		assertEquals(0, instanceCalls.get());
		assertEquals(1, perCallCalls.get());
		strings.getResult("OnlyInEn", override.toBuilder().translationFallbackObserver(null).build());
		assertEquals(1, instanceCalls.get());
		assertEquals(1, perCallCalls.get());
		fixture().build().get("OnlyInEn", override);
		assertEquals(2, perCallCalls.get());
	}

	@Test
	public void eventsAreImmutableSnapshotsWithSafeDiagnostics() {
		List<Locale> attempts = new ArrayList<>(List.of(BRITISH, ENGLISH));
		TranslationResult translationResult = new TranslationResult("Key", "SECRET-TRANSLATION", BRITISH, ENGLISH, attempts,
				TranslationResultStatus.TRANSLATED, null, null);
		Throwable cause = new IllegalStateException("SECRET-CAUSE") {
			@Override
			public String toString() {
				throw new AssertionError("Diagnostics must not inspect causes");
			}
		};
		List<PrecedingFailure> precedingFailures = new ArrayList<>(List.of(
				new PrecedingFailure(BRITISH, TranslationFailureReason.RESOLUTION_FAILURE, cause)));
		TranslationFallbackEvent translationFallbackEvent = new TranslationFallbackEvent(translationResult, precedingFailures);
		attempts.clear();
		precedingFailures.clear();

		assertEquals(List.of(BRITISH, ENGLISH), translationFallbackEvent.getAttemptedLocales());
		assertEquals(1, translationFallbackEvent.getPrecedingFailures().size());
		assertSame(cause, translationFallbackEvent.getPrecedingFailures().get(0).getCause().get());
		assertFalse(translationFallbackEvent.getLocaleMatchResult().isPresent());
		assertThrows(UnsupportedOperationException.class, () -> translationFallbackEvent.getAttemptedLocales().clear());
		assertThrows(UnsupportedOperationException.class, () -> translationFallbackEvent.getPrecedingFailures().clear());
		assertFalse(translationFallbackEvent.toString().contains("SECRET-TRANSLATION"));
		assertFalse(translationFallbackEvent.toString().contains("SECRET-CAUSE"));
	}

	@Test
	public void invalidEventsAndFailureRecordsAreRefused() {
		TranslationResult successfulTranslationResult = new TranslationResult("Key", "Value", BRITISH, ENGLISH, List.of(BRITISH, ENGLISH),
				TranslationResultStatus.TRANSLATED, null, null);
		TranslationResult failedTranslationResult = new TranslationResult("Key", "Key", BRITISH, null, List.of(BRITISH),
				TranslationResultStatus.RETURNED_KEY, TranslationFailureReason.MISSING_TRANSLATION, null);
		PrecedingFailure missingPrecedingFailure = new PrecedingFailure(BRITISH, TranslationFailureReason.MISSING_TRANSLATION, null);
		assertThrows(IllegalArgumentException.class, () -> new TranslationFallbackEvent(failedTranslationResult, List.of(missingPrecedingFailure)));
		assertThrows(IllegalArgumentException.class, () -> new TranslationFallbackEvent(successfulTranslationResult, List.of()));
		assertThrows(IllegalArgumentException.class, () -> new TranslationFallbackEvent(successfulTranslationResult, List.of(
				new PrecedingFailure(WORLD, TranslationFailureReason.MISSING_TRANSLATION, null))));
		assertThrows(IllegalArgumentException.class,
				() -> new PrecedingFailure(BRITISH, TranslationFailureReason.RESOLUTION_FAILURE, null));
		assertThrows(IllegalArgumentException.class, () -> new PrecedingFailure(BRITISH,
				TranslationFailureReason.MISSING_TRANSLATION, new IllegalStateException()));
	}

	@Test
	public void reentrantObserversKeepLookupStateSeparate() {
		List<TranslationFallbackEvent> translationFallbackEvents = new ArrayList<>();
		AtomicReference<Strings> holder = new AtomicReference<>();
		Strings strings = fixture().translationFallbackObserver(translationFallbackEvent -> {
			translationFallbackEvents.add(translationFallbackEvent);
			if (translationFallbackEvent.getKey().equals("OnlyInEn"))
				assertEquals("French only", holder.get().get("OnlyInFallback"));
		}).build();
		holder.set(strings);

		assertEquals("English only", strings.get("OnlyInEn"));
		assertEquals(2, translationFallbackEvents.size());
		assertEquals("OnlyInEn", translationFallbackEvents.get(0).getKey());
		assertEquals(2, translationFallbackEvents.get(0).getPrecedingFailures().size());
		assertEquals("OnlyInFallback", translationFallbackEvents.get(1).getKey());
		assertEquals(3, translationFallbackEvents.get(1).getPrecedingFailures().size());
	}

	@Test
	public void concurrentLookupsShareTheObserverWithoutSharingEventState() throws Exception {
		ThreadLocal<TranslationFallbackEvent> observedTranslationFallbackEvent = new ThreadLocal<>();
		AtomicInteger observations = new AtomicInteger();
		Strings strings = fixture().translationFallbackObserver(translationFallbackEvent -> {
			observedTranslationFallbackEvent.set(translationFallbackEvent);
			observations.incrementAndGet();
		}).build();
		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			List<Future<Boolean>> futures = new ArrayList<>();
			for (int index = 0; index < 32; ++index) {
				Locale locale = index % 2 == 0 ? BRITISH : WORLD;
				futures.add(executor.submit(() -> {
					TranslationResult translationResult = strings.getResult("OnlyInEn", TranslationOptions.forLocale(locale));
					TranslationFallbackEvent translationFallbackEvent = observedTranslationFallbackEvent.get();
					observedTranslationFallbackEvent.remove();
					return translationFallbackEvent.getLookupLocale().equals(locale)
							&& translationFallbackEvent.getAttemptedLocales() == translationResult.getAttemptedLocales()
							&& translationFallbackEvent.getLocaleMatchResult().get() == translationResult.getLocaleMatchResult().get();
				}));
			}
			for (Future<Boolean> future : futures)
				assertTrue(future.get());
			assertEquals(32, observations.get());
		} finally {
			executor.shutdownNow();
		}
	}
}
