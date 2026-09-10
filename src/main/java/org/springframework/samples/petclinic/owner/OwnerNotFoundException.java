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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a well-formed owner ID matches no stored <code>Owner</code>.
 * <p>
 * The <code>@ResponseStatus</code> annotation makes such a request a 404 instead of a
 * 500, without a handler that would also reclassify unrelated failures.
 * </p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
class OwnerNotFoundException extends RuntimeException {

	OwnerNotFoundException(int ownerId) {
		super("Owner not found with id: " + ownerId);
	}

}
