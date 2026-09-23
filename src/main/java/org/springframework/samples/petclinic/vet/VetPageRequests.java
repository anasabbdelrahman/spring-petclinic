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
 * Two translations sit behind this seam. {@link #unvalidated(int)}, extracted from
 * {@code VetController.findPaginated} unchanged, applies no bound of its own and is the
 * one the controller calls. {@link #validated(int)} rejects every page below 1 with
 * {@link InvalidVetPageException} and is not yet called. For a page they both accept,
 * both apply the same page size and the same one-based to zero-based translation.
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

	/**
	 * Translates a one-based page number, rejecting every page below 1.
	 * @param page the one-based page number taken from the request
	 * @return a page request for index {@code page - 1} at the fixed page size
	 * @throws InvalidVetPageException if {@code page} is less than 1
	 */
	static Pageable validated(int page) {
		if (page < 1) {
			throw new InvalidVetPageException();
		}
		return PageRequest.of(page - 1, PAGE_SIZE);
	}

}
