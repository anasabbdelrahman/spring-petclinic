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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the one-based {@code page} of {@code GET /vets.html} is below 1.
 * <p>
 * A dedicated type rather than {@link IllegalArgumentException}, so that binding a status
 * to it cannot reclassify unrelated failures. The message is fixed and does not echo the
 * requested value.
 * </p>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class InvalidVetPageException extends RuntimeException {

	InvalidVetPageException() {
		super("Page must be 1 or greater");
	}

}
