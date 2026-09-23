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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

/**
 * Characterization tests for the {@code page} request parameter of
 * {@code GET /vets.html}. These pin what the application does <em>today</em>, not what it
 * ought to do. Every expected value below was captured by running the unmodified
 * application and recording its responses, not copied from a prior document.
 * <p>
 * Two of them - {@link #pageZeroIsInternalServerErrorAndRendersErrorPage()} and
 * {@link #negativePageIsInternalServerErrorAndRendersErrorPage()} - record an HTTP 500
 * that rows 1 and 2 of the frozen criterion in {@code acceptance-2026-09.md} intend to
 * replace with a 400 in the Toggle iteration. The characterization and the criterion are
 * meant to disagree until then; these two are the safety net proving nothing else moves
 * when they are updated.
 * <p>
 * Bug flagged here and deliberately not fixed: {@code showVetList} binds {@code page}
 * with no bound at either end and passes {@code page - 1} straight to
 * {@code PageRequest.of}, so a {@code page} of 0 or below raises
 * {@code IllegalArgumentException}. Nothing resolves it - there is no
 * {@code @ExceptionHandler} or {@code @ControllerAdvice} anywhere in the application - so
 * an ordinary URL answers 500. That is PAID item D1, and repairing it is the subject of
 * later iterations.
 * <p>
 * The harness is a full-context server on a random port driven by
 * {@code TestRestTemplate}, chosen so that the container's error dispatch and the shared
 * error page take part in the result. It mirrors the owner package's
 * {@code OwnerNotFoundIntegrationTests}, which is package-private and so cannot be linked
 * from here. Rendered body text is asserted only for the shared layout and the
 * status-specific message; the exception message itself is not asserted, because whether
 * the container populates it depends on {@code server.error.include-message}, which
 * nothing in this repository configures.
 * <p>
 * Row identity per page is deliberately not asserted: the paginated query declares no
 * sort order (PAID item D3), so which vet lands on which page is undefined. Only counts
 * are pinned.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class VetPaginationCharacterizationTests {

	private static final int SEEDED_VETS = 6;

	private static final int PAGE_SIZE = 5;

	private static final String LAYOUT_TITLE = "PetClinic :: a Spring Framework demonstration";

	@LocalServerPort
	int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private VetRepository vets;

	@Test
	void seedDataIsTheFixtureTheseTestsAssume() {
		assertThat(this.vets.findAll()).hasSize(SEEDED_VETS);
	}

	@Test
	void pageZeroIsInternalServerErrorAndRendersErrorPage() {
		ResponseEntity<String> response = getHtml("/vets.html?page=0");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains(LAYOUT_TITLE);
		assertThat(response.getBody()).contains("Something happened...");
		assertThat(response.getBody()).contains("An internal server error occurred.");
		assertThat(response.getBody()).doesNotContain("Whitelabel Error Page");
	}

	@Test
	void negativePageIsInternalServerErrorAndRendersErrorPage() {
		ResponseEntity<String> response = getHtml("/vets.html?page=-1");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains(LAYOUT_TITLE);
		assertThat(response.getBody()).contains("Something happened...");
		assertThat(response.getBody()).contains("An internal server error occurred.");
		assertThat(response.getBody()).doesNotContain("Whitelabel Error Page");
	}

	@Test
	void integerMinValuePageIsInternalServerErrorAndRendersErrorPage() {
		ResponseEntity<String> response = getHtml("/vets.html?page=-2147483648");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains(LAYOUT_TITLE);
		assertThat(response.getBody()).contains("Something happened...");
		assertThat(response.getBody()).contains("An internal server error occurred.");
		assertThat(response.getBody()).doesNotContain("Whitelabel Error Page");
	}

	@Test
	void absentPageServesAFullFirstPage() {
		ResponseEntity<String> response = getHtml("/vets.html");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isEqualTo(PAGE_SIZE);
		assertThat(response.getBody()).contains("/vets.html?page=2");
	}

	@Test
	void firstPageServesAFullPage() {
		ResponseEntity<String> response = getHtml("/vets.html?page=1");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isEqualTo(PAGE_SIZE);
		assertThat(response.getBody()).contains("/vets.html?page=2");
	}

	@Test
	void secondPageServesTheRemainingVets() {
		ResponseEntity<String> response = getHtml("/vets.html?page=2");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isEqualTo(SEEDED_VETS - PAGE_SIZE);
		assertThat(response.getBody()).contains("/vets.html?page=1");
	}

	@Test
	void pageBeyondTheLastServesAnEmptyTable() {
		ResponseEntity<String> response = getHtml("/vets.html?page=999");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(vetRowCount(response.getBody())).isZero();
		// The pager still renders, because totalPages (2) is greater than 1.
		assertThat(response.getBody()).contains("/vets.html?page=1");
	}

	@Test
	void nonNumericPageIsBadRequest() {
		ResponseEntity<String> response = getHtml("/vets.html?page=abc");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private ResponseEntity<String> getHtml(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.TEXT_HTML));
		headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
		return this.rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	/**
	 * The template renders one table row per vet inside the table body, plus exactly one
	 * header row inside the table head (vetList.html:10-25), so the count of closing row
	 * tags is the vet count plus one.
	 */
	private int vetRowCount(String body) {
		assertThat(body).isNotNull();
		return StringUtils.countOccurrencesOf(body, "</tr>") - 1;
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

}
