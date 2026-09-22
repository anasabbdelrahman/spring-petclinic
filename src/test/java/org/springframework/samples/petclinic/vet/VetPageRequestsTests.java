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
package org.springframework.samples.petclinic.vet;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link VetPageRequests}, the one-based page to zero-based {@link Pageable}
 * translation extracted from {@code VetController.findPaginated}.
 * <p>
 * These pin the extracted translation exactly as it behaved inside the controller, and
 * nothing more. It applies no bound of its own: rejecting a page below 1 with a 400 is a
 * later iteration's work, and this class must keep passing unchanged when that arrives,
 * because the unvalidated translation survives the toggle.
 * </p>
 */
class VetPageRequestsTests {

	@Test
	void firstPageTranslatesToPageIndexZero() {
		assertEquals(0, VetPageRequests.unvalidated(1).getPageNumber());
	}

	@Test
	void secondPageTranslatesToPageIndexOne() {
		assertEquals(1, VetPageRequests.unvalidated(2).getPageNumber());
	}

	@Test
	void translationUsesTheContractPageSizeOfFive() {
		assertEquals(5, VetPageRequests.unvalidated(1).getPageSize());
	}

	@Test
	void offsetFollowsFromThePageIndexAndTheContractPageSize() {
		assertEquals(0L, VetPageRequests.unvalidated(1).getOffset());
		assertEquals(5L, VetPageRequests.unvalidated(2).getOffset());
	}

	@Test
	void pageFarBeyondTheLastIsTranslatedRatherThanRejected() {
		assertEquals(998, VetPageRequests.unvalidated(999).getPageNumber());
	}

	@Test
	void pageZeroIsRejectedByTheFrameworkAndNotByThisTranslation() {
		assertThrows(IllegalArgumentException.class, () -> VetPageRequests.unvalidated(0));
	}

	@Test
	void negativePageIsRejectedByTheFrameworkAndNotByThisTranslation() {
		assertThrows(IllegalArgumentException.class, () -> VetPageRequests.unvalidated(-1));
	}

	@Test
	void integerMinValueWrapsToTheHighestPageIndexAndIsNotRejected() {
		// page - 1 overflows to Integer.MAX_VALUE, a legal page index, so this input
		// answers an empty page today rather than raising. Recorded, not fixed here.
		assertEquals(Integer.MAX_VALUE, VetPageRequests.unvalidated(Integer.MIN_VALUE).getPageNumber());
	}

	@Test
	void noSortOrderIsApplied() {
		assertTrue(VetPageRequests.unvalidated(1).getSort().isUnsorted());
	}

}
