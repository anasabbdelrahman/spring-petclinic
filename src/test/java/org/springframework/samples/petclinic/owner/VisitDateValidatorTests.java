/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link VisitDateValidator}.
 * <p>
 * A visit date is valid only when it falls strictly after the supplied reference date.
 * Every date here is a literal, so no outcome depends on the current date.
 * </p>
 */
class VisitDateValidatorTests {

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 6, 15);

	@Test
	void visitDateAfterReferenceDateIsValid() {
		assertTrue(VisitDateValidator.isValid(LocalDate.of(2026, 6, 16), REFERENCE_DATE));
	}

	@Test
	void visitDateEqualToReferenceDateIsInvalid() {
		assertFalse(VisitDateValidator.isValid(LocalDate.of(2026, 6, 15), REFERENCE_DATE));
	}

	@Test
	void visitDateBeforeReferenceDateIsInvalid() {
		assertFalse(VisitDateValidator.isValid(LocalDate.of(2026, 6, 14), REFERENCE_DATE));
	}

	@Test
	void nullVisitDateIsInvalid() {
		assertFalse(VisitDateValidator.isValid(null, REFERENCE_DATE));
	}

	@Test
	void nullReferenceDateIsRejected() {
		assertThrows(NullPointerException.class, () -> VisitDateValidator.isValid(LocalDate.of(2026, 6, 16), null));
		// A missing reference date is always a programming error, so it takes precedence
		// over the rule that a null visit date is merely invalid.
		assertThrows(NullPointerException.class, () -> VisitDateValidator.isValid(null, null));
	}

	@Test
	void datesAreComparedAcrossAYearBoundary() {
		assertTrue(VisitDateValidator.isValid(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31)));
		assertFalse(VisitDateValidator.isValid(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)));
	}

	@Test
	void datesAreComparedAcrossALeapDay() {
		assertTrue(VisitDateValidator.isValid(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 28)));
		assertTrue(VisitDateValidator.isValid(LocalDate.of(2028, 3, 1), LocalDate.of(2028, 2, 29)));
		assertFalse(VisitDateValidator.isValid(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29)));
	}

	@Test
	void resultDependsOnTheReferenceDateAndNotOnTheCurrentDate() {
		// Both dates lie in the real-world past.
		assertTrue(VisitDateValidator.isValid(LocalDate.of(2000, 1, 2), LocalDate.of(2000, 1, 1)));
		assertFalse(VisitDateValidator.isValid(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 2)));
		// Both dates lie in the real-world future.
		assertTrue(VisitDateValidator.isValid(LocalDate.of(2999, 1, 2), LocalDate.of(2999, 1, 1)));
		assertFalse(VisitDateValidator.isValid(LocalDate.of(2998, 12, 31), LocalDate.of(2999, 1, 1)));
	}

}
