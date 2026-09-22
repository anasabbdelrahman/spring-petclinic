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

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Translates the one-based {@code page} request parameter of {@code GET /vets.html} into
 * the zero-based {@link Pageable} the repository expects.
 * <p>
 * Extracted from {@code VetController.findPaginated} with no change of behavior. The
 * translation applies no bound of its own, so a {@code page} whose predecessor is
 * negative reaches {@link PageRequest#of(int, int)} as a negative index and raises
 * {@link IllegalArgumentException} - the behavior
 * {@code VetPaginationCharacterizationTests} pins as an HTTP 500.
 * </p>
 */
final class VetPageRequests {

	/**
	 * The number of vets shown per page. Contract, not a default: the pager in
	 * {@code vetList.html} and the frozen acceptance criterion both depend on it.
	 */
	private static final int PAGE_SIZE = 5;

	private VetPageRequests() {
	}

	/**
	 * Translates a one-based page number, validating nothing.
	 * @param page the one-based page number taken from the request
	 * @return a page request for index {@code page - 1} at the fixed page size
	 * @throws IllegalArgumentException if {@code page - 1} is negative, which
	 * {@link PageRequest#of(int, int)} rejects. Integer arithmetic wraps, so
	 * {@code Integer.MIN_VALUE} does not reach that branch.
	 */
	static Pageable unvalidated(int page) {
		return PageRequest.of(page - 1, PAGE_SIZE);
	}

}
