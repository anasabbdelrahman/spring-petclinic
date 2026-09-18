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

import java.time.LocalDate;
import java.util.Objects;

/**
 * Decides whether a visit date is acceptable relative to a supplied reference date.
 * <p>
 * The caller supplies the reference date, so this class never reads the system clock and
 * its result is reproducible.
 * </p>
 */
final class VisitDateValidator {

	private VisitDateValidator() {
	}

	/**
	 * A visit date is valid only when it falls strictly after the reference date.
	 * @param visitDate the date to check, may be {@code null}
	 * @param referenceDate the date the visit date is compared against, never
	 * {@code null}
	 * @return {@code true} if the visit date is after the reference date, {@code false}
	 * if it is on or before it, or if it is {@code null}
	 * @throws NullPointerException if the reference date is {@code null}
	 */
	static boolean isValid(LocalDate visitDate, LocalDate referenceDate) {
		Objects.requireNonNull(referenceDate, "referenceDate must not be null");
		return visitDate != null && visitDate.isAfter(referenceDate);
	}

}
